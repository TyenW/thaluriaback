package economia;

import geografia.Cidade;
import geografia.Conexao;

import java.util.*;

/**
 * Disseminação de conhecimento entre cidades: um tipo de arma ou uma receita que o Mestre
 * concede a uma cidade e que se espalha pelo grafo de rotas em três estágios
 * (Rumor → Comércio → Produção), contra uma resistência, como se cada cidade tentasse
 * guardar o segredo. Ver docs/Sistema de Economia/04, seção 3.
 *
 * O cálculo é determinístico: para cada cidade sai o "atraso" de cada estágio, contado no
 * relógio do conhecimento (que para quando o Mestre pausa). Receitas e tipos sem conhecimento
 * cadastrado são comuns: valem no mundo todo, como antes.
 *
 * Esta classe é uma fotografia imutável; o servidor monta outra quando algo muda.
 */
public final class RedeConhecimento {

    public enum Estagio {
        NENHUM("Desconhecido"), RUMOR("Rumor"), COMERCIO("Comércio"), PRODUCAO("Produção");

        private final String rotulo;
        Estagio(String rotulo) { this.rotulo = rotulo; }
        public String getRotulo() { return rotulo; }
        public boolean peloMenos(Estagio outro) { return ordinal() >= outro.ordinal(); }
    }

    public enum TipoConhecimento { TIPO_ARMA, RECEITA }

    /** Intervenção do Mestre numa cidade: adiantar até um estágio (desde {@code atraso}) ou revogar. */
    public record Ajuste(Estagio adiantadoPara, boolean revogada, double atraso) {}

    public record Conhecimento(int id, TipoConhecimento tipo, Integer tipoArmaId, Integer receitaId, int cidadeOrigemId, int diaConcessao,
                               double sigiloDias, double diasPor100Km, double resistenciaPorSalto, double diasRumorComercio, double diasComercioProducao,
                               boolean pausado, Integer pausadoDesde, int diasPausados, boolean segredo, Map<Integer, Ajuste> ajustes) {
        public Conhecimento {
            ajustes = Map.copyOf(ajustes == null ? Map.of() : ajustes);
        }

        /** Dias corridos no relógio do conhecimento (pausas não contam). */
        public double decorrido(int dia) {
            double pausaAtual = pausado && pausadoDesde != null ? Math.max(0, dia - pausadoDesde) : 0;
            return dia - diaConcessao - diasPausados - pausaAtual;
        }
    }

    /** Atrasos (em dias do relógio do conhecimento) de cada estágio numa cidade. Infinito = não chega. */
    public record Previsao(double rumor, double comercio, double producao, int saltos, int km, boolean origem, boolean revogada, Estagio adiantadoPara) {
        public double atrasoDe(Estagio e) {
            return switch (e) {
                case RUMOR -> rumor;
                case COMERCIO -> comercio;
                case PRODUCAO -> producao;
                case NENHUM -> 0;
            };
        }
    }

    private record Ponto(double atraso, int saltos, int km) {}

    private final Map<Integer, Conhecimento> conhecimentos = new LinkedHashMap<>();
    private final Map<Integer, Map<Integer, Previsao>> previsoes = new HashMap<>();
    /** Conhecimentos que valem para cada receita (a própria e o tipo de arma dela). */
    private final Map<Integer, List<Conhecimento>> porReceita = new HashMap<>();
    private final Map<Integer, List<Conhecimento>> porItem = new HashMap<>();
    private final Map<Integer, Conhecimento> porTipo = new HashMap<>();
    /** Especialidade que leva o item à prateleira no estágio Comércio. */
    private final Map<Integer, Set<Lojas.TipoEstabelecimento>> especialidadesDoItem = new HashMap<>();

    /** Rede vazia: tudo é comum. */
    public static RedeConhecimento vazia() {
        return new RedeConhecimento(List.of(), List.of(), Map.of(), Map.of(), List.of());
    }

