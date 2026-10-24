package personagem;

import java.util.*;

/**
 * Sistema de Sorteio Ponderado de Raças na Criação de Personagem (SorteadorRacaCriacao).
 * Regras Canônicas de docs/Status.md e docs/raças/:
 * - Vaga 1 (Fixa): Sempre Humano.
 * - Pool das Vagas 2 e 3 (Sorteadas sem repetição): Contém apenas as raças com disponivelSorteioInicial = true
 *   (Por padrão: Anões, Elfos da Floresta, Fadas e Draconatos).
 * - Matemática de Probabilidade na Base 1.000:
 *   * Anões: COMUM -> Peso 425 (42,50% no 1º sorteio | ~75,4% acumulado)
 *   * Elfos da Floresta: COMUM -> Peso 425 (42,50% no 1º sorteio | ~75,4% acumulado)
 *   * Fadas: RARA -> Peso 146 (14,60% no 1º sorteio | ~47,8% acumulado)
 *   * Draconatos: ANCESTRAL_MITICA -> Peso 4 (0,40% no 1º sorteio | ~0,84% acumulado < 1%)
 * - Pity System (Sistema Não-Tão-Aleatório):
 *   * Acumulador de sorte aumenta progressivamente o peso de Fadas.
 *   * Draconato fica blindado com teto de probabilidade (tetoMaximoProbabilidade = 0.009 / < 1%),
 *     permanecendo como evento lendário a menos que o Mestre libere o "Impulso Dracônico".
 */
public class SorteadorRacaCriacao {

    public static final double TETO_MAXIMO_PROBABILIDADE_DRACONATO = 0.009;

    public static class OpcoesSorteio {
        private final Raca opcao1Humano;
        private final Raca opcao2Sorteada;
        private final Raca opcao3Sorteada;

        public OpcoesSorteio(Raca opcao1Humano, Raca opcao2Sorteada, Raca opcao3Sorteada) {
            this.opcao1Humano = opcao1Humano;
            this.opcao2Sorteada = opcao2Sorteada;
            this.opcao3Sorteada = opcao3Sorteada;
        }

        public Raca getOpcao1Humano() { return opcao1Humano; }
        public Raca getOpcao2Sorteada() { return opcao2Sorteada; }
        public Raca getOpcao3Sorteada() { return opcao3Sorteada; }

        public List<Raca> getListaOpcoes() {
            return List.of(opcao1Humano, opcao2Sorteada, opcao3Sorteada);
        }
    }

    /**
     * Sorteia as 3 opções de criação respeitando Vaga 1 (Humano) e Vagas 2 e 3 (Pool restrito).
     */
    public static OpcoesSorteio sortearOpcoesCriacao(int fatorSorteAcumulado, boolean boostCampanhaMestre, Random random) {
        return sortearOpcoesCriacao(fatorSorteAcumulado, boostCampanhaMestre, false, random);
    }

    /**
     * Sorteia as 3 opções de criação com suporte explícito ao "Impulso Dracônico" do Mestre.
     */
    public static OpcoesSorteio sortearOpcoesCriacao(int fatorSorteAcumulado, boolean boostCampanhaMestre,
                                                    boolean impulsoDraconico, Random random) {
        Random r = random != null ? random : new Random();
        Raca opcao1 = Raca.HUMANO;

        // Pool de raças ativas para as vagas 2 e 3 (sem o Humano)
        List<Raca> candidatas = new ArrayList<>(Raca.getRacasPoolSorteioInicial());
        if (candidatas.size() < 2) {
            // Fallback de segurança se nenhuma ou apenas 1 estiver ativa
            for (Raca raca : Raca.getTodasRacasJogaveis()) {
                if (!"Humano".equalsIgnoreCase(raca.getNome()) && !candidatas.contains(raca)) {
                    candidatas.add(raca);
                }
            }
        }

        Raca opcao2 = sortearUmaRaca(candidatas, fatorSorteAcumulado, boostCampanhaMestre, impulsoDraconico, r);
        candidatas.remove(opcao2);

        Raca opcao3 = sortearUmaRaca(candidatas, fatorSorteAcumulado, boostCampanhaMestre, impulsoDraconico, r);

        return new OpcoesSorteio(opcao1, opcao2, opcao3);
    }

