package persistencia;

import personagem.*;

import java.sql.*;
import java.util.*;

/**
 * Data Access Object (DAO) para persistência e recuperação de Raças no SQLite.
 * Salva e recupera instâncias canônicas completas de Raca.java sem mock em memória.
 */
public class RacaDAO {

    public void salvar(Raca raca) {
        String sql = """
            INSERT INTO racas (
                nome, reino_origem, dominancia, raridade, tipo_acesso, disponivel_sorteio_inicial,
                vig_min, vig_max, for_min, for_max, des_min, des_max,
                men_min, men_max, esp_min, esp_max, von_min, von_max,
                hab_nome, hab_tipo, hab_custo_energia, hab_custo_mana,
                hab_alcance, hab_dado, hab_atributos, hab_descricao, hab_afinidade_1d8,
                mod_estrutural_tipo, mod_estrutural_formula,
                mod_metabolico_tipo, mod_metabolico_dados
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(nome) DO UPDATE SET
                reino_origem = excluded.reino_origem,
                dominancia = excluded.dominancia,
                raridade = excluded.raridade,
                tipo_acesso = excluded.tipo_acesso,
                disponivel_sorteio_inicial = excluded.disponivel_sorteio_inicial,
                vig_min = excluded.vig_min, vig_max = excluded.vig_max,
                for_min = excluded.for_min, for_max = excluded.for_max,
                des_min = excluded.des_min, des_max = excluded.des_max,
                men_min = excluded.men_min, men_max = excluded.men_max,
                esp_min = excluded.esp_min, esp_max = excluded.esp_max,
                von_min = excluded.von_min, von_max = excluded.von_max,
                hab_nome = excluded.hab_nome,
                hab_tipo = excluded.hab_tipo,
                hab_custo_energia = excluded.hab_custo_energia,
                hab_custo_mana = excluded.hab_custo_mana,
                hab_alcance = excluded.hab_alcance,
                hab_dado = excluded.hab_dado,
                hab_atributos = excluded.hab_atributos,
                hab_descricao = excluded.hab_descricao,
                hab_afinidade_1d8 = excluded.hab_afinidade_1d8,
                mod_estrutural_tipo = excluded.mod_estrutural_tipo,
                mod_estrutural_formula = excluded.mod_estrutural_formula,
                mod_metabolico_tipo = excluded.mod_metabolico_tipo,
                mod_metabolico_dados = excluded.mod_metabolico_dados;
        """;

        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, raca.getNome());
            ps.setString(2, raca.getReinoOrigem());
            ps.setString(3, raca.getDominancia().name());
            ps.setString(4, raca.getRaridade().name());
            ps.setString(5, raca.getTipoAcesso().name());
            ps.setInt(6, raca.isDisponivelSorteioInicial() ? 1 : 0);

            ps.setInt(7, raca.getMinimo(Atributo.VIG));
            ps.setInt(8, raca.getMaximo(Atributo.VIG));
            ps.setInt(9, raca.getMinimo(Atributo.FOR));
            ps.setInt(10, raca.getMaximo(Atributo.FOR));
            ps.setInt(11, raca.getMinimo(Atributo.DES));
            ps.setInt(12, raca.getMaximo(Atributo.DES));
            ps.setInt(13, raca.getMinimo(Atributo.MEN));
            ps.setInt(14, raca.getMaximo(Atributo.MEN));
            ps.setInt(15, raca.getMinimo(Atributo.ESP));
            ps.setInt(16, raca.getMaximo(Atributo.ESP));
            ps.setInt(17, raca.getMinimo(Atributo.VON));
            ps.setInt(18, raca.getMaximo(Atributo.VON));

