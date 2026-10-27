package economia;

import java.util.List;

/**
 * Receita de manufatura cadastrada pelo Mestre (tabelas receitas e receita_insumos).
 * Diferente do MoldeReceita, que trabalha com subtipos de material, esta usa itens
 * concretos do catálogo: é o que as lojas do mundo produzem no dia a dia.
 *
 * Receitas de arma da forja são compiladas para cá: os insumos são todos os materiais,
 * e cada parte vira uma {@link Etapa} (subcraft) feita antes da montagem. Um componente
 * livre na prateleira pode substituir a etapa e poupa o tempo dela.
 *
 * @param tipoLoja especialidade que conhece a receita; null = qualquer loja
 * @param etapas subcrafts antes da montagem (vazio = receita comum, de uma etapa só)
 * @param somenteEncomenda a IA do mercador não a produz sozinha (componentes e encomendas personalizadas)
 */
public record ReceitaLoja(int id, String nome, int itemResultadoId, int quantidadeResultado,
                          double tempoHoras, Lojas.TipoEstabelecimento tipoLoja, List<Insumo> insumos,
                          List<Etapa> etapas, boolean somenteEncomenda) {

    /** Mão de obra cobrada por lote: 10% do valor de referência do que se produz. */
    public static final double FRACAO_MAO_DE_OBRA = 0.10;

    public record Insumo(int itemId, int quantidade) {}

    /** Um componente feito por subcraft: os materiais que ele consome e quanto tempo leva. */
    public record Etapa(int componenteItemId, String nomeParte, double tempoHoras, List<Insumo> insumos) {
        public Etapa {
            insumos = List.copyOf(insumos);
        }
    }

    public ReceitaLoja {
        insumos = List.copyOf(insumos);
        etapas = etapas == null ? List.of() : List.copyOf(etapas);
        quantidadeResultado = Math.max(1, quantidadeResultado);
    }

    public ReceitaLoja(int id, String nome, int itemResultadoId, int quantidadeResultado,
                       double tempoHoras, Lojas.TipoEstabelecimento tipoLoja, List<Insumo> insumos) {
        this(id, nome, itemResultadoId, quantidadeResultado, tempoHoras, tipoLoja, insumos, List.of(), false);
    }

    /** Dias de mercado até o lote ficar pronto (mínimo 1). */
    public int diasProducao() {
        return Math.max(1, (int) Math.ceil(tempoHoras / 24.0));
    }

    /** Horas da montagem final, depois de todas as etapas. */
    public double horasMontagem() {
        double etapasHoras = 0;
        for (Etapa e : etapas) etapasHoras += e.tempoHoras();
        return Math.max(0, tempoHoras - etapasHoras);
    }

    public boolean conhecidaPor(Lojas loja) {
        return tipoLoja == null || (loja != null && tipoLoja == loja.getTipoEstabelecimento());
    }

    public long custoMaoDeObraEmLatao(Item resultado, int lotes) {
        if (resultado == null) return 0L;
        return Math.round(resultado.getPrecoEmLatao() * quantidadeResultado * FRACAO_MAO_DE_OBRA) * Math.max(1, lotes);
    }
}
