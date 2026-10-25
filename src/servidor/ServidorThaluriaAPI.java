package servidor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import economia.MotorEconomico;
import menu.CenarioInicial;
import persistencia.*;
import personagem.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP nativo (JDK 21) para o ecossistema Web de Thaluria RPG.
 * Conecta o Front-end (Jogador e Mestre) ao motor Java e ao banco SQLite (thaluria.db).
 */
public class ServidorThaluriaAPI {

    private final HttpServer server;
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private final FichaDAO fichaDAO = new FichaDAO();
    private final MonstroDAO monstroDAO = new MonstroDAO();
    private final PermissoesMestreDAO permissoesDAO = new PermissoesMestreDAO();
    private final EconomiaDAO economiaDAO = new EconomiaDAO();

    private final ControlePermissoesMestre controleMestre;
    private final MotorEconomico motorEconomico;
    private final Map<Integer, SessaoCriacaoFicha> sessoesEmMemoria = new ConcurrentHashMap<>();

    public ServidorThaluriaAPI(int porta) throws IOException {
        BancoDados.inicializarEsquema();
        usuarioDAO.criarContasPadraoSeVazio();
        monstroDAO.popularBestiarioCanonicaSeVazio();

        CenarioInicial.DadosCenario cenario = CenarioInicial.criarCenarioPadrao();
        this.motorEconomico = cenario.getMotor();
        this.controleMestre = permissoesDAO.carregar(this.motorEconomico);

        this.server = HttpServer.create(new InetSocketAddress(porta), 0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        configurarRotas();
    }

    private void configurarRotas() {
        // 1. Autenticação (Jogador e Mestre)
        server.createContext("/api/login", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            String user = params.getOrDefault("username", "");
            String pass = params.getOrDefault("senha", "");

            UsuarioDAO.ContaUsuario conta = usuarioDAO.autenticar(user, pass);
            if (conta == null) {
                enviarJson(exchange, 401, "{\"erro\":\"Credenciais inválidas\"}");
                return;
            }
            String json = String.format(Locale.US,
                "{\"id\":%d,\"username\":\"%s\",\"papel\":\"%s\",\"limiteFichas\":%d,\"pity\":%d}",
                conta.getId(), escapar(conta.getUsername()), conta.getPapel(),
                conta.getLimiteMaximoFichas(), conta.getFatorSortePity()
            );
            enviarJson(exchange, 200, json);
        });

        // 2. Iniciar Criação de Ficha (Sorteia Humano + 2 Raças com Draconato < 1%)
        server.createContext("/api/criacao/iniciar", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            int usuarioId = Integer.parseInt(params.getOrDefault("usuarioId", "2"));
            String username = params.getOrDefault("username", "jogador1");
            String nomePersonagem = params.getOrDefault("nomePersonagem", "Novo Aventureiro");

            UsuarioDAO.ContaUsuario conta = usuarioDAO.buscarPorUsername(username);
            int pityAtual = (conta != null) ? conta.getFatorSortePity() : 0;

            SessaoCriacaoFicha sessao = new SessaoCriacaoFicha(
                    username, nomePersonagem, conta != null ? conta.getLimiteMaximoFichas() : 2, 0
            );
            sessao.iniciarEExecutarSorteioRacas(pityAtual, new Random());
            sessoesEmMemoria.put(usuarioId, sessao);
            usuarioDAO.salvarSessaoCriacaoEmTempoReal(usuarioId, sessao, null);
            usuarioDAO.atualizarFatorPity(usuarioId, pityAtual + 1);

            StringBuilder racasJson = new StringBuilder("[");
            List<Raca> sorteadas = sessao.getOpcoesRacaSorteadas();
            for (int i = 0; i < sorteadas.size(); i++) {
                if (i > 0) racasJson.append(",");
                Raca r = sorteadas.get(i);
                racasJson.append(String.format("{\"id\":\"%s\",\"nome\":\"%s\",\"reino\":\"%s\",\"raridade\":\"%s\"}",
                        r.getId(), escapar(r.getNome()), escapar(r.getReinoOrigem()), r.getRaridade().name()));
            }
            racasJson.append("]");

            enviarJson(exchange, 200, String.format(
                    "{\"usuarioId\":%d,\"etapa\":\"%s\",\"opcoesRacas\":%s}",
                    usuarioId, sessao.getEtapaAtual().name(), racasJson
            ));
        });

