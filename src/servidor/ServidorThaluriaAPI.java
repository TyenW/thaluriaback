package servidor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import economia.*;
import menu.CenarioInicial;
import persistencia.*;
import personagem.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servidor HTTP nativo (JDK 21) para o ecossistema Web de Thaluria RPG.
 * Conecta o Front-end (Jogador e Mestre) ao motor Java e ao banco SQLite (thaluria.db).
 */
public class ServidorThaluriaAPI {

    private static final AtomicLong totalOperacoes = new AtomicLong(0);
    private static final AtomicLong totalFalhas = new AtomicLong(0);
    private static final DateTimeFormatter FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HttpServer server;
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private final FichaDAO fichaDAO = new FichaDAO();
    private final MonstroDAO monstroDAO = new MonstroDAO();
    private final PermissoesMestreDAO permissoesDAO = new PermissoesMestreDAO();
    private final EconomiaDAO economiaDAO = new EconomiaDAO();
    private final FichaExtrasDAO fichaExtrasDAO = new FichaExtrasDAO();
    private final AdminCrud adminCrud = new AdminCrud();
    private final RotasComercio rotasComercio = new RotasComercio(this);
    private final RotasForja rotasForja = new RotasForja(this, rotasComercio);
    private final RotasConhecimento rotasConhecimento = new RotasConhecimento(this);
    private final SessaoLoginDAO sessaoDAO = new SessaoLoginDAO();

    /** Rotas abertas a quem ainda não entrou. */
    private static final Set<String> ROTAS_PUBLICAS = Set.of("/api/login", "/api/logout");
    /**
     * Parâmetros já conferidos de cada pedido em andamento. Não usa exchange.setAttribute:
     * no HttpServer do JDK os atributos ficam no contexto da rota e vazam entre pedidos simultâneos.
     */
    private final Map<HttpExchange, Map<String, String>> paramsDoPedido = new ConcurrentHashMap<>();
    /** Máximo de dias simulados num só pedido (um ano de jogo). */
    static final int MAX_DIAS_POR_AVANCO = 365;
    /** Alterações de ficha reservadas ao Mestre: o jogador apenas consulta. */
    private static final List<String> FICHA_SO_MESTRE = List.of(
            "/api/fichas/inventario/", "/api/fichas/pericias/", "/api/fichas/condicoes/",
            "/api/fichas/magias/", "/api/fichas/excluir");
    /** Campos de /api/fichas/atualizar que o jogador muda na própria ficha (moedas à parte). */
    private static final Set<String> CAMPOS_ATUALIZAR_DO_JOGADOR = Set.of(
            "id", "imagemUrl", "hpAtual", "sanAtual", "energiaAtual", "manaAtual");

    /** Tentativas de login falhas por conta: bloqueia após muitas falhas seguidas. */
    private static final int LOGIN_FALHAS_MAX = 8;
    private static final long LOGIN_JANELA_MS = 15 * 60 * 1000;
    private final Map<String, long[]> falhasLogin = new ConcurrentHashMap<>();

    private final ControlePermissoesMestre controleMestre;
    // volatile: o painel de gerenciamento recarrega o mundo após editar cidades, rotas ou lojas.
    private volatile MotorEconomico motorEconomico;
    private final Map<Integer, SessaoCriacaoFicha> sessoesEmMemoria = new ConcurrentHashMap<>();

    private SessaoCriacaoFicha obterOuCarregarSessao(int usuarioId) {
        SessaoCriacaoFicha s = sessoesEmMemoria.get(usuarioId);
        if (s == null) {
            s = usuarioDAO.carregarSessaoCriacao(usuarioId);
            if (s != null && s.getEtapaAtual() != SessaoCriacaoFicha.EtapaCriacao.FINALIZADO) {
                sessoesEmMemoria.put(usuarioId, s);
            }
        }
        return s;
    }

    @FunctionalInterface
    public interface RotaHandler {
        void handle(HttpExchange exchange) throws Exception;
    }

    void criarRota(String path, RotaHandler handler) {
        server.createContext(path, exchange -> {
            if (tratarCors(exchange)) return;
            totalOperacoes.incrementAndGet();
            try {
                if (!autorizarPedido(exchange)) return;
                handler.handle(exchange);
            } catch (Throwable t) {
                totalFalhas.incrementAndGet();
                String metodo = exchange.getRequestMethod();
                String pathUri = exchange.getRequestURI().getPath();
                registrarErro(metodo + " " + pathUri, "Exceção não tratada ao executar operação: " + t.getMessage(), t);
                try {
                    enviarJsonDireto(exchange, 500, "{\"erro\":\"Erro interno no servidor: " + escapar(t.getMessage()) + "\"}");
                } catch (IOException ignored) {}
            } finally {
                paramsDoPedido.remove(exchange);
            }
        });
    }

