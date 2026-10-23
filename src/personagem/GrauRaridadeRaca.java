package personagem;

/**
 * Graus de Raridade para sorteio de raças na criação de ficha e gacha ponderado.
 */
public enum GrauRaridadeRaca {
    COMUM("Comum", 100),
    INCOMUM("Incomum", 50),
    RARA("Rara", 20),
    ANCESTRAL_MITICA("Ancestral / Mítica", 5);

    private final String descricao;
    private final int pesoBase;

    GrauRaridadeRaca(String descricao, int pesoBase) {
        this.descricao = descricao;
        this.pesoBase = pesoBase;
    }

    public String getDescricao() {
        return descricao;
    }

    public int getPesoBase() {
        return pesoBase;
    }
}
