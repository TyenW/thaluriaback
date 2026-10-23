package economia;

import geografia.Cidade;
import java.util.*;

/**
 * Representa uma Sede Regional da Guilda de Comércio vinculada a uma Cidade específica.
 * A Guilda é rigorosamente abastecida pela fauna, flora e geologia da sua própria região.
 * Caçadores e aventureiros locais apenas trazem espólios e materiais nativos daquele bioma.
 * Também gerencia as Requisições de Expedições de Coleta abertas por lojas ou aventureiros.
 */
public class SedeGuildaRegional {

    private final Cidade cidadeSede;
    private final String nome;
    private final List<Item> estoqueRegional = new ArrayList<>();
    private final List<RequisicaoGuilda> requisicoesAtivas = new ArrayList<>();
    private final List<RequisicaoGuilda> historicoRequisicoes = new ArrayList<>();
    private int proximoIdRequisicao = 1;

    public SedeGuildaRegional(Cidade cidadeSede) {
        this((cidadeSede != null ? "Guilda Comercial de " + cidadeSede.getNome() : "Guilda Regional"), cidadeSede);
    }

    public SedeGuildaRegional(String nome, Cidade cidadeSede) {
        this.nome = (nome != null && !nome.isBlank()) ? nome.trim() : "Guilda Regional";
        this.cidadeSede = cidadeSede;
    }

    /**
     * Valida se um determinado item ou espólio de criatura pertence ao ecossistema da cidade sede.
     * Impede que a guilda do deserto receba itens da neve de caçadores locais!
     */
    public boolean itemPertenceAoEcossistemaLocal(Item item) {
        if (item == null || cidadeSede == null) return false;

        // 1. Região de Origem (Bioma/Clima)
        if (item.getRegiaoOrigem() != null && item.getRegiaoOrigem() != cidadeSede.getRegiao()) {
            if (!cidadeSede.isFronteira() || item.getRegiaoOrigem() != cidadeSede.getRegiaofronteira()) {
                return false; // Bloqueado: bioma incompatível com o território local!
            }
        }

        // 2. Geologia de Origem (Relevo/Solo)
        if (item.getGeologiaOrigem() != null && item.getGeologiaOrigem() != cidadeSede.getGeologia()) {
            return false;
        }

        // 3. Aquífero de Origem (Corpo Hídrico)
        if (item.getAquiferoOrigem() != null && item.getAquiferoOrigem() != cidadeSede.getAquifero()) {
            return false;
        }

        return true;
    }

    /**
     * Aventureiros NPCs locais depositam ou vendem insumos coletados nas redondezas.
     * Rejeita se o item não fizer parte da fauna/flora/geologia da cidade sede.
     */
    public boolean adicionarItemAventureiroLocal(Item item) {
        if (!itemPertenceAoEcossistemaLocal(item)) {
            return false;
        }
        // Regra de Insumos Raros: drops épicos, lendários e míticos não entram sozinhos de aventureiros NPCs comuns
        if (item.getRaridade() == Item.RaridadeItem.EPICO ||
            item.getRaridade() == Item.RaridadeItem.LENDARIO ||
            item.getRaridade() == Item.RaridadeItem.MITICO) {
            return false;
        }
        for (Item existente : estoqueRegional) {
            if (existente.getId() == item.getId()) {
                existente.adicionarQuantidade(item.getQuantidade());
                return true;
            }
        }
        estoqueRegional.add(Lojas.clonarItem(item, item.getQuantidade()));
        return true;
    }

    /**
     * Jogadores podem vender qualquer insumo (incluindo espólios raros de chefes) no balcão da Guilda.
     */
    public boolean adicionarItemVendidoPorJogador(Item item) {
        if (item == null || item.getQuantidade() <= 0) return false;
        for (Item existente : estoqueRegional) {
            if (existente.getId() == item.getId()) {
                existente.adicionarQuantidade(item.getQuantidade());
                return true;
            }
        }
        estoqueRegional.add(Lojas.clonarItem(item, item.getQuantidade()));
        return true;
    }

