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

                // 12. MONSTROS DOMADOS DO PERSONAGEM (SISTEMA DE DOMA)
                """
                CREATE TABLE IF NOT EXISTS ficha_monstros_domados (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ficha_id INTEGER NOT NULL,
                    nome TEXT NOT NULL,
                    especie TEXT NOT NULL,
                    porte TEXT NOT NULL,
                    domesticavel INTEGER NOT NULL DEFAULT 1,
                    vig INTEGER NOT NULL,
                    forca INTEGER NOT NULL,
                    des INTEGER NOT NULL,
                    men INTEGER NOT NULL,
                    esp INTEGER NOT NULL,
                    von INTEGER NOT NULL,
                    hp INTEGER NOT NULL,
                    hp_max INTEGER NOT NULL,
                    FOREIGN KEY (ficha_id) REFERENCES fichas(id) ON DELETE CASCADE
                );
                """
        };

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : tabelasDdl) {
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao inicializar esquema do banco SQLite!", e);
        }
    }
}
