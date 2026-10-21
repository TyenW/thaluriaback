package economia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ficha técnica especializada para grimórios e artefatos de feitiçaria (TipoItem.MAGICO).
 */
public class FichaGrimorio {

    private int nivelGrimorio; // 1 a 5
    private int capacidadeMaximaCirculos;
    private final List<CirculoMagico> circulosDesenhados = new ArrayList<>();

    public FichaGrimorio(int nivelGrimorio, int capacidadeMaximaCirculos) {
        this.nivelGrimorio = Math.max(1, Math.min(5, nivelGrimorio));
        this.capacidadeMaximaCirculos = Math.max(1, capacidadeMaximaCirculos);
    }

    public void aplicarModificadorQualidade(QualidadeCraft qualidade) {
        if (qualidade == null) return;
        this.capacidadeMaximaCirculos += qualidade.getBonusPaginasGrimorio();
    }

    /**
     * Tenta inscrever um novo círculo mágico nas páginas do grimório.
     * Rejeita se o nível do círculo for superior ao suporte do livro ou se atingiu o limite de páginas.
     */
    public boolean inscreverCirculo(CirculoMagico circulo) {
        if (circulo == null) return false;
        if (circulo.getNivelCirculo() > this.nivelGrimorio) {
            return false; // As páginas arcanas não suportam a densidade desta magia
        }
        if (circulosDesenhados.size() >= this.capacidadeMaximaCirculos) {
            return false; // Grimório cheio
        }
        if (!circulosDesenhados.contains(circulo)) {
            circulosDesenhados.add(circulo);
            return true;
        }
        return false;
    }

    public int getNivelGrimorio() {
        return nivelGrimorio;
    }

    public void setNivelGrimorio(int nivelGrimorio) {
        this.nivelGrimorio = Math.max(1, Math.min(5, nivelGrimorio));
    }

    public int getCapacidadeMaximaCirculos() {
        return capacidadeMaximaCirculos;
    }

    public void setCapacidadeMaximaCirculos(int capacidadeMaximaCirculos) {
        this.capacidadeMaximaCirculos = Math.max(1, capacidadeMaximaCirculos);
    }

    public List<CirculoMagico> getCirculosDesenhados() {
        return Collections.unmodifiableList(circulosDesenhados);
    }

    @Override
    public String toString() {
        return String.format("Grimório Nv.%d [Círculos Inscritos: %d/%d]",
                nivelGrimorio, circulosDesenhados.size(), capacidadeMaximaCirculos);
    }
}
