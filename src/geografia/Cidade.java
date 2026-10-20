package geografia;

import economia.Lojas;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Representa uma cidade, reino ou assentamento civilizatório em Thaluria.
 */
public class Cidade {

    public enum TipoAssentamento {
        CIDADE("Cidade"),
        REINO("Reino / Capital"),
        VILA("Vila / Aldeia"),
        FORTALEZA("Fortaleza / Cidadela");

        private final String nomeFormatado;
        TipoAssentamento(String nomeFormatado) { this.nomeFormatado = nomeFormatado; }
        public String getNomeFormatado() { return nomeFormatado; }
        @Override public String toString() { return nomeFormatado; }
    }

    private int id;
    private String nome;
    private TipoAssentamento tipo;
    private Regiao regiao;
    private boolean porto;
    private boolean fronteira;
    private Regiao regiaofronteira;
    private Geologia geologia;
    private Aquifero aquifero;

    private final List<Conexao> conexoes = new ArrayList<>();
    private final List<Lojas> lojas = new ArrayList<>();

    public Cidade(String nome, TipoAssentamento tipo, Regiao regiao, boolean porto, boolean fronteira, Regiao regiaofronteira) {
        this(0, nome, tipo, regiao, null, null, porto, fronteira, regiaofronteira);
    }

    public Cidade(int id, String nome, TipoAssentamento tipo, Regiao regiao, boolean porto, boolean fronteira, Regiao regiaofronteira) {
        this(id, nome, tipo, regiao, null, null, porto, fronteira, regiaofronteira);
    }

    public Cidade(int id, String nome, TipoAssentamento tipo, Regiao regiao, Geologia geologia, Aquifero aquifero, boolean porto, boolean fronteira, Regiao regiaofronteira) {
        this.id = id;
        this.nome = (nome != null && !nome.trim().isEmpty()) ? nome : "Cidade Sem Nome";
        this.tipo = (tipo != null) ? tipo : TipoAssentamento.CIDADE;
        this.regiao = regiao;
        this.geologia = geologia;
        this.aquifero = aquifero;
        this.porto = porto;
        this.fronteira = fronteira;
        this.regiaofronteira = fronteira ? regiaofronteira : null;
    }

    // Gerenciamento de Rotas
    public void adicionarConexao(Conexao conexao) {
        if (conexao != null && !this.conexoes.contains(conexao)) {
            this.conexoes.add(conexao);
        }
    }

    public boolean removerConexao(Conexao conexao) {
        return this.conexoes.remove(conexao);
    }

    public List<Conexao> getConexoes() {
        return Collections.unmodifiableList(conexoes);
    }

    public List<Cidade> listarCidadesVizinhas() {
        List<Cidade> vizinhas = new ArrayList<>();
        for (Conexao c : conexoes) {
            if (!c.isBloqueada()) {
                vizinhas.add(c.outraPonta(this));
            }
        }
        return vizinhas;
    }

    // Gerenciamento de Lojas
    public void adicionarLoja(Lojas loja) {
        if (loja != null && !this.lojas.contains(loja)) {
            this.lojas.add(loja);
            if (loja.getCidadeSede() != this) {
                loja.setCidadeSede(this);
            }
        }
    }

    public boolean removerLoja(Lojas loja) {
        return this.lojas.remove(loja);
    }

    public List<Lojas> getLojas() {
        return Collections.unmodifiableList(lojas);
    }

    // Algoritmos de Ordenação (Merge Sort por ID)
    public static Cidade[] ordenarCidadesPorId(Cidade[] cidades) {
        if (cidades == null || cidades.length < 2) {
            return cidades;
        }
        int n = cidades.length;
        int meio = n / 2;
        Cidade[] esquerda = new Cidade[meio];
        Cidade[] direita = new Cidade[n - meio];

        System.arraycopy(cidades, 0, esquerda, 0, meio);
        System.arraycopy(cidades, 0 + meio, direita, 0, n - meio);

        esquerda = ordenarCidadesPorId(esquerda);
        direita = ordenarCidadesPorId(direita);
        return merge(esquerda, direita);
    }

