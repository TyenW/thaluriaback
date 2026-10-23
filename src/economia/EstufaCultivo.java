package economia;

import geografia.Cidade;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gerencia o cultivo botânico e a colheita de plantas e ervas medicinais na Base do Jogador ou em Boticas.
 * Se equipada com ESTUFA_CLIMATIZADA, permite cultivar qualquer espécie vegetal
 * independente do clima e relevo externo da cidade sede.
 */
public class EstufaCultivo {

    public static class CanteiroCultivo {
        private final int idCanteiro;
        private Item planta;
        private int diasCrescimentoTotal;
        private int diasRestantes;
        private int rendimentoUnidades;
        private boolean prontoParaColheita;

        public CanteiroCultivo(int idCanteiro) {
            this.idCanteiro = idCanteiro;
            this.prontoParaColheita = false;
        }

        public boolean plantar(Item sementeOuMuda, int diasCrescimento, int rendimento,
                               Cidade cidadeAtual, boolean estufaClimatizada) {
            if (sementeOuMuda == null || ocupado()) return false;

            // Validação de Bioma se não for climatizada
            if (!estufaClimatizada && cidadeAtual != null && sementeOuMuda.getRegiaoOrigem() != null) {
                if (sementeOuMuda.getRegiaoOrigem() != cidadeAtual.getRegiao()) {
                    if (!cidadeAtual.isFronteira() || sementeOuMuda.getRegiaoOrigem() != cidadeAtual.getRegiaofronteira()) {
                        return false; // Clima hostil sem estufa climatizada!
                    }
                }
            }

            this.planta = Lojas.clonarItem(sementeOuMuda, 1);
            this.diasCrescimentoTotal = Math.max(1, diasCrescimento);
            this.diasRestantes = this.diasCrescimentoTotal;
            this.rendimentoUnidades = Math.max(1, rendimento);
            this.prontoParaColheita = false;
            return true;
        }

        public boolean avancarDia() {
            if (!ocupado() || prontoParaColheita) return false;
            diasRestantes--;
            if (diasRestantes <= 0) {
                prontoParaColheita = true;
                return true;
            }
            return false;
        }

        public Item colher() {
            if (!prontoParaColheita || planta == null) return null;
            Item colheita = Lojas.clonarItem(planta, rendimentoUnidades);
            this.planta = null;
            this.prontoParaColheita = false;
            return colheita;
        }

        public boolean ocupado() {
            return planta != null;
        }

        public int getIdCanteiro() { return idCanteiro; }
        public Item getPlanta() { return planta; }
        public int getDiasRestantes() { return diasRestantes; }
        public int getDiasCrescimentoTotal() { return diasCrescimentoTotal; }
        public int getRendimentoUnidades() { return rendimentoUnidades; }
        public boolean isProntoParaColheita() { return prontoParaColheita; }
    }

    private final List<CanteiroCultivo> canteiros = new ArrayList<>();
    private boolean climatizada = false;

    public EstufaCultivo(int totalCanteirosIniciais) {
        int total = Math.max(1, totalCanteirosIniciais);
        for (int i = 1; i <= total; i++) {
            canteiros.add(new CanteiroCultivo(i));
        }
    }

    public void adicionarCanteiro() {
        canteiros.add(new CanteiroCultivo(canteiros.size() + 1));
    }

    public boolean plantarEmCanteiroLivre(Item sementeOuMuda, int dias, int rendimento, Cidade cidadeAtual) {
        for (CanteiroCultivo c : canteiros) {
            if (!c.ocupado()) {
                return c.plantar(sementeOuMuda, dias, rendimento, cidadeAtual, this.climatizada);
            }
        }
        return false;
    }

    public void avancarDia() {
        for (CanteiroCultivo c : canteiros) {
            c.avancarDia();
        }
    }

    public List<Item> colherTudo() {
        List<Item> colhidos = new ArrayList<>();
        for (CanteiroCultivo c : canteiros) {
            if (c.isProntoParaColheita()) {
                Item item = c.colher();
                if (item != null) colhidos.add(item);
            }
        }
        return colhidos;
    }

    public List<CanteiroCultivo> getCanteiros() {
        return Collections.unmodifiableList(canteiros);
    }

    public boolean isClimatizada() {
        return climatizada;
    }

    public void setClimatizada(boolean climatizada) {
        this.climatizada = climatizada;
    }
}
