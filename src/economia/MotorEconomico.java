package economia;

import geografia.Cidade;
import geografia.Conexao;
import geografia.NavegadorGrafo;
import java.util.*;

/**
 * Motor central de simulação da economia dinâmica de Thaluria.
 * Orquestra a passagem do tempo, ciclos diários, compras locais de NPCs,
 * oscilação de oferta e demanda, cálculo de fretes no grafo (Dijkstra),
 * expedição de caravanas, quadros de missões (escolta e resgate) e
 * impactos dinâmicos na segurança das estradas.
 */
public class MotorEconomico {

    private final List<Cidade> cidades = new ArrayList<>();
    private final List<Caravana> caravanas = new ArrayList<>();
    private final List<MissaoComercial> missoes = new ArrayList<>();
    private final Random random;

    private int diaAtual = 1;
    private int horaAtual = 8;

    /** Rotas já calculadas no ciclo atual (RotaComercial é imutável); limpo a cada dia, pois o perigo muda. */
    private final Map<String, Optional<RotaComercial>> rotasDoDia = new HashMap<>();

    /** Custo entregue (mercadoria + frete + escolta) máximo, como fração do valor de revenda local. */
    private static final double MARGEM_MAXIMA_CUSTO_ENCOMENDA = 0.80;

    // Receitas cadastradas pelo Mestre e catálogo de itens (carregados do banco)
    private List<ReceitaLoja> receitas = new ArrayList<>();
    private Map<Integer, ReceitaLoja> receitasPorId = new HashMap<>();
    private RedeConhecimento redeConhecimento = RedeConhecimento.vazia();
    private Map<Integer, Item> catalogoItens = new HashMap<>();

    public MotorEconomico() {
        this(new Random());
    }

    public MotorEconomico(Random random) {
        this.random = (random != null) ? random : new Random();
    }

    public void registrarCidade(Cidade cidade) {
        if (cidade != null && !cidades.contains(cidade)) {
            cidades.add(cidade);
            for (Lojas loja : cidade.getLojas()) {
                loja.setCidadeSede(cidade);
            }
        }
    }

    /**
     * Avança o relógio do mundo hora a hora, disparando ciclos diários,
     * prazos de missões e deslocamentos físicos de caravanas.
     */
    public void avancarTempo(int horas) {
        for (int i = 0; i < horas; i++) {
            horaAtual++;
            if (horaAtual >= 24) {
                horaAtual = 0;
                diaAtual++;
                executarCicloDiario();
            }
            processarMissoesPendentes(1);
            processarCaravanasEmTransito(1);
        }
    }

