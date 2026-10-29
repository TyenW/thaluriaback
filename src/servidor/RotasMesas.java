package servidor;

import economia.*;
import persistencia.*;

import java.util.*;

/**
 * Mesas e linhas do tempo (docs/Sistema de Economia/05): várias mesas, cada uma no seu dia,
 * dividindo o mesmo mundo. O mundo segue a mesa ativa mais à frente; mesa atrasada lê o
 * snapshot do dia dela e age no presente. Rotas sob /api/mestre/mesas.
 *
 * Também concentra o que todas as rotas precisam para respeitar a mesa escolhida: qual mesa
 * veio no pedido, de onde ler preço e estoque, e o que fazer depois que o tempo anda
 * (entregar encomendas, apagar snapshots velhos, gravar tudo).
 */
class RotasMesas {

    private final ServidorThaluriaAPI servidor;
    private final MesaDAO mesaDAO = new MesaDAO();
    private final SnapshotMercadoDAO snapshotDAO = new SnapshotMercadoDAO();
    private final FichaExtrasDAO fichaExtrasDAO = new FichaExtrasDAO();

    RotasMesas(ServidorThaluriaAPI servidor) {
        this.servidor = servidor;
    }

    @FunctionalInterface
    private interface Acao {
        Object executar(Map<String, String> params);
    }

    void registrar() {
        rota("/api/mestre/mesas", p -> painel());
        rota("/api/mestre/mesas/criar", this::criar);
        rota("/api/mestre/mesas/editar", p -> {
            Mesa m = mesa(p, "id");
            String nome = p.getOrDefault("nome", "").trim();
            if (nome.isEmpty() || nome.length() > 60) throw new AdminCrud.ErroValidacao("O nome da mesa vai de 1 a 60 caracteres.");
            m.setNome(nome);
            mesaDAO.salvar(m);
            return painel();
        });
        rota("/api/mestre/mesas/status", this::mudarStatus);
        rota("/api/mestre/mesas/avancar", p -> {
            Mesa m = mesa(p, "id");
            int horas = horasDoPedido(p);
            Map<String, Object> r = avancarMesa(m, horas);
            r.put("painel", painel());
            return r;
        });
        rota("/api/mestre/mesas/simular", p -> {
            Map<String, Object> r = simularMercado(diasDoPedido(p));
            r.put("painel", painel());
            return r;
        });
        rota("/api/mestre/mesas/trocar", this::trocarFicha);
    }

    private void rota(String caminho, Acao acao) {
        servidor.criarRota(caminho, exchange -> {
            Map<String, String> params = servidor.lerQueryOuForm(exchange);
            Object resposta;
            try {
                synchronized (servidor) {
                    resposta = acao.executar(params);
                }
            } catch (AdminCrud.ErroValidacao e) {
                servidor.enviarJson(exchange, 400, "{\"erro\":" + AdminCrud.json(e.getMessage()) + "}");
                return;
            }
            servidor.enviarJson(exchange, 200, AdminCrud.json(resposta));
        });
    }

    // ------------------------------------------------------------------
    // Inicialização
    // ------------------------------------------------------------------

    /**
     * Na subida do servidor: cria a mesa Principal se o banco ainda não tem mesas, entrega as
     * mesas ao motor, liga a foto diária e garante a foto do dia atual (base para quem ficar para trás).
     */
    void iniciar(MotorEconomico motor, int diaCampanha) {
        int hora = diaCampanha == motor.getDiaAtual() ? motor.getHoraAtual() : 8;
        Mesa principal = mesaDAO.migrarMesaPrincipal(diaCampanha, hora);
        if (principal != null) System.out.println("  🎲 Mesa \"Principal\" criada no dia " + principal.getDia() + " com todas as fichas.");
        motor.definirMesas(mesaDAO.listar());
        motor.setAoFecharDia(this::fotografar);
        if (!snapshotDAO.temDia(motor.getDiaAtual())) fotografar(motor, motor.getDiaAtual());
    }

