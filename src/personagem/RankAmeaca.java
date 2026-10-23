package personagem;

/**
 * Ranks Oficiais de Monstros e Perigos (Índice de Ameaça - IA) em Thaluria RPG (docs/Status.md).
 * Compartilha a mesma equação algébrica do Potencial de Alma (PA), unificando Jogador vs Monstro.
 */
public enum RankAmeaca {
    RANK_F("Rank F", 0, 49, 0, "Civis e Criaturas Fracas"),
    RANK_E("Rank E", 50, 199, 1, "Aventureiro Inicial / Guarda"),
    RANK_D("Rank D", 200, 599, 2, "Veterano / Monstro Médio"),
    RANK_C("Rank C", 600, 1399, 3, "Elite / Capitão / Quimera"),
    RANK_B("Rank B", 1400, 2799, 5, "Herói Regional / Demônio"),
    RANK_A("Rank A", 2800, 4999, 7, "Pilar de Reino / Dragão"),
    RANK_S("Rank S", 5000, 7499, 8, "Lenda Viva / Catástrofe"),
    RANK_SS("Rank SS", 7500, 9999, 9, "Semideus Mortal"),
    RANK_SSS("Rank SSS", 10000, 10000, 10, "O Ápice Mortal"),
    RANK_EX("Rank EX", 10001, Integer.MAX_VALUE, 11, "Extrapolação / Calamidades");

    private final String rotulo;
    private final int iaMin;
    private final int iaMax;
    private final int grauEquivalente;
    private final String representacao;

    RankAmeaca(String rotulo, int iaMin, int iaMax, int grauEquivalente, String representacao) {
        this.rotulo = rotulo;
        this.iaMin = iaMin;
        this.iaMax = iaMax;
        this.grauEquivalente = grauEquivalente;
        this.representacao = representacao;
    }

    public String getRotulo() { return rotulo; }
    public int getIaMin() { return iaMin; }
    public int getIaMax() { return iaMax; }
    public int getGrauEquivalente() { return grauEquivalente; }
    public String getRepresentacao() { return representacao; }

    public static RankAmeaca deIA(double ia) {
        if (ia < 50) return RANK_F;
        if (ia < 200) return RANK_E;
        if (ia < 600) return RANK_D;
        if (ia < 1400) return RANK_C;
        if (ia < 2800) return RANK_B;
        if (ia < 5000) return RANK_A;
        if (ia < 7500) return RANK_S;
        if (ia < 10000) return RANK_SS;
        if (Math.round(ia) == 10000) return RANK_SSS;
        return RANK_EX;
    }
}
