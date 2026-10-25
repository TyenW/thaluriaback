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
    private final SessaoLoginDAO sessaoDAO = new SessaoLoginDAO();

    /** Rotas abertas a quem ainda não entrou. */
    private static final Set<String> ROTAS_PUBLICAS = Set.of("/api/login", "/api/logout");
    private static final String ATRIBUTO_PARAMS = "thaluria.params";
    private static final String ATRIBUTO_SESSAO = "thaluria.sessao";

    /** Tentativas de login falhas por conta: bloqueia após muitas falhas seguidas. */
    private static final int LOGIN_FALHAS_MAX = 8;
    private static final long LOGIN_JANELA_MS = 15 * 60 * 1000;
    private final Map<String, long[]> falhasLogin = new ConcurrentHashMap<>();

    private final ControlePermissoesMestre controleMestre;
    // volatile: o painel de gerenciamento recarrega o mundo após editar cidades, rotas ou lojas.
    private volatile MotorEconomico motorEconomico;
    private final Map<Integer, SessaoCriacaoFicha> sessoesEmMemoria = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface RotaHandler {
        void handle(HttpExchange exchange) throws Exception;
    }

    private void criarRota(String path, RotaHandler handler) {
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
        configurarRotas();
    }

    /**
     * Confere o token e as permissões antes de qualquer rota. O papel vem do banco,
     * nunca do navegador; parâmetros como usuarioId e papel são reescritos para o
     * jogador não agir em nome de outro. Devolve false se já respondeu com erro.
     */
    private boolean autorizarPedido(HttpExchange exchange) throws IOException {
        Map<String, String> params = lerParametrosDoPedido(exchange);
        exchange.setAttribute(ATRIBUTO_PARAMS, params);
        String rota = exchange.getRequestURI().getPath();
        if (ROTAS_PUBLICAS.contains(rota)) return true;

        SessaoLoginDAO.Sessao sessao = sessaoDAO.validar(tokenDoPedido(exchange));
        if (sessao == null) {
            enviarJson(exchange, 401, "{\"erro\":\"Sessão expirada ou inválida. Entre novamente.\"}");
            return false;
        }
        exchange.setAttribute(ATRIBUTO_SESSAO, sessao);
        // O que cada um enxerga depende do papel gravado no banco, não do que o navegador diz.
        if ("/api/conteudo/visivel".equals(rota) || "/api/fichas".equals(rota)) params.put("papel", sessao.papel());
        if (sessao.ehMestre()) return true;

        boolean soMestre = rota.startsWith("/api/admin/") || rota.startsWith("/api/mestre/") || rota.startsWith("/api/fichas/inventario/");
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
            String id = "/api/fichas/atualizar".equals(rota) || "/api/fichas/excluir".equals(rota)
                    ? params.getOrDefault("id", "") : params.getOrDefault("fichaId", "");
            return exigirDonoDaFicha(exchange, sessao, id);
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
        // Se o jogador já tem uma criação em andamento, ela é retomada: recarregar a
        // página não pode gerar um novo sorteio nem novas rerolagens.
        criarRota("/api/criacao/iniciar", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            int usuarioId = Integer.parseInt(params.getOrDefault("usuarioId", "2"));
            String username = params.getOrDefault("username", "jogador1");
            String nomePersonagem = params.getOrDefault("nomePersonagem", "Novo Aventureiro");

            SessaoCriacaoFicha existente = sessoesEmMemoria.get(usuarioId);
            if (existente != null && existente.getEtapaAtual() != SessaoCriacaoFicha.EtapaCriacao.FINALIZADO) {
                enviarJson(exchange, 200, sessaoCriacaoJson(usuarioId, existente, true));
                return;
            }

            UsuarioDAO.ContaUsuario conta = usuarioDAO.buscarPorUsername(username);
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
            SessaoCriacaoFicha sessao = sessoesEmMemoria.get(usuarioId);
            if (sessao == null || sessao.getEtapaAtual() == SessaoCriacaoFicha.EtapaCriacao.FINALIZADO) {
                enviarJson(exchange, 200, "{\"ativa\":false}");
                return;
            }
            enviarJson(exchange, 200, sessaoCriacaoJson(usuarioId, sessao, true));
        });

        // 3. Jogador Escolhe Raça e Rola Atributos dentro de [min, max] (máx. 2 rerolagens)
        criarRota("/api/criacao/escolher-raca", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            int usuarioId = Integer.parseInt(params.getOrDefault("usuarioId", "2"));
            int indiceOpcao = Integer.parseInt(params.getOrDefault("indiceOpcao", "0"));
            String linhagem = params.getOrDefault("linhagemDraconica", null);

            SessaoCriacaoFicha sessao = sessoesEmMemoria.get(usuarioId);
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
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < sessoes.size(); i++) {
                if (i > 0) sb.append(",");
                UsuarioDAO.EstadoSessaoCriacaoDTO s = sessoes.get(i);
                sb.append(String.format(Locale.US,
                    "{\"usuarioId\":%d,\"personagem\":\"%s\",\"etapa\":\"%s\",\"opcoes\":\"%s\",\"racaEscolhida\":\"%s\",\"dicaMestre\":\"%s\",\"aprovada\":%b}",
                    s.usuarioId, escapar(s.nomePersonagemRascunho), s.etapaAtual,
                    String.join(",", s.opcoesRacasSorteadas),
                    s.racaEscolhida != null ? s.racaEscolhida : "",
                    s.dicaMestre != null ? escapar(s.dicaMestre) : "",
                    s.aprovadaPeloMestre
                ));
            }
            sb.append("]");
            enviarJson(exchange, 200, sb.toString());
        });

        // 5. Painel do Mestre: Intervir na Sessão do Jogador (Dica, Reroll, Impulso Dracónico, Aprovar)
        criarRota("/api/mestre/assistir-sessao", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            int usuarioId = Integer.parseInt(params.getOrDefault("usuarioId", "2"));
            String acao = params.getOrDefault("acao", "DICA");
            String textoDica = params.getOrDefault("dica", "");

            SessaoCriacaoFicha sessao = sessoesEmMemoria.get(usuarioId);
            if (sessao == null) {
                enviarJson(exchange, 404, "{\"erro\":\"Sessão ativa não encontrada em memória\"}");
                return;
            }

            switch (acao.toUpperCase()) {
                case "DICA" -> sessao.enviarDicaDoMestre(textoDica);
                case "REROLL" -> sessao.concederRerollAssistidoPeloMestre(1);
                case "IMPULSO_DRACONICO" -> sessao.liberarImpulsoDraconico();
                case "APROVAR" -> sessao.aprovarFichaPeloMestre();
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
                            + "\"chefe\":%b,\"domesticavel\":%b,\"imagemUrl\":%s,\"descricao\":%s,\"golpesPokemon\":%s}",
                        m.id, escapar(m.nome), m.regiaoNativa.name(), m.elementoPrimario, AdminCrud.json(m.elementoSecundario), m.porte,
                        m.chefeSolo, m.domesticavel, AdminCrud.json(m.imagemUrl), AdminCrud.json(m.descricao), golpesJson
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
                "{\"diaMercado\":%d,\"capitulosWiki\":%s,\"cidades\":%s,\"rotas\":%s,\"bestiario\":%s}",
                controleMestre.getDiaAtualCampanha(), wikiJson, cidadesJson, rotasJson, bestiarioJson
            ));
        });

        // 7. Mestre: Desbloquear Capítulo da Wiki, Monstro do Bestiário ou Avançar Dia de Mercado
        criarRota("/api/mestre/controle-mundo", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            String acao = params.getOrDefault("acao", "");

            if ("AVANCAR_DIA".equalsIgnoreCase(acao)) {
                int dias = Integer.parseInt(params.getOrDefault("dias", "1"));
                controleMestre.avancarDiasDeMercado(dias, new Random());
                motorEconomico.avancarTempo(24 * dias);
                economiaDAO.salvarEstadoCompleto(motorEconomico);
            } else if ("LIBERAR_WIKI".equalsIgnoreCase(acao)) {
                controleMestre.liberarCapituloWiki(params.getOrDefault("capitulo", ""));
            } else if ("REVELAR_MONSTRO".equalsIgnoreCase(acao)) {
                controleMestre.revelarMonstroBestiario(params.getOrDefault("monstroId", ""));
            } else if ("VISIBILIDADE_CIDADE".equalsIgnoreCase(acao)) {
                controleMestre.definirVisibilidadeCidade(
                    params.getOrDefault("cidade", ""),
                    ControlePermissoesMestre.NivelVisibilidadeMapa.valueOf(params.getOrDefault("nivel", "ROTA_E_NOME_APENAS"))
                );
            }
            permissoesDAO.salvar(controleMestre);
            enviarJson(exchange, 200, "{\"status\":\"OK\",\"diaAtual\":" + controleMestre.getDiaAtualCampanha() + "}");
        });

        // 8. Finalizar Criação de Ficha e Persistir no SQLite (FichaDAO)
        criarRota("/api/criacao/finalizar", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            int usuarioId = Integer.parseInt(params.getOrDefault("usuarioId", "2"));

            SessaoCriacaoFicha sessao = sessoesEmMemoria.get(usuarioId);
            if (sessao == null) {
                enviarJson(exchange, 400, "{\"erro\":\"Nenhuma sessão de criação ativa para este usuário\"}");
                return;
            }
            Ficha novaFicha = sessao.finalizarFicha();
            novaFicha.setUsuarioId(usuarioId);
            fichaDAO.salvar(novaFicha);
            sessoesEmMemoria.remove(usuarioId);

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
    @SuppressWarnings("unchecked")
    private Map<String, String> lerQueryOuForm(HttpExchange exchange) throws IOException {
        Object lidos = exchange.getAttribute(ATRIBUTO_PARAMS);
        return lidos != null ? (Map<String, String>) lidos : lerParametrosDoPedido(exchange);
    }

    private Map<String, String> lerParametrosDoPedido(HttpExchange exchange) throws IOException {
        Map<String, String> map = new HashMap<>();
        String query = exchange.getRequestURI().getQuery();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String combinado = (query != null ? query : "") + "&" + body;
        for (String par : combinado.split("&")) {
            if (par.isBlank()) continue;
            String[] kv = par.split("=", 2);
            String k = java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String v = kv.length > 1 ? java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(k.trim(), v.trim());
        }
        return map;
    }

    private void enviarJson(HttpExchange exchange, int status, String json) throws IOException {
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
            if (r.equals(s.getRacaEscolhida())) indiceEscolhido = i;
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

    /** Recarrega cidades, rotas e lojas do banco após alterações feitas no painel. */
    private synchronized void recarregarMundo() {
        MotorEconomico anterior = this.motorEconomico;
        MotorEconomico novo = economiaDAO.carregarMotorEconomicoCompleto();
        if (anterior != null) {
            novo.setDiaAtual(anterior.getDiaAtual());
            novo.setHoraAtual(anterior.getHoraAtual());
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
