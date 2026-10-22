package teste;

import economia.*;
import geografia.Aquifero;
import geografia.Cidade;
import geografia.Geologia;
import geografia.Regiao;
import java.util.*;

/**
 * Suíte de Testes Automatizados para o Sistema de Crafting v5 em Thaluria RPG.
 * Valida a conexão de Item v4.1 com MoldeReceita, OficinaCrafting, os 7 graus de QualidadeCraft,
 * as 5 áreas de manufatura, a Regra do Camarão em itens manufaturados e a integração com o MotorEconomico.
 */
public class TesteCraftingV5 {

    private static int totalTestes = 0;
    private static int testesPassados = 0;

    public static void main(String[] args) {
        System.out.println("======================================================================");
        System.out.println("   🔨 INICIANDO SUITE DE TESTES DO SISTEMA DE CRAFTING v5 (THALURIA)   ");
        System.out.println("======================================================================\n");

        testarMateriaisGenericosVsEspecificos();
        testarTravaDeNivelDeLoja();
        testarProgressao7GrausQualidadeCraft();
        testarCraftingForja();
        testarCraftingAlfaiatariaEGrimorio();
        testarCraftingJoalheriaEPassivas();
        testarCraftingTavernaEBotanicaComRegraDoCamarao();
        testarCadeiaDeSuprimentosAutonomaEMotorEconomico();
        testarSubsistemasConcretosEModosFiltro();
        testarGuildaRegionalEAbastecimentoLocal();
        testarGeologiaMineracaoEGeradorDaForja();
        testarSistemaDeRequisicoesGuildaEDado20();

        System.out.println("\n======================================================================");
        if (testesPassados == totalTestes) {
            System.out.println(String.format("   ✨ RESULTADO FINAL: %d/%d TESTES DE CRAFTING v5 PASSARAM COM SUCESSO!",
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

    // 1. Bloco 1: Materiais Genéricos vs. Específicos
    private static void testarMateriaisGenericosVsEspecificos() {
        System.out.println("[1/8] Testando Materiais Genéricos (Q1-Q5) vs. Específicos e Nomenclatura...");

        Cidade frostdale = new Cidade(10, "Frostdale", Cidade.TipoAssentamento.CIDADE,
                Regiao.TUNDRA, Geologia.FLORESTA, Aquifero.RIO, false, false, null);
        Lojas alfaiataria = new Lojas("Manto Gelado", "Mestre Alfaiate", frostdale, false, null, Lojas.TipoEstabelecimento.ALFAIATARIA);
        alfaiataria.setNivelLoja(3);

        MoldeReceita moldeGibao = MoldeReceita.criarMoldeArmadura(101, "Gibão de Couro", Lojas.TipoEstabelecimento.ALFAIATARIA,
                2, 3.0, TamanhoItem.MEDIO, 2000L, List.of(FichaArmadura.SlotArmadura.PEITO), 4, 30);
        moldeGibao.adicionarRequisito(FichaMaterial.SubtipoMaterial.COURO_PELE, 2, 2, true, false);

        // A) Fabricando com material genérico
        Item couroGenerico = new Item(201, "Couro Curtido", "Couro comum de gado", 1.0, 1.0, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 2);
        couroGenerico.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.COURO_PELE, 2, false, ""));

        Item gibaoGenerico = OficinaCrafting.fabricarItem(alfaiataria, moldeGibao, List.of(couroGenerico), 501);
        afirme(gibaoGenerico != null, "Gibão fabricado com sucesso com couro genérico");
        afirme(gibaoGenerico.getNome().contains("Gibão de Couro"), "Nome base mantido");
        afirme(!gibaoGenerico.getNome().contains("de Couro Curtido"), "Material genérico não insere redundância no nome");
        afirme(gibaoGenerico.getRegiaoOrigem() == Regiao.TUNDRA, "Item genérico herda a região da cidade sede (Tundra)");

        // B) Fabricando com material específico (Drop de criatura do Deserto)
        Item escamaDragao = new Item(202, "Escamas de Dragão", "Escamas de wyrm do deserto", 50.0, 2.0, Item.TipoItem.MATERIAL, Item.RaridadeItem.MITICO, 2);
        escamaDragao.setOrigemGeografica(Regiao.DESERTO, Geologia.CAVERNA, Aquifero.OCEANO_AGUAS_PROFUNDAS);
        escamaDragao.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.COURO_PELE, 5, 0, 4, null, true, "de Escamas de Dragão"));

        Item gibaoDragao = OficinaCrafting.fabricarItem(alfaiataria, moldeGibao, List.of(escamaDragao), 502);
        afirme(gibaoDragao != null, "Gibão fabricado com sucesso com escamas de dragão");
        afirme(gibaoDragao.getNome().contains("de Escamas de Dragão"), "Nome incorpora o sufixo específico do material");
        afirme(gibaoDragao.getRegiaoOrigem() == Regiao.DESERTO, "Herda a região específica do monstro (Deserto)");
        afirme(gibaoDragao.getGeologiaOrigem() == Geologia.CAVERNA, "Herda a geologia específica do monstro (Caverna)");
    }

    // 2. Bloco 2: Trava de Nível de Loja
    private static void testarTravaDeNivelDeLoja() {
        System.out.println("\n[2/8] Testando Trava de Nível de Loja (1 a 5)...");

        Cidade sunreach = new Cidade(11, "Sunreach", Cidade.TipoAssentamento.CIDADE,
                Regiao.DESERTO, Geologia.PLANICIE, Aquifero.RIO, false, false, null);
        Lojas forjaNv2 = new Lojas("Ferraria Simples", "Ferreiro Aprendiz", sunreach, false, null, Lojas.TipoEstabelecimento.FORJA);
        forjaNv2.setNivelLoja(2);

        Lojas forjaNv5 = new Lojas("Forja Ancestral", "Grão-Mestre Anão", sunreach, false, null, Lojas.TipoEstabelecimento.FORJA);
        forjaNv5.setNivelLoja(5);

        MoldeReceita moldeEspadaMestre = MoldeReceita.criarMoldeArmaBranca(102, "Montante Rúnico", 4, 4.0, TamanhoItem.GRANDE,
                10000L, FichaArmaBranca.SubtipoArmaBranca.MONTANTE);
        moldeEspadaMestre.adicionarRequisito(FichaMaterial.SubtipoMaterial.METAL_LINGOTE, 3, 4, true, false);

        afirme(!OficinaCrafting.lojaAptaParaMolde(forjaNv2, moldeEspadaMestre),
                "Loja Nível 2 é rejeitada ao tentar forjar molde Nível 4");
        afirme(OficinaCrafting.lojaAptaParaMolde(forjaNv5, moldeEspadaMestre),
                "Loja Nível 5 é aceita para forjar molde Nível 4");
    }

