package economia;

import geografia.Aquifero;
import geografia.Cidade;
import geografia.Geologia;
import geografia.Regiao;
import java.util.Objects;

/**
 * Representa um item, arma, armadura, consumível ou recurso no universo de Thaluria.
 * Atua como o núcleo universal para inventários, lojas, carteiras e caravanas,
 * integrando-se modularmente com fichas técnicas especializadas.
 */
public class Item {

    public enum TipoItem {
        ARMA("Arma"), // Mantido para compatibilidade total com o legado v3
        ARMA_BRANCA("Arma Branca / Corpo a Corpo"),
        ARMA_LONGO_ALCANCE("Arma de Longo Alcance"),
        PROJETIL("Projétil / Munição"),
        ARMADURA("Armadura"),
        ESCUDO("Escudo"),
        CONSUMIVEL("Consumível"),
        ACESSORIO("Acessório"),
        MATERIAL("Material de Forja"),
        MAGICO("Item Mágico / Grimório"),
        OUTRO("Geral");

        private final String nomeFormatado;
        TipoItem(String nomeFormatado) { this.nomeFormatado = nomeFormatado; }
        public String getNomeFormatado() { return nomeFormatado; }
        @Override public String toString() { return nomeFormatado; }
    }

    public enum RaridadeItem {
        COMUM("Comum"),
        INCOMUM("Incomum"),
        RARO("Raro"),
        EPICO("Épico"),
        LENDARIO("Lendário"),
        MITICO("Mítico");

        private final String nomeFormatado;
        RaridadeItem(String nomeFormatado) { this.nomeFormatado = nomeFormatado; }
        public String getNomeFormatado() { return nomeFormatado; }
        @Override public String toString() { return nomeFormatado; }
    }

    public enum OrigemItem {
        COLETAVEL_OU_DROP("Coletável / Drop de Monstro"),
        MANUFATURADO_CRAFT("Manufaturado / Crafting");

        private final String nomeFormatado;
        OrigemItem(String nomeFormatado) { this.nomeFormatado = nomeFormatado; }
        public String getNomeFormatado() { return nomeFormatado; }
        @Override public String toString() { return nomeFormatado; }
    }

    private int id;
    private String nome;
    private String descricao;
    private double preco; // Em moedas de ouro (PO)
    private double peso;  // Em kg
    private TipoItem tipo;
    private RaridadeItem raridade;
    private int quantidade;

    // FASE 1 e 4.1: Origem de Manufatura vs Drops e assinatura geográfica
    private OrigemItem origemItem = OrigemItem.COLETAVEL_OU_DROP;
    private QualidadeCraft qualidadeCraft = null;
    private String monstroOrigem = null;

    private TamanhoItem tamanho = TamanhoItem.MEDIO;
    private Regiao regiaoOrigem;
    private Geologia geologiaOrigem;
    private Aquifero aquiferoOrigem;

    // Fichas técnicas modulares (Composição)
    private FichaMaterial fichaMaterial;
    private FichaArmaBranca fichaArmaBranca;
    private FichaArmaLongoAlcance fichaArmaLongoAlcance;
    private FichaArmadura fichaArmadura;
    private FichaEscudo fichaEscudo;
    private FichaAcessorio fichaAcessorio;
    private FichaConsumivel fichaConsumivel;
    private FichaGrimorio fichaGrimorio;

    public Item() {
        this(0, "Item Desconhecido", "Sem descrição", 0.0, 0.1, TipoItem.OUTRO, RaridadeItem.COMUM, 1);
    }

    public Item(String nome, double preco) {
        this(0, nome, "Sem descrição", preco, 0.5, TipoItem.OUTRO, RaridadeItem.COMUM, 1);
    }

    public Item(int id, String nome, double preco) {
        this(id, nome, "Sem descrição", preco, 0.5, TipoItem.OUTRO, RaridadeItem.COMUM, 1);
    }

