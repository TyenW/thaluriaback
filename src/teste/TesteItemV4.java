package teste;

import economia.*;
import geografia.*;
import personagem.Ficha;
import java.util.*;

/**
 * Suite de testes unitários e de integração para a evolução do Item v4 (Pré-Crafting).
 * Valida os 10 subtipos de itens, tamanhos físicos, regra do camarão no litoral,
 * as 8 fichas técnicas modulares e o sistema de equipamento multi-slot na Ficha.
 */
public class TesteItemV4 {

    private static int totalTestes = 0;
    private static int testesPassados = 0;

    private static void afirmo(boolean condicao, String mensagem) {
        totalTestes++;
        if (condicao) {
            testesPassados++;
            System.out.println("  ✓ [PASSOU] " + mensagem);
        } else {
            System.err.println("  ✗ [FALHOU] " + mensagem);
            throw new AssertionError("Falha no teste: " + mensagem);
        }
    }

    public static void main(String[] args) {
        System.out.println("======================================================================");
        System.out.println("   ⚔️ INICIANDO SUITE DE TESTES DO ITEM v4 (PRÉ-CRAFTING TÁTICO)     ");
        System.out.println("======================================================================\n");

        testarTamanhosEMochila();
        testarRegraDoCamaraoEPrecosRegionais();
        testarFichaMaterialESorteioDrop();
        testarFichaArmaBranca33Subtipos();
        testarFichaArmaLongoAlcanceEAmplificacao();
        testarFichaArmadura13SlotsMultiOcupacao();
        testarFichaEscudoEBloqueio();
        testarFichaAcessorio5SlotsEPassivas();
        testarFichaConsumivel();
        testarFichaGrimorioECirculosMagicos();
        testarEquipamentoEFichaPersonagem();
        testarQualidadeCraftEArmas();
        testarDropMonstroComHerancaGeografica();
        testarPassivaExecutavelEMultiplicadorDano();

        System.out.println("\n======================================================================");
        System.out.printf("   🎉 RESULTADO FINAL: %d/%d TESTES DO ITEM v4.1 PASSARAM COM SUCESSO!\n",
                testesPassados, totalTestes);
        System.out.println("======================================================================");
    }

    private static void testarTamanhosEMochila() {
        System.out.println("[1/11] Testando Tamanho Físico e Capacidade de Mochila...");
        Item anel = new Item("Anel de Prata", 5.0);
        anel.setTamanho(TamanhoItem.MINUSCULO);
        afirmo(anel.getTamanho().getSlotsMochila() == 0.1, "Anel ocupa 0.1 slot de mochila");

        Item espada = new Item("Espada Longa", 15.0);
        espada.setTamanho(TamanhoItem.MEDIO);
        espada.setQuantidade(2);
        afirmo(espada.getEspacoTotal() == 6.0, "2 espadas médias ocupam 6.0 slots no total");

        Item cranioDragao = new Item("Crânio de Dragão Ancestral", 500.0);
        cranioDragao.setTamanho(TamanhoItem.COLOSSAL);
        afirmo(!cranioDragao.getTamanho().isCabeEmMochilaComum(), "Item colossal não cabe em mochila comum");

        Ficha aventureiro = new Ficha(10, 10, 10, 10, 10, 10);
        boolean colocouColossal = aventureiro.adicionarAoInventarioComValidacao(cranioDragao);
        afirmo(!colocouColossal, "Mochila rejeitou colocar item colossal");

        Item adaga = new Item("Adaga de Ferro", 2.0);
        adaga.setTamanho(TamanhoItem.PEQUENO);
        boolean colocouAdaga = aventureiro.adicionarAoInventarioComValidacao(adaga);
        afirmo(colocouAdaga, "Adaga pequena coube perfeitamente na mochila");
    }

