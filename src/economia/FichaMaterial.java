package economia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Ficha técnica especializada para materiais de forja, manufatura e espólios de monstros (TipoItem.MATERIAL).
 */
public class FichaMaterial {

    public enum SubtipoMaterial {
        METAL_LINGOTE("Metal Refinado / Lingote"),
        MINERIO_BRUTO("Minério Bruto / Rochas"),
        GEMA_CRISTAL("Gema Preciosa / Cristal"),
        MADEIRA_FIBRA("Madeira / Fibras Naturais"),
        COURO_PELE("Couro / Pele / Escama"),
        PLANTA_ERVA("Planta / Erva Medicinal / Fungo"),
        COMIDA_INSUMO("Comida / Insumo Culinário"),
        ESPOLIO_MONSTRO("Espólio de Monstro / Fera");

        private final String nomeFormatado;

        SubtipoMaterial(String nomeFormatado) {
            this.nomeFormatado = nomeFormatado;
        }

        public String getNomeFormatado() {
            return nomeFormatado;
        }

        @Override
        public String toString() {
            return nomeFormatado;
        }
    }

    private SubtipoMaterial subtipo;
    private int qualidade; // 1 (comum/bruto) a 5 (obra-prima/lendário)
    private int bonusFacesDadoArma; // Ex: +2 faces se usado em armas (1d10 -> 1d12)
    private int bonusResistenciaArmadura; // Ex: +2 RD se usado em armaduras
    private final List<EfeitoItem> efeitosPossiveis = new ArrayList<>();
    private EfeitoItem efeitoAtribuido;

    // Diferenciação entre insumo genérico (ex: "Couro Q2") e específico (ex: "Escamas de Dragão", "Camarão da Costa")
    private boolean especificoDeOrigem = false;
    private String sufixoCrafting = "";

    public FichaMaterial(SubtipoMaterial subtipo, int qualidade) {
        this(subtipo, qualidade, 0, 0, null);
    }

    public FichaMaterial(SubtipoMaterial subtipo, int qualidade, boolean especificoDeOrigem, String sufixoCrafting) {
        this(subtipo, qualidade, 0, 0, null);
        this.especificoDeOrigem = especificoDeOrigem;
        this.sufixoCrafting = (sufixoCrafting != null) ? sufixoCrafting.trim() : "";
    }

    public FichaMaterial(SubtipoMaterial subtipo, int qualidade, int bonusFacesDadoArma,
                         int bonusResistenciaArmadura, EfeitoItem efeitoAtribuido) {
        this(subtipo, qualidade, bonusFacesDadoArma, bonusResistenciaArmadura, efeitoAtribuido, false, "");
    }

    public FichaMaterial(SubtipoMaterial subtipo, int qualidade, int bonusFacesDadoArma,
                         int bonusResistenciaArmadura, EfeitoItem efeitoAtribuido,
                         boolean especificoDeOrigem, String sufixoCrafting) {
        this.subtipo = (subtipo != null) ? subtipo : SubtipoMaterial.MINERIO_BRUTO;
        this.qualidade = Math.max(1, Math.min(5, qualidade));
        this.bonusFacesDadoArma = bonusFacesDadoArma;
        this.bonusResistenciaArmadura = bonusResistenciaArmadura;
        this.efeitoAtribuido = efeitoAtribuido;
        this.especificoDeOrigem = especificoDeOrigem;
        this.sufixoCrafting = (sufixoCrafting != null) ? sufixoCrafting.trim() : "";
    }

    public void adicionarEfeitoPossivel(EfeitoItem efeito) {
        if (efeito != null && !efeitosPossiveis.contains(efeito)) {
            efeitosPossiveis.add(efeito);
        }
    }

    /**
     * Sorteia um dos efeitos do pool de possibilidades no momento do abate ou geração de lote.
     */
    public EfeitoItem sortearEfeitoNoDrop(Random random) {
        if (efeitosPossiveis.isEmpty()) {
            return null;
        }
        Random rnd = (random != null) ? random : new Random();
        this.efeitoAtribuido = efeitosPossiveis.get(rnd.nextInt(efeitosPossiveis.size()));
        return this.efeitoAtribuido;
    }

    public SubtipoMaterial getSubtipo() {
        return subtipo;
    }

    public void setSubtipo(SubtipoMaterial subtipo) {
        this.subtipo = subtipo;
    }

    public int getQualidade() {
        return qualidade;
    }

    public void setQualidade(int qualidade) {
        this.qualidade = Math.max(1, Math.min(5, qualidade));
    }

    public int getBonusFacesDadoArma() {
        return bonusFacesDadoArma;
    }

    public void setBonusFacesDadoArma(int bonusFacesDadoArma) {
        this.bonusFacesDadoArma = bonusFacesDadoArma;
    }

    public int getBonusResistenciaArmadura() {
        return bonusResistenciaArmadura;
    }

    public void setBonusResistenciaArmadura(int bonusResistenciaArmadura) {
        this.bonusResistenciaArmadura = bonusResistenciaArmadura;
    }

    public List<EfeitoItem> getEfeitosPossiveis() {
        return Collections.unmodifiableList(efeitosPossiveis);
    }

    public EfeitoItem getEfeitoAtribuido() {
        return efeitoAtribuido;
    }

    public void setEfeitoAtribuido(EfeitoItem efeitoAtribuido) {
        this.efeitoAtribuido = efeitoAtribuido;
    }

    public boolean isEspecificoDeOrigem() {
        return especificoDeOrigem;
    }

    public void setEspecificoDeOrigem(boolean especificoDeOrigem) {
        this.especificoDeOrigem = especificoDeOrigem;
    }

    public String getSufixoCrafting() {
        return sufixoCrafting;
    }

    public void setSufixoCrafting(String sufixoCrafting) {
        this.sufixoCrafting = (sufixoCrafting != null) ? sufixoCrafting.trim() : "";
    }

    @Override
    public String toString() {
        String ef = (efeitoAtribuido != null) ? " | Efeito: " + efeitoAtribuido.getNome() : "";
        String bArma = (bonusFacesDadoArma > 0) ? String.format(" [Arma: +%d faces]", bonusFacesDadoArma) : "";
        String bArmad = (bonusResistenciaArmadura > 0) ? String.format(" [Armadura: +%d RD]", bonusResistenciaArmadura) : "";
        return String.format("%s (Qualidade Q%d)%s%s%s", subtipo, qualidade, ef, bArma, bArmad);
    }
}
