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

    private static Item criarItemMaterial(int qualidade, Item.RaridadeItem raridade, boolean ehEspolio) {
        FichaMaterial.SubtipoMaterial subtipo = ehEspolio ? FichaMaterial.SubtipoMaterial.ESPOLIO_MONSTRO : FichaMaterial.SubtipoMaterial.METAL_LINGOTE;
        Item item = new Item(100 + qualidade, "Material Q" + qualidade, "Descrição", 10.0 * qualidade, 1.0,
                Item.TipoItem.MATERIAL, raridade, 1);
        item.setFichaMaterial(new FichaMaterial(subtipo, qualidade, 0, 0, null, ehEspolio, ehEspolio ? "Ancestral" : ""));
        return item;
    }
}
