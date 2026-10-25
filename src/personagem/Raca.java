package personagem;

import java.util.*;

/**
 * Representa uma Raça canônica de Thaluria RPG (docs/raças/ e docs/Compêndio de Raças por Reino.md).
 * Cada raça possui:
 * - Intervalos mínimos e máximos para rolagem inicial dos 6 atributos.
 * - Exatamente 1 modificador Estrutural e 1 modificador Metabólico encapsulados em ParametrosFormulaRacial.
 * - Habilidade de Combate inata.
 */
public class Raca {

    private final String nome;
    private final String reinoOrigem;
    private final DominanciaOntologica dominancia;
    private final GrauRaridadeRaca raridade;
    private final TipoAcessoRaca tipoAcesso;
    private boolean disponivelSorteioInicial;
    private final Map<Atributo, int[]> limitesAtributos;
    private final ParametrosFormulaRacial parametros;
    private final HabilidadeCombate habilidadeInata;

    public Raca(String nome, String reinoOrigem, DominanciaOntologica dominancia,
                GrauRaridadeRaca raridade, TipoAcessoRaca tipoAcesso,
                Map<Atributo, int[]> limitesAtributos,
                ParametrosFormulaRacial parametros,
                HabilidadeCombate habilidadeInata) {
        this(nome, reinoOrigem, dominancia, raridade, tipoAcesso, false, limitesAtributos, parametros, habilidadeInata);
    }

    public Raca(String nome, String reinoOrigem, DominanciaOntologica dominancia,
                GrauRaridadeRaca raridade, TipoAcessoRaca tipoAcesso,
                boolean disponivelSorteioInicial,
                Map<Atributo, int[]> limitesAtributos,
                ParametrosFormulaRacial parametros,
                HabilidadeCombate habilidadeInata) {
        this.nome = nome;
        this.reinoOrigem = reinoOrigem;
        this.dominancia = dominancia;
        this.raridade = raridade;
        this.tipoAcesso = tipoAcesso;
        this.disponivelSorteioInicial = disponivelSorteioInicial;
        this.limitesAtributos = new EnumMap<>(limitesAtributos);
        this.parametros = parametros != null ? parametros : ParametrosFormulaRacial.padrao();
        this.habilidadeInata = habilidadeInata;

        // Validação da regra canônica: Não-humanos devem ter exatamente 1 estrutural e 1 metabólico
        if (!"Humano".equalsIgnoreCase(nome)) {
            if (this.parametros.contarSobrescritasEstruturais() != 1) {
                throw new IllegalStateException(String.format("Raça %s deve possuir exatamente 1 sobrescrita estrutural (possui %d)",
                        nome, this.parametros.contarSobrescritasEstruturais()));
            }
            if (this.parametros.contarSobrescritasMetabolicas() != 1) {
                throw new IllegalStateException(String.format("Raça %s deve possuir exatamente 1 sobrescrita metabólica (possui %d)",
                        nome, this.parametros.contarSobrescritasMetabolicas()));
            }
        }
    }

    public String getNome() { return nome; }
    public String getId() { return nome; }
    public String getReinoOrigem() { return reinoOrigem; }
    public DominanciaOntologica getDominancia() { return dominancia; }
    public GrauRaridadeRaca getRaridade() { return raridade; }
    public TipoAcessoRaca getTipoAcesso() { return tipoAcesso; }
    public boolean isDisponivelSorteioInicial() { return disponivelSorteioInicial; }
    public void setDisponivelSorteioInicial(boolean disponivel) { this.disponivelSorteioInicial = disponivel; }
    public ParametrosFormulaRacial getParametros() { return parametros; }
    public HabilidadeCombate getHabilidadeInata() { return habilidadeInata; }

    public int[] getIntervalo(Atributo a) {
        return limitesAtributos.getOrDefault(a, new int[]{1, 10});
    }

    public int getMinimo(Atributo a) {
        return getIntervalo(a)[0];
    }

    public int getMaximo(Atributo a) {
        return getIntervalo(a)[1];
    }

    // =========================================================================
    // CATÁLOGO OFICIAL DE TODAS AS 11 RAÇAS DE THALURIA
    // =========================================================================