    private static void testarRegraDoCamaraoEPrecosRegionais() {
        System.out.println("\n[2/11] Testando a Regra do Camarão no Litoral (Precificação Regional)...");
        Cidade oakhaven = new Cidade(1, "Porto Oakhaven", Cidade.TipoAssentamento.CIDADE,
                Regiao.TEMPERADO, Geologia.FLORESTA, Aquifero.OCEANO_AGUAS_COSTEIRAS, true, false, null);
        Cidade valdorian = new Cidade(2, "Valdorian", Cidade.TipoAssentamento.REINO,
                Regiao.TEMPERADO, Geologia.PLANICIE, Aquifero.RIO, false, false, null);
        Cidade sunreach = new Cidade(3, "Sunreach", Cidade.TipoAssentamento.CIDADE,
                Regiao.DESERTO, Geologia.CAVERNA, Aquifero.OCEANO_AGUAS_PROFUNDAS, false, false, null);

        Item camarao = new Item(10, "Camarão da Costa", "Fruto do mar fresco", 2.0, 0.5,
                Item.TipoItem.CONSUMIVEL, Item.RaridadeItem.COMUM, 1);
        camarao.setOrigemGeografica(Regiao.TEMPERADO, Geologia.FLORESTA, Aquifero.OCEANO_AGUAS_COSTEIRAS);

        // 1. Em Oakhaven (Cidade nativa costeira) -> Abundância (0.80x)
        double multNativo = camarao.calcularMultiplicadorRegional(oakhaven);
        afirmo(multNativo == 0.80, "Camarão no litoral nativo possui desconto de abundância (0.80x)");

        // 2. Em Valdorian (Planície com Rio) -> Bioma neutro/próximo (1.25x)
        double multNeutro = camarao.calcularMultiplicadorRegional(valdorian);
        afirmo(multNeutro == 1.25, "Camarão em cidade do interior próxima é vendido a 1.25x");

        // 3. Em Sunreach (Deserto árido) -> Bioma oposto/exótico (1.85x)
        double multExotico = camarao.calcularMultiplicadorRegional(sunreach);
        afirmo(multExotico == 1.85, "Camarão no deserto profundo atua como especiaria exótica cara (1.85x)");

        // 4. Integração com a Loja de Sunreach
        Lojas empórioSunreach = new Lojas("Empório do Deserto", "Farid", sunreach, false, null, Lojas.TipoEstabelecimento.LOJA_GERAL);
        empórioSunreach.adicionarItem(camarao);
        long precoSunreach = empórioSunreach.calcularPrecoDinamicoVendaEmLatao(camarao);
        long precoBaseLatao = camarao.getPrecoEmLatao(); // 20.000 latão
        afirmo(precoSunreach > precoBaseLatao, "Loja no deserto cobra preço substancialmente maior pelo camarão exótico");
    }

    private static void testarFichaMaterialESorteioDrop() {
        System.out.println("\n[3/11] Testando FichaMaterial e Sorteio de Efeito no Drop...");
        FichaMaterial materialGarra = new FichaMaterial(FichaMaterial.SubtipoMaterial.ESPOLIO_MONSTRO, 3);
        EfeitoItem gelo = new EfeitoItem(1, "Corte Congelante", EfeitoItem.GatilhoEfeito.AO_ACERTAR, "1d4", "Gelo", 2, "Congela o membro");
        EfeitoItem sangria = new EfeitoItem(2, "Sangramento Ártico", EfeitoItem.GatilhoEfeito.AO_ACERTAR, "1d6", "Sangramento", 3, "Drena vida");

        materialGarra.adicionarEfeitoPossivel(gelo);
        materialGarra.adicionarEfeitoPossivel(sangria);
        materialGarra.setBonusFacesDadoArma(2); // +2 faces no crafting de armas

        afirmo(materialGarra.getEfeitoAtribuido() == null, "Material recém-instanciado ainda não tem efeito fixado");

        // Simula abate de monstro e sorteio com Random com seed fixa
        Random rnd = new Random(42);
        EfeitoItem sorteado = materialGarra.sortearEfeitoNoDrop(rnd);
        afirmo(sorteado != null, "Efeito foi sorteado com sucesso no drop");
        afirmo(materialGarra.getEfeitoAtribuido().equals(sorteado), "Efeito sorteado foi fixado no material");
        afirmo(materialGarra.getBonusFacesDadoArma() == 2, "Bônus de +2 faces de dado do material mantido");
    }

