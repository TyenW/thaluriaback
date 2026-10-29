package economia;

import geografia.Cidade;
import java.util.*;

/**
 * Representa uma loja ou entreposto comercial em um assentamento de Thaluria.
 * Integra o controle de estoque, ofertas e demandas dinâmicas calculadas em Latão,
 * compras de fornecedores NPCs, livro-caixa com histórico e comércio intermunicipal.
 */
public class Lojas {

    public enum TipoEstabelecimento {
        FORJA("Forja / Ferraria"),
        ALFAIATARIA("Alfaiataria / Tecelagem"),
        LOJA_GERAL("Loja Geral / Empório"),
        ALQUIMIA("Alquimia / Botica"),
        JOALHERIA("Joalheria"),
        TAVERNA("Taverna / Comidas e Bebidas"),
        BOTANICA("Botânica / Alquimia");

        private final String nomeFormatado;

        TipoEstabelecimento(String nomeFormatado) {
            this.nomeFormatado = nomeFormatado;
        }

        public String getNomeFormatado() {
            return nomeFormatado;
        }

        @Override
        public String toString() {
            return nomeFormatado;
        }
    }

    private int id;
    private String nome;
    private String mercador;
    private boolean lojaDeRede;
    private String nomeDaRede;
    private final List<Item> estoque = new ArrayList<>();
    private Carteira caixa;

    // Vínculos geográficos e de especialização
    private Cidade cidadeSede;
    private TipoEstabelecimento tipoEstabelecimento;
    private int nivelLoja = 3; // Nível da oficina (1 a 5), padrão 3 para compatibilidade v3
    private final List<MoldeReceita> livroDeReceitas = new ArrayList<>();
    private GeradorMineracaoForja geradorMineracao;

    // V6: Gestão Avançada de Lojas, Prateleiras, Fila de Produção e Talentos
    private final ArvoreEvolucaoLoja arvoreEvolucao = new ArvoreEvolucaoLoja();
    private final Set<TalentoLojista> talentos = new LinkedHashSet<>();
    private final List<OrdemProducao> filaProducao = new ArrayList<>();
    private final List<LotePrateleira> lotesPrateleira = new ArrayList<>();
    private int capacidadeSlotsArmazemBase = 50;
    private int capacidadeSlotsPrateleiraBase = 30;
    private int proximoIdOrdem = 1;
    private int contadorDiasCamaraConservacao = 0;

    // Métricas de Mercado, Oferta e Demanda
    private final Map<Integer, Item> catalogoConhecido = new LinkedHashMap<>();
    private final Map<Integer, Integer> demandaPorItem = new LinkedHashMap<>();
    private final Map<Integer, Integer> entradasRecentes = new LinkedHashMap<>();
    private final Map<Integer, Integer> saidasRecentes = new LinkedHashMap<>();
    private final List<RegistroMovimentacao> historico = new ArrayList<>();
    /** Quantos registros do histórico já foram gravados no livro-caixa do banco. */
    private int historicoPersistido = 0;

    // Produção por receitas do catálogo (ReceitaLoja), persistida em loja_ordens_producao
    private final List<OrdemReceita> ordensReceita = new ArrayList<>();

    public Lojas() {
        this("Empório Geral", "Mercador Local", false, null, TipoEstabelecimento.LOJA_GERAL);
    }

    public Lojas(String nome, String mercador) {
        this(nome, mercador, false, null, TipoEstabelecimento.LOJA_GERAL);
    }

    public Lojas(String nome, String mercador, boolean lojaDeRede, String nomeDaRede) {
        this(nome, mercador, lojaDeRede, nomeDaRede, TipoEstabelecimento.LOJA_GERAL);
    }

    public Lojas(String nome, String mercador, boolean lojaDeRede, String nomeDaRede, TipoEstabelecimento tipo) {
        this(nome, mercador, null, lojaDeRede, nomeDaRede, tipo);
    }

    public Lojas(String nome, String mercador, Cidade cidadeSede, boolean lojaDeRede, String nomeDaRede) {
        this(nome, mercador, cidadeSede, lojaDeRede, nomeDaRede, TipoEstabelecimento.LOJA_GERAL);
    }

    public Lojas(String nome, String mercador, Cidade cidadeSede, boolean lojaDeRede, String nomeDaRede, TipoEstabelecimento tipo) {
        this.nome = (nome != null && !nome.trim().isEmpty()) ? nome : "Loja Sem Nome";
        this.mercador = (mercador != null && !mercador.trim().isEmpty()) ? mercador : "Mercador Itinerante";
        this.lojaDeRede = lojaDeRede;
        this.nomeDaRede = lojaDeRede ? nomeDaRede : null;
        this.tipoEstabelecimento = (tipo != null) ? tipo : TipoEstabelecimento.LOJA_GERAL;
        this.caixa = new Carteira(50); // Fundo de caixa padrão de 50 PO (500.000 latão na v2)
        this.cidadeSede = cidadeSede;
        if (cidadeSede != null) {
            cidadeSede.adicionarLoja(this);
        }
        if (this.tipoEstabelecimento == TipoEstabelecimento.FORJA) {
            this.geradorMineracao = new GeradorMineracaoForja();
        }
    }

    /**
     * Adiciona um item ao estoque da loja, clonando-o para evitar efeitos colaterais
     * e registrando o item no catálogo de mercadorias conhecidas do estabelecimento.
     */
    public void adicionarItem(Item item) {
        if (item == null) return;
        catalogoConhecido.putIfAbsent(item.getId(), clonarItem(item, 1));
        for (Item existente : estoque) {
            if (existente.getId() == item.getId() || existente.equals(item)) {
                existente.adicionarQuantidade(item.getQuantidade());
                return;
            }
        }
        estoque.add(clonarItem(item, item.getQuantidade()));
    }

    public boolean removerItem(Item item) {
        return this.estoque.remove(item);
    }

    public boolean removerUnidades(int idItem, int qtd) {
        if (qtd <= 0) return false;
        Iterator<Item> it = estoque.iterator();
        while (it.hasNext()) {
            Item atual = it.next();
            if (atual.getId() == idItem) {
                if (atual.getQuantidade() < qtd) return false;
                atual.removerQuantidade(qtd);
                if (atual.getQuantidade() <= 0) {
                    it.remove();
                }
                return true;
            }
        }
        return false;
    }

    public boolean removerItem(int idItem, int qtd) {
        return removerUnidades(idItem, qtd);
    }

