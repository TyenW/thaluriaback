package teste;

import menu.CenarioInicial;
import persistencia.*;
import personagem.*;
import servidor.ServidorThaluriaAPI;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Suíte de Testes Automatizada para o Passo 1 e Passo 2:
 * 1. Persistência em SQLite de Usuários, Sessão em Tempo Real, Bestiário Pokémon e Monstros Domados.
 * 2. Servidor API HTTP (JDK 21) com rotas de autenticação, criação de personagem ao vivo,
 *    Fog-of-War e controle do Mestre.
 */
public class TestePasso1E2PersistenciaEServidor {

    private static int assercoesTotal = 0;
    private static int assercoesPassaram = 0;

    private static void afirme(boolean condicao, String mensagem) {
        assercoesTotal++;
        if (condicao) {
            assercoesPassaram++;
            System.out.println(" [OK] " + mensagem);
        } else {
            System.err.println(" [FALHA] " + mensagem);
            throw new AssertionError("Falha na asserção: " + mensagem);
        }
    }

    public static void main(String[] args) {
        System.out.println("======================================================================");
        System.out.println("  INICIANDO SUITE DE TESTES: PASSO 1 & 2 (PERSISTÊNCIA & API HTTP)");
        System.out.println("======================================================================");

        String dbTeste = "thaluria_passo1_2_teste.db";
        File f = new File(dbTeste);
        if (f.exists()) f.delete();

        try {
            BancoDados.setUrlBanco("jdbc:sqlite:" + dbTeste);
            BancoDados.inicializarEsquema();

            testarUsuarioDAO();
            testarMonstroDAO();
            testarServidorThaluriaAPI();

            System.out.println("----------------------------------------------------------------------");
            System.out.printf("RESULTADO: %d/%d asserções passaram com 100%% de sucesso!\n", assercoesPassaram, assercoesTotal);
            System.out.println("PERSISTÊNCIA SQLITE v8 E SERVIDOR HTTP VALIDADO COM SUCESSO ABSOLUTO!");
            System.out.println("======================================================================");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        } finally {
            BancoDados.setUrlBanco("jdbc:sqlite:thaluria.db");
            BancoDados.inicializarEsquema();
            new File(dbTeste).delete();
        }
    }

    private static void testarUsuarioDAO() {
        System.out.println("\n--- 1. Teste de UsuarioDAO: Contas, Pity e Sessão ao Vivo ---");
        UsuarioDAO usuarioDAO = new UsuarioDAO();
        usuarioDAO.criarContasPadraoSeVazio();

        // 1.1 Autenticação Mestre e Jogador
        UsuarioDAO.ContaUsuario mestre = usuarioDAO.autenticar("mestre", "thaluriaadmin");
        afirme(mestre != null, "Conta do mestre autenticada com sucesso.");
        afirme(mestre.isMestre(), "Conta do mestre possui papel MESTRE.");
        afirme(mestre.getLimiteMaximoFichas() == 999, "Limite de fichas do mestre é 999.");

        UsuarioDAO.ContaUsuario jogador1 = usuarioDAO.autenticar("jogador1", "123456");
        afirme(jogador1 != null, "Conta de jogador1 autenticada com sucesso.");
        afirme(!jogador1.isMestre(), "Jogador1 não é mestre.");
        afirme(jogador1.getLimiteMaximoFichas() == 2, "Limite de fichas inicial de jogador1 é 2.");
        afirme(jogador1.getFatorSortePity() == 0, "Pity inicial é 0.");

        // 1.2 Atualização de limites e pity
        usuarioDAO.atualizarLimiteFichas(jogador1.getId(), 3);
        usuarioDAO.atualizarFatorPity(jogador1.getId(), 5);
        UsuarioDAO.ContaUsuario jogadorAtualizado = usuarioDAO.buscarPorUsername("jogador1");
        afirme(jogadorAtualizado.getLimiteMaximoFichas() == 3, "Limite de fichas atualizado para 3.");
        afirme(jogadorAtualizado.getFatorSortePity() == 5, "Fator de pity atualizado para 5.");

        // 1.3 Sessão de Criação em tempo real salva no SQLite
        SessaoCriacaoFicha sessao = new SessaoCriacaoFicha("jogador1", "Kaelen da Neve", 3, 5);
        sessao.iniciarEExecutarSorteioRacas(5, new java.util.Random());
        afirme(sessao.getOpcoesRacaSorteadas().size() == 3, "3 opções de raça sorteadas na sessão.");

        sessao.escolherRacaERolarAtributos(0, new java.util.Random());
        afirme(sessao.getRacaEscolhida() != null, "Raça escolhida com sucesso.");
        afirme(sessao.getAtributosRolados().size() == 6, "6 atributos rolados.");

        usuarioDAO.salvarSessaoCriacaoEmTempoReal(jogador1.getId(), sessao, null);

        List<UsuarioDAO.EstadoSessaoCriacaoDTO> sessoesAtivas = usuarioDAO.listarTodasSessoesAtivasParaMestre();
        afirme(!sessoesAtivas.isEmpty(), "Mestre visualiza lista de sessões ativas.");
        UsuarioDAO.EstadoSessaoCriacaoDTO s0 = sessoesAtivas.get(0);
        afirme(s0.usuarioId == jogador1.getId(), "ID do jogador confere na sessão observada.");
        afirme("Kaelen da Neve".equals(s0.nomePersonagemRascunho), "Nome de rascunho conferido no banco.");
        afirme(s0.atributosRolados.size() == 6, "6 atributos conferidos no espelhamento do Mestre.");
    }