    private static Cidade[] merge(Cidade[] esquerda, Cidade[] direita) {
        int n = esquerda.length + direita.length;
        Cidade[] resultado = new Cidade[n];
        int i = 0, j = 0, k = 0;

        while (i < esquerda.length && j < direita.length) {
            if (esquerda[i] != null && (direita[j] == null || esquerda[i].getId() <= direita[j].getId())) {
                resultado[k++] = esquerda[i++];
            } else {
                resultado[k++] = direita[j++];
            }
        }
        while (i < esquerda.length) resultado[k++] = esquerda[i++];
        while (j < direita.length) resultado[k++] = direita[j++];
        return resultado;
    }

    // Algoritmo de Busca Binária por ID
    public static Cidade buscarCidadePorId(Cidade[] cidades, int id) {
        if (cidades == null || cidades.length == 0) return null;
        return getCidade(cidades, 0, cidades.length - 1, id);
    }

    public static Cidade getCidade(Cidade[] cidades, int comeco, int fim, int id) {
        if (comeco > fim || cidades == null) {
            return null;
        }
        int meio = comeco + (fim - comeco) / 2;
        if (cidades[meio] == null) {
            return null;
        }
        if (cidades[meio].getId() == id) {
            return cidades[meio];
        }
        if (cidades[meio].getId() > id) {
            return getCidade(cidades, comeco, meio - 1, id);
        }
        return getCidade(cidades, meio + 1, fim, id);
    }

    // Métodos legados para retrocompatibilidade
    public Cidade[] ordenarcidadesporid(Cidade[] cidades) {
        return ordenarCidadesPorId(cidades);
    }

    public Cidade getcidade(Cidade[] cidades, int comeco, int fim, int id) {
        return getCidade(cidades, comeco, fim, id);
    }

    public void mudarNome(String nome) { setNome(nome); }
    public void mudarTipoAssentamento(TipoAssentamento tipo) { setTipo(tipo); }
    public void mudarRegiao(Regiao regiao) { setRegiao(regiao); }
    public void mudarPorto(boolean porto) { setPorto(porto); }
    public void mudarFronteira(boolean fronteira) { setFronteira(fronteira); }
    public void mudarRegiaofronteira(Regiao regiaofronteira) { setRegiaofronteira(regiaofronteira); }

    // Getters e Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public TipoAssentamento getTipo() { return tipo; }
    public void setTipo(TipoAssentamento tipo) { this.tipo = tipo; }

    public Regiao getRegiao() { return regiao; }
    public void setRegiao(Regiao regiao) { this.regiao = regiao; }

    public boolean temPorto() { return porto; }
    public boolean isPorto() { return porto; }
    public void setPorto(boolean porto) { this.porto = porto; }

    public boolean isFronteira() { return fronteira; }
    public void setFronteira(boolean fronteira) {
        this.fronteira = fronteira;
        if (!fronteira) this.regiaofronteira = null;
    }

    public Regiao getRegiaofronteira() { return regiaofronteira; }
    public void setRegiaofronteira(Regiao regiaofronteira) { this.regiaofronteira = regiaofronteira; }

    public Geologia getGeologia() { return geologia; }
    public void setGeologia(Geologia geologia) { this.geologia = geologia; }

    public Aquifero getAquifero() { return aquifero; }
    public void setAquifero(Aquifero aquifero) { this.aquifero = aquifero; }

