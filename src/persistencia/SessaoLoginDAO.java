package persistencia;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Sessões de login por token. O token só é conhecido pelo navegador;
 * o banco guarda o SHA-256 dele, então um vazamento do thaluria.db não abre sessões.
 * O papel é lido da tabela de usuários a cada pedido: rebaixar ou excluir uma conta vale na hora.
 */
public class SessaoLoginDAO {

    public static final long VALIDADE_MS = 30L * 24 * 60 * 60 * 1000;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** Quem está fazendo o pedido. */
    public record Sessao(int usuarioId, String username, String papel) {
        public boolean ehMestre() { return "MESTRE".equalsIgnoreCase(papel); }
    }

    /** Cria uma sessão e devolve o token (mostrado uma única vez ao navegador). */
    public String abrir(int usuarioId) {
        byte[] bruto = new byte[32];
        ALEATORIO.nextBytes(bruto);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bruto);
        long agora = System.currentTimeMillis();
        try (Connection conn = BancoDados.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM sessoes_login WHERE expira_em < ?")) {
                ps.setLong(1, agora);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO sessoes_login (token_hash, usuario_id, criada_em, expira_em) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, hash(token));
                ps.setInt(2, usuarioId);
                ps.setLong(3, agora);
                ps.setLong(4, agora + VALIDADE_MS);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao abrir sessão: " + e.getMessage(), e);
        }
        return token;
    }

    /** Sessão válida do token, ou null se ausente, expirado ou de conta excluída. */
    public Sessao validar(String token) {
        if (token == null || token.isBlank() || token.length() > 200) return null;
        String sql = """
            SELECT u.id, u.username, u.papel
            FROM sessoes_login s JOIN usuarios u ON u.id = s.usuario_id
            WHERE s.token_hash = ? AND s.expira_em > ?
            """;
        try (Connection conn = BancoDados.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash(token));
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Sessao(rs.getInt(1), rs.getString(2), rs.getString(3)) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao validar sessão: " + e.getMessage(), e);
        }
    }

    public void encerrar(String token) {
        if (token == null || token.isBlank()) return;
        executar("DELETE FROM sessoes_login WHERE token_hash = ?", hash(token));
    }

    /** Derruba todas as sessões da conta (ex.: após troca de senha). */
    public void encerrarDoUsuario(int usuarioId) {
        executar("DELETE FROM sessoes_login WHERE usuario_id = ?", usuarioId);
    }

    /** Dono da ficha, ou null se a ficha não existir. */
    public Integer donoDaFicha(long fichaId) {
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT usuario_id FROM fichas WHERE id = ?")) {
            ps.setLong(1, fichaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao consultar dono da ficha: " + e.getMessage(), e);
        }
    }

    private static void executar(String sql, Object arg) {
        try (Connection conn = BancoDados.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, arg);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao encerrar sessão: " + e.getMessage(), e);
        }
    }

    private static String hash(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
