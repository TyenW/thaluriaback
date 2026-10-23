package economia;

import java.util.*;

/**
 * Representa uma receita universal de manufatura para uma das 5 áreas de Crafting em Thaluria:
 * Forja, Alfaiataria, Joalheria, Taverna e Botânica/Alquimia.
 * Guarda cabeçalho, insumos exigidos e o template da ficha técnica a ser gerada.
 */
public class MoldeReceita {

    public static class RequisitoInsumo {
        private final FichaMaterial.SubtipoMaterial subtipoExigido;
        private final int quantidade;
        private final int qualidadeMinima; // 1 a 5
        private final boolean defineSufixoPrincipal;
        private final boolean slotCatalisadorOpcional;

        public RequisitoInsumo(FichaMaterial.SubtipoMaterial subtipoExigido, int quantidade,
                               int qualidadeMinima, boolean defineSufixoPrincipal, boolean slotCatalisadorOpcional) {
            this.subtipoExigido = (subtipoExigido != null) ? subtipoExigido : FichaMaterial.SubtipoMaterial.MINERIO_BRUTO;
            this.quantidade = Math.max(1, quantidade);
            this.qualidadeMinima = Math.max(1, Math.min(5, qualidadeMinima));
            this.defineSufixoPrincipal = defineSufixoPrincipal;
            this.slotCatalisadorOpcional = slotCatalisadorOpcional;
        }

        public FichaMaterial.SubtipoMaterial getSubtipoExigido() { return subtipoExigido; }
        public int getQuantidade() { return quantidade; }
        public int getQualidadeMinima() { return qualidadeMinima; }
        public boolean isDefineSufixoPrincipal() { return defineSufixoPrincipal; }
        public boolean isSlotCatalisadorOpcional() { return slotCatalisadorOpcional; }

        @Override
        public String toString() {
            return String.format("%dx %s (Q%d+)%s%s", quantidade, subtipoExigido, qualidadeMinima,
                    defineSufixoPrincipal ? " [Principal]" : "",
                    slotCatalisadorOpcional ? " [Opcional/Catalisador]" : "");
        }
    }

    private final int idMolde;
    private final String nomeBaseItem;
    private final Lojas.TipoEstabelecimento areaCrafting;
    private final int nivelReceita; // 1 a 5
    private final Item.TipoItem tipoItemGerado;
    private final double pesoBaseKg;
    private final TamanhoItem tamanhoItem;
    private final long custoMaoDeObraEmLatao;
    private final List<RequisitoInsumo> requisitos = new ArrayList<>();
    private final List<FiltroInsumoReceita> filtrosInsumo = new ArrayList<>();

    // Template para ARMA_BRANCA
    private FichaArmaBranca.SubtipoArmaBranca subtipoArmaBranca;
    private FichaArmaBranca.Empunhadura empunhaduraArmaBranca;
    private int qtdDadosArmaBranca;
    private int facesDadoBaseArmaBranca;

    // Template para ARMA_LONGO_ALCANCE
    private FichaArmaLongoAlcance.SubtipoArmaDistante subtipoArmaDistante;
    private double amplificacaoBaseDistante;
    private int alcanceMetrosBaseDistante;

    // Template para ARMADURA
    private final EnumSet<FichaArmadura.SlotArmadura> slotsArmadura = EnumSet.noneOf(FichaArmadura.SlotArmadura.class);
    private int rdBaseArmadura;
    private int durabilidadeBaseArmadura;
    private final Map<String, Integer> bonusStatusArmadura = new LinkedHashMap<>();

    // Template para ESCUDO
    private FichaArmaBranca.Empunhadura empunhaduraEscudo;
    private int hpBaseEscudo;
    private int limiteGolpeBaseEscudo;

    // Template para ACESSORIO
    private FichaAcessorio.SlotAcessorio slotAcessorio;
    private final Map<String, Integer> bonusAtributosAcessorio = new LinkedHashMap<>();
    private PassivaItem passivaBaseAcessorio;
    private final List<String> ataquesBaseAcessorio = new ArrayList<>();

    // Template para CONSUMIVEL
    private int restauracaoVidaBase;
    private int restauracaoManaBase;
    private int restauracaoVigorBase;
    private final List<String> condicoesCuradasBase = new ArrayList<>();
    private final List<EfeitoItem> efeitosConsumivelBase = new ArrayList<>();

