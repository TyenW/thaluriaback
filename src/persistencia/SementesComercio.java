package persistencia;

import economia.*;
import geografia.Cidade;

import java.sql.*;
import java.util.*;

/**
 * Povoa o comércio do mundo uma única vez (marcadores em meta_sementes):
 * itens de ofício que faltam no catálogo, receitas-base de cada especialidade
 * e lojas-base em toda cidade, conforme o porte do assentamento.
 * Itens e receitas são achados pelo nome: nada do que o Mestre já cadastrou é duplicado,
 * e o que ele apagar depois não volta.
 */
public class SementesComercio {

    public static final String CHAVE_RECEITAS = "comercio_receitas_base_v1";
    public static final String CHAVE_LOJAS = "comercio_lojas_base_v1";

    private record ItemNovo(String nome, String descricao, String tipo, String raridade, double pesoKg, long precoLatao) {}

    /** Itens de ofício que o catálogo canônico não tem (comida, bebida, roupas, insumos simples). */
    private static final List<ItemNovo> ITENS_DE_OFICIO = List.of(
        new ItemNovo("Carvão Vegetal", "Combustível das forjas.", "MATERIAL", "COMUM", 1.0, 300),
        new ItemNovo("Carretel de Linha Encerada", "Linha resistente de alfaiate.", "MATERIAL", "COMUM", 0.1, 300),
        new ItemNovo("Frasco de Vidro", "Recipiente para poções e unguentos.", "MATERIAL", "COMUM", 0.2, 300),
        new ItemNovo("Óleo Vegetal Prensado", "Base para unguentos e lamparinas.", "MATERIAL", "COMUM", 0.5, 400),
        new ItemNovo("Saco de Trigo", "Grão para pão e rações.", "MATERIAL", "COMUM", 5.0, 400),
        new ItemNovo("Favo de Mel Silvestre", "Mel bruto para fermentar hidromel.", "MATERIAL", "COMUM", 0.5, 600),
        new ItemNovo("Barra de Prata", "Prata refinada para joalheiros.", "MATERIAL", "INCOMUM", 0.5, 6000),
        new ItemNovo("Pão de Centeio", "Pão escuro e duradouro.", "CONSUMIVEL", "COMUM", 0.3, 300),
        new ItemNovo("Ensopado de Caça", "Prato quente de taverna.", "CONSUMIVEL", "COMUM", 0.6, 1500),
        new ItemNovo("Ração de Viagem", "Provisão seca para três dias de estrada.", "CONSUMIVEL", "COMUM", 0.8, 1200),
        new ItemNovo("Hidromel das Montanhas", "Bebida forte fermentada em barris de carvalho.", "CONSUMIVEL", "COMUM", 1.0, 2000),
        new ItemNovo("Poção de Cura Menor", "Restaura ferimentos leves.", "CONSUMIVEL", "COMUM", 0.3, 3500),
        new ItemNovo("Antídoto de Lótus", "Neutraliza venenos comuns e raros.", "CONSUMIVEL", "RARO", 0.3, 55000),
        new ItemNovo("Unguento de Erva-de-Sangue", "Pomada cicatrizante de botica.", "CONSUMIVEL", "COMUM", 0.2, 3000),
        new ItemNovo("Capa de Viagem de Linho", "Leve e fresca para estradas longas.", "ARMADURA", "COMUM", 1.0, 9000),
        new ItemNovo("Gibão de Couro Batido", "Proteção leve de couro curtido.", "ARMADURA", "COMUM", 4.0, 12000),
        new ItemNovo("Manto de Pelagem Boreal", "Isola do frio extremo do norte.", "ARMADURA", "INCOMUM", 2.5, 16000),
        new ItemNovo("Mochila de Couro", "Bolsa reforçada de viajante.", "OUTRO", "COMUM", 1.5, 6000),
        new ItemNovo("Corda de Cânhamo (15 m)", "Corda grossa e confiável.", "OUTRO", "COMUM", 2.0, 800),
        new ItemNovo("Tocha Embebida", "Ilumina por cerca de uma hora.", "CONSUMIVEL", "COMUM", 0.5, 150),
        new ItemNovo("Saco de Dormir", "Lã e lona para noites ao relento.", "OUTRO", "COMUM", 3.0, 2500),
        new ItemNovo("Anel de Prata Lavrada", "Joia fina de ourives.", "ACESSORIO", "INCOMUM", 0.05, 18000),
        new ItemNovo("Amuleto de Cristal Solar", "Guarda o calor do deserto num pingente.", "ACESSORIO", "RARO", 0.1, 55000)
    );

