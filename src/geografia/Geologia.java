package geografia;

/**
 * Representa os tipos de relevo e formações geológicas em Thaluria.
 */
public enum Geologia {
    MONTANHA("Montanha"),
    COLINA("Colina"),
    PLANICIE("Planície"),
    CAVERNA("Caverna"),
    VALE("Vale"),
    CANYON("Canyon"),
    FLORESTA("Floresta");

    private final String nomeFormatado;

    Geologia(String nomeFormatado) {
        this.nomeFormatado = nomeFormatado;
    }

    public String getNomeFormatado() {
        return nomeFormatado;
    }

    /**
     * Busca a geologia compatível por nome ou constante (case-insensitive).
     */
    public static Geologia parse(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return null;
        }
        String normalizado = valor.trim().toUpperCase();
        for (Geologia g : values()) {
            if (g.name().equalsIgnoreCase(normalizado) || 
                g.nomeFormatado.equalsIgnoreCase(valor.trim())) {
                return g;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return nomeFormatado;
    }
}