package personagem;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fórmula imutável do tipo {@code base + Σ ⌊atributo × num / den⌋}.
 * Cobre todas as equações de recursos/defesas de Status.md e as sobrescritas raciais,
 * com divisões sempre arredondadas para baixo (Math.floorDiv).
 */
public final class FormulaLinear {
    private final int base;
    private final Map<Atributo, int[]> termos; // {numerador, denominador}

    private FormulaLinear(int base, Map<Atributo, int[]> termos) {
        this.base = base;
        this.termos = termos;
    }

    public static Builder base(int base) { return new Builder(base); }

    public int avaliar(Map<Atributo, Integer> valores) {
        int total = base;
        for (Map.Entry<Atributo, int[]> e : termos.entrySet()) {
            int v = valores.getOrDefault(e.getKey(), 0);
            total += Math.floorDiv(v * e.getValue()[0], e.getValue()[1]);
        }
        return total;
    }

    public int getBase() { return base; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FormulaLinear f)) return false;
        if (base != f.base || termos.size() != f.termos.size()) return false;
        for (Map.Entry<Atributo, int[]> e : termos.entrySet()) {
            int[] outro = f.termos.get(e.getKey());
            if (outro == null) return false;
            // compara frações por produto cruzado
            if (e.getValue()[0] * outro[1] != outro[0] * e.getValue()[1]) return false;
        }
        return true;
    }

    @Override
    public int hashCode() { return Objects.hash(base, termos.keySet()); }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append(base);
        for (Map.Entry<Atributo, int[]> e : termos.entrySet()) {
            int[] f = e.getValue();
            sb.append(" + ").append(e.getKey());
            if (f[1] != 1) sb.append("/").append(f[1]);
            if (f[0] != 1) sb.append("×").append(f[0]);
        }
        return sb.toString();
    }

    public static final class Builder {
        private final int base;
        private final Map<Atributo, int[]> termos = new EnumMap<>(Atributo.class);

        private Builder(int base) { this.base = base; }

        /** Soma {@code a × mult}. */
        public Builder mais(Atributo a, int mult) { return maisFracao(a, mult, 1); }

        /** Soma {@code ⌊a / div⌋}. */
        public Builder maisDiv(Atributo a, int div) { return maisFracao(a, 1, div); }

        public Builder maisFracao(Atributo a, int num, int den) {
            if (den <= 0) throw new IllegalArgumentException("Denominador deve ser positivo");
            termos.put(a, new int[]{num, den});
            return this;
        }

        public FormulaLinear construir() { return new FormulaLinear(base, new EnumMap<>(termos)); }
    }
}
