package teste;

import persistencia.AdminCrud;
import persistencia.BancoDados;
import persistencia.OrigemItensDAO;
import personagem.ControlePermissoesMestre;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

/**
 * Origem dos itens (coletado, dropado ou craftado), drops ligados ao catálogo,
 * nome da rede só em lojas de rede e o desrevelar de monstros do bestiário.
 *
 * Usa um banco próprio (thaluria_teste_origem.db), apagado no início e no fim.
 */
public class TesteOrigemItens {

    private static final String BANCO_TESTE = "thaluria_teste_origem.db";
    private static int total = 0;
    private static int passaram = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("=== ORIGEM DOS ITENS, REDES E BESTIÁRIO ===\n");
        new File(BANCO_TESTE).delete();
        BancoDados.setUrlBanco("jdbc:sqlite:" + BANCO_TESTE);
        try {
            BancoDados.inicializarEsquema();
            testarOrigens();
            testarRedeDaLoja();
        } finally {
            BancoDados.setUrlBanco("jdbc:sqlite:thaluria.db");
            new File(BANCO_TESTE).delete();
        }
        testarDesrevelarMonstro();

        System.out.printf("%n%d/%d testes passaram.%n", passaram, total);
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> item(Map<String, Object> auditoria, String nome) {
        for (Map<String, Object> m : (List<Map<String, Object>>) auditoria.get("itens")) {
            if (nome.equals(m.get("nome"))) return m;
        }
        return null;
    }

    private static void sql(String... comandos) throws Exception {
        try (Connection c = BancoDados.getConnection(); Statement st = c.createStatement()) {
            for (String s : comandos) st.executeUpdate(s);
        }
    }

    @SuppressWarnings("unchecked")
    private static void testarOrigens() throws Exception {
        System.out.println("-- Origem dos itens");
        AdminCrud crud = new AdminCrud();
        sql("DELETE FROM monstro_drops", "DELETE FROM receita_insumos", "DELETE FROM receitas", "DELETE FROM item_locais_coleta", "DELETE FROM itens",
            "INSERT INTO itens (id, nome, preco_latao, peso_kg, tipo, raridade, tier) VALUES (1, 'Minério de Ferro', 10, 1, 'MATERIAL', 'COMUM', 1)",
            "INSERT INTO itens (id, nome, preco_latao, peso_kg, tipo, raridade, tier) VALUES (2, 'Presa de Lobo', 30, 0.2, 'MATERIAL', 'COMUM', 1)",
            "INSERT INTO itens (id, nome, preco_latao, peso_kg, tipo, raridade, tier) VALUES (3, 'Espada Curta', 300, 1, 'ARMA_BRANCA', 'COMUM', 1)",
            "INSERT INTO itens (id, nome, preco_latao, peso_kg, tipo, raridade, tier) VALUES (4, 'Pedra Esquecida', 1, 1, 'OUTRO', 'COMUM', 1)",
            "INSERT OR REPLACE INTO monstros_catalogo (id, nome, regiao_nativa, geologia_nativa, aquifero_nativo, elemento_primario, vig_base, for_base, des_base, men_base, esp_base, von_base) "
                + "VALUES ('LOBO_T', 'Lobo de Teste', 'TEMPERADO', 'PLANICIE', 'RIO', 'NEUTRO', 10, 10, 10, 10, 10, 10)",
            // Drop antigo, só pelo nome: deve ser ligado ao item
            "INSERT INTO monstro_drops (monstro_catalogo_id, item_nome, probabilidade) VALUES ('LOBO_T', 'presa de lobo', 0.5)",
            // Drop de um item que não existe no catálogo
            "INSERT INTO monstro_drops (monstro_catalogo_id, item_nome, probabilidade) VALUES ('LOBO_T', 'Olho Perdido', 0.1)");

        // Receita pelo CRUD do Mestre: a espada passa a ser craftada
        Map<String, String> receita = new HashMap<>(Map.of("entidade", "receitas", "nome", "Forjar espada curta", "item_resultado_id", "3",
                "quantidade_resultado", "1", "grau_minimo", "0", "tempo_horas", "8"));
        crud.salvar(crud.obter("receitas"), receita);

        // Minério: coletável, mas ainda sem local
        sql("UPDATE itens SET coletavel = 1 WHERE id = 1");
        Map<String, Object> a = new OrigemItensDAO().auditar();
        Map<String, Object> minerio = item(a, "Minério de Ferro");
        afirme(((List<?>) minerio.get("origens")).isEmpty() && !((List<?>) minerio.get("avisos")).isEmpty(),
                "Coletável sem local de coleta não conta como origem e gera aviso");
        afirme(((List<String>) item(a, "Presa de Lobo").get("origens")).contains("DROPADO"), "Drop cadastrado só pelo nome é ligado ao item (sem diferenciar maiúsculas)");
        afirme(((List<String>) item(a, "Espada Curta").get("origens")).contains("CRAFTADO"), "Item produzido por receita é craftado");
        afirme(((List<?>) item(a, "Pedra Esquecida").get("origens")).isEmpty(), "Item sem coleta, drop ou receita fica sem origem");
        afirme(((List<?>) a.get("dropsOrfaos")).size() == 1, "Drop de item fora do catálogo aparece como órfão");

        // Local de coleta pelo CRUD (filho do item)
        crud.salvar(crud.obter("item_locais_coleta"), new HashMap<>(Map.of("item_id", "1", "regiao", "TEMPERADO", "probabilidade", "0.4", "qtd_min", "1", "qtd_max", "3")));
        a = new OrigemItensDAO().auditar();
        afirme(((List<String>) item(a, "Minério de Ferro").get("origens")).contains("COLETADO"), "Coletável com local de coleta é coletado");
        afirme(((Number) a.get("semOrigem")).intValue() == 1, "Só a Pedra Esquecida continua sem origem");

        try {
            crud.salvar(crud.obter("item_locais_coleta"), new HashMap<>(Map.of("item_id", "1", "probabilidade", "0.4", "qtd_min", "5", "qtd_max", "2")));
            afirme(false, "Local de coleta com mínimo maior que o máximo é recusado");
        } catch (AdminCrud.ErroValidacao e) {
            afirme(true, "Local de coleta com mínimo maior que o máximo é recusado");
        }

        // Drop novo pelo CRUD: escolhe o item e grava também o nome legado
        AdminCrud.Salvo drop = crud.salvar(crud.obter("monstro_drops"), new HashMap<>(Map.of("monstro_catalogo_id", "LOBO_T", "item_id", "4",
                "probabilidade", "0.3", "qtd_min", "1", "qtd_max", "1", "raridade", "COMUM", "qualidade_material", "1")));
        try (Connection c = BancoDados.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT item_nome FROM monstro_drops WHERE id = " + drop.id)) {
            afirme(rs.next() && "Pedra Esquecida".equals(rs.getString(1)), "Drop salvo pelo Mestre guarda o nome do item escolhido");
        }
        afirme(((Number) new OrigemItensDAO().auditar().get("semOrigem")).intValue() == 0, "Com o drop, nenhum item fica sem origem");

        try {
            crud.excluir(crud.obter("itens"), "4");
            afirme(false, "Item usado como drop não pode ser excluído");
        } catch (AdminCrud.ErroValidacao e) {
            afirme(e.getMessage().contains("drop"), "Item usado como drop não pode ser excluído");
        }
    }

