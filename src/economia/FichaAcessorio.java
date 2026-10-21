package economia;

import java.util.*;

/**
 * Ficha técnica especializada para joias, talismãs e acessórios táticos (TipoItem.ACESSORIO).
 * Possui 5 slots corporais exclusivos totalmente segregados das armaduras.
 */
public class FichaAcessorio {

    public enum SlotAcessorio {
        ORELHAS("Orelhas / Brincos / Pendentes"),
        OLHOS("Olhos / Óculos Arcanos / Vendas"),
        ANTEBRACOS("Antebraços / Braceletes / Pulseiras"),
        ANEIS("Anéis / Sinetes de Combate"),
        ACESSORIO_GERAL("Acessório Geral / Amuletos / Insígnias");

        private final String nomeFormatado;

        SlotAcessorio(String nomeFormatado) {
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

    private SlotAcessorio slotExclusivo;
    private final Map<String, Integer> bonusAtributos = new LinkedHashMap<>();
    private final List<PassivaItem> passivas = new ArrayList<>();
    private final List<String> ataquesHabilitados = new ArrayList<>();

    public FichaAcessorio(SlotAcessorio slotExclusivo) {
        this.slotExclusivo = (slotExclusivo != null) ? slotExclusivo : SlotAcessorio.ACESSORIO_GERAL;
    }

    public void adicionarBonusAtributo(String atributo, int valor) {
        if (atributo != null) {
            bonusAtributos.put(atributo.toUpperCase().trim(), valor);
        }
    }

    public void adicionarPassiva(PassivaItem passiva) {
        if (passiva != null) {
            passivas.add(passiva);
        }
    }

    public void adicionarAtaqueHabilitado(String ataque) {
        if (ataque != null && !ataque.trim().isEmpty()) {
            ataquesHabilitados.add(ataque.trim());
        }
    }

    public SlotAcessorio getSlotExclusivo() {
        return slotExclusivo;
    }

    public void setSlotExclusivo(SlotAcessorio slotExclusivo) {
        this.slotExclusivo = (slotExclusivo != null) ? slotExclusivo : SlotAcessorio.ACESSORIO_GERAL;
    }

    public Map<String, Integer> getBonusAtributos() {
        return Collections.unmodifiableMap(bonusAtributos);
    }

    public List<PassivaItem> getPassivas() {
        return Collections.unmodifiableList(passivas);
    }

    public List<String> getAtaquesHabilitados() {
        return Collections.unmodifiableList(ataquesHabilitados);
    }

    @Override
    public String toString() {
        return String.format("Acessório [%s] (Bônus: %s | Passivas: %d | Habilidades: %d)",
                slotExclusivo, bonusAtributos, passivas.size(), ataquesHabilitados.size());
    }
}
