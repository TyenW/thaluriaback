package economia;

import geografia.Cidade;
import java.util.*;

/**
 * Representa uma loja ou entreposto comercial em um assentamento de Thaluria.
 * Integra o controle de estoque, ofertas e demandas dinâmicas calculadas em Latão,
 * compras de fornecedores NPCs, livro-caixa com histórico e comércio intermunicipal.
 */
public class Lojas {

    public enum TipoEstabelecimento {
        FORJA("Forja / Ferraria"),
        ALFAIATARIA("Alfaiataria / Tecelagem"),
        LOJA_GERAL("Loja Geral / Empório"),
        ALQUIMIA("Alquimia / Botica"),
        JOALHERIA("Joalheria"),
        TAVERNA("Taverna / Comidas e Bebidas");

        private final String nomeFormatado;

        TipoEstabelecimento(String nomeFormatado) {
            this.nomeFormatado = nomeFormatado;
        }

        public String getNomeFormatado() {
            return nomeFormatado;
        }

        @Override
        public String toString() {
            return nomeFormatado;
        }
    }

    private String nome;
    private String mercador;
    private boolean lojaDeRede;
    private String nomeDaRede;
    private final List<Item> estoque = new ArrayList<>();
    private Carteira caixa;

    // Vínculos geográficos e de especialização
    private Cidade cidadeSede;
    private TipoEstabelecimento tipoEstabelecimento;

    // Métricas de Mercado, Oferta e Demanda
    private final Map<Integer, Item> catalogoConhecido = new LinkedHashMap<>();
    private final Map<Integer, Integer> demandaPorItem = new LinkedHashMap<>();
    private final Map<Integer, Integer> entradasRecentes = new LinkedHashMap<>();
    private final Map<Integer, Integer> saidasRecentes = new LinkedHashMap<>();
    private final List<RegistroMovimentacao> historico = new ArrayList<>();

    public Lojas() {
        this("Empório Geral", "Mercador Local", false, null, TipoEstabelecimento.LOJA_GERAL);
    }

    public Lojas(String nome, String mercador) {
        this(nome, mercador, false, null, TipoEstabelecimento.LOJA_GERAL);
    }

    public Lojas(String nome, String mercador, boolean lojaDeRede, String nomeDaRede) {
        this(nome, mercador, lojaDeRede, nomeDaRede, TipoEstabelecimento.LOJA_GERAL);
    }

    public Lojas(String nome, String mercador, boolean lojaDeRede, String nomeDaRede, TipoEstabelecimento tipo) {
        this(nome, mercador, null, lojaDeRede, nomeDaRede, tipo);
    }

    public Lojas(String nome, String mercador, Cidade cidadeSede, boolean lojaDeRede, String nomeDaRede) {
        this(nome, mercador, cidadeSede, lojaDeRede, nomeDaRede, TipoEstabelecimento.LOJA_GERAL);
    }

    public Lojas(String nome, String mercador, Cidade cidadeSede, boolean lojaDeRede, String nomeDaRede, TipoEstabelecimento tipo) {
        this.nome = (nome != null && !nome.trim().isEmpty()) ? nome : "Loja Sem Nome";
        this.mercador = (mercador != null && !mercador.trim().isEmpty()) ? mercador : "Mercador Itinerante";
        this.lojaDeRede = lojaDeRede;
        this.nomeDaRede = lojaDeRede ? nomeDaRede : null;
        this.tipoEstabelecimento = (tipo != null) ? tipo : TipoEstabelecimento.LOJA_GERAL;
        this.caixa = new Carteira(50); // Fundo de caixa padrão de 50 PO (500.000 latão na v2)
        this.cidadeSede = cidadeSede;
        if (cidadeSede != null) {
            cidadeSede.adicionarLoja(this);
        }
    }

    /**
     * Adiciona um item ao estoque da loja, clonando-o para evitar efeitos colaterais
     * e registrando o item no catálogo de mercadorias conhecidas do estabelecimento.
     */
    public void adicionarItem(Item item) {
        if (item == null) return;
        catalogoConhecido.putIfAbsent(item.getId(), clonarItem(item, 1));
        for (Item existente : estoque) {
            if (existente.getId() == item.getId() || existente.equals(item)) {
                existente.adicionarQuantidade(item.getQuantidade());
                return;
            }
        }
        estoque.add(clonarItem(item, item.getQuantidade()));
    }

    public boolean removerItem(Item item) {
        return this.estoque.remove(item);
    }

