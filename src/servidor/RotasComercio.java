package servidor;

import economia.*;
import geografia.Cidade;
import geografia.Conexao;
import persistencia.*;
import personagem.Ficha;

import java.util.*;

/**
 * Aba de Comércio do Mestre: acompanhar as lojas do mundo e agir como mercador
 * (mandar produzir receitas, vender itens a personagens, ajustar caixa e estoque).
 * Todas as rotas ficam sob /api/mestre/, que o porteiro do servidor reserva ao Mestre.
 * Cada ação mexe no motor em memória e grava a loja na hora.
 */
class RotasComercio {

    /** Entidades do gerenciador cuja edição muda as receitas ou o catálogo usados pelo motor. */
    static final Set<String> ENTIDADES_DE_RECEITA = Set.of("receitas", "receita_insumos", "itens");

    private static final int DIAS_DO_RESUMO = 7;
    private static final int LOTES_MAX = 99;

    private final ServidorThaluriaAPI servidor;
    private final ComercioDAO comercioDAO = new ComercioDAO();
    private final FichaDAO fichaDAO = new FichaDAO();
    private final FichaExtrasDAO fichaExtrasDAO = new FichaExtrasDAO();
    private final ItemDAO itemDAO = new ItemDAO();
    private final OrigemItensDAO origemItensDAO = new OrigemItensDAO();
    private final ForjaDAO forjaDAO = new ForjaDAO();
    private final ConhecimentoDAO conhecimentoDAO = new ConhecimentoDAO();

    RotasComercio(ServidorThaluriaAPI servidor) {
        this.servidor = servidor;
    }

    /** Erro de regra com mensagem pronta para a tela (vira HTTP 400). */
    private static class Recusa extends RuntimeException {
        Recusa(String mensagem) { super(mensagem); }
    }

    void atualizarReceitasDoMotor() {
        synchronized (servidor) {
            MotorEconomico motor = servidor.getMotorEconomico();
            motor.definirReceitasECatalogo(comercioDAO.listarReceitas(), itemDAO.listarTodos());
            // Receitas novas ou apagadas mudam o que cada conhecimento cobre
            motor.setRedeConhecimento(conhecimentoDAO.carregarRede(motor));
        }
    }

    void registrar() {
        rota("/api/mestre/comercio/resumo", p -> resumo());
        rota("/api/mestre/comercio/loja", p -> detalheLoja(loja(p)));
        rota("/api/mestre/comercio/produzir", this::produzir);
        rota("/api/mestre/comercio/vender", this::vender);
        rota("/api/mestre/comercio/ajustar", this::ajustar);
        rota("/api/mestre/comercio/caravanas", p -> caravanas());
        rota("/api/mestre/itens/origens", p -> origemItensDAO.auditar());
        rota("/api/mestre/comercio/encomendar-arma", this::encomendarArma);
        rota("/api/mestre/comercio/cancelar", this::cancelar);
    }

    @FunctionalInterface
    private interface Acao {
        Object executar(Map<String, String> params);
    }

    private void rota(String caminho, Acao acao) {
        servidor.criarRota(caminho, exchange -> {
            Map<String, String> params = servidor.lerQueryOuForm(exchange);
            Object resposta;
            try {
                synchronized (servidor) {
                    resposta = acao.executar(params);
                }
            } catch (Recusa | NumberFormatException e) {
                String msg = e instanceof NumberFormatException ? "Valor numérico inválido." : e.getMessage();
                servidor.enviarJson(exchange, 400, "{\"erro\":" + AdminCrud.json(msg) + "}");
                return;
            }
            servidor.enviarJson(exchange, 200, AdminCrud.json(resposta));
        });
    }

    // ------------------------------------------------------------------
    // Leitura
    // ------------------------------------------------------------------

