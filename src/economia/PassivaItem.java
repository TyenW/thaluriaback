package economia;

import java.util.Objects;

/**
 * Representa uma habilidade passiva atrelada a acessórios e equipamentos táticos.
 * Opera como máquina de estados executável que avalia gatilhos em tempo real e dispara EfeitoItem na Ficha.
 */
public class PassivaItem {

    public enum TipoCondicaoPassiva {
        SEMPRE_ATIVA_AO_EQUIPAR("Sempre Ativa ao Equipar"),
        VIDA_ABAIXO_PERCENTUAL("Vida Abaixo de Percentual"),
        AO_CAUSAR_CRITICO("Ao Causar Acerto Crítico"),
        AO_SOFRER_DANO("Ao Sofrer Dano"),
        AO_ABATER_INIMIGO("Ao Abater Inimigo"),
        INICIO_DE_COMBATE("No Início do Combate");

        private final String descricao;

        TipoCondicaoPassiva(String descricao) {
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

    private final String nomePassiva;
    private final String condicaoAtivacao;
    private final String efeitoMecanico;
    private final int tempoRecargaTurnos;
    private int recargaRestanteTurnos;

    // Campos executáveis (Item v4.1)
    private final TipoCondicaoPassiva tipoCondicao;
    private final double parametroCondicao; // Ex: 0.30 para 30% da vida
    private final EfeitoItem efeitoAoAtivar;

    public PassivaItem(String nomePassiva, String condicaoAtivacao, String efeitoMecanico, int tempoRecargaTurnos) {
        this(nomePassiva, condicaoAtivacao, efeitoMecanico, tempoRecargaTurnos,
                TipoCondicaoPassiva.SEMPRE_ATIVA_AO_EQUIPAR, 1.0, null);
    }

    public PassivaItem(String nomePassiva, String condicaoAtivacao, String efeitoMecanico,
                       int tempoRecargaTurnos, TipoCondicaoPassiva tipoCondicao,
                       double parametroCondicao, EfeitoItem efeitoAoAtivar) {
        this.nomePassiva = (nomePassiva != null && !nomePassiva.trim().isEmpty()) ? nomePassiva.trim() : "Passiva Anônima";
        this.condicaoAtivacao = (condicaoAtivacao != null) ? condicaoAtivacao.trim() : "Sempre ativa";
        this.efeitoMecanico = (efeitoMecanico != null) ? efeitoMecanico.trim() : "Sem efeito";
        this.tempoRecargaTurnos = Math.max(0, tempoRecargaTurnos);
        this.recargaRestanteTurnos = 0;
        this.tipoCondicao = (tipoCondicao != null) ? tipoCondicao : TipoCondicaoPassiva.SEMPRE_ATIVA_AO_EQUIPAR;
        this.parametroCondicao = parametroCondicao;
        this.efeitoAoAtivar = efeitoAoAtivar;
    }

    public boolean estaDisponivel() {
        return this.recargaRestanteTurnos <= 0;
    }

    public boolean prontaParaAtivar() {
        return estaDisponivel();
    }

    /**
     * Tenta ativar a passiva se o evento e as condições baterem, iniciando o tempo de recarga com redução por qualidade.
     * Retorna o EfeitoItem vinculado para aplicação na Ficha, ou null se não puder ser ativado.
     */
    public EfeitoItem tentarAtivar(TipoCondicaoPassiva eventoAtual, double percentualVidaAtual, int reducaoCooldownQualidade) {
        if (!estaDisponivel() || this.tipoCondicao != eventoAtual) {
            return null;
        }
        if (eventoAtual == TipoCondicaoPassiva.VIDA_ABAIXO_PERCENTUAL
                && percentualVidaAtual > this.parametroCondicao) {
            return null;
        }
        int cdFinal = Math.max(0, this.tempoRecargaTurnos - Math.max(0, reducaoCooldownQualidade));
        this.recargaRestanteTurnos = cdFinal;
        return this.efeitoAoAtivar;
    }

    public boolean disparar() {
        if (estaDisponivel()) {
            this.recargaRestanteTurnos = this.tempoRecargaTurnos;
            return true;
        }
        return false;
    }

    public boolean ativar() {
        return disparar();
    }

    public void avancarTurno() {
        if (this.recargaRestanteTurnos > 0) {
            this.recargaRestanteTurnos--;
        }
    }

    public void resetarRecarga() {
        this.recargaRestanteTurnos = 0;
    }

    public String getNomePassiva() { return nomePassiva; }
    public String getCondicaoAtivacao() { return condicaoAtivacao; }
    public String getEfeitoMecanico() { return efeitoMecanico; }
    public int getTempoRecargaTurnos() { return tempoRecargaTurnos; }
    public int getRecargaRestanteTurnos() { return recargaRestanteTurnos; }
    public int getTurnosRestantesRecarga() { return recargaRestanteTurnos; }
    public TipoCondicaoPassiva getTipoCondicao() { return tipoCondicao; }
    public double getParametroCondicao() { return parametroCondicao; }
    public EfeitoItem getEfeitoAoAtivar() { return efeitoAoAtivar; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PassivaItem that = (PassivaItem) o;
        return Objects.equals(nomePassiva, that.nomePassiva);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nomePassiva);
    }

    @Override
    public String toString() {
        String cd = (tempoRecargaTurnos > 0) ? String.format(" [Cd: %d turnos]", tempoRecargaTurnos) : " [Sem Cd]";
        return String.format("%s: %s (%s)%s", nomePassiva, efeitoMecanico, condicaoAtivacao, cd);
    }
}
