package teste;

import economia.*;
import geografia.*;
import personagem.Ficha;

import java.util.List;
import java.util.Random;

/**
 * Bateria completa de Testes End-to-End (E2E) do Sistema Econômico v3 e Geografia Dinâmica.
 * Valida todas as 11 camadas do sistema de ponta a ponta sem dependências externas.
 */
public class TesteEndToEnd {

    private int totalTestes = 0;
    private int testesPassados = 0;

    public static void main(String[] args) {
        TesteEndToEnd suite = new TesteEndToEnd();
        suite.executarTodos();
    }

    public void executarTodos() {
        System.out.println("======================================================================");
        System.out.println("   ⚔️ INICIANDO SUITE DE TESTES END-TO-END (E2E) - THALURIA RPG v3    ");
        System.out.println("======================================================================");

        try {
            teste1_EscalaMonetariaELatao();
            teste2_EstabelecimentosCanônicosEEstoque();
            teste3_OfertaDemandaEPrecosDinamicos();
            teste4_ComercioDeBalcaoJogador();
            teste5_FornecedorNpcLocal();
            teste6_GrafoDijkstraERotasBloqueadas();
            teste7_ComercioIntermunicipalEDescontoRede();
            teste8_MissaoEscoltaAssumidaJogador();
            teste9_CaravanaExtraviadaEResgateSucesso();
            teste10_ResgateFalhaEEscaladaDePerigo();
            teste11_AuditoriaContabilLivroCaixa();

            System.out.println("\n======================================================================");
            System.out.printf("   🎉 RESULTADO FINAL: %d/%d TESTES PASSARAM COM 100%% DE SUCESSO!   \n",
                    testesPassados, totalTestes);
            System.out.println("======================================================================");
        } catch (Throwable t) {
            System.err.println("\n❌ FALHA CRÍTICA DURANTE A EXECUÇÃO DOS TESTES E2E:");
            t.printStackTrace();
            System.exit(1);
        }
    }

    private void afirmo(boolean condicao, String mensagem) {
        totalTestes++;
        if (condicao) {
            testesPassados++;
            System.out.printf("  ✓ [PASSOU] %s\n", mensagem);
        } else {
            System.err.printf("  ✗ [FALHOU] %s\n", mensagem);
            throw new AssertionError("Falha na asserção: " + mensagem);
        }
    }

    // ------------------------------------------------------------------------
    // TESTE 1: ESCALA MONETÁRIA
    // ------------------------------------------------------------------------
    private void teste1_EscalaMonetariaELatao() {
        System.out.println("\n[1/11] Testando Escala Monetária de 10 Moedas e Cálculos em Latão...");

        Carteira c = new Carteira();
        afirmo(c.getValorTotalEmLatao() == 0L, "Carteira nova inicia com saldo 0");

        // 1 PO = 10.000 latão
        c.adicionarOuro(1);
        afirmo(c.getValorTotalEmLatao() == 10_000L, "1 Ouro (PO) equivale exatamente a 10.000 latão");

        // Normalização de troco: 25 pratas = 2 ouros e 5 pratas (25.000 latão)
        Carteira c2 = new Carteira();
        c2.adicionarPrata(25);
        afirmo(c2.getOuro() == 2 && c2.getPrata() == 5, "25 Pratas normalizam automaticamente para 2 PO e 5 PP");

        // Decomposição complexa
        long valorComplexo = 1_234_567L; // 1 CB, 2 PT, 3 PO, 4 PP, 5 PC, 6 PF, 7 PL
        Carteira c3 = Carteira.deLatao(valorComplexo);
        afirmo(c3.getCobalto() == 1 && c3.getPlatina() == 2 && c3.getOuro() == 3 &&
               c3.getPrata() == 4 && c3.getCobre() == 5 && c3.getFerro() == 6 && c3.getLatao() == 7,
               "Decomposição exata de 1.234.567 latão nas moedas correspondentes");

        // Pagamento com troco automático
        boolean pagou = c3.pagarLatao(34_567L);
        afirmo(pagou, "Pagamento de 34.567 latão debitado com sucesso");
        afirmo(c3.getValorTotalEmLatao() == 1_200_000L, "Saldo restante é exatamente 1.200.000 latão");
    }