    private Map<String, Object> resumo() {
        MotorEconomico motor = servidor.getMotorEconomico();
        int dia = motor.getDiaAtual();
        Map<Integer, long[]> semana = comercioDAO.resumirDesde(dia - DIAS_DO_RESUMO + 1);

        List<Map<String, Object>> lojas = new ArrayList<>();
        long caixaTotal = 0, estoqueTotal = 0;
        int ordens = 0, emAlerta = 0;
        for (Cidade c : motor.getCidades()) {
            for (Lojas l : c.getLojas()) {
                Map<String, Object> m = resumoLoja(l, semana.getOrDefault(l.getId(), new long[3]), motor);
                lojas.add(m);
                caixaTotal += l.getCaixa().getValorTotalEmLatao();
                estoqueTotal += (Long) m.get("valorEstoqueLatao");
                ordens += l.getOrdensReceita().size();
                if (!((List<?>) m.get("alertas")).isEmpty()) emAlerta++;
            }
        }
        Map<String, Object> totais = new LinkedHashMap<>();
        totais.put("lojas", lojas.size());
        totais.put("caixaLatao", caixaTotal);
        totais.put("valorEstoqueLatao", estoqueTotal);
        totais.put("ordensEmAndamento", ordens);
        totais.put("lojasEmAlerta", emAlerta);
        totais.put("caravanasEmViagem", motor.getCaravanasAtivas().size());

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("dia", dia);
        r.put("diasDoResumo", DIAS_DO_RESUMO);
        r.put("totais", totais);
        r.put("lojas", lojas);
        return r;
    }

    private Map<String, Object> resumoLoja(Lojas l, long[] semana, MotorEconomico motor) {
        int unidades = 0;
        long valorEstoque = 0;
        for (Item i : l.getEstoque()) {
            unidades += i.getQuantidade();
            valorEstoque += i.getPrecoEmLatao() * i.getQuantidade();
        }
        long caixa = l.getCaixa().getValorTotalEmLatao();
        int cidadeId = l.getCidadeSede() != null ? l.getCidadeSede().getId() : -1;
        boolean conheceReceitas = motor.getReceitas().stream().anyMatch(r -> r.tipoLoja() != null && !r.somenteEncomenda() && r.conhecidaPor(l)
                && motor.getRedeConhecimento().receitaLiberada(r.id(), cidadeId, motor.getDiaAtual()));

        List<String> alertas = new ArrayList<>();
        if (caixa < Lojas.RESERVA_CAIXA_LATAO * 2) alertas.add("Caixa baixo");
        if (unidades == 0) alertas.add("Prateleiras vazias");
        if (semana[1] > 0 && semana[1] > semana[0] * 1.2) alertas.add("Gastando mais do que vende");
        if (conheceReceitas && l.getOrdensReceita().isEmpty()) alertas.add("Oficina parada");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("nome", l.getNome());
        m.put("mercador", l.getMercador());
        m.put("tipo", l.getTipoEstabelecimento().name());
        m.put("tipoRotulo", l.getTipoEstabelecimento().getNomeFormatado());
        m.put("cidade", l.getCidadeSede() != null ? l.getCidadeSede().getNome() : null);
        m.put("cidadeId", l.getCidadeSede() != null ? l.getCidadeSede().getId() : null);
        m.put("nivel", l.getNivelLoja());
        m.put("rede", l.isLojaDeRede() ? l.getNomeDaRede() : null);
        m.put("caixaLatao", caixa);
        m.put("unidades", unidades);
        m.put("valorEstoqueLatao", valorEstoque);
        m.put("ordens", l.getOrdensReceita().size());
        m.put("entradasSemanaLatao", semana[0]);
        m.put("saidasSemanaLatao", semana[1]);
        m.put("unidadesVendidasSemana", semana[2]);
        m.put("alertas", alertas);
        return m;
    }

