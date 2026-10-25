package persistencia;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gerenciador central de persistência e conexão SQLite para o sistema RPG Thaluria.
 * Responsável pela criação de tabelas, migração de esquema e garantia de conexão JDBC.
 */
public class BancoDados {

    private static String urlBanco = "jdbc:sqlite:thaluria.db";

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Driver SQLite JDBC não encontrado no classpath!", e);
        }
    }

    public static synchronized void setUrlBanco(String novaUrl) {
        urlBanco = novaUrl;
    }

    public static synchronized String getUrlBanco() {
        return urlBanco;
    }

    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(urlBanco);
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL;");
            s.execute("PRAGMA busy_timeout=5000;");
        }
        return conn;
    }

    public static Connection obterConexao() throws SQLException {
        return getConnection();
    }

    /** Indica se uma carga inicial (semente) já foi aplicada neste banco. */
    public static boolean sementeAplicada(String chave) {
        try (Connection conn = getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM meta_sementes WHERE chave = ?")) {
            ps.setString(1, chave);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public static void marcarSemente(String chave) {
        try (Connection conn = getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO meta_sementes (chave) VALUES (?)")) {
            ps.setString(1, chave);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao registrar semente " + chave, e);
        }
    }

    /**
     * Inicializa todas as tabelas relacionais do sistema em SQLite (DDL idempotente).
     */
    public static void inicializarEsquema() {
        String[] tabelasDdl = {
                // 1. RAÇAS E HABILIDADES
                """
                CREATE TABLE IF NOT EXISTS racas (
                    nome TEXT PRIMARY KEY,
                    reino_origem TEXT NOT NULL,
                    dominancia TEXT NOT NULL,
                    raridade TEXT NOT NULL,
                    tipo_acesso TEXT NOT NULL,
                    disponivel_sorteio_inicial INTEGER NOT NULL DEFAULT 0,
                    vig_min INTEGER NOT NULL, vig_max INTEGER NOT NULL,
                    for_min INTEGER NOT NULL, for_max INTEGER NOT NULL,
                    des_min INTEGER NOT NULL, des_max INTEGER NOT NULL,
                    men_min INTEGER NOT NULL, men_max INTEGER NOT NULL,
                    esp_min INTEGER NOT NULL, esp_max INTEGER NOT NULL,
                    von_min INTEGER NOT NULL, von_max INTEGER NOT NULL,
                    hab_nome TEXT NOT NULL,
                    hab_tipo TEXT NOT NULL,
                    hab_custo_energia INTEGER NOT NULL,
                    hab_custo_mana INTEGER NOT NULL,
                    hab_alcance REAL NOT NULL,
                    hab_dado TEXT,
                    hab_atributos TEXT,
                    hab_descricao TEXT,
                    hab_afinidade_1d8 INTEGER,
                    mod_estrutural_tipo TEXT,
                    mod_estrutural_formula TEXT,
                    mod_metabolico_tipo TEXT,
                    mod_metabolico_dados TEXT
                );
                """,

                // 2. CATÁLOGO DE ITENS
                """
                CREATE TABLE IF NOT EXISTS itens (
                    id INTEGER PRIMARY KEY,
                    nome TEXT NOT NULL,
                    descricao TEXT,
                    preco_latao INTEGER NOT NULL,
                    peso_kg REAL NOT NULL,
                    tipo TEXT NOT NULL,
                    raridade TEXT NOT NULL,
                    tier INTEGER NOT NULL
                );
                """,

                // 3. GEOGRAFIA E CIDADES
                """
                CREATE TABLE IF NOT EXISTS cidades (
                    id INTEGER PRIMARY KEY,
                    nome TEXT NOT NULL UNIQUE,
                    tipo TEXT NOT NULL,
                    regiao TEXT NOT NULL,
                    geologia TEXT NOT NULL,
                    aquifero TEXT NOT NULL,
                    tem_porto INTEGER NOT NULL DEFAULT 0,
                    e_fronteira INTEGER NOT NULL DEFAULT 0,
                    regiao_fronteira TEXT
                );
                """,

                // 4. CONEXÕES VIÁRIAS / ESTRADAS DO GRAFO
                """
                CREATE TABLE IF NOT EXISTS conexoes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    origem_id INTEGER NOT NULL,
                    destino_id INTEGER NOT NULL,
                    distancia_km INTEGER NOT NULL,
                    perigo INTEGER NOT NULL,
                    tipo_rota TEXT NOT NULL,
                    bloqueada INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (origem_id) REFERENCES cidades(id),
                    FOREIGN KEY (destino_id) REFERENCES cidades(id)
                );
                """,

                // 5. ESTABELECIMENTOS E LOJAS
                """
                CREATE TABLE IF NOT EXISTS lojas (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nome TEXT NOT NULL,
                    mercador TEXT NOT NULL,
                    cidade_id INTEGER NOT NULL,
                    tipo TEXT NOT NULL,
                    e_rede INTEGER NOT NULL DEFAULT 0,
                    nome_rede TEXT,
                    caixa_latao INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (cidade_id) REFERENCES cidades(id)
                );
                """,

                // 6. ESTOQUE E DEMANDA POR LOJA
                """
                CREATE TABLE IF NOT EXISTS loja_estoque (
                    loja_id INTEGER NOT NULL,
                    item_id INTEGER NOT NULL,
                    quantidade INTEGER NOT NULL DEFAULT 0,
                    demanda INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (loja_id, item_id),
                    FOREIGN KEY (loja_id) REFERENCES lojas(id),
                    FOREIGN KEY (item_id) REFERENCES itens(id)
                );
                """,

                // 7. LIVRO-CAIXA / AUDITORIA CONTÁBIL
                """
                CREATE TABLE IF NOT EXISTS livro_caixa_movimentacoes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    loja_id INTEGER NOT NULL,
                    dia_mundo INTEGER NOT NULL,
                    hora_mundo INTEGER NOT NULL,
                    tipo_operacao TEXT NOT NULL,
                    item_nome TEXT NOT NULL,
                    quantidade INTEGER NOT NULL,
                    valor_latao INTEGER NOT NULL,
                    descricao TEXT,
                    FOREIGN KEY (loja_id) REFERENCES lojas(id)
                );
                """,

                // 8. FICHAS DE PERSONAGENS
                """
                CREATE TABLE IF NOT EXISTS fichas (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    usuario_id INTEGER NOT NULL DEFAULT 1,
                    nome TEXT NOT NULL,
                    raca_nome TEXT NOT NULL,
                    vig INTEGER NOT NULL,
                    forca INTEGER NOT NULL,
                    des INTEGER NOT NULL,
                    men INTEGER NOT NULL,
                    esp INTEGER NOT NULL,
                    von INTEGER NOT NULL,
                    hp INTEGER NOT NULL,
                    hp_max INTEGER NOT NULL,
                    sanidade INTEGER NOT NULL,
                    sanidade_max INTEGER NOT NULL,
                    energia INTEGER NOT NULL,
                    energia_max INTEGER NOT NULL,
                    mana INTEGER NOT NULL,
                    mana_max INTEGER NOT NULL,
                    defesa INTEGER NOT NULL,
                    defesa_oculta INTEGER NOT NULL,
                    pontos_essencia INTEGER NOT NULL DEFAULT 0,
                    carteira_latao INTEGER NOT NULL DEFAULT 0,
                    cidade_id INTEGER,
                    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
                    FOREIGN KEY (raca_nome) REFERENCES racas(nome),
                    FOREIGN KEY (cidade_id) REFERENCES cidades(id)
                );
                """,

                // 9. PERÍCIAS DO PERSONAGEM
                """
                CREATE TABLE IF NOT EXISTS ficha_pericias (
                    ficha_id INTEGER NOT NULL,
                    pericia TEXT NOT NULL,
                    grau INTEGER NOT NULL DEFAULT 0,
                    maestria INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (ficha_id, pericia),
                    FOREIGN KEY (ficha_id) REFERENCES fichas(id) ON DELETE CASCADE
                );
                """,

                // 10. INVENTÁRIO DO PERSONAGEM
                """
                CREATE TABLE IF NOT EXISTS ficha_inventario (
                    ficha_id INTEGER NOT NULL,
                    item_id INTEGER NOT NULL,
                    quantidade INTEGER NOT NULL DEFAULT 1,
                    PRIMARY KEY (ficha_id, item_id),
                    FOREIGN KEY (ficha_id) REFERENCES fichas(id) ON DELETE CASCADE,
                    FOREIGN KEY (item_id) REFERENCES itens(id)
                );
                """,

                // 11. CONTROLE DO MESTRE E FOG-OF-WAR
                """
                CREATE TABLE IF NOT EXISTS controle_mestre (
                    categoria TEXT NOT NULL,
                    chave TEXT NOT NULL,
                    valor TEXT NOT NULL,
                    PRIMARY KEY (categoria, chave)
                );
                """,

                // 12. TABELA DE CONTAS/UTILIZADORES (Login, Papel Jogador/Mestre, Limite de Fichas e Pity)
                """
                CREATE TABLE IF NOT EXISTS usuarios (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    senha TEXT NOT NULL,
                    papel TEXT NOT NULL DEFAULT 'JOGADOR',
                    limite_maximo_fichas INTEGER NOT NULL DEFAULT 1,
                    fator_sorte_pity INTEGER NOT NULL DEFAULT 0
                );
                """,

                // 13. TABELA DE SESSÕES DE CRIAÇÃO DE FICHA EM TEMPO REAL (Espelhamento Jogador -> Mestre)
                """
                CREATE TABLE IF NOT EXISTS sessoes_criacao_ficha (
                    usuario_id INTEGER PRIMARY KEY,
                    nome_personagem_rascunho TEXT,
                    etapa_atual TEXT NOT NULL,
                    opcoes_racas_sorteadas TEXT,
                    raca_escolhida TEXT,
                    linhagem_draconica TEXT,
                    atributos_rolados_json TEXT,
                    impulso_draconico_ativo INTEGER NOT NULL DEFAULT 0,
                    rerolls_concedidos INTEGER NOT NULL DEFAULT 0,
                    dica_mestre TEXT,
                    aprovada_pelo_mestre INTEGER NOT NULL DEFAULT 0,
                    atualizado_em INTEGER NOT NULL,
                    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
                );
                """,

                // 14. CATÁLOGO DO BESTIÁRIO (Monstros Selvagens e Chefes Solo)
                """
                CREATE TABLE IF NOT EXISTS monstros_catalogo (
                    id TEXT PRIMARY KEY,
                    nome TEXT NOT NULL,
                    regiao_nativa TEXT NOT NULL,
                    geologia_nativa TEXT NOT NULL,
                    aquifero_nativo TEXT NOT NULL,
                    elemento_primario TEXT NOT NULL,
                    elemento_secundario TEXT,
                    porte TEXT NOT NULL DEFAULT 'MEDIO',
                    vig_base INTEGER NOT NULL,
                    for_base INTEGER NOT NULL,
                    des_base INTEGER NOT NULL,
                    men_base INTEGER NOT NULL,
                    esp_base INTEGER NOT NULL,
                    von_base INTEGER NOT NULL,
                    domesticavel INTEGER NOT NULL DEFAULT 1,
                    chefe_solo INTEGER NOT NULL DEFAULT 0,
                    multiplicador_hp_chefe INTEGER NOT NULL DEFAULT 1
                );
                """,

                // 15. MOVESET ESTILO POKÉMON DOS MONSTROS (4 Slots Ativos + Learnset por Grau/Rank)
                """
                CREATE TABLE IF NOT EXISTS monstro_golpes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    monstro_catalogo_id TEXT NOT NULL,
                    grau_desbloqueio INTEGER NOT NULL DEFAULT 0,
                    slot_inicial INTEGER NOT NULL DEFAULT 0,
                    nome_golpe TEXT NOT NULL,
                    categoria_pokemon TEXT NOT NULL,
                    elemento TEXT NOT NULL,
                    custo_energia INTEGER NOT NULL DEFAULT 0,
                    custo_mana INTEGER NOT NULL DEFAULT 0,
                    qtd_dados INTEGER NOT NULL DEFAULT 1,
                    faces_dado INTEGER NOT NULL DEFAULT 6,
                    atributo_escala_1 TEXT NOT NULL,
                    atributo_escala_2 TEXT,
                    efeito_colateral TEXT,
                    FOREIGN KEY (monstro_catalogo_id) REFERENCES monstros_catalogo(id) ON DELETE CASCADE
                );
                """,

                // 16. TABELA DE DROPS DE MONSTROS (Qualidade Q1-Q5, Raridade e Regra do Camarão)
                """
                CREATE TABLE IF NOT EXISTS monstro_drops (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    monstro_catalogo_id TEXT NOT NULL,
                    item_nome TEXT NOT NULL,
                    probabilidade REAL NOT NULL,
                    qtd_min INTEGER NOT NULL DEFAULT 1,
                    qtd_max INTEGER NOT NULL DEFAULT 1,
                    raridade TEXT NOT NULL DEFAULT 'COMUM',
                    qualidade_material INTEGER NOT NULL DEFAULT 1,
                    drop_exclusivo INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (monstro_catalogo_id) REFERENCES monstros_catalogo(id) ON DELETE CASCADE
                );
                """,

                // 17. FICHAS DE MONSTROS DOMADOS VINCULADAS À FICHA DO JOGADOR (Compatibilidade Unificada v7/v8)
                """
                CREATE TABLE IF NOT EXISTS ficha_monstros_domados (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ficha_id INTEGER,
                    ficha_dono_id INTEGER,
                    monstro_catalogo_id TEXT,
                    apelido TEXT,
                    ficha_propria_id INTEGER,
                    nome TEXT,
                    especie TEXT,
                    porte TEXT,
                    domesticavel INTEGER NOT NULL DEFAULT 1,
                    vig INTEGER NOT NULL DEFAULT 10,
                    forca INTEGER NOT NULL DEFAULT 10,
                    des INTEGER NOT NULL DEFAULT 10,
                    men INTEGER NOT NULL DEFAULT 10,
                    esp INTEGER NOT NULL DEFAULT 10,
                    von INTEGER NOT NULL DEFAULT 10,
                    hp INTEGER NOT NULL DEFAULT 30,
                    hp_max INTEGER NOT NULL DEFAULT 30,
                    golpe_slot_1 TEXT,
                    golpe_slot_2 TEXT,
                    golpe_slot_3 TEXT,
                    golpe_slot_4 TEXT,
                    besta_de_carga_ativa INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (ficha_dono_id) REFERENCES fichas(id) ON DELETE CASCADE,
                    FOREIGN KEY (monstro_catalogo_id) REFERENCES monstros_catalogo(id),
                    FOREIGN KEY (ficha_propria_id) REFERENCES fichas(id) ON DELETE CASCADE,
                    FOREIGN KEY (ficha_id) REFERENCES fichas(id) ON DELETE CASCADE
                );
                """
        };

        String[] tabelasNovas = {
            // Sessões de login (só o SHA-256 do token é guardado)
            """
            CREATE TABLE IF NOT EXISTS sessoes_login (
                token_hash TEXT PRIMARY KEY,
                usuario_id INTEGER NOT NULL,
                criada_em INTEGER NOT NULL,
                expira_em INTEGER NOT NULL
            );
            """,
            // Condições ativas no personagem (envenenado, abençoado, exausto...)
            """
            CREATE TABLE IF NOT EXISTS ficha_condicoes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ficha_id INTEGER NOT NULL,
                nome TEXT NOT NULL,
                tipo TEXT NOT NULL DEFAULT 'NEGATIVA',
                intensidade INTEGER NOT NULL DEFAULT 1,
                duracao_turnos INTEGER,
                descricao TEXT,
                FOREIGN KEY (ficha_id) REFERENCES fichas(id) ON DELETE CASCADE
            );
            """,
            // Magias e técnicas conhecidas pelo personagem
            """
            CREATE TABLE IF NOT EXISTS ficha_magias (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ficha_id INTEGER NOT NULL,
                nome TEXT NOT NULL,
                escola TEXT,
                elemento TEXT,
                circulo INTEGER NOT NULL DEFAULT 1,
                custo_mana INTEGER NOT NULL DEFAULT 0,
                custo_energia INTEGER NOT NULL DEFAULT 0,
                alcance_m REAL NOT NULL DEFAULT 0,
                dado TEXT,
                tempo_conjuracao TEXT,
                duracao TEXT,
                descricao TEXT,
                preparada INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY (ficha_id) REFERENCES fichas(id) ON DELETE CASCADE
            );
            """,
            // Receitas de manufatura (crafting) cadastradas pelo Mestre
            """
            CREATE TABLE IF NOT EXISTS receitas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nome TEXT NOT NULL,
                item_resultado_id INTEGER NOT NULL,
                quantidade_resultado INTEGER NOT NULL DEFAULT 1,
                pericia TEXT,
                grau_minimo INTEGER NOT NULL DEFAULT 0,
                tempo_horas REAL NOT NULL DEFAULT 1,
                descricao TEXT,
                FOREIGN KEY (item_resultado_id) REFERENCES itens(id)
            );
            """,
            """
            CREATE TABLE IF NOT EXISTS receita_insumos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                receita_id INTEGER NOT NULL,
                item_id INTEGER NOT NULL,
                quantidade INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY (receita_id) REFERENCES receitas(id) ON DELETE CASCADE,
                FOREIGN KEY (item_id) REFERENCES itens(id)
            );
            """,
            // Marcadores de carga inicial já executada (evita recriar o que o Mestre apagou)
            """
            CREATE TABLE IF NOT EXISTS meta_sementes (
                chave TEXT PRIMARY KEY,
                aplicada_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """
        };

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : tabelasDdl) {
                stmt.execute(sql);
            }
            for (String sql : tabelasNovas) {
                stmt.execute(sql);
            }

            // Migração defensiva caso ficha_monstros_domados já existisse sem as colunas v8
            String[] colunasMigracao = {
                "ALTER TABLE ficha_monstros_domados ADD COLUMN ficha_dono_id INTEGER",
                "ALTER TABLE ficha_monstros_domados ADD COLUMN monstro_catalogo_id TEXT",
                "ALTER TABLE ficha_monstros_domados ADD COLUMN apelido TEXT",
                "ALTER TABLE ficha_monstros_domados ADD COLUMN ficha_propria_id INTEGER",
                "ALTER TABLE ficha_monstros_domados ADD COLUMN golpe_slot_1 TEXT",
                "ALTER TABLE ficha_monstros_domados ADD COLUMN golpe_slot_2 TEXT",
                "ALTER TABLE ficha_monstros_domados ADD COLUMN golpe_slot_3 TEXT",
                "ALTER TABLE ficha_monstros_domados ADD COLUMN golpe_slot_4 TEXT",
                "ALTER TABLE ficha_monstros_domados ADD COLUMN besta_de_carga_ativa INTEGER NOT NULL DEFAULT 1",
                "ALTER TABLE fichas ADD COLUMN imagem_url TEXT",
                "ALTER TABLE fichas ADD COLUMN usuario_id INTEGER NOT NULL DEFAULT 1",
                "ALTER TABLE ficha_pericias ADD COLUMN nome_exibicao TEXT",
                "ALTER TABLE ficha_pericias ADD COLUMN atributo_base TEXT",
                "ALTER TABLE ficha_pericias ADD COLUMN descricao TEXT",
                "ALTER TABLE fichas ADD COLUMN carteira_moedas TEXT",
                // Ficha: história e identidade do personagem
                "ALTER TABLE fichas ADD COLUMN historia TEXT",
                "ALTER TABLE fichas ADD COLUMN aparencia TEXT",
                "ALTER TABLE fichas ADD COLUMN personalidade TEXT",
                "ALTER TABLE fichas ADD COLUMN anotacoes TEXT",
                // Cidades: posição e apresentação no mapa (viewBox 1536 x 1024)
                "ALTER TABLE cidades ADD COLUMN mapa_x REAL",
                "ALTER TABLE cidades ADD COLUMN mapa_y REAL",
                "ALTER TABLE cidades ADD COLUMN reino TEXT",
                "ALTER TABLE cidades ADD COLUMN icone_mapa TEXT",
                "ALTER TABLE cidades ADD COLUMN descricao TEXT",
                // Ilustrações e descrições do catálogo (data URL ou endereço http/https)
                "ALTER TABLE monstros_catalogo ADD COLUMN imagem_url TEXT",
                "ALTER TABLE monstros_catalogo ADD COLUMN descricao TEXT",
                "ALTER TABLE itens ADD COLUMN imagem_url TEXT"
            };
            for (String colSql : colunasMigracao) {
                try {
                    stmt.execute(colSql);
                } catch (SQLException ignored) {
                    // Coluna já presente
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao inicializar esquema do banco SQLite!", e);
        }
    }
}
