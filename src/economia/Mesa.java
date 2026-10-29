package economia;

/**
 * Uma mesa de jogo com relógio próprio (dia + hora). Várias mesas dividem o mesmo mundo:
 * o mercado anda junto com a mesa ativa mais à frente, e uma mesa atrasada lê o passado
 * nos snapshots diários. Ver docs/Sistema de Economia/05.
 */
public class Mesa {

    public enum Status { ATIVA, ARQUIVADA }

    private final int id;
    private String nome;
    private int dia;
    private int hora;
    private Status status;
    private final Integer mestreId;

    public Mesa(int id, String nome, int dia, int hora, Status status, Integer mestreId) {
        this.id = id;
        this.nome = nome != null && !nome.isBlank() ? nome.trim() : "Mesa " + id;
        this.dia = Math.max(1, dia);
        this.hora = Math.max(0, Math.min(23, hora));
        this.status = status != null ? status : Status.ATIVA;
        this.mestreId = mestreId;
    }

    /** Horas corridas desde o dia 1, 0h: compara relógios de mesa, personagem e mundo. */
    public static int momento(int dia, int hora) {
        return dia * 24 + hora;
    }

    public int momento() { return momento(dia, hora); }

    /** Anda o relógio da mesa (o tempo da mesa nunca volta). */
    public void avancar(int horas) {
        int novo = momento() + Math.max(0, horas);
        dia = novo / 24;
        hora = novo % 24;
    }

    /**
     * Troca de mesa só para frente: o personagem entra se o relógio da mesa for igual ou maior
     * que o dele (o momento mais à frente em que já esteve). Para trás seria viagem no tempo.
     */
    public boolean aceitaPersonagemEm(int momentoPersonagem) {
        return momento() >= momentoPersonagem;
    }

    /** Rótulo usado no livro-caixa para operações desta mesa: "[Mesa Norte · dia 8]". */
    public String rotulo() {
        return "[Mesa " + nome + " · dia " + dia + "]";
    }

    public boolean isAtiva() { return status == Status.ATIVA; }
    public int getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { if (nome != null && !nome.isBlank()) this.nome = nome.trim(); }
    public int getDia() { return dia; }
    public int getHora() { return hora; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { if (status != null) this.status = status; }
    public Integer getMestreId() { return mestreId; }
}
