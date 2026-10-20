package economia;

import geografia.Cidade;
import geografia.Conexao;
import personagem.Ficha;
import java.util.Objects;

/**
 * Representa uma missão de escolta comercial ou resgate de carga extraviada em Thaluria.
 * Integra-se aos quadros de avisos municipais, permitindo que aventureiros assumam
 * os contratos antes do encerramento do prazo, momento em que o sistema resolve o destino
 * da caravana via contratação de NPCs ou expedições de milícias locais.
 */
public class MissaoComercial {

    public enum TipoMissao {
        ESCOLTA("Escolta Armada"),
        RESGATE_CARGA("Resgate de Carga Extraviada");

        private final String descricao;

        TipoMissao(String descricao) {
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

    public enum StatusMissao {
        DISPONIVEL_JOGADORES("Disponível no Quadro de Avisos"),
        ACEITA_POR_JOGADOR("Aceita por Aventureiro"),
        ASSUMIDA_POR_NPCS("Assumida por Mercenários NPCs"),
        EXPIRADA_SEM_ESCOLTA("Expirada (Partiu Sem Proteção)"),
        CONCLUIDA_SUCESSO("Concluída com Sucesso"),
        FRACASSADA("Fracassada");

        private final String descricao;

        StatusMissao(String descricao) {
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

    private static int sequencialId = 100;

    private final int id;
    private final TipoMissao tipo;
    private final Cidade cidadeDisponivel;
    private final Caravana caravanaAlvo;
    private final long recompensaEmLatao;
    private int horasRestantesPrazo;
    private StatusMissao status;
    private String nomeJogador;
    private Carteira carteiraJogador;
    private Ficha fichaJogador;

    public MissaoComercial(TipoMissao tipo, Cidade cidadeDisponivel, Caravana caravanaAlvo,
                           long recompensaEmLatao, int prazoHoras) {
        this.id = sequencialId++;
        this.tipo = Objects.requireNonNull(tipo, "Tipo de missão não pode ser nulo");
        this.cidadeDisponivel = Objects.requireNonNull(cidadeDisponivel, "Cidade da missão não pode ser nula");
        this.caravanaAlvo = Objects.requireNonNull(caravanaAlvo, "Caravana alvo não pode ser nula");
        this.recompensaEmLatao = Math.max(0L, recompensaEmLatao);
        this.horasRestantesPrazo = Math.max(1, prazoHoras);
        this.status = StatusMissao.DISPONIVEL_JOGADORES;
    }

    public boolean aceitarPorJogador(String nomeJogador, int dia, int hora) {
        return aceitarPorJogador(nomeJogador, (Carteira) null, dia, hora);
    }

    public boolean aceitarPorJogador(Ficha jogador, int dia, int hora) {
        return aceitarPorJogador("Aventureiro", jogador, dia, hora);
    }

    public boolean aceitarPorJogador(String nomeJogador, Ficha jogador, int dia, int hora) {
        this.fichaJogador = jogador;
        Carteira cart = (jogador != null) ? jogador.getCarteira() : null;
        return aceitarPorJogador(nomeJogador, cart, dia, hora);
    }

    public boolean aceitarPorJogador(String nomeJogador, Carteira carteiraJogador, int dia, int hora) {
        if (status != StatusMissao.DISPONIVEL_JOGADORES) return false;
        this.status = StatusMissao.ACEITA_POR_JOGADOR;
        this.nomeJogador = (nomeJogador != null && !nomeJogador.trim().isEmpty()) ? nomeJogador : "Aventureiro";
        this.carteiraJogador = carteiraJogador;

        if (tipo == TipoMissao.ESCOLTA) {
            caravanaAlvo.iniciarViagem(Caravana.TipoProtecao.ESCOLTADA_JOGADOR, dia, hora);
        }
        return true;
    }

    public boolean concluirMissaoJogador(int dia, int hora) {
        if (status != StatusMissao.ACEITA_POR_JOGADOR) return false;
        this.status = StatusMissao.CONCLUIDA_SUCESSO;

        // 1. Lastro Financeiro Absoluto:
        // A recompensa deve sair do caixa da loja contratante (loja de destino da carga encomendada).
        Lojas contratante = caravanaAlvo.getLojaDestino();
        long valorPagoLatao = recompensaEmLatao;

        if (contratante != null && contratante.getCaixa() != null) {
            if (contratante.getCaixa().temSaldoEmLatao(recompensaEmLatao)) {
                contratante.getCaixa().pagarLatao(recompensaEmLatao);
            } else {
                // Se o caixa não cobrir o total, paga o saldo remanescente disponível
                valorPagoLatao = contratante.getCaixa().getValorTotalEmLatao();
                contratante.getCaixa().pagarLatao(valorPagoLatao);
            }
        }

        // 2. Credita na carteira do jogador
        if (carteiraJogador != null && valorPagoLatao > 0) {
            carteiraJogador.receberLatao(valorPagoLatao);
        }

        // 3. Deslocamento Geográfico do Jogador:
        // Ao escoltar uma caravana da Cidade A até a Cidade B, o aventureiro chega à cidade de destino
        if (tipo == TipoMissao.ESCOLTA) {
            if (fichaJogador != null && caravanaAlvo.getCidadeDestino() != null) {
                fichaJogador.setCidadeAtual(caravanaAlvo.getCidadeDestino());
            }
        }

        // 4. Registro Contábil e Efeitos no Grafo
        if (tipo == TipoMissao.RESGATE_CARGA) {
            Conexao aresta = caravanaAlvo.getArestaAtual();
            if (aresta != null) {
                aresta.ajustarPerigo(-1); // Resgate bem-sucedido pacifica o trecho
            }
            caravanaAlvo.concluirEntrega(dia, hora);
            if (contratante != null) {
                contratante.registrarMovimentacao(dia, hora,
                        RegistroMovimentacao.TipoOperacao.DESPESA_ESCOLTA,
                        caravanaAlvo.getCarga().getNome(), caravanaAlvo.getCarga().getQuantidade(),
                        valorPagoLatao, "Recompensa de resgate paga a " + nomeJogador);
            }
        } else if (tipo == TipoMissao.ESCOLTA) {
            if (contratante != null) {
                contratante.registrarMovimentacao(dia, hora,
                        RegistroMovimentacao.TipoOperacao.DESPESA_ESCOLTA,
                        caravanaAlvo.getCarga().getNome(), caravanaAlvo.getCarga().getQuantidade(),
                        valorPagoLatao, "Escolta honrada pelo aventureiro " + nomeJogador);
            }
        }
        return true;
    }

    public void reduzirPrazo(int horas) {
        if (status == StatusMissao.DISPONIVEL_JOGADORES) {
            this.horasRestantesPrazo -= horas;
        }
    }

    public boolean expirou() {
        return status == StatusMissao.DISPONIVEL_JOGADORES && horasRestantesPrazo <= 0;
    }

    // Getters e Setters
    public int getId() { return id; }
    public TipoMissao getTipo() { return tipo; }
    public Cidade getCidadeDisponivel() { return cidadeDisponivel; }
    public Caravana getCaravanaAlvo() { return caravanaAlvo; }
    public long getRecompensaEmLatao() { return recompensaEmLatao; }
    public double getRecompensaEmOuro() { return Carteira.lataoParaOuro(recompensaEmLatao); }
    public int getHorasRestantesPrazo() { return horasRestantesPrazo; }
    public StatusMissao getStatus() { return status; }
    public void setStatus(StatusMissao status) { this.status = status; }
    public String getNomeJogador() { return nomeJogador; }
    public Carteira getCarteiraJogador() { return carteiraJogador; }
    public void setCarteiraJogador(Carteira carteiraJogador) { this.carteiraJogador = carteiraJogador; }
    public Ficha getFichaJogador() { return fichaJogador; }
    public void setFichaJogador(Ficha fichaJogador) { this.fichaJogador = fichaJogador; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MissaoComercial that = (MissaoComercial) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        String prazo = (status == StatusMissao.DISPONIVEL_JOGADORES) ? (" | Prazo: " + horasRestantesPrazo + "h") : "";
        return String.format("Missão #%d [%s] em %s | Carga: %s (x%d) | Recompensa: %s | Status: %s%s | Pos: %s",
                id, tipo.getDescricao(), cidadeDisponivel.getNome(),
                caravanaAlvo.getCarga().getNome(), caravanaAlvo.getCarga().getQuantidade(),
                Carteira.formatarLatao(recompensaEmLatao), status.getDescricao(), prazo,
                caravanaAlvo.descreverPosicaoNoGrafo());
    }
}
