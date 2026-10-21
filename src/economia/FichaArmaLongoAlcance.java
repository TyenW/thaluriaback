package economia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ficha técnica especializada para armas de disparo e combate à distância (TipoItem.ARMA_LONGO_ALCANCE).
 * Não possui dano fixo próprio; atua como amplificador tático dos projéteis disparados.
 */
public class FichaArmaLongoAlcance {

    public enum SubtipoArmaDistante {
        ARCO_CURTO("Arco Curto", 1.20, 25, FichaArmaBranca.Empunhadura.DUAS_MAOS),
        ARCO_LONGO("Arco Longo de Guerra", 1.50, 60, FichaArmaBranca.Empunhadura.DUAS_MAOS),
        BESTA_LEVE("Besta Leve", 1.35, 35, FichaArmaBranca.Empunhadura.UMA_MAO),
        BESTA_PESADA("Besta Pesada (Arbalesta)", 1.75, 50, FichaArmaBranca.Empunhadura.DUAS_MAOS),
        ZARABATANA("Zarabatana", 1.10, 15, FichaArmaBranca.Empunhadura.UMA_MAO),
        ESTILINGUE("Estilingue / Funda", 1.15, 20, FichaArmaBranca.Empunhadura.UMA_MAO);

        private final String nomeFormatado;
        private final double amplificacaoBase;
        private final int alcanceMetrosBase;
        private final FichaArmaBranca.Empunhadura empunhadura;

        SubtipoArmaDistante(String nomeFormatado, double amplificacaoBase, int alcanceMetrosBase,
                            FichaArmaBranca.Empunhadura empunhadura) {
            this.nomeFormatado = nomeFormatado;
            this.amplificacaoBase = amplificacaoBase;
            this.alcanceMetrosBase = alcanceMetrosBase;
            this.empunhadura = empunhadura;
        }

        public String getNomeFormatado() { return nomeFormatado; }
        public double getAmplificacaoBase() { return amplificacaoBase; }
        public int getAlcanceMetrosBase() { return alcanceMetrosBase; }
        public FichaArmaBranca.Empunhadura getEmpunhadura() { return empunhadura; }
        @Override public String toString() { return nomeFormatado; }
    }

    private SubtipoArmaDistante subtipo;
    private FichaArmaBranca.Empunhadura empunhadura;
    private double multiplicadorAmplificacao; // Ex: 1.25x (+25% no dano do projétil)
    private int alcanceMetros;
    private int slotsDeEfeitoMaximos;
    private final List<EfeitoItem> efeitosEngastados = new ArrayList<>();

    public FichaArmaLongoAlcance(SubtipoArmaDistante subtipo) {
        this(subtipo, (subtipo != null) ? subtipo.getAmplificacaoBase() : 1.20,
                (subtipo != null) ? subtipo.getAlcanceMetrosBase() : 25, 1);
    }

    public FichaArmaLongoAlcance(SubtipoArmaDistante subtipo, double multiplicadorAmplificacao,
                                 int alcanceMetros, int slotsDeEfeitoMaximos) {
        this.subtipo = (subtipo != null) ? subtipo : SubtipoArmaDistante.ARCO_CURTO;
        this.empunhadura = this.subtipo.getEmpunhadura();
        this.multiplicadorAmplificacao = Math.max(1.0, multiplicadorAmplificacao);
        this.alcanceMetros = Math.max(1, alcanceMetros);
        this.slotsDeEfeitoMaximos = Math.max(0, slotsDeEfeitoMaximos);
    }

    public void aplicarModificadorQualidade(QualidadeCraft qualidade) {
        if (qualidade == null) return;
        this.multiplicadorAmplificacao = Math.round((this.multiplicadorAmplificacao * qualidade.getMultiplicadorAtributosEDano()) * 100.0) / 100.0;
        this.alcanceMetros = (int) Math.round(this.alcanceMetros * qualidade.getMultiplicadorAtributosEDano());
        this.slotsDeEfeitoMaximos = Math.max(this.slotsDeEfeitoMaximos, qualidade.getSlotsEfeitoMaximos());
    }

    /**
     * Calcula o dano final do disparo aplicando o multiplicador de amplificação sobre o projétil.
     */
    public int calcularDanoDisparo(int danoProjetil) {
        if (danoProjetil <= 0) return 0;
        return (int) Math.round(danoProjetil * this.multiplicadorAmplificacao);
    }

    public boolean adicionarEfeito(EfeitoItem efeito) {
        if (efeito != null && efeitosEngastados.size() < slotsDeEfeitoMaximos) {
            efeitosEngastados.add(efeito);
            return true;
        }
        return false;
    }

    public boolean removerEfeito(EfeitoItem efeito) {
        return efeitosEngastados.remove(efeito);
    }

    public SubtipoArmaDistante getSubtipo() { return subtipo; }
    public void setSubtipo(SubtipoArmaDistante subtipo) { this.subtipo = subtipo; }

    public FichaArmaBranca.Empunhadura getEmpunhadura() { return empunhadura; }
    public void setEmpunhadura(FichaArmaBranca.Empunhadura empunhadura) { this.empunhadura = empunhadura; }

    public double getMultiplicadorAmplificacao() { return multiplicadorAmplificacao; }
    public void setMultiplicadorAmplificacao(double multiplicadorAmplificacao) {
        this.multiplicadorAmplificacao = Math.max(1.0, multiplicadorAmplificacao);
    }

    public int getAlcanceMetros() { return alcanceMetros; }
    public void setAlcanceMetros(int alcanceMetros) { this.alcanceMetros = Math.max(1, alcanceMetros); }

    public int getSlotsDeEfeitoMaximos() { return slotsDeEfeitoMaximos; }
    public void setSlotsDeEfeitoMaximos(int slotsDeEfeitoMaximos) { this.slotsDeEfeitoMaximos = Math.max(0, slotsDeEfeitoMaximos); }

    public List<EfeitoItem> getEfeitosEngastados() { return Collections.unmodifiableList(efeitosEngastados); }

    @Override
    public String toString() {
        return String.format("%s (Amplificação: %.2fx | Alcance: %dm | Engastes: %d/%d)",
                subtipo, multiplicadorAmplificacao, alcanceMetros, efeitosEngastados.size(), slotsDeEfeitoMaximos);
    }
}