    /**
     * Ciclo diário de mercado: decai fluxos recentes, simula fornecimento de NPCs locais
     * baseado no bioma e na formação geológica da cidade, recalcula demandas orgânicas
     * e dispara encomendas intermunicipais automáticas.
     */
    public void executarCicloDiario() {
        rotasDoDia.clear();
        limparCaravanasConcluidas();
        for (Cidade cidade : cidades) {
            // Receitas regionais só saem das cidades que já chegaram à Produção (as comuns valem em todo lugar)
            List<ReceitaLoja> receitasDaCidade = receitas.stream()
                    .filter(r -> redeConhecimento.receitaLiberada(r.id(), cidade.getId(), diaAtual)).toList();
            for (Lojas loja : cidade.getLojas()) {
                loja.decairFluxoRecente();

                // Conhecimento que chegou ao estágio Comércio: a loja passa a procurar o item
                for (Integer itemId : redeConhecimento.itensParaPrateleira(loja, diaAtual)) {
                    Item modelo = catalogoItens.get(itemId);
                    if (modelo != null && !loja.getCatalogoConhecido().containsKey(itemId)) loja.definirDemanda(modelo, 2);
                }

                // 1. Lotes cuja produção terminou entram no estoque
                loja.concluirOrdensProntas(diaAtual, horaAtual, receitasPorId, catalogoItens);

                // 2. Clientes da cidade compram no balcão (o dinheiro que sustenta a loja)
                loja.atenderClientesNpc(random, diaAtual, horaAtual);

                for (Item modelo : new ArrayList<>(loja.getCatalogoConhecido().values())) {
                    // 3. Oscilação orgânica da demanda (entre 2 e 10 unidades)
                    loja.definirDemanda(modelo, 2 + random.nextInt(9));

                    // 4. Fornecedores NPC locais (bioma e geologia), só até o estoque-alvo e sem tocar na reserva
                    // (item regional só tem fornecedor local onde já se sabe fazer)
                    RedeConhecimento.Estagio estagio = redeConhecimento.estagioDoItem(modelo.getId(), cidade.getId(), diaAtual);
                    boolean temFornecedorLocal = estagio == null || estagio == RedeConhecimento.Estagio.PRODUCAO;
                    int falta = loja.estoqueAlvo(modelo.getId()) - loja.getQuantidadeEstoque(modelo.getId());
                    if (temFornecedorLocal && falta > 0 && random.nextDouble() < cidade.calcularChanceFornecedorNpc(modelo)) {
                        int qtdFornecida = Math.min(falta, cidade.calcularQtdFornecedorNpc(modelo, random));
                        if (loja.calcularPrecoCompraNpcEmLatao(modelo) * qtdFornecida <= loja.caixaDisponivelParaCompras()) {
                            loja.comprarDeNpc(modelo, qtdFornecida, diaAtual, horaAtual);
                        }
                    }

                    // 5. Encomenda intermunicipal caso falte estoque para atender à demanda projetada
                    int deficit = loja.getDeficitDemanda(modelo.getId());
                    if (deficit > 0 && !temCaravanaAtivaPara(loja, modelo.getId())) {
                        solicitarCompraEntreCidades(loja, modelo, deficit);
                    }
                }

                // 6. IA do mercador: escolhe a receita mais rentável do dia (ou pede os insumos que faltam)
                IALojistaDeterministica.decidirProducaoDoDia(loja, receitasDaCidade, catalogoItens, diaAtual, horaAtual);

                // 7. Manufatura legada por moldes de material (MoldeReceita)
                loja.processarCraftingDiario(diaAtual, horaAtual);

                // 5. Ciclo v6: deterioração de perecíveis, prateleira/liquidação, ordens e mineração
                loja.processarCicloDiarioV6(diaAtual, horaAtual, random);
            }

            // 6. Comércio B2B Inter-Lojas (Loja Geral adquire de especializadas da mesma cidade)
            for (Lojas loja : cidade.getLojas()) {
                if (loja.getTipoEstabelecimento() == Lojas.TipoEstabelecimento.LOJA_GERAL) {
                    loja.executarComprasB2B(cidade.getLojas(), diaAtual, horaAtual);
                }
            }

            // 7. Processamento das Requisições ativas da Guilda Regional da Cidade
            if (cidade.getGuildaRegional() != null) {
                cidade.getGuildaRegional().processarDiaRequisicoes(diaAtual, horaAtual, random);
            }
        }
    }