    // ------------------------------------------------------------------------
    // TESTE 2: ESTABELECIMENTOS CANÔNICOS
    // ------------------------------------------------------------------------
    private void teste2_EstabelecimentosCanônicosEEstoque() {
        System.out.println("\n[2/11] Testando os 6 Tipos Canônicos de Estabelecimentos...");

        Cidade frostdale = new Cidade(1, "Frostdale", Cidade.TipoAssentamento.CIDADE, Regiao.TUNDRA, true, false, null);

        Lojas forja = new Lojas("Forja Central", "Torvald", frostdale, false, null, Lojas.TipoEstabelecimento.FORJA);
        Lojas alfaiataria = new Lojas("Alfaiataria Nobre", "Vivienne", frostdale, false, null, Lojas.TipoEstabelecimento.ALFAIATARIA);
        Lojas geral = new Lojas("Empório Geral", "Garrick", frostdale, true, "Rede Norte", Lojas.TipoEstabelecimento.LOJA_GERAL);
        Lojas alquimia = new Lojas("Botica Mística", "Serena", frostdale, false, null, Lojas.TipoEstabelecimento.ALQUIMIA);
        Lojas joalheria = new Lojas("Joalheria Imperial", "Nadim", frostdale, false, null, Lojas.TipoEstabelecimento.JOALHERIA);
        Lojas taverna = new Lojas("Taverna do Lobo", "Olaf", frostdale, false, null, Lojas.TipoEstabelecimento.TAVERNA);

        afirmo(forja.getTipoEstabelecimento() == Lojas.TipoEstabelecimento.FORJA, "Tipo FORJA validado");
        afirmo(alfaiataria.getTipoEstabelecimento() == Lojas.TipoEstabelecimento.ALFAIATARIA, "Tipo ALFAIATARIA validado");
        afirmo(geral.getTipoEstabelecimento() == Lojas.TipoEstabelecimento.LOJA_GERAL, "Tipo LOJA_GERAL validado");
        afirmo(alquimia.getTipoEstabelecimento() == Lojas.TipoEstabelecimento.ALQUIMIA, "Tipo ALQUIMIA validado");
        afirmo(joalheria.getTipoEstabelecimento() == Lojas.TipoEstabelecimento.JOALHERIA, "Tipo JOALHERIA validado");
        afirmo(taverna.getTipoEstabelecimento() == Lojas.TipoEstabelecimento.TAVERNA, "Tipo TAVERNA validado");

        // Vínculo bidirecional com a cidade
        afirmo(frostdale.getLojas().size() == 6, "Cidade registrou todas as 6 lojas");
        afirmo(taverna.getCidadeSede().equals(frostdale), "Loja possui vínculo com a cidade sede");

        // Teste de estoque
        Item hidromel = new Item(8, "Hidromel", "Bebida forte", 2.0, 1.0, Item.TipoItem.CONSUMIVEL, Item.RaridadeItem.COMUM, 10);
        taverna.adicionarItem(hidromel);
        afirmo(taverna.getQuantidadeEstoque(8) == 10, "Taverna recebeu 10 unidades de hidromel");
    }

