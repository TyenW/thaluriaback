package persistencia;

import economia.SnapshotMercado;

import java.sql.*;
import java.util.*;

/**
 * Fotos diárias do mercado (mercado_snapshot). Guardadas desde o dia da mesa ativa mais
 * atrasada; o resto é apagado a cada avanço do tempo (docs/Sistema de Economia/05, seção 4).
 */
public class SnapshotMercadoDAO {

    /** Grava (ou regrava) a foto de um dia numa transação só. */
    public void gravar(List<SnapshotMercado.Linha> linhas) {
        if (linhas.isEmpty()) return;
        String sql = """
            INSERT OR REPLACE INTO mercado_snapshot (dia, loja_id, item_id, quantidade, preco_venda_latao, preco_compra_latao, demanda)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = BancoDados.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (SnapshotMercado.Linha l : linhas) {
                    ps.setInt(1, l.dia());
                    ps.setInt(2, l.lojaId());
                    ps.setInt(3, l.itemId());
                    ps.setInt(4, l.quantidade());
                    ps.setLong(5, l.precoVendaLatao());
                    ps.setLong(6, l.precoCompraLatao());
                    ps.setInt(7, l.demanda());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao gravar o snapshot do mercado", e);
        }
    }

    /** Mercadorias de uma loja no dia (itemId -> linha). Vazio se o dia não foi fotografado. */
    public Map<Integer, SnapshotMercado.Linha> lerLoja(int dia, int lojaId) {
        Map<Integer, SnapshotMercado.Linha> mapa = new HashMap<>();
        String sql = "SELECT item_id, quantidade, preco_venda_latao, preco_compra_latao, demanda FROM mercado_snapshot WHERE dia = ? AND loja_id = ?";
        try (Connection conn = BancoDados.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dia);
            ps.setInt(2, lojaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mapa.put(rs.getInt(1), new SnapshotMercado.Linha(dia, lojaId, rs.getInt(1), rs.getInt(2), rs.getLong(3), rs.getLong(4), rs.getInt(5)));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao ler o snapshot do dia " + dia, e);
        }
        return mapa;
    }

    /** Unidades em estoque de cada loja no dia (lojaId -> unidades), para o resumo das lojas. */
    public Map<Integer, long[]> resumirDia(int dia) {
        Map<Integer, long[]> mapa = new HashMap<>();
        String sql = "SELECT loja_id, SUM(quantidade), SUM(quantidade * preco_venda_latao) FROM mercado_snapshot WHERE dia = ? GROUP BY loja_id";
        try (Connection conn = BancoDados.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dia);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) mapa.put(rs.getInt(1), new long[] { rs.getLong(2), rs.getLong(3) });
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao resumir o snapshot do dia " + dia, e);
        }
        return mapa;
    }

    public boolean temDia(int dia) {
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM mercado_snapshot WHERE dia = ? LIMIT 1")) {
            ps.setInt(1, dia);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao consultar o snapshot do dia " + dia, e);
        }
    }

    /** Apaga as fotos anteriores ao dia (retenção). @return linhas apagadas */
    public int apagarAntesDe(int dia) {
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM mercado_snapshot WHERE dia < ?")) {
            ps.setInt(1, dia);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao apagar snapshots antigos", e);
        }
    }

    /** Primeiro e último dia guardados ({0, 0} se não há nenhum). */
    public int[] intervalo() {
        try (Connection conn = BancoDados.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT MIN(dia), MAX(dia) FROM mercado_snapshot")) {
            return rs.next() ? new int[] { rs.getInt(1), rs.getInt(2) } : new int[] { 0, 0 };
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao consultar os snapshots guardados", e);
        }
    }
}
