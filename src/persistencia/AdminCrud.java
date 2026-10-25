package persistencia;

import economia.Item;
import economia.Lojas;
import geografia.Aquifero;
import geografia.Cidade;
import geografia.Conexao;
import geografia.Geologia;
import geografia.Regiao;
import personagem.Monstro;

import java.sql.*;
import java.util.*;

/**
 * CRUD genérico do painel de gerenciamento do Mestre.
 *
 * Cada entidade é descrita por um esquema (tabela, chave, campos tipados e
 * validações). O mesmo esquema é enviado ao front-end, que monta formulários
 * e listas a partir dele. Validações que envolvem mais de um campo ou outras
 * tabelas ficam em {@link #validarRegrasDaEntidade}.
 */
public class AdminCrud {

    public enum TipoCampo { TEXTO, TEXTO_LONGO, INTEIRO, REAL, BOOLEANO, ENUM, REFERENCIA, IMAGEM }

    /** Tamanho máximo de uma imagem em data URL (o front reduz para 512 px antes de enviar). */
    public static final int IMAGEM_MAX_CARACTERES = 1_500_000;

    public static final class Campo {
        public final String coluna;
        public final String rotulo;
        public final TipoCampo tipo;
        public boolean obrigatorio;
        public Double min;
        public Double max;
        public List<String> opcoes = List.of();
        /** Entidade referenciada (TipoCampo.REFERENCIA). */
        public String referencia;
        public String ajuda;
        public Object padrao;
        /** Exibir como coluna na listagem. */
        public boolean naLista;
        /** Seção do formulário em que o campo aparece (ex.: "Atributos base"). */
        public String grupo;

        Campo(String coluna, String rotulo, TipoCampo tipo) {
            this.coluna = coluna;
            this.rotulo = rotulo;
            this.tipo = tipo;
        }

        Campo obrigatorio() { this.obrigatorio = true; return this; }
        Campo faixa(double min, double max) { this.min = min; this.max = max; return this; }
        Campo minimo(double min) { this.min = min; return this; }
        Campo ajuda(String a) { this.ajuda = a; return this; }
        Campo padrao(Object p) { this.padrao = p; return this; }
        Campo lista() { this.naLista = true; return this; }
    }

    public static final class Entidade {
        public final String id;
        public final String rotulo;
        public final String tabela;
        public final String chave;
        /** Chave textual informada pelo usuário (ex.: id de monstro). */
        public boolean chaveTexto;
        /** Chave gerada pelo banco (AUTOINCREMENT). Se falso e numérica, usa MAX(id)+1. */
        public boolean chaveAuto;
        public String colunaTitulo;
        /** Entidade-mãe para registros filhos (ex.: golpes de um monstro). */
        public String pai;
        public String colunaPai;
        /** Alterações exigem recarregar cidades/rotas/lojas em memória. */
        public boolean afetaMundo;
        public String descricao;
        public final List<Campo> campos = new ArrayList<>();
        /** Grupo aplicado aos próximos campos declarados (ver {@link #grupo(String)}). */
        private String grupoAtual;

        Entidade(String id, String rotulo, String tabela, String chave) {
            this.id = id;
            this.rotulo = rotulo;
            this.tabela = tabela;
            this.chave = chave;
        }

        /** Os campos declarados a seguir ficam nesta seção do formulário. */
        Entidade grupo(String nome) {
            this.grupoAtual = nome;
            return this;
        }

        Campo campo(String coluna, String rotulo, TipoCampo tipo) {
            Campo c = new Campo(coluna, rotulo, tipo);
            c.grupo = grupoAtual;
            campos.add(c);
            return c;
        }

        Campo enumCampo(String coluna, String rotulo, Enum<?>[] valores) {
            Campo c = campo(coluna, rotulo, TipoCampo.ENUM);
            List<String> nomes = new ArrayList<>();
            for (Enum<?> v : valores) nomes.add(v.name());
            c.opcoes = nomes;
            return c;
        }

        Campo opcoesCampo(String coluna, String rotulo, String... valores) {
            Campo c = campo(coluna, rotulo, TipoCampo.ENUM);
            c.opcoes = List.of(valores);
            return c;
        }

        Campo ref(String coluna, String rotulo, String entidade) {
            Campo c = campo(coluna, rotulo, TipoCampo.REFERENCIA);
            c.referencia = entidade;
            return c;
        }
    }

    /** Erro de validação com mensagem pronta para o usuário. */
    public static class ErroValidacao extends RuntimeException {
        public ErroValidacao(String mensagem) { super(mensagem); }
    }

    private static final String[] ATRIBUTOS = { "VIG", "FOR", "DES", "MEN", "ESP", "VON" };
    private static final String[] ELEMENTOS = { "FOGO", "AGUA", "GELO", "TERRA", "AR", "RAIO", "LUZ", "TREVAS", "NATUREZA", "VENENO", "ARCANO", "NEUTRO" };

