package economia;

/**
 * Representa os 7 Graus de Feitura e Qualidade de Manufatura para itens craftados (OrigemItem.MANUFATURADO_CRAFT).
 * Escala simultaneamente dano, evolução de dados, slots de engaste, durabilidade, peso (ergonomia) e valor.
 */
public enum QualidadeCraft {
    PLEBEU(1, "Plebeu", 0.85, 0, 0, 0, 1.10, 0.80, 0, 0, 0.75),
    ARTESANAL(2, "Artesanal", 1.00, 0, 0, 1, 1.00, 1.00, 0, 0, 1.00),
    REFINADO(3, "Refinado", 1.15, 0, 1, 1, 0.95, 1.25, 1, 0, 1.35),
    NOBRE(4, "Nobre", 1.35, 2, 1, 2, 0.90, 1.50, 2, 1, 1.80),
    REAL(5, "Real", 1.60, 2, 2, 2, 0.85, 1.85, 3, 1, 2.50),
    IMPERIAL(6, "Imperial", 1.90, 4, 4, 3, 0.80, 2.30, 4, 2, 3.50),
    REGIO_ANCESTRAL(7, "Régio Ancestral", 2.30, 4, 6, 4, 0.75, 3.00, 6, 2, 5.00);

    private final int grau;
    private final String nomeFormatado;
    private final double multiplicadorAtributosEDano; // Escala RD, Amplificação, Status e Cura
    private final int bonusFacesDadoArma;             // +0, +2 ou +4 faces no dado da arma
    private final int bonusDanoFixo;                  // Bônus plano de dano
    private final int slotsEfeitoMaximos;             // Quantos efeitos de materiais cabem no item
    private final double multiplicadorPeso;           // Ergonomia: itens superiores pesam até 25% menos
    private final double multiplicadorDurabilidade;   // Escala Durabilidade da Armadura e HP do Escudo
    private final int bonusPaginasGrimorio;           // Círculos mágicos extras no grimório
    private final int reducaoCooldownPassivaTurnos;   // Reduz tempo de recarga das passivas
    private final double multiplicadorPrecoBase;      // Impacto no valor em Latão

    QualidadeCraft(int grau, String nomeFormatado, double multiplicadorAtributosEDano,
                   int bonusFacesDadoArma, int bonusDanoFixo, int slotsEfeitoMaximos,
                   double multiplicadorPeso, double multiplicadorDurabilidade,
                   int bonusPaginasGrimorio, int reducaoCooldownPassivaTurnos,
                   double multiplicadorPrecoBase) {
        this.grau = grau;
        this.nomeFormatado = nomeFormatado;
        this.multiplicadorAtributosEDano = multiplicadorAtributosEDano;
        this.bonusFacesDadoArma = bonusFacesDadoArma;
        this.bonusDanoFixo = bonusDanoFixo;
        this.slotsEfeitoMaximos = slotsEfeitoMaximos;
        this.multiplicadorPeso = multiplicadorPeso;
        this.multiplicadorDurabilidade = multiplicadorDurabilidade;
        this.bonusPaginasGrimorio = bonusPaginasGrimorio;
        this.reducaoCooldownPassivaTurnos = reducaoCooldownPassivaTurnos;
        this.multiplicadorPrecoBase = multiplicadorPrecoBase;
    }

    public boolean concedeDadoExtraArma() {
        return this == REGIO_ANCESTRAL;
    }

    public int getGrau() { return grau; }
    public String getNomeFormatado() { return nomeFormatado; }
    public double getMultiplicadorAtributosEDano() { return multiplicadorAtributosEDano; }
    public int getBonusFacesDadoArma() { return bonusFacesDadoArma; }
    public int getBonusDanoFixo() { return bonusDanoFixo; }
    public int getSlotsEfeitoMaximos() { return slotsEfeitoMaximos; }
    public double getMultiplicadorPeso() { return multiplicadorPeso; }
    public double getMultiplicadorDurabilidade() { return multiplicadorDurabilidade; }
    public int getBonusPaginasGrimorio() { return bonusPaginasGrimorio; }
    public int getReducaoCooldownPassivaTurnos() { return reducaoCooldownPassivaTurnos; }
    public double getMultiplicadorPrecoBase() { return multiplicadorPrecoBase; }

    public static QualidadeCraft doGrau(int grau) {
        int g = Math.max(1, Math.min(7, grau));
        for (QualidadeCraft qc : values()) {
            if (qc.getGrau() == g) return qc;
        }
        return PLEBEU;
    }

    @Override
    public String toString() {
        return String.format("%s (Grau %d, x%.2f)", nomeFormatado, grau, multiplicadorAtributosEDano);
    }
}
