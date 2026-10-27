package servidor;

import economia.MotorEconomico;
import economia.ReceitaLoja;
import economia.RedeConhecimento;
import economia.RedeConhecimento.Conhecimento;
import economia.RedeConhecimento.Estagio;
import economia.RedeConhecimento.Previsao;
import economia.RedeConhecimento.TipoConhecimento;
import geografia.Cidade;
import persistencia.AdminCrud;
import persistencia.ComercioDAO;
import persistencia.ConhecimentoDAO;
import persistencia.ForjaDAO;

import java.util.*;

/**
 * Tela de Disseminação do Mestre: conceder um tipo de arma ou uma receita a uma cidade,
 * acompanhar quando cada cidade chega ao Rumor, ao Comércio e à Produção, e intervir
 * (pausar, segredo, adiantar, revogar). Só o Mestre vê. Rotas sob /api/mestre/conhecimentos.
 */
class RotasConhecimento {

    private final ServidorThaluriaAPI servidor;
    private final ConhecimentoDAO dao = new ConhecimentoDAO();
    private final ForjaDAO forjaDAO = new ForjaDAO();
    private final ComercioDAO comercioDAO = new ComercioDAO();

    RotasConhecimento(ServidorThaluriaAPI servidor) {
        this.servidor = servidor;
    }

    @FunctionalInterface
    private interface Acao {
        Object executar(Map<String, String> params);
    }

    void registrar() {
        rota("/api/mestre/conhecimentos", p -> painel(), false);
        rota("/api/mestre/conhecimentos/conceder", p -> {
            TipoConhecimento tipo = "TIPO_ARMA".equals(p.get("tipo")) ? TipoConhecimento.TIPO_ARMA : TipoConhecimento.RECEITA;
            int id = dao.conceder(tipo, inteiroOuNulo(p, "tipoArmaId"), inteiroOuNulo(p, "receitaId"), inteiro(p, "cidadeOrigemId"), dia(), parametros(p));
            return Map.of("id", id);
        }, true);
        rota("/api/mestre/conhecimentos/parametros", p -> { dao.editarParametros(inteiro(p, "id"), parametros(p)); return ok(); }, true);
        rota("/api/mestre/conhecimentos/pausar", p -> { dao.pausar(conhecimento(p), "true".equals(p.get("pausar")), dia()); return ok(); }, true);
        rota("/api/mestre/conhecimentos/segredo", p -> { dao.definirSegredo(conhecimento(p).id(), "true".equals(p.get("segredo"))); return ok(); }, true);
        rota("/api/mestre/conhecimentos/cidade", p -> {
            dao.ajustarCidade(conhecimento(p), inteiro(p, "cidadeId"), p.getOrDefault("acao", "").trim().toUpperCase(Locale.ROOT), dia());
            return ok();
        }, true);
        rota("/api/mestre/conhecimentos/excluir", p -> { dao.excluir(conhecimento(p).id()); return ok(); }, true);
    }

    private void rota(String caminho, Acao acao, boolean mudaRede) {
        servidor.criarRota(caminho, exchange -> {
            Map<String, String> params = servidor.lerQueryOuForm(exchange);
            Object resposta;
            try {
                synchronized (servidor) {
                    resposta = acao.executar(params);
                    if (mudaRede) {
                        MotorEconomico motor = servidor.getMotorEconomico();
                        motor.setRedeConhecimento(dao.carregarRede(motor));
                    }
                }
            } catch (AdminCrud.ErroValidacao e) {
                servidor.enviarJson(exchange, 400, "{\"erro\":" + AdminCrud.json(e.getMessage()) + "}");
                return;
            }
            servidor.enviarJson(exchange, 200, AdminCrud.json(resposta));
        });
    }

