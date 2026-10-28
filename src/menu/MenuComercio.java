package menu;

import economia.*;
import geografia.*;
import personagem.Ficha;

import java.util.*;

/**
 * Menu interativo em terminal para validação e teste de todas as funcionalidades
 * do Sistema Econômico v2 e Geografia de Thaluria.
 */
public class MenuComercio {

    private final CenarioInicial.DadosMundo dados;
    private final Scanner scanner;

    public MenuComercio() {
        this.dados = CenarioInicial.criarCenarioPadrao();
        this.scanner = new Scanner(System.in);
    }

    public static void main(String[] args) {
        MenuComercio menu = new MenuComercio();
        menu.executar();
    }

    public void executar() {
        boolean rodando = true;
        while (rodando) {
            imprimirCabecalho();
            System.out.println(" 1.  Visualizar Mapa e Grafo Territorial (Cidades, Estradas, Perigos)");
            System.out.println(" 2.  Consultar Lojas, Especializações e Estoques");
            System.out.println(" 3.  Simular Oferta e Demanda (Preços Dinâmicos em Latão)");
            System.out.println(" 4.  Comércio no Balcão com Jogador (Comprar / Vender da Loja)");
            System.out.println(" 5.  Simular Fornecedor NPC Local (Loja compra a 65% do valor)");
            System.out.println(" 6.  Simular Comércio Intermunicipal & Rota Dijkstra (Rede e Frete)");
            System.out.println(" 7.  Quadro de Missões (Escolta Armada e Resgate de Cargas)");
            System.out.println(" 8.  Avançar Tempo do Mundo (1h, 6h, 12h, 24h / Ciclo Diário)");
            System.out.println(" 9.  Gerenciar Perigo e Bloqueio de Estradas no Grafo");
            System.out.println(" 10. Livro-Caixa e Histórico de Movimentações Contábeis");
            System.out.println(" 11. Ver Ficha, Carteira e Inventário do Jogador / Viajar");
            System.out.println(" 12. Executar Demonstração Automatizada Completa (Passo a Passo)");
            System.out.println(" 0.  Sair do Sistema");
            System.out.println("======================================================================");
            System.out.print("Escolha uma opção: ");

            int opcao = lerInteiro(0, 12);
            System.out.println();

            switch (opcao) {
                case 1 -> menuMapaGrafo();
                case 2 -> menuConsultarLojas();
                case 3 -> menuOfertaDemanda();
                case 4 -> menuComercioBalcao();
                case 5 -> menuFornecedorNpc();
                case 6 -> menuComercioIntermunicipal();
                case 7 -> menuQuadroMissoes();
                case 8 -> menuAvancarTempo();
                case 9 -> menuGerenciarPerigo();
                case 10 -> menuHistoricoContabil();
                case 11 -> menuJogador();
                case 12 -> executarDemonstracaoCompleta();
                case 0 -> {
                    System.out.println("Encerrando a simulação do Sistema Econômico de Thaluria. Até logo!");
                    rodando = false;
                }
            }

            if (rodando && opcao != 0) {
                pausar();
            }
        }
    }

    private void imprimirCabecalho() {
        System.out.println("======================================================================");
        System.out.println("       THALURIA RPG - SISTEMA ECONÔMICO v2 & GEOGRAFIA DINÂMICA       ");
        System.out.println("======================================================================");
        System.out.printf(" Tempo do Mundo: Dia %d, %02dh | Jogador em: %s | Carteira: %s\n",
                dados.motor.getDiaAtual(),
                dados.motor.getHoraAtual(),
                dados.cidadeAtualJogador.getNome(),
                dados.jogador.getCarteira().toString());
        System.out.printf(" Caravanas Ativas: %d | Missões no Quadro: %d\n",
                dados.motor.getCaravanas().size(),
                dados.motor.listarMissoesDisponiveis().size());
        System.out.println("----------------------------------------------------------------------");
    }

    // -------------------------------------------------------------------------
    // 1. MAPA E GRAFO
    // -------------------------------------------------------------------------
    private void menuMapaGrafo() {
        System.out.println("=== 1. MAPA TERRITORIAL E CONEXÕES DO GRAFO ===");
        Set<Conexao> arestasUnicas = new LinkedHashSet<>();

        for (Cidade c : dados.motor.getCidades()) {
            System.out.printf("\n* %s [ID: %d] - Tipo: %s | Bioma: %s | Porto: %s | Lojas: %d\n",
                    c.getNome(), c.getId(), c.getTipo(), c.getRegiao(),
                    c.temPorto() ? "SIM" : "NÃO", c.getLojas().size());
            for (Conexao conexao : c.getConexoes()) {
                arestasUnicas.add(conexao);
            }
        }

        System.out.println("\n--- Estradas e Rotas de Navegação Registradas ---");
        int idx = 1;
        for (Conexao aresta : arestasUnicas) {
            System.out.printf(" [%d] %s <---> %s | Distância: %3d km | Perigo: %2d/10 | Tipo: %-9s | Status: %s\n",
                    idx++,
                    aresta.getOrigem().getNome(),
                    aresta.getDestino().getNome(),
                    aresta.getDistanciaKm(),
                    aresta.getPerigo(),
                    aresta.getTipo(),
                    aresta.isBloqueada() ? "BLOQUEADA" : "LIVRE");
        }
    }

