package teste;

import personagem.*;
import java.util.*;

/**
 * Suíte de Testes Automatizados para a Camada v7:
 * Consolidação Matemática de Status, Sobrescrita de Constantes Raciais,
 * Compilador de Grafiturgia Elemental, Perícias, Habilidades Inatas e
 * Ecossistema Jogador vs Mestre (Sorteador, Tempo Real e Fog-of-War).
 */
public class TesteCamadaV7 {

    private static int totalTestes = 0;
    private static int testesPassados = 0;

    public static void main(String[] args) {
        System.out.println("======================================================================");
        System.out.println("   🔮 INICIANDO SUITE DE TESTES DA CAMADA v7 (THALURIA RPG)           ");
        System.out.println("======================================================================\n");

        testarFormulasCanonicasFicha();
        testarCatalogoRacasESobrescritas();
        testarEfeitosPraticosConstantesRaciais();
        testarDescansoMetabolicoEColapso();
        testarProgressaoPEETrava50Pontos();
        testarEquacaoPoderPAIAESintonia();
        testarPericiasERolagensD20();
        testarHabilidadesInatasECombate();
        testarCompiladorGrafiturgiaElemental();
        testarMetamagiaAvancadaGrafiturgia();
        testarSorteadorPitySessaoEFogOfWar();

        System.out.println("\n======================================================================");
        if (testesPassados == totalTestes) {
            System.out.println(String.format("   ✨ RESULTADO FINAL: %d/%d TESTES DA CAMADA v7 PASSARAM COM 100%% DE SUCESSO!",
                    testesPassados, totalTestes));
        } else {
            System.err.println(String.format("   ❌ FALHA: Apenas %d de %d testes passaram.",
                    testesPassados, totalTestes));
            System.exit(1);
        }
        System.out.println("======================================================================\n");
    }

    private static void afirme(boolean condicao, String mensagem) {
        totalTestes++;
        if (condicao) {
            testesPassados++;
            System.out.println("  ✔ [PASSOU] " + mensagem);
        } else {
            System.err.println("  ❌ [FALHOU] " + mensagem);
            throw new AssertionError("Falha na asserção: " + mensagem);
        }
    }

    // =========================================================================
    // 1. FÓRMULAS CANÔNICAS DE STATUS DA FICHA HUMANA
    // =========================================================================
    private static void testarFormulasCanonicasFicha() {
        System.out.println("[1/11] Testando Malha Canônica de Recursos e Defesas de Status.md...");

        // Ficha com todos os 6 atributos no valor 10 (Grau 1)
        Ficha f = new Ficha(10, 10, 10, 10, 10, 10, Raca.HUMANO);

        // HP: 20 + (10*3) + 10 + 10 = 70
        afirme(f.getHpMax() == 70, "HP Canônico: 20 + (10*3) + 10 + 10 = 70");

        // SAN: 20 + (10*3) + 10 + 10 = 70
        afirme(f.getSanidadeMax() == 70, "SAN Canônica: 20 + (10*3) + 10 + 10 = 70");

        // Energia: 10 + (10*2) + 10 + 10 = 50
        afirme(f.getEnergiaMax() == 50, "Energia Canônica: 10 + (10*2) + 10 + 10 = 50");

        // Mana: 10 + (10*2) + 10 + 10 = 50
        afirme(f.getManaMax() == 50, "Mana Canônica: 10 + (10*2) + 10 + 10 = 50");

        // Defesa Física: 5 + 10 + ⌊10/2⌋ = 20
        afirme(f.getDefesa() == 20, "Defesa Física Canônica: 5 + 10 + 5 = 20");

        // Defesa Oculta: 5 + 10 + ⌊10/2⌋ = 20
        afirme(f.getDefesaOculta() == 20, "Defesa Oculta Canônica: 5 + 10 + 5 = 20");

        // Regen Mana: 1 + ⌊(10+10)/5⌋ = 5
        afirme(f.getManaRegen() == 5, "Regeneração de Mana: 1 + ⌊20/5⌋ = 5");

        // Compatibilidade de Aliases Legados da v4
        afirme(f.getAtributoEfetivo("CONSTITUICAO") == 10, "Alias CONSTITUICAO redireciona para VIG (10)");
        afirme(f.getAtributoEfetivo("INTELIGENCIA") == 10, "Alias INTELIGENCIA redireciona para MEN (10)");
        afirme(f.getAtributoEfetivo("SABEDORIA") == 10, "Alias SABEDORIA redireciona para VON (10)");
    }