    private Map<String, Object> detalheLoja(Lojas l) {
        MotorEconomico motor = servidor.getMotorEconomico();
        Map<Integer, Item> catalogo = motor.getCatalogoItens();
        int dia = motor.getDiaAtual();

        // Estoque e mercadorias conhecidas (inclusive as zeradas, que a loja ainda procura)
        Map<Integer, Item> mercadorias = new LinkedHashMap<>(l.getCatalogoConhecido());
        for (Item i : l.getEstoque()) mercadorias.putIfAbsent(i.getId(), i);
        // Peças prontas de encomendas aparecem na prateleira, mas reservadas (não vendem)
        Map<Integer, Integer> reservados = l.getReservados();
        for (Integer id : reservados.keySet()) if (catalogo.containsKey(id)) mercadorias.putIfAbsent(id, catalogo.get(id));
        Map<Integer, String> imagens = comercioDAO.imagensDosItens();
        Map<Integer, Map<String, Object>> armas = comercioDAO.armasDaForja();
        List<Map<String, Object>> estoque = new ArrayList<>();
        for (Item i : mercadorias.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemId", i.getId());
            m.put("nome", i.getNome());
            m.put("imagemUrl", imagens.get(i.getId()));
            m.put("descricao", i.getDescricao());
            m.put("tipo", i.getTipo().name());
            m.put("raridade", i.getRaridade().name());
            m.put("quantidade", l.getQuantidadeEstoque(i.getId()));
            m.put("demanda", l.getDemandaPorItem().getOrDefault(i.getId(), 0));
            m.put("precoBaseLatao", i.getPrecoEmLatao());
            m.put("precoVendaLatao", l.calcularPrecoDinamicoVendaEmLatao(i));
            m.put("precoCompraLatao", l.calcularPrecoCompraNpcEmLatao(i));
            m.put("reservado", reservados.getOrDefault(i.getId(), 0));
            RedeConhecimento.Estagio estagio = l.getCidadeSede() == null ? null
                    : motor.getRedeConhecimento().estagioDoItem(i.getId(), l.getCidadeSede().getId(), dia);
            m.put("estagioConhecimento", estagio != null ? estagio.getRotulo() : null);
            m.put("arma", armas.get(i.getId()));
            estoque.add(m);
        }
        estoque.sort(Comparator.comparing(m -> String.valueOf(m.get("nome"))));

        // Receitas: as da especialidade primeiro, com a conta que a IA faz
        List<Map<String, Object>> receitas = new ArrayList<>();
        Set<Integer> internas = comercioDAO.receitasInternasDaForja();
        for (ReceitaLoja r : motor.getReceitas()) {
            if (internas.contains(r.id())) continue; // subcrafts e encomendas avulsas: aparecem só dentro das ordens
            IALojistaDeterministica.AvaliacaoReceita a = IALojistaDeterministica.avaliar(l, r, catalogo);
            Item resultado = catalogo.get(r.itemResultadoId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.id());
            m.put("nome", r.nome());
            m.put("conhecida", r.conhecidaPor(l));
            // Receita regional que ainda não chegou à Produção nesta cidade: a IA não a faz (o Mestre ainda pode mandar)
            m.put("regional", motor.getRedeConhecimento().receitaRegional(r.id()));
            m.put("liberadaAqui", l.getCidadeSede() == null || motor.getRedeConhecimento().receitaLiberada(r.id(), l.getCidadeSede().getId(), dia));
            m.put("especialidade", r.tipoLoja() != null ? r.tipoLoja().getNomeFormatado() : "Qualquer loja");
            m.put("resultadoId", r.itemResultadoId());
            m.put("resultadoNome", resultado != null ? resultado.getNome() : "Item apagado (#" + r.itemResultadoId() + ")");
            m.put("quantidadeResultado", r.quantidadeResultado());
            m.put("dias", r.diasProducao());
            m.put("somenteEncomenda", r.somenteEncomenda());
            m.put("etapas", r.etapas().stream().map(ReceitaLoja.Etapa::nomeParte).toList());
            List<Map<String, Object>> insumos = new ArrayList<>();
            for (ReceitaLoja.Insumo ins : r.insumos()) {
                Item it = catalogo.get(ins.itemId());
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("itemId", ins.itemId());
                im.put("nome", it != null ? it.getNome() : "Item #" + ins.itemId());
                im.put("quantidade", ins.quantidade());
                im.put("emEstoque", l.getQuantidadeEstoque(ins.itemId()));
                insumos.add(im);
            }
            m.put("insumos", insumos);
            m.put("receitaEsperadaLatao", a.receitaEsperada());
            m.put("custoInsumosLatao", a.custoInsumos());
            m.put("maoDeObraLatao", a.maoDeObra());
            m.put("lucroLatao", a.lucroEsperado());
            m.put("temInsumos", a.insumosFaltantes().isEmpty());
            m.put("estoqueCheio", a.estoqueCheio());
            receitas.add(m);
        }
        receitas.sort(Comparator.comparing((Map<String, Object> m) -> !(Boolean) m.get("conhecida"))
                .thenComparing(m -> -((Number) m.get("lucroLatao")).longValue()));

        List<Map<String, Object>> ordens = new ArrayList<>();
        int indice = 0;
        for (OrdemReceita o : l.getOrdensReceita()) {
            ReceitaLoja r = motor.getReceitasPorId().get(o.getReceitaId());
            Item resultado = r != null ? catalogo.get(r.itemResultadoId()) : null;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("receitaId", o.getReceitaId());
            m.put("receitaNome", r != null ? r.nome() : "Receita apagada");
            m.put("resultadoNome", resultado != null ? resultado.getNome() : "?");
            m.put("quantidade", r != null ? r.quantidadeResultado() * o.getLotes() : 0);
            m.put("lotes", o.getLotes());
            m.put("diaInicio", o.getDiaInicio());
            m.put("diaConclusao", o.getDiaConclusao());
            m.put("origem", o.getOrigem().name());
            m.put("indice", indice++);
            m.put("insumosPorConta", o.isInsumosPorConta());
            List<Map<String, Object>> etapas = new ArrayList<>();
            for (int i = 0; i < o.getEtapas().size(); i++) {
                OrdemReceita.EtapaOrdem e = o.getEtapas().get(i);
                Item peca = catalogo.get(e.getComponenteItemId());
                Map<String, Object> em = new LinkedHashMap<>();
                em.put("parte", r != null && i < r.etapas().size() ? r.etapas().get(i).nomeParte() : "Peça");
                em.put("componente", peca != null ? peca.getNome() : "Peça #" + e.getComponenteItemId());
                em.put("dia", e.getDiaConclusao());
                em.put("estado", e.getEstado().name());
                etapas.add(em);
            }
            m.put("etapas", etapas);
            ordens.add(m);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("dia", dia);
        r.put("loja", resumoLoja(l, comercioDAO.resumirDesde(dia - DIAS_DO_RESUMO + 1).getOrDefault(l.getId(), new long[3]), motor));
        r.put("estoque", estoque);
        r.put("receitas", receitas);
        r.put("ordens", ordens);
        r.put("ordensMax", l.getOrdensSimultaneasMax());
        r.put("movimentacoes", comercioDAO.listarMovimentacoes(l.getId(), 80));
        return r;
    }

    /** Caravanas ativas com a posição no caminho, para desenhar no mapa. */
    private Map<String, Object> caravanas() {
        MotorEconomico motor = servidor.getMotorEconomico();
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Caravana c : motor.getCaravanasAtivas()) {
            RotaComercial rota = c.getRota();
            List<String> caminho = new ArrayList<>();
            for (Cidade cidade : rota.getCaminhoCidades()) caminho.add(cidade.getNome());
            List<Map<String, Object>> trechos = new ArrayList<>();
            for (Conexao a : rota.getArestas()) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("tipo", a.getTipo().name());
                t.put("km", a.getDistanciaKm());
                t.put("perigo", a.getPerigo());
                trechos.add(t);
            }
            int indice = Math.min(c.getIndiceArestaAtual(), Math.max(0, trechos.size() - 1));
            Conexao trecho = c.getArestaAtual();
            double fracao = trecho == null ? 0 : Math.min(1.0, c.getKmNaArestaAtual() / Math.max(1, trecho.getDistanciaKm()));

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("status", c.getStatus().name());
            m.put("statusDescricao", c.getStatus().getDescricao());
            m.put("protecao", c.getProtecao().name());
            m.put("protecaoDescricao", c.getProtecao().getDescricao());
            m.put("itemId", c.getCarga().getId());
            m.put("carga", c.getCarga().getNome());
            m.put("quantidade", c.getCarga().getQuantidade());
            m.put("valorCargaLatao", c.getCarga().getValorTotalEmLatao());
            m.put("lojaOrigem", c.getLojaOrigem().getNome());
            m.put("lojaOrigemId", c.getLojaOrigem().getId());
            m.put("lojaDestino", c.getLojaDestino().getNome());
            m.put("lojaDestinoId", c.getLojaDestino().getId());
            m.put("caminho", caminho);
            m.put("trechos", trechos);
            m.put("indiceTrecho", indice);
            m.put("fracaoNoTrecho", fracao);
            m.put("kmPercorridos", Math.round(c.kmPercorridos()));
            m.put("kmTotal", rota.getDistanciaTotalKm());
            m.put("horasRestantes", Math.round(c.horasRestantesEstimadas()));
            MissaoComercial missao = motor.missaoAbertaDa(c);
            if (missao != null) {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("tipo", missao.getTipo().getDescricao());
                mm.put("status", missao.getStatus().getDescricao());
                mm.put("recompensaLatao", missao.getRecompensaEmLatao());
                mm.put("horasRestantes", missao.getHorasRestantesPrazo());
                mm.put("cidade", missao.getCidadeDisponivel() != null ? missao.getCidadeDisponivel().getNome() : null);
                m.put("missao", mm);
            }
            lista.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("dia", motor.getDiaAtual());
        r.put("hora", motor.getHoraAtual());
        r.put("caravanas", lista);
        return r;
    }