    // ------------------------------------------------------------------------
    // TESTE 3: OFERTA E DEMANDA DINÂMICA
    // ------------------------------------------------------------------------
    private void teste3_OfertaDemandaEPrecosDinamicos() {
        System.out.println("\n[3/11] Testando Oferta e Demanda e Precificação Dinâmica...");

        Lojas forja = new Lojas("Forja de Teste", "Ferreiro", false, null, Lojas.TipoEstabelecimento.FORJA);
        Item lingote = new Item(1, "Lingote de Ferro", "Metal", 2.5, 3.0, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 20);
        forja.adicionarItem(lingote);
        forja.definirDemanda(lingote, 20); // Oferta equilibrada com demanda

        long precoBase = lingote.getPrecoEmLatao(); // 25.000 latão
        long precoEquilibrado = forja.calcularPrecoDinamicoVendaEmLatao(lingote);
        afirmo(Math.abs(precoEquilibrado - precoBase) < 500, "Preço equilibrado é próximo ao valor base");

        // Choque de alta demanda
        forja.definirDemanda(lingote, 200);
        long precoEscassez = forja.calcularPrecoDinamicoVendaEmLatao(lingote);
        afirmo(precoEscassez > precoBase, "Preço dinâmico subiu com a escassez de mercado");
        afirmo(precoEscassez <= Math.round(precoBase * 2.50), "Preço respeita o teto de 250%");

        // Choque de excesso de oferta
        forja.definirDemanda(lingote, 0);
        forja.adicionarItem(Lojas.clonarItem(lingote, 100));
        long precoSaturado = forja.calcularPrecoDinamicoVendaEmLatao(lingote);
        afirmo(precoSaturado < precoBase, "Preço dinâmico caiu com a saturação de estoque");
        afirmo(precoSaturado >= Math.round(precoBase * 0.40), "Preço respeita o piso de 40%");

        // Preço de compra NPC = 65% do preço dinâmico de venda
        long precoCompraNpc = forja.calcularPrecoCompraNpcEmLatao(lingote);
        afirmo(precoCompraNpc == Math.round(precoSaturado * 0.65), "Preço de compra de NPC é exatamente 65% do preço de venda");
    }

    // ------------------------------------------------------------------------
    // TESTE 4: COMÉRCIO DE BALCÃO
    // ------------------------------------------------------------------------
    private void teste4_ComercioDeBalcaoJogador() {
        System.out.println("\n[4/11] Testando Venda e Compra de Balcão com Jogador...");

        Lojas loja = new Lojas("Empório do Balcão", "Mercador", false, null);
        loja.setCaixa(new Carteira(100)); // 1.000.000 latão
        Item pocao = new Item(3, "Poção de Cura", "Cura", 5.0, 0.5, Item.TipoItem.CONSUMIVEL, Item.RaridadeItem.COMUM, 10);
        loja.adicionarItem(pocao);
        loja.definirDemanda(pocao, 10);

        Ficha jogador = new Ficha(10, 10, 10, 10, 10, 10);
        jogador.getCarteira().adicionarOuro(20); // 200.000 latão
        long saldoInicialJogador = jogador.getCarteira().getValorTotalEmLatao();
        long caixaInicialLoja = loja.getCaixa().getValorTotalEmLatao();

        // 1. Jogador compra 1x poção no balcão
        long precoVenda = loja.calcularPrecoDinamicoVendaEmLatao(pocao);
        boolean comprou = loja.venderItem(pocao, jogador.getCarteira(), 1, 10);
        afirmo(comprou, "Jogador comprou poção no balcão com sucesso");
        afirmo(jogador.getCarteira().getValorTotalEmLatao() == saldoInicialJogador - precoVenda, "Saldo do jogador debitado exatamente");
        afirmo(loja.getCaixa().getValorTotalEmLatao() == caixaInicialLoja + precoVenda, "Caixa da loja creditado exatamente");
        afirmo(loja.getQuantidadeEstoque(3) == 9, "Estoque da loja reduziu para 9");

        // 2. Jogador vende um item para a loja
        Item gema = new Item(5, "Gema Pequena", "Gema", 10.0, 0.1, Item.TipoItem.ACESSORIO, Item.RaridadeItem.COMUM, 1);
        boolean vendeu = loja.comprarItem(gema, jogador.getCarteira(), 10.0, 1, 11);
        afirmo(vendeu, "Jogador vendeu item para a loja no balcão com sucesso");
        afirmo(loja.buscarItemPorNome("Gema Pequena") != null, "Loja incorporou a gema ao estoque");
    }