    /**
     * Abre uma requisição de expedição encomendada por uma loja.
     * Trava estrita: a guilda só aceita encomendas de itens que existam na sua região!
     */
    public RequisicaoGuilda abrirRequisicao(Lojas lojaSolicitante, Item itemAlvo, int quantidade,
                                            long recompensaCaucaoLatao, int dia, int hora) {
        if (lojaSolicitante == null || itemAlvo == null || quantidade <= 0) {
            return null;
        }
        if (!itemPertenceAoEcossistemaLocal(itemAlvo)) {
            return null; // A guilda rejeita encomendas de criaturas que não habitam sua região
        }

        int nivel = determinarNivelRequisicao(itemAlvo);
        long taxaFixa = RequisicaoGuilda.calcularTaxaFixaNivelEmLatao(nivel);

        // Talento BARGANHA_AVANCADA: 20% de desconto nas taxas da guilda
        if (lojaSolicitante.temTalento(TalentoLojista.BARGANHA_AVANCADA)) {
            taxaFixa = (long) Math.ceil(taxaFixa * 0.80);
        }

        long custoTotal = taxaFixa + recompensaCaucaoLatao;

        if (!lojaSolicitante.getCaixa().pagarLatao(custoTotal)) {
            return null; // Saldo insuficiente para cobrir taxa + caução
        }

        int dias = RequisicaoGuilda.calcularDiasExpedicaoNivel(nivel);
        RequisicaoGuilda req = new RequisicaoGuilda(proximoIdRequisicao++, cidadeSede, lojaSolicitante,
                itemAlvo, quantidade, nivel, taxaFixa, recompensaCaucaoLatao, dias);

        requisicoesAtivas.add(req);
        lojaSolicitante.registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.DESPESA_ESCOLTA,
                itemAlvo.getNome(), quantidade, custoTotal,
                "Abertura de Requisição na Guilda (Taxa: " + Carteira.formatarLatao(taxaFixa) + " + Caução)");

        return req;
    }

    /**
     * Abre requisição direta para um aventureiro/jogador portando Carteira.
     */
    public RequisicaoGuilda abrirRequisicaoParaCliente(Carteira carteiraCliente, Item itemAlvo, int quantidade,
                                                       long recompensaCaucaoLatao, int dia, int hora) {
        if (carteiraCliente == null || itemAlvo == null || quantidade <= 0) {
            return null;
        }
        if (!itemPertenceAoEcossistemaLocal(itemAlvo)) {
            return null;
        }

        int nivel = determinarNivelRequisicao(itemAlvo);
        long taxaFixa = RequisicaoGuilda.calcularTaxaFixaNivelEmLatao(nivel);
        long custoTotal = taxaFixa + recompensaCaucaoLatao;

        if (!carteiraCliente.pagarLatao(custoTotal)) {
            return null;
        }

        int dias = RequisicaoGuilda.calcularDiasExpedicaoNivel(nivel);
        RequisicaoGuilda req = new RequisicaoGuilda(proximoIdRequisicao++, cidadeSede, null, carteiraCliente,
                itemAlvo, quantidade, nivel, taxaFixa, recompensaCaucaoLatao, dias);

        requisicoesAtivas.add(req);
        return req;
    }

    public static int determinarNivelRequisicao(Item item) {
        if (item == null) return 1;
        int nivel = 1;
        if (item.getFichaMaterial() != null) {
            nivel = item.getFichaMaterial().getQualidade();
        }
        if (item.getRaridade() == Item.RaridadeItem.EPICO) {
            nivel = Math.max(nivel, 4);
        } else if (item.getRaridade() == Item.RaridadeItem.LENDARIO || item.getRaridade() == Item.RaridadeItem.MITICO) {
            nivel = 5;
        }
        return Math.max(1, Math.min(5, nivel));
    }

    /**
     * Avança 1 dia nas requisições ativas da guilda e resolve as que atingiram o prazo.
     */
    public void processarDiaRequisicoes(int dia, int hora, Random random) {
        Iterator<RequisicaoGuilda> it = requisicoesAtivas.iterator();
        while (it.hasNext()) {
            RequisicaoGuilda req = it.next();
            boolean resolveu = req.avancarDiaEResolver(dia, hora, random);
            if (resolveu) {
                it.remove();
                historicoRequisicoes.add(req);
            }
        }
    }

    public Cidade getCidadeSede() { return cidadeSede; }
    public String getNome() { return nome; }
    public List<Item> getEstoqueRegional() { return Collections.unmodifiableList(estoqueRegional); }
    public List<RequisicaoGuilda> getRequisicoesAtivas() { return Collections.unmodifiableList(requisicoesAtivas); }
    public List<RequisicaoGuilda> getHistoricoRequisicoes() { return Collections.unmodifiableList(historicoRequisicoes); }

    @Override
    public String toString() {
        return String.format("%s em %s [Estoque Regional: %d itens | Requisições Ativas: %d]",
                nome, (cidadeSede != null ? cidadeSede.getNome() : "Sem sede"),
                estoqueRegional.size(), requisicoesAtivas.size());
    }
}
