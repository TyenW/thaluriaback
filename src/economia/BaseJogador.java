package economia;

import geografia.Cidade;

/**
 * Representa a Propriedade / Base Comercial e Residencial do grupo de jogadores em Thaluria.
 * Permite evolução end-game em Platina, Cobalto, Diamante e Talento,
 * integrando estufas de cultivo botânico, armazém de segurança e oficina própria.
 */
public class BaseJogador {

    public enum NivelBase {
        NIVEL_0_TERRENO(0, "Terreno Bruto", 0L),
        NIVEL_1_ACAMPAMENTO(1, "Acampamento Fortificado", 500_000L),          // 50 PO (5 PT)
        NIVEL_2_FEITORIA(2, "Feitoria Comercial", 1_000_000L),               // 100 PO (1 CB)
        NIVEL_3_BASTIAO(3, "Bastião Autônomo", 5_000_000L),                 // 500 PO (5 CB)
        NIVEL_4_COMPLEXO(4, "Complexo Arcano-Industrial", 10_000_000L),      // 1.000 PO (1 DI)
        NIVEL_5_CIDADELA(5, "Cidadela Mendeliana", 100_000_000L);            // 10.000 PO (1 TL)

        private final int nivel;
        private final String nome;
        private final long custoEmLatao;

        NivelBase(int nivel, String nome, long custoEmLatao) {
            this.nivel = nivel;
            this.nome = nome;
            this.custoEmLatao = custoEmLatao;
        }

        public int getNivel() { return nivel; }
        public String getNome() { return nome; }
        public long getCustoEmLatao() { return custoEmLatao; }
    }

    private final String nomeBase;
    private final Cidade cidadeSede;
    private NivelBase nivelAtual = NivelBase.NIVEL_0_TERRENO;
    private final EstufaCultivo estufa;
    private final Lojas oficinaPropria;
    private final Carteira cofre;

    public BaseJogador(String nomeBase, Cidade cidadeSede) {
        this.nomeBase = (nomeBase != null && !nomeBase.isBlank()) ? nomeBase.trim() : "Refúgio dos Heróis";
        this.cidadeSede = cidadeSede;
        this.estufa = new EstufaCultivo(2);
        this.oficinaPropria = new Lojas("Oficina de " + this.nomeBase, "Mestre da Base", cidadeSede, false, null, Lojas.TipoEstabelecimento.FORJA);
        this.oficinaPropria.setNivelLoja(1);
        this.cofre = new Carteira(0);
    }

    public boolean evoluirBase(Carteira pagadora, int dia, int hora) {
        if (pagadora == null) return false;
        int proximoOrdinal = nivelAtual.ordinal() + 1;
        if (proximoOrdinal >= NivelBase.values().length) return false;

        NivelBase proximo = NivelBase.values()[proximoOrdinal];
        if (!pagadora.pagarLatao(proximo.getCustoEmLatao())) {
            return false;
        }

        this.nivelAtual = proximo;
        this.estufa.adicionarCanteiro();
        this.oficinaPropria.setNivelLoja(Math.min(5, nivelAtual.getNivel()));

        if (nivelAtual.getNivel() >= 3) {
            this.estufa.setClimatizada(true);
            this.oficinaPropria.getArvoreEvolucao().adicionarMelhoriaDireta(ArvoreEvolucaoLoja.MelhoriaLoja.ESTUFA_CLIMATIZADA);
        }
        if (nivelAtual.getNivel() >= 4) {
            this.oficinaPropria.getArvoreEvolucao().adicionarMelhoriaDireta(ArvoreEvolucaoLoja.MelhoriaLoja.FORNO_ALTA_TEMPERATURA);
            this.oficinaPropria.getArvoreEvolucao().adicionarMelhoriaDireta(ArvoreEvolucaoLoja.MelhoriaLoja.CAMARA_CONSERVACAO);
        }

        oficinaPropria.registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.MANUTENCAO_BASE,
                proximo.getNome(), 1, proximo.getCustoEmLatao(),
                "Evolução da Base do Jogador para " + proximo.getNome());
        return true;
    }

    public void processarDia(int dia, int hora) {
        estufa.avancarDia();
    }

    public String getNomeBase() {
        return nomeBase;
    }

    public Cidade getCidadeSede() {
        return cidadeSede;
    }

    public NivelBase getNivelAtual() {
        return nivelAtual;
    }

    public EstufaCultivo getEstufa() {
        return estufa;
    }

    public Lojas getOficinaPropria() {
        return oficinaPropria;
    }

    public Carteira getCofre() {
        return cofre;
    }
}
