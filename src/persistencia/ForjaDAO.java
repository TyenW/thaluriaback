package persistencia;

import economia.ForjaArmas;
import economia.ForjaArmas.*;
import economia.FichaArmaBranca;
import economia.FichaArmaLongoAlcance;
import economia.Item;
import economia.Lojas;

import java.sql.*;
import java.util.*;

/**
 * Persistência da forja de armas: moldes (famílias), partes e opções, tipos de arma,
 * receitas prontas e a compilação delas para o motor do comércio.
 *
 * Compilar uma receita de arma gera (sem duplicar, pela chave_forja):
 * <ul>
 *   <li>um item Componente de Arma e uma receita de subcraft "só por encomenda" para cada parte;</li>
 *   <li>o item da arma (com os números de combate em item_arma);</li>
 *   <li>uma receita comum em {@code receitas}, com todos os materiais como insumos e uma etapa por parte.</li>
 * </ul>
 * Assim a IA dos mercadores, o livro-caixa e a origem "craftado" funcionam sem saber da forja.
 */
public class ForjaDAO {

    private static final String CHAVE_SEMENTE = "forja_moldes_v1";

    /** Dados que a forja precisa para calcular: moldes, tipos e propriedades dos materiais. */
    public record Catalogo(Map<Integer, Molde> moldes, Map<Integer, TipoArma> tipos, Map<Integer, Material> materiais) {}

    public record ReceitaArma(int id, String nome, int tipoArmaId, boolean somenteEncomenda, Integer receitaId, Integer itemId,
                              String descricao, List<Escolha> materiais) {}

    // ------------------------------------------------------------------
    // Leitura
    // ------------------------------------------------------------------

