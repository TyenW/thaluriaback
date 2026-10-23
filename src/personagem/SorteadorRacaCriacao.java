package personagem;

import java.util.*;

/**
 * Sistema de Sorteio Ponderado de Raças na Criação de Personagem (SorteadorRacaCriacao).
 * - Opção 1: Humano (sempre garantido).
 * - Opções 2 e 3: Sorteadas sem repetição entre as raças JOGAVEL via sistema não-tão-aleatório (Pity).
 * - Rolagem de atributos iniciais nos limites estritos [min, max] da raça escolhida.
 */
public class SorteadorRacaCriacao {

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
     * Sorteia as 3 opções de raça considerando o fator de sorte acumulado (Pity) e boost de mesa.
     */
    public static OpcoesSorteio sortearOpcoesCriacao(int fatorSorteAcumulado, boolean boostCampanhaMestre, Random random) {
        Random r = random != null ? random : new Random();
        Raca opcao1 = Raca.HUMANO;

        List<Raca> candidatas = new ArrayList<>();
        for (Raca raca : Raca.getTodasRacasJogaveis()) {
            if (!"Humano".equalsIgnoreCase(raca.getNome())) {
                candidatas.add(raca);
            }
        }

        Raca opcao2 = sortearUmaRaca(candidatas, fatorSorteAcumulado, boostCampanhaMestre, r);
        candidatas.remove(opcao2);

        Raca opcao3 = sortearUmaRaca(candidatas, fatorSorteAcumulado, boostCampanhaMestre, r);

        return new OpcoesSorteio(opcao1, opcao2, opcao3);
    }

    private static Raca sortearUmaRaca(List<Raca> pool, int pity, boolean boost, Random r) {
        if (pool.isEmpty()) return Raca.HUMANO;

        int bonusRaridade = pity * 10;
        if (boost) bonusRaridade += 50;

        int pesoTotal = 0;
        Map<Raca, Integer> pesos = new LinkedHashMap<>();

        for (Raca raca : pool) {
            int pesoBase = raca.getRaridade().getPesoBase();
            int pesoFinal = pesoBase;

            switch (raca.getRaridade()) {
                case INCOMUM -> pesoFinal += bonusRaridade;
                case RARA -> pesoFinal += (bonusRaridade * 2);
                case ANCESTRAL_MITICA -> pesoFinal += (bonusRaridade * 3);
                case COMUM -> pesoFinal = Math.max(10, pesoBase - (bonusRaridade / 2));
            }
            pesos.put(raca, Math.max(1, pesoFinal));
            pesoTotal += Math.max(1, pesoFinal);
        }

        int sorteado = r.nextInt(pesoTotal);
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