    private static void testarFichaArmaBranca33Subtipos() {
        System.out.println("\n[4/11] Testando FichaArmaBranca (33 Subtipos e Evolução de Dano)...");
        FichaArmaBranca.SubtipoArmaBranca[] subtipos = FichaArmaBranca.SubtipoArmaBranca.values();
        afirmo(subtipos.length == 33, "Exatamente 33 armas brancas registradas no arsenal");

        // Teste de Montante (Duas Mãos, 2d6 base)
        FichaArmaBranca montante = new FichaArmaBranca(FichaArmaBranca.SubtipoArmaBranca.MONTANTE);
        afirmo(montante.getEmpunhadura() == FichaArmaBranca.Empunhadura.DUAS_MAOS, "Montante exige duas mãos");
        afirmo(montante.getDescricaoDano().equals("2d6"), "Montante possui dano base 2d6");

        // Teste de Katana (1d10 base) evoluída com metal nobre (+2 faces -> 1d12)
        FichaArmaBranca katana = new FichaArmaBranca(FichaArmaBranca.SubtipoArmaBranca.KATANA);
        afirmo(katana.getDescricaoDano().equals("1d10"), "Katana comum causa 1d10 de dano");
        katana.setBonusFacesDado(2);
        afirmo(katana.getDescricaoDano().equals("1d12"), "Katana com material refinado evoluiu para 1d12");

        // Teste de Slots de Engaste
        katana.setSlotsDeEfeitoMaximos(1);
        EfeitoItem fogo = new EfeitoItem(3, "Chama Voraz", EfeitoItem.GatilhoEfeito.AO_ACERTAR, "1d4", "Fogo", 2, "Queima o alvo");
        boolean engastou1 = katana.adicionarEfeito(fogo);
        afirmo(engastou1, "Engastou primeiro efeito na arma");
        boolean engastou2 = katana.adicionarEfeito(fogo);
        afirmo(!engastou2, "Rejeitou engastar além do limite de slots da arma");
    }

    private static void testarFichaArmaLongoAlcanceEAmplificacao() {
        System.out.println("\n[5/11] Testando FichaArmaLongoAlcance e Amplificação de Projétil...");
        FichaArmaLongoAlcance arcoLongo = new FichaArmaLongoAlcance(FichaArmaLongoAlcance.SubtipoArmaDistante.ARCO_LONGO, 1.50, 60, 2);
        afirmo(arcoLongo.getMultiplicadorAmplificacao() == 1.50, "Arco Longo possui amplificação de 1.50x (+50%)");
        afirmo(arcoLongo.getAlcanceMetros() == 60, "Alcance efetivo de 60 metros");

        // Disparo de flecha de ferro com dano base 10
        int danoProjetil = 10;
        int danoFinal = arcoLongo.calcularDanoDisparo(danoProjetil);
        afirmo(danoFinal == 15, "Dano amplificado de 10 subiu para 15 com o Arco Longo");
    }

