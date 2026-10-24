package persistencia;

import economia.Item;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object (DAO) para itens do catálogo canônico no SQLite.
 */
public class ItemDAO {

    public void salvar(Item item) {
        String sql = """
            INSERT INTO itens (id, nome, descricao, preco_latao, peso_kg, tipo, raridade, tier)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                nome = excluded.nome,
                descricao = excluded.descricao,
                preco_latao = excluded.preco_latao,
                peso_kg = excluded.peso_kg,
                tipo = excluded.tipo,
                raridade = excluded.raridade,
                tier = excluded.tier;
        """;

        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, item.getId());
            ps.setString(2, item.getNome());
            ps.setString(3, item.getDescricao());
            ps.setLong(4, item.getPrecoEmLatao());
            ps.setDouble(5, item.getPeso());
            ps.setString(6, item.getTipo().name());
            ps.setString(7, item.getRaridade().name());
            ps.setInt(8, 1);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar item no SQLite: " + item.getNome(), e);
        }
    }

    public Item buscarPorId(int id) {
        String sql = "SELECT * FROM itens WHERE id = ?";
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return converterResultSetParaItem(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar item no SQLite: " + id, e);
        }
        return null;
    }

    public List<Item> listarTodos() {
        List<Item> itens = new ArrayList<>();
        String sql = "SELECT * FROM itens ORDER BY id ASC";
        try (Connection conn = BancoDados.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                itens.add(converterResultSetParaItem(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar itens do SQLite", e);
        }
        return itens;
    }

    public void popularItensPadraoSeVazio() {
        boolean vazio = false;
        String sql = "SELECT COUNT(*) FROM itens";
        try (Connection conn = BancoDados.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next() && rs.getInt(1) == 0) {
                vazio = true;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao verificar catálogo de itens no SQLite", e);
        }

        if (vazio) {
            // Catálogo Canônico de Thaluria
            salvar(new Item(1, "Lingote de Ferro", "Metal denso refinado", 2.5, 3.0, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 1));
            salvar(new Item(2, "Espada Longa de Ferro", "Lâmina afiada para soldados", 15.0, 2.5, Item.TipoItem.ARMA, Item.RaridadeItem.COMUM, 1));
            salvar(new Item(3, "Poção de Cura", "Elixir restaurador de vitalidade", 5.0, 0.5, Item.TipoItem.CONSUMIVEL, Item.RaridadeItem.COMUM, 1));
            salvar(new Item(4, "Manto de Seda", "Vestimenta nobre de Valdorian", 12.0, 1.0, Item.TipoItem.ARMADURA, Item.RaridadeItem.INCOMUM, 1));
            salvar(new Item(5, "Gema Polida das Areias", "Pedra preciosa do deserto", 50.0, 0.2, Item.TipoItem.ACESSORIO, Item.RaridadeItem.RARO, 1));
            salvar(new Item(6, "Madeira Nobre da Taiga", "Troncos densos ideais para arcos e navios", 4.0, 5.0, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 1));
            salvar(new Item(7, "Ração de Viagem Élfica", "Pão de viagem nutritivo e conservas", 1.5, 0.4, Item.TipoItem.CONSUMIVEL, Item.RaridadeItem.COMUM, 1));
            salvar(new Item(8, "Hidromel das Montanhas", "Bebida forte fermentada em barris de carvalho", 2.0, 1.0, Item.TipoItem.CONSUMIVEL, Item.RaridadeItem.COMUM, 1));
        }
    }

    private Item converterResultSetParaItem(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String nome = rs.getString("nome");
        String descricao = rs.getString("descricao");
        long precoLatao = rs.getLong("preco_latao");
        double peso = rs.getDouble("peso_kg");
        Item.TipoItem tipo = Item.TipoItem.valueOf(rs.getString("tipo"));
        Item.RaridadeItem raridade = Item.RaridadeItem.valueOf(rs.getString("raridade"));
        double precoPO = precoLatao / 10000.0;
        return new Item(id, nome, descricao, precoPO, peso, tipo, raridade, 1);
    }
}