    /**
     * Obtém o peso base na escala canônica de 1.000 para o sorteador inicial.
     */
    public static int getPesoBase(Raca raca) {
        if (raca == null) return 100;
        String n = raca.getNome().toLowerCase(Locale.ROOT);
        if (n.contains("anões") || n.contains("anoes")) return 425;
        if (n.contains("elfos da floresta")) return 425;
        if (n.contains("fada")) return 146;
        if (n.contains("draconato")) return 4;

        // Fallback proporcional para raças que o Mestre habilitar no painel
        return switch (raca.getRaridade()) {
            case COMUM -> 425;
            case INCOMUM -> 250;
            case RARA -> 146;
            case ANCESTRAL_MITICA -> 4;
        };
    }

    /**
     * Sorteia uma única raça dentre o pool respeitando os pesos na base 1.000,
     * o escalonamento do Pity nas Fadas e a trava de teto (< 1%) do Draconato.
     */
    private static Raca sortearUmaRaca(List<Raca> pool, int pity, boolean boostMestre,
                                       boolean impulsoDraconico, Random r) {
        if (pool.isEmpty()) return Raca.HUMANO;
        if (pool.size() == 1) return pool.get(0);

        Map<Raca, Integer> pesos = new LinkedHashMap<>();
        int pesoOutros = 0;
        Raca draconatoNoPool = null;

        for (Raca raca : pool) {
            int peso = getPesoBase(raca);

            if ("Draconatos".equalsIgnoreCase(raca.getNome())) {
                draconatoNoPool = raca;
                if (impulsoDraconico || boostMestre) {
                    peso = 250; // Impulso dracônico liberado pelo Mestre
                }
                pesos.put(raca, peso);
            } else {
                // Pity: aumenta o peso de Fadas (e raças raras) para quem cria mais fichas
                if (raca.getRaridade() == GrauRaridadeRaca.RARA) {
                    peso += (pity * 20);
                } else if (raca.getRaridade() == GrauRaridadeRaca.COMUM) {
                    peso = Math.max(50, peso - (pity * 8));
                }
                pesos.put(raca, Math.max(1, peso));
                pesoOutros += pesos.get(raca);
            }
        }

        // Blindagem do Draconato: Trava de teto (probabilidade <= 0.009 / < 1%)
        if (draconatoNoPool != null && !impulsoDraconico && !boostMestre) {
            int pesoAtualDrac = pesos.get(draconatoNoPool);
            double probAtual = (double) pesoAtualDrac / (pesoOutros + pesoAtualDrac);

            if (probAtual > TETO_MAXIMO_PROBABILIDADE_DRACONATO) {
                // pesoDrac / (pesoOutros + pesoDrac) <= 0.009 => pesoDrac <= (0.009 / (1 - 0.009)) * pesoOutros
                int pesoClamped = Math.max(1, (int) Math.floor((TETO_MAXIMO_PROBABILIDADE_DRACONATO / (1.0 - TETO_MAXIMO_PROBABILIDADE_DRACONATO)) * pesoOutros));
                pesos.put(draconatoNoPool, pesoClamped);
            }
        }

        int pesoTotal = 0;
        for (int p : pesos.values()) {
            pesoTotal += p;
        }

        int sorteado = r.nextInt(Math.max(1, pesoTotal));
        int acumulado = 0;
        for (Map.Entry<Raca, Integer> entry : pesos.entrySet()) {
            acumulado += entry.getValue();
            if (sorteado < acumulado) {
                return entry.getKey();
            }
        }

        return pool.get(0);
    }

    /**
     * Sorteia os 6 atributos iniciais estritamente dentro do intervalo [min, max] da raça.
     */
    public static Map<Atributo, Integer> rolarAtributosIniciais(Raca raca, Random random) {
        Random r = random != null ? random : new Random();
        Map<Atributo, Integer> atributos = new EnumMap<>(Atributo.class);

        for (Atributo a : Atributo.values()) {
            int min = raca != null ? raca.getMinimo(a) : 3;
            int max = raca != null ? raca.getMaximo(a) : 8;
            int valor = min + r.nextInt(Math.max(1, (max - min) + 1));
            atributos.put(a, valor);
        }
        return atributos;
    }
}