    // =========================================================================
    // 2. CATÁLOGO COMPLETO DE RAÇAS E VALIDAÇÃO DE SOBRESCRITAS
    // =========================================================================
    private static void testarCatalogoRacasESobrescritas() {
        System.out.println("\n[2/11] Testando Catálogo Oficial de Raças e Regra de Sobrescritas (1 Estrutural + 1 Metabólica)...");

        List<Raca> jogaveis = Raca.getTodasRacasJogaveis();
        afirme(jogaveis.size() == 11, "Catálogo contém exatamente 11 raças jogáveis cadastradas");

        // Humano não possui sobrescritas (reproduz padrão)
        afirme(Raca.HUMANO.getParametros().contarSobrescritasEstruturais() == 0, "Humano possui 0 sobrescritas estruturais (Padrão canônico)");
        afirme(Raca.HUMANO.getParametros().contarSobrescritasMetabolicas() == 0, "Humano possui 0 sobrescritas metabólicas (Padrão canônico)");

        // Todas as 10 raças não-humanas devem ter estritamente 1 estrutural e 1 metabólica
        String[] naoHumanos = {
                "Anões", "Genasi do Gelo e Água", "Elfos da Floresta", "Fadas",
                "Homens-Lagarto", "Kobolds", "Draconatos", "Genasi do Fogo",
                "Drow", "Shadar-kai"
        };

        for (String nome : naoHumanos) {
            Raca r = Raca.buscarPorNome(nome);
            afirme(r != null, "Raça " + nome + " encontrada no catálogo");
            afirme(r.getParametros().contarSobrescritasEstruturais() == 1,
                    nome + " possui exatamente 1 sobrescrita estrutural");
            afirme(r.getParametros().contarSobrescritasMetabolicas() == 1,
                    nome + " possui exatamente 1 sobrescrita metabólica");
        }
    }

    // =========================================================================
    // 3. EFEITOS PRÁTICOS DAS CONSTANTES RACIAIS
    // =========================================================================
    private static void testarEfeitosPraticosConstantesRaciais() {
        System.out.println("\n[3/11] Testando Efeitos das Constantes Raciais nos Recursos da Ficha...");

        // Anões: Defesa Física = 8 + DES + ⌊FOR / 2⌋
        // Com FOR=10, DES=10, MEN=10 -> Defesa = 8 + 10 + 5 = 23 (Humano seria 20)
        Ficha anao = new Ficha(10, 10, 10, 10, 10, 10, Raca.ANOES);
        afirme(anao.getDefesa() == 23, "Anões: Defesa Física sobre para 8 + 10 + 5 = 23");
        afirme(anao.getTurnosMorte() == 6, "Anões: Turnos até morte (HP 0) = 5 + Grau_VIG(1) = 6");

        // Genasi Gelo/Água: Mana = 15 + (ESP * 3) + VON + VIG = 15 + 30 + 10 + 10 = 65
        Ficha genasiGelo = new Ficha(10, 10, 10, 10, 10, 10, Raca.GENASI_GELO_AGUA);
        afirme(genasiGelo.getManaMax() == 65, "Genasi Gelo/Água: Mana = 15 + (10*3) + 10 + 10 = 65");

        // Elfos da Floresta: Energia = 15 + (VIG * 2) + DES + MEN = 15 + 20 + 10 + 10 = 55
        Ficha elfo = new Ficha(10, 10, 10, 10, 10, 10, Raca.ELFOS_FLORESTA);
        afirme(elfo.getEnergiaMax() == 55, "Elfos da Floresta: Energia = 15 + 20 + 10 + 10 = 55");

        // Fadas: Defesa Física = 8 + (DES * 2) + ⌊MEN / 2⌋ = 8 + 20 + 5 = 33
        Ficha fada = new Ficha(10, 10, 10, 10, 10, 10, Raca.FADAS);
        afirme(fada.getDefesa() == 33, "Fadas: Defesa Física = 8 + (10*2) + 5 = 33");

        // Homens-Lagarto: HP = 25 + (VIG * 4) + FOR + VON = 25 + 40 + 10 + 10 = 85
        Ficha lagarto = new Ficha(10, 10, 10, 10, 10, 10, Raca.HOMENS_LAGARTO);
        afirme(lagarto.getHpMax() == 85, "Homens-Lagarto: HP = 25 + (10*4) + 10 + 10 = 85");

        // Kobolds: Defesa Física = 5 + DES + MEN = 5 + 10 + 10 = 25
        Ficha kobold = new Ficha(10, 10, 10, 10, 10, 10, Raca.KOBOLDS);
        afirme(kobold.getDefesa() == 25, "Kobolds: Defesa Física = 5 + 10 + 10 = 25");

        // Draconatos: HP = 25 + (VIG * 3) + (FOR * 2) + VON = 25 + 30 + 20 + 10 = 85
        Ficha draconato = new Ficha(10, 10, 10, 10, 10, 10, Raca.DRACONATOS);
        afirme(draconato.getHpMax() == 85, "Draconatos: HP = 25 + 30 + 20 + 10 = 85");

        // Genasi Fogo: Mana = 10 + (ESP * 2) + (VON * 2) + VIG = 10 + 20 + 20 + 10 = 60
        Ficha genasiFogo = new Ficha(10, 10, 10, 10, 10, 10, Raca.GENASI_FOGO);
        afirme(genasiFogo.getManaMax() == 60, "Genasi do Fogo: Mana = 10 + 20 + 20 + 10 = 60");
        afirme(genasiFogo.getManaRegen() == 7, "Genasi do Fogo: Regen Mana = 2 + ⌊20/4⌋ = 7");

        // Drow: Defesa Oculta = 8 + VON + ⌊MEN / 2⌋ = 8 + 10 + 5 = 23
        Ficha drow = new Ficha(10, 10, 10, 10, 10, 10, Raca.DROW);
        afirme(drow.getDefesaOculta() == 23, "Drow: Defesa Oculta = 8 + 10 + 5 = 23");

        // Shadar-kai: Sanidade = 25 + (VON * 4) + MEN + VIG = 25 + 40 + 10 + 10 = 85
        Ficha shadar = new Ficha(10, 10, 10, 10, 10, 10, Raca.SHADAR_KAI);
        afirme(shadar.getSanidadeMax() == 85, "Shadar-kai: Sanidade = 25 + 40 + 10 + 10 = 85");
    }

