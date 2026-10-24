package persistencia;

import economia.*;
import geografia.*;

import java.sql.*;
import java.util.*;

/**
 * Data Access Object (DAO) para persistência completa do mundo econômico e geográfico no SQLite.
 * Gerencia cidades, grafo de conexões/estradas, lojas, estoques, demandas e livro-caixa contábil.
 */
public class EconomiaDAO {

    private final ItemDAO itemDAO;

    public EconomiaDAO() {
        this.itemDAO = new ItemDAO();
    }

    public EconomiaDAO(ItemDAO itemDAO) {
        this.itemDAO = itemDAO != null ? itemDAO : new ItemDAO();
    }

    // =========================================================================
    // CIDADES
    // =========================================================================

    public void salvarCidade(Cidade cidade) {
        if (cidade == null) return;
        String sql = """
            INSERT INTO cidades (id, nome, tipo, regiao, geologia, aquifero, tem_porto, e_fronteira, regiao_fronteira)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                nome = excluded.nome,
                tipo = excluded.tipo,
                regiao = excluded.regiao,
                geologia = excluded.geologia,
                aquifero = excluded.aquifero,
                tem_porto = excluded.tem_porto,
                e_fronteira = excluded.e_fronteira,
                regiao_fronteira = excluded.regiao_fronteira;
        """;

        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cidade.getId());
            ps.setString(2, cidade.getNome());
            ps.setString(3, cidade.getTipo().name());
            ps.setString(4, cidade.getRegiao().name());
            ps.setString(5, cidade.getGeologia() != null ? cidade.getGeologia().name() : Geologia.PLANICIE.name());
            ps.setString(6, cidade.getAquifero() != null ? cidade.getAquifero().name() : Aquifero.RIO.name());
            ps.setInt(7, cidade.temPorto() ? 1 : 0);
            ps.setInt(8, cidade.isFronteira() ? 1 : 0);
            ps.setString(9, cidade.getRegiaofronteira() != null ? cidade.getRegiaofronteira().name() : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar cidade no SQLite: " + cidade.getNome(), e);
        }
    }

    public Cidade buscarCidadePorId(int id) {
        String sql = "SELECT * FROM cidades WHERE id = ?";
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return converterResultSetParaCidade(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar cidade por ID no SQLite: " + id, e);
        }
        return null;
    }

    public Cidade buscarCidadePorNome(String nome) {
        if (nome == null) return null;
        String sql = "SELECT * FROM cidades WHERE UPPER(nome) = UPPER(?)";
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nome.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return converterResultSetParaCidade(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar cidade por nome no SQLite: " + nome, e);
        }
        return null;
    }

    public List<Cidade> listarCidades() {
        List<Cidade> lista = new ArrayList<>();
        String sql = "SELECT * FROM cidades ORDER BY id ASC";
        try (Connection conn = BancoDados.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(converterResultSetParaCidade(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar cidades do SQLite", e);
        }
        return lista;
    }

    private Cidade converterResultSetParaCidade(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String nome = rs.getString("nome");
        Cidade.TipoAssentamento tipo = Cidade.TipoAssentamento.valueOf(rs.getString("tipo"));
        Regiao regiao = Regiao.valueOf(rs.getString("regiao"));
        Geologia geologia = Geologia.valueOf(rs.getString("geologia"));
        Aquifero aquifero = Aquifero.valueOf(rs.getString("aquifero"));
        boolean porto = rs.getInt("tem_porto") == 1;
        boolean fronteira = rs.getInt("e_fronteira") == 1;
        String regFronteiraStr = rs.getString("regiao_fronteira");
        Regiao regFronteira = regFronteiraStr != null ? Regiao.valueOf(regFronteiraStr) : null;

        return new Cidade(id, nome, tipo, regiao, geologia, aquifero, porto, fronteira, regFronteira);
    }

    // =========================================================================
    // CONEXÕES / GRAFO VIÁRIO
    // =========================================================================

    public void salvarConexao(Conexao conexao) {
        if (conexao == null || conexao.getOrigem() == null || conexao.getDestino() == null) return;
        String sql = """
            INSERT INTO conexoes (origem_id, destino_id, distancia_km, perigo, tipo_rota, bloqueada)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, conexao.getOrigem().getId());
            ps.setInt(2, conexao.getDestino().getId());
            ps.setInt(3, conexao.getDistanciaKm());
            ps.setInt(4, conexao.getPerigo());
            ps.setString(5, conexao.getTipo().name());
            ps.setInt(6, conexao.isBloqueada() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar conexão no SQLite: " + conexao, e);
        }
    }

    public List<Conexao> listarConexoes(Map<Integer, Cidade> cidadesMap) {
        List<Conexao> lista = new ArrayList<>();
        String sql = "SELECT * FROM conexoes ORDER BY id ASC";
        try (Connection conn = BancoDados.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int origId = rs.getInt("origem_id");
                int destId = rs.getInt("destino_id");
                Cidade orig = cidadesMap.get(origId);
                Cidade dest = cidadesMap.get(destId);
                if (orig != null && dest != null) {
                    int dist = rs.getInt("distancia_km");
                    int perigo = rs.getInt("perigo");
                    Conexao.TipoDeRota tipo = Conexao.TipoDeRota.valueOf(rs.getString("tipo_rota"));
                    boolean bloqueada = rs.getInt("bloqueada") == 1;

                    Conexao c = new Conexao(orig, dest, dist, perigo, tipo);
                    if (bloqueada) c.bloquear();
                    lista.add(c);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar conexões do SQLite", e);
        }
        return lista;
    }

    // =========================================================================
    // LOJAS E ESTABELECIMENTOS
    // =========================================================================

    public void salvarLoja(Lojas loja) {
        if (loja == null || loja.getCidadeSede() == null) return;

        try (Connection conn = BancoDados.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (loja.getId() == 0) {
                    String sql = """
                        INSERT INTO lojas (nome, mercador, cidade_id, tipo, e_rede, nome_rede, caixa_latao)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                    """;
                    try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, loja.getNome());
                        ps.setString(2, loja.getMercador());
                        ps.setInt(3, loja.getCidadeSede().getId());
                        ps.setString(4, loja.getTipoEstabelecimento().name());
                        ps.setInt(5, loja.isLojaDeRede() ? 1 : 0);
                        ps.setString(6, loja.getNomeDaRede());
                        ps.setLong(7, loja.getCaixa() != null ? loja.getCaixa().getValorTotalEmLatao() : 0L);
                        ps.executeUpdate();
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            if (rs.next()) {
                                loja.setId(rs.getInt(1));
                            }
                        }
                    }
                } else {
                    String sql = """
                        UPDATE lojas SET
                            nome = ?, mercador = ?, cidade_id = ?, tipo = ?,
                            e_rede = ?, nome_rede = ?, caixa_latao = ?
                        WHERE id = ?
                    """;
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, loja.getNome());
                        ps.setString(2, loja.getMercador());
                        ps.setInt(3, loja.getCidadeSede().getId());
                        ps.setString(4, loja.getTipoEstabelecimento().name());
                        ps.setInt(5, loja.isLojaDeRede() ? 1 : 0);
                        ps.setString(6, loja.getNomeDaRede());
                        ps.setLong(7, loja.getCaixa() != null ? loja.getCaixa().getValorTotalEmLatao() : 0L);
                        ps.setInt(8, loja.getId());
                        ps.executeUpdate();
                    }
                }

                // Sincronizar Estoque e Demanda
                salvarEstoqueLoja(conn, loja);

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar loja no SQLite: " + loja.getNome(), e);
        }
    }

    private void salvarEstoqueLoja(Connection conn, Lojas loja) throws SQLException {
        String sqlDelete = "DELETE FROM loja_estoque WHERE loja_id = ?";
        try (PreparedStatement psDel = conn.prepareStatement(sqlDelete)) {
            psDel.setInt(1, loja.getId());
            psDel.executeUpdate();
        }

        Map<Integer, Integer> quantidades = new LinkedHashMap<>();
        for (Item it : loja.getEstoque()) {
            quantidades.put(it.getId(), it.getQuantidade());
        }

        Map<Integer, Integer> demandas = loja.getDemandaPorItem();
        Set<Integer> todosItens = new LinkedHashSet<>(quantidades.keySet());
        todosItens.addAll(demandas.keySet());

        if (todosItens.isEmpty()) return;

        String sqlIns = "INSERT INTO loja_estoque (loja_id, item_id, quantidade, demanda) VALUES (?, ?, ?, ?)";
        try (PreparedStatement psIns = conn.prepareStatement(sqlIns)) {
            for (Integer itemId : todosItens) {
                if (itemId != null && itemId > 0) {
                    int qtd = quantidades.getOrDefault(itemId, 0);
                    int dem = demandas.getOrDefault(itemId, 0);
                    psIns.setInt(1, loja.getId());
                    psIns.setInt(2, itemId);
                    psIns.setInt(3, qtd);
                    psIns.setInt(4, dem);
                    psIns.addBatch();
                }
            }
            psIns.executeBatch();
        }
    }

    public List<Lojas> listarLojas(Map<Integer, Cidade> cidadesMap) {
        List<Lojas> lista = new ArrayList<>();
        String sql = "SELECT * FROM lojas ORDER BY id ASC";
        try (Connection conn = BancoDados.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String nome = rs.getString("nome");
                String mercador = rs.getString("mercador");
                int cidadeId = rs.getInt("cidade_id");
                Lojas.TipoEstabelecimento tipo = Lojas.TipoEstabelecimento.valueOf(rs.getString("tipo"));
                boolean rede = rs.getInt("e_rede") == 1;
                String nomeRede = rs.getString("nome_rede");
                long caixaLatao = rs.getLong("caixa_latao");

                Cidade cidade = cidadesMap.get(cidadeId);
                Lojas loja = new Lojas(nome, mercador, cidade, rede, nomeRede, tipo);
                loja.setId(id);
                loja.setCaixa(Carteira.deLatao(caixaLatao));

                carregarEstoqueEDemanda(conn, loja);
                lista.add(loja);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar lojas do SQLite", e);
        }
        return lista;
    }

    private void carregarEstoqueEDemanda(Connection conn, Lojas loja) throws SQLException {
        String sql = """
            SELECT le.item_id, le.quantidade, le.demanda, i.*
            FROM loja_estoque le
            JOIN itens i ON le.item_id = i.id
            WHERE le.loja_id = ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, loja.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int itemId = rs.getInt("item_id");
                    int qtd = rs.getInt("quantidade");
                    int dem = rs.getInt("demanda");

                    String nome = rs.getString("nome");
                    String desc = rs.getString("descricao");
                    long precoLatao = rs.getLong("preco_latao");
                    double peso = rs.getDouble("peso_kg");
                    Item.TipoItem tipo = Item.TipoItem.valueOf(rs.getString("tipo"));
                    Item.RaridadeItem raridade = Item.RaridadeItem.valueOf(rs.getString("raridade"));
                    int tier = rs.getInt("tier");

                    Item item = new Item(itemId, nome, desc, precoLatao / 10000.0, peso, tipo, raridade, qtd);
                    if (qtd > 0) {
                        loja.adicionarItem(item);
                    }
                    loja.definirDemanda(item, dem);
                }
            }
        }
    }

    // =========================================================================
    // LIVRO-CAIXA / AUDITORIA CONTÁBIL
    // =========================================================================

    public void registrarMovimentacaoCaixa(int lojaId, int dia, int hora, String tipoOperacao,
                                           String itemNome, int quantidade, long valorLatao, String descricao) {
        String sql = """
            INSERT INTO livro_caixa_movimentacoes (loja_id, dia_mundo, hora_mundo, tipo_operacao, item_nome, quantidade, valor_latao, descricao)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lojaId);
            ps.setInt(2, dia);
            ps.setInt(3, hora);
            ps.setString(4, tipoOperacao);
            ps.setString(5, itemNome);
            ps.setInt(6, quantidade);
            ps.setLong(7, valorLatao);
            ps.setString(8, descricao);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao registrar livro-caixa no SQLite para loja ID " + lojaId, e);
        }
    }

    public List<String> listarMovimentacoesFormatadas(int lojaId) {
        List<String> logs = new ArrayList<>();
        String sql = "SELECT * FROM livro_caixa_movimentacoes WHERE loja_id = ? ORDER BY id DESC LIMIT 50";
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lojaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int dia = rs.getInt("dia_mundo");
                    int hora = rs.getInt("hora_mundo");
                    String op = rs.getString("tipo_operacao");
                    String item = rs.getString("item_nome");
                    int qtd = rs.getInt("quantidade");
                    long valor = rs.getLong("valor_latao");
                    String desc = rs.getString("descricao");

                    logs.add(String.format("[Dia %d, %02dh] %s - %dx %s (%s) | %s",
                            dia, hora, op, qtd, item, Carteira.formatarLatao(valor), desc));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar movimentações do livro-caixa", e);
        }
        return logs;
    }

    // =========================================================================
    // POPULAR CENÁRIO PADRÃO CANÔNICO SE VAZIO
    // =========================================================================

    public void popularCenarioPadraoSeVazio() {
        itemDAO.popularItensPadraoSeVazio();

        boolean vazio = false;
        String sqlCount = "SELECT COUNT(*) FROM cidades";
        try (Connection conn = BancoDados.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sqlCount)) {
            if (rs.next() && rs.getInt(1) == 0) {
                vazio = true;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao verificar cidades no SQLite", e);
        }

        if (vazio) {
            // 1. Cidades Canônicas
                Cidade frostdale = new Cidade(1, "Frostdale", Cidade.TipoAssentamento.CIDADE, Regiao.TUNDRA, Geologia.FLORESTA, Aquifero.OCEANO_AGUAS_COSTEIRAS, true, false, null);
                Cidade ironhold = new Cidade(2, "Ironhold", Cidade.TipoAssentamento.FORTALEZA, Regiao.TEMPERADO, Geologia.MONTANHA, Aquifero.RIO, false, false, null);
                Cidade valdorian = new Cidade(3, "Valdorian", Cidade.TipoAssentamento.REINO, Regiao.TEMPERADO, Geologia.PLANICIE, Aquifero.RIO, false, false, null);
                Cidade oakhaven = new Cidade(4, "Porto Oakhaven", Cidade.TipoAssentamento.CIDADE, Regiao.TEMPERADO, Geologia.FLORESTA, Aquifero.OCEANO_AGUAS_COSTEIRAS, true, false, null);
                Cidade sunreach = new Cidade(5, "Sunreach", Cidade.TipoAssentamento.CIDADE, Regiao.DESERTO, Geologia.CAVERNA, Aquifero.OCEANO_AGUAS_PROFUNDAS, true, true, Regiao.SAVANA);

                salvarCidade(frostdale);
                salvarCidade(ironhold);
                salvarCidade(valdorian);
                salvarCidade(oakhaven);
                salvarCidade(sunreach);

                // 2. Conexões Viárias
                salvarConexao(new Conexao(frostdale, ironhold, 120, 4, Conexao.TipoDeRota.TERRESTRE));
                salvarConexao(new Conexao(ironhold, valdorian, 80, 2, Conexao.TipoDeRota.TERRESTRE));
                salvarConexao(new Conexao(valdorian, oakhaven, 90, 2, Conexao.TipoDeRota.TERRESTRE));
                salvarConexao(new Conexao(frostdale, oakhaven, 160, 3, Conexao.TipoDeRota.MARITIMA));
                salvarConexao(new Conexao(valdorian, sunreach, 240, 6, Conexao.TipoDeRota.TERRESTRE));
                salvarConexao(new Conexao(oakhaven, sunreach, 210, 5, Conexao.TipoDeRota.MARITIMA));

                // 3. Catálogo de Itens
                Item ferroLingote = itemDAO.buscarPorId(1);
                Item espadaFerro = itemDAO.buscarPorId(2);
                Item pocaoCura = itemDAO.buscarPorId(3);
                Item mantoSeda = itemDAO.buscarPorId(4);
                Item gemaPolida = itemDAO.buscarPorId(5);
                Item madeiraNobre = itemDAO.buscarPorId(6);
                Item racaoViagem = itemDAO.buscarPorId(7);
                Item hidromel = itemDAO.buscarPorId(8);

                // 4. Lojas de Ironhold
                Lojas forjaIronhold = new Lojas("Forja do Martelo Rubro", "Torvald Martelo-de-Ferro", ironhold, false, null, Lojas.TipoEstabelecimento.FORJA);
                forjaIronhold.setCaixa(new Carteira(150));
                forjaIronhold.adicionarItem(Lojas.clonarItem(ferroLingote, 25));
                forjaIronhold.adicionarItem(Lojas.clonarItem(espadaFerro, 8));
                forjaIronhold.definirDemanda(ferroLingote, 30);
                forjaIronhold.definirDemanda(espadaFerro, 10);
                salvarLoja(forjaIronhold);

                Lojas redeNorteIronhold = new Lojas("Empório da Rede do Norte", "Garrick Mercador", ironhold, true, "Liga Mercantil do Norte", Lojas.TipoEstabelecimento.LOJA_GERAL);
                redeNorteIronhold.setCaixa(new Carteira(80));
                redeNorteIronhold.adicionarItem(Lojas.clonarItem(pocaoCura, 12));
                redeNorteIronhold.adicionarItem(Lojas.clonarItem(madeiraNobre, 15));
                redeNorteIronhold.definirDemanda(pocaoCura, 15);
                redeNorteIronhold.definirDemanda(ferroLingote, 20);
                salvarLoja(redeNorteIronhold);

                // 5. Lojas de Valdorian
                Lojas redeNorteValdorian = new Lojas("Armazém Central da Rede", "Mestre Elidor", valdorian, true, "Liga Mercantil do Norte", Lojas.TipoEstabelecimento.LOJA_GERAL);
                redeNorteValdorian.setCaixa(new Carteira(200));
                redeNorteValdorian.adicionarItem(Lojas.clonarItem(ferroLingote, 40));
                redeNorteValdorian.adicionarItem(Lojas.clonarItem(espadaFerro, 15));
                redeNorteValdorian.definirDemanda(ferroLingote, 10);
                salvarLoja(redeNorteValdorian);

                Lojas boticaValdorian = new Lojas("Botica das Ervas Místicas", "Alquimista Serena", valdorian, false, null, Lojas.TipoEstabelecimento.ALQUIMIA);
                boticaValdorian.setCaixa(new Carteira(100));
                boticaValdorian.adicionarItem(Lojas.clonarItem(pocaoCura, 30));
                boticaValdorian.definirDemanda(pocaoCura, 20);
                salvarLoja(boticaValdorian);

                Lojas alfaiatariaValdorian = new Lojas("Alfaiataria Seda Dourada", "Madamme Vivienne", valdorian, false, null, Lojas.TipoEstabelecimento.ALFAIATARIA);
                alfaiatariaValdorian.setCaixa(new Carteira(120));
                alfaiatariaValdorian.adicionarItem(Lojas.clonarItem(mantoSeda, 10));
                alfaiatariaValdorian.definirDemanda(mantoSeda, 8);
                salvarLoja(alfaiatariaValdorian);

                // 6. Lojas de Frostdale
                Lojas tavernaFrostdale = new Lojas("Taverna do Lobo Uivante", "Olaf Barba-de-Gelo", frostdale, false, null, Lojas.TipoEstabelecimento.TAVERNA);
                tavernaFrostdale.setCaixa(new Carteira(90));
                tavernaFrostdale.adicionarItem(Lojas.clonarItem(racaoViagem, 40));
                tavernaFrostdale.adicionarItem(Lojas.clonarItem(hidromel, 25));
                tavernaFrostdale.definirDemanda(racaoViagem, 30);
                tavernaFrostdale.definirDemanda(hidromel, 20);
                salvarLoja(tavernaFrostdale);

                // 7. Lojas de Sunreach
                Lojas bazarSunreach = new Lojas("Bazar das Mil Areias", "Nadim Al-Mansur", sunreach, false, null, Lojas.TipoEstabelecimento.JOALHERIA);
                bazarSunreach.setCaixa(new Carteira(300));
                bazarSunreach.adicionarItem(Lojas.clonarItem(gemaPolida, 14));
                bazarSunreach.definirDemanda(gemaPolida, 10);
                bazarSunreach.definirDemanda(espadaFerro, 20);
                salvarLoja(bazarSunreach);
        }
    }

    /**
     * Carrega todas as cidades, conexões e lojas do banco SQLite montando um MotorEconomico completo.
     */
    public MotorEconomico carregarMotorEconomicoCompleto() {
        popularCenarioPadraoSeVazio();

        MotorEconomico motor = new MotorEconomico();
        List<Cidade> cidades = listarCidades();
        Map<Integer, Cidade> cidadesMap = new LinkedHashMap<>();

        for (Cidade c : cidades) {
            cidadesMap.put(c.getId(), c);
            motor.registrarCidade(c);
        }

        listarConexoes(cidadesMap);
        listarLojas(cidadesMap);

        return motor;
    }
}
