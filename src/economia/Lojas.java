package economia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa uma loja ou entreposto comercial em um assentamento de Thaluria.
 */
public class Lojas {
    private String nome;
    private String mercador;
    private boolean lojaDeRede;
    private String nomeDaRede;
    private final List<Item> estoque = new ArrayList<>();
    private Carteira caixa;

    public Lojas() {
        this("Empório Geral", "Mercador Local", false, null);
    }

    public Lojas(String nome, String mercador) {
        this(nome, mercador, false, null);
    }

    public Lojas(String nome, String mercador, boolean lojaDeRede, String nomeDaRede) {
        this.nome = (nome != null) ? nome : "Loja Sem Nome";
        this.mercador = (mercador != null) ? mercador : "Mercador Itinerante";
        this.lojaDeRede = lojaDeRede;
        this.nomeDaRede = lojaDeRede ? nomeDaRede : null;
        this.caixa = new Carteira(50, 0, 0); // Fundo de caixa padrão de 50 PO
    }

    public void adicionarItem(Item item) {
        if (item == null) return;
        for (Item existente : estoque) {
            if (existente.equals(item)) {
                existente.adicionarQuantidade(item.getQuantidade());
                return;
            }
        }
        this.estoque.add(item);
    }

    public boolean removerItem(Item item) {
        return this.estoque.remove(item);
    }

    public Item buscarItemPorNome(String nomeItem) {
        if (nomeItem == null) return null;
        for (Item item : estoque) {
            if (item.getNome().equalsIgnoreCase(nomeItem)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Vende um item do estoque da loja para a carteira de um cliente.
     */
    public boolean venderItem(Item item, Carteira carteiraCliente) {
        if (item == null || carteiraCliente == null) return false;
        if (!estoque.contains(item)) return false;

        double preco = item.getPreco();
        if (carteiraCliente.pagar(preco)) {
            this.caixa.adicionarMoedas((int) preco, 0, 0);
            item.removerQuantidade(1);
            if (item.getQuantidade() <= 0) {
                estoque.remove(item);
            }
            return true;
        }
        return false;
    }

    /**
     * Compra um item de um aventureiro, adicionando-o ao estoque e pagando o cliente.
     */
    public boolean comprarItem(Item item, Carteira carteiraCliente, double precoCompra) {
        if (item == null || carteiraCliente == null) return false;
        if (this.caixa.pagar(precoCompra)) {
            carteiraCliente.adicionarOuro((int) precoCompra);
            adicionarItem(item);
            return true;
        }
        return false;
    }

    public List<Item> getEstoque() {
        return Collections.unmodifiableList(estoque);
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getMercador() {
        return mercador;
    }

    public void setMercador(String mercador) {
        this.mercador = mercador;
    }

    public boolean isLojaDeRede() {
        return lojaDeRede;
    }

    public void setLojaDeRede(boolean lojaDeRede) {
        this.lojaDeRede = lojaDeRede;
    }

    public String getNomeDaRede() {
        return nomeDaRede;
    }

    public void setNomeDaRede(String nomeDaRede) {
        this.nomeDaRede = nomeDaRede;
    }

    public Carteira getCaixa() {
        return caixa;
    }

    public void setCaixa(Carteira caixa) {
        if (caixa != null) this.caixa = caixa;
    }

    @Override
    public String toString() {
        String infoRede = lojaDeRede ? " [Rede: " + nomeDaRede + "]" : "";
        return String.format("%s (Mercador: %s)%s - Itens em estoque: %d | Caixa: %s",
            nome, mercador, infoRede, estoque.size(), caixa.toString());
    }
}
