package economia;

import java.util.Objects;

/**
 * Representa um item, arma, armadura, consumível ou recurso no universo de Thaluria.
 */
public class Item {

    public enum TipoItem {
        ARMA("Arma"),
        ARMADURA("Armadura"),
        ESCUDO("Escudo"),
        CONSUMIVEL("Consumível"),
        ACESSORIO("Acessório"),
        MATERIAL("Material de Forja"),
        MAGICO("Item Mágico / Grimório"),
        OUTRO("Geral");

        private final String nomeFormatado;
        TipoItem(String nomeFormatado) { this.nomeFormatado = nomeFormatado; }
        public String getNomeFormatado() { return nomeFormatado; }
        @Override public String toString() { return nomeFormatado; }
    }

    public enum RaridadeItem {
        COMUM("Comum"),
        INCOMUM("Incomum"),
        RARO("Raro"),
        EPICO("Épico"),
        LENDARIO("Lendário");

        private final String nomeFormatado;
        RaridadeItem(String nomeFormatado) { this.nomeFormatado = nomeFormatado; }
        public String getNomeFormatado() { return nomeFormatado; }
        @Override public String toString() { return nomeFormatado; }
    }

    private int id;
    private String nome;
    private String descricao;
    private double preco; // Em moedas de ouro (PO)
    private double peso;  // Em kg
    private TipoItem tipo;
    private RaridadeItem raridade;
    private int quantidade;

    public Item() {
        this(0, "Item Desconhecido", "Sem descrição", 0.0, 0.1, TipoItem.OUTRO, RaridadeItem.COMUM, 1);
    }

    public Item(String nome, double preco) {
        this(0, nome, "Sem descrição", preco, 0.5, TipoItem.OUTRO, RaridadeItem.COMUM, 1);
    }

    public Item(int id, String nome, double preco) {
        this(id, nome, "Sem descrição", preco, 0.5, TipoItem.OUTRO, RaridadeItem.COMUM, 1);
    }

    public Item(int id, String nome, String descricao, double preco, double peso, TipoItem tipo, RaridadeItem raridade, int quantidade) {
        this.id = id;
        this.nome = (nome != null && !nome.trim().isEmpty()) ? nome : "Item Sem Nome";
        this.descricao = (descricao != null) ? descricao : "";
        this.preco = Math.max(0.0, preco);
        this.peso = Math.max(0.0, peso);
        this.tipo = (tipo != null) ? tipo : TipoItem.OUTRO;
        this.raridade = (raridade != null) ? raridade : RaridadeItem.COMUM;
        this.quantidade = Math.max(1, quantidade);
    }

    public void adicionarQuantidade(int qtd) {
        if (qtd > 0) {
            this.quantidade += qtd;
        }
    }

    public boolean removerQuantidade(int qtd) {
        if (qtd > 0 && this.quantidade >= qtd) {
            this.quantidade -= qtd;
            return true;
        }
        return false;
    }

    public double getPesoTotal() {
        return this.peso * this.quantidade;
    }

    public double getValorTotal() {
        return this.preco * this.quantidade;
    }

    /**
     * Preço unitário convertido para latão (menor unidade do sistema monetário).
     */
    public long getPrecoEmLatao() {
        return Carteira.ouroParaLatao(this.preco);
    }

    /**
     * Valor total do estoque deste item (preço × quantidade), em latão.
     */
    public long getValorTotalEmLatao() {
        return getPrecoEmLatao() * this.quantidade;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public double getPreco() {
        return preco;
    }

    public void setPreco(double preco) {
        this.preco = Math.max(0.0, preco);
    }

    public double getPeso() {
        return peso;
    }

    public void setPeso(double peso) {
        this.peso = Math.max(0.0, peso);
    }

    public TipoItem getTipo() {
        return tipo;
    }

    public void setTipo(TipoItem tipo) {
        this.tipo = tipo;
    }

    public RaridadeItem getRaridade() {
        return raridade;
    }

    public void setRaridade(RaridadeItem raridade) {
        this.raridade = raridade;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(int quantidade) {
        this.quantidade = Math.max(0, quantidade);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Item item = (Item) o;
        return id == item.id && Objects.equals(nome, item.nome);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, nome);
    }

    @Override
    public String toString() {
        return String.format("%s [x%d] (%s, %.1f kg) - %s (%s)",
            nome, quantidade, Carteira.formatarLatao(getPrecoEmLatao()), peso, tipo, raridade);
    }
}

/**
 * Subclasse para retrocompatibilidade com código que utilizava o identificador 'item' em minúsculo.
 */
class item extends Item {
    public item() { super(); }
    public item(String nome, double preco) { super(nome, preco); }
    public item(int id, String nome, double preco) { super(id, nome, preco); }
}
