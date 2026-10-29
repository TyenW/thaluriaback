package economia;

import java.util.Map;

/**
 * O mercado de uma loja como uma mesa o enxerga: ao vivo (mesa no dia do mundo) ou pelo
 * snapshot do dia da mesa (mesa atrasada). A mesa atrasada lê o passado, mas age no presente:
 * o que ela compra sai do estoque de agora, então o disponível é o menor dos dois.
 */
public final class VisaoMercado {

    /** De onde vêm preço e estoque. */
    public enum Fonte {
        AO_VIVO,
        SNAPSHOT,
        /** Mesa atrasada sem snapshot do dia dela (ex.: mesa arquivada reativada): lê o presente. */
        PRESENTE_SEM_SNAPSHOT
    }

    private final Lojas loja;
    private final Map<Integer, SnapshotMercado.Linha> snapshot;
    private final Fonte fonte;

    /** @param snapshot linhas do dia da mesa para esta loja; null quando a mesa lê ao vivo */
    public VisaoMercado(Lojas loja, Map<Integer, SnapshotMercado.Linha> snapshot, Fonte fonte) {
        this.loja = loja;
        this.fonte = fonte;
        this.snapshot = fonte == Fonte.SNAPSHOT ? snapshot : null;
    }

    public static VisaoMercado aoVivo(Lojas loja) {
        return new VisaoMercado(loja, null, Fonte.AO_VIVO);
    }

    public Fonte getFonte() { return fonte; }
    public boolean lePassado() { return snapshot != null; }

    /** Item que a loja tinha no dia lido (no snapshot, itens que só chegaram depois não aparecem). */
    public boolean conhecia(int itemId) {
        return snapshot == null || snapshot.containsKey(itemId);
    }

    public int quantidade(Item item) {
        if (snapshot == null) return loja.getQuantidadeEstoque(item.getId());
        SnapshotMercado.Linha l = snapshot.get(item.getId());
        return l != null ? l.quantidade() : 0;
    }

    public long precoVenda(Item item) {
        SnapshotMercado.Linha l = snapshot != null ? snapshot.get(item.getId()) : null;
        return l != null ? l.precoVendaLatao() : loja.calcularPrecoDinamicoVendaEmLatao(item);
    }

    public long precoCompra(Item item) {
        SnapshotMercado.Linha l = snapshot != null ? snapshot.get(item.getId()) : null;
        return l != null ? l.precoCompraLatao() : loja.calcularPrecoCompraNpcEmLatao(item);
    }

    public int demanda(Item item) {
        SnapshotMercado.Linha l = snapshot != null ? snapshot.get(item.getId()) : null;
        return l != null ? l.demanda() : loja.getDemandaPorItem().getOrDefault(item.getId(), 0);
    }

    /** Quanto a mesa pode comprar: o que a foto mostra, limitado ao que ainda existe hoje. */
    public int disponivelParaCompra(Item item) {
        return Math.min(quantidade(item), loja.getQuantidadeEstoque(item.getId()));
    }
}