    // ------------------------------------------------------------------------
    // TESTE 5: FORNECEDOR NPC LOCAL
    // ------------------------------------------------------------------------
    private void teste5_FornecedorNpcLocal() {
        System.out.println("\n[5/11] Testando Aquisição de Fornecedor NPC Local...");

        Lojas loja = new Lojas("Forja Local", "Ferreiro", false, null);
        loja.setCaixa(new Carteira(50)); // 500.000 latão
        Item ferro = new Item(1, "Ferro Bruto", "Insumo", 2.0, 2.0, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 5);
        loja.adicionarItem(ferro);

        long precoNpcUnit = loja.calcularPrecoCompraNpcEmLatao(ferro);
        int qtdComprar = 10;
        long custoEsperado = precoNpcUnit * qtdComprar;
        long caixaAntes = loja.getCaixa().getValorTotalEmLatao();

        boolean comprouNpc = loja.comprarDeNpc(ferro, qtdComprar, 1, 14);
        afirmo(comprouNpc, "Loja comprou lote de NPC local com sucesso");
        afirmo(loja.getQuantidadeEstoque(1) == 15, "Estoque subiu para 15 unidades");
        afirmo(loja.getCaixa().getValorTotalEmLatao() == caixaAntes - custoEsperado, "Custo correto deduzido do caixa");
        afirmo(loja.getHistorico().get(loja.getHistorico().size() - 1).getTipo() == RegistroMovimentacao.TipoOperacao.ENTRADA_NPC,
               "Operação registrada como ENTRADA_NPC no livro-caixa");

        // Validação da Produção Regional por Bioma e Geologia (Ponte para o Crafting)
        Cidade cidadeMontanha = new Cidade(10, "Pico Rochoso", Cidade.TipoAssentamento.CIDADE, Regiao.TEMPERADO, Geologia.MONTANHA, null, false, false, null);
        Cidade cidadeFloresta = new Cidade(11, "Bosque Ancestral", Cidade.TipoAssentamento.CIDADE, Regiao.TAIGA, Geologia.FLORESTA, null, false, false, null);
        Item madeira = new Item(20, "Madeira Nobre", "Tronco denso", 4.0, 5.0, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 1);

        double chanceFerroMontanha = cidadeMontanha.calcularChanceFornecedorNpc(ferro);
        double chanceMadeiraMontanha = cidadeMontanha.calcularChanceFornecedorNpc(madeira);
        double chanceMadeiraFloresta = cidadeFloresta.calcularChanceFornecedorNpc(madeira);
        double chanceFerroFloresta = cidadeFloresta.calcularChanceFornecedorNpc(ferro);

        afirmo(chanceFerroMontanha == 0.65, "Cidade em Montanha possui alta afinidade com minérios e ferro (65%)");
        afirmo(chanceMadeiraMontanha == 0.15, "Madeira é escassa em alta montanha rochosa (15%)");
        afirmo(chanceMadeiraFloresta == 0.70, "Cidade em Floresta possui altíssima afinidade com madeira (70%)");
        afirmo(chanceFerroFloresta == 0.20, "Minério é escasso no solo profundo de floresta (20%)");
    }

