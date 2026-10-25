package teste;

import persistencia.BancoDados;
import persistencia.EconomiaDAO;
import servidor.ServidorThaluriaAPI;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class TesteSincronizacaoAtlas {
    public static void main(String[] args) throws Exception {
        System.out.println("--- Testando Sincronizacao Atlas e visibilidadeJogador ---");

        BancoDados.setUrlBanco("jdbc:sqlite:thaluria.db");
        BancoDados.inicializarEsquema();

        EconomiaDAO economiaDAO = new EconomiaDAO();
        var motor = economiaDAO.carregarMotorEconomicoCompleto();

        int totalCidades = motor.getMapa().getCidades().size();
        System.out.println("Total de cidades carregadas no motor economico: " + totalCidades);
        if (totalCidades < 34) {
            throw new AssertionError("Esperava pelo menos 34 cidades do atlas, mas foram: " + totalCidades);
        }

        // Inicia servidor em porta efemera e testa visibilidadeJogador
        ServidorThaluriaAPI api = new ServidorThaluriaAPI(8089);
        api.iniciar();

        try {
            URI uri = URI.create("http://localhost:8089/api/conteudo/visivel?papel=MESTRE");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.connect();

            int code = conn.getResponseCode();
            System.out.println("GET /api/conteudo/visivel?papel=MESTRE -> HTTP " + code);
            if (code != 200) {
                throw new AssertionError("HTTP status esperado 200, recebido: " + code);
            }

            InputStream is = conn.getInputStream();
            String resposta = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            if (!resposta.contains("\"visibilidadeJogador\":")) {
                throw new AssertionError("Resposta do Mestre nao contem o campo 'visibilidadeJogador'!");
            }
            System.out.println(" [OK] Campo 'visibilidadeJogador' retornado com sucesso!");

            if (!resposta.contains("Frosthelm") || !resposta.contains("Aurelia") || !resposta.contains("Ironhold")) {
                throw new AssertionError("Resposta nao contem todas as cidades esperadas do Atlas!");
            }
            System.out.println(" [OK] Cidades do Atlas e canônicas verificadas na resposta da API!");

            System.out.println("=================================================================");
            System.out.println(" TESTE DE SINCRONIZACAO DO ATLAS E PATCH CONCLUIDO COM SUCESSO! ");
            System.out.println("=================================================================");
        } finally {
            api.parar();
        }
    }
}
