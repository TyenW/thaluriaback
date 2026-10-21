package economia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ficha técnica especializada para consumíveis (TipoItem.CONSUMIVEL): comidas, poções, bebidas e antídotos.
 */
public class FichaConsumivel {

    private final List<EfeitoItem> efeitosAplicados = new ArrayList<>();
    private final List<String> condicoesRemovidas = new ArrayList<>();
    private int restauracaoVida;
    private int restauracaoMana;
    private int restauracaoVigor;

    public FichaConsumivel() {
        this(0, 0, 0);
    }

    public FichaConsumivel(int restauracaoVida, int restauracaoMana, int restauracaoVigor) {
        this.restauracaoVida = Math.max(0, restauracaoVida);
        this.restauracaoMana = Math.max(0, restauracaoMana);
        this.restauracaoVigor = Math.max(0, restauracaoVigor);
    }

    public void aplicarModificadorQualidade(QualidadeCraft qualidade) {
        if (qualidade == null) return;
        this.restauracaoVida = (int) Math.round(this.restauracaoVida * qualidade.getMultiplicadorAtributosEDano());
        this.restauracaoMana = (int) Math.round(this.restauracaoMana * qualidade.getMultiplicadorAtributosEDano());
        this.restauracaoVigor = (int) Math.round(this.restauracaoVigor * qualidade.getMultiplicadorAtributosEDano());
    }

    public void adicionarEfeitoAplicado(EfeitoItem efeito) {
        if (efeito != null) {
            efeitosAplicados.add(efeito);
        }
    }

    public void adicionarCondicaoRemovida(String condicao) {
        if (condicao != null && !condicao.trim().isEmpty()) {
            condicoesRemovidas.add(condicao.trim());
        }
    }

    public int getRestauracaoVida() {
        return restauracaoVida;
    }

    public void setRestauracaoVida(int restauracaoVida) {
        this.restauracaoVida = Math.max(0, restauracaoVida);
    }

    public int getRestauracaoMana() {
        return restauracaoMana;
    }

    public void setRestauracaoMana(int restauracaoMana) {
        this.restauracaoMana = Math.max(0, restauracaoMana);
    }

    public int getRestauracaoVigor() {
        return restauracaoVigor;
    }

    public void setRestauracaoVigor(int restauracaoVigor) {
        this.restauracaoVigor = Math.max(0, restauracaoVigor);
    }

    public List<EfeitoItem> getEfeitosAplicados() {
        return Collections.unmodifiableList(efeitosAplicados);
    }

    public List<String> getCondicoesRemovidas() {
        return Collections.unmodifiableList(condicoesRemovidas);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Consumível [");
        if (restauracaoVida > 0) sb.append("+").append(restauracaoVida).append(" HP ");
        if (restauracaoMana > 0) sb.append("+").append(restauracaoMana).append(" Mana ");
        if (restauracaoVigor > 0) sb.append("+").append(restauracaoVigor).append(" Vigor ");
        if (!condicoesRemovidas.isEmpty()) sb.append("Cura: ").append(condicoesRemovidas).append(" ");
        if (!efeitosAplicados.isEmpty()) sb.append("Efeitos: ").append(efeitosAplicados.size());
        sb.append("]");
        return sb.toString();
    }
}