    public int getQuantidadeEstoque(int idItem) {
        for (Item item : estoque) {
            if (item.getId() == idItem) {
                return item.getQuantidade();
            }
        }
        return 0;
    }

    public Item buscarItemPorId(int idItem) {
        for (Item item : estoque) {
            if (item.getId() == idItem) {
                return item;
            }
        }
        return null;
    }

    public Item buscarItemPorNome(String nomeItem) {
        if (nomeItem == null) return null;
        for (Item item : estoque) {
            if (item.getNome().equalsIgnoreCase(nomeItem.trim())) {
                return item;
            }
        }
        return null;
    }

    /**
     * REQUISITO 6: Preço dinâmico de venda calculado em Latão (long).
     * Cruza estoque atual, entradas recentes, saídas recentes e demanda projetada.
     * Variação limitada de 40% (excesso severo) a 250% (alta escassez) do preço base.
     */
    public long calcularPrecoDinamicoVendaEmLatao(Item itemBase) {
        if (itemBase == null) return 1L;
        int id = itemBase.getId();
        int qtdEstoque = getQuantidadeEstoque(id);
        int demanda = demandaPorItem.getOrDefault(id, 5);
        int entradas = entradasRecentes.getOrDefault(id, 0);
        int saidas = saidasRecentes.getOrDefault(id, 0);

        double pressaoDemanda = demanda + (saidas * 0.5) + 1.0;
        double pressaoOferta = qtdEstoque + (entradas * 0.3) + 1.0;

        double multiplicador = Math.sqrt(pressaoDemanda / pressaoOferta);
        multiplicador = Math.max(0.40, Math.min(2.50, multiplicador));

        double multRegional = (cidadeSede != null) ? itemBase.calcularMultiplicadorRegional(cidadeSede) : 1.0;
        return Math.max(1L, Math.round(itemBase.getPrecoEmLatao() * multiplicador * multRegional));
    }

    /**
     * Preço de compra que a loja paga a fornecedores NPCs locais (65% do preço dinâmico de venda).
     */
    public long calcularPrecoCompraNpcEmLatao(Item itemBase) {
        return Math.max(1L, Math.round(calcularPrecoDinamicoVendaEmLatao(itemBase) * 0.65));
    }