    private static void testarRedeDaLoja() throws Exception {
        System.out.println("-- Nome da rede");
        AdminCrud crud = new AdminCrud();
        sql("INSERT OR IGNORE INTO cidades (id, nome, tipo, regiao, geologia, aquifero) VALUES (900, 'Vila Teste', 'VILA', 'TEMPERADO', 'PLANICIE', 'RIO')");
        Map<String, String> loja = new HashMap<>(Map.of("nome", "Forja Solitária", "mercador", "Brom", "cidade_id", "900", "tipo", "FORJA",
                "caixa_latao", "1000", "nivel", "3", "e_rede", "false", "nome_rede", "Rede Fantasma"));
        AdminCrud.Salvo s = crud.salvar(crud.obter("lojas"), loja);
        try (Connection c = BancoDados.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT nome_rede FROM lojas WHERE id = " + s.id)) {
            afirme(rs.next() && rs.getString(1) == null, "Loja fora de rede não guarda nome de rede");
        }
        loja.put("e_rede", "true");
        loja.put("nome_rede", "");
        try {
            crud.salvar(crud.obter("lojas"), loja);
            afirme(false, "Loja de rede sem nome da rede é recusada");
        } catch (AdminCrud.ErroValidacao e) {
            afirme(true, "Loja de rede sem nome da rede é recusada");
        }
    }

    private static void testarDesrevelarMonstro() {
        System.out.println("-- Bestiário");
        ControlePermissoesMestre c = new ControlePermissoesMestre();
        c.revelarMonstroBestiario("lobo_t");
        afirme(c.isMonstroRevelado("LOBO_T"), "Monstro revelado aparece aos jogadores");
        c.ocultarMonstroBestiario("Lobo_T");
        afirme(!c.isMonstroRevelado("LOBO_T"), "Monstro desrevelado volta a ficar oculto");
    }
}
