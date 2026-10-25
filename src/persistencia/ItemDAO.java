package persistencia;

import economia.Item;
import economia.TamanhoItem;
import geografia.Aquifero;
import geografia.Geologia;
import geografia.Regiao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object (DAO) para itens do catálogo canônico no SQLite.
 * Suporta o catálogo de 55+ itens canônicos em Latão, proveniência geográfica
 * (Região, Geologia, Aquífero) e volumetria de mochila (TamanhoItem).
 */
public class ItemDAO {

    public static final String CHAVE_SEMENTE_ITENS = "seed_55_itens_canonicos_v5";

    public void salvar(Item item) {
        if (item == null) return;

        String sql = """
            INSERT INTO itens (
                id, nome, descricao, preco_latao, peso_kg, tipo, raridade, tier,
                regiao_nativa, geologia_nativa, aquifero_nativo, preco_base_latao, peso
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                nome = excluded.nome,
                descricao = excluded.descricao,
                preco_latao = excluded.preco_latao,
                peso_kg = excluded.peso_kg,
                tipo = excluded.tipo,
                raridade = excluded.raridade,
                tier = excluded.tier,
                regiao_nativa = excluded.regiao_nativa,
                geologia_nativa = excluded.geologia_nativa,
                aquifero_nativo = excluded.aquifero_nativo,
                preco_base_latao = excluded.preco_base_latao,
                peso = excluded.peso;
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
            ps.setString(9, item.getRegiaoOrigem() != null ? item.getRegiaoOrigem().name() : null);
            ps.setString(10, item.getGeologiaOrigem() != null ? item.getGeologiaOrigem().name() : null);
            ps.setString(11, item.getAquiferoOrigem() != null ? item.getAquiferoOrigem().name() : null);
            ps.setLong(12, item.getPrecoEmLatao());
            ps.setDouble(13, item.getPeso());
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

    public Item buscarPorNome(String nome) {
        if (nome == null || nome.trim().isEmpty()) return null;
        String sql = "SELECT * FROM itens WHERE LOWER(nome) = LOWER(?) LIMIT 1";
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nome.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return converterResultSetParaItem(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar item por nome no SQLite: " + nome, e);
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
        popularCatalogoCanonico55Itens(false);
    }

    public synchronized void popularCatalogoCanonico55Itens(boolean forcarAtualizacao) {
        if (!forcarAtualizacao && BancoDados.sementeAplicada(CHAVE_SEMENTE_ITENS)) {
            return;
        }

        int totalExistente = 0;
        try (Connection conn = BancoDados.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM itens")) {
            if (rs.next()) {
                totalExistente = rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao checar contagem de itens", e);
        }

        if (!forcarAtualizacao && totalExistente >= 55) {
            BancoDados.marcarSemente(CHAVE_SEMENTE_ITENS);
            return;
        }

        // Definição dos dados canônicos dos 57 itens balanceados em Latão
        Object[][] catalogo = {
            // 1. MATERIAIS E INSUMOS CONCRETOS (Q1 a Q5)
            {1, "Minério de Ferro de Ironhold (Q1)", "Minério bruto extraído das jazidas profundas.", "MATERIAL", "COMUM", 1, 2.0, 1500L, "TEMPERADO", "MONTANHA", "RIO"},
            {2, "Lingote de Ferro Forjado (Q2)", "Metal denso trabalhado nas forjas de Ironhold.", "MATERIAL", "COMUM", 1, 1.5, 3500L, "TEMPERADO", "MONTANHA", "RIO"},
            {3, "Lingote de Aço Temperado de Ironhold (Q3)", "Aço de alto teor de carbono, resfriado a óleo.", "MATERIAL", "INCOMUM", 2, 1.5, 12000L, "TEMPERADO", "MONTANHA", "RIO"},
            {4, "Lingote de Mithril da Profundeza (Q4)", "Metal prateado ultra-leve de alta condutividade.", "MATERIAL", "RARO", 3, 0.8, 65000L, "TEMPERADO", "CAVERNA", "RIO"},
            {5, "Lingote de Adamante Ancestral (Q5)", "O mineral mais rígido de Thaluria.", "MATERIAL", "EPICO", 4, 2.5, 250000L, "TEMPERADO", "MONTANHA", "OCEANO_AGUAS_PROFUNDAS"},
            {6, "Tora de Pinho Boreal de Frostdale (Q2)", "Madeira resistente às geadas perpétuas.", "MATERIAL", "COMUM", 1, 2.0, 2000L, "TUNDRA", "FLORESTA", "OCEANO_AGUAS_COSTEIRAS"},
            {7, "Madeira de Carvalho Antigo de Oakhaven (Q3)", "Cerne de carvalho com séculos de cura natural.", "MATERIAL", "INCOMUM", 2, 2.0, 8500L, "TEMPERADO", "FLORESTA", "OCEANO_AGUAS_COSTEIRAS"},
            {8, "Cerne de Árvore-Mundo de Sylvanor (Q5)", "Madeira lendária pulsante de energia natural.", "MATERIAL", "LENDARIO", 5, 1.2, 180000L, "TEMPERADO", "FLORESTA", "RIO"},
            {9, "Couro Batido de Planície (Q1)", "Couro flexível tratado em curtume artesanal.", "MATERIAL", "COMUM", 1, 1.0, 1800L, "TEMPERADO", "PLANICIE", "RIO"},
            {10, "Pelagem Branca Boreal (Q2)", "Pele espessa com isolamento térmico extremo.", "MATERIAL", "COMUM", 1, 1.2, 4500L, "TUNDRA", "FLORESTA", "OCEANO_AGUAS_COSTEIRAS"},
            {11, "Quitina Mineral de Ironhold (Q3)", "Carapaça fóssil com dureza comparável ao ferro.", "MATERIAL", "INCOMUM", 2, 2.2, 18000L, "TEMPERADO", "MONTANHA", "RIO"},
            {12, "Escama Dracônica Rubra (Q5)", "Escama pura com resistência térmica lendária.", "MATERIAL", "LENDARIO", 5, 1.8, 320000L, "DESERTO", "MONTANHA", "OCEANO_AGUAS_PROFUNDAS"},
            {13, "Linho Fino de Valdorian (Q2)", "Tecido nobre de fios alinhados e confortáveis.", "MATERIAL", "COMUM", 1, 0.4, 2500L, "TEMPERADO", "PLANICIE", "RIO"},
            {14, "Seda Élfica Luminescente (Q4)", "Fibras brilhantes com propriedades dissipadoras.", "MATERIAL", "RARO", 3, 0.2, 48000L, "TEMPERADO", "FLORESTA", "RIO"},
            {15, "Erva-de-Sangue de Valdorian (Q2)", "Planta medicinal comum nos vales fluviais.", "MATERIAL", "COMUM", 1, 0.1, 1200L, "TEMPERADO", "PLANICIE", "RIO"},
            {16, "Lótus Glacial de Frostdale (Q4)", "Flor rara colhida nas fendas de gelo eterno.", "MATERIAL", "RARO", 3, 0.1, 35000L, "TUNDRA", "VALE", "LAGO_PEQUENO"},
            {17, "Cristal Solar de Sunreach (Q3)", "Gema amarela que absorve e irradia calor.", "MATERIAL", "RARO", 3, 0.2, 28000L, "DESERTO", "CAVERNA", "OCEANO_AGUAS_PROFUNDAS"},
            {18, "Diamante Astral de Sunreach (Q5)", "Mineral estelar mítico com ressonância perfeita.", "MATERIAL", "MITICO", 6, 0.1, 500000L, "DESERTO", "CAVERNA", "OCEANO_AGUAS_PROFUNDAS"},
            {19, "Carne de Caça Defumada (Q2)", "Provisão durável para jornadas terrestres.", "MATERIAL", "COMUM", 1, 0.5, 800L, "TEMPERADO", "FLORESTA", "RIO"},
            {20, "Costela Real Condicionada para Doma (Q4)", "Isca especial com feromônios de atração.", "MATERIAL", "RARO", 3, 1.0, 15000L, "TEMPERADO", "PLANICIE", "RIO"},

            // 2. ARMAS BRANCAS CANÔNICAS (33 Subtipos)
            {21, "Adaga de Aço de Valdorian", "Lâmina curta perfurante (1M | 1d4 | DES).", "ARMA_BRANCA", "COMUM", 1, 1.0, 8000L, "TEMPERADO", "PLANICIE", "RIO"},
            {22, "Punhal Ritual de Obsidiana", "Foco de perfuração e arcanismo (1M | 1d4 | DES/MEN).", "ARMA_BRANCA", "RARO", 3, 1.0, 42000L, "DESERTO", "CAVERNA", "OCEANO_AGUAS_PROFUNDAS"},
            {23, "Espada Curta da Guarda de Oakhaven", "Versátil para infantaria leve (1M | 1d6 | DES/FOR).", "ARMA_BRANCA", "COMUM", 1, 2.0, 14000L, "TEMPERADO", "FLORESTA", "OCEANO_AGUAS_COSTEIRAS"},
            {24, "Espada Longa de Ironhold", "Lâmina militar canônica (1M | 1d8 | FOR).", "ARMA_BRANCA", "INCOMUM", 2, 3.0, 25000L, "TEMPERADO", "MONTANHA", "RIO"},
            {25, "Espada Bastarda do Cruzado", "Equilíbrio e alcance versátil (1/2M | 1d10 | FOR).", "ARMA_BRANCA", "INCOMUM", 2, 3.5, 38000L, "TEMPERADO", "PLANICIE", "RIO"},
            {26, "Montante Zweihänder Quebra-Linhas", "Impacto maciço de infantaria pesada (2M | 2d6 | FOR).", "ARMA_BRANCA", "RARO", 3, 6.5, 68000L, "TEMPERADO", "MONTANHA", "RIO"},
            {27, "Rapiera Dourada de Valdorian", "Finesse e precisão estocadora (1M | 1d8 | DES).", "ARMA_BRANCA", "INCOMUM", 2, 1.8, 32000L, "TEMPERADO", "PLANICIE", "RIO"},
            {28, "Cimitarra das Dunas de Sunreach", "Corte curvado rápido (1M | 1d6 | DES).", "ARMA_BRANCA", "INCOMUM", 2, 2.4, 29000L, "DESERTO", "CAVERNA", "OCEANO_AGUAS_PROFUNDAS"},
            {29, "Katana Vento Cortante", "Aço dobrado de corte extremo (1/2M | 1d10 | DES).", "ARMA_BRANCA", "RARO", 3, 2.2, 85000L, "TEMPERADO", "FLORESTA", "RIO"},
            {30, "Machado de Batalha Anão", "Corte e rachadura pesada (1M | 1d8 | FOR).", "ARMA_BRANCA", "INCOMUM", 2, 3.8, 27000L, "TEMPERADO", "MONTANHA", "RIO"},
            {31, "Machado Grande de Carnificina Boreal", "Dano bruto devastador (2M | 1d12 | FOR).", "ARMA_BRANCA", "RARO", 3, 7.0, 62000L, "TUNDRA", "FLORESTA", "OCEANO_AGUAS_COSTEIRAS"},
            {32, "Alabarda Imperial de Guarda", "Alcance tático e anticarregamento (2M | 1d10 | FOR).", "ARMA_BRANCA", "INCOMUM", 2, 6.0, 45000L, "TEMPERADO", "PLANICIE", "RIO"},
            {33, "Maça Estrela Esmaga-Crânios", "Impacto perfurante perfura armaduras (1M | 1d8 | FOR).", "ARMA_BRANCA", "INCOMUM", 2, 3.5, 24000L, "TEMPERADO", "MONTANHA", "RIO"},
            {34, "Martelo de Guerra da Bigorna", "Concussão maciça anti-placas (1M | 1d8 | FOR).", "ARMA_BRANCA", "RARO", 3, 4.5, 54000L, "TEMPERADO", "MONTANHA", "RIO"},
            {35, "Malho Pesado Sísmico (2 Mãos)", "Quebra de ossos e escudos (2M | 2d6 | FOR).", "ARMA_BRANCA", "EPICO", 4, 9.0, 115000L, "TEMPERADO", "MONTANHA", "RIO"},
            {36, "Lança Longa de Falange", "Formação tática defensiva (2M | 1d10 | FOR).", "ARMA_BRANCA", "COMUM", 1, 4.0, 16000L, "TEMPERADO", "PLANICIE", "RIO"},
            {37, "Tridente das Marés de Oakhaven", "Arma de pescadores de alto mar (1/2M | 1d8 | FOR/DES).", "ARMA_BRANCA", "RARO", 3, 3.5, 58000L, "TEMPERADO", "FLORESTA", "OCEANO_AGUAS_COSTEIRAS"},
            {38, "Manopla de Impacto Cestus de Ferro", "Combate corporal contundente (1M | 1d6 | FOR/VIG).", "ARMA_BRANCA", "COMUM", 1, 1.5, 11000L, "TEMPERADO", "MONTANHA", "RIO"},

            // 3. ARMAS DE LONGO ALCANCE E PROJÉTEIS
            {39, "Arco Curto de Caçador (Amplif. 1.20x)", "Cadência rápida para bosques (2M | Mult 1.20x | 30m).", "ARMA_LONGO_ALCANCE", "COMUM", 1, 1.5, 15000L, "TEMPERADO", "FLORESTA", "RIO"},
            {40, "Arco Longo Composto de Sylvanor (Amplif. 1.50x)", "Tração potente para franco-atiradores (2M | Mult 1.50x | 60m).", "ARMA_LONGO_ALCANCE", "RARO", 3, 2.5, 52000L, "TEMPERADO", "FLORESTA", "RIO"},
            {41, "Besta Leve de Repetição (Amplif. 1.35x)", "Mecanismo mecânico ágil (1/2M | Mult 1.35x | 40m).", "ARMA_LONGO_ALCANCE", "INCOMUM", 2, 3.0, 34000L, "TEMPERADO", "MONTANHA", "RIO"},
            {42, "Besta Pesada de Cerco de Ironhold (Amplif. 1.85x)", "Potência de penetração máxima (2M | Mult 1.85x | 80m).", "ARMA_LONGO_ALCANCE", "EPICO", 4, 6.5, 95000L, "TEMPERADO", "MONTANHA", "RIO"},
            {43, "Zarabatana Silenciosa do Pântano (Amplif. 1.00x)", "Foco em aplicação furtiva de toxinas (1M | Mult 1.00x | 15m).", "ARMA_LONGO_ALCANCE", "INCOMUM", 2, 0.5, 12000L, "TROPICAL", "FLORESTA", "PANTANO"},
            {44, "Aljava de Flechas de Aço (20x - 1d6)", "Pontas perfurantes padrão em feixe.", "PROJETIL", "COMUM", 1, 1.0, 2500L, "TEMPERADO", "MONTANHA", "RIO"},
            {45, "Virotes Perfurantes de Mithril (20x - 1d10)", "Projéteis cinéticos anti-armadura.", "PROJETIL", "RARO", 3, 1.5, 18000L, "TEMPERADO", "MONTANHA", "RIO"},

            // 4. ARMADURAS MULTI-SLOT E ESCUDOS
            {46, "Elmo Fechado de Justa [CABECA+ROSTO+PESCOCO]", "Proteção craniana total com viseira reforçada.", "ARMADURA", "RARO", 3, 3.5, 42000L, "TEMPERADO", "MONTANHA", "RIO"},
            {47, "Couraça de Placas de Ironhold [PEITO+COSTAS]", "Placa peitoral curvada com deflexão de estocadas.", "ARMADURA", "RARO", 3, 10.0, 98000L, "TEMPERADO", "MONTANHA", "RIO"},
            {48, "Cota de Malha Valdoriana [PEITO+COSTAS+BRACOS+CINTURA]", "Elos rebitados de alta flexibilidade e cobertura.", "ARMADURA", "INCOMUM", 2, 8.0, 55000L, "TEMPERADO", "PLANICIE", "RIO"},
            {49, "Manoplas Articuladas de Aço [MAOS]", "Proteção de dedos e punho para combate corpo a corpo.", "ARMADURA", "INCOMUM", 2, 1.5, 22000L, "TEMPERADO", "MONTANHA", "RIO"},
            {50, "Grevas e Joelheiras de Infantaria [JOELHOS+PES]", "Chapas articuladas para membros inferiores.", "ARMADURA", "INCOMUM", 2, 3.0, 26000L, "TEMPERADO", "MONTANHA", "RIO"},
            {51, "Broquel de Duelista (HP 40 | Segurar 12)", "Escudo leve para aparo rápido (1M | HP 40 | Teto 12).", "ESCUDO", "COMUM", 1, 2.0, 14000L, "TEMPERADO", "PLANICIE", "RIO"},
            {52, "Escudo Torre Baluarte de Ironhold (2M | HP 150 | Segurar 35)", "Paredão blindado portátil (2M | HP 150 | Teto 35).", "ESCUDO", "EPICO", 4, 12.0, 110000L, "TEMPERADO", "MONTANHA", "RIO"},

            // 5. ACESSÓRIOS, CONSUMÍVEIS, GRIMÓRIO E COLOSSAL
            {53, "Anel do Sangue Furioso [ANEIS]", "Dispara Fúria (+100% dano) quando HP < 25%.", "ACESSORIO", "RARO", 3, 0.1, 65000L, "DESERTO", "CAVERNA", "OCEANO_AGUAS_PROFUNDAS"},
            {54, "Monóculo Tático do Artífice [OLHOS]", "Concede leitura instantânea de fraquezas e RD.", "ACESSORIO", "RARO", 3, 0.1, 48000L, "TEMPERADO", "MONTANHA", "RIO"},
            {55, "Bigorna Ancestral de Forja Móvel [COLOSSAL 30.0 Slots]", "Equipamento de forja monumental. Exige Besta de Carga GRANDE/COLOSSAL.", "OUTRO", "EPICO", 4, 85.0, 190000L, "TEMPERADO", "MONTANHA", "RIO"},
            {56, "Poção de Restauração Vital Maior", "Elixir alquímico avançado de restauração acelerada de HP.", "CONSUMIVEL", "INCOMUM", 2, 0.5, 9500L, "TEMPERADO", "PLANICIE", "RIO"},
            {57, "Grimório de Couro Rúnico (Nv. 3 | 12 Páginas)", "Tomo de couro com páginas encantadas para grafiturgia elemental.", "MAGICO", "RARO", 3, 2.0, 75000L, "DESERTO", "CAVERNA", "OCEANO_AGUAS_PROFUNDAS"}
        };

        String sql = """
            INSERT INTO itens (
                id, nome, descricao, tipo, raridade, tier, peso, peso_kg,
                preco_base_latao, preco_latao, regiao_nativa, geologia_nativa, aquifero_nativo
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                nome = excluded.nome,
                descricao = excluded.descricao,
                tipo = excluded.tipo,
                raridade = excluded.raridade,
                tier = excluded.tier,
                peso = excluded.peso,
                peso_kg = excluded.peso_kg,
                preco_base_latao = excluded.preco_base_latao,
                preco_latao = excluded.preco_latao,
                regiao_nativa = excluded.regiao_nativa,
                geologia_nativa = excluded.geologia_nativa,
                aquifero_nativo = excluded.aquifero_nativo;
        """;

        try (Connection conn = BancoDados.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Object[] row : catalogo) {
                    int id = (Integer) row[0];
                    String nome = (String) row[1];
                    String desc = (String) row[2];
                    String tipo = (String) row[3];
                    String rari = (String) row[4];
                    int tier = (Integer) row[5];
                    double peso = (Double) row[6];
                    long preco = (Long) row[7];
                    String reg = (String) row[8];
                    String geo = (String) row[9];
                    String aqui = (String) row[10];

                    ps.setInt(1, id);
                    ps.setString(2, nome);
                    ps.setString(3, desc);
                    ps.setString(4, tipo);
                    ps.setString(5, rari);
                    ps.setInt(6, tier);
                    ps.setDouble(7, peso);
                    ps.setDouble(8, peso);
                    ps.setLong(9, preco);
                    ps.setLong(10, preco);
                    ps.setString(11, reg);
                    ps.setString(12, geo);
                    ps.setString(13, aqui);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
            BancoDados.marcarSemente(CHAVE_SEMENTE_ITENS);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao popular catálogo canônico de 55 itens no SQLite", e);
        }
    }

    private Item converterResultSetParaItem(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String nome = rs.getString("nome");
        String descricao = rs.getString("descricao");

        long precoLatao = rs.getLong("preco_latao");
        if (precoLatao == 0) {
            try {
                long pBase = rs.getLong("preco_base_latao");
                if (pBase > 0) precoLatao = pBase;
            } catch (SQLException ignored) {}
        }

        double peso = rs.getDouble("peso_kg");
        if (peso == 0.0) {
            try {
                double p = rs.getDouble("peso");
                if (p > 0.0) peso = p;
            } catch (SQLException ignored) {}
        }

        String tipoStr = rs.getString("tipo");
        Item.TipoItem tipo = Item.TipoItem.OUTRO;
        if (tipoStr != null) {
            try {
                tipo = Item.TipoItem.valueOf(tipoStr.trim());
            } catch (IllegalArgumentException e) {
                tipo = Item.TipoItem.OUTRO;
            }
        }

        String raridadeStr = rs.getString("raridade");
        Item.RaridadeItem raridade = Item.RaridadeItem.COMUM;
        if (raridadeStr != null) {
            try {
                raridade = Item.RaridadeItem.valueOf(raridadeStr.trim());
            } catch (IllegalArgumentException e) {
                raridade = Item.RaridadeItem.COMUM;
            }
        }

        int tier = 1;
        try {
            tier = rs.getInt("tier");
        } catch (SQLException ignored) {}
        if (tier < 1) tier = 1;

        double precoPO = precoLatao / 10000.0;
        Item item = new Item(id, nome, descricao, precoPO, peso, tipo, raridade, 1);

        // Volumetria / Tamanho físico da mochila
        if (nome != null && (nome.contains("COLOSSAL") || peso >= 25.0)) {
            item.setTamanho(TamanhoItem.COLOSSAL);
        } else if (nome != null && nome.contains("MINUSCULO")) {
            item.setTamanho(TamanhoItem.MINUSCULO);
        }

        // Recuperar e vincular geografia nativa do item (Regra do Camarão)
        try {
            String regStr = rs.getString("regiao_nativa");
            String geoStr = rs.getString("geologia_nativa");
            String aquiStr = rs.getString("aquifero_nativo");

            Regiao regiao = (regStr != null && !regStr.trim().isEmpty()) ? Regiao.valueOf(regStr.trim()) : null;
            Geologia geologia = (geoStr != null && !geoStr.trim().isEmpty()) ? Geologia.valueOf(geoStr.trim()) : null;
            Aquifero aquifero = (aquiStr != null && !aquiStr.trim().isEmpty()) ? Aquifero.valueOf(aquiStr.trim()) : null;

            if (regiao != null || geologia != null || aquifero != null) {
                item.setOrigemGeografica(regiao, geologia, aquifero);
            }
        } catch (Exception ignored) {}

        return item;
    }
}
