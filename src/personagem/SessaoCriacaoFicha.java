package personagem;

import java.util.*;

/**
 * Sessão de Criação de Ficha em Tempo Real (Jogador vs Mestre - Web-Ready).
 * Mantém um objeto observável em memória (RascunhoFichaEmAndamento) onde cada etapa
 * é refletida imediatamente para o painel do Mestre, permitindo dicas, aprovação e rerolls assistidos.
 */
public class SessaoCriacaoFicha {

    public enum EtapaCriacao {
        SORTEIO_RACAS("1: Sorteio das 3 Raças"),
        ESCOLHA_RACA("2: Escolha da Raça"),
        ROLAGEM_LIMITES_INICIAIS("3: Rolagem dos Limites Iniciais"),
        ALOCACAO_PERICIAS("4: Alocação de Perícias"),
        AGUARDANDO_APROVACAO("5: Aguardando Aprovação do Mestre"),
        FINALIZADO("6: Finalizado e Integrado ao Mundo");

        private final String descricao;
        EtapaCriacao(String descricao) { this.descricao = descricao; }
        public String getDescricao() { return descricao; }
    }

    public static class RascunhoFichaEmAndamento {
        private final String idJogador;
        private final String nomePersonagem;
        private EtapaCriacao etapaAtual;

        private SorteadorRacaCriacao.OpcoesSorteio opcoesRacas;
        private Raca racaEscolhida;
        private Map<Atributo, Integer> atributosRolados = new EnumMap<>(Atributo.class);
        private final Map<Pericia, int[]> periciasAlocadas = new EnumMap<>(Pericia.class); // {grau, maestria}

        private boolean aprovadoPeloMestre = false;
        private String feedbackMestre = "";
        private int rerollsAssistidosDisponiveis = 0;

        public RascunhoFichaEmAndamento(String idJogador, String nomePersonagem) {
            this.idJogador = idJogador;
            this.nomePersonagem = nomePersonagem;
            this.etapaAtual = EtapaCriacao.SORTEIO_RACAS;
        }

        public String getIdJogador() { return idJogador; }
        public String getNomePersonagem() { return nomePersonagem; }
        public EtapaCriacao getEtapaAtual() { return etapaAtual; }
        public SorteadorRacaCriacao.OpcoesSorteio getOpcoesRacas() { return opcoesRacas; }
        public Raca getRacaEscolhida() { return racaEscolhida; }
        public Map<Atributo, Integer> getAtributosRolados() { return Collections.unmodifiableMap(atributosRolados); }
        public Map<Pericia, int[]> getPericiasAlocadas() { return Collections.unmodifiableMap(periciasAlocadas); }
        public boolean isAprovadoPeloMestre() { return aprovadoPeloMestre; }
        public String getFeedbackMestre() { return feedbackMestre; }
        public int getRerollsAssistidosDisponiveis() { return rerollsAssistidosDisponiveis; }
    }

    private final RascunhoFichaEmAndamento rascunho;

    public SessaoCriacaoFicha(String idJogador, String nomePersonagem) {
        this.rascunho = new RascunhoFichaEmAndamento(idJogador, nomePersonagem);
    }

    public RascunhoFichaEmAndamento getRascunho() {
        return rascunho;
    }

    /**
     * Etapa 1: Sorteia as 3 raças disponíveis (Humano + 2 sorteadas com Pity).
     */
    public SorteadorRacaCriacao.OpcoesSorteio executarSorteioRacas(int fatorSorte, boolean boostCampanha, Random r) {
        rascunho.opcoesRacas = SorteadorRacaCriacao.sortearOpcoesCriacao(fatorSorte, boostCampanha, r);
        rascunho.etapaAtual = EtapaCriacao.ESCOLHA_RACA;
        return rascunho.opcoesRacas;
    }

    /**
     * Etapa 2: Jogador escolhe 1 das 3 opções sorteadas.
     */
    public boolean escolherRaca(Raca raca) {
        if (rascunho.opcoesRacas == null) return false;
        if (!rascunho.opcoesRacas.getListaOpcoes().contains(raca)) {
            return false; // Raça não está entre as opções sorteadas
        }
        rascunho.racaEscolhida = raca;
        rascunho.etapaAtual = EtapaCriacao.ROLAGEM_LIMITES_INICIAIS;
        return true;
    }

    /**
     * Etapa 3: Rola os 6 atributos iniciais dentro do intervalo [min, max] da raça escolhida.
     */
    public Map<Atributo, Integer> rolarAtributosIniciais(Random r) {
        if (rascunho.racaEscolhida == null) return Collections.emptyMap();
        rascunho.atributosRolados = SorteadorRacaCriacao.rolarAtributosIniciais(rascunho.racaEscolhida, r);
        rascunho.etapaAtual = EtapaCriacao.ALOCACAO_PERICIAS;
        return rascunho.atributosRolados;
    }

    /**
     * Permite que o Mestre conceda um reroll assistido de atributos para o jogador na hora.
     */
    public boolean concederERealizarRerollAssistido(Random r) {
        if (rascunho.racaEscolhida == null) return false;
        rascunho.rerollsAssistidosDisponiveis++;
        rascunho.atributosRolados = SorteadorRacaCriacao.rolarAtributosIniciais(rascunho.racaEscolhida, r);
        rascunho.feedbackMestre = "Reroll assistido concedido e executado pelo Mestre.";
        return true;
    }

    /**
     * Etapa 4: Aloca grau e maestria em uma perícia respeitando a trava do atributo base.
     */
    public boolean alocarPericia(Pericia p, int grau, int maestria) {
        if (p == null || !rascunho.atributosRolados.containsKey(p.getAtributoBase())) return false;
        int valorAtributo = rascunho.atributosRolados.get(p.getAtributoBase());
        int grauAtributo = valorAtributo / 10;

        if (!p.validarTravaAtributo(grau, grauAtributo) || !Pericia.validarMaestria(maestria)) {
            return false;
        }

        rascunho.periciasAlocadas.put(p, new int[]{grau, maestria});
        return true;
    }

    /**
     * Painel do Mestre: Envia mensagem ou dica tática em tempo real para a criação do jogador.
     */
    public void enviarDicaOuObservacaoMestre(String feedback) {
        rascunho.feedbackMestre = feedback != null ? feedback : "";
    }

    /**
     * Painel do Mestre: Aprova a ficha para finalização.
     */
    public void aprovarPeloMestre() {
        rascunho.aprovadoPeloMestre = true;
        rascunho.etapaAtual = EtapaCriacao.AGUARDANDO_APROVACAO;
    }

    /**
     * Etapa 5: Finaliza e materializa o objeto Ficha definitivo.
     */
    public Ficha finalizarFicha() {
        if (rascunho.racaEscolhida == null || rascunho.atributosRolados.size() < 6) {
            throw new IllegalStateException("Criação incompleta: raça ou atributos pendentes");
        }

        Ficha ficha = new Ficha(
                rascunho.atributosRolados.get(Atributo.VIG),
                rascunho.atributosRolados.get(Atributo.FOR),
                rascunho.atributosRolados.get(Atributo.DES),
                rascunho.atributosRolados.get(Atributo.MEN),
                rascunho.atributosRolados.get(Atributo.ESP),
                rascunho.atributosRolados.get(Atributo.VON),
                rascunho.racaEscolhida
        );

        for (Map.Entry<Pericia, int[]> e : rascunho.periciasAlocadas.entrySet()) {
            ficha.setPericia(e.getKey(), e.getValue()[0], e.getValue()[1]);
        }

        rascunho.etapaAtual = EtapaCriacao.FINALIZADO;
        return ficha;
    }
}