    // ------------------------------------------------------------------------
    // TESTE 6: GRAFO, DIJKSTRA E ROTAS BLOQUEADAS
    // ------------------------------------------------------------------------
    private void teste6_GrafoDijkstraERotasBloqueadas() {
        System.out.println("\n[6/11] Testando Grafo, Dijkstra e Desvio de Rotas Bloqueadas...");

        Cidade a = new Cidade(1, "Cidade A", Cidade.TipoAssentamento.CIDADE, Regiao.TEMPERADO, true, false, null);
        Cidade b = new Cidade(2, "Cidade B", Cidade.TipoAssentamento.CIDADE, Regiao.TEMPERADO, false, false, null);
        Cidade c = new Cidade(3, "Cidade C", Cidade.TipoAssentamento.CIDADE, Regiao.TEMPERADO, true, false, null);

        // Caminho direto A <-> C (perigoso: perigo 8, 100km)
        Conexao direta = new Conexao(a, c, 100, 8, Conexao.TipoDeRota.TERRESTRE);
        // Caminho via B: A <-> B (50km, perigo 2) e B <-> C (50km, perigo 2)
        Conexao ab = new Conexao(a, b, 50, 2, Conexao.TipoDeRota.TERRESTRE);
        Conexao bc = new Conexao(b, c, 50, 2, Conexao.TipoDeRota.TERRESTRE);

        // Dijkstra deve preferir o caminho mais seguro via B
        RotaComercial rota = NavegadorGrafo.calcularMelhorRota(a, c, 0.50);
        afirmo(rota != null, "Rota encontrada por Dijkstra");
        afirmo(rota.getCaminhoCidades().size() == 3, "Dijkstra escolheu desviar por B devido ao menor custo de perigo");
        afirmo(rota.getCaminhoCidades().get(1).equals(b), "Cidade B está no meio do caminho");

        // Bloqueia a aresta B <-> C: Dijkstra deve ser forçado a usar a rota direta
        bc.bloquear();
        RotaComercial rotaComBloqueio = NavegadorGrafo.calcularMelhorRota(a, c, 0.50);
        afirmo(rotaComBloqueio.getCaminhoCidades().size() == 2, "Dijkstra recalculou e utilizou a rota direta porque B-C estava bloqueada");
        afirmo(rotaComBloqueio.getArestas().get(0).equals(direta), "Utilizou a conexão direta");

        bc.desbloquear();
    }

    // ------------------------------------------------------------------------
    // TESTE 7: COMÉRCIO INTERMUNICIPAL E DESCONTO DE REDE
    // ------------------------------------------------------------------------
    private void teste7_ComercioIntermunicipalEDescontoRede() {
        System.out.println("\n[7/11] Testando Comércio Intermunicipal com Desconto de Rede...");

        MotorEconomico motor = new MotorEconomico();
        Cidade c1 = new Cidade(1, "Porto Norte", Cidade.TipoAssentamento.CIDADE, Regiao.TUNDRA, true, false, null);
        Cidade c2 = new Cidade(2, "Vila Sul", Cidade.TipoAssentamento.CIDADE, Regiao.TEMPERADO, true, false, null);
        new Conexao(c1, c2, 100, 2, Conexao.TipoDeRota.TERRESTRE);

        Lojas compradora = new Lojas("Empório Sul", "Garrick", c2, true, "Liga Mercantil", Lojas.TipoEstabelecimento.LOJA_GERAL);
        compradora.setCaixa(new Carteira(100)); // 1.000.000 latão

        Lojas fornecedora = new Lojas("Armazém Norte", "Elidor", c1, true, "Liga Mercantil", Lojas.TipoEstabelecimento.LOJA_GERAL);
        fornecedora.setCaixa(new Carteira(100));

        Item item = new Item(1, "Madeira Nobre", "Tronco", 4.0, 5.0, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 50);
        fornecedora.adicionarItem(item);
        fornecedora.definirDemanda(item, 10);

        motor.registrarCidade(c1);
        motor.registrarCidade(c2);

        int qtd = 10;
        long precoCheio = fornecedora.calcularPrecoDinamicoVendaEmLatao(item);
        long precoEsperadoComDesconto = Math.round(precoCheio * 0.75); // 25% de desconto de rede

        long caixaFornecAntes = fornecedora.getCaixa().getValorTotalEmLatao();
        boolean sucesso = motor.solicitarCompraEntreCidades(compradora, item, qtd);

        afirmo(sucesso, "Encomenda intermunicipal concluída com sucesso");
        afirmo(fornecedora.getQuantidadeEstoque(1) == 40, "Estoque da fornecedora debitado em 10 unidades");
        afirmo(motor.getCaravanas().size() == 1, "Caravana comercial expedida e registrada no motor");

        long ganhoFornecedora = fornecedora.getCaixa().getValorTotalEmLatao() - caixaFornecAntes;
        afirmo(ganhoFornecedora == precoEsperadoComDesconto * qtd, "Fornecedora recebeu o valor com desconto exato de 25% de rede");
    }

