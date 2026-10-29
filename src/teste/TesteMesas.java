package teste;

import economia.*;
import economia.RedeConhecimento.Conhecimento;
import economia.RedeConhecimento.Estagio;
import economia.RedeConhecimento.TipoConhecimento;
import geografia.*;
import persistencia.BancoDados;
import persistencia.ComercioDAO;
import persistencia.MesaDAO;
import persistencia.SnapshotMercadoDAO;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;

/**
 * Mesas e linhas do tempo (docs/Sistema de Economia/05, seção 8): o mundo segue a mesa mais
 * à frente, mesa atrasada lê o snapshot e age no presente, encomenda com prazo da mesa,
 * troca de mesa só para frente, retenção de snapshots e aquecimento do mercado.
 *
 * Usa um banco próprio (thaluria_teste_mesas.db); o thaluria.db não é tocado.
 */
public class TesteMesas {

    private static final String BANCO_TESTE = "thaluria_teste_mesas.db";
    private static int total = 0;
    private static int passaram = 0;

    private static final Item ESPADA = new Item(52, "Espada de Ferro", "", 0.5, 2.2, Item.TipoItem.ARMA_BRANCA, Item.RaridadeItem.COMUM, 1);
    /** Só por encomenda: a IA do mercador não a produz sozinha. 120 h = 5 dias. */
    private static final ReceitaLoja RECEITA = new ReceitaLoja(8, "Espada de Ferro", 52, 1, 120, Lojas.TipoEstabelecimento.FORJA, List.of(), List.of(), true);

    public static void main(String[] args) throws Exception {
        System.out.println("=== MESAS E LINHAS DO TEMPO ===\n");
        new File(BANCO_TESTE).delete();
        BancoDados.setUrlBanco("jdbc:sqlite:" + BANCO_TESTE);
        try {
            BancoDados.inicializarEsquema();
            testarLeituraDoPassado();
            testarMundoSegueMesaDaFrente();
            testarEncomendaDaMesaAtrasada();
            testarTrocaDeMesa();
            testarRetencao();
            testarAquecerMercado();
            testarPersistenciaDaOrdem();
        } finally {
            BancoDados.setUrlBanco("jdbc:sqlite:thaluria.db");
            new File(BANCO_TESTE).delete();
            new File(BANCO_TESTE + "-wal").delete();
            new File(BANCO_TESTE + "-shm").delete();
        }
        System.out.printf("%n%d/%d testes passaram.%n", passaram, total);
        if (passaram != total) System.exit(1);
    }

    private static void afirme(boolean condicao, String mensagem) {
        total++;
        if (condicao) {
            passaram++;
            System.out.println("  [OK] " + mensagem);
        } else {
            System.err.println("  [FALHOU] " + mensagem);
            throw new AssertionError("Falha: " + mensagem);
        }
    }

    private static int proximaCidade = 8000;

    private static Cidade cidade(String nome) {
        return new Cidade(proximaCidade++, nome, Cidade.TipoAssentamento.CIDADE, Regiao.TEMPERADO, Geologia.PLANICIE, Aquifero.RIO, false, false, null);
    }

    /** Mundo de uma cidade e uma forja, no dia 10 às 8h. */
    private static MotorEconomico mundo(Lojas[] forjaSaida) {
        Cidade c = cidade("Brasa");
        Lojas forja = new Lojas("Forja do Teste", "Brom", c, false, null, Lojas.TipoEstabelecimento.FORJA);
        forja.setId(4242);
        forja.setCaixa(Carteira.deLatao(10_000_000));
        MotorEconomico motor = new MotorEconomico(new Random(7));
        motor.registrarCidade(c);
        motor.definirReceitasECatalogo(List.of(RECEITA), List.of(ESPADA));
        motor.setDiaAtual(10);
        motor.setHoraAtual(8);
        forjaSaida[0] = forja;
        return motor;
    }

    // ------------------------------------------------------------------

