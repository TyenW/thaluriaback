package persistencia;

import economia.Item;
import geografia.Aquifero;
import geografia.Geologia;
import geografia.Regiao;
import personagem.*;

import java.sql.*;
import java.util.*;

/**
 * DAO responsável por persistir o Bestiário (Camada v8), os Movesets estilo Pokémon
 * (4 slots ativos + learnset), as Tabelas de Drop e as Fichas de Monstros Domados.
 */
public class MonstroDAO {

    public static class GolpePokemonDTO {
        public int grauDesbloqueio;
        public int slotInicial; // 1 a 4 (0 se for apenas do learnset futuro)
        public String nomeGolpe;
        public String categoriaPokemon; // FISICO_CONTATO, ESPECIAL_ELEMENTAL, STATUS_TATICO
        public String elemento;         // FOGO, GELO, TERRA, VENTO, RAIO, AGUA, LUZ, SOMBRA, CAOS, ORDEM, FISICO
        public int custoEnergia;
        public int custoMana;
        public int qtdDados;
        public int facesDado;
        public Atributo atributoEscala1;
        public Atributo atributoEscala2;
        public String efeitoColateral;

        public GolpePokemonDTO(int grauDesbloqueio, int slotInicial, String nomeGolpe,
                               String categoriaPokemon, String elemento, int custoEnergia, int custoMana,
                               int qtdDados, int facesDado, Atributo attr1, Atributo attr2, String efeito) {
            this.grauDesbloqueio = grauDesbloqueio;
            this.slotInicial = slotInicial;
            this.nomeGolpe = nomeGolpe;
            this.categoriaPokemon = categoriaPokemon;
            this.elemento = elemento;
            this.custoEnergia = custoEnergia;
            this.custoMana = custoMana;
            this.qtdDados = qtdDados;
            this.facesDado = facesDado;
            this.atributoEscala1 = attr1;
            this.atributoEscala2 = attr2;
            this.efeitoColateral = efeito;
        }

        public HabilidadeCombate converterParaHabilidadeCombate() {
            HabilidadeCombate.CategoriaHabilidade cat = "ESPECIAL_ELEMENTAL".equals(categoriaPokemon)
                    ? HabilidadeCombate.CategoriaHabilidade.MAGIA_ARCANA
                    : HabilidadeCombate.CategoriaHabilidade.ATAQUE_INATO_RACIAL;
            return new HabilidadeCombate(
                    nomeGolpe, cat, custoEnergia, custoMana,
                    qtdDados, facesDado, false,
                    atributoEscala1, atributoEscala2,
                    "[" + categoriaPokemon + " | " + elemento + "] " + (efeitoColateral != null ? efeitoColateral : "")
            );
        }
    }

    public static class DropMonstroDTO {
        public String itemNome;
        public double probabilidade;
        public int qtdMin;
        public int qtdMax;
        public Item.RaridadeItem raridade;
        public int qualidadeMaterial;
        public boolean dropExclusivo;

        public DropMonstroDTO(String itemNome, double probabilidade, int qtdMin, int qtdMax,
                              Item.RaridadeItem raridade, int qualidadeMaterial, boolean dropExclusivo) {
            this.itemNome = itemNome;
            this.probabilidade = probabilidade;
            this.qtdMin = qtdMin;
            this.qtdMax = qtdMax;
            this.raridade = raridade;
            this.qualidadeMaterial = qualidadeMaterial;
            this.dropExclusivo = dropExclusivo;
        }
    }

    public static class EspecieMonstroPersistida {
        public String id;
        public String nome;
        public Regiao regiaoNativa;
        public Geologia geologiaNativa;
        public Aquifero aquiferoNativo;
        public String elementoPrimario;
        public String elementoSecundario;
        public String porte; // MIUDO, PEQUENO, MEDIO, GRANDE, COLOSSAL
        public int vigBase, forBase, desBase, menBase, espBase, vonBase;
        public boolean domesticavel;
        public boolean chefeSolo;
        public int multiplicadorHpChefe;
        public String imagemUrl;
        public String descricao;
        public List<GolpePokemonDTO> learnset = new ArrayList<>();
        public List<DropMonstroDTO> drops = new ArrayList<>();
    }

