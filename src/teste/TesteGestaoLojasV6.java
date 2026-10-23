package teste;

import economia.*;
import geografia.Aquifero;
import geografia.Cidade;
import geografia.Geologia;
import geografia.Regiao;
import java.util.*;

/**
 * Suíte de Testes Automatizados para a Camada v6:
 * Gestão Avançada de Lojas, Guilda Regional, Subsistemas Concretos e Base do Jogador em Thaluria RPG.
 */
public class TesteGestaoLojasV6 {

    private static int totalTestes = 0;
    private static int testesPassados = 0;

    public static void main(String[] args) {
        System.out.println("======================================================================");
        System.out.println("   🏬 INICIANDO SUITE DE TESTES DE GESTÃO DE LOJAS v6 (THALURIA)      ");
        System.out.println("======================================================================\n");

        testarSubsistemasConcretosEFiltros();
        testarGuildaRegionalEInsumosRaros();
        testarGeologiaEGeradorMineracao();
        testarTempoCraftPrateleiraERoletaLiquidacao();
        testarComercioB2BAtacado();
        testarCapacidadeArmazemEPerecibilidade();
        testarCamaraConservacaoTriplicada();
        testarArvoreEvolucaoETalentosLojista();
        testarIADeterministicaECatalogoDescobertas();
        testarContratosEncomendaUrgente();
        testarBaseJogadorEEstufaClimatizada();

        System.out.println("\n======================================================================");
        if (testesPassados == totalTestes) {
            System.out.println(String.format("   ✨ RESULTADO FINAL: %d/%d TESTES DA CAMADA v6 PASSARAM COM 100%% DE SUCESSO!",
                    testesPassados, totalTestes));
        } else {
            System.out.println(String.format("   ⚠️ RESULTADO: %d/%d testes passaram (%d FALHARAM).",
                    testesPassados, totalTestes, (totalTestes - testesPassados)));
        }
        System.out.println("======================================================================");
    }

    private static void afirme(boolean condicao, String mensagem) {
        totalTestes++;
        if (condicao) {
            testesPassados++;
            System.out.println("  ✓ [PASSOU] " + mensagem);
        } else {
            System.err.println("  ✗ [FALHOU] " + mensagem);
            throw new AssertionError("Falha no teste: " + mensagem);
        }
    }

