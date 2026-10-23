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
        // Extração diária de minérios se for Forja com gerador de mineradores ativo
        if (this.tipoEstabelecimento == TipoEstabelecimento.FORJA && this.geradorMineracao != null && this.cidadeSede != null) {
            this.geradorMineracao.extrairMineriosDoDia(this, this.cidadeSede.getVeiosMineraisDisponiveis(), dia, hora, new Random());
        }

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

        // Verifica materiais
        List<Item> consumidos = new ArrayList<>();
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
            if (selecionado != null) {
                removerItem(selecionado.getId(), filtro.getQuantidadeExigida());
                consumidos.add(clonarItem(selecionado, filtro.getQuantidadeExigida()));
            }
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

                long precoVendaNormal = especializada.calcularPrecoDinamicoVendaEmLatao(itemVenda);
                long precoAtacado = (long) Math.floor(precoVendaNormal * 0.85);

                if (this.caixa.getValorTotalEmLatao() >= precoAtacado * 2) {
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

                        this.registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.COMPRA_BALCAO,
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

    @Override
    public String toString() {
        String infoRede = lojaDeRede ? " [Rede: " + nomeDaRede + "]" : "";
        String sede = (cidadeSede != null) ? " em " + cidadeSede.getNome() : "";
        return String.format("%s (%s)%s%s [%s | Nv.%d] - %d tipo(s) no estoque | Caixa: %s",
                nome, mercador, sede, infoRede, tipoEstabelecimento, nivelLoja, estoque.size(),
                Carteira.formatarLatao(caixa.getValorTotalEmLatao()));
    }
}
