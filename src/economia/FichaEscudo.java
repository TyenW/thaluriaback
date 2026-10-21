package economia;

/**
 * Ficha técnica especializada para escudos com HP próprio de bloqueio e limite de absorção por golpe (TipoItem.ESCUDO).
 */
public class FichaEscudo {

    public static class ResultadoBloqueio {
        private final int danoAbsorvido;
        private final int danoVazado;
        private final boolean escudoQuebrou;

        public ResultadoBloqueio(int danoAbsorvido, int danoVazado, boolean escudoQuebrou) {
            this.danoAbsorvido = danoAbsorvido;
            this.danoVazado = danoVazado;
            this.escudoQuebrou = escudoQuebrou;
        }

        public int getDanoAbsorvido() { return danoAbsorvido; }
        public int getDanoVazado() { return danoVazado; }
        public boolean isEscudoQuebrou() { return escudoQuebrou; }

        @Override
        public String toString() {
            return String.format("[Bloqueio: Absorvido=%d, Vazou=%d, Quebrou=%b]",
                    danoAbsorvido, danoVazado, escudoQuebrou);
        }
    }

    private FichaArmaBranca.Empunhadura empunhadura;
    private int hpMaximo;
    private int hpAtual;
    private int limiteSegurarPorGolpe; // Máximo que a estrutura aguenta amortecer de um só impacto

    public FichaEscudo(int hpMaximo, int limiteSegurarPorGolpe) {
        this(FichaArmaBranca.Empunhadura.UMA_MAO, hpMaximo, limiteSegurarPorGolpe);
    }

    public FichaEscudo(FichaArmaBranca.Empunhadura empunhadura, int hpMaximo, int limiteSegurarPorGolpe) {
        this.empunhadura = (empunhadura != null) ? empunhadura : FichaArmaBranca.Empunhadura.UMA_MAO;
        this.hpMaximo = Math.max(1, hpMaximo);
        this.hpAtual = this.hpMaximo;
        this.limiteSegurarPorGolpe = Math.max(1, limiteSegurarPorGolpe);
    }

    public void aplicarModificadorQualidade(QualidadeCraft qualidade) {
        if (qualidade == null) return;
        this.hpMaximo = Math.max(1, (int) Math.round(this.hpMaximo * qualidade.getMultiplicadorDurabilidade()));
        this.hpAtual = this.hpMaximo;
        this.limiteSegurarPorGolpe = Math.max(1, (int) Math.round(this.limiteSegurarPorGolpe * qualidade.getMultiplicadorAtributosEDano()));
    }

    /**
     * Absorve o golpe do atacante:
     * - Se dano <= limite: absorve tudo até o HP disponível do escudo.
     * - Se dano > limite: absorve até o limite, e o excesso vaza para o defensor.
     */
    public ResultadoBloqueio absorverGolpe(int danoRecebido) {
        if (danoRecebido <= 0) {
            return new ResultadoBloqueio(0, 0, estaQuebrado());
        }

        if (estaQuebrado()) {
            return new ResultadoBloqueio(0, danoRecebido, true);
        }

        int maximoAbsorvivelPelaEstrutura = Math.min(danoRecebido, this.limiteSegurarPorGolpe);
        int danoRealmenteAbsorvido = Math.min(maximoAbsorvivelPelaEstrutura, this.hpAtual);

        this.hpAtual -= danoRealmenteAbsorvido;
        boolean quebrou = (this.hpAtual <= 0);

        int danoVazado = danoRecebido - danoRealmenteAbsorvido;
        return new ResultadoBloqueio(danoRealmenteAbsorvido, Math.max(0, danoVazado), quebrou);
    }

    public void reparar(int qtd) {
        if (qtd > 0) {
            this.hpAtual = Math.min(this.hpMaximo, this.hpAtual + qtd);
        }
    }

    public boolean estaQuebrado() {
        return this.hpAtual <= 0;
    }

    public FichaArmaBranca.Empunhadura getEmpunhadura() { return empunhadura; }
    public void setEmpunhadura(FichaArmaBranca.Empunhadura empunhadura) { this.empunhadura = empunhadura; }

    public int getHpMaximo() { return hpMaximo; }
    public void setHpMaximo(int hpMaximo) { this.hpMaximo = Math.max(1, hpMaximo); }

    public int getHpAtual() { return hpAtual; }
    public void setHpAtual(int hpAtual) { this.hpAtual = Math.max(0, Math.min(this.hpMaximo, hpAtual)); }

    public int getLimiteSegurarPorGolpe() { return limiteSegurarPorGolpe; }
    public void setLimiteSegurarPorGolpe(int limiteSegurarPorGolpe) { this.limiteSegurarPorGolpe = Math.max(1, limiteSegurarPorGolpe); }

    @Override
    public String toString() {
        return String.format("Escudo (%s | HP: %d/%d | Limite Golpe: %d RD)",
                empunhadura, hpAtual, hpMaximo, limiteSegurarPorGolpe);
    }
}