    // =========================================================================
    // 4. CICLO DE DESCANSO E COLAPSO (RECURSOS EM 0)
    // =========================================================================
    private static void testarDescansoMetabolicoEColapso() {
        System.out.println("\n[4/11] Testando Ciclo Metabólico, Descanso (H/Q) e Colapso de Recursos...");

        Ficha f = new Ficha(10, 10, 10, 10, 10, 10, Raca.HUMANO);
        f.setEnergia(0);
        f.setMana(0);
        f.setHp(10);
        f.setSanidade(10);

        // Descanso de 8 horas com conforto Padrão (Q=1.00) restaura 100% da Energia
        f.descansar(8, ConfortoDescanso.PADRAO);
        afirme(f.getEnergia() == f.getEnergiaMax(), "8 horas de descanso com Q=1.0 recuperam 100% de Energia");
        afirme(f.getMana() == f.getManaMax(), "8 horas de descanso com Q=1.0 recuperam 100% de Mana");

        // Custo Biológico Diferenciado de Regeneração de Mana
        // Humano queima 1 Energia para 2 Mana
        Ficha humano = new Ficha(10, 10, 10, 10, 10, 10, Raca.HUMANO);
        humano.setMana(0);
        humano.setEnergia(50);
        int manaGerada = humano.regenerarManaPorTurno(true);
        afirme(manaGerada == 5, "Humano regenerou 5 de Mana");
        afirme(humano.getEnergia() == 47, "Humano queimou 3 de Energia para gerar 5 de Mana (1 Energia / 2 Mana)");

        // Genasi Gelo queima 1 Energia para cada 4 Mana
        Ficha genasi = new Ficha(10, 10, 10, 10, 10, 10, Raca.GENASI_GELO_AGUA);
        genasi.setMana(0);
        genasi.setEnergia(50);
        int manaGenasi = genasi.regenerarManaPorTurno(true);
        afirme(manaGenasi == 5, "Genasi Gelo regenerou 5 de Mana");
        afirme(genasi.getEnergia() == 48, "Genasi queimou apenas 2 de Energia para gerar 5 de Mana (1 Energia / 4 Mana)");

        // Forçar habilidades em Recursos 0
        // Draconato desconta apenas metade do custo no HP
        Ficha draco = new Ficha(10, 10, 10, 10, 10, 10, Raca.DRACONATOS);
        draco.setEnergia(0);
        draco.setHp(80);
        int danoHp = draco.forcarHabilidadeFisicaSemEnergia(6);
        afirme(danoHp == 3, "Draconatos: técnica de custo 6 com Energia 0 desconta apenas 3 de HP (divisorHp = 2)");
        afirme(draco.getHp() == 77, "HP do draconato foi para 77");

        // Fadas descontam 1x em SAN (burnout reduzido pela metade)
        Ficha fada = new Ficha(10, 10, 10, 10, 10, 10, Raca.FADAS);
        fada.setMana(0);
        fada.setSanidade(70);
        int danoSanFada = fada.forcarMagiaSemMana(4);
        afirme(danoSanFada == 4, "Fadas: forçar magia de 4 sem Mana desconta 4 de SAN (1x)");

        // Humano desconta o dobro (2x) em SAN
        Ficha hum = new Ficha(10, 10, 10, 10, 10, 10, Raca.HUMANO);
        hum.setMana(0);
        hum.setSanidade(70);
        int danoSanHum = hum.forcarMagiaSemMana(4);
        afirme(danoSanHum == 8, "Humano: forçar magia de 4 sem Mana desconta 8 de SAN (2x)");
    }