    private static void testarMonstroDAO() {
        System.out.println("\n--- 2. Teste de MonstroDAO: Bestiário Canônico, Moveset e Drops ---");
        MonstroDAO monstroDAO = new MonstroDAO();
        monstroDAO.popularBestiarioCanonicaSeVazio();

        List<MonstroDAO.EspecieMonstroPersistida> catalogo = monstroDAO.listarCatalogoCompleto();
        afirme(catalogo.size() == 3, "3 monstros canônicos iniciais encontrados no catálogo.");

        MonstroDAO.EspecieMonstroPersistida lobo = catalogo.stream()
                .filter(m -> "LOBO_GLACIAL_FROSTDALE".equals(m.id)).findFirst().orElse(null);
        afirme(lobo != null, "Lobo Glacial de Frostdale localizado no catálogo.");
        afirme(lobo.regiaoNativa == geografia.Regiao.TUNDRA, "Região nativa do Lobo é Tundra.");
        afirme("GELO".equals(lobo.elementoPrimario), "Elemento primário do Lobo é Gelo.");
        afirme(lobo.learnset.size() == 4, "Lobo possui 4 golpes no learnset estilo Pokémon.");
        afirme(lobo.drops.size() == 2, "Lobo possui 2 materiais na tabela de drops.");

        MonstroDAO.EspecieMonstroPersistida besouro = catalogo.stream()
                .filter(m -> "BESOURO_HERCULES_IRONHOLD".equals(m.id)).findFirst().orElse(null);
        afirme(besouro != null, "Besouro-Hércules localizado no catálogo.");
        afirme("COLOSSAL".equals(besouro.porte), "Porte do Besouro-Hércules é COLOSSAL (Besta de Carga).");

        MonstroDAO.EspecieMonstroPersistida escorpiao = catalogo.stream()
                .filter(m -> "ESCORPIAO_REI_SUNREACH".equals(m.id)).findFirst().orElse(null);
        afirme(escorpiao != null, "Escorpião-Rei de Cristal localizado no catálogo.");
        afirme(escorpiao.chefeSolo, "Escorpião-Rei marcado como Chefe Solo.");
        afirme(escorpiao.multiplicadorHpChefe == 3, "Escorpião-Rei possui multiplicador de HP x3.");

        // Conversão de GolpePokemonDTO para HabilidadeCombate
        MonstroDAO.GolpePokemonDTO golpePresa = lobo.learnset.get(0);
        HabilidadeCombate hab = golpePresa.converterParaHabilidadeCombate();
        afirme(hab != null, "GolpePokemonDTO convertido para HabilidadeCombate com sucesso.");
        afirme(hab.getNome().equals(golpePresa.nomeGolpe), "Nome da habilidade preservado.");

        // Persistência de Ficha de Monstro Domado vinculada ao Dono
        Ficha dono = new Ficha(14, 12, 14, 10, 16, 12);
        dono.setNome("Treinador de Feras");
        FichaDAO fichaDAO = new FichaDAO();
        fichaDAO.salvar(dono);

        Ficha fichaLobo = new Ficha(12, 14, 16, 8, 12, 10);
        fichaLobo.setNome("Lobo do Treinador");

        int domadoId = monstroDAO.salvarMonstroDomado(
                (int) dono.getId(), lobo.id, "Nevasca", fichaLobo,
                List.of("Presa Congelante", "Arranhão Ártico", "Uivo da Alcateia", "Sopro de Geada"),
                true
        );
        afirme(domadoId > 0, "Monstro domado salvo no SQLite com ID: " + domadoId);

        List<MonstroDAO.FichaMonstroDomadoPersistida> domadosDono = monstroDAO.listarDomadosPorFichaDono((int) dono.getId());
        afirme(domadosDono.size() == 1, "1 monstro domado recuperado para o dono.");
        afirme("Nevasca".equals(domadosDono.get(0).apelido), "Apelido do monstro recuperado: Nevasca.");
        afirme(domadosDono.get(0).quatroSlotsAtaques.size() == 4, "4 golpes ativos recuperados no Moveset Pokémon.");
    }

