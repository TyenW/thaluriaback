package teste;

import economia.*;
import geografia.*;
import persistencia.*;
import personagem.Ficha;
import servidor.ServidorThaluriaAPI;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

/**
 * Suíte do comércio vivo: clientela, reposição, IA dos mercadores, produção por receitas,
 * encomendas e caravanas, sementes das lojas-base, persistência, estabilidade do mundo
 * e as rotas da aba de Comércio do Mestre.
 *
 * Usa um banco próprio (thaluria_teste_comercio.db), apagado no início e no fim:
 * o thaluria.db da campanha nunca é tocado.
 */
public class TesteComercioVivo {

    private static final String BANCO_TESTE = "thaluria_teste_comercio.db";
    private static final int PORTA = 8993;

    private static int total = 0;
    private static int passaram = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("======================================================================");
        System.out.println("   SUITE DO COMERCIO VIVO (THALURIA)");
        System.out.println("======================================================================\n");

        // 1. Regras do motor, em mundos montados à mão (sem banco)
        testarClientelaNpc();
        testarReposicaoEReserva();
        testarIaDoMercador();
        testarOrdensDoMestre();
        testarEncomendasECaravanas();
        testarServicosDeTransporte();

        // 2. Banco, sementes, persistência, estabilidade e API (banco de teste)
        apagarBancoTeste();
        BancoDados.setUrlBanco("jdbc:sqlite:" + BANCO_TESTE);
        try {
            BancoDados.inicializarEsquema();
            testarSementesDasLojasBase();
            testarPersistenciaCompleta();
            testarEstabilidadeDoMundo();
            testarApiDoMestre();
        } finally {
            BancoDados.setUrlBanco("jdbc:sqlite:thaluria.db");
            apagarBancoTeste();
        }