    public static final Raca HUMANO;
    public static final Raca ANOES;
    public static final Raca GENASI_GELO_AGUA;
    public static final Raca ELFOS_FLORESTA;
    public static final Raca FADAS;
    public static final Raca HOMENS_LAGARTO;
    public static final Raca KOBOLDS;
    public static final Raca DRACONATOS;
    public static final Raca GENASI_FOGO;
    public static final Raca DROW;
    public static final Raca SHADAR_KAI;

    private static final Map<String, Raca> CATALOGO = new LinkedHashMap<>();

    static {
        // 0. HUMANO (Mortal Padrão)
        Map<Atributo, int[]> limHumano = new EnumMap<>(Atributo.class);
        limHumano.put(Atributo.VIG, new int[]{3, 8});
        limHumano.put(Atributo.FOR, new int[]{3, 8});
        limHumano.put(Atributo.DES, new int[]{3, 8});
        limHumano.put(Atributo.MEN, new int[]{3, 8});
        limHumano.put(Atributo.ESP, new int[]{3, 8});
        limHumano.put(Atributo.VON, new int[]{3, 8});

        HabilidadeCombate golpeHumano = new HabilidadeCombate(
                "Determinação Humana (Golpe Oportunista)",
                HabilidadeCombate.TipoAcaoCombate.ATAQUE_CORPORAL,
                1, 0, 1.5, "1d4",
                List.of(Atributo.FOR, Atributo.DES),
                "Golpe adaptável e versátil em combate corpo a corpo"
        );

        HUMANO = new Raca("Humano", "Terras Centrais",
                DominanciaOntologica.HIBRIDA_EQUILIBRADA,
                GrauRaridadeRaca.COMUM, TipoAcessoRaca.JOGAVEL,
                false, // Fixo na Vaga 1 do sorteador
                limHumano, ParametrosFormulaRacial.padrao(), golpeHumano);

        // 1. ANÕES (Dwarves) - Frosthelm
        Map<Atributo, int[]> limAnoes = new EnumMap<>(Atributo.class);
        limAnoes.put(Atributo.VIG, new int[]{5, 11});
        limAnoes.put(Atributo.FOR, new int[]{5, 10});
        limAnoes.put(Atributo.DES, new int[]{2, 6});
        limAnoes.put(Atributo.MEN, new int[]{3, 7});
        limAnoes.put(Atributo.ESP, new int[]{1, 5});
        limAnoes.put(Atributo.VON, new int[]{4, 9});

        ParametrosFormulaRacial paramAnoes = ParametrosFormulaRacial.padrao().modificar()
                .defFisica(FormulaLinear.base(8).mais(Atributo.DES, 1).maisDiv(Atributo.FOR, 2).construir())
                .turnosMorte(FormulaLinear.base(5).mais(Atributo.VIG, 1).construir())
                .construir();

        HabilidadeCombate impactoSismico = new HabilidadeCombate(
                "Impacto Sísmico (Quebra-Rocha)",
                HabilidadeCombate.TipoAcaoCombate.ATAQUE_CORPORAL,
                2, 0, 1.5, "1d4",
                List.of(Atributo.FOR, Atributo.VIG),
                "Golpe de esmagamento com onda de choque em área que derruba inimigos (Caído)"
        );

        ANOES = new Raca("Anões", "Frosthelm",
                DominanciaOntologica.FISICA_MATERIA,
                GrauRaridadeRaca.COMUM, TipoAcessoRaca.JOGAVEL,
                true, // Disponível no sorteio inicial (Pool Vagas 2 e 3)
                limAnoes, paramAnoes, impactoSismico);

        // 2. GENASI DO GELO E ÁGUA - Frosthelm
        Map<Atributo, int[]> limGenasiGelo = new EnumMap<>(Atributo.class);
        limGenasiGelo.put(Atributo.VIG, new int[]{3, 7});
        limGenasiGelo.put(Atributo.FOR, new int[]{2, 6});
        limGenasiGelo.put(Atributo.DES, new int[]{4, 9});
        limGenasiGelo.put(Atributo.MEN, new int[]{3, 8});
        limGenasiGelo.put(Atributo.ESP, new int[]{5, 11});
        limGenasiGelo.put(Atributo.VON, new int[]{3, 8});

        ParametrosFormulaRacial paramGenasiGelo = ParametrosFormulaRacial.padrao().modificar()
                .mana(FormulaLinear.base(15).mais(Atributo.ESP, 3).mais(Atributo.VON, 1).mais(Atributo.VIG, 1).construir())
                .manaPorEnergia(4)
                .construir();

        HabilidadeCombate estilhacoGelo = new HabilidadeCombate(
                "Estilhaço Criogênico (Lança Glacial)",
                HabilidadeCombate.TipoAcaoCombate.PROJETIL,
                1, 3, 8.0, "1d8",
                List.of(Atributo.ESP, Atributo.DES),
                "Lanças pontiagudas de gelo que aplicam -2 Defesa Física e -50% deslocamento"
        );

        GENASI_GELO_AGUA = new Raca("Genasi do Gelo e Água", "Frosthelm",
                DominanciaOntologica.HIBRIDA_EQUILIBRADA,
                GrauRaridadeRaca.INCOMUM, TipoAcessoRaca.JOGAVEL,
                false, // Ativável pelo Mestre
                limGenasiGelo, paramGenasiGelo, estilhacoGelo);

        // 3. ELFOS DA FLORESTA (Wood Elves) - Sylvanor
        Map<Atributo, int[]> limElfos = new EnumMap<>(Atributo.class);
        limElfos.put(Atributo.VIG, new int[]{3, 7});
        limElfos.put(Atributo.FOR, new int[]{2, 6});
        limElfos.put(Atributo.DES, new int[]{5, 11});
        limElfos.put(Atributo.MEN, new int[]{4, 9});
        limElfos.put(Atributo.ESP, new int[]{3, 8});
        limElfos.put(Atributo.VON, new int[]{3, 7});

        ParametrosFormulaRacial paramElfos = ParametrosFormulaRacial.padrao().modificar()
                .energia(FormulaLinear.base(15).mais(Atributo.VIG, 2).mais(Atributo.DES, 1).mais(Atributo.MEN, 1).construir())
                .energiaDescanso(10, 3)
                .construir();

        HabilidadeCombate disparoZefiro = new HabilidadeCombate(
                "Disparo do Zéfiro (Flecha Guiada)",
                HabilidadeCombate.TipoAcaoCombate.PROJETIL,
                2, 0, 25.0, null,
                List.of(Atributo.DES, Atributo.MEN),
                "Disparo de projétil guiado por vento (+1d20 no acerto) sem quebrar furtividade"
        );

        ELFOS_FLORESTA = new Raca("Elfos da Floresta", "Sylvanor",
                DominanciaOntologica.HIBRIDA_EQUILIBRADA,
                GrauRaridadeRaca.COMUM, TipoAcessoRaca.JOGAVEL,
                true, // Disponível no sorteio inicial (Pool Vagas 2 e 3)
                limElfos, paramElfos, disparoZefiro);

        // 4. FADAS (Pixies / Fairy) - Sylvanor
        Map<Atributo, int[]> limFadas = new EnumMap<>(Atributo.class);
        limFadas.put(Atributo.VIG, new int[]{1, 4});
        limFadas.put(Atributo.FOR, new int[]{1, 3});
        limFadas.put(Atributo.DES, new int[]{5, 10});
        limFadas.put(Atributo.MEN, new int[]{4, 9});
        limFadas.put(Atributo.ESP, new int[]{6, 12});
        limFadas.put(Atributo.VON, new int[]{3, 8});

        ParametrosFormulaRacial paramFadas = ParametrosFormulaRacial.padrao().modificar()
                .defFisica(FormulaLinear.base(8).mais(Atributo.DES, 2).maisDiv(Atributo.MEN, 2).construir())
                .multiplicadorSanBurnout(1)
                .construir();

        HabilidadeCombate poeiraFeerica = new HabilidadeCombate(
                "Poeira Feérica Cintilante",
                HabilidadeCombate.TipoAcaoCombate.EXPLOSAO_MAGICA,
                0, 3, 4.0, "1d6",
                List.of(Atributo.ESP, Atributo.MEN),
                "Nuvem mágica de pólen estelar que cega e desorienta (-3 em testes por 1 rodada)"
        );

        FADAS = new Raca("Fadas", "Sylvanor",
                DominanciaOntologica.ETEREA_IMATERIAL,
                GrauRaridadeRaca.RARA, TipoAcessoRaca.JOGAVEL,
                true, // Disponível no sorteio inicial (Pool Vagas 2 e 3)
                limFadas, paramFadas, poeiraFeerica);

        // 5. HOMENS-LAGARTO (Lizardfolk) - Aurelia
        Map<Atributo, int[]> limLagarto = new EnumMap<>(Atributo.class);
        limLagarto.put(Atributo.VIG, new int[]{5, 11});
        limLagarto.put(Atributo.FOR, new int[]{5, 10});
        limLagarto.put(Atributo.DES, new int[]{3, 7});
        limLagarto.put(Atributo.MEN, new int[]{1, 5});
        limLagarto.put(Atributo.ESP, new int[]{1, 5});
        limLagarto.put(Atributo.VON, new int[]{4, 9});

        ParametrosFormulaRacial paramLagarto = ParametrosFormulaRacial.padrao().modificar()
                .hp(FormulaLinear.base(25).mais(Atributo.VIG, 4).mais(Atributo.FOR, 1).mais(Atributo.VON, 1).construir())
                .turnosMorte(FormulaLinear.base(4).maisDiv(Atributo.VIG, 2).maisDiv(Atributo.VON, 2).construir())
                .construir();

        HabilidadeCombate mordidaVoraz = new HabilidadeCombate(
                "Bote da Mandíbula Voraz",
                HabilidadeCombate.TipoAcaoCombate.ATAQUE_CORPORAL,
                3, 0, 1.5, "1d6",
                List.of(Atributo.FOR, Atributo.VIG),
                "Mordida brutal que agarra o alvo e restaura 5 + Grau_VIG de Energia do predador"
        );

        HOMENS_LAGARTO = new Raca("Homens-Lagarto", "Aurelia",
                DominanciaOntologica.FISICA_MATERIA,
                GrauRaridadeRaca.COMUM, TipoAcessoRaca.JOGAVEL,
                false, // Ativável pelo Mestre
                limLagarto, paramLagarto, mordidaVoraz);

        // 6. KOBOLDS - Aurelia
        Map<Atributo, int[]> limKobolds = new EnumMap<>(Atributo.class);
        limKobolds.put(Atributo.VIG, new int[]{2, 6});
        limKobolds.put(Atributo.FOR, new int[]{1, 5});
        limKobolds.put(Atributo.DES, new int[]{5, 11});
        limKobolds.put(Atributo.MEN, new int[]{4, 10});
        limKobolds.put(Atributo.ESP, new int[]{2, 6});
        limKobolds.put(Atributo.VON, new int[]{1, 5});

        ParametrosFormulaRacial paramKobolds = ParametrosFormulaRacial.padrao().modificar()
                .defFisica(FormulaLinear.base(5).mais(Atributo.DES, 1).mais(Atributo.MEN, 1).construir())
                .conforto(ConfortoDescanso.PESSIMO, 75)
                .conforto(ConfortoDescanso.PRECARIO, 100)
                .construir();

        HabilidadeCombate golpeSujo = new HabilidadeCombate(
                "Golpe Sujo e Frasco de Cal",
                HabilidadeCombate.TipoAcaoCombate.GOLPE_TATICO,
                2, 0, 3.0, "1d4",
                List.of(Atributo.DES, Atributo.MEN),
                "Arremesso de cal que cega oponente por 1 rodada e permite fuga sem ataque de oportunidade"
        );

        KOBOLDS = new Raca("Kobolds", "Aurelia",
                DominanciaOntologica.FISICA_MATERIA,
                GrauRaridadeRaca.COMUM, TipoAcessoRaca.JOGAVEL,
                false, // Ativável pelo Mestre
                limKobolds, paramKobolds, golpeSujo);

        // 7. DRACONATOS (Dragonborn) - Drakos
        Map<Atributo, int[]> limDraconatos = new EnumMap<>(Atributo.class);
        limDraconatos.put(Atributo.VIG, new int[]{5, 11});
        limDraconatos.put(Atributo.FOR, new int[]{5, 11});
        limDraconatos.put(Atributo.DES, new int[]{2, 6});
        limDraconatos.put(Atributo.MEN, new int[]{2, 7});
        limDraconatos.put(Atributo.ESP, new int[]{3, 8});
        limDraconatos.put(Atributo.VON, new int[]{4, 9});

        ParametrosFormulaRacial paramDraconatos = ParametrosFormulaRacial.padrao().modificar()
                .hp(FormulaLinear.base(25).mais(Atributo.VIG, 3).mais(Atributo.FOR, 2).mais(Atributo.VON, 1).construir())
                .divisorHpTecnicaFisica(2)
                .construir();

        HabilidadeCombate soproDraconiano = new HabilidadeCombate(
                "Sopro Primordial Draconiano",
                HabilidadeCombate.TipoAcaoCombate.SOPRO_ELEMENTAL,
                3, 3, 4.0, "2d6",
                List.of(Atributo.FOR, Atributo.ESP),
                "Sopro elemental (1d8 afinidade) com condições táticas e teste de resistência",
                1 // Padrão Fogo
        );

        DRACONATOS = new Raca("Draconatos", "Drakos",
                DominanciaOntologica.FISICA_MATERIA,
                GrauRaridadeRaca.ANCESTRAL_MITICA, TipoAcessoRaca.JOGAVEL,
                true, // Disponível no sorteio inicial (< 1%)
                limDraconatos, paramDraconatos, soproDraconiano);

        // 8. GENASI DO FOGO (Fire Genasi) - Drakos
        Map<Atributo, int[]> limGenasiFogo = new EnumMap<>(Atributo.class);
        limGenasiFogo.put(Atributo.VIG, new int[]{3, 7});
        limGenasiFogo.put(Atributo.FOR, new int[]{2, 6});
        limGenasiFogo.put(Atributo.DES, new int[]{4, 9});
        limGenasiFogo.put(Atributo.MEN, new int[]{3, 8});
        limGenasiFogo.put(Atributo.ESP, new int[]{5, 11});
        limGenasiFogo.put(Atributo.VON, new int[]{4, 9});

        ParametrosFormulaRacial paramGenasiFogo = ParametrosFormulaRacial.padrao().modificar()
                .mana(FormulaLinear.base(10).mais(Atributo.ESP, 2).mais(Atributo.VON, 2).mais(Atributo.VIG, 1).construir())
                .regenMana(2, 4)
                .construir();

        HabilidadeCombate pulsoBrasas = new HabilidadeCombate(
                "Pulso de Brasas e Onda de Calor",
                HabilidadeCombate.TipoAcaoCombate.EXPLOSAO_MAGICA,
                1, 3, 6.0, "1d8",
                List.of(Atributo.ESP, Atributo.VON),
                "Rajada de calor que empurra alvos 2m para trás e consome 2 de Energia do oponente"
        );

        GENASI_FOGO = new Raca("Genasi do Fogo", "Drakos",
                DominanciaOntologica.HIBRIDA_EQUILIBRADA,
                GrauRaridadeRaca.INCOMUM, TipoAcessoRaca.JOGAVEL,
                false, // Ativável pelo Mestre
                limGenasiFogo, paramGenasiFogo, pulsoBrasas);

        // 9. DROW (Elfos Negros) - Valthera
        Map<Atributo, int[]> limDrow = new EnumMap<>(Atributo.class);
        limDrow.put(Atributo.VIG, new int[]{2, 6});
        limDrow.put(Atributo.FOR, new int[]{2, 6});
        limDrow.put(Atributo.DES, new int[]{5, 11});
        limDrow.put(Atributo.MEN, new int[]{4, 10});
        limDrow.put(Atributo.ESP, new int[]{3, 8});
        limDrow.put(Atributo.VON, new int[]{4, 9});

        ParametrosFormulaRacial paramDrow = ParametrosFormulaRacial.padrao().modificar()
                .defOculta(FormulaLinear.base(8).mais(Atributo.VON, 1).maisDiv(Atributo.MEN, 2).construir())
                .sanDescanso(2, 10)
                .construir();

        HabilidadeCombate laminaVeneno = new HabilidadeCombate(
                "Lâmina do Veneno das Sombras",
                HabilidadeCombate.TipoAcaoCombate.ATAQUE_CORPORAL,
                2, 0, 6.0, null,
                List.of(Atributo.DES, Atributo.MEN),
                "Golpe furtivo com toxina paralisante (-3 Defesa Física e perda de Energia)"
        );

        DROW = new Raca("Drow", "Valthera",
                DominanciaOntologica.HIBRIDA_EQUILIBRADA,
                GrauRaridadeRaca.INCOMUM, TipoAcessoRaca.JOGAVEL,
                false, // Ativável pelo Mestre
                limDrow, paramDrow, laminaVeneno);

        // 10. SHADAR-KAI - Valthera
        Map<Atributo, int[]> limShadarKai = new EnumMap<>(Atributo.class);
        limShadarKai.put(Atributo.VIG, new int[]{3, 8});
        limShadarKai.put(Atributo.FOR, new int[]{2, 6});
        limShadarKai.put(Atributo.DES, new int[]{4, 9});
        limShadarKai.put(Atributo.MEN, new int[]{3, 7});
        limShadarKai.put(Atributo.ESP, new int[]{4, 9});
        limShadarKai.put(Atributo.VON, new int[]{5, 11});

        ParametrosFormulaRacial paramShadarKai = ParametrosFormulaRacial.padrao().modificar()
                .sanidade(FormulaLinear.base(25).mais(Atributo.VON, 4).mais(Atributo.MEN, 1).mais(Atributo.VIG, 1).construir())
                .rodadasLucidasSanZero(1)
                .construir();

        HabilidadeCombate ceifaEspectral = new HabilidadeCombate(
                "Ceifa Espectral do Vazio (Lâmina da Alma)",
                HabilidadeCombate.TipoAcaoCombate.ATAQUE_CORPORAL,
                1, 2, 3.0, null,
                List.of(Atributo.ESP, Atributo.VON),
                "Golpe necrótico pelas sombras que rouba vitalidade, curando HP do usuário"
        );

        SHADAR_KAI = new Raca("Shadar-kai", "Valthera",
                DominanciaOntologica.HIBRIDA_EQUILIBRADA,
                GrauRaridadeRaca.ANCESTRAL_MITICA, TipoAcessoRaca.JOGAVEL,
                false, // Ativável pelo Mestre
                limShadarKai, paramShadarKai, ceifaEspectral);

        // Registro no Catálogo Oficial
        registrar(HUMANO);
        registrar(ANOES);
        registrar(GENASI_GELO_AGUA);
        registrar(ELFOS_FLORESTA);
        registrar(FADAS);
        registrar(HOMENS_LAGARTO);
        registrar(KOBOLDS);
        registrar(DRACONATOS);
        registrar(GENASI_FOGO);
        registrar(DROW);
        registrar(SHADAR_KAI);
    }

