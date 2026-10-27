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
        private String nomePersonagem;
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
        public void setNomePersonagem(String nomePersonagem) { this.nomePersonagem = nomePersonagem; }
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
    private boolean impulsoDraconico = false;
    private int limiteMaximoFichas = 2;
    private int fatorSortePity = 0;

    public SessaoCriacaoFicha(String idJogador, String nomePersonagem) {
        this.rascunho = new RascunhoFichaEmAndamento(idJogador, nomePersonagem);
    }

    public SessaoCriacaoFicha(String idJogador, String nomePersonagem, int limiteMaximoFichas, int fatorSortePity) {
        this(idJogador, nomePersonagem);
        this.limiteMaximoFichas = limiteMaximoFichas;
        this.fatorSortePity = fatorSortePity;
    }

    public int getLimiteMaximoFichas() {
        return limiteMaximoFichas;
    }

    public int getFatorSortePity() {
        return fatorSortePity;
    }

    public String getNomePersonagem() {
        return rascunho.getNomePersonagem();
    }

    public void setNomePersonagem(String nome) {
        if (nome != null && !nome.isBlank()) {
            rascunho.setNomePersonagem(nome.trim());
        }
    }

    public SorteadorRacaCriacao.OpcoesSorteio rerolarOpcoesRacas(int fatorSorte, Random r) {
        rascunho.opcoesRacas = SorteadorRacaCriacao.sortearOpcoesCriacao(fatorSorte, false, impulsoDraconico, r);
        rascunho.racaEscolhida = null;
        rascunho.atributosRolados.clear();
        rascunho.etapaAtual = EtapaCriacao.ESCOLHA_RACA;
        this.rolagensRealizadas = 0;
        rascunho.feedbackMestre = "O Mestre concedeu uma nova roletagem de raças!";
        return rascunho.opcoesRacas;
    }

    public void restaurarEstado(
            String nomePersonagem,
            EtapaCriacao etapa,
            SorteadorRacaCriacao.OpcoesSorteio opcoes,
            Raca racaEscolhida,
            Map<Atributo, Integer> atributos,
            boolean impulsoDraconico,
            int rerollsConcedidos,
            String dicaMestre,
            boolean aprovada
    ) {
        if (nomePersonagem != null && !nomePersonagem.isBlank()) {
            this.rascunho.setNomePersonagem(nomePersonagem.trim());
        }
        if (etapa != null) this.rascunho.etapaAtual = etapa;
        if (opcoes != null) this.rascunho.opcoesRacas = opcoes;
        this.rascunho.racaEscolhida = racaEscolhida;
        if (atributos != null) {
            this.rascunho.atributosRolados.clear();
            this.rascunho.atributosRolados.putAll(atributos);
        }
        this.impulsoDraconico = impulsoDraconico;
        this.rascunho.rerollsAssistidosDisponiveis = Math.max(0, rerollsConcedidos);
        this.rascunho.feedbackMestre = dicaMestre != null ? dicaMestre : "";
        this.rascunho.aprovadoPeloMestre = aprovada;
        if (atributos != null && !atributos.isEmpty()) {
            this.rolagensRealizadas = 1;
        }
    }

    public EtapaCriacao getEtapaAtual() {
        return rascunho.getEtapaAtual();
    }

    public Raca getRacaEscolhida() {
        return rascunho.getRacaEscolhida();
    }

    public Map<Atributo, Integer> getAtributosRolados() {
        return rascunho.getAtributosRolados();
    }

    public int getRerollsDisponiveis() {
        return rascunho.getRerollsAssistidosDisponiveis();
    }

    public String getUltimaDicaMestre() {
        return rascunho.getFeedbackMestre();
    }

    public boolean isAprovadaPeloMestre() {
        return rascunho.isAprovadoPeloMestre();
    }

    public List<Raca> getOpcoesRacaSorteadas() {
        if (rascunho.getOpcoesRacas() == null) {
            return Collections.emptyList();
        }
        return rascunho.getOpcoesRacas().getListaOpcoes();
    }

    public SorteadorRacaCriacao.OpcoesSorteio iniciarEExecutarSorteioRacas(int fatorSorte, Random r) {
        return executarSorteioRacas(fatorSorte, false, r);
    }

    /** Rerolagens de atributos que todo jogador tem na criação (além das concedidas pelo Mestre). */
    public static final int REROLAGENS_PADRAO = 2;

    /** Quantas vezes os atributos já foram rolados pelo jogador nesta sessão (a 1ª não é rerolagem). */
    private int rolagensRealizadas = 0;

    public int getRerolagensPermitidas() {
        return REROLAGENS_PADRAO + rascunho.getRerollsAssistidosDisponiveis();
    }

    public int getRerolagensUsadas() {
        return Math.max(0, rolagensRealizadas - 1);
    }

    public int getRerolagensRestantes() {
        return Math.max(0, getRerolagensPermitidas() - getRerolagensUsadas());
    }

    /**
     * Escolhe a raça e rola os atributos. Toda rolagem após a primeira conta como
     * rerolagem, inclusive ao trocar de raça (senão trocar de raça daria rerolagens infinitas).
     *
     * @throws IllegalStateException se as rerolagens acabaram
     */
    public void escolherRacaERolarAtributos(int indiceOpcao, Random r) {
        List<Raca> opcoes = getOpcoesRacaSorteadas();
        if (indiceOpcao < 0 || indiceOpcao >= opcoes.size()) {
            throw new IllegalArgumentException("Opção de raça inválida");
        }
        if (rolagensRealizadas > 0 && getRerolagensRestantes() <= 0) {
            throw new IllegalStateException("As " + getRerolagensPermitidas() + " rerolagens de atributos desta criação já foram usadas");
        }
        escolherRaca(opcoes.get(indiceOpcao));
        rolarAtributosIniciais(r);
        rolagensRealizadas++;
    }

    public void enviarDicaDoMestre(String dica) {
        enviarDicaOuObservacaoMestre(dica);
    }

    public void concederRerollAssistidoPeloMestre(int qtd) {
        for (int i = 0; i < qtd; i++) {
            rascunho.rerollsAssistidosDisponiveis++;
        }
        rascunho.feedbackMestre = "Reroll(s) assistido(s) concedido(s) pelo Mestre (+" + qtd + ")";
    }

    public void aprovarFichaPeloMestre() {
        aprovarPeloMestre();
    }

    public RascunhoFichaEmAndamento getRascunho() {
        return rascunho;
    }

    public void liberarImpulsoDraconico() {
        this.impulsoDraconico = true;
    }

    public boolean isImpulsoDraconicoLiberado() {
        return impulsoDraconico;
    }

    /**
     * Etapa 1: Sorteia as 3 raças disponíveis (Humano + 2 sorteadas com Pity).
     */
    public SorteadorRacaCriacao.OpcoesSorteio executarSorteioRacas(int fatorSorte, boolean boostCampanha, Random r) {
        rascunho.opcoesRacas = SorteadorRacaCriacao.sortearOpcoesCriacao(fatorSorte, boostCampanha, impulsoDraconico, r);
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

        ficha.setNome(rascunho.getNomePersonagem());
        for (Map.Entry<Pericia, int[]> e : rascunho.periciasAlocadas.entrySet()) {
            ficha.setPericia(e.getKey(), e.getValue()[0], e.getValue()[1]);
        }

        rascunho.etapaAtual = EtapaCriacao.FINALIZADO;
        return ficha;
    }
}