    // =========================================================================
    // 5. PROGRESSÃO POR PE E TRAVA DOS 50 PONTOS
    // =========================================================================
    private static void testarProgressaoPEETrava50Pontos() {
        System.out.println("\n[5/11] Testando Progressão por PE, Pedágio de Grau e Trava dos 50 Pontos...");

        Ficha f = new Ficha(10, 10, 10, 10, 10, 10, Raca.HUMANO);
        f.setForca(5); // Grau 0
        f.setPontosEssencia(50);

        // Subir do 5 para o 6 custa Grau 0 + 1 = 1 PE
        afirme(f.calcularCustoPEParaEvoluir(Atributo.FOR) == 1, "Subir de 5 para 6 custa 1 PE");
        f.evoluirAtributoComPE(Atributo.FOR);
        afirme(f.getForca() == 6 && f.getPontosEssencia() == 49, "Força subiu para 6 e PE debitou 1");

        // Fechar dezena exata (9 -> 10) cobra pedágio de 2 * 9 = 18 PE
        f.setForca(9);
        afirme(f.calcularCustoPEParaEvoluir(Atributo.FOR) == 18, "Pedágio de Quebra de Grau (9->10) custa 18 PE (2 * 9)");
        boolean evoluiu = f.evoluirAtributoComPE(Atributo.FOR);
        afirme(evoluiu && f.getForca() == 10, "Força quebrou o grau e subiu para 10");
        afirme(f.getPontosEssencia() == (49 - 18), "PE debitou exatamente 18 do pedágio");

        // Teste da Trava dos 50 Pontos (Maior Status - Menor Status <= 50)
        f.setForca(60);
        f.setDes(10);
        f.setVig(10);
        f.setMen(10);
        f.setEsp(10);
        f.setVon(10);
        // Diferença atual: 60 - 10 = 50 (no limite permitido)
        afirme(f.podeEvoluirAtributo(Atributo.FOR) == false, "Trava dos 50 Pontos impede Força de subir para 61 enquanto o menor estiver em 10!");
        afirme(f.podeEvoluirAtributo(Atributo.DES) == true, "Destreza pode subir normalmente para equilibrar o chassi");
    }

    // =========================================================================
    // 6. EQUAÇÃO DE PODER (PA / IA), NR, ORDEM E SINTONIA DE GRUPO
    // =========================================================================
    private static void testarEquacaoPoderPAIAESintonia() {
        System.out.println("\n[6/11] Testando Equação de Poder (PA / IA), NR, Ordem e Sintonia de Grupo...");

        // Ficha equilibrada com todos em 10
        Ficha f1 = new Ficha(10, 10, 10, 10, 10, 10, Raca.HUMANO);
        double pa = f1.calcularPA();
        afirme(pa > 0, "PA calculado com sucesso: " + pa);
        int nr = f1.getNivelRessonancia();
        int ordem = f1.getOrdem();
        afirme(nr == (int) Math.floor(Math.sqrt(pa)), "Nível de Ressonância é ⌊sqrt(PA)⌋ = " + nr);
        afirme(ordem == nr / 10, "Ordem é ⌊NR / 10⌋ = " + ordem);

        // Ficha com atributos em 100 (Ápice Mortal: PA ~10.000)
        Ficha apice = new Ficha(100, 100, 100, 100, 100, 100, Raca.HUMANO);
        double paApice = apice.calcularPA();
        afirme(Math.round(paApice) == 10000, "Ser perfeito no 100 possui PA exatamente normalizado em 10.000");
        afirme(apice.getPatamarAscensao() == PatamarAscensao.APICE, "Patamar é Ápice");
        afirme(apice.getRankAmeaca() == RankAmeaca.RANK_SSS, "Monstro perfeito no 100 é Rank SSS");

        // Ficha Transcendente (Rank EX com status >= 101) ignora o redutor de estabilidade
        Ficha ex = new Ficha(110, 100, 100, 100, 100, 100, Raca.HUMANO);
        afirme(ex.getPatamarAscensao() == PatamarAscensao.TRANSCENDENTE, "Status 110 atinge Patamar Transcendente");
        afirme(ex.getRankAmeaca() == RankAmeaca.RANK_EX, "Criatura com status 110 atinge Rank EX");

        // Trava de Sintonia de Grupo (|Delta NR| <= 5)
        Ficha fA = new Ficha(10, 10, 10, 10, 10, 10, Raca.HUMANO); // NR ~ 9
        Ficha fB = new Ficha(12, 12, 12, 12, 12, 12, Raca.HUMANO); // Próximo
        Ficha fC = new Ficha(50, 50, 50, 50, 50, 50, Raca.HUMANO); // Muito superior

        afirme(fA.temSintoniaDeGrupoCom(fB), "fA e fB possuem Sintonia de Grupo (|ΔNR| <= 5)");
        afirme(!fA.temSintoniaDeGrupoCom(fC), "fA e fC REJEITAM Sintonia de Grupo por abismo tático (|ΔNR| > 5)");
    }