            HabilidadeCombate hab = raca.getHabilidadeInata();
            if (hab != null) {
                ps.setString(19, hab.getNome());
                ps.setString(20, hab.getTipoAcao().name());
                ps.setInt(21, hab.getCustoEnergia());
                ps.setInt(22, hab.getCustoMana());
                ps.setDouble(23, hab.getAlcanceMetros());
                ps.setString(24, hab.getDadoBase());

                StringBuilder atrs = new StringBuilder();
                for (Atributo a : hab.getAtributosDano()) {
                    if (!atrs.isEmpty()) atrs.append(",");
                    atrs.append(a.name());
                }
                ps.setString(25, atrs.toString());
                ps.setString(26, hab.getDescricaoEfeito());
                if (hab.getAfinidadeDraconica1d8() != null) {
                    ps.setInt(27, hab.getAfinidadeDraconica1d8());
                } else {
                    ps.setNull(27, Types.INTEGER);
                }
            } else {
                ps.setString(19, "Ataque Básico");
                ps.setString(20, HabilidadeCombate.TipoAcaoCombate.ATAQUE_CORPORAL.name());
                ps.setInt(21, 1);
                ps.setInt(22, 0);
                ps.setDouble(23, 1.5);
                ps.setString(24, "1d4");
                ps.setString(25, "FOR");
                ps.setString(26, "Ataque comum");
                ps.setNull(27, Types.INTEGER);
            }

            ParametrosFormulaRacial p = raca.getParametros();
            String[] estrutural = serializarModificadorEstrutural(p);
            String[] metabolico = serializarModificadorMetabolico(p);