    public ServidorThaluriaAPI(int porta) throws IOException {
        BancoDados.inicializarEsquema();
        usuarioDAO.criarContasPadraoSeVazio();
        int migradas = usuarioDAO.migrarSenhasEmTextoPuro();
        if (migradas > 0) System.out.println("  🔒 " + migradas + " senha(s) em texto puro convertida(s) para hash PBKDF2.");
        monstroDAO.popularBestiarioCanonicaSeVazio();

        CenarioInicial.DadosCenario cenario = CenarioInicial.criarCenarioPadrao();
        this.motorEconomico = cenario.getMotor();
        this.controleMestre = permissoesDAO.carregar(this.motorEconomico);

        this.server = HttpServer.create(new InetSocketAddress(porta), 0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        // Dois relógios: a campanha (dias jogados, guia a disseminação) e o mercado, que pode estar à
        // frente quando o Mestre simula dias sem jogá-los. O motor recomeçava no dia 1 a cada reinício.
        int diaCampanha = Math.max(1, controleMestre.getDiaAtualCampanha());
        this.motorEconomico.setDiaCampanha(diaCampanha);
        this.motorEconomico.setDiaAtual(Math.max(diaCampanha, inteiroSalvo("dia", diaCampanha)));
        this.motorEconomico.setHoraAtual(Math.max(0, Math.min(23, inteiroSalvo("hora", this.motorEconomico.getHoraAtual()))));
        configurarRotas();
        rotasComercio.registrar();
        rotasForja.registrar();
        rotasConhecimento.registrar();
    }

    /**
     * Confere o token e as permissões antes de qualquer rota. O papel vem do banco,
     * nunca do navegador; parâmetros como usuarioId e papel são reescritos para o
     * jogador não agir em nome de outro. Devolve false se já respondeu com erro.
     */
    private boolean autorizarPedido(HttpExchange exchange) throws IOException {
        Map<String, String> params = lerParametrosDoPedido(exchange);
        paramsDoPedido.put(exchange, params);
        String rota = exchange.getRequestURI().getPath();
        if (ROTAS_PUBLICAS.contains(rota)) return true;

        SessaoLoginDAO.Sessao sessao = sessaoDAO.validar(tokenDoPedido(exchange));
        if (sessao == null) {
            enviarJson(exchange, 401, "{\"erro\":\"Sessão expirada ou inválida. Entre novamente.\"}");
            return false;
        }
        // O que cada um enxerga depende do papel gravado no banco, não do que o navegador diz.
        if ("/api/conteudo/visivel".equals(rota) || "/api/fichas".equals(rota)) params.put("papel", sessao.papel());
        if (sessao.ehMestre()) return true;

        boolean soMestre = rota.startsWith("/api/admin/") || rota.startsWith("/api/mestre/")
                || FICHA_SO_MESTRE.stream().anyMatch(rota::startsWith);
        if (soMestre) {
            enviarJson(exchange, 403, "{\"erro\":\"Apenas o Mestre pode fazer isso.\"}");
            return false;
        }

        String usuarioId = String.valueOf(sessao.usuarioId());
        if (rota.startsWith("/api/criacao/")) {
            params.put("usuarioId", usuarioId);
            params.put("username", sessao.username());
        }

        if ("/api/fichas".equals(rota)) {
            String id = params.getOrDefault("id", "").trim();
            if (id.isEmpty() || "0".equals(id)) {
                params.put("usuarioId", usuarioId);
                return true;
            }
            return exigirDonoDaFicha(exchange, sessao, id);
        }
        if (rota.startsWith("/api/fichas/")) {
            boolean atualizar = "/api/fichas/atualizar".equals(rota);
            String id = atualizar ? params.getOrDefault("id", "") : params.getOrDefault("fichaId", "");
            if (!exigirDonoDaFicha(exchange, sessao, id)) return false;
            return !atualizar || conferirAtualizacaoDoJogador(exchange, params, id);
        }
        return true;
    }

    /** Na própria ficha o jogador só troca o retrato, ajusta os recursos em jogo e gasta moedas. */
    private boolean conferirAtualizacaoDoJogador(HttpExchange exchange, Map<String, String> params, String idBruto) throws IOException {
        Ficha ficha = null;
        for (Map.Entry<String, String> campo : params.entrySet()) {
            if (CAMPOS_ATUALIZAR_DO_JOGADOR.contains(campo.getKey())) continue;
            Carteira.Moeda moeda = null;
            if (campo.getKey().startsWith("moeda_")) {
                try {
                    moeda = Carteira.Moeda.valueOf(campo.getKey().substring("moeda_".length()));
                } catch (IllegalArgumentException ignored) {}
            }
            if (moeda == null) {
                enviarJson(exchange, 403, "{\"erro\":\"Apenas o Mestre pode alterar esse campo da ficha.\"}");
                return false;
            }
            int nova;
            try {
                nova = Integer.parseInt(campo.getValue().trim());
            } catch (NumberFormatException ex) {
                continue; // a rota responde 400 com a mensagem certa
            }
            if (ficha == null) ficha = fichaDAO.buscarPorId(Long.parseLong(idBruto.trim()));
            int atual = ficha != null && ficha.getCarteira() != null ? ficha.getCarteira().getQuantidade(moeda) : 0;
            if (nova > atual) {
                enviarJson(exchange, 403, "{\"erro\":\"Apenas o Mestre pode dar moedas ao personagem.\"}");
                return false;
            }
        }
        return true;
    }

    private boolean exigirDonoDaFicha(HttpExchange exchange, SessaoLoginDAO.Sessao sessao, String idBruto) throws IOException {
        Integer dono = null;
        try {
            dono = sessaoDAO.donoDaFicha(Long.parseLong(idBruto.trim()));
        } catch (NumberFormatException ignored) {}
        if (dono == null) {
            enviarJson(exchange, 404, "{\"erro\":\"Ficha não encontrada\"}");
            return false;
        }
        if (dono != sessao.usuarioId()) {
            enviarJson(exchange, 403, "{\"erro\":\"Esta ficha pertence a outro jogador.\"}");
            return false;
        }
        return true;
    }

    private static String tokenDoPedido(HttpExchange exchange) {
        String cabecalho = exchange.getRequestHeaders().getFirst("Authorization");
        if (cabecalho == null || !cabecalho.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        return cabecalho.substring(7).trim();
    }

    /** Bloqueio por excesso de senhas erradas; devolve os minutos restantes ou 0. */
    private long minutosDeBloqueio(String conta) {
        long[] r = falhasLogin.get(conta);
        if (r == null) return 0;
        long passou = System.currentTimeMillis() - r[1];
        if (passou > LOGIN_JANELA_MS) {
            falhasLogin.remove(conta);
            return 0;
        }
        return r[0] >= LOGIN_FALHAS_MAX ? Math.max(1, (LOGIN_JANELA_MS - passou) / 60000) : 0;
    }

    private void registrarFalhaLogin(String conta) {
        falhasLogin.compute(conta, (k, r) -> {
            long agora = System.currentTimeMillis();
            if (r == null || agora - r[1] > LOGIN_JANELA_MS) return new long[] { 1, agora };
            r[0]++;
            return r;
        });
    }

    private void configurarRotas() {
        // 1. Autenticação (Jogador e Mestre)
        criarRota("/api/login", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            String user = params.getOrDefault("username", "");
            String pass = params.getOrDefault("senha", "");
            String chaveConta = user.trim().toLowerCase(Locale.ROOT);

            long bloqueio = minutosDeBloqueio(chaveConta);
            if (bloqueio > 0) {
                enviarJson(exchange, 429, "{\"erro\":\"Muitas tentativas erradas. Tente de novo em " + bloqueio + " min.\"}");
                return;
            }
            UsuarioDAO.ContaUsuario conta = usuarioDAO.autenticar(user, pass);
            if (conta == null) {
                registrarFalhaLogin(chaveConta);
                enviarJson(exchange, 401, "{\"erro\":\"Credenciais inválidas\"}");
                return;
            }
            falhasLogin.remove(chaveConta);
            String token = sessaoDAO.abrir(conta.getId());
            String json = String.format(Locale.US,
                "{\"id\":%d,\"username\":\"%s\",\"papel\":\"%s\",\"limiteFichas\":%d,\"pity\":%d,\"token\":\"%s\"}",
                conta.getId(), escapar(conta.getUsername()), conta.getPapel(),
                conta.getLimiteMaximoFichas(), conta.getFatorSortePity(), token
            );
            enviarJson(exchange, 200, json);
        });

        // 1.1 Encerrar a sessão (invalida o token no servidor)
        criarRota("/api/logout", exchange -> {
            sessaoDAO.encerrar(tokenDoPedido(exchange));
            enviarJson(exchange, 200, "{\"status\":\"ENCERRADA\"}");
        });

        // 2. Iniciar Criação de Ficha (Sorteia Humano + 2 Raças com Draconato < 1%).
        // Se o jogador já tem uma criação em andamento, ela é retomada com persistência no SQLite.
        criarRota("/api/criacao/iniciar", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            int usuarioId = Integer.parseInt(params.getOrDefault("usuarioId", "2"));
            String username = params.getOrDefault("username", "jogador1");
            String nomePersonagem = params.getOrDefault("nomePersonagem", "Novo Aventureiro");

            SessaoCriacaoFicha existente = obterOuCarregarSessao(usuarioId);
            if (existente != null && existente.getEtapaAtual() != SessaoCriacaoFicha.EtapaCriacao.FINALIZADO) {
                if (nomePersonagem != null && !nomePersonagem.isBlank()) {
                    existente.setNomePersonagem(nomePersonagem);
                    usuarioDAO.salvarSessaoCriacaoEmTempoReal(usuarioId, existente, null);
                }
                enviarJson(exchange, 200, sessaoCriacaoJson(usuarioId, existente, true));
                return;
            }

            UsuarioDAO.ContaUsuario conta = usuarioDAO.buscarPorUsername(username);
            if (conta == null) conta = usuarioDAO.buscarPorId(usuarioId);
            int pityAtual = (conta != null) ? conta.getFatorSortePity() : 0;

            SessaoCriacaoFicha sessao = new SessaoCriacaoFicha(
                    username, nomePersonagem, conta != null ? conta.getLimiteMaximoFichas() : 2, 0
            );
            sessao.iniciarEExecutarSorteioRacas(pityAtual, new Random());
            sessoesEmMemoria.put(usuarioId, sessao);
            usuarioDAO.salvarSessaoCriacaoEmTempoReal(usuarioId, sessao, null);
            usuarioDAO.atualizarFatorPity(usuarioId, pityAtual + 1);

            enviarJson(exchange, 200, sessaoCriacaoJson(usuarioId, sessao, false));
        });

        // 2.1 Consultar a criação em andamento do jogador (para retomar ao abrir a página)
        criarRota("/api/criacao/sessao", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            int usuarioId = Integer.parseInt(params.getOrDefault("usuarioId", "0"));
            SessaoCriacaoFicha sessao = obterOuCarregarSessao(usuarioId);
            if (sessao == null || sessao.getEtapaAtual() == SessaoCriacaoFicha.EtapaCriacao.FINALIZADO) {
                enviarJson(exchange, 200, "{\"ativa\":false}");
                return;
            }
            enviarJson(exchange, 200, sessaoCriacaoJson(usuarioId, sessao, true));
        });

