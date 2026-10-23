package economia;

/**
 * Registra uma entrada, saída ou evento contábil no livro-caixa de uma loja em Thaluria.
 * Todos os valores monetários são rastreados com precisão absoluta em Latão (long)
 * e formatados na escala de 10 moedas do sistema econômico v2.
 */
public class RegistroMovimentacao {

    public enum TipoOperacao {
        ENTRADA_NPC("Entrada via Fornecedor NPC"),
        VENDA_BALCAO("Venda no Balcão"),
        COMPRA_BALCAO("Compra no Balcão (Aventureiro)"),
        COMPRA_INTERMUNICIPAL("Compra Intermunicipal (Encomenda)"),
        VENDA_INTERMUNICIPAL("Venda Intermunicipal (Fornecimento)"),
        DESPESA_FRETE("Despesa com Frete"),
        DESPESA_ESCOLTA("Despesa com Escolta Armada"),
        EXTRAVIO_CARGA("Extravio de Carga"),
        CARGA_RECUPERADA("Carga Recuperada"),
        PRODUCAO_CRAFTING("Produção / Crafting"),
        MANUTENCAO_BASE("Manutenção / Infraestrutura"),
        SALARIO_MINERADORES("Salário dos Mineradores"),
        PERDA_DETERIORACAO("Perda por Deterioração"),
        LIQUIDACAO_PRATELEIRA("Liquidação de Prateleira"),
        RECICLAGEM_SUCATA("Reciclagem de Sucata"),
        VENDA_B2B("Venda B2B Atacado"),
        CONTRATO_ENCOMENDA("Contrato de Encomenda Especial");

        private final String descricao;

        TipoOperacao(String descricao) {
            this.descricao = descricao;
        }

        public String getDescricao() {
            return descricao;
        }

        @Override
        public String toString() {
            return descricao;
        }
    }

    private final int diaMundo;
    private final int horaMundo;
    private final TipoOperacao tipo;
    private final String nomeItem;
    private final int quantidade;
    private final long valorEmLatao;
    private final long saldoCaixaAposEmLatao;
    private final String observacao;

    public RegistroMovimentacao(int diaMundo, int horaMundo, TipoOperacao tipo,
                                String nomeItem, int quantidade, long valorEmLatao,
                                long saldoCaixaAposEmLatao, String observacao) {
        this.diaMundo = diaMundo;
        this.horaMundo = horaMundo;
        this.tipo = (tipo != null) ? tipo : TipoOperacao.ENTRADA_NPC;
        this.nomeItem = (nomeItem != null) ? nomeItem : "Item Geral";
        this.quantidade = Math.max(0, quantidade);
        this.valorEmLatao = valorEmLatao;
        this.saldoCaixaAposEmLatao = saldoCaixaAposEmLatao;
        this.observacao = (observacao != null) ? observacao : "";
    }

    public int getDiaMundo() {
        return diaMundo;
    }

    public int getHoraMundo() {
        return horaMundo;
    }

    public TipoOperacao getTipo() {
        return tipo;
    }

    public String getNomeItem() {
        return nomeItem;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public long getValorEmLatao() {
        return valorEmLatao;
    }

    public long getSaldoCaixaAposEmLatao() {
        return saldoCaixaAposEmLatao;
    }

    public String getObservacao() {
        return observacao;
    }

    @Override
    public String toString() {
        return String.format("[Dia %d, %02dh] %-28s | %-16s (x%d) | Valor: %-14s | Caixa: %-14s | %s",
                diaMundo, horaMundo, tipo.getDescricao(), nomeItem, quantidade,
                Carteira.formatarLatao(valorEmLatao),
                Carteira.formatarLatao(saldoCaixaAposEmLatao),
                observacao);
    }
}
