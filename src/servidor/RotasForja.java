package servidor;

import economia.ForjaArmas;
import economia.ForjaArmas.*;
import persistencia.AdminCrud;
import persistencia.ForjaDAO;

import java.util.*;

/**
 * Tela da Forja do Mestre: moldes de arma (partes e o que cada uma aceita), tipos de arma
 * e receitas prontas, com prévia calculada pelo servidor. Rotas sob /api/mestre/forja/.
 * Toda gravação recompila as armas e recarrega as receitas do motor do comércio.
 */
class RotasForja {

    private final ServidorThaluriaAPI servidor;
    private final RotasComercio rotasComercio;
    private final ForjaDAO forjaDAO = new ForjaDAO();

    RotasForja(ServidorThaluriaAPI servidor, RotasComercio rotasComercio) {
        this.servidor = servidor;
        this.rotasComercio = rotasComercio;
    }

    @FunctionalInterface
    private interface Acao {
        Object executar(Map<String, String> params);
    }

    void registrar() {
        rota("/api/mestre/forja", p -> painel(), false);
        rota("/api/mestre/forja/previa", p -> armaJson(forjaDAO.previa(p.getOrDefault("nome", "Arma"), inteiro(p, "tipoArmaId"), escolhas(p.get("materiais")))), false);
        rota("/api/mestre/forja/molde", p -> Map.of("id", forjaDAO.salvarMolde(inteiroOuNulo(p, "id"), p.get("nome"), p.getOrDefault("tipoLoja", "FORJA"),
                inteiro(p, "slotsEfeito"), real(p, "tempoMontagemHoras"), p.get("descricao"), p.get("partes"))), true);
        rota("/api/mestre/forja/molde/excluir", p -> { forjaDAO.excluirMolde(inteiro(p, "id")); return Map.of("status", "EXCLUIDO"); }, true);
        rota("/api/mestre/forja/tipo", p -> Map.of("id", forjaDAO.salvarTipo(inteiroOuNulo(p, "id"), p.get("nome"), inteiro(p, "moldeId"), p.get("tipoItem"),
                inteiro(p, "qtdDados"), inteiro(p, "facesDado"), inteiro(p, "alcanceM"), p.get("descricao"))), true);
        rota("/api/mestre/forja/tipo/excluir", p -> { forjaDAO.excluirTipo(inteiro(p, "id")); return Map.of("status", "EXCLUIDO"); }, true);
        rota("/api/mestre/forja/receita", p -> Map.of("id", forjaDAO.salvarReceita(inteiroOuNulo(p, "id"), p.get("nome"), inteiro(p, "tipoArmaId"),
                escolhas(p.get("materiais")), "true".equalsIgnoreCase(p.get("somenteEncomenda")), p.get("descricao")).id()), true);
        rota("/api/mestre/forja/receita/excluir", p -> { forjaDAO.excluirReceita(inteiro(p, "id")); return Map.of("status", "EXCLUIDO"); }, true);
    }

    private void rota(String caminho, Acao acao, boolean mudaReceitas) {
        servidor.criarRota(caminho, exchange -> {
            Map<String, String> params = servidor.lerQueryOuForm(exchange);
            Object resposta;
            try {
                synchronized (servidor) {
                    resposta = acao.executar(params);
                    if (mudaReceitas) rotasComercio.atualizarReceitasDoMotor();
                }
            } catch (AdminCrud.ErroValidacao e) {
                servidor.enviarJson(exchange, 400, "{\"erro\":" + AdminCrud.json(e.getMessage()) + "}");
                return;
            }
            servidor.enviarJson(exchange, 200, AdminCrud.json(resposta));
        });
    }

