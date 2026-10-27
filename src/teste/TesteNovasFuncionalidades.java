package teste;

import personagem.SessaoCriacaoFicha;
import persistencia.UsuarioDAO;
import servidor.ServidorThaluriaAPI;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class TesteNovasFuncionalidades {
    private static void af(boolean cond, String msg) {
        if (!cond) throw new AssertionError("FALHA: " + msg);
        System.out.println(" [OK] " + msg);
    }

    private static String token = "";

    private static String req(String metodo, String urlStr, String jsonBody) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        c.setRequestMethod(metodo);
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        if (token != null && !token.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (jsonBody != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = c.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
        if (is == null) return "CODE:" + code;
        try (Scanner s = new Scanner(is, StandardCharsets.UTF_8.name())) {
            s.useDelimiter("\\A");
            return s.hasNext() ? s.next() : "";
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("--- Teste de Novas Funcionalidades: Reroll Raças, Comentários, Renomear, Reiniciar e Persistência ---");
        
        // Inicia servidor em porta alternativa 8089 para teste limpo
        ServidorThaluriaAPI srv = new ServidorThaluriaAPI(8089);
        srv.iniciar();
        Thread.sleep(600);

        try {
            // Login mestre para obter token
            String rLogin = req("POST", "http://localhost:8089/api/login", "username=mestre&senha=Senha14933");
            af(rLogin.contains("\"token\":\""), "Login mestre realizado com sucesso");
            int start = rLogin.indexOf("\"token\":\"") + 9;
            token = rLogin.substring(start, rLogin.indexOf("\"", start));

            int uid = 2; // Jogador 1
            
            // 1. Inicia criação de ficha
            String r1 = req("POST", "http://localhost:8089/api/criacao/iniciar", 
                "{\"usuarioId\":2,\"username\":\"jogador1\",\"nomePersonagem\":\"Herói Teste\"}");
            af(r1.contains("\"ativa\":true"), "Sessão iniciada com sucesso");
            af(r1.contains("Humano"), "Vaga 1 é Humano");

            // 2. Mestre envia comentário com caracteres especiais e acentos
            String rDica = req("POST", "http://localhost:8089/api/mestre/assistir-sessao",
                "{\"usuarioId\":2,\"acao\":\"DICA\",\"dica\":\"Atenção: 100% de bônus & fogo sagrado!\"}");
            af(rDica.contains("\"status\":\"OK\""), "Mestre enviou dica com caracteres especiais sem travar");

            // 3. Verifica se a dica e os dados aparecem em GET /api/criacao/sessao
            String rSessao = req("GET", "http://localhost:8089/api/criacao/sessao?usuarioId=2", null);
            af(rSessao.contains("100% de bônus"), "Dica do mestre refletida na sessão do jogador");

            // 4. Mestre executa REROLL_RACA (roletagem de novas raças)
            String rRerollRaca = req("POST", "http://localhost:8089/api/mestre/assistir-sessao",
                "{\"usuarioId\":2,\"acao\":\"REROLL_RACA\"}");
            af(rRerollRaca.contains("\"status\":\"OK\""), "Mestre executou REROLL_RACA com sucesso");

            // 5. Verifica se as raças foram sorteadas de novo
            String rSessaoAposReroll = req("GET", "http://localhost:8089/api/criacao/sessao?usuarioId=2", null);
            af(rSessaoAposReroll.contains("opcoesRacas"), "Novas raças presentes na sessão");
            af(rSessaoAposReroll.contains("nova roletagem de raças"), "Notificação do sorteio de raças registrada");

            // 6. Atualizar nome do herói em andamento
            String rNome = req("POST", "http://localhost:8089/api/criacao/atualizar-nome",
                "{\"usuarioId\":2,\"nomePersonagem\":\"Vaelin das Sombras\"}");
            af(rNome.contains("Vaelin das Sombras"), "Nome atualizado com sucesso no rascunho");

            // 7. Simula reinício de servidor (limpa memória) e testa restauração do SQLite
            UsuarioDAO dao = new UsuarioDAO();
            SessaoCriacaoFicha restaurada = dao.carregarSessaoCriacao(2);
            af(restaurada != null, "Sessão restaurada com sucesso do SQLite após suposta queda de memória");
            af("Vaelin das Sombras".equals(restaurada.getNomePersonagem()), "Nome 'Vaelin das Sombras' persistido no SQLite");

            // 8. Teste de reiniciar criação
            String rReiniciar = req("POST", "http://localhost:8089/api/criacao/reiniciar",
                "{\"usuarioId\":2}");
            af(rReiniciar.contains("\"status\":\"OK\""), "Rascunho reiniciado com sucesso");

            String rSessaoVazia = req("GET", "http://localhost:8089/api/criacao/sessao?usuarioId=2", null);
            af(rSessaoVazia.contains("\"ativa\":false"), "Sessão agora retorna ativa: false após reiniciar");

            System.out.println("\n TODOS OS TESTES ESPECÍFICOS PASSARAM COM SUCESSO!");
        } finally {
            srv.parar();
        }
    }
}
