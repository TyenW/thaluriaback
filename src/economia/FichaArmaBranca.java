package economia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ficha técnica especializada para armas brancas e combate corpo a corpo (TipoItem.ARMA_BRANCA).
 */
public class FichaArmaBranca {

    public enum Empunhadura {
        UMA_MAO("Uma Mão"),
        DUAS_MAOS("Duas Mãos");

        private final String descricao;

        Empunhadura(String descricao) {
            this.descricao = descricao;
        }

        public String getDescricao() {
            return descricao;
        }

        @Override
        public String toString() {
            return descricao;
        }
    }

    public enum SubtipoArmaBranca {
        // Espadas (11)
        ESPADA_CURTA("Espada Curta", Empunhadura.UMA_MAO, 1, 6),
        ESPADA_LONGA("Espada Longa", Empunhadura.UMA_MAO, 1, 8),
        ESPADA_CURVA("Espada Curva", Empunhadura.UMA_MAO, 1, 8),
        ESPADA_LARGA("Espada Larga (Broadsword)", Empunhadura.UMA_MAO, 1, 8),
        SABRE("Sabre", Empunhadura.UMA_MAO, 1, 6),
        CIMITARRA("Cimitarra", Empunhadura.UMA_MAO, 1, 6),
        ALFANJE("Alfanje", Empunhadura.UMA_MAO, 1, 8),
        RAPIEIRA("Rapieira", Empunhadura.UMA_MAO, 1, 8),
        FLORETE("Florete", Empunhadura.UMA_MAO, 1, 6),
        KATANA("Katana", Empunhadura.UMA_MAO, 1, 10),
        MONTANTE("Montante (Claymore / Duas Mãos)", Empunhadura.DUAS_MAOS, 2, 6),

        // Lâminas Curtas (3)
        ADAGA("Adaga", Empunhadura.UMA_MAO, 1, 4),
        KUKRI("Kukri", Empunhadura.UMA_MAO, 1, 4),
        KATAR("Katar", Empunhadura.UMA_MAO, 1, 6),

        // Machados (4)
        MACHADO_CURTO("Machado Curto / Machadinha", Empunhadura.UMA_MAO, 1, 6),
        MACHADO_DE_BATALHA("Machado de Batalha", Empunhadura.UMA_MAO, 1, 8),
        MACHADO_DUPLO("Machado de Lâmina Dupla", Empunhadura.DUAS_MAOS, 1, 12),
        MACHADO_DE_HASTE("Machado de Haste (Pollaxe)", Empunhadura.DUAS_MAOS, 1, 10),

        // Contusão (4)
        CLAVA("Clava", Empunhadura.UMA_MAO, 1, 6),
        MACA("Maça de Armas", Empunhadura.UMA_MAO, 1, 6),
        MARTELO_DE_GUERRA("Martelo de Guerra", Empunhadura.UMA_MAO, 1, 8),
        MARTELO_DE_DUAS_MAOS("Martelo de Guerra Pesado", Empunhadura.DUAS_MAOS, 2, 6),

        // Hastes e Alcance (8)
        CHICOTE("Chicote", Empunhadura.UMA_MAO, 1, 4),
        LANCA_CURTA("Lança Curta", Empunhadura.UMA_MAO, 1, 6),
        LANCA_LONGA("Lança Longa (Pique)", Empunhadura.DUAS_MAOS, 1, 8),
        ALABARDA("Alabarda", Empunhadura.DUAS_MAOS, 1, 10),
        GLAIVE("Glaive", Empunhadura.DUAS_MAOS, 1, 10),
        LANCA_DE_CAVALARIA("Lança de Cavalaria", Empunhadura.DUAS_MAOS, 1, 12),
        TRIDENTE("Tridente", Empunhadura.UMA_MAO, 1, 6),
        BIDENTE("Bidente", Empunhadura.UMA_MAO, 1, 6),

        // Pugilato / Curto Alcance (3)
        GARRAS("Garras de Combate", Empunhadura.UMA_MAO, 1, 4),
        SOQUEIRA("Soqueira", Empunhadura.UMA_MAO, 1, 4),
        MANOPLA_DE_COMBATE("Manopla de Combate", Empunhadura.UMA_MAO, 1, 6);

        private final String nomeFormatado;
        private final Empunhadura empunhaduraPadrao;
        private final int qtdDadosPadrao;
        private final int facesDadoPadrao;

        SubtipoArmaBranca(String nomeFormatado, Empunhadura empunhaduraPadrao, int qtdDadosPadrao, int facesDadoPadrao) {
            this.nomeFormatado = nomeFormatado;
            this.empunhaduraPadrao = empunhaduraPadrao;
            this.qtdDadosPadrao = qtdDadosPadrao;
            this.facesDadoPadrao = facesDadoPadrao;
        }

        public String getNomeFormatado() { return nomeFormatado; }
        public Empunhadura getEmpunhaduraPadrao() { return empunhaduraPadrao; }
        public int getQtdDadosPadrao() { return qtdDadosPadrao; }
        public int getFacesDadoPadrao() { return facesDadoPadrao; }
        @Override public String toString() { return nomeFormatado; }
    }