    private Map<String, Object> painel() {
        ForjaDAO.Catalogo c = forjaDAO.carregarCatalogo();

        List<Map<String, Object>> moldes = new ArrayList<>();
        for (Molde m : c.moldes().values()) {
            List<Map<String, Object>> partes = new ArrayList<>();
            for (Parte p : m.partes()) {
                List<Map<String, Object>> opcoes = new ArrayList<>();
                for (Opcao o : p.opcoes()) {
                    Map<String, Object> om = new LinkedHashMap<>();
                    om.put("id", o.id());
                    om.put("categoria", o.categoria() != null ? o.categoria().name() : null);
                    om.put("itemId", o.itemId());
                    om.put("itemNome", o.itemId() != null && c.materiais().containsKey(o.itemId()) ? c.materiais().get(o.itemId()).nome() : null);
                    om.put("bonusDano", o.bonusDano());
                    om.put("bonusDurabilidade", o.bonusDurabilidade());
                    om.put("bonusAlcanceM", o.bonusAlcanceM());
                    opcoes.add(om);
                }
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("id", p.id());
                pm.put("nome", p.nome());
                pm.put("qtdMateriais", p.qtdMateriais());
                pm.put("tempoHoras", p.tempoHoras());
                pm.put("opcoes", opcoes);
                partes.add(pm);
            }
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("id", m.id());
            mm.put("nome", m.nome());
            mm.put("tipoLoja", m.tipoLoja().name());
            mm.put("slotsEfeito", m.slotsEfeito());
            mm.put("tempoMontagemHoras", m.tempoMontagemHoras());
            mm.put("partes", partes);
            moldes.add(mm);
        }

        List<Map<String, Object>> tipos = new ArrayList<>();
        for (TipoArma t : c.tipos().values()) {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("id", t.id());
            tm.put("nome", t.nome());
            tm.put("moldeId", t.moldeId());
            tm.put("tipoItem", t.tipoItem().name());
            tm.put("qtdDados", t.qtdDados());
            tm.put("facesDado", t.facesDado());
            tm.put("alcanceM", t.alcanceM());
            tipos.add(tm);
        }

        // Só itens com categoria de material (ou citados por alguma opção) interessam ao editor
        Set<Integer> citados = new HashSet<>();
        c.moldes().values().forEach(m -> m.partes().forEach(p -> p.opcoes().forEach(o -> { if (o.itemId() != null) citados.add(o.itemId()); })));
        List<Map<String, Object>> materiais = new ArrayList<>();
        for (Material m : c.materiais().values()) {
            if (m.categoria() == null && !citados.contains(m.itemId())) continue;
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("id", m.itemId());
            mm.put("nome", m.nome());
            mm.put("categoria", m.categoria() != null ? m.categoria().name() : null);
            mm.put("bonusDano", m.bonusDano());
            mm.put("durabilidade", m.durabilidade());
            mm.put("pesoKg", m.pesoKg());
            mm.put("precoLatao", m.precoLatao());
            mm.put("raridade", m.raridade().name());
            materiais.add(mm);
        }
        materiais.sort(Comparator.comparing(m -> String.valueOf(m.get("nome"))));

        List<Map<String, Object>> receitas = new ArrayList<>();
        for (ForjaDAO.ReceitaArma r : forjaDAO.listarReceitas()) {
            TipoArma t = c.tipos().get(r.tipoArmaId());
            Molde m = t != null ? c.moldes().get(t.moldeId()) : null;
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("id", r.id());
            rm.put("nome", r.nome());
            rm.put("tipoArmaId", r.tipoArmaId());
            rm.put("somenteEncomenda", r.somenteEncomenda());
            rm.put("descricao", r.descricao());
            rm.put("receitaId", r.receitaId());
            rm.put("itemId", r.itemId());
            List<Map<String, Object>> esc = new ArrayList<>();
            for (Escolha e : r.materiais()) esc.add(Map.of("parteId", e.parteId(), "itemId", e.itemId(), "quantidade", e.quantidade()));
            rm.put("materiais", esc);
            rm.put("arma", t != null && m != null ? armaJson(ForjaArmas.calcular(r.nome(), t, m, r.materiais(), c.materiais()))
                    : Map.of("problemas", List.of("O tipo ou o molde desta arma foi apagado.")));
            receitas.add(rm);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("moldes", moldes);
        r.put("tipos", tipos);
        r.put("materiais", materiais);
        r.put("receitas", receitas);
        r.put("categorias", Arrays.stream(CategoriaMaterial.values()).map(Enum::name).toList());
        return r;
    }

    static Map<String, Object> armaJson(Arma a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", a.nome());
        m.put("dano", a.dano());
        m.put("bonusDano", a.bonusDano());
        m.put("durabilidade", a.durabilidade());
        m.put("pesoKg", a.pesoKg());
        m.put("precoLatao", a.precoLatao());
        m.put("raridade", a.raridade().name());
        m.put("alcanceM", a.alcanceM());
        m.put("slotsEfeito", a.slotsEfeito());
        m.put("tempoHoras", a.tempoHoras());
        List<Map<String, Object>> comps = new ArrayList<>();
        for (Componente c : a.componentes()) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("parteId", c.parte().id());
            cm.put("parte", c.parte().nome());
            cm.put("nome", c.nome());
            cm.put("bonusDano", c.bonusDano());
            cm.put("durabilidade", c.durabilidade());
            cm.put("pesoKg", c.pesoKg());
            cm.put("precoLatao", c.precoLatao());
            cm.put("raridade", c.raridade().name());
            comps.add(cm);
        }
        m.put("componentes", comps);
        m.put("problemas", a.problemas());
        return m;
    }

    /** "parteId:itemId:qtd,..." → escolhas. */
    static List<Escolha> escolhas(String texto) {
        List<Escolha> lista = new ArrayList<>();
        if (texto == null || texto.isBlank()) return lista;
        for (String par : texto.split(",")) {
            String[] c = par.split(":");
            if (c.length != 3) throw new AdminCrud.ErroValidacao("Lista de materiais mal formada.");
            try {
                lista.add(new Escolha(Integer.parseInt(c[0].trim()), Integer.parseInt(c[1].trim()), Integer.parseInt(c[2].trim())));
            } catch (NumberFormatException e) {
                throw new AdminCrud.ErroValidacao("Lista de materiais mal formada.");
            }
        }
        return lista;
    }

    private static int inteiro(Map<String, String> p, String chave) {
        Integer v = inteiroOuNulo(p, chave);
        if (v == null) throw new AdminCrud.ErroValidacao("Parâmetro obrigatório ausente: " + chave + ".");
        return v;
    }

    private static Integer inteiroOuNulo(Map<String, String> p, String chave) {
        String v = p.get(chave);
        if (v == null || v.isBlank()) return null;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new AdminCrud.ErroValidacao(chave + ": número inteiro inválido.");
        }
    }

    private static double real(Map<String, String> p, String chave) {
        String v = p.getOrDefault(chave, "0").trim().replace(',', '.');
        try {
            return v.isEmpty() ? 0 : Double.parseDouble(v);
        } catch (NumberFormatException e) {
            throw new AdminCrud.ErroValidacao(chave + ": número inválido.");
        }
    }

}
