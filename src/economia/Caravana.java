package economia;

import geografia.Cidade;
import geografia.Conexao;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Modela uma expedição comercial de transporte de mercadorias no grafo territorial de Thaluria.
 * Acompanha o deslocamento quilômetro a quilômetro entre as conexões, calcula a velocidade
 * conforme o tipo de trajeto (terrestre ou marítimo) e avalia o risco de emboscadas e extravio.
 */
public class Caravana {

    public enum StatusCaravana {
        AGUARDANDO_ESCOLTA("Aguardando Escolta"),
        EM_TRANSITO("Em Trânsito"),
        EXTRAVIADA("Extraviada (Ataque no Grafo)"),
        ENTREGUE("Entregue com Sucesso"),
        PERDIDA_DEFINITIVAMENTE("Perdida Definitivamente");

        private final String descricao;

        StatusCaravana(String descricao) {
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

    public enum TipoProtecao {
        DESPROTEGIDA("Desprotegida"),
        ESCOLTADA_NPC("Escolta de Mercenários NPCs (-75% risco)"),
        ESCOLTADA_JOGADOR("Escolta por Jogadores (100% Segurança)");

        private final String descricao;

        TipoProtecao(String descricao) {
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

    private static int sequencialId = 1;

    private final int id;
    private final Lojas lojaOrigem;
    private final Lojas lojaDestino;
    private final Item carga;
    private final RotaComercial rota;

    private StatusCaravana status;
    private TipoProtecao protecao;

    private int indiceArestaAtual = 0;
    private double kmNaArestaAtual = 0;
    private int diaUltimoEvento;
    private int horaUltimoEvento;

    public Caravana(Lojas lojaOrigem, Lojas lojaDestino, Item carga, RotaComercial rota, int dia, int hora) {
        this.id = sequencialId++;
        this.lojaOrigem = Objects.requireNonNull(lojaOrigem, "Loja de origem não pode ser nula");
        this.lojaDestino = Objects.requireNonNull(lojaDestino, "Loja de destino não pode ser nula");
        this.carga = Objects.requireNonNull(carga, "Carga não pode ser nula");
        this.rota = Objects.requireNonNull(rota, "Rota comercial não pode ser nula");
        this.status = StatusCaravana.AGUARDANDO_ESCOLTA;
        this.protecao = TipoProtecao.DESPROTEGIDA;
        this.diaUltimoEvento = dia;
        this.horaUltimoEvento = hora;
    }

    public void iniciarViagem(TipoProtecao protecao, int dia, int hora) {
        this.protecao = (protecao != null) ? protecao : TipoProtecao.DESPROTEGIDA;
        this.status = StatusCaravana.EM_TRANSITO;
        this.diaUltimoEvento = dia;
        this.horaUltimoEvento = hora;
    }

    /**
     * Avança a caravana pelo trajeto geográfico com base nas horas decorridas.
     *
     * @return true se a caravana sofreu uma emboscada e foi extraviada durante este avanço.
     */
    public boolean avancarHoras(int horas, int diaAtual, int horaAtual, Random random) {
        if (status != StatusCaravana.EM_TRANSITO) return false;

        List<Conexao> arestas = rota.getArestas();
        if (arestas.isEmpty()) {
            concluirEntrega(diaAtual, horaAtual);
            return false;
        }

        double horasRestantes = horas;

        while (horasRestantes > 0 && indiceArestaAtual < arestas.size()) {
            Conexao aresta = arestas.get(indiceArestaAtual);
            double velocidadeKmH = (aresta.getTipo() == Conexao.TipoDeRota.MARITIMA) ? 14.0 : 6.0;
            double kmRestantesTrecho = aresta.getDistanciaKm() - kmNaArestaAtual;
            double tempoParaConcluirTrecho = kmRestantesTrecho / velocidadeKmH;

            double tempoGasto = Math.min(horasRestantes, tempoParaConcluirTrecho);
            kmNaArestaAtual += tempoGasto * velocidadeKmH;
            horasRestantes -= tempoGasto;

            // Se o jogador está escoltando, a segurança é absoluta (100%)
            if (protecao != TipoProtecao.ESCOLTADA_JOGADOR) {
                double riscoHora = aresta.getPerigo() * 0.012;
                if (protecao == TipoProtecao.ESCOLTADA_NPC) {
                    riscoHora *= 0.25; // Escolta de NPCs reduz o perigo em 75%
                }
                double chanceAtaque = 1.0 - Math.pow(1.0 - riscoHora, Math.max(0.01, tempoGasto));
                if (random != null && random.nextDouble() < chanceAtaque) {
                    this.status = StatusCaravana.EXTRAVIADA;
                    this.diaUltimoEvento = diaAtual;
                    this.horaUltimoEvento = horaAtual;
                    return true;
                }
            }

            if (kmNaArestaAtual >= aresta.getDistanciaKm() - 0.001) {
                indiceArestaAtual++;
                kmNaArestaAtual = 0;
            }
        }

        if (indiceArestaAtual >= arestas.size()) {
            concluirEntrega(diaAtual, horaAtual);
        }
        return false;
    }

    public void concluirEntrega(int dia, int hora) {
        this.status = StatusCaravana.ENTREGUE;
        this.diaUltimoEvento = dia;
        this.horaUltimoEvento = hora;
        lojaDestino.adicionarItem(carga);
        lojaDestino.registrarMovimentacao(dia, hora,
                RegistroMovimentacao.TipoOperacao.COMPRA_INTERMUNICIPAL,
                carga.getNome(), carga.getQuantidade(), 0L,
                "Caravana #" + id + " entregue com sucesso vinda de " + lojaOrigem.getNome());
    }

    public Conexao getArestaAtual() {
        List<Conexao> arestas = rota.getArestas();
        if (arestas.isEmpty()) return null;
        return arestas.get(Math.min(indiceArestaAtual, arestas.size() - 1));
    }

    public Cidade getCidadeMaisProxima() {
        Conexao aresta = getArestaAtual();
        if (aresta == null) return lojaOrigem.getCidadeSede();
        List<Cidade> cidades = rota.getCaminhoCidades();
        Cidade origemTrecho = cidades.get(Math.min(indiceArestaAtual, cidades.size() - 1));
        Cidade destinoTrecho = aresta.outraPonta(origemTrecho);
        return (kmNaArestaAtual < aresta.getDistanciaKm() / 2.0) ? origemTrecho : destinoTrecho;
    }

    public String descreverPosicaoNoGrafo() {
        if (status == StatusCaravana.ENTREGUE) {
            String destinoNome = (lojaDestino.getCidadeSede() != null) ? lojaDestino.getCidadeSede().getNome() : lojaDestino.getNome();
            return "Entregue no destino (" + destinoNome + ")";
        }
        if (status == StatusCaravana.AGUARDANDO_ESCOLTA) {
            String origemNome = (lojaOrigem.getCidadeSede() != null) ? lojaOrigem.getCidadeSede().getNome() : lojaOrigem.getNome();
            return "Aguardando partida no porto/entreposto de " + origemNome;
        }

        Conexao aresta = getArestaAtual();
        if (aresta == null) {
            return (lojaOrigem.getCidadeSede() != null) ? lojaOrigem.getCidadeSede().getNome() : "Desconhecido";
        }
        List<Cidade> cidades = rota.getCaminhoCidades();
        Cidade c1 = cidades.get(Math.min(indiceArestaAtual, cidades.size() - 1));
        Cidade c2 = aresta.outraPonta(c1);
        return String.format("[%s <-> %s] no Km %.1f/%dkm (Perigo %d/10 | Dia %d, %02dh)",
                c1.getNome(), c2.getNome(), kmNaArestaAtual, aresta.getDistanciaKm(),
                aresta.getPerigo(), diaUltimoEvento, horaUltimoEvento);
    }

    // Getters e Setters
    public int getId() { return id; }
    public Lojas getLojaOrigem() { return lojaOrigem; }
    public Lojas getLojaDestino() { return lojaDestino; }
    public Cidade getCidadeOrigem() { return (lojaOrigem != null) ? lojaOrigem.getCidadeSede() : null; }
    public Cidade getCidadeDestino() { return (lojaDestino != null) ? lojaDestino.getCidadeSede() : null; }
    public Item getCarga() { return carga; }
    public RotaComercial getRota() { return rota; }
    public StatusCaravana getStatus() { return status; }
    public void setStatus(StatusCaravana status) { this.status = status; }
    public TipoProtecao getProtecao() { return protecao; }
    public void setProtecao(TipoProtecao protecao) { this.protecao = protecao; }
    public int getIndiceArestaAtual() { return indiceArestaAtual; }
    public double getKmNaArestaAtual() { return kmNaArestaAtual; }
    public int getDiaUltimoEvento() { return diaUltimoEvento; }
    public int getHoraUltimoEvento() { return horaUltimoEvento; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Caravana caravana = (Caravana) o;
        return id == caravana.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Caravana #%d [%s -> %s] | Carga: %s (x%d) | Status: %s (%s) | %s",
                id,
                (lojaOrigem.getCidadeSede() != null ? lojaOrigem.getCidadeSede().getNome() : lojaOrigem.getNome()),
                (lojaDestino.getCidadeSede() != null ? lojaDestino.getCidadeSede().getNome() : lojaDestino.getNome()),
                carga.getNome(), carga.getQuantidade(),
                status.getDescricao(), protecao.getDescricao(),
                descreverPosicaoNoGrafo());
    }
}