    /**
     * @param tipoDaReceita receita compilada pela forja → tipo de arma
     * @param lojaDoTipo tipo de arma → especialidade que forja o molde dele
     */
    public RedeConhecimento(List<Conhecimento> lista, List<ReceitaLoja> receitas, Map<Integer, Integer> tipoDaReceita,
                            Map<Integer, Lojas.TipoEstabelecimento> lojaDoTipo, Collection<Cidade> cidades) {
        Map<Integer, Cidade> mapaCidades = new HashMap<>();
        for (Cidade c : cidades) mapaCidades.put(c.getId(), c);
        for (Conhecimento k : lista) {
            conhecimentos.put(k.id(), k);
            previsoes.put(k.id(), calcularPrevisoes(k, mapaCidades));
            if (k.tipo() == TipoConhecimento.TIPO_ARMA && k.tipoArmaId() != null) porTipo.put(k.tipoArmaId(), k);
        }
        for (ReceitaLoja r : receitas) {
            List<Conhecimento> ks = new ArrayList<>();
            Set<Lojas.TipoEstabelecimento> esp = new HashSet<>();
            for (Conhecimento k : lista) {
                if (k.tipo() == TipoConhecimento.RECEITA && Objects.equals(k.receitaId(), r.id())) {
                    ks.add(k);
                    if (r.tipoLoja() != null) esp.add(r.tipoLoja());
                }
            }
            Integer tipo = tipoDaReceita.get(r.id());
            if (tipo != null && porTipo.containsKey(tipo)) {
                ks.add(porTipo.get(tipo));
                Lojas.TipoEstabelecimento t = lojaDoTipo.getOrDefault(tipo, r.tipoLoja());
                if (t != null) esp.add(t);
            }
            if (ks.isEmpty()) continue;
            porReceita.put(r.id(), ks);
            for (Conhecimento k : ks) {
                List<Conhecimento> doItem = porItem.computeIfAbsent(r.itemResultadoId(), x -> new ArrayList<>());
                if (!doItem.contains(k)) doItem.add(k);
            }
            especialidadesDoItem.computeIfAbsent(r.itemResultadoId(), x -> new HashSet<>()).addAll(esp);
        }
    }