    private Map<String, Object> painel() {
        MotorEconomico motor = servidor.getMotorEconomico();
        RedeConhecimento rede = motor.getRedeConhecimento();
        int dia = motor.getDiaAtual();
        ForjaDAO.Catalogo forja = forjaDAO.carregarCatalogo();
        Map<Integer, Map<String, Object>> dadosMapa = servidor.getEconomiaDAO().listarDadosMapa();

        List<Map<String, Object>> cidades = new ArrayList<>();
        for (Cidade c : motor.getCidades()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("nome", c.getNome());
            Map<String, Object> pos = dadosMapa.get(c.getId());
            m.put("x", pos != null ? pos.get("x") : null);
            m.put("y", pos != null ? pos.get("y") : null);
            cidades.add(m);
        }

        List<Map<String, Object>> conhecimentos = new ArrayList<>();
        for (Conhecimento k : rede.getConhecimentos()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", k.id());
            m.put("tipo", k.tipo().name());
            m.put("tipoArmaId", k.tipoArmaId());
            m.put("receitaId", k.receitaId());
            m.put("nome", nomeDo(k, forja, motor));
            m.put("cidadeOrigemId", k.cidadeOrigemId());
            m.put("diaConcessao", k.diaConcessao());
            m.put("sigiloDias", k.sigiloDias());
            m.put("diasPor100Km", k.diasPor100Km());
            m.put("resistenciaPorSalto", k.resistenciaPorSalto());
            m.put("diasRumorComercio", k.diasRumorComercio());
            m.put("diasComercioProducao", k.diasComercioProducao());
            m.put("pausado", k.pausado());
            m.put("segredo", k.segredo());
            m.put("decorrido", k.decorrido(dia));

            Map<String, Integer> contagem = new LinkedHashMap<>();
            for (Estagio e : Estagio.values()) contagem.put(e.name(), 0);
            List<Map<String, Object>> porCidade = new ArrayList<>();
            for (Cidade c : motor.getCidades()) {
                Previsao pr = rede.previsao(k.id(), c.getId());
                if (pr == null) continue;
                Estagio atual = rede.estagio(k, c.getId(), dia);
                contagem.merge(atual.name(), 1, Integer::sum);
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("cidadeId", c.getId());
                cm.put("estagio", atual.name());
                cm.put("diaRumor", rede.diaPrevisto(k, c.getId(), Estagio.RUMOR, dia));
                cm.put("diaComercio", rede.diaPrevisto(k, c.getId(), Estagio.COMERCIO, dia));
                cm.put("diaProducao", rede.diaPrevisto(k, c.getId(), Estagio.PRODUCAO, dia));
                cm.put("alcancavel", !Double.isInfinite(pr.rumor()));
                cm.put("saltos", pr.saltos());
                cm.put("km", pr.km());
                cm.put("origem", pr.origem());
                cm.put("revogada", pr.revogada());
                cm.put("adiantadoPara", pr.adiantadoPara() != null ? pr.adiantadoPara().name() : null);
                porCidade.add(cm);
            }
            m.put("contagem", contagem);
            m.put("cidades", porCidade);
            conhecimentos.add(m);
        }

        // O que pode ser concedido: tipos de arma e receitas prontas (não as peças nem encomendas avulsas)
        Set<Integer> internas = comercioDAO.receitasInternasDaForja();
        List<Map<String, Object>> tipos = new ArrayList<>();
        forja.tipos().values().forEach(t -> tipos.add(Map.of("id", t.id(), "nome", t.nome())));
        List<Map<String, Object>> receitas = new ArrayList<>();
        for (ReceitaLoja r : motor.getReceitas()) {
            if (internas.contains(r.id())) continue;
            receitas.add(Map.of("id", r.id(), "nome", r.nome(), "especialidade", r.tipoLoja() != null ? r.tipoLoja().getNomeFormatado() : "Qualquer loja"));
        }
        receitas.sort(Comparator.comparing(x -> String.valueOf(x.get("nome"))));

        ConhecimentoDAO.Parametros padrao = ConhecimentoDAO.Parametros.PADRAO;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("dia", dia);
        r.put("conhecimentos", conhecimentos);
        r.put("cidades", cidades);
        r.put("tiposArma", tipos);
        r.put("receitas", receitas);
        r.put("padrao", Map.of("sigiloDias", padrao.sigiloDias(), "diasPor100Km", padrao.diasPor100Km(), "resistenciaPorSalto", padrao.resistenciaPorSalto(),
                "diasRumorComercio", padrao.diasRumorComercio(), "diasComercioProducao", padrao.diasComercioProducao()));
        return r;
    }

    private static String nomeDo(Conhecimento k, ForjaDAO.Catalogo forja, MotorEconomico motor) {
        if (k.tipo() == TipoConhecimento.TIPO_ARMA) {
            var t = forja.tipos().get(k.tipoArmaId());
            return t != null ? t.nome() : "Tipo de arma apagado";
        }
        ReceitaLoja r = motor.getReceitasPorId().get(k.receitaId());
        return r != null ? r.nome() : "Receita apagada";
    }

    private Conhecimento conhecimento(Map<String, String> p) {
        int id = inteiro(p, "id");
        for (Conhecimento k : servidor.getMotorEconomico().getRedeConhecimento().getConhecimentos()) if (k.id() == id) return k;
        throw new AdminCrud.ErroValidacao("Conhecimento não encontrado. Atualize a página.");
    }

    private int dia() {
        return servidor.getMotorEconomico().getDiaAtual();
    }

    private static Map<String, Object> ok() {
        return Map.of("status", "OK");
    }

    private static ConhecimentoDAO.Parametros parametros(Map<String, String> p) {
        ConhecimentoDAO.Parametros d = ConhecimentoDAO.Parametros.PADRAO;
        return new ConhecimentoDAO.Parametros(real(p, "sigiloDias", d.sigiloDias()), real(p, "diasPor100Km", d.diasPor100Km()),
                real(p, "resistenciaPorSalto", d.resistenciaPorSalto()), real(p, "diasRumorComercio", d.diasRumorComercio()),
                real(p, "diasComercioProducao", d.diasComercioProducao()));
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

    private static double real(Map<String, String> p, String chave, double padrao) {
        String v = p.get(chave);
        if (v == null || v.isBlank()) return padrao;
        try {
            return Double.parseDouble(v.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new AdminCrud.ErroValidacao(chave + ": número inválido.");
        }
    }
}
