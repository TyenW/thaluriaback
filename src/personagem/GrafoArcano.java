package personagem;

import java.util.*;

/**
 * Compilador de Grafiturgia Elemental de Thaluria baseado na Teoria dos Grafos
 * (docs/Compêndio de Grafiturgia Elemental.md e manuais 01 a 06).
 */
public class GrafoArcano {

    public static final int PESO_INFINITO = 999999;

    public enum ElementoArcano {
        FOGO("Fogo (Ignis)", "HP"),
        AGUA("Água (Aqua)", "HP"),
        TERRA("Terra (Terra)", "HP"),
        VENTO("Vento (Aer)", "HP"),
        RAIO("Raio (Fulmen)", "HP"),
        GELO("Gelo (Glacies)", "HP"),
        LUZ("Luz (Lux)", "HP"),
        SOMBRA("Sombra (Umbra)", "HP ou SAN"),
        CAOS("Caos (Psicose)", "SAN"),
        ORDEM("Ordem (Vetor)", "Mental / Estrutural");

        private final String nomeExibicao;
        private final String alvoRecurso;

        ElementoArcano(String nomeExibicao, String alvoRecurso) {
            this.nomeExibicao = nomeExibicao;
            this.alvoRecurso = alvoRecurso;
        }

        public String getNomeExibicao() { return nomeExibicao; }
        public String getAlvoRecurso() { return alvoRecurso; }
    }

    public enum TopologiaMagia {
        CAMINHO_HAMILTONIANO("Caminho Hamiltoniano (Disparo pontual / Projétil)"),
        CICLO_HAMILTONIANO("Ciclo Hamiltoniano Fechado (Disparo com ressonância de ciclo)"),
        ESTRELA_EULERIANO("Grafo Estrela / Euleriano (Detonação e AoE centrífugo)"),
        BARREIRA_REGULAR("Grafo Regular Simétrico (Barreira / Escudo k-regular)"),
        ARVORE_CICLO_ORDEM("Árvore / Ciclo com Ordem (Maldição / DoT / Controle)");

        private final String descricao;
        TopologiaMagia(String descricao) { this.descricao = descricao; }
        public String getDescricao() { return descricao; }
    }

    public enum ConsequenciaCompilacao {
        SUCESSO_ESTAVEL,
        SUCESSO_SOBRECARGA_ESTABILIZADA,
        FALHA_LEVE,
        FALHA_GRAVE,
        FALHA_CRITICA,
        INVERSAO_FEITICO
    }

    public static class VerticeArcano {
        private final int id;
        private final ElementoArcano elemento;
        private boolean blossomSuperNode = false;
        private GrafoArcano subgrafoBlossom = null;

        public VerticeArcano(int id, ElementoArcano elemento) {
            this.id = id;
            this.elemento = elemento;
        }

        public int getId() { return id; }
        public ElementoArcano getElemento() { return elemento; }
        public boolean isBlossomSuperNode() { return blossomSuperNode; }
        public GrafoArcano getSubgrafoBlossom() { return subgrafoBlossom; }