    // =========================================================================
    // 7. AS 14 PERÍCIAS CANÔNICAS E ROLAGEM D20
    // =========================================================================
    private static void testarPericiasERolagensD20() {
        System.out.println("\n[7/11] Testando as 14 Perícias Canônicas e Regra do d20 (Grau 0 vs Grau >= 1)...");

        afirme(Pericia.values().length == 15, "Existem exatamente 15 perícias canônicas registradas");

        Ficha f = new Ficha(20, 20, 20, 20, 20, 20, Raca.HUMANO); // Grau 2 em tudo

        // Trava de Atributo: Grau de Perícia não pode superar o Grau do Atributo Base
        afirme(f.setPericia(Pericia.OCULTISMO_ARCANISMO, 2, 3), "Alocou Grau 2 e Maestria 3 em Ocultismo");
        afirme(!f.setPericia(Pericia.OCULTISMO_ARCANISMO, 3, 3), "Bloqueou alocar Grau 3 em Ocultismo porque ESP está no Grau 2!");
        afirme(!f.setPericia(Pericia.OCULTISMO_ARCANISMO, 2, 4), "Bloqueou Maestria 4 (Máximo permitido é 3)!");

        // Regra do Grau 0: Rola 2d20 e pega o PIOR resultado (+0)
        int rolagemGrau0 = Pericia.rolarDeterministico(0, 0, 18, 4);
        afirme(rolagemGrau0 == 4, "Grau 0 com dados (18, 4) pegou o pior resultado: 4");

        // Regra do Grau >= 1: Rola (Grau)d20 e pega o MELHOR + (Maestria * 5)
        // Grau 2, Maestria 2 (+10), dados (7, 15) -> melhor 15 + 10 = 25
        int rolagemGrau2 = Pericia.rolarDeterministico(2, 2, 7, 15);
        afirme(rolagemGrau2 == 25, "Grau 2 com Maestria 2 e dados (7, 15) resultou em 15 + 10 = 25");
    }

    // =========================================================================
    // 8. TÉCNICAS E HABILIDADES DE COMBATE INATAS
    // =========================================================================
    private static void testarHabilidadesInatasECombate() {
        System.out.println("\n[8/11] Testando Técnicas Inatas das Raças e Execução de Combate...");

        Ficha anao = new Ficha(10, 10, 10, 10, 10, 10, Raca.ANOES);
        Ficha alvo = new Ficha(10, 10, 10, 10, 10, 10, Raca.HUMANO);

        // Impacto Sísmico: 1d4 + FOR(10) + VIG(10)
        // Rolando 4 no d4 -> Dano bruto 4 + 10 + 10 = 24. Defesa Física do alvo é 20 -> Dano real = 4
        HabilidadeCombate impacto = anao.getRaca().getHabilidadeInata();
        int danoReal = impacto.executar(anao, alvo, 4);
        afirme(danoReal == 4, "Impacto Sísmico infligiu 4 de dano físico no alvo");
        afirme(alvo.getHp() == (alvo.getHpMax() - 4), "HP do alvo reduziu em 4");

        // Bote da Mandíbula Voraz dos Homens-Lagarto: restaura 5 + Grau_VIG de Energia do predador
        Ficha lagarto = new Ficha(10, 10, 10, 10, 10, 10, Raca.HOMENS_LAGARTO);
        lagarto.setEnergia(10);
        HabilidadeCombate bote = lagarto.getRaca().getHabilidadeInata();
        bote.executar(lagarto, alvo, 4);
        // Custo 3 energia, restaura 5 + 1 = 6 -> energia final: 10 - 3 + 6 = 13
        afirme(lagarto.getEnergia() == 13, "Homem-Lagarto recuperou energia com a mordida predatória");

        // Draconatos: Sopro Primordial (2d6 + FOR + ESP) com afinidade 1d8
        Ficha draco = new Ficha(10, 10, 10, 10, 10, 10, Raca.DRACONATOS);
        HabilidadeCombate sopro = draco.getRaca().getHabilidadeInata();
        afirme(sopro.getDadoBase().equals("2d6"), "Dado fixo do sopro draconiano é 2d6");
        afirme(sopro.getAfinidadeDraconica1d8() == 1, "Afinidade inicial configurada");
    }