    // 3. Bloco 3: Progressão dos 7 Graus de QualidadeCraft
    private static void testarProgressao7GrausQualidadeCraft() {
        System.out.println("\n[3/8] Testando Progressão dos 7 Graus de QualidadeCraft...");

        Cidade cidade = new Cidade(12, "Capital", Cidade.TipoAssentamento.CIDADE,
                Regiao.TEMPERADO, Geologia.COLINA, Aquifero.RIO, false, false, null);
        Lojas loja = new Lojas("Oficina Imperial", "Artesão", cidade, false, null, Lojas.TipoEstabelecimento.FORJA);

        MoldeReceita molde = MoldeReceita.criarMoldeArmaBranca(103, "Lâmina", 2, 2.0, TamanhoItem.MEDIO, 1000L,
                FichaArmaBranca.SubtipoArmaBranca.ESPADA_CURTA);
        molde.adicionarRequisito(FichaMaterial.SubtipoMaterial.METAL_LINGOTE, 1, 1, true, false);

        // 1. PLEBEU: Loja Nv 1 + Mat Q1 (Penalidade de improviso)
        loja.setNivelLoja(1);
        MoldeReceita molde1 = MoldeReceita.criarMoldeArmaBranca(103, "Lâmina", 1, 2.0, TamanhoItem.MEDIO, 1000L,
                FichaArmaBranca.SubtipoArmaBranca.ESPADA_CURTA);
        Item matQ1 = criarItemMaterial(1, Item.RaridadeItem.COMUM, false);
        QualidadeCraft q1 = OficinaCrafting.calcularQualidade(loja, molde1, List.of(matQ1));
        afirme(q1 == QualidadeCraft.PLEBEU, "Nível 1 com Mat Q1 resulta em grau PLEBEU (1)");

        // 2. ARTESANAL: Loja Nv 2 + Mat Q2
        loja.setNivelLoja(2);
        MoldeReceita molde2 = MoldeReceita.criarMoldeArmaBranca(103, "Lâmina", 2, 2.0, TamanhoItem.MEDIO, 1000L,
                FichaArmaBranca.SubtipoArmaBranca.ESPADA_CURTA);
        Item matQ2 = criarItemMaterial(2, Item.RaridadeItem.COMUM, false);
        QualidadeCraft q2 = OficinaCrafting.calcularQualidade(loja, molde2, List.of(matQ2));
        afirme(q2 == QualidadeCraft.ARTESANAL, "Nível 2 com Mat Q2 resulta em grau ARTESANAL (2)");

        // 3. REFINADO: Loja Nv 3 + Mat Q3
        loja.setNivelLoja(3);
        MoldeReceita molde3 = MoldeReceita.criarMoldeArmaBranca(103, "Lâmina", 3, 2.0, TamanhoItem.MEDIO, 1000L,
                FichaArmaBranca.SubtipoArmaBranca.ESPADA_CURTA);
        Item matQ3 = criarItemMaterial(3, Item.RaridadeItem.INCOMUM, false);
        QualidadeCraft q3 = OficinaCrafting.calcularQualidade(loja, molde3, List.of(matQ3));
        afirme(q3 == QualidadeCraft.REFINADO, "Nível 3 com Mat Q3 resulta em grau REFINADO (3)");

        // 4. NOBRE: Loja Nv 4 + Mat Q4
        loja.setNivelLoja(4);
        MoldeReceita molde4 = MoldeReceita.criarMoldeArmaBranca(103, "Lâmina", 4, 2.0, TamanhoItem.MEDIO, 1000L,
                FichaArmaBranca.SubtipoArmaBranca.ESPADA_CURTA);
        Item matQ4 = criarItemMaterial(4, Item.RaridadeItem.RARO, false);
        QualidadeCraft q4 = OficinaCrafting.calcularQualidade(loja, molde4, List.of(matQ4));
        afirme(q4 == QualidadeCraft.NOBRE, "Nível 4 com Mat Q4 resulta em grau NOBRE (4)");

        // 5. REAL: Loja Nv 5 + Mat Q5 genérico
        loja.setNivelLoja(5);
        MoldeReceita molde5 = MoldeReceita.criarMoldeArmaBranca(103, "Lâmina", 5, 2.0, TamanhoItem.MEDIO, 1000L,
                FichaArmaBranca.SubtipoArmaBranca.ESPADA_CURTA);
        Item matQ5 = criarItemMaterial(5, Item.RaridadeItem.RARO, false);
        QualidadeCraft q5 = OficinaCrafting.calcularQualidade(loja, molde5, List.of(matQ5));
        afirme(q5 == QualidadeCraft.REAL, "Nível 5 com Mat Q5 genérico resulta em grau REAL (5)");

        // 6. IMPERIAL: Loja Nv 5 + Mat Q5 + Espólio Épico
        Item espolioEpico = criarItemMaterial(5, Item.RaridadeItem.EPICO, true);
        QualidadeCraft q6 = OficinaCrafting.calcularQualidade(loja, molde5, List.of(matQ5, espolioEpico));
        afirme(q6 == QualidadeCraft.IMPERIAL, "Nível 5 com Mat Q5 e Espólio Épico resulta em grau IMPERIAL (6)");

        // Bônus de Excedente Técnico: Loja Nv 5 forjando receita Nv 2 com Mat Q5
        QualidadeCraft qExcedente = OficinaCrafting.calcularQualidade(loja, molde2, List.of(matQ5));
        afirme(qExcedente == QualidadeCraft.IMPERIAL, "Excedente técnico (+2 níveis de loja sobre receita) concede +1 grau");

        // 7. REGIO_ANCESTRAL: Loja Nv 5 + Mat Q5 + Espólio Mítico
        Item espolioMitico = criarItemMaterial(5, Item.RaridadeItem.MITICO, true);
        QualidadeCraft q7 = OficinaCrafting.calcularQualidade(loja, molde5, List.of(matQ5, espolioMitico));
        afirme(q7 == QualidadeCraft.REGIO_ANCESTRAL, "Nível 5 com Espólio Mítico resulta em REGIO_ANCESTRAL (7)");
    }