    private record Insumo(String item, int qtd) {}

    private record ReceitaBase(String nome, Lojas.TipoEstabelecimento tipo, String resultado, int qtd, double horas, List<Insumo> insumos) {}

    private static final Lojas.TipoEstabelecimento FORJA = Lojas.TipoEstabelecimento.FORJA;
    private static final Lojas.TipoEstabelecimento ALFAIATARIA = Lojas.TipoEstabelecimento.ALFAIATARIA;
    private static final Lojas.TipoEstabelecimento ALQUIMIA = Lojas.TipoEstabelecimento.ALQUIMIA;
    private static final Lojas.TipoEstabelecimento BOTANICA = Lojas.TipoEstabelecimento.BOTANICA;
    private static final Lojas.TipoEstabelecimento TAVERNA = Lojas.TipoEstabelecimento.TAVERNA;
    private static final Lojas.TipoEstabelecimento JOALHERIA = Lojas.TipoEstabelecimento.JOALHERIA;
    private static final Lojas.TipoEstabelecimento LOJA_GERAL = Lojas.TipoEstabelecimento.LOJA_GERAL;

    private static final String MINERIO = "Minério de Ferro de Ironhold (Q1)";
    private static final String LINGOTE = "Lingote de Ferro Forjado (Q2)";
    private static final String TORA = "Tora de Pinho Boreal de Frostdale (Q2)";
    private static final String COURO = "Couro Batido de Planície (Q1)";
    private static final String PELAGEM = "Pelagem Branca Boreal (Q2)";
    private static final String LINHO = "Linho Fino de Valdorian (Q2)";
    private static final String ERVA = "Erva-de-Sangue de Valdorian (Q2)";
    private static final String LOTUS = "Lótus Glacial de Frostdale (Q4)";
    private static final String CRISTAL = "Cristal Solar de Sunreach (Q3)";
    private static final String CARNE = "Carne de Caça Defumada (Q2)";