    public static class FichaMonstroDomadoPersistida {
        public int idDomado;
        public int fichaDonoId;
        public String monstroCatalogoId;
        public String apelido;
        public Ficha fichaPropriaCriatura;
        public List<String> quatroSlotsAtaques = new ArrayList<>();
        public boolean bestaDeCargaAtiva;
    }

    /**
     * Popula o Bestiário Inicial das 5 Regiões Canónicas de Thaluria caso esteja vazio.
     */
    public void popularBestiarioCanonicaSeVazio() {
        try (Connection conn = BancoDados.obterConexao();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM monstros_catalogo")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao verificar catálogo de monstros: " + e.getMessage(), e);
        }

        // 1. Lobo Glacial de Frostdale (Tundra / Floresta - Rank E - Gelo)
        EspecieMonstroPersistida lobo = new EspecieMonstroPersistida();
        lobo.id = "LOBO_GLACIAL_FROSTDALE";
        lobo.nome = "Lobo Glacial de Frostdale";
        lobo.regiaoNativa = Regiao.TUNDRA;
        lobo.geologiaNativa = Geologia.FLORESTA;
        lobo.aquiferoNativo = Aquifero.OCEANO_AGUAS_COSTEIRAS;
        lobo.elementoPrimario = "GELO";
        lobo.elementoSecundario = null;
        lobo.porte = "MEDIO";
        lobo.vigBase = 12; lobo.forBase = 14; lobo.desBase = 16;
        lobo.menBase = 8;  lobo.espBase = 12; lobo.vonBase = 10;
        lobo.domesticavel = true;
        lobo.chefeSolo = false;
        lobo.multiplicadorHpChefe = 1;
        lobo.learnset.add(new GolpePokemonDTO(0, 1, "Presa Congelante", "FISICO_CONTATO", "GELO", 2, 0, 1, 6, Atributo.FORCA, Atributo.DESTREZA, "Reduz deslocamento do alvo"));
        lobo.learnset.add(new GolpePokemonDTO(0, 2, "Arranhão Ártico", "FISICO_CONTATO", "FISICO", 2, 0, 2, 4, Atributo.DESTREZA, null, "Ataque duplo veloz"));
        lobo.learnset.add(new GolpePokemonDTO(1, 3, "Uivo da Alcateia", "STATUS_TATICO", "GELO", 2, 1, 0, 0, Atributo.ESPIRITO, Atributo.VONTADE, "+2 FOR e +2 DES por 2 turnos"));
        lobo.learnset.add(new GolpePokemonDTO(1, 4, "Sopro de Geada", "ESPECIAL_ELEMENTAL", "GELO", 0, 4, 2, 6, Atributo.ESPIRITO, Atributo.MENTAL, "Cone 4m - aplica Congelamento"));
        lobo.drops.add(new DropMonstroDTO("Pelagem Branca Boreal", 0.85, 1, 2, Item.RaridadeItem.COMUM, 2, false));
        lobo.drops.add(new DropMonstroDTO("Presa de Gelo Eterno", 0.25, 1, 1, Item.RaridadeItem.RARO, 3, true));
        salvarEspecieMonstro(lobo);