    /**
     * REQUISITO 1: Compra automática de mercadorias de fornecedores NPCs usando latão v2.
     */
    public boolean comprarDeNpc(Item item, int quantidade, int dia, int hora) {
        if (item == null || quantidade <= 0) return false;
        catalogoConhecido.putIfAbsent(item.getId(), clonarItem(item, 1));

        long precoUnitarioLatao = calcularPrecoCompraNpcEmLatao(item);
        long custoTotalLatao = precoUnitarioLatao * quantidade;

        if (!caixa.pagarLatao(custoTotalLatao)) {
            return false;
        }

        adicionarItem(clonarItem(item, quantidade));
        entradasRecentes.merge(item.getId(), quantidade, Integer::sum);

        registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.ENTRADA_NPC,
                item.getNome(), quantidade, custoTotalLatao, "Compra de fornecedor NPC local");
        return true;
    }

    /**
     * Venda de balcão (compatibilidade com assinatura padrão).
     */
    public boolean venderItem(Item item, Carteira carteiraCliente) {
        return venderItem(item, carteiraCliente, 1, 12);
    }

    /**
     * Venda de balcão com timestamp do mundo.
     */
    public boolean venderItem(Item item, Carteira carteiraCliente, int dia, int hora) {
        if (item == null || carteiraCliente == null) return false;

        Item noEstoque = (item.getId() != 0) ? buscarItemPorId(item.getId()) : null;
        if (noEstoque == null) {
            noEstoque = buscarItemPorNome(item.getNome());
        }
        if (noEstoque == null && estoque.contains(item)) {
            noEstoque = item;
        }
        if (noEstoque == null || noEstoque.getQuantidade() <= 0) return false;

        long precoDinamicoLatao = calcularPrecoDinamicoVendaEmLatao(noEstoque);
        if (!carteiraCliente.pagarLatao(precoDinamicoLatao)) {
            return false;
        }

        caixa.receberLatao(precoDinamicoLatao);
        removerUnidades(noEstoque.getId(), 1);
        saidasRecentes.merge(noEstoque.getId(), 1, Integer::sum);
        demandaPorItem.computeIfPresent(noEstoque.getId(), (k, v) -> Math.max(0, v - 1));

        registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.VENDA_BALCAO,
                noEstoque.getNome(), 1, precoDinamicoLatao, "Venda balcão para cliente");
        return true;
    }

    /**
     * Compra no balcão (jogador vendendo mercadoria para a loja).
     */
    public boolean comprarItem(Item item, Carteira carteiraCliente, double precoCompraEmOuro) {
        return comprarItem(item, carteiraCliente, precoCompraEmOuro, 1, 12);
    }

    /**
     * Compra no balcão com dia e hora especificados.
     */
    public boolean comprarItem(Item item, Carteira carteiraCliente, double precoCompraEmOuro, int dia, int hora) {
        if (item == null || carteiraCliente == null || precoCompraEmOuro < 0) return false;
        long valorLatao = Carteira.ouroParaLatao(precoCompraEmOuro);

        if (!caixa.pagarLatao(valorLatao)) return false;

        carteiraCliente.receberLatao(valorLatao);
        adicionarItem(clonarItem(item, 1));
        entradasRecentes.merge(item.getId(), 1, Integer::sum);

        registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.COMPRA_BALCAO,
                item.getNome(), 1, valorLatao, "Compra balcão de aventureiro");
        return true;
    }

    /**
     * Um aventureiro vende {@code qtd} unidades à loja por {@code totalLatao}, pago na hora.
     * O item entra no estoque de agora (mesmo que a mesa dele esteja no passado).
     *
     * @return false se a loja não tem esse caixa
     */
    public boolean comprarDeAventureiro(Item modelo, int qtd, long totalLatao, int dia, int hora, String obs) {
        if (modelo == null || qtd <= 0 || totalLatao < 0) return false;
        if (totalLatao > 0 && !caixa.pagarLatao(totalLatao)) return false;
        adicionarItem(clonarItem(modelo, qtd));
        entradasRecentes.merge(modelo.getId(), qtd, Integer::sum);
        registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.COMPRA_BALCAO, modelo.getNome(), qtd, totalLatao, obs);
        return true;
    }

    public void definirDemanda(Item item, int qtdDemanda) {
        if (item == null) return;
        catalogoConhecido.putIfAbsent(item.getId(), clonarItem(item, 1));
        demandaPorItem.put(item.getId(), Math.max(0, qtdDemanda));
    }

    public int getDeficitDemanda(int idItem) {
        return Math.max(0, demandaPorItem.getOrDefault(idItem, 0) - getQuantidadeEstoque(idItem));
    }

    public void registrarMovimentacao(int dia, int hora, RegistroMovimentacao.TipoOperacao tipo,
                                      String nomeItem, int qtd, long valorLatao, String obs) {
        historico.add(new RegistroMovimentacao(
                dia, hora, tipo, nomeItem, qtd, valorLatao, caixa.getValorTotalEmLatao(), obs
        ));
    }

    public void decairFluxoRecente() {
        entradasRecentes.replaceAll((k, v) -> v / 2);
        saidasRecentes.replaceAll((k, v) -> v / 2);
    }

    public Item fabricarComMolde(MoldeReceita molde, List<Item> materiaisEntregues, int dia, int hora) {
        if (molde == null) return null;
        if (!OficinaCrafting.lojaAptaParaMolde(this, molde)) {
            return null;
        }

        List<Item> insumosParaUso = new ArrayList<>();
        boolean usandoEstoqueDaLoja = (materiaisEntregues == null || materiaisEntregues.isEmpty());

        if (usandoEstoqueDaLoja) {
            // Coletar do estoque da loja
            if (!molde.getFiltrosInsumo().isEmpty()) {
                for (FiltroInsumoReceita filtro : molde.getFiltrosInsumo()) {
                    if (filtro.isSlotCatalisadorOpcional()) continue;
                    int aindaNecessario = filtro.getQuantidadeExigida();
                    for (Item item : this.estoque) {
                        if (filtro.aceitaMaterial(item)) {
                            int pegar = Math.min(aindaNecessario, item.getQuantidade());
                            Item copia = clonarItem(item, pegar);
                            insumosParaUso.add(copia);
                            aindaNecessario -= pegar;
                            if (aindaNecessario <= 0) break;
                        }
                    }
                    if (aindaNecessario > 0) {
                        return null; // Falta matéria-prima no estoque da loja
                    }
                }
            } else {
                for (MoldeReceita.RequisitoInsumo req : molde.getRequisitos()) {
                    if (req.isSlotCatalisadorOpcional()) continue;
                    int aindaNecessario = req.getQuantidade();
                    for (Item item : this.estoque) {
                        if (item.getFichaMaterial() != null
                                && item.getFichaMaterial().getSubtipo() == req.getSubtipoExigido()
                                && item.getFichaMaterial().getQualidade() >= req.getQualidadeMinima()) {
                            int pegar = Math.min(aindaNecessario, item.getQuantidade());
                            Item copia = clonarItem(item, pegar);
                            insumosParaUso.add(copia);
                            aindaNecessario -= pegar;
                            if (aindaNecessario <= 0) break;
                        }
                    }
                    if (aindaNecessario > 0) {
                        return null; // Falta matéria-prima no estoque da loja
                    }
                }
            }
        } else {
            insumosParaUso.addAll(materiaisEntregues);
            if (!OficinaCrafting.validarInsumos(molde, insumosParaUso)) {
                return null;
            }
        }

        long custoMaoDeObra = molde.getCustoMaoDeObraEmLatao();
        if (usandoEstoqueDaLoja) {
            if (!caixa.pagarLatao(custoMaoDeObra)) {
                return null; // Caixa insuficiente para pagar mão de obra da forja
            }
            // Deduz materiais do estoque da loja
            for (Item usado : insumosParaUso) {
                removerUnidades(usado.getId(), usado.getQuantidade());
            }
        }

        // Fabrica o item através da OficinaCrafting
        Item itemPronto = OficinaCrafting.fabricarItem(this, molde, insumosParaUso, 0);
        if (itemPronto == null) {
            if (usandoEstoqueDaLoja && custoMaoDeObra > 0) {
                caixa.receberLatao(custoMaoDeObra);
            }
            return null;
        }

        if (usandoEstoqueDaLoja) {
            adicionarItem(itemPronto);
            registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.PRODUCAO_CRAFTING,
                    itemPronto.getNome(), 1, itemPronto.getPrecoEmLatao(),
                    "Manufatura na oficina [" + itemPronto.getClassificacaoFormatada() + "]");
        }

        return itemPronto;
    }

    public boolean processarCraftingDiario(int dia, int hora) {
        // A extração de minérios da forja acontece uma vez por dia em processarCicloDiarioV6.
        boolean fabricouAlgo = false;
        for (MoldeReceita molde : new ArrayList<>(livroDeReceitas)) {
            Item feito = fabricarComMolde(molde, null, dia, hora);
            if (feito != null) {
                fabricouAlgo = true;
            } else {
                for (MoldeReceita.RequisitoInsumo req : molde.getRequisitos()) {
                    if (req.isSlotCatalisadorOpcional()) continue;
                    int presente = 0;
                    Item exemplo = null;
                    for (Item it : estoque) {
                        if (it.getFichaMaterial() != null && it.getFichaMaterial().getSubtipo() == req.getSubtipoExigido()) {
                            presente += it.getQuantidade();
                            exemplo = it;
                        }
                    }
                    if (exemplo == null) {
                        for (Item cat : catalogoConhecido.values()) {
                            if (cat.getFichaMaterial() != null && cat.getFichaMaterial().getSubtipo() == req.getSubtipoExigido()) {
                                exemplo = cat;
                                break;
                            }
                        }
                    }
                    if (presente < req.getQuantidade() && exemplo != null) {
                        int deficit = req.getQuantidade() - presente;
                        definirDemanda(exemplo, Math.max(demandaPorItem.getOrDefault(exemplo.getId(), 0), deficit + 2));
                    }
                }
            }
        }
        return fabricouAlgo;
    }

    public static Item clonarItem(Item base, int quantidade) {
        if (base == null) return null;
        Item clone = new Item(base.getId(), base.getNome(), base.getDescricao(),
                base.getPreco(), base.getPeso(), base.getTipo(), base.getRaridade(),
                Math.max(1, quantidade));
        clone.setTamanho(base.getTamanho());
        clone.setRegiaoOrigem(base.getRegiaoOrigem());
        clone.setGeologiaOrigem(base.getGeologiaOrigem());
        clone.setAquiferoOrigem(base.getAquiferoOrigem());

        if (base.getFichaMaterial() != null) {
            FichaMaterial fm = base.getFichaMaterial();
            FichaMaterial fmClone = new FichaMaterial(fm.getSubtipo(), fm.getQualidade(),
                    fm.getBonusFacesDadoArma(), fm.getBonusResistenciaArmadura(), fm.getEfeitoAtribuido(),
                    fm.isEspecificoDeOrigem(), fm.getSufixoCrafting());
            for (EfeitoItem ef : fm.getEfeitosPossiveis()) {
                fmClone.adicionarEfeitoPossivel(ef);
            }
            clone.setFichaMaterial(fmClone);
        }

        clone.setFichaArmaBranca(base.getFichaArmaBranca());
        clone.setFichaArmaLongoAlcance(base.getFichaArmaLongoAlcance());
        clone.setFichaArmadura(base.getFichaArmadura());
        clone.setFichaEscudo(base.getFichaEscudo());
        clone.setFichaAcessorio(base.getFichaAcessorio());
        clone.setFichaConsumivel(base.getFichaConsumivel());
        clone.setFichaGrimorio(base.getFichaGrimorio());
        clone.setOrigemItem(base.getOrigemItem());
        clone.setQualidadeCraft(base.getQualidadeCraft());
        clone.setMonstroOrigem(base.getMonstroOrigem());
        clone.setObraPrima(base.isObraPrima());
        clone.setPerecivel(base.isPerecivel());
        clone.setDiasValidadeRestantes(base.getDiasValidadeRestantes());
        clone.setDiasValidadeMaximo(base.getDiasValidadeMaximo());
        return clone;
    }

    // Getters e Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getNivelLoja() { return nivelLoja; }
    public void setNivelLoja(int nivelLoja) { this.nivelLoja = Math.max(1, Math.min(5, nivelLoja)); }

    public List<MoldeReceita> getLivroDeReceitas() { return Collections.unmodifiableList(livroDeReceitas); }
    public void adicionarReceita(MoldeReceita receita) {
        if (receita != null && !livroDeReceitas.contains(receita)) {
            livroDeReceitas.add(receita);
        }
    }

    public GeradorMineracaoForja getGeradorMineracao() { return geradorMineracao; }
    public void setGeradorMineracao(GeradorMineracaoForja geradorMineracao) { this.geradorMineracao = geradorMineracao; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getMercador() { return mercador; }
    public void setMercador(String mercador) { this.mercador = mercador; }

    public boolean isLojaDeRede() { return lojaDeRede; }
    public void setLojaDeRede(boolean lojaDeRede) { this.lojaDeRede = lojaDeRede; }

    public String getNomeDaRede() { return nomeDaRede; }
    public void setNomeDaRede(String nomeDaRede) { this.nomeDaRede = nomeDaRede; }

    public List<Item> getEstoque() { return Collections.unmodifiableList(estoque); }
    public Carteira getCaixa() { return caixa; }
    public void setCaixa(Carteira caixa) { if (caixa != null) this.caixa = caixa; }

    public Cidade getCidadeSede() { return cidadeSede; }
    public void setCidadeSede(Cidade cidadeSede) { this.cidadeSede = cidadeSede; }

    public TipoEstabelecimento getTipoEstabelecimento() { return tipoEstabelecimento; }
    public void setTipoEstabelecimento(TipoEstabelecimento tipoEstabelecimento) {
        this.tipoEstabelecimento = tipoEstabelecimento;
    }

    public Map<Integer, Item> getCatalogoConhecido() { return Collections.unmodifiableMap(catalogoConhecido); }
    public Map<Integer, Integer> getDemandaPorItem() { return Collections.unmodifiableMap(demandaPorItem); }
    public Map<Integer, Integer> getEntradasRecentes() { return Collections.unmodifiableMap(entradasRecentes); }
    public Map<Integer, Integer> getSaidasRecentes() { return Collections.unmodifiableMap(saidasRecentes); }
    public List<RegistroMovimentacao> getHistorico() { return Collections.unmodifiableList(historico); }

    // =========================================================================
    // MÉTODOS DE GESTÃO AVANÇADA v6 (PRATELEIRA, B2B, ORDENS E DETERIORAÇÃO)
    // =========================================================================

    public ArvoreEvolucaoLoja getArvoreEvolucao() { return arvoreEvolucao; }

    public void adicionarTalento(TalentoLojista talento) {
        if (talento != null) talentos.add(talento);
    }

    public boolean temTalento(TalentoLojista talento) {
        return talentos.contains(talento);
    }

    public Set<TalentoLojista> getTalentos() {
        return Collections.unmodifiableSet(talentos);
    }

    public int getCapacidadeSlotsArmazemEfetiva() {
        return capacidadeSlotsArmazemBase + arvoreEvolucao.getBonusSlotsArmazem();
    }

    public int getCapacidadeSlotsPrateleiraEfetiva() {
        return capacidadeSlotsPrateleiraBase + arvoreEvolucao.getBonusSlotsPrateleira();
    }

    public double calcularEspacoArmazemOcupado() {
        double espaco = 0.0;
        for (Item it : estoque) {
            espaco += it.getEspacoTotal();
        }
        return espaco;
    }

    public double calcularEspacoPrateleiraOcupado() {
        double espaco = 0.0;
        for (LotePrateleira lote : lotesPrateleira) {
            if (lote.getItem() != null) {
                espaco += (lote.getItem().getTamanho() != null ? lote.getItem().getTamanho().getSlotsMochila() : 1.0) * lote.getQuantidade();
            }
        }
        return espaco;
    }

    public boolean isArmazemLotado() {
        return calcularEspacoArmazemOcupado() >= getCapacidadeSlotsArmazemEfetiva();
    }

    public boolean isPrateleiraLotada() {
        return calcularEspacoPrateleiraOcupado() >= getCapacidadeSlotsPrateleiraEfetiva();
    }

    public void adicionarItemProntoPrateleira(Item itemPronto) {
        if (itemPronto == null) return;
        adicionarItem(itemPronto);
        lotesPrateleira.add(new LotePrateleira(clonarItem(itemPronto, itemPronto.getQuantidade()), itemPronto.getQuantidade()));
    }

    public List<LotePrateleira> getLotesPrateleira() {
        return Collections.unmodifiableList(lotesPrateleira);
    }

    public List<OrdemProducao> getFilaProducao() {
        return Collections.unmodifiableList(filaProducao);
    }

    public boolean iniciarOrdemProducao(MoldeReceita molde, int dia, int hora) {
        if (molde == null) return false;
        if (nivelLoja < molde.getNivelReceita()) return false;

        int maxOrdens = 1 + arvoreEvolucao.getBonusOrdensSimultaneas();
        if (filaProducao.size() >= maxOrdens) return false;

        // Verifica fundos para mão de obra
        if (!caixa.pagarLatao(molde.getCustoMaoDeObraEmLatao())) {
            return false;
        }

        // Confere todos os materiais antes de consumir: faltando um, nada sai do estoque
        Map<FiltroInsumoReceita, Item> escolhidos = new LinkedHashMap<>();
        for (FiltroInsumoReceita filtro : molde.getFiltrosInsumo()) {
            Item selecionado = null;
            for (Item cand : estoque) {
                if (filtro.aceitaMaterial(cand) && cand.getQuantidade() >= filtro.getQuantidadeExigida()) {
                    selecionado = cand;
                    break;
                }
            }
            if (selecionado == null && !filtro.isSlotCatalisadorOpcional()) {
                caixa.receberLatao(molde.getCustoMaoDeObraEmLatao());
                return false;
            }
            if (selecionado != null) escolhidos.put(filtro, selecionado);
        }
        List<Item> consumidos = new ArrayList<>();
        for (Map.Entry<FiltroInsumoReceita, Item> e : escolhidos.entrySet()) {
            int qtd = e.getKey().getQuantidadeExigida();
            consumidos.add(clonarItem(e.getValue(), qtd));
            removerItem(e.getValue().getId(), qtd);
        }

        int idResultado = molde.getIdMolde() * 100 + proximoIdOrdem;
        OrdemProducao ordem = new OrdemProducao(proximoIdOrdem++, this, molde, consumidos, idResultado);
        filaProducao.add(ordem);
        return true;
    }

    public void processarDeterioracaoInsumos(int dia, int hora) {
        boolean desacelerar = arvoreEvolucao.isCamaraConservacaoAtiva();
        if (desacelerar) {
            contadorDiasCamaraConservacao++;
            if (contadorDiasCamaraConservacao % 3 != 0) {
                return;
            }
        }

        Iterator<Item> it = estoque.iterator();
        while (it.hasNext()) {
            Item item = it.next();
            if (item.isPerecivel()) {
                boolean apodreceu = item.avancarDiaValidade();
                if (apodreceu) {
                    long valorPerda = item.getValorTotalEmLatao();
                    it.remove();
                    registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.PERDA_DETERIORACAO,
                            item.getNome(), item.getQuantidade(), valorPerda,
                            "Material orgânico apodreceu no armazém por expiração de validade");
                }
            }
        }
    }

    public void processarFilaProducao(int dia, int hora) {
        Iterator<OrdemProducao> it = filaProducao.iterator();
        while (it.hasNext()) {
            OrdemProducao ordem = it.next();
            boolean concluiu = ordem.avancarDia(dia, hora);
            if (concluiu) {
                it.remove();
            }
        }
    }

    public void processarPrateleiraERoleta(int dia, int hora, Random random) {
        Iterator<LotePrateleira> it = lotesPrateleira.iterator();
        while (it.hasNext()) {
            LotePrateleira lote = it.next();
            // Clientes podem ter levado parte do lote no balcão; sem isso a roleta pagaria por itens já vendidos
            lote.limitarQuantidade(getQuantidadeEstoque(lote.getItem().getId()));
            if (lote.getQuantidade() <= 0) {
                it.remove();
                continue;
            }
            boolean liquidou = lote.avancarDiaEResolver(this, dia, hora, random);
            if (liquidou || lote.isVendidoOuLiquidado()) {
                it.remove();
            }
        }
    }

    public void executarComprasB2B(List<Lojas> lojasDaCidade, int dia, int hora) {
        if (this.tipoEstabelecimento != TipoEstabelecimento.LOJA_GERAL || lojasDaCidade == null) {
            return;
        }

        for (Lojas especializada : lojasDaCidade) {
            if (especializada == this) continue;

            for (Item itemVenda : new ArrayList<>(especializada.getEstoque())) {
                if (itemVenda.getTipo() == Item.TipoItem.MATERIAL) continue;
                if (itemVenda.getQuantidade() < 2) continue;
                if (getQuantidadeEstoque(itemVenda.getId()) >= estoqueAlvo(itemVenda.getId())) continue;

                long precoVendaNormal = especializada.calcularPrecoDinamicoVendaEmLatao(itemVenda);
                long precoAtacado = (long) Math.floor(precoVendaNormal * 0.85);

                if (caixaDisponivelParaCompras() >= precoAtacado * 2) {
                    int qtdComprada = Math.min(2, itemVenda.getQuantidade() - 1);
                    long totalPago = precoAtacado * qtdComprada;

                    if (this.caixa.pagarLatao(totalPago)) {
                        especializada.removerItem(itemVenda.getId(), qtdComprada);
                        especializada.getCaixa().receberLatao(totalPago);

                        Item loteComprado = clonarItem(itemVenda, qtdComprada);
                        this.adicionarItemProntoPrateleira(loteComprado);

                        especializada.registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.VENDA_B2B,
                                itemVenda.getNome(), qtdComprada, totalPago,
                                "Venda B2B Atacado para " + this.getNome() + " (-15% desconto de atacado)");

                        this.registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.COMPRA_B2B,
                                itemVenda.getNome(), qtdComprada, totalPago,
                                "Aquisição B2B Atacado de " + especializada.getNome());
                    }
                }
            }
        }
    }

    public void processarCicloDiarioV6(int dia, int hora, Random random) {
        processarDeterioracaoInsumos(dia, hora);
        processarFilaProducao(dia, hora);
        processarPrateleiraERoleta(dia, hora, random);
        if (geradorMineracao != null && cidadeSede != null) {
            geradorMineracao.extrairMineriosDoDia(this, cidadeSede.getVeiosMineraisDisponiveis(), dia, hora, random);
        }
    }

    // =========================================================================
    // CLIENTELA, REPOSIÇÃO E PRODUÇÃO POR RECEITAS DO CATÁLOGO
    // =========================================================================

    /** Caixa que a loja preserva: não gasta com reposição, atacado nem encomendas. */
    public static final long RESERVA_CAIXA_LATAO = 20_000L;

    /** Estoque que a loja tenta manter de um item: o dobro da demanda, mais uma folga. */
    public int estoqueAlvo(int idItem) {
        return demandaPorItem.getOrDefault(idItem, 0) * 2 + 2;
    }

    public long caixaDisponivelParaCompras() {
        return Math.max(0L, caixa.getValorTotalEmLatao() - RESERVA_CAIXA_LATAO);
    }

    /** Movimento de clientes conforme o porte do assentamento. */
    private double fatorClientela() {
        if (cidadeSede == null || cidadeSede.getTipo() == null) return 1.0;
        return switch (cidadeSede.getTipo()) {
            case REINO -> 1.5;
            case CIDADE -> 1.0;
            case FORTALEZA -> 0.8;
            case VILA -> 0.5;
        };
    }

    /**
     * Clientes da cidade compram ao longo do dia. A procura segue a demanda do item e
     * reage ao preço: cai quando o preço dinâmico passa do valor de referência e sobe
     * quando está barato. Materiais vendem pouco no balcão (o grosso vai às oficinas).
     *
     * @return total recebido em latão
     */
    public long atenderClientesNpc(Random random, int dia, int hora) {
        Random rnd = random != null ? random : new Random();
        long recebidoTotal = 0L;
        for (Item item : new ArrayList<>(estoque)) {
            int disponivel = item.getQuantidade();
            if (disponivel <= 0) continue;
            long preco = calcularPrecoDinamicoVendaEmLatao(item);
            double referencia = Math.max(1.0, item.getPrecoEmLatao()
                    * (cidadeSede != null ? item.calcularMultiplicadorRegional(cidadeSede) : 1.0));
            double elasticidade = Math.max(0.3, Math.min(2.0, referencia / preco));
            double procura = demandaPorItem.getOrDefault(item.getId(), 3) * 0.5 * elasticidade * fatorClientela();
            if (item.getTipo() == Item.TipoItem.MATERIAL) procura *= 0.3;
            int vendidos = Math.min(disponivel, (int) Math.round(procura * (0.6 + 0.8 * rnd.nextDouble())));
            if (vendidos <= 0) continue;

            long recebido = preco * vendidos;
            removerUnidades(item.getId(), vendidos);
            caixa.receberLatao(recebido);
            saidasRecentes.merge(item.getId(), vendidos, Integer::sum);
            recebidoTotal += recebido;
            registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.VENDA_BALCAO,
                    item.getNome(), vendidos, recebido, "Clientes da cidade");
        }
        return recebidoTotal;
    }

    public List<OrdemReceita> getOrdensReceita() { return Collections.unmodifiableList(ordensReceita); }

    /** Usado pela persistência ao recarregar o mundo. */
    public void adicionarOrdemCarregada(OrdemReceita ordem) {
        if (ordem != null) ordensReceita.add(ordem);
    }

    public int getOrdensSimultaneasMax() {
        return 1 + arvoreEvolucao.getBonusOrdensSimultaneas();
    }

    /**
     * Como a loja vai cumprir {@code lotes} da receita: em receitas com etapas, cada componente
     * livre na prateleira substitui a etapa dele (e os materiais dela); o resto sai dos materiais.
     */
    private record PlanoProducao(List<Boolean> etapaDaPrateleira, Map<Integer, Integer> materiais) {}

    private PlanoProducao planejar(ReceitaLoja receita, int lotes, boolean usarPrateleira) {
        int n = Math.max(1, lotes);
        Map<Integer, Integer> materiais = new LinkedHashMap<>();
        List<Boolean> daPrateleira = new ArrayList<>();
        if (receita.etapas().isEmpty()) {
            for (ReceitaLoja.Insumo ins : receita.insumos()) materiais.merge(ins.itemId(), ins.quantidade() * n, Integer::sum);
            return new PlanoProducao(daPrateleira, materiais);
        }
        Map<Integer, Integer> livres = new HashMap<>();
        for (ReceitaLoja.Etapa e : receita.etapas()) {
            int livre = livres.computeIfAbsent(e.componenteItemId(), this::getQuantidadeEstoque);
            if (usarPrateleira && livre >= n) {
                livres.put(e.componenteItemId(), livre - n);
                daPrateleira.add(true);
            } else {
                daPrateleira.add(false);
                for (ReceitaLoja.Insumo ins : e.insumos()) materiais.merge(ins.itemId(), ins.quantidade() * n, Integer::sum);
            }
        }
        return new PlanoProducao(daPrateleira, materiais);
    }

    /** Insumos que faltam no estoque para {@code lotes} da receita (itemId -> unidades faltantes). */
    public Map<Integer, Integer> insumosFaltantes(ReceitaLoja receita, int lotes) {
        Map<Integer, Integer> faltam = new LinkedHashMap<>();
        planejar(receita, lotes, true).materiais().forEach((id, precisa) -> {
            int tem = getQuantidadeEstoque(id);
            if (tem < precisa) faltam.put(id, precisa - tem);
        });
        return faltam;
    }

    /**
     * Abre uma ordem de produção: consome os insumos e paga a mão de obra na hora.
     * Com {@code insumosPorConta} (o Mestre fornece tudo) nada sai do estoque nem do caixa.
     *
     * Em receitas com etapas, componentes livres na prateleira são reservados na hora e poupam
     * o tempo da etapa deles; as demais etapas ficam prontas uma depois da outra, e a montagem
     * vem no fim.
     *
     * @return a ordem aberta, ou null se faltar insumo ou caixa
     */
    public OrdemReceita iniciarReceita(ReceitaLoja receita, Item resultado, int lotes, int dia, int hora,
                                       OrdemReceita.Origem origem, boolean insumosPorConta) {
        return iniciarReceita(receita, resultado, lotes, dia, hora, origem, insumosPorConta, null);
    }

    /** @param rotuloMesa origem gravada no livro-caixa (ex.: "[Mesa Norte · dia 8]"); null = sem mesa */
    public OrdemReceita iniciarReceita(ReceitaLoja receita, Item resultado, int lotes, int dia, int hora,
                                       OrdemReceita.Origem origem, boolean insumosPorConta, String rotuloMesa) {
        if (receita == null || resultado == null || lotes < 1) return null;
        PlanoProducao plano = planejar(receita, lotes, !insumosPorConta);
        if (!insumosPorConta) {
            if (!insumosFaltantes(receita, lotes).isEmpty()) return null;
            long maoDeObra = receita.custoMaoDeObraEmLatao(resultado, lotes);
            if (!caixa.pagarLatao(maoDeObra)) return null;
            plano.materiais().forEach((id, qtd) -> removerUnidades(id, qtd));
            registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.PRODUCAO_CRAFTING,
                    resultado.getNome(), receita.quantidadeResultado() * lotes, maoDeObra,
                    (rotuloMesa != null ? rotuloMesa + " " : "") + "Mão de obra: " + receita.nome()
                            + (origem == OrdemReceita.Origem.MESTRE ? " (ordem do Mestre)" : ""));
        }

        if (receita.etapas().isEmpty()) {
            OrdemReceita ordem = new OrdemReceita(0, receita.id(), lotes, dia, dia + receita.diasProducao(), origem, insumosPorConta, List.of());
            ordensReceita.add(ordem);
            return ordem;
        }

        List<OrdemReceita.EtapaOrdem> etapas = new ArrayList<>();
        double horas = 0;
        for (int i = 0; i < receita.etapas().size(); i++) {
            ReceitaLoja.Etapa e = receita.etapas().get(i);
            if (plano.etapaDaPrateleira().get(i)) {
                removerUnidades(e.componenteItemId(), lotes);
                etapas.add(new OrdemReceita.EtapaOrdem(e.componenteItemId(), dia, OrdemReceita.EstadoEtapa.DA_PRATELEIRA));
                continue;
            }
            horas += e.tempoHoras();
            etapas.add(new OrdemReceita.EtapaOrdem(e.componenteItemId(), dia + Math.max(1, (int) Math.ceil(horas / 24.0)), OrdemReceita.EstadoEtapa.PENDENTE));
        }
        int diaFinal = dia + Math.max(1, (int) Math.ceil((horas + receita.horasMontagem()) / 24.0));
        OrdemReceita ordem = new OrdemReceita(0, receita.id(), lotes, dia, diaFinal, origem, insumosPorConta, etapas);
        ordensReceita.add(ordem);
        return ordem;
    }

    /** Conclui as etapas e ordens cujo prazo chegou: peças prontas ficam reservadas, o resultado entra no estoque. */
    public int concluirOrdensProntas(int dia, int hora, Map<Integer, ReceitaLoja> receitas, Map<Integer, Item> catalogo) {
        int concluidas = 0;
        Iterator<OrdemReceita> it = ordensReceita.iterator();
        while (it.hasNext()) {
            OrdemReceita ordem = it.next();
            if (ordem.isProntaAguardando()) continue;
            for (OrdemReceita.EtapaOrdem e : ordem.getEtapas()) {
                if (e.getEstado() != OrdemReceita.EstadoEtapa.PENDENTE || (e.getDiaConclusao() > dia && !ordem.prontaNoDia(dia))) continue;
                e.setEstado(OrdemReceita.EstadoEtapa.RESERVADA);
                Item peca = catalogo.get(e.getComponenteItemId());
                registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.PRODUCAO_CONCLUIDA,
                        peca != null ? peca.getNome() : "Peça #" + e.getComponenteItemId(), ordem.getLotes(), 0L, "Peça pronta, reservada para a encomenda");
            }
            if (!ordem.prontaNoDia(dia)) continue;
            ReceitaLoja receita = receitas.get(ordem.getReceitaId());
            if (ordem.isDaMesa()) {
                // Encomenda de mesa: o resultado fica guardado até a mesa chegar ao prazo dela
                ordem.marcarProntaAguardando();
                Item modelo = receita != null ? catalogo.get(receita.itemResultadoId()) : null;
                registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.PRODUCAO_CONCLUIDA,
                        modelo != null ? modelo.getNome() : "Receita #" + ordem.getReceitaId(), ordem.getLotes(), 0L,
                        "Encomenda pronta, reservada até a mesa chegar ao dia " + ordem.getDiaConclusaoMesa());
                concluidas++;
                continue;
            }
            it.remove();
            Item modelo = receita != null ? catalogo.get(receita.itemResultadoId()) : null;
            if (modelo == null) {
                registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.PERDA_DETERIORACAO,
                        "Receita #" + ordem.getReceitaId(), ordem.getLotes(), 0L, "Lote perdido: a receita ou o item foi apagado");
                continue;
            }
            int qtd = receita.quantidadeResultado() * ordem.getLotes();
            adicionarItem(clonarItem(modelo, qtd));
            entradasRecentes.merge(modelo.getId(), qtd, Integer::sum);
            registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.PRODUCAO_CONCLUIDA,
                    modelo.getNome(), qtd, 0L, "Lote pronto: " + receita.nome());
            concluidas++;
        }
        return concluidas;
    }

    /** O Mestre apressa a oficina: a ordem termina agora, sem esperar os dias de produção. */
    public boolean concluirImediatamente(OrdemReceita ordem, int dia, int hora, Map<Integer, ReceitaLoja> receitas, Map<Integer, Item> catalogo) {
        if (ordem == null || !ordensReceita.remove(ordem)) return false;
        ordensReceita.add(ordem.comConclusao(dia));
        return concluirOrdensProntas(dia, hora, receitas, catalogo) > 0;
    }

    /** Ordens que ainda ocupam a oficina (encomendas prontas esperando a mesa não contam). */
    public int getOrdensEmProducao() {
        int n = 0;
        for (OrdemReceita o : ordensReceita) if (!o.isProntaAguardando()) n++;
        return n;
    }

    /**
     * Encomenda de mesa chegou ao prazo no relógio da mesa: sai da oficina mesmo que a loja
     * não tenha terminado ("no passado, ela já estava pronta"). Sem personagem destinatário,
     * o resultado entra no estoque para o Mestre vender; com personagem, é devolvido a quem
     * chamou para ir à ficha.
     *
     * @param rotuloMesa ex.: "[Mesa Norte · dia 13]", gravado no livro-caixa
     * @return o resultado (com a quantidade), ou null se a receita ou o item foi apagado
     */
    public Item entregarEncomenda(OrdemReceita ordem, int dia, int hora, Map<Integer, ReceitaLoja> receitas,
                                  Map<Integer, Item> catalogo, String rotuloMesa) {
        if (ordem == null || !ordensReceita.remove(ordem)) return null;
        ReceitaLoja receita = receitas.get(ordem.getReceitaId());
        Item modelo = receita != null ? catalogo.get(receita.itemResultadoId()) : null;
        if (modelo == null) {
            registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.PERDA_DETERIORACAO,
                    "Receita #" + ordem.getReceitaId(), ordem.getLotes(), 0L, rotuloMesa + " Encomenda perdida: a receita ou o item foi apagado");
            return null;
        }
        int qtd = receita.quantidadeResultado() * ordem.getLotes();
        String antecipada = ordem.isProntaAguardando() ? "" : " (concluída antes do prazo da loja)";
        if (ordem.getFichaId() == null) {
            adicionarItem(clonarItem(modelo, qtd));
            entradasRecentes.merge(modelo.getId(), qtd, Integer::sum);
        }
        registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.PRODUCAO_CONCLUIDA, modelo.getNome(), qtd, 0L,
                rotuloMesa + " Encomenda entregue" + antecipada + (ordem.getFichaId() == null ? ", no balcão" : " ao personagem"));
        return clonarItem(modelo, qtd);
    }

    /**
     * Cancela uma ordem: as peças já reservadas viram estoque comum e os materiais das etapas
     * que ainda não viraram peça voltam à prateleira. A mão de obra paga não volta.
     * Se o Mestre forneceu os insumos, só as peças prontas ficam com a loja.
     */
    public boolean cancelarOrdem(OrdemReceita ordem, int dia, int hora, Map<Integer, ReceitaLoja> receitas, Map<Integer, Item> catalogo) {
        if (ordem == null || !ordensReceita.remove(ordem)) return false;
        ReceitaLoja receita = receitas.get(ordem.getReceitaId());
        Map<Integer, Integer> devolvidos = new LinkedHashMap<>();
        if (ordem.isProntaAguardando()) {
            // A peça já estava pronta esperando a mesa: vira estoque comum
            if (receita != null) devolvidos.merge(receita.itemResultadoId(), receita.quantidadeResultado() * ordem.getLotes(), Integer::sum);
        } else if (ordem.getEtapas().isEmpty()) {
            if (receita != null && !ordem.isInsumosPorConta()) {
                for (ReceitaLoja.Insumo ins : receita.insumos()) devolvidos.merge(ins.itemId(), ins.quantidade() * ordem.getLotes(), Integer::sum);
            }
        } else {
            for (OrdemReceita.EtapaOrdem e : ordem.getEtapas()) {
                if (e.isReservada()) {
                    devolvidos.merge(e.getComponenteItemId(), ordem.getLotes(), Integer::sum);
                } else if (receita != null && !ordem.isInsumosPorConta()) {
                    receita.etapas().stream().filter(et -> et.componenteItemId() == e.getComponenteItemId()).findFirst()
                            .ifPresent(et -> et.insumos().forEach(ins -> devolvidos.merge(ins.itemId(), ins.quantidade() * ordem.getLotes(), Integer::sum)));
                }
            }
        }
        devolvidos.forEach((id, qtd) -> {
            Item modelo = catalogo.get(id);
            if (modelo != null) adicionarItem(clonarItem(modelo, qtd));
        });
        registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.ENCOMENDA_CANCELADA,
                receita != null ? receita.nome() : "Receita #" + ordem.getReceitaId(), ordem.getLotes(), 0L,
                devolvidos.isEmpty() ? "Ordem cancelada" : "Ordem cancelada: peças prontas e materiais voltaram à prateleira");
        return true;
    }

    /** Peças prontas na oficina reservadas para encomendas (itemId -> unidades). Não podem ser vendidas. */
    public Map<Integer, Integer> getReservados() {
        return getReservados(Map.of());
    }

    /** Idem, incluindo o resultado das encomendas de mesa que a loja terminou e esperam o prazo da mesa. */
    public Map<Integer, Integer> getReservados(Map<Integer, ReceitaLoja> receitas) {
        Map<Integer, Integer> r = new LinkedHashMap<>();
        for (OrdemReceita o : ordensReceita) {
            if (o.isProntaAguardando()) {
                ReceitaLoja receita = receitas.get(o.getReceitaId());
                if (receita != null) r.merge(receita.itemResultadoId(), receita.quantidadeResultado() * o.getLotes(), Integer::sum);
                continue;
            }
            for (OrdemReceita.EtapaOrdem e : o.getEtapas()) {
                if (e.isReservada()) r.merge(e.getComponenteItemId(), o.getLotes(), Integer::sum);
            }
        }
        return r;
    }

    /** Registros ainda não gravados no livro-caixa do banco. */
    public List<RegistroMovimentacao> getHistoricoNaoPersistido() {
        return Collections.unmodifiableList(historico.subList(Math.min(historicoPersistido, historico.size()), historico.size()));
    }

    public void marcarHistoricoPersistido() {
        historicoPersistido = historico.size();
    }

    /** Ajuste manual do Mestre: abastecer ou retirar estoque sem movimentar o caixa. */
    public void ajustarEstoque(Item modelo, int delta, int dia, int hora) {
        if (modelo == null || delta == 0) return;
        if (delta > 0) adicionarItem(clonarItem(modelo, delta));
        else if (!removerUnidades(modelo.getId(), Math.min(-delta, getQuantidadeEstoque(modelo.getId())))) return;
        registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.AJUSTE_MESTRE,
                modelo.getNome(), Math.abs(delta), 0L, delta > 0 ? "Estoque abastecido pelo Mestre" : "Estoque retirado pelo Mestre");
    }

    /** Ajuste manual do Mestre: define o caixa da loja. */
    public void definirCaixa(long novoCaixaLatao, int dia, int hora) {
        long anterior = caixa.getValorTotalEmLatao();
        caixa = Carteira.deLatao(Math.max(0L, novoCaixaLatao));
        registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.AJUSTE_MESTRE, "Caixa", 1,
                Math.abs(novoCaixaLatao - anterior), "Caixa ajustado pelo Mestre de " + Carteira.formatarLatao(anterior));
    }

    @Override
    public String toString() {
        String infoRede = lojaDeRede ? " [Rede: " + nomeDaRede + "]" : "";
        String sede = (cidadeSede != null) ? " em " + cidadeSede.getNome() : "";
        return String.format("%s (%s)%s%s [%s | Nv.%d] - %d tipo(s) no estoque | Caixa: %s",
                nome, mercador, sede, infoRede, tipoEstabelecimento, nivelLoja, estoque.size(),
                Carteira.formatarLatao(caixa.getValorTotalEmLatao()));
    }
}