    private static void testarFichaArmadura13SlotsMultiOcupacao() {
        System.out.println("\n[6/11] Testando FichaArmadura (13 Slots Corporais e Multi-Ocupação)...");
        afirmo(FichaArmadura.SlotArmadura.values().length == 13, "Exatamente 13 slots corporais de armadura registrados");

        // Peça Multi-Slot: Armadura Completa de Placas (Peito, Costas, Ombros, Cintura)
        EnumSet<FichaArmadura.SlotArmadura> slotsPlacas = EnumSet.of(
                FichaArmadura.SlotArmadura.PEITO,
                FichaArmadura.SlotArmadura.COSTAS,
                FichaArmadura.SlotArmadura.OMBROS,
                FichaArmadura.SlotArmadura.CINTURA
        );
        FichaArmadura armaduraPlacas = new FichaArmadura(slotsPlacas, 8, 120, 2);
        afirmo(armaduraPlacas.getSlotsOcupados().size() == 4, "Armadura de placas ocupa 4 slots anatômicos");
        afirmo(armaduraPlacas.getReducaoDeDanoEfetiva() == 8, "Armadura oferece 8 de Redução de Dano (RD)");

        // Dano e quebra de durabilidade
        armaduraPlacas.sofrerDanoDurabilidade(120);
        afirmo(armaduraPlacas.estaQuebrada(), "Armadura quebrou após perder toda durabilidade");
        afirmo(armaduraPlacas.getReducaoDeDanoEfetiva() == 0, "Armadura quebrada perde a proteção de RD");
        armaduraPlacas.reparar(60);
        afirmo(!armaduraPlacas.estaQuebrada() && armaduraPlacas.getReducaoDeDanoEfetiva() == 8, "Armadura reparada recuperou a proteção");
    }

    private static void testarFichaEscudoEBloqueio() {
        System.out.println("\n[7/11] Testando FichaEscudo (HP de Bloqueio e Limite por Golpe)...");
        // Escudo de Ferro: HP 30, Limite de Absorção por Golpe 10
        FichaEscudo escudo = new FichaEscudo(30, 10);

        // 1. Golpe fraco de 7 de dano (<= limite de 10)
        FichaEscudo.ResultadoBloqueio r1 = escudo.absorverGolpe(7);
        afirmo(r1.getDanoAbsorvido() == 7 && r1.getDanoVazado() == 0, "Escudo absorveu 100% do golpe fraco (7 dano, 0 vazado)");
        afirmo(escudo.getHpAtual() == 23, "HP do escudo caiu de 30 para 23");

        // 2. Golpe pesado de 18 de dano (> limite de 10)
        FichaEscudo.ResultadoBloqueio r2 = escudo.absorverGolpe(18);
        afirmo(r2.getDanoAbsorvido() == 10, "Escudo absorveu até o limite da sua estrutura (10 de dano)");
        afirmo(r2.getDanoVazado() == 8, "Dano excedente de 8 vazou para o defensor");
        afirmo(escudo.getHpAtual() == 13, "HP do escudo caiu de 23 para 13");
    }

    private static void testarFichaAcessorio5SlotsEPassivas() {
        System.out.println("\n[8/11] Testando FichaAcessorio (5 Slots Exclusivos e Passivas com Cooldown)...");
        afirmo(FichaAcessorio.SlotAcessorio.values().length == 5, "Exatamente 5 slots exclusivos de acessórios registrados");

        FichaAcessorio amuleto = new FichaAcessorio(FichaAcessorio.SlotAcessorio.ACESSORIO_GERAL);
        amuleto.adicionarBonusAtributo("FORCA", 3);
        PassivaItem ultimoSuspiro = new PassivaItem("Último Suspiro", "HP < 25%", "Ganha +30% Esquiva", 3);
        amuleto.adicionarPassiva(ultimoSuspiro);

        afirmo(ultimoSuspiro.estaDisponivel(), "Passiva inicia disponível");
        boolean ativou = ultimoSuspiro.disparar();
        afirmo(ativou && !ultimoSuspiro.estaDisponivel(), "Passiva ativou e entrou em tempo de recarga");
        afirmo(ultimoSuspiro.getRecargaRestanteTurnos() == 3, "Recarga restante é de 3 turnos");

        ultimoSuspiro.avancarTurno();
        ultimoSuspiro.avancarTurno();
        ultimoSuspiro.avancarTurno();
        afirmo(ultimoSuspiro.estaDisponivel(), "Passiva voltou a ficar disponível após 3 turnos");
    }