            ps.setString(28, estrutural[0]);
            ps.setString(29, estrutural[1]);
            ps.setString(30, metabolico[0]);
            ps.setString(31, metabolico[1]);

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar raça no SQLite: " + raca.getNome(), e);
        }
    }

    public Raca buscarPorNome(String nome) {
        if (nome == null) return null;
        String sql = "SELECT * FROM racas WHERE UPPER(nome) = UPPER(?)";
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nome.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return converterResultSetParaRaca(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar raça no SQLite: " + nome, e);
        }
        return null;
    }

    public List<Raca> listarTodas() {
        List<Raca> lista = new ArrayList<>();
        String sql = "SELECT * FROM racas ORDER BY nome ASC";
        try (Connection conn = BancoDados.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(converterResultSetParaRaca(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar raças no SQLite", e);
        }
        return lista;
    }

    public List<Raca> listarPoolSorteioInicial() {
        List<Raca> lista = new ArrayList<>();
        String sql = "SELECT * FROM racas WHERE disponivel_sorteio_inicial = 1 AND UPPER(nome) != 'HUMANO' ORDER BY nome ASC";
        try (Connection conn = BancoDados.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(converterResultSetParaRaca(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar pool inicial de raças no SQLite", e);
        }
        return lista;
    }

    public void atualizarDisponibilidadeSorteio(String nome, boolean disponivel) {
        String sql = "UPDATE racas SET disponivel_sorteio_inicial = ? WHERE UPPER(nome) = UPPER(?)";
        try (Connection conn = BancoDados.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, disponivel ? 1 : 0);
            ps.setString(2, nome.trim());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar disponibilidade de sorteio da raça: " + nome, e);
        }
    }

    public void popularRacasPadraoSeVazio() {
        boolean vazio = false;
        String sqlCount = "SELECT COUNT(*) FROM racas";
        try (Connection conn = BancoDados.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sqlCount)) {
            if (rs.next() && rs.getInt(1) == 0) {
                vazio = true;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao verificar catálogo padrão de raças no SQLite", e);
        }

        if (vazio) {
            for (Raca r : Raca.getTodasRacas()) {
                salvar(r);
            }
        }
    }

    // =========================================================================
    // CONVERSORES & SERIALIZADORES
    // =========================================================================

    private Raca converterResultSetParaRaca(ResultSet rs) throws SQLException {
        String nome = rs.getString("nome");
        String reino = rs.getString("reino_origem");
        DominanciaOntologica dom = DominanciaOntologica.valueOf(rs.getString("dominancia"));
        GrauRaridadeRaca rar = GrauRaridadeRaca.valueOf(rs.getString("raridade"));
        TipoAcessoRaca acesso = TipoAcessoRaca.valueOf(rs.getString("tipo_acesso"));
        boolean disponivelSorteio = rs.getInt("disponivel_sorteio_inicial") == 1;

        Map<Atributo, int[]> limites = new EnumMap<>(Atributo.class);
        limites.put(Atributo.VIG, new int[]{rs.getInt("vig_min"), rs.getInt("vig_max")});
        limites.put(Atributo.FOR, new int[]{rs.getInt("for_min"), rs.getInt("for_max")});
        limites.put(Atributo.DES, new int[]{rs.getInt("des_min"), rs.getInt("des_max")});
        limites.put(Atributo.MEN, new int[]{rs.getInt("men_min"), rs.getInt("men_max")});
        limites.put(Atributo.ESP, new int[]{rs.getInt("esp_min"), rs.getInt("esp_max")});
        limites.put(Atributo.VON, new int[]{rs.getInt("von_min"), rs.getInt("von_max")});

        // Habilidade Inata
        String habNome = rs.getString("hab_nome");
        HabilidadeCombate.TipoAcaoCombate habTipo = HabilidadeCombate.TipoAcaoCombate.valueOf(rs.getString("hab_tipo"));
        int habCustoE = rs.getInt("hab_custo_energia");
        int habCustoM = rs.getInt("hab_custo_mana");
        double habAlcance = rs.getDouble("hab_alcance");
        String habDado = rs.getString("hab_dado");
        String habAtrStr = rs.getString("hab_atributos");
        List<Atributo> atrs = new ArrayList<>();
        if (habAtrStr != null && !habAtrStr.isBlank()) {
            for (String a : habAtrStr.split(",")) {
                atrs.add(Atributo.valueOf(a.trim()));
            }
        }
        String habDesc = rs.getString("hab_descricao");
        int afinidadeObj = rs.getInt("hab_afinidade_1d8");
        Integer afinidade = rs.wasNull() ? null : afinidadeObj;

        HabilidadeCombate hab = new HabilidadeCombate(habNome, habTipo, habCustoE, habCustoM, habAlcance, habDado, atrs, habDesc, afinidade);

        // Parâmetros de Fórmula Racial
        String modEstruturalTipo = rs.getString("mod_estrutural_tipo");
        String modEstruturalFormula = rs.getString("mod_estrutural_formula");
        String modMetabolicoTipo = rs.getString("mod_metabolico_tipo");
        String modMetabolicoDados = rs.getString("mod_metabolico_dados");

        ParametrosFormulaRacial params = deserializarParametros(modEstruturalTipo, modEstruturalFormula, modMetabolicoTipo, modMetabolicoDados);

        return new Raca(nome, reino, dom, rar, acesso, disponivelSorteio, limites, params, hab);
    }

    private static String[] serializarModificadorEstrutural(ParametrosFormulaRacial p) {
        ParametrosFormulaRacial padrao = ParametrosFormulaRacial.padrao();
        if (!p.getHp().equals(padrao.getHp())) {
            return new String[]{"HP", serializarFormula(p.getHp())};
        }
        if (!p.getSanidade().equals(padrao.getSanidade())) {
            return new String[]{"SANIDADE", serializarFormula(p.getSanidade())};
        }
        if (!p.getEnergia().equals(padrao.getEnergia())) {
            return new String[]{"ENERGIA", serializarFormula(p.getEnergia())};
        }
        if (!p.getMana().equals(padrao.getMana())) {
            return new String[]{"MANA", serializarFormula(p.getMana())};
        }
        if (!p.getDefFisica().equals(padrao.getDefFisica())) {
            return new String[]{"DEF_FISICA", serializarFormula(p.getDefFisica())};
        }
        if (!p.getDefOculta().equals(padrao.getDefOculta())) {
            return new String[]{"DEF_OCULTA", serializarFormula(p.getDefOculta())};
        }
        return new String[]{"PADRAO", ""};
    }

    private static String[] serializarModificadorMetabolico(ParametrosFormulaRacial p) {
        ParametrosFormulaRacial padrao = ParametrosFormulaRacial.padrao();
        if (!p.getTurnosMorte().equals(padrao.getTurnosMorte())) {
            return new String[]{"TURNOS_MORTE", serializarFormula(p.getTurnosMorte())};
        }
        if (p.getEnergiaDescansoBase() != padrao.getEnergiaDescansoBase() || p.getEnergiaDescansoMultVig() != padrao.getEnergiaDescansoMultVig()) {
            return new String[]{"ENERGIA_DESCANSO", "base=" + p.getEnergiaDescansoBase() + ";multVig=" + p.getEnergiaDescansoMultVig()};
        }
        if (p.getMultiplicadorSanBurnout() != padrao.getMultiplicadorSanBurnout()) {
            return new String[]{"BURNOUT_SAN", "mult=" + p.getMultiplicadorSanBurnout()};
        }
        if (p.getDivisorHpTecnicaFisica() != padrao.getDivisorHpTecnicaFisica()) {
            return new String[]{"BURNOUT_ENERGIA", "divisor=" + p.getDivisorHpTecnicaFisica()};
        }
        if (p.getManaPorEnergia() != padrao.getManaPorEnergia()) {
            return new String[]{"MANA_POR_ENERGIA", "taxa=" + p.getManaPorEnergia()};
        }
        if (p.getRegenManaBase() != padrao.getRegenManaBase() || p.getRegenManaDivisor() != padrao.getRegenManaDivisor()) {
            return new String[]{"REGEN_MANA", "base=" + p.getRegenManaBase() + ";divisor=" + p.getRegenManaDivisor()};
        }
        if (p.getSanDescansoMinHoras() != padrao.getSanDescansoMinHoras() || p.getSanDescansoDivisor() != padrao.getSanDescansoDivisor()) {
            return new String[]{"SAN_DESCANSO", "minHoras=" + p.getSanDescansoMinHoras() + ";divisor=" + p.getSanDescansoDivisor()};
        }
        if (p.getRodadasLucidasSanZero() != padrao.getRodadasLucidasSanZero()) {
            return new String[]{"SAN_ZERO_LUCIDO", "rodadas=" + p.getRodadasLucidasSanZero()};
        }
        if (p.getQCentesimos(ConfortoDescanso.PESSIMO) != ConfortoDescanso.PESSIMO.getQPadraoCentesimos()
                || p.getQCentesimos(ConfortoDescanso.PRECARIO) != ConfortoDescanso.PRECARIO.getQPadraoCentesimos()) {
            return new String[]{"CONFORTO_DESCANSO", "PESSIMO:" + p.getQCentesimos(ConfortoDescanso.PESSIMO) + ",PRECARIO:" + p.getQCentesimos(ConfortoDescanso.PRECARIO)};
        }
        return new String[]{"PADRAO", ""};
    }

    public static String serializarFormula(FormulaLinear f) {
        if (f == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("base=").append(f.getBase());
        for (Map.Entry<Atributo, int[]> e : f.getTermos().entrySet()) {
            sb.append(";term=").append(e.getKey().name())
              .append(":").append(e.getValue()[0])
              .append(":").append(e.getValue()[1]);
        }
        return sb.toString();
    }

    public static FormulaLinear deserializarFormula(String s) {
        if (s == null || s.isBlank()) return FormulaLinear.base(0).construir();
        String[] partes = s.split(";");
        int base = 0;
        FormulaLinear.Builder b = null;
        for (String p : partes) {
            if (p.startsWith("base=")) {
                base = Integer.parseInt(p.substring(5));
                b = FormulaLinear.base(base);
            } else if (p.startsWith("term=") && b != null) {
                String[] t = p.substring(5).split(":");
                Atributo atr = Atributo.valueOf(t[0]);
                int num = Integer.parseInt(t[1]);
                int den = Integer.parseInt(t[2]);
                b.maisFracao(atr, num, den);
            }
        }
        return b != null ? b.construir() : FormulaLinear.base(base).construir();
    }

    private static ParametrosFormulaRacial deserializarParametros(String modEstTipo, String modEstFormula,
                                                                 String modMetTipo, String modMetDados) {
        ParametrosFormulaRacial.Builder b = ParametrosFormulaRacial.padrao().modificar();

        if (modEstTipo != null && !modEstTipo.equalsIgnoreCase("PADRAO")) {
            FormulaLinear f = deserializarFormula(modEstFormula);
            switch (modEstTipo.toUpperCase(Locale.ROOT)) {
                case "HP" -> b.hp(f);
                case "SANIDADE" -> b.sanidade(f);
                case "ENERGIA" -> b.energia(f);
                case "MANA" -> b.mana(f);
                case "DEF_FISICA" -> b.defFisica(f);
                case "DEF_OCULTA" -> b.defOculta(f);
            }
        }

        if (modMetTipo != null && !modMetTipo.equalsIgnoreCase("PADRAO")) {
            switch (modMetTipo.toUpperCase(Locale.ROOT)) {
                case "TURNOS_MORTE" -> b.turnosMorte(deserializarFormula(modMetDados));
                case "ENERGIA_DESCANSO" -> {
                    int base = 10, multVig = 3;
                    for (String part : modMetDados.split(";")) {
                        if (part.startsWith("base=")) base = Integer.parseInt(part.substring(5));
                        if (part.startsWith("multVig=")) multVig = Integer.parseInt(part.substring(8));
                    }
                    b.energiaDescanso(base, multVig);
                }
                case "BURNOUT_SAN" -> {
                    int mult = 1;
                    if (modMetDados.startsWith("mult=")) mult = Integer.parseInt(modMetDados.substring(5));
                    b.multiplicadorSanBurnout(mult);
                }
                case "BURNOUT_ENERGIA" -> {
                    int div = 2;
                    if (modMetDados.startsWith("divisor=")) div = Integer.parseInt(modMetDados.substring(8));
                    b.divisorHpTecnicaFisica(div);
                }
                case "MANA_POR_ENERGIA" -> {
                    int taxa = 4;
                    if (modMetDados.startsWith("taxa=")) taxa = Integer.parseInt(modMetDados.substring(5));
                    b.manaPorEnergia(taxa);
                }
                case "REGEN_MANA" -> {
                    int base = 2, div = 4;
                    for (String part : modMetDados.split(";")) {
                        if (part.startsWith("base=")) base = Integer.parseInt(part.substring(5));
                        if (part.startsWith("divisor=")) div = Integer.parseInt(part.substring(8));
                    }
                    b.regenMana(base, div);
                }
                case "SAN_DESCANSO" -> {
                    int minHoras = 2, div = 10;
                    for (String part : modMetDados.split(";")) {
                        if (part.startsWith("minHoras=")) minHoras = Integer.parseInt(part.substring(9));
                        if (part.startsWith("divisor=")) div = Integer.parseInt(part.substring(8));
                    }
                    b.sanDescanso(minHoras, div);
                }
                case "SAN_ZERO_LUCIDO" -> {
                    int rodadas = 1;
                    if (modMetDados.startsWith("rodadas=")) rodadas = Integer.parseInt(modMetDados.substring(8));
                    b.rodadasLucidasSanZero(rodadas);
                }
                case "CONFORTO_DESCANSO" -> {
                    for (String item : modMetDados.split(",")) {
                        String[] pair = item.split(":");
                        ConfortoDescanso c = ConfortoDescanso.valueOf(pair[0].trim());
                        int q = Integer.parseInt(pair[1].trim());
                        b.conforto(c, q);
                    }
                }
            }
        }

        return b.construir();
    }
}
