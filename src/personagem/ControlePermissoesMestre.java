package personagem;

import java.util.*;

/**
 * Controle de Permissões e Fog-of-War (Jogador vs. Mestre - Web-Ready).
 * - Visão do Jogador: Bloqueio progressivo de Wiki, Bestiário oculto como "???",
 *   e 3 níveis de visibilidade no Mapa Interativo (OCULTO, ROTA_E_NOME_APENAS, INFORMACOES_COMPLETAS).
 * - Visão do Mestre: Omnisciente e controle de liberação com um clique.
 */
public class ControlePermissoesMestre {

    public enum NivelVisibilidadeMapa {
        OCULTO("Oculto (Nó/aresta invisível no mapa do jogador)"),
        ROTA_E_NOME_APENAS("Rota e Nome Apenas (Visualiza estrada e nó sem dados internos ou lojas)"),
        INFORMACOES_COMPLETAS("Informações Completas (Lojas, clima, geologia e preços revelados)");

        private final String descricao;
        NivelVisibilidadeMapa(String descricao) { this.descricao = descricao; }
        public String getDescricao() { return descricao; }
    }

    // Wiki / Documentação: Capítulos iniciam bloqueados
    private final Map<String, Boolean> capitulosWikiLiberados = new LinkedHashMap<>();

    // Bestiário: Monstros identificados/estudados
    private final Set<String> monstrosReveladosBestiario = new HashSet<>();

    // Mapa Interativo: Nível de visibilidade de Cidades e Conexões
    private final Map<String, NivelVisibilidadeMapa> visibilidadeCidades = new LinkedHashMap<>();
    private final Map<String, NivelVisibilidadeMapa> visibilidadeConexoes = new LinkedHashMap<>();

    // Controle de Dia de Campanha pelo Mestre
    private int diaAtualCampanha = 1;

    public ControlePermissoesMestre() {
        // Padrão canônico: Capítulos essenciais iniciam bloqueados para exploração gradual
        capitulosWikiLiberados.put("Status", false);
        capitulosWikiLiberados.put("Grafiturgia Elemental", false);
        capitulosWikiLiberados.put("Economia e Comercio", false);
        capitulosWikiLiberados.put("Bestiario Regional", false);
    }

    // --- Wiki e Documentação ---
    public boolean isCapituloLiberado(String capitulo) {
        return capitulosWikiLiberados.getOrDefault(capitulo, false);
    }

    public void liberarCapituloWiki(String capitulo) {
        capitulosWikiLiberados.put(capitulo, true);
    }

    public void bloquearCapituloWiki(String capitulo) {
        capitulosWikiLiberados.put(capitulo, false);
    }

    // --- Bestiário e Ocultismo ---
    public boolean isMonstroRevelado(String idOuNomeMonstro) {
        if (idOuNomeMonstro == null) return false;
        return monstrosReveladosBestiario.contains(idOuNomeMonstro.trim().toUpperCase(Locale.ROOT));
    }

    public void revelarMonstroBestiario(String idOuNomeMonstro) {
        if (idOuNomeMonstro != null) {
            monstrosReveladosBestiario.add(idOuNomeMonstro.trim().toUpperCase(Locale.ROOT));
        }
    }

    /**
     * Retorna o nome exibido para o jogador (ex: "???" se oculto, ou o nome real se estudado/liberado).
     */
    public String getNomeExibicaoMonstroJogador(String idOuNomeMonstro, String nomeReal) {
        if (isMonstroRevelado(idOuNomeMonstro)) {
            return nomeReal != null ? nomeReal : idOuNomeMonstro;
        }
        return "??? (Criatura Desconhecida)";
    }

    // --- Mapa Interativo ---
    public NivelVisibilidadeMapa getVisibilidadeCidade(String nomeCidade) {
        return visibilidadeCidades.getOrDefault(nomeCidade, NivelVisibilidadeMapa.OCULTO);
    }

    public void setVisibilidadeCidade(String nomeCidade, NivelVisibilidadeMapa nivel) {
        if (nomeCidade != null && nivel != null) {
            visibilidadeCidades.put(nomeCidade, nivel);
        }
    }

    public NivelVisibilidadeMapa getVisibilidadeConexao(String chaveConexao) {
        return visibilidadeConexoes.getOrDefault(chaveConexao, NivelVisibilidadeMapa.OCULTO);
    }

    public void setVisibilidadeConexao(String chaveConexao, NivelVisibilidadeMapa nivel) {
        if (chaveConexao != null && nivel != null) {
            visibilidadeConexoes.put(chaveConexao, nivel);
        }
    }

    // --- Controle de Raças no Sorteador Inicial ---
    public void ativarRacaSorteioInicial(String nomeRaca) {
        Raca r = Raca.buscarPorNome(nomeRaca);
        if (r != null) {
            r.setDisponivelSorteioInicial(true);
        }
    }

    public void desativarRacaSorteioInicial(String nomeRaca) {
        Raca r = Raca.buscarPorNome(nomeRaca);
        if (r != null) {
            r.setDisponivelSorteioInicial(false);
        }
    }

    public boolean isRacaDisponivelSorteioInicial(String nomeRaca) {
        Raca r = Raca.buscarPorNome(nomeRaca);
        return r != null && r.isDisponivelSorteioInicial();
    }

    // --- Controle de Campanha do Mestre ---
    public int getDiaAtualCampanha() { return diaAtualCampanha; }

    public int avancarDiaMestre(economia.MotorEconomico motor) {
        diaAtualCampanha++;
        if (motor != null) {
            motor.avancarTempo(24);
        }
        return diaAtualCampanha;
    }

    public void avancarDiasDeMercado(int dias, Random r) {
        int d = Math.max(1, dias);
        diaAtualCampanha += d;
    }

    public void avancarDiasDeMercado(int dias, economia.MotorEconomico motor, Random r) {
        int d = Math.max(1, dias);
        for (int i = 0; i < d; i++) {
            avancarDiaMestre(motor);
        }
    }

    public boolean isMonstroReveladoNoBestiario(String idOuNomeMonstro) {
        return isMonstroRevelado(idOuNomeMonstro);
    }

    public void definirVisibilidadeCidade(String nomeCidade, NivelVisibilidadeMapa nivel) {
        setVisibilidadeCidade(nomeCidade, nivel);
    }

    public List<String> getCapitulosWikiLiberados() {
        List<String> liberados = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : capitulosWikiLiberados.entrySet()) {
            if (Boolean.TRUE.equals(e.getValue())) {
                liberados.add(e.getKey());
            }
        }
        return liberados;
    }

    public Map<String, Boolean> getMapaCapitulosWiki() {
        return Collections.unmodifiableMap(capitulosWikiLiberados);
    }

    public Set<String> getMonstrosRevelados() {
        return Collections.unmodifiableSet(monstrosReveladosBestiario);
    }

    public Map<String, NivelVisibilidadeMapa> getVisibilidadeCidades() {
        return Collections.unmodifiableMap(visibilidadeCidades);
    }

    public Map<String, NivelVisibilidadeMapa> getVisibilidadeConexoes() {
        return Collections.unmodifiableMap(visibilidadeConexoes);
    }
}