    private static final List<ReceitaBase> RECEITAS = List.of(
        new ReceitaBase("Fundir lingote de ferro", FORJA, LINGOTE, 1, 12, List.of(new Insumo(MINERIO, 2), new Insumo("Carvão Vegetal", 1))),
        new ReceitaBase("Forjar adaga de aço", FORJA, "Adaga de Aço de Valdorian", 1, 24, List.of(new Insumo(LINGOTE, 1), new Insumo("Carvão Vegetal", 1))),
        new ReceitaBase("Forjar espada curta", FORJA, "Espada Curta da Guarda de Oakhaven", 1, 24, List.of(new Insumo(LINGOTE, 2), new Insumo(TORA, 1))),
        new ReceitaBase("Forjar espada longa", FORJA, "Espada Longa de Ironhold", 1, 48, List.of(new Insumo(LINGOTE, 3), new Insumo("Carvão Vegetal", 2))),
        new ReceitaBase("Forjar machado anão", FORJA, "Machado de Batalha Anão", 1, 48, List.of(new Insumo(LINGOTE, 3), new Insumo(TORA, 1))),
        new ReceitaBase("Montar broquel", FORJA, "Broquel de Duelista (HP 40 | Segurar 12)", 1, 24,
                List.of(new Insumo(TORA, 2), new Insumo(LINGOTE, 1), new Insumo(COURO, 1))),
        new ReceitaBase("Costurar capa de linho", ALFAIATARIA, "Capa de Viagem de Linho", 1, 24, List.of(new Insumo(LINHO, 2), new Insumo("Carretel de Linha Encerada", 1))),
        new ReceitaBase("Costurar gibão de couro", ALFAIATARIA, "Gibão de Couro Batido", 1, 24, List.of(new Insumo(COURO, 4), new Insumo("Carretel de Linha Encerada", 1))),
        new ReceitaBase("Costurar manto boreal", ALFAIATARIA, "Manto de Pelagem Boreal", 1, 48, List.of(new Insumo(PELAGEM, 2), new Insumo("Carretel de Linha Encerada", 1))),
        new ReceitaBase("Costurar mochila", ALFAIATARIA, "Mochila de Couro", 1, 24, List.of(new Insumo(COURO, 2), new Insumo("Carretel de Linha Encerada", 1))),
        new ReceitaBase("Destilar poção de cura menor", ALQUIMIA, "Poção de Cura Menor", 1, 12, List.of(new Insumo(ERVA, 1), new Insumo("Frasco de Vidro", 1))),
        new ReceitaBase("Destilar restauração vital maior", ALQUIMIA, "Poção de Restauração Vital Maior", 1, 24, List.of(new Insumo(ERVA, 3), new Insumo("Frasco de Vidro", 1))),
        new ReceitaBase("Preparar antídoto de lótus", ALQUIMIA, "Antídoto de Lótus", 1, 48, List.of(new Insumo(LOTUS, 1), new Insumo("Frasco de Vidro", 1))),
        new ReceitaBase("Preparar unguento", BOTANICA, "Unguento de Erva-de-Sangue", 1, 12, List.of(new Insumo(ERVA, 1), new Insumo("Óleo Vegetal Prensado", 1))),
        new ReceitaBase("Assar pão de centeio", TAVERNA, "Pão de Centeio", 4, 12, List.of(new Insumo("Saco de Trigo", 1))),
        new ReceitaBase("Cozinhar ensopado de caça", TAVERNA, "Ensopado de Caça", 2, 12, List.of(new Insumo(CARNE, 1), new Insumo("Saco de Trigo", 1))),
        new ReceitaBase("Embalar ração de viagem", TAVERNA, "Ração de Viagem", 2, 24, List.of(new Insumo(CARNE, 1), new Insumo("Saco de Trigo", 1))),
        new ReceitaBase("Fermentar hidromel", TAVERNA, "Hidromel das Montanhas", 2, 72, List.of(new Insumo("Favo de Mel Silvestre", 2))),
        new ReceitaBase("Lavrar anel de prata", JOALHERIA, "Anel de Prata Lavrada", 1, 48, List.of(new Insumo("Barra de Prata", 2))),
        new ReceitaBase("Engastar amuleto solar", JOALHERIA, "Amuleto de Cristal Solar", 1, 72, List.of(new Insumo(CRISTAL, 1), new Insumo("Barra de Prata", 1)))
    );

    private record Mercadoria(String item, int estoque, int demanda) {}