    /**
     * Dijkstra no relógio do conhecimento: a origem solta o segredo depois do sigilo; cada trecho
     * custa km/100 × velocidade + resistência. Cidades adiantadas pelo Mestre também espalham a
     * partir do dia em que foram adiantadas; revogadas não sabem nem passam adiante.
     */
    private static Map<Integer, Previsao> calcularPrevisoes(Conhecimento k, Map<Integer, Cidade> cidades) {
        Map<Integer, Ponto> melhor = new HashMap<>();
        PriorityQueue<Map.Entry<Integer, Ponto>> fila = new PriorityQueue<>(Comparator.comparingDouble(e -> e.getValue().atraso()));
        java.util.function.BiConsumer<Integer, Ponto> oferecer = (id, p) -> {
            Ponto atual = melhor.get(id);
            if (atual == null || p.atraso() < atual.atraso()) {
                melhor.put(id, p);
                fila.add(Map.entry(id, p));
            }
        };

        boolean origemRevogada = k.ajustes().containsKey(k.cidadeOrigemId()) && k.ajustes().get(k.cidadeOrigemId()).revogada();
        if (!origemRevogada) oferecer.accept(k.cidadeOrigemId(), new Ponto(k.sigiloDias(), 0, 0));
        for (Map.Entry<Integer, Ajuste> a : k.ajustes().entrySet()) {
            if (!a.getValue().revogada() && a.getValue().adiantadoPara() != null && a.getKey() != k.cidadeOrigemId()) {
                oferecer.accept(a.getKey(), new Ponto(a.getValue().atraso(), 0, 0));
            }
        }

        Set<Integer> fechadas = new HashSet<>();
        while (!fila.isEmpty()) {
            Map.Entry<Integer, Ponto> e = fila.poll();
            int id = e.getKey();
            if (!fechadas.add(id) || e.getValue() != melhor.get(id)) continue;
            if (k.segredo()) continue; // segredo: ninguém passa adiante
            Cidade c = cidades.get(id);
            if (c == null) continue;
            for (Conexao con : c.getConexoes()) {
                if (con.isBloqueada()) continue;
                Cidade outra = con.outraPonta(c);
                Ajuste aj = k.ajustes().get(outra.getId());
                if (aj != null && aj.revogada()) continue;
                Ponto p = e.getValue();
                double custo = con.getDistanciaKm() / 100.0 * k.diasPor100Km() + k.resistenciaPorSalto();
                oferecer.accept(outra.getId(), new Ponto(p.atraso() + custo, p.saltos() + 1, p.km() + con.getDistanciaKm()));
            }
        }

        Map<Integer, Previsao> r = new HashMap<>();
        for (Integer id : cidades.keySet()) {
            Ajuste aj = k.ajustes().get(id);
            if (aj != null && aj.revogada()) {
                r.put(id, new Previsao(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 0, id == k.cidadeOrigemId(), true, null));
                continue;
            }
            if (id == k.cidadeOrigemId()) {
                r.put(id, new Previsao(0, 0, 0, 0, 0, true, false, null));
                continue;
            }
            Ponto p = melhor.get(id);
            double rumor = p != null ? p.atraso() : Double.POSITIVE_INFINITY;
            double comercio = rumor + k.diasRumorComercio();
            double producao = comercio + k.diasComercioProducao();
            Estagio adiantado = aj != null ? aj.adiantadoPara() : null;
            if (adiantado != null) {
                if (adiantado.peloMenos(Estagio.RUMOR)) rumor = Math.min(rumor, aj.atraso());
                if (adiantado.peloMenos(Estagio.COMERCIO)) comercio = Math.min(comercio, aj.atraso());
                if (adiantado.peloMenos(Estagio.PRODUCAO)) producao = Math.min(producao, aj.atraso());
            }
            r.put(id, new Previsao(rumor, comercio, producao, p != null ? p.saltos() : 0, p != null ? p.km() : 0, false, false, adiantado));
        }
        return r;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    public Collection<Conhecimento> getConhecimentos() { return Collections.unmodifiableCollection(conhecimentos.values()); }

    public Previsao previsao(int conhecimentoId, int cidadeId) {
        Map<Integer, Previsao> m = previsoes.get(conhecimentoId);
        return m != null ? m.get(cidadeId) : null;
    }

    public Estagio estagio(Conhecimento k, int cidadeId, int dia) {
        Previsao p = previsao(k.id(), cidadeId);
        if (p == null || p.revogada()) return Estagio.NENHUM;
        double t = k.decorrido(dia);
        if (t >= p.producao()) return Estagio.PRODUCAO;
        if (t >= p.comercio()) return Estagio.COMERCIO;
        if (t >= p.rumor()) return Estagio.RUMOR;
        return Estagio.NENHUM;
    }

    /** Dia do calendário em que o estágio chega (null = não chega ou o espalhamento está pausado). */
    public Integer diaPrevisto(Conhecimento k, int cidadeId, Estagio e, int diaAtual) {
        Previsao p = previsao(k.id(), cidadeId);
        if (p == null || p.revogada()) return null;
        double atraso = p.atrasoDe(e);
        if (Double.isInfinite(atraso)) return null;
        double falta = atraso - k.decorrido(diaAtual);
        if (falta <= 0) return diaAtual + (int) Math.floor(falta); // já chegou: dia aproximado em que chegou
        return k.pausado() ? null : diaAtual + (int) Math.ceil(falta);
    }

    public boolean receitaRegional(int receitaId) {
        return porReceita.containsKey(receitaId);
    }

    /** A receita pode ser fabricada na cidade? Comum: sempre. Regional: a cidade precisa estar em Produção. */
    public boolean receitaLiberada(int receitaId, int cidadeId, int dia) {
        List<Conhecimento> ks = porReceita.get(receitaId);
        if (ks == null) return true;
        for (Conhecimento k : ks) if (estagio(k, cidadeId, dia) == Estagio.PRODUCAO) return true;
        return false;
    }

    /** Estágio do item na cidade (o melhor entre os conhecimentos que o cobrem); null = item comum. */
    public Estagio estagioDoItem(int itemId, int cidadeId, int dia) {
        List<Conhecimento> ks = porItem.get(itemId);
        if (ks == null) return null;
        Estagio melhor = Estagio.NENHUM;
        for (Conhecimento k : ks) {
            Estagio e = estagio(k, cidadeId, dia);
            if (e.ordinal() > melhor.ordinal()) melhor = e;
        }
        return melhor;
    }

    /** Estágio do tipo de arma na cidade; null = tipo comum (qualquer forja sabe fazer). */
    public Estagio estagioDoTipo(int tipoArmaId, int cidadeId, int dia) {
        Conhecimento k = porTipo.get(tipoArmaId);
        return k == null ? null : estagio(k, cidadeId, dia);
    }

    /** Itens regionais que uma loja deve passar a procurar: cidade em Comércio (ou mais) e loja da especialidade ou Loja Geral. */
    public List<Integer> itensParaPrateleira(Lojas loja, int dia) {
        if (loja.getCidadeSede() == null) return List.of();
        List<Integer> itens = new ArrayList<>();
        for (Map.Entry<Integer, Set<Lojas.TipoEstabelecimento>> e : especialidadesDoItem.entrySet()) {
            boolean daLoja = loja.getTipoEstabelecimento() == Lojas.TipoEstabelecimento.LOJA_GERAL || e.getValue().contains(loja.getTipoEstabelecimento());
            if (!daLoja) continue;
            Estagio est = estagioDoItem(e.getKey(), loja.getCidadeSede().getId(), dia);
            if (est != null && est.peloMenos(Estagio.COMERCIO)) itens.add(e.getKey());
        }
        return itens;
    }
}
