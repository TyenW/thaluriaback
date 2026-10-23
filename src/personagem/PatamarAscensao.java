package personagem;

/**
 * Patamares de Ascensão exclusivos para Jogadores em Thaluria RPG (docs/Status.md).
 * Mapeados diretamente a partir do Potencial de Alma (PA), Nível de Ressonância (NR) e Ordem.
 */
public enum PatamarAscensao {
    MORTAL("Mortal", 0, 49, 0, 6, 0, "Desperto / Civil"),
    DESPERTO("Desperto", 50, 199, 7, 14, 1, "Vanguarda Novata"),
    ADEPTO("Adepto", 200, 599, 15, 24, 2, "Combatente Calejado"),
    ESPECIALISTA("Especialista", 600, 1399, 25, 37, 3, "Mestre de Ofício"),
    MESTRE("Mestre", 1400, 2799, 38, 52, 5, "Campeão Regional"),
    GRAO_MESTRE("Grão-mestre", 2800, 4999, 53, 70, 7, "Pilar do Mundo"),
    ASCENDENTE("Ascendente", 5000, 7499, 71, 86, 8, "Abaixo do Céu"),
    SUPREMO("Supremo", 7500, 9999, 87, 99, 9, "Divindade Terrena"),
    APICE("Ápice", 10000, 10000, 100, 100, 10, "O Ser Perfeito"),
    TRANSCENDENTE("Transcendente", 10001, Integer.MAX_VALUE, 101, Integer.MAX_VALUE, 11, "Anomalia Viva");

    private final String titulo;
    private final int paMin;
    private final int paMax;
    private final int nrMin;
    private final int nrMax;
    private final int ordemMin;
    private final String descricao;

    PatamarAscensao(String titulo, int paMin, int paMax, int nrMin, int nrMax, int ordemMin, String descricao) {
        this.titulo = titulo;
        this.paMin = paMin;
        this.paMax = paMax;
        this.nrMin = nrMin;
        this.nrMax = nrMax;
        this.ordemMin = ordemMin;
        this.descricao = descricao;
    }

    public String getTitulo() { return titulo; }
    public int getPaMin() { return paMin; }
    public int getPaMax() { return paMax; }
    public int getNrMin() { return nrMin; }
    public int getNrMax() { return nrMax; }
    public int getOrdemMin() { return ordemMin; }
    public String getDescricao() { return descricao; }

    public static PatamarAscensao dePA(double pa) {
        if (pa < 50) return MORTAL;
        if (pa < 200) return DESPERTO;
        if (pa < 600) return ADEPTO;
        if (pa < 1400) return ESPECIALISTA;
        if (pa < 2800) return MESTRE;
        if (pa < 5000) return GRAO_MESTRE;
        if (pa < 7500) return ASCENDENTE;
        if (pa < 10000) return SUPREMO;
        if (Math.round(pa) == 10000) return APICE;
        return TRANSCENDENTE;
    }
}