    private static void testarFichaConsumivel() {
        System.out.println("\n[9/11] Testando FichaConsumivel (Restauração e Cura de Condições)...");
        FichaConsumivel antidoto = new FichaConsumivel(15, 0, 10);
        antidoto.adicionarCondicaoRemovida("Envenenamento");
        antidoto.adicionarCondicaoRemovida("Paralisia");

        afirmo(antidoto.getRestauracaoVida() == 15, "Consumível restaura 15 HP");
        afirmo(antidoto.getRestauracaoVigor() == 10, "Consumível restaura 10 Vigor");
        afirmo(antidoto.getCondicoesRemovidas().contains("Envenenamento"), "Cura a condição Envenenamento");
    }

    private static void testarFichaGrimorioECirculosMagicos() {
        System.out.println("\n[10/11] Testando FichaGrimorio e Círculos Mágicos...");
        FichaGrimorio grimorio = new FichaGrimorio(2, 3); // Nível 2, capacidade para 3 magias

        CirculoMagico bolaFogo = new CirculoMagico(1, "Labareda Ígnea", 1, 10, "Runa Fogo", "Causa dano de fogo em cone");
        CirculoMagico escudoVento = new CirculoMagico(2, "Barreira de Vento", 2, 18, "Runa Vento", "Bloqueia projéteis");
        CirculoMagico meteoroArcano = new CirculoMagico(3, "Meteoro Arcano", 4, 80, "Runa Cósmica", "Devastação em área");

        boolean ins1 = grimorio.inscreverCirculo(bolaFogo);
        boolean ins2 = grimorio.inscreverCirculo(escudoVento);
        afirmo(ins1 && ins2, "Círculos de nível 1 e 2 foram inscritos com sucesso");

        boolean ins3 = grimorio.inscreverCirculo(meteoroArcano);
        afirmo(!ins3, "Grimório de Nível 2 rejeitou inscrever magia de Nível 4 (evitou queimar páginas)");
        afirmo(grimorio.getCirculosDesenhados().size() == 2, "Grimório contém exatamente os 2 feitiços permitidos");
    }

