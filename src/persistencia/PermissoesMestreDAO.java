package persistencia;

import personagem.ControlePermissoesMestre;
import personagem.Raca;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data Access Object (DAO) para o painel do Mestre, Fog-of-War e estado da campanha no SQLite.
 * Salva capítulos de Wiki desbloqueados, criaturas reveladas no Bestiário,
 * níveis de visibilidade do mapa interativo e dia atual de campanha.
 */
public class PermissoesMestreDAO {

    public void salvarValor(String categoria, String chave, String valor) {
        if (categoria == null || chave == null) return;
        String sql = """
            INSERT INTO controle_mestre (categoria, chave, valor)
            VALUES (?, ?, ?)
            ON CONFLICT(categoria, chave) DO UPDATE SET valor = excluded.valor;
        """;

        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, categoria);
            ps.setString(2, chave);
            ps.setString(3, valor != null ? valor : "");
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar permissão do mestre no SQLite: " + categoria + "/" + chave, e);
        }
    }

    public String obterValor(String categoria, String chave, String valorPadrao) {
        String sql = "SELECT valor FROM controle_mestre WHERE categoria = ? AND chave = ?";
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, categoria);
            ps.setString(2, chave);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("valor");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar permissão do mestre no SQLite: " + categoria + "/" + chave, e);
        }
        return valorPadrao;
    }

    public Map<String, String> listarPorCategoria(String categoria) {
        Map<String, String> mapa = new LinkedHashMap<>();
        String sql = "SELECT chave, valor FROM controle_mestre WHERE categoria = ?";
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, categoria);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mapa.put(rs.getString("chave"), rs.getString("valor"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar permissões da categoria no SQLite: " + categoria, e);
        }
        return mapa;
    }

    /**
     * Sincroniza o objeto ControlePermissoesMestre salvando todas as suas definições no SQLite.
     */
    public void salvarControleCompleto(ControlePermissoesMestre c) {
        if (c == null) return;
        salvarValor("CAMPANHA", "dia_atual", String.valueOf(c.getDiaAtualCampanha()));

        // Wiki
        String[] capitulos = {"Status", "Grafiturgia Elemental", "Economia e Comercio", "Bestiario Regional"};
        for (String cap : capitulos) {
            salvarValor("WIKI", cap, String.valueOf(c.isCapituloLiberado(cap)));
        }
    }

    /**
     * Carrega as configurações persistidas do SQLite para o ControlePermissoesMestre em memória.
     */
    public void carregarParaControle(ControlePermissoesMestre c) {
        if (c == null) return;

        // Wiki
        Map<String, String> wiki = listarPorCategoria("WIKI");
        for (Map.Entry<String, String> e : wiki.entrySet()) {
            if (Boolean.parseBoolean(e.getValue())) {
                c.liberarCapituloWiki(e.getKey());
            } else {
                c.bloquearCapituloWiki(e.getKey());
            }
        }

        // Bestiário
        Map<String, String> bestiario = listarPorCategoria("BESTIARIO");
        for (String monstro : bestiario.keySet()) {
            c.revelarMonstroBestiario(monstro);
        }

        // Fog Cidades
        Map<String, String> fogCidades = listarPorCategoria("FOG_CIDADE");
        for (Map.Entry<String, String> e : fogCidades.entrySet()) {
            try {
                c.setVisibilidadeCidade(e.getKey(), ControlePermissoesMestre.NivelVisibilidadeMapa.valueOf(e.getValue()));
            } catch (Exception ignored) {}
        }

        // Fog Conexoes
        Map<String, String> fogConexoes = listarPorCategoria("FOG_CONEXAO");
        for (Map.Entry<String, String> e : fogConexoes.entrySet()) {
            try {
                c.setVisibilidadeConexao(e.getKey(), ControlePermissoesMestre.NivelVisibilidadeMapa.valueOf(e.getValue()));
            } catch (Exception ignored) {}
        }
    }
}
