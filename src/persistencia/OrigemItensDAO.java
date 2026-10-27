package persistencia;

import java.sql.*;
import java.util.*;

/**
 * Auditoria de origem do catálogo: todo item precisa ser coletado, dropado ou craftado.
 * Um item pode ter mais de uma origem. Itens sem nenhuma aparecem para o Mestre resolver,
 * junto com drops cujo nome não corresponde a nenhum item do catálogo.
 */
public class OrigemItensDAO {

    public Map<String, Object> auditar() {
        Map<Long, Map<String, Object>> itens = new LinkedHashMap<>();
        List<Map<String, Object>> dropsOrfaos = new ArrayList<>();
        try (Connection conn = BancoDados.getConnection(); Statement st = conn.createStatement()) {
            BancoDados.vincularDropsPorNome(conn);

            try (ResultSet rs = st.executeQuery("SELECT id, nome, tipo, raridade, imagem_url, coletavel FROM itens ORDER BY nome COLLATE NOCASE")) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("nome", rs.getString("nome"));
                    m.put("tipo", rs.getString("tipo"));
                    m.put("raridade", rs.getString("raridade"));
                    m.put("imagemUrl", rs.getString("imagem_url"));
                    m.put("coletavel", rs.getInt("coletavel") == 1);
                    m.put("locaisColeta", 0);
                    m.put("receitas", new ArrayList<String>());
                    m.put("monstros", new ArrayList<String>());
                    itens.put(rs.getLong("id"), m);
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT item_id, COUNT(*) FROM item_locais_coleta GROUP BY item_id")) {
                while (rs.next()) {
                    Map<String, Object> m = itens.get(rs.getLong(1));
                    if (m != null) m.put("locaisColeta", rs.getInt(2));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT item_resultado_id, nome FROM receitas ORDER BY nome")) {
                while (rs.next()) adicionar(itens.get(rs.getLong(1)), "receitas", rs.getString(2));
            }
            try (ResultSet rs = st.executeQuery("""
                    SELECT d.id, d.item_id, d.item_nome, d.monstro_catalogo_id, m.nome AS monstro
                    FROM monstro_drops d LEFT JOIN monstros_catalogo m ON m.id = d.monstro_catalogo_id
                    ORDER BY m.nome
                    """)) {
                while (rs.next()) {
                    String monstro = rs.getString("monstro") != null ? rs.getString("monstro") : rs.getString("monstro_catalogo_id");
                    long itemId = rs.getLong("item_id");
                    if (!rs.wasNull() && itens.containsKey(itemId)) {
                        adicionar(itens.get(itemId), "monstros", monstro);
                    } else {
                        Map<String, Object> o = new LinkedHashMap<>();
                        o.put("dropId", rs.getLong("id"));
                        o.put("itemNome", rs.getString("item_nome"));
                        o.put("monstroId", rs.getString("monstro_catalogo_id"));
                        o.put("monstro", monstro);
                        dropsOrfaos.add(o);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao auditar a origem dos itens: " + e.getMessage(), e);
        }

        int semOrigem = 0;
        for (Map<String, Object> m : itens.values()) {
            boolean coletado = (Boolean) m.get("coletavel") && (Integer) m.get("locaisColeta") > 0;
            boolean dropado = !((List<?>) m.get("monstros")).isEmpty();
            boolean craftado = !((List<?>) m.get("receitas")).isEmpty();
            List<String> origens = new ArrayList<>();
            if (coletado) origens.add("COLETADO");
            if (dropado) origens.add("DROPADO");
            if (craftado) origens.add("CRAFTADO");
            m.put("origens", origens);
            List<String> avisos = new ArrayList<>();
            if ((Boolean) m.get("coletavel") && !coletado) avisos.add("Marcado como coletável, mas sem nenhum local de coleta.");
            m.put("avisos", avisos);
            if (origens.isEmpty()) semOrigem++;
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalItens", itens.size());
        r.put("semOrigem", semOrigem);
        r.put("itens", new ArrayList<>(itens.values()));
        r.put("dropsOrfaos", dropsOrfaos);
        return r;
    }

    @SuppressWarnings("unchecked")
    private static void adicionar(Map<String, Object> item, String lista, String valor) {
        if (item == null) return;
        List<String> l = (List<String>) item.get(lista);
        if (!l.contains(valor)) l.add(valor);
    }
}
