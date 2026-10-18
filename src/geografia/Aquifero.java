package geografia;

/**
 * Representa os tipos de aquíferos e corpos hídricos no mundo de Thaluria.
 */
public enum Aquifero {
    LAGO_PEQUENO("Lago Pequeno"),
    LAGO_GRANDE("Lago Grande"),
    RIO("Rio"),
    OCEANO_AGUAS_PROFUNDAS("Oceano - Aguas Profundas"),
    OCEANO_AGUAS_COSTEIRAS("Oceano - Aguas Costeiras"),
    PANTANO("Pantano");

    private final String nomeFormatado;

    Aquifero(String nomeFormatado) {
        this.nomeFormatado = nomeFormatado;
    }

    public String getNomeFormatado() {
        return nomeFormatado;
    }

    /**
     * Busca o aquífero compatível por nome ou constante (case-insensitive).
     */
    public static Aquifero parse(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return null;
        }
        String normalizado = valor.trim().replace(" ", "_").toUpperCase();
        for (Aquifero a : values()) {
            if (a.name().equalsIgnoreCase(normalizado) || 
                a.nomeFormatado.equalsIgnoreCase(valor.trim())) {
                return a;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return nomeFormatado;
    }
}