    public Item(int id, String nome, String descricao, double preco, double peso, TipoItem tipo, RaridadeItem raridade, int quantidade) {
        this.id = id;
        this.nome = (nome != null && !nome.trim().isEmpty()) ? nome : "Item Sem Nome";
        this.descricao = (descricao != null) ? descricao : "";
        this.preco = Math.max(0.0, preco);
        this.peso = Math.max(0.0, peso);
        this.tipo = (tipo != null) ? tipo : TipoItem.OUTRO;
        this.raridade = (raridade != null) ? raridade : RaridadeItem.COMUM;
        this.quantidade = Math.max(1, quantidade);
        this.tamanho = inferirTamanhoPadrao(this.tipo, this.peso);
    }

    public Item(int id, String nome, String descricao, double preco, double peso, TipoItem tipo, RaridadeItem raridade, int quantidade, TamanhoItem tamanho) {
        this(id, nome, descricao, preco, peso, tipo, raridade, quantidade);
        if (tamanho != null) {
            this.tamanho = tamanho;
        }
    }

    private static TamanhoItem inferirTamanhoPadrao(TipoItem tipo, double peso) {
        if (tipo == TipoItem.ACESSORIO) return TamanhoItem.MINUSCULO;
        if (tipo == TipoItem.CONSUMIVEL || tipo == TipoItem.PROJETIL) return TamanhoItem.PEQUENO;
        if (peso >= 15.0) return TamanhoItem.GRANDE;
        if (peso <= 0.8) return TamanhoItem.PEQUENO;
        return TamanhoItem.MEDIO;
    }

    public void adicionarQuantidade(int qtd) {
        if (qtd > 0) {
            this.quantidade += qtd;
        }
    }

    public boolean removerQuantidade(int qtd) {
        if (qtd > 0 && this.quantidade >= qtd) {
            this.quantidade -= qtd;
            return true;
        }
        return false;
    }

    public double getPesoTotal() {
        return this.peso * this.quantidade;
    }

    public double getValorTotal() {
        return this.preco * this.quantidade;
    }

    public double getEspacoTotal() {
        TamanhoItem t = (this.tamanho != null) ? this.tamanho : TamanhoItem.MEDIO;
        return t.getSlotsMochila() * this.quantidade;
    }

    /**
     * Regra do Camarão no Litoral (Precificação Geográfica e Exotismo):
     * - Cidade nativa/abundante: 0.80x (-20% desconto local);
     * - Bioma oposto / exótico: 1.85x (+85% valorização de especiaria);
     * - Bioma neutro ou próximo: 1.25x (+25%).
     * Se o item não possuir origem geográfica declarada, retorna 1.0 (neutro v3).
     */
    public double calcularMultiplicadorRegional(Cidade cidade) {
        if (cidade == null || (regiaoOrigem == null && geologiaOrigem == null && aquiferoOrigem == null)) {
            return 1.0;
        }

        boolean matchRegiao = (regiaoOrigem == null || cidade.getRegiao() == regiaoOrigem
                || (cidade.isFronteira() && cidade.getRegiaofronteira() == regiaoOrigem));
        boolean matchGeologia = (geologiaOrigem == null || cidade.getGeologia() == geologiaOrigem);
        boolean matchAquifero = (aquiferoOrigem == null || cidade.getAquifero() == aquiferoOrigem);

        // Abundância local na cidade nativa
        if (matchRegiao && matchGeologia && matchAquifero) {
            return 0.80;
        }

        // Bioma oposto / Exotismo extremo (ex: produto de Tundra em Deserto, ou marítimo em deserto árido)
        boolean opostoClima = (regiaoOrigem == Regiao.TUNDRA && cidade.getRegiao() == Regiao.DESERTO)
                || (regiaoOrigem == Regiao.DESERTO && cidade.getRegiao() == Regiao.TUNDRA);
        boolean costeiroEmDeserto = (aquiferoOrigem == Aquifero.OCEANO_AGUAS_COSTEIRAS
                && cidade.getRegiao() == Regiao.DESERTO);

        if (opostoClima || costeiroEmDeserto) {
            return 1.85;
        }

        // Bioma neutro ou próximo
        return 1.25;
    }

