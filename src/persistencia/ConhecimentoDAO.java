package persistencia;

import economia.Lojas;
import economia.MotorEconomico;
import economia.RedeConhecimento;
import economia.RedeConhecimento.Ajuste;
import economia.RedeConhecimento.Conhecimento;
import economia.RedeConhecimento.Estagio;
import economia.RedeConhecimento.TipoConhecimento;

import java.sql.*;
import java.util.*;

/**
 * Persistência da disseminação: conhecimentos concedidos pelo Mestre (tipo de arma ou receita),
 * os parâmetros de espalhamento e as intervenções por cidade. Monta a {@link RedeConhecimento}
 * que o motor do comércio consulta todo dia.
 */
public class ConhecimentoDAO {

    /** Parâmetros de espalhamento (em dias). Os padrões vêm da decisão com o Mestre. */
    public record Parametros(double sigiloDias, double diasPor100Km, double resistenciaPorSalto, double diasRumorComercio, double diasComercioProducao) {
        public static final Parametros PADRAO = new Parametros(7, 2, 3, 10, 20);

        public Parametros {
            if (sigiloDias < 0 || diasPor100Km < 0 || resistenciaPorSalto < 0 || diasRumorComercio < 0 || diasComercioProducao < 0) {
                throw new AdminCrud.ErroValidacao("Os tempos de espalhamento não podem ser negativos.");
            }
        }
    }

