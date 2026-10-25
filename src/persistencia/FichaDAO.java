package persistencia;

import economia.Carteira;
import economia.Item;
import geografia.Cidade;
import personagem.Atributo;
import personagem.Ficha;
import personagem.Monstro;
import personagem.Pericia;
import personagem.Raca;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object (DAO) para fichas de personagens no SQLite.
 * Persiste atributos, recursos atuais e máximos, carteira em latão,
 * perícias e inventário de forma transacional e normalizada.
 */
public class FichaDAO {

    private final RacaDAO racaDAO;
    private final ItemDAO itemDAO;

    public FichaDAO() {
        this.racaDAO = new RacaDAO();
        this.itemDAO = new ItemDAO();
    }

    public FichaDAO(RacaDAO racaDAO, ItemDAO itemDAO) {
        this.racaDAO = racaDAO != null ? racaDAO : new RacaDAO();
        this.itemDAO = itemDAO != null ? itemDAO : new ItemDAO();
    }

    /**
     * Salva ou atualiza uma ficha de personagem no banco SQLite.
     * Atualiza id gerado na própria instância de Ficha.
     */
    public void salvar(Ficha ficha) {
        if (ficha == null) return;

        try (Connection conn = BancoDados.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (ficha.getId() == 0) {
                    inserirFicha(conn, ficha);
                } else {
                    atualizarFicha(conn, ficha);
                }

                salvarPericias(conn, ficha);
                salvarInventario(conn, ficha);
                salvarMonstrosDomados(conn, ficha);

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao persistir ficha no SQLite: " + ficha.getNome(), e);
        }
    }