    /**
     * Localiza a melhor fornecedora no grafo (menor custo de mercadoria + frete),
     * desconta benefício de rede de lojas se aplicável, deduz do caixa da compradora
     * e expede a caravana comercial com suporte a escoltas.
     */
    public boolean solicitarCompraEntreCidades(Lojas compradora, Item itemDesejado, int qtd) {
        Cidade destino = compradora.getCidadeSede();
        if (destino == null || itemDesejado == null || qtd <= 0) return false;
        // Item regional só viaja para cidades que pelo menos ouviram falar dele
        RedeConhecimento.Estagio estagio = redeConhecimento.estagioDoItem(itemDesejado.getId(), destino.getId(), diaAtual);
        if (estagio != null && !estagio.peloMenos(RedeConhecimento.Estagio.RUMOR)) return false;

        Lojas melhorFornecedora = null;
        RotaComercial melhorRota = null;
        long menorCustoTotalLatao = Long.MAX_VALUE;
        long custoMercadoriaEscolhidaLatao = 0;

        double pesoTotalCarga = itemDesejado.getPeso() * qtd;

        for (Cidade outraCidade : cidades) {
            if (outraCidade.equals(destino)) continue;

            // Só calcula a rota (Dijkstra) se alguma loja da cidade tiver o item: é a parte cara do ciclo
            boolean alguemTem = false;
            for (Lojas l : outraCidade.getLojas()) {
                if (l.getQuantidadeEstoque(itemDesejado.getId()) >= qtd) { alguemTem = true; break; }
            }
            if (!alguemTem) continue;

            RotaComercial rota = rotasDoDia.computeIfAbsent(outraCidade.getId() + ">" + destino.getId(),
                    k -> Optional.ofNullable(NavegadorGrafo.calcularMelhorRota(outraCidade, destino, 0.25))).orElse(null);
            if (rota == null) continue;

            for (Lojas fornecedora : outraCidade.getLojas()) {
                if (fornecedora.getQuantidadeEstoque(itemDesejado.getId()) < qtd) continue;

                long precoUnitarioLatao = fornecedora.calcularPrecoDinamicoVendaEmLatao(itemDesejado);

                // Desconto de 25% entre estabelecimentos da mesma rede comercial
                if (compradora.isLojaDeRede() && fornecedora.isLojaDeRede()
                        && Objects.equals(compradora.getNomeDaRede(), fornecedora.getNomeDaRede())) {
                    precoUnitarioLatao = Math.round(precoUnitarioLatao * 0.75);
                }

                long custoProdutos = precoUnitarioLatao * qtd;
                long custoFrete = rota.calcularCustoFreteEmLatao(pesoTotalCarga);
                long custoTotal = custoProdutos + custoFrete;

                if (custoTotal < menorCustoTotalLatao) {
                    menorCustoTotalLatao = custoTotal;
                    melhorFornecedora = fornecedora;
                    melhorRota = rota;
                    custoMercadoriaEscolhidaLatao = custoProdutos;
                }
            }
        }

        if (melhorFornecedora == null || melhorRota == null) return false;

        // Verifica se a loja compradora tem saldo em latão para mercadoria + frete (+ escolta caso recomendada)
        long custoEscoltaEstimado = melhorRota.recomendaEscolta() ? melhorRota.calcularCustoEscoltaEmLatao() : 0L;
        if (compradora.caixaDisponivelParaCompras() < menorCustoTotalLatao + custoEscoltaEstimado) {
            return false;
        }

        // Mercador racional: só encomenda se a mercadoria chega custando bem menos do que vende na própria loja.
        // Sem isso, frete e escolta consumiam mais do que todo o caixa do mundo em poucas semanas.
        long valorDeRevendaLocal = compradora.calcularPrecoDinamicoVendaEmLatao(itemDesejado) * qtd;
        if (menorCustoTotalLatao + custoEscoltaEstimado > Math.round(valorDeRevendaLocal * MARGEM_MAXIMA_CUSTO_ENCOMENDA)) {
            return false;
        }

        // 1. Pagamento das mercadorias à fornecedora
        compradora.getCaixa().pagarLatao(custoMercadoriaEscolhidaLatao);
        melhorFornecedora.getCaixa().receberLatao(custoMercadoriaEscolhidaLatao);
        melhorFornecedora.removerUnidades(itemDesejado.getId(), qtd);

        // 2. Pagamento do frete
        long freteLatao = melhorRota.calcularCustoFreteEmLatao(pesoTotalCarga);
        melhorRota.pagarFrete(compradora.getCaixa(), pesoTotalCarga);

        melhorFornecedora.registrarMovimentacao(diaAtual, horaAtual,
                RegistroMovimentacao.TipoOperacao.VENDA_INTERMUNICIPAL,
                itemDesejado.getNome(), qtd, custoMercadoriaEscolhidaLatao,
                "Venda para " + compradora.getNome() + " (" + destino.getNome() + ")");

        compradora.registrarMovimentacao(diaAtual, horaAtual,
                RegistroMovimentacao.TipoOperacao.COMPRA_INTERMUNICIPAL,
                itemDesejado.getNome(), qtd, custoMercadoriaEscolhidaLatao,
                "Encomenda de " + melhorFornecedora.getNome() + " (" + melhorFornecedora.getCidadeSede().getNome() + ")");
        compradora.registrarMovimentacao(diaAtual, horaAtual,
                RegistroMovimentacao.TipoOperacao.DESPESA_FRETE,
                itemDesejado.getNome(), qtd, freteLatao,
                "Frete da encomenda");

        Item loteCarga = Lojas.clonarItem(itemDesejado, qtd);
        Caravana caravana = new Caravana(melhorFornecedora, compradora, loteCarga, melhorRota, diaAtual, horaAtual);
        caravanas.add(caravana);

        // 3. Recomendação de escolta com base no risco da rota
        if (melhorRota.recomendaEscolta()) {
            long recompensaEscolta = melhorRota.calcularCustoEscoltaEmLatao();
            missoes.add(new MissaoComercial(
                    MissaoComercial.TipoMissao.ESCOLTA,
                    melhorFornecedora.getCidadeSede(),
                    caravana,
                    recompensaEscolta,
                    12 // 12 horas de prazo para aventureiros no quadro de avisos
            ));
        } else {
            caravana.iniciarViagem(Caravana.TipoProtecao.DESPROTEGIDA, diaAtual, horaAtual);
        }

        return true;
    }