    private static void testarLeituraDoPassado() {
        System.out.println("-- Mesa atrasada lê o snapshot e compra no presente");
        Lojas[] f = new Lojas[1];
        MotorEconomico motor = mundo(f);
        Lojas forja = f[0];
        forja.adicionarItem(Lojas.clonarItem(ESPADA, 3));
        forja.definirDemanda(ESPADA, 5);
        SnapshotMercadoDAO dao = new SnapshotMercadoDAO();
        dao.gravar(SnapshotMercado.fotografar(motor, 8));
        long precoDia8 = forja.calcularPrecoDinamicoVendaEmLatao(ESPADA);

        // No dia 10 a loja encheu de espadas: ao vivo o preço caiu
        forja.adicionarItem(Lojas.clonarItem(ESPADA, 30));
        long precoHoje = forja.calcularPrecoDinamicoVendaEmLatao(ESPADA);
        afirme(precoHoje < precoDia8, "Com mais estoque, o preço ao vivo cai (" + precoDia8 + " → " + precoHoje + ")");

        VisaoMercado norte = new VisaoMercado(forja, dao.lerLoja(8, forja.getId()), VisaoMercado.Fonte.SNAPSHOT);
        afirme(norte.lePassado() && norte.precoVenda(ESPADA) == precoDia8, "Mesa no dia 8 vê o preço do snapshot do dia 8, não o ao vivo");
        afirme(norte.quantidade(ESPADA) == 3 && norte.demanda(ESPADA) == 5, "E o estoque e a procura do dia 8");
        afirme(VisaoMercado.aoVivo(forja).precoVenda(ESPADA) == precoHoje, "Mesa no dia do mundo vê o motor ao vivo");
        afirme(norte.disponivelParaCompra(ESPADA) == 3, "Disponível = min(snapshot 3, hoje 33) = 3");

        // A mesa Sul (no dia 10) levou quase tudo
        forja.removerUnidades(ESPADA.getId(), 32);
        afirme(norte.disponivelParaCompra(ESPADA) == 1, "Sobrou 1 hoje: a Norte só leva 1, mesmo vendo 3 na foto");
        forja.removerUnidades(ESPADA.getId(), 1);
        afirme(norte.disponivelParaCompra(ESPADA) == 0 && norte.quantidade(ESPADA) == 3,
                "A Sul levou tudo: a foto mostra 3, mas a Norte vê esgotado (paradoxo aceito)");

        // Venda da mesa atrasada: entra no estoque de agora, paga na hora
        long caixaAntes = forja.getCaixa().getValorTotalEmLatao();
        long precoCompraDia8 = norte.precoCompra(ESPADA);
        afirme(forja.comprarDeAventureiro(ESPADA, 2, precoCompraDia8 * 2, motor.getDiaAtual(), 8, "[Mesa Norte · dia 8] Comprado de Teste"),
                "A loja compra 2 espadas do personagem pelo preço do dia 8");
        afirme(forja.getQuantidadeEstoque(ESPADA.getId()) == 2 && forja.getCaixa().getValorTotalEmLatao() == caixaAntes - precoCompraDia8 * 2,
                "O item entra no estoque atual e o dinheiro sai do caixa atual");
        RegistroMovimentacao ultimo = forja.getHistoricoNaoPersistido().get(forja.getHistoricoNaoPersistido().size() - 1);
        afirme(ultimo.getDiaMundo() == 10 && ultimo.getObservacao().startsWith("[Mesa Norte · dia 8]"),
                "O livro-caixa grava o dia do mundo (10) e a origem [Mesa Norte · dia 8]");

        afirme(new VisaoMercado(forja, null, VisaoMercado.Fonte.PRESENTE_SEM_SNAPSHOT).precoVenda(ESPADA) == forja.calcularPrecoDinamicoVendaEmLatao(ESPADA),
                "Sem snapshot do dia (mesa reativada), a mesa lê o presente");
    }

    private static void testarMundoSegueMesaDaFrente() {
        System.out.println("-- O mundo segue a mesa ativa mais à frente");
        Lojas[] f = new Lojas[1];
        MotorEconomico motor = mundo(f);
        Mesa norte = new Mesa(1, "Norte", 8, 8, Mesa.Status.ATIVA, null);
        Mesa sul = new Mesa(2, "Sul", 10, 8, Mesa.Status.ATIVA, null);
        motor.definirMesas(List.of(norte, sul));
        int[] ciclos = { 0 };
        motor.setAoFecharDia((m, dia) -> ciclos[0]++);

        afirme(motor.getDiaCampanha() == 10, "O dia da campanha do motor é o da mesa mais à frente (Sul, dia 10)");
        afirme(motor.mesaNoPassado(norte) && !motor.mesaNoPassado(sul), "Norte (dia 8) lê o passado; Sul (dia 10) lê ao vivo");

        int andou = motor.avancarMesa(sul, 24);
        afirme(andou == 24 && ciclos[0] == 1 && motor.getDiaAtual() == 11, "Sul avança um dia: o mundo vai junto (1 ciclo diário)");
        andou = motor.avancarMesa(norte, 24 * 2);
        afirme(andou == 0 && ciclos[0] == 1 && motor.getDiaAtual() == 11 && norte.getDia() == 10,
                "Norte avança 2 dias mas continua atrás: o mundo não anda");
        andou = motor.avancarMesa(norte, 24 * 3);
        afirme(andou == 48 && ciclos[0] == 3 && motor.getDiaAtual() == 13 && motor.getHoraAtual() == 8,
                "Norte passa o mundo (dia 13): o motor simula só a diferença (2 dias)");
        afirme(motor.getDiaCampanha() == 13 && motor.mesaNoPassado(sul), "Agora a Norte é a da frente e a Sul ficou no passado");

        Mesa parada = new Mesa(3, "Parada", 20, 8, Mesa.Status.ARQUIVADA, null);
        motor.registrarMesa(parada);
        afirme(motor.getDiaCampanha() == 13 && motor.avancarMesa(parada, 24) == 0, "Mesa arquivada não conta nem puxa o mundo");
    }

