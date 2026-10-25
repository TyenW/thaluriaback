package persistencia;

import personagem.Atributo;
import personagem.Raca;
import personagem.SessaoCriacaoFicha;

import java.sql.*;
import java.util.*;

/**
 * DAO responsável por autenticar Jogadores e Mestre, controlar o limite de fichas
 * por conta, guardar o Pity System do sorteador de raças e sincronizar a
 * SessaoCriacaoFicha em tempo real entre Jogador e Mestre.
 */
public class UsuarioDAO {

    public static class ContaUsuario {
        private final int id;
        private final String username;
        private final String papel; // "JOGADOR" ou "MESTRE"
        private int limiteMaximoFichas;
        private int fatorSortePity;

        public ContaUsuario(int id, String username, String papel, int limiteMaximoFichas, int fatorSortePity) {
            this.id = id;
            this.username = username;
            this.papel = papel;
            this.limiteMaximoFichas = limiteMaximoFichas;
            this.fatorSortePity = fatorSortePity;
        }

        public int getId() { return id; }
        public String getUsername() { return username; }
        public String getPapel() { return papel; }
        public boolean isMestre() { return "MESTRE".equalsIgnoreCase(papel); }
        public int getLimiteMaximoFichas() { return limiteMaximoFichas; }
        public int getFatorSortePity() { return fatorSortePity; }
    }

    public static class ResumoUsuarioDTO {
        public int id;
        public String username;
        public String papel;
        public int limiteFichas;
        public int pity;
        public int totalFichas;
    }

    public static class EstadoSessaoCriacaoDTO {
        public int usuarioId;
        public String nomePersonagemRascunho;
        public String etapaAtual;
        public List<String> opcoesRacasSorteadas = new ArrayList<>();
        public String racaEscolhida;
        public String linhagemDraconica;
        public Map<String, Integer> atributosRolados = new LinkedHashMap<>();
        public boolean impulsoDraconicoAtivo;
        public int rerollsConcedidos;
        public String dicaMestre;
        public boolean aprovadaPeloMestre;
        public long atualizadoEm;
    }

