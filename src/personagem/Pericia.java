package personagem;

import java.util.Arrays;
import java.util.Random;

/**
 * As 14 Perícias Canônicas de Thaluria (docs/Status.md).
 * Mecânica de Rolagem: (Grau)d20 + (Nível de Maestria × 5).
 * - Grau 0: Rola 2d20 e pega o PIOR resultado (+0).
 * - Grau >= 1: Rola (Grau)d20 e pega o MELHOR resultado + (Maestria × 5).
 * - Trava de Atributo: Grau_Perícia <= Grau_Atributo_Base.
 * - Trava de Maestria: Maestria entre 0 e 3 (+0 a +15).
 */
public enum Pericia {
    // FOR
    ATLETISMO("Atletismo", Atributo.FOR, "Corrida, escalada, salto, nado e testes de força muscular continuada"),
    INTIMIDACAO_FISICA("Intimidação Física", Atributo.FOR, "Coerção pela presença física, força bruta e imposição corporal"),

    // DES
    FURTIVIDADE("Furtividade", Atributo.DES, "Mover-se sem ser detectado, camuflagem e passos silenciosos"),
    ACROBACIA("Acrobacia", Atributo.DES, "Equilíbrio, esquivas acrobáticas, manobras aéreas e amortecimento de quedas"),
    PRESTIDIGITACAO("Prestidigitação", Atributo.DES, "Arrombamento de fechaduras, mãos leves, roubo e desarmar armadilhas"),
    PONTARIA_LUTA("Pontaria/Luta", Atributo.DES, "Precisão de ataques corporais finos e disparos de projéteis"),

    // VIG
    SOBREVIVENCIA_BRUTA("Sobrevivência Bruta", Atributo.VIG, "Resistência a fome, sede, toxinas, intempéries e climas extremos"),

    // MEN
    ENGANACAO("Enganação", Atributo.MEN, "Mentiras, disfarces, blefes e manipulação de fatos"),
    PERSUASAO_DIPLOMACIA("Persuasão/Diplomacia", Atributo.MEN, "Negociação, diplomacia, retórica e convencer pela lógica"),
    INVESTIGACAO("Investigação", Atributo.MEN, "Dedução lógica, busca por pistas, análise de documentos e engenhocas"),
    MEDICINA_ALQUIMIA("Medicina/Alquimia", Atributo.MEN, "Tratamento de ferimentos, diagnóstico de doenças e identificação de compostos"),

    // VON
    INTIMIDACAO_PSICOLOGICA("Intimidação Psicológica", Atributo.VON, "Pressão de aura, olhar penetrante e imposição da força de espírito"),
    INTUICAO_EMPATIA("Intuição/Empatia", Atributo.VON, "Percepção de mentiras, leitura de intenções e sensibilidade emocional"),

    // ESP
    OCULTISMO_ARCANISMO("Ocultismo/Arcanismo", Atributo.ESP, "Identificação de magias, leitura de IA/grafos, contra-mágica e rituais"),
    ADESTRAMENTO("Adestramento", Atributo.ESP, "Comunicação com feras, domesticação e comando de criaturas");

    private final String nomeExibicao;
    private final Atributo atributoBase;
    private final String descricao;

    Pericia(String nomeExibicao, Atributo atributoBase, String descricao) {
        this.nomeExibicao = nomeExibicao;
        this.atributoBase = atributoBase;
        this.descricao = descricao;
    }

    public String getNomeExibicao() { return nomeExibicao; }
    public Atributo getAtributoBase() { return atributoBase; }
    public String getDescricao() { return descricao; }

    /**
     * Valida se o grau pretendido da perícia respeita a trava do atributo base.
     */
    public boolean validarTravaAtributo(int grauPericia, int grauAtributoBase) {
        return grauPericia >= 0 && grauPericia <= Math.max(0, grauAtributoBase);
    }

    /**
     * Valida o nível de maestria (0 a 3).
     */
    public static boolean validarMaestria(int maestria) {
        return maestria >= 0 && maestria <= 3;
    }

    /**
     * Rola a perícia estocasticamente conforme as regras de Status.md:
     * - Grau 0: 2d20, pior resultado (+0).
     * - Grau >= 1: (Grau)d20, melhor resultado + (maestria * 5).
     */
    public int rolar(int grauPericia, int maestria, Random random) {
        Random r = random != null ? random : new Random();
        int m = Math.max(0, Math.min(3, maestria));
        int bonusMaestria = m * 5;

        if (grauPericia <= 0) {
            int d1 = 1 + r.nextInt(20);
            int d2 = 1 + r.nextInt(20);
            return Math.min(d1, d2); // Pior resultado no Grau 0
        }

        int melhor = 1;
        for (int i = 0; i < grauPericia; i++) {
            int d = 1 + r.nextInt(20);
            if (d > melhor) melhor = d;
        }
        return melhor + bonusMaestria;
    }

    /**
     * Resolução determinística para testes unitários com valores de dados simulados.
     */
    public static int rolarDeterministico(int grauPericia, int maestria, int... dados) {
        int m = Math.max(0, Math.min(3, maestria));
        int bonusMaestria = m * 5;

        if (grauPericia <= 0) {
            if (dados == null || dados.length < 2) {
                throw new IllegalArgumentException("Grau 0 exige ao menos 2 dados d20");
            }
            return Math.min(dados[0], dados[1]);
        }

        if (dados == null || dados.length < grauPericia) {
            throw new IllegalArgumentException(String.format("Grau %d exige ao menos %d dados rolados", grauPericia, grauPericia));
        }

        int melhor = dados[0];
        for (int i = 1; i < grauPericia; i++) {
            if (dados[i] > melhor) melhor = dados[i];
        }
        return melhor + bonusMaestria;
    }
}