    private static void testarEncomendaDaMesaAtrasada() {
        System.out.println("-- Encomenda da mesa atrasada");
        Lojas[] f = new Lojas[1];
        MotorEconomico motor = mundo(f);
        Lojas forja = f[0];
        Mesa norte = new Mesa(1, "Norte", 8, 8, Mesa.Status.ATIVA, null);
        Mesa sul = new Mesa(2, "Sul", 10, 8, Mesa.Status.ATIVA, null);
        motor.definirMesas(List.of(norte, sul));

        // A Norte encomenda no dia 8 uma arma de 5 dias: a ordem entra na fila agora (mundo no dia 10)
        OrdemReceita ordem = forja.iniciarReceita(RECEITA, ESPADA, 1, motor.getDiaAtual(), 8, OrdemReceita.Origem.MESTRE, true, norte.rotulo());
        ordem.vincularMesa(norte.getId(), norte.getDia() + (ordem.getDiaConclusao() - motor.getDiaAtual()), 77L, false);
        afirme(ordem.getDiaConclusao() == 15 && ordem.getDiaConclusaoMesa() == 13, "Prazo da loja: dia 15 do mundo; prazo do jogador: dia 13 da mesa");
        afirme(forja.getOrdensEmProducao() == 1, "A encomenda ocupa a oficina normalmente");

        motor.avancarMesa(norte, 24 * 4);
        afirme(norte.getDia() == 12 && motor.entregarEncomendasVencidas().isEmpty(), "Norte no dia 12: ainda não venceu");
        motor.avancarMesa(norte, 24);
        List<MotorEconomico.EntregaEncomenda> entregas = motor.entregarEncomendasVencidas();
        afirme(motor.getDiaAtual() == 13 && entregas.size() == 1, "Norte chega ao dia 13 (mundo no 13 < 15): a arma é entregue assim mesmo");
        afirme(entregas.get(0).fichaId() == 77L && entregas.get(0).item().getId() == ESPADA.getId() && forja.getOrdensReceita().isEmpty(),
                "Vai para a ficha do personagem e a ordem sai da oficina (concluída antes)");

        // A loja termina antes de a mesa chegar ao prazo: o resultado fica reservado
        Mesa lenta = new Mesa(3, "Lenta", 3, 8, Mesa.Status.ATIVA, null);
        motor.registrarMesa(lenta);
        OrdemReceita outra = forja.iniciarReceita(RECEITA, ESPADA, 1, motor.getDiaAtual(), 8, OrdemReceita.Origem.MESTRE, true, lenta.rotulo());
        outra.vincularMesa(lenta.getId(), lenta.getDia() + (outra.getDiaConclusao() - motor.getDiaAtual()), null, false);
        motor.avancarTempo(24 * 5);
        afirme(motor.getDiaAtual() == 18 && outra.isProntaAguardando() && motor.entregarEncomendasVencidas().isEmpty(),
                "Mundo no dia 18, loja terminou (dia 18): a peça espera, porque a Lenta está no dia 3 (prazo 8)");
        afirme(forja.getReservados(motor.getReceitasPorId()).getOrDefault(ESPADA.getId(), 0) == 1 && forja.getOrdensEmProducao() == 0,
                "A espada fica reservada para a encomenda e libera a oficina");
        int noBalcao = forja.getQuantidadeEstoque(ESPADA.getId());
        motor.avancarMesa(lenta, 24 * 5);
        entregas = motor.entregarEncomendasVencidas();
        afirme(entregas.size() == 1 && entregas.get(0).fichaId() == null && forja.getQuantidadeEstoque(ESPADA.getId()) == noBalcao + 1,
                "A Lenta chega ao dia 8: sem personagem destinatário, a espada vai para o balcão");
        afirme(motor.getDiaAtual() == 18, "Para a mesa atrasada, o mundo não andou");
    }

