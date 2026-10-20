package geografia;

import java.util.Objects;

/**
 * Representa uma rota de viagem (terrestre ou marítima) ligando duas cidades em
 * Thaluria.
 */
public class Conexao {

    public enum TipoDeRota {
        TERRESTRE("Terrestre"),
        MARITIMA("Marítima");

        private final String nomeFormatado;

        TipoDeRota(String nomeFormatado) {
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

    private final Cidade origem;
    private final Cidade destino;
    private final int distanciaKm;
    private int perigo; // Escala de 1 a 10
    private final TipoDeRota tipo;
    private boolean bloqueada;

    public Conexao(Cidade origem, Cidade destino, int distanciaKm, int perigo, TipoDeRota tipo) {
        if (origem == null || destino == null) {
            throw new IllegalArgumentException("Cidades de origem e destino não podem ser nulas");
        }
        if (origem.equals(destino)) {
            throw new IllegalArgumentException("Uma rota não pode conectar uma cidade a ela mesma");
        }
        if (perigo < 1 || perigo > 10) {
            throw new IllegalArgumentException("O nível de perigo da rota deve estar entre 1 e 10");
        }
        if (distanciaKm <= 0) {
            throw new IllegalArgumentException("A distância em quilômetros deve ser positiva");
        }
        if (tipo == TipoDeRota.MARITIMA && !(origem.temPorto() && destino.temPorto())) {
            throw new IllegalArgumentException("Rotas marítimas exigem infraestrutura portuária em ambas as cidades");
        }

        this.origem = origem;
        this.destino = destino;
        this.distanciaKm = distanciaKm;
        this.perigo = perigo;
        this.tipo = (tipo != null) ? tipo : TipoDeRota.TERRESTRE;
        this.bloqueada = false;

        origem.adicionarConexao(this);
        destino.adicionarConexao(this);
    }

    public void ajustarPerigo(int delta) {
        this.perigo = Math.max(1, Math.min(10, this.perigo + delta));
    }

    public void bloquear() {
        this.bloqueada = true;
    }

    public void desbloquear() {
        this.bloqueada = false;
    }

    /**
     * Retorna a cidade oposta na conexão dada uma das pontas.
     */
    public Cidade outraPonta(Cidade c) {
        if (c == null) {
            throw new IllegalArgumentException("Cidade de referência não pode ser nula");
        }
        if (c.equals(origem))
            return destino;
        if (c.equals(destino))
            return origem;
        throw new IllegalArgumentException("A cidade '" + c.getNome() + "' não pertence a esta conexão");
    }

    public Cidade getOrigem() {
        return origem;
    }

    public Cidade getDestino() {
        return destino;
    }

    public int getDistanciaKm() {
        return distanciaKm;
    }

    public int getPerigo() {
        return perigo;
    }

    public TipoDeRota getTipo() {
        return tipo;
    }

    public boolean isBloqueada() {
        return bloqueada;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Conexao conexao = (Conexao) o;
        return (Objects.equals(origem, conexao.origem) && Objects.equals(destino, conexao.destino)) ||
                (Objects.equals(origem, conexao.destino) && Objects.equals(destino, conexao.origem));
    }

    @Override
    public int hashCode() {
        return Objects.hash(origem, destino) + Objects.hash(destino, origem);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s <---> %s (%d km, Perigo: %d/10%s)",
                tipo, origem.getNome(), destino.getNome(), distanciaKm, perigo, bloqueada ? " [BLOQUEADA]" : "");
    }
}
