package persistencia;

import java.sql.*;
import java.util.*;

/**
 * História, condições e magias da ficha. Ficam fora da classe Ficha porque são
 * registros livres do jogador/Mestre, sem regra de cálculo no motor.
 */
public class FichaExtrasDAO {

    public static final String[] CAMPOS_HISTORIA = { "historia", "aparencia", "personalidade", "anotacoes" };
    public static final int TEXTO_HISTORIA_MAX = 20000;
    public static final List<String> TIPOS_CONDICAO = List.of("NEGATIVA", "POSITIVA", "NEUTRA");

    public static class ErroValidacao extends RuntimeException {
        public ErroValidacao(String m) { super(m); }
    }

    // ------------------------------------------------------------------
    // História
    // ------------------------------------------------------------------

    public Map<String, String> lerHistoria(long fichaId) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String c : CAMPOS_HISTORIA) m.put(c, "");
        String sql = "SELECT historia, aparencia, personalidade, anotacoes FROM fichas WHERE id = ?";
        try (Connection conn = BancoDados.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, fichaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    for (String c : CAMPOS_HISTORIA) {
                        String v = rs.getString(c);
                        m.put(c, v != null ? v : "");
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao ler história da ficha " + fichaId, e);
        }
        return m;
    }

    /** Atualiza apenas os campos presentes em {@code valores}. */
    public void salvarHistoria(long fichaId, Map<String, String> valores) {
        List<String> colunas = new ArrayList<>();
        List<String> args = new ArrayList<>();
        for (String c : CAMPOS_HISTORIA) {
            if (!valores.containsKey(c)) continue;
            String v = valores.get(c) == null ? "" : valores.get(c);
            if (v.length() > TEXTO_HISTORIA_MAX) throw new ErroValidacao("O campo " + c + " passa de " + TEXTO_HISTORIA_MAX + " caracteres.");
            colunas.add(c + " = ?");
            args.add(v);
        }
        if (colunas.isEmpty()) return;
        String sql = "UPDATE fichas SET " + String.join(", ", colunas) + " WHERE id = ?";
        try (Connection conn = BancoDados.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (String a : args) ps.setString(i++, a);
            ps.setLong(i, fichaId);
            if (ps.executeUpdate() == 0) throw new ErroValidacao("Ficha não encontrada.");
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar história da ficha " + fichaId, e);
        }
    }

    // ------------------------------------------------------------------
    // Condições
    // ------------------------------------------------------------------

    public List<Map<String, Object>> listarCondicoes(long fichaId) {
        return listar("SELECT id, nome, tipo, intensidade, duracao_turnos, descricao FROM ficha_condicoes WHERE ficha_id = ? ORDER BY id", fichaId);
    }

    public long salvarCondicao(long fichaId, Long id, String nome, String tipo, int intensidade, Integer duracao, String descricao) {
        nome = obrigatorio(nome, "O nome da condição", 80);
        if (!TIPOS_CONDICAO.contains(tipo)) throw new ErroValidacao("Tipo de condição inválido.");
        if (intensidade < 1 || intensidade > 10) throw new ErroValidacao("A intensidade vai de 1 a 10.");
        if (duracao != null && (duracao < 1 || duracao > 9999)) throw new ErroValidacao("A duração vai de 1 a 9999 turnos (ou vazia = indefinida).");
        descricao = limitar(descricao, 1000, "A descrição");
        try (Connection conn = BancoDados.getConnection()) {
            if (id != null) {
                exigirDono(conn, "ficha_condicoes", id, fichaId);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE ficha_condicoes SET nome = ?, tipo = ?, intensidade = ?, duracao_turnos = ?, descricao = ? WHERE id = ?")) {
                    ps.setString(1, nome); ps.setString(2, tipo); ps.setInt(3, intensidade);
                    ps.setObject(4, duracao); ps.setString(5, descricao); ps.setLong(6, id);
                    ps.executeUpdate();
                }
                return id;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ficha_condicoes (ficha_id, nome, tipo, intensidade, duracao_turnos, descricao) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, fichaId); ps.setString(2, nome); ps.setString(3, tipo);
                ps.setInt(4, intensidade); ps.setObject(5, duracao); ps.setString(6, descricao);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) { return rs.next() ? rs.getLong(1) : 0; }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar condição", e);
        }
    }

    /** Desconta 1 turno de cada condição temporária e remove as que terminaram. */
    public int avancarTurno(long fichaId) {
        try (Connection conn = BancoDados.getConnection()) {
            int encerradas;
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM ficha_condicoes WHERE ficha_id = ? AND duracao_turnos IS NOT NULL AND duracao_turnos <= 1")) {
                ps.setLong(1, fichaId);
                encerradas = ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ficha_condicoes SET duracao_turnos = duracao_turnos - 1 WHERE ficha_id = ? AND duracao_turnos IS NOT NULL")) {
                ps.setLong(1, fichaId);
                ps.executeUpdate();
            }
            return encerradas;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao avançar turno das condições", e);
        }
    }

    // ------------------------------------------------------------------
    // Magias
    // ------------------------------------------------------------------

    public List<Map<String, Object>> listarMagias(long fichaId) {
        return listar("SELECT id, nome, escola, elemento, circulo, custo_mana, custo_energia, alcance_m, dado, "
                + "tempo_conjuracao, duracao, descricao, preparada FROM ficha_magias WHERE ficha_id = ? ORDER BY circulo, nome COLLATE NOCASE", fichaId);
    }

    public long salvarMagia(long fichaId, Long id, Map<String, String> p) {
        String nome = obrigatorio(p.get("nome"), "O nome da magia", 80);
        String escola = limitar(p.get("escola"), 60, "A escola");
        String elemento = limitar(p.get("elemento"), 40, "O elemento");
        int circulo = inteiro(p.get("circulo"), 1, 0, 10, "O círculo");
        int custoMana = inteiro(p.get("custoMana"), 0, 0, 100000, "O custo de mana");
        int custoEnergia = inteiro(p.get("custoEnergia"), 0, 0, 100000, "O custo de energia");
        double alcance;
        try {
            alcance = p.get("alcanceM") == null || p.get("alcanceM").isBlank() ? 0 : Double.parseDouble(p.get("alcanceM").replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new ErroValidacao("O alcance deve ser um número.");
        }
        if (alcance < 0 || alcance > 100000) throw new ErroValidacao("O alcance deve estar entre 0 e 100000 m.");
        String dado = limitar(p.get("dado"), 40, "O dado");
        if (dado != null && !dado.isBlank() && !dado.matches("(?i)\\s*\\d{0,3}d\\d{1,3}(\\s*[+-]\\s*\\d{1,4})?\\s*")) {
            throw new ErroValidacao("Dado inválido. Use o formato 2d6, 1d20+3…");
        }
        String tempo = limitar(p.get("tempoConjuracao"), 60, "O tempo de conjuração");
        String duracao = limitar(p.get("duracao"), 60, "A duração");
        String descricao = limitar(p.get("descricao"), 3000, "A descrição");
        boolean preparada = !"false".equalsIgnoreCase(p.getOrDefault("preparada", "true"));

        String colunas = "nome = ?, escola = ?, elemento = ?, circulo = ?, custo_mana = ?, custo_energia = ?, alcance_m = ?, "
                + "dado = ?, tempo_conjuracao = ?, duracao = ?, descricao = ?, preparada = ?";
        Object[] args = { nome, escola, elemento, circulo, custoMana, custoEnergia, alcance, dado, tempo, duracao, descricao, preparada ? 1 : 0 };
        try (Connection conn = BancoDados.getConnection()) {
            if (id != null) {
                exigirDono(conn, "ficha_magias", id, fichaId);
                try (PreparedStatement ps = conn.prepareStatement("UPDATE ficha_magias SET " + colunas + " WHERE id = ?")) {
                    for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
                    ps.setLong(args.length + 1, id);
                    ps.executeUpdate();
                }
                return id;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ficha_magias (ficha_id, nome, escola, elemento, circulo, custo_mana, custo_energia, alcance_m, dado, "
                            + "tempo_conjuracao, duracao, descricao, preparada) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, fichaId);
                for (int i = 0; i < args.length; i++) ps.setObject(i + 2, args[i]);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) { return rs.next() ? rs.getLong(1) : 0; }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar magia", e);
        }
    }

    // ------------------------------------------------------------------
    // Comuns
    // ------------------------------------------------------------------

    /** Remove um registro de {@code tabela} (ficha_condicoes ou ficha_magias) da ficha informada. */
    public boolean remover(String tabela, long fichaId, long id) {
        if (!"ficha_condicoes".equals(tabela) && !"ficha_magias".equals(tabela)) throw new IllegalArgumentException(tabela);
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM " + tabela + " WHERE id = ? AND ficha_id = ?")) {
            ps.setLong(1, id);
            ps.setLong(2, fichaId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao remover registro", e);
        }
    }

    // ------------------------------------------------------------------
    // Inventário (itens concedidos pelo Mestre ou obtidos em jogo)
    // ------------------------------------------------------------------

    public static final int QUANTIDADE_ITEM_MAX = 99999;

    public List<Map<String, Object>> listarInventario(long fichaId) {
        return listar("""
            SELECT i.id, i.nome, i.descricao, i.preco_latao, i.peso_kg, i.tipo, i.raridade, i.imagem_url, fi.quantidade
            FROM ficha_inventario fi
            JOIN itens i ON i.id = fi.item_id
            WHERE fi.ficha_id = ?
            ORDER BY i.nome COLLATE NOCASE
            """, fichaId);
    }

    /** Soma {@code quantidade} unidades do item ao inventário da ficha. */
    public void concederItem(long fichaId, long itemId, int quantidade) {
        if (quantidade < 1 || quantidade > QUANTIDADE_ITEM_MAX) {
            throw new ErroValidacao("A quantidade vai de 1 a " + QUANTIDADE_ITEM_MAX + ".");
        }
        try (Connection conn = BancoDados.getConnection()) {
            exigirExistente(conn, "SELECT 1 FROM fichas WHERE id = ?", fichaId, "Ficha não encontrada.");
            exigirExistente(conn, "SELECT 1 FROM itens WHERE id = ?", itemId, "Item não encontrado no catálogo.");
            String sql = """
                INSERT INTO ficha_inventario (ficha_id, item_id, quantidade) VALUES (?, ?, ?)
                ON CONFLICT(ficha_id, item_id) DO UPDATE SET quantidade = MIN(?, quantidade + excluded.quantidade)
                """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, fichaId);
                ps.setLong(2, itemId);
                ps.setInt(3, quantidade);
                ps.setInt(4, QUANTIDADE_ITEM_MAX);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao conceder item à ficha " + fichaId, e);
        }
    }

    /** Retira unidades do item; {@code quantidade} nula remove a pilha inteira. */
    public void retirarItem(long fichaId, long itemId, Integer quantidade) {
        if (quantidade != null && quantidade < 1) throw new ErroValidacao("A quantidade a retirar deve ser positiva.");
        try (Connection conn = BancoDados.getConnection()) {
            if (quantidade != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE ficha_inventario SET quantidade = quantidade - ? WHERE ficha_id = ? AND item_id = ?")) {
                    ps.setInt(1, quantidade);
                    ps.setLong(2, fichaId);
                    ps.setLong(3, itemId);
                    if (ps.executeUpdate() == 0) throw new ErroValidacao("O personagem não possui esse item.");
                }
            }
            String sqlLimpeza = quantidade == null
                ? "DELETE FROM ficha_inventario WHERE ficha_id = ? AND item_id = ?"
                : "DELETE FROM ficha_inventario WHERE ficha_id = ? AND item_id = ? AND quantidade <= 0";
            try (PreparedStatement ps = conn.prepareStatement(sqlLimpeza)) {
                ps.setLong(1, fichaId);
                ps.setLong(2, itemId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao retirar item da ficha " + fichaId, e);
        }
    }

    private static void exigirExistente(Connection conn, String sql, long id, String mensagem) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new ErroValidacao(mensagem);
            }
        }
    }

    /** Chamado ao excluir a ficha (as FKs do SQLite não estão com cascata ativa). */
    public void excluirDaFicha(Connection conn, long fichaId) throws SQLException {
        for (String t : new String[] { "ficha_condicoes", "ficha_magias" }) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + t + " WHERE ficha_id = ?")) {
                ps.setLong(1, fichaId);
                ps.executeUpdate();
            }
        }
    }

    private void exigirDono(Connection conn, String tabela, long id, long fichaId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT ficha_id FROM " + tabela + " WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getLong(1) != fichaId) throw new ErroValidacao("Registro não encontrado nesta ficha.");
            }
        }
    }

    private List<Map<String, Object>> listar(String sql, long fichaId) {
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection conn = BancoDados.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, fichaId);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        m.put(camel(md.getColumnLabel(i)), rs.getObject(i));
                    }
                    if (m.containsKey("preparada")) m.put("preparada", Integer.valueOf(1).equals(m.get("preparada")));
                    lista.add(m);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar registros da ficha", e);
        }
        return lista;
    }

    private static String camel(String coluna) {
        StringBuilder sb = new StringBuilder();
        boolean maiuscula = false;
        for (char c : coluna.toCharArray()) {
            if (c == '_') { maiuscula = true; continue; }
            sb.append(maiuscula ? Character.toUpperCase(c) : c);
            maiuscula = false;
        }
        return sb.toString();
    }

    private static String obrigatorio(String v, String rotulo, int max) {
        if (v == null || v.isBlank()) throw new ErroValidacao(rotulo + " é obrigatório.");
        return limitar(v, max, rotulo);
    }

    private static String limitar(String v, int max, String rotulo) {
        if (v == null) return null;
        v = v.trim();
        if (v.length() > max) throw new ErroValidacao(rotulo + " pode ter no máximo " + max + " caracteres.");
        return v;
    }

    private static int inteiro(String v, int padrao, int min, int max, String rotulo) {
        if (v == null || v.isBlank()) return padrao;
        int n;
        try {
            n = Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new ErroValidacao(rotulo + " deve ser um número inteiro.");
        }
        if (n < min || n > max) throw new ErroValidacao(rotulo + " deve estar entre " + min + " e " + max + ".");
        return n;
    }
}