    private void fotografar(MotorEconomico motor, int dia) {
        snapshotDAO.gravar(SnapshotMercado.fotografar(motor, dia));
    }

    // ------------------------------------------------------------------
    // Leitura pela mesa (usada pelas rotas de comércio e conhecimento)
    // ------------------------------------------------------------------

    /** Mesa escolhida no pedido (parâmetro mesaId); null = sem mesa, lê o mundo ao vivo como antes. */
    Mesa mesaDoPedido(Map<String, String> p) {
        String bruto = p.get("mesaId");
        if (bruto == null || bruto.isBlank()) return null;
        return mesa(p, "mesaId");
    }

    /** Dia que vale para as consultas de conhecimento: o da mesa ou, sem mesa, o da campanha (mesa mais à frente). */
    int diaDaConsulta(Mesa m) {
        return m != null ? m.getDia() : servidor.getMotorEconomico().getDiaCampanha();
    }

    /** Preço e estoque da loja como a mesa os vê: ao vivo ou pelo snapshot do dia dela. */
    VisaoMercado visao(Lojas loja, Mesa m) {
        MotorEconomico motor = servidor.getMotorEconomico();
        if (!motor.mesaNoPassado(m)) return VisaoMercado.aoVivo(loja);
        if (!snapshotDAO.temDia(m.getDia())) return new VisaoMercado(loja, null, VisaoMercado.Fonte.PRESENTE_SEM_SNAPSHOT);
        return new VisaoMercado(loja, snapshotDAO.lerLoja(m.getDia(), loja.getId()), VisaoMercado.Fonte.SNAPSHOT);
    }

    /** Unidades e valor de estoque de cada loja no snapshot do dia da mesa; null se a mesa lê ao vivo. */
    Map<Integer, long[]> estoquesDoPassado(Mesa m) {
        if (!servidor.getMotorEconomico().mesaNoPassado(m) || !snapshotDAO.temDia(m.getDia())) return null;
        return snapshotDAO.resumirDia(m.getDia());
    }

    /** Os dois relógios (mundo e mesa) e de onde a mesa está lendo, para a tela avisar. */
    Map<String, Object> relogio(Mesa m) {
        MotorEconomico motor = servidor.getMotorEconomico();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("mundoDia", motor.getDiaAtual());
        r.put("mundoHora", motor.getHoraAtual());
        r.put("diaCampanha", motor.getDiaCampanha());
        if (m == null) {
            r.put("mesa", null);
            r.put("noPassado", false);
            r.put("fonte", VisaoMercado.Fonte.AO_VIVO.name());
            return r;
        }
        boolean passado = motor.mesaNoPassado(m);
        r.put("mesa", resumoMesa(m, motor));
        r.put("noPassado", passado);
        r.put("fonte", !passado ? VisaoMercado.Fonte.AO_VIVO.name()
                : snapshotDAO.temDia(m.getDia()) ? VisaoMercado.Fonte.SNAPSHOT.name() : VisaoMercado.Fonte.PRESENTE_SEM_SNAPSHOT.name());
        return r;
    }

    // ------------------------------------------------------------------
    // O tempo anda
    // ------------------------------------------------------------------

    /**
     * Avança uma mesa e os personagens dela; se ela passar o mundo, o motor simula a diferença.
     * Depois entrega as encomendas vencidas e apaga as fotos que ninguém mais usa.
     */
    Map<String, Object> avancarMesa(Mesa m, int horas) {
        if (!m.isAtiva()) throw new AdminCrud.ErroValidacao("A mesa " + m.getNome() + " está arquivada. Reative-a para avançar o tempo dela.");
        MotorEconomico motor = servidor.getMotorEconomico();
        int mundoAndou = motor.avancarMesa(m, horas);
        mesaDAO.salvar(m);
        mesaDAO.acompanharMesa(m);
        Map<String, Object> r = depoisDoTempo();
        r.put("mesa", resumoMesa(m, motor));
        r.put("mundoAndouHoras", mundoAndou);
        return r;
    }

