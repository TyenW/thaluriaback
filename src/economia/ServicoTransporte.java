package economia;

import geografia.Conexao;

/**
 * Serviços de transporte que uma loja pode contratar para trazer uma encomenda.
 * Cada um troca velocidade e segurança por preço de frete:
 * <ul>
 * <li><b>Comum</b>: o padrão, nem rápido nem seguro, frete base.</li>
 * <li><b>Expresso</b>: rápido, mas os batedores chamam atenção (mais ataques por hora).</li>
 * <li><b>Comboio Blindado</b>: carroças reforçadas e guardas, seguro e lento.</li>
 * <li><b>Guarda Real</b>: rápido e seguro, o frete mais caro.</li>
 * </ul>
 * No mar o ritmo é o do navio, igual para todos os serviços; a segurança continua valendo.
 */
public enum ServicoTransporte {
    COMUM("Caravana Comum", 6.0, 1.0, 1.0),
    EXPRESSO("Expresso de Batedores", 10.0, 1.6, 1.3),
    COMBOIO_BLINDADO("Comboio Blindado", 4.0, 0.35, 1.4),
    GUARDA_REAL("Guarda Real", 9.0, 0.35, 2.2);

    /** Velocidade de um navio mercante, em km/h. */
    public static final double VELOCIDADE_MARITIMA_KMH = 14.0;
    /** Chance de ataque por hora para cada nível de perigo do trecho (antes da proteção). */
    public static final double RISCO_POR_HORA_POR_PERIGO = 0.012;

    private final String descricao;
    private final double velocidadeTerrestreKmH;
    private final double fatorRisco;
    private final double fatorFrete;

    ServicoTransporte(String descricao, double velocidadeTerrestreKmH, double fatorRisco, double fatorFrete) {
        this.descricao = descricao;
        this.velocidadeTerrestreKmH = velocidadeTerrestreKmH;
        this.fatorRisco = fatorRisco;
        this.fatorFrete = fatorFrete;
    }

    /** Velocidade neste trecho, em km/h. */
    public double velocidadeKmH(Conexao trecho) {
        return trecho.getTipo() == Conexao.TipoDeRota.MARITIMA ? VELOCIDADE_MARITIMA_KMH : velocidadeTerrestreKmH;
    }

    /** Chance de ataque por hora neste trecho, sem escolta. */
    public double riscoPorHora(Conexao trecho) {
        return trecho.getPerigo() * RISCO_POR_HORA_POR_PERIGO * fatorRisco;
    }

    public String getDescricao() { return descricao; }
    public double getVelocidadeTerrestreKmH() { return velocidadeTerrestreKmH; }
    public double getFatorRisco() { return fatorRisco; }
    public double getFatorFrete() { return fatorFrete; }

    @Override
    public String toString() {
        return descricao;
    }
}