    private void inserirFicha(Connection conn, Ficha ficha) throws SQLException {
        String sql = """
            INSERT INTO fichas (
                nome, raca_nome, vig, forca, des, men, esp, von,
                hp, hp_max, sanidade, sanidade_max, energia, energia_max, mana, mana_max,
                defesa, defesa_oculta, pontos_essencia, carteira_latao, cidade_id, imagem_url, usuario_id, carteira_moedas
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            preencherParametrosFicha(ps, ficha);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    ficha.setId(rs.getLong(1));
                }
            }
        }
    }

    private void atualizarFicha(Connection conn, Ficha ficha) throws SQLException {
        String sql = """
            UPDATE fichas SET
                nome = ?, raca_nome = ?, vig = ?, forca = ?, des = ?, men = ?, esp = ?, von = ?,
                hp = ?, hp_max = ?, sanidade = ?, sanidade_max = ?, energia = ?, energia_max = ?, mana = ?, mana_max = ?,
                defesa = ?, defesa_oculta = ?, pontos_essencia = ?, carteira_latao = ?, cidade_id = ?, imagem_url = ?, usuario_id = ?, carteira_moedas = ?
            WHERE id = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            preencherParametrosFicha(ps, ficha);
            ps.setLong(25, ficha.getId());
            ps.executeUpdate();
        }
    }

    private void preencherParametrosFicha(PreparedStatement ps, Ficha ficha) throws SQLException {
        ps.setString(1, ficha.getNome());
        ps.setString(2, ficha.getRaca() != null ? ficha.getRaca().getNome() : "Humano");
        ps.setInt(3, ficha.getVig());
        ps.setInt(4, ficha.getForca());
        ps.setInt(5, ficha.getDes());
        ps.setInt(6, ficha.getMen());
        ps.setInt(7, ficha.getEsp());
        ps.setInt(8, ficha.getVon());
        ps.setInt(9, ficha.getHp());
        ps.setInt(10, ficha.getHpMax());
        ps.setInt(11, ficha.getSanidade());
        ps.setInt(12, ficha.getSanidadeMax());
        ps.setInt(13, ficha.getEnergia());
        ps.setInt(14, ficha.getEnergiaMax());
        ps.setInt(15, ficha.getMana());
        ps.setInt(16, ficha.getManaMax());
        ps.setInt(17, ficha.getDefesa());
        ps.setInt(18, ficha.getDefesaOculta());
        ps.setInt(19, ficha.getPontosEssencia());
        ps.setLong(20, ficha.getCarteira() != null ? ficha.getCarteira().getValorTotalEmLatao() : 0L);
        if (ficha.getCidadeAtual() != null && ficha.getCidadeAtual().getId() > 0) {
            ps.setInt(21, ficha.getCidadeAtual().getId());
        } else {
            ps.setNull(21, Types.INTEGER);
        }
        ps.setString(22, ficha.getImagemUrl());
        ps.setInt(23, ficha.getUsuarioId() > 0 ? ficha.getUsuarioId() : 1);
        ps.setString(24, ficha.getCarteira() != null ? ficha.getCarteira().serializarMoedasFisicas() : null);
    }

    private void salvarPericias(Connection conn, Ficha ficha) throws SQLException {
        Map<Pericia, Integer> graus = ficha.getGrausPericia();
        Map<Pericia, Integer> maestrias = ficha.getMaestriasPericia();
        if (graus == null || graus.isEmpty()) return;

        String sqlIns = """
            INSERT INTO ficha_pericias (ficha_id, pericia, grau, maestria) VALUES (?, ?, ?, ?)
            ON CONFLICT(ficha_id, pericia) DO UPDATE SET
                grau = excluded.grau,
                maestria = excluded.maestria
        """;
        try (PreparedStatement psIns = conn.prepareStatement(sqlIns)) {
            for (Map.Entry<Pericia, Integer> entry : graus.entrySet()) {
                Pericia p = entry.getKey();
                int grau = entry.getValue();
                int maestria = maestrias.getOrDefault(p, 0);
                psIns.setLong(1, ficha.getId());
                psIns.setString(2, p.name());
                psIns.setInt(3, grau);
                psIns.setInt(4, maestria);
                psIns.addBatch();
            }
            psIns.executeBatch();
        }
    }

    private void salvarInventario(Connection conn, Ficha ficha) throws SQLException {
        String sqlDelete = "DELETE FROM ficha_inventario WHERE ficha_id = ?";
        try (PreparedStatement psDel = conn.prepareStatement(sqlDelete)) {
            psDel.setLong(1, ficha.getId());
            psDel.executeUpdate();
        }

        List<Item> inventario = ficha.getInventario();
        if (inventario == null || inventario.isEmpty()) return;

        String sqlIns = """
            INSERT INTO ficha_inventario (ficha_id, item_id, quantidade)
            VALUES (?, ?, ?)
            ON CONFLICT(ficha_id, item_id) DO UPDATE SET quantidade = quantidade + excluded.quantidade
        """;
        try (PreparedStatement psIns = conn.prepareStatement(sqlIns)) {
            for (Item it : inventario) {
                if (it.getId() > 0) {
                    psIns.setLong(1, ficha.getId());
                    psIns.setInt(2, it.getId());
                    psIns.setInt(3, Math.max(1, it.getQuantidade()));
                    psIns.addBatch();
                }
            }
            psIns.executeBatch();
        }
    }

    private void salvarMonstrosDomados(Connection conn, Ficha ficha) throws SQLException {
        String sqlDelete = "DELETE FROM ficha_monstros_domados WHERE ficha_id = ?";
        try (PreparedStatement psDel = conn.prepareStatement(sqlDelete)) {
            psDel.setLong(1, ficha.getId());
            psDel.executeUpdate();
        }

        List<Monstro> monstros = ficha.getMonstrosDomados();
        if (monstros == null || monstros.isEmpty()) return;

        String sqlIns = """
            INSERT INTO ficha_monstros_domados (
                ficha_id, nome, especie, porte, domesticavel,
                vig, forca, des, men, esp, von, hp, hp_max
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement psIns = conn.prepareStatement(sqlIns, Statement.RETURN_GENERATED_KEYS)) {
            for (Monstro m : monstros) {
                psIns.setLong(1, ficha.getId());
                psIns.setString(2, m.getNome());
                psIns.setString(3, m.getEspecie());
                psIns.setString(4, m.getPorte().name());
                psIns.setInt(5, m.isDomesticavel() ? 1 : 0);
                psIns.setInt(6, m.getVig());
                psIns.setInt(7, m.getForca());
                psIns.setInt(8, m.getDes());
                psIns.setInt(9, m.getMen());
                psIns.setInt(10, m.getEsp());
                psIns.setInt(11, m.getVon());
                psIns.setInt(12, m.getHp());
                psIns.setInt(13, m.getHpMax());
                psIns.executeUpdate();
                try (ResultSet rs = psIns.getGeneratedKeys()) {
                    if (rs.next()) {
                        m.setId(rs.getInt(1));
                    }
                }
            }
        }
    }

    public Ficha buscarPorId(long id) {
        String sql = "SELECT * FROM fichas WHERE id = ?";
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return carregarFichaDoResultSet(conn, rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar ficha no SQLite: ID " + id, e);
        }
        return null;
    }

    public Ficha buscarPorNome(String nome) {
        if (nome == null) return null;
        String sql = "SELECT * FROM fichas WHERE UPPER(nome) = UPPER(?) ORDER BY id DESC LIMIT 1";
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nome.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return carregarFichaDoResultSet(conn, rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar ficha por nome no SQLite: " + nome, e);
        }
        return null;
    }

    public List<Ficha> listarTodas() {
        List<Ficha> lista = new ArrayList<>();
        String sql = "SELECT * FROM fichas ORDER BY id ASC";
        try (Connection conn = BancoDados.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(carregarFichaDoResultSet(conn, rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar fichas do SQLite", e);
        }
        return lista;
    }

    public List<Ficha> listarPorUsuario(int usuarioId) {
        List<Ficha> lista = new ArrayList<>();
        String sql = "SELECT * FROM fichas WHERE usuario_id = ? ORDER BY id ASC";
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(carregarFichaDoResultSet(conn, rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar fichas do usuário " + usuarioId, e);
        }
        return lista;
    }

    public boolean excluir(long id) {
        try (Connection conn = BancoDados.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps1 = conn.prepareStatement("DELETE FROM ficha_pericias WHERE ficha_id = ?")) {
                    ps1.setLong(1, id);
                    ps1.executeUpdate();
                }
                try (PreparedStatement ps2 = conn.prepareStatement("DELETE FROM ficha_inventario WHERE ficha_id = ?")) {
                    ps2.setLong(1, id);
                    ps2.executeUpdate();
                }
                try (PreparedStatement ps3 = conn.prepareStatement("DELETE FROM ficha_monstros_domados WHERE ficha_dono_id = ? OR ficha_id = ?")) {
                    ps3.setLong(1, id);
                    ps3.setLong(2, id);
                    ps3.executeUpdate();
                }
                boolean deletou;
                try (PreparedStatement psFicha = conn.prepareStatement("DELETE FROM fichas WHERE id = ?")) {
                    psFicha.setLong(1, id);
                    deletou = psFicha.executeUpdate() > 0;
                }
                conn.commit();
                return deletou;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao excluir ficha no SQLite: ID " + id, e);
        }
    }

    private Ficha carregarFichaDoResultSet(Connection conn, ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String nome = rs.getString("nome");
        String racaNome = rs.getString("raca_nome");
        int vig = rs.getInt("vig");
        int forca = rs.getInt("forca");
        int des = rs.getInt("des");
        int men = rs.getInt("men");
        int esp = rs.getInt("esp");
        int von = rs.getInt("von");

        Raca raca = racaDAO.buscarPorNome(racaNome);
        if (raca == null) {
            raca = Raca.buscarPorNome(racaNome);
        }
        if (raca == null) {
            raca = Raca.HUMANO;
        }

        Ficha ficha = new Ficha(vig, forca, des, men, esp, von, raca);
        ficha.setId(id);
        ficha.setNome(nome);
        try {
            ficha.setUsuarioId(rs.getInt("usuario_id"));
        } catch (SQLException ignored) {}
        ficha.setHp(rs.getInt("hp"));
        ficha.setSanidade(rs.getInt("sanidade"));
        ficha.setEnergia(rs.getInt("energia"));
        ficha.setMana(rs.getInt("mana"));
        ficha.setPontosEssencia(rs.getInt("pontos_essencia"));
        try {
            ficha.setImagemUrl(rs.getString("imagem_url"));
        } catch (SQLException ignored) {}

        // Moedas físicas por denominação; fichas antigas (coluna vazia) são
        // migradas a partir do total em latão.
        String moedasFisicas = null;
        try {
            moedasFisicas = rs.getString("carteira_moedas");
        } catch (SQLException ignored) {}
        if (moedasFisicas != null && !moedasFisicas.isBlank()) {
            ficha.setCarteira(Carteira.deMoedasFisicas(moedasFisicas));
        } else {
            ficha.setCarteira(Carteira.deLatao(rs.getLong("carteira_latao")));
        }

        // Carregar Perícias
        String sqlPericias = "SELECT pericia, grau, maestria FROM ficha_pericias WHERE ficha_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlPericias)) {
            ps.setLong(1, id);
            try (ResultSet rsP = ps.executeQuery()) {
                while (rsP.next()) {
                    try {
                        Pericia p = Pericia.valueOf(rsP.getString("pericia"));
                        ficha.carregarPericiaDoBanco(p, rsP.getInt("grau"), rsP.getInt("maestria"));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }

        // Carregar Inventário
        String sqlInv = """
            SELECT i.id, i.nome, i.descricao, i.preco_latao, i.peso_kg, i.tipo, i.raridade, i.tier, fi.quantidade
            FROM ficha_inventario fi
            JOIN itens i ON fi.item_id = i.id
            WHERE fi.ficha_id = ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sqlInv)) {
            ps.setLong(1, id);
            try (ResultSet rsI = ps.executeQuery()) {
                while (rsI.next()) {
                    int itemId = rsI.getInt("id");
                    String itemNome = rsI.getString("nome");
                    String desc = rsI.getString("descricao");
                    long precoLatao = rsI.getLong("preco_latao");
                    double peso = rsI.getDouble("peso_kg");
                    Item.TipoItem tipo = Item.TipoItem.valueOf(rsI.getString("tipo"));
                    Item.RaridadeItem raridade = Item.RaridadeItem.valueOf(rsI.getString("raridade"));
                    int qtd = rsI.getInt("quantidade");

                    Item item = new Item(itemId, itemNome, desc, precoLatao / 10000.0, peso, tipo, raridade, qtd);
                    ficha.adicionarItem(item);
                }
            }
        }

        // Carregar Monstros Domados
        String sqlMonstros = "SELECT * FROM ficha_monstros_domados WHERE ficha_id = ?";
        try (PreparedStatement psM = conn.prepareStatement(sqlMonstros)) {
            psM.setLong(1, id);
            try (ResultSet rsM = psM.executeQuery()) {
                while (rsM.next()) {
                    int mId = rsM.getInt("id");
                    String mNome = rsM.getString("nome");
                    String mEspecie = rsM.getString("especie");
                    Monstro.PorteMonstro mPorte = Monstro.PorteMonstro.valueOf(rsM.getString("porte"));
                    boolean mDom = rsM.getInt("domesticavel") == 1;
                    int mVig = rsM.getInt("vig");
                    int mFor = rsM.getInt("forca");
                    int mDes = rsM.getInt("des");
                    int mMen = rsM.getInt("men");
                    int mEsp = rsM.getInt("esp");
                    int mVon = rsM.getInt("von");
                    int mHp = rsM.getInt("hp");
                    int mHpMax = rsM.getInt("hp_max");

                    Monstro monstro = new Monstro(mId, mNome, mEspecie, mPorte, mDom, mVig, mFor, mDes, mMen, mEsp, mVon);
                    monstro.setHpMax(mHpMax);
                    monstro.setHp(mHp);
                    ficha.adicionarMonstroDomado(monstro);
                }
            }
        }

        return ficha;
    }

    public static class PericiaDTO {
        public String pericia;
        public String nome;
        public String atributoBase;
        public int grau;
        public int maestria;
        public String descricao;
        public boolean personalizada;
    }

    public void salvarPericiaIndividual(long fichaId, String pericia, String nomeExibicao, String atributoBase, int grau, int maestria, String descricao) {
        String sql = """
            INSERT INTO ficha_pericias (ficha_id, pericia, grau, maestria, nome_exibicao, atributo_base, descricao)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(ficha_id, pericia) DO UPDATE SET
                grau = excluded.grau,
                maestria = excluded.maestria,
                nome_exibicao = excluded.nome_exibicao,
                atributo_base = excluded.atributo_base,
                descricao = excluded.descricao
        """;
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, fichaId);
            ps.setString(2, pericia);
            ps.setInt(3, grau);
            ps.setInt(4, maestria);
            ps.setString(5, nomeExibicao);
            ps.setString(6, atributoBase);
            ps.setString(7, descricao);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar perícia individual no SQLite", e);
        }
    }

    public boolean removerPericia(long fichaId, String pericia) {
        String sql = "DELETE FROM ficha_pericias WHERE ficha_id = ? AND pericia = ?";
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, fichaId);
            ps.setString(2, pericia);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao remover perícia no SQLite", e);
        }
    }

    public List<PericiaDTO> listarPericiasCompletas(long fichaId) {
        Map<String, PericiaDTO> mapa = new LinkedHashMap<>();

        // 1. Inicializa com as 15 perícias canônicas padrão
        for (Pericia p : Pericia.values()) {
            PericiaDTO dto = new PericiaDTO();
            dto.pericia = p.name();
            dto.nome = p.getNomeExibicao();
            dto.atributoBase = p.getAtributoBase().name();
            dto.grau = 0;
            dto.maestria = 0;
            dto.descricao = p.getDescricao();
            dto.personalizada = false;
            mapa.put(dto.pericia, dto);
        }

        // 2. Sobrepõe com valores do banco de dados e adiciona customizadas
        String sql = "SELECT pericia, grau, maestria, nome_exibicao, atributo_base, descricao FROM ficha_pericias WHERE ficha_id = ?";
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, fichaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String periciaKey = rs.getString("pericia");
                    int grau = rs.getInt("grau");
                    int maestria = rs.getInt("maestria");
                    String nomeExibicao = rs.getString("nome_exibicao");
                    String attrBase = rs.getString("atributo_base");
                    String desc = rs.getString("descricao");

                    if (mapa.containsKey(periciaKey)) {
                        PericiaDTO existente = mapa.get(periciaKey);
                        existente.grau = grau;
                        existente.maestria = maestria;
                        if (nomeExibicao != null && !nomeExibicao.isBlank()) existente.nome = nomeExibicao;
                        if (attrBase != null && !attrBase.isBlank()) existente.atributoBase = attrBase;
                        if (desc != null && !desc.isBlank()) existente.descricao = desc;
                    } else {
                        PericiaDTO custom = new PericiaDTO();
                        custom.pericia = periciaKey;
                        custom.nome = (nomeExibicao != null && !nomeExibicao.isBlank()) ? nomeExibicao : periciaKey;
                        custom.atributoBase = (attrBase != null && !attrBase.isBlank()) ? attrBase : "FOR";
                        custom.grau = grau;
                        custom.maestria = maestria;
                        custom.descricao = desc != null ? desc : "";
                        custom.personalizada = true;
                        mapa.put(periciaKey, custom);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao carregar perícias da ficha " + fichaId, e);
        }

        return new ArrayList<>(mapa.values());
    }
}