    // 4. Bloco 4: Crafting na Forja (Arma Branca, Armadura e Escudo)
    private static void testarCraftingForja() {
        System.out.println("\n[4/8] Testando Crafting na FORJA (Arma Branca com Catalisador e Escudo)...");

        Cidade cidade = new Cidade(13, "Ironpeak", Cidade.TipoAssentamento.CIDADE,
                Regiao.TAIGA, Geologia.MONTANHA, Aquifero.RIO, false, false, null);
        Lojas forja = new Lojas("Martelo Rubro", "Torvald", cidade, false, null, Lojas.TipoEstabelecimento.FORJA);
        forja.setNivelLoja(4);

        // A) Katana com Aço Refinado (+2 faces) e Catalisador de Urso Gélido (Efeito Frio)
        MoldeReceita moldeKatana = MoldeReceita.criarMoldeArmaBranca(104, "Katana", 3, 2.0, TamanhoItem.MEDIO,
                3000L, FichaArmaBranca.SubtipoArmaBranca.KATANA);
        moldeKatana.adicionarRequisito(FichaMaterial.SubtipoMaterial.METAL_LINGOTE, 2, 4, true, false);
        moldeKatana.adicionarRequisito(FichaMaterial.SubtipoMaterial.ESPOLIO_MONSTRO, 1, 3, false, true);

        Item acoRefinado = new Item(301, "Lingote de Aço Dobrado", "Metal com +2 faces", 15.0, 1.0,
                Item.TipoItem.MATERIAL, Item.RaridadeItem.RARO, 2);
        acoRefinado.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.METAL_LINGOTE, 4, 2, 0, null));

        EfeitoItem toqueCongelante = new EfeitoItem(44, "Toque Congelante", EfeitoItem.GatilhoEfeito.AO_ACERTAR,
                "1d4", "Gelo", 2);
        Item garraUrso = new Item(302, "Garra de Urso Gélido", "Catalisador elemental", 25.0, 0.5,
                Item.TipoItem.MATERIAL, Item.RaridadeItem.EPICO, 1);
        FichaMaterial fmGarra = new FichaMaterial(FichaMaterial.SubtipoMaterial.ESPOLIO_MONSTRO, 4, 0, 0, toqueCongelante, true, "do Urso Gélido");
        garraUrso.setFichaMaterial(fmGarra);

        Item katanaPronta = OficinaCrafting.fabricarItem(forja, moldeKatana, List.of(acoRefinado, garraUrso), 701);
        afirme(katanaPronta != null, "Katana manufaturada com sucesso");
        afirme(katanaPronta.isCraftado(), "Katana registrada como item manufaturado");
        afirme(katanaPronta.getFichaArmaBranca() != null, "Possui FichaArmaBranca");

        afirme(katanaPronta.getFichaArmaBranca().getFacesTotal() >= 14, "Faces do dado evoluíram (>= 14 faces)");
        afirme(katanaPronta.getFichaArmaBranca().getEfeitosEngastados().contains(toqueCongelante),
                "Efeito Toque Congelante do catalisador foi injetado nos slots da arma");

        // B) Escudo de Ferro
        MoldeReceita moldeEscudo = MoldeReceita.criarMoldeEscudo(105, "Escudo de Torre", 3, 6.0, TamanhoItem.GRANDE,
                2500L, FichaArmaBranca.Empunhadura.UMA_MAO, 50, 8);
        moldeEscudo.adicionarRequisito(FichaMaterial.SubtipoMaterial.METAL_LINGOTE, 3, 3, true, false);

        Item lingoteComum = new Item(303, "Lingote de Ferro", "Ferro", 5.0, 2.0, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 3);
        lingoteComum.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.METAL_LINGOTE, 3, 0, 2, null));

        Item escudoPronto = OficinaCrafting.fabricarItem(forja, moldeEscudo, List.of(lingoteComum), 702);
        afirme(escudoPronto != null, "Escudo manufaturado com sucesso");
        afirme(escudoPronto.getFichaEscudo() != null, "Possui FichaEscudo");
        afirme(escudoPronto.getFichaEscudo().getHpMaximo() > 50, "HP do escudo escalado pela durabilidade");
    }

    // 5. Bloco 5: Crafting na Alfaiataria e Grimório
    private static void testarCraftingAlfaiatariaEGrimorio() {
        System.out.println("\n[5/8] Testando Crafting na ALFAIATARIA (Arco Longo e Grimório Imperial)...");

        Cidade cidade = new Cidade(14, "Silverwood", Cidade.TipoAssentamento.CIDADE,
                Regiao.TEMPERADO, Geologia.FLORESTA, Aquifero.RIO, false, false, null);
        Lojas alfaiataria = new Lojas("Tear de Prata", "Eldrin", cidade, false, null, Lojas.TipoEstabelecimento.ALFAIATARIA);
        alfaiataria.setNivelLoja(5);

        // A) Arco Longo com Madeira Nobre
        MoldeReceita moldeArco = MoldeReceita.criarMoldeArmaDistante(106, "Arco Longo de Guerra", 3, 1.5, TamanhoItem.GRANDE,
                3000L, FichaArmaLongoAlcance.SubtipoArmaDistante.ARCO_LONGO);
        moldeArco.adicionarRequisito(FichaMaterial.SubtipoMaterial.MADEIRA_FIBRA, 2, 4, true, false);

        Item teixoNobre = new Item(401, "Madeira de Teixo Nobre", "Madeira elástica", 12.0, 0.7, Item.TipoItem.MATERIAL, Item.RaridadeItem.RARO, 2);
        teixoNobre.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.MADEIRA_FIBRA, 4));

        Item arcoPronto = OficinaCrafting.fabricarItem(alfaiataria, moldeArco, List.of(teixoNobre), 801);
        afirme(arcoPronto != null, "Arco longo manufaturado com sucesso");
        afirme(arcoPronto.getFichaArmaLongoAlcance() != null, "Possui FichaArmaLongoAlcance");
        afirme(arcoPronto.getFichaArmaLongoAlcance().getMultiplicadorAmplificacao() > 1.50,
                "Amplificação do arco escalada pela qualidade artesanal");

        // B) Grimório Arcano Imperial
        MoldeReceita moldeGrimorio = MoldeReceita.criarMoldeGrimorio(107, "Tomo Arcano", 4, 2.0, TamanhoItem.MEDIO,
                8000L, 4, 6);
        moldeGrimorio.adicionarRequisito(FichaMaterial.SubtipoMaterial.COURO_PELE, 2, 5, true, false);
        moldeGrimorio.adicionarRequisito(FichaMaterial.SubtipoMaterial.GEMA_CRISTAL, 1, 5, false, true);

        Item couroAbissal = new Item(402, "Couro de Leviatã", "Couro lendário", 40.0, 1.0, Item.TipoItem.MATERIAL, Item.RaridadeItem.LENDARIO, 2);
        couroAbissal.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.COURO_PELE, 5, true, "de Leviatã"));

        Item cristalAstral = new Item(403, "Cristal Astral", "Foco arcano", 50.0, 0.5, Item.TipoItem.MATERIAL, Item.RaridadeItem.EPICO, 1);
        cristalAstral.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.GEMA_CRISTAL, 5));

        Item tomoPronto = OficinaCrafting.fabricarItem(alfaiataria, moldeGrimorio, List.of(couroAbissal, cristalAstral), 802);
        afirme(tomoPronto != null, "Tomo arcano manufaturado com sucesso");
        afirme(tomoPronto.getFichaGrimorio() != null, "Possui FichaGrimorio");
        afirme(tomoPronto.getFichaGrimorio().getCapacidadeMaximaCirculos() >= 10,
                "Grimório Imperial/Régio ganhou páginas arcanas extras (>= 10 páginas)");
    }

    // 6. Bloco 6: Crafting na Joalheria e Passivas Executáveis
    private static void testarCraftingJoalheriaEPassivas() {
        System.out.println("\n[6/8] Testando Crafting na JOALHERIA (Acessórios e Passivas Executáveis)...");

        Cidade cidade = new Cidade(15, "Goldhaven", Cidade.TipoAssentamento.CIDADE,
                Regiao.TEMPERADO, Geologia.PLANICIE, Aquifero.OCEANO_AGUAS_COSTEIRAS, true, false, null);
        Lojas joalheria = new Lojas("Joias do Sol", "Aurelius", cidade, false, null, Lojas.TipoEstabelecimento.JOALHERIA);
        joalheria.setNivelLoja(4);

        MoldeReceita moldeAnel = MoldeReceita.criarMoldeAcessorio(108, "Anel Solar", 3, 0.05, TamanhoItem.MINUSCULO,
                5000L, FichaAcessorio.SlotAcessorio.ANEIS, Map.of("FORCA", 4, "DESTREZA", 2), null);
        moldeAnel.adicionarRequisito(FichaMaterial.SubtipoMaterial.GEMA_CRISTAL, 1, 4, true, false);

        EfeitoItem efeitoChama = new EfeitoItem(99, "Fogo Interior", EfeitoItem.GatilhoEfeito.ATIVACAO_DE_PASSIVA,
                "1d6", "Fogo", 3, Map.of("FORCA", 6), 1.5, 0);

        Item rubiSolar = new Item(501, "Rubi Vulcânico", "Gema incandescente", 60.0, 0.05, Item.TipoItem.MATERIAL, Item.RaridadeItem.EPICO, 1);
        rubiSolar.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.GEMA_CRISTAL, 4, 0, 0, efeitoChama, true, "de Rubi Vulcânico"));

        Item anelPronto = OficinaCrafting.fabricarItem(joalheria, moldeAnel, List.of(rubiSolar), 901);
        afirme(anelPronto != null, "Anel manufaturado com sucesso");
        afirme(anelPronto.getFichaAcessorio() != null, "Possui FichaAcessorio");
        afirme(anelPronto.getFichaAcessorio().getSlotExclusivo() == FichaAcessorio.SlotAcessorio.ANEIS,
                "Slot exclusivo do acessório é ANEIS");
        afirme(!anelPronto.getFichaAcessorio().getPassivas().isEmpty(),
                "Efeito da gema foi convertido em PassivaItem executável na joia");
        afirme(anelPronto.getFichaAcessorio().getBonusAtributos().getOrDefault("FORCA", 0) == 4,
                "Bônus de Força preservado na joia");
    }

    // 7. Bloco 7: Crafting na Taverna e Botânica + "Regra do Camarão"
    private static void testarCraftingTavernaEBotanicaComRegraDoCamarao() {
        System.out.println("\n[7/8] Testando Crafting na TAVERNA e BOTÂNICA com a Regra do Camarão...");

        Cidade oakhaven = new Cidade(16, "Porto Oakhaven", Cidade.TipoAssentamento.CIDADE,
                Regiao.TEMPERADO, Geologia.FLORESTA, Aquifero.OCEANO_AGUAS_COSTEIRAS, true, false, null);
        Lojas taverna = new Lojas("Taverna do Mar", "Mestre Cozinheiro", oakhaven, false, null, Lojas.TipoEstabelecimento.TAVERNA);
        taverna.setNivelLoja(3);

        MoldeReceita moldeMoqueca = MoldeReceita.criarMoldeConsumivel(109, "Moqueca", Lojas.TipoEstabelecimento.TAVERNA,
                2, 0.5, TamanhoItem.PEQUENO, 500L, 25, 0, 15);
        moldeMoqueca.adicionarRequisito(FichaMaterial.SubtipoMaterial.COMIDA_INSUMO, 2, 2, true, false);

        Item camarao = new Item(601, "Camarão da Costa", "Fruto do mar fresco", 2.0, 0.25, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 2);
        camarao.setOrigemGeografica(Regiao.TEMPERADO, Geologia.FLORESTA, Aquifero.OCEANO_AGUAS_COSTEIRAS);
        camarao.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.COMIDA_INSUMO, 3, true, "de Camarão da Costa"));

        Item pratoPronto = OficinaCrafting.fabricarItem(taverna, moldeMoqueca, List.of(camarao), 1001);
        afirme(pratoPronto != null, "Prato manufaturado na taverna");
        afirme(pratoPronto.getNome().contains("de Camarão da Costa"), "Prato nomeado com camarão específico");
        afirme(pratoPronto.getRegiaoOrigem() == Regiao.TEMPERADO, "Prato herda origem da costa");
        afirme(pratoPronto.getAquiferoOrigem() == Aquifero.OCEANO_AGUAS_COSTEIRAS, "Prato herda aquífero oceânico costeiro");

        // Cidade Continental no Deserto / Cavernas
        Cidade sunreach = new Cidade(17, "Sunreach", Cidade.TipoAssentamento.CIDADE,
                Regiao.DESERTO, Geologia.CAVERNA, Aquifero.OCEANO_AGUAS_PROFUNDAS, false, false, null);
        double multRegional = pratoPronto.calcularMultiplicadorRegional(sunreach);
        afirme(multRegional >= 1.70, "Prato de camarão atinge valorização de especiaria exótica no deserto (>= 1.70x)");

        // Botânica: Poção de Cura com Ervas
        Lojas botica = new Lojas("Botica Verde", "Alquimista", oakhaven, false, null, Lojas.TipoEstabelecimento.ALQUIMIA);
        MoldeReceita moldeElixir = MoldeReceita.criarMoldeConsumivel(110, "Elixir Revigorante", Lojas.TipoEstabelecimento.BOTANICA,
                2, 0.3, TamanhoItem.PEQUENO, 800L, 40, 20, 0);
        moldeElixir.adicionarRequisito(FichaMaterial.SubtipoMaterial.PLANTA_ERVA, 2, 2, true, false);

        Item ervaCura = new Item(602, "Folha de Alento", "Erva medicinal", 3.0, 0.1, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 2);
        ervaCura.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.PLANTA_ERVA, 3));

        Item pocaoPronta = OficinaCrafting.fabricarItem(botica, moldeElixir, List.of(ervaCura), 1002);
        afirme(pocaoPronta != null, "Poção manufaturada com sucesso na Botica/Alquimia");
        afirme(pocaoPronta.getFichaConsumivel() != null, "Possui FichaConsumivel");
        afirme(pocaoPronta.getFichaConsumivel().getRestauracaoVida() >= 40, "Vida restaurada pela poção devidamente aplicada");
    }

    // 8. Bloco 8: Cadeia de Suprimentos Autônoma e MotorEconomico
    private static void testarCadeiaDeSuprimentosAutonomaEMotorEconomico() {
        System.out.println("\n[8/8] Testando Cadeia de Suprimentos Autônoma com MotorEconomico e Livro-Caixa...");

        Cidade cidade = new Cidade(18, "Valenor", Cidade.TipoAssentamento.CIDADE,
                Regiao.TEMPERADO, Geologia.COLINA, Aquifero.RIO, false, false, null);
        Lojas forja = new Lojas("Forja de Valenor", "Mestre Forjador", cidade, false, null, Lojas.TipoEstabelecimento.FORJA);
        forja.setNivelLoja(3);
        forja.getCaixa().receberLatao(100_000L); // 10 PO de saldo

        MoldeReceita moldeEspada = MoldeReceita.criarMoldeArmaBranca(111, "Espada de Infantaria", 2, 2.5, TamanhoItem.MEDIO,
                1500L, FichaArmaBranca.SubtipoArmaBranca.ESPADA_LONGA);
        moldeEspada.adicionarRequisito(FichaMaterial.SubtipoMaterial.METAL_LINGOTE, 2, 2, true, false);
        forja.adicionarReceita(moldeEspada);

        // A) Inicialmente sem estoque de lingotes: processarCraftingDiario não fabrica, mas gera demanda
        Item lingoteModelo = new Item(701, "Lingote de Aço Comum", "Lingote básico", 4.0, 1.25, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 1);
        lingoteModelo.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.METAL_LINGOTE, 2));
        forja.adicionarItem(lingoteModelo);
        forja.removerUnidades(701, 1); // Garante catálogo conhecido mas estoque 0

        boolean fabricou = forja.processarCraftingDiario(1, 8);
        afirme(!fabricou, "Não fabricou pois não havia matéria-prima suficiente");
        afirme(forja.getDemandaPorItem().getOrDefault(701, 0) > 0, "Demanda automática gerada para o insumo faltante");

        // B) Abastecendo o estoque da loja com matéria-prima
        Item loteLingotes = new Item(701, "Lingote de Aço Comum", "Lingote básico", 4.0, 1.25, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 4);
        loteLingotes.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.METAL_LINGOTE, 2));
        forja.adicionarItem(loteLingotes);

        // C) Executando o processamento diário com insumos e saldo em caixa
        boolean fabricouComSucesso = forja.processarCraftingDiario(1, 12);
        afirme(fabricouComSucesso, "Forja processou a manufatura autônoma com sucesso");

        Item espadaNoEstoque = forja.buscarItemPorNome("Espada de Infantaria [Refinado]");
        if (espadaNoEstoque == null) {
            espadaNoEstoque = forja.buscarItemPorNome("Espada de Infantaria [Artesanal]");
        }
        afirme(espadaNoEstoque != null, "Item manufaturado foi incorporado ao estoque da loja");
        afirme(forja.getQuantidadeEstoque(701) == 2, "Consumiu exatamente 2 lingotes do estoque (4 - 2 = 2)");

        // D) Validação da auditoria contábil do livro-caixa
        boolean temRegistroCrafting = forja.getHistorico().stream()
                .anyMatch(r -> r.getTipo() == RegistroMovimentacao.TipoOperacao.PRODUCAO_CRAFTING);
        afirme(temRegistroCrafting, "Livro-caixa registrou movimentação do tipo PRODUCAO_CRAFTING");
    }

    // 9. Bloco 9: Subsistemas de Materiais Concretos (X, Y, Z) e Modos de Filtro
    private static void testarSubsistemasConcretosEModosFiltro() {
        System.out.println("\n[9/12] Testando Subsistemas de Materiais Concretos (X, Y, Z) e Filtros de Insumo...");

        Cidade cidade = new Cidade(20, "Floresta Verde", Cidade.TipoAssentamento.CIDADE,
                Regiao.TEMPERADO, Geologia.FLORESTA, Aquifero.RIO, false, false, null);
        Lojas alfaiataria = new Lojas("Ateliê", "Alfaiate", cidade, false, null, Lojas.TipoEstabelecimento.ALFAIATARIA);
        alfaiataria.setNivelLoja(3);

        // Materiais Concretos no grupo COURO_PELE Q1
        // Item X (id: 201) - Couro de Javali
        Item couroJavaliX = new Item(201, "Couro de Javali", "Couro resistente de floresta", 2.0, 1.0, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 5);
        couroJavaliX.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.COURO_PELE, 1, 0, 1, null, true, "de Javali"));

        // Item Y (id: 202) - Couro de Cervo
        Item couroCervoY = new Item(202, "Couro de Cervo", "Couro macio e flexível", 2.0, 0.8, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 5);
        couroCervoY.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.COURO_PELE, 1, 0, 0, null, true, "de Cervo"));

        // Item Z (id: 203) - Couro de Chacal
        Item couroChacalZ = new Item(203, "Couro de Chacal", "Couro rígido de deserto", 2.0, 0.9, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 5);
        couroChacalZ.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.COURO_PELE, 1, 0, 0, null, true, "de Chacal"));

        // Modo 1: ABERTO_SUBSISTEMA (aceita X, Y ou Z)
        FiltroInsumoReceita filtroAberto = FiltroInsumoReceita.qualquerDoSubsistema(
                FichaMaterial.SubtipoMaterial.COURO_PELE, 1, 2, true, false);
        afirme(filtroAberto.aceitaMaterial(couroJavaliX), "Filtro Aberto aceita Item X (Javali)");
        afirme(filtroAberto.aceitaMaterial(couroCervoY), "Filtro Aberto aceita Item Y (Cervo)");
        afirme(filtroAberto.aceitaMaterial(couroChacalZ), "Filtro Aberto aceita Item Z (Chacal)");

        MoldeReceita moldeBotas = MoldeReceita.criarMoldeArmadura(120, "Botas de Viagem", Lojas.TipoEstabelecimento.ALFAIATARIA,
                1, 1.0, TamanhoItem.PEQUENO, 500L, List.of(FichaArmadura.SlotArmadura.PES), 2, 20);
        moldeBotas.adicionarFiltroInsumo(filtroAberto);

        Item botasComZ = OficinaCrafting.fabricarItem(alfaiataria, moldeBotas, List.of(couroChacalZ), 951);
        afirme(botasComZ != null, "Botas manufaturadas com sucesso usando Couro de Chacal (Z) em modo aberto");

        // Modo 2: SUBCONJUNTO_RESTRITO (aceita X ou Y; rejeita Z!)
        FiltroInsumoReceita filtroRestritoXY = FiltroInsumoReceita.apenasEntreOpcoes(
                FichaMaterial.SubtipoMaterial.COURO_PELE, 1, 2, Set.of(201, 202), true, false);
        afirme(filtroRestritoXY.aceitaMaterial(couroJavaliX), "Filtro Restrito aceita X (Javali)");
        afirme(filtroRestritoXY.aceitaMaterial(couroCervoY), "Filtro Restrito aceita Y (Cervo)");
        afirme(!filtroRestritoXY.aceitaMaterial(couroChacalZ), "Filtro Restrito REJEITA Z (Chacal)!");

        MoldeReceita moldeGibaoFlexivel = MoldeReceita.criarMoldeArmadura(121, "Gibão Flexível", Lojas.TipoEstabelecimento.ALFAIATARIA,
                1, 2.0, TamanhoItem.MEDIO, 1000L, List.of(FichaArmadura.SlotArmadura.PEITO), 3, 25);
        moldeGibaoFlexivel.adicionarFiltroInsumo(filtroRestritoXY);

        Item gibaoTentativaInvalida = OficinaCrafting.fabricarItem(alfaiataria, moldeGibaoFlexivel, List.of(couroChacalZ), 952);
        afirme(gibaoTentativaInvalida == null, "Receita com subconjunto restrito rejeitou fabricar com Couro Z");

        Item gibaoValido = OficinaCrafting.fabricarItem(alfaiataria, moldeGibaoFlexivel, List.of(couroCervoY), 953);
        afirme(gibaoValido != null, "Receita com subconjunto restrito fabricou com sucesso usando Couro Y (Cervo)");

        // Modo 3: ITEM_EXATO (aceita exclusivamente X; rejeita Y e Z!)
        FiltroInsumoReceita filtroExatoX = FiltroInsumoReceita.itemEspecificoExato(
                201, FichaMaterial.SubtipoMaterial.COURO_PELE, 1, 2, true, false);
        afirme(filtroExatoX.aceitaMaterial(couroJavaliX), "Filtro Exato aceita X (Javali)");
        afirme(!filtroExatoX.aceitaMaterial(couroCervoY), "Filtro Exato REJEITA Y (Cervo)");
        afirme(!filtroExatoX.aceitaMaterial(couroChacalZ), "Filtro Exato REJEITA Z (Chacal)");

        MoldeReceita moldeManoplaJavali = MoldeReceita.criarMoldeArmadura(122, "Manopla Farpada", Lojas.TipoEstabelecimento.ALFAIATARIA,
                1, 1.0, TamanhoItem.PEQUENO, 1200L, List.of(FichaArmadura.SlotArmadura.MAOS), 3, 20);
        moldeManoplaJavali.adicionarFiltroInsumo(filtroExatoX);

        Item manoplaInvalida = OficinaCrafting.fabricarItem(alfaiataria, moldeManoplaJavali, List.of(couroCervoY), 954);
        afirme(manoplaInvalida == null, "Receita de item exato rejeitou fabricar com Couro Y");

        Item manoplaValida = OficinaCrafting.fabricarItem(alfaiataria, moldeManoplaJavali, List.of(couroJavaliX), 955);
        afirme(manoplaValida != null, "Receita de item exato fabricou com sucesso usando exclusivamente Couro X");
        afirme(manoplaValida.getNome().contains("de Javali"), "Manopla herda o nome do Couro de Javali");
    }

    // 10. Bloco 10: Guilda Regional e Abastecimento Local (Trava Geográfica)
    private static void testarGuildaRegionalEAbastecimentoLocal() {
        System.out.println("\n[10/12] Testando Guilda Regional e Abastecimento por Aventureiros Locais...");

        Cidade sunreach = new Cidade(21, "Sunreach", Cidade.TipoAssentamento.CIDADE,
                Regiao.DESERTO, Geologia.CAVERNA, Aquifero.OCEANO_AGUAS_PROFUNDAS, false, false, null);
        SedeGuildaRegional guildaDeserto = new SedeGuildaRegional(sunreach);
        sunreach.setGuildaRegional(guildaDeserto);

        // Drop local do Deserto
        Item couroChacal = new Item(203, "Couro de Chacal", "Deserto", 3.0, 1.0, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 4);
        couroChacal.setOrigemGeografica(Regiao.DESERTO, Geologia.CAVERNA, Aquifero.OCEANO_AGUAS_PROFUNDAS);

        // Drop da Neve/Tundra
        Item peleLoboNeves = new Item(204, "Pele de Lobo das Neves", "Tundra", 15.0, 2.0, Item.TipoItem.MATERIAL, Item.RaridadeItem.INCOMUM, 2);
        peleLoboNeves.setOrigemGeografica(Regiao.TUNDRA, Geologia.FLORESTA, Aquifero.RIO);

        afirme(guildaDeserto.itemPertenceAoEcossistemaLocal(couroChacal),
                "Couro de Chacal pertence ao ecossistema de Sunreach (Deserto/Caverna)");
        afirme(!guildaDeserto.itemPertenceAoEcossistemaLocal(peleLoboNeves),
                "Pele de Lobo das Neves NÃO pertence ao ecossistema de Sunreach (Tundra)");

        // Aventureiros locais tentam vender seus saques para a Guilda do Deserto
        boolean aceitouLocal = guildaDeserto.adicionarItemAventureiroLocal(couroChacal);
        afirme(aceitouLocal, "Guilda do Deserto aceitou espólio de criatura nativa (Couro de Chacal)");
        afirme(guildaDeserto.getEstoqueRegional().size() == 1, "Estoque regional da guilda contém 1 item");

        boolean aceitouNeve = guildaDeserto.adicionarItemAventureiroLocal(peleLoboNeves);
        afirme(!aceitouNeve, "Guilda do Deserto REJEITOU entrada de item da Tundra (Pele de Lobo das Neves)!");
    }

    // 11. Bloco 11: Geologia de Mineração e Árvore de Mineradores da Forja
    private static void testarGeologiaMineracaoEGeradorDaForja() {
        System.out.println("\n[11/12] Testando Geologia de Mineração e Árvore do Gerador do Ferreiro...");

        Cidade ironhold = new Cidade(22, "Ironhold", Cidade.TipoAssentamento.CIDADE,
                Regiao.TAIGA, Geologia.MONTANHA, Aquifero.RIO, false, false, null);

        // Veios minerais nativos da Montanha
        Item veioFerro = new Item(301, "Minério de Ferro Bruto", "Ferro", 5.0, 2.0, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 100);
        veioFerro.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.MINERIO_BRUTO, 1));
        ironhold.adicionarVeioMineral(veioFerro);

        Item veioPrata = new Item(302, "Minério de Prata", "Prata Q3", 25.0, 1.5, Item.TipoItem.MATERIAL, Item.RaridadeItem.INCOMUM, 100);
        veioPrata.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.MINERIO_BRUTO, 3));
        ironhold.adicionarVeioMineral(veioPrata);

        Lojas forja = new Lojas("Forja do Pico", "Grimnir", ironhold, false, null, Lojas.TipoEstabelecimento.FORJA);
        forja.setNivelLoja(3);
        forja.getCaixa().receberLatao(2_000_000L); // 200 PO

        GeradorMineracaoForja gerador = forja.getGeradorMineracao();
        afirme(gerador != null, "Forja inicializa com módulo de Gerador de Mineração");
        afirme(gerador.getNivelAtual() == GeradorMineracaoForja.NivelGeradorMina.NIVEL_0_DESATIVADO,
                "Gerador inicia no Nível 0 (desativado)");

        // Evolui para Nível 1: Picaretas Alugadas (custo 200.000 latão = 20 PO)
        boolean evoluiuNv1 = gerador.evoluirGerador(forja, 1, 8);
        afirme(evoluiuNv1, "Evoluiu para Nível 1 (Picaretas Alugadas)");
        afirme(gerador.getNivelAtual() == GeradorMineracaoForja.NivelGeradorMina.NIVEL_1_ESCAVADORES, "Nível 1 ativo");

        // Executa extração com fundos no caixa: paga salário diário e extrai minério Q1
        List<Item> mineriosExtraidos = gerador.extrairMineriosDoDia(forja, ironhold.getVeiosMineraisDisponiveis(), 1, 12, new Random(42));
        afirme(!mineriosExtraidos.isEmpty(), "Mineradores extraíram minérios no dia com salário pago");
        afirme(forja.getQuantidadeEstoque(301) > 0, "Minério de Ferro foi adicionado ao estoque da Forja");

        // Simula ferreiro falido (saldo zero) -> Mineradores entram em greve!
        forja.getCaixa().pagarLatao(forja.getCaixa().getValorTotalEmLatao());
        List<Item> greveMinerios = gerador.extrairMineriosDoDia(forja, ironhold.getVeiosMineraisDisponiveis(), 2, 8, new Random(42));
        afirme(greveMinerios.isEmpty(), "Ferreiro sem saldo: mineradores entraram em greve e extração foi 0");

        // Trava de Nível: Forja Nv 3 não pode evoluir gerador para Nível 4 (sonda alquímica exige Forja Nv 4)
        forja.getCaixa().receberLatao(100_000_000L); // Caixa cheio
        gerador.evoluirGerador(forja, 2, 10); // Nv 2
        gerador.evoluirGerador(forja, 2, 11); // Nv 3
        boolean tentouNv4Invalido = gerador.evoluirGerador(forja, 2, 12);
        afirme(!tentouNv4Invalido, "Trava de Nível: Forja Nv 3 é impedida de desbloquear Gerador Nv 4");
    }

    // 12. Bloco 12: Sistema de Requisições na Guilda e Resolução no Dado (1d20)
    private static void testarSistemaDeRequisicoesGuildaEDado20() {
        System.out.println("\n[12/12] Testando Sistema de Requisições na Guilda e Resoluções no Dado (1d20)...");

        Cidade portHaven = new Cidade(23, "Port Haven", Cidade.TipoAssentamento.CIDADE,
                Regiao.TEMPERADO, Geologia.FLORESTA, Aquifero.OCEANO_AGUAS_COSTEIRAS, true, false, null);
        SedeGuildaRegional guilda = new SedeGuildaRegional(portHaven);
        portHaven.setGuildaRegional(guilda);

        Lojas taverna = new Lojas("Taverna", "Cozinheiro", portHaven, false, null, Lojas.TipoEstabelecimento.TAVERNA);
        taverna.getCaixa().receberLatao(500_000L); // 50 PO

        Item camarao = new Item(601, "Camarão da Costa", "Fresco", 2.0, 0.25, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 1);
        camarao.setOrigemGeografica(Regiao.TEMPERADO, Geologia.FLORESTA, Aquifero.OCEANO_AGUAS_COSTEIRAS);

        // A) Requisição bem-sucedida: Nível 1 (Taxa 5k, CD 6, 1 dia)
        long taxa = RequisicaoGuilda.calcularTaxaFixaNivelEmLatao(1);
        afirme(taxa == 5_000L, "Taxa fixa de Nível 1 é exatamente 5.000 latão (5 PP)");
        afirme(RequisicaoGuilda.calcularCdBaseNivel(1) == 6, "CD base de Nível 1 é 6");

        RequisicaoGuilda reqNormal = guilda.abrirRequisicao(taverna, camarao, 4, 20_000L, 1, 8);
        afirme(reqNormal != null, "Requisição aberta com sucesso na Guilda");
        afirme(reqNormal.getStatus() == RequisicaoGuilda.StatusRequisicao.EM_EXPEDICAO, "Status inicial é EM_EXPEDICAO");

        // Seed determinística para simular rolagem com sucesso (d20 >= 6)
        Random rndSucesso = new Random() {
            @Override public int nextInt(int bound) { return 11; } // Rola 12
        };
        reqNormal.avancarDiaEResolver(2, 8, rndSucesso);
        afirme(reqNormal.getStatus() == RequisicaoGuilda.StatusRequisicao.CONCLUIDA_SUCESSO,
                "Requisição concluída com SUCESSO (rolou 12 no d20 >= CD 6)");
        afirme(reqNormal.getQuantidadeEntregue() == 4, "Entregou exatamente 100% da quantidade (4 unidades)");

        // B) Sucesso Crítico (20 Natural): 150% entregue
        RequisicaoGuilda reqCritica = guilda.abrirRequisicao(taverna, camarao, 4, 20_000L, 2, 8);
        Random rndCritico = new Random() {
            @Override public int nextInt(int bound) { return 19; } // Rola 20
        };
        reqCritica.avancarDiaEResolver(3, 8, rndCritico);
        afirme(reqCritica.getStatus() == RequisicaoGuilda.StatusRequisicao.CONCLUIDA_SUCESSO_CRITICO,
                "Requisição concluída com SUCESSO CRÍTICO (20 Natural no d20)");
        afirme(reqCritica.getQuantidadeEntregue() == 6, "Sucesso crítico entregou 150% do lote (6 unidades)");

        // C) Sucesso Parcial (CD - 3 <= Total < CD): 50% entregue e 50% de estorno
        long saldoAntesParcial = taverna.getCaixa().getValorTotalEmLatao();
        RequisicaoGuilda reqParcial = guilda.abrirRequisicao(taverna, camarao, 4, 40_000L, 3, 8);
        Random rndParcial = new Random() {
            @Override public int nextInt(int bound) { return 2; } // Rola 3 (CD 6 - 3 = 3 <= 3 < 6)
        };
        reqParcial.avancarDiaEResolver(4, 8, rndParcial);
        afirme(reqParcial.getStatus() == RequisicaoGuilda.StatusRequisicao.CONCLUIDA_PARCIAL,
                "Requisição concluída com SUCESSO PARCIAL");
        afirme(reqParcial.getQuantidadeEntregue() == 2, "Sucesso parcial entregou 50% do lote (2 unidades)");

        // D) Fracasso da Expedição: 0 entregue e 100% do caução estornado
        long saldoAntesFracasso = taverna.getCaixa().getValorTotalEmLatao();
        RequisicaoGuilda reqFracasso = guilda.abrirRequisicao(taverna, camarao, 4, 50_000L, 4, 8);
        Random rndFalha = new Random() {
            @Override public int nextInt(int bound) { return 0; } // Rola 1 (falha crítica)
        };
        reqFracasso.avancarDiaEResolver(5, 8, rndFalha);
        afirme(reqFracasso.getStatus() == RequisicaoGuilda.StatusRequisicao.FRACASSADA, "Expedição FRACASSOU");
        afirme(reqFracasso.getQuantidadeEntregue() == 0, "Nenhum item entregue em fracasso");
        // O caixa perdeu a taxa fixa (5k), mas recuperou os 50.000 da caução
        long saldoAposFracasso = taverna.getCaixa().getValorTotalEmLatao();
        afirme(saldoAposFracasso == saldoAntesFracasso - 5_000L,
                "Apenas a taxa da Guilda foi retida; 100% da caução de recompensa foi estornada");
    }

    private static Item criarItemMaterial(int qualidade, Item.RaridadeItem raridade, boolean ehEspolio) {
        FichaMaterial.SubtipoMaterial subtipo = ehEspolio ? FichaMaterial.SubtipoMaterial.ESPOLIO_MONSTRO : FichaMaterial.SubtipoMaterial.METAL_LINGOTE;
        Item item = new Item(100 + qualidade, "Material Q" + qualidade, "Descrição", 10.0 * qualidade, 1.0,
                Item.TipoItem.MATERIAL, raridade, 1);
        item.setFichaMaterial(new FichaMaterial(subtipo, qualidade, 0, 0, null, ehEspolio, ehEspolio ? "Ancestral" : ""));
        return item;
    }
}
