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

    /** Relógio do mundo (mercado): nunca anda para trás e fica igual ou à frente da mesa ativa mais adiantada. */
    private int diaAtual = 1;
    private int horaAtual = 8;
    /**
     * Mesas de jogo, cada uma com o seu relógio (docs/Sistema de Economia/05). O dia da campanha do motor
     * é o da mesa ativa mais à frente: simular o mercado (aquecer) não move mesa nenhuma, então não espalha rumores.
     */
    private final Map<Integer, Mesa> mesas = new LinkedHashMap<>();
    /** Dia da campanha usado só quando não há mesa ativa (testes e mundos sem mesas). */
    private int diaCampanha = 1;
    /** Chamado no fim de cada ciclo diário: o servidor grava a foto do mercado do dia (mercado_snapshot). */
    private java.util.function.ObjIntConsumer<MotorEconomico> aoFecharDia;

    /** Rotas já calculadas no ciclo atual (RotaComercial é imutável); limpo a cada dia, pois o perigo muda. */
    private final Map<String, Optional<RotaComercial>> rotasDoDia = new HashMap<>();

    /** Custo entregue (mercadoria + frete + escolta) máximo, como fração do valor de revenda local. */
    private static final double MARGEM_MAXIMA_CUSTO_ENCOMENDA = 0.80;
    /** Custo de esperar a carga, por hora, como fração do valor de revenda (dinheiro parado). */
    private static final double CUSTO_HORA_ESPERA = 0.002;
    /** Idem quando a prateleira está vazia: cada hora de espera é venda perdida. */
    private static final double CUSTO_HORA_ESPERA_SEM_ESTOQUE = 0.01;
    /**
     * Risco esperado quando a rota pede escolta: 60% de chance de achar mercenários NPCs (-75%)
     * se nenhum jogador aceitar, senão viaja desprotegida (0,6 × 0,25 + 0,4 × 1).
     */
    private static final double FATOR_RISCO_COM_ESCOLTA_RECOMENDADA = 0.55;

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
                if (mesas.isEmpty()) diaCampanha++;
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
        int diaCampanha = getDiaCampanha();
        for (Cidade cidade : cidades) {
            // Receitas regionais só saem das cidades que já chegaram à Produção (as comuns valem em todo lugar)
            List<ReceitaLoja> receitasDaCidade = receitas.stream()
                    .filter(r -> redeConhecimento.receitaLiberada(r.id(), cidade.getId(), diaCampanha)).toList();
            for (Lojas loja : cidade.getLojas()) {
                loja.decairFluxoRecente();

                // Conhecimento que chegou ao estágio Comércio: a loja passa a procurar o item
                for (Integer itemId : redeConhecimento.itensParaPrateleira(loja, diaCampanha)) {
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
                    RedeConhecimento.Estagio estagio = redeConhecimento.estagioDoItem(modelo.getId(), cidade.getId(), diaCampanha);
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

        // 8. Foto do mercado do dia, lida pelas mesas que ficarem para trás
        if (aoFecharDia != null) aoFecharDia.accept(this, diaAtual);
    }

    /**
     * Localiza a melhor combinação de fornecedora e serviço de transporte no grafo,
     * desconta benefício de rede de lojas se aplicável, deduz do caixa da compradora
     * e expede a caravana comercial com suporte a escoltas.
     *
     * <p>O mercador escolhe pelo menor custo efetivo: o que sai do caixa (mercadoria,
     * frete do serviço e escolta) mais a perda esperada (valor de revenda × chance de
     * ataque) mais o custo da espera (horas de viagem × valor de revenda × taxa).
     * Rotas perigosas e cargas valiosas puxam para serviços seguros; prateleira vazia
     * puxa para os rápidos.
     */
    public boolean solicitarCompraEntreCidades(Lojas compradora, Item itemDesejado, int qtd) {
        Cidade destino = compradora.getCidadeSede();
        if (destino == null || itemDesejado == null || qtd <= 0) return false;
        // Item regional só viaja para cidades que pelo menos ouviram falar dele
        RedeConhecimento.Estagio estagio = redeConhecimento.estagioDoItem(itemDesejado.getId(), destino.getId(), getDiaCampanha());
        if (estagio != null && !estagio.peloMenos(RedeConhecimento.Estagio.RUMOR)) return false;

        Lojas melhorFornecedora = null;
        RotaComercial melhorRota = null;
        ServicoTransporte melhorServico = null;
        double menorCustoEfetivo = Double.MAX_VALUE;
        long custoMercadoriaEscolhidaLatao = 0;

        double pesoTotalCarga = itemDesejado.getPeso() * qtd;
        long caixaDisponivel = compradora.caixaDisponivelParaCompras();

        // Mercador racional: só encomenda se a mercadoria chega custando bem menos do que vende na própria loja.
        // Sem isso, frete e escolta consumiam mais do que todo o caixa do mundo em poucas semanas.
        long valorDeRevendaLocal = compradora.calcularPrecoDinamicoVendaEmLatao(itemDesejado) * qtd;
        long custoMaximoAceito = Math.round(valorDeRevendaLocal * MARGEM_MAXIMA_CUSTO_ENCOMENDA);
        double custoPorHoraDeEspera = valorDeRevendaLocal * (compradora.getQuantidadeEstoque(itemDesejado.getId()) == 0
                ? CUSTO_HORA_ESPERA_SEM_ESTOQUE : CUSTO_HORA_ESPERA);

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

                for (ServicoTransporte servico : ServicoTransporte.values()) {
                    boolean escolta = rota.recomendaEscolta(servico);
                    // Mercadoria + frete (+ escolta caso recomendada): precisa caber no caixa e na margem
                    long custoCaixa = custoProdutos + rota.calcularCustoFreteEmLatao(pesoTotalCarga, servico)
                            + (escolta ? rota.calcularCustoEscoltaEmLatao() : 0L);
                    if (custoCaixa > caixaDisponivel || custoCaixa > custoMaximoAceito) continue;

                    double chanceAtaque = rota.chanceDeAtaque(servico, escolta ? FATOR_RISCO_COM_ESCOLTA_RECOMENDADA : 1.0);
                    double custoEfetivo = custoCaixa + chanceAtaque * valorDeRevendaLocal
                            + rota.horasDeViagem(servico) * custoPorHoraDeEspera;
                    if (custoEfetivo < menorCustoEfetivo) {
                        menorCustoEfetivo = custoEfetivo;
                        melhorFornecedora = fornecedora;
                        melhorRota = rota;
                        melhorServico = servico;
                        custoMercadoriaEscolhidaLatao = custoProdutos;
                    }
                }
            }
        }

        if (melhorFornecedora == null || melhorRota == null) return false;

        // 1. Pagamento das mercadorias à fornecedora
        compradora.getCaixa().pagarLatao(custoMercadoriaEscolhidaLatao);
        melhorFornecedora.getCaixa().receberLatao(custoMercadoriaEscolhidaLatao);
        melhorFornecedora.removerUnidades(itemDesejado.getId(), qtd);

        // 2. Pagamento do frete
        long freteLatao = melhorRota.calcularCustoFreteEmLatao(pesoTotalCarga, melhorServico);
        melhorRota.pagarFrete(compradora.getCaixa(), pesoTotalCarga, melhorServico);

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
                "Frete da encomenda (" + melhorServico.getDescricao() + ")");

        Item loteCarga = Lojas.clonarItem(itemDesejado, qtd);
        Caravana caravana = new Caravana(melhorFornecedora, compradora, loteCarga, melhorRota, melhorServico, diaAtual, horaAtual);
        caravanas.add(caravana);

        // 3. Recomendação de escolta com base no risco da rota para o serviço contratado
        if (melhorRota.recomendaEscolta(melhorServico)) {
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

    /** Momento do mundo em horas corridas (mesma escala de {@link Mesa#momento()}). */
    public int momento() { return Mesa.momento(diaAtual, horaAtual); }

    // ------------------------------------------------------------------
    // Mesas e linhas do tempo (docs/Sistema de Economia/05)
    // ------------------------------------------------------------------

    /** Dia da campanha do motor: o da mesa ativa mais à frente (sem mesa ativa, o dia guardado). */
    public int getDiaCampanha() {
        int maior = 0;
        for (Mesa m : mesas.values()) if (m.isAtiva()) maior = Math.max(maior, m.getDia());
        return maior > 0 ? maior : diaCampanha;
    }

    public void setDiaCampanha(int diaCampanha) { this.diaCampanha = diaCampanha; }

    /** Dia da mesa ativa mais atrasada (sem mesa ativa, o do mundo): daí para trás os snapshots podem ser apagados. */
    public int getDiaMesaMaisAtrasada() {
        int menor = Integer.MAX_VALUE;
        for (Mesa m : mesas.values()) if (m.isAtiva()) menor = Math.min(menor, m.getDia());
        return menor == Integer.MAX_VALUE ? diaAtual : menor;
    }

    public void definirMesas(Collection<Mesa> lista) {
        mesas.clear();
        for (Mesa m : lista) mesas.put(m.getId(), m);
    }

    public void registrarMesa(Mesa m) { if (m != null) mesas.put(m.getId(), m); }
    public Mesa getMesa(int id) { return mesas.get(id); }
    public Collection<Mesa> getMesas() { return Collections.unmodifiableCollection(mesas.values()); }

    public void setAoFecharDia(java.util.function.ObjIntConsumer<MotorEconomico> aoFecharDia) { this.aoFecharDia = aoFecharDia; }
    public java.util.function.ObjIntConsumer<MotorEconomico> getAoFecharDia() { return aoFecharDia; }

    /** A mesa está num dia anterior ao do mundo: lê o snapshot do dia dela e não mexe em caravanas. */
    public boolean mesaNoPassado(Mesa m) {
        return m != null && m.getDia() < diaAtual;
    }

    /**
     * Anda o relógio de uma mesa. Se ela passar o mundo, o motor simula só a diferença
     * (o mundo segue a mesa mais à frente). Mesa arquivada não puxa o mundo.
     *
     * @return horas que o mundo andou
     */
    public int avancarMesa(Mesa mesa, int horas) {
        mesa.avancar(horas);
        int diferenca = mesa.momento() - momento();
        if (!mesa.isAtiva() || diferenca <= 0) return 0;
        avancarTempo(diferenca);
        return diferenca;
    }

    /** Encomenda de mesa entregue: o resultado vai à ficha (fichaId) ou já entrou no balcão da loja (fichaId nulo). */
    public record EntregaEncomenda(Lojas loja, Mesa mesa, Long fichaId, Item item) {}

    /**
     * Entrega as encomendas cujas mesas chegaram ao prazo. A loja pode ainda não ter terminado
     * (mesa atrasada): no passado, a peça já estava pronta. Quem chama leva os itens às fichas.
     */
    public List<EntregaEncomenda> entregarEncomendasVencidas() {
        List<EntregaEncomenda> entregas = new ArrayList<>();
        for (Cidade c : cidades) {
            for (Lojas l : c.getLojas()) {
                for (OrdemReceita o : new ArrayList<>(l.getOrdensReceita())) {
                    if (!o.isDaMesa()) continue;
                    Mesa m = mesas.get(o.getMesaId());
                    if (m == null || o.getDiaConclusaoMesa() == null || m.getDia() < o.getDiaConclusaoMesa()) continue;
                    Item item = l.entregarEncomenda(o, diaAtual, horaAtual, receitasPorId, catalogoItens,
                            "[Mesa " + m.getNome() + " · dia " + o.getDiaConclusaoMesa() + "]");
                    if (item != null) entregas.add(new EntregaEncomenda(l, m, o.getFichaId(), item));
                }
            }
        }
        return entregas;
    }

    /** O personagem trocou de mesa: as encomendas dele passam a seguir o relógio da nova. */
    public void moverEncomendasDaFicha(long fichaId, int novaMesaId) {
        for (Cidade c : cidades) {
            for (Lojas l : c.getLojas()) {
                for (OrdemReceita o : l.getOrdensReceita()) {
                    if (o.getFichaId() != null && o.getFichaId() == fichaId) o.moverParaMesa(novaMesaId);
                }
            }
        }
    }
}
