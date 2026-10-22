package economia;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Define as regras de filtragem e aceitação de materiais concretos nos slots de um MoldeReceita.
 * Suporta três modos de exigência:
 * 1. ABERTO_SUBSISTEMA: aceita qualquer item concreto do SubtipoMaterial com qualidade >= mínima.
 * 2. SUBCONJUNTO_RESTRITO: aceita apenas os IDs de itens autorizados (ex: varia apenas entre X ou Y; rejeita Z).
 * 3. ITEM_EXATO: aceita exclusivamente um único item com ID exato (ex: só X).
 */
public class FiltroInsumoReceita {

    public enum ModoFiltro {
        ABERTO_SUBSISTEMA,    // Aceita X, Y ou Z (qualquer item do SubtipoMaterial com Qualidade >= min)
        SUBCONJUNTO_RESTRITO, // Aceita APENAS os IDs listados (ex: varia só entre X ou Y; rejeita Z)
        ITEM_EXATO            // Aceita EXCLUSIVAMENTE um único ID específico (ex: só X)
    }

    private final ModoFiltro modo;
    private final FichaMaterial.SubtipoMaterial subtipoExigido;
    private final int qualidadeMinima;
    private final int quantidadeExigida;
    private final Set<Integer> idsPermitidos; // Vazio no modo ABERTO; [X, Y] no SUBCONJUNTO; [X] no EXATO
    private final boolean defineNomePrincipal;
    private final boolean slotCatalisadorOpcional;

    // 1. Fábrica para Modo Aberto (Qualquer Couro Q1: aceita X, Y ou Z)
    public static FiltroInsumoReceita qualquerDoSubsistema(
            FichaMaterial.SubtipoMaterial subtipo, int qualidadeMinima, int quantidade,
            boolean defineNomePrincipal, boolean opcional) {
        return new FiltroInsumoReceita(ModoFiltro.ABERTO_SUBSISTEMA, subtipo,
                qualidadeMinima, quantidade, Collections.emptySet(), defineNomePrincipal, opcional);
    }

    // 2. Fábrica para Modo Subconjunto (Varia só entre X ou Y; rejeita Z)
    public static FiltroInsumoReceita apenasEntreOpcoes(
            FichaMaterial.SubtipoMaterial subtipo, int qualidadeMinima, int quantidade,
            Set<Integer> idsPermitidosXY, boolean defineNomePrincipal, boolean opcional) {
        if (idsPermitidosXY == null || idsPermitidosXY.size() < 2) {
            throw new IllegalArgumentException("Subconjunto restrito deve conter pelo menos 2 IDs permitidos (ex: X ou Y).");
        }
        return new FiltroInsumoReceita(ModoFiltro.SUBCONJUNTO_RESTRITO, subtipo,
                qualidadeMinima, quantidade, idsPermitidosXY, defineNomePrincipal, opcional);
    }

    // 3. Fábrica para Modo Exato (Exige especificamente o item X)
    public static FiltroInsumoReceita itemEspecificoExato(
            int idItemExato, FichaMaterial.SubtipoMaterial subtipo, int qualidadeMinima,
            int quantidade, boolean defineNomePrincipal, boolean opcional) {
        return new FiltroInsumoReceita(ModoFiltro.ITEM_EXATO, subtipo,
                qualidadeMinima, quantidade, Set.of(idItemExato), defineNomePrincipal, opcional);
    }

    private FiltroInsumoReceita(ModoFiltro modo, FichaMaterial.SubtipoMaterial subtipoExigido,
                                int qualidadeMinima, int quantidadeExigida, Set<Integer> idsPermitidos,
                                boolean defineNomePrincipal, boolean slotCatalisadorOpcional) {
        this.modo = modo;
        this.subtipoExigido = subtipoExigido;
        this.qualidadeMinima = Math.max(1, Math.min(5, qualidadeMinima));
        this.quantidadeExigida = Math.max(1, quantidadeExigida);
        this.idsPermitidos = new HashSet<>(idsPermitidos);
        this.defineNomePrincipal = defineNomePrincipal;
        this.slotCatalisadorOpcional = slotCatalisadorOpcional;
    }

    /**
     * Valida se um Item concreto do estoque (X, Y ou Z) atende a este slot da receita!
     */
    public boolean aceitaMaterial(Item candidato) {
        if (candidato == null || candidato.getFichaMaterial() == null) return false;
        FichaMaterial fm = candidato.getFichaMaterial();

        // Verifica subcategoria e qualidade mínima (1 a 5)
        if (fm.getSubtipo() != this.subtipoExigido) return false;
        if (fm.getQualidade() < this.qualidadeMinima) return false;

        // Verifica restrição de identidade (X, Y ou Z)
        return switch (this.modo) {
            case ABERTO_SUBSISTEMA -> true; // Aceita qualquer item concreto desse subtipo e qualidade
            case SUBCONJUNTO_RESTRITO, ITEM_EXATO -> idsPermitidos.contains(candidato.getId());
        };
    }

    public ModoFiltro getModo() { return modo; }
    public FichaMaterial.SubtipoMaterial getSubtipoExigido() { return subtipoExigido; }
    public int getQualidadeMinima() { return qualidadeMinima; }
    public int getQuantidadeExigida() { return quantidadeExigida; }
    public Set<Integer> getIdsPermitidos() { return Collections.unmodifiableSet(idsPermitidos); }
    public boolean isDefineNomePrincipal() { return defineNomePrincipal; }
    public boolean isSlotCatalisadorOpcional() { return slotCatalisadorOpcional; }

    @Override
    public String toString() {
        return switch (modo) {
            case ABERTO_SUBSISTEMA -> String.format("%dx %s (Q%d+ | Qualquer)", quantidadeExigida, subtipoExigido, qualidadeMinima);
            case SUBCONJUNTO_RESTRITO -> String.format("%dx %s (Q%d+ | Restrito a IDs %s)", quantidadeExigida, subtipoExigido, qualidadeMinima, idsPermitidos);
            case ITEM_EXATO -> String.format("%dx %s (Q%d+ | Exclusivo ID %s)", quantidadeExigida, subtipoExigido, qualidadeMinima, idsPermitidos);
        };
    }
}