        System.out.println("\n======================================================================");
        System.out.println(passaram == total
                ? String.format("   RESULTADO FINAL: %d/%d TESTES DO COMERCIO PASSARAM.", passaram, total)
                : String.format("   RESULTADO: %d/%d passaram (%d falharam).", passaram, total, total - passaram));
        System.out.println("======================================================================");
        if (passaram != total) System.exit(1);
    }

    private static void afirme(boolean condicao, String mensagem) {
        total++;
        if (condicao) {
            passaram++;
            System.out.println("  [OK] " + mensagem);
        } else {
            System.err.println("  [FALHOU] " + mensagem);
            throw new AssertionError("Falha: " + mensagem);
        }
    }

    // ------------------------------------------------------------------
    // Construtores de mundo para os testes de regra
    // ------------------------------------------------------------------

    private static int proximoIdCidade = 900;

    private static Cidade cidade(String nome, Cidade.TipoAssentamento porte) {
        return new Cidade(proximoIdCidade++, nome, porte, Regiao.TEMPERADO, Geologia.PLANICIE, Aquifero.RIO, false, false, null);
    }

    private static Item item(int id, String nome, long precoLatao, Item.TipoItem tipo) {
        return new Item(id, nome, "Item de teste", precoLatao / 10_000.0, 1.0, tipo, Item.RaridadeItem.COMUM, 1);
    }

    private static Lojas loja(String nome, Cidade cidade, Lojas.TipoEstabelecimento tipo, long caixaLatao) {
        Lojas l = new Lojas(nome, "Mercador de Teste", cidade, false, null, tipo);
        l.setCaixa(Carteira.deLatao(caixaLatao));
        return l;
    }

    private static long somaRegistros(Lojas l, RegistroMovimentacao.TipoOperacao tipo) {
        return l.getHistorico().stream().filter(r -> r.getTipo() == tipo).mapToLong(RegistroMovimentacao::getValorEmLatao).sum();
    }

    private static long contaRegistros(Lojas l, RegistroMovimentacao.TipoOperacao tipo) {
        return l.getHistorico().stream().filter(r -> r.getTipo() == tipo).count();
    }

    // ------------------------------------------------------------------
    // 1. Regras do motor
    // ------------------------------------------------------------------

    private static void testarClientelaNpc() {
        System.out.println("[1/10] Clientela NPC (vendas de balcão)...");
        Item pao = item(1, "Pão de Teste", 10_000, Item.TipoItem.CONSUMIVEL);

        Lojas capital = loja("Padaria da Capital", cidade("Capital", Cidade.TipoAssentamento.REINO), Lojas.TipoEstabelecimento.TAVERNA, 50_000);
        Lojas vila = loja("Padaria da Vila", cidade("Vila", Cidade.TipoAssentamento.VILA), Lojas.TipoEstabelecimento.TAVERNA, 50_000);
        for (Lojas l : List.of(capital, vila)) {
            l.adicionarItem(Lojas.clonarItem(pao, 40));
            l.definirDemanda(pao, 10);
        }
        long caixaAntes = capital.getCaixa().getValorTotalEmLatao();
        long recebido = capital.atenderClientesNpc(new Random(7), 1, 8);
        vila.atenderClientesNpc(new Random(7), 1, 8);
        int vendidosCapital = 40 - capital.getQuantidadeEstoque(1);
        int vendidosVila = 40 - vila.getQuantidadeEstoque(1);

        afirme(vendidosCapital > 0, "Clientes compram no balcão (" + vendidosCapital + " pães na capital).");
        afirme(capital.getCaixa().getValorTotalEmLatao() == caixaAntes + recebido, "O caixa sobe exatamente o valor recebido.");
        afirme(somaRegistros(capital, RegistroMovimentacao.TipoOperacao.VENDA_BALCAO) == recebido, "A venda fica no livro-caixa com o valor certo.");
        afirme(vendidosCapital > vendidosVila, "Capital vende mais que vila com a mesma procura (" + vendidosCapital + " > " + vendidosVila + ").");

        Item minerio = item(2, "Minério de Teste", 10_000, Item.TipoItem.MATERIAL);
        Lojas forja = loja("Forja", cidade("Cidade", Cidade.TipoAssentamento.CIDADE), Lojas.TipoEstabelecimento.FORJA, 50_000);
        Lojas taverna = loja("Taverna", cidade("Cidade B", Cidade.TipoAssentamento.CIDADE), Lojas.TipoEstabelecimento.TAVERNA, 50_000);
        forja.adicionarItem(Lojas.clonarItem(minerio, 40));
        forja.definirDemanda(minerio, 10);
        taverna.adicionarItem(Lojas.clonarItem(pao, 40));
        taverna.definirDemanda(pao, 10);
        forja.atenderClientesNpc(new Random(3), 1, 8);
        taverna.atenderClientesNpc(new Random(3), 1, 8);
        afirme(40 - forja.getQuantidadeEstoque(2) < 40 - taverna.getQuantidadeEstoque(1), "Materiais vendem menos no balcão que produtos prontos.");

        // Preço alto espanta clientes: escassez (pouco estoque, muita procura) encarece e reduz a procura relativa
        Lojas cara = loja("Loja Cara", cidade("Cidade C", Cidade.TipoAssentamento.CIDADE), Lojas.TipoEstabelecimento.TAVERNA, 50_000);
        cara.adicionarItem(Lojas.clonarItem(pao, 3));
        cara.definirDemanda(pao, 10);
        afirme(cara.calcularPrecoDinamicoVendaEmLatao(pao) > pao.getPrecoEmLatao(), "Pouco estoque e muita procura encarecem o item.");
        afirme(cara.atenderClientesNpc(new Random(7), 1, 8) >= 0 && cara.getQuantidadeEstoque(1) >= 0, "A loja nunca vende mais do que tem.");
    }

    private static void testarReposicaoEReserva() {
        System.out.println("\n[2/10] Reposição por fornecedores e reserva de caixa...");
        MotorEconomico motor = new MotorEconomico(new Random(11));
        Cidade c = cidade("Entreposto", Cidade.TipoAssentamento.CIDADE);
        Item tocha = item(10, "Tocha de Teste", 1_000, Item.TipoItem.CONSUMIVEL);

        Lojas cheia = loja("Empório Cheio", c, Lojas.TipoEstabelecimento.LOJA_GERAL, 500_000);
        cheia.adicionarItem(Lojas.clonarItem(tocha, 200));
        cheia.definirDemanda(tocha, 5);

        Item corda = item(11, "Corda de Teste", 1_000, Item.TipoItem.OUTRO);
        Lojas pobre = loja("Empório Pobre", c, Lojas.TipoEstabelecimento.LOJA_GERAL, Lojas.RESERVA_CAIXA_LATAO);
        pobre.definirDemanda(corda, 8);

        motor.registrarCidade(c);
        motor.definirReceitasECatalogo(List.of(), List.of(tocha, corda));
        for (int d = 0; d < 10; d++) motor.executarCicloDiario();

        afirme(contaRegistros(cheia, RegistroMovimentacao.TipoOperacao.ENTRADA_NPC) == 0, "Loja acima do estoque-alvo não compra de fornecedores.");
        afirme(contaRegistros(pobre, RegistroMovimentacao.TipoOperacao.ENTRADA_NPC) == 0, "Loja só com a reserva não gasta com reposição.");
        afirme(pobre.getCaixa().getValorTotalEmLatao() == Lojas.RESERVA_CAIXA_LATAO, "A reserva de caixa fica intacta.");

        Lojas vazia = loja("Empório Vazio", c, Lojas.TipoEstabelecimento.LOJA_GERAL, 500_000);
        vazia.definirDemanda(tocha, 6);
        for (int d = 0; d < 15; d++) motor.executarCicloDiario();
        afirme(contaRegistros(vazia, RegistroMovimentacao.TipoOperacao.ENTRADA_NPC) > 0, "Loja vazia e com caixa se abastece com fornecedores locais.");
        afirme(vazia.getQuantidadeEstoque(10) <= vazia.estoqueAlvo(10) + 10, "Reposição para perto do estoque-alvo, sem inchar o armazém.");
    }

    private static void testarIaDoMercador() {
        System.out.println("\n[3/10] IA do mercador (receitas por lucro diário)...");
        Cidade c = cidade("Forjaria", Cidade.TipoAssentamento.CIDADE);
        Item lingote = item(20, "Lingote T", 3_000, Item.TipoItem.MATERIAL);
        Item gema = item(21, "Gema T", 5_000, Item.TipoItem.MATERIAL);
        Item adaga = item(22, "Adaga T", 20_000, Item.TipoItem.ARMA_BRANCA);
        Item coroa = item(23, "Coroa T", 900_000, Item.TipoItem.ACESSORIO);
        Map<Integer, Item> catalogo = Map.of(20, lingote, 21, gema, 22, adaga, 23, coroa);

        ReceitaLoja fazerAdaga = new ReceitaLoja(1, "Forjar adaga", 22, 1, 24, Lojas.TipoEstabelecimento.FORJA, List.of(new ReceitaLoja.Insumo(20, 2)));
        ReceitaLoja fazerCoroa = new ReceitaLoja(2, "Forjar coroa", 23, 1, 48, Lojas.TipoEstabelecimento.FORJA, List.of(new ReceitaLoja.Insumo(21, 3)));
        ReceitaLoja deAlfaiate = new ReceitaLoja(3, "Costurar", 22, 1, 24, Lojas.TipoEstabelecimento.ALFAIATARIA, List.of(new ReceitaLoja.Insumo(20, 1)));
        List<ReceitaLoja> receitas = List.of(fazerAdaga, fazerCoroa, deAlfaiate);

        Lojas forja = loja("Forja da IA", c, Lojas.TipoEstabelecimento.FORJA, 500_000);
        forja.adicionarItem(Lojas.clonarItem(lingote, 10));
        forja.definirDemanda(adaga, 4);

        afirme(!deAlfaiate.conhecidaPor(forja) && fazerAdaga.conhecidaPor(forja), "Cada especialidade só conhece as próprias receitas.");
        IALojistaDeterministica.AvaliacaoReceita a = IALojistaDeterministica.avaliar(forja, fazerAdaga, catalogo);
        afirme(a.lucroEsperado() > 0 && a.insumosFaltantes().isEmpty(), "A IA calcula lucro positivo para a adaga (" + a.lucroEsperado() + " latão).");

        OrdemReceita ordem = IALojistaDeterministica.decidirProducaoDoDia(forja, receitas, catalogo, 1, 8);
        afirme(ordem != null && ordem.getReceitaId() == 1, "Sem gemas, a IA produz a adaga (a coroa daria mais, mas está travada).");
        afirme(forja.getQuantidadeEstoque(20) == 8, "Os insumos saem do estoque ao abrir a ordem.");
        afirme(forja.getDemandaPorItem().getOrDefault(21, 0) >= 3, "A loja passa a procurar gemas para a receita mais lucrativa.");
        afirme(IALojistaDeterministica.decidirProducaoDoDia(forja, receitas, catalogo, 1, 8) == null, "Com a oficina ocupada, a IA não abre outra ordem.");

        forja.concluirOrdensProntas(1, 20, Map.of(1, fazerAdaga, 2, fazerCoroa), catalogo);
        afirme(forja.getQuantidadeEstoque(22) == 0, "O lote não fica pronto antes do prazo.");
        forja.concluirOrdensProntas(2, 0, Map.of(1, fazerAdaga, 2, fazerCoroa), catalogo);
        afirme(forja.getQuantidadeEstoque(22) == 1 && forja.getOrdensReceita().isEmpty(), "No dia certo, a adaga entra no estoque e a oficina libera.");

        forja.adicionarItem(Lojas.clonarItem(gema, 3));
        OrdemReceita segunda = IALojistaDeterministica.decidirProducaoDoDia(forja, receitas, catalogo, 2, 8);
        afirme(segunda != null && segunda.getReceitaId() == 2, "Com as gemas em estoque, a IA escolhe a coroa (maior lucro por dia).");

        Lojas abarrotada = loja("Forja Abarrotada", c, Lojas.TipoEstabelecimento.FORJA, 500_000);
        abarrotada.adicionarItem(Lojas.clonarItem(lingote, 10));
        abarrotada.adicionarItem(Lojas.clonarItem(adaga, 50));
        abarrotada.definirDemanda(adaga, 2);
        afirme(IALojistaDeterministica.decidirProducaoDoDia(abarrotada, List.of(fazerAdaga), catalogo, 1, 8) == null, "A IA não produz o que já lota a prateleira.");
    }

    private static void testarOrdensDoMestre() {
        System.out.println("\n[4/10] Ordens do Mestre como mercador...");
        Cidade c = cidade("Oficina Real", Cidade.TipoAssentamento.CIDADE);
        Item linho = item(30, "Linho T", 2_000, Item.TipoItem.MATERIAL);
        Item capa = item(31, "Capa T", 15_000, Item.TipoItem.ARMADURA);
        Map<Integer, Item> catalogo = Map.of(30, linho, 31, capa);
        ReceitaLoja costurar = new ReceitaLoja(5, "Costurar capa", 31, 2, 48, Lojas.TipoEstabelecimento.ALFAIATARIA, List.of(new ReceitaLoja.Insumo(30, 3)));
        Lojas alfaiate = loja("Alfaiate", c, Lojas.TipoEstabelecimento.ALFAIATARIA, 100_000);

        afirme(alfaiate.iniciarReceita(costurar, capa, 1, 1, 8, OrdemReceita.Origem.MESTRE, false) == null, "Sem insumos e sem a ajuda do Mestre, a ordem é recusada.");
        afirme(alfaiate.insumosFaltantes(costurar, 2).get(30) == 6, "A loja informa exatamente o que falta (6 linhos para 2 lotes).");

        OrdemReceita ordem = alfaiate.iniciarReceita(costurar, capa, 2, 1, 8, OrdemReceita.Origem.MESTRE, true);
        afirme(ordem != null && alfaiate.getCaixa().getValorTotalEmLatao() == 100_000, "Com insumos por conta do Mestre, nada sai do caixa.");
        afirme(ordem.getDiaConclusao() == 3, "Receita de 48 h fica pronta em 2 dias.");
        alfaiate.concluirImediatamente(ordem, 1, 9, Map.of(5, costurar), catalogo);
        afirme(alfaiate.getQuantidadeEstoque(31) == 4, "\"Pronto agora\" entrega 2 lotes × 2 capas na hora.");
        afirme(contaRegistros(alfaiate, RegistroMovimentacao.TipoOperacao.PRODUCAO_CONCLUIDA) == 1, "A conclusão fica registrada no livro-caixa.");

        alfaiate.ajustarEstoque(linho, 10, 1, 10);
        alfaiate.definirCaixa(250_000, 1, 10);
        afirme(alfaiate.getQuantidadeEstoque(30) == 10 && alfaiate.getCaixa().getValorTotalEmLatao() == 250_000, "Ajustes do Mestre mudam estoque e caixa.");
        afirme(contaRegistros(alfaiate, RegistroMovimentacao.TipoOperacao.AJUSTE_MESTRE) == 2, "Os ajustes aparecem como ajuste do Mestre.");
    }

    private static void testarEncomendasECaravanas() {
        System.out.println("\n[5/10] Encomendas entre cidades e caravanas...");
        MotorEconomico motor = new MotorEconomico(new Random(5));
        Cidade mina = cidade("Mina", Cidade.TipoAssentamento.CIDADE);
        Cidade porto = cidade("Porto", Cidade.TipoAssentamento.CIDADE);
        Cidade longe = cidade("Fim do Mundo", Cidade.TipoAssentamento.VILA);
        new Conexao(mina, porto, 10, 1, Conexao.TipoDeRota.TERRESTRE);
        new Conexao(porto, longe, 400, 1, Conexao.TipoDeRota.TERRESTRE);

        Item espada = item(40, "Espada T", 60_000, Item.TipoItem.ARMA_BRANCA);
        Item pedra = item(41, "Pedra T", 300, Item.TipoItem.MATERIAL);
        Lojas fornecedora = loja("Fornecedora", mina, Lojas.TipoEstabelecimento.FORJA, 100_000);
        fornecedora.adicionarItem(Lojas.clonarItem(espada, 60));
        fornecedora.adicionarItem(Lojas.clonarItem(pedra, 60));
        Lojas compradora = loja("Compradora", porto, Lojas.TipoEstabelecimento.LOJA_GERAL, 2_000_000);
        compradora.definirDemanda(espada, 10);
        Lojas distante = loja("Distante", longe, Lojas.TipoEstabelecimento.LOJA_GERAL, 2_000_000);
        distante.definirDemanda(pedra, 10);
        for (Cidade c : List.of(mina, porto, longe)) motor.registrarCidade(c);
        motor.definirReceitasECatalogo(List.of(), List.of(espada, pedra));

        afirme(!motor.solicitarCompraEntreCidades(distante, pedra, 5), "Não encomenda pedra barata por 410 km: o frete custaria mais que a revenda.");
        afirme(motor.getCaravanas().isEmpty(), "Nenhuma caravana sai numa encomenda que não compensa.");

        long caixaAntes = compradora.getCaixa().getValorTotalEmLatao();
        afirme(motor.solicitarCompraEntreCidades(compradora, espada, 5), "Encomenda de espadas pela estrada curta é feita.");
        Caravana caravana = motor.getCaravanas().get(0);
        long compra = somaRegistros(compradora, RegistroMovimentacao.TipoOperacao.COMPRA_INTERMUNICIPAL);
        long frete = somaRegistros(compradora, RegistroMovimentacao.TipoOperacao.DESPESA_FRETE);
        afirme(compra > 0 && frete > 0, "Mercadoria e frete ficam em lançamentos separados (" + compra + " + " + frete + ").");
        afirme(caixaAntes - compradora.getCaixa().getValorTotalEmLatao() == compra + frete, "O caixa da compradora cai exatamente mercadoria + frete.");
        afirme(somaRegistros(fornecedora, RegistroMovimentacao.TipoOperacao.VENDA_INTERMUNICIPAL) == compra, "A fornecedora recebe o valor da mercadoria.");
        afirme(caravana.isAtiva() && motor.getCaravanasAtivas().size() == 1, "A caravana aparece como ativa (vai para o mapa).");
        afirme(motor.temCaravanaAtivaPara(compradora, 40), "Não se encomenda de novo o que já está a caminho.");

        double horas = caravana.horasRestantesEstimadas();
        double velocidade = caravana.getServico().getVelocidadeTerrestreKmH();
        afirme(Math.abs(horas - 10 / velocidade) < 0.01, "Previsão de chegada: 10 km a " + String.format("%.0f", velocidade)
                + " km/h (" + caravana.getServico() + ") ≈ " + String.format("%.1f", horas) + " h.");
        motor.avancarTempo(1);
        afirme(caravana.kmPercorridos() > 0 && caravana.kmPercorridos() < 10, "Após 1 h a caravana está no meio da estrada (" + String.format("%.1f", caravana.kmPercorridos()) + " km).");
        motor.avancarTempo(5);
        afirme(caravana.getStatus() == Caravana.StatusCaravana.ENTREGUE && compradora.getQuantidadeEstoque(40) == 5, "A carga chega ao destino e entra no estoque.");
        afirme(contaRegistros(compradora, RegistroMovimentacao.TipoOperacao.ENTREGA_CARAVANA) == 1, "A chegada fica registrada como entrega de caravana.");

        motor.setDiaAtual(motor.getDiaAtual() + 9);
        motor.executarCicloDiario();
        afirme(!motor.getCaravanas().contains(caravana), "Caravanas entregues há mais de uma semana saem da memória.");
    }

    /** Sorteio que nunca cai no ataque: a caravana só anda. */
    private static final Random SEM_ATAQUE = new Random() {
        @Override public double nextDouble() { return 0.999999; }
    };

    private static void testarServicosDeTransporte() {
        System.out.println("\n[6/10] Serviços de transporte (velocidade, risco e frete)...");
        Cidade a = cidade("Ponta A", Cidade.TipoAssentamento.CIDADE);
        Cidade b = cidade("Ponta B", Cidade.TipoAssentamento.CIDADE);
        Conexao estrada = new Conexao(a, b, 100, 6, Conexao.TipoDeRota.TERRESTRE);
        RotaComercial rota = new RotaComercial(List.of(a, b), List.of(estrada));
        Lojas la = loja("Loja A", a, Lojas.TipoEstabelecimento.LOJA_GERAL, 1_000_000);
        Lojas lb = loja("Loja B", b, Lojas.TipoEstabelecimento.LOJA_GERAL, 1_000_000);
        Item carga = item(50, "Caixote T", 10_000, Item.TipoItem.MATERIAL);

        Map<ServicoTransporte, Double> kmEmUmaHora = new EnumMap<>(ServicoTransporte.class);
        for (ServicoTransporte s : ServicoTransporte.values()) {
            Caravana c = new Caravana(la, lb, Lojas.clonarItem(carga, 1), rota, s, 1, 8);
            c.iniciarViagem(Caravana.TipoProtecao.DESPROTEGIDA, 1, 8);
            c.avancarHoras(1, 1, 9, SEM_ATAQUE);
            kmEmUmaHora.put(s, c.kmPercorridos());
        }
        afirme(Math.abs(kmEmUmaHora.get(ServicoTransporte.COMUM) - 6) < 0.01
                && Math.abs(kmEmUmaHora.get(ServicoTransporte.EXPRESSO) - 10) < 0.01
                && Math.abs(kmEmUmaHora.get(ServicoTransporte.COMBOIO_BLINDADO) - 4) < 0.01
                && Math.abs(kmEmUmaHora.get(ServicoTransporte.GUARDA_REAL) - 9) < 0.01,
                "Cada serviço anda na sua velocidade em 1 h: " + kmEmUmaHora + ".");
        afirme(Math.abs(rota.horasDeViagem(ServicoTransporte.EXPRESSO) - 10) < 0.01, "100 km de Expresso levam 10 h.");

        long freteComum = rota.calcularCustoFreteEmLatao(50, ServicoTransporte.COMUM);
        afirme(freteComum == rota.calcularCustoFreteEmLatao(50), "O frete sem serviço é o da Caravana Comum.");
        afirme(freteComum < rota.calcularCustoFreteEmLatao(50, ServicoTransporte.EXPRESSO)
                && rota.calcularCustoFreteEmLatao(50, ServicoTransporte.EXPRESSO) < rota.calcularCustoFreteEmLatao(50, ServicoTransporte.COMBOIO_BLINDADO)
                && rota.calcularCustoFreteEmLatao(50, ServicoTransporte.COMBOIO_BLINDADO) < rota.calcularCustoFreteEmLatao(50, ServicoTransporte.GUARDA_REAL),
                "Frete: Comum < Expresso < Blindado < Guarda Real.");
        double chanceComum = rota.chanceDeAtaque(ServicoTransporte.COMUM, 1.0);
        double chanceBlindado = rota.chanceDeAtaque(ServicoTransporte.COMBOIO_BLINDADO, 1.0);
        double chanceReal = rota.chanceDeAtaque(ServicoTransporte.GUARDA_REAL, 1.0);
        afirme(chanceBlindado < chanceComum && chanceReal < chanceBlindado,
                String.format("Chance de ataque na viagem: Comum %.0f%%, Blindado %.0f%%, Real %.0f%%.",
                        chanceComum * 100, chanceBlindado * 100, chanceReal * 100));
        afirme(rota.recomendaEscolta(ServicoTransporte.COMUM) && rota.recomendaEscolta(ServicoTransporte.EXPRESSO)
                && !rota.recomendaEscolta(ServicoTransporte.COMBOIO_BLINDADO) && !rota.recomendaEscolta(ServicoTransporte.GUARDA_REAL),
                "Em estrada de perigo 6, só os serviços seguros dispensam escolta.");

        // Carga valiosa por estrada perigosa: o mercador paga por segurança e não precisa de escolta
        MotorEconomico motor = new MotorEconomico(new Random(7));
        Cidade mina = cidade("Mina Funda", Cidade.TipoAssentamento.CIDADE);
        Cidade feira = cidade("Feira Alta", Cidade.TipoAssentamento.CIDADE);
        new Conexao(mina, feira, 120, 7, Conexao.TipoDeRota.TERRESTRE);
        Item joia = item(51, "Joia T", 200_000, Item.TipoItem.MATERIAL);
        Lojas joalheiro = loja("Joalheiro", mina, Lojas.TipoEstabelecimento.JOALHERIA, 100_000);
        joalheiro.adicionarItem(Lojas.clonarItem(joia, 20));
        Lojas comprador = loja("Comprador", feira, Lojas.TipoEstabelecimento.LOJA_GERAL, 50_000_000);
        comprador.definirDemanda(joia, 10);
        for (Cidade c : List.of(mina, feira)) motor.registrarCidade(c);
        motor.definirReceitasECatalogo(List.of(), List.of(joia));
        afirme(motor.solicitarCompraEntreCidades(comprador, joia, 5), "Joias são encomendadas pela estrada perigosa.");
        Caravana joias = motor.getCaravanas().get(0);
        afirme(joias.getServico().getFatorRisco() < 1, "Carga valiosa em estrada perigosa vai por serviço seguro (" + joias.getServico() + ").");
        afirme(joias.getStatus() == Caravana.StatusCaravana.EM_TRANSITO && motor.missaoAbertaDa(joias) == null,
                "O serviço seguro parte na hora, sem missão de escolta.");

        // Carga pesada de pouco valor, estrada curta e segura, prateleira abastecida: o frete mais barato vence
        MotorEconomico motor2 = new MotorEconomico(new Random(7));
        Cidade vila = cidade("Vila Mansa", Cidade.TipoAssentamento.VILA);
        Cidade burgo = cidade("Burgo Manso", Cidade.TipoAssentamento.CIDADE);
        new Conexao(vila, burgo, 30, 1, Conexao.TipoDeRota.TERRESTRE);
        Item tijolo = new Item(52, "Tijolo T", "Item de teste", 2.0, 20.0, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 1);
        Lojas olaria = loja("Olaria", vila, Lojas.TipoEstabelecimento.LOJA_GERAL, 100_000);
        olaria.adicionarItem(Lojas.clonarItem(tijolo, 20));
        Lojas obra = loja("Obra", burgo, Lojas.TipoEstabelecimento.LOJA_GERAL, 5_000_000);
        obra.adicionarItem(Lojas.clonarItem(tijolo, 3));
        obra.definirDemanda(tijolo, 10);
        for (Cidade c : List.of(vila, burgo)) motor2.registrarCidade(c);
        motor2.definirReceitasECatalogo(List.of(), List.of(tijolo));
        afirme(motor2.solicitarCompraEntreCidades(obra, tijolo, 5), "Tijolos são encomendados pela estrada curta.");
        ServicoTransporte servicoTijolo = motor2.getCaravanas().get(0).getServico();
        afirme(servicoTijolo == ServicoTransporte.COMUM, "Carga pesada e barata em estrada segura vai pela Caravana Comum (" + servicoTijolo + ").");
    }

    // ------------------------------------------------------------------
    // 2. Banco de teste
    // ------------------------------------------------------------------

    private static long contar(String sql) throws Exception {
        try (Connection conn = BancoDados.getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static void testarSementesDasLojasBase() throws Exception {
        System.out.println("\n[7/10] Sementes: lojas-base, receitas e itens de ofício...");
        MotorEconomico motor = new EconomiaDAO().carregarMotorEconomicoCompleto();

        long maritimas = contar("SELECT COUNT(*) FROM conexoes WHERE tipo_rota = 'MARITIMA'");
        afirme(maritimas > 0 && contar("SELECT COUNT(*) FROM conexoes WHERE tipo_rota = 'MARITIMA' AND bloqueada = 0") == 0,
                "As " + maritimas + " rotas marítimas continuam cadastradas, todas bloqueadas.");

        boolean todasComLoja = motor.getCidades().stream().allMatch(c -> !c.getLojas().isEmpty());
        afirme(todasComLoja, "Todas as " + motor.getCidades().size() + " cidades têm lojas.");
        for (Cidade c : motor.getCidades()) {
            Set<Lojas.TipoEstabelecimento> tipos = EnumSet.noneOf(Lojas.TipoEstabelecimento.class);
            c.getLojas().forEach(l -> tipos.add(l.getTipoEstabelecimento()));
            int esperado = switch (c.getTipo()) { case REINO -> 7; case CIDADE -> 5; case FORTALEZA -> 3; case VILA -> 2; };
            if (tipos.size() < esperado) afirme(false, c.getNome() + " (" + c.getTipo() + ") tem só " + tipos + ".");
        }
        afirme(true, "Cada cidade tem todas as especialidades do seu porte.");

        long receitas = contar("SELECT COUNT(*) FROM receitas WHERE tipo_loja IS NOT NULL");
        afirme(receitas >= 20, receitas + " receitas-base com especialidade.");
        afirme(motor.getReceitas().size() == contar("SELECT COUNT(*) FROM receitas"), "O motor carregou todas as receitas do banco.");
        afirme(contar("SELECT COUNT(*) FROM receita_insumos ri LEFT JOIN itens i ON i.id = ri.item_id WHERE i.id IS NULL") == 0, "Todo insumo de receita existe no catálogo.");
        for (String nome : List.of("Carvão Vegetal", "Hidromel das Montanhas", "Gibão de Couro Batido", "Frasco de Vidro")) {
            afirme(contar("SELECT COUNT(*) FROM itens WHERE nome = '" + nome + "'") == 1, "Item de ofício \"" + nome + "\" criado uma vez.");
        }

        long lojas = contar("SELECT COUNT(*) FROM lojas");
        long receitasTotal = contar("SELECT COUNT(*) FROM receitas");
        try (Connection conn = BancoDados.getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM receita_insumos WHERE receita_id = (SELECT id FROM receitas WHERE nome = 'Fermentar hidromel')");
            st.executeUpdate("DELETE FROM receitas WHERE nome = 'Fermentar hidromel'");
        }
        new EconomiaDAO().carregarMotorEconomicoCompleto();
        afirme(contar("SELECT COUNT(*) FROM lojas") == lojas, "Recarregar não duplica lojas.");
        afirme(contar("SELECT COUNT(*) FROM receitas") == receitasTotal - 1, "Receita apagada pelo Mestre não volta.");
    }

    private static void testarPersistenciaCompleta() throws Exception {
        System.out.println("\n[8/10] Persistência: caixa, estoque, nível, ordens, livro-caixa e caravanas...");
        EconomiaDAO dao = new EconomiaDAO();
        MotorEconomico motor = dao.carregarMotorEconomicoCompleto();
        motor.avancarTempo(24 * 6);

        Lojas alvo = motor.getCidades().stream().flatMap(c -> c.getLojas().stream()).filter(l -> !l.getOrdensReceita().isEmpty()).findFirst().orElse(null);
        afirme(alvo != null, "Após 6 dias há lojas produzindo sozinhas (IA ativa).");
        alvo.setNivelLoja(5);

        Map<Integer, Long> caixas = new HashMap<>();
        Map<Integer, Integer> unidades = new HashMap<>();
        Map<Integer, Integer> ordens = new HashMap<>();
        for (Cidade c : motor.getCidades()) for (Lojas l : c.getLojas()) {
            caixas.put(l.getId(), l.getCaixa().getValorTotalEmLatao());
            unidades.put(l.getId(), l.getEstoque().stream().mapToInt(Item::getQuantidade).sum());
            ordens.put(l.getId(), l.getOrdensReceita().size());
        }
        Map<Integer, Double> posicoes = new HashMap<>();
        Map<Integer, ServicoTransporte> servicos = new HashMap<>();
        for (Caravana c : motor.getCaravanasAtivas()) {
            posicoes.put(c.getId(), c.kmPercorridos());
            servicos.put(c.getId(), c.getServico());
        }
        afirme(motor.getCaravanasAtivas().stream().flatMap(c -> c.getRota().getArestas().stream())
                .noneMatch(a -> a.getTipo() == Conexao.TipoDeRota.MARITIMA), "Nenhuma caravana viaja pelo mar.");
        dao.salvarEstadoCompleto(motor);

        MotorEconomico recarregado = dao.carregarMotorEconomicoCompleto();
        boolean iguais = true;
        for (Cidade c : recarregado.getCidades()) for (Lojas l : c.getLojas()) {
            iguais &= Objects.equals(caixas.get(l.getId()), l.getCaixa().getValorTotalEmLatao());
            iguais &= Objects.equals(unidades.get(l.getId()), l.getEstoque().stream().mapToInt(Item::getQuantidade).sum());
            iguais &= Objects.equals(ordens.get(l.getId()), l.getOrdensReceita().size());
        }
        afirme(iguais, "Caixa, estoque e ordens de todas as " + caixas.size() + " lojas voltam iguais.");
        afirme(recarregado.buscarLojaPorId(alvo.getId()).getNivelLoja() == 5, "O nível da loja é salvo.");
        afirme(contar("SELECT COUNT(*) FROM livro_caixa_movimentacoes") > 1000, "O livro-caixa foi gravado no banco.");
        afirme(contar("SELECT COUNT(*) FROM livro_caixa_movimentacoes WHERE saldo_apos_latao IS NULL") == 0, "Todo movimento guarda o caixa depois dele.");

        afirme(!posicoes.isEmpty(), posicoes.size() + " caravanas em viagem no momento do salvamento.");
        boolean mesmasPosicoes = recarregado.getCaravanasAtivas().size() == posicoes.size();
        for (Caravana c : recarregado.getCaravanasAtivas()) {
            Double antes = posicoes.get(c.getId());
            mesmasPosicoes &= antes != null && Math.abs(antes - c.kmPercorridos()) < 0.01 && servicos.get(c.getId()) == c.getServico();
        }
        afirme(mesmasPosicoes, "As caravanas voltam com o mesmo id, serviço e ponto da estrada.");

        ComercioDAO comercio = new ComercioDAO();
        afirme(!comercio.listarMovimentacoes(alvo.getId(), 10).isEmpty(), "O livro-caixa da loja pode ser consultado.");
        afirme(!comercio.resumirDesde(1).isEmpty(), "O resumo por loja soma entradas e saídas.");
    }

    private static void testarEstabilidadeDoMundo() {
        System.out.println("\n[9/10] Estabilidade do mundo real em 60 dias...");
        MotorEconomico motor = new EconomiaDAO().carregarMotorEconomicoCompleto();
        List<Lojas> lojas = motor.getCidades().stream().flatMap(c -> c.getLojas().stream()).toList();
        long caixaInicial = lojas.stream().mapToLong(l -> l.getCaixa().getValorTotalEmLatao()).sum();

        long t0 = System.nanoTime();
        motor.avancarTempo(24 * 60);
        double msPorDia = (System.nanoTime() - t0) / 1e6 / 60;

        long caixaFinal = lojas.stream().mapToLong(l -> l.getCaixa().getValorTotalEmLatao()).sum();
        double razao = (double) caixaFinal / caixaInicial;
        long falidas = lojas.stream().filter(l -> l.getCaixa().getValorTotalEmLatao() < Lojas.RESERVA_CAIXA_LATAO / 2).count();
        long produziu = lojas.stream().mapToLong(l -> contaRegistros(l, RegistroMovimentacao.TipoOperacao.PRODUCAO_CONCLUIDA)).sum();
        long vendeu = lojas.stream().mapToLong(l -> contaRegistros(l, RegistroMovimentacao.TipoOperacao.VENDA_BALCAO)).sum();
        long entregues = motor.getCaravanas().stream().filter(c -> c.getStatus() == Caravana.StatusCaravana.ENTREGUE).count();

        afirme(razao > 0.6 && razao < 3.0, String.format("O dinheiro do mundo não some nem explode (%.2fx do caixa inicial).", razao));
        afirme(falidas <= lojas.size() / 10, falidas + " de " + lojas.size() + " lojas quase sem caixa (até 10%% é tolerado).".replace("%%", "%"));
        afirme(produziu > 100, produziu + " lotes produzidos pelas IAs.");
        afirme(vendeu > 1000, vendeu + " vendas de balcão a clientes.");
        afirme(entregues > 0, entregues + " caravanas entregues na última semana.");
        afirme(msPorDia < 400, String.format("Cada dia de mercado leva %.0f ms.", msPorDia));
    }

    // ------------------------------------------------------------------
    // 3. API da aba de Comércio
    // ------------------------------------------------------------------

    private static String token = "";

    private record Resposta(int codigo, String corpo) {}

    private static Resposta chamar(String metodo, String rota, String corpo) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create("http://localhost:" + PORTA + rota).toURL().openConnection();
        c.setRequestMethod(metodo);
        if (!token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
        if (corpo != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            try (OutputStream os = c.getOutputStream()) {
                os.write(corpo.getBytes(StandardCharsets.UTF_8));
            }
        }
        int codigo = c.getResponseCode();
        try (InputStream is = codigo >= 400 ? c.getErrorStream() : c.getInputStream()) {
            return new Resposta(codigo, is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String entrar(String usuario, String senha) throws Exception {
        token = "";
        Resposta r = chamar("POST", "/api/login", "username=" + usuario + "&senha=" + senha);
        int i = r.corpo().indexOf("\"token\":\"");
        return i < 0 ? "" : r.corpo().substring(i + 9, r.corpo().indexOf('"', i + 9));
    }

    private static int numero(String json, String campo) {
        int i = json.indexOf("\"" + campo + "\":");
        if (i < 0) return -1;
        int ini = i + campo.length() + 3, fim = ini;
        while (fim < json.length() && (Character.isDigit(json.charAt(fim)) || json.charAt(fim) == '-')) fim++;
        return Integer.parseInt(json.substring(ini, fim));
    }

    private static void testarApiDoMestre() throws Exception {
        System.out.println("\n[10/10] API da aba de Comércio do Mestre...");
        UsuarioDAO usuarios = new UsuarioDAO();
        usuarios.cadastrarUsuario("mestre_teste", "segredo-do-teste", "MESTRE", 999);
        UsuarioDAO.ContaUsuario jogador = usuarios.cadastrarUsuario("jogador_teste", "senha-do-jogador", "JOGADOR", 3);

        FichaDAO fichas = new FichaDAO();
        Ficha heroi = new Ficha(12, 12, 12, 12, 12, 12);
        heroi.setNome("Herói do Teste de Comércio");
        heroi.setUsuarioId(jogador.getId());
        heroi.getCarteira().adicionarOuro(100);
        fichas.salvar(heroi);

        ServidorThaluriaAPI servidor = new ServidorThaluriaAPI(PORTA);
        servidor.iniciar();
        try {
            afirme(chamar("GET", "/api/mestre/comercio/resumo", null).codigo() == 401, "Sem login a aba responde 401.");
            token = entrar("jogador_teste", "senha-do-jogador");
            afirme(chamar("GET", "/api/mestre/comercio/resumo", null).codigo() == 403, "Jogador recebe 403 na aba de Comércio.");

            token = entrar("mestre_teste", "segredo-do-teste");
            Resposta resumo = chamar("GET", "/api/mestre/comercio/resumo", null);
            afirme(resumo.codigo() == 200 && numero(resumo.corpo(), "lojas") > 100, "Resumo lista " + numero(resumo.corpo(), "lojas") + " lojas.");
            afirme(resumo.corpo().contains("\"caravanasEmViagem\""), "O resumo informa as caravanas em viagem.");

            MotorEconomico motor = new EconomiaDAO().carregarMotorEconomicoCompleto();
            Lojas forja = motor.getCidades().stream().flatMap(c -> c.getLojas().stream())
                    .filter(l -> l.getTipoEstabelecimento() == Lojas.TipoEstabelecimento.FORJA && !l.getEstoque().isEmpty()).findFirst().orElseThrow();
            ReceitaLoja receita = motor.getReceitas().stream().filter(r -> r.conhecidaPor(forja) && r.tipoLoja() != null).findFirst().orElseThrow();

            Resposta det = chamar("GET", "/api/mestre/comercio/loja?id=" + forja.getId(), null);
            afirme(det.codigo() == 200 && det.corpo().contains("\"receitas\"") && det.corpo().contains("\"movimentacoes\""), "Detalhe da loja traz estoque, receitas e livro-caixa.");

            Resposta prod = chamar("POST", "/api/mestre/comercio/produzir",
                    "lojaId=" + forja.getId() + "&receitaId=" + receita.id() + "&lotes=2&insumosPorConta=true&imediato=true");
            afirme(prod.codigo() == 200 && prod.corpo().contains("\"PRONTO\""), "Mandar fazer \"" + receita.nome() + "\" pronto agora.");
            Resposta recusada = chamar("POST", "/api/mestre/comercio/produzir", "lojaId=" + forja.getId() + "&receitaId=" + receita.id() + "&lotes=99");
            afirme(recusada.codigo() == 400 && recusada.corpo().contains("Faltam insumos"), "99 lotes sem insumos é recusado com a lista do que falta.");

            int itemVenda = receita.itemResultadoId();
            long saldoAntes = fichas.buscarPorId(heroi.getId()).getCarteira().getValorTotalEmLatao();
            Resposta venda = chamar("POST", "/api/mestre/comercio/vender",
                    "lojaId=" + forja.getId() + "&itemId=" + itemVenda + "&fichaId=" + heroi.getId() + "&quantidade=1&precoUnitarioLatao=50000&cobrar=true");
            afirme(venda.codigo() == 200, "Venda de 1 item por 5 PO ao herói.");
            Ficha heroiDepois = fichas.buscarPorId(heroi.getId());
            afirme(heroiDepois.getCarteira().getValorTotalEmLatao() == saldoAntes - 50_000, "A carteira do herói cai exatamente 5 PO.");
            afirme(new FichaExtrasDAO().listarInventario(heroi.getId()).stream().anyMatch(m -> ((Number) m.get("id")).intValue() == itemVenda), "O item vendido está no inventário do herói.");
            afirme(chamar("POST", "/api/mestre/comercio/vender", "lojaId=" + forja.getId() + "&itemId=" + itemVenda + "&fichaId=" + heroi.getId()
                    + "&quantidade=1&precoUnitarioLatao=999999999&cobrar=true").codigo() == 400, "Venda acima do saldo do herói é recusada.");

            Resposta ajuste = chamar("POST", "/api/mestre/comercio/ajustar", "lojaId=" + forja.getId() + "&acao=CAIXA&valorLatao=777777");
            afirme(ajuste.codigo() == 200 && ajuste.corpo().contains("\"caixaLatao\":777777"), "Ajuste de caixa pela API.");
            afirme(chamar("POST", "/api/mestre/comercio/ajustar", "lojaId=" + forja.getId() + "&acao=NIVEL&nivel=9").codigo() == 400, "Nível fora de 1–5 é recusado.");

            Resposta avancar = chamar("POST", "/api/mestre/controle-mundo", "acao=AVANCAR_DIA&dias=3");
            afirme(avancar.codigo() == 200, "Avançar 3 dias pela API.");
            Resposta caravanas = chamar("GET", "/api/mestre/comercio/caravanas", null);
            afirme(caravanas.codigo() == 200 && caravanas.corpo().contains("\"caravanas\":["), "Rota de caravanas para o mapa responde.");
            if (caravanas.corpo().contains("\"id\":")) {
                afirme(caravanas.corpo().contains("\"caminho\":[") && caravanas.corpo().contains("\"fracaoNoTrecho\""), "Cada caravana traz caminho e posição no trecho.");
            }

            // Forja: painel, prévia e encomenda personalizada com etapas
            Resposta painelForja = chamar("GET", "/api/mestre/forja", null);
            afirme(painelForja.codigo() == 200 && painelForja.corpo().contains("\"Espada\"") && painelForja.corpo().contains("\"Katana\""), "Painel da Forja traz moldes e tipos semeados.");
            ForjaDAO.Catalogo forjaCat = new ForjaDAO().carregarCatalogo();
            ForjaArmas.TipoArma katana = forjaCat.tipos().values().stream().filter(t -> t.nome().equals("Katana")).findFirst().orElseThrow();
            int metal = forjaCat.materiais().values().stream().filter(m -> m.categoria() == ForjaArmas.CategoriaMaterial.METAL).findFirst().orElseThrow().itemId();
            int couro = forjaCat.materiais().values().stream().filter(m -> m.categoria() == ForjaArmas.CategoriaMaterial.COURO).findFirst().orElseThrow().itemId();
            StringJoiner mats = new StringJoiner(",");
            for (ForjaArmas.Parte p : forjaCat.moldes().get(katana.moldeId()).partes()) {
                mats.add(p.id() + ":" + (p.nome().equals("Cabo") ? couro : metal) + ":" + p.qtdMateriais());
            }
            Resposta previa = chamar("POST", "/api/mestre/forja/previa", "tipoArmaId=" + katana.id() + "&materiais=" + mats);
            afirme(previa.codigo() == 200 && previa.corpo().contains("\"dano\":\"1d10") && previa.corpo().contains("\"problemas\":[]"), "Prévia calcula a katana sem gravar.");
            Resposta encomenda = chamar("POST", "/api/mestre/comercio/encomendar-arma",
                    "lojaId=" + forja.getId() + "&tipoArmaId=" + katana.id() + "&materiais=" + mats + "&insumosPorConta=true&imediato=false");
            afirme(encomenda.codigo() == 200 && encomenda.corpo().contains("\"etapas\":[{"), "Encomenda personalizada abre ordem com etapas.");
            int ultima = encomenda.corpo().split("\"indice\":", -1).length - 2;
            Resposta cancelada = chamar("POST", "/api/mestre/comercio/cancelar", "lojaId=" + forja.getId() + "&indice=" + ultima);
            afirme(cancelada.codigo() == 200 && cancelada.corpo().contains("\"CANCELADA\""), "Ordem cancelada pela API.");

            // Disseminação: a katana vira conhecimento secreto de outra cidade; a forja daqui não sabe fazê-la
            int outraCidade = motor.getCidades().stream().filter(c -> !c.equals(forja.getCidadeSede())).findFirst().orElseThrow().getId();
            Resposta concedido = chamar("POST", "/api/mestre/conhecimentos/conceder", "tipo=TIPO_ARMA&tipoArmaId=" + katana.id() + "&cidadeOrigemId=" + outraCidade);
            afirme(concedido.codigo() == 200, "Mestre concede a Katana a outra cidade.");
            int conhecimentoId = (int) numero(concedido.corpo(), "id");
            afirme(chamar("POST", "/api/mestre/conhecimentos/segredo", "id=" + conhecimentoId + "&segredo=true").codigo() == 200, "E a marca como segredo.");
            Resposta barrada = chamar("POST", "/api/mestre/comercio/encomendar-arma",
                    "lojaId=" + forja.getId() + "&tipoArmaId=" + katana.id() + "&materiais=" + mats + "&insumosPorConta=true&imediato=true");
            afirme(barrada.codigo() == 400 && barrada.corpo().contains("ainda não sabem"), "Forja de cidade sem Produção não aceita encomenda de Katana.");
            Resposta painelDis = chamar("GET", "/api/mestre/conhecimentos", null);
            afirme(painelDis.codigo() == 200 && painelDis.corpo().contains("\"Katana\"") && painelDis.corpo().contains("\"diaRumor\""), "Painel de disseminação traz a previsão por cidade.");
            chamar("POST", "/api/mestre/conhecimentos/cidade", "id=" + conhecimentoId + "&cidadeId=" + forja.getCidadeSede().getId() + "&acao=PRODUCAO");
            Resposta liberada = chamar("POST", "/api/mestre/comercio/encomendar-arma",
                    "lojaId=" + forja.getId() + "&tipoArmaId=" + katana.id() + "&materiais=" + mats + "&insumosPorConta=true&imediato=true");
            afirme(liberada.codigo() == 200 && liberada.corpo().contains("\"PRONTO\""), "Adiantada a cidade para Produção, a katana sai na hora.");
        } finally {
            servidor.parar();
        }
    }

    private static void apagarBancoTeste() {
        for (String sufixo : List.of("", "-wal", "-shm")) {
            File f = new File(BANCO_TESTE + sufixo);
            if (f.exists() && !f.delete()) f.deleteOnExit();
        }
    }
}