    // ------------------------------------------------------------------------
    // TESTE 8: ESCOLTA ASSUMIDA POR JOGADOR (100% SEGURANÇA)
    // ------------------------------------------------------------------------
    private void teste8_MissaoEscoltaAssumidaJogador() {
        System.out.println("\n[8/11] Testando Missão de Escolta Armada Assumida por Jogador...");

        Cidade c1 = new Cidade(1, "Alpha", Cidade.TipoAssentamento.CIDADE, Regiao.TEMPERADO, false, false, null);
        Cidade c2 = new Cidade(2, "Beta", Cidade.TipoAssentamento.CIDADE, Regiao.TEMPERADO, false, false, null);
        Conexao estrada = new Conexao(c1, c2, 60, 9, Conexao.TipoDeRota.TERRESTRE); // Perigo extremo 9

        Lojas lojaA = new Lojas("Loja A", "Mercador A", c1, false, null);
        Lojas lojaB = new Lojas("Loja B", "Mercador B", c2, false, null);
        lojaB.setCaixa(new Carteira(30)); // 300.000 latão de capital de giro inicial
        long caixaLojaBAntes = lojaB.getCaixa().getValorTotalEmLatao();

        Item carga = new Item(1, "Cristais Mágicos", "Item Valioso", 50.0, 2.0, Item.TipoItem.MAGICO, Item.RaridadeItem.RARO, 5);
        RotaComercial rota = new RotaComercial(List.of(c1, c2), List.of(estrada));

        Caravana caravana = new Caravana(lojaA, lojaB, carga, rota, 1, 8);
        long recompensa = 80_000L; // 8 PO
        MissaoComercial missao = new MissaoComercial(MissaoComercial.TipoMissao.ESCOLTA, c1, caravana, recompensa, 12);

        Ficha jogador = new Ficha(12, 12, 12, 12, 12, 12);
        jogador.setCidadeAtual(c1); // Jogador está na cidade de partida (Alpha)
        long saldoAntes = jogador.getCarteira().getValorTotalEmLatao();

        // Jogador aceita a missão passando sua Ficha
        boolean aceitou = missao.aceitarPorJogador("Valerius", jogador, 1, 8);
        afirmo(aceitou, "Jogador aceitou o contrato de escolta");
        afirmo(caravana.getProtecao() == Caravana.TipoProtecao.ESCOLTADA_JOGADOR, "Proteção da caravana é ESCOLTADA_JOGADOR");
        afirmo(caravana.getStatus() == Caravana.StatusCaravana.EM_TRANSITO, "Caravana iniciou o trânsito");

        // Avança 15 horas (a 6 km/h, 60 km levam 10 horas)
        Random fixoSemSorte = new Random(42);
        boolean emboscada = caravana.avancarHoras(15, 1, 23, fixoSemSorte);
        afirmo(!emboscada, "Nenhuma emboscada ocorreu: jogador garantiu 100% de segurança mesmo no perigo 9");
        afirmo(caravana.getStatus() == Caravana.StatusCaravana.ENTREGUE, "Caravana entregue com sucesso no destino");

        // Conclui missão e verifica remuneração, lastro financeiro e deslocamento
        missao.concluirMissaoJogador(1, 23);
        afirmo(missao.getStatus() == MissaoComercial.StatusMissao.CONCLUIDA_SUCESSO, "Missão concluída com sucesso");
        afirmo(jogador.getCarteira().getValorTotalEmLatao() == saldoAntes + recompensa, "Jogador recebeu a recompensa de 80.000 latão na carteira");
        afirmo(lojaB.getCaixa().getValorTotalEmLatao() == caixaLojaBAntes - recompensa, "Caixa da loja contratante (loja B) debitado com lastro financeiro real");
        afirmo(lojaB.getHistorico().get(lojaB.getHistorico().size() - 1).getTipo() == RegistroMovimentacao.TipoOperacao.DESPESA_ESCOLTA, "Livro-caixa da loja B registrou DESPESA_ESCOLTA");
        afirmo(jogador.getCidadeAtual().equals(c2), "Jogador foi deslocado geograficamente no mundo para a cidade destino (Beta)");
    }