    // ------------------------------------------------------------------
    // O Mestre como mercador
    // ------------------------------------------------------------------

    private Object produzir(Map<String, String> p) {
        MotorEconomico motor = servidor.getMotorEconomico();
        Lojas l = loja(p);
        ReceitaLoja receita = motor.getReceitasPorId().get(inteiro(p, "receitaId"));
        if (receita == null) throw new Recusa("Receita não encontrada.");
        Item resultado = motor.getCatalogoItens().get(receita.itemResultadoId());
        if (resultado == null) throw new Recusa("O item que esta receita produz foi apagado do catálogo.");
        int lotes = inteiro(p, "lotes");
        if (lotes < 1 || lotes > LOTES_MAX) throw new Recusa("Os lotes vão de 1 a " + LOTES_MAX + ".");
        boolean porConta = booleano(p, "insumosPorConta");
        boolean imediato = booleano(p, "imediato");
        int dia = motor.getDiaAtual(), hora = motor.getHoraAtual();

        if (!porConta) {
            Map<Integer, Integer> faltam = l.insumosFaltantes(receita, lotes);
            if (!faltam.isEmpty()) {
                List<String> nomes = new ArrayList<>();
                faltam.forEach((id, qtd) -> {
                    Item it = motor.getCatalogoItens().get(id);
                    nomes.add(qtd + "× " + (it != null ? it.getNome() : "item #" + id));
                });
                throw new Recusa("Faltam insumos no estoque: " + String.join(", ", nomes) + ". Abasteça a loja ou marque \"O Mestre fornece os insumos\".");
            }
            if (!l.getCaixa().temSaldoEmLatao(receita.custoMaoDeObraEmLatao(resultado, lotes))) {
                throw new Recusa("A loja não tem caixa para a mão de obra (" + Carteira.formatarLatao(receita.custoMaoDeObraEmLatao(resultado, lotes)) + ").");
            }
        }
        OrdemReceita ordem = l.iniciarReceita(receita, resultado, lotes, dia, hora, OrdemReceita.Origem.MESTRE, porConta);
        if (ordem == null) throw new Recusa("A oficina não conseguiu abrir a ordem.");
        if (imediato) l.concluirImediatamente(ordem, dia, hora, motor.getReceitasPorId(), motor.getCatalogoItens());
        servidor.getEconomiaDAO().salvarLoja(l);
        return Map.of("status", imediato ? "PRONTO" : "EM_PRODUCAO", "detalhe", detalheLoja(l));
    }