        // 3. Jogador Escolhe Raça e Rola Atributos dentro de [min, max]
        server.createContext("/api/criacao/escolher-raca", exchange -> {
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
            sessao.escolherRacaERolarAtributos(indiceOpcao, new Random());
            usuarioDAO.salvarSessaoCriacaoEmTempoReal(usuarioId, sessao, linhagem);

            StringBuilder attrJson = new StringBuilder("{");
            int idx = 0;
            for (Map.Entry<Atributo, Integer> e : sessao.getAtributosRolados().entrySet()) {
                if (idx++ > 0) attrJson.append(",");
                attrJson.append("\"").append(e.getKey().name()).append("\":").append(e.getValue());
            }
            attrJson.append("}");

            enviarJson(exchange, 200, String.format(
                    "{\"racaEscolhida\":\"%s\",\"etapa\":\"%s\",\"atributosRolados\":%s}",
                    sessao.getRacaEscolhida().getId(), sessao.getEtapaAtual().name(), attrJson
            ));
        });

        // 4. Painel do Mestre: Monitorização em Tempo Real de Todas as Fichas em Criação
        server.createContext("/api/mestre/sessoes-ao-vivo", exchange -> {
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
        server.createContext("/api/mestre/assistir-sessao", exchange -> {
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
        server.createContext("/api/conteudo/visivel", exchange -> {
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
                        "{\"id\":\"%s\",\"revelado\":true,\"nome\":\"%s\",\"regiao\":\"%s\",\"elemento\":\"%s\",\"porte\":\"%s\",\"golpesPokemon\":%s}",
                        m.id, escapar(m.nome), m.regiaoNativa.name(), m.elementoPrimario, m.porte, golpesJson
                    ));
                }
            }
            // Cidades e Rotas filtradas pelo Fog-of-War do Mestre
            StringBuilder cidadesJson = new StringBuilder("[");
            List<geografia.Cidade> cidades = motorEconomico.getMapa().getCidades();
            for (int i = 0; i < cidades.size(); i++) {
                if (i > 0) cidadesJson.append(",");
                geografia.Cidade c = cidades.get(i);
                ControlePermissoesMestre.NivelVisibilidadeMapa vis = visaoMestre
                    ? ControlePermissoesMestre.NivelVisibilidadeMapa.INFORMACOES_COMPLETAS
                    : controleMestre.getVisibilidadeCidade(c.getNome());

                if (vis == ControlePermissoesMestre.NivelVisibilidadeMapa.OCULTO) {
                    cidadesJson.append(String.format("{\"nome\":\"???\",\"visibilidade\":\"OCULTO\"}"));
                } else if (vis == ControlePermissoesMestre.NivelVisibilidadeMapa.ROTA_E_NOME_APENAS) {
                    cidadesJson.append(String.format("{\"nome\":\"%s\",\"visibilidade\":\"ROTA_E_NOME_APENAS\"}", escapar(c.getNome())));
                } else {
                    cidadesJson.append(String.format(
                        "{\"nome\":\"%s\",\"visibilidade\":\"INFORMACOES_COMPLETAS\",\"visibilidadeJogador\":\"%s\",\"regiao\":\"%s\",\"geologia\":\"%s\",\"aquifero\":\"%s\",\"lojas\":%d}",
                        escapar(c.getNome()), controleMestre.getVisibilidadeCidade(c.getNome()).name(),
                        c.getRegiao().name(), c.getGeologia().name(), c.getAquifero().name(), c.getLojas().size()
                    ));
                }
            }
            cidadesJson.append("]");

            // Rotas / Conexões do Grafo
            StringBuilder rotasJson = new StringBuilder("[");
            Set<geografia.Conexao> todasConexoes = new LinkedHashSet<>();
            for (geografia.Cidade c : cidades) {
                todasConexoes.addAll(c.getConexoes());
            }
            int rIdx = 0;
            for (geografia.Conexao rota : todasConexoes) {
                if (rIdx++ > 0) rotasJson.append(",");
                rotasJson.append(String.format(Locale.US,
                    "{\"origem\":\"%s\",\"destino\":\"%s\",\"distanciaKm\":%d,\"perigo\":%d,\"tipo\":\"%s\",\"bloqueada\":%b}",
                    escapar(rota.getOrigem().getNome()), escapar(rota.getDestino().getNome()),
                    rota.getDistanciaKm(), rota.getPerigo(), rota.getTipo().name(), rota.isBloqueada()
                ));
            }
            rotasJson.append("]");

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
        server.createContext("/api/mestre/controle-mundo", exchange -> {
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
        server.createContext("/api/criacao/finalizar", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            int usuarioId = Integer.parseInt(params.getOrDefault("usuarioId", "2"));

            SessaoCriacaoFicha sessao = sessoesEmMemoria.get(usuarioId);
            if (sessao == null) {
                enviarJson(exchange, 400, "{\"erro\":\"Nenhuma sessão de criação ativa para este usuário\"}");
                return;
            }
            Ficha novaFicha = sessao.finalizarFicha();
            fichaDAO.salvar(novaFicha);
            sessoesEmMemoria.remove(usuarioId);

            enviarJson(exchange, 200, String.format(Locale.US,
                "{\"status\":\"CRIADA\",\"id\":%d,\"nome\":\"%s\",\"raca\":\"%s\",\"hpMax\":%d,\"manaMax\":%d,\"energiaMax\":%d,\"sanMax\":%d}",
                novaFicha.getId(),
                escapar(novaFicha.getNome()), escapar(novaFicha.getRaca().getNome()),
                novaFicha.getVidaMaxima(), novaFicha.getManaMaxima(),
                novaFicha.getEnergiaMaxima(), novaFicha.getSanidadeMaxima()
            ));
        });

        // 9. Listar Fichas Criadas (e seus Monstros Domados com 4 Golpes Estilo Pokémon)
        server.createContext("/api/fichas", exchange -> {
            if (tratarCors(exchange)) return;
            List<Ficha> fichas = fichaDAO.listarTodas();
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

                sb.append(String.format(Locale.US,
                    "{\"id\":%d,\"nome\":\"%s\",\"raca\":\"%s\",\"vig\":%d,\"forca\":%d,\"des\":%d,\"men\":%d,\"esp\":%d,\"von\":%d," +
                    "\"hpAtual\":%d,\"hpMax\":%d,\"sanAtual\":%d,\"sanMax\":%d,\"energiaAtual\":%d,\"energiaMax\":%d,\"manaAtual\":%d,\"manaMax\":%d," +
                    "\"defFisica\":%d,\"defOculta\":%d,\"domados\":%s}",
                    f.getId(), escapar(f.getNome()), f.getRaca() != null ? escapar(f.getRaca().getNome()) : "Humano",
                    f.getAtributoBase(Atributo.VIGOR), f.getAtributoBase(Atributo.FORCA), f.getAtributoBase(Atributo.DESTREZA),
                    f.getAtributoBase(Atributo.MENTAL), f.getAtributoBase(Atributo.ESPIRITO), f.getAtributoBase(Atributo.VONTADE),
                    f.getVidaAtual(), f.getVidaMaxima(), f.getSanidadeAtual(), f.getSanidadeMaxima(),
                    f.getEnergiaAtual(), f.getEnergiaMaxima(), f.getManaAtual(), f.getManaMaxima(),
                    f.getDefesaFisica(), f.getDefesaOculta(), domJson
                ));
            }
            sb.append("]");
            enviarJson(exchange, 200, sb.toString());
        });

        // 10. Mestre: Cadastrar Jogador ou Alterar Limite de Fichas
        server.createContext("/api/mestre/usuarios", exchange -> {
            if (tratarCors(exchange)) return;
            Map<String, String> params = lerQueryOuForm(exchange);
            String username = params.getOrDefault("username", "");
            String senha = params.getOrDefault("senha", "123456");
            String papel = params.getOrDefault("papel", "JOGADOR");
            int limite = Integer.parseInt(params.getOrDefault("limiteFichas", "2"));

            UsuarioDAO.ContaUsuario conta = usuarioDAO.cadastrarUsuario(username, senha, papel, limite);
            enviarJson(exchange, 200, String.format(
                "{\"id\":%d,\"username\":\"%s\",\"limiteFichas\":%d}",
                conta.getId(), escapar(conta.getUsername()), conta.getLimiteMaximoFichas()
            ));
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

    public static void main(String[] args) throws IOException {
        int porta = 8080;
        if (args.length > 0) {
            try { porta = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        ServidorThaluriaAPI api = new ServidorThaluriaAPI(porta);
        api.iniciar();
        System.out.println("Servidor Thaluria API iniciado na porta " + porta);
    }

    private boolean tratarCors(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private Map<String, String> lerQueryOuForm(HttpExchange exchange) throws IOException {
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
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String escapar(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
