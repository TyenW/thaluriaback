package personagem;

/**
 * Escopo de Acesso da Raça no ecossistema (Jogador vs Mestre vs Bestiário).
 */
public enum TipoAcessoRaca {
    JOGAVEL("Jogável (Disponível para criação de fichas de aventureiros)"),
    NPC_CIVIL("NPC Civil (Restrito ao mundo mundano)"),
    MONSTRO_BESTIARIO("Monstro / Bestiário (Exclusivo para encontros e combate)"),
    RESTRITO_MESTRE("Restrito ao Mestre (Linhagens especiais / Deuses / Chefes)");

    private final String descricao;

    TipoAcessoRaca(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