    private Object vender(Map<String, String> p) {
        MotorEconomico motor = servidor.getMotorEconomico();
        Lojas l = loja(p);
        int itemId = inteiro(p, "itemId");
        long fichaId = inteiro(p, "fichaId");
        int qtd = inteiro(p, "quantidade");
        long precoUnitario = Long.parseLong(p.getOrDefault("precoUnitarioLatao", "0").trim());
        boolean cobrar = booleano(p, "cobrar");
        if (qtd < 1) throw new Recusa("A quantidade deve ser positiva.");
        if (precoUnitario < 0) throw new Recusa("O preço não pode ser negativo.");
        if (l.getQuantidadeEstoque(itemId) < qtd) throw new Recusa("A loja só tem " + l.getQuantidadeEstoque(itemId) + " unidade(s) desse item.");
        if (!comercioDAO.itemExiste(itemId)) throw new Recusa("Este item não está no catálogo e não pode ir para uma ficha.");

        Ficha ficha = fichaDAO.buscarPorId(fichaId);
        if (ficha == null) throw new Recusa("Ficha não encontrada.");
        long total = precoUnitario * qtd;
        if (cobrar && total > 0) {
            if (!ficha.getCarteira().pagarLatao(total)) {
                throw new Recusa(ficha.getNome() + " não tem " + Carteira.formatarLatao(total) + " (tem " + Carteira.formatarLatao(ficha.getCarteira().getValorTotalEmLatao()) + ").");
            }
            // Grava a carteira antes de conceder: salvar a ficha regrava o inventário que foi carregado
            fichaDAO.salvar(ficha);
        }
        fichaExtrasDAO.concederItem(fichaId, itemId, qtd);

        Item vendido = l.buscarItemPorId(itemId);
        String nomeItem = vendido != null ? vendido.getNome() : "Item #" + itemId;
        l.removerUnidades(itemId, qtd);
        long recebido = cobrar ? total : 0L;
        if (recebido > 0) l.getCaixa().receberLatao(recebido);
        l.registrarMovimentacao(motor.getDiaAtual(), motor.getHoraAtual(), RegistroMovimentacao.TipoOperacao.VENDA_MESTRE,
                nomeItem, qtd, recebido, (cobrar ? "Vendido a " : "Entregue sem cobrança a ") + ficha.getNome());
        servidor.getEconomiaDAO().salvarLoja(l);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "VENDIDO");
        r.put("totalLatao", recebido);
        r.put("saldoFichaLatao", ficha.getCarteira().getValorTotalEmLatao());
        r.put("detalhe", detalheLoja(l));
        return r;
    }

    /**
     * Encomenda personalizada: o Mestre escolhe o material de cada parte (como um jogador pedindo
     * uma arma sob medida). A composição vira uma receita "só por encomenda" e a ordem é aberta.
     */
    private Object encomendarArma(Map<String, String> p) {
        MotorEconomico motor = servidor.getMotorEconomico();
        Lojas l = loja(p);
        if (l.getCidadeSede() != null) {
            RedeConhecimento.Estagio estagio = motor.getRedeConhecimento().estagioDoTipo(inteiro(p, "tipoArmaId"), l.getCidadeSede().getId(), motor.getDiaAtual());
            if (estagio != null && estagio != RedeConhecimento.Estagio.PRODUCAO) {
                throw new Recusa("Os ferreiros de " + l.getCidadeSede().getNome() + " ainda não sabem fazer este tipo de arma (estágio: "
                        + estagio.getRotulo() + "). Adiante a cidade em Disseminação se quiser forjar mesmo assim.");
            }
        }
        int receitaId;
        try {
            receitaId = forjaDAO.compilarEncomenda(inteiro(p, "tipoArmaId"), RotasForja.escolhas(p.get("materiais")));
        } catch (AdminCrud.ErroValidacao e) {
            throw new Recusa(e.getMessage());
        }
        atualizarReceitasDoMotor();
        Map<String, String> ordem = new HashMap<>(p);
        ordem.put("receitaId", String.valueOf(receitaId));
        ordem.putIfAbsent("lotes", "1");
        return produzir(ordem);
    }

    private Object cancelar(Map<String, String> p) {
        MotorEconomico motor = servidor.getMotorEconomico();
        Lojas l = loja(p);
        int indice = inteiro(p, "indice");
        if (indice < 0 || indice >= l.getOrdensReceita().size()) throw new Recusa("Ordem não encontrada. Atualize a página.");
        OrdemReceita ordem = l.getOrdensReceita().get(indice);
        l.cancelarOrdem(ordem, motor.getDiaAtual(), motor.getHoraAtual(), motor.getReceitasPorId(), motor.getCatalogoItens());
        servidor.getEconomiaDAO().salvarLoja(l);
        return Map.of("status", "CANCELADA", "detalhe", detalheLoja(l));
    }

    private Object ajustar(Map<String, String> p) {
        MotorEconomico motor = servidor.getMotorEconomico();
        Lojas l = loja(p);
        int dia = motor.getDiaAtual(), hora = motor.getHoraAtual();
        String acao = p.getOrDefault("acao", "").trim().toUpperCase(Locale.ROOT);
        switch (acao) {
            case "CAIXA" -> {
                long valor = Long.parseLong(p.getOrDefault("valorLatao", "").trim());
                if (valor < 0) throw new Recusa("O caixa não pode ser negativo.");
                l.definirCaixa(valor, dia, hora);
            }
            case "NIVEL" -> {
                int nivel = inteiro(p, "nivel");
                if (nivel < 1 || nivel > 5) throw new Recusa("O nível vai de 1 a 5.");
                l.setNivelLoja(nivel);
            }
            case "ESTOQUE" -> {
                Item modelo = motor.getCatalogoItens().get(inteiro(p, "itemId"));
                if (modelo == null) throw new Recusa("Item não encontrado no catálogo.");
                int delta = inteiro(p, "delta");
                if (delta == 0 || Math.abs(delta) > 99999) throw new Recusa("Informe quanto abastecer (positivo) ou retirar (negativo).");
                l.ajustarEstoque(modelo, delta, dia, hora);
            }
            case "DEMANDA" -> {
                Item modelo = motor.getCatalogoItens().get(inteiro(p, "itemId"));
                if (modelo == null) throw new Recusa("Item não encontrado no catálogo.");
                int demanda = inteiro(p, "demanda");
                if (demanda < 0 || demanda > 999) throw new Recusa("A demanda vai de 0 a 999.");
                l.definirDemanda(modelo, demanda);
            }
            default -> throw new Recusa("Ação desconhecida: " + acao);
        }
        servidor.getEconomiaDAO().salvarLoja(l);
        return Map.of("status", "AJUSTADO", "detalhe", detalheLoja(l));
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private Lojas loja(Map<String, String> p) {
        Lojas l = servidor.getMotorEconomico().buscarLojaPorId(inteiro(p, "lojaId", "id"));
        if (l == null) throw new Recusa("Loja não encontrada. Ela pode ter sido excluída.");
        return l;
    }

    private static int inteiro(Map<String, String> p, String... chaves) {
        for (String c : chaves) {
            String v = p.get(c);
            if (v != null && !v.isBlank()) return Integer.parseInt(v.trim());
        }
        throw new Recusa("Parâmetro obrigatório ausente: " + chaves[0] + ".");
    }

    private static boolean booleano(Map<String, String> p, String chave) {
        String v = p.getOrDefault(chave, "").trim();
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "sim".equalsIgnoreCase(v);
    }
}