    // 1. Bloco 1: Subsistemas Concretos (X, Y, Z) e Filtros de Receita
    private static void testarSubsistemasConcretosEFiltros() {
        System.out.println("[1/11] Testando Subsistemas Concretos (X, Y, Z) e Modos de Filtro...");

        Item couroJavaliX = new Item(201, "Couro de Javali", 5.0);
        couroJavaliX.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.COURO_PELE, 1, true, "de Javali"));

        Item couroCervoY = new Item(202, "Couro de Cervo", 5.0);
        couroCervoY.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.COURO_PELE, 1, true, "de Cervo"));

        Item couroChacalZ = new Item(203, "Couro de Chacal", 5.0);
        couroChacalZ.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.COURO_PELE, 1, true, "de Chacal"));

        // A) Modo Aberto
        FiltroInsumoReceita aberto = FiltroInsumoReceita.qualquerDoSubsistema(FichaMaterial.SubtipoMaterial.COURO_PELE, 1, 1, true, false);
        afirme(aberto.aceitaMaterial(couroJavaliX), "Modo Aberto aceita Item X (Javali)");
        afirme(aberto.aceitaMaterial(couroCervoY), "Modo Aberto aceita Item Y (Cervo)");
        afirme(aberto.aceitaMaterial(couroChacalZ), "Modo Aberto aceita Item Z (Chacal)");

        // B) Modo Subconjunto Restrito (X ou Y)
        FiltroInsumoReceita restrito = FiltroInsumoReceita.apenasEntreOpcoes(FichaMaterial.SubtipoMaterial.COURO_PELE, 1, 1, Set.of(201, 202), true, false);
        afirme(restrito.aceitaMaterial(couroJavaliX), "Modo Restrito aceita X (Javali)");
        afirme(restrito.aceitaMaterial(couroCervoY), "Modo Restrito aceita Y (Cervo)");
        afirme(!restrito.aceitaMaterial(couroChacalZ), "Modo Restrito REJEITA Z (Chacal)");

        // C) Modo Item Exato (só X)
        FiltroInsumoReceita exato = FiltroInsumoReceita.itemEspecificoExato(201, FichaMaterial.SubtipoMaterial.COURO_PELE, 1, 1, true, false);
        afirme(exato.aceitaMaterial(couroJavaliX), "Modo Exato aceita X");
        afirme(!exato.aceitaMaterial(couroCervoY), "Modo Exato rejeita Y");
        afirme(!exato.aceitaMaterial(couroChacalZ), "Modo Exato rejeita Z");
    }

    // 2. Bloco 2: Guilda Regional, Trava de Bioma e Regra de Insumos Raros
    private static void testarGuildaRegionalEInsumosRaros() {
        System.out.println("\n[2/11] Testando Guilda Regional, Trava Ecológica e Regra de Insumos Raros...");

        Cidade sunreach = new Cidade(1, "Sunreach", Cidade.TipoAssentamento.CIDADE,
                Regiao.DESERTO, Geologia.CAVERNA, Aquifero.RIO, false, false, null);
        SedeGuildaRegional guilda = new SedeGuildaRegional(sunreach);
        sunreach.setGuildaRegional(guilda);

        Item couroLocal = new Item(203, "Couro de Chacal", 4.0);
        couroLocal.setOrigemGeografica(Regiao.DESERTO, Geologia.CAVERNA, Aquifero.RIO);
        couroLocal.setRaridade(Item.RaridadeItem.COMUM);

        Item insumoTundra = new Item(204, "Pele de Lobo Polar", 10.0);
        insumoTundra.setOrigemGeografica(Regiao.TUNDRA, Geologia.FLORESTA, Aquifero.RIO);
        insumoTundra.setRaridade(Item.RaridadeItem.COMUM);

        // Aventureiros NPCs tentam fornecer
        afirme(guilda.adicionarItemAventureiroLocal(couroLocal), "Guilda aceitou item de caçadores locais nativo do Deserto");
        afirme(!guilda.adicionarItemAventureiroLocal(insumoTundra), "Guilda bloqueou insumo da Tundra trazido por NPC comum");

        // Insumo Raro/Mítico: não entra sozinho de NPC comum
        Item escamaDragaoMitica = new Item(205, "Escamas de Dragão Ancestral", 200.0);
        escamaDragaoMitica.setOrigemGeografica(Regiao.DESERTO, Geologia.CAVERNA, Aquifero.RIO);
        escamaDragaoMitica.setRaridade(Item.RaridadeItem.MITICO);

        afirme(!guilda.adicionarItemAventureiroLocal(escamaDragaoMitica), "Insumo Mítico foi bloqueado de entrar por NPC comum");
        afirme(guilda.adicionarItemVendidoPorJogador(escamaDragaoMitica), "Insumo Mítico entrou no estoque da Guilda quando vendido por Jogador");
    }

    // 3. Bloco 3: Geologia de Mineração e Árvore do Ferreiro
    private static void testarGeologiaEGeradorMineracao() {
        System.out.println("\n[3/11] Testando Geologia de Mineração e Salários dos Mineradores...");

        Cidade ironhold = new Cidade(2, "Ironhold", Cidade.TipoAssentamento.CIDADE,
                Regiao.TAIGA, Geologia.MONTANHA, Aquifero.RIO, false, false, null);
        Item veioFerro = new Item(301, "Minério de Ferro", 5.0);
        veioFerro.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.MINERIO_BRUTO, 1));
        ironhold.adicionarVeioMineral(veioFerro);

        Lojas forja = new Lojas("Forja de Ferro", "Grimnir", ironhold, false, null, Lojas.TipoEstabelecimento.FORJA);
        forja.setNivelLoja(2);
        forja.getCaixa().receberLatao(500_000L); // 50 PO

        GeradorMineracaoForja gerador = forja.getGeradorMineracao();
        afirme(gerador != null, "Forja inicializou com gerador de mineração");

        gerador.evoluirGerador(forja, 1, 8); // Nv 1 (Picaretas)
        afirme(gerador.getNivelAtual() == GeradorMineracaoForja.NivelGeradorMina.NIVEL_1_ESCAVADORES, "Evoluiu para Nível 1");

        List<Item> minerios = gerador.extrairMineriosDoDia(forja, ironhold.getVeiosMineraisDisponiveis(), 1, 10, new Random(42));
        afirme(!minerios.isEmpty(), "Extraiu minérios com salário pago");
        afirme(forja.getQuantidadeEstoque(301) > 0, "Minério depositado no estoque da forja");
    }

    // 4. Bloco 4: Tempo de Craft, Fila de Ordens e Roleta de Liquidação
    private static void testarTempoCraftPrateleiraERoletaLiquidacao() {
        System.out.println("\n[4/11] Testando OrdemProducao em Dias de Mesa e Roleta de Liquidação (1d100)...");

        Cidade cidade = new Cidade(3, "Valdorian", Cidade.TipoAssentamento.CIDADE,
                Regiao.TEMPERADO, Geologia.PLANICIE, Aquifero.RIO, false, false, null);
        Lojas alfaiataria = new Lojas("Tecelagem Real", "Mestre", cidade, false, null, Lojas.TipoEstabelecimento.ALFAIATARIA);
        alfaiataria.getCaixa().receberLatao(200_000L);

        MoldeReceita molde = MoldeReceita.criarMoldeArmadura(101, "Gibão de Couro", Lojas.TipoEstabelecimento.ALFAIATARIA,
                1, 2.0, TamanhoItem.MEDIO, 2_000L, List.of(FichaArmadura.SlotArmadura.PEITO), 3, 20);
        molde.adicionarFiltroInsumo(FiltroInsumoReceita.qualquerDoSubsistema(FichaMaterial.SubtipoMaterial.COURO_PELE, 1, 2, true, false));
        molde.setDiasParaConcluirBase(3); // 3 dias de mesa

        Item couro = new Item(201, "Couro Curtido", 2.0);
        couro.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.COURO_PELE, 1));
        couro.setQuantidade(5);
        alfaiataria.adicionarItem(couro);

        // Inicia Ordem de Produção
        boolean iniciou = alfaiataria.iniciarOrdemProducao(molde, 1, 8);
        afirme(iniciou, "Iniciou Ordem de Produção com sucesso");
        afirme(alfaiataria.getFilaProducao().size() == 1, "Ordem registrada na fila da loja");
        afirme(alfaiataria.getQuantidadeEstoque(201) == 3, "Consumiu 2 couros na abertura da ordem (5 - 2 = 3)");

        OrdemProducao ordem = alfaiataria.getFilaProducao().get(0);
        afirme(ordem.getDiasRestantes() == 3, "Ordem exige 3 dias de mesa");

        // Dia 1
        ordem.avancarDia(2, 8);
        afirme(ordem.getDiasRestantes() == 2 && !ordem.isConcluida(), "Dia 1 decorrido, restam 2 dias");

        // Dia 2
        ordem.avancarDia(3, 8);
        afirme(ordem.getDiasRestantes() == 1 && !ordem.isConcluida(), "Dia 2 decorrido, resta 1 dia");

        // Dia 3: Conclusão
        ordem.avancarDia(4, 8);
        afirme(ordem.isConcluida(), "Dia 3 decorrido: Ordem concluída!");
        afirme(ordem.getItemProduzido() != null, "Item físico gerado");
        afirme(alfaiataria.getLotesPrateleira().size() == 1, "Item foi entregue diretamente na prateleira da loja");

        // Roleta de Liquidação na Prateleira
        LotePrateleira lote = alfaiataria.getLotesPrateleira().get(0);
        lote.setDiasEncalhado(7); // Simula 7 dias encalhado

        // Teste de roleta: rolagem 90 (Colecionador 1.40x)
        Random rndColecionador = new Random() {
            @Override public int nextInt(int bound) { return 89; } // Rola 90
        };
        lote.executarRoletaLiquidacao(alfaiataria, 5, 10, rndColecionador);
        afirme(lote.getUltimoResultado() == LotePrateleira.ResultadoRoleta.LUCRO_EXTRA_COLECIONADOR,
                "Roleta 90 resultou em Lucro Extra Colecionador (1.40x)");
        afirme(lote.isVendidoOuLiquidado(), "Lote liquidado e removido");
    }

    // 5. Bloco 5: Comércio B2B Atacado (-15%)
    private static void testarComercioB2BAtacado() {
        System.out.println("\n[5/11] Testando Comércio B2B Inter-Lojas com Desconto de Atacado (15% off)...");

        Cidade cidade = new Cidade(4, "Oakhaven", Cidade.TipoAssentamento.CIDADE,
                Regiao.TEMPERADO, Geologia.FLORESTA, Aquifero.RIO, false, false, null);

        Lojas forja = new Lojas("Forja de Oak", "Ferreiro", cidade, false, null, Lojas.TipoEstabelecimento.FORJA);
        Lojas lojaGeral = new Lojas("Empório Central", "Mercador", cidade, false, null, Lojas.TipoEstabelecimento.LOJA_GERAL);

        forja.getCaixa().receberLatao(100_000L);
        lojaGeral.getCaixa().receberLatao(500_000L);

        Item espada = new Item(401, "Espada de Ferro", 10.0); // 10 PO = 100.000 latão
        espada.setTipo(Item.TipoItem.ARMA_BRANCA);
        espada.setQuantidade(3);
        forja.adicionarItemProntoPrateleira(espada);

        long saldoGeralAntes = lojaGeral.getCaixa().getValorTotalEmLatao();
        long saldoForjaAntes = forja.getCaixa().getValorTotalEmLatao();

        lojaGeral.executarComprasB2B(List.of(forja, lojaGeral), 1, 8);

        afirme(forja.getQuantidadeEstoque(401) < 3, "Forja vendeu lote no atacado para a Loja Geral");
        afirme(lojaGeral.getQuantidadeEstoque(401) > 0, "Loja Geral recebeu o estoque na sua prateleira");
        afirme(forja.getCaixa().getValorTotalEmLatao() > saldoForjaAntes, "Forja recebeu o pagamento de atacado");
        afirme(lojaGeral.getCaixa().getValorTotalEmLatao() < saldoGeralAntes, "Loja Geral pagou o valor com desconto de 15%");
    }

    // 6. Bloco 6: Capacidade Volumétrica e Perecibilidade
    private static void testarCapacidadeArmazemEPerecibilidade() {
        System.out.println("\n[6/11] Testando Capacidade Volumétrica e Deterioração de Insumos Orgânicos...");

        Lojas botanica = new Lojas("Botica Verde", "Herbalista", null, false, null, Lojas.TipoEstabelecimento.BOTANICA);
        afirme(botanica.getCapacidadeSlotsArmazemEfetiva() == 50, "Capacidade padrão de armazém é 50 slots");

        Item ervaPerecivel = new Item(501, "Erva Sangue-de-Dragão", 1.0);
        ervaPerecivel.setTipo(Item.TipoItem.MATERIAL);
        ervaPerecivel.configurarPerecivel(2); // Válida por 2 dias
        botanica.adicionarItem(ervaPerecivel);

        afirme(ervaPerecivel.isPerecivel(), "Item configurado como perecível");
        afirme(ervaPerecivel.getDiasValidadeRestantes() == 2, "Validade inicial é 2 dias");

        // Dia 1: decrementa validade
        botanica.processarDeterioracaoInsumos(1, 0);
        afirme(botanica.getQuantidadeEstoque(501) == 1, "Erva ainda está fresca no armazém");
        Item ervaNoArmazem = botanica.getEstoque().get(0);
        afirme(ervaNoArmazem.getDiasValidadeRestantes() == 1, "Resta 1 dia de validade");

        // Dia 2: validade zera -> material apodrece e gera PERDA_DETERIORACAO
        botanica.processarDeterioracaoInsumos(2, 0);
        afirme(botanica.getQuantidadeEstoque(501) == 0, "Erva apodreceu e foi removida do armazém");
        afirme(!botanica.getHistorico().isEmpty(), "Lançamento de perda gerado no Livro-Caixa");
        afirme(botanica.getHistorico().get(botanica.getHistorico().size() - 1).getTipo() == RegistroMovimentacao.TipoOperacao.PERDA_DETERIORACAO,
                "Tipo de operação registrada é PERDA_DETERIORACAO");
    }

    // 7. Bloco 7: Câmara de Conservação (Triplica Validade)
    private static void testarCamaraConservacaoTriplicada() {
        System.out.println("\n[7/11] Testando Câmara de Conservação (Validade Triplicada)...");

        Lojas taverna = new Lojas("Taverna do Barril", "Taberneiro", null, false, null, Lojas.TipoEstabelecimento.TAVERNA);
        taverna.getArvoreEvolucao().adicionarMelhoriaDireta(ArvoreEvolucaoLoja.MelhoriaLoja.CAMARA_CONSERVACAO);
        afirme(taverna.getArvoreEvolucao().isCamaraConservacaoAtiva(), "Câmara de Conservação instalada e ativa");

        Item carneFresco = new Item(502, "Carne de Javali", 2.0);
        carneFresco.configurarPerecivel(2);
        taverna.adicionarItem(carneFresco);

        // Com câmara de conservação, a perda diária é desacelerada em 3x
        taverna.processarDeterioracaoInsumos(1, 0);
        taverna.processarDeterioracaoInsumos(2, 0);
        afirme(taverna.getQuantidadeEstoque(502) == 1, "Alimento não apodreceu no prazo normal devido à conservação arcana");
    }

    // 8. Bloco 8: Árvore de Evolução e Talentos Lojistas
    private static void testarArvoreEvolucaoETalentosLojista() {
        System.out.println("\n[8/11] Testando Árvores de Evolução da Loja e Talentos Lojistas...");

        Lojas loja = new Lojas("Ferraria Real", "Mestre", null, false, null, Lojas.TipoEstabelecimento.FORJA);
        loja.setNivelLoja(3);
        loja.getCaixa().receberLatao(1_000_000L); // 100 PO

        // Compra melhoria de Expansão de Armazém (+30 slots)
        boolean comprouArmazem = loja.getArvoreEvolucao().comprarMelhoria(ArvoreEvolucaoLoja.MelhoriaLoja.EXPANSAO_ARMAZEM, loja, 1, 8);
        afirme(comprouArmazem, "Comprou expansão de armazém");
        afirme(loja.getCapacidadeSlotsArmazemEfetiva() == 80, "Capacidade de armazém expandiu de 50 para 80 slots");

        // Talentos
        loja.adicionarTalento(TalentoLojista.BARGANHA_AVANCADA);
        loja.adicionarTalento(TalentoLojista.MESTRE_ARTESAO);
        loja.adicionarTalento(TalentoLojista.RECICLAGEM_DE_SUCATA);

        afirme(loja.temTalento(TalentoLojista.BARGANHA_AVANCADA), "Possui talento Barganha Avançada");
        afirme(loja.temTalento(TalentoLojista.MESTRE_ARTESAO), "Possui talento Mestre Artesão");
        afirme(loja.temTalento(TalentoLojista.RECICLAGEM_DE_SUCATA), "Possui talento Reciclagem de Sucata");

        // Obra-Prima Mestre Artesão (3x valor)
        Item pecaObraPrima = new Item(601, "Katana Perfeita", 10.0);
        pecaObraPrima.setObraPrima(true);
        afirme(pecaObraPrima.getPrecoEmLatao() == 300_000L, "Item Obra-Prima é vendido por 3x o valor base (10 PO -> 30 PO = 300.000 PL)");

        // Reciclagem de Sucata em Encalhe Crítico (1d100 = 10)
        LotePrateleira loteEncalhado = new LotePrateleira(pecaObraPrima, 1, 7);
        Random rndReciclagem = new Random() {
            @Override public int nextInt(int bound) { return 9; } // Rola 10
        };
        loteEncalhado.executarRoletaLiquidacao(loja, 2, 8, rndReciclagem);
        afirme(loteEncalhado.getUltimoResultado() == LotePrateleira.ResultadoRoleta.RECICLAGEM_SUCATA,
                "Encalhe crítico acionou Reciclagem de Sucata graças ao talento do lojista");
    }

    // 9. Bloco 9: IA Determinística e Catálogo Global de Descobertas
    private static void testarIADeterministicaECatalogoDescobertas() {
        System.out.println("\n[9/11] Testando IA Determinística (IRD) e Catálogo Global de Descobertas...");

        Lojas oficina = new Lojas("Oficina Alquímica", "Alquimista", null, false, null, Lojas.TipoEstabelecimento.BOTANICA);
        oficina.setNivelLoja(5);

        MoldeReceita pocaoSimples = MoldeReceita.criarMoldeConsumivel(701, "Poção Menor", Lojas.TipoEstabelecimento.BOTANICA,
                1, 0.2, TamanhoItem.MINUSCULO, 1_000L, 20, 0, 0);
        pocaoSimples.adicionarFiltroInsumo(FiltroInsumoReceita.qualquerDoSubsistema(FichaMaterial.SubtipoMaterial.PLANTA_ERVA, 1, 1, true, false));

        MoldeReceita elixirLendario = MoldeReceita.criarMoldeConsumivel(702, "Elixir dos Deuses", Lojas.TipoEstabelecimento.BOTANICA,
                5, 0.5, TamanhoItem.PEQUENO, 50_000L, 100, 100, 100);
        elixirLendario.adicionarFiltroInsumo(FiltroInsumoReceita.qualquerDoSubsistema(FichaMaterial.SubtipoMaterial.PLANTA_ERVA, 5, 2, true, false));

        oficina.adicionarReceita(pocaoSimples);
        oficina.adicionarReceita(elixirLendario);

        CatalogoGlobalDescobertas catalogo = CatalogoGlobalDescobertas.getInstancia();
        catalogo.limparDescobertas();

        // Elixir Lendário está oculto no catálogo
        afirme(!catalogo.isReceitaDescoberta(702), "Elixir Lendário inicia oculto para o mercado");
        MoldeReceita melhorReceitaAntes = IALojistaDeterministica.selecionarMelhorReceita(oficina, catalogo);
        afirme(melhorReceitaAntes.getIdMolde() == 701, "IA selecionou a Poção Menor porque a avançada está oculta");

        // Jogadores descobrem a receita no mundo!
        catalogo.registrarDescobertaReceita(702);
        afirme(catalogo.isReceitaDescoberta(702), "Receita desbloqueada globalmente pelos jogadores");

        MoldeReceita melhorReceitaDepois = IALojistaDeterministica.selecionarMelhorReceita(oficina, catalogo);
        afirme(melhorReceitaDepois.getIdMolde() == 702, "IA adaptou instantaneamente e selecionou o Elixir Lendário de maior IRD");
    }

    // 10. Bloco 10: Contratos de Encomenda Urgente (+50%)
    private static void testarContratosEncomendaUrgente() {
        System.out.println("\n[10/11] Testando Contratos de Encomenda Urgente (+50%) e Terceirização...");

        Lojas forja = new Lojas("Forja Central", "Grimnir", null, false, null, Lojas.TipoEstabelecimento.FORJA);
        forja.setNivelLoja(2);

        MoldeReceita moldeMachado = MoldeReceita.criarMoldeArmaBranca(801, "Machado de Aço", 2, 2.0,
                TamanhoItem.MEDIO, 5_000L, FichaArmaBranca.SubtipoArmaBranca.MACHADO_DE_BATALHA);
        moldeMachado.adicionarFiltroInsumo(FiltroInsumoReceita.qualquerDoSubsistema(FichaMaterial.SubtipoMaterial.METAL_LINGOTE, 2, 2, true, false));

        // Encomenda: 5 machados em 4 dias, recompensa base 100.000 latão + 50% bônus
        ContratoEncomenda contrato = new ContratoEncomenda(1, "Duque de Valdorian", moldeMachado, 5, 4, 100_000L, 0.50);
        afirme(contrato.getValorTotalRecompensaEmLatao() == 150_000L, "Recompensa total é 150.000 latão (+50% bônus)");

        // Loja não tem materiais -> Terceiriza automaticamente para os jogadores
        boolean aceitou = contrato.aceitarEProduzir(forja, 1, 8);
        afirme(!aceitou, "Forja não tinha estoque de aço suficiente para produzir 5 machados no prazo");
        afirme(contrato.getStatus() == ContratoEncomenda.StatusContrato.TERCEIRIZADO_JOGADORES,
                "Contrato foi terceirizado para aventureiros no quadro de missões");

        // Simula forja recebendo os itens prontos e concluindo o contrato
        Item loteMachados = new Item(801, "Machado de Aço", 15.0);
        loteMachados.setTipo(Item.TipoItem.ARMA_BRANCA);
        loteMachados.setQuantidade(5);
        forja.adicionarItemProntoPrateleira(loteMachados);

        long saldoAntes = forja.getCaixa().getValorTotalEmLatao();
        boolean entregou = contrato.entregarItensProntos(forja, 2, 10);
        afirme(entregou, "Contrato entregue com sucesso!");
        afirme(contrato.getStatus() == ContratoEncomenda.StatusContrato.CONCLUIDO_SUCESSO, "Status CONCLUIDO_SUCESSO");
        afirme(forja.getCaixa().getValorTotalEmLatao() == saldoAntes + 150_000L, "Forja recebeu exatamente 150.000 latão");
    }

    // 11. Bloco 11: Base do Jogador e Estufa Climatizada
    private static void testarBaseJogadorEEstufaClimatizada() {
        System.out.println("\n[11/11] Testando Base do Jogador, Estufa Climatizada e Cultivo Botânico...");

        Cidade frostdale = new Cidade(5, "Frostdale", Cidade.TipoAssentamento.CIDADE,
                Regiao.TUNDRA, Geologia.FLORESTA, Aquifero.RIO, false, false, null);

        BaseJogador base = new BaseJogador("Cidadela da Névoa", frostdale);
        afirme(base.getNivelAtual() == BaseJogador.NivelBase.NIVEL_0_TERRENO, "Base inicia no Terreno Bruto");
        afirme(!base.getEstufa().isClimatizada(), "Estufa inicial não é climatizada");

        // Flor do Deserto não cresce na Tundra sem estufa climatizada!
        Item florDeserto = new Item(901, "Lótus das Areias", 10.0);
        florDeserto.setRegiaoOrigem(Regiao.DESERTO);

        boolean plantouSemEstufa = base.getEstufa().plantarEmCanteiroLivre(florDeserto, 3, 4, frostdale);
        afirme(!plantouSemEstufa, "Flor do Deserto rejeitou ser plantada no solo gelado da Tundra");

        // Grupo investe e evolui a base até o Nível 3 (Bastião Autônomo)
        Carteira carteiraGrupo = new Carteira(1_000); // 1.000 PO
        base.evoluirBase(carteiraGrupo, 1, 8); // Nv 1 (Acampamento)
        base.evoluirBase(carteiraGrupo, 1, 9); // Nv 2 (Feitoria)
        base.evoluirBase(carteiraGrupo, 1, 10); // Nv 3 (Bastião)

        afirme(base.getNivelAtual() == BaseJogador.NivelBase.NIVEL_3_BASTIAO, "Base evoluiu para Bastião Autônomo");
        afirme(base.getEstufa().isClimatizada(), "Estufa agora possui Climatização Arcana ativa!");

        // Agora a Flor do Deserto cresce perfeitamente na Tundra!
        boolean plantouComEstufa = base.getEstufa().plantarEmCanteiroLivre(florDeserto, 2, 5, frostdale);
        afirme(plantouComEstufa, "Flor do Deserto foi plantada com sucesso na Estufa Climatizada");

        // Avança 2 dias de cultivo
        base.processarDia(2, 8);
        base.processarDia(3, 8);

        List<Item> colheita = base.getEstufa().colherTudo();
        afirme(!colheita.isEmpty(), "Colheita realizada com sucesso");
        afirme(colheita.get(0).getQuantidade() == 5, "Canteiro rendeu exatamente 5 unidades de Lótus das Areias");
    }
}
