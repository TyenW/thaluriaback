package persistencia;

import economia.*;
import geografia.Cidade;
import geografia.Conexao;

import java.sql.*;
import java.util.*;

/**
 * Persistência do comércio vivo: receitas das lojas, ordens de produção em andamento
 * e o livro-caixa de cada loja (com o saldo após cada movimento).
 */
public class ComercioDAO {

    /** Receitas cadastradas pelo Mestre (e as compiladas pela forja), com insumos e etapas. */
    public List<ReceitaLoja> listarReceitas() {
        Map<Integer, List<ReceitaLoja.Insumo>> insumos = new HashMap<>();
        Map<Integer, List<ReceitaLoja.Etapa>> etapas = new HashMap<>();
        List<ReceitaLoja> lista = new ArrayList<>();
        try (Connection conn = BancoDados.getConnection(); Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT receita_id, item_id, quantidade FROM receita_insumos")) {
                while (rs.next()) {
                    insumos.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>())
                            .add(new ReceitaLoja.Insumo(rs.getInt(2), Math.max(1, rs.getInt(3))));
                }
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT receita_id, nome_parte, componente_item_id, componente_receita_id, tempo_horas FROM receita_etapas ORDER BY receita_id, ordem")) {
                while (rs.next()) {
                    etapas.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>())
                            .add(new ReceitaLoja.Etapa(rs.getInt(3), rs.getString(2), rs.getDouble(5), insumos.getOrDefault(rs.getInt(4), List.of())));
                }
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT id, nome, item_resultado_id, quantidade_resultado, tempo_horas, tipo_loja, somente_encomenda FROM receitas ORDER BY nome")) {
                while (rs.next()) {
                    Lojas.TipoEstabelecimento tipo = null;
                    String bruto = rs.getString("tipo_loja");
                    if (bruto != null && !bruto.isBlank()) {
                        try { tipo = Lojas.TipoEstabelecimento.valueOf(bruto.trim()); } catch (IllegalArgumentException ignored) {}
                    }
                    int id = rs.getInt("id");
                    lista.add(new ReceitaLoja(id, rs.getString("nome"), rs.getInt("item_resultado_id"),
                            rs.getInt("quantidade_resultado"), rs.getDouble("tempo_horas"), tipo,
                            insumos.getOrDefault(id, List.of()), etapas.getOrDefault(id, List.of()), rs.getInt("somente_encomenda") == 1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar receitas: " + e.getMessage(), e);
        }
        return lista;
    }

    public void carregarOrdens(Connection conn, Lojas loja) throws SQLException {
        String sql = "SELECT id, receita_id, lotes, dia_inicio, dia_conclusao, origem, insumos_por_conta, etapas FROM loja_ordens_producao WHERE loja_id = ? ORDER BY id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, loja.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrdemReceita.Origem origem = "MESTRE".equals(rs.getString("origem")) ? OrdemReceita.Origem.MESTRE : OrdemReceita.Origem.IA;
                    loja.adicionarOrdemCarregada(new OrdemReceita(rs.getInt("id"), rs.getInt("receita_id"), rs.getInt("lotes"),
                            rs.getInt("dia_inicio"), rs.getInt("dia_conclusao"), origem,
                            rs.getInt("insumos_por_conta") == 1, OrdemReceita.lerEtapas(rs.getString("etapas"))));
                }
            }
        }
    }

    /** Regrava as ordens em andamento da loja (as concluídas já saíram da lista). */
    public void salvarOrdens(Connection conn, Lojas loja) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM loja_ordens_producao WHERE loja_id = ?")) {
            del.setInt(1, loja.getId());
            del.executeUpdate();
        }
        if (loja.getOrdensReceita().isEmpty()) return;
        String sql = "INSERT INTO loja_ordens_producao (loja_id, receita_id, lotes, dia_inicio, dia_conclusao, origem, insumos_por_conta, etapas) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (OrdemReceita o : loja.getOrdensReceita()) {
                ps.setInt(1, loja.getId());
                ps.setInt(2, o.getReceitaId());
                ps.setInt(3, o.getLotes());
                ps.setInt(4, o.getDiaInicio());
                ps.setInt(5, o.getDiaConclusao());
                ps.setString(6, o.getOrigem().name());
                ps.setInt(7, o.isInsumosPorConta() ? 1 : 0);
                ps.setString(8, o.getEtapas().isEmpty() ? null : o.etapasSerializadas());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Grava no livro-caixa os movimentos que a loja registrou desde o último salvamento. */
    public void gravarHistoricoPendente(Connection conn, Lojas loja) throws SQLException {
        List<RegistroMovimentacao> pendentes = loja.getHistoricoNaoPersistido();
        if (pendentes.isEmpty()) return;
        String sql = """
            INSERT INTO livro_caixa_movimentacoes
                (loja_id, dia_mundo, hora_mundo, tipo_operacao, item_nome, quantidade, valor_latao, descricao, saldo_apos_latao)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (RegistroMovimentacao r : pendentes) {
                ps.setInt(1, loja.getId());
                ps.setInt(2, r.getDiaMundo());
                ps.setInt(3, r.getHoraMundo());
                ps.setString(4, r.getTipo().name());
                ps.setString(5, r.getNomeItem());
                ps.setInt(6, r.getQuantidade());
                ps.setLong(7, r.getValorEmLatao());
                ps.setString(8, r.getObservacao());
                ps.setLong(9, r.getSaldoCaixaAposEmLatao());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Últimos movimentos do livro-caixa de uma loja, do mais recente ao mais antigo. */
    public List<Map<String, Object>> listarMovimentacoes(int lojaId, int limite) {
        List<Map<String, Object>> lista = new ArrayList<>();
        String sql = """
            SELECT id, dia_mundo, hora_mundo, tipo_operacao, item_nome, quantidade, valor_latao, descricao, saldo_apos_latao
            FROM livro_caixa_movimentacoes WHERE loja_id = ? ORDER BY id DESC LIMIT ?
            """;
        try (Connection conn = BancoDados.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lojaId);
            ps.setInt(2, Math.max(1, Math.min(500, limite)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tipo = rs.getString("tipo_operacao");
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("dia", rs.getInt("dia_mundo"));
                    m.put("hora", rs.getInt("hora_mundo"));
                    m.put("tipo", tipo);
                    m.put("tipoDescricao", descricaoTipo(tipo));
                    m.put("fluxo", fluxoTipo(tipo));
                    m.put("item", rs.getString("item_nome"));
                    m.put("quantidade", rs.getInt("quantidade"));
                    m.put("valorLatao", rs.getLong("valor_latao"));
                    m.put("descricao", rs.getString("descricao"));
                    long saldo = rs.getLong("saldo_apos_latao");
                    m.put("saldoAposLatao", rs.wasNull() ? null : saldo);
                    lista.add(m);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar livro-caixa da loja " + lojaId, e);
        }
        return lista;
    }

    /** Totais por loja a partir de um dia: entradas, saídas e unidades vendidas. */
    public Map<Integer, long[]> resumirDesde(int diaInicial) {
        Map<Integer, long[]> resumo = new HashMap<>();
        String sql = """
            SELECT loja_id, tipo_operacao, SUM(valor_latao), SUM(quantidade)
            FROM livro_caixa_movimentacoes WHERE dia_mundo >= ? GROUP BY loja_id, tipo_operacao
            """;
        try (Connection conn = BancoDados.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, diaInicial);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long[] r = resumo.computeIfAbsent(rs.getInt(1), k -> new long[3]);
                    String tipo = rs.getString(2);
                    int fluxo = fluxoTipo(tipo);
                    if (fluxo > 0) r[0] += rs.getLong(3);
                    if (fluxo < 0) r[1] += rs.getLong(3);
                    if (tipo != null && tipo.startsWith("VENDA")) r[2] += rs.getLong(4);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao resumir livro-caixa", e);
        }
        return resumo;
    }

    // ------------------------------------------------------------------
    // Caravanas em viagem
    // ------------------------------------------------------------------

    /** Regrava a fotografia das caravanas ativas (as encerradas não precisam voltar). */
    public void salvarCaravanas(Connection conn, MotorEconomico motor) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM caravanas_ativas");
        }
        String sql = """
            INSERT INTO caravanas_ativas (id, loja_origem_id, loja_destino_id, item_id, quantidade, caminho_cidades, tipos_trechos,
                                          indice_trecho, km_no_trecho, status, protecao, dia_evento, hora_evento, servico)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Caravana c : motor.getCaravanasAtivas()) {
                StringJoiner cidades = new StringJoiner(",");
                for (Cidade cidade : c.getRota().getCaminhoCidades()) cidades.add(String.valueOf(cidade.getId()));
                StringJoiner tipos = new StringJoiner(",");
                for (Conexao a : c.getRota().getArestas()) tipos.add(a.getTipo().name());
                ps.setInt(1, c.getId());
                ps.setInt(2, c.getLojaOrigem().getId());
                ps.setInt(3, c.getLojaDestino().getId());
                ps.setInt(4, c.getCarga().getId());
                ps.setInt(5, c.getCarga().getQuantidade());
                ps.setString(6, cidades.toString());
                ps.setString(7, tipos.toString());
                ps.setInt(8, c.getIndiceArestaAtual());
                ps.setDouble(9, c.getKmNaArestaAtual());
                ps.setString(10, c.getStatus().name());
                ps.setString(11, c.getProtecao().name());
                ps.setInt(12, c.getDiaUltimoEvento());
                ps.setInt(13, c.getHoraUltimoEvento());
                ps.setString(14, c.getServico().name());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Devolve ao motor as caravanas salvas, refazendo a rota pelas conexões atuais.
     * Se uma estrada do caminho deixou de existir, a carga (já paga) é entregue no destino.
     */
    public int carregarCaravanas(MotorEconomico motor) {
        Map<Integer, Cidade> cidades = new HashMap<>();
        for (Cidade c : motor.getCidades()) cidades.put(c.getId(), c);
        int restauradas = 0;
        try (Connection conn = BancoDados.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM caravanas_ativas ORDER BY id")) {
            while (rs.next()) {
                Lojas origem = motor.buscarLojaPorId(rs.getInt("loja_origem_id"));
                Lojas destino = motor.buscarLojaPorId(rs.getInt("loja_destino_id"));
                Item modelo = motor.getCatalogoItens().get(rs.getInt("item_id"));
                if (origem == null || destino == null || modelo == null) continue; // loja ou item apagado
                Item carga = Lojas.clonarItem(modelo, rs.getInt("quantidade"));

                RotaComercial rota = reconstruirRota(cidades, rs.getString("caminho_cidades"), rs.getString("tipos_trechos"));
                if (rota == null) {
                    destino.adicionarItem(carga);
                    destino.registrarMovimentacao(motor.getDiaAtual(), motor.getHoraAtual(), RegistroMovimentacao.TipoOperacao.ENTREGA_CARAVANA,
                            carga.getNome(), carga.getQuantidade(), 0L, "Rota alterada pelo Mestre: carga entregue direto");
                    continue;
                }
                Caravana c = Caravana.restaurar(rs.getInt("id"), origem, destino, carga, rota,
                        enumOu(ServicoTransporte.class, rs.getString("servico"), ServicoTransporte.COMUM),
                        enumOu(Caravana.StatusCaravana.class, rs.getString("status"), Caravana.StatusCaravana.EM_TRANSITO),
                        enumOu(Caravana.TipoProtecao.class, rs.getString("protecao"), Caravana.TipoProtecao.DESPROTEGIDA),
                        rs.getInt("indice_trecho"), rs.getDouble("km_no_trecho"), rs.getInt("dia_evento"), rs.getInt("hora_evento"));
                motor.restaurarCaravana(c);
                restauradas++;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao carregar caravanas: " + e.getMessage(), e);
        }
        return restauradas;
    }

    private static RotaComercial reconstruirRota(Map<Integer, Cidade> cidades, String caminho, String tipos) {
        String[] ids = caminho.split(",");
        String[] tiposTrechos = tipos.isBlank() ? new String[0] : tipos.split(",");
        List<Cidade> caminhoCidades = new ArrayList<>();
        for (String id : ids) {
            Cidade c = cidades.get(Integer.parseInt(id.trim()));
            if (c == null) return null;
            caminhoCidades.add(c);
        }
        List<Conexao> arestas = new ArrayList<>();
        for (int i = 0; i + 1 < caminhoCidades.size(); i++) {
            Cidade a = caminhoCidades.get(i), b = caminhoCidades.get(i + 1);
            String tipo = i < tiposTrechos.length ? tiposTrechos[i] : null;
            Conexao escolhida = null;
            for (Conexao con : a.getConexoes()) {
                if (!b.equals(con.outraPonta(a))) continue;
                if (escolhida == null || con.getTipo().name().equals(tipo)) escolhida = con;
            }
            if (escolhida == null) return null;
            arestas.add(escolhida);
        }
        return new RotaComercial(caminhoCidades, arestas);
    }

    private static <E extends Enum<E>> E enumOu(Class<E> tipo, String nome, E padrao) {
        try {
            return Enum.valueOf(tipo, nome);
        } catch (RuntimeException e) {
            return padrao;
        }
    }

    /** Ilustração de cada item do catálogo que tem uma (o motor em memória não guarda imagens). */
    public Map<Integer, String> imagensDosItens() {
        Map<Integer, String> imagens = new HashMap<>();
        try (Connection conn = BancoDados.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, imagem_url FROM itens WHERE imagem_url IS NOT NULL AND imagem_url <> ''")) {
            while (rs.next()) imagens.put(rs.getInt(1), rs.getString(2));
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao ler imagens dos itens", e);
        }
        return imagens;
    }

    /** Números de combate das armas feitas pela forja (itemId -> dano, durabilidade, alcance, slots). */
    public Map<Integer, Map<String, Object>> armasDaForja() {
        Map<Integer, Map<String, Object>> armas = new HashMap<>();
        try (Connection conn = BancoDados.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT item_id, dano, durabilidade, alcance_m, slots_efeito, composicao FROM item_arma")) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("dano", rs.getString("dano"));
                m.put("durabilidade", rs.getInt("durabilidade"));
                m.put("alcanceM", rs.getInt("alcance_m"));
                m.put("slotsEfeito", rs.getInt("slots_efeito"));
                m.put("composicao", rs.getString("composicao"));
                armas.put(rs.getInt("item_id"), m);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao ler armas da forja", e);
        }
        return armas;
    }

    /** Receitas geradas pela forja que não aparecem na lista da oficina: subcrafts de peças e encomendas avulsas. */
    public Set<Integer> receitasInternasDaForja() {
        Set<Integer> ids = new HashSet<>();
        try (Connection conn = BancoDados.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM receitas WHERE chave_forja LIKE 'RC:%' OR chave_forja LIKE 'R:EP:%'")) {
            while (rs.next()) ids.add(rs.getInt(1));
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao ler receitas da forja", e);
        }
        return ids;
    }

    public boolean itemExiste(int itemId) {
        try (Connection conn = BancoDados.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM itens WHERE id = ?")) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao consultar item " + itemId, e);
        }
    }

    private static int fluxoTipo(String tipo) {
        try {
            return RegistroMovimentacao.TipoOperacao.valueOf(tipo).getFluxo();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static String descricaoTipo(String tipo) {
        try {
            return RegistroMovimentacao.TipoOperacao.valueOf(tipo).getDescricao();
        } catch (RuntimeException e) {
            return tipo;
        }
    }
}