    // Template para MAGICO (Grimório)
    private int nivelArcanoGrimorio;
    private int paginasBaseGrimorio;

    private MoldeReceita(int idMolde, String nomeBaseItem, Lojas.TipoEstabelecimento areaCrafting,
                         int nivelReceita, Item.TipoItem tipoItemGerado, double pesoBaseKg,
                         TamanhoItem tamanhoItem, long custoMaoDeObraEmLatao) {
        this.idMolde = idMolde;
        this.nomeBaseItem = (nomeBaseItem != null && !nomeBaseItem.isBlank()) ? nomeBaseItem.trim() : "Item Manufaturado";
        this.areaCrafting = (areaCrafting != null) ? areaCrafting : Lojas.TipoEstabelecimento.FORJA;
        this.nivelReceita = Math.max(1, Math.min(5, nivelReceita));
        this.tipoItemGerado = (tipoItemGerado != null) ? tipoItemGerado : Item.TipoItem.OUTRO;
        this.pesoBaseKg = Math.max(0.05, pesoBaseKg);
        this.tamanhoItem = (tamanhoItem != null) ? tamanhoItem : TamanhoItem.MEDIO;
        this.custoMaoDeObraEmLatao = Math.max(0L, custoMaoDeObraEmLatao);
    }

    private int diasParaConcluirBase = 0;

    public int getDiasParaConcluirBase() {
        if (diasParaConcluirBase <= 0) {
            return switch (tipoItemGerado) {
                case CONSUMIVEL -> 1;
                case ARMA_BRANCA, ARMA_LONGO_ALCANCE, ESCUDO -> Math.max(1, nivelReceita);
                case ARMADURA -> Math.max(2, nivelReceita + 1);
                default -> Math.max(1, nivelReceita);
            };
        }
        return diasParaConcluirBase;
    }

    public void setDiasParaConcluirBase(int dias) {
        this.diasParaConcluirBase = Math.max(1, dias);
    }

    public MoldeReceita adicionarRequisito(RequisitoInsumo requisito) {
        if (requisito != null) {
            this.requisitos.add(requisito);
            this.filtrosInsumo.add(FiltroInsumoReceita.qualquerDoSubsistema(
                    requisito.getSubtipoExigido(), requisito.getQualidadeMinima(),
                    requisito.getQuantidade(), requisito.isDefineSufixoPrincipal(),
                    requisito.isSlotCatalisadorOpcional()
            ));
        }
        return this;
    }

    public MoldeReceita adicionarFiltroInsumo(FiltroInsumoReceita filtro) {
        if (filtro != null) {
            this.filtrosInsumo.add(filtro);
            this.requisitos.add(new RequisitoInsumo(
                    filtro.getSubtipoExigido(), filtro.getQuantidadeExigida(),
                    filtro.getQualidadeMinima(), filtro.isDefineNomePrincipal(),
                    filtro.isSlotCatalisadorOpcional()
            ));
        }
        return this;
    }

    public MoldeReceita adicionarRequisito(FichaMaterial.SubtipoMaterial subtipo, int qtd, int qualidadeMin,
                                           boolean principal, boolean opcional) {
        return adicionarRequisito(new RequisitoInsumo(subtipo, qtd, qualidadeMin, principal, opcional));
    }

    public List<FiltroInsumoReceita> getFiltrosInsumo() {
        return Collections.unmodifiableList(filtrosInsumo);
    }

    // --- MÉTODOS FÁBRICA ESTÁTICOS ---

    public static MoldeReceita criarMoldeArmaBranca(int id, String nome, int nivel, double pesoKg,
                                                     TamanhoItem tamanho, long custoMaoDeObra,
                                                     FichaArmaBranca.SubtipoArmaBranca subtipo) {
        MoldeReceita m = new MoldeReceita(id, nome, Lojas.TipoEstabelecimento.FORJA, nivel,
                Item.TipoItem.ARMA_BRANCA, pesoKg, tamanho, custoMaoDeObra);
        m.subtipoArmaBranca = (subtipo != null) ? subtipo : FichaArmaBranca.SubtipoArmaBranca.ESPADA_LONGA;
        m.empunhaduraArmaBranca = m.subtipoArmaBranca.getEmpunhaduraPadrao();
        m.qtdDadosArmaBranca = m.subtipoArmaBranca.getQtdDadosPadrao();
        m.facesDadoBaseArmaBranca = m.subtipoArmaBranca.getFacesDadoPadrao();
        return m;
    }

