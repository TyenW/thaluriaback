package economia;

import java.util.List;

/**
 * Receita de manufatura cadastrada pelo Mestre (tabelas receitas e receita_insumos).
 * Diferente do MoldeReceita, que trabalha com subtipos de material, esta usa itens
 * concretos do catálogo: é o que as lojas do mundo produzem no dia a dia.
 *
 * @param tipoLoja especialidade que conhece a receita; null = qualquer loja
 */
public record ReceitaLoja(int id, String nome, int itemResultadoId, int quantidadeResultado,
                          double tempoHoras, Lojas.TipoEstabelecimento tipoLoja, List<Insumo> insumos) {

    /** Mão de obra cobrada por lote: 10% do valor de referência do que se produz. */
    public static final double FRACAO_MAO_DE_OBRA = 0.10;

    public record Insumo(int itemId, int quantidade) {}

    public ReceitaLoja {
        insumos = List.copyOf(insumos);
        quantidadeResultado = Math.max(1, quantidadeResultado);
    }

    /** Dias de mercado até o lote ficar pronto (mínimo 1). */
    public int diasProducao() {
        return Math.max(1, (int) Math.ceil(tempoHoras / 24.0));
    }

    public boolean conhecidaPor(Lojas loja) {
        return tipoLoja == null || (loja != null && tipoLoja == loja.getTipoEstabelecimento());
    }

    public long custoMaoDeObraEmLatao(Item resultado, int lotes) {
        if (resultado == null) return 0L;
        return Math.round(resultado.getPrecoEmLatao() * quantidadeResultado * FRACAO_MAO_DE_OBRA) * Math.max(1, lotes);
    }
}