    private final Map<String, Entidade> entidades = new LinkedHashMap<>();

    public AdminCrud() {
        // ---------------- Itens ----------------
        Entidade itens = nova("itens", "Itens", "itens", "id");
        itens.colunaTitulo = "nome";
        itens.descricao = "Catálogo de itens usado por lojas, receitas, drops e inventários.";
        itens.grupo("Identidade");
        itens.campo("imagem_url", "Ilustração", TipoCampo.IMAGEM).ajuda("Envie uma imagem ou cole um endereço http(s).");
        itens.campo("nome", "Nome", TipoCampo.TEXTO).obrigatorio().lista();
        itens.enumCampo("tipo", "Tipo", Item.TipoItem.values()).obrigatorio().padrao("OUTRO").lista();
        itens.enumCampo("raridade", "Raridade", Item.RaridadeItem.values()).obrigatorio().padrao("COMUM").lista();
        itens.campo("descricao", "Descrição", TipoCampo.TEXTO_LONGO);
        itens.grupo("Valores");
        itens.campo("preco_latao", "Preço (latão)", TipoCampo.INTEIRO).obrigatorio().minimo(0).padrao(0).lista()
                .ajuda("Preço de referência em latão (1 PO = 10.000 latão).");
        itens.campo("peso_kg", "Peso (kg)", TipoCampo.REAL).obrigatorio().minimo(0).padrao(0).lista();
        itens.campo("tier", "Tier", TipoCampo.INTEIRO).obrigatorio().faixa(1, 10).padrao(1);

        // ---------------- Receitas ----------------
        Entidade receitas = nova("receitas", "Receitas", "receitas", "id");
        receitas.chaveAuto = true;
        receitas.colunaTitulo = "nome";
        receitas.descricao = "Receitas de manufatura: item produzido, perícia exigida e insumos.";
        receitas.campo("nome", "Nome", TipoCampo.TEXTO).obrigatorio().lista();
        receitas.ref("item_resultado_id", "Item produzido", "itens").obrigatorio().lista();
        receitas.campo("quantidade_resultado", "Quantidade produzida", TipoCampo.INTEIRO).obrigatorio().faixa(1, 999).padrao(1).lista();
        receitas.campo("pericia", "Perícia exigida", TipoCampo.TEXTO).ajuda("Ex.: Medicina / Alquimia, Atletismo, Ferraria…");
        receitas.campo("grau_minimo", "Grau mínimo", TipoCampo.INTEIRO).obrigatorio().faixa(0, 20).padrao(0);
        receitas.campo("tempo_horas", "Tempo (horas)", TipoCampo.REAL).obrigatorio().minimo(0).padrao(1).lista();
        receitas.campo("descricao", "Descrição", TipoCampo.TEXTO_LONGO);

        Entidade insumos = nova("receita_insumos", "Insumos", "receita_insumos", "id");
        insumos.chaveAuto = true;
        insumos.pai = "receitas";
        insumos.colunaPai = "receita_id";
        insumos.ref("item_id", "Item", "itens").obrigatorio().lista();
        insumos.campo("quantidade", "Quantidade", TipoCampo.INTEIRO).obrigatorio().faixa(1, 9999).padrao(1).lista();

        // ---------------- Monstros ----------------
        Entidade monstros = nova("monstros", "Monstros", "monstros_catalogo", "id");
        monstros.chaveTexto = true;
        monstros.colunaTitulo = "nome";
        monstros.descricao = "Bestiário: atributos, habitat, golpes e drops de cada espécie.";
        monstros.grupo("Identidade");
        monstros.campo("imagem_url", "Ilustração", TipoCampo.IMAGEM).ajuda("Aparece no bestiário dos jogadores quando a espécie for revelada.");
        monstros.campo("nome", "Nome", TipoCampo.TEXTO).obrigatorio().lista();
        monstros.opcoesCampo("elemento_primario", "Elemento primário", ELEMENTOS).obrigatorio().padrao("NEUTRO").lista();
        monstros.opcoesCampo("elemento_secundario", "Elemento secundário", ELEMENTOS);
        monstros.enumCampo("porte", "Porte", Monstro.PorteMonstro.values()).obrigatorio().padrao("MEDIO").lista();
        monstros.campo("descricao", "Descrição / lenda", TipoCampo.TEXTO_LONGO).ajuda("Texto exibido no bestiário.");
        monstros.grupo("Habitat");
        monstros.enumCampo("regiao_nativa", "Região nativa", Regiao.values()).obrigatorio().padrao("TEMPERADO").lista();
        monstros.enumCampo("geologia_nativa", "Geologia nativa", Geologia.values()).obrigatorio().padrao("PLANICIE");
        monstros.enumCampo("aquifero_nativo", "Aquífero nativo", Aquifero.values()).obrigatorio().padrao("RIO");
        monstros.grupo("Atributos base");
        for (String[] a : new String[][] { { "vig_base", "VIG" }, { "for_base", "FOR" }, { "des_base", "DES" },
                { "men_base", "MEN" }, { "esp_base", "ESP" }, { "von_base", "VON" } }) {
            monstros.campo(a[0], a[1], TipoCampo.INTEIRO).obrigatorio().faixa(0, 500).padrao(10);
        }
        monstros.grupo("Regras de combate");
        monstros.campo("domesticavel", "Domesticável", TipoCampo.BOOLEANO).padrao(true);
        monstros.campo("chefe_solo", "Chefe solo", TipoCampo.BOOLEANO).padrao(false).lista();
        monstros.campo("multiplicador_hp_chefe", "Multiplicador de HP (chefe)", TipoCampo.INTEIRO).obrigatorio().faixa(1, 100).padrao(1);

        Entidade golpes = nova("monstro_golpes", "Golpes", "monstro_golpes", "id");
        golpes.chaveAuto = true;
        golpes.pai = "monstros";
        golpes.colunaPai = "monstro_catalogo_id";
        golpes.colunaTitulo = "nome_golpe";
        golpes.campo("nome_golpe", "Nome do golpe", TipoCampo.TEXTO).obrigatorio().lista();
        golpes.opcoesCampo("categoria_pokemon", "Categoria", "FISICO", "FISICO_CONTATO", "ESPECIAL_ELEMENTAL", "STATUS_TATICO")
                .obrigatorio().padrao("FISICO").lista();
        golpes.opcoesCampo("elemento", "Elemento", ELEMENTOS).obrigatorio().padrao("NEUTRO").lista();
        golpes.campo("qtd_dados", "Qtd. de dados", TipoCampo.INTEIRO).obrigatorio().faixa(0, 50).padrao(1).lista();
        golpes.campo("faces_dado", "Faces do dado", TipoCampo.INTEIRO).obrigatorio().faixa(0, 100).padrao(6).lista();
        golpes.campo("custo_energia", "Custo de energia", TipoCampo.INTEIRO).obrigatorio().minimo(0).padrao(0);
        golpes.campo("custo_mana", "Custo de mana", TipoCampo.INTEIRO).obrigatorio().minimo(0).padrao(0);
        golpes.opcoesCampo("atributo_escala_1", "Atributo de escala", ATRIBUTOS).obrigatorio().padrao("FOR");
        golpes.opcoesCampo("atributo_escala_2", "2º atributo de escala", ATRIBUTOS);
        golpes.campo("grau_desbloqueio", "Grau de desbloqueio", TipoCampo.INTEIRO).obrigatorio().faixa(0, 50).padrao(0);
        golpes.campo("slot_inicial", "Slot inicial (0 = não equipado)", TipoCampo.INTEIRO).obrigatorio().faixa(0, 4).padrao(0);
        golpes.campo("efeito_colateral", "Efeito colateral", TipoCampo.TEXTO_LONGO);

        Entidade drops = nova("monstro_drops", "Drops", "monstro_drops", "id");
        drops.chaveAuto = true;
        drops.pai = "monstros";
        drops.colunaPai = "monstro_catalogo_id";
        drops.colunaTitulo = "item_nome";
        drops.campo("item_nome", "Item", TipoCampo.TEXTO).obrigatorio().lista();
        drops.campo("probabilidade", "Probabilidade (0 a 1)", TipoCampo.REAL).obrigatorio().faixa(0, 1).padrao(0.5).lista();
        drops.campo("qtd_min", "Qtd. mínima", TipoCampo.INTEIRO).obrigatorio().faixa(0, 999).padrao(1).lista();
        drops.campo("qtd_max", "Qtd. máxima", TipoCampo.INTEIRO).obrigatorio().faixa(0, 999).padrao(1).lista();
        drops.enumCampo("raridade", "Raridade", Item.RaridadeItem.values()).obrigatorio().padrao("COMUM");
        drops.campo("qualidade_material", "Qualidade (Q1–Q5)", TipoCampo.INTEIRO).obrigatorio().faixa(1, 5).padrao(1);
        drops.campo("drop_exclusivo", "Drop exclusivo", TipoCampo.BOOLEANO).padrao(false);

        // ---------------- Cidades ----------------
        Entidade cidades = nova("cidades", "Cidades", "cidades", "id");
        cidades.colunaTitulo = "nome";
        cidades.afetaMundo = true;
        cidades.descricao = "Cidades do mundo e sua posição no mapa.";
        cidades.campo("nome", "Nome", TipoCampo.TEXTO).obrigatorio().lista();
        cidades.enumCampo("tipo", "Assentamento", Cidade.TipoAssentamento.values()).obrigatorio().padrao("CIDADE").lista();
        cidades.opcoesCampo("reino", "Reino", "frosthelm", "sylvanor", "aurelia", "drakos", "valthera", "campos", "ilhas").lista();
        cidades.opcoesCampo("icone_mapa", "Ícone no mapa", "capital", "cidade", "vila", "porto", "fortaleza").padrao("cidade");
        cidades.campo("mapa_x", "Posição X no mapa", TipoCampo.REAL).faixa(0, 1536).ajuda("Clique no mapa para posicionar.");
        cidades.campo("mapa_y", "Posição Y no mapa", TipoCampo.REAL).faixa(0, 1024);
        cidades.campo("tem_porto", "Possui porto", TipoCampo.BOOLEANO).padrao(false).lista().ajuda("Necessário para rotas marítimas.");
        cidades.enumCampo("regiao", "Clima / região", Regiao.values()).obrigatorio().padrao("TEMPERADO");
        cidades.enumCampo("geologia", "Geologia", Geologia.values()).obrigatorio().padrao("PLANICIE");
        cidades.enumCampo("aquifero", "Aquífero", Aquifero.values()).obrigatorio().padrao("RIO");
        cidades.campo("e_fronteira", "Cidade de fronteira", TipoCampo.BOOLEANO).padrao(false);
        cidades.enumCampo("regiao_fronteira", "Região de fronteira", Regiao.values());
        cidades.campo("descricao", "Descrição", TipoCampo.TEXTO_LONGO);

        // ---------------- Rotas ----------------
        Entidade rotas = nova("rotas", "Rotas", "conexoes", "id");
        rotas.chaveAuto = true;
        rotas.afetaMundo = true;
        rotas.descricao = "Estradas e rotas marítimas entre cidades.";
        rotas.ref("origem_id", "Origem", "cidades").obrigatorio().lista();
        rotas.ref("destino_id", "Destino", "cidades").obrigatorio().lista();
        rotas.enumCampo("tipo_rota", "Tipo", Conexao.TipoDeRota.values()).obrigatorio().padrao("TERRESTRE").lista()
                .ajuda("Rotas marítimas exigem porto nas duas cidades.");
        rotas.campo("distancia_km", "Distância (km)", TipoCampo.INTEIRO).obrigatorio().faixa(1, 100000).padrao(100).lista();
        rotas.campo("perigo", "Perigo (1–10)", TipoCampo.INTEIRO).obrigatorio().faixa(1, 10).padrao(3).lista();
        rotas.campo("bloqueada", "Bloqueada", TipoCampo.BOOLEANO).padrao(false).lista();

        // ---------------- Lojas ----------------
        Entidade lojas = nova("lojas", "Lojas", "lojas", "id");
        lojas.chaveAuto = true;
        lojas.colunaTitulo = "nome";
        lojas.afetaMundo = true;
        lojas.descricao = "Estabelecimentos comerciais de cada cidade.";
        lojas.campo("nome", "Nome", TipoCampo.TEXTO).obrigatorio().lista();
        lojas.campo("mercador", "Mercador", TipoCampo.TEXTO).obrigatorio().lista();
        lojas.ref("cidade_id", "Cidade", "cidades").obrigatorio().lista();
        lojas.enumCampo("tipo", "Tipo", Lojas.TipoEstabelecimento.values()).obrigatorio().padrao("LOJA_GERAL").lista();
        lojas.campo("caixa_latao", "Caixa (latão)", TipoCampo.INTEIRO).obrigatorio().minimo(0).padrao(0);
        lojas.campo("e_rede", "Faz parte de uma rede", TipoCampo.BOOLEANO).padrao(false);
        lojas.campo("nome_rede", "Nome da rede", TipoCampo.TEXTO);

        Entidade estoque = nova("loja_estoque", "Estoque", "loja_estoque", "rowid");
        estoque.chaveAuto = true;
        estoque.pai = "lojas";
        estoque.colunaPai = "loja_id";
        estoque.ref("item_id", "Item", "itens").obrigatorio().lista();
        estoque.campo("quantidade", "Quantidade", TipoCampo.INTEIRO).obrigatorio().minimo(0).padrao(1).lista();
        estoque.campo("demanda", "Demanda", TipoCampo.INTEIRO).obrigatorio().minimo(0).padrao(0).lista();
    }

