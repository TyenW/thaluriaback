package persistencia;

import economia.Mesa;

import java.sql.*;
import java.util.*;

/**
 * Mesas de jogo e personagens em mesa (docs/Sistema de Economia/05, seção 3).
 * Cada ficha está em no máximo uma mesa e guarda o próprio relógio: o momento mais à
 * frente em que o personagem já esteve.
 */
public class MesaDAO {

    /** Ficha vista pelo controle de mesas: onde está e até quando já viveu. */
    public record FichaNaMesa(long id, String nome, String raca, int usuarioId, Integer mesaId, Integer dia, Integer hora) {
        /** Momento do personagem; sem relógio gravado, o da mesa (ou o começo do mundo). */
        public int momento() {
            return dia != null ? Mesa.momento(dia, hora != null ? hora : 0) : 0;
        }
    }

    public List<Mesa> listar() {
        List<Mesa> lista = new ArrayList<>();
        try (Connection conn = BancoDados.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, nome, dia, hora, status, mestre_id FROM mesas ORDER BY id")) {
            while (rs.next()) lista.add(ler(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar mesas", e);
        }
        return lista;
    }

    public Mesa criar(String nome, int dia, int hora, Integer mestreId) {
        String sql = "INSERT INTO mesas (nome, dia, hora, status, mestre_id) VALUES (?, ?, ?, 'ATIVA', ?)";
        try (Connection conn = BancoDados.getConnection(); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nome.trim());
            ps.setInt(2, Math.max(1, dia));
            ps.setInt(3, Math.max(0, Math.min(23, hora)));
            ps.setObject(4, mestreId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return new Mesa(rs.getInt(1), nome, dia, hora, Mesa.Status.ATIVA, mestreId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao criar mesa", e);
        }
    }

    public void salvar(Mesa m) {
        String sql = "UPDATE mesas SET nome = ?, dia = ?, hora = ?, status = ? WHERE id = ?";
        try (Connection conn = BancoDados.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, m.getNome());
            ps.setInt(2, m.getDia());
            ps.setInt(3, m.getHora());
            ps.setString(4, m.getStatus().name());
            ps.setInt(5, m.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar a mesa " + m.getId(), e);
        }
    }

    /**
     * Migração para o modelo de mesas: cria a mesa "Principal" no dia da campanha e coloca nela
     * todas as fichas. Roda uma vez por banco.
     *
     * @return a mesa criada, ou null se o banco já tinha mesas
     */
    public Mesa migrarMesaPrincipal(int diaCampanha, int hora) {
        if (BancoDados.sementeAplicada("mesa_principal") || !listar().isEmpty()) {
            BancoDados.marcarSemente("mesa_principal");
            return null;
        }
        Mesa principal = criar("Principal", diaCampanha, hora, null);
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE fichas SET mesa_id = ?, dia_personagem = ?, hora_personagem = ? WHERE mesa_id IS NULL")) {
            ps.setInt(1, principal.getId());
            ps.setInt(2, principal.getDia());
            ps.setInt(3, principal.getHora());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao colocar as fichas na mesa Principal", e);
        }
        BancoDados.marcarSemente("mesa_principal");
        return principal;
    }

    public List<FichaNaMesa> listarFichas() {
        List<FichaNaMesa> lista = new ArrayList<>();
        String sql = "SELECT id, nome, raca_nome, usuario_id, mesa_id, dia_personagem, hora_personagem FROM fichas ORDER BY nome COLLATE NOCASE";
        try (Connection conn = BancoDados.getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) lista.add(lerFicha(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar fichas por mesa", e);
        }
        return lista;
    }

    public FichaNaMesa buscarFicha(long fichaId) {
        String sql = "SELECT id, nome, raca_nome, usuario_id, mesa_id, dia_personagem, hora_personagem FROM fichas WHERE id = ?";
        try (Connection conn = BancoDados.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, fichaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? lerFicha(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar a ficha " + fichaId, e);
        }
    }

    /** Coloca a ficha numa mesa (null = fora de jogo) e grava o relógio do personagem. */
    public void moverFicha(long fichaId, Integer mesaId, int dia, int hora) {
        String sql = "UPDATE fichas SET mesa_id = ?, dia_personagem = ?, hora_personagem = ? WHERE id = ?";
        try (Connection conn = BancoDados.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, mesaId);
            ps.setInt(2, dia);
            ps.setInt(3, hora);
            ps.setLong(4, fichaId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao mover a ficha " + fichaId + " de mesa", e);
        }
    }

    /** A mesa andou: o relógio de quem está nela vai junto (nunca para trás). */
    public void acompanharMesa(Mesa m) {
        String sql = """
            UPDATE fichas SET dia_personagem = ?, hora_personagem = ?
            WHERE mesa_id = ? AND (dia_personagem IS NULL OR dia_personagem * 24 + COALESCE(hora_personagem, 0) < ?)
            """;
        try (Connection conn = BancoDados.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, m.getDia());
            ps.setInt(2, m.getHora());
            ps.setInt(3, m.getId());
            ps.setInt(4, m.momento());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao avançar os personagens da mesa " + m.getId(), e);
        }
    }

    public void registrarTroca(FichaNaMesa ficha, Mesa destino, boolean forcada) {
        String sql = """
            INSERT INTO mesa_trocas (ficha_id, mesa_origem_id, mesa_destino_id, forcada, dia_personagem, hora_personagem, dia_mesa, hora_mesa)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = BancoDados.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ficha.id());
            ps.setObject(2, ficha.mesaId());
            ps.setObject(3, destino != null ? destino.getId() : null);
            ps.setInt(4, forcada ? 1 : 0);
            ps.setObject(5, ficha.dia());
            ps.setObject(6, ficha.hora());
            ps.setObject(7, destino != null ? destino.getDia() : null);
            ps.setObject(8, destino != null ? destino.getHora() : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao registrar a troca de mesa da ficha " + ficha.id(), e);
        }
    }

    /** Últimas trocas de mesa, da mais recente para a mais antiga. */
    public List<Map<String, Object>> listarTrocas(int limite) {
        List<Map<String, Object>> lista = new ArrayList<>();
        String sql = """
            SELECT t.id, t.ficha_id, f.nome AS ficha_nome, t.mesa_origem_id, t.mesa_destino_id, t.forcada,
                   t.dia_personagem, t.hora_personagem, t.dia_mesa, t.hora_mesa, t.registrado_em
            FROM mesa_trocas t LEFT JOIN fichas f ON f.id = t.ficha_id
            ORDER BY t.id DESC LIMIT ?
            """;
        try (Connection conn = BancoDados.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, Math.min(200, limite)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("fichaId", rs.getLong("ficha_id"));
                    m.put("fichaNome", rs.getString("ficha_nome"));
                    m.put("mesaOrigemId", rs.getObject("mesa_origem_id"));
                    m.put("mesaDestinoId", rs.getObject("mesa_destino_id"));
                    m.put("forcada", rs.getInt("forcada") == 1);
                    m.put("diaPersonagem", rs.getObject("dia_personagem"));
                    m.put("horaPersonagem", rs.getObject("hora_personagem"));
                    m.put("diaMesa", rs.getObject("dia_mesa"));
                    m.put("horaMesa", rs.getObject("hora_mesa"));
                    m.put("registradoEm", rs.getString("registrado_em"));
                    lista.add(m);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar trocas de mesa", e);
        }
        return lista;
    }

    private static Mesa ler(ResultSet rs) throws SQLException {
        Mesa.Status status;
        try {
            status = Mesa.Status.valueOf(rs.getString("status"));
        } catch (IllegalArgumentException | NullPointerException e) {
            status = Mesa.Status.ATIVA;
        }
        return new Mesa(rs.getInt("id"), rs.getString("nome"), rs.getInt("dia"), rs.getInt("hora"), status,
                rs.getObject("mestre_id") != null ? rs.getInt("mestre_id") : null);
    }

    private static FichaNaMesa lerFicha(ResultSet rs) throws SQLException {
        return new FichaNaMesa(rs.getLong("id"), rs.getString("nome"), rs.getString("raca_nome"), rs.getInt("usuario_id"),
                rs.getObject("mesa_id") != null ? rs.getInt("mesa_id") : null,
                rs.getObject("dia_personagem") != null ? rs.getInt("dia_personagem") : null,
                rs.getObject("hora_personagem") != null ? rs.getInt("hora_personagem") : null);
    }
}