    public List<Conhecimento> listar() {
        Map<Integer, Map<Integer, Ajuste>> ajustes = new HashMap<>();
        List<Conhecimento> lista = new ArrayList<>();
        try (Connection conn = BancoDados.getConnection(); Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT conhecimento_id, cidade_id, acao, atraso FROM conhecimento_cidades")) {
                while (rs.next()) {
                    String acao = rs.getString("acao");
                    boolean revogada = "REVOGADA".equals(acao);
                    Estagio e = revogada ? null : Estagio.valueOf(acao);
                    ajustes.computeIfAbsent(rs.getInt(1), k -> new HashMap<>()).put(rs.getInt(2), new Ajuste(e, revogada, rs.getDouble("atraso")));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT * FROM conhecimentos ORDER BY id")) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    int pausadoDesde = rs.getInt("pausado_desde");
                    Integer desde = rs.wasNull() ? null : pausadoDesde;
                    lista.add(new Conhecimento(id, TipoConhecimento.valueOf(rs.getString("tipo")), inteiroOuNulo(rs, "tipo_arma_id"), inteiroOuNulo(rs, "receita_id"),
                            rs.getInt("cidade_origem_id"), rs.getInt("dia_concessao"), rs.getDouble("sigilo_dias"), rs.getDouble("dias_por_100km"),
                            rs.getDouble("resistencia_salto"), rs.getDouble("dias_rumor_comercio"), rs.getDouble("dias_comercio_producao"),
                            rs.getInt("pausado") == 1, desde, rs.getInt("dias_pausados"), rs.getInt("segredo") == 1, ajustes.getOrDefault(id, Map.of())));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar conhecimentos: " + e.getMessage(), e);
        }
        return lista;
    }

    /** Monta a rede com as receitas e cidades do motor (para o ciclo diário e para a tela do Mestre). */
    public RedeConhecimento carregarRede(MotorEconomico motor) {
        Map<Integer, Integer> tipoDaReceita = new HashMap<>();
        Map<Integer, Lojas.TipoEstabelecimento> lojaDoTipo = new HashMap<>();
        try (Connection conn = BancoDados.getConnection(); Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT receita_id, tipo_arma_id FROM receitas_arma WHERE receita_id IS NOT NULL")) {
                while (rs.next()) tipoDaReceita.put(rs.getInt(1), rs.getInt(2));
            }
            // Encomendas sob medida: chave "R:EP:<tipo>:..."
            try (ResultSet rs = st.executeQuery("SELECT id, chave_forja FROM receitas WHERE chave_forja LIKE 'R:EP:%'")) {
                while (rs.next()) {
                    String[] partes = rs.getString(2).split(":");
                    try {
                        tipoDaReceita.put(rs.getInt(1), Integer.parseInt(partes[2]));
                    } catch (RuntimeException ignored) {
                        // chave fora do formato: fica como receita comum
                    }
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT t.id, m.tipo_loja FROM tipos_arma t JOIN moldes_arma m ON m.id = t.molde_id")) {
                while (rs.next()) {
                    try {
                        lojaDoTipo.put(rs.getInt(1), Lojas.TipoEstabelecimento.valueOf(rs.getString(2)));
                    } catch (RuntimeException ignored) {
                        // especialidade apagada do enum
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao montar a rede de conhecimento: " + e.getMessage(), e);
        }
        return new RedeConhecimento(listar(), motor.getReceitas(), tipoDaReceita, lojaDoTipo, motor.getCidades());
    }

    public int conceder(TipoConhecimento tipo, Integer tipoArmaId, Integer receitaId, int cidadeOrigemId, int dia, Parametros p) {
        try (Connection conn = BancoDados.getConnection()) {
            if (contar(conn, "SELECT COUNT(*) FROM cidades WHERE id = ?", cidadeOrigemId) == 0) throw new AdminCrud.ErroValidacao("Cidade de origem não encontrada.");
            if (tipo == TipoConhecimento.TIPO_ARMA) {
                if (tipoArmaId == null || contar(conn, "SELECT COUNT(*) FROM tipos_arma WHERE id = ?", tipoArmaId) == 0) throw new AdminCrud.ErroValidacao("Tipo de arma não encontrado.");
                if (contar(conn, "SELECT COUNT(*) FROM conhecimentos WHERE tipo = 'TIPO_ARMA' AND tipo_arma_id = ?", tipoArmaId) > 0) {
                    throw new AdminCrud.ErroValidacao("Este tipo de arma já está se espalhando. Edite o conhecimento existente.");
                }
                receitaId = null;
            } else {
                if (receitaId == null || contar(conn, "SELECT COUNT(*) FROM receitas WHERE id = ?", receitaId) == 0) throw new AdminCrud.ErroValidacao("Receita não encontrada.");
                if (contar(conn, "SELECT COUNT(*) FROM receitas WHERE id = ? AND (chave_forja LIKE 'RC:%' OR chave_forja LIKE 'R:EP:%')", receitaId) > 0) {
                    throw new AdminCrud.ErroValidacao("Peças de forja e encomendas avulsas seguem a arma: espalhe o tipo de arma ou a receita pronta.");
                }
                if (contar(conn, "SELECT COUNT(*) FROM conhecimentos WHERE tipo = 'RECEITA' AND receita_id = ?", receitaId) > 0) {
                    throw new AdminCrud.ErroValidacao("Esta receita já está se espalhando. Edite o conhecimento existente.");
                }
                tipoArmaId = null;
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO conhecimentos (tipo, tipo_arma_id, receita_id, cidade_origem_id, dia_concessao,
                        sigilo_dias, dias_por_100km, resistencia_salto, dias_rumor_comercio, dias_comercio_producao)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS)) {
                preencher(ps, tipo.name(), tipoArmaId, receitaId, cidadeOrigemId, dia,
                        p.sigiloDias(), p.diasPor100Km(), p.resistenciaPorSalto(), p.diasRumorComercio(), p.diasComercioProducao());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao conceder conhecimento: " + e.getMessage(), e);
        }
    }

    public void editarParametros(int id, Parametros p) {
        executar("UPDATE conhecimentos SET sigilo_dias = ?, dias_por_100km = ?, resistencia_salto = ?, dias_rumor_comercio = ?, dias_comercio_producao = ? WHERE id = ?",
                p.sigiloDias(), p.diasPor100Km(), p.resistenciaPorSalto(), p.diasRumorComercio(), p.diasComercioProducao(), id);
    }

    /** Pausar congela o relógio do conhecimento; retomar soma os dias parados. */
    public void pausar(Conhecimento k, boolean pausar, int dia) {
        if (pausar && !k.pausado()) {
            executar("UPDATE conhecimentos SET pausado = 1, pausado_desde = ? WHERE id = ?", dia, k.id());
        } else if (!pausar && k.pausado()) {
            int parados = k.pausadoDesde() != null ? Math.max(0, dia - k.pausadoDesde()) : 0;
            executar("UPDATE conhecimentos SET pausado = 0, pausado_desde = NULL, dias_pausados = dias_pausados + ? WHERE id = ?", parados, k.id());
        }
    }

    public void definirSegredo(int id, boolean segredo) {
        executar("UPDATE conhecimentos SET segredo = ? WHERE id = ?", segredo ? 1 : 0, id);
    }

    /**
     * Intervenção numa cidade. {@code acao}: RUMOR, COMERCIO ou PRODUCAO (adianta até lá a partir
     * de agora), REVOGAR (a cidade esquece e não passa adiante) ou LIMPAR (volta ao cálculo normal).
     */
    public void ajustarCidade(Conhecimento k, int cidadeId, String acao, int dia) {
        switch (acao) {
            case "LIMPAR" -> executar("DELETE FROM conhecimento_cidades WHERE conhecimento_id = ? AND cidade_id = ?", k.id(), cidadeId);
            case "REVOGAR" -> executar("INSERT OR REPLACE INTO conhecimento_cidades (conhecimento_id, cidade_id, acao, atraso) VALUES (?, ?, 'REVOGADA', 0)", k.id(), cidadeId);
            case "RUMOR", "COMERCIO", "PRODUCAO" -> executar("INSERT OR REPLACE INTO conhecimento_cidades (conhecimento_id, cidade_id, acao, atraso) VALUES (?, ?, ?, ?)",
                    k.id(), cidadeId, acao, Math.max(0, k.decorrido(dia)));
            default -> throw new AdminCrud.ErroValidacao("Ação desconhecida: " + acao);
        }
    }

    public void excluir(int id) {
        executar("DELETE FROM conhecimento_cidades WHERE conhecimento_id = ?", id);
        executar("DELETE FROM conhecimentos WHERE id = ?", id);
    }

    // ------------------------------------------------------------------

    private static Integer inteiroOuNulo(ResultSet rs, String coluna) throws SQLException {
        int v = rs.getInt(coluna);
        return rs.wasNull() ? null : v;
    }

    private static void preencher(PreparedStatement ps, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
    }

    private static long contar(Connection conn, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            preencher(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private static void executar(String sql, Object... args) {
        try (Connection conn = BancoDados.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            preencher(ps, args);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao gravar conhecimento: " + e.getMessage(), e);
        }
    }
}