    /**
     * Preço unitário convertido para latão (menor unidade do sistema monetário).
     */
    public long getPrecoEmLatao() {
        return Carteira.ouroParaLatao(this.preco);
    }

    /**
     * Valor total do estoque deste item (preço × quantidade), em latão.
     */
    public long getValorTotalEmLatao() {
        return getPrecoEmLatao() * this.quantidade;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public double getPreco() { return preco; }
    public void setPreco(double preco) { this.preco = Math.max(0.0, preco); }

    public double getPeso() { return peso; }
    public void setPeso(double peso) { this.peso = Math.max(0.0, peso); }

    public TipoItem getTipo() { return tipo; }
    public void setTipo(TipoItem tipo) { this.tipo = tipo; }

    public RaridadeItem getRaridade() { return raridade; }
    public void setRaridade(RaridadeItem raridade) { this.raridade = raridade; }

    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = Math.max(0, quantidade); }

    public TamanhoItem getTamanho() { return tamanho; }
    public void setTamanho(TamanhoItem tamanho) { this.tamanho = (tamanho != null) ? tamanho : TamanhoItem.MEDIO; }

    public Regiao getRegiaoOrigem() { return regiaoOrigem; }
    public void setRegiaoOrigem(Regiao regiaoOrigem) { this.regiaoOrigem = regiaoOrigem; }

    public Geologia getGeologiaOrigem() { return geologiaOrigem; }
    public void setGeologiaOrigem(Geologia geologiaOrigem) { this.geologiaOrigem = geologiaOrigem; }

    public Aquifero getAquiferoOrigem() { return aquiferoOrigem; }
    public void setAquiferoOrigem(Aquifero aquiferoOrigem) { this.aquiferoOrigem = aquiferoOrigem; }

    public void setOrigemGeografica(Regiao regiao, Geologia geologia, Aquifero aquifero) {
        this.regiaoOrigem = regiao;
        this.geologiaOrigem = geologia;
        this.aquiferoOrigem = aquifero;
    }

    // Getters e Setters de Fichas Modulares
    public FichaMaterial getFichaMaterial() { return fichaMaterial; }
    public void setFichaMaterial(FichaMaterial fichaMaterial) { this.fichaMaterial = fichaMaterial; }

    public FichaArmaBranca getFichaArmaBranca() { return fichaArmaBranca; }
    public void setFichaArmaBranca(FichaArmaBranca fichaArmaBranca) { this.fichaArmaBranca = fichaArmaBranca; }

    public FichaArmaLongoAlcance getFichaArmaLongoAlcance() { return fichaArmaLongoAlcance; }
    public void setFichaArmaLongoAlcance(FichaArmaLongoAlcance fichaArmaLongoAlcance) { this.fichaArmaLongoAlcance = fichaArmaLongoAlcance; }

    public FichaArmadura getFichaArmadura() { return fichaArmadura; }
    public void setFichaArmadura(FichaArmadura fichaArmadura) { this.fichaArmadura = fichaArmadura; }

    public FichaEscudo getFichaEscudo() { return fichaEscudo; }
    public void setFichaEscudo(FichaEscudo fichaEscudo) { this.fichaEscudo = fichaEscudo; }

    public FichaAcessorio getFichaAcessorio() { return fichaAcessorio; }
    public void setFichaAcessorio(FichaAcessorio fichaAcessorio) { this.fichaAcessorio = fichaAcessorio; }

    public FichaConsumivel getFichaConsumivel() { return fichaConsumivel; }
    public void setFichaConsumivel(FichaConsumivel fichaConsumivel) { this.fichaConsumivel = fichaConsumivel; }

