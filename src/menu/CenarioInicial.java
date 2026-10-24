package menu;

import economia.*;
import geografia.*;
import personagem.Ficha;
import persistencia.*;

/**
 * Constrói e gerencia o cenário canônico de Thaluria persistido em SQLite,
 * com malha viária, cidades, lojas de rede, estoques e aventureiro de teste.
 * Elimina dados mocados em memória, garantindo round-trip com thaluria.db.
 */
public class CenarioInicial {

    public static class DadosMundo {
        public MotorEconomico motor;
        public Ficha jogador;
        public Cidade cidadeAtualJogador;
        public Cidade frostdale;
        public Cidade ironhold;
        public Cidade valdorian;
        public Cidade oakhaven;
        public Cidade sunreach;
    }

    public static DadosMundo criarCenarioPadrao() {
        // 1. Inicializa o banco SQLite e popula esquema e catálogos canônicos se vazios
        BancoDados.inicializarEsquema();
        new RacaDAO().popularRacasPadraoSeVazio();
        new ItemDAO().popularItensPadraoSeVazio();
        EconomiaDAO economiaDAO = new EconomiaDAO();
        economiaDAO.popularCenarioPadraoSeVazio();

        DadosMundo dados = new DadosMundo();
        dados.motor = economiaDAO.carregarMotorEconomicoCompleto();

        for (Cidade c : dados.motor.getCidades()) {
            if (c.getNome().equalsIgnoreCase("Frostdale")) dados.frostdale = c;
            else if (c.getNome().equalsIgnoreCase("Ironhold")) dados.ironhold = c;
            else if (c.getNome().equalsIgnoreCase("Valdorian")) dados.valdorian = c;
            else if (c.getNome().equalsIgnoreCase("Porto Oakhaven")) dados.oakhaven = c;
            else if (c.getNome().equalsIgnoreCase("Sunreach")) dados.sunreach = c;
        }

        // 2. Personagem do jogador carregado ou persistido no SQLite
        FichaDAO fichaDAO = new FichaDAO();
        Ficha jogador = fichaDAO.buscarPorNome("Aventureiro de Ironhold");
        if (jogador == null) {
            jogador = new Ficha(14, 15, 12, 10, 10, 11);
            jogador.setNome("Aventureiro de Ironhold");
            jogador.getCarteira().adicionarOuro(25);
            Item ferro = new ItemDAO().buscarPorId(1);
            if (ferro != null) {
                jogador.adicionarItem(Lojas.clonarItem(ferro, 2));
            }
            jogador.setCidadeAtual(dados.ironhold);
            fichaDAO.salvar(jogador);
        } else {
            jogador.setCidadeAtual(dados.ironhold);
        }

        dados.jogador = jogador;
        dados.cidadeAtualJogador = dados.ironhold;

        return dados;
    }
}