    private static void registrar(Raca r) {
        CATALOGO.put(r.getNome().toUpperCase(Locale.ROOT), r);
    }

    public static Raca buscarPorNome(String nome) {
        if (nome == null) return null;
        return CATALOGO.get(nome.trim().toUpperCase(Locale.ROOT));
    }

    public static List<Raca> getTodasRacas() {
        return new ArrayList<>(CATALOGO.values());
    }

    public static List<Raca> getTodasRacasJogaveis() {
        List<Raca> jogaveis = new ArrayList<>();
        for (Raca r : CATALOGO.values()) {
            if (r.getTipoAcesso() == TipoAcessoRaca.JOGAVEL) {
                jogaveis.add(r);
            }
        }
        return jogaveis;
    }

    /**
     * Retorna a lista de raças ativas para o sorteio inicial nas vagas 2 e 3 (sem o Humano).
     * Por padrão: Anões, Elfos da Floresta, Fadas e Draconatos.
     */
    public static List<Raca> getRacasPoolSorteioInicial() {
        List<Raca> pool = new ArrayList<>();
        for (Raca r : CATALOGO.values()) {
            if (r.isDisponivelSorteioInicial() && !"Humano".equalsIgnoreCase(r.getNome())) {
                pool.add(r);
            }
        }
        return pool;
    }

    @Override
    public String toString() {
        return String.format("%s (%s | %s | %s)", nome, reinoOrigem, raridade.getDescricao(), dominancia.getDescricao());
    }
}