    private static void testarEquipamentoEFichaPersonagem() {
        System.out.println("\n[11/11] Testando Equipamento Tático e Slots na Ficha do Personagem...");
        Ficha heroi = new Ficha(12, 14, 10, 10, 10, 10);

        // 1. Equipar Armadura Multi-Slot
        Item peitoral = new Item(100, "Peitoral de Cavalaria", 40.0);
        peitoral.setTipo(Item.TipoItem.ARMADURA);
        EnumSet<FichaArmadura.SlotArmadura> slotsPeito = EnumSet.of(
                FichaArmadura.SlotArmadura.PEITO,
                FichaArmadura.SlotArmadura.COSTAS,
                FichaArmadura.SlotArmadura.OMBROS
        );
        peitoral.setFichaArmadura(new FichaArmadura(slotsPeito, 6, 100, 1));
        heroi.adicionarItem(peitoral);

        boolean equipouPeitoral = heroi.equiparArmadura(peitoral);
        afirmo(equipouPeitoral, "Herói equipou armadura multi-slot nos slots PEITO, COSTAS e OMBROS");
        afirmo(heroi.calcularReducaoDanoTotalArmaduras() == 6, "RD total do herói é 6");

        // Tentar equipar outra armadura que conflite com OMBROS
        Item capaOmbro = new Item(101, "Capa com Ombreira", 10.0);
        capaOmbro.setTipo(Item.TipoItem.ARMADURA);
        capaOmbro.setFichaArmadura(new FichaArmadura(FichaArmadura.SlotArmadura.OMBROS, 2, 50));
        heroi.adicionarItem(capaOmbro);
        boolean conflitou = heroi.equiparArmadura(capaOmbro);
        afirmo(!conflitou, "Sistema rejeitou equipar armadura em slot já ocupado (OMBROS)");

        // 2. Equipar Arma de 2 Mãos vs Mão Secundária
        Item montante = new Item(102, "Montante de Aço", 30.0);
        montante.setTipo(Item.TipoItem.ARMA_BRANCA);
        montante.setFichaArmaBranca(new FichaArmaBranca(FichaArmaBranca.SubtipoArmaBranca.MONTANTE));
        heroi.adicionarItem(montante);

        Item escudoBroquel = new Item(103, "Broquel de Madeira", 5.0);
        escudoBroquel.setTipo(Item.TipoItem.ESCUDO);
        escudoBroquel.setFichaEscudo(new FichaEscudo(20, 8));
        heroi.adicionarItem(escudoBroquel);

        // Equipa o montante na mão principal
        heroi.equiparMaoPrincipal(montante);
        afirmo(heroi.getMaoPrincipal().equals(montante), "Montante empunhado na mão principal");

        // Tenta equipar escudo na mão secundária enquanto segura montante de 2 mãos
        boolean equipouSecundariaComMontante = heroi.equiparMaoSecundaria(escudoBroquel);
        afirmo(!equipouSecundariaComMontante, "Rejeitou equipar escudo na mão secundária pois arma principal exige 2 mãos");

        // Troca para Espada Longa (1 mão) e equipa o escudo
        Item espadaLonga = new Item(104, "Espada Longa", 15.0);
        espadaLonga.setTipo(Item.TipoItem.ARMA_BRANCA);
        espadaLonga.setFichaArmaBranca(new FichaArmaBranca(FichaArmaBranca.SubtipoArmaBranca.ESPADA_LONGA));
        heroi.adicionarItem(espadaLonga);

        heroi.equiparMaoPrincipal(espadaLonga);
        boolean equipouBroquel = heroi.equiparMaoSecundaria(escudoBroquel);
        afirmo(equipouBroquel, "Com espada de 1 mão, conseguiu empunhar o escudo na mão secundária com sucesso");
    }

    private static void testarQualidadeCraftEArmas() {
        System.out.println("\n[12/14] Testando QualidadeCraft (7 Graus de Manufatura e Evolução de Armas)...");
        afirmo(QualidadeCraft.values().length == 7, "Exatamente 7 graus canônicos de QualidadeCraft");

        // 1. Katana Base: 1d10, 2.0kg, Preço 50 PO
        Item katana = new Item(201, "Katana da Forja", 50.0);
        katana.setPeso(2.0);
        katana.setTipo(Item.TipoItem.ARMA_BRANCA);
        katana.setFichaArmaBranca(new FichaArmaBranca(FichaArmaBranca.SubtipoArmaBranca.KATANA));
        afirmo(katana.getFichaArmaBranca().getDescricaoDano().equals("1d10"), "Katana base tem dano 1d10");

        // 2. Aplica Qualidade NOBRE: +2 faces (1d12) + 1 dano fixo (1d12+1), 2 slots, peso -10% (1.80kg)
        katana.aplicarQualidadeCraft(QualidadeCraft.NOBRE);
        afirmo(katana.isCraftado(), "Item agora possui origem MANUFATURADO_CRAFT");
        afirmo(katana.getQualidadeCraft() == QualidadeCraft.NOBRE, "Item possui grau de qualidade NOBRE");
        afirmo(katana.getFichaArmaBranca().getDescricaoDano().equals("1d12+1"), "Katana Nobre evoluiu para 1d12+1");
        afirmo(katana.getFichaArmaBranca().getSlotsDeEfeitoMaximos() == 2, "Katana Nobre tem 2 slots de efeito");
        afirmo(katana.getPeso() == 1.80, "Ergonomia Nobre reduziu peso para 1.80kg (-10%)");

        // 3. Katana Régio Ancestral: +4 faces + 1 dado extra (2d14) + 6 dano fixo (2d14+6), 4 slots, peso -25%
        Item katanaAncestral = new Item(202, "Katana Ancestral Lendária", 100.0);
        katanaAncestral.setPeso(2.0);
        katanaAncestral.setTipo(Item.TipoItem.ARMA_BRANCA);
        katanaAncestral.setFichaArmaBranca(new FichaArmaBranca(FichaArmaBranca.SubtipoArmaBranca.KATANA));
        katanaAncestral.aplicarQualidadeCraft(QualidadeCraft.REGIO_ANCESTRAL);
        afirmo(katanaAncestral.getFichaArmaBranca().getDescricaoDano().equals("2d14+6"), "Katana Régio Ancestral evoluiu para 2d14+6 com dado extra");
        afirmo(katanaAncestral.getFichaArmaBranca().getSlotsDeEfeitoMaximos() == 4, "Katana Régio Ancestral liberou 4 slots de efeito");
        afirmo(katanaAncestral.getPeso() == 1.50, "Ergonomia Régio Ancestral reduziu peso em 25% (1.50kg)");
        afirmo(katanaAncestral.getPreco() == 500.0, "Preço base multiplicado em 5.00x para peça Régio Ancestral");
    }

