package economia;

import java.util.Random;

/**
 * Representa um lote de produtos prontos expostos na prateleira de vendas da loja.
 * Monitora os dias de encalhe e aciona a Roleta de Liquidação (1d100) quando atinge o prazo limite.
 */
public class LotePrateleira {

    public enum ResultadoRoleta {
        AGUARDANDO,
        LUCRO_EXTRA_COLECIONADOR,
        VENDA_PRECO_BASE,
        QUEIMA_ESTOQUE,
        RECICLAGEM_SUCATA,
        LIQUIDACAO_SUCATA
    }

    private final Item item;
    private int quantidade;
    private int diasEncalhado;
    private int limiteDiasPrateleira;
    private ResultadoRoleta ultimoResultado = ResultadoRoleta.AGUARDANDO;
    private int ultimaRolagemD100 = 0;

    public LotePrateleira(Item item, int quantidade) {
        this(item, quantidade, 7);
    }

    public LotePrateleira(Item item, int quantidade, int limiteDiasPrateleira) {
        this.item = item;
        this.quantidade = Math.max(1, quantidade);
        this.limiteDiasPrateleira = Math.max(1, limiteDiasPrateleira);
        this.diasEncalhado = 0;
    }

    /**
     * Avança 1 dia no contador de encalhe. Se atingir o limite, executa a roleta de liquidação.
     */
    public boolean avancarDiaEResolver(Lojas loja, int diaMundo, int horaMundo, Random random) {
        if (quantidade <= 0) return false;
        diasEncalhado++;
        if (diasEncalhado >= limiteDiasPrateleira) {
            executarRoletaLiquidacao(loja, diaMundo, horaMundo, random);
            return true;
        }
        return false;
    }

    /**
     * Executa a Roleta de Liquidação (1d100):
     * - 86 a 100: Lucro Extra Colecionador (1.40x)
     * - 51 a 85: Venda Preço Base (1.00x)
     * - 16 a 50: Queima de Estoque (0.55x)
     * - 1 a 15: Encalhe Crítico (Reciclagem 30% insumos se tiver talento; senão sucata 0.20x)
     */
    public void executarRoletaLiquidacao(Lojas loja, int diaMundo, int horaMundo, Random random) {
        Random rnd = (random != null) ? random : new Random();
        this.ultimaRolagemD100 = 1 + rnd.nextInt(100);

        long valorBaseTotal = item.getPrecoEmLatao() * quantidade;

        if (ultimaRolagemD100 >= 86) {
            this.ultimoResultado = ResultadoRoleta.LUCRO_EXTRA_COLECIONADOR;
            long valorFinal = (long) Math.ceil(valorBaseTotal * 1.40);
            if (loja != null) {
                loja.getCaixa().receberLatao(valorFinal);
                loja.removerItem(item.getId(), quantidade);
                loja.registrarMovimentacao(diaMundo, horaMundo, RegistroMovimentacao.TipoOperacao.LIQUIDACAO_PRATELEIRA,
                        item.getNome(), quantidade, valorFinal,
                        "Roleta d100: " + ultimaRolagemD100 + " - Lucro Extra Colecionador (1.40x)");
            }
            this.quantidade = 0;
        } else if (ultimaRolagemD100 >= 51) {
            this.ultimoResultado = ResultadoRoleta.VENDA_PRECO_BASE;
            long valorFinal = valorBaseTotal;
            if (loja != null) {
                loja.getCaixa().receberLatao(valorFinal);
                loja.removerItem(item.getId(), quantidade);
                loja.registrarMovimentacao(diaMundo, horaMundo, RegistroMovimentacao.TipoOperacao.LIQUIDACAO_PRATELEIRA,
                        item.getNome(), quantidade, valorFinal,
                        "Roleta d100: " + ultimaRolagemD100 + " - Venda Normal de Prateleira (1.00x)");
            }
            this.quantidade = 0;
        } else if (ultimaRolagemD100 >= 16) {
            this.ultimoResultado = ResultadoRoleta.QUEIMA_ESTOQUE;
            long valorFinal = (long) Math.floor(valorBaseTotal * 0.55);
            if (loja != null) {
                loja.getCaixa().receberLatao(valorFinal);
                loja.removerItem(item.getId(), quantidade);
                loja.registrarMovimentacao(diaMundo, horaMundo, RegistroMovimentacao.TipoOperacao.LIQUIDACAO_PRATELEIRA,
                        item.getNome(), quantidade, valorFinal,
                        "Roleta d100: " + ultimaRolagemD100 + " - Queima de Estoque (0.55x)");
            }
            this.quantidade = 0;
        } else {
            boolean temReciclagem = (loja != null && loja.temTalento(TalentoLojista.RECICLAGEM_DE_SUCATA));
            if (temReciclagem) {
                this.ultimoResultado = ResultadoRoleta.RECICLAGEM_SUCATA;
                if (loja != null) {
                    loja.removerItem(item.getId(), quantidade);
                    Item sucataRecuperada = new Item(item.getId() + 9000, "Insumos Reciclados de " + item.getNome(),
                            "Matéria-prima reaproveitada", Math.max(0.1, (item.getPreco() * 0.30) / Math.max(1, quantidade)),
                            item.getPeso() * 0.30, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, quantidade);
                    sucataRecuperada.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.METAL_LINGOTE, 1));
                    loja.adicionarItem(sucataRecuperada);
                    loja.registrarMovimentacao(diaMundo, horaMundo, RegistroMovimentacao.TipoOperacao.RECICLAGEM_SUCATA,
                            item.getNome(), quantidade, (long)(valorBaseTotal * 0.30),
                            "Roleta d100: " + ultimaRolagemD100 + " - Reciclagem de Sucata (30% de insumos recuperados)");
                }
            } else {
                this.ultimoResultado = ResultadoRoleta.LIQUIDACAO_SUCATA;
                long valorFinal = (long) Math.floor(valorBaseTotal * 0.20);
                if (loja != null) {
                    loja.getCaixa().receberLatao(valorFinal);
                    loja.removerItem(item.getId(), quantidade);
                    loja.registrarMovimentacao(diaMundo, horaMundo, RegistroMovimentacao.TipoOperacao.LIQUIDACAO_PRATELEIRA,
                            item.getNome(), quantidade, valorFinal,
                            "Roleta d100: " + ultimaRolagemD100 + " - Liquidação Sucata (0.20x)");
                }
            }
            this.quantidade = 0;
        }
    }

    public Item getItem() {
        return item;
    }

    /** Reduz o lote ao que ainda existe no estoque da loja. */
    public void limitarQuantidade(int maximo) {
        quantidade = Math.max(0, Math.min(quantidade, maximo));
    }

    public int getQuantidade() {
        return quantidade;
    }

    public int getDiasEncalhado() {
        return diasEncalhado;
    }

    public void setDiasEncalhado(int diasEncalhado) {
        this.diasEncalhado = Math.max(0, diasEncalhado);
    }

    public int getLimiteDiasPrateleira() {
        return limiteDiasPrateleira;
    }

    public void setLimiteDiasPrateleira(int limiteDiasPrateleira) {
        this.limiteDiasPrateleira = Math.max(1, limiteDiasPrateleira);
    }

    public ResultadoRoleta getUltimoResultado() {
        return ultimoResultado;
    }

    public int getUltimaRolagemD100() {
        return ultimaRolagemD100;
    }

    public boolean isVendidoOuLiquidado() {
        return quantidade <= 0;
    }
}
