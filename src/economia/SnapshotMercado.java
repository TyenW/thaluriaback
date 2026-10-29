package economia;

import geografia.Cidade;

import java.util.*;

/**
 * Foto diária do mercado (gravada em mercado_snapshot no fim de cada ciclo diário).
 * Uma mesa atrasada lê preço, estoque e procura daqui, em vez do motor ao vivo.
 */
public final class SnapshotMercado {

    private SnapshotMercado() {}

    public record Linha(int dia, int lojaId, int itemId, int quantidade, long precoVendaLatao, long precoCompraLatao, int demanda) {}

    /** Uma linha por mercadoria conhecida ou em estoque de cada loja. */
    public static List<Linha> fotografar(MotorEconomico motor, int dia) {
        List<Linha> linhas = new ArrayList<>();
        for (Cidade c : motor.getCidades()) {
            for (Lojas l : c.getLojas()) {
                Map<Integer, Item> mercadorias = new LinkedHashMap<>(l.getCatalogoConhecido());
                for (Item i : l.getEstoque()) mercadorias.putIfAbsent(i.getId(), i);
                for (Item i : mercadorias.values()) {
                    linhas.add(new Linha(dia, l.getId(), i.getId(), l.getQuantidadeEstoque(i.getId()),
                            l.calcularPrecoDinamicoVendaEmLatao(i), l.calcularPrecoCompraNpcEmLatao(i),
                            l.getDemandaPorItem().getOrDefault(i.getId(), 0)));
                }
            }
        }
        return linhas;
    }
}