    /** Mercadorias iniciais de cada especialidade: produtos à venda e insumos das receitas. */
    private static final Map<Lojas.TipoEstabelecimento, List<Mercadoria>> SORTIMENTO = Map.of(
        FORJA, List.of(new Mercadoria(MINERIO, 12, 6), new Mercadoria(LINGOTE, 8, 6), new Mercadoria("Carvão Vegetal", 12, 6),
                new Mercadoria(TORA, 6, 4), new Mercadoria(COURO, 3, 2), new Mercadoria("Adaga de Aço de Valdorian", 3, 3),
                new Mercadoria("Espada Curta da Guarda de Oakhaven", 2, 2), new Mercadoria("Espada Longa de Ironhold", 1, 2),
                new Mercadoria("Machado de Batalha Anão", 1, 1), new Mercadoria("Broquel de Duelista (HP 40 | Segurar 12)", 1, 1),
                new Mercadoria("Aljava de Flechas de Aço (20x - 1d6)", 4, 3)),
        ALFAIATARIA, List.of(new Mercadoria(LINHO, 6, 4), new Mercadoria(COURO, 8, 6), new Mercadoria(PELAGEM, 4, 2),
                new Mercadoria("Carretel de Linha Encerada", 8, 4), new Mercadoria("Capa de Viagem de Linho", 2, 2),
                new Mercadoria("Gibão de Couro Batido", 2, 2), new Mercadoria("Manto de Pelagem Boreal", 1, 1), new Mercadoria("Mochila de Couro", 2, 2)),
        ALQUIMIA, List.of(new Mercadoria(ERVA, 10, 6), new Mercadoria("Frasco de Vidro", 10, 6), new Mercadoria(LOTUS, 1, 1),
                new Mercadoria("Poção de Cura Menor", 6, 5), new Mercadoria("Poção de Restauração Vital Maior", 2, 2), new Mercadoria("Antídoto de Lótus", 1, 1)),
        BOTANICA, List.of(new Mercadoria(ERVA, 14, 8), new Mercadoria("Óleo Vegetal Prensado", 8, 4), new Mercadoria(LOTUS, 2, 1),
                new Mercadoria("Unguento de Erva-de-Sangue", 6, 4)),
        TAVERNA, List.of(new Mercadoria("Saco de Trigo", 6, 4), new Mercadoria(CARNE, 6, 4), new Mercadoria("Favo de Mel Silvestre", 6, 3),
                new Mercadoria("Pão de Centeio", 10, 8), new Mercadoria("Ensopado de Caça", 6, 6), new Mercadoria("Ração de Viagem", 6, 5),
                new Mercadoria("Hidromel das Montanhas", 6, 5)),
        JOALHERIA, List.of(new Mercadoria("Barra de Prata", 6, 3), new Mercadoria(CRISTAL, 1, 1), new Mercadoria("Anel de Prata Lavrada", 2, 2),
                new Mercadoria("Amuleto de Cristal Solar", 1, 1), new Mercadoria("Anel do Sangue Furioso [ANEIS]", 1, 1)),
        LOJA_GERAL, List.of(new Mercadoria("Corda de Cânhamo (15 m)", 6, 4), new Mercadoria("Tocha Embebida", 15, 8), new Mercadoria("Saco de Dormir", 3, 2),
                new Mercadoria("Ração de Viagem", 6, 4), new Mercadoria("Poção de Cura Menor", 3, 3), new Mercadoria("Mochila de Couro", 1, 1),
                new Mercadoria("Aljava de Flechas de Aço (20x - 1d6)", 3, 2))
    );

    /** Especialidades presentes em cada porte de assentamento. */
    private static List<Lojas.TipoEstabelecimento> tiposDoPorte(Cidade.TipoAssentamento porte) {
        if (porte == null) return List.of(LOJA_GERAL, TAVERNA);
        return switch (porte) {
            case REINO -> List.of(LOJA_GERAL, TAVERNA, FORJA, ALQUIMIA, ALFAIATARIA, JOALHERIA, BOTANICA);
            case CIDADE -> List.of(LOJA_GERAL, TAVERNA, FORJA, ALQUIMIA, ALFAIATARIA);
            case FORTALEZA -> List.of(LOJA_GERAL, TAVERNA, FORJA);
            case VILA -> List.of(LOJA_GERAL, TAVERNA);
        };
    }

    private static long caixaInicial(Cidade.TipoAssentamento porte, Lojas.TipoEstabelecimento tipo) {
        long base = porte == null ? 120_000L : switch (porte) {
            case REINO -> 400_000L;
            case CIDADE -> 250_000L;
            case FORTALEZA -> 200_000L;
            case VILA -> 120_000L;
        };
        return tipo == JOALHERIA ? base * 3 : base;
    }

