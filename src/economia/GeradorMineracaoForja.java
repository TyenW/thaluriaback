package economia;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Representa a Árvore de Gerador de Mineração de uma Forja em Thaluria.
 * Permite ao Ferreiro investir seu fundo de caixa em contratos de mineradores
 * para extrair minérios diários dos veios geológicos da sua cidade.
 * Se o Ferreiro não tiver saldo para pagar as diárias da equipe, os mineradores entram em greve.
 */
public class GeradorMineracaoForja {

    public enum NivelGeradorMina {
        NIVEL_0_DESATIVADO(0, "Sem Mineradores", 0, 0, 0, 0L, 0L, 0.0),
        NIVEL_1_ESCAVADORES(1, "Picaretas Alugadas (2 Aprendizes)", 2, 4, 1, 4_000L, 200_000L, 0.0),
        NIVEL_2_VAGONETAS(2, "Capataz e Vagonetas (5 Mineradores)", 5, 8, 2, 10_000L, 800_000L, 0.0),
        NIVEL_3_GALERIA_PROFUNDA(3, "Galeria Profunda (Especialistas)", 8, 12, 3, 25_000L, 2_500_000L, 0.05),
        NIVEL_4_SONDA_ALQUIMICA(4, "Detonação e Sonda Geológica", 12, 16, 4, 50_000L, 8_000_000L, 0.20),
        NIVEL_5_LAVRA_ABISSAL(5, "Mestre de Lavra Abissal", 15, 22, 5, 100_000L, 30_000_000L, 0.35);

        private final int nivel;
        private final String descricao;
        private final int qtdMinDiaria;
        private final int qtdMaxDiaria;
        private final int qualidadeMaximaExtraida;
        private final long salarioDiarioEmLatao;
        private final long custoUpgradeEmLatao;
        private final double chanceGemaExtra;

        NivelGeradorMina(int nivel, String descricao, int qtdMinDiaria, int qtdMaxDiaria,
                         int qualidadeMaximaExtraida, long salarioDiarioEmLatao,
                         long custoUpgradeEmLatao, double chanceGemaExtra) {
            this.nivel = nivel;
            this.descricao = descricao;
            this.qtdMinDiaria = qtdMinDiaria;
            this.qtdMaxDiaria = qtdMaxDiaria;
            this.qualidadeMaximaExtraida = qualidadeMaximaExtraida;
            this.salarioDiarioEmLatao = salarioDiarioEmLatao;
            this.custoUpgradeEmLatao = custoUpgradeEmLatao;
            this.chanceGemaExtra = chanceGemaExtra;
        }

        public int getNivel() { return nivel; }
        public String getDescricao() { return descricao; }
        public int getQtdMinDiaria() { return qtdMinDiaria; }
        public int getQtdMaxDiaria() { return qtdMaxDiaria; }
        public int getQualidadeMaximaExtraida() { return qualidadeMaximaExtraida; }
        public long getSalarioDiarioEmLatao() { return salarioDiarioEmLatao; }
        public long getCustoUpgradeEmLatao() { return custoUpgradeEmLatao; }
        public double getChanceGemaExtra() { return chanceGemaExtra; }
    }

    private NivelGeradorMina nivelAtual = NivelGeradorMina.NIVEL_0_DESATIVADO;

    public GeradorMineracaoForja() {
        this(NivelGeradorMina.NIVEL_0_DESATIVADO);
    }

    public GeradorMineracaoForja(NivelGeradorMina nivelInicial) {
        this.nivelAtual = (nivelInicial != null) ? nivelInicial : NivelGeradorMina.NIVEL_0_DESATIVADO;
    }

    /**
     * Evolui a árvore do gerador do ferreiro pagando o custo de infraestrutura em Latão.
     */
    public boolean evoluirGerador(Lojas forja, int dia, int hora) {
        if (forja == null || forja.getTipoEstabelecimento() != Lojas.TipoEstabelecimento.FORJA) {
            return false;
        }
        int proximoOrdinal = nivelAtual.ordinal() + 1;
        if (proximoOrdinal >= NivelGeradorMina.values().length) return false;

        NivelGeradorMina proximoNivel = NivelGeradorMina.values()[proximoOrdinal];
        // O nível do gerador não pode ultrapassar o nível da própria Forja (1 a 5)
        if (proximoNivel.getNivel() > forja.getNivelLoja()) return false;

        if (!forja.getCaixa().pagarLatao(proximoNivel.getCustoUpgradeEmLatao())) {
            return false;
        }

        this.nivelAtual = proximoNivel;
        forja.registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.MANUTENCAO_BASE,
                proximoNivel.getDescricao(), 1, proximoNivel.getCustoUpgradeEmLatao(),
                "Evolução da Árvore de Mineradores da Forja para Nv." + proximoNivel.getNivel());
        return true;
    }

    /**
     * Executado diariamente pelo MotorEconomico:
     * Paga os mineradores e extrai minérios EXCLUSIVAMENTE dos veios geológicos da Cidade sede!
     */
    public List<Item> extrairMineriosDoDia(Lojas forja, List<Item> veiosMineraisDaCidade,
                                           int dia, int hora, Random random) {
        List<Item> extraidos = new ArrayList<>();
        if (nivelAtual == NivelGeradorMina.NIVEL_0_DESATIVADO || veiosMineraisDaCidade == null || veiosMineraisDaCidade.isEmpty()) {
            return extraidos;
        }

        // Filtra apenas os minérios da região cuja qualidade o nível atual de mineradores alcança
        List<Item> veiosAcessiveis = new ArrayList<>();
        for (Item veio : veiosMineraisDaCidade) {
            if (veio.getFichaMaterial() != null
                    && veio.getFichaMaterial().getQualidade() <= nivelAtual.getQualidadeMaximaExtraida()) {
                veiosAcessiveis.add(veio);
            }
        }
        if (veiosAcessiveis.isEmpty()) return extraidos;

        // Paga o salário diário dos mineradores
        if (!forja.getCaixa().pagarLatao(nivelAtual.getSalarioDiarioEmLatao())) {
            return extraidos; // Sem dinheiro para pagar a diária = mineradores em greve hoje!
        }

        Random rnd = (random != null) ? random : new Random();
        int qtdTotal = nivelAtual.getQtdMinDiaria()
                + rnd.nextInt(nivelAtual.getQtdMaxDiaria() - nivelAtual.getQtdMinDiaria() + 1);

        Item minérioEscolhido = veiosAcessiveis.get(rnd.nextInt(veiosAcessiveis.size()));
        Item loteExtraido = Lojas.clonarItem(minérioEscolhido, qtdTotal);
        forja.adicionarItem(loteExtraido);
        extraidos.add(loteExtraido);

        forja.registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.ENTRADA_NPC,
                loteExtraido.getNome(), qtdTotal, nivelAtual.getSalarioDiarioEmLatao(),
                "Extração própria [" + nivelAtual.getDescricao() + "]");

        return extraidos;
    }

    public NivelGeradorMina getNivelAtual() { return nivelAtual; }
    public void setNivelAtual(NivelGeradorMina nivelAtual) {
        if (nivelAtual != null) this.nivelAtual = nivelAtual;
    }
}