    public boolean removerUnidades(int idItem, int qtd) {
        if (qtd <= 0) return false;
        Iterator<Item> it = estoque.iterator();
        while (it.hasNext()) {
            Item atual = it.next();
            if (atual.getId() == idItem) {
                if (atual.getQuantidade() < qtd) return false;
                atual.removerQuantidade(qtd);
                if (atual.getQuantidade() <= 0) {
                    it.remove();
                }
                return true;
            }
        }
        return false;
    }

    public int getQuantidadeEstoque(int idItem) {
        for (Item item : estoque) {
            if (item.getId() == idItem) {
                return item.getQuantidade();
            }
        }
        return 0;
    }

    public Item buscarItemPorId(int idItem) {
        for (Item item : estoque) {
            if (item.getId() == idItem) {
                return item;
            }
        }
        return null;
    }

    public Item buscarItemPorNome(String nomeItem) {
        if (nomeItem == null) return null;
        for (Item item : estoque) {
            if (item.getNome().equalsIgnoreCase(nomeItem.trim())) {
                return item;
            }
        }
        return null;
    }

    /**
     * REQUISITO 6: Preço dinâmico de venda calculado em Latão (long).
     * Cruza estoque atual, entradas recentes, saídas recentes e demanda projetada.
     * Variação limitada de 40% (excesso severo) a 250% (alta escassez) do preço base.
     */
    public long calcularPrecoDinamicoVendaEmLatao(Item itemBase) {
        if (itemBase == null) return 1L;
        int id = itemBase.getId();
        int qtdEstoque = getQuantidadeEstoque(id);
        int demanda = demandaPorItem.getOrDefault(id, 5);
        int entradas = entradasRecentes.getOrDefault(id, 0);
        int saidas = saidasRecentes.getOrDefault(id, 0);

        double pressaoDemanda = demanda + (saidas * 0.5) + 1.0;
        double pressaoOferta = qtdEstoque + (entradas * 0.3) + 1.0;

        double multiplicador = Math.sqrt(pressaoDemanda / pressaoOferta);
        multiplicador = Math.max(0.40, Math.min(2.50, multiplicador));

        return Math.max(1L, Math.round(itemBase.getPrecoEmLatao() * multiplicador));
    }

    /**
     * Preço de compra que a loja paga a fornecedores NPCs locais (65% do preço dinâmico de venda).
     */
    public long calcularPrecoCompraNpcEmLatao(Item itemBase) {
        return Math.max(1L, Math.round(calcularPrecoDinamicoVendaEmLatao(itemBase) * 0.65));
    }

