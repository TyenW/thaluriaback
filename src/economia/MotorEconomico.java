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
        for (Cidade cidade : cidades) {
            for (Lojas loja : cidade.getLojas()) {
                loja.decairFluxoRecente();

                for (Item modelo : new ArrayList<>(loja.getCatalogoConhecido().values())) {
                    // 1. Entrada de fornecedores NPCs locais baseada na geologia e bioma da cidade (Ponte para Crafting)
                    double chanceNpc = cidade.calcularChanceFornecedorNpc(modelo);
                    if (random.nextDouble() < chanceNpc) {
                        int qtdFornecida = cidade.calcularQtdFornecedorNpc(modelo, random);
                        loja.comprarDeNpc(modelo, qtdFornecida, diaAtual, horaAtual);
                    }

                    // 2. Oscilação orgânica da demanda (entre 2 e 10 unidades)
                    loja.definirDemanda(modelo, 2 + random.nextInt(9));

                    // 3. Encomenda intermunicipal caso falte estoque para atender à demanda projetada
                    int deficit = loja.getDeficitDemanda(modelo.getId());
                    if (deficit > 0 && !temCaravanaAtivaPara(loja, modelo.getId())) {
                        solicitarCompraEntreCidades(loja, modelo, deficit);
                    }
                }

                // 4. Manufatura autônoma da loja para atender ao livro de receitas de artesanato
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

        Lojas melhorFornecedora = null;
        RotaComercial melhorRota = null;
        long menorCustoTotalLatao = Long.MAX_VALUE;
        long custoMercadoriaEscolhidaLatao = 0;

        double pesoTotalCarga = itemDesejado.getPeso() * qtd;

        for (Cidade outraCidade : cidades) {
            if (outraCidade.equals(destino)) continue;

            RotaComercial rota = NavegadorGrafo.calcularMelhorRota(outraCidade, destino, 0.25);
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
        if (!compradora.getCaixa().temSaldoEmLatao(menorCustoTotalLatao + custoEscoltaEstimado)) {
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
                RegistroMovimentacao.TipoOperacao.DESPESA_FRETE,
                itemDesejado.getNome(), qtd, custoMercadoriaEscolhidaLatao + freteLatao,
                "Encomenda + Frete de " + melhorFornecedora.getNome());

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

    // Getters de Estado
    public List<Cidade> getCidades() { return Collections.unmodifiableList(cidades); }
    public List<Caravana> getCaravanas() { return Collections.unmodifiableList(caravanas); }
    public List<MissaoComercial> getMissoes() { return Collections.unmodifiableList(missoes); }
    public int getDiaAtual() { return diaAtual; }
    public int getHoraAtual() { return horaAtual; }
    public void setDiaAtual(int diaAtual) { this.diaAtual = diaAtual; }
    public void setHoraAtual(int horaAtual) { this.horaAtual = horaAtual; }
}