    private Entidade nova(String id, String rotulo, String tabela, String chave) {
        Entidade e = new Entidade(id, rotulo, tabela, chave);
        entidades.put(id, e);
        return e;
    }

    public Collection<Entidade> getEntidades() { return entidades.values(); }

    public Entidade obter(String id) {
        Entidade e = entidades.get(id);
        if (e == null) throw new ErroValidacao("Entidade desconhecida: " + id);
        return e;
    }

    // ------------------------------------------------------------------
    // Listagem
    // ------------------------------------------------------------------

    public List<Map<String, Object>> listar(Entidade e, String valorPai) {
        StringBuilder sql = new StringBuilder("SELECT ").append(e.chave).append(" AS __id");
        for (Campo c : e.campos) sql.append(", ").append(c.coluna);
        if (e.colunaPai != null) sql.append(", ").append(e.colunaPai);
        sql.append(" FROM ").append(e.tabela);
        if (e.colunaPai != null) {
            if (valorPai == null || valorPai.isBlank()) throw new ErroValidacao("Informe o registro pai de " + e.rotulo + ".");
            sql.append(" WHERE ").append(e.colunaPai).append(" = ?");
        }
        sql.append(" ORDER BY ").append(e.colunaTitulo != null ? e.colunaTitulo + " COLLATE NOCASE" : e.chave);

        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (e.colunaPai != null) ps.setString(1, valorPai);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> linha = new LinkedHashMap<>();
                    linha.put("id", rs.getObject("__id"));
                    for (Campo c : e.campos) linha.put(c.coluna, lerValor(rs, c));
                    lista.add(linha);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erro ao listar " + e.rotulo + ": " + ex.getMessage(), ex);
        }
        return lista;
    }

    private Object lerValor(ResultSet rs, Campo c) throws SQLException {
        Object bruto = rs.getObject(c.coluna);
        if (bruto == null) return null;
        return switch (c.tipo) {
            case INTEIRO, REFERENCIA -> rs.getLong(c.coluna);
            case REAL -> rs.getDouble(c.coluna);
            case BOOLEANO -> rs.getInt(c.coluna) == 1;
            default -> rs.getString(c.coluna);
        };
    }

    // ------------------------------------------------------------------
    // Salvar (inserir ou atualizar)
    // ------------------------------------------------------------------

    /** Resultado de um salvamento: chave gravada e, para cidades, o nome anterior. */
    public static final class Salvo {
        public final String id;
        public final String nomeAnterior;
        Salvo(String id, String nomeAnterior) { this.id = id; this.nomeAnterior = nomeAnterior; }
    }

    public Salvo salvar(Entidade e, Map<String, String> params) {
        String idParam = params.getOrDefault("id", "").trim();
        Map<String, Object> valores = new LinkedHashMap<>();
        List<String> erros = new ArrayList<>();
        for (Campo c : e.campos) {
            try {
                valores.put(c.coluna, converter(c, params.get(c.coluna)));
            } catch (ErroValidacao ex) {
                erros.add(ex.getMessage());
            }
        }
        if (!erros.isEmpty()) throw new ErroValidacao(String.join(" ", erros));

        String valorPai = null;
        if (e.colunaPai != null) {
            valorPai = params.getOrDefault(e.colunaPai, "").trim();
            if (valorPai.isBlank()) throw new ErroValidacao("Registro pai não informado.");
            if (!existe(obter(e.pai), valorPai)) throw new ErroValidacao("O registro pai não existe mais.");
        }

        try (Connection conn = BancoDados.getConnection()) {
            boolean edicao = !idParam.isBlank() && existe(conn, e, idParam);
            if (!idParam.isBlank() && !edicao && !e.chaveTexto && !"itens".equals(e.id) && !"cidades".equals(e.id)) {
                throw new ErroValidacao("Registro #" + idParam + " não encontrado. Ele pode ter sido excluído.");
            }
            String nomeAnterior = edicao && "cidades".equals(e.id) ? lerTexto(conn, "SELECT nome FROM cidades WHERE id = ?", idParam) : null;
            validarRegrasDaEntidade(conn, e, idParam, edicao, valores);

            if (edicao) {
                StringBuilder sql = new StringBuilder("UPDATE ").append(e.tabela).append(" SET ");
                int i = 0;
                for (String col : valores.keySet()) sql.append(i++ > 0 ? ", " : "").append(col).append(" = ?");
                sql.append(" WHERE ").append(e.chave).append(" = ?");
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    int p = 1;
                    for (Object v : valores.values()) ps.setObject(p++, paraBanco(v));
                    ps.setString(p, idParam);
                    ps.executeUpdate();
                }
                return new Salvo(idParam, nomeAnterior);
            }

            String novaChave = null;
            if (e.chaveTexto) {
                novaChave = !idParam.isBlank() ? idParam : gerarChaveTexto((String) valores.get(e.colunaTitulo));
                if (!novaChave.matches("[A-Za-z0-9_\\-]{1,60}")) {
                    throw new ErroValidacao("O identificador deve ter só letras, números, _ ou - (até 60).");
                }
                if (existe(conn, e, novaChave)) throw new ErroValidacao("Já existe um registro com o identificador " + novaChave + ".");
            } else if (!e.chaveAuto) {
                novaChave = !idParam.isBlank() ? idParam
                        : String.valueOf(lerLong(conn, "SELECT COALESCE(MAX(" + e.chave + "), 0) + 1 FROM " + e.tabela));
            }

            List<String> colunas = new ArrayList<>(valores.keySet());
            List<Object> args = new ArrayList<>(valores.values());
            if (novaChave != null) { colunas.add(0, e.chave); args.add(0, novaChave); }
            if (e.colunaPai != null) { colunas.add(e.colunaPai); args.add(valorPai); }

            String sql = "INSERT INTO " + e.tabela + " (" + String.join(", ", colunas) + ") VALUES ("
                    + String.join(", ", Collections.nCopies(colunas.size(), "?")) + ")";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, paraBanco(args.get(i)));
                ps.executeUpdate();
                if (novaChave == null) {
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) novaChave = String.valueOf(rs.getLong(1));
                    }
                }
            }
            return new Salvo(novaChave, null);
        } catch (SQLException ex) {
            if (String.valueOf(ex.getMessage()).contains("UNIQUE")) throw new ErroValidacao("Já existe um registro com esses dados.");
            throw new RuntimeException("Erro ao salvar " + e.rotulo + ": " + ex.getMessage(), ex);
        }
    }

    private Object converter(Campo c, String bruto) {
        String v = bruto == null ? "" : bruto.trim();
        if (v.isEmpty()) {
            if (c.tipo == TipoCampo.BOOLEANO) return Boolean.TRUE.equals(c.padrao);
            if (c.obrigatorio) throw new ErroValidacao(c.rotulo + " é obrigatório.");
            return null;
        }
        switch (c.tipo) {
            case TEXTO -> {
                if (v.length() > 120) throw new ErroValidacao(c.rotulo + ": no máximo 120 caracteres.");
                return v;
            }
            case TEXTO_LONGO -> {
                if (v.length() > 5000) throw new ErroValidacao(c.rotulo + ": no máximo 5000 caracteres.");
                return v;
            }
            case IMAGEM -> {
                boolean dataUrl = v.matches("(?s)^data:image/(png|jpeg|webp|gif);base64,[A-Za-z0-9+/=\\s]+$");
                boolean endereco = v.matches("^https?://\\S+$");
                if (!dataUrl && !endereco) throw new ErroValidacao(c.rotulo + ": envie uma imagem (PNG, JPG, WEBP ou GIF) ou um endereço http(s).");
                if (v.length() > IMAGEM_MAX_CARACTERES) throw new ErroValidacao(c.rotulo + ": imagem grande demais. Use uma de até ~1 MB.");
                return v;
            }
            case BOOLEANO -> {
                return "true".equalsIgnoreCase(v) || "1".equals(v) || "sim".equalsIgnoreCase(v);
            }
            case ENUM -> {
                if (!c.opcoes.contains(v)) throw new ErroValidacao(c.rotulo + ": valor inválido (" + v + ").");
                return v;
            }
            case INTEIRO, REFERENCIA -> {
                long n;
                try {
                    n = Long.parseLong(v);
                } catch (NumberFormatException ex) {
                    throw new ErroValidacao(c.rotulo + " deve ser um número inteiro.");
                }
                checarFaixa(c, n);
                return n;
            }
            case REAL -> {
                double d;
                try {
                    d = Double.parseDouble(v.replace(',', '.'));
                } catch (NumberFormatException ex) {
                    throw new ErroValidacao(c.rotulo + " deve ser um número.");
                }
                if (Double.isNaN(d) || Double.isInfinite(d)) throw new ErroValidacao(c.rotulo + " deve ser um número.");
                checarFaixa(c, d);
                return d;
            }
        }
        return v;
    }

    private void checarFaixa(Campo c, double n) {
        if (c.min != null && n < c.min) throw new ErroValidacao(c.rotulo + " deve ser no mínimo " + formatar(c.min) + ".");
        if (c.max != null && n > c.max) throw new ErroValidacao(c.rotulo + " deve ser no máximo " + formatar(c.max) + ".");
    }

    private static String formatar(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static Object paraBanco(Object v) {
        if (v instanceof Boolean b) return b ? 1 : 0;
        return v;
    }

    private String gerarChaveTexto(String nome) {
        String base = java.text.Normalizer.normalize(nome == null ? "monstro" : nome, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        if (base.isBlank()) base = "MONSTRO";
        return base.length() > 50 ? base.substring(0, 50) : base;
    }

    /** Regras que envolvem outros campos ou tabelas. */
    private void validarRegrasDaEntidade(Connection conn, Entidade e, String id, boolean edicao, Map<String, Object> v) throws SQLException {
        switch (e.id) {
            case "cidades" -> {
                Long repetida = lerLongOuNull(conn, "SELECT id FROM cidades WHERE UPPER(nome) = UPPER(?) AND id <> ?", v.get("nome"), edicao ? id : "-1");
                if (repetida != null) throw new ErroValidacao("Já existe uma cidade chamada " + v.get("nome") + ".");
                if ((v.get("mapa_x") == null) != (v.get("mapa_y") == null)) {
                    throw new ErroValidacao("Informe X e Y juntos (ou deixe os dois vazios).");
                }
                if (edicao && !Boolean.TRUE.equals(v.get("tem_porto"))) {
                    long maritimas = lerLong(conn, "SELECT COUNT(*) FROM conexoes WHERE tipo_rota = 'MARITIMA' AND (origem_id = ? OR destino_id = ?)", id, id);
                    if (maritimas > 0) {
                        throw new ErroValidacao("A cidade tem " + maritimas + " rota(s) marítima(s); remova-as antes de retirar o porto.");
                    }
                }
            }
            case "rotas" -> {
                Object o = v.get("origem_id"), d = v.get("destino_id");
                if (Objects.equals(o, d)) throw new ErroValidacao("Origem e destino devem ser cidades diferentes.");
                if (!existeCidade(conn, o) || !existeCidade(conn, d)) throw new ErroValidacao("Cidade de origem ou destino não encontrada.");
                if ("MARITIMA".equals(v.get("tipo_rota"))) {
                    long portos = lerLong(conn, "SELECT COUNT(*) FROM cidades WHERE tem_porto = 1 AND id IN (?, ?)", o, d);
                    if (portos < 2) throw new ErroValidacao("Rotas marítimas exigem porto nas duas cidades.");
                }
                long repetidas = lerLong(conn, "SELECT COUNT(*) FROM conexoes WHERE tipo_rota = ? AND id <> ? AND "
                        + "((origem_id = ? AND destino_id = ?) OR (origem_id = ? AND destino_id = ?))",
                        v.get("tipo_rota"), edicao ? id : "-1", o, d, d, o);
                if (repetidas > 0) throw new ErroValidacao("Já existe uma rota desse tipo entre essas cidades.");
            }
            case "monstro_drops" -> {
                Object min = v.get("qtd_min"), max = v.get("qtd_max");
                if (min instanceof Long a && max instanceof Long b && a > b) {
                    throw new ErroValidacao("A quantidade mínima não pode ser maior que a máxima.");
                }
            }
            case "receitas" -> {
                if (!existeId(conn, "itens", v.get("item_resultado_id"))) throw new ErroValidacao("Item produzido não encontrado.");
            }
            case "receita_insumos", "loja_estoque" -> {
                if (!existeId(conn, "itens", v.get("item_id"))) throw new ErroValidacao("Item não encontrado.");
            }
            case "lojas" -> {
                if (!existeCidade(conn, v.get("cidade_id"))) throw new ErroValidacao("Cidade não encontrada.");
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------
    // Exclusão
    // ------------------------------------------------------------------

    /** Exclui o registro; devolve o nome removido (cidades) para limpar o fog-of-war. */
    public String excluir(Entidade e, String id) {
        if (id == null || id.isBlank()) throw new ErroValidacao("Registro não informado.");
        try (Connection conn = BancoDados.getConnection()) {
            if (!existe(conn, e, id)) throw new ErroValidacao("Registro não encontrado. Ele pode já ter sido excluído.");
            String nome = null;
            conn.setAutoCommit(false);
            try {
                switch (e.id) {
                    case "itens" -> {
                        List<String> usos = new ArrayList<>();
                        long r = lerLong(conn, "SELECT COUNT(*) FROM receitas WHERE item_resultado_id = ?", id)
                                + lerLong(conn, "SELECT COUNT(*) FROM receita_insumos WHERE item_id = ?", id);
                        long inv = lerLong(conn, "SELECT COUNT(*) FROM ficha_inventario WHERE item_id = ?", id);
                        long est = lerLong(conn, "SELECT COUNT(*) FROM loja_estoque WHERE item_id = ?", id);
                        if (r > 0) usos.add(r + " receita(s)");
                        if (inv > 0) usos.add("inventário de " + inv + " ficha(s)");
                        if (est > 0) usos.add("estoque de " + est + " loja(s)");
                        if (!usos.isEmpty()) throw new ErroValidacao("Item em uso: " + String.join(", ", usos) + ". Remova esses usos antes.");
                    }
                    case "receitas" -> executar(conn, "DELETE FROM receita_insumos WHERE receita_id = ?", id);
                    case "monstros" -> {
                        long domados = lerLong(conn, "SELECT COUNT(*) FROM ficha_monstros_domados WHERE monstro_catalogo_id = ?", id);
                        if (domados > 0) throw new ErroValidacao("Há " + domados + " criatura(s) domada(s) dessa espécie em fichas de jogadores.");
                        executar(conn, "DELETE FROM monstro_golpes WHERE monstro_catalogo_id = ?", id);
                        executar(conn, "DELETE FROM monstro_drops WHERE monstro_catalogo_id = ?", id);
                    }
                    case "cidades" -> {
                        long lojas = lerLong(conn, "SELECT COUNT(*) FROM lojas WHERE cidade_id = ?", id);
                        if (lojas > 0) throw new ErroValidacao("A cidade tem " + lojas + " loja(s). Exclua ou mova as lojas antes.");
                        nome = lerTexto(conn, "SELECT nome FROM cidades WHERE id = ?", id);
                        executar(conn, "DELETE FROM conexoes WHERE origem_id = ? OR destino_id = ?", id, id);
                        executar(conn, "UPDATE fichas SET cidade_id = NULL WHERE cidade_id = ?", id);
                    }
                    case "lojas" -> {
                        executar(conn, "DELETE FROM loja_estoque WHERE loja_id = ?", id);
                        executar(conn, "DELETE FROM livro_caixa_movimentacoes WHERE loja_id = ?", id);
                    }
                    default -> { }
                }
                executar(conn, "DELETE FROM " + e.tabela + " WHERE " + e.chave + " = ?", id);
                conn.commit();
            } catch (RuntimeException | SQLException ex) {
                conn.rollback();
                throw ex;
            }
            return nome;
        } catch (SQLException ex) {
            throw new RuntimeException("Erro ao excluir " + e.rotulo + ": " + ex.getMessage(), ex);
        }
    }

    // ------------------------------------------------------------------
    // Auxiliares SQL
    // ------------------------------------------------------------------

    private boolean existe(Entidade e, String id) {
        try (Connection conn = BancoDados.getConnection()) {
            return existe(conn, e, id);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private boolean existe(Connection conn, Entidade e, String id) throws SQLException {
        return lerLongOuNull(conn, "SELECT 1 FROM " + e.tabela + " WHERE " + e.chave + " = ?", id) != null;
    }

    private boolean existeCidade(Connection conn, Object id) throws SQLException {
        return existeId(conn, "cidades", id);
    }

    private boolean existeId(Connection conn, String tabela, Object id) throws SQLException {
        return id != null && lerLongOuNull(conn, "SELECT 1 FROM " + tabela + " WHERE id = ?", id) != null;
    }

    private static void preencher(PreparedStatement ps, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
    }

    private static void executar(Connection conn, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            preencher(ps, args);
            ps.executeUpdate();
        }
    }

    private static long lerLong(Connection conn, String sql, Object... args) throws SQLException {
        Long v = lerLongOuNull(conn, sql, args);
        return v == null ? 0 : v;
    }

    private static Long lerLongOuNull(Connection conn, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            preencher(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private static String lerTexto(Connection conn, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            preencher(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    // ------------------------------------------------------------------
    // JSON
    // ------------------------------------------------------------------

    public static String json(Object v) {
        if (v == null) return "null";
        if (v instanceof Boolean || v instanceof Integer || v instanceof Long) return v.toString();
        if (v instanceof Double d) return (Double.isNaN(d) || Double.isInfinite(d)) ? "null" : String.valueOf(d);
        if (v instanceof Number n) return n.toString();
        if (v instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder("{");
            for (Map.Entry<?, ?> en : m.entrySet()) {
                if (sb.length() > 1) sb.append(',');
                sb.append(json(String.valueOf(en.getKey()))).append(':').append(json(en.getValue()));
            }
            return sb.append('}').toString();
        }
        if (v instanceof Collection<?> c) {
            StringBuilder sb = new StringBuilder("[");
            for (Object o : c) {
                if (sb.length() > 1) sb.append(',');
                sb.append(json(o));
            }
            return sb.append(']').toString();
        }
        String s = v.toString();
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
                }
            }
        }
        return sb.append('"').toString();
    }

    /** Esquema completo para o front-end montar listas e formulários. */
    public String esquemaJson() {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Entidade e : entidades.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id);
            m.put("rotulo", e.rotulo);
            m.put("descricao", e.descricao);
            m.put("chaveTexto", e.chaveTexto);
            m.put("colunaTitulo", e.colunaTitulo);
            m.put("pai", e.pai);
            m.put("colunaPai", e.colunaPai);
            m.put("afetaMundo", e.afetaMundo);
            List<Map<String, Object>> campos = new ArrayList<>();
            for (Campo c : e.campos) {
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("coluna", c.coluna);
                cm.put("rotulo", c.rotulo);
                cm.put("tipo", c.tipo.name());
                cm.put("obrigatorio", c.obrigatorio);
                cm.put("min", c.min);
                cm.put("max", c.max);
                cm.put("opcoes", c.opcoes);
                cm.put("referencia", c.referencia);
                cm.put("ajuda", c.ajuda);
                cm.put("padrao", c.padrao);
                cm.put("naLista", c.naLista);
                cm.put("grupo", c.grupo);
                campos.add(cm);
            }
            m.put("campos", campos);
            lista.add(m);
        }
        return json(lista);
    }

}
