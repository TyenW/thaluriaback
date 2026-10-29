package teste;

import economia.*;
import economia.RedeConhecimento.Ajuste;
import economia.RedeConhecimento.Conhecimento;
import economia.RedeConhecimento.Estagio;
import economia.RedeConhecimento.TipoConhecimento;
import geografia.*;
import persistencia.BancoDados;
import persistencia.ConhecimentoDAO;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;

/**
 * Disseminação de conhecimento: tempo pelo grafo (sigilo + km + resistência por salto),
 * estágios, pausa, segredo, adiantar e revogar cidades, e o efeito no motor do comércio
 * (produção, fornecedor local e encomendas entre cidades).
 *
 * Usa um banco próprio (thaluria_teste_disseminacao.db) só na parte de persistência.
 */
public class TesteDisseminacao {

    private static final String BANCO_TESTE = "thaluria_teste_disseminacao.db";
    private static int total = 0;
    private static int passaram = 0;
    private static int proximaCidade = 7000;

    public static void main(String[] args) throws Exception {
        System.out.println("=== DISSEMINAÇÃO DE CONHECIMENTO ===\n");
        testarTempoPeloGrafo();
        testarControlesDoMestre();
        testarEfeitoNoMotor();
        new File(BANCO_TESTE).delete();
        BancoDados.setUrlBanco("jdbc:sqlite:" + BANCO_TESTE);
        try {
            BancoDados.inicializarEsquema();
            testarPersistencia();
        } finally {
            BancoDados.setUrlBanco("jdbc:sqlite:thaluria.db");
            new File(BANCO_TESTE).delete();
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

    private static Cidade cidade(String nome) {
        return new Cidade(proximaCidade++, nome, Cidade.TipoAssentamento.CIDADE, Regiao.TEMPERADO, Geologia.PLANICIE, Aquifero.RIO, false, false, null);
    }

    /** Conhecimento com os padrões do Mestre: sigilo 7, 2 dias/100 km, resistência 3, +10, +20. */
    private static Conhecimento conhecimento(int origem, int dia, boolean pausado, Integer pausadoDesde, int diasPausados, boolean segredo, Map<Integer, Ajuste> ajustes) {
        return new Conhecimento(1, TipoConhecimento.RECEITA, null, 9, origem, dia, 7, 2, 3, 10, 20, pausado, pausadoDesde, diasPausados, segredo, ajustes);
    }

    private static RedeConhecimento rede(Conhecimento k, List<Cidade> cidades) {
        ReceitaLoja r = new ReceitaLoja(9, "Poção Rara", 90, 1, 24, Lojas.TipoEstabelecimento.ALQUIMIA, List.of());
        return new RedeConhecimento(List.of(k), List.of(r), Map.of(), Map.of(), cidades);
    }

    // A —100 km— B —300 km— C        A —(bloqueada)— D
    private static Cidade a, b, c, d;

    private static List<Cidade> grafo() {
        a = cidade("Alvorada");
        b = cidade("Brumal");
        c = cidade("Cinzel");
        d = cidade("Distante");
        new Conexao(a, b, 100, 1, Conexao.TipoDeRota.TERRESTRE);
        new Conexao(b, c, 300, 1, Conexao.TipoDeRota.TERRESTRE);
        new Conexao(a, d, 50, 1, Conexao.TipoDeRota.TERRESTRE).bloquear();
        return List.of(a, b, c, d);
    }

    private static void testarTempoPeloGrafo() {
        System.out.println("-- Tempo pelo grafo");
        List<Cidade> cidades = grafo();
        Conhecimento k = conhecimento(a.getId(), 10, false, null, 0, false, Map.of());
        RedeConhecimento rede = rede(k, cidades);

        afirme(rede.estagio(k, a.getId(), 10) == Estagio.PRODUCAO, "A cidade de origem já produz no dia da concessão");
        afirme(rede.diaPrevisto(k, b.getId(), Estagio.RUMOR, 10) == 22, "Brumal ouve o rumor no dia 22 (10 + 7 de sigilo + 2 dias pelos 100 km + 3 de resistência)");
        afirme(rede.diaPrevisto(k, b.getId(), Estagio.COMERCIO, 10) == 32 && rede.diaPrevisto(k, b.getId(), Estagio.PRODUCAO, 10) == 52,
                "Depois vêm Comércio (+10) e Produção (+20)");
        afirme(rede.diaPrevisto(k, c.getId(), Estagio.RUMOR, 10) == 31, "Cinzel, a dois saltos, ouve no dia 31 (mais 6 dias pelos 300 km e 3 de resistência)");
        afirme(rede.estagio(k, b.getId(), 21) == Estagio.NENHUM && rede.estagio(k, b.getId(), 22) == Estagio.RUMOR, "O estágio muda exatamente no dia previsto");
        afirme(rede.estagio(k, b.getId(), 40) == Estagio.COMERCIO && rede.estagio(k, b.getId(), 60) == Estagio.PRODUCAO, "Comércio e Produção chegam na sequência");
        afirme(rede.diaPrevisto(k, d.getId(), Estagio.RUMOR, 10) == null && rede.estagio(k, d.getId(), 500) == Estagio.NENHUM, "Rota bloqueada não propaga");
    }

    private static void testarControlesDoMestre() {
        System.out.println("-- Controles do Mestre");
        List<Cidade> cidades = grafo();

        Conhecimento pausado = conhecimento(a.getId(), 10, true, 15, 0, false, Map.of());
        RedeConhecimento r1 = rede(pausado, cidades);
        afirme(r1.estagio(pausado, b.getId(), 30) == Estagio.NENHUM && r1.diaPrevisto(pausado, b.getId(), Estagio.RUMOR, 30) == null,
                "Pausado no dia 15, o relógio para: no dia 30 Brumal ainda não sabe e não há previsão");
        Conhecimento retomado = conhecimento(a.getId(), 10, false, null, 15, false, Map.of());
        RedeConhecimento r2 = rede(retomado, cidades);
        afirme(r2.diaPrevisto(retomado, b.getId(), Estagio.RUMOR, 30) == 37, "Retomado depois de 15 dias parados, tudo atrasa 15 dias (rumor no dia 37)");

        Conhecimento segredo = conhecimento(a.getId(), 10, false, null, 0, true, Map.of());
        afirme(rede(segredo, cidades).estagio(segredo, b.getId(), 999) == Estagio.NENHUM, "Segredo não sai da cidade de origem");

        Conhecimento adiantado = conhecimento(a.getId(), 10, false, null, 0, false, Map.of(c.getId(), new Ajuste(Estagio.PRODUCAO, false, 0)));
        RedeConhecimento r3 = rede(adiantado, cidades);
        afirme(r3.estagio(adiantado, c.getId(), 10) == Estagio.PRODUCAO, "Cidade adiantada pelo Mestre produz na hora");
        afirme(r3.diaPrevisto(adiantado, b.getId(), Estagio.RUMOR, 10) == 19, "E também espalha: Brumal ouve de Cinzel antes (dia 19) do que ouviria de Alvorada (22)");

        Conhecimento revogado = conhecimento(a.getId(), 10, false, null, 0, false, Map.of(b.getId(), new Ajuste(null, true, 0)));
        RedeConhecimento r4 = rede(revogado, cidades);
        afirme(r4.estagio(revogado, b.getId(), 999) == Estagio.NENHUM, "Cidade revogada esquece o conhecimento");
        afirme(r4.estagio(revogado, c.getId(), 999) == Estagio.NENHUM, "E não passa adiante: Cinzel, que só ouviria por Brumal, fica sem saber");
    }

    private static void testarEfeitoNoMotor() {
        System.out.println("-- Efeito no comércio");
        List<Cidade> cidades = grafo();
        Item erva = new Item(80, "Erva T", "", 0.03, 0.1, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 1);
        Item pocao = new Item(90, "Poção Rara T", "", 5.0, 0.1, Item.TipoItem.CONSUMIVEL, Item.RaridadeItem.RARO, 1);
        ReceitaLoja receita = new ReceitaLoja(9, "Poção Rara", 90, 1, 24, Lojas.TipoEstabelecimento.ALQUIMIA, List.of(new ReceitaLoja.Insumo(80, 1)));
        Conhecimento k = conhecimento(a.getId(), 1, false, null, 0, false, Map.of());
        RedeConhecimento rede = new RedeConhecimento(List.of(k), List.of(receita), Map.of(), Map.of(), cidades);

        afirme(rede.receitaRegional(9) && rede.receitaLiberada(9, a.getId(), 1) && !rede.receitaLiberada(9, b.getId(), 30), "Receita regional só se faz onde chegou a Produção");
        afirme(rede.estagioDoItem(80, b.getId(), 1) == null, "A erva, que não é de receita regional, continua comum");

        Lojas geral = new Lojas("Empório B", "Ana", b, false, null, Lojas.TipoEstabelecimento.LOJA_GERAL);
        Lojas forja = new Lojas("Forja B", "Brom", b, false, null, Lojas.TipoEstabelecimento.FORJA);
        afirme(rede.itensParaPrateleira(geral, 22).isEmpty() && rede.itensParaPrateleira(geral, 23).contains(90),
                "No Comércio (dia 23), a Loja Geral de Brumal passa a procurar a poção");
        afirme(rede.itensParaPrateleira(forja, 40).isEmpty(), "Lojas de outra especialidade não");

        // Encomenda entre cidades: a poção só viaja para quem já ouviu falar dela
        MotorEconomico motor = new MotorEconomico(new Random(3));
        for (Cidade cc : cidades) motor.registrarCidade(cc);
        Lojas fonte = new Lojas("Botica A", "Cora", a, false, null, Lojas.TipoEstabelecimento.ALQUIMIA);
        fonte.setCaixa(Carteira.deLatao(1_000_000));
        fonte.adicionarItem(Lojas.clonarItem(pocao, 50));
        Lojas compradora = new Lojas("Botica B", "Duda", b, false, null, Lojas.TipoEstabelecimento.ALQUIMIA);
        compradora.setCaixa(Carteira.deLatao(5_000_000));
        compradora.definirDemanda(pocao, 10);
        motor.definirReceitasECatalogo(List.of(receita), List.of(erva, pocao));
        motor.setRedeConhecimento(rede);
        motor.setDiaAtual(5);
        afirme(!motor.solicitarCompraEntreCidades(compradora, pocao, 3), "Antes do Rumor, Brumal não consegue encomendar a poção");
        motor.setRedeConhecimento(RedeConhecimento.vazia());
        boolean semRegra = motor.solicitarCompraEntreCidades(compradora, pocao, 3);
        afirme(semRegra, "Com a poção comum, a mesma encomenda passa (a recusa era só pelo conhecimento)");

        // Produção: a IA da botica de Brumal não faz a receita regional antes da Produção
        MotorEconomico motor2 = new MotorEconomico(new Random(4));
        List<Cidade> cidades2 = grafo();
        RedeConhecimento rede2 = new RedeConhecimento(List.of(conhecimento(a.getId(), 1, false, null, 0, false, Map.of())), List.of(receita), Map.of(), Map.of(), cidades2);
        for (Cidade cc : cidades2) motor2.registrarCidade(cc);
        Lojas botica = new Lojas("Botica B2", "Eli", b, false, null, Lojas.TipoEstabelecimento.ALQUIMIA);
        botica.setCaixa(Carteira.deLatao(5_000_000));
        botica.adicionarItem(Lojas.clonarItem(erva, 20));
        botica.definirDemanda(pocao, 10);
        motor2.definirReceitasECatalogo(List.of(receita), List.of(erva, pocao));
        motor2.setRedeConhecimento(rede2);
        motor2.setDiaAtual(10);
        motor2.executarCicloDiario();
        afirme(botica.getOrdensReceita().stream().noneMatch(o -> o.getReceitaId() == 9), "Sem Produção em Brumal, a IA da botica não fabrica a poção");
        // A disseminação segue o dia da campanha (mesa ativa mais à frente), não o do mercado
        motor2.setDiaAtual(60);
        motor2.registrarMesa(new Mesa(1, "Mesa", 60, 8, Mesa.Status.ATIVA, null));
        motor2.executarCicloDiario();
        afirme(botica.getOrdensReceita().stream().anyMatch(o -> o.getReceitaId() == 9), "Chegada a Produção (dia 43), a botica passa a fabricá-la");
    }

    private static void testarPersistencia() throws Exception {
        System.out.println("-- Persistência");
        try (Connection conn = BancoDados.getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO cidades (id, nome, tipo, regiao, geologia, aquifero) VALUES (1, 'Origem T', 'CIDADE', 'TEMPERADO', 'PLANICIE', 'RIO')");
            st.executeUpdate("INSERT INTO itens (id, nome, preco_latao, peso_kg, tipo, raridade, tier) VALUES (1, 'Pocao T', 100, 1, 'CONSUMIVEL', 'COMUM', 1)");
            st.executeUpdate("INSERT INTO receitas (id, nome, item_resultado_id, tempo_horas, tipo_loja) VALUES (1, 'Fazer pocao', 1, 24, 'ALQUIMIA')");
        }
        ConhecimentoDAO dao = new ConhecimentoDAO();
        int id = dao.conceder(TipoConhecimento.RECEITA, null, 1, 1, 5, ConhecimentoDAO.Parametros.PADRAO);
        afirme(dao.listar().size() == 1 && dao.listar().get(0).sigiloDias() == 7, "Conceder grava o conhecimento com os padrões");
        try {
            dao.conceder(TipoConhecimento.RECEITA, null, 1, 1, 5, ConhecimentoDAO.Parametros.PADRAO);
            afirme(false, "Não se concede a mesma receita duas vezes");
        } catch (RuntimeException e) {
            afirme(true, "Não se concede a mesma receita duas vezes");
        }
        dao.pausar(dao.listar().get(0), true, 10);
        dao.pausar(dao.listar().get(0), false, 14);
        Conhecimento k = dao.listar().get(0);
        afirme(!k.pausado() && k.diasPausados() == 4, "Pausar e retomar soma os dias parados");
        dao.ajustarCidade(k, 1, "REVOGAR", 20);
        afirme(dao.listar().get(0).ajustes().get(1).revogada(), "Revogar fica gravado por cidade");
        dao.ajustarCidade(k, 1, "LIMPAR", 20);
        afirme(dao.listar().get(0).ajustes().isEmpty(), "Limpar devolve a cidade ao cálculo normal");
        dao.excluir(id);
        afirme(dao.listar().isEmpty(), "Excluir torna a receita comum de novo");
    }
}
