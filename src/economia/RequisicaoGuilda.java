package economia;

import geografia.Cidade;
import java.util.Random;

/**
 * Representa um contrato de expedição encomendado a uma Sede Regional da Guilda de Comércio.
 * A expedição envolve tempo de viagem em dias, taxa fixa da guilda não-reembolsável,
 * recompensa em caução para os aventureiros e teste de resolução em 1d20.
 */
public class RequisicaoGuilda {

    public enum StatusRequisicao {
        EM_EXPEDICAO,
        CONCLUIDA_SUCESSO_CRITICO,
        CONCLUIDA_SUCESSO,
        CONCLUIDA_PARCIAL,
        FRACASSADA
    }

    private final int idRequisicao;
    private final Cidade cidadeGuilda;
    private final Lojas lojaSolicitante;
    private final Carteira carteiraSolicitante; // Caso a requisição seja feita por aventureiro/jogador direto
    private final Item itemAlvo;
    private final int quantidadeSolicitada;
    private final int nivelRequisicao; // 1 a 5
    private final long taxaGuildaPagaLatao;
    private final long recompensaCaucaoLatao;
    private int diasRestantes;
    private StatusRequisicao status;
    private int ultimaRolagemD20 = 0;
    private int quantidadeEntregue = 0;

    public RequisicaoGuilda(int idRequisicao, Cidade cidadeGuilda, Lojas lojaSolicitante,
                            Item itemAlvo, int quantidadeSolicitada, int nivelRequisicao,
                            long taxaGuildaPagaLatao, long recompensaCaucaoLatao, int diasExpedicao) {
        this(idRequisicao, cidadeGuilda, lojaSolicitante, (lojaSolicitante != null ? lojaSolicitante.getCaixa() : null),
                itemAlvo, quantidadeSolicitada, nivelRequisicao, taxaGuildaPagaLatao, recompensaCaucaoLatao, diasExpedicao);
    }

    public RequisicaoGuilda(int idRequisicao, Cidade cidadeGuilda, Lojas lojaSolicitante,
                            Carteira carteiraSolicitante, Item itemAlvo, int quantidadeSolicitada,
                            int nivelRequisicao, long taxaGuildaPagaLatao, long recompensaCaucaoLatao,
                            int diasExpedicao) {
        this.idRequisicao = idRequisicao;
        this.cidadeGuilda = cidadeGuilda;
        this.lojaSolicitante = lojaSolicitante;
        this.carteiraSolicitante = carteiraSolicitante;
        this.itemAlvo = itemAlvo;
        this.quantidadeSolicitada = Math.max(1, quantidadeSolicitada);
        this.nivelRequisicao = Math.max(1, Math.min(5, nivelRequisicao));
        this.taxaGuildaPagaLatao = Math.max(0L, taxaGuildaPagaLatao);
        this.recompensaCaucaoLatao = Math.max(0L, recompensaCaucaoLatao);
        this.diasRestantes = Math.max(1, diasExpedicao);
        this.status = StatusRequisicao.EM_EXPEDICAO;
    }

    public static long calcularTaxaFixaNivelEmLatao(int nivel) {
        return switch (Math.max(1, Math.min(5, nivel))) {
            case 1 -> 5_000L;      // 5 PP
            case 2 -> 15_000L;     // 1 PO, 5 PP
            case 3 -> 40_000L;     // 4 PO
            case 4 -> 120_000L;    // 1 PT, 2 PO
            default -> 500_000L;   // 5 PT
        };
    }

    public static int calcularCdBaseNivel(int nivel) {
        return switch (Math.max(1, Math.min(5, nivel))) {
            case 1 -> 6;
            case 2 -> 9;
            case 3 -> 12;
            case 4 -> 15;
            default -> 18;
        };
    }