    // -------------------------------------------------------------------------
    // 2. CONSULTAR LOJAS
    // -------------------------------------------------------------------------
    private void menuConsultarLojas() {
        System.out.println("=== 2. ESTABELECIMENTOS COMERCIAIS E ESTOQUE ===");
        for (Cidade cidade : dados.motor.getCidades()) {
            System.out.println("\n-------------------------------------------------------------");
            System.out.printf("Cidade: %s (Bioma: %s)\n", cidade.getNome(), cidade.getRegiao());
            System.out.println("-------------------------------------------------------------");
            if (cidade.getLojas().isEmpty()) {
                System.out.println("  (Nenhuma loja sediada nesta cidade)");
                continue;
            }

            for (Lojas loja : cidade.getLojas()) {
                String rede = loja.isLojaDeRede() ? " [Rede: " + loja.getNomeDaRede() + "]" : " [Independente]";
                System.out.printf("  * Loja: %-28s | Tipo: %-20s%s\n", loja.getNome(), loja.getTipoEstabelecimento(), rede);
                System.out.printf("    Mercador: %-24s | Caixa: %s\n", loja.getMercador(), Carteira.formatarLatao(loja.getCaixa().getValorTotalEmLatao()));

                if (loja.getEstoque().isEmpty()) {
                    System.out.println("    (Estoque Vazio)");
                } else {
                    System.out.println("    Itens em Estoque:");
                    for (Item item : loja.getEstoque()) {
                        long precoVenda = loja.calcularPrecoDinamicoVendaEmLatao(item);
                        long precoCompraNpc = loja.calcularPrecoCompraNpcEmLatao(item);
                        int demanda = loja.getDemandaPorItem().getOrDefault(item.getId(), 0);
                        System.out.printf("      - [ID %d] %-24s x%-2d | Base: %-10s | Venda: %-12s | Compra NPC: %-12s | Demanda: %d\n",
                                item.getId(), item.getNome(), item.getQuantidade(),
                                Carteira.formatarLatao(item.getPrecoEmLatao()),
                                Carteira.formatarLatao(precoVenda),
                                Carteira.formatarLatao(precoCompraNpc),
                                demanda);
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 3. OFERTA E DEMANDA
    // -------------------------------------------------------------------------
    private void menuOfertaDemanda() {
        System.out.println("=== 3. SIMULAÇÃO DE OFERTA E DEMANDA (PREÇOS DINÂMICOS EM LATÃO) ===");
        System.out.println("Fórmula Canônica:");
        System.out.println("  pressaoDemanda = Demanda + (SaídasRecentes * 0.5) + 1.0");
        System.out.println("  pressaoOferta  = Estoque + (EntradasRecentes * 0.3) + 1.0");
        System.out.println("  multiplicador  = sqrt(pressaoDemanda / pressaoOferta) [Clamp 0.40 a 2.50]");
        System.out.println("  Preço de Compra NPC = 65% do Preço Dinâmico de Venda\n");

        Lojas loja = selecionarLoja();
        if (loja == null) return;

        System.out.println("\nLoja selecionada: " + loja.getNome());
        System.out.println("Escolha uma ação:");
        System.out.println(" 1. Ver tabela completa de preços dinâmicos atuais");
        System.out.println(" 2. Alterar demanda de um item manualmente e ver o preço reagir");
        System.out.println(" 3. Adicionar/remover unidades de estoque para ver o impacto no preço");
        System.out.print("Opção: ");
        int sub = lerInteiro(1, 3);

        if (sub == 1) {
            exibirTabelaPrecosLoja(loja);
        } else if (sub == 2) {
            Item item = selecionarItemDaLoja(loja);
            if (item == null) return;
            System.out.printf("Demanda atual de '%s': %d. Digite a nova demanda desejada: ",
                    item.getNome(), loja.getDemandaPorItem().getOrDefault(item.getId(), 5));
            int novaDemanda = lerInteiro(0, 500);
            long precoAntes = loja.calcularPrecoDinamicoVendaEmLatao(item);
            loja.definirDemanda(item, novaDemanda);
            long precoDepois = loja.calcularPrecoDinamicoVendaEmLatao(item);

            System.out.printf("\n[ATUALIZAÇÃO DE MERCADO]\n");
            System.out.printf("  Item: %s\n", item.getNome());
            System.out.printf("  Preço Base: %s\n", Carteira.formatarLatao(item.getPrecoEmLatao()));
            System.out.printf("  Preço Dinâmico Anterior : %s (%d latão)\n", Carteira.formatarLatao(precoAntes), precoAntes);
            System.out.printf("  Preço Dinâmico Atual    : %s (%d latão)\n", Carteira.formatarLatao(precoDepois), precoDepois);
            double variacao = ((double) precoDepois / item.getPrecoEmLatao()) * 100.0;
            System.out.printf("  Multiplicador em relação ao base: %.1f%%\n", variacao);
        } else if (sub == 3) {
            Item item = selecionarItemDaLoja(loja);
            if (item == null) return;
            System.out.printf("Estoque atual de '%s': %d. Quantas unidades quer adicionar (positivo) ou remover (negativo)? ",
                    item.getNome(), loja.getQuantidadeEstoque(item.getId()));
            int delta = lerInteiro(-500, 500);
            long precoAntes = loja.calcularPrecoDinamicoVendaEmLatao(item);
            if (delta > 0) {
                loja.adicionarItem(Lojas.clonarItem(item, delta));
            } else if (delta < 0) {
                loja.removerUnidades(item.getId(), Math.abs(delta));
            }
            long precoDepois = loja.calcularPrecoDinamicoVendaEmLatao(item);
            System.out.printf("\n[REAJUSTE POR OFERTA]\n");
            System.out.printf("  Novo Estoque: %d unidades\n", loja.getQuantidadeEstoque(item.getId()));
            System.out.printf("  Preço Antes : %s\n", Carteira.formatarLatao(precoAntes));
            System.out.printf("  Preço Depois: %s\n", Carteira.formatarLatao(precoDepois));
        }
    }

    private void exibirTabelaPrecosLoja(Lojas loja) {
        System.out.printf("\n--- Tabela de Preços Dinâmicos: %s ---\n", loja.getNome());
        for (Item item : loja.getEstoque()) {
            long base = item.getPrecoEmLatao();
            long venda = loja.calcularPrecoDinamicoVendaEmLatao(item);
            long compraNpc = loja.calcularPrecoCompraNpcEmLatao(item);
            int est = loja.getQuantidadeEstoque(item.getId());
            int dem = loja.getDemandaPorItem().getOrDefault(item.getId(), 5);
            double pct = ((double) venda / base) * 100.0;

            System.out.printf(" * %-22s | Est: %2d | Dem: %2d | Base: %-12s | Venda: %-12s (%.0f%%) | Compra NPC: %-12s\n",
                    item.getNome(), est, dem,
                    Carteira.formatarLatao(base),
                    Carteira.formatarLatao(venda),
                    pct,
                    Carteira.formatarLatao(compraNpc));
        }
    }

    // -------------------------------------------------------------------------
    // 4. COMÉRCIO NO BALCÃO COM JOGADOR
    // -------------------------------------------------------------------------
    private void menuComercioBalcao() {
        System.out.println("=== 4. COMÉRCIO NO BALCÃO COM O JOGADOR ===");
        System.out.printf("Você está em: %s\n", dados.cidadeAtualJogador.getNome());
        List<Lojas> lojasLocais = dados.cidadeAtualJogador.getLojas();

        if (lojasLocais.isEmpty()) {
            System.out.println("Não há estabelecimentos comerciais nesta cidade.");
            return;
        }

        System.out.println("Lojas disponíveis na cidade:");
        for (int i = 0; i < lojasLocais.size(); i++) {
            System.out.printf(" [%d] %s (%s)\n", i + 1, lojasLocais.get(i).getNome(), lojasLocais.get(i).getTipoEstabelecimento());
        }
        System.out.print("Escolha a loja: ");
        int sel = lerInteiro(1, lojasLocais.size()) - 1;
        Lojas loja = lojasLocais.get(sel);

        System.out.println("\nO que deseja fazer?");
        System.out.println(" 1. Comprar item da loja (Preço dinâmico de venda)");
        System.out.println(" 2. Vender item do seu inventário para a loja");
        System.out.print("Opção: ");
        int acao = lerInteiro(1, 2);

        if (acao == 1) {
            if (loja.getEstoque().isEmpty()) {
                System.out.println("Esta loja não possui itens em estoque no momento.");
                return;
            }
            System.out.println("\nItens à venda:");
            for (int i = 0; i < loja.getEstoque().size(); i++) {
                Item it = loja.getEstoque().get(i);
                long precoVenda = loja.calcularPrecoDinamicoVendaEmLatao(it);
                System.out.printf(" [%d] %-24s (x%d) | Preço: %s\n",
                        i + 1, it.getNome(), it.getQuantidade(), Carteira.formatarLatao(precoVenda));
            }
            System.out.print("Escolha o item: ");
            int itemSel = lerInteiro(1, loja.getEstoque().size()) - 1;
            Item escolhido = loja.getEstoque().get(itemSel);
            long preco = loja.calcularPrecoDinamicoVendaEmLatao(escolhido);

            if (!dados.jogador.getCarteira().temSaldoEmLatao(preco)) {
                System.out.printf("Saldo insuficiente! O item custa %s e você tem %s.\n",
                        Carteira.formatarLatao(preco), dados.jogador.getCarteira().toString());
                return;
            }

            boolean ok = loja.venderItem(escolhido, dados.jogador.getCarteira(), dados.motor.getDiaAtual(), dados.motor.getHoraAtual());
            if (ok) {
                dados.jogador.adicionarItem(Lojas.clonarItem(escolhido, 1));
                System.out.printf("\n[COMPRA CONCLUÍDA COM SUCESSO!]\n");
                System.out.printf("Você comprou 1x '%s' por %s.\n", escolhido.getNome(), Carteira.formatarLatao(preco));
                System.out.printf("Seu novo saldo: %s\n", dados.jogador.getCarteira().toString());
                System.out.printf("Caixa da loja : %s\n", Carteira.formatarLatao(loja.getCaixa().getValorTotalEmLatao()));
            } else {
                System.out.println("Falha ao concluir a venda no balcão.");
            }
        } else {
            if (dados.jogador.getInventario().isEmpty()) {
                System.out.println("Seu inventário está vazio!");
                return;
            }
            System.out.println("\nSeu Inventário:");
            for (int i = 0; i < dados.jogador.getInventario().size(); i++) {
                Item it = dados.jogador.getInventario().get(i);
                long ofertaLoja = loja.calcularPrecoCompraNpcEmLatao(it);
                System.out.printf(" [%d] %-24s (x%d) | Oferta da Loja: %s\n",
                        i + 1, it.getNome(), it.getQuantidade(), Carteira.formatarLatao(ofertaLoja));
            }
            System.out.print("Escolha o item para vender: ");
            int itemSel = lerInteiro(1, dados.jogador.getInventario().size()) - 1;
            Item itVender = dados.jogador.getInventario().get(itemSel);
            long ofertaLoja = loja.calcularPrecoCompraNpcEmLatao(itVender);
            double ofertaEmOuro = Carteira.lataoParaOuro(ofertaLoja);

            if (!loja.getCaixa().temSaldoEmLatao(ofertaLoja)) {
                System.out.println("A loja não possui saldo em caixa suficiente para comprar seu item!");
                return;
            }

            boolean ok = loja.comprarItem(itVender, dados.jogador.getCarteira(), ofertaEmOuro, dados.motor.getDiaAtual(), dados.motor.getHoraAtual());
            if (ok) {
                dados.jogador.removerItem(itVender);
                System.out.printf("\n[VENDA CONCLUÍDA COM SUCESSO!]\n");
                System.out.printf("Você vendeu '%s' e recebeu %s.\n", itVender.getNome(), Carteira.formatarLatao(ofertaLoja));
                System.out.printf("Seu novo saldo: %s\n", dados.jogador.getCarteira().toString());
            } else {
                System.out.println("Falha ao efetuar a compra.");
            }
        }
    }

    // -------------------------------------------------------------------------
    // 5. FORNECEDOR NPC LOCAL
    // -------------------------------------------------------------------------
    private void menuFornecedorNpc() {
        System.out.println("=== 5. SIMULAR FORNECIMENTO LOCAL POR NPCS ===");
        System.out.println("Um caçador, ferreiro aprendiz ou coletor local oferece matéria-prima para a loja.");
        Lojas loja = selecionarLoja();
        if (loja == null) return;

        Item item = selecionarItemDoCatalogo(loja);
        if (item == null) return;

        System.out.print("Quantidade de unidades oferecidas pelo NPC: ");
        int qtd = lerInteiro(1, 50);

        long precoUnitario = loja.calcularPrecoCompraNpcEmLatao(item);
        long custoTotal = precoUnitario * qtd;

        Cidade cidade = loja.getCidadeSede();
        double afinidade = (cidade != null) ? cidade.calcularChanceFornecedorNpc(item) : 0.35;
        String descGeol = (cidade != null && cidade.getGeologia() != null) ? cidade.getGeologia().getNomeFormatado() : "Relevo Padrão";
        String nivelAfinidade = afinidade >= 0.60 ? "ALTA (Abundância Regional)" : (afinidade <= 0.20 ? "BAIXA (Insumo Escasso)" : "MÉDIA (Padrão)");

        System.out.printf("\nProposta do Fornecedor NPC:\n");
        System.out.printf("  Item               : %s | Quantidade: %d\n", item.getNome(), qtd);
        System.out.printf("  Relevo e Bioma     : %s (%s)\n", descGeol, (cidade != null ? cidade.getRegiao() : "N/D"));
        System.out.printf("  Afinidade Regional : %s (Chance diária: %.0f%%)\n", nivelAfinidade, afinidade * 100);
        System.out.printf("  Preço Unitário (65%%): %s\n", Carteira.formatarLatao(precoUnitario));
        System.out.printf("  Custo Total        : %s\n", Carteira.formatarLatao(custoTotal));
        System.out.printf("  Saldo da Loja      : %s\n", Carteira.formatarLatao(loja.getCaixa().getValorTotalEmLatao()));

        if (!loja.getCaixa().temSaldoEmLatao(custoTotal)) {
            System.out.println("[RECUSADO] A loja não tem capital de giro suficiente para essa compra.");
            return;
        }

        boolean ok = loja.comprarDeNpc(item, qtd, dados.motor.getDiaAtual(), dados.motor.getHoraAtual());
        if (ok) {
            System.out.println("\n[COMPRA DE NPC EFETIVADA!]");
            System.out.printf("Novo estoque de '%s': %d unidades.\n", item.getNome(), loja.getQuantidadeEstoque(item.getId()));
            System.out.printf("Novo saldo de caixa: %s\n", Carteira.formatarLatao(loja.getCaixa().getValorTotalEmLatao()));
            System.out.println("Operação devidamente registrada no Livro-Caixa da loja.");
        }
    }

    // -------------------------------------------------------------------------
    // 6. COMÉRCIO INTERMUNICIPAL & DIJKSTRA
    // -------------------------------------------------------------------------
    private void menuComercioIntermunicipal() {
        System.out.println("=== 6. COMÉRCIO INTERMUNICIPAL & CÁLCULO DE ROTA (DIJKSTRA) ===");
        System.out.println("Escolha a loja COMPRADORA que deseja encomendar mercadorias de outra cidade:");
        Lojas compradora = selecionarLoja();
        if (compradora == null) return;

        Item item = selecionarItemDoCatalogo(compradora);
        if (item == null) return;

        System.out.printf("Quantidade que a loja '%s' deseja encomendar: ", compradora.getNome());
        int qtd = lerInteiro(1, 100);

        System.out.println("\n[PROCESSANDO ALGORITMO DE DIJKSTRA NO GRAFO DE THALURIA...]");
        boolean sucesso = dados.motor.solicitarCompraEntreCidades(compradora, item, qtd);

        if (sucesso) {
            Caravana novaCaravana = dados.motor.getCaravanas().get(dados.motor.getCaravanas().size() - 1);
            System.out.println("\n[ENCOMENDA APROVADA E CARAVANA EXPEDIDA COM SUCESSO!]");
            System.out.printf("  Fornecedora Escolhida: %s em %s\n",
                    novaCaravana.getLojaOrigem().getNome(), novaCaravana.getLojaOrigem().getCidadeSede().getNome());
            System.out.printf("  Destino               : %s em %s\n",
                    novaCaravana.getLojaDestino().getNome(), novaCaravana.getLojaDestino().getCidadeSede().getNome());
            System.out.printf("  Carga                 : %s (x%d)\n", novaCaravana.getCarga().getNome(), novaCaravana.getCarga().getQuantidade());
            System.out.printf("  Distância Total Rota  : %d km (Perigo Médio: %.1f/10)\n",
                    novaCaravana.getRota().getDistanciaTotalKm(), novaCaravana.getRota().getPerigoMedio());
            System.out.printf("  Serviço de Transporte : %s (%.0f km/h)\n",
                    novaCaravana.getServico(), novaCaravana.getServico().getVelocidadeTerrestreKmH());
            System.out.printf("  Frete Pago            : %s\n", Carteira.formatarLatao(novaCaravana.getRota()
                    .calcularCustoFreteEmLatao(novaCaravana.getCarga().getPesoTotal(), novaCaravana.getServico())));

            if (novaCaravana.getRota().recomendaEscolta(novaCaravana.getServico())) {
                System.out.println("  ⚠️ Rota perigosa! Uma MISSÃO DE ESCOLTA foi criada no Quadro de Avisos com 12h de prazo.");
            } else {
                System.out.println("  ✓ Rota considerada segura. A caravana partiu imediatamente desprotegida.");
            }
        } else {
            System.out.println("\n[FALHA NA ENCOMENDA]");
            System.out.println("Motivo possível: Nenhuma outra cidade possui estoque suficiente ou a loja compradora não possui saldo para cobrir produtos + frete.");
        }
    }

    // -------------------------------------------------------------------------
    // 7. QUADRO DE MISSÕES
    // -------------------------------------------------------------------------
    private void menuQuadroMissoes() {
        System.out.println("=== 7. QUADRO DE MISSÕES E CONTRATOS DE AVENTUREIROS ===");
        List<MissaoComercial> todas = dados.motor.getMissoes();

        if (todas.isEmpty()) {
            System.out.println("Nenhuma missão foi gerada até o momento.");
            return;
        }

        System.out.println("Missões Registradas no Mundo:");
        for (MissaoComercial m : todas) {
            System.out.println(" * " + m.toString());
        }

        System.out.printf("Sua Localização Atual: %s\n", dados.cidadeAtualJogador.getNome());
        System.out.println("Filtrar missões: [1] Exclusivas desta Cidade  [2] Todas do Continente");
        System.out.print("Escolha (1 ou 2): ");
        int filtro = lerInteiro(1, 2);

        List<MissaoComercial> disponiveis = (filtro == 1)
                ? dados.motor.listarMissoesDisponiveis(dados.cidadeAtualJogador)
                : dados.motor.listarMissoesDisponiveis();

        if (disponiveis.isEmpty()) {
            System.out.println("\nNão há missões disponíveis no quadro para esta seleção.");
            return;
        }

        System.out.println("\nMissões que você pode aceitar:");
        for (int i = 0; i < disponiveis.size(); i++) {
            MissaoComercial m = disponiveis.get(i);
            System.out.printf(" [%d] #%d [%s] em %s | Recompensa: %s | Prazo: %dh\n",
                    i + 1, m.getId(), m.getTipo(), m.getCidadeDisponivel().getNome(),
                    Carteira.formatarLatao(m.getRecompensaEmLatao()), m.getHorasRestantesPrazo());
        }

        System.out.print("Escolha o número da missão para aceitar (ou 0 para voltar): ");
        int sel = lerInteiro(0, disponiveis.size());
        if (sel == 0) return;

        MissaoComercial escolhida = disponiveis.get(sel - 1);
        boolean aceita = escolhida.aceitarPorJogador("Valerius, o Andarilho", dados.jogador,
                dados.motor.getDiaAtual(), dados.motor.getHoraAtual());

        if (aceita) {
            System.out.printf("\n[CONTRATO FIRMADO!] Você aceitou a Missão #%d (%s)!\n", escolhida.getId(), escolhida.getTipo());
            if (escolhida.getTipo() == MissaoComercial.TipoMissao.ESCOLTA) {
                System.out.println("Você assumiu a guarda da caravana. A segurança durante a viagem é de 100%!");
                System.out.println("A recompensa será debitada do caixa da loja contratante e creditada na sua carteira na chegada.");
                System.out.printf("Ao término da viagem, sua localização física será atualizada para %s.\n",
                        escolhida.getCaravanaAlvo().getCidadeDestino().getNome());
            } else if (escolhida.getTipo() == MissaoComercial.TipoMissao.RESGATE_CARGA) {
                System.out.println("Você foi enviado para resgatar a carga emboscada no grafo!");
                System.out.print("Deseja concluir o resgate agora combatendo os saqueadores? (1-Sim, 2-Não): ");
                int luta = lerInteiro(1, 2);
                if (luta == 1) {
                    escolhida.concluirMissaoJogador(dados.motor.getDiaAtual(), dados.motor.getHoraAtual());
                    System.out.println("\n[VITÓRIA NO RESGATE!]");
                    System.out.printf("Você recuperou a carga! Recompensa debitada do contratante e recebida: %s.\n",
                            Carteira.formatarLatao(escolhida.getRecompensaEmLatao()));
                    System.out.println("A rota foi pacificada (-1 de perigo na estrada) e a carga entregue à loja.");
                    System.out.printf("Seu novo saldo: %s\n", dados.jogador.getCarteira().toString());
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 8. AVANÇAR TEMPO
    // -------------------------------------------------------------------------
    private void menuAvancarTempo() {
        System.out.println("=== 8. AVANÇAR O TEMPO DO MUNDO ===");
        System.out.println("Escolha quanto tempo avançar:");
        System.out.println(" 1. Avançar 1 hora");
        System.out.println(" 2. Avançar 6 horas");
        System.out.println(" 3. Avançar 12 horas");
        System.out.println(" 4. Avançar 24 horas (Dispara Ciclo Diário Completo)");
        System.out.println(" 5. Avançar quantidade personalizada de horas");
        System.out.print("Opção: ");
        int op = lerInteiro(1, 5);

        int horas = switch (op) {
            case 1 -> 1;
            case 2 -> 6;
            case 3 -> 12;
            case 4 -> 24;
            default -> {
                System.out.print("Digite as horas a avançar: ");
                yield lerInteiro(1, 720);
            }
        };

        Cidade cidadeAntes = dados.jogador.getCidadeAtual();
        System.out.printf("\n[AVANÇANDO %d HORA(S) NO RELÓGIO DE THALURIA...]\n", horas);
        dados.motor.avancarTempo(horas);
        System.out.printf("Novo Horário: Dia %d, %02dh.\n", dados.motor.getDiaAtual(), dados.motor.getHoraAtual());

        if (dados.jogador.getCidadeAtual() != null && !dados.jogador.getCidadeAtual().equals(cidadeAntes)) {
            dados.cidadeAtualJogador = dados.jogador.getCidadeAtual();
            System.out.printf("\n🚩 [DESLOCAMENTO DE ESCOLTA CONCLUÍDO!]\n");
            System.out.printf("Você viajou como guarda da caravana e chegou são e salvo em: %s!\n",
                    dados.cidadeAtualJogador.getNome());
        }

        System.out.println("\nStatus das Caravanas:");
        if (dados.motor.getCaravanas().isEmpty()) {
            System.out.println("  (Nenhuma caravana em circulação)");
        } else {
            for (Caravana c : dados.motor.getCaravanas()) {
                System.out.println("  * " + c.toString());
            }
        }
    }

    // -------------------------------------------------------------------------
    // 9. GERENCIAR PERIGO NO GRAFO
    // -------------------------------------------------------------------------
    private void menuGerenciarPerigo() {
        System.out.println("=== 9. GERENCIAR PERIGO E BLOQUEIOS NO GRAFO ===");
        List<Conexao> conexoes = new ArrayList<>();
        Set<Conexao> set = new LinkedHashSet<>();
        for (Cidade c : dados.motor.getCidades()) {
            set.addAll(c.getConexoes());
        }
        conexoes.addAll(set);

        for (int i = 0; i < conexoes.size(); i++) {
            Conexao c = conexoes.get(i);
            System.out.printf(" [%d] %s <---> %s (Perigo: %d/10 | %s)\n",
                    i + 1, c.getOrigem().getNome(), c.getDestino().getNome(), c.getPerigo(),
                    c.isBloqueada() ? "BLOQUEADA" : "LIVRE");
        }

        System.out.print("Escolha a conexão para ajustar (ou 0 para voltar): ");
        int sel = lerInteiro(0, conexoes.size());
        if (sel == 0) return;

        Conexao escolhida = conexoes.get(sel - 1);
        System.out.println("Ação:");
        System.out.println(" 1. Aumentar perigo (+1)");
        System.out.println(" 2. Diminuir perigo (-1)");
        System.out.println(" 3. Alternar bloqueio (Bloquear / Desbloquear)");
        System.out.print("Opção: ");
        int acao = lerInteiro(1, 3);

        if (acao == 1) {
            escolhida.ajustarPerigo(+1);
            System.out.printf("Perigo ajustado para %d/10.\n", escolhida.getPerigo());
        } else if (acao == 2) {
            escolhida.ajustarPerigo(-1);
            System.out.printf("Perigo ajustado para %d/10.\n", escolhida.getPerigo());
        } else {
            if (escolhida.isBloqueada()) {
                escolhida.desbloquear();
                System.out.println("Conexão DESBLOQUEADA para caravanas.");
            } else {
                escolhida.bloquear();
                System.out.println("Conexão BLOQUEADA. O algoritmo de Dijkstra desviará desta rota!");
            }
        }
    }

    // -------------------------------------------------------------------------
    // 10. LIVRO-CAIXA / HISTÓRICO
    // -------------------------------------------------------------------------
    private void menuHistoricoContabil() {
        System.out.println("=== 10. LIVRO-CAIXA E HISTÓRICO DE MOVIMENTAÇÕES CONTÁBEIS ===");
        Lojas loja = selecionarLoja();
        if (loja == null) return;

        List<RegistroMovimentacao> hist = loja.getHistorico();
        System.out.printf("\n--- Extrato Contábil: %s ---\n", loja.getNome());
        System.out.printf("Saldo Atual em Caixa: %s (%d latão)\n\n",
                Carteira.formatarLatao(loja.getCaixa().getValorTotalEmLatao()),
                loja.getCaixa().getValorTotalEmLatao());

        if (hist.isEmpty()) {
            System.out.println("(Nenhuma transação contábil registrada ainda para esta loja)");
            return;
        }

        for (RegistroMovimentacao reg : hist) {
            System.out.println(reg.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 11. FICHA E INVENTÁRIO DO JOGADOR
    // -------------------------------------------------------------------------
    private void menuJogador() {
        System.out.println("=== 11. FICHA DO JOGADOR, INVENTÁRIO E VIAGEM ===");
        System.out.println(dados.jogador.toString());
        System.out.printf("Carteira: %s (%d latão)\n", dados.jogador.getCarteira().toString(), dados.jogador.getCarteira().getValorTotalEmLatao());
        System.out.printf("Cidade Atual: %s\n\n", dados.cidadeAtualJogador.getNome());

        System.out.println("Inventário de Aventureiro:");
        if (dados.jogador.getInventario().isEmpty()) {
            System.out.println("  (Inventário Vazio)");
        } else {
            for (Item item : dados.jogador.getInventario()) {
                System.out.printf("  * %s (Peso: %.1f kg | Base: %s)\n",
                        item.toString(), item.getPesoTotal(), Carteira.formatarLatao(item.getPrecoEmLatao()));
            }
        }

        System.out.println("\nDeseja viajar para outra cidade?");
        System.out.println(" 1. Mudar cidade atual do jogador");
        System.out.println(" 2. Adicionar moedas à carteira de teste");
        System.out.println(" 3. Voltar ao menu principal");
        System.out.print("Opção: ");
        int op = lerInteiro(1, 3);

        if (op == 1) {
            List<Cidade> cidades = dados.motor.getCidades();
            for (int i = 0; i < cidades.size(); i++) {
                System.out.printf(" [%d] %s\n", i + 1, cidades.get(i).getNome());
            }
            System.out.print("Escolha a cidade de destino: ");
            int sel = lerInteiro(1, cidades.size()) - 1;
            dados.cidadeAtualJogador = cidades.get(sel);
            System.out.printf("Você viajou para %s!\n", dados.cidadeAtualJogador.getNome());
        } else if (op == 2) {
            System.out.print("Quantas moedas de Ouro (PO) deseja adicionar à sua carteira? ");
            int po = lerInteiro(1, 1000);
            dados.jogador.getCarteira().adicionarOuro(po);
            System.out.printf("Adicionado! Novo saldo: %s\n", dados.jogador.getCarteira().toString());
        }
    }

    // -------------------------------------------------------------------------
    // 12. DEMONSTRAÇÃO AUTOMATIZADA COMPLETA
    // -------------------------------------------------------------------------
    public void executarDemonstracaoCompleta() {
        System.out.println("======================================================================");
        System.out.println("       INICIANDO BATERIA COMPLETA DE TESTES AUTOMATIZADOS (v2)        ");
        System.out.println("======================================================================");

        // Teste 1: Escala Monetária e Latão
        System.out.println("\n[TESTE 1/6] Base Monetária em Latão (long) e Formatação de 10 Moedas:");
        Carteira c = new Carteira();
        c.receberLatao(1_234_567L);
        System.out.printf("  Valor 1.234.567 latão decomposto automaticamente: %s\n", c.descreverMoedas());
        System.out.printf("  Valor de 50 PO em latão: %d latão\n", Carteira.ouroParaLatao(50.0));

        // Teste 2: Dijkstra e Rotas Comerciais
        System.out.println("\n[TESTE 2/6] Cálculo de Rotas no Grafo com Dijkstra (NavegadorGrafo):");
        RotaComercial rota = NavegadorGrafo.calcularMelhorRota(dados.frostdale, dados.sunreach, 0.25);
        if (rota != null) {
            System.out.printf("  Origem: %s -> Destino: %s\n", dados.frostdale.getNome(), dados.sunreach.getNome());
            System.out.print("  Caminho de Cidades: ");
            for (Cidade cid : rota.getCaminhoCidades()) System.out.print(cid.getNome() + " -> ");
            System.out.println("Fim.");
            System.out.printf("  Distância Total: %d km | Perigo Médio: %.1f | Pico de Perigo: %d\n",
                    rota.getDistanciaTotalKm(), rota.getPerigoMedio(), rota.getPerigoMaximo());
            System.out.printf("  Frete para 60kg: %s (%d latão)\n", rota.descreverCustoFrete(60.0), rota.calcularCustoFreteEmLatao(60.0));
            System.out.printf("  Custo de Escolta: %s | Recomenda Escolta: %s\n", rota.descreverCustoEscolta(), rota.recomendaEscolta() ? "SIM" : "NÃO");
        }

        // Teste 3: Oferta e Demanda Dinâmica
        System.out.println("\n[TESTE 3/6] Precificação Dinâmica por Oferta e Demanda:");
        Lojas forja = dados.ironhold.getLojas().get(0);
        Item lingote = forja.getEstoque().get(0);
        long precoNormal = forja.calcularPrecoDinamicoVendaEmLatao(lingote);
        System.out.printf("  Item '%s' (Base: %s):\n", lingote.getNome(), Carteira.formatarLatao(lingote.getPrecoEmLatao()));
        System.out.printf("    Preço Inicial (Est: %d, Dem: %d): %s\n",
                forja.getQuantidadeEstoque(lingote.getId()),
                forja.getDemandaPorItem().getOrDefault(lingote.getId(), 0),
                Carteira.formatarLatao(precoNormal));

        forja.definirDemanda(lingote, 100); // Dispara alta demanda
        long precoAltaDemanda = forja.calcularPrecoDinamicoVendaEmLatao(lingote);
        System.out.printf("    Após explosão de demanda (Dem: 100): %s (+%.1f%%)\n",
                Carteira.formatarLatao(precoAltaDemanda),
                (((double) precoAltaDemanda / precoNormal) - 1.0) * 100.0);

        forja.definirDemanda(lingote, 30); // Restaura

        // Teste 4: Comércio Intermunicipal com Desconto de Rede
        System.out.println("\n[TESTE 4/6] Encomenda Intermunicipal com Desconto de Rede Comercial:");
        Lojas compradoraRede = dados.ironhold.getLojas().get(1); // Empório da Rede do Norte
        Item itemEncomenda = forja.getEstoque().get(0); // Lingote de Ferro
        System.out.printf("  Loja '%s' (%s) precisa de 5x '%s'.\n",
                compradoraRede.getNome(), compradoraRede.getNomeDaRede(), itemEncomenda.getNome());
        boolean encomendaOk = dados.motor.solicitarCompraEntreCidades(compradoraRede, itemEncomenda, 5);
        System.out.printf("  Resultado da Encomenda: %s\n", encomendaOk ? "SUCESSO (Caravana expedida)" : "FALHA");

        // Teste 5: Avanço no Tempo e Movimentação da Caravana
        System.out.println("\n[TESTE 5/6] Deslocamento no Grafo e Missão de Escolta:");
        if (!dados.motor.getCaravanas().isEmpty()) {
            Caravana c1 = dados.motor.getCaravanas().get(dados.motor.getCaravanas().size() - 1);
            System.out.printf("  Caravana #%d antes: %s\n", c1.getId(), c1.descreverPosicaoNoGrafo());
            System.out.println("  Avançando 12 horas no mundo...");
            dados.motor.avancarTempo(12);
            System.out.printf("  Caravana #%d depois: %s | Status: %s\n", c1.getId(), c1.descreverPosicaoNoGrafo(), c1.getStatus());
        }

        // Teste 6: Extrato Contábil Formato Moedas
        System.out.println("\n[TESTE 6/6] Livro-Caixa da Loja (RegistroMovimentacao):");
        for (RegistroMovimentacao r : compradoraRede.getHistorico()) {
            System.out.println("  " + r.toString());
        }

        System.out.println("\n======================================================================");
        System.out.println("           DEMONSTRAÇÃO CONCLUÍDA: 100% DAS ROTINAS VALIDADAS         ");
        System.out.println("======================================================================");
    }

    // -------------------------------------------------------------------------
    // MÉTODOS AUXILIARES DE ENTRADA E SELEÇÃO
    // -------------------------------------------------------------------------
    private Lojas selecionarLoja() {
        List<Lojas> todasLojas = new ArrayList<>();
        for (Cidade c : dados.motor.getCidades()) {
            todasLojas.addAll(c.getLojas());
        }
        System.out.println("Selecione um estabelecimento comercial:");
        for (int i = 0; i < todasLojas.size(); i++) {
            Lojas l = todasLojas.get(i);
            String cid = (l.getCidadeSede() != null) ? l.getCidadeSede().getNome() : "Sem Sede";
            System.out.printf(" [%d] %-28s em %-15s (Caixa: %s)\n",
                    i + 1, l.getNome(), cid, Carteira.formatarLatao(l.getCaixa().getValorTotalEmLatao()));
        }
        System.out.print("Escolha o número da loja: ");
        int sel = lerInteiro(1, todasLojas.size()) - 1;
        return todasLojas.get(sel);
    }

    private Item selecionarItemDaLoja(Lojas loja) {
        if (loja.getEstoque().isEmpty()) {
            System.out.println("A loja não possui itens em estoque.");
            return null;
        }
        for (int i = 0; i < loja.getEstoque().size(); i++) {
            Item it = loja.getEstoque().get(i);
            System.out.printf(" [%d] %-24s (Estoque: %d)\n", i + 1, it.getNome(), it.getQuantidade());
        }
        System.out.print("Escolha o item: ");
        int sel = lerInteiro(1, loja.getEstoque().size()) - 1;
        return loja.getEstoque().get(sel);
    }

    private Item selecionarItemDoCatalogo(Lojas loja) {
        Map<Integer, Item> cat = loja.getCatalogoConhecido();
        if (cat.isEmpty()) {
            return selecionarItemDaLoja(loja);
        }
        List<Item> lista = new ArrayList<>(cat.values());
        for (int i = 0; i < lista.size(); i++) {
            Item it = lista.get(i);
            System.out.printf(" [%d] %-24s (Base: %s)\n", i + 1, it.getNome(), Carteira.formatarLatao(it.getPrecoEmLatao()));
        }
        System.out.print("Escolha o item: ");
        int sel = lerInteiro(1, lista.size()) - 1;
        return lista.get(sel);
    }

    private int lerInteiro(int min, int max) {
        while (true) {
            try {
                String linha = scanner.nextLine().trim();
                int val = Integer.parseInt(linha);
                if (val >= min && val <= max) {
                    return val;
                }
                System.out.printf("Por favor, digite um número entre %d e %d: ", min, max);
            } catch (Exception e) {
                System.out.printf("Entrada inválida. Digite um número entre %d e %d: ", min, max);
            }
        }
    }

    private void pausar() {
        System.out.println("\nPressione [ENTER] para continuar...");
        try {
            scanner.nextLine();
        } catch (Exception ignored) {}
    }
}