    private static int nivelInicial(Cidade.TipoAssentamento porte) {
        if (porte == null) return 2;
        return switch (porte) {
            case REINO -> 4;
            case CIDADE, FORTALEZA -> 3;
            case VILA -> 2;
        };
    }

    private static final Map<Lojas.TipoEstabelecimento, String[]> NOMES = Map.of(
        FORJA, new String[] { "Bigorna Rubra", "Forja do Martelo Velho", "Ferraria da Brasa Viva" },
        ALFAIATARIA, new String[] { "Agulha de Prata", "Alfaiataria do Fio Dourado", "Tecelagem da Lã Boa" },
        ALQUIMIA, new String[] { "Botica do Alambique", "Frascos & Fervuras", "Laboratório da Retorta Azul" },
        BOTANICA, new String[] { "Estufa das Raízes", "Herbário do Orvalho", "Jardim das Sete Ervas" },
        TAVERNA, new String[] { "Taverna do Javali Assado", "Estalagem do Caneco Cheio", "Taverna da Lareira" },
        JOALHERIA, new String[] { "Ourivesaria da Gema Clara", "Joalheria do Brilho Eterno", "Lapidaria do Olho de Falcão" },
        LOJA_GERAL, new String[] { "Empório do Viajante", "Armazém da Encruzilhada", "Mercearia da Praça" }
    );

    private static final String[] MERCADORES = {
        "Bram Pedraforte", "Ilsa Ventoclaro", "Doran Malho", "Maeve Fiandeira", "Corvin Alambique", "Nara Folhaverde",
        "Hugo Canecão", "Selene Lapidária", "Tobias Encruzilhada", "Greta Ferroviva", "Ewan Dedal", "Lira Orvalho",
        "Otto Barrica", "Yara Prataluz", "Fenwick Sacola", "Runa Brasa", "Edda Tear", "Silas Retorta", "Mirela Raiz", "Joss Lareira"
    };

    /** Aplica as sementes que ainda não foram aplicadas neste banco. */
    public static void aplicar(EconomiaDAO economiaDAO) {
        if (!BancoDados.sementeAplicada(CHAVE_RECEITAS)) {
            semearItensEReceitas();
            BancoDados.marcarSemente(CHAVE_RECEITAS);
        }
        if (!BancoDados.sementeAplicada(CHAVE_LOJAS)) {
            semearLojas(economiaDAO);
            BancoDados.marcarSemente(CHAVE_LOJAS);
        }
    }

