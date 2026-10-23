package economia;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Gerencia as melhorias de infraestrutura física de uma oficina, loja ou base em Thaluria.
 * Inclui expansões volumétricas, climatização botânica, câmaras de conservação e fornos avançados.
 */
public class ArvoreEvolucaoLoja {

    public enum MelhoriaLoja {
        EXPANSAO_ARMAZEM("Expansão de Armazém", "Aumenta a capacidade volumétrica do armazém em +30 slots", 50_000L, 1),
        EXPANSAO_PRATELEIRA("Expansão de Prateleira", "Aumenta a capacidade de exposição da prateleira em +20 slots", 40_000L, 1),
        BANCADAS_ADICIONAIS("Bancadas Adicionais", "Permite +2 ordens simultâneas e reduz tempo de manufatura", 120_000L, 2),
        ESTUFA_CLIMATIZADA("Estufa Climatizada", "Permite cultivo botânico independente do clima e bioma externo", 300_000L, 3),
        FORNO_ALTA_TEMPERATURA("Forno de Alta Temperatura", "Permite fundição de ligas avançadas e concede bônus em peças metálicas", 350_000L, 3),
        LABORATORIO_DESTILACAO("Laboratório de Destilação", "Acelera a destilação de poções e fermentação em 50%", 250_000L, 3),
        CAMARA_CONSERVACAO("Câmara de Conservação", "Triplica a validade em dias de todos os insumos perecíveis armazenados", 180_000L, 2);

        private final String nome;
        private final String descricao;
        private final long custoEmLatao;
        private final int nivelMinimoLoja;

        MelhoriaLoja(String nome, String descricao, long custoEmLatao, int nivelMinimoLoja) {
            this.nome = nome;
            this.descricao = descricao;
            this.custoEmLatao = custoEmLatao;
            this.nivelMinimoLoja = nivelMinimoLoja;
        }

        public String getNome() {
            return nome;
        }

        public String getDescricao() {
            return descricao;
        }

        public long getCustoEmLatao() {
            return custoEmLatao;
        }

        public int getNivelMinimoLoja() {
            return nivelMinimoLoja;
        }
    }

    private final Set<MelhoriaLoja> melhoriasInstaladas = new LinkedHashSet<>();

    /**
     * Adquire uma melhoria para a loja debitando o custo em latão do caixa da loja.
     */
    public boolean comprarMelhoria(MelhoriaLoja melhoria, Lojas loja, int dia, int hora) {
        if (melhoria == null || loja == null) return false;
        if (melhoriasInstaladas.contains(melhoria)) return false;
        if (loja.getNivelLoja() < melhoria.getNivelMinimoLoja()) return false;

        if (!loja.getCaixa().pagarLatao(melhoria.getCustoEmLatao())) {
            return false;
        }

        melhoriasInstaladas.add(melhoria);
        loja.registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.MANUTENCAO_BASE,
                melhoria.getNome(), 1, melhoria.getCustoEmLatao(),
                "Instalação de melhoria: " + melhoria.getNome());
        return true;
    }

    public boolean temMelhoria(MelhoriaLoja melhoria) {
        return melhoriasInstaladas.contains(melhoria);
    }

    public Set<MelhoriaLoja> getMelhoriasInstaladas() {
        return Collections.unmodifiableSet(melhoriasInstaladas);
    }

    public int getBonusSlotsArmazem() {
        return temMelhoria(MelhoriaLoja.EXPANSAO_ARMAZEM) ? 30 : 0;
    }

    public int getBonusSlotsPrateleira() {
        return temMelhoria(MelhoriaLoja.EXPANSAO_PRATELEIRA) ? 20 : 0;
    }

    public int getBonusOrdensSimultaneas() {
        return temMelhoria(MelhoriaLoja.BANCADAS_ADICIONAIS) ? 2 : 0;
    }

    public boolean isCamaraConservacaoAtiva() {
        return temMelhoria(MelhoriaLoja.CAMARA_CONSERVACAO);
    }

    public void adicionarMelhoriaDireta(MelhoriaLoja melhoria) {
        if (melhoria != null) {
            this.melhoriasInstaladas.add(melhoria);
        }
    }
}