    public FichaGrimorio getFichaGrimorio() { return fichaGrimorio; }
    public void setFichaGrimorio(FichaGrimorio fichaGrimorio) { this.fichaGrimorio = fichaGrimorio; }

    // --- Métodos de Drop de Monstro e Qualidade de Crafting (Item v4.1) ---
    public void configurarComoDropDeMonstro(String nomeMonstro, RaridadeItem raridadeDrop,
                                            Geologia geologiaMonstro, Regiao regiaoMonstro,
                                            Aquifero aquiferoMonstro, java.util.Random random) {
        this.origemItem = OrigemItem.COLETAVEL_OU_DROP;
        this.qualidadeCraft = null;
        this.monstroOrigem = nomeMonstro;
        if (raridadeDrop != null) {
            this.raridade = raridadeDrop;
        }
        this.geologiaOrigem = geologiaMonstro;
        this.regiaoOrigem = regiaoMonstro;
        this.aquiferoOrigem = aquiferoMonstro;

        if (this.fichaMaterial != null && random != null) {
            this.fichaMaterial.sortearEfeitoNoDrop(random);
        }
    }

    public void aplicarQualidadeCraft(QualidadeCraft qualidade) {
        if (qualidade == null) return;
        this.origemItem = OrigemItem.MANUFATURADO_CRAFT;
        this.qualidadeCraft = qualidade;

        this.peso = Math.max(0.05, Math.round((this.peso * qualidade.getMultiplicadorPeso()) * 100.0) / 100.0);
        this.preco = Math.max(0.01, Math.round((this.preco * qualidade.getMultiplicadorPrecoBase()) * 100.0) / 100.0);

        if (this.fichaArmaBranca != null) {
            this.fichaArmaBranca.aplicarModificadorQualidade(qualidade);
        }
        if (this.fichaArmaLongoAlcance != null) {
            this.fichaArmaLongoAlcance.aplicarModificadorQualidade(qualidade);
        }
        if (this.fichaArmadura != null) {
            this.fichaArmadura.aplicarModificadorQualidade(qualidade);
        }
        if (this.fichaEscudo != null) {
            this.fichaEscudo.aplicarModificadorQualidade(qualidade);
        }
        if (this.fichaConsumivel != null) {
            this.fichaConsumivel.aplicarModificadorQualidade(qualidade);
        }
        if (this.fichaGrimorio != null) {
            this.fichaGrimorio.aplicarModificadorQualidade(qualidade);
        }
    }

    public boolean isCraftado() {
        return origemItem == OrigemItem.MANUFATURADO_CRAFT && qualidadeCraft != null;
    }

    public String getClassificacaoFormatada() {
        if (isCraftado()) {
            return "Qualidade: " + qualidadeCraft.getNomeFormatado();
        }
        return "Raridade: " + raridade.getNomeFormatado();
    }

    public OrigemItem getOrigemItem() { return origemItem; }
    public void setOrigemItem(OrigemItem origemItem) { this.origemItem = (origemItem != null) ? origemItem : OrigemItem.COLETAVEL_OU_DROP; }

    public QualidadeCraft getQualidadeCraft() { return qualidadeCraft; }
    public void setQualidadeCraft(QualidadeCraft qualidadeCraft) { this.qualidadeCraft = qualidadeCraft; }

    public String getMonstroOrigem() { return monstroOrigem; }
    public void setMonstroOrigem(String monstroOrigem) { this.monstroOrigem = monstroOrigem; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Item item = (Item) o;
        return id == item.id && Objects.equals(nome, item.nome);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, nome);
    }

    @Override
    public String toString() {
        String infoTamanho = (tamanho != null) ? ", " + tamanho.getNomeFormatado() : "";
        return String.format("%s [x%d] (%s, %.1f kg%s) - %s (%s)",
            nome, quantidade, Carteira.formatarLatao(getPrecoEmLatao()), peso, infoTamanho, tipo, getClassificacaoFormatada());
    }
}