    private static void testarServidorThaluriaAPI() throws Exception {
        System.out.println("\n--- 3. Teste do ServidorThaluriaAPI HTTP e Rotas ao Vivo ---");
        int porta = 8991;
        ServidorThaluriaAPI servidor = new ServidorThaluriaAPI(porta);
        servidor.iniciar();

        try {
            // 3.1 POST /api/login (Sucesso)
            String loginPayload = "username=mestre&senha=thaluriaadmin";
            HttpResposta respLogin = enviarRequisicao("POST", "http://localhost:" + porta + "/api/login", loginPayload);
            afirme(respLogin.codigo == 200, "POST /api/login mestre respondeu 200 OK.");
            afirme(respLogin.corpo.contains("\"papel\":\"MESTRE\""), "Login retornou papel MESTRE.");

            // 3.2 POST /api/login (Credencial Inválida)
            HttpResposta respLoginInvalido = enviarRequisicao("POST", "http://localhost:" + porta + "/api/login", "username=x&senha=y");
            afirme(respLoginInvalido.codigo == 401, "POST /api/login inválido respondeu 401.");

            // 3.3 POST /api/criacao/iniciar
            String iniciarPayload = "usuarioId=2&username=jogador1&nomePersonagem=AventureiroWeb";
            HttpResposta respIniciar = enviarRequisicao("POST", "http://localhost:" + porta + "/api/criacao/iniciar", iniciarPayload);
            afirme(respIniciar.codigo == 200, "POST /api/criacao/iniciar respondeu 200 OK.");
            afirme(respIniciar.corpo.contains("opcoesRacas"), "Resposta contém opções de raças.");
            afirme(respIniciar.corpo.contains("Humano"), "Vaga 1 é obrigatoriamente Humano.");

            // 3.4 POST /api/criacao/escolher-raca
            String escolherPayload = "usuarioId=2&indiceOpcao=0";
            HttpResposta respEscolher = enviarRequisicao("POST", "http://localhost:" + porta + "/api/criacao/escolher-raca", escolherPayload);
            afirme(respEscolher.codigo == 200, "POST /api/criacao/escolher-raca respondeu 200 OK.");
            afirme(respEscolher.corpo.contains("atributosRolados"), "Resposta contém atributos rolados.");
            afirme(respEscolher.corpo.contains("VIG"), "Atributo VIG presente no JSON.");

            // 3.5 GET /api/mestre/sessoes-ao-vivo
            HttpResposta respSessoesMestre = enviarRequisicao("GET", "http://localhost:" + porta + "/api/mestre/sessoes-ao-vivo", null);
            afirme(respSessoesMestre.codigo == 200, "GET /api/mestre/sessoes-ao-vivo respondeu 200 OK.");
            afirme(respSessoesMestre.corpo.contains("AventureiroWeb"), "Mestre visualiza a ficha em criação ao vivo!");

            // 3.6 POST /api/mestre/assistir-sessao (Dica)
            String dicaPayload = "usuarioId=2&acao=DICA&dica=Foque+em+Vigor+para+resistir+ao+frio";
            HttpResposta respDica = enviarRequisicao("POST", "http://localhost:" + porta + "/api/mestre/assistir-sessao", dicaPayload);
            afirme(respDica.codigo == 200, "POST /api/mestre/assistir-sessao (DICA) respondeu 200 OK.");

            // 3.7 POST /api/mestre/assistir-sessao (Aprovar)
            String aprovarPayload = "usuarioId=2&acao=APROVAR";
            HttpResposta respAprovar = enviarRequisicao("POST", "http://localhost:" + porta + "/api/mestre/assistir-sessao", aprovarPayload);
            afirme(respAprovar.codigo == 200, "POST /api/mestre/assistir-sessao (APROVAR) respondeu 200 OK.");

            // 3.8 GET /api/conteudo/visivel (Visão Jogador: Fog of War ativado)
            HttpResposta respConteudoJogador = enviarRequisicao("GET", "http://localhost:" + porta + "/api/conteudo/visivel?papel=JOGADOR", null);
            afirme(respConteudoJogador.codigo == 200, "GET /api/conteudo/visivel (JOGADOR) respondeu 200 OK.");
            afirme(respConteudoJogador.corpo.contains("???"), "Monstro oculto aparece como '???' para o jogador.");
            afirme(respConteudoJogador.corpo.contains("cidades"), "Resposta contém lista de cidades com Fog-of-War.");
            afirme(respConteudoJogador.corpo.contains("rotas"), "Resposta contém lista de rotas viárias.");
            afirme(respConteudoJogador.corpo.contains("capitulosWiki"), "Resposta contém lista de capítulos liberados da Wiki.");

            // 3.9 POST /api/mestre/controle-mundo (Avançar Dia e Revelar Monstro)
            String revelarPayload = "acao=REVELAR_MONSTRO&monstroId=LOBO_GLACIAL_FROSTDALE";
            HttpResposta respRevelar = enviarRequisicao("POST", "http://localhost:" + porta + "/api/mestre/controle-mundo", revelarPayload);
            afirme(respRevelar.codigo == 200, "POST /api/mestre/controle-mundo (REVELAR_MONSTRO) respondeu 200 OK.");

            HttpResposta respConteudoJogadorApos = enviarRequisicao("GET", "http://localhost:" + porta + "/api/conteudo/visivel?papel=JOGADOR", null);
            afirme(respConteudoJogadorApos.corpo.contains("Lobo Glacial de Frostdale"), "Monstro revelado agora aparece com nome e golpes Pokémon para o jogador!");

            String avancarPayload = "acao=AVANCAR_DIA&dias=2";
            HttpResposta respAvancar = enviarRequisicao("POST", "http://localhost:" + porta + "/api/mestre/controle-mundo", avancarPayload);
            afirme(respAvancar.codigo == 200, "POST /api/mestre/controle-mundo (AVANCAR_DIA) respondeu 200 OK.");
            afirme(respAvancar.corpo.contains("\"diaAtual\":3"), "Dia da campanha avançou para 3.");

            // 3.10 POST /api/criacao/finalizar
            String finalizarPayload = "usuarioId=2";
            HttpResposta respFinalizar = enviarRequisicao("POST", "http://localhost:" + porta + "/api/criacao/finalizar", finalizarPayload);
            afirme(respFinalizar.codigo == 200, "POST /api/criacao/finalizar respondeu 200 OK.");
            afirme(respFinalizar.corpo.contains("\"status\":\"CRIADA\""), "Ficha criada e finalizada com sucesso!");
            afirme(respFinalizar.corpo.contains("hpMax"), "Ficha finalizada retornou HP Máximo.");

            // 3.11 GET /api/fichas
            HttpResposta respFichas = enviarRequisicao("GET", "http://localhost:" + porta + "/api/fichas", null);
            afirme(respFichas.codigo == 200, "GET /api/fichas respondeu 200 OK.");
            afirme(respFichas.corpo.contains("AventureiroWeb"), "Ficha finalizada presente na listagem de fichas.");
            afirme(respFichas.corpo.contains("defFisica"), "Listagem de fichas contém defesa física.");
            afirme(respFichas.corpo.contains("domados"), "Listagem de fichas inclui monstros domados.");

            // 3.12 POST /api/mestre/usuarios
            String novoUserPayload = "username=jogador2&senha=pass123&papel=JOGADOR&limiteFichas=4";
            HttpResposta respNovoUser = enviarRequisicao("POST", "http://localhost:" + porta + "/api/mestre/usuarios", novoUserPayload);
            afirme(respNovoUser.codigo == 200, "POST /api/mestre/usuarios respondeu 200 OK.");
            afirme(respNovoUser.corpo.contains("jogador2"), "Novo usuário cadastrado com sucesso.");
            afirme(respNovoUser.corpo.contains("\"limiteFichas\":4"), "Limite de fichas configurado como 4.");

        } finally {
            servidor.parar();
        }
    }

    private static class HttpResposta {
        int codigo;
        String corpo;
        HttpResposta(int c, String b) { this.codigo = c; this.corpo = b; }
    }

    private static HttpResposta enviarRequisicao(String metodo, String urlStr, String body) throws Exception {
        URI uri = new URI(urlStr);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod(metodo);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        if (body != null && !body.isEmpty()) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }

        int codigo = conn.getResponseCode();
        InputStream is = (codigo >= 200 && codigo < 400) ? conn.getInputStream() : conn.getErrorStream();
        String corpo = "";
        if (is != null) {
            corpo = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        return new HttpResposta(codigo, corpo);
    }
}
