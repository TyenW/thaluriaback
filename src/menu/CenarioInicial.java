package menu;

import economia.Carteira;
import economia.Item;
import economia.Lojas;
import economia.MotorEconomico;
import geografia.Aquifero;
import geografia.Cidade;
import geografia.Conexao;
import geografia.Geologia;
import geografia.Regiao;
import personagem.Ficha;

/**
 * Constrói o cenário de teste e demonstração canônico de Thaluria,
 * com malha viária, cidades, lojas de rede, estoques e aventureiro de teste.
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
        DadosMundo dados = new DadosMundo();
        dados.motor = new MotorEconomico();

        // 1. Criação das Cidades com Bioma, Geologia e Aquífero
        dados.frostdale = new Cidade(1, "Frostdale", Cidade.TipoAssentamento.CIDADE, Regiao.TUNDRA, Geologia.FLORESTA, Aquifero.OCEANO_AGUAS_COSTEIRAS, true, false, null);
        dados.ironhold = new Cidade(2, "Ironhold", Cidade.TipoAssentamento.FORTALEZA, Regiao.TEMPERADO, Geologia.MONTANHA, Aquifero.RIO, false, false, null);
        dados.valdorian = new Cidade(3, "Valdorian", Cidade.TipoAssentamento.REINO, Regiao.TEMPERADO, Geologia.PLANICIE, Aquifero.RIO, false, false, null);
        dados.oakhaven = new Cidade(4, "Porto Oakhaven", Cidade.TipoAssentamento.CIDADE, Regiao.TEMPERADO, Geologia.FLORESTA, Aquifero.OCEANO_AGUAS_COSTEIRAS, true, false, null);
        dados.sunreach = new Cidade(5, "Sunreach", Cidade.TipoAssentamento.CIDADE, Regiao.DESERTO, Geologia.CAVERNA, Aquifero.OCEANO_AGUAS_PROFUNDAS, true, true, Regiao.SAVANA);

        // 2. Conexões e Estradas do Grafo
        new Conexao(dados.frostdale, dados.ironhold, 120, 4, Conexao.TipoDeRota.TERRESTRE);
        new Conexao(dados.ironhold, dados.valdorian, 80, 2, Conexao.TipoDeRota.TERRESTRE);
        new Conexao(dados.valdorian, dados.oakhaven, 90, 2, Conexao.TipoDeRota.TERRESTRE);
        new Conexao(dados.frostdale, dados.oakhaven, 160, 3, Conexao.TipoDeRota.MARITIMA);
        new Conexao(dados.valdorian, dados.sunreach, 240, 6, Conexao.TipoDeRota.TERRESTRE);
        new Conexao(dados.oakhaven, dados.sunreach, 210, 5, Conexao.TipoDeRota.MARITIMA);

        // 3. Catálogo de Itens Canônicos
        Item ferroLingote = new Item(1, "Lingote de Ferro", "Metal denso refinado", 2.5, 3.0, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 1);
        Item espadaFerro = new Item(2, "Espada Longa de Ferro", "Lâmina afiada para soldados", 15.0, 2.5, Item.TipoItem.ARMA, Item.RaridadeItem.COMUM, 1);
        Item pocaoCura = new Item(3, "Poção de Cura", "Elixir restaurador de vitalidade", 5.0, 0.5, Item.TipoItem.CONSUMIVEL, Item.RaridadeItem.COMUM, 1);
        Item mantoSeda = new Item(4, "Manto de Seda", "Vestimenta nobre de Valdorian", 12.0, 1.0, Item.TipoItem.ARMADURA, Item.RaridadeItem.INCOMUM, 1);
        Item gemaPolida = new Item(5, "Gema Polida das Areias", "Pedra preciosa do deserto", 50.0, 0.2, Item.TipoItem.ACESSORIO, Item.RaridadeItem.RARO, 1);
        Item madeiraNobre = new Item(6, "Madeira Nobre da Taiga", "Troncos densos ideais para arcos e navios", 4.0, 5.0, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 1);
        Item racaoViagem = new Item(7, "Ração de Viagem Élfica", "Pão de viagem nutritivo e conservas", 1.5, 0.4, Item.TipoItem.CONSUMIVEL, Item.RaridadeItem.COMUM, 1);
        Item hidromel = new Item(8, "Hidromel das Montanhas", "Bebida forte fermentada em barris de carvalho", 2.0, 1.0, Item.TipoItem.CONSUMIVEL, Item.RaridadeItem.COMUM, 1);

        // 4. Lojas de Ironhold
        Lojas forjaIronhold = new Lojas("Forja do Martelo Rubro", "Torvald Martelo-de-Ferro", dados.ironhold, false, null, Lojas.TipoEstabelecimento.FORJA);
        forjaIronhold.setCaixa(new Carteira(150)); // 150 PO de caixa
        forjaIronhold.adicionarItem(Lojas.clonarItem(ferroLingote, 25));
        forjaIronhold.adicionarItem(Lojas.clonarItem(espadaFerro, 8));
        forjaIronhold.definirDemanda(ferroLingote, 30);
        forjaIronhold.definirDemanda(espadaFerro, 10);

        Lojas redeNorteIronhold = new Lojas("Empório da Rede do Norte", "Garrick Mercador", dados.ironhold, true, "Liga Mercantil do Norte", Lojas.TipoEstabelecimento.LOJA_GERAL);
        redeNorteIronhold.setCaixa(new Carteira(80));
        redeNorteIronhold.adicionarItem(Lojas.clonarItem(pocaoCura, 12));
        redeNorteIronhold.adicionarItem(Lojas.clonarItem(madeiraNobre, 15));
        redeNorteIronhold.definirDemanda(pocaoCura, 15);
        redeNorteIronhold.definirDemanda(ferroLingote, 20); // Tem déficit de ferro!

        // 5. Lojas de Valdorian
        Lojas redeNorteValdorian = new Lojas("Armazém Central da Rede", "Mestre Elidor", dados.valdorian, true, "Liga Mercantil do Norte", Lojas.TipoEstabelecimento.LOJA_GERAL);
        redeNorteValdorian.setCaixa(new Carteira(200));
        redeNorteValdorian.adicionarItem(Lojas.clonarItem(ferroLingote, 40));
        redeNorteValdorian.adicionarItem(Lojas.clonarItem(espadaFerro, 15));
        redeNorteValdorian.definirDemanda(ferroLingote, 10);

        Lojas boticaValdorian = new Lojas("Botica das Ervas Místicas", "Alquimista Serena", dados.valdorian, false, null, Lojas.TipoEstabelecimento.ALQUIMIA);
        boticaValdorian.setCaixa(new Carteira(100));
        boticaValdorian.adicionarItem(Lojas.clonarItem(pocaoCura, 30));
        boticaValdorian.definirDemanda(pocaoCura, 20);

        Lojas alfaiatariaValdorian = new Lojas("Alfaiataria Seda Dourada", "Madamme Vivienne", dados.valdorian, false, null, Lojas.TipoEstabelecimento.ALFAIATARIA);
        alfaiatariaValdorian.setCaixa(new Carteira(120));
        alfaiatariaValdorian.adicionarItem(Lojas.clonarItem(mantoSeda, 10));
        alfaiatariaValdorian.definirDemanda(mantoSeda, 8);

        // 6. Lojas de Frostdale (Taverna)
        Lojas tavernaFrostdale = new Lojas("Taverna do Lobo Uivante", "Olaf Barba-de-Gelo", dados.frostdale, false, null, Lojas.TipoEstabelecimento.TAVERNA);
        tavernaFrostdale.setCaixa(new Carteira(90));
        tavernaFrostdale.adicionarItem(Lojas.clonarItem(racaoViagem, 40));
        tavernaFrostdale.adicionarItem(Lojas.clonarItem(hidromel, 25));
        tavernaFrostdale.definirDemanda(racaoViagem, 30);
        tavernaFrostdale.definirDemanda(hidromel, 20);

        // 7. Lojas de Sunreach
        Lojas bazarSunreach = new Lojas("Bazar das Mil Areias", "Nadim Al-Mansur", dados.sunreach, false, null, Lojas.TipoEstabelecimento.JOALHERIA);
        bazarSunreach.setCaixa(new Carteira(300));
        bazarSunreach.adicionarItem(Lojas.clonarItem(gemaPolida, 14));
        bazarSunreach.definirDemanda(gemaPolida, 10);
        bazarSunreach.definirDemanda(espadaFerro, 20);

        // Registro das cidades no motor
        dados.motor.registrarCidade(dados.frostdale);
        dados.motor.registrarCidade(dados.ironhold);
        dados.motor.registrarCidade(dados.valdorian);
        dados.motor.registrarCidade(dados.oakhaven);
        dados.motor.registrarCidade(dados.sunreach);

        // 8. Personagem / Jogador de Teste
        dados.jogador = new Ficha(14, 15, 12, 10, 10, 11);
        dados.jogador.getCarteira().adicionarOuro(25); // 25 PO de saldo inicial (250.000 latão)
        dados.jogador.adicionarItem(Lojas.clonarItem(ferroLingote, 2));
        dados.cidadeAtualJogador = dados.ironhold;
        dados.jogador.setCidadeAtual(dados.ironhold);

        return dados;
    }
}
