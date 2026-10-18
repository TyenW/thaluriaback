package geografia;

/**
 * Representa os grandes biomas e zonas climáticas do mundo de Thaluria.
 */
public enum Regiao {
    POLAR("Polar", "Frio extremo e geleiras perpétuas"),
    TUNDRA("Tundra", "Planícies gélidas e vegetação rasteira"),
    TAIGA("Taiga", "Florestas boreais densas de coníferas"),
    TEMPERADO("Temperado", "Clima ameno e bosques férteis"),
    TROPICAL("Tropical", "Florestas úmidas densas e alta biodiversidade"),
    SAVANA("Savana", "Campos abertos e estações secas"),
    DESERTO("Deserto", "Aridez severa, dunas e calor incandescente");

    // Aliases estáticos para compatibilidade com código que utilizava PascalCase
    public static final Regiao Polar = POLAR;
    public static final Regiao Tundra = TUNDRA;
    public static final Regiao Taiga = TAIGA;
    public static final Regiao Temperado = TEMPERADO;
    public static final Regiao Tropical = TROPICAL;
    public static final Regiao Savana = SAVANA;
    public static final Regiao Deserto = DESERTO;

    private final String nomeFormatado;
    private final String descricao;

    Regiao(String nomeFormatado, String descricao) {
        this.nomeFormatado = nomeFormatado;
        this.descricao = descricao;
    }

    public String getNomeFormatado() {
        return nomeFormatado;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Busca a região compatível por nome ou constante (case-insensitive).
     */
    public static Regiao parse(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return null;
        }
        String normalizado = valor.trim().toUpperCase();
        for (Regiao r : values()) {
            if (r.name().equalsIgnoreCase(normalizado) || 
                r.nomeFormatado.equalsIgnoreCase(valor.trim())) {
                return r;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return nomeFormatado;
    }
}
