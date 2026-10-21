package economia;

import java.util.Objects;

/**
 * Representa um círculo ou fórmula mágica inscrita nas páginas de um grimório arcano.
 */
public class CirculoMagico {

    private final int id;
    private final String nomeFeitico;
    private final int nivelCirculo; // 1 a 5
    private final int custoMana;
    private final String diagramaRunico;
    private final String efeitoMagico;

    public CirculoMagico(int id, String nomeFeitico, int nivelCirculo, int custoMana,
                         String diagramaRunico, String efeitoMagico) {
        this.id = id;
        this.nomeFeitico = (nomeFeitico != null && !nomeFeitico.trim().isEmpty()) ? nomeFeitico : "Círculo Desconhecido";
        this.nivelCirculo = Math.max(1, Math.min(5, nivelCirculo));
        this.custoMana = Math.max(0, custoMana);
        this.diagramaRunico = (diagramaRunico != null) ? diagramaRunico : "Runa Básica";
        this.efeitoMagico = (efeitoMagico != null) ? efeitoMagico : "";
    }

    public int getId() {
        return id;
    }

    public String getNomeFeitico() {
        return nomeFeitico;
    }

    public int getNivelCirculo() {
        return nivelCirculo;
    }

    public int getCustoMana() {
        return custoMana;
    }

    public String getDiagramaRunico() {
        return diagramaRunico;
    }

    public String getEfeitoMagico() {
        return efeitoMagico;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CirculoMagico that = (CirculoMagico) o;
        return id == that.id && Objects.equals(nomeFeitico, that.nomeFeitico);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, nomeFeitico);
    }

    @Override
    public String toString() {
        return String.format("%s (Círculo Nv.%d, Custo: %d Mana) - %s",
                nomeFeitico, nivelCirculo, custoMana, efeitoMagico);
    }
}