    private void processarMissoesPendentes(int horas) {
        for (MissaoComercial missao : missoes) {
            if (missao.getStatus() != MissaoComercial.StatusMissao.DISPONIVEL_JOGADORES) continue;

            missao.reduzirPrazo(horas);
            if (!missao.expirou()) continue;

            Caravana caravana = missao.getCaravanaAlvo();

            if (missao.getTipo() == MissaoComercial.TipoMissao.ESCOLTA) {
                boolean npcDisponivel = random.nextInt(100) < 60;
                Lojas compradora = caravana.getLojaDestino();

                if (npcDisponivel && caravana.getRota().pagarEscolta(compradora.getCaixa())) {
                    missao.setStatus(MissaoComercial.StatusMissao.ASSUMIDA_POR_NPCS);
                    compradora.registrarMovimentacao(diaAtual, horaAtual,
                            RegistroMovimentacao.TipoOperacao.DESPESA_ESCOLTA,
                            caravana.getCarga().getNome(), caravana.getCarga().getQuantidade(),
                            missao.getRecompensaEmLatao(), "Escolta de mercenários NPCs contratada");
                    caravana.iniciarViagem(Caravana.TipoProtecao.ESCOLTADA_NPC, diaAtual, horaAtual);
                } else {
                    missao.setStatus(MissaoComercial.StatusMissao.EXPIRADA_SEM_ESCOLTA);
                    caravana.iniciarViagem(Caravana.TipoProtecao.DESPROTEGIDA, diaAtual, horaAtual);
                }
            } else if (missao.getTipo() == MissaoComercial.TipoMissao.RESGATE_CARGA) {
                Conexao aresta = caravana.getArestaAtual();
                int chanceRecuperar = Math.max(15, 65 - (aresta != null ? aresta.getPerigo() * 5 : 0));

                if (random.nextInt(100) < chanceRecuperar) {
                    missao.setStatus(MissaoComercial.StatusMissao.CONCLUIDA_SUCESSO);
                    if (aresta != null) aresta.ajustarPerigo(-1); // Milícia pacifica o trecho (-1 perigo)
                    caravana.concluirEntrega(diaAtual, horaAtual);
                    caravana.getLojaDestino().registrarMovimentacao(diaAtual, horaAtual,
                            RegistroMovimentacao.TipoOperacao.CARGA_RECUPERADA,
                            caravana.getCarga().getNome(), caravana.getCarga().getQuantidade(),
                            0L, "Carga recuperada por milícia local de NPCs");
                } else {
                    missao.setStatus(MissaoComercial.StatusMissao.FRACASSADA);
                    caravana.setStatus(Caravana.StatusCaravana.PERDIDA_DEFINITIVAMENTE);
                    if (aresta != null) aresta.ajustarPerigo(+1); // Saqueadores se estabelecem (+1 perigo)
                }
            }
        }
    }