    // =========================================================================
    // 9. COMPILADOR DE GRAFITURGIA ELEMENTAL E MATRIZ DE ADJACÊNCIA
    // =========================================================================
    private static void testarCompiladorGrafiturgiaElemental() {
        System.out.println("\n[9/11] Testando Compilador de Grafiturgia Elemental e Matriz de Adjacência...");

        // 1. Conexão Proibida Direta (Fogo -> Gelo sem ponte): W = ∞ -> INVERSAO_FEITICO
        GrafoArcano magiaInvertida = new GrafoArcano("Curto-Circuito Térmico", GrafoArcano.TopologiaMagia.CAMINHO_HAMILTONIANO);
        GrafoArcano.VerticeArcano vFogo = magiaInvertida.adicionarVertice(1, GrafoArcano.ElementoArcano.FOGO);
        GrafoArcano.VerticeArcano vGelo = magiaInvertida.adicionarVertice(2, GrafoArcano.ElementoArcano.GELO);
        magiaInvertida.conectar(vFogo, vGelo, true);

        afirme(magiaInvertida.contemLigacaoProibidaDireta(), "Identificou conexão direta entre Fogo e Gelo como Proibida (W=∞)");
        GrafoArcano.ResultadoCompilacao resInvertida = magiaInvertida.compilar(2, 1, 20);
        afirme(resInvertida.getConsequencia() == GrafoArcano.ConsequenciaCompilacao.INVERSAO_FEITICO,
                "Compilação disparou INVERSAO_FEITICO imediata!");

        // 2. Lei da Ponte: Fogo -> Vento (W=1) -> Gelo (W=1) com Vento como Ponte Estabilizadora!
        GrafoArcano lancaNevasca = new GrafoArcano("Lança da Nevasca Incandescente", GrafoArcano.TopologiaMagia.CAMINHO_HAMILTONIANO);
        GrafoArcano.VerticeArcano f = lancaNevasca.adicionarVertice(1, GrafoArcano.ElementoArcano.FOGO);
        GrafoArcano.VerticeArcano v = lancaNevasca.adicionarVertice(2, GrafoArcano.ElementoArcano.VENTO);
        GrafoArcano.VerticeArcano g = lancaNevasca.adicionarVertice(3, GrafoArcano.ElementoArcano.GELO);

        lancaNevasca.conectar(f, v, true); // W=1
        lancaNevasca.conectar(v, g, true); // W=1

        afirme(!lancaNevasca.contemLigacaoProibidaDireta(), "Feitiço com Ponte é perfeitamente estável!");
        afirme(lancaNevasca.getSomaPesos() == 2, "Soma dos pesos W = 1 + 1 = 2");
        afirme(lancaNevasca.getCustoMana(1) == 4, "Custo de Mana = (2 * 2 * 1) = 4");

        // 3. Checagem de Limite Mental e Sobrecarga:
        // Conjurador com MEN 0 (Grau 0) -> Limite é 2 + 0 = 2 vértices. A lancaNevasca tem 3 vértices!
        afirme(!lancaNevasca.isCognitivamenteEstavel(0), "Conjurador de Grau 0 entra em Sobrecarga Neural com 3 vértices");
        int dt = lancaNevasca.calcularDTSobrecarga(); // 10 + (5*3) + (2*2) = 29
        afirme(dt == 29, "DT de Sobrecarga Neural calculada: 10 + 15 + 4 = 29");

        // Teste superado com Arcanismo 30 >= DT 29
        GrafoArcano.ResultadoCompilacao resSobrecarga = lancaNevasca.compilar(0, 1, 30);
        afirme(resSobrecarga.getConsequencia() == GrafoArcano.ConsequenciaCompilacao.SUCESSO_SOBRECARGA_ESTABILIZADA,
                "Sobrecarga superada com sucesso pelo teste de Arcanismo!");

        // 4. Barreira Regular (K3: 3 nós, k=2 arestas por nó, Grau 1):
        // HP = 5*3 + 5*2*1 = 25 | RD = 2*1 = 2
        GrafoArcano barreira = new GrafoArcano("Baluarte de Diamante", GrafoArcano.TopologiaMagia.BARREIRA_REGULAR);
        barreira.adicionarVertice(1, GrafoArcano.ElementoArcano.TERRA);
        barreira.adicionarVertice(2, GrafoArcano.ElementoArcano.ORDEM);
        barreira.adicionarVertice(3, GrafoArcano.ElementoArcano.GELO);
        barreira.setKRegularidade(2);
        afirme(barreira.getHpBarreiraRegular(1) == 25, "Barreira Regular K3 tem 25 de HP");
        afirme(barreira.getRdBarreiraRegular(1) == 2, "Barreira Regular K3 tem RD 2");
    }