        public void contrairComoBlossom(GrafoArcano subgrafo) {
            this.blossomSuperNode = true;
            this.subgrafoBlossom = subgrafo;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VerticeArcano v)) return false;
            return id == v.id;
        }

        @Override
        public int hashCode() { return Objects.hash(id); }

        @Override
        public String toString() {
            return String.format("V%d(%s%s)", id, elemento.name(), blossomSuperNode ? " [Blossom]" : "");
        }
    }

    public static class ArestaArcana {
        private final VerticeArcano origem;
        private final VerticeArcano destino;
        private final boolean direcionada;
        private final int pesoW;

        public ArestaArcana(VerticeArcano origem, VerticeArcano destino, boolean direcionada) {
            this.origem = origem;
            this.destino = destino;
            this.direcionada = direcionada;
            this.pesoW = calcularPeso(origem.getElemento(), destino.getElemento(), direcionada);
        }

        public ArestaArcana(VerticeArcano origem, VerticeArcano destino, boolean direcionada, int pesoW) {
            this.origem = origem;
            this.destino = destino;
            this.direcionada = direcionada;
            this.pesoW = pesoW;
        }

        public VerticeArcano getOrigem() { return origem; }
        public VerticeArcano getDestino() { return destino; }
        public boolean isDirecionada() { return direcionada; }
        public int getPesoW() { return pesoW; }

        public boolean isProibida() {
            return pesoW >= PESO_INFINITO;
        }

        @Override
        public String toString() {
            String seta = direcionada ? " -> " : " <-> ";
            return origem + seta + destino + " (W=" + (isProibida() ? "∞" : pesoW) + ")";
        }
    }

    // --- Matriz Canônica de Adjacência e Pesos W ---
    public static int calcularPeso(ElementoArcano orig, ElementoArcano dest, boolean direcionada) {
        if (orig == null || dest == null) return PESO_INFINITO;

        if (!direcionada) {
            // Em grafos não-direcionados prevalece sempre o maior peso entre os dois sentidos
            int ida = getPesoDireto(orig, dest);
            int volta = getPesoDireto(dest, orig);
            return Math.max(ida, volta);
        }
        return getPesoDireto(orig, dest);
    }

    private static int getPesoDireto(ElementoArcano orig, ElementoArcano dest) {
        // Tabela Canônica (docs/Sistema de Magia/02 - Matriz Elemental e Regra das Pontes.md)
        return switch (orig) {
            case FOGO -> switch (dest) {
                case VENTO, RAIO, CAOS -> 1;
                case FOGO, TERRA, LUZ, SOMBRA -> 2;
                case AGUA, GELO -> PESO_INFINITO;
                case ORDEM -> 2;
            };
            case AGUA -> switch (dest) {
                case GELO, TERRA, ORDEM -> 1;
                case AGUA, VENTO, LUZ -> 2;
                case FOGO, RAIO -> PESO_INFINITO; // Exceção condutiva direta sem ponte
                case SOMBRA -> 2;
                case CAOS -> PESO_INFINITO;
            };
            case TERRA -> switch (dest) {
                case AGUA, FOGO, ORDEM -> 1;
                case TERRA, GELO, LUZ, SOMBRA -> 2;
                case VENTO, RAIO, CAOS -> PESO_INFINITO;
            };
            case VENTO -> switch (dest) {
                case FOGO, RAIO, GELO -> 1;
                case VENTO, AGUA, CAOS, LUZ -> 2;
                case TERRA, ORDEM, SOMBRA -> PESO_INFINITO;
            };
            case RAIO -> switch (dest) {
                case FOGO, VENTO, AGUA -> 1;
                case RAIO, LUZ, CAOS, ORDEM -> 2;
                case TERRA, GELO, SOMBRA -> PESO_INFINITO;
            };
            case GELO -> switch (dest) {
                case AGUA, VENTO, ORDEM -> 1;
                case GELO, TERRA, SOMBRA -> 2;
                case FOGO, CAOS, RAIO, LUZ -> PESO_INFINITO;
            };
            case LUZ -> switch (dest) {
                case FOGO, RAIO, ORDEM -> 1;
                case LUZ, AGUA, TERRA, VENTO -> 2;
                case SOMBRA, CAOS, GELO -> PESO_INFINITO;
            };
            case SOMBRA -> switch (dest) {
                case GELO, CAOS -> 1;
                case SOMBRA, TERRA, FOGO, AGUA -> 2;
                case LUZ, ORDEM, VENTO, RAIO -> PESO_INFINITO;
            };
            case CAOS -> switch (dest) {
                case FOGO, SOMBRA -> 1;
                case CAOS, VENTO, RAIO -> 2;
                case ORDEM, GELO, LUZ, AGUA, TERRA -> PESO_INFINITO;
            };
            case ORDEM -> switch (dest) {
                case AGUA, TERRA, GELO, LUZ -> 1;
                case ORDEM, RAIO, FOGO, SOMBRA -> 2;
                case CAOS, VENTO -> PESO_INFINITO;
            };
        };
    }

    // --- Estrutura do Grafo Arcano ---
    private final String nomeFeitico;
    private TopologiaMagia topologia;
    private final List<VerticeArcano> vertices = new ArrayList<>();
    private final List<ArestaArcana> arestas = new ArrayList<>();
    private int kRegularidade = 0; // Para BARREIRA_REGULAR

    public GrafoArcano(String nomeFeitico, TopologiaMagia topologia) {
        this.nomeFeitico = nomeFeitico;
        this.topologia = topologia;
    }

    public String getNomeFeitico() { return nomeFeitico; }
    public TopologiaMagia getTopologia() { return topologia; }
    public void setTopologia(TopologiaMagia topologia) { this.topologia = topologia; }
    public List<VerticeArcano> getVertices() { return Collections.unmodifiableList(vertices); }
    public List<ArestaArcana> getArestas() { return Collections.unmodifiableList(arestas); }
    public int getKRegularidade() { return kRegularidade; }
    public void setKRegularidade(int k) { this.kRegularidade = Math.max(0, k); }

    public VerticeArcano adicionarVertice(int id, ElementoArcano elemento) {
        VerticeArcano v = new VerticeArcano(id, elemento);
        vertices.add(v);
        return v;
    }

    public ArestaArcana conectar(VerticeArcano orig, VerticeArcano dest, boolean direcionada) {
        ArestaArcana a = new ArestaArcana(orig, dest, direcionada);
        arestas.add(a);
        return a;
    }

    public ArestaArcana conectarComPeso(VerticeArcano orig, VerticeArcano dest, boolean direcionada, int pesoW) {
        ArestaArcana a = new ArestaArcana(orig, dest, direcionada, pesoW);
        arestas.add(a);
        return a;
    }

    // --- Cálculos Matemáticos Canônicos ---

    public int getQuantidadeVertices() {
        return vertices.size();
    }

    /**
     * Capacidade Mental efetiva: com Contração de Blossom, um ciclo ímpar encapsulado
     * consome apenas |V_externo| + (|V_Blossom| - 1).
     */
    public int getVerticesEfetivosParaLimiteMental() {
        int total = 0;
        for (VerticeArcano v : vertices) {
            if (v.isBlossomSuperNode() && v.getSubgrafoBlossom() != null) {
                total += (v.getSubgrafoBlossom().getQuantidadeVertices() - 1);
            } else {
                total += 1;
            }
        }
        return total;
    }

    public boolean isCognitivamenteEstavel(int grauMental) {
        return getVerticesEfetivosParaLimiteMental() <= (2 + Math.max(0, grauMental));
    }

    public int getSomaPesos() {
        int soma = 0;
        for (ArestaArcana a : arestas) {
            soma += a.getPesoW();
        }
        for (VerticeArcano v : vertices) {
            if (v.isBlossomSuperNode() && v.getSubgrafoBlossom() != null) {
                soma += v.getSubgrafoBlossom().getSomaPesos();
            }
        }
        return soma;
    }

    public int getCustoMana(int grauMagia) {
        int g = Math.max(1, grauMagia);
        return getSomaPesos() * 2 * g;
    }

    public int getQuantidadeDadosDanoBase() {
        int total = vertices.size();
        for (VerticeArcano v : vertices) {
            if (v.isBlossomSuperNode() && v.getSubgrafoBlossom() != null) {
                total += v.getSubgrafoBlossom().getQuantidadeDadosDanoBase();
            }
        }
        return total;
    }

    /**
     * Bônus de Densidade / Cordas (Delta E):
     * Cada aresta além da árvore mínima (|E| > |V| - 1) concede +1d4 de dano e +2 de penetração de defesa.
     */
    public int getQtdCordasDensidade() {
        if (vertices.size() <= 1) return 0;
        int arvoreMinima = vertices.size() - 1;
        return Math.max(0, arestas.size() - arvoreMinima);
    }

    public int getPenetracaoDefesaCordas() {
        return getQtdCordasDensidade() * 2;
    }

    public int getBonusDanoCicloFechado(int grauMagia) {
        if (topologia == TopologiaMagia.CICLO_HAMILTONIANO) {
            return 5 * Math.max(1, grauMagia);
        }
        return 0;
    }

    public int getHpBarreiraRegular(int grauMagia) {
        int g = Math.max(1, grauMagia);
        return (5 * vertices.size()) + (5 * kRegularidade * g);
    }

    public int getRdBarreiraRegular(int grauMagia) {
        int g = Math.max(1, grauMagia);
        return kRegularidade * g;
    }

    public boolean contemLigacaoProibidaDireta() {
        for (ArestaArcana a : arestas) {
            if (a.isProibida()) return true;
        }
        for (VerticeArcano v : vertices) {
            if (v.isBlossomSuperNode() && v.getSubgrafoBlossom() != null) {
                if (v.getSubgrafoBlossom().contemLigacaoProibidaDireta()) return true;
            }
        }
        return false;
    }

    public int calcularDTSobrecarga() {
        return 10 + (5 * getVerticesEfetivosParaLimiteMental()) + (2 * getSomaPesos());
    }

    /**
     * Compila o feitiço conforme o Protocolo do Mestre (04 - Protocolo do Mestre e Colapso de Grafo.md).
     */
    public ResultadoCompilacao compilar(int grauMental, int grauMagia, int rolagemOcultismo) {
        if (contemLigacaoProibidaDireta()) {
            return new ResultadoCompilacao(
                    ConsequenciaCompilacao.INVERSAO_FEITICO,
                    0, 0, 0,
                    "Inversão de Feitiço Imediata! Ligação oposta proibida sem vértice ponte."
            );
        }

        int gMagia = Math.max(1, grauMagia);
        int custoMana = getCustoMana(gMagia);
        int dadosBase = getQuantidadeDadosDanoBase();
        int bonusFixo = getBonusDanoCicloFechado(gMagia);

        if (isCognitivamenteEstavel(grauMental)) {
            return new ResultadoCompilacao(
                    ConsequenciaCompilacao.SUCESSO_ESTAVEL,
                    custoMana, dadosBase, bonusFixo,
                    "Feitiço compilado com estabilidade cognitiva total."
            );
        }

        // Sobrecarga Neural
        int dt = calcularDTSobrecarga();
        if (rolagemOcultismo >= dt) {
            return new ResultadoCompilacao(
                    ConsequenciaCompilacao.SUCESSO_SOBRECARGA_ESTABILIZADA,
                    custoMana, dadosBase, bonusFixo,
                    String.format("Sobrecarga Neural superada (Rolagem %d >= DT %d).", rolagemOcultismo, dt)
            );
        }

        int margemFalha = dt - rolagemOcultismo;
        if (margemFalha >= 1 && margemFalha <= 4) {
            return new ResultadoCompilacao(
                    ConsequenciaCompilacao.FALHA_LEVE,
                    custoMana, Math.max(1, dadosBase / 2), bonusFixo / 2,
                    String.format("Falha Leve por %d pontos: gasta Mana, sofre %d dano no HP e metade do efeito.",
                            margemFalha, getSomaPesos())
            );
        } else if (margemFalha >= 5 && margemFalha <= 9) {
            return new ResultadoCompilacao(
                    ConsequenciaCompilacao.FALHA_GRAVE,
                    0, 0, 0,
                    String.format("Falha Grave por %d pontos: feitiço dissipa, perde o turno e sofre %d de dano na SAN.",
                            margemFalha, 2 * vertices.size())
            );
        } else {
            return new ResultadoCompilacao(
                    ConsequenciaCompilacao.FALHA_CRITICA,
                    custoMana, 0, 0,
                    "Colapso Catastrófico de Grafo! Mana queimada, dano total de HP e perda permanente de 1d6 SAN."
            );
        }
    }

    public static class ResultadoCompilacao {
        private final ConsequenciaCompilacao consequencia;
        private final int custoMana;
        private final int dadosDano;
        private final int bonusDanoFixo;
        private final String parecerNarrativo;

        public ResultadoCompilacao(ConsequenciaCompilacao consequencia, int custoMana, int dadosDano, int bonusDanoFixo, String parecerNarrativo) {
            this.consequencia = consequencia;
            this.custoMana = custoMana;
            this.dadosDano = dadosDano;
            this.bonusDanoFixo = bonusDanoFixo;
            this.parecerNarrativo = parecerNarrativo;
        }

        public ConsequenciaCompilacao getConsequencia() { return consequencia; }
        public int getCustoMana() { return custoMana; }
        public int getDadosDano() { return dadosDano; }
        public int getBonusDanoFixo() { return bonusDanoFixo; }
        public String getParecerNarrativo() { return parecerNarrativo; }
        public boolean isSucesso() {
            return consequencia == ConsequenciaCompilacao.SUCESSO_ESTAVEL ||
                    consequencia == ConsequenciaCompilacao.SUCESSO_SOBRECARGA_ESTABILIZADA;
        }
    }

    // --- OPERAÇÕES DE METAMAGIA ALGORÍTMICA (Manual 06) ---

    /**
     * 1. Contração de Blossom: Encapsula um ciclo ímpar (>= 3 nós) em um Super-Vértice Blossom.
     */
    public boolean contrairBlossom(int idVerticeSuperNode, GrafoArcano cicloImpar) {
        if (cicloImpar == null || cicloImpar.getQuantidadeVertices() < 3 || cicloImpar.getQuantidadeVertices() % 2 == 0) {
            return false; // Ciclo ímpar exige 3, 5, 7 nós
        }
        VerticeArcano v = new VerticeArcano(idVerticeSuperNode, cicloImpar.getVertices().get(0).getElemento());
        v.contrairComoBlossom(cicloImpar);
        this.vertices.add(v);
        return true;
    }

    /**
     * 2. Emparelhamento Perfeito (Perfect Matching): Fusão cooperativa entre dois magos com |V_A| == |V_B|.
     */
    public static GrafoArcano emparelhamentoPerfeito(GrafoArcano magoA, GrafoArcano magoB, List<int[]> pares) {
        if (magoA == null || magoB == null || magoA.getQuantidadeVertices() != magoB.getQuantidadeVertices()) {
            throw new IllegalArgumentException("Emparelhamento Perfeito exige grafos com exatamente a mesma quantidade de vértices");
        }

        GrafoArcano fusao = new GrafoArcano("Fusão Cataclísmica: " + magoA.getNomeFeitico() + " + " + magoB.getNomeFeitico(), TopologiaMagia.ESTRELA_EULERIANO);
        fusao.vertices.addAll(magoA.getVertices());
        fusao.vertices.addAll(magoB.getVertices());
        fusao.arestas.addAll(magoA.getArestas());
        fusao.arestas.addAll(magoB.getArestas());

        // Conecta as arestas de emparelhamento
        for (int[] par : pares) {
            VerticeArcano va = magoA.getVertices().get(par[0]);
            VerticeArcano vb = magoB.getVertices().get(par[1]);
            int peso = calcularPeso(va.getElemento(), vb.getElemento(), false);
            if (peso >= PESO_INFINITO) {
                throw new IllegalStateException("Emparelhamento inválido: elementos opostos conectados diretamente sem ponte!");
            }
            fusao.conectarComPeso(va, vb, false, peso);
        }

        return fusao;
    }

    /**
     * 3. Poda de Kruskal / MST de Emergência: Reduz as arestas para a Árvore Geradora Mínima.
     */
    public GrafoArcano aplicarPodaKruskalMST() {
        GrafoArcano mst = new GrafoArcano(this.nomeFeitico + " [Poda Kruskal MST]", TopologiaMagia.ARVORE_CICLO_ORDEM);
        mst.vertices.addAll(this.vertices);

        List<ArestaArcana> ordenadas = new ArrayList<>(this.arestas);
        ordenadas.sort(Comparator.comparingInt(ArestaArcana::getPesoW));

        Map<Integer, Integer> pai = new HashMap<>();
        for (VerticeArcano v : vertices) {
            pai.put(v.getId(), v.getId());
        }

        for (ArestaArcana a : ordenadas) {
            int raizOrig = encontrarRaiz(pai, a.getOrigem().getId());
            int raizDest = encontrarRaiz(pai, a.getDestino().getId());
            if (raizOrig != raizDest) {
                mst.arestas.add(a);
                pai.put(raizOrig, raizDest);
                if (mst.arestas.size() == vertices.size() - 1) break;
            }
        }

        return mst;
    }

    private static int encontrarRaiz(Map<Integer, Integer> pai, int i) {
        if (pai.get(i) == i) return i;
        int r = encontrarRaiz(pai, pai.get(i));
        pai.put(i, r);
        return r;
    }

    /**
     * 4. Vértices de Articulação (Cut-Vertices): Identifica pontos de corte para contra-mágica cirúrgica.
     */
    public List<VerticeArcano> encontrarVerticesDeArticulacao() {
        List<VerticeArcano> articulacoes = new ArrayList<>();
        int n = vertices.size();
        if (n <= 2) return articulacoes;

        Map<Integer, List<Integer>> adj = new HashMap<>();
        for (VerticeArcano v : vertices) adj.put(v.getId(), new ArrayList<>());
        for (ArestaArcana a : arestas) {
            adj.get(a.getOrigem().getId()).add(a.getDestino().getId());
            adj.get(a.getDestino().getId()).add(a.getOrigem().getId());
        }

        for (VerticeArcano v : vertices) {
            // Remove v e checa se o grafo desconecta
            Set<Integer> visitados = new HashSet<>();
            int inicio = -1;
            for (VerticeArcano outro : vertices) {
                if (outro.getId() != v.getId()) {
                    inicio = outro.getId();
                    break;
                }
            }

            if (inicio != -1) {
                dfsConexao(inicio, v.getId(), adj, visitados);
                if (visitados.size() < (n - 1)) {
                    articulacoes.add(v);
                }
            }
        }
        return articulacoes;
    }

    private void dfsConexao(int u, int ignorado, Map<Integer, List<Integer>> adj, Set<Integer> visitados) {
        visitados.add(u);
        for (int viz : adj.getOrDefault(u, Collections.emptyList())) {
            if (viz != ignorado && !visitados.contains(viz)) {
                dfsConexao(viz, ignorado, adj, visitados);
            }
        }
    }
}
