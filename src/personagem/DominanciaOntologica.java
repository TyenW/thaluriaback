package personagem;

/**
 * Classificação Ontológica da Raça (Ficha de Cadastro de Raça).
 */
public enum DominanciaOntologica {
    FISICA_MATERIA("Dominância Física (Matéria)"),
    ETEREA_IMATERIAL("Dominância Etérea (Imaterial)"),
    HIBRIDA_EQUILIBRADA("Híbrida / Equilibrada");

    private final String descricao;

    DominanciaOntologica(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