    public static int calcularDiasExpedicaoNivel(int nivel) {
        return switch (Math.max(1, Math.min(5, nivel))) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 5;
            default -> 7;
        };
    }

    /**
     * Avança 1 dia na requisição. Quando os dias chegam a zero, rola 1d20 para determinar o resultado.
     */
    public boolean avancarDiaEResolver(int diaMundo, int horaMundo, Random random) {
        if (status != StatusRequisicao.EM_EXPEDICAO) return false;
        diasRestantes--;
        if (diasRestantes > 0) return false;

        Random rnd = (random != null) ? random : new Random();
        this.ultimaRolagemD20 = 1 + rnd.nextInt(20);
        int cd = calcularCdBaseNivel(nivelRequisicao);

        // Calcula bônus no dado se a recompensa for maior que o valor base do lote (+1 a +4)
        long valorBaseLote = Math.max(1L, itemAlvo.getPrecoEmLatao() * quantidadeSolicitada);
        double proporcaoRecompensa = (double) recompensaCaucaoLatao / valorBaseLote;
        int bonusRecompensa = Math.min(4, Math.max(0, (int) Math.floor((proporcaoRecompensa - 1.0) * 4.0)));

        int totalTeste = ultimaRolagemD20 + bonusRecompensa;

        if (ultimaRolagemD20 == 20) {
            // SUCESSO CRÍTICO (20 Natural): 150% da quantidade solicitada!
            this.status = StatusRequisicao.CONCLUIDA_SUCESSO_CRITICO;
            this.quantidadeEntregue = (int) Math.ceil(quantidadeSolicitada * 1.5);
            entregarLoteParaSolicitante(this.quantidadeEntregue, rnd);
        } else if (ultimaRolagemD20 != 1 && totalTeste >= cd) {
            // SUCESSO NORMAL: 100% da quantidade solicitada
            this.status = StatusRequisicao.CONCLUIDA_SUCESSO;
            this.quantidadeEntregue = quantidadeSolicitada;
            entregarLoteParaSolicitante(this.quantidadeEntregue, rnd);
        } else if (ultimaRolagemD20 != 1 && totalTeste >= (cd - 3)) {
            // SUCESSO PARCIAL: Entrega 50% do lote e estorna 50% da recompensa para o caixa/carteira!
            this.status = StatusRequisicao.CONCLUIDA_PARCIAL;
            this.quantidadeEntregue = Math.max(1, quantidadeSolicitada / 2);
            long estornoMetade = recompensaCaucaoLatao / 2;
            devolverLatao(estornoMetade);
            entregarLoteParaSolicitante(this.quantidadeEntregue, rnd);
        } else {
            // FRACASSO: Nenhum item entregue; estorna 100% da recompensa em caução (perde só a taxa da Guilda)
            this.status = StatusRequisicao.FRACASSADA;
            this.quantidadeEntregue = 0;
            devolverLatao(recompensaCaucaoLatao);
        }
        return true;
    }

    private void devolverLatao(long valorLatao) {
        if (valorLatao <= 0) return;
        if (lojaSolicitante != null) {
            lojaSolicitante.getCaixa().receberLatao(valorLatao);
        } else if (carteiraSolicitante != null) {
            carteiraSolicitante.receberLatao(valorLatao);
        }
    }

    private void entregarLoteParaSolicitante(int qtd, Random random) {
        Item lote = Lojas.clonarItem(itemAlvo, qtd);
        if (lote.getFichaMaterial() != null) {
            lote.getFichaMaterial().sortearEfeitoNoDrop(random);
        }
        if (lojaSolicitante != null) {
            lojaSolicitante.adicionarItem(lote);
        }
    }

    public int getIdRequisicao() { return idRequisicao; }
    public Cidade getCidadeGuilda() { return cidadeGuilda; }
    public Lojas getLojaSolicitante() { return lojaSolicitante; }
    public Carteira getCarteiraSolicitante() { return carteiraSolicitante; }
    public Item getItemAlvo() { return itemAlvo; }
    public int getQuantidadeSolicitada() { return quantidadeSolicitada; }
    public int getQuantidadeEntregue() { return quantidadeEntregue; }
    public int getNivelRequisicao() { return nivelRequisicao; }
    public long getTaxaGuildaPagaLatao() { return taxaGuildaPagaLatao; }
    public long getRecompensaCaucaoLatao() { return recompensaCaucaoLatao; }
    public int getDiasRestantes() { return diasRestantes; }
    public StatusRequisicao getStatus() { return status; }
    public int getUltimaRolagemD20() { return ultimaRolagemD20; }
}