    private static void testarTrocaDeMesa() throws Exception {
        System.out.println("-- Personagem troca de mesa só para frente");
        Mesa norte = new Mesa(1, "Norte", 8, 15, Mesa.Status.ATIVA, null);
        Mesa sul = new Mesa(2, "Sul", 10, 8, Mesa.Status.ATIVA, null);
        int personagemNoDia10 = Mesa.momento(10, 8);
        afirme(!norte.aceitaPersonagemEm(personagemNoDia10), "Quem viveu até o dia 10 não entra numa mesa no dia 8 (viagem no tempo)");
        afirme(sul.aceitaPersonagemEm(personagemNoDia10) && sul.aceitaPersonagemEm(Mesa.momento(9, 23)), "Entra numa mesa no mesmo momento ou à frente");
        afirme(norte.aceitaPersonagemEm(Mesa.momento(8, 15)) && !norte.aceitaPersonagemEm(Mesa.momento(8, 16)), "A hora conta, não só o dia");

        try (Connection conn = BancoDados.getConnection(); Statement st = conn.createStatement()) {
            for (String nome : List.of("Aria", "Borin")) {
                st.executeUpdate("INSERT INTO fichas (nome, raca_nome, vig, forca, des, men, esp, von, hp, hp_max, sanidade, sanidade_max, energia, energia_max, mana, mana_max, defesa, defesa_oculta) "
                        + "VALUES ('" + nome + "', 'Humano', 1,1,1,1,1,1, 10,10,10,10,10,10,10,10,10,10)");
            }
        }
        MesaDAO dao = new MesaDAO();
        Mesa principal = dao.migrarMesaPrincipal(6, 8);
        afirme(principal != null && "Principal".equals(principal.getNome()) && principal.getDia() == 6, "Migração cria a mesa Principal no dia da campanha");
        afirme(dao.listarFichas().stream().allMatch(x -> Objects.equals(x.mesaId(), principal.getId()) && x.dia() == 6),
                "E coloca todas as fichas nela, com o relógio da mesa");
        afirme(dao.migrarMesaPrincipal(6, 8) == null, "A migração roda uma vez só");

        MesaDAO.FichaNaMesa aria = dao.listarFichas().get(0);
        dao.moverFicha(aria.id(), principal.getId(), 9, 0);
        principal.avancar(24);
        dao.acompanharMesa(principal);
        afirme(dao.buscarFicha(aria.id()).dia() == 9, "A mesa avança para o dia 7: quem já viveu até o dia 9 não volta no tempo");
        afirme(dao.listarFichas().get(1).dia() == 7, "Os outros personagens da mesa avançam junto");

        Mesa sulDb = dao.criar("Sul", 10, 8, null);
        dao.registrarTroca(dao.buscarFicha(aria.id()), principal, true);
        afirme(dao.listarTrocas(5).get(0).get("forcada").equals(true), "A troca forçada fica registrada");
        afirme(sulDb.aceitaPersonagemEm(dao.buscarFicha(aria.id()).momento()), "Aria (dia 9) pode ir para a Sul (dia 10)");
    }

    private static void testarRetencao() {
        System.out.println("-- Retenção de snapshots");
        Lojas[] f = new Lojas[1];
        MotorEconomico motor = mundo(f);
        f[0].adicionarItem(Lojas.clonarItem(ESPADA, 1));
        SnapshotMercadoDAO dao = new SnapshotMercadoDAO();
        dao.apagarAntesDe(Integer.MAX_VALUE);
        for (int dia = 3; dia <= 10; dia++) dao.gravar(SnapshotMercado.fotografar(motor, dia));
        motor.definirMesas(List.of(
                new Mesa(1, "Norte", 8, 8, Mesa.Status.ATIVA, null),
                new Mesa(2, "Antiga", 3, 8, Mesa.Status.ARQUIVADA, null),
                new Mesa(3, "Sul", 10, 8, Mesa.Status.ATIVA, null)));
        afirme(motor.getDiaMesaMaisAtrasada() == 8, "A mesa ativa mais atrasada é a Norte (8); a arquivada (3) não conta");
        dao.apagarAntesDe(motor.getDiaMesaMaisAtrasada());
        afirme(!dao.temDia(7) && !dao.temDia(3) && dao.temDia(8) && dao.temDia(10), "Ficam só as fotos do dia 8 em diante");
        int[] intervalo = dao.intervalo();
        afirme(intervalo[0] == 8 && intervalo[1] == 10, "Intervalo guardado: 8 a 10");
        motor.definirMesas(List.of());
        afirme(motor.getDiaMesaMaisAtrasada() == motor.getDiaAtual(), "Sem mesa ativa, guarda-se só o presente");
    }