    public Catalogo carregarCatalogo() {
        try (Connection conn = BancoDados.getConnection()) {
            return carregarCatalogo(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao carregar a forja: " + e.getMessage(), e);
        }
    }

    private Catalogo carregarCatalogo(Connection conn) throws SQLException {
        Map<Integer, List<Opcao>> opcoes = new HashMap<>();
        Map<Integer, List<Parte>> partes = new HashMap<>();
        Map<Integer, Molde> moldes = new LinkedHashMap<>();
        Map<Integer, TipoArma> tipos = new LinkedHashMap<>();
        Map<Integer, Material> materiais = new HashMap<>();
        try (Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT * FROM molde_parte_opcoes ORDER BY id")) {
                while (rs.next()) {
                    int item = rs.getInt("item_id");
                    Integer itemId = rs.wasNull() ? null : item;
                    opcoes.computeIfAbsent(rs.getInt("parte_id"), k -> new ArrayList<>()).add(new Opcao(rs.getInt("id"),
                            CategoriaMaterial.de(rs.getString("categoria_material")), itemId,
                            rs.getInt("bonus_dano"), rs.getInt("bonus_durabilidade"), rs.getInt("bonus_alcance_m")));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT * FROM molde_partes ORDER BY molde_id, ordem, id")) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    partes.computeIfAbsent(rs.getInt("molde_id"), k -> new ArrayList<>()).add(new Parte(id, rs.getString("nome"),
                            rs.getInt("qtd_materiais"), rs.getDouble("tempo_horas"), opcoes.getOrDefault(id, List.of())));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT * FROM moldes_arma ORDER BY nome COLLATE NOCASE")) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    moldes.put(id, new Molde(id, rs.getString("nome"), tipoLoja(rs.getString("tipo_loja")), rs.getInt("slots_efeito"),
                            rs.getDouble("tempo_montagem_horas"), partes.getOrDefault(id, List.of())));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT * FROM tipos_arma ORDER BY nome COLLATE NOCASE")) {
                while (rs.next()) {
                    Item.TipoItem tipoItem = "ARMA_LONGO_ALCANCE".equals(rs.getString("tipo_item")) ? Item.TipoItem.ARMA_LONGO_ALCANCE : Item.TipoItem.ARMA_BRANCA;
                    tipos.put(rs.getInt("id"), new TipoArma(rs.getInt("id"), rs.getString("nome"), rs.getInt("molde_id"), tipoItem,
                            rs.getInt("qtd_dados"), rs.getInt("faces_dado"), rs.getInt("alcance_m")));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT id, nome, categoria_material, bonus_dano, durabilidade, peso_kg, preco_latao, raridade FROM itens")) {
                while (rs.next()) {
                    Item.RaridadeItem raridade;
                    try {
                        raridade = Item.RaridadeItem.valueOf(rs.getString("raridade"));
                    } catch (RuntimeException e) {
                        raridade = Item.RaridadeItem.COMUM;
                    }
                    materiais.put(rs.getInt("id"), new Material(rs.getInt("id"), rs.getString("nome"), CategoriaMaterial.de(rs.getString("categoria_material")),
                            rs.getDouble("bonus_dano"), rs.getDouble("durabilidade"), rs.getDouble("peso_kg"), rs.getLong("preco_latao"), raridade));
                }
            }
        }
        return new Catalogo(moldes, tipos, materiais);
    }

    public List<ReceitaArma> listarReceitas() {
        try (Connection conn = BancoDados.getConnection()) {
            return listarReceitas(conn, null);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar receitas de arma: " + e.getMessage(), e);
        }
    }

    private List<ReceitaArma> listarReceitas(Connection conn, Integer soId) throws SQLException {
        Map<Integer, List<Escolha>> materiais = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT receita_arma_id, parte_id, item_id, quantidade FROM receita_arma_materiais ORDER BY id")) {
            while (rs.next()) {
                materiais.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>()).add(new Escolha(rs.getInt(2), rs.getInt(3), rs.getInt(4)));
            }
        }
        List<ReceitaArma> lista = new ArrayList<>();
        String sql = "SELECT * FROM receitas_arma" + (soId != null ? " WHERE id = " + soId : "") + " ORDER BY nome COLLATE NOCASE";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                int receita = rs.getInt("receita_id");
                Integer receitaId = rs.wasNull() ? null : receita;
                int item = rs.getInt("item_id");
                Integer itemId = rs.wasNull() ? null : item;
                lista.add(new ReceitaArma(id, rs.getString("nome"), rs.getInt("tipo_arma_id"), rs.getInt("somente_encomenda") == 1,
                        receitaId, itemId, rs.getString("descricao"), materiais.getOrDefault(id, List.of())));
            }
        }
        return lista;
    }

    /** Calcula a arma sem gravar nada (prévia do editor e da encomenda). */
    public Arma previa(String nome, int tipoArmaId, List<Escolha> escolhas) {
        Catalogo c = carregarCatalogo();
        TipoArma tipo = c.tipos().get(tipoArmaId);
        if (tipo == null) throw new AdminCrud.ErroValidacao("Tipo de arma não encontrado.");
        Molde molde = c.moldes().get(tipo.moldeId());
        if (molde == null) throw new AdminCrud.ErroValidacao("O molde deste tipo de arma foi apagado.");
        return ForjaArmas.calcular(nome, tipo, molde, escolhas, c.materiais());
    }

    // ------------------------------------------------------------------
    // Moldes e tipos
    // ------------------------------------------------------------------

    /**
     * Grava um molde e as partes dele. {@code partes} vem do editor numa linha por parte:
     * {@code id \t nome \t qtd \t horas \t opções}, com opções separadas por {@code |} no formato
     * {@code id,categoria,itemId,bonusDano,bonusDurabilidade,bonusAlcance} (categoria OU item).
     * Partes que somem do editor são apagadas, junto com os materiais que receitas punham nelas.
     */
    public int salvarMolde(Integer id, String nome, String tipoLoja, int slots, double montagemHoras, String descricao, String partes) {
        if (nome == null || nome.isBlank()) throw new AdminCrud.ErroValidacao("Dê um nome ao molde (ex.: Espada).");
        if (slots < 0 || slots > 20) throw new AdminCrud.ErroValidacao("Os slots de efeito vão de 0 a 20.");
        if (montagemHoras < 0) throw new AdminCrud.ErroValidacao("O tempo de montagem não pode ser negativo.");
        if (tipoLoja(tipoLoja) == null) throw new AdminCrud.ErroValidacao("Especialidade de loja inválida.");
        List<String[]> linhas = new ArrayList<>();
        for (String l : (partes == null ? "" : partes).split("\n")) {
            if (l.isBlank()) continue;
            String[] c = l.split("\t", -1);
            if (c.length < 5) throw new AdminCrud.ErroValidacao("Parte mal formada no editor.");
            if (c[1].isBlank()) throw new AdminCrud.ErroValidacao("Toda parte precisa de nome.");
            linhas.add(c);
        }
        if (linhas.isEmpty()) throw new AdminCrud.ErroValidacao("O molde precisa de ao menos uma parte.");

        try (Connection conn = BancoDados.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int moldeId;
                if (id != null && id > 0) {
                    executar(conn, "UPDATE moldes_arma SET nome = ?, tipo_loja = ?, slots_efeito = ?, tempo_montagem_horas = ?, descricao = ? WHERE id = ?",
                            nome.trim(), tipoLoja, slots, montagemHoras, vazioNulo(descricao), id);
                    moldeId = id;
                } else {
                    moldeId = inserir(conn, "INSERT INTO moldes_arma (nome, tipo_loja, slots_efeito, tempo_montagem_horas, descricao) VALUES (?, ?, ?, ?, ?)",
                            nome.trim(), tipoLoja, slots, montagemHoras, vazioNulo(descricao));
                }

                Set<Integer> mantidas = new HashSet<>();
                int ordem = 0;
                for (String[] c : linhas) {
                    int qtd = inteiro(c[2], "Quantidade de materiais da parte " + c[1]);
                    double horas = real(c[3], "Tempo da parte " + c[1]);
                    if (qtd < 1 || qtd > 20) throw new AdminCrud.ErroValidacao(c[1] + ": a quantidade de materiais vai de 1 a 20.");
                    if (horas < 0) throw new AdminCrud.ErroValidacao(c[1] + ": o tempo não pode ser negativo.");
                    int parteId = c[0].isBlank() ? 0 : inteiro(c[0], "Parte");
                    if (parteId > 0 && lerLong(conn, "SELECT COUNT(*) FROM molde_partes WHERE id = ? AND molde_id = ?", parteId, moldeId) > 0) {
                        executar(conn, "UPDATE molde_partes SET nome = ?, ordem = ?, qtd_materiais = ?, tempo_horas = ? WHERE id = ?", c[1].trim(), ordem, qtd, horas, parteId);
                    } else {
                        parteId = inserir(conn, "INSERT INTO molde_partes (molde_id, nome, ordem, qtd_materiais, tempo_horas) VALUES (?, ?, ?, ?, ?)",
                                moldeId, c[1].trim(), ordem, qtd, horas);
                    }
                    mantidas.add(parteId);
                    ordem++;

                    executar(conn, "DELETE FROM molde_parte_opcoes WHERE parte_id = ?", parteId);
                    int nOpcoes = 0;
                    for (String o : c[4].split("\\|")) {
                        if (o.isBlank()) continue;
                        String[] p = o.split(",", -1);
                        if (p.length < 6) throw new AdminCrud.ErroValidacao("Opção mal formada na parte " + c[1] + ".");
                        CategoriaMaterial cat = CategoriaMaterial.de(p[1]);
                        Integer itemId = p[2].isBlank() ? null : inteiro(p[2], "Item da opção");
                        if ((cat == null) == (itemId == null)) throw new AdminCrud.ErroValidacao(c[1] + ": cada opção é uma categoria de material ou um item, não os dois.");
                        if (itemId != null && lerLong(conn, "SELECT COUNT(*) FROM itens WHERE id = ?", itemId) == 0) throw new AdminCrud.ErroValidacao(c[1] + ": item da opção não existe.");
                        executar(conn, "INSERT INTO molde_parte_opcoes (parte_id, categoria_material, item_id, bonus_dano, bonus_durabilidade, bonus_alcance_m) VALUES (?, ?, ?, ?, ?, ?)",
                                parteId, cat != null ? cat.name() : null, itemId, inteiro(p[3], "Bônus de dano"), inteiro(p[4], "Bônus de durabilidade"), inteiro(p[5], "Bônus de alcance"));
                        nOpcoes++;
                    }
                    if (nOpcoes == 0) throw new AdminCrud.ErroValidacao(c[1] + ": diga o que esta parte aceita (ao menos uma opção).");
                }

                List<Integer> antigas = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM molde_partes WHERE molde_id = ?")) {
                    ps.setInt(1, moldeId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) if (!mantidas.contains(rs.getInt(1))) antigas.add(rs.getInt(1));
                    }
                }
                for (int p : antigas) {
                    executar(conn, "DELETE FROM molde_parte_opcoes WHERE parte_id = ?", p);
                    executar(conn, "DELETE FROM receita_arma_materiais WHERE parte_id = ?", p);
                    executar(conn, "DELETE FROM molde_partes WHERE id = ?", p);
                }
                recompilarDoMolde(conn, moldeId);
                conn.commit();
                return moldeId;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            if (String.valueOf(e.getMessage()).contains("UNIQUE")) throw new AdminCrud.ErroValidacao("Já existe um molde com esse nome.");
            throw new RuntimeException("Erro ao salvar molde: " + e.getMessage(), e);
        }
    }

    public void excluirMolde(int id) {
        try (Connection conn = BancoDados.getConnection()) {
            long tipos = lerLong(conn, "SELECT COUNT(*) FROM tipos_arma WHERE molde_id = ?", id);
            if (tipos > 0) throw new AdminCrud.ErroValidacao("Há " + tipos + " tipo(s) de arma usando este molde. Mude-os de molde ou exclua-os antes.");
            executar(conn, "DELETE FROM molde_parte_opcoes WHERE parte_id IN (SELECT id FROM molde_partes WHERE molde_id = ?)", id);
            executar(conn, "DELETE FROM molde_partes WHERE molde_id = ?", id);
            executar(conn, "DELETE FROM moldes_arma WHERE id = ?", id);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao excluir molde: " + e.getMessage(), e);
        }
    }

    public int salvarTipo(Integer id, String nome, int moldeId, String tipoItem, int qtdDados, int facesDado, int alcanceM, String descricao) {
        if (nome == null || nome.isBlank()) throw new AdminCrud.ErroValidacao("Dê um nome ao tipo de arma (ex.: Katana).");
        if (qtdDados < 1 || qtdDados > 20 || facesDado < 2 || facesDado > 100) throw new AdminCrud.ErroValidacao("Dado inválido: de 1 a 20 dados, de 2 a 100 faces.");
        if (alcanceM < 0) throw new AdminCrud.ErroValidacao("O alcance não pode ser negativo.");
        String ti = "ARMA_LONGO_ALCANCE".equals(tipoItem) ? "ARMA_LONGO_ALCANCE" : "ARMA_BRANCA";
        try (Connection conn = BancoDados.getConnection()) {
            if (lerLong(conn, "SELECT COUNT(*) FROM moldes_arma WHERE id = ?", moldeId) == 0) throw new AdminCrud.ErroValidacao("Molde não encontrado.");
            conn.setAutoCommit(false);
            try {
                int tipoId;
                if (id != null && id > 0) {
                    executar(conn, "UPDATE tipos_arma SET nome = ?, molde_id = ?, tipo_item = ?, qtd_dados = ?, faces_dado = ?, alcance_m = ?, descricao = ? WHERE id = ?",
                            nome.trim(), moldeId, ti, qtdDados, facesDado, alcanceM, vazioNulo(descricao), id);
                    tipoId = id;
                } else {
                    tipoId = inserir(conn, "INSERT INTO tipos_arma (nome, molde_id, tipo_item, qtd_dados, faces_dado, alcance_m, descricao) VALUES (?, ?, ?, ?, ?, ?, ?)",
                            nome.trim(), moldeId, ti, qtdDados, facesDado, alcanceM, vazioNulo(descricao));
                }
                Catalogo c = carregarCatalogo(conn);
                for (ReceitaArma r : listarReceitas(conn, null)) {
                    if (r.tipoArmaId() == tipoId) compilarSeValida(conn, c, r);
                }
                conn.commit();
                return tipoId;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            if (String.valueOf(e.getMessage()).contains("UNIQUE")) throw new AdminCrud.ErroValidacao("Já existe um tipo de arma com esse nome.");
            throw new RuntimeException("Erro ao salvar tipo de arma: " + e.getMessage(), e);
        }
    }

    public void excluirTipo(int id) {
        try (Connection conn = BancoDados.getConnection()) {
            long receitas = lerLong(conn, "SELECT COUNT(*) FROM receitas_arma WHERE tipo_arma_id = ?", id);
            if (receitas > 0) throw new AdminCrud.ErroValidacao("Há " + receitas + " receita(s) de arma deste tipo. Exclua-as antes.");
            executar(conn, "DELETE FROM tipos_arma WHERE id = ?", id);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao excluir tipo de arma: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Receitas de arma
    // ------------------------------------------------------------------

    /** Grava a receita pronta e a compila para o motor. Recusa composições inválidas. */
    public ReceitaArma salvarReceita(Integer id, String nome, int tipoArmaId, List<Escolha> escolhas, boolean somenteEncomenda, String descricao) {
        if (nome == null || nome.isBlank()) throw new AdminCrud.ErroValidacao("Dê um nome à arma (ex.: Katana de Aço Negro).");
        try (Connection conn = BancoDados.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Catalogo c = carregarCatalogo(conn);
                validar(c, nome, tipoArmaId, escolhas);
                int rid;
                if (id != null && id > 0) {
                    if (lerLong(conn, "SELECT COUNT(*) FROM receitas_arma WHERE id = ?", id) == 0) throw new AdminCrud.ErroValidacao("Receita não encontrada.");
                    executar(conn, "UPDATE receitas_arma SET nome = ?, tipo_arma_id = ?, somente_encomenda = ?, descricao = ? WHERE id = ?",
                            nome.trim(), tipoArmaId, somenteEncomenda ? 1 : 0, vazioNulo(descricao), id);
                    rid = id;
                } else {
                    rid = inserir(conn, "INSERT INTO receitas_arma (nome, tipo_arma_id, somente_encomenda, descricao) VALUES (?, ?, ?, ?)",
                            nome.trim(), tipoArmaId, somenteEncomenda ? 1 : 0, vazioNulo(descricao));
                }
                gravarMateriais(conn, rid, escolhas);
                ReceitaArma r = listarReceitas(conn, rid).get(0);
                compilar(conn, c, r, "RA:" + rid);
                conn.commit();
                return listarReceitas(conn, rid).get(0);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar receita de arma: " + e.getMessage(), e);
        }
    }

    /**
     * Encomenda personalizada: o Mestre escolhe o material de cada parte na hora. Gera (ou
     * reaproveita, se a mesma composição já foi pedida) uma receita "só por encomenda".
     *
     * @return id da receita comum (em {@code receitas}) pronta para abrir a ordem
     */
    public int compilarEncomenda(int tipoArmaId, List<Escolha> escolhas) {
        try (Connection conn = BancoDados.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Catalogo c = carregarCatalogo(conn);
                TipoArma tipo = c.tipos().get(tipoArmaId);
                if (tipo == null) throw new AdminCrud.ErroValidacao("Tipo de arma não encontrado.");
                Arma previa = ForjaArmas.calcular(tipo.nome(), tipo, c.moldes().get(tipo.moldeId()), escolhas, c.materiais());
                if (!previa.valida()) throw new AdminCrud.ErroValidacao(String.join(" ", previa.problemas()));
                StringJoiner chave = new StringJoiner("|", "EP:" + tipoArmaId + ":", "");
                for (Componente comp : previa.componentes()) chave.add(comp.chave());
                String nome = tipo.nome() + " de " + nomeMaterialPrincipal(previa);
                ReceitaArma r = new ReceitaArma(0, nome, tipoArmaId, true, null, null, null, escolhas);
                int receitaId = compilar(conn, c, r, chave.toString());
                conn.commit();
                return receitaId;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao compilar encomenda: " + e.getMessage(), e);
        }
    }

    public void excluirReceita(int id) {
        try (Connection conn = BancoDados.getConnection()) {
            Long receitaId = lerLongOuNulo(conn, "SELECT receita_id FROM receitas_arma WHERE id = ?", id);
            if (receitaId != null) {
                long ordens = lerLong(conn, "SELECT COUNT(*) FROM loja_ordens_producao WHERE receita_id = ?", receitaId);
                if (ordens > 0) throw new AdminCrud.ErroValidacao("Há " + ordens + " ordem(ns) desta arma em produção. Cancele-as ou espere terminarem.");
            }
            conn.setAutoCommit(false);
            try {
                if (receitaId != null) apagarReceitaComum(conn, receitaId);
                executar(conn, "DELETE FROM receita_arma_materiais WHERE receita_arma_id = ?", id);
                executar(conn, "DELETE FROM receitas_arma WHERE id = ?", id);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao excluir receita de arma: " + e.getMessage(), e);
        }
    }

    /** Recalcula todas as armas (ex.: o Mestre mudou o bônus de um material). Receitas que ficaram inválidas mantêm o último cálculo. */
    public void recompilarTudo() {
        try (Connection conn = BancoDados.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Catalogo c = carregarCatalogo(conn);
                for (ReceitaArma r : listarReceitas(conn, null)) compilarSeValida(conn, c, r);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao recalcular as armas: " + e.getMessage(), e);
        }
    }

    private void recompilarDoMolde(Connection conn, int moldeId) throws SQLException {
        Catalogo c = carregarCatalogo(conn);
        for (ReceitaArma r : listarReceitas(conn, null)) {
            TipoArma t = c.tipos().get(r.tipoArmaId());
            if (t != null && t.moldeId() == moldeId) compilarSeValida(conn, c, r);
        }
    }

    private void compilarSeValida(Connection conn, Catalogo c, ReceitaArma r) throws SQLException {
        TipoArma t = c.tipos().get(r.tipoArmaId());
        Molde m = t != null ? c.moldes().get(t.moldeId()) : null;
        if (m == null || !ForjaArmas.calcular(r.nome(), t, m, r.materiais(), c.materiais()).valida()) return;
        compilar(conn, c, r, "RA:" + r.id());
    }

    private void validar(Catalogo c, String nome, int tipoArmaId, List<Escolha> escolhas) {
        TipoArma tipo = c.tipos().get(tipoArmaId);
        if (tipo == null) throw new AdminCrud.ErroValidacao("Tipo de arma não encontrado.");
        Molde molde = c.moldes().get(tipo.moldeId());
        if (molde == null) throw new AdminCrud.ErroValidacao("O molde deste tipo de arma foi apagado.");
        Arma a = ForjaArmas.calcular(nome, tipo, molde, escolhas, c.materiais());
        if (!a.valida()) throw new AdminCrud.ErroValidacao(String.join(" ", a.problemas()));
    }

    private void gravarMateriais(Connection conn, int receitaArmaId, List<Escolha> escolhas) throws SQLException {
        executar(conn, "DELETE FROM receita_arma_materiais WHERE receita_arma_id = ?", receitaArmaId);
        for (Escolha e : escolhas) {
            if (e.quantidade() <= 0) continue;
            executar(conn, "INSERT INTO receita_arma_materiais (receita_arma_id, parte_id, item_id, quantidade) VALUES (?, ?, ?, ?)",
                    receitaArmaId, e.parteId(), e.itemId(), e.quantidade());
        }
    }

    /** Gera componentes, item da arma e receita comum. Devolve o id da receita comum. */
    private int compilar(Connection conn, Catalogo c, ReceitaArma r, String chave) throws SQLException {
        TipoArma tipo = c.tipos().get(r.tipoArmaId());
        Molde molde = c.moldes().get(tipo.moldeId());
        Arma arma = ForjaArmas.calcular(r.nome(), tipo, molde, r.materiais(), c.materiais());
        String tipoLoja = molde.tipoLoja().name();

        record EtapaGerada(String parte, int itemId, int receitaId, double horas) {}
        List<EtapaGerada> etapas = new ArrayList<>();
        Map<Integer, Integer> todosMateriais = new LinkedHashMap<>();
        StringJoiner composicao = new StringJoiner(" · ");
        for (Componente comp : arma.componentes()) {
            String desc = "Peça de forja (" + comp.parte().nome() + "): " + listarMateriais(comp.materiais(), c.materiais())
                    + ". Dano +" + fmt(comp.bonusDano()) + ", durabilidade " + fmt(comp.durabilidade()) + ".";
            int itemId = upsertItem(conn, comp.chave(), comp.nome(), desc, Item.TipoItem.COMPONENTE_ARMA.name(), comp.raridade().name(),
                    comp.precoLatao(), comp.pesoKg(), comp.bonusDano(), comp.durabilidade());
            int receitaComp = upsertReceita(conn, "RC:" + comp.chave(), "Forjar " + comp.nome(), itemId, comp.parte().tempoHoras(), tipoLoja, true,
                    comp.materiais(), List.of());
            etapas.add(new EtapaGerada(comp.parte().nome(), itemId, receitaComp, comp.parte().tempoHoras()));
            comp.materiais().forEach((id, q) -> todosMateriais.merge(id, q, Integer::sum));
            composicao.add(comp.nome());
        }

        String descArma = "Dano " + arma.dano() + " · durabilidade " + arma.durabilidade()
                + (arma.alcanceM() > 0 ? " · alcance " + arma.alcanceM() + " m" : "") + " · " + arma.slotsEfeito() + " slot(s) de efeito. "
                + composicao + (r.descricao() != null ? "\n" + r.descricao() : "");
        int armaId = upsertItem(conn, "A:" + chave, r.nome(), descArma, tipo.tipoItem().name(), arma.raridade().name(),
                arma.precoLatao(), arma.pesoKg(), arma.bonusDano(), arma.durabilidade());
        executar(conn, "INSERT OR REPLACE INTO item_arma (item_id, tipo_arma_id, dano, bonus_dano, durabilidade, alcance_m, slots_efeito, composicao) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                armaId, tipo.id(), arma.dano(), arma.bonusDano(), arma.durabilidade(), arma.alcanceM(), arma.slotsEfeito(), composicao.toString());

        List<Object[]> etapasLinhas = new ArrayList<>();
        for (EtapaGerada e : etapas) etapasLinhas.add(new Object[] { e.parte(), e.itemId(), e.receitaId(), e.horas() });
        int receitaId = upsertReceita(conn, "R:" + chave, r.nome(), armaId, arma.tempoHoras(), tipoLoja, r.somenteEncomenda(), todosMateriais, etapasLinhas);
        if (r.id() > 0) executar(conn, "UPDATE receitas_arma SET receita_id = ?, item_id = ? WHERE id = ?", receitaId, armaId, r.id());
        return receitaId;
    }

    private int upsertItem(Connection conn, String chave, String nome, String descricao, String tipo, String raridade,
                           long preco, double peso, double bonusDano, double durabilidade) throws SQLException {
        Long existente = lerLongOuNulo(conn, "SELECT id FROM itens WHERE chave_forja = ?", chave);
        if (existente != null) {
            executar(conn, "UPDATE itens SET nome = ?, descricao = ?, tipo = ?, raridade = ?, preco_latao = ?, preco_base_latao = ?, peso_kg = ?, peso = ?, "
                    + "bonus_dano = ?, durabilidade = ? WHERE id = ?", nome, descricao, tipo, raridade, preco, preco, peso, peso, bonusDano, durabilidade, existente);
            return existente.intValue();
        }
        int id = (int) lerLong(conn, "SELECT COALESCE(MAX(id), 0) + 1 FROM itens");
        executar(conn, "INSERT INTO itens (id, nome, descricao, preco_latao, preco_base_latao, peso_kg, peso, tipo, raridade, tier, bonus_dano, durabilidade, chave_forja) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)", id, nome, descricao, preco, preco, peso, peso, tipo, raridade, bonusDano, durabilidade, chave);
        return id;
    }

    private int upsertReceita(Connection conn, String chave, String nome, int itemId, double horas, String tipoLoja, boolean somenteEncomenda,
                              Map<Integer, Integer> insumos, List<Object[]> etapas) throws SQLException {
        Long existente = lerLongOuNulo(conn, "SELECT id FROM receitas WHERE chave_forja = ?", chave);
        int id;
        if (existente != null) {
            id = existente.intValue();
            executar(conn, "UPDATE receitas SET nome = ?, item_resultado_id = ?, quantidade_resultado = 1, tempo_horas = ?, tipo_loja = ?, somente_encomenda = ? WHERE id = ?",
                    nome, itemId, horas, tipoLoja, somenteEncomenda ? 1 : 0, id);
        } else {
            id = inserir(conn, "INSERT INTO receitas (nome, item_resultado_id, quantidade_resultado, pericia, grau_minimo, tempo_horas, descricao, tipo_loja, somente_encomenda, chave_forja) "
                    + "VALUES (?, ?, 1, 'Ferraria', 0, ?, 'Gerada pela forja', ?, ?, ?)", nome, itemId, horas, tipoLoja, somenteEncomenda ? 1 : 0, chave);
        }
        executar(conn, "DELETE FROM receita_insumos WHERE receita_id = ?", id);
        for (Map.Entry<Integer, Integer> e : insumos.entrySet()) {
            executar(conn, "INSERT INTO receita_insumos (receita_id, item_id, quantidade) VALUES (?, ?, ?)", id, e.getKey(), e.getValue());
        }
        executar(conn, "DELETE FROM receita_etapas WHERE receita_id = ?", id);
        int ordem = 0;
        for (Object[] e : etapas) {
            executar(conn, "INSERT INTO receita_etapas (receita_id, ordem, nome_parte, componente_item_id, componente_receita_id, tempo_horas) VALUES (?, ?, ?, ?, ?, ?)",
                    id, ordem++, e[0], e[1], e[2], e[3]);
        }
        return id;
    }

    private void apagarReceitaComum(Connection conn, long receitaId) throws SQLException {
        executar(conn, "DELETE FROM receita_etapas WHERE receita_id = ?", receitaId);
        executar(conn, "DELETE FROM receita_insumos WHERE receita_id = ?", receitaId);
        executar(conn, "DELETE FROM receitas WHERE id = ?", receitaId);
    }

    /** Material da primeira parte (a lâmina, a cabeça, o corpo...), para batizar encomendas. */
    private static String nomeMaterialPrincipal(Arma a) {
        if (a.componentes().isEmpty()) return "forja";
        String n = a.componentes().get(0).nome();
        int de = n.indexOf(" de ");
        return de >= 0 ? n.substring(de + 4) : n;
    }

    private static String listarMateriais(Map<Integer, Integer> usados, Map<Integer, Material> materiais) {
        StringJoiner j = new StringJoiner(", ");
        usados.forEach((id, q) -> j.add(q + "× " + (materiais.containsKey(id) ? materiais.get(id).nome() : "#" + id)));
        return j.toString();
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d);
    }

    // ------------------------------------------------------------------
    // Sementes: moldes, tipos de arma e categoria dos materiais existentes
    // ------------------------------------------------------------------

    private record ParteSemente(String nome, int qtd, double horas, CategoriaMaterial... aceita) {}

    public static void semear() {
        if (BancoDados.sementeAplicada(CHAVE_SEMENTE)) return;
        Map<String, List<ParteSemente>> moldes = new LinkedHashMap<>();
        moldes.put("Espada", List.of(new ParteSemente("Lâmina", 2, 16, CategoriaMaterial.METAL, CategoriaMaterial.OSSO),
                new ParteSemente("Guarda", 1, 6, CategoriaMaterial.METAL), new ParteSemente("Cabo", 1, 4, CategoriaMaterial.MADEIRA, CategoriaMaterial.COURO, CategoriaMaterial.OSSO),
                new ParteSemente("Pomo", 1, 4, CategoriaMaterial.METAL, CategoriaMaterial.GEMA)));
        moldes.put("Machado", List.of(new ParteSemente("Cabeça", 2, 16, CategoriaMaterial.METAL, CategoriaMaterial.OSSO),
                new ParteSemente("Cabo", 1, 4, CategoriaMaterial.MADEIRA, CategoriaMaterial.METAL)));
        moldes.put("Contusão", List.of(new ParteSemente("Cabeça", 2, 12, CategoriaMaterial.METAL, CategoriaMaterial.MADEIRA, CategoriaMaterial.OSSO),
                new ParteSemente("Cabo", 1, 4, CategoriaMaterial.MADEIRA, CategoriaMaterial.METAL)));
        moldes.put("Haste", List.of(new ParteSemente("Ponta", 1, 8, CategoriaMaterial.METAL, CategoriaMaterial.OSSO),
                new ParteSemente("Haste", 1, 6, CategoriaMaterial.MADEIRA, CategoriaMaterial.METAL)));
        moldes.put("Arco", List.of(new ParteSemente("Corpo", 1, 12, CategoriaMaterial.MADEIRA, CategoriaMaterial.OSSO, CategoriaMaterial.METAL),
                new ParteSemente("Corda", 1, 4, CategoriaMaterial.FIBRA, CategoriaMaterial.COURO)));
        moldes.put("Besta", List.of(new ParteSemente("Corpo", 1, 12, CategoriaMaterial.MADEIRA, CategoriaMaterial.METAL),
                new ParteSemente("Corda", 1, 4, CategoriaMaterial.FIBRA, CategoriaMaterial.COURO), new ParteSemente("Mecanismo", 1, 12, CategoriaMaterial.METAL)));

        try (Connection conn = BancoDados.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Integer> ids = new HashMap<>();
                for (Map.Entry<String, List<ParteSemente>> m : moldes.entrySet()) {
                    Long existente = lerLongOuNulo(conn, "SELECT id FROM moldes_arma WHERE nome = ?", m.getKey());
                    if (existente != null) { ids.put(m.getKey(), existente.intValue()); continue; }
                    int moldeId = inserir(conn, "INSERT INTO moldes_arma (nome, tipo_loja, slots_efeito, tempo_montagem_horas) VALUES (?, 'FORJA', 1, 8)", m.getKey());
                    ids.put(m.getKey(), moldeId);
                    int ordem = 0;
                    for (ParteSemente p : m.getValue()) {
                        int parteId = inserir(conn, "INSERT INTO molde_partes (molde_id, nome, ordem, qtd_materiais, tempo_horas) VALUES (?, ?, ?, ?, ?)",
                                moldeId, p.nome(), ordem++, p.qtd(), p.horas());
                        for (CategoriaMaterial cat : p.aceita()) {
                            executar(conn, "INSERT INTO molde_parte_opcoes (parte_id, categoria_material) VALUES (?, ?)", parteId, cat.name());
                        }
                    }
                }

                for (FichaArmaBranca.SubtipoArmaBranca s : FichaArmaBranca.SubtipoArmaBranca.values()) {
                    String molde = moldeDoSubtipo(s.name());
                    if (molde == null) continue; // chicote, garras, soqueiras: o Mestre cria o molde se quiser
                    executar(conn, "INSERT OR IGNORE INTO tipos_arma (nome, molde_id, tipo_item, qtd_dados, faces_dado, alcance_m) VALUES (?, ?, 'ARMA_BRANCA', ?, ?, 0)",
                            s.getNomeFormatado(), ids.get(molde), s.getQtdDadosPadrao(), s.getFacesDadoPadrao());
                }
                for (FichaArmaLongoAlcance.SubtipoArmaDistante s : FichaArmaLongoAlcance.SubtipoArmaDistante.values()) {
                    String molde = s.name().startsWith("ARCO") ? "Arco" : s.name().startsWith("BESTA") ? "Besta" : null;
                    if (molde == null) continue;
                    executar(conn, "INSERT OR IGNORE INTO tipos_arma (nome, molde_id, tipo_item, qtd_dados, faces_dado, alcance_m) VALUES (?, ?, 'ARMA_LONGO_ALCANCE', 1, ?, ?)",
                            s.getNomeFormatado(), ids.get(molde), molde.equals("Besta") ? 10 : 8, s.getAlcanceMetrosBase());
                }

                categorizarMateriais(conn);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao semear a forja: " + e.getMessage(), e);
        }
        BancoDados.marcarSemente(CHAVE_SEMENTE);
    }

    static String moldeDoSubtipo(String s) {
        if (s.startsWith("ESPADA") || Set.of("SABRE", "CIMITARRA", "ALFANJE", "RAPIEIRA", "FLORETE", "KATANA", "MONTANTE", "ADAGA", "KUKRI", "KATAR").contains(s)) return "Espada";
        if (s.startsWith("MACHADO")) return "Machado";
        if (Set.of("CLAVA", "MACA").contains(s) || s.startsWith("MARTELO")) return "Contusão";
        if (s.startsWith("LANCA") || Set.of("ALABARDA", "GLAIVE", "TRIDENTE", "BIDENTE").contains(s)) return "Haste";
        return null;
    }

    /** Dá categoria e números de forja aos materiais que já existiam, pelo nome (o Mestre ajusta depois). */
    private static void categorizarMateriais(Connection conn) throws SQLException {
        String[][] regras = {
                { "METAL", "ferro", "aço", "aco", "cobre", "bronze", "estanho", "prata", "ouro", "mithril", "adamant", "lingote", "minério", "minerio", "metal" },
                { "MADEIRA", "madeira", "tora", "tábua", "tabua", "carvalho", "freixo", "teixo", "bambu" },
                { "COURO", "couro", "pelagem", "pele" },
                { "FIBRA", "linha", "fibra", "seda", "corda", "teia", "cânhamo", "canhamo", "tendão", "tendao" },
                { "GEMA", "cristal", "gema", "rubi", "safira", "esmeralda", "diamante", "ametista" },
                { "OSSO", "osso", "presa", "chifre", "garra" },
                { "ESCAMA", "escama", "quitina", "carapaça", "carapaca" },
        };
        Map<String, double[]> porRaridade = Map.of("COMUM", new double[] { 1, 50 }, "INCOMUM", new double[] { 2, 70 }, "RARO", new double[] { 3, 90 },
                "EPICO", new double[] { 4, 120 }, "LENDARIO", new double[] { 6, 160 }, "MITICO", new double[] { 8, 220 });
        List<Object[]> mudar = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, nome, raridade FROM itens WHERE tipo = 'MATERIAL' AND categoria_material IS NULL")) {
            while (rs.next()) {
                // Compara pelo começo de cada palavra: "Saco de Trigo" não é aço, "Grosso" não é osso
                String[] palavras = rs.getString("nome").toLowerCase(Locale.ROOT).split("[^\\p{L}]+");
                for (String[] r : regras) {
                    boolean bate = false;
                    for (int i = 1; i < r.length && !bate; i++) {
                        for (String p : palavras) if (p.startsWith(r[i])) { bate = true; break; }
                    }
                    if (!bate) continue;
                    double[] v = porRaridade.getOrDefault(rs.getString("raridade"), porRaridade.get("COMUM"));
                    mudar.add(new Object[] { r[0], v[0], v[1], rs.getInt("id") });
                    break;
                }
            }
        }
        for (Object[] m : mudar) {
            executar(conn, "UPDATE itens SET categoria_material = ?, bonus_dano = CASE WHEN bonus_dano = 0 THEN ? ELSE bonus_dano END, "
                    + "durabilidade = CASE WHEN durabilidade = 0 THEN ? ELSE durabilidade END WHERE id = ?", m);
        }
    }

    // ------------------------------------------------------------------
    // Auxiliares JDBC
    // ------------------------------------------------------------------

    private static Lojas.TipoEstabelecimento tipoLoja(String nome) {
        try {
            return Lojas.TipoEstabelecimento.valueOf(nome == null ? "FORJA" : nome.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String vazioNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static int inteiro(String v, String campo) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new AdminCrud.ErroValidacao(campo + ": número inteiro inválido.");
        }
    }

    private static double real(String v, String campo) {
        try {
            return Double.parseDouble(v.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new AdminCrud.ErroValidacao(campo + ": número inválido.");
        }
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

    private static int inserir(Connection conn, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            preencher(ps, args);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("O banco não devolveu o id gerado.");
    }

    private static long lerLong(Connection conn, String sql, Object... args) throws SQLException {
        Long v = lerLongOuNulo(conn, sql, args);
        return v == null ? 0 : v;
    }

    private static Long lerLongOuNulo(Connection conn, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            preencher(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                long v = rs.getLong(1);
                return rs.wasNull() ? null : v;
            }
        }
    }
}