        // 2.2 Descartar rascunho de criação e reiniciar do zero
        criarRota("/api/criacao/reiniciar", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            int usuarioId = Integer.parseInt(params.getOrDefault("usuarioId", "0"));
            sessoesEmMemoria.remove(usuarioId);
            usuarioDAO.excluirSessaoCriacao(usuarioId);
            enviarJson(exchange, 200, "{\"status\":\"OK\",\"acao\":\"REINICIADA\"}");
        });

        // 2.3 Atualizar nome do personagem no rascunho em tempo real
        criarRota("/api/criacao/atualizar-nome", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            int usuarioId = Integer.parseInt(params.getOrDefault("usuarioId", "0"));
            String novoNome = params.getOrDefault("nomePersonagem", "").trim();
            SessaoCriacaoFicha sessao = obterOuCarregarSessao(usuarioId);
            if (sessao != null && !novoNome.isBlank()) {
                sessao.setNomePersonagem(novoNome);
                usuarioDAO.salvarSessaoCriacaoEmTempoReal(usuarioId, sessao, null);
                enviarJson(exchange, 200, sessaoCriacaoJson(usuarioId, sessao, false));
            } else {
                enviarJson(exchange, 200, "{\"status\":\"IGNORADO\"}");
            }
        });

        // 3. Jogador Escolhe Raça e Rola Atributos dentro de [min, max] (máx. 2 rerolagens)
        criarRota("/api/criacao/escolher-raca", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            int usuarioId = Integer.parseInt(params.getOrDefault("usuarioId", "2"));
            int indiceOpcao = Integer.parseInt(params.getOrDefault("indiceOpcao", "0"));
            String linhagem = params.getOrDefault("linhagemDraconica", null);

            SessaoCriacaoFicha sessao = obterOuCarregarSessao(usuarioId);
            if (sessao == null) {
                enviarJson(exchange, 400, "{\"erro\":\"Sessão de criação não iniciada\"}");
                return;
            }
            try {
                sessao.escolherRacaERolarAtributos(indiceOpcao, new Random());
            } catch (IllegalStateException ex) {
                enviarJson(exchange, 409, "{\"erro\":\"" + escapar(ex.getMessage()) + "\",\"rerolagensRestantes\":0}");
                return;
            } catch (IllegalArgumentException ex) {
                enviarJson(exchange, 400, "{\"erro\":\"" + escapar(ex.getMessage()) + "\"}");
                return;
            }
            usuarioDAO.salvarSessaoCriacaoEmTempoReal(usuarioId, sessao, linhagem);
            enviarJson(exchange, 200, sessaoCriacaoJson(usuarioId, sessao, false));
        });


        // 4. Painel do Mestre: Monitorização em Tempo Real de Todas as Fichas em Criação
        criarRota("/api/mestre/sessoes-ao-vivo", exchange -> {
            if (tratarCors(exchange)) return;
            List<UsuarioDAO.EstadoSessaoCriacaoDTO> sessoes = usuarioDAO.listarTodasSessoesAtivasParaMestre();
            List<Map<String, Object>> lista = new ArrayList<>();
            for (UsuarioDAO.EstadoSessaoCriacaoDTO s : sessoes) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("usuarioId", s.usuarioId);
                m.put("personagem", s.nomePersonagemRascunho != null ? s.nomePersonagemRascunho : "Novo Aventureiro");
                m.put("etapa", s.etapaAtual != null ? s.etapaAtual : "");
                m.put("opcoes", String.join(",", s.opcoesRacasSorteadas));
                m.put("racaEscolhida", s.racaEscolhida != null ? s.racaEscolhida : "");
                m.put("dicaMestre", s.dicaMestre != null ? s.dicaMestre : "");
                m.put("aprovada", s.aprovadaPeloMestre);
                m.put("rerollsDisponiveis", s.rerollsConcedidos);
                lista.add(m);
            }
            enviarJson(exchange, 200, AdminCrud.json(lista));
        });

        // 5. Painel do Mestre: Intervir na Sessão do Jogador (Dica, Reroll, Sorteio de Novas Raças, Impulso Dracónico, Aprovar, Reiniciar)
        criarRota("/api/mestre/assistir-sessao", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            int usuarioId = Integer.parseInt(params.getOrDefault("usuarioId", "2"));
            String acao = params.getOrDefault("acao", "DICA");
            String textoDica = params.getOrDefault("dica", "");

            SessaoCriacaoFicha sessao = obterOuCarregarSessao(usuarioId);
            if (sessao == null) {
                enviarJson(exchange, 404, "{\"erro\":\"Sessão ativa não encontrada\"}");
                return;
            }

            switch (acao.toUpperCase()) {
                case "DICA" -> sessao.enviarDicaDoMestre(textoDica);
                case "REROLL" -> sessao.concederRerollAssistidoPeloMestre(1);
                case "REROLL_RACA" -> {
                    UsuarioDAO.ContaUsuario conta = usuarioDAO.buscarPorId(usuarioId);
                    int pity = conta != null ? conta.getFatorSortePity() : 0;
                    sessao.rerolarOpcoesRacas(pity, new Random());
                }
                case "IMPULSO_DRACONICO" -> sessao.liberarImpulsoDraconico();
                case "APROVAR" -> sessao.aprovarFichaPeloMestre();
                case "REINICIAR" -> {
                    sessoesEmMemoria.remove(usuarioId);
                    usuarioDAO.excluirSessaoCriacao(usuarioId);
                    enviarJson(exchange, 200, "{\"status\":\"OK\",\"acaoAplicada\":\"REINICIAR\"}");
                    return;
                }
            }
            usuarioDAO.salvarSessaoCriacaoEmTempoReal(usuarioId, sessao, null);
            enviarJson(exchange, 200, "{\"status\":\"OK\",\"acaoAplicada\":\"" + acao + "\"}");
        });

        // 6. Conteúdo Filtrado por Fog-of-War (Wiki, Bestiário com Moveset Pokémon e Mapa)
        criarRota("/api/conteudo/visivel", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            boolean visaoMestre = "MESTRE".equalsIgnoreCase(params.getOrDefault("papel", "JOGADOR"));

            List<MonstroDAO.EspecieMonstroPersistida> catalogo = monstroDAO.listarCatalogoCompleto();
            StringBuilder bestiarioJson = new StringBuilder("[");
            for (int i = 0; i < catalogo.size(); i++) {
                if (i > 0) bestiarioJson.append(",");
                MonstroDAO.EspecieMonstroPersistida m = catalogo.get(i);
                boolean liberado = visaoMestre || controleMestre.isMonstroReveladoNoBestiario(m.id);
                if (!liberado) {
                    bestiarioJson.append(String.format("{\"id\":\"%s\",\"revelado\":false,\"nome\":\"???\"}", m.id));
                } else {
                    StringBuilder golpesJson = new StringBuilder("[");
                    for (int g = 0; g < m.learnset.size(); g++) {
                        if (g > 0) golpesJson.append(",");
                        MonstroDAO.GolpePokemonDTO golpe = m.learnset.get(g);
                        golpesJson.append(String.format(
                            "{\"nome\":\"%s\",\"categoria\":\"%s\",\"elemento\":\"%s\",\"dados\":\"%dd%d\"}",
                            escapar(golpe.nomeGolpe), golpe.categoriaPokemon, golpe.elemento, golpe.qtdDados, golpe.facesDado
                        ));
                    }
                    golpesJson.append("]");
                    bestiarioJson.append(String.format(
                        "{\"id\":\"%s\",\"revelado\":true,\"nome\":\"%s\",\"regiao\":\"%s\",\"elemento\":\"%s\",\"elementoSecundario\":%s,\"porte\":\"%s\","
                            + "\"chefe\":%b,\"domesticavel\":%b,\"imagemUrl\":%s,\"descricao\":%s,\"golpesPokemon\":%s%s}",
                        m.id, escapar(m.nome), m.regiaoNativa.name(), m.elementoPrimario, AdminCrud.json(m.elementoSecundario), m.porte,
                        m.chefeSolo, m.domesticavel, AdminCrud.json(m.imagemUrl), AdminCrud.json(m.descricao), golpesJson,
                        // O Mestre vê tudo; este campo diz o que os jogadores enxergam
                        visaoMestre ? ",\"reveladoJogador\":" + controleMestre.isMonstroReveladoNoBestiario(m.id) : ""
                    ));
                }
            }
            bestiarioJson.append("]");

            // Cidades e Rotas filtradas pelo Fog-of-War do Mestre (com posição no mapa)
            Map<Integer, Map<String, Object>> dadosMapa = economiaDAO.listarDadosMapa();
            List<Map<String, Object>> cidadesLista = new ArrayList<>();
            Set<String> visiveisJogador = new HashSet<>();
            List<geografia.Cidade> cidades = motorEconomico.getMapa().getCidades();
            for (geografia.Cidade c : cidades) {
                ControlePermissoesMestre.NivelVisibilidadeMapa visJog = controleMestre.getVisibilidadeCidade(c.getNome());
                ControlePermissoesMestre.NivelVisibilidadeMapa vis = visaoMestre
                    ? ControlePermissoesMestre.NivelVisibilidadeMapa.INFORMACOES_COMPLETAS : visJog;
                if (visJog != ControlePermissoesMestre.NivelVisibilidadeMapa.OCULTO) visiveisJogador.add(c.getNome());

                Map<String, Object> cm = new LinkedHashMap<>();
                if (vis == ControlePermissoesMestre.NivelVisibilidadeMapa.OCULTO) {
                    cm.put("nome", "???");
                    cm.put("visibilidade", "OCULTO");
                    cidadesLista.add(cm);
                    continue;
                }
                cm.put("id", c.getId());
                cm.put("nome", c.getNome());
                cm.put("visibilidade", vis.name());
                Map<String, Object> mapa = dadosMapa.getOrDefault(c.getId(), Map.of());
                cm.put("x", mapa.get("x"));
                cm.put("y", mapa.get("y"));
                cm.put("reino", mapa.get("reino"));
                cm.put("icone", mapa.get("icone"));
                cm.put("temPorto", c.temPorto());
                if (vis == ControlePermissoesMestre.NivelVisibilidadeMapa.INFORMACOES_COMPLETAS) {
                    cm.put("visibilidadeJogador", visJog.name());
                    cm.put("tipo", c.getTipo().name());
                    cm.put("regiao", c.getRegiao().name());
                    cm.put("geologia", c.getGeologia() != null ? c.getGeologia().name() : null);
                    cm.put("aquifero", c.getAquifero() != null ? c.getAquifero().name() : null);
                    cm.put("lojas", c.getLojas().size());
                    cm.put("descricao", mapa.get("descricao"));
                }
                cidadesLista.add(cm);
            }
            String cidadesJson = AdminCrud.json(cidadesLista);

            // Rotas / Conexões do Grafo (o jogador só recebe rotas entre cidades reveladas)
            Set<geografia.Conexao> todasConexoes = new LinkedHashSet<>();
            for (geografia.Cidade c : cidades) {
                todasConexoes.addAll(c.getConexoes());
            }
            List<Map<String, Object>> rotasLista = new ArrayList<>();
            for (geografia.Conexao rota : todasConexoes) {
                String o = rota.getOrigem().getNome(), d = rota.getDestino().getNome();
                if (!visaoMestre && !(visiveisJogador.contains(o) && visiveisJogador.contains(d))) continue;
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("origem", o);
                rm.put("destino", d);
                rm.put("distanciaKm", rota.getDistanciaKm());
                rm.put("perigo", rota.getPerigo());
                rm.put("tipo", rota.getTipo().name());
                rm.put("bloqueada", rota.isBloqueada());
                rotasLista.add(rm);
            }
            String rotasJson = AdminCrud.json(rotasLista);

            // Capítulos da Wiki liberados
            StringBuilder wikiJson = new StringBuilder("[");
            int wIdx = 0;
            for (String cap : controleMestre.getCapitulosWikiLiberados()) {
                if (wIdx++ > 0) wikiJson.append(",");
                wikiJson.append("\"").append(escapar(cap)).append("\"");
            }
            wikiJson.append("]");

            enviarJson(exchange, 200, String.format(
                "{\"diaCampanha\":%d,\"diaMercado\":%d,\"capitulosWiki\":%s,\"cidades\":%s,\"rotas\":%s,\"bestiario\":%s}",
                controleMestre.getDiaAtualCampanha(), motorEconomico.getDiaAtual(), wikiJson, cidadesJson, rotasJson, bestiarioJson
            ));
        });

        // 7. Mestre: Desbloquear Capítulo da Wiki, Monstro do Bestiário ou Avançar Dia de Mercado
        criarRota("/api/mestre/controle-mundo", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            String acao = params.getOrDefault("acao", "");

            if ("AVANCAR_DIA".equalsIgnoreCase(acao) || "SIMULAR_MERCADO".equalsIgnoreCase(acao)) {
                int dias;
                try {
                    dias = Integer.parseInt(params.getOrDefault("dias", "1").trim());
                } catch (NumberFormatException e) {
                    dias = 0;
                }
                // Limite evita travar o servidor simulando anos de mercado num clique
                if (dias < 1 || dias > MAX_DIAS_POR_AVANCO) {
                    enviarJson(exchange, 400, "{\"erro\":\"Informe de 1 a " + MAX_DIAS_POR_AVANCO + " dias.\"}");
                    return;
                }
                boolean passaCampanha = "AVANCAR_DIA".equalsIgnoreCase(acao);
                synchronized (this) {
                    if (passaCampanha) controleMestre.avancarDiasDeMercado(dias, new Random());
                    motorEconomico.avancarTempo(24 * dias, passaCampanha);
                    motorEconomico.setDiaCampanha(controleMestre.getDiaAtualCampanha());
                    economiaDAO.salvarEstadoCompleto(motorEconomico);
                    salvarRelogioDoMercado();
                }
            } else if ("LIBERAR_WIKI".equalsIgnoreCase(acao)) {
                controleMestre.liberarCapituloWiki(params.getOrDefault("capitulo", ""));
            } else if ("REVELAR_MONSTRO".equalsIgnoreCase(acao)) {
                controleMestre.revelarMonstroBestiario(params.getOrDefault("monstroId", ""));
            } else if ("OCULTAR_MONSTRO".equalsIgnoreCase(acao)) {
                String monstroId = params.getOrDefault("monstroId", "").trim().toUpperCase(Locale.ROOT);
                controleMestre.ocultarMonstroBestiario(monstroId);
                // salvar() só grava os revelados; a chave removida precisa sair do banco
                permissoesDAO.removerValor("BESTIARIO", monstroId);
            } else if ("VISIBILIDADE_CIDADE".equalsIgnoreCase(acao)) {
                controleMestre.definirVisibilidadeCidade(
                    params.getOrDefault("cidade", ""),
                    ControlePermissoesMestre.NivelVisibilidadeMapa.valueOf(params.getOrDefault("nivel", "ROTA_E_NOME_APENAS"))
                );
            }
            permissoesDAO.salvar(controleMestre);
            enviarJson(exchange, 200, "{\"status\":\"OK\",\"diaAtual\":" + controleMestre.getDiaAtualCampanha()
                    + ",\"diaMercado\":" + motorEconomico.getDiaAtual() + "}");
        });

        // 8. Finalizar Criação de Ficha e Persistir no SQLite (FichaDAO)
        criarRota("/api/criacao/finalizar", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            int usuarioId = Integer.parseInt(params.getOrDefault("usuarioId", "2"));

            SessaoCriacaoFicha sessao = obterOuCarregarSessao(usuarioId);
            if (sessao == null) {
                enviarJson(exchange, 400, "{\"erro\":\"Nenhuma sessão de criação ativa para este usuário\"}");
                return;
            }
            Ficha novaFicha = sessao.finalizarFicha();
            novaFicha.setUsuarioId(usuarioId);
            fichaDAO.salvar(novaFicha);
            sessoesEmMemoria.remove(usuarioId);
            usuarioDAO.excluirSessaoCriacao(usuarioId);

            enviarJson(exchange, 200, String.format(Locale.US,
                "{\"status\":\"CRIADA\",\"id\":%d,\"usuarioId\":%d,\"nome\":\"%s\",\"raca\":\"%s\",\"hpMax\":%d,\"manaMax\":%d,\"energiaMax\":%d,\"sanMax\":%d}",
                novaFicha.getId(), novaFicha.getUsuarioId(),
                escapar(novaFicha.getNome()), escapar(novaFicha.getRaca().getNome()),
                novaFicha.getVidaMaxima(), novaFicha.getManaMaxima(),
                novaFicha.getEnergiaMaxima(), novaFicha.getSanidadeMaxima()
            ));
        });

        // 9. Listar Fichas Criadas (Filtradas por Usuário para Jogadores, Todas para o Mestre)
        criarRota("/api/fichas", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            String papel = params.getOrDefault("papel", "JOGADOR");
            int usuarioId = 0;
            try {
                usuarioId = Integer.parseInt(params.getOrDefault("usuarioId", "0"));
            } catch (NumberFormatException ignored) {}

            long fichaIdFiltro = 0;
            try {
                fichaIdFiltro = Long.parseLong(params.getOrDefault("id", "0"));
            } catch (NumberFormatException ignored) {}

            List<Ficha> fichas;
            if (fichaIdFiltro > 0) {
                Ficha unica = fichaDAO.buscarPorId(fichaIdFiltro);
                fichas = unica != null ? List.of(unica) : List.of();
            } else if ("MESTRE".equalsIgnoreCase(papel)) {
                fichas = fichaDAO.listarTodas();
            } else if (usuarioId > 0) {
                fichas = fichaDAO.listarPorUsuario(usuarioId);
            } else {
                fichas = fichaDAO.listarTodas();
            }

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < fichas.size(); i++) {
                if (i > 0) sb.append(",");
                Ficha f = fichas.get(i);
                List<MonstroDAO.FichaMonstroDomadoPersistida> domados = monstroDAO.listarDomadosPorFichaDono(f.getId());
                StringBuilder domJson = new StringBuilder("[");
                for (int d = 0; d < domados.size(); d++) {
                    if (d > 0) domJson.append(",");
                    MonstroDAO.FichaMonstroDomadoPersistida m = domados.get(d);
                    domJson.append(String.format(
                        "{\"idDomado\":%d,\"apelido\":\"%s\",\"especie\":\"%s\",\"bestaDeCarga\":%b,\"golpesAtivos\":[\"%s\"]}",
                        m.idDomado, escapar(m.apelido), m.monstroCatalogoId, m.bestaDeCargaAtiva,
                        String.join("\",\"", m.quatroSlotsAtaques)
                    ));
                }
                domJson.append("]");

                List<FichaDAO.PericiaDTO> pericias = fichaDAO.listarPericiasCompletas(f.getId());
                StringBuilder perJson = new StringBuilder("[");
                for (int pIdx = 0; pIdx < pericias.size(); pIdx++) {
                    if (pIdx > 0) perJson.append(",");
                    FichaDAO.PericiaDTO p = pericias.get(pIdx);
                    perJson.append(String.format(
                        "{\"pericia\":\"%s\",\"nome\":\"%s\",\"atributoBase\":\"%s\",\"grau\":%d,\"maestria\":%d,\"descricao\":\"%s\",\"personalizada\":%b}",
                        p.pericia, escapar(p.nome), p.atributoBase, p.grau, p.maestria, escapar(p.descricao), p.personalizada
                    ));
                }
                perJson.append("]");

                Raca racaObj = f.getRaca();
                String racaNome = racaObj != null ? racaObj.getNome() : "Humano";
                String reino = racaObj != null ? racaObj.getReinoOrigem() : "Terras Centrais";
                HabilidadeCombate inata = racaObj != null ? racaObj.getHabilidadeInata() : null;
                String inataNome = inata != null ? inata.getNome() : "Determinação Padrão";
                String inataDesc = inata != null ? inata.getDescricaoEfeito() : "Técnica instintiva de combate.";
                String inataDado = inata != null && inata.getDadoBase() != null ? inata.getDadoBase() : "1d4";
                int inataEnergia = inata != null ? inata.getCustoEnergia() : 1;
                int inataMana = inata != null ? inata.getCustoMana() : 0;
                double inataAlcance = inata != null ? inata.getAlcanceMetros() : 1.5;

                sb.append(String.format(Locale.US,
                    "{\"id\":%d,\"usuarioId\":%d,\"nome\":\"%s\",\"raca\":\"%s\",\"reinoOrigem\":\"%s\"," +
                    "\"vig\":%d,\"forca\":%d,\"des\":%d,\"men\":%d,\"esp\":%d,\"von\":%d," +
                    "\"hpAtual\":%d,\"hpMax\":%d,\"sanAtual\":%d,\"sanMax\":%d,\"energiaAtual\":%d,\"energiaMax\":%d,\"manaAtual\":%d,\"manaMax\":%d," +
                    "\"defFisica\":%d,\"defOculta\":%d,\"imagemUrl\":\"%s\"," +
                    "\"habilidadeInataNome\":\"%s\",\"habilidadeInataDescricao\":\"%s\",\"habilidadeInataDado\":\"%s\"," +
                    "\"habilidadeInataCustoEnergia\":%d,\"habilidadeInataCustoMana\":%d,\"habilidadeInataAlcance\":%.1f," +
                    "\"pontosEssencia\":%d,\"carteiraLatao\":%d,\"moedas\":%s,\"inventario\":%s,%s," +
                    "\"domados\":%s,\"pericias\":%s}",
                    f.getId(), f.getUsuarioId(), escapar(f.getNome()), escapar(racaNome), escapar(reino),
                    f.getAtributoBase(Atributo.VIGOR), f.getAtributoBase(Atributo.FORCA), f.getAtributoBase(Atributo.DESTREZA),
                    f.getAtributoBase(Atributo.MENTAL), f.getAtributoBase(Atributo.ESPIRITO), f.getAtributoBase(Atributo.VONTADE),
                    f.getVidaAtual(), f.getVidaMaxima(), f.getSanidadeAtual(), f.getSanidadeMaxima(),
                    f.getEnergiaAtual(), f.getEnergiaMaxima(), f.getManaAtual(), f.getManaMaxima(),
                    f.getDefesaFisica(), f.getDefesaOculta(),
                    f.getImagemUrl() != null ? escapar(f.getImagemUrl()) : "",
                    escapar(inataNome), escapar(inataDesc), escapar(inataDado),
                    inataEnergia, inataMana, inataAlcance,
                    f.getPontosEssencia(),
                    f.getCarteira() != null ? f.getCarteira().getValorTotalEmLatao() : 0L,
                    moedasJson(f.getCarteira()), inventarioJson(f), extrasFichaJson(f),
                    domJson, perJson
                ));
            }
            sb.append("]");
            enviarJson(exchange, 200, sb.toString());
        });

        // 9.1 Atualizar / Editar Ficha de Personagem (Nome, Recursos Vitais, Atributos, Carteira, PE, Imagem)
        criarRota("/api/fichas/atualizar", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            long id = Long.parseLong(params.getOrDefault("id", "0"));
            if (id <= 0) {
                enviarJson(exchange, 400, "{\"erro\":\"ID de ficha inválido\"}");
                return;
            }

            Ficha f = fichaDAO.buscarPorId(id);
            if (f == null) {
                enviarJson(exchange, 404, "{\"erro\":\"Ficha não encontrada no banco SQLite\"}");
                return;
            }

            // Moedas físicas: moeda_OURO=10, moeda_PRATA=3... Cada denominação é
            // gravada exatamente como enviada, sem conversão automática.
            Map<Carteira.Moeda, Integer> moedasRecebidas = new EnumMap<>(Carteira.Moeda.class);
            for (Carteira.Moeda m : Carteira.Moeda.values()) {
                String bruto = params.get("moeda_" + m.name());
                if (bruto == null) continue;
                int qtd;
                try {
                    qtd = Integer.parseInt(bruto.trim());
                } catch (NumberFormatException ex) {
                    enviarJson(exchange, 400, "{\"erro\":\"Quantidade inválida para a moeda " + m.getNome() + "\"}");
                    return;
                }
                if (qtd < 0) {
                    enviarJson(exchange, 400, "{\"erro\":\"A quantidade de " + m.getNome() + " não pode ser negativa\"}");
                    return;
                }
                moedasRecebidas.put(m, qtd);
            }

            if (params.containsKey("nome") && !params.get("nome").isBlank()) {
                f.setNome(params.get("nome"));
            }
            if (params.containsKey("imagemUrl")) {
                f.setImagemUrl(params.get("imagemUrl"));
            }
            if (params.containsKey("vig")) {
                f.setVig(Integer.parseInt(params.get("vig")));
            }
            if (params.containsKey("forca")) {
                f.setForca(Integer.parseInt(params.get("forca")));
            }
            if (params.containsKey("des")) {
                f.setDes(Integer.parseInt(params.get("des")));
            }
            if (params.containsKey("men")) {
                f.setMen(Integer.parseInt(params.get("men")));
            }
            if (params.containsKey("esp")) {
                f.setEsp(Integer.parseInt(params.get("esp")));
            }
            if (params.containsKey("von")) {
                f.setVon(Integer.parseInt(params.get("von")));
            }

            if (params.containsKey("hpAtual")) {
                f.setHp(Integer.parseInt(params.get("hpAtual")));
            }
            if (params.containsKey("sanAtual")) {
                f.setSanidade(Integer.parseInt(params.get("sanAtual")));
            }
            if (params.containsKey("energiaAtual")) {
                f.setEnergia(Integer.parseInt(params.get("energiaAtual")));
            }
            if (params.containsKey("manaAtual")) {
                f.setMana(Integer.parseInt(params.get("manaAtual")));
            }

            if (params.containsKey("carteiraLatao")) {
                try {
                    f.setCarteira(Carteira.deLatao(Long.parseLong(params.get("carteiraLatao"))));
                } catch (Exception ignored) {}
            }
            if (!moedasRecebidas.isEmpty()) {
                Carteira carteira = f.getCarteira() != null ? f.getCarteira() : new Carteira();
                moedasRecebidas.forEach(carteira::definirQuantidadeFisica);
                f.setCarteira(carteira);
            }
            if (params.containsKey("pontosEssencia")) {
                try {
                    f.setPontosEssencia(Integer.parseInt(params.get("pontosEssencia")));
                } catch (Exception ignored) {}
            }
            if (params.containsKey("cidadeId")) {
                String bruto = params.get("cidadeId").trim();
                if (bruto.isEmpty()) {
                    f.setCidadeAtual(null);
                } else {
                    geografia.Cidade cidade = null;
                    try {
                        cidade = economiaDAO.buscarCidadePorId(Integer.parseInt(bruto));
                    } catch (NumberFormatException ignored) {}
                    if (cidade == null) {
                        enviarJson(exchange, 400, "{\"erro\":\"Cidade não encontrada\"}");
                        return;
                    }
                    f.setCidadeAtual(cidade);
                }
            }


            fichaDAO.salvar(f);

            enviarJson(exchange, 200, String.format(Locale.US,
                "{\"status\":\"ATUALIZADA\",\"id\":%d,\"nome\":\"%s\",\"imagemUrl\":\"%s\",\"hpAtual\":%d,\"hpMax\":%d,\"sanAtual\":%d,\"sanMax\":%d,\"energiaAtual\":%d,\"energiaMax\":%d,\"manaAtual\":%d,\"manaMax\":%d,\"vig\":%d,\"forca\":%d,\"des\":%d,\"men\":%d,\"esp\":%d,\"von\":%d,\"defFisica\":%d,\"defOculta\":%d,\"carteiraLatao\":%d,\"moedas\":%s,\"pontosEssencia\":%d,\"cidadeAtual\":%s,\"cidadeAtualId\":%s}",
                f.getId(), escapar(f.getNome()), f.getImagemUrl() != null ? escapar(f.getImagemUrl()) : "",
                f.getVidaAtual(), f.getVidaMaxima(),
                f.getSanidadeAtual(), f.getSanidadeMaxima(), f.getEnergiaAtual(), f.getEnergiaMaxima(),
                f.getManaAtual(), f.getManaMaxima(), f.getAtributoBase(Atributo.VIG),
                f.getAtributoBase(Atributo.FOR), f.getAtributoBase(Atributo.DES),
                f.getAtributoBase(Atributo.MEN), f.getAtributoBase(Atributo.ESP),
                f.getAtributoBase(Atributo.VON), f.getDefesaFisica(), f.getDefesaOculta(),
                f.getCarteira() != null ? f.getCarteira().getValorTotalEmLatao() : 0L,
                moedasJson(f.getCarteira()),
                f.getPontosEssencia(),
                AdminCrud.json(f.getCidadeAtual() != null ? f.getCidadeAtual().getNome() : null),
                AdminCrud.json(f.getCidadeAtual() != null ? f.getCidadeAtual().getId() : null)
            ));
        });

        // 9.2 Salvar / Editar / Criar Perícia (Mestre e Jogador)
        criarRota("/api/fichas/pericias/salvar", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            long fichaId = Long.parseLong(params.getOrDefault("fichaId", "0"));
            if (fichaId <= 0) {
                enviarJson(exchange, 400, "{\"erro\":\"ID de ficha inválido\"}");
                return;
            }

            String nome = params.getOrDefault("nome", "").trim();
            if (nome.isBlank()) {
                enviarJson(exchange, 400, "{\"erro\":\"Nome da perícia é obrigatório\"}");
                return;
            }

            String periciaKey = params.getOrDefault("pericia", "").trim();
            if (periciaKey.isBlank()) {
                periciaKey = "CUSTOM_" + System.currentTimeMillis();
            }

            String atributoBase = params.getOrDefault("atributoBase", "FOR").toUpperCase();
            int grau = Integer.parseInt(params.getOrDefault("grau", "0"));
            int maestria = Integer.parseInt(params.getOrDefault("maestria", "0"));
            String descricao = params.getOrDefault("descricao", "");

            fichaDAO.salvarPericiaIndividual(fichaId, periciaKey, nome, atributoBase, grau, maestria, descricao);

            List<FichaDAO.PericiaDTO> pericias = fichaDAO.listarPericiasCompletas(fichaId);
            StringBuilder perJson = new StringBuilder("[");
            for (int pIdx = 0; pIdx < pericias.size(); pIdx++) {
                if (pIdx > 0) perJson.append(",");
                FichaDAO.PericiaDTO p = pericias.get(pIdx);
                perJson.append(String.format(
                    "{\"pericia\":\"%s\",\"nome\":\"%s\",\"atributoBase\":\"%s\",\"grau\":%d,\"maestria\":%d,\"descricao\":\"%s\",\"personalizada\":%b}",
                    p.pericia, escapar(p.nome), p.atributoBase, p.grau, p.maestria, escapar(p.descricao), p.personalizada
                ));
            }
            perJson.append("]");

            enviarJson(exchange, 200, String.format(
                "{\"status\":\"SALVA\",\"fichaId\":%d,\"pericia\":\"%s\",\"pericias\":%s}",
                fichaId, periciaKey, perJson
            ));
        });

        // 9.3 Remover Perícia Customizada
        criarRota("/api/fichas/pericias/remover", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            long fichaId = Long.parseLong(params.getOrDefault("fichaId", "0"));
            String periciaKey = params.getOrDefault("pericia", "").trim();

            if (fichaId <= 0 || periciaKey.isBlank()) {
                enviarJson(exchange, 400, "{\"erro\":\"Parâmetros inválidos para remoção de perícia\"}");
                return;
            }

            boolean removida = fichaDAO.removerPericia(fichaId, periciaKey);

            List<FichaDAO.PericiaDTO> pericias = fichaDAO.listarPericiasCompletas(fichaId);
            StringBuilder perJson = new StringBuilder("[");
            for (int pIdx = 0; pIdx < pericias.size(); pIdx++) {
                if (pIdx > 0) perJson.append(",");
                FichaDAO.PericiaDTO p = pericias.get(pIdx);
                perJson.append(String.format(
                    "{\"pericia\":\"%s\",\"nome\":\"%s\",\"atributoBase\":\"%s\",\"grau\":%d,\"maestria\":%d,\"descricao\":\"%s\",\"personalizada\":%b}",
                    p.pericia, escapar(p.nome), p.atributoBase, p.grau, p.maestria, escapar(p.descricao), p.personalizada
                ));
            }
            perJson.append("]");

            enviarJson(exchange, 200, String.format(
                "{\"status\":\"REMOVIDA\",\"sucesso\":%b,\"fichaId\":%d,\"pericia\":\"%s\",\"pericias\":%s}",
                removida, fichaId, periciaKey, perJson
            ));
        });

        // 9.5 História, aparência, personalidade e anotações da ficha
        criarRota("/api/fichas/historia/salvar", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            long fichaId = lerFichaId(params);
            if (fichaId <= 0 || fichaDAO.buscarPorId(fichaId) == null) {
                enviarJson(exchange, 404, "{\"erro\":\"Ficha não encontrada\"}");
                return;
            }
            Map<String, String> valores = new HashMap<>();
            for (String c : FichaExtrasDAO.CAMPOS_HISTORIA) {
                if (params.containsKey(c)) valores.put(c, params.get(c));
            }
            try {
                fichaExtrasDAO.salvarHistoria(fichaId, valores);
            } catch (FichaExtrasDAO.ErroValidacao ex) {
                enviarJson(exchange, 400, "{\"erro\":" + AdminCrud.json(ex.getMessage()) + "}");
                return;
            }
            enviarJson(exchange, 200, "{\"status\":\"SALVA\",\"historia\":" + AdminCrud.json(fichaExtrasDAO.lerHistoria(fichaId)) + "}");
        });

        // 9.6 Condições ativas (salvar, remover, avançar turno)
        criarRota("/api/fichas/condicoes/salvar", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> p = lerQueryOuForm(exchange);
            long fichaId = lerFichaId(p);
            if (fichaId <= 0 || fichaDAO.buscarPorId(fichaId) == null) {
                enviarJson(exchange, 404, "{\"erro\":\"Ficha não encontrada\"}");
                return;
            }
            try {
                String intens = p.getOrDefault("intensidade", "").trim();
                String dur = p.getOrDefault("duracaoTurnos", "").trim();
                fichaExtrasDAO.salvarCondicao(fichaId, lerIdOpcional(p.get("id")), p.get("nome"),
                        p.getOrDefault("tipo", "NEGATIVA").toUpperCase(),
                        intens.isEmpty() ? 1 : Integer.parseInt(intens),
                        dur.isEmpty() ? null : Integer.valueOf(dur),
                        p.get("descricao"));
            } catch (NumberFormatException ex) {
                enviarJson(exchange, 400, "{\"erro\":\"Intensidade e duração devem ser números inteiros.\"}");
                return;
            } catch (FichaExtrasDAO.ErroValidacao ex) {
                enviarJson(exchange, 400, "{\"erro\":" + AdminCrud.json(ex.getMessage()) + "}");
                return;
            }
            enviarJson(exchange, 200, "{\"status\":\"SALVA\",\"condicoes\":" + AdminCrud.json(fichaExtrasDAO.listarCondicoes(fichaId)) + "}");
        });

        criarRota("/api/fichas/condicoes/remover", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> p = lerQueryOuForm(exchange);
            long fichaId = lerFichaId(p);
            Long id = lerIdOpcional(p.get("id"));
            if (fichaId <= 0 || id == null) {
                enviarJson(exchange, 400, "{\"erro\":\"Parâmetros inválidos\"}");
                return;
            }
            fichaExtrasDAO.remover("ficha_condicoes", fichaId, id);
            enviarJson(exchange, 200, "{\"status\":\"REMOVIDA\",\"condicoes\":" + AdminCrud.json(fichaExtrasDAO.listarCondicoes(fichaId)) + "}");
        });

        criarRota("/api/fichas/condicoes/avancar-turno", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> p = lerQueryOuForm(exchange);
            long fichaId = lerFichaId(p);
            if (fichaId <= 0) {
                enviarJson(exchange, 400, "{\"erro\":\"ID de ficha inválido\"}");
                return;
            }
            int encerradas = fichaExtrasDAO.avancarTurno(fichaId);
            enviarJson(exchange, 200, "{\"status\":\"OK\",\"encerradas\":" + encerradas + ",\"condicoes\":"
                    + AdminCrud.json(fichaExtrasDAO.listarCondicoes(fichaId)) + "}");
        });

        // 9.7 Magias conhecidas
        criarRota("/api/fichas/magias/salvar", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> p = lerQueryOuForm(exchange);
            long fichaId = lerFichaId(p);
            if (fichaId <= 0 || fichaDAO.buscarPorId(fichaId) == null) {
                enviarJson(exchange, 404, "{\"erro\":\"Ficha não encontrada\"}");
                return;
            }
            try {
                fichaExtrasDAO.salvarMagia(fichaId, lerIdOpcional(p.get("id")), p);
            } catch (FichaExtrasDAO.ErroValidacao ex) {
                enviarJson(exchange, 400, "{\"erro\":" + AdminCrud.json(ex.getMessage()) + "}");
                return;
            }
            enviarJson(exchange, 200, "{\"status\":\"SALVA\",\"magias\":" + AdminCrud.json(fichaExtrasDAO.listarMagias(fichaId)) + "}");
        });

        criarRota("/api/fichas/magias/remover", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> p = lerQueryOuForm(exchange);
            long fichaId = lerFichaId(p);
            Long id = lerIdOpcional(p.get("id"));
            if (fichaId <= 0 || id == null) {
                enviarJson(exchange, 400, "{\"erro\":\"Parâmetros inválidos\"}");
                return;
            }
            fichaExtrasDAO.remover("ficha_magias", fichaId, id);
            enviarJson(exchange, 200, "{\"status\":\"REMOVIDA\",\"magias\":" + AdminCrud.json(fichaExtrasDAO.listarMagias(fichaId)) + "}");
        });

        // 9.8 Inventário: o Mestre concede ou retira itens do catálogo
        criarRota("/api/fichas/inventario/conceder", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> p = lerQueryOuForm(exchange);
            long fichaId = lerFichaId(p);
            Long itemId = lerIdOpcional(p.get("itemId"));
            if (fichaId <= 0 || itemId == null) {
                enviarJson(exchange, 400, "{\"erro\":\"Informe a ficha e o item.\"}");
                return;
            }
            try {
                String qtd = p.getOrDefault("quantidade", "1").trim();
                fichaExtrasDAO.concederItem(fichaId, itemId, qtd.isEmpty() ? 1 : Integer.parseInt(qtd));
            } catch (NumberFormatException ex) {
                enviarJson(exchange, 400, "{\"erro\":\"A quantidade deve ser um número inteiro.\"}");
                return;
            } catch (FichaExtrasDAO.ErroValidacao ex) {
                enviarJson(exchange, 400, "{\"erro\":" + AdminCrud.json(ex.getMessage()) + "}");
                return;
            }
            enviarJson(exchange, 200, "{\"status\":\"CONCEDIDO\",\"inventario\":" + AdminCrud.json(fichaExtrasDAO.listarInventario(fichaId)) + "}");
        });

        criarRota("/api/fichas/inventario/retirar", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> p = lerQueryOuForm(exchange);
            long fichaId = lerFichaId(p);
            Long itemId = lerIdOpcional(p.get("itemId"));
            if (fichaId <= 0 || itemId == null) {
                enviarJson(exchange, 400, "{\"erro\":\"Informe a ficha e o item.\"}");
                return;
            }
            try {
                // Sem quantidade: remove a pilha inteira.
                String qtd = p.getOrDefault("quantidade", "").trim();
                fichaExtrasDAO.retirarItem(fichaId, itemId, qtd.isEmpty() ? null : Integer.valueOf(qtd));
            } catch (NumberFormatException ex) {
                enviarJson(exchange, 400, "{\"erro\":\"A quantidade deve ser um número inteiro.\"}");
                return;
            } catch (FichaExtrasDAO.ErroValidacao ex) {
                enviarJson(exchange, 400, "{\"erro\":" + AdminCrud.json(ex.getMessage()) + "}");
                return;
            }
            enviarJson(exchange, 200, "{\"status\":\"RETIRADO\",\"inventario\":" + AdminCrud.json(fichaExtrasDAO.listarInventario(fichaId)) + "}");
        });

        // 12. Painel de gerenciamento do Mestre: CRUD genérico (itens, receitas, monstros, cidades, rotas, lojas)
        criarRota("/api/admin/esquema", exchange -> {
            if (tratarCors(exchange)) return;
            enviarJson(exchange, 200, adminCrud.esquemaJson());
        });

        criarRota("/api/admin/listar", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> p = lerQueryOuForm(exchange);
            try {
                AdminCrud.Entidade e = adminCrud.obter(p.getOrDefault("entidade", ""));
                enviarJson(exchange, 200, AdminCrud.json(adminCrud.listar(e, p.get("pai"))));
            } catch (AdminCrud.ErroValidacao ex) {
                enviarJson(exchange, 400, "{\"erro\":" + AdminCrud.json(ex.getMessage()) + "}");
            }
        });

        criarRota("/api/admin/ligacoes", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> p = lerQueryOuForm(exchange);
            try {
                enviarJson(exchange, 200, AdminCrud.json(adminCrud.ligacoes(p.getOrDefault("entidade", ""), p.getOrDefault("id", ""))));
            } catch (AdminCrud.ErroValidacao ex) {
                enviarJson(exchange, 400, "{\"erro\":" + AdminCrud.json(ex.getMessage()) + "}");
            }
        });

        criarRota("/api/admin/salvar", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> p = lerQueryOuForm(exchange);
            try {
                AdminCrud.Entidade e = adminCrud.obter(p.getOrDefault("entidade", ""));
                AdminCrud.Salvo salvo = adminCrud.salvar(e, p);
                String nomeNovo = p.getOrDefault("nome", "").trim();
                if ("cidades".equals(e.id) && salvo.nomeAnterior != null && !salvo.nomeAnterior.equals(nomeNovo)) {
                    // O fog-of-war é guardado pelo nome: leva a visibilidade para o novo nome.
                    controleMestre.setVisibilidadeCidade(nomeNovo, controleMestre.getVisibilidadeCidade(salvo.nomeAnterior));
                    permissoesDAO.salvar(controleMestre);
                }
                if (e.afetaMundo || "loja_estoque".equals(e.id)) recarregarMundo();
                // Material com outro bônus ou preço muda as armas da forja que o usam
                if ("itens".equals(e.id)) new ForjaDAO().recompilarTudo();
                if (RotasComercio.ENTIDADES_DE_RECEITA.contains(e.id)) rotasComercio.atualizarReceitasDoMotor();
                enviarJson(exchange, 200, "{\"status\":\"SALVO\",\"id\":" + AdminCrud.json(salvo.id) + "}");
            } catch (AdminCrud.ErroValidacao ex) {
                enviarJson(exchange, 400, "{\"erro\":" + AdminCrud.json(ex.getMessage()) + "}");
            }
        });

        criarRota("/api/admin/excluir", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> p = lerQueryOuForm(exchange);
            try {
                AdminCrud.Entidade e = adminCrud.obter(p.getOrDefault("entidade", ""));
                adminCrud.excluir(e, p.get("id"));
                if (e.afetaMundo || "loja_estoque".equals(e.id)) recarregarMundo();
                if (RotasComercio.ENTIDADES_DE_RECEITA.contains(e.id)) rotasComercio.atualizarReceitasDoMotor();
                enviarJson(exchange, 200, "{\"status\":\"EXCLUIDO\"}");
            } catch (AdminCrud.ErroValidacao ex) {
                enviarJson(exchange, 400, "{\"erro\":" + AdminCrud.json(ex.getMessage()) + "}");
            }
        });

        // 9.4 Excluir Ficha de Personagem (Mestre e Jogador Dono)
        criarRota("/api/fichas/excluir", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            long id = Long.parseLong(params.getOrDefault("id", "0"));
            if (id <= 0) {
                enviarJson(exchange, 400, "{\"erro\":\"ID de ficha inválido para exclusão\"}");
                return;
            }

            boolean sucesso = fichaDAO.excluir(id);
            if (!sucesso) {
                enviarJson(exchange, 404, "{\"erro\":\"Ficha não encontrada para exclusão\"}");
                return;
            }

            enviarJson(exchange, 200, String.format(Locale.US,
                "{\"status\":\"EXCLUIDA\",\"sucesso\":true,\"id\":%d}", id
            ));
        });

        // 10. Mestre: Listar Todos os Usuários (GET) ou Cadastrar/Alterar Limite (POST)
        criarRota("/api/mestre/usuarios", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            String metodo = exchange.getRequestMethod();

            // Se for GET ou se não tiver username passado, retorna a lista de todos os usuários
            if ("GET".equalsIgnoreCase(metodo) || (!params.containsKey("username") && !params.containsKey("limiteFichas"))) {
                List<UsuarioDAO.ResumoUsuarioDTO> users = usuarioDAO.listarTodos();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < users.size(); i++) {
                    if (i > 0) sb.append(",");
                    UsuarioDAO.ResumoUsuarioDTO u = users.get(i);
                    sb.append(String.format(Locale.US,
                        "{\"id\":%d,\"username\":\"%s\",\"papel\":\"%s\",\"limiteFichas\":%d,\"pity\":%d,\"totalFichas\":%d}",
                        u.id, escapar(u.username), u.papel, u.limiteFichas, u.pity, u.totalFichas
                    ));
                }
                sb.append("]");
                enviarJson(exchange, 200, sb.toString());
                return;
            }

            // POST: Cadastrar ou atualizar usuário
            String username = params.getOrDefault("username", "");
            String senha = params.get("senha"); // vazio/ausente = manter a senha atual
            String papel = params.getOrDefault("papel", "JOGADOR");
            int limite = Integer.parseInt(params.getOrDefault("limiteFichas", "2"));

            UsuarioDAO.ContaUsuario conta = usuarioDAO.cadastrarUsuario(username, senha, papel, limite);
            // Senha nova derruba as sessões abertas da conta
            if (senha != null && !senha.isBlank()) sessaoDAO.encerrarDoUsuario(conta.getId());
            enviarJson(exchange, 200, String.format(
                "{\"id\":%d,\"username\":\"%s\",\"papel\":\"%s\",\"limiteFichas\":%d}",
                conta.getId(), escapar(conta.getUsername()), conta.getPapel(), conta.getLimiteMaximoFichas()
            ));
        });

        // 10.1 Mestre: Excluir Conta de Usuário / Jogador
        criarRota("/api/mestre/usuarios/excluir", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            int id = Integer.parseInt(params.getOrDefault("id", "0"));
            if (id <= 0) {
                enviarJson(exchange, 400, "{\"erro\":\"ID de usuário inválido\"}");
                return;
            }

            try {
                boolean sucesso = usuarioDAO.excluirUsuario(id);
                if (sucesso) {
                    enviarJson(exchange, 200, String.format(Locale.US, "{\"status\":\"EXCLUIDO\",\"sucesso\":true,\"id\":%d}", id));
                } else {
                    enviarJson(exchange, 404, "{\"erro\":\"Usuário não encontrado\"}");
                }
            } catch (IllegalArgumentException ex) {
                enviarJson(exchange, 400, String.format("{\"erro\":\"%s\"}", escapar(ex.getMessage())));
            } catch (Exception ex) {
                enviarJson(exchange, 500, String.format("{\"erro\":\"Erro ao excluir conta: %s\"}", escapar(ex.getMessage())));
            }
        });

        // 11. Fallback 404 para rotas inexistentes
        server.createContext("/", exchange -> {
            if ("/favicon.ico".equals(exchange.getRequestURI().getPath())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (tratarCors(exchange)) return;
            totalOperacoes.incrementAndGet();
            totalFalhas.incrementAndGet();
            String metodo = exchange.getRequestMethod();
            String pathUri = exchange.getRequestURI().getPath();
            registrarErro(metodo + " " + pathUri, "HTTP 404 - Rota inexistente no servidor", null);
            enviarJsonDireto(exchange, 404, "{\"erro\":\"Rota não encontrada\"}");
        });
    }

    public void iniciar() {
        server.start();
    }

    public void parar() {
        server.stop(0);
    }

    MotorEconomico getMotorEconomico() { return motorEconomico; }

    EconomiaDAO getEconomiaDAO() { return economiaDAO; }

    public int getPorta() {
        return server.getAddress().getPort();
    }

    public static void main(String[] args) {
        int porta = 8080;
        if (args.length > 0) {
            try { porta = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        try {
            ServidorThaluriaAPI api = new ServidorThaluriaAPI(porta);
            api.iniciar();
            System.out.println("================================================================================");
            System.out.println("  🏰 [THALÚRIA RPG] Servidor API HTTP iniciado na porta " + porta);
            System.out.println("  🛡️  [REGISTRO DE ERROS] Monitoramento ativo: Registrando SOMENTE ERROS no terminal.");
            System.out.println("  ⚔️  Aguardando requisições do Front-End...");
            System.out.println("================================================================================\n");
        } catch (BindException e) {
            totalOperacoes.incrementAndGet();
            totalFalhas.incrementAndGet();
            registrarErro("Inicialização do Servidor (Porta " + porta + ")",
                "A porta " + porta + " já está em uso por outro processo (outra instância do backend).", e);
            System.err.println("💡 [DICA PARA LIBERAR A PORTA NO WINDOWS]:");
            System.err.println("   1. Abra o terminal (PowerShell ou CMD)");
            System.err.println("   2. Digite: netstat -ano | findstr :" + porta);
            System.err.println("   3. Finalize o processo antigo: taskkill /PID <PID_ENCONTRADO> /F\n");
        } catch (Exception e) {
            totalOperacoes.incrementAndGet();
            totalFalhas.incrementAndGet();
            registrarErro("Inicialização do Servidor (Porta " + porta + ")",
                "Falha crítica ao iniciar o servidor: " + e.getMessage(), e);
        }
    }

    private boolean tratarCors(HttpExchange exchange) throws IOException {
        // set (e não add): criarRota e os handlers chamam este método; add duplicava
        // o cabeçalho ("*, *") e o navegador bloqueava requisições de outra origem.
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    /** Parâmetros já lidos (e reescritos) pelo porteiro autorizarPedido; o corpo só pode ser lido uma vez. */
    Map<String, String> lerQueryOuForm(HttpExchange exchange) throws IOException {
        Map<String, String> lidos = paramsDoPedido.get(exchange);
        return lidos != null ? lidos : lerParametrosDoPedido(exchange);
    }

    private Map<String, String> lerParametrosDoPedido(HttpExchange exchange) throws IOException {
        Map<String, String> map = new HashMap<>();
        String query = exchange.getRequestURI().getQuery();
        if (query != null && !query.isBlank()) {
            parsearQueryString(query, map);
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (!body.isEmpty()) {
            if (body.startsWith("{") && body.endsWith("}")) {
                parsearJsonSimples(body, map);
            } else {
                parsearQueryString(body, map);
            }
        }
        return map;
    }

    private void parsearJsonSimples(String json, Map<String, String> destino) {
        String conteudo = json.trim();
        if (conteudo.startsWith("{") && conteudo.endsWith("}")) {
            conteudo = conteudo.substring(1, conteudo.length() - 1).trim();
        }
        boolean emAspas = false;
        StringBuilder sbChave = new StringBuilder();
        StringBuilder sbValor = new StringBuilder();
        boolean lendoValor = false;

        for (int i = 0; i < conteudo.length(); i++) {
            char c = conteudo.charAt(i);
            if (c == '\\' && i + 1 < conteudo.length()) {
                char prox = conteudo.charAt(i + 1);
                if (prox == '"' || prox == '\\' || prox == '/') {
                    (lendoValor ? sbValor : sbChave).append(prox);
                    i++;
                    continue;
                }
            }
            if (c == '"') {
                emAspas = !emAspas;
                continue;
            }
            if (!emAspas && c == ':') {
                lendoValor = true;
                continue;
            }
            if (!emAspas && c == ',') {
                String k = sbChave.toString().trim();
                String v = sbValor.toString().trim();
                if (!k.isEmpty()) {
                    destino.put(k, v);
                }
                sbChave.setLength(0);
                sbValor.setLength(0);
                lendoValor = false;
                continue;
            }
            (lendoValor ? sbValor : sbChave).append(c);
        }
        String k = sbChave.toString().trim();
        String v = sbValor.toString().trim();
        if (!k.isEmpty()) {
            destino.put(k, v);
        }
    }

    private void parsearQueryString(String dados, Map<String, String> destino) {
        for (String par : dados.split("&")) {
            if (par.isBlank()) continue;
            String[] kv = par.split("=", 2);
            String k = decodificarUrlSeguro(kv[0]);
            String v = kv.length > 1 ? decodificarUrlSeguro(kv[1]) : "";
            destino.put(k.trim(), v.trim());
        }
    }

    private static String decodificarUrlSeguro(String s) {
        try {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    void enviarJson(HttpExchange exchange, int status, String json) throws IOException {
        if (status >= 400) {
            totalFalhas.incrementAndGet();
            String metodo = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            String endpoint = metodo + " " + path + (query != null ? "?" + query : "");
            String motivo = "HTTP " + status + " - " + extrairMensagemErro(json);
            registrarErro(endpoint, motivo, null);
        }
        enviarJsonDireto(exchange, status, json);
    }

    private void enviarJsonDireto(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String extrairMensagemErro(String json) {
        if (json == null || json.isBlank()) return "Falha na requisição";
        int idx = json.indexOf("\"erro\":\"");
        if (idx != -1) {
            int start = idx + 8;
            int end = json.indexOf("\"", start);
            if (end != -1) return json.substring(start, end);
        }
        idx = json.indexOf("\"erro\": \"");
        if (idx != -1) {
            int start = idx + 9;
            int end = json.indexOf("\"", start);
            if (end != -1) return json.substring(start, end);
        }
        return json.length() > 100 ? json.substring(0, 100) + "..." : json;
    }

    public static synchronized void registrarErro(String onde, String motivo, Throwable causa) {
        long total = totalOperacoes.get();
        long falhas = totalFalhas.get();
        long sucessos = Math.max(0, total - falhas);
        double taxaFalhas = total > 0 ? ((double) falhas / total) * 100.0 : 0.0;
        String agora = LocalDateTime.now().format(FORMATO_DATA_HORA);

        StringBuilder sb = new StringBuilder();
        sb.append("\n================================================================================\n");
        sb.append("                         [REGISTRO DE ERRO NO SERVIDOR]                         \n");
        sb.append("================================================================================\n");
        sb.append(String.format("📅 Data / Hora          : %s\n", agora));
        sb.append(String.format("📍 Onde falhou          : %s\n", onde != null ? onde : "Indeterminado"));
        sb.append(String.format("⚠️ Motivo / Detalhes    : %s\n", motivo != null ? motivo : "Falha na operação"));

        if (causa != null) {
            sb.append(String.format("💥 Tipo da Exceção      : %s\n", causa.getClass().getName()));
            if (causa.getMessage() != null && !causa.getMessage().isBlank()) {
                sb.append(String.format("💬 Mensagem             : %s\n", causa.getMessage()));
            }
            StackTraceElement frameApp = null;
            for (StackTraceElement elem : causa.getStackTrace()) {
                String className = elem.getClassName();
                if (className.startsWith("servidor.") || className.startsWith("persistencia.")
                        || className.startsWith("personagem.") || className.startsWith("economia.")
                        || className.startsWith("geografia.") || className.startsWith("menu.")) {
                    frameApp = elem;
                    break;
                }
            }
            if (frameApp != null) {
                sb.append(String.format("🔍 Ponto no Código      : %s.%s(%s:%d)\n",
                        frameApp.getClassName(), frameApp.getMethodName(),
                        frameApp.getFileName(), frameApp.getLineNumber()));
            }
        }

        sb.append("--------------------------------------------------------------------------------\n");
        sb.append("📊 ESTATÍSTICAS OPERACIONAIS:\n");
        sb.append(String.format("   • Total de Operações : %d\n", total));
        sb.append(String.format("   • Operações com Êxito: %d\n", sucessos));
        sb.append(String.format("   • Falhas Registradas : %d (%.2f%% do total)\n", falhas, taxaFalhas));
        sb.append("================================================================================\n");

        System.err.println(sb.toString());
    }

    public static long getTotalOperacoes() {
        return totalOperacoes.get();
    }

    public static long getTotalFalhas() {
        return totalFalhas.get();
    }

    public static void resetarMetricas() {
        totalOperacoes.set(0);
        totalFalhas.set(0);
    }

    /** Estado completo da criação de ficha (usado ao iniciar, retomar e rolar atributos). */
    private String sessaoCriacaoJson(int usuarioId, SessaoCriacaoFicha s, boolean retomada) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ativa", true);
        m.put("retomada", retomada);
        m.put("usuarioId", usuarioId);
        m.put("etapa", s.getEtapaAtual().name());
        m.put("nomePersonagem", s.getNomePersonagem());
        List<Map<String, Object>> racas = new ArrayList<>();
        int indiceEscolhido = -1;
        List<Raca> opcoes = s.getOpcoesRacaSorteadas();
        for (int i = 0; i < opcoes.size(); i++) {
            Raca r = opcoes.get(i);
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("id", r.getId());
            rm.put("nome", r.getNome());
            rm.put("reino", r.getReinoOrigem());
            rm.put("raridade", r.getRaridade().name());
            racas.add(rm);
            if (s.getRacaEscolhida() != null && r.getNome().equalsIgnoreCase(s.getRacaEscolhida().getNome())) {
                indiceEscolhido = i;
            }
        }
        m.put("opcoesRacas", racas);
        m.put("racaEscolhida", s.getRacaEscolhida() != null ? s.getRacaEscolhida().getId() : null);
        m.put("racaEscolhidaNome", s.getRacaEscolhida() != null ? s.getRacaEscolhida().getNome() : null);
        m.put("indiceRacaEscolhida", indiceEscolhido);
        Map<String, Object> attrs = new LinkedHashMap<>();
        for (Map.Entry<Atributo, Integer> e : s.getAtributosRolados().entrySet()) attrs.put(e.getKey().name(), e.getValue());
        m.put("atributosRolados", attrs);
        m.put("rerolagensPermitidas", s.getRerolagensPermitidas());
        m.put("rerolagensRestantes", s.getRerolagensRestantes());
        m.put("dicaMestre", s.getUltimaDicaMestre());
        m.put("aprovadaPeloMestre", s.isAprovadaPeloMestre());
        m.put("impulsoDraconicoLiberado", s.isImpulsoDraconicoLiberado());
        return AdminCrud.json(m);
    }

    /**
     * Campos extras da ficha como fragmento JSON (sem chaves externas):
     * cidade atual, história, condições e magias.
     */
    private String extrasFichaJson(Ficha f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cidadeAtual", f.getCidadeAtual() != null ? f.getCidadeAtual().getNome() : null);
        m.put("cidadeAtualId", f.getCidadeAtual() != null ? f.getCidadeAtual().getId() : null);
        m.put("historia", fichaExtrasDAO.lerHistoria(f.getId()));
        m.put("condicoes", fichaExtrasDAO.listarCondicoes(f.getId()));
        m.put("magias", fichaExtrasDAO.listarMagias(f.getId()));
        String objeto = AdminCrud.json(m);
        return objeto.substring(1, objeto.length() - 1);
    }

    private static long lerFichaId(Map<String, String> params) {
        try {
            return Long.parseLong(params.getOrDefault("fichaId", "0").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Long lerIdOpcional(String bruto) {
        if (bruto == null || bruto.isBlank()) return null;
        try {
            return Long.valueOf(bruto.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Relógio do mercado, guardado à parte do dia da campanha (podem divergir após simulações). */
    private void salvarRelogioDoMercado() {
        permissoesDAO.salvarValor("MERCADO", "dia", String.valueOf(motorEconomico.getDiaAtual()));
        permissoesDAO.salvarValor("MERCADO", "hora", String.valueOf(motorEconomico.getHoraAtual()));
    }

    private int inteiroSalvo(String chave, int padrao) {
        try {
            return Integer.parseInt(permissoesDAO.obterValor("MERCADO", chave, String.valueOf(padrao)).trim());
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    /** Recarrega cidades, rotas e lojas do banco após alterações feitas no painel. */
    synchronized void recarregarMundo() {
        MotorEconomico anterior = this.motorEconomico;
        MotorEconomico novo = economiaDAO.carregarMotorEconomicoCompleto();
        if (anterior != null) {
            novo.setDiaAtual(anterior.getDiaAtual());
            novo.setHoraAtual(anterior.getHoraAtual());
            novo.setDiaCampanha(anterior.getDiaCampanha());
        }
        this.motorEconomico = novo;
    }

    /** Quantidade física de cada moeda, ex.: {"LATAO":0,...,"OURO":10,...}. */
    private String moedasJson(Carteira carteira) {
        StringBuilder sb = new StringBuilder("{");
        for (Carteira.Moeda m : Carteira.Moeda.values()) {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"").append(m.name()).append("\":").append(carteira != null ? carteira.getQuantidade(m) : 0);
        }
        return sb.append("}").toString();
    }

    /** Lido direto do banco para incluir a ilustração do item, que a classe Item não carrega. */
    private String inventarioJson(Ficha f) {
        return AdminCrud.json(fichaExtrasDAO.listarInventario(f.getId()));
    }

    /** Escapa texto para uso dentro de uma string JSON (aspas, barras e quebras de linha). */
    private String escapar(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