    private static void testarDropMonstroComHerancaGeografica() {
        System.out.println("\n[13/14] Testando Drop de Monstro e Herança Geográfica com a Regra do Camarão...");
        afirmo(Item.RaridadeItem.MITICO != null, "Raridade MÍTICO registrada como 6º nível de raridade de drop");

        Item coracaoDragao = new Item(901, "Coração de Dragão Abissal", "Órgão vulcânico pulsante",
                250.0, 4.0, Item.TipoItem.MATERIAL, Item.RaridadeItem.MITICO, 1, TamanhoItem.MEDIO);
        FichaMaterial fichaMat = new FichaMaterial(FichaMaterial.SubtipoMaterial.ESPOLIO_MONSTRO, 5);
        fichaMat.adicionarEfeitoPossivel(new EfeitoItem(99, "Calor Vulcânico", EfeitoItem.GatilhoEfeito.AO_ACERTAR, "2d6", "Fogo", 3, "Queima contínua"));
        coracaoDragao.setFichaMaterial(fichaMat);

        // Monstro abatido em caverna do deserto de Sunreach
        coracaoDragao.configurarComoDropDeMonstro(
                "Dragão Abissal de Sunreach",
                Item.RaridadeItem.MITICO,
                Geologia.CAVERNA,
                Regiao.DESERTO,
                Aquifero.OCEANO_AGUAS_PROFUNDAS,
                new Random(42)
        );

        afirmo(!coracaoDragao.isCraftado(), "Drop de monstro possui origem COLETAVEL_OU_DROP");
        afirmo(coracaoDragao.getRaridade() == Item.RaridadeItem.MITICO, "Raridade é Mítico");
        afirmo(coracaoDragao.getGeologiaOrigem() == Geologia.CAVERNA, "Herança de Geologia do monstro validada (CAVERNA)");
        afirmo(coracaoDragao.getRegiaoOrigem() == Regiao.DESERTO, "Herança de Região do monstro validada (DESERTO)");
        afirmo(coracaoDragao.getFichaMaterial().getEfeitoAtribuido() != null, "Efeito de crafting sorteado no abate do monstro");

        // Venda em Frostdale (Tundra, clima oposto ao deserto): Regra do Camarão paga 1.85x (+85%)!
        Cidade frostdale = new Cidade(1, "Frostdale", Cidade.TipoAssentamento.CIDADE,
                Regiao.TUNDRA, Geologia.FLORESTA, Aquifero.OCEANO_AGUAS_COSTEIRAS, true, false, null);
        double multVenda = coracaoDragao.calcularMultiplicadorRegional(frostdale);
        afirmo(multVenda == 1.85, "Espólio do dragão do deserto vale 1.85x como especiaria exótica na gélida Frostdale");
    }