    private static void testarAquecerMercado() {
        System.out.println("-- Aquecer o mercado");
        Cidade a = cidade("Alvorada");
        Cidade b = cidade("Brumal");
        new Conexao(a, b, 100, 1, Conexao.TipoDeRota.TERRESTRE);
        MotorEconomico motor = new MotorEconomico(new Random(5));
        motor.registrarCidade(a);
        motor.registrarCidade(b);
        // Concedido no dia 1 em Alvorada: Brumal (100 km) tem Rumor no dia 13, Comércio no 23 e Produção no 43
        Conhecimento k = new Conhecimento(1, TipoConhecimento.RECEITA, null, 9, a.getId(), 1, 7, 2, 3, 10, 20, false, null, 0, false, Map.of());
        ReceitaLoja r = new ReceitaLoja(9, "Poção Rara", 90, 1, 24, Lojas.TipoEstabelecimento.ALQUIMIA, List.of());
        RedeConhecimento rede = new RedeConhecimento(List.of(k), List.of(r), Map.of(), Map.of(), List.of(a, b));
        motor.setRedeConhecimento(rede);
        motor.setDiaAtual(10);
        Mesa norte = new Mesa(1, "Norte", 8, 8, Mesa.Status.ATIVA, null);
        Mesa sul = new Mesa(2, "Sul", 10, 8, Mesa.Status.ATIVA, null);
        motor.definirMesas(List.of(norte, sul));

        Estagio antes = rede.estagio(k, b.getId(), motor.getDiaCampanha());
        motor.avancarTempo(24 * 40);
        afirme(motor.getDiaAtual() == 50, "O mercado andou 40 dias");
        afirme(norte.getDia() == 8 && sul.getDia() == 10 && motor.getDiaCampanha() == 10, "Nenhuma mesa mudou de dia; a campanha continua no dia 10");
        afirme(antes == Estagio.NENHUM && rede.estagio(k, b.getId(), motor.getDiaCampanha()) == Estagio.NENHUM
                        && !rede.receitaLiberada(9, b.getId(), motor.getDiaCampanha()),
                "Nada se espalhou: Brumal continua sem saber da poção");
        afirme(rede.estagio(k, b.getId(), norte.getDia()) == Estagio.NENHUM && rede.estagio(k, b.getId(), 23) == Estagio.COMERCIO,
                "Consultas pela mesa usam o dia dela (a função da rede é pura em relação ao dia)");
        afirme(motor.mesaNoPassado(sul) && motor.mesaNoPassado(norte), "Com o mercado aquecido, todas as mesas leem snapshots");
    }

    private static void testarPersistenciaDaOrdem() throws Exception {
        System.out.println("-- Persistência da encomenda de mesa");
        Lojas[] f = new Lojas[1];
        mundo(f);
        Lojas forja = f[0];
        OrdemReceita o = forja.iniciarReceita(RECEITA, ESPADA, 1, 10, 8, OrdemReceita.Origem.MESTRE, true);
        o.vincularMesa(1, 13, 77L, true);
        ComercioDAO dao = new ComercioDAO();
        Lojas lida = new Lojas("Forja Lida", "Brom", null, false, null, Lojas.TipoEstabelecimento.FORJA);
        lida.setId(forja.getId());
        try (Connection conn = BancoDados.getConnection()) {
            dao.salvarOrdens(conn, forja);
            dao.carregarOrdens(conn, lida);
        }
        OrdemReceita volta = lida.getOrdensReceita().get(0);
        afirme(volta.getMesaId() == 1 && volta.getDiaConclusaoMesa() == 13 && volta.getFichaId() == 77L && volta.isProntaAguardando(),
                "Mesa, prazo da mesa, personagem e \"pronta aguardando\" sobrevivem ao banco");
    }
}