    /**
     * REQUISITO 1: Compra automática de mercadorias de fornecedores NPCs usando latão v2.
     */
    public boolean comprarDeNpc(Item item, int quantidade, int dia, int hora) {
        if (item == null || quantidade <= 0) return false;
        catalogoConhecido.putIfAbsent(item.getId(), clonarItem(item, 1));

        long precoUnitarioLatao = calcularPrecoCompraNpcEmLatao(item);
        long custoTotalLatao = precoUnitarioLatao * quantidade;

        if (!caixa.pagarLatao(custoTotalLatao)) {
            return false;
        }

        adicionarItem(clonarItem(item, quantidade));
        entradasRecentes.merge(item.getId(), quantidade, Integer::sum);

        registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.ENTRADA_NPC,
                item.getNome(), quantidade, custoTotalLatao, "Compra de fornecedor NPC local");
        return true;
    }

    /**
     * Venda de balcão (compatibilidade com assinatura padrão).
     */
    public boolean venderItem(Item item, Carteira carteiraCliente) {
        return venderItem(item, carteiraCliente, 1, 12);
    }

    /**
     * Venda de balcão com timestamp do mundo.
     */
    public boolean venderItem(Item item, Carteira carteiraCliente, int dia, int hora) {
        if (item == null || carteiraCliente == null) return false;

        Item noEstoque = (item.getId() != 0) ? buscarItemPorId(item.getId()) : null;
        if (noEstoque == null) {
            noEstoque = buscarItemPorNome(item.getNome());
        }
        if (noEstoque == null && estoque.contains(item)) {
            noEstoque = item;
        }
        if (noEstoque == null || noEstoque.getQuantidade() <= 0) return false;

        long precoDinamicoLatao = calcularPrecoDinamicoVendaEmLatao(noEstoque);
        if (!carteiraCliente.pagarLatao(precoDinamicoLatao)) {
            return false;
        }

        caixa.receberLatao(precoDinamicoLatao);
        removerUnidades(noEstoque.getId(), 1);
        saidasRecentes.merge(noEstoque.getId(), 1, Integer::sum);
        demandaPorItem.computeIfPresent(noEstoque.getId(), (k, v) -> Math.max(0, v - 1));

        registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.VENDA_BALCAO,
                noEstoque.getNome(), 1, precoDinamicoLatao, "Venda balcão para cliente");
        return true;
    }

    /**
     * Compra no balcão (jogador vendendo mercadoria para a loja).
     */
    public boolean comprarItem(Item item, Carteira carteiraCliente, double precoCompraEmOuro) {
        return comprarItem(item, carteiraCliente, precoCompraEmOuro, 1, 12);
    }

    /**
     * Compra no balcão com dia e hora especificados.
     */
    public boolean comprarItem(Item item, Carteira carteiraCliente, double precoCompraEmOuro, int dia, int hora) {
        if (item == null || carteiraCliente == null || precoCompraEmOuro < 0) return false;
        long valorLatao = Carteira.ouroParaLatao(precoCompraEmOuro);

        if (!caixa.pagarLatao(valorLatao)) return false;

        carteiraCliente.receberLatao(valorLatao);
        adicionarItem(clonarItem(item, 1));
        entradasRecentes.merge(item.getId(), 1, Integer::sum);

        registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.COMPRA_BALCAO,
                item.getNome(), 1, valorLatao, "Compra balcão de aventureiro");
        return true;
    }

    public void definirDemanda(Item item, int qtdDemanda) {
        if (item == null) return;
        catalogoConhecido.putIfAbsent(item.getId(), clonarItem(item, 1));
        demandaPorItem.put(item.getId(), Math.max(0, qtdDemanda));
    }

    public int getDeficitDemanda(int idItem) {
        return Math.max(0, demandaPorItem.getOrDefault(idItem, 0) - getQuantidadeEstoque(idItem));
    }

    public void registrarMovimentacao(int dia, int hora, RegistroMovimentacao.TipoOperacao tipo,
                                      String nomeItem, int qtd, long valorLatao, String obs) {
        historico.add(new RegistroMovimentacao(
                dia, hora, tipo, nomeItem, qtd, valorLatao, caixa.getValorTotalEmLatao(), obs
        ));
    }

    public void decairFluxoRecente() {
        entradasRecentes.replaceAll((k, v) -> v / 2);
        saidasRecentes.replaceAll((k, v) -> v / 2);
    }

    public static Item clonarItem(Item base, int quantidade) {
        if (base == null) return null;
        return new Item(base.getId(), base.getNome(), base.getDescricao(),
                base.getPreco(), base.getPeso(), base.getTipo(), base.getRaridade(),
                Math.max(1, quantidade));
    }

    // Getters e Setters
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getMercador() { return mercador; }
    public void setMercador(String mercador) { this.mercador = mercador; }

    public boolean isLojaDeRede() { return lojaDeRede; }
    public void setLojaDeRede(boolean lojaDeRede) { this.lojaDeRede = lojaDeRede; }

    public String getNomeDaRede() { return nomeDaRede; }
    public void setNomeDaRede(String nomeDaRede) { this.nomeDaRede = nomeDaRede; }

    public List<Item> getEstoque() { return Collections.unmodifiableList(estoque); }
    public Carteira getCaixa() { return caixa; }
    public void setCaixa(Carteira caixa) { if (caixa != null) this.caixa = caixa; }

    public Cidade getCidadeSede() { return cidadeSede; }
    public void setCidadeSede(Cidade cidadeSede) { this.cidadeSede = cidadeSede; }

    public TipoEstabelecimento getTipoEstabelecimento() { return tipoEstabelecimento; }
    public void setTipoEstabelecimento(TipoEstabelecimento tipoEstabelecimento) {
        this.tipoEstabelecimento = tipoEstabelecimento;
    }

    public Map<Integer, Item> getCatalogoConhecido() { return Collections.unmodifiableMap(catalogoConhecido); }
    public Map<Integer, Integer> getDemandaPorItem() { return Collections.unmodifiableMap(demandaPorItem); }
    public Map<Integer, Integer> getEntradasRecentes() { return Collections.unmodifiableMap(entradasRecentes); }
    public Map<Integer, Integer> getSaidasRecentes() { return Collections.unmodifiableMap(saidasRecentes); }
    public List<RegistroMovimentacao> getHistorico() { return Collections.unmodifiableList(historico); }

    @Override
    public String toString() {
        String infoRede = lojaDeRede ? " [Rede: " + nomeDaRede + "]" : "";
        String sede = (cidadeSede != null) ? " em " + cidadeSede.getNome() : "";
        return String.format("%s (%s)%s%s [%s] - %d tipo(s) no estoque | Caixa: %s",
                nome, mercador, sede, infoRede, tipoEstabelecimento, estoque.size(),
                Carteira.formatarLatao(caixa.getValorTotalEmLatao()));
    }
}