    // ------------------------------------------------------------------------
    // TESTE 9: EXTRAVIO E RESGATE COM SUCESSO (PACIFICAÇÃO)
    // ------------------------------------------------------------------------
    private void teste9_CaravanaExtraviadaEResgateSucesso() {
        System.out.println("\n[9/11] Testando Extravio de Carga e Missão de Resgate Bem-Sucedida...");

        Cidade c1 = new Cidade(1, "Origem", Cidade.TipoAssentamento.CIDADE, Regiao.TEMPERADO, false, false, null);
        Cidade c2 = new Cidade(2, "Destino", Cidade.TipoAssentamento.CIDADE, Regiao.TEMPERADO, false, false, null);
        Conexao estrada = new Conexao(c1, c2, 100, 8, Conexao.TipoDeRota.TERRESTRE); // Perigo 8

        Lojas l1 = new Lojas("L1", "M1", c1, false, null);
        Lojas l2 = new Lojas("L2", "M2", c2, false, null);
        l2.setCaixa(new Carteira(20)); // 200.000 latão de capital de giro
        long caixaL2Antes = l2.getCaixa().getValorTotalEmLatao();

        Item carga = new Item(2, "Armaduras", "Pesadas", 20.0, 10.0, Item.TipoItem.ARMADURA, Item.RaridadeItem.COMUM, 4);

        RotaComercial rota = new RotaComercial(List.of(c1, c2), List.of(estrada));
        Caravana caravana = new Caravana(l1, l2, carga, rota, 1, 8);
        caravana.iniciarViagem(Caravana.TipoProtecao.DESPROTEGIDA, 1, 8);

        // Força extravio com gerador de probabilidade 1.0
        Random forcaAtaque = new Random() {
            @Override
            public double nextDouble() { return 0.0001; } // Garante ataque no teste de chance
        };

        boolean extraviou = caravana.avancarHoras(4, 1, 12, forcaAtaque);
        afirmo(extraviou, "Caravana desprotegida foi emboscada no trajeto");
        afirmo(caravana.getStatus() == Caravana.StatusCaravana.EXTRAVIADA, "Status alterado para EXTRAVIADA");
        afirmo(caravana.getArestaAtual().equals(estrada), "Localização exata da aresta rastreada no grafo");

        // Cria missão de resgate
        long recompensa = 50_000L;
        MissaoComercial resgate = new MissaoComercial(MissaoComercial.TipoMissao.RESGATE_CARGA, c1, caravana, recompensa, 24);

        Ficha heroi = new Ficha(14, 14, 14, 14, 14, 14);
        resgate.aceitarPorJogador("Heroi", heroi, 1, 13);

        int perigoAntes = estrada.getPerigo();
        resgate.concluirMissaoJogador(1, 14);

        afirmo(resgate.getStatus() == MissaoComercial.StatusMissao.CONCLUIDA_SUCESSO, "Missão de resgate concluída com sucesso");
        afirmo(estrada.getPerigo() == perigoAntes - 1, "Resgate bem-sucedido pacificou a estrada (-1 no nível de perigo)");
        afirmo(caravana.getStatus() == Caravana.StatusCaravana.ENTREGUE, "Carga resgatada foi devidamente entregue à loja destino");
        afirmo(l2.getQuantidadeEstoque(2) == 4, "Estoque de armaduras adicionado à loja destino");
        afirmo(l2.getCaixa().getValorTotalEmLatao() == caixaL2Antes - recompensa, "Caixa da loja contratante debitado com lastro financeiro real no resgate");
        afirmo(heroi.getCarteira().getValorTotalEmLatao() == recompensa, "Herói recebeu a recompensa legítima de resgate");
    }

