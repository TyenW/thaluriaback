package personagem;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Os 6 Status Primários de Thaluria (docs/Status.md).
 * Pilar Físico: VIG, FOR, DES. Pilar Etéreo: MEN, ESP, VON.
 * Centraliza a resolução de nomes/aliases (inclusive os legados da v4).
 */
public enum Atributo {
    VIG("Vigor", true),
    FOR("Força", true),
    DES("Destreza", true),
    MEN("Mental", false),
    ESP("Espírito", false),
    VON("Vontade", false);

    private final String nomeCompleto;
    private final boolean fisico;

    Atributo(String nomeCompleto, boolean fisico) {
        this.nomeCompleto = nomeCompleto;
        this.fisico = fisico;
    }

    public String getNomeCompleto() { return nomeCompleto; }
    public boolean isFisico() { return fisico; }

    /** Grau = ⌊Valor / 10⌋. */
    public static int grauDe(int valor) {
        return Math.max(0, valor) / 10;
    }

    /**
     * Resolve um nome (sem distinção de acento/caixa) para o atributo.
     * Aliases legados: CONSTITUICAO→VIG, INTELIGENCIA→MEN, SABEDORIA→VON.
     * @return o atributo ou {@code null} se o texto não for um atributo (ex.: "RD").
     */
    public static Atributo parse(String texto) {
        if (texto == null) return null;
        String t = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
        return switch (t) {
            case "VIG", "VIGOR", "CONSTITUICAO" -> VIG;
            case "FOR", "FORCA" -> FOR;
            case "DES", "DESTREZA" -> DES;
            case "MEN", "MENTE", "MENTAL", "INTELIGENCIA" -> MEN;
            case "ESP", "ESPIRITO" -> ESP;
            case "VON", "VONTADE", "SABEDORIA" -> VON;
            default -> null;
        };
    }
}