    public void criarContasPadraoSeVazio() {
        try (Connection conn = BancoDados.obterConexao();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM usuarios")) {
            if (rs.next() && rs.getInt(1) == 0) {
                cadastrarUsuario("mestre", "thaluriaadmin", "MESTRE", 999);
                cadastrarUsuario("jogador1", "123456", "JOGADOR", 2);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao inicializar contas padrão: " + e.getMessage(), e);
        }
    }

    /**
     * Cadastra ou atualiza uma conta. Senha nula ou vazia mantém a senha atual
     * (ou usa "123456" em contas novas), para que ajustar papel/limite não
     * redefina a senha do jogador.
     */
    public ContaUsuario cadastrarUsuario(String username, String senha, String papel, int limiteFichas) {
        boolean manterSenha = senha == null || senha.isBlank();
        String sql = """
            INSERT INTO usuarios (username, senha, papel, limite_maximo_fichas, fator_sorte_pity)
            VALUES (?, ?, ?, ?, 0)
            ON CONFLICT(username) DO UPDATE SET
                senha = CASE WHEN ? THEN usuarios.senha ELSE excluded.senha END,
                papel = excluded.papel,
                limite_maximo_fichas = excluded.limite_maximo_fichas;
        """;
        try (Connection conn = BancoDados.obterConexao();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            ps.setString(2, Senhas.gerarHash(manterSenha ? "123456" : senha));
            ps.setString(3, papel.toUpperCase());
            ps.setInt(4, Math.max(1, limiteFichas));
            ps.setBoolean(5, manterSenha);
            ps.executeUpdate();
            return buscarPorUsername(username.trim());
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao cadastrar utilizador '" + username + "': " + e.getMessage(), e);
        }
    }

    /** Troca senhas antigas em texto puro pelo hash. Chamado ao iniciar o servidor. */
    public int migrarSenhasEmTextoPuro() {
        Map<Integer, String> antigas = new LinkedHashMap<>();
        try (Connection conn = BancoDados.obterConexao()) {
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT id, senha FROM usuarios")) {
                while (rs.next()) {
                    if (!Senhas.ehHash(rs.getString(2))) antigas.put(rs.getInt(1), rs.getString(2));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE usuarios SET senha = ? WHERE id = ?")) {
                for (Map.Entry<Integer, String> e : antigas.entrySet()) {
                    ps.setString(1, Senhas.gerarHash(e.getValue() == null ? "" : e.getValue()));
                    ps.setInt(2, e.getKey());
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao migrar senhas: " + e.getMessage(), e);
        }
        return antigas.size();
    }

    public ContaUsuario autenticar(String username, String senha) {
        String sql = "SELECT id, username, papel, limite_maximo_fichas, fator_sorte_pity, senha FROM usuarios WHERE username = ?";
        try (Connection conn = BancoDados.obterConexao();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && Senhas.conferir(senha, rs.getString("senha"))) {
                    return new ContaUsuario(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("papel"),
                        rs.getInt("limite_maximo_fichas"),
                        rs.getInt("fator_sorte_pity")
                    );
                }
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro na autenticação: " + e.getMessage(), e);
        }
    }

    public ContaUsuario buscarPorUsername(String username) {
        String sql = "SELECT id, username, papel, limite_maximo_fichas, fator_sorte_pity FROM usuarios WHERE username = ?";
        try (Connection conn = BancoDados.obterConexao();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ContaUsuario(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("papel"),
                        rs.getInt("limite_maximo_fichas"),
                        rs.getInt("fator_sorte_pity")
                    );
                }
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar utilizador: " + e.getMessage(), e);
        }
    }

    public void atualizarLimiteFichas(int usuarioId, int novoLimite) {
        String sql = "UPDATE usuarios SET limite_maximo_fichas = ? WHERE id = ?";
        try (Connection conn = BancoDados.obterConexao();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(0, novoLimite));
            ps.setInt(2, usuarioId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar limite de fichas: " + e.getMessage(), e);
        }
    }

    public void atualizarFatorPity(int usuarioId, int novoPity) {
        String sql = "UPDATE usuarios SET fator_sorte_pity = ? WHERE id = ?";
        try (Connection conn = BancoDados.obterConexao();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(0, novoPity));
            ps.setInt(2, usuarioId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar pity: " + e.getMessage(), e);
        }
    }

    /**
     * Sincroniza o estado em tempo real de uma SessaoCriacaoFicha para o banco SQLite,
     * permitindo que o painel do Mestre acompanhe instantaneamente cada escolha do jogador.
     */
    public void salvarSessaoCriacaoEmTempoReal(int usuarioId, SessaoCriacaoFicha sessao, String linhagemDraconica) {
        String sql = """
            INSERT INTO sessoes_criacao_ficha (
                usuario_id, nome_personagem_rascunho, etapa_atual, opcoes_racas_sorteadas,
                raca_escolhida, linhagem_draconica, atributos_rolados_json,
                impulso_draconico_ativo, rerolls_concedidos, dica_mestre,
                aprovada_pelo_mestre, atualizado_em
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(usuario_id) DO UPDATE SET
                nome_personagem_rascunho = excluded.nome_personagem_rascunho,
                etapa_atual = excluded.etapa_atual,
                opcoes_racas_sorteadas = excluded.opcoes_racas_sorteadas,
                raca_escolhida = excluded.raca_escolhida,
                linhagem_draconica = excluded.linhagem_draconica,
                atributos_rolados_json = excluded.atributos_rolados_json,
                impulso_draconico_ativo = excluded.impulso_draconico_ativo,
                rerolls_concedidos = excluded.rerolls_concedidos,
                dica_mestre = excluded.dica_mestre,
                aprovada_pelo_mestre = excluded.aprovada_pelo_mestre,
                atualizado_em = excluded.atualizado_em;
        """;

        StringBuilder opcoesStr = new StringBuilder();
        for (Raca r : sessao.getOpcoesRacaSorteadas()) {
            if (opcoesStr.length() > 0) opcoesStr.append(";");
            opcoesStr.append(r.getId());
        }

        StringBuilder attrStr = new StringBuilder();
        for (Map.Entry<Atributo, Integer> entry : sessao.getAtributosRolados().entrySet()) {
            if (attrStr.length() > 0) attrStr.append(";");
            attrStr.append(entry.getKey().name()).append("=").append(entry.getValue());
        }

        try (Connection conn = BancoDados.obterConexao();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            ps.setString(2, sessao.getNomePersonagem());
            ps.setString(3, sessao.getEtapaAtual().name());
            ps.setString(4, opcoesStr.toString());
            ps.setString(5, sessao.getRacaEscolhida() != null ? sessao.getRacaEscolhida().getId() : null);
            ps.setString(6, linhagemDraconica);
            ps.setString(7, attrStr.toString());
            ps.setInt(8, sessao.isImpulsoDraconicoLiberado() ? 1 : 0);
            ps.setInt(9, sessao.getRerollsDisponiveis());
            ps.setString(10, sessao.getUltimaDicaMestre());
            ps.setInt(11, sessao.isAprovadaPeloMestre() ? 1 : 0);
            ps.setLong(12, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao gravar sessão de criação em tempo real: " + e.getMessage(), e);
        }
    }

    public List<EstadoSessaoCriacaoDTO> listarTodasSessoesAtivasParaMestre() {
        List<EstadoSessaoCriacaoDTO> lista = new ArrayList<>();
        String sql = "SELECT * FROM sessoes_criacao_ficha ORDER BY atualizado_em DESC";
        try (Connection conn = BancoDados.obterConexao();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                EstadoSessaoCriacaoDTO dto = new EstadoSessaoCriacaoDTO();
                dto.usuarioId = rs.getInt("usuario_id");
                dto.nomePersonagemRascunho = rs.getString("nome_personagem_rascunho");
                dto.etapaAtual = rs.getString("etapa_atual");
                String opcoes = rs.getString("opcoes_racas_sorteadas");
                if (opcoes != null && !opcoes.isBlank()) {
                    dto.opcoesRacasSorteadas.addAll(Arrays.asList(opcoes.split(";")));
                }
                dto.racaEscolhida = rs.getString("raca_escolhida");
                dto.linhagemDraconica = rs.getString("linhagem_draconica");
                String attrs = rs.getString("atributos_rolados_json");
                if (attrs != null && !attrs.isBlank()) {
                    for (String par : attrs.split(";")) {
                        String[] kv = par.split("=");
                        if (kv.length == 2) {
                            dto.atributosRolados.put(kv[0], Integer.parseInt(kv[1]));
                        }
                    }
                }
                dto.impulsoDraconicoAtivo = rs.getInt("impulso_draconico_ativo") == 1;
                dto.rerollsConcedidos = rs.getInt("rerolls_concedidos");
                dto.dicaMestre = rs.getString("dica_mestre");
                dto.aprovadaPeloMestre = rs.getInt("aprovada_pelo_mestre") == 1;
                dto.atualizadoEm = rs.getLong("atualizado_em");
                lista.add(dto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar sessões ativas: " + e.getMessage(), e);
        }
        return lista;
    }

    public List<ResumoUsuarioDTO> listarTodos() {
        List<ResumoUsuarioDTO> lista = new ArrayList<>();
        String sql = """
            SELECT u.id, u.username, u.papel, u.limite_maximo_fichas, u.fator_sorte_pity,
                   (SELECT COUNT(*) FROM fichas f WHERE f.usuario_id = u.id) as total_fichas
            FROM usuarios u
            ORDER BY u.id ASC
        """;
        try (Connection conn = BancoDados.obterConexao();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ResumoUsuarioDTO dto = new ResumoUsuarioDTO();
                dto.id = rs.getInt("id");
                dto.username = rs.getString("username");
                dto.papel = rs.getString("papel");
                dto.limiteFichas = rs.getInt("limite_maximo_fichas");
                dto.pity = rs.getInt("fator_sorte_pity");
                dto.totalFichas = rs.getInt("total_fichas");
                lista.add(dto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar todos os usuários: " + e.getMessage(), e);
        }
        return lista;
    }

    public boolean excluirUsuario(int id) {
        String sqlCheck = "SELECT username FROM usuarios WHERE id = ?";
        try (Connection conn = BancoDados.obterConexao()) {
            String username = null;
            try (PreparedStatement psCheck = conn.prepareStatement(sqlCheck)) {
                psCheck.setInt(1, id);
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (rs.next()) {
                        username = rs.getString("username");
                    }
                }
            }
            if (username == null) {
                return false;
            }
            if ("mestre".equalsIgnoreCase(username.trim())) {
                throw new IllegalArgumentException("A conta principal 'mestre' não pode ser excluída.");
            }

            conn.setAutoCommit(false);
            try {
                // 1. Obter IDs das fichas deste usuário
                List<Long> fichaIds = new ArrayList<>();
                try (PreparedStatement psFichas = conn.prepareStatement("SELECT id FROM fichas WHERE usuario_id = ?")) {
                    psFichas.setInt(1, id);
                    try (ResultSet rsF = psFichas.executeQuery()) {
                        while (rsF.next()) {
                            fichaIds.add(rsF.getLong("id"));
                        }
                    }
                }

                // 2. Apagar perícias, inventário e monstros domados associados às fichas
                for (Long fId : fichaIds) {
                    try (PreparedStatement psP = conn.prepareStatement("DELETE FROM ficha_pericias WHERE ficha_id = ?")) {
                        psP.setLong(1, fId);
                        psP.executeUpdate();
                    }
                    try (PreparedStatement psI = conn.prepareStatement("DELETE FROM ficha_inventario WHERE ficha_id = ?")) {
                        psI.setLong(1, fId);
                        psI.executeUpdate();
                    }
                    try (PreparedStatement psM = conn.prepareStatement("DELETE FROM ficha_monstros_domados WHERE ficha_id = ?")) {
                        psM.setLong(1, fId);
                        psM.executeUpdate();
                    }
                }

                // 3. Apagar fichas
                try (PreparedStatement psDelF = conn.prepareStatement("DELETE FROM fichas WHERE usuario_id = ?")) {
                    psDelF.setInt(1, id);
                    psDelF.executeUpdate();
                }

                // 4. Apagar sessões de criação de ficha
                try (PreparedStatement psSessao = conn.prepareStatement("DELETE FROM sessoes_criacao_ficha WHERE usuario_id = ?")) {
                    psSessao.setInt(1, id);
                    psSessao.executeUpdate();
                }

                // 5. Apagar o usuário
                int rows;
                try (PreparedStatement psUser = conn.prepareStatement("DELETE FROM usuarios WHERE id = ?")) {
                    psUser.setInt(1, id);
                    rows = psUser.executeUpdate();
                }

                conn.commit();
                return rows > 0;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao excluir usuário: " + e.getMessage(), e);
        }
    }
}