        // 2. Besouro-Hércules de Couraça (Ironhold - Montanha - COLOSSAL / Besta de Carga)
        EspecieMonstroPersistida besouro = new EspecieMonstroPersistida();
        besouro.id = "BESOURO_HERCULES_IRONHOLD";
        besouro.nome = "Besouro-Hércules de Couraça";
        besouro.regiaoNativa = Regiao.TEMPERADO;
        besouro.geologiaNativa = Geologia.MONTANHA;
        besouro.aquiferoNativo = Aquifero.RIO;
        besouro.elementoPrimario = "TERRA";
        besouro.elementoSecundario = null;
        besouro.porte = "COLOSSAL";
        besouro.vigBase = 24; besouro.forBase = 26; besouro.desBase = 8;
        besouro.menBase = 6;  besouro.espBase = 10; besouro.vonBase = 18;
        besouro.domesticavel = true;
        besouro.chefeSolo = false;
        besouro.multiplicadorHpChefe = 1;
        besouro.learnset.add(new GolpePokemonDTO(0, 1, "Chifrada Titânica", "FISICO_CONTATO", "TERRA", 3, 0, 2, 6, Atributo.FORCA, Atributo.VIGOR, "Empurra 3m e derruba"));
        besouro.learnset.add(new GolpePokemonDTO(0, 2, "Endurecer Carapaça", "STATUS_TATICO", "TERRA", 2, 0, 0, 0, Atributo.VIGOR, null, "+5 RD Física por 3 turnos"));
        besouro.learnset.add(new GolpePokemonDTO(1, 3, "Arremesso de Rocha", "FISICO_CONTATO", "TERRA", 3, 0, 1, 10, Atributo.FORCA, null, "Ataque de médio alcance"));
        besouro.learnset.add(new GolpePokemonDTO(2, 4, "Abalo Sísmico", "ESPECIAL_ELEMENTAL", "TERRA", 2, 4, 2, 8, Atributo.FORCA, Atributo.ESPIRITO, "Dano em área ao redor"));
        besouro.drops.add(new DropMonstroDTO("Quitina Mineral de Ironhold", 0.90, 1, 3, Item.RaridadeItem.INCOMUM, 3, false));
        besouro.drops.add(new DropMonstroDTO("Chifre Colossal de Hércules", 0.15, 1, 1, Item.RaridadeItem.EPICO, 4, true));
        salvarEspecieMonstro(besouro);