    // =========================================================================
    // 10. METAMAGIA AVANÇADA DA GRAFITURGIA
    // =========================================================================
    private static void testarMetamagiaAvancadaGrafiturgia() {
        System.out.println("\n[10/11] Testando Metamagia Avançada: Blossom, MST Kruskal, Emparelhamento e Articulação...");

        // 1. Contração de Blossom: ciclo ímpar C3 encapsulado em Super-Vértice
        GrafoArcano blossomInterno = new GrafoArcano("Detonação Interna Blossom", GrafoArcano.TopologiaMagia.CICLO_HAMILTONIANO);
        blossomInterno.adicionarVertice(101, GrafoArcano.ElementoArcano.ORDEM);
        blossomInterno.adicionarVertice(102, GrafoArcano.ElementoArcano.FOGO);
        blossomInterno.adicionarVertice(103, GrafoArcano.ElementoArcano.CAOS);

        GrafoArcano magiaComBlossom = new GrafoArcano("Flecha com Sub-rotina Blossom", GrafoArcano.TopologiaMagia.CAMINHO_HAMILTONIANO);
        magiaComBlossom.adicionarVertice(1, GrafoArcano.ElementoArcano.VENTO);
        magiaComBlossom.contrairBlossom(2, blossomInterno);

        // Vértices efetivos: 1 externo + (3 blossom - 1) = 3 (em vez de 4)
        afirme(magiaComBlossom.getVerticesEfetivosParaLimiteMental() == 3,
                "Contração de Blossom reduziu o processamento cognitivo para 3 vértices efetivos!");

        // 2. Poda de Kruskal / MST de Emergência
        GrafoArcano denso = new GrafoArcano("Malha Pesada", GrafoArcano.TopologiaMagia.CAMINHO_HAMILTONIANO);
        GrafoArcano.VerticeArcano v1 = denso.adicionarVertice(1, GrafoArcano.ElementoArcano.FOGO);
        GrafoArcano.VerticeArcano v2 = denso.adicionarVertice(2, GrafoArcano.ElementoArcano.VENTO);
        GrafoArcano.VerticeArcano v3 = denso.adicionarVertice(3, GrafoArcano.ElementoArcano.RAIO);

        denso.conectarComPeso(v1, v2, false, 1);
        denso.conectarComPeso(v2, v3, false, 1);
        denso.conectarComPeso(v1, v3, false, 2); // Aresta redundante de maior peso

        afirme(denso.getArestas().size() == 3, "Grafo possui 3 arestas antes da poda");
        GrafoArcano mst = denso.aplicarPodaKruskalMST();
        afirme(mst.getArestas().size() == 2, "Poda MST de Kruskal reduziu para exatamente |V|-1 = 2 arestas mínimas");
        afirme(mst.getSomaPesos() == 2, "Soma de pesos da MST é mínima: 1 + 1 = 2 (eliminou aresta de peso 2)");

        // 3. Vértices de Articulação (Cut-Vertices): Fogo -> Vento -> Gelo
        // Remover Vento divide o grafo em {Fogo} e {Gelo}
        GrafoArcano comArticulacao = new GrafoArcano("Ponte Frágil", GrafoArcano.TopologiaMagia.CAMINHO_HAMILTONIANO);
        GrafoArcano.VerticeArcano vf = comArticulacao.adicionarVertice(1, GrafoArcano.ElementoArcano.FOGO);
        GrafoArcano.VerticeArcano vv = comArticulacao.adicionarVertice(2, GrafoArcano.ElementoArcano.VENTO);
        GrafoArcano.VerticeArcano vg = comArticulacao.adicionarVertice(3, GrafoArcano.ElementoArcano.GELO);
        comArticulacao.conectar(vf, vv, true);
        comArticulacao.conectar(vv, vg, true);

        List<GrafoArcano.VerticeArcano> cortes = comArticulacao.encontrarVerticesDeArticulacao();
        afirme(cortes.size() == 1 && cortes.get(0).getId() == 2,
                "Identificou cirurgicamente o Vértice 2 (Vento) como Vértice de Articulação!");
    }