    public static MoldeReceita criarMoldeArmaDistante(int id, String nome, int nivel, double pesoKg,
                                                       TamanhoItem tamanho, long custoMaoDeObra,
                                                       FichaArmaLongoAlcance.SubtipoArmaDistante subtipo) {
        MoldeReceita m = new MoldeReceita(id, nome, Lojas.TipoEstabelecimento.ALFAIATARIA, nivel,
                Item.TipoItem.ARMA_LONGO_ALCANCE, pesoKg, tamanho, custoMaoDeObra);
        m.subtipoArmaDistante = (subtipo != null) ? subtipo : FichaArmaLongoAlcance.SubtipoArmaDistante.ARCO_LONGO;
        m.amplificacaoBaseDistante = m.subtipoArmaDistante.getAmplificacaoBase();
        m.alcanceMetrosBaseDistante = m.subtipoArmaDistante.getAlcanceMetrosBase();
        return m;
    }

    public static MoldeReceita criarMoldeArmadura(int id, String nome, Lojas.TipoEstabelecimento area,
                                                   int nivel, double pesoKg, TamanhoItem tamanho,
                                                   long custoMaoDeObra, Collection<FichaArmadura.SlotArmadura> slots,
                                                   int rdBase, int durabilidadeBase) {
        MoldeReceita m = new MoldeReceita(id, nome, area, nivel, Item.TipoItem.ARMADURA, pesoKg, tamanho, custoMaoDeObra);
        if (slots != null && !slots.isEmpty()) {
            m.slotsArmadura.addAll(slots);
        } else {
            m.slotsArmadura.add(FichaArmadura.SlotArmadura.PEITO);
        }
        m.rdBaseArmadura = Math.max(1, rdBase);
        m.durabilidadeBaseArmadura = Math.max(1, durabilidadeBase);
        return m;
    }

    public static MoldeReceita criarMoldeEscudo(int id, String nome, int nivel, double pesoKg,
                                                TamanhoItem tamanho, long custoMaoDeObra,
                                                FichaArmaBranca.Empunhadura empunhadura, int hpBase, int limiteGolpeBase) {
        MoldeReceita m = new MoldeReceita(id, nome, Lojas.TipoEstabelecimento.FORJA, nivel,
                Item.TipoItem.ESCUDO, pesoKg, tamanho, custoMaoDeObra);
        m.empunhaduraEscudo = (empunhadura != null) ? empunhadura : FichaArmaBranca.Empunhadura.UMA_MAO;
        m.hpBaseEscudo = Math.max(10, hpBase);
        m.limiteGolpeBaseEscudo = Math.max(2, limiteGolpeBase);
        return m;
    }

    public static MoldeReceita criarMoldeAcessorio(int id, String nome, int nivel, double pesoKg,
                                                   TamanhoItem tamanho, long custoMaoDeObra,
                                                   FichaAcessorio.SlotAcessorio slot,
                                                   Map<String, Integer> bonusAtributos,
                                                   PassivaItem passivaBase) {
        MoldeReceita m = new MoldeReceita(id, nome, Lojas.TipoEstabelecimento.JOALHERIA, nivel,
                Item.TipoItem.ACESSORIO, pesoKg, tamanho, custoMaoDeObra);
        m.slotAcessorio = (slot != null) ? slot : FichaAcessorio.SlotAcessorio.ANEIS;
        if (bonusAtributos != null) {
            m.bonusAtributosAcessorio.putAll(bonusAtributos);
        }
        m.passivaBaseAcessorio = passivaBase;
        return m;
    }

    public static MoldeReceita criarMoldeConsumivel(int id, String nome, Lojas.TipoEstabelecimento area,
                                                    int nivel, double pesoKg, TamanhoItem tamanho,
                                                    long custoMaoDeObra, int vida, int mana, int vigor) {
        MoldeReceita m = new MoldeReceita(id, nome, area, nivel, Item.TipoItem.CONSUMIVEL, pesoKg, tamanho, custoMaoDeObra);
        m.restauracaoVidaBase = Math.max(0, vida);
        m.restauracaoManaBase = Math.max(0, mana);
        m.restauracaoVigorBase = Math.max(0, vigor);
        return m;
    }