    private static void semearItensEReceitas() {
        try (Connection conn = BancoDados.getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (ItemNovo i : ITENS_DE_OFICIO) {
                    if (idDoItem(conn, i.nome()) != null) continue;
                    String sql = """
                        INSERT INTO itens (id, nome, descricao, preco_latao, peso_kg, tipo, raridade, tier, preco_base_latao, peso)
                        VALUES ((SELECT COALESCE(MAX(id), 0) + 1 FROM itens), ?, ?, ?, ?, ?, ?, 1, ?, ?)
                        """;
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, i.nome());
                        ps.setString(2, i.descricao());
                        ps.setLong(3, i.precoLatao());
                        ps.setDouble(4, i.pesoKg());
                        ps.setString(5, i.tipo());
                        ps.setString(6, i.raridade());
                        ps.setLong(7, i.precoLatao());
                        ps.setDouble(8, i.pesoKg());
                        ps.executeUpdate();
                    }
                }
                for (ReceitaBase r : RECEITAS) {
                    if (existe(conn, "SELECT 1 FROM receitas WHERE nome = ?", r.nome())) continue;
                    Integer resultado = idDoItem(conn, r.resultado());
                    if (resultado == null) continue; // item canônico removido pelo Mestre
                    List<int[]> insumos = new ArrayList<>();
                    for (Insumo ins : r.insumos()) {
                        Integer id = idDoItem(conn, ins.item());
                        if (id == null) { insumos = null; break; }
                        insumos.add(new int[] { id, ins.qtd() });
                    }
                    if (insumos == null) continue;
                    long receitaId;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO receitas (nome, item_resultado_id, quantidade_resultado, grau_minimo, tempo_horas, tipo_loja, descricao) VALUES (?, ?, ?, 0, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, r.nome());
                        ps.setInt(2, resultado);
                        ps.setInt(3, r.qtd());
                        ps.setDouble(4, r.horas());
                        ps.setString(5, r.tipo().name());
                        ps.setString(6, "Receita-base de " + r.tipo().getNomeFormatado() + ".");
                        ps.executeUpdate();
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            rs.next();
                            receitaId = rs.getLong(1);
                        }
                    }
                    try (PreparedStatement ps = conn.prepareStatement("INSERT INTO receita_insumos (receita_id, item_id, quantidade) VALUES (?, ?, ?)")) {
                        for (int[] ins : insumos) {
                            ps.setLong(1, receitaId);
                            ps.setInt(2, ins[0]);
                            ps.setInt(3, ins[1]);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao semear itens e receitas do comércio: " + e.getMessage(), e);
        }
    }

    /** Cada cidade recebe as especialidades do seu porte que ainda não tem. */
    private static void semearLojas(EconomiaDAO economiaDAO) {
        ItemDAO itemDAO = new ItemDAO();
        Map<String, Item> porNome = new HashMap<>();
        for (Item i : itemDAO.listarTodos()) porNome.putIfAbsent(i.getNome(), i);

        Map<Integer, Set<Lojas.TipoEstabelecimento>> existentes = new HashMap<>();
        try (Connection conn = BancoDados.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT cidade_id, tipo FROM lojas")) {
            while (rs.next()) {
                try {
                    existentes.computeIfAbsent(rs.getInt(1), k -> EnumSet.noneOf(Lojas.TipoEstabelecimento.class))
                            .add(Lojas.TipoEstabelecimento.valueOf(rs.getString(2)));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao ler lojas existentes", e);
        }

        for (Cidade cidade : economiaDAO.listarCidades()) {
            Set<Lojas.TipoEstabelecimento> jaTem = existentes.getOrDefault(cidade.getId(), Set.of());
            for (Lojas.TipoEstabelecimento tipo : tiposDoPorte(cidade.getTipo())) {
                if (jaTem.contains(tipo)) continue;
                int semente = Math.abs(cidade.getId() * 31 + tipo.ordinal() * 7);
                String[] nomes = NOMES.get(tipo);
                String nome = nomes[semente % nomes.length] + " de " + cidade.getNome();
                String mercador = MERCADORES[semente % MERCADORES.length];

                // Sem cidadeSede no construtor: a loja é só gravada, não entra na lista em memória desta cidade
                Lojas loja = new Lojas(nome, mercador, false, null, tipo);
                loja.setCidadeSede(cidade);
                loja.setNivelLoja(nivelInicial(cidade.getTipo()));
                loja.setCaixa(Carteira.deLatao(caixaInicial(cidade.getTipo(), tipo)));
                for (Mercadoria m : SORTIMENTO.getOrDefault(tipo, List.of())) {
                    Item modelo = porNome.get(m.item());
                    if (modelo == null) continue;
                    loja.adicionarItem(Lojas.clonarItem(modelo, m.estoque()));
                    loja.definirDemanda(modelo, m.demanda());
                }
                economiaDAO.salvarLoja(loja);
            }
        }
    }

    private static Integer idDoItem(Connection conn, String nome) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM itens WHERE nome = ? ORDER BY id LIMIT 1")) {
            ps.setString(1, nome);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private static boolean existe(Connection conn, String sql, String valor) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, valor);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
