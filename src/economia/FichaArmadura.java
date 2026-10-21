package economia;

import java.util.*;

/**
 * Ficha técnica especializada para armaduras corporais com suporte a multi-slot (TipoItem.ARMADURA).
 */
public class FichaArmadura {

    public enum SlotArmadura {
        CABECA("Cabeça / Capacete / Capuz"),
        ROSTO("Rosto / Máscara / Viseira"),
        PESCOCO("Pescoço / Gorjal"),
        OMBROS("Ombros / Ombreiras"),
        PEITO("Peito / Peitoral / Cota"),
        COSTAS("Costas / Manto Reforçado"),
        BRACOS("Braços / Braçadeiras"),
        COTOVELOS("Cotovelos / Cotoveleiras"),
        MAOS("Mãos / Luvas / Manoplas"),
        CINTURA("Cintura / Cinturão Blindado"),
        PERNAS("Pernas / Calças de Armadura"),
        JOELHOS("Joelhos / Joelheiras"),
        PES("Pés / Botas / Grevas");

        private final String nomeFormatado;

        SlotArmadura(String nomeFormatado) {
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

    private final EnumSet<SlotArmadura> slotsOcupados = EnumSet.noneOf(SlotArmadura.class);
    private int reducaoDeDano; // RD direta subtraída do dano físico
    private int durabilidadeMaxima;
    private int durabilidadeAtual;
    private final Map<String, Integer> bonusStatus = new LinkedHashMap<>();
    private final List<EfeitoItem> efeitosArmadura = new ArrayList<>();
    private int slotsDeEfeitoMaximos;

    public FichaArmadura(SlotArmadura slotPrincipal, int reducaoDeDano, int durabilidadeMaxima) {
        if (slotPrincipal != null) {
            this.slotsOcupados.add(slotPrincipal);
        } else {
            this.slotsOcupados.add(SlotArmadura.PEITO);
        }
        this.reducaoDeDano = Math.max(0, reducaoDeDano);
        this.durabilidadeMaxima = Math.max(1, durabilidadeMaxima);
        this.durabilidadeAtual = this.durabilidadeMaxima;
        this.slotsDeEfeitoMaximos = 1;
    }

    public FichaArmadura(Collection<SlotArmadura> slots, int reducaoDeDano, int durabilidadeMaxima, int slotsDeEfeitoMaximos) {
        if (slots != null && !slots.isEmpty()) {
            this.slotsOcupados.addAll(slots);
        } else {
            this.slotsOcupados.add(SlotArmadura.PEITO);
        }
        this.reducaoDeDano = Math.max(0, reducaoDeDano);
        this.durabilidadeMaxima = Math.max(1, durabilidadeMaxima);
        this.durabilidadeAtual = this.durabilidadeMaxima;
        this.slotsDeEfeitoMaximos = Math.max(0, slotsDeEfeitoMaximos);
    }

    public void aplicarModificadorQualidade(QualidadeCraft qualidade) {
        if (qualidade == null) return;
        this.reducaoDeDano = Math.max(1, (int) Math.round(this.reducaoDeDano * qualidade.getMultiplicadorAtributosEDano()));
        this.durabilidadeMaxima = Math.max(1, (int) Math.round(this.durabilidadeMaxima * qualidade.getMultiplicadorDurabilidade()));
        this.durabilidadeAtual = this.durabilidadeMaxima;
        this.slotsDeEfeitoMaximos = Math.max(this.slotsDeEfeitoMaximos, qualidade.getSlotsEfeitoMaximos());
    }

    public void adicionarSlotOcupado(SlotArmadura slot) {
        if (slot != null) {
            this.slotsOcupados.add(slot);
        }
    }

    public void sofrerDanoDurabilidade(int dano) {
        if (dano > 0) {
            this.durabilidadeAtual = Math.max(0, this.durabilidadeAtual - dano);
        }
    }

    public void reparar(int qtd) {
        if (qtd > 0) {
            this.durabilidadeAtual = Math.min(this.durabilidadeMaxima, this.durabilidadeAtual + qtd);
        }
    }

    public boolean estaQuebrada() {
        return this.durabilidadeAtual <= 0;
    }

    public int getReducaoDeDanoEfetiva() {
        if (estaQuebrada()) {
            return 0; // Armadura quebrada perde a proteção de RD
        }
        return reducaoDeDano;
    }

    public void adicionarBonusStatus(String status, int valor) {
        if (status != null) {
            bonusStatus.put(status.toUpperCase().trim(), valor);
        }
    }

    public boolean adicionarEfeito(EfeitoItem efeito) {
        if (efeito != null && efeitosArmadura.size() < slotsDeEfeitoMaximos) {
            efeitosArmadura.add(efeito);
            return true;
        }
        return false;
    }

    public Set<SlotArmadura> getSlotsOcupados() {
        return Collections.unmodifiableSet(slotsOcupados);
    }

    public int getReducaoDeDano() {
        return reducaoDeDano;
    }

    public void setReducaoDeDano(int reducaoDeDano) {
        this.reducaoDeDano = Math.max(0, reducaoDeDano);
    }

    public int getDurabilidadeMaxima() {
        return durabilidadeMaxima;
    }

    public void setDurabilidadeMaxima(int durabilidadeMaxima) {
        this.durabilidadeMaxima = Math.max(1, durabilidadeMaxima);
    }

    public int getDurabilidadeAtual() {
        return durabilidadeAtual;
    }

    public void setDurabilidadeAtual(int durabilidadeAtual) {
        this.durabilidadeAtual = Math.max(0, Math.min(this.durabilidadeMaxima, durabilidadeAtual));
    }

    public Map<String, Integer> getBonusStatus() {
        return Collections.unmodifiableMap(bonusStatus);
    }

    public List<EfeitoItem> getEfeitosArmadura() {
        return Collections.unmodifiableList(efeitosArmadura);
    }

    public int getSlotsDeEfeitoMaximos() {
        return slotsDeEfeitoMaximos;
    }

    public void setSlotsDeEfeitoMaximos(int slotsDeEfeitoMaximos) {
        this.slotsDeEfeitoMaximos = Math.max(0, slotsDeEfeitoMaximos);
    }

    @Override
    public String toString() {
        return String.format("Armadura %s (RD: %d | Durabilidade: %d/%d)",
                slotsOcupados, getReducaoDeDanoEfetiva(), durabilidadeAtual, durabilidadeMaxima);
    }
}