    public static MoldeReceita criarMoldeGrimorio(int id, String nome, int nivel, double pesoKg,
                                                  TamanhoItem tamanho, long custoMaoDeObra,
                                                  int nivelArcano, int paginasBase) {
        MoldeReceita m = new MoldeReceita(id, nome, Lojas.TipoEstabelecimento.ALFAIATARIA, nivel,
                Item.TipoItem.MAGICO, pesoKg, tamanho, custoMaoDeObra);
        m.nivelArcanoGrimorio = Math.max(1, Math.min(5, nivelArcano));
        m.paginasBaseGrimorio = Math.max(1, paginasBase);
        return m;
    }

    // --- GETTERS ---
    public int getIdMolde() { return idMolde; }
    public String getNomeBaseItem() { return nomeBaseItem; }
    public Lojas.TipoEstabelecimento getAreaCrafting() { return areaCrafting; }
    public int getNivelReceita() { return nivelReceita; }
    public Item.TipoItem getTipoItemGerado() { return tipoItemGerado; }
    public double getPesoBaseKg() { return pesoBaseKg; }
    public TamanhoItem getTamanhoItem() { return tamanhoItem; }
    public long getCustoMaoDeObraEmLatao() { return custoMaoDeObraEmLatao; }
    public List<RequisitoInsumo> getRequisitos() { return Collections.unmodifiableList(requisitos); }

    public FichaArmaBranca.SubtipoArmaBranca getSubtipoArmaBranca() { return subtipoArmaBranca; }
    public FichaArmaBranca.Empunhadura getEmpunhaduraArmaBranca() { return empunhaduraArmaBranca; }
    public int getQtdDadosArmaBranca() { return qtdDadosArmaBranca; }
    public int getFacesDadoBaseArmaBranca() { return facesDadoBaseArmaBranca; }

    public FichaArmaLongoAlcance.SubtipoArmaDistante getSubtipoArmaDistante() { return subtipoArmaDistante; }
    public double getAmplificacaoBaseDistante() { return amplificacaoBaseDistante; }
    public int getAlcanceMetrosBaseDistante() { return alcanceMetrosBaseDistante; }

    public Set<FichaArmadura.SlotArmadura> getSlotsArmadura() { return Collections.unmodifiableSet(slotsArmadura); }
    public int getRdBaseArmadura() { return rdBaseArmadura; }
    public int getDurabilidadeBaseArmadura() { return durabilidadeBaseArmadura; }
    public Map<String, Integer> getBonusStatusArmadura() { return Collections.unmodifiableMap(bonusStatusArmadura); }

    public FichaArmaBranca.Empunhadura getEmpunhaduraEscudo() { return empunhaduraEscudo; }
    public int getHpBaseEscudo() { return hpBaseEscudo; }
    public int getLimiteGolpeBaseEscudo() { return limiteGolpeBaseEscudo; }

    public FichaAcessorio.SlotAcessorio getSlotAcessorio() { return slotAcessorio; }
    public Map<String, Integer> getBonusAtributosAcessorio() { return Collections.unmodifiableMap(bonusAtributosAcessorio); }
    public PassivaItem getPassivaBaseAcessorio() { return passivaBaseAcessorio; }
    public List<String> getAtaquesBaseAcessorio() { return Collections.unmodifiableList(ataquesBaseAcessorio); }

    public int getRestauracaoVidaBase() { return restauracaoVidaBase; }
    public int getRestauracaoManaBase() { return restauracaoManaBase; }
    public int getRestauracaoVigorBase() { return restauracaoVigorBase; }
    public List<String> getCondicoesCuradasBase() { return Collections.unmodifiableList(condicoesCuradasBase); }
    public List<EfeitoItem> getEfeitosConsumivelBase() { return Collections.unmodifiableList(efeitosConsumivelBase); }

    public int getNivelArcanoGrimorio() { return nivelArcanoGrimorio; }
    public int getPaginasBaseGrimorio() { return paginasBaseGrimorio; }
}