    /**
     * Calcula a probabilidade diária de um fornecedor NPC local vender um determinado item nesta cidade,
     * baseando-se no bioma (Regiao) e na formação de relevo (Geologia).
     * Servirá de fundação para o sistema de Crafting regional.
     */
    public double calcularChanceFornecedorNpc(economia.Item item) {
        if (item == null) return 0.35;
        double chanceBase = 0.35;
        String nome = item.getNome().toLowerCase();

        boolean isMadeira = nome.contains("madeira") || nome.contains("tronco") || nome.contains("tábua");
        boolean isMineral = (nome.contains("ferro") || nome.contains("minério") || nome.contains("lingote")
                || nome.contains("metal") || nome.contains("aço") || (item.getTipo() == economia.Item.TipoItem.MATERIAL && !isMadeira));
        boolean isGema = nome.contains("gema") || nome.contains("cristal") || nome.contains("diamante")
                || nome.contains("joia") || item.getTipo() == economia.Item.TipoItem.ACESSORIO;
        boolean isErvaAlquimica = nome.contains("erva") || nome.contains("poção") || nome.contains("planta")
                || nome.contains("elixir") || nome.contains("raiz");
        boolean isTecidoCouro = nome.contains("seda") || nome.contains("manto") || nome.contains("couro")
                || nome.contains("pele") || nome.contains("tecido") || nome.contains("linho");

        // 1. Afinidade de MONTANHA / CAVERNA -> Minérios, Metais e Gemas
        if (geologia == Geologia.MONTANHA || geologia == Geologia.CAVERNA) {
            if (isMadeira) return 0.15; // Escasso em altas montanhas rochosas
            if (isMineral) return 0.65;
            if (isGema) return 0.60;
        }

        // 2. Afinidade de FLORESTA / TAIGA / TUNDRA / TROPICAL -> Madeira, Couros, Caça
        if (geologia == Geologia.FLORESTA || regiao == Regiao.TUNDRA || regiao == Regiao.TAIGA || regiao == Regiao.TROPICAL) {
            if (isMadeira) return 0.70;
            if (isTecidoCouro) return 0.60;
            if (isMineral) return 0.20;
        }

        // 3. Afinidade de PLANÍCIE / VALE / TEMPERADO -> Ervas Alquímicas, Fibras, Seda, Alimentos
        if (geologia == Geologia.PLANICIE || geologia == Geologia.VALE || regiao == Regiao.TEMPERADO) {
            if (isErvaAlquimica) return 0.65;
            if (isTecidoCouro) return 0.60;
        }

        // 4. Afinidade de DESERTO / SAVANA / CANYON -> Gemas Preciosas, Cristais e Joalheria
        if (regiao == Regiao.DESERTO || regiao == Regiao.SAVANA || geologia == Geologia.CANYON) {
            if (isGema) return 0.75;
            if (isMadeira) return 0.10; // Raro no deserto
        }

        return chanceBase;
    }

    /**
     * Calcula a quantidade de matéria-prima fornecida por lote, bonificando regiões abundantes.
     */
    public int calcularQtdFornecedorNpc(economia.Item item, java.util.Random random) {
        if (random == null) random = new java.util.Random();
        double chance = calcularChanceFornecedorNpc(item);
        if (chance >= 0.60) {
            // Região de alta afinidade e abundância: lote ampliado
            return 2 + random.nextInt(4); // 2 a 5 unidades
        }
        return 1 + random.nextInt(3); // 1 a 3 unidades
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cidade cidade = (Cidade) o;
        return id == cidade.id && Objects.equals(nome, cidade.nome);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, nome);
    }

    @Override
    public String toString() {
        String infoFronteira = fronteira ? " (Fronteira com " + regiaofronteira + ")" : "";
        String infoPorto = porto ? " [Porto Ativo]" : "";
        String infoGeol = (geologia != null) ? " | " + geologia.getNomeFormatado() : "";
        String infoAqui = (aquifero != null) ? " | " + aquifero.getNomeFormatado() : "";
        return String.format("%s [ID: %d] - %s em %s%s%s%s%s (Rotas ativas: %d)",
            nome, id, tipo, regiao, infoPorto, infoFronteira, infoGeol, infoAqui, conexoes.size());
    }
}