        // 3. Escorpião-Rei de Cristal (Sunreach - Deserto/Caverna - Terra/Caos)
        EspecieMonstroPersistida escorpiao = new EspecieMonstroPersistida();
        escorpiao.id = "ESCORPIAO_REI_SUNREACH";
        escorpiao.nome = "Escorpião-Rei de Cristal";
        escorpiao.regiaoNativa = Regiao.DESERTO;
        escorpiao.geologiaNativa = Geologia.CAVERNA;
        escorpiao.aquiferoNativo = Aquifero.OCEANO_AGUAS_PROFUNDAS;
        escorpiao.elementoPrimario = "TERRA";
        escorpiao.elementoSecundario = "CAOS";
        escorpiao.porte = "GRANDE";
        escorpiao.vigBase = 20; escorpiao.forBase = 22; escorpiao.desBase = 18;
        escorpiao.menBase = 12; escorpiao.espBase = 18; escorpiao.vonBase = 16;
        escorpiao.domesticavel = true;
        escorpiao.chefeSolo = true;
        escorpiao.multiplicadorHpChefe = 3;
        escorpiao.learnset.add(new GolpePokemonDTO(0, 1, "Pinça Esmagadora", "FISICO_CONTATO", "TERRA", 3, 0, 2, 6, Atributo.FORCA, null, "Prende o alvo"));
        escorpiao.learnset.add(new GolpePokemonDTO(0, 2, "Ferrão Neurotóxico", "FISICO_CONTATO", "CAOS", 3, 2, 1, 8, Atributo.DESTREZA, Atributo.ESPIRITO, "Aplica Veneno e reduz SAN"));
        escorpiao.learnset.add(new GolpePokemonDTO(1, 3, "Tempestade de Areia Cristalina", "ESPECIAL_ELEMENTAL", "TERRA", 0, 4, 2, 6, Atributo.ESPIRITO, Atributo.MENTAL, "Cega alvos na área"));
        escorpiao.learnset.add(new GolpePokemonDTO(2, 4, "Pulso Alucinógeno Abissal", "ESPECIAL_ELEMENTAL", "CAOS", 0, 5, 2, 8, Atributo.ESPIRITO, Atributo.VONTADE, "Dano direto na Sanidade"));
        escorpiao.drops.add(new DropMonstroDTO("Glândula de Veneno Prismático", 0.75, 1, 2, Item.RaridadeItem.RARO, 4, false));
        escorpiao.drops.add(new DropMonstroDTO("Núcleo Cristalina do Rei Escorpião", 0.10, 1, 1, Item.RaridadeItem.LENDARIO, 5, true));
        salvarEspecieMonstro(escorpiao);
    }

    public void salvarEspecieMonstro(EspecieMonstroPersistida m) {
        String sqlCatalogo = """
            INSERT INTO monstros_catalogo (
                id, nome, regiao_nativa, geologia_nativa, aquifero_nativo,
                elemento_primario, elemento_secundario, porte,
                vig_base, for_base, des_base, men_base, esp_base, von_base,
                domesticavel, chefe_solo, multiplicador_hp_chefe
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                nome = excluded.nome,
                porte = excluded.porte,
                vig_base = excluded.vig_base,
                for_base = excluded.for_base,
                des_base = excluded.des_base,
                men_base = excluded.men_base,
                esp_base = excluded.esp_base,
                von_base = excluded.von_base;
        """;

        try (Connection conn = BancoDados.obterConexao()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sqlCatalogo)) {
                ps.setString(1, m.id);
                ps.setString(2, m.nome);
                ps.setString(3, m.regiaoNativa.name());
                ps.setString(4, m.geologiaNativa.name());
                ps.setString(5, m.aquiferoNativo.name());
                ps.setString(6, m.elementoPrimario);
                ps.setString(7, m.elementoSecundario);
                ps.setString(8, m.porte);
                ps.setInt(9, m.vigBase);
                ps.setInt(10, m.forBase);
                ps.setInt(11, m.desBase);
                ps.setInt(12, m.menBase);
                ps.setInt(13, m.espBase);
                ps.setInt(14, m.vonBase);
                ps.setInt(15, m.domesticavel ? 1 : 0);
                ps.setInt(16, m.chefeSolo ? 1 : 0);
                ps.setInt(17, Math.max(1, m.multiplicadorHpChefe));
                ps.executeUpdate();
            }

            try (PreparedStatement delG = conn.prepareStatement("DELETE FROM monstro_golpes WHERE monstro_catalogo_id = ?");
                 PreparedStatement delD = conn.prepareStatement("DELETE FROM monstro_drops WHERE monstro_catalogo_id = ?")) {
                delG.setString(1, m.id);
                delG.executeUpdate();
                delD.setString(1, m.id);
                delD.executeUpdate();
            }

            String sqlGolpe = """
                INSERT INTO monstro_golpes (
                    monstro_catalogo_id, grau_desbloqueio, slot_inicial, nome_golpe,
                    categoria_pokemon, elemento, custo_energia, custo_mana,
                    qtd_dados, faces_dado, atributo_escala_1, atributo_escala_2, efeito_colateral
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """;
            try (PreparedStatement psG = conn.prepareStatement(sqlGolpe)) {
                for (GolpePokemonDTO g : m.learnset) {
                    psG.setString(1, m.id);
                    psG.setInt(2, g.grauDesbloqueio);
                    psG.setInt(3, g.slotInicial);
                    psG.setString(4, g.nomeGolpe);
                    psG.setString(5, g.categoriaPokemon);
                    psG.setString(6, g.elemento);
                    psG.setInt(7, g.custoEnergia);
                    psG.setInt(8, g.custoMana);
                    psG.setInt(9, g.qtdDados);
                    psG.setInt(10, g.facesDado);
                    psG.setString(11, g.atributoEscala1.name());
                    psG.setString(12, g.atributoEscala2 != null ? g.atributoEscala2.name() : null);
                    psG.setString(13, g.efeitoColateral);
                    psG.addBatch();
                }
                psG.executeBatch();
            }

            String sqlDrop = """
                INSERT INTO monstro_drops (
                    monstro_catalogo_id, item_nome, probabilidade, qtd_min, qtd_max,
                    raridade, qualidade_material, drop_exclusivo
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
            """;
            try (PreparedStatement psD = conn.prepareStatement(sqlDrop)) {
                for (DropMonstroDTO d : m.drops) {
                    psD.setString(1, m.id);
                    psD.setString(2, d.itemNome);
                    psD.setDouble(3, d.probabilidade);
                    psD.setInt(4, d.qtdMin);
                    psD.setInt(5, d.qtdMax);
                    psD.setString(6, d.raridade.name());
                    psD.setInt(7, d.qualidadeMaterial);
                    psD.setInt(8, d.dropExclusivo ? 1 : 0);
                    psD.addBatch();
                }
                psD.executeBatch();
            }

            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar espécie de monstro '" + m.id + "': " + e.getMessage(), e);
        }
    }

    public List<EspecieMonstroPersistida> listarCatalogoCompleto() {
        List<EspecieMonstroPersistida> lista = new ArrayList<>();
        String sql = "SELECT * FROM monstros_catalogo ORDER BY nome";
        try (Connection conn = BancoDados.obterConexao();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                EspecieMonstroPersistida m = new EspecieMonstroPersistida();
                m.id = rs.getString("id");
                m.nome = rs.getString("nome");
                m.regiaoNativa = Regiao.parse(rs.getString("regiao_nativa"));
                m.geologiaNativa = Geologia.parse(rs.getString("geologia_nativa"));
                m.aquiferoNativo = Aquifero.parse(rs.getString("aquifero_nativo"));
                m.elementoPrimario = rs.getString("elemento_primario");
                m.elementoSecundario = rs.getString("elemento_secundario");
                m.porte = rs.getString("porte");
                m.vigBase = rs.getInt("vig_base");
                m.forBase = rs.getInt("for_base");
                m.desBase = rs.getInt("des_base");
                m.menBase = rs.getInt("men_base");
                m.espBase = rs.getInt("esp_base");
                m.vonBase = rs.getInt("von_base");
                m.domesticavel = rs.getInt("domesticavel") == 1;
                m.chefeSolo = rs.getInt("chefe_solo") == 1;
                m.multiplicadorHpChefe = rs.getInt("multiplicador_hp_chefe");
                m.imagemUrl = rs.getString("imagem_url");
                m.descricao = rs.getString("descricao");
                carregarGolpesEDrops(conn, m);
                lista.add(m);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar catálogo de monstros: " + e.getMessage(), e);
        }
        return lista;
    }

    private void carregarGolpesEDrops(Connection conn, EspecieMonstroPersistida m) throws SQLException {
        try (PreparedStatement psG = conn.prepareStatement(
                "SELECT * FROM monstro_golpes WHERE monstro_catalogo_id = ? ORDER BY grau_desbloqueio, slot_inicial")) {
            psG.setString(1, m.id);
            try (ResultSet rs = psG.executeQuery()) {
                while (rs.next()) {
                    String attr2 = rs.getString("atributo_escala_2");
                    Atributo a1 = Atributo.parse(rs.getString("atributo_escala_1"));
                    Atributo a2 = attr2 != null ? Atributo.parse(attr2) : null;
                    m.learnset.add(new GolpePokemonDTO(
                        rs.getInt("grau_desbloqueio"),
                        rs.getInt("slot_inicial"),
                        rs.getString("nome_golpe"),
                        rs.getString("categoria_pokemon"),
                        rs.getString("elemento"),
                        rs.getInt("custo_energia"),
                        rs.getInt("custo_mana"),
                        rs.getInt("qtd_dados"),
                        rs.getInt("faces_dado"),
                        a1 != null ? a1 : Atributo.FOR,
                        a2,
                        rs.getString("efeito_colateral")
                    ));
                }
            }
        }

        try (PreparedStatement psD = conn.prepareStatement(
                "SELECT * FROM monstro_drops WHERE monstro_catalogo_id = ?")) {
            psD.setString(1, m.id);
            try (ResultSet rs = psD.executeQuery()) {
                while (rs.next()) {
                    m.drops.add(new DropMonstroDTO(
                        rs.getString("item_nome"),
                        rs.getDouble("probabilidade"),
                        rs.getInt("qtd_min"),
                        rs.getInt("qtd_max"),
                        Item.RaridadeItem.valueOf(rs.getString("raridade")),
                        rs.getInt("qualidade_material"),
                        rs.getInt("drop_exclusivo") == 1
                    ));
                }
            }
        }
    }

    /**
     * Salva a Ficha completa de um Monstro Domado (incluindo a sua própria Ficha na tabela 'fichas'
     * e os 4 ataques ativos do Moveset Pokémon) vinculada ao ID da Ficha do Jogador dono.
     */
    public int salvarMonstroDomado(int fichaDonoId, String monstroCatalogoId, String apelido,
                                   Ficha fichaPropriaMonstro, List<String> quatroGolpesAtivos,
                                   boolean bestaDeCargaAtiva) {
        FichaDAO fichaDAO = new FichaDAO();
        fichaDAO.salvar(fichaPropriaMonstro);
        Ficha fichaSalva = fichaDAO.buscarPorNome(fichaPropriaMonstro.getNome());
        int idFichaMonstro = (fichaSalva != null) ? (int) fichaSalva.getId() : (int) fichaPropriaMonstro.getId();

        String sql = """
            INSERT INTO ficha_monstros_domados (
                ficha_dono_id, monstro_catalogo_id, apelido, ficha_propria_id,
                golpe_slot_1, golpe_slot_2, golpe_slot_3, golpe_slot_4, besta_de_carga_ativa,
                ficha_id, nome, especie, porte
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

        try (Connection conn = BancoDados.obterConexao();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, fichaDonoId);
            ps.setString(2, monstroCatalogoId);
            ps.setString(3, apelido);
            ps.setInt(4, idFichaMonstro);
            ps.setString(5, quatroGolpesAtivos.size() > 0 ? quatroGolpesAtivos.get(0) : null);
            ps.setString(6, quatroGolpesAtivos.size() > 1 ? quatroGolpesAtivos.get(1) : null);
            ps.setString(7, quatroGolpesAtivos.size() > 2 ? quatroGolpesAtivos.get(2) : null);
            ps.setString(8, quatroGolpesAtivos.size() > 3 ? quatroGolpesAtivos.get(3) : null);
            ps.setInt(9, bestaDeCargaAtiva ? 1 : 0);
            ps.setInt(10, fichaDonoId);
            ps.setString(11, apelido);
            ps.setString(12, monstroCatalogoId);
            ps.setString(13, "MEDIO");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            return -1;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar ficha de monstro domado: " + e.getMessage(), e);
        }
    }

    public List<FichaMonstroDomadoPersistida> listarDomadosPorFichaDono(long fichaDonoId) {
        return listarDomadosPorFichaDono((int) fichaDonoId);
    }

    public List<FichaMonstroDomadoPersistida> listarDomadosPorFichaDono(int fichaDonoId) {
        List<FichaMonstroDomadoPersistida> lista = new ArrayList<>();
        FichaDAO fichaDAO = new FichaDAO();
        String sql = """
            SELECT d.*, f.nome AS nome_ficha_interna
            FROM ficha_monstros_domados d
            JOIN fichas f ON f.id = d.ficha_propria_id
            WHERE d.ficha_dono_id = ?;
        """;
        try (Connection conn = BancoDados.obterConexao();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fichaDonoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FichaMonstroDomadoPersistida dom = new FichaMonstroDomadoPersistida();
                    dom.idDomado = rs.getInt("id");
                    dom.fichaDonoId = rs.getInt("ficha_dono_id");
                    dom.monstroCatalogoId = rs.getString("monstro_catalogo_id");
                    dom.apelido = rs.getString("apelido");
                    dom.fichaPropriaCriatura = fichaDAO.buscarPorNome(rs.getString("nome_ficha_interna"));
                    for (int i = 1; i <= 4; i++) {
                        String g = rs.getString("golpe_slot_" + i);
                        if (g != null && !g.isBlank()) {
                            dom.quatroSlotsAtaques.add(g);
                        }
                    }
                    dom.bestaDeCargaAtiva = rs.getInt("besta_de_carga_ativa") == 1;
                    lista.add(dom);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao carregar monstros domados da ficha: " + e.getMessage(), e);
        }
        return lista;
    }
}
