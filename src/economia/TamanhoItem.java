package economia;

/**
 * Modela o volume físico de um item e o espaço ocupado dentro da mochila do aventureiro.
 */
public enum TamanhoItem {
    MINUSCULO("Minúsculo", 0.1, true, "Anéis, gemas, moedas, ervas, agulhas"),
    PEQUENO("Pequeno", 1.0, true, "Adagas, poções, carnes cortadas, maçãs, grimórios de bolso"),
    MEDIO("Médio", 3.0, true, "Espadas longas, botas, elmos, lingotes, escudos leves"),
    GRANDE("Grande", 8.0, true, "Montantes, alabardas, peitorais pesados, peles de urso"),
    COLOSSAL("Colossal", 25.0, false, "Busto/Crânio de dragão, toras brutas de madeira, bigornas");

    private final String nomeFormatado;
    private final double slotsMochila;
    private final boolean cabeEmMochilaComum;
    private final String exemplos;

    TamanhoItem(String nomeFormatado, double slotsMochila, boolean cabeEmMochilaComum, String exemplos) {
        this.nomeFormatado = nomeFormatado;
        this.slotsMochila = slotsMochila;
        this.cabeEmMochilaComum = cabeEmMochilaComum;
        this.exemplos = exemplos;
    }

    public String getNomeFormatado() {
        return nomeFormatado;
    }

    public double getSlotsMochila() {
        return slotsMochila;
    }

    public boolean isCabeEmMochilaComum() {
        return cabeEmMochilaComum;
    }

    public String getExemplos() {
        return exemplos;
    }

    @Override
    public String toString() {
        return String.format("%s (%.1f slot%s)", nomeFormatado, slotsMochila, slotsMochila > 1.0 ? "s" : "");
    }
}