    private SubtipoArmaBranca subtipo;
    private Empunhadura empunhadura;
    private int quantidadeDados;
    private int facesDadoBase;
    private int bonusFacesDado; // Ex: metal nobre concedendo +2 faces (1d10 -> 1d12)
    private int bonusDanoFixo;  // Bônus plano de dano concedido por qualidades superiores
    private int slotsDeEfeitoMaximos;
    private final List<EfeitoItem> efeitosEngastados = new ArrayList<>();

    public FichaArmaBranca(SubtipoArmaBranca subtipo) {
        this(subtipo, (subtipo != null) ? subtipo.getEmpunhaduraPadrao() : Empunhadura.UMA_MAO,
                (subtipo != null) ? subtipo.getQtdDadosPadrao() : 1,
                (subtipo != null) ? subtipo.getFacesDadoPadrao() : 6, 0, 1);
    }

    public FichaArmaBranca(SubtipoArmaBranca subtipo, Empunhadura empunhadura,
                           int quantidadeDados, int facesDadoBase, int bonusFacesDado, int slotsDeEfeitoMaximos) {
        this(subtipo, empunhadura, quantidadeDados, facesDadoBase, bonusFacesDado, 0, slotsDeEfeitoMaximos);
    }

    public FichaArmaBranca(SubtipoArmaBranca subtipo, Empunhadura empunhadura,
                           int quantidadeDados, int facesDadoBase, int bonusFacesDado, int bonusDanoFixo, int slotsDeEfeitoMaximos) {
        this.subtipo = (subtipo != null) ? subtipo : SubtipoArmaBranca.ESPADA_LONGA;
        this.empunhadura = (empunhadura != null) ? empunhadura : this.subtipo.getEmpunhaduraPadrao();
        this.quantidadeDados = Math.max(1, quantidadeDados);
        this.facesDadoBase = Math.max(2, facesDadoBase);
        this.bonusFacesDado = Math.max(0, bonusFacesDado);
        this.bonusDanoFixo = Math.max(0, bonusDanoFixo);
        this.slotsDeEfeitoMaximos = Math.max(0, slotsDeEfeitoMaximos);
    }

    public void aplicarModificadorQualidade(QualidadeCraft qualidade) {
        if (qualidade == null) return;
        this.bonusFacesDado += qualidade.getBonusFacesDadoArma();
        this.bonusDanoFixo += qualidade.getBonusDanoFixo();
        this.slotsDeEfeitoMaximos = Math.max(this.slotsDeEfeitoMaximos, qualidade.getSlotsEfeitoMaximos());
        if (qualidade.concedeDadoExtraArma()) {
            this.quantidadeDados += 1;
        }
    }

    public int getFacesTotal() {
        return this.facesDadoBase + this.bonusFacesDado;
    }

    public String getDescricaoDano() {
        String base = String.format("%dd%d", quantidadeDados, getFacesTotal());
        return (bonusDanoFixo > 0) ? base + "+" + bonusDanoFixo : base;
    }

    public int getBonusDanoFixo() { return bonusDanoFixo; }
    public void setBonusDanoFixo(int bonusDanoFixo) { this.bonusDanoFixo = Math.max(0, bonusDanoFixo); }

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

    public SubtipoArmaBranca getSubtipo() { return subtipo; }
    public void setSubtipo(SubtipoArmaBranca subtipo) { this.subtipo = subtipo; }

    public Empunhadura getEmpunhadura() { return empunhadura; }
    public void setEmpunhadura(Empunhadura empunhadura) { this.empunhadura = empunhadura; }

    public int getQuantidadeDados() { return quantidadeDados; }
    public void setQuantidadeDados(int quantidadeDados) { this.quantidadeDados = Math.max(1, quantidadeDados); }

    public int getFacesDadoBase() { return facesDadoBase; }
    public void setFacesDadoBase(int facesDadoBase) { this.facesDadoBase = Math.max(2, facesDadoBase); }

    public int getBonusFacesDado() { return bonusFacesDado; }
    public void setBonusFacesDado(int bonusFacesDado) { this.bonusFacesDado = Math.max(0, bonusFacesDado); }

    public int getSlotsDeEfeitoMaximos() { return slotsDeEfeitoMaximos; }
    public void setSlotsDeEfeitoMaximos(int slotsDeEfeitoMaximos) { this.slotsDeEfeitoMaximos = Math.max(0, slotsDeEfeitoMaximos); }

    public List<EfeitoItem> getEfeitosEngastados() { return Collections.unmodifiableList(efeitosEngastados); }

    @Override
    public String toString() {
        String slots = String.format(" [Engastes: %d/%d]", efeitosEngastados.size(), slotsDeEfeitoMaximos);
        return String.format("%s (%s | Dano: %s)%s", subtipo, empunhadura, getDescricaoDano(), slots);
    }
}