    // =========================================================================
    // 11. SORTEADOR PONDERADO (PITY), SESSÃO EM TEMPO REAL E FOG-OF-WAR
    // =========================================================================
    private static void testarSorteadorPitySessaoEFogOfWar() {
        System.out.println("\n[11/11] Testando Sorteador de Raças (Pity), Sessão em Tempo Real e Fog-of-War...");

        // 1. Sorteador: 3 opções (Humano sempre garantido + 2 sorteadas sem repetição)
        SorteadorRacaCriacao.OpcoesSorteio opcoes = SorteadorRacaCriacao.sortearOpcoesCriacao(0, false, new Random(42));
        afirme(opcoes.getOpcao1Humano() == Raca.HUMANO, "Opção 1 é sempre Humano garantido");
        afirme(opcoes.getOpcao2Sorteada() != null && opcoes.getOpcao3Sorteada() != null, "Opções 2 e 3 sorteadas");
        afirme(opcoes.getOpcao2Sorteada() != opcoes.getOpcao3Sorteada(), "Opções 2 e 3 foram sorteadas sem repetição");

        // 2. Rolagem inicial dentro dos limites estritos da raça escolhida (Anões: VIG 5-11, FOR 5-10)
        Map<Atributo, Integer> rolagens = SorteadorRacaCriacao.rolarAtributosIniciais(Raca.ANOES, new Random(42));
        afirme(rolagens.get(Atributo.VIG) >= 5 && rolagens.get(Atributo.VIG) <= 11, "VIG rolado dentro de [5, 11]");
        afirme(rolagens.get(Atributo.FOR) >= 5 && rolagens.get(Atributo.FOR) <= 10, "FOR rolado dentro de [5, 10]");

        // 3. Sessão de Criação de Ficha em Tempo Real
        SessaoCriacaoFicha sessao = new SessaoCriacaoFicha("jog_01", "Grombrindal");
        afirme(sessao.getRascunho().getEtapaAtual() == SessaoCriacaoFicha.EtapaCriacao.SORTEIO_RACAS, "Iniciou no sorteio");

        sessao.executarSorteioRacas(0, false, new Random(42));
        afirme(sessao.getRascunho().getEtapaAtual() == SessaoCriacaoFicha.EtapaCriacao.ESCOLHA_RACA, "Avançou para escolha");

        Raca racaEscolhida = sessao.getRascunho().getOpcoesRacas().getOpcao1Humano();
        sessao.escolherRaca(racaEscolhida);
        afirme(sessao.getRascunho().getRacaEscolhida() == Raca.HUMANO, "Escolheu Humano");

        sessao.rolarAtributosIniciais(new Random(42));
        sessao.concederERealizarRerollAssistido(new Random(99)); // Mestre autorizou reroll
        afirme(sessao.getRascunho().getRerollsAssistidosDisponiveis() == 1, "Reroll assistido registrado");

        sessao.enviarDicaOuObservacaoMestre("Distribua perícias com foco em persuasão.");
        sessao.aprovarPeloMestre();
        afirme(sessao.getRascunho().isAprovadoPeloMestre(), "Aprovado pelo Mestre");

        Ficha fichaFinal = sessao.finalizarFicha();
        afirme(fichaFinal != null && fichaFinal.getRaca() == Raca.HUMANO, "Ficha finalizada com sucesso!");

        // 4. Fog-of-War e Permissões do Mestre
        ControlePermissoesMestre fow = new ControlePermissoesMestre();
        afirme(!fow.isCapituloLiberado("Grafiturgia Elemental"), "Capítulo da Grafiturgia inicia bloqueado para os jogadores");
        fow.liberarCapituloWiki("Grafiturgia Elemental");
        afirme(fow.isCapituloLiberado("Grafiturgia Elemental"), "Mestre liberou o capítulo com um clique");

        afirme(fow.getNomeExibicaoMonstroJogador("DRAGAO_MAGMA", "Dragão de Magma").equals("??? (Criatura Desconhecida)"),
                "Monstro não estudado aparece como '???' para os jogadores");
        fow.revelarMonstroBestiario("DRAGAO_MAGMA");
        afirme(fow.getNomeExibicaoMonstroJogador("DRAGAO_MAGMA", "Dragão de Magma").equals("Dragão de Magma"),
                "Monstro estudado/revelado exibe o nome real");

        fow.setVisibilidadeCidade("Frosthelm", ControlePermissoesMestre.NivelVisibilidadeMapa.ROTA_E_NOME_APENAS);
        afirme(fow.getVisibilidadeCidade("Frosthelm") == ControlePermissoesMestre.NivelVisibilidadeMapa.ROTA_E_NOME_APENAS,
                "Visibilidade da cidade configurada como ROTA_E_NOME_APENAS no mapa");
    }
}
