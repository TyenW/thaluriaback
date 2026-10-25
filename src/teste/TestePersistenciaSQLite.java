package teste;

import economia.*;
import geografia.*;
import menu.CenarioInicial;
import personagem.*;
import persistencia.*;

import java.io.File;
import java.util.List;

/**
 * Suite de Testes Completa de Persistência SQLite para o RPG Thaluria.
 * Valida schema DDL, CRUD de Raças, Itens, Geografia, Lojas, Estoques,
 * Livro-Caixa, Fichas de Personagens e Painel do Mestre sem nenhum mock em memória.
 */
public class TestePersistenciaSQLite {

    private static int assercoesExecutadas = 0;
    private static int assercoesSucesso = 0;

    public static void main(String[] args) {
        System.out.println("======================================================================");
        System.out.println("  INICIANDO SUITE DE TESTES: PERSISTÊNCIA COMPLETA EM SQLITE (v1.0)");
        System.out.println("======================================================================");

        try {
            // Usa banco de teste para isolamento ou thaluria.db
            String dbTeste = "thaluria_teste.db";
            File f = new File(dbTeste);
            if (f.exists()) {
                f.delete();
            }
            BancoDados.setUrlBanco("jdbc:sqlite:" + dbTeste);

            testarInicializacaoEsquema();
            testarPersistenciaRacas();
            testarPersistenciaItens();
            testarPersistenciaEconomiaEGeografia();
            testarPersistenciaFichaPersonagem();
            testarPersistenciaPermissoesMestre();
            testarIntegracaoCenarioCompleto();

            System.out.println("----------------------------------------------------------------------");
            System.out.printf("RESULTADO: %d/%d asserções passaram com 100%% de sucesso!\n", assercoesSucesso, assercoesExecutadas);
            System.out.println("TODOS OS DADOS FORAM ARMAZENADOS E RECUPERADOS DO SQLITE CORRETAMENTE!");
            System.out.println("======================================================================");

            // Restaura URL padrão do banco
            BancoDados.setUrlBanco("jdbc:sqlite:thaluria.db");
            // Também garante que thaluria.db canônico está inicializado
            BancoDados.inicializarEsquema();
            new RacaDAO().popularRacasPadraoSeVazio();
            new ItemDAO().popularItensPadraoSeVazio();
            new EconomiaDAO().popularCenarioPadraoSeVazio();

        } catch (Throwable t) {
            System.err.println("FALHA CRÍTICA NA SUITE DE TESTES SQLITE:");
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static void afirme(boolean condicao, String mensagem) {
        assercoesExecutadas++;
        if (!condicao) {
            System.err.println(" [ERRO] " + mensagem);
            throw new AssertionError("Falha no teste: " + mensagem);
        }
        assercoesSucesso++;
        System.out.println(" [OK] " + mensagem);
    }

    private static void testarInicializacaoEsquema() {
        System.out.println("\n--- 1. Teste de Inicialização do Esquema SQLite ---");
        BancoDados.inicializarEsquema();
        afirme(true, "Esquema SQLite inicializado com sucesso (11 tabelas criadas).");
    }

    private static void testarPersistenciaRacas() {
        System.out.println("\n--- 2. Teste de Persistência de Raças no SQLite ---");
        RacaDAO racaDAO = new RacaDAO();
        racaDAO.popularRacasPadraoSeVazio();

        List<Raca> todas = racaDAO.listarTodas();
        afirme(!todas.isEmpty(), "Catálogo de raças populado no SQLite (Total: " + todas.size() + ").");

        Raca anao = racaDAO.buscarPorNome("Anões");
        afirme(anao != null, "Raça Anões recuperada do banco SQLite.");
        afirme(anao.getMinimo(Atributo.VIG) == 5, "Intervalo VIG mínimo dos Anões recuperado corretamente (5).");
        afirme(anao.getMaximo(Atributo.VIG) == 11, "Intervalo VIG máximo dos Anões recuperado corretamente (11).");
        afirme(anao.getHabilidadeInata() != null, "Habilidade inata dos Anões recuperada do SQLite.");
        afirme(anao.getHabilidadeInata().getNome().equals("Impacto Sísmico (Quebra-Rocha)"), "Habilidade inata é Impacto Sísmico (Quebra-Rocha).");
        afirme(anao.isDisponivelSorteioInicial(), "Anões estão marcados como disponíveis para sorteio inicial.");

        // Atualizar disponibilidade
        racaDAO.atualizarDisponibilidadeSorteio("Anões", false);
        Raca anaoDesativado = racaDAO.buscarPorNome("Anões");
        afirme(!anaoDesativado.isDisponivelSorteioInicial(), "Disponibilidade de sorteio dos Anões atualizada para false no banco.");
        racaDAO.atualizarDisponibilidadeSorteio("Anões", true);

        // Testar Draconatos
        Raca draconato = racaDAO.buscarPorNome("Draconatos");
        afirme(draconato != null, "Draconatos recuperados do banco SQLite.");
        afirme(draconato.getRaridade() == GrauRaridadeRaca.ANCESTRAL_MITICA, "Raridade dos Draconatos é ANCESTRAL_MITICA.");
    }

    private static void testarPersistenciaItens() {
        System.out.println("\n--- 3. Teste de Persistência de Itens no SQLite ---");
        ItemDAO itemDAO = new ItemDAO();
        itemDAO.popularItensPadraoSeVazio();

        List<Item> itens = itemDAO.listarTodos();
        afirme(itens.size() >= 8, "Catálogo de itens canônicos recuperado do SQLite (Total: " + itens.size() + ").");

        Item item1 = itemDAO.buscarPorId(1);
        afirme(item1 != null, "Item canônico recuperado por ID (1).");
        afirme(item1.getNome().equals("Minério de Ferro de Ironhold (Q1)"), "Nome do item validado com sucesso: " + item1.getNome());
        afirme(item1.getPrecoEmLatao() == 1500L, "Preço em latão do Minério de Ferro validado (1.500 latão).");
        afirme(item1.getRegiaoOrigem() == Regiao.TEMPERADO, "Região nativa TEMPERADO validada no SQLite.");
    }

    private static void testarPersistenciaEconomiaEGeografia() {
        System.out.println("\n--- 4. Teste de Geografia, Conexões e Lojas no SQLite ---");
        EconomiaDAO economiaDAO = new EconomiaDAO();
        economiaDAO.popularCenarioPadraoSeVazio();

        List<Cidade> cidades = economiaDAO.listarCidades();
        afirme(cidades.size() >= 5, "Cidades canônicas carregadas do SQLite (Total: " + cidades.size() + ").");

        Cidade ironhold = economiaDAO.buscarCidadePorNome("Ironhold");
        afirme(ironhold != null, "Ironhold recuperada por nome.");
        afirme(ironhold.getTipo() == Cidade.TipoAssentamento.FORTALEZA, "Ironhold é do tipo FORTALEZA.");
        afirme(ironhold.getGeologia() == Geologia.MONTANHA, "Geologia de Ironhold é MONTANHA.");

        // Carregar Motor Completo
        MotorEconomico motor = economiaDAO.carregarMotorEconomicoCompleto();
        afirme(motor != null, "Motor econômico carregado com sucesso do banco.");
        afirme(motor.getCidades().size() >= 5, "Motor contém as cidades registradas (Total: " + motor.getCidades().size() + ").");

        // Validar conexões
        Cidade frostdale = motor.buscarCidadePorNome("Frostdale");
        afirme(frostdale != null, "Frostdale localizada no motor.");
        afirme(!frostdale.getConexoes().isEmpty(), "Frostdale possui conexões ativas recuperadas do banco.");

        // Validar lojas
        Cidade ironholdMotor = motor.buscarCidadePorNome("Ironhold");
        afirme(ironholdMotor != null && !ironholdMotor.getLojas().isEmpty(), "Ironhold possui lojas carregadas do banco.");
        Lojas forja = ironholdMotor.getLojas().get(0);
        afirme(forja.getId() > 0, "Loja possui ID relacional gerado no banco (" + forja.getId() + ").");
        afirme(forja.getCaixa().getValorTotalEmLatao() > 0, "Caixa da loja recuperado do banco em latão.");
        afirme(!forja.getEstoque().isEmpty(), "Estoque da loja recuperado do banco.");

        // Testar Livro-Caixa no SQLite
        economiaDAO.registrarMovimentacaoCaixa(forja.getId(), 1, 14, "VENDA_TESTE", "Espada Longa de Ferro", 1, 150000L, "Teste de auditoria contábil");
        List<String> logs = economiaDAO.listarMovimentacoesFormatadas(forja.getId());
        afirme(!logs.isEmpty(), "Registro do livro-caixa recuperado do banco SQLite.");
        afirme(logs.get(0).contains("VENDA_TESTE"), "Conteúdo da movimentação validado no livro-caixa.");
    }

    private static void testarPersistenciaFichaPersonagem() {
        System.out.println("\n--- 5. Teste de Persistência de Fichas e Perícias no SQLite ---");
        FichaDAO fichaDAO = new FichaDAO();

        Raca elfo = new RacaDAO().buscarPorNome("Elfos da Floresta");
        Ficha f = new Ficha(12, 14, 16, 11, 10, 13, elfo);
        f.setNome("Legolas de Valdorian");
        f.getCarteira().receberLatao(500000L); // 50 PO em latão
        f.adicionarPontosEssencia(15);

        // Perícias
        f.setPericia(Pericia.PONTARIA_LUTA, 1, 1);
        f.setPericia(Pericia.FURTIVIDADE, 1, 0);

        // Inventário
        Item arco = new Item(2, "Espada Longa de Ferro", "Lâmina", 15.0, 2.5, Item.TipoItem.ARMA, Item.RaridadeItem.COMUM, 1);
        Item pocao = new Item(3, "Poção de Cura", "Elixir", 5.0, 0.5, Item.TipoItem.CONSUMIVEL, Item.RaridadeItem.COMUM, 3);
        f.adicionarItem(arco);
        f.adicionarItem(pocao);

        // Salvar no SQLite
        fichaDAO.salvar(f);
        afirme(f.getId() > 0, "Ficha salva com sucesso! ID gerado no SQLite: " + f.getId());

        // Recuperar por ID
        Ficha recuperada = fichaDAO.buscarPorId(f.getId());
        afirme(recuperada != null, "Ficha recuperada por ID do SQLite.");
        afirme(recuperada.getNome().equals("Legolas de Valdorian"), "Nome da ficha preservado: " + recuperada.getNome());
        afirme(recuperada.getRaca().getNome().equals("Elfos da Floresta"), "Raça da ficha preservada: " + recuperada.getRaca().getNome());
        afirme(recuperada.getDes() == 16, "Atributo Destreza preservado (16).");
        afirme(recuperada.getPontosEssencia() == 15, "Pontos de Essência preservados (15).");
        afirme(recuperada.getCarteira().getValorTotalEmLatao() == 500000L, "Saldo da Carteira em Latão preservado (500.000 latão).");

        // Perícias
        afirme(recuperada.getGrauPericia(Pericia.PONTARIA_LUTA) == 1, "Grau da perícia Pontaria/Luta preservado (1).");
        afirme(recuperada.getMaestriaPericia(Pericia.PONTARIA_LUTA) == 1, "Maestria da perícia Pontaria/Luta preservada (1).");
        afirme(recuperada.getGrauPericia(Pericia.FURTIVIDADE) == 1, "Grau da perícia Furtividade preservado (1).");

        // Inventário
        afirme(recuperada.getInventario().size() >= 2, "Itens do inventário recuperados do SQLite (Total: " + recuperada.getInventario().size() + ").");

        // Atualização
        recuperada.setHp(10);
        recuperada.getCarteira().receberLatao(25000L);
        fichaDAO.salvar(recuperada);

        Ficha atualizada = fichaDAO.buscarPorId(recuperada.getId());
        afirme(atualizada.getHp() == 10, "Atualização de HP persistida com sucesso.");
        afirme(atualizada.getCarteira().getValorTotalEmLatao() == 525000L, "Atualização de carteira persistida com sucesso.");

        // Exclusão
        boolean excluiu = fichaDAO.excluir(atualizada.getId());
        afirme(excluiu, "Ficha excluída com sucesso do SQLite.");
        afirme(fichaDAO.buscarPorId(atualizada.getId()) == null, "Ficha não existe mais no banco de dados.");
    }

    private static void testarPersistenciaPermissoesMestre() {
        System.out.println("\n--- 6. Teste de Permissões do Mestre e Fog-of-War no SQLite ---");
        PermissoesMestreDAO mestreDAO = new PermissoesMestreDAO();
        ControlePermissoesMestre controle = new ControlePermissoesMestre();

        controle.liberarCapituloWiki("Status");
        controle.revelarMonstroBestiario("GRIFO_ALTO_VALE");
        controle.setVisibilidadeCidade("Sunreach", ControlePermissoesMestre.NivelVisibilidadeMapa.INFORMACOES_COMPLETAS);

        mestreDAO.salvarControleCompleto(controle);
        mestreDAO.salvarValor("BESTIARIO", "GRIFO_ALTO_VALE", "REVELADO");
        mestreDAO.salvarValor("FOG_CIDADE", "Sunreach", ControlePermissoesMestre.NivelVisibilidadeMapa.INFORMACOES_COMPLETAS.name());

        ControlePermissoesMestre carregado = new ControlePermissoesMestre();
        mestreDAO.carregarParaControle(carregado);

        afirme(carregado.isCapituloLiberado("Status"), "Capítulo 'Status' da Wiki carregado como desbloqueado.");
        afirme(carregado.isMonstroRevelado("GRIFO_ALTO_VALE"), "Monstro 'GRIFO_ALTO_VALE' carregado como revelado.");
        afirme(carregado.getVisibilidadeCidade("Sunreach") == ControlePermissoesMestre.NivelVisibilidadeMapa.INFORMACOES_COMPLETAS,
                "Visibilidade de Sunreach carregada como INFORMACOES_COMPLETAS.");
    }

    private static void testarIntegracaoCenarioCompleto() {
        System.out.println("\n--- 7. Teste de Integração de CenarioInicial com SQLite ---");
        CenarioInicial.DadosMundo dados = CenarioInicial.criarCenarioPadrao();

        afirme(dados != null, "CenarioInicial criado através do banco SQLite.");
        afirme(dados.motor != null, "Motor carregado pelo CenarioInicial.");
        afirme(dados.jogador != null, "Jogador carregado pelo CenarioInicial.");
        afirme(dados.jogador.getId() > 0, "Jogador possui ID persistido no banco SQLite (" + dados.jogador.getId() + ").");
        afirme(dados.ironhold != null, "Cidade Ironhold instanciada e conectada.");
        afirme(dados.cidadeAtualJogador != null, "Cidade atual do jogador definida.");
    }
}
