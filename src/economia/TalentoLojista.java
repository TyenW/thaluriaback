package economia;

/**
 * Representa os talentos de especialização comercial adquiridos por mercadores ou jogadores lojistas em Thaluria.
 */
public enum TalentoLojista {
    BARGANHA_AVANCADA("Barganha Avançada", "Desconto de 20% nas compras e taxas da Guilda Comercial"),
    MESTRE_ARTESAO("Mestre Artesão", "Chance de gerar itens Obra-Prima vendidos por 3x o valor base"),
    PRODUCAO_EM_MASSA("Produção em Massa", "Reduz o tempo de craft em 25% (mínimo 1 dia), com -1 grau na QualidadeCraft"),
    RECICLAGEM_DE_SUCATA("Reciclagem de Sucata", "Recupera 30% das matérias-primas ao desmontar itens encalhados na prateleira");

    private final String nome;
    private final String descricao;

    TalentoLojista(String nome, String descricao) {
        this.nome = nome;
        this.descricao = descricao;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    @Override
    public String toString() {
        return nome;
    }
}