    /** Aquecer o mercado: só o mundo anda; nenhuma mesa muda de dia e nada se espalha. */
    Map<String, Object> simularMercado(int dias) {
        servidor.getMotorEconomico().avancarTempo(24 * dias);
        Map<String, Object> r = depoisDoTempo();
        r.put("mundoAndouHoras", 24 * dias);
        return r;
    }

    private Map<String, Object> depoisDoTempo() {
        MotorEconomico motor = servidor.getMotorEconomico();
        List<Map<String, Object>> entregas = entregarEncomendas();
        int apagadas = snapshotDAO.apagarAntesDe(motor.getDiaMesaMaisAtrasada());
        servidor.getEconomiaDAO().salvarEstadoCompleto(motor);
        servidor.salvarRelogios();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("mundoDia", motor.getDiaAtual());
        r.put("mundoHora", motor.getHoraAtual());
        r.put("entregas", entregas);
        r.put("snapshotsApagados", apagadas);
        return r;
    }

    /** Leva às fichas as encomendas cujas mesas chegaram ao prazo. */
    List<Map<String, Object>> entregarEncomendas() {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (MotorEconomico.EntregaEncomenda e : servidor.getMotorEconomico().entregarEncomendasVencidas()) {
            String destino = "balcão de " + e.loja().getNome();
            if (e.fichaId() != null) {
                try {
                    fichaExtrasDAO.concederItem(e.fichaId(), e.item().getId(), e.item().getQuantidade());
                    MesaDAO.FichaNaMesa f = mesaDAO.buscarFicha(e.fichaId());
                    destino = f != null ? f.nome() : "ficha #" + e.fichaId();
                } catch (FichaExtrasDAO.ErroValidacao | AdminCrud.ErroValidacao erro) {
                    // Ficha apagada ou item fora do catálogo: a peça fica no balcão para o Mestre decidir
                    e.loja().adicionarItem(e.item());
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mesa", e.mesa().getNome());
            m.put("loja", e.loja().getNome());
            m.put("item", e.item().getNome());
            m.put("quantidade", e.item().getQuantidade());
            m.put("destino", destino);
            lista.add(m);
        }
        return lista;
    }

    // ------------------------------------------------------------------
    // Ações
    // ------------------------------------------------------------------

    private Object criar(Map<String, String> p) {
        MotorEconomico motor = servidor.getMotorEconomico();
        String nome = p.getOrDefault("nome", "").trim();
        if (nome.isEmpty() || nome.length() > 60) throw new AdminCrud.ErroValidacao("O nome da mesa vai de 1 a 60 caracteres.");
        for (Mesa outra : motor.getMesas()) {
            if (outra.getNome().equalsIgnoreCase(nome)) throw new AdminCrud.ErroValidacao("Já existe uma mesa chamada " + outra.getNome() + ".");
        }
        // Mesa nova começa no presente do mundo, a não ser que o Mestre diga outro dia
        int dia = inteiroOu(p, "dia", motor.getDiaAtual());
        int hora = inteiroOu(p, "hora", motor.getHoraAtual());
        if (dia < 1 || hora < 0 || hora > 23) throw new AdminCrud.ErroValidacao("Dia a partir de 1 e hora de 0 a 23.");
        Mesa m = mesaDAO.criar(nome, dia, hora, null);
        motor.registrarMesa(m);
        Map<String, Object> r = new LinkedHashMap<>();
        // Mesa criada à frente do mundo puxa o mercado até ela
        if (m.momento() > motor.momento()) r.putAll(avancarMesa(m, 0));
        else if (motor.mesaNoPassado(m) && !snapshotDAO.temDia(m.getDia())) {
            r.put("aviso", "Não há foto do mercado do dia " + m.getDia() + ": a mesa " + m.getNome() + " vai ler o presente até alcançar o mundo.");
        }
        r.put("id", m.getId());
        r.put("painel", painel());
        return r;
    }

    private Object mudarStatus(Map<String, String> p) {
        MotorEconomico motor = servidor.getMotorEconomico();
        Mesa m = mesa(p, "id");
        Mesa.Status novo;
        try {
            novo = Mesa.Status.valueOf(p.getOrDefault("status", "").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AdminCrud.ErroValidacao("Status inválido: use ATIVA ou ARQUIVADA.");
        }
        Map<String, Object> r = new LinkedHashMap<>();
        if (novo == Mesa.Status.ARQUIVADA) {
            long ativas = motor.getMesas().stream().filter(Mesa::isAtiva).count();
            if (m.isAtiva() && ativas <= 1) throw new AdminCrud.ErroValidacao("Esta é a única mesa ativa. Crie ou reative outra antes de arquivá-la.");
        }
        m.setStatus(novo);
        mesaDAO.salvar(m);
        if (novo == Mesa.Status.ATIVA) {
            if (m.momento() > motor.momento()) {
                r.putAll(avancarMesa(m, 0));
            } else if (motor.mesaNoPassado(m) && !snapshotDAO.temDia(m.getDia())) {
                r.put("aviso", "A mesa " + m.getNome() + " está no dia " + m.getDia() + ", que não tem mais foto do mercado. Ela vai ler o presente até alcançar o mundo.");
            }
        } else {
            // Arquivada não segura mais o histórico
            snapshotDAO.apagarAntesDe(motor.getDiaMesaMaisAtrasada());
        }
        servidor.salvarRelogios();
        r.put("painel", painel());
        return r;
    }

    /**
     * Troca de mesa só para frente: o personagem entra numa mesa cujo relógio é igual ou maior
     * que o dele. Para trás seria viagem no tempo; o Mestre pode forçar, e fica registrado.
     */
    private Object trocarFicha(Map<String, String> p) {
        MotorEconomico motor = servidor.getMotorEconomico();
        long fichaId = Long.parseLong(p.getOrDefault("fichaId", "0").trim());
        MesaDAO.FichaNaMesa f = mesaDAO.buscarFicha(fichaId);
        if (f == null) throw new AdminCrud.ErroValidacao("Ficha não encontrada.");
        boolean forcar = "true".equalsIgnoreCase(p.getOrDefault("forcar", "").trim());
        Mesa destino = mesaDoPedido(p);
        Map<String, Object> r = new LinkedHashMap<>();

        if (destino == null) {
            // Fora de jogo: guarda o relógio e as encomendas continuam na mesa antiga
            mesaDAO.registrarTroca(f, null, false);
            mesaDAO.moverFicha(fichaId, null, f.dia() != null ? f.dia() : motor.getDiaAtual(), f.hora() != null ? f.hora() : 0);
            r.put("painel", painel());
            return r;
        }
        if (Objects.equals(f.mesaId(), destino.getId())) throw new AdminCrud.ErroValidacao(f.nome() + " já está na mesa " + destino.getNome() + ".");
        boolean paraTras = f.dia() != null && !destino.aceitaPersonagemEm(f.momento());
        if (paraTras && !forcar) {
            throw new AdminCrud.ErroValidacao(f.nome() + " já viveu até o dia " + f.dia() + ", " + f.hora() + "h, e a mesa " + destino.getNome()
                    + " está no dia " + destino.getDia() + ", " + destino.getHora() + "h. Entrar nela seria voltar no tempo; marque \"forçar\" se for intencional.");
        }
        mesaDAO.registrarTroca(f, destino, paraTras);
        // O relógio do personagem é o momento mais à frente em que ele já esteve
        boolean manterProprio = paraTras;
        mesaDAO.moverFicha(fichaId, destino.getId(), manterProprio ? f.dia() : destino.getDia(), manterProprio ? f.hora() : destino.getHora());
        motor.moverEncomendasDaFicha(fichaId, destino.getId());
        // O que vencia no intervalo já está pronto na nova mesa
        List<Map<String, Object>> entregas = entregarEncomendas();
        servidor.getEconomiaDAO().salvarEstadoCompleto(motor);
        r.put("forcada", paraTras);
        r.put("entregas", entregas);
        r.put("painel", painel());
        return r;
    }

    private Map<String, Object> painel() {
        MotorEconomico motor = servidor.getMotorEconomico();
        List<MesaDAO.FichaNaMesa> fichas = mesaDAO.listarFichas();
        Map<Integer, Integer> porMesa = new HashMap<>();
        for (MesaDAO.FichaNaMesa f : fichas) if (f.mesaId() != null) porMesa.merge(f.mesaId(), 1, Integer::sum);

        List<Map<String, Object>> mesas = new ArrayList<>();
        for (Mesa m : motor.getMesas()) {
            Map<String, Object> mm = resumoMesa(m, motor);
            mm.put("personagens", porMesa.getOrDefault(m.getId(), 0));
            mm.put("temSnapshot", !motor.mesaNoPassado(m) || snapshotDAO.temDia(m.getDia()));
            mesas.add(mm);
        }
        List<Map<String, Object>> listaFichas = new ArrayList<>();
        for (MesaDAO.FichaNaMesa f : fichas) {
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("id", f.id());
            fm.put("nome", f.nome());
            fm.put("raca", f.raca());
            fm.put("usuarioId", f.usuarioId());
            fm.put("mesaId", f.mesaId());
            fm.put("dia", f.dia());
            fm.put("hora", f.hora());
            listaFichas.add(fm);
        }
        int[] guardados = snapshotDAO.intervalo();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("mundoDia", motor.getDiaAtual());
        r.put("mundoHora", motor.getHoraAtual());
        r.put("diaCampanha", motor.getDiaCampanha());
        r.put("snapshotsDe", guardados[0] > 0 ? guardados[0] : null);
        r.put("snapshotsAte", guardados[1] > 0 ? guardados[1] : null);
        r.put("mesas", mesas);
        r.put("fichas", listaFichas);
        r.put("trocas", mesaDAO.listarTrocas(30));
        return r;
    }

    private static Map<String, Object> resumoMesa(Mesa m, MotorEconomico motor) {
        Map<String, Object> mm = new LinkedHashMap<>();
        mm.put("id", m.getId());
        mm.put("nome", m.getNome());
        mm.put("dia", m.getDia());
        mm.put("hora", m.getHora());
        mm.put("status", m.getStatus().name());
        mm.put("noPassado", motor.mesaNoPassado(m));
        mm.put("diasAtras", Math.max(0, motor.getDiaAtual() - m.getDia()));
        return mm;
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private Mesa mesa(Map<String, String> p, String chave) {
        int id;
        try {
            id = Integer.parseInt(p.getOrDefault(chave, "").trim());
        } catch (NumberFormatException e) {
            throw new AdminCrud.ErroValidacao("Escolha uma mesa.");
        }
        Mesa m = servidor.getMotorEconomico().getMesa(id);
        if (m == null) throw new AdminCrud.ErroValidacao("Mesa não encontrada. Atualize a página.");
        return m;
    }

    /** Horas pedidas: "horas" e/ou "dias" (1 hora a 365 dias). */
    static int horasDoPedido(Map<String, String> p) {
        int horas = inteiroOu(p, "horas", 0) + 24 * inteiroOu(p, "dias", 0);
        if (horas < 1 || horas > 24 * ServidorThaluriaAPI.MAX_DIAS_POR_AVANCO) {
            throw new AdminCrud.ErroValidacao("Avance de 1 hora a " + ServidorThaluriaAPI.MAX_DIAS_POR_AVANCO + " dias.");
        }
        return horas;
    }

    static int diasDoPedido(Map<String, String> p) {
        int dias = inteiroOu(p, "dias", 1);
        if (dias < 1 || dias > ServidorThaluriaAPI.MAX_DIAS_POR_AVANCO) {
            throw new AdminCrud.ErroValidacao("Informe de 1 a " + ServidorThaluriaAPI.MAX_DIAS_POR_AVANCO + " dias.");
        }
        return dias;
    }

    private static int inteiroOu(Map<String, String> p, String chave, int padrao) {
        String v = p.get(chave);
        if (v == null || v.isBlank()) return padrao;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new AdminCrud.ErroValidacao(chave + ": número inteiro inválido.");
        }
    }
}