    private static void testarPassivaExecutavelEMultiplicadorDano() {
        System.out.println("\n[14/14] Testando Passivas Executáveis, Multiplicação de Dano e Bônus na Ficha...");
        Ficha heroi = new Ficha(10, 10, 10, 10, 10, 10);
        afirmo(heroi.getMultiplicadorDanoTotal() == 1.0, "Multiplicador de dano base do herói é 1.0x");
        afirmo(heroi.getAtributoEfetivo("FORCA") == 10, "Força efetiva base é 10");

        // Cria Efeito que DOBRA O DANO (2.0x), dá +10 Força e cura 15 de HP
        EfeitoItem efeitoBerserk = new EfeitoItem(
                77, "Despertar do Berserker", EfeitoItem.GatilhoEfeito.ATIVACAO_DE_PASSIVA,
                "2d6", "Fogo", 3,
                Map.of("FORCA", 10, "DESTREZA", 5),
                2.0, // DOBRA O DANO!
                15   // Cura 15 HP!
        );

        PassivaItem passivaFuria = new PassivaItem(
                "Fúria Sangrenta",
                "Quando o HP cair abaixo de 30%",
                "Dobra o dano (2.0x), concede +10 Força e cura 15 HP",
                4, // 4 turnos de Cooldown
                PassivaItem.TipoCondicaoPassiva.VIDA_ABAIXO_PERCENTUAL,
                0.30, // 30% da vida
                efeitoBerserk
        );

        Item colar = new Item(301, "Colar do Sangue Selvagem", 50.0);
        colar.setTipo(Item.TipoItem.ACESSORIO);
        FichaAcessorio fichaAcc = new FichaAcessorio(FichaAcessorio.SlotAcessorio.ACESSORIO_GERAL);
        fichaAcc.adicionarPassiva(passivaFuria);
        colar.setFichaAcessorio(fichaAcc);

        heroi.adicionarItem(colar);
        heroi.equiparAcessorio(colar);

        // Com vida cheia (100%), o gatilho não dispara
        List<EfeitoItem> disparadosVidaCheia = heroi.verificarEGatilharPassivas(PassivaItem.TipoCondicaoPassiva.VIDA_ABAIXO_PERCENTUAL);
        afirmo(disparadosVidaCheia.isEmpty(), "Passiva de vida baixa não dispara com HP em 100%");
        afirmo(heroi.getMultiplicadorDanoTotal() == 1.0, "Dano continua em 1.0x");

        // Simula sofrer dano pesado: HP cai para 20 (bem abaixo de 30% do hpMax 70, ou seja, 28.5%)
        heroi.sofrerDano(50);
        afirmo(heroi.getHp() == 20, "Herói com vida crítica (20/70 HP, 28.5% da vida)");

        // Agora verifica o gatilho: a passiva deve disparar!
        List<EfeitoItem> disparadosCritico = heroi.verificarEGatilharPassivas(PassivaItem.TipoCondicaoPassiva.VIDA_ABAIXO_PERCENTUAL);
        afirmo(!disparadosCritico.isEmpty(), "Passiva executável disparou com sucesso!");
        afirmo(heroi.getMultiplicadorDanoTotal() == 2.0, "Multiplicador de dano da Ficha dobrou para 2.0x!");
        afirmo(heroi.getAtributoEfetivo("FORCA") == 20, "Força efetiva subiu de 10 para 20 (+10 de bônus)");
        afirmo(heroi.getHp() == 35, "Cura instantânea de +15 HP aplicada (vida subiu de 20 para 35)");
        afirmo(!passivaFuria.estaDisponivel(), "Passiva agora está em tempo de recarga");
    }
}