    private void processarCaravanasEmTransito(int horas) {
        for (Caravana caravana : caravanas) {
            if (caravana.getStatus() == Caravana.StatusCaravana.EM_TRANSITO) {
                boolean extraviou = caravana.avancarHoras(horas, diaAtual, horaAtual, random);
                if (extraviou) {
                    long recompensaResgate = Math.max(10_000L, Math.round(caravana.getCarga().getValorTotalEmLatao() * 0.35));
                    missoes.add(new MissaoComercial(
                            MissaoComercial.TipoMissao.RESGATE_CARGA,
                            caravana.getCidadeMaisProxima(),
                            caravana,
                            recompensaResgate,
                            24 // 24 horas para resgate da carga
                    ));
                    caravana.getLojaDestino().registrarMovimentacao(diaAtual, horaAtual,
                            RegistroMovimentacao.TipoOperacao.EXTRAVIO_CARGA,
                            caravana.getCarga().getNome(), caravana.getCarga().getQuantidade(),
                            0L, "Extraviada em " + caravana.descreverPosicaoNoGrafo());
                } else if (caravana.getStatus() == Caravana.StatusCaravana.ENTREGUE) {
                    // Verifica se havia missão de escolta assumida por jogador para honrar o contrato
                    for (MissaoComercial m : missoes) {
                        if (m.getCaravanaAlvo().equals(caravana) && m.getTipo() == MissaoComercial.TipoMissao.ESCOLTA) {
                            if (m.getStatus() == MissaoComercial.StatusMissao.ACEITA_POR_JOGADOR) {
                                m.concluirMissaoJogador(diaAtual, horaAtual);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Dias que uma caravana entregue ou perdida continua na memória antes de ser descartada. */
    private static final int DIAS_MEMORIA_CARAVANA = 7;

    /**
     * Esquece caravanas encerradas há mais de uma semana (e suas missões encerradas).
     * Sem isso a lista crescia para sempre e era percorrida para cada item, todo dia.
     */
    private void limparCaravanasConcluidas() {
        Set<Caravana> descartadas = new HashSet<>();
        for (Caravana c : caravanas) {
            if (!c.isAtiva() && c.getDiaUltimoEvento() < diaAtual - DIAS_MEMORIA_CARAVANA) descartadas.add(c);
        }
        if (descartadas.isEmpty()) return;
        caravanas.removeAll(descartadas);
        missoes.removeIf(m -> descartadas.contains(m.getCaravanaAlvo())
                && m.getStatus() != MissaoComercial.StatusMissao.DISPONIVEL_JOGADORES
                && m.getStatus() != MissaoComercial.StatusMissao.ACEITA_POR_JOGADOR);
    }

    /**
     * Devolve ao mundo uma caravana carregada do banco. As missões não são salvas:
     * quem esperava escolta parte desprotegida, e carga extraviada ganha nova missão de resgate.
     */
    public void restaurarCaravana(Caravana c) {
        if (c == null) return;
        caravanas.add(c);
        if (c.getStatus() == Caravana.StatusCaravana.AGUARDANDO_ESCOLTA) {
            c.iniciarViagem(Caravana.TipoProtecao.DESPROTEGIDA, diaAtual, horaAtual);
        } else if (c.getStatus() == Caravana.StatusCaravana.EXTRAVIADA) {
            long recompensa = Math.max(10_000L, Math.round(c.getCarga().getValorTotalEmLatao() * 0.35));
            missoes.add(new MissaoComercial(MissaoComercial.TipoMissao.RESGATE_CARGA, c.getCidadeMaisProxima(), c, recompensa, 24));
        }
    }

    public List<Caravana> getCaravanasAtivas() {
        List<Caravana> lista = new ArrayList<>();
        for (Caravana c : caravanas) if (c.isAtiva()) lista.add(c);
        return lista;
    }

    /** Missão ainda em aberto ligada à caravana (escolta ou resgate), se houver. */
    public MissaoComercial missaoAbertaDa(Caravana c) {
        for (MissaoComercial m : missoes) {
            if (m.getCaravanaAlvo().equals(c) && (m.getStatus() == MissaoComercial.StatusMissao.DISPONIVEL_JOGADORES
                    || m.getStatus() == MissaoComercial.StatusMissao.ACEITA_POR_JOGADOR)) return m;
        }
        return null;
    }

    public boolean temCaravanaAtivaPara(Lojas destino, int idItem) {
        for (Caravana c : caravanas) {
            if (c.getLojaDestino().equals(destino) && c.getCarga().getId() == idItem
                    && (c.getStatus() == Caravana.StatusCaravana.AGUARDANDO_ESCOLTA
                    || c.getStatus() == Caravana.StatusCaravana.EM_TRANSITO
                    || c.getStatus() == Caravana.StatusCaravana.EXTRAVIADA)) {
                return true;
            }
        }
        return false;
    }

    // Buscas e Filtros Utilitários para o Menu e Simulação
    public Cidade buscarCidadePorNome(String nome) {
        if (nome == null) return null;
        for (Cidade c : cidades) {
            if (c.getNome().equalsIgnoreCase(nome.trim())) return c;
        }
        return null;
    }

    public Lojas buscarLojaPorNome(String nome) {
        if (nome == null) return null;
        for (Cidade c : cidades) {
            for (Lojas l : c.getLojas()) {
                if (l.getNome().equalsIgnoreCase(nome.trim())) return l;
            }
        }
        return null;
    }

    public Caravana buscarCaravanaPorId(int id) {
        for (Caravana c : caravanas) {
            if (c.getId() == id) return c;
        }
        return null;
    }

    public MissaoComercial buscarMissaoPorId(int id) {
        for (MissaoComercial m : missoes) {
            if (m.getId() == id) return m;
        }
        return null;
    }

    public List<MissaoComercial> listarMissoesDisponiveis() {
        List<MissaoComercial> lista = new ArrayList<>();
        for (MissaoComercial m : missoes) {
            if (m.getStatus() == MissaoComercial.StatusMissao.DISPONIVEL_JOGADORES) {
                lista.add(m);
            }
        }
        return Collections.unmodifiableList(lista);
    }

    /**
     * Lista contratos disponíveis exclusivamente na cidade onde o aventureiro se encontra (Requisito 4).
     */
    public List<MissaoComercial> listarMissoesDisponiveis(Cidade cidade) {
        if (cidade == null) return listarMissoesDisponiveis();
        List<MissaoComercial> lista = new ArrayList<>();
        for (MissaoComercial m : missoes) {
            if (m.getStatus() == MissaoComercial.StatusMissao.DISPONIVEL_JOGADORES
                    && cidade.equals(m.getCidadeDisponivel())) {
                lista.add(m);
            }
        }
        return Collections.unmodifiableList(lista);
    }

    public List<MissaoComercial> listarMissoesPorCidade(Cidade cidade) {
        List<MissaoComercial> lista = new ArrayList<>();
        if (cidade == null) return lista;
        for (MissaoComercial m : missoes) {
            if (m.getCidadeDisponivel().equals(cidade)) {
                lista.add(m);
            }
        }
        return Collections.unmodifiableList(lista);
    }

    /** Recebe do banco as receitas cadastradas e o catálogo de itens. */
    public void definirReceitasECatalogo(List<ReceitaLoja> receitas, Collection<Item> catalogo) {
        this.receitas = new ArrayList<>(receitas);
        this.receitasPorId = new HashMap<>();
        for (ReceitaLoja r : receitas) receitasPorId.put(r.id(), r);
        this.catalogoItens = new HashMap<>();
        for (Item i : catalogo) catalogoItens.put(i.getId(), i);
    }

    public List<ReceitaLoja> getReceitas() { return Collections.unmodifiableList(receitas); }

    /** Quem sabe fazer o quê (disseminação de tipos de arma e receitas regionais). */
    public RedeConhecimento getRedeConhecimento() { return redeConhecimento; }

    public void setRedeConhecimento(RedeConhecimento rede) {
        this.redeConhecimento = rede != null ? rede : RedeConhecimento.vazia();
    }
    public Map<Integer, ReceitaLoja> getReceitasPorId() { return Collections.unmodifiableMap(receitasPorId); }
    public Map<Integer, Item> getCatalogoItens() { return Collections.unmodifiableMap(catalogoItens); }

    public Lojas buscarLojaPorId(int id) {
        for (Cidade c : cidades) {
            for (Lojas l : c.getLojas()) {
                if (l.getId() == id) return l;
            }
        }
        return null;
    }

    // Getters de Estado
    public MotorEconomico getMapa() { return this; }
    public List<Cidade> getCidades() { return Collections.unmodifiableList(cidades); }
    public List<Caravana> getCaravanas() { return Collections.unmodifiableList(caravanas); }
    public List<MissaoComercial> getMissoes() { return Collections.unmodifiableList(missoes); }
    public int getDiaAtual() { return diaAtual; }
    public int getHoraAtual() { return horaAtual; }
    public void setDiaAtual(int diaAtual) { this.diaAtual = diaAtual; }
    public void setHoraAtual(int horaAtual) { this.horaAtual = horaAtual; }
}