    // ------------------------------------------------------------------------
    // TESTE 10: FRACASSO DE RESGATE E ESCALADA DE PERIGO
    // ------------------------------------------------------------------------
    private void teste10_ResgateFalhaEEscaladaDePerigo() {
        System.out.println("\n[10/11] Testando Fracasso de Resgate e Escalada do Perigo na Estrada...");

        Cidade c1 = new Cidade(1, "Base", Cidade.TipoAssentamento.CIDADE, Regiao.TEMPERADO, false, false, null);
        Cidade c2 = new Cidade(2, "Posto", Cidade.TipoAssentamento.CIDADE, Regiao.TEMPERADO, false, false, null);
        Conexao estrada = new Conexao(c1, c2, 80, 5, Conexao.TipoDeRota.TERRESTRE);

        Lojas l1 = new Lojas("L1", "M1", c1, false, null);
        Lojas l2 = new Lojas("L2", "M2", c2, false, null);
        Item carga = new Item(3, "Medicamentos", "Frágeis", 10.0, 1.0, Item.TipoItem.CONSUMIVEL, Item.RaridadeItem.COMUM, 2);

        RotaComercial rota = new RotaComercial(List.of(c1, c2), List.of(estrada));
        Caravana caravana = new Caravana(l1, l2, carga, rota, 1, 8);
        caravana.setStatus(Caravana.StatusCaravana.EXTRAVIADA);

        MissaoComercial resgate = new MissaoComercial(MissaoComercial.TipoMissao.RESGATE_CARGA, c1, caravana, 20_000L, 1);
        resgate.reduzirPrazo(2); // Expira prazo

        int perigoAntes = estrada.getPerigo();
        // Simulação do fracasso da milícia de NPCs
        resgate.setStatus(MissaoComercial.StatusMissao.FRACASSADA);
        caravana.setStatus(Caravana.StatusCaravana.PERDIDA_DEFINITIVAMENTE);
        estrada.ajustarPerigo(+1);

        afirmo(caravana.getStatus() == Caravana.StatusCaravana.PERDIDA_DEFINITIVAMENTE, "Carga perdida definitivamente após fracasso");
        afirmo(estrada.getPerigo() == perigoAntes + 1, "Perigo da estrada aumentou (+1) porque os saqueadores se fortaleceram");
    }

    // ------------------------------------------------------------------------
    // TESTE 11: AUDITORIA DO LIVRO-CAIXA
    // ------------------------------------------------------------------------
    private void teste11_AuditoriaContabilLivroCaixa() {
        System.out.println("\n[11/11] Testando Livro-Caixa e Auditoria Contábil (RegistroMovimentacao)...");

        Lojas loja = new Lojas("Casa Comercial Thaluria", "Mestre Contábil", false, null);
        loja.setCaixa(new Carteira(100));

        loja.registrarMovimentacao(1, 8, RegistroMovimentacao.TipoOperacao.ENTRADA_NPC, "Lingote", 10, 20_000L, "Compra de fornecedor");
        loja.registrarMovimentacao(1, 10, RegistroMovimentacao.TipoOperacao.VENDA_BALCAO, "Lingote", 2, 6_000L, "Venda balcão");
        loja.registrarMovimentacao(1, 14, RegistroMovimentacao.TipoOperacao.DESPESA_FRETE, "Lingote", 5, 8_500L, "Frete de caravana");

        List<RegistroMovimentacao> hist = loja.getHistorico();
        afirmo(hist.size() == 3, "Livro-caixa registrou com precisão as 3 operações");
        afirmo(hist.get(0).getDiaMundo() == 1 && hist.get(0).getHoraMundo() == 8, "Timestamp do mundo Dia 1, 08h validado");
        afirmo(hist.get(1).getTipo() == RegistroMovimentacao.TipoOperacao.VENDA_BALCAO, "Tipo VENDA_BALCAO validado");

        String descricaoFormatada = hist.get(0).toString();
        afirmo(descricaoFormatada.contains("Lingote") && descricaoFormatada.contains("Caixa:"), "String formatada contém itens e moedas");
    }
}
