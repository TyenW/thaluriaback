package personagem;

import java.util.Random;

/**
 * Subtabela canônica 1d8 de Linhagem e Sopro Primordial Draconiano (docs/raças/Draconatos (Dragonborn).md).
 * Define afinidade elemental, cor típica das escamas, geometria da área, tipo de dano e condição tática exata.
 */
public enum LinhagemDraconica {
    FOGO(1, "Fogo / Piromântico", "Carmesim, Rubro ou Cobre", "Cone de 4 metros", "Dano de Fogo",
            "Queimadura Contínua: Alvos pegos inflamam, sofrendo ⌊Grau de ESP / 2⌋ + 2 de dano de fogo extra no início de seus próximos 2 turnos.",
            false, 4.0),

    GELO(2, "Gelo / Criogênico", "Branco, Prateado ou Celeste", "Cone de 4 metros", "Dano de Gelo",
            "Congelamento Articular: Cristais de gelo travam juntas e armaduras, aplicando -2 na Defesa Física e reduzindo o deslocamento pela metade (-50%) por 1 rodada.",
            false, 4.0),

    RAIO(3, "Raio / Elétrico", "Azul-Cobalto, Índigo ou Bronze", "Linha reta de 8 metros (1m larg.)", "Dano Elétrico",
            "Choque e Concussão: O arco voltaico atordoa o sistema nervoso dos alvos; falha no teste consome -2 PA das vítimas em seu próximo turno.",
            true, 8.0),

    ACIDO(4, "Ácido / Corrosivo", "Verde-Escuro, Oliva ou Latão", "Linha reta de 8 metros (1m larg.)", "Dano Ácido",
            "Corrosão de Blindagem: Vapores e jatos cáusticos dissolvem ligas e couro, reduzindo a Defesa Física do alvo em -3 por 2 rodadas completas.",
            true, 8.0),

    VENTO(5, "Vento / Cinético", "Cinza-Tempestade, Platina ou Ouro", "Cone de 4 metros", "Dano de Impacto",
            "Onda Concussiva: Rajada brutal de pressão que empurra os alvos 3 metros para trás e os derruba no chão (Condição: Caído).",
            false, 4.0),

    VENENO(6, "Veneno / Miasma", "Púrpura-Escuro, Pântano ou Esmeralda", "Cone de 4 metros", "Dano Biológico",
            "Intoxicação Debilitante: Miasma sufocante que bloqueia a regeneração natural de Energia/Mana dos alvos por 2 rodadas e impõe desvantagem (-2) em testes físicos.",
            false, 4.0),

    LUZ(7, "Luz / Radiante", "Dourado Fulgurante ou Alvo Cintilante", "Linha reta de 8 metros (1m larg.)", "Dano Radiante",
            "Clarão Cegante: Lampejo estelar que sobrecarrega a visão e os sentidos, impondo a condição Ofuscado / Cego (-4 em ataques e percepção) por 1 rodada.",
            true, 8.0),

    SOMBRA(8, "Sombra / Vácuo", "Obsidiana, Preto Fosco ou Ébano", "Cone de 4 metros", "Dano Necrótico",
            "Terror Primordial: Emanação abissal que devora matéria e vitalidade psíquica, subtraindo ⌊Grau de ESP / 2⌋ + 2 pontos diretos de Sanidade (SAN) além do dano físico.",
            false, 4.0);

    private final int numero1d8;
    private final String elemento;
    private final String corEscamas;
    private final String geometriaArea;
    private final String tipoDano;
    private final String condicaoTatica;
    private final boolean emLinhaReta;
    private final double alcanceMetros;

    LinhagemDraconica(int numero1d8, String elemento, String corEscamas, String geometriaArea,
                      String tipoDano, String condicaoTatica, boolean emLinhaReta, double alcanceMetros) {
        this.numero1d8 = numero1d8;
        this.elemento = elemento;
        this.corEscamas = corEscamas;
        this.geometriaArea = geometriaArea;
        this.tipoDano = tipoDano;
        this.condicaoTatica = condicaoTatica;
        this.emLinhaReta = emLinhaReta;
        this.alcanceMetros = alcanceMetros;
    }

    public int getNumero1d8() { return numero1d8; }
    public String getElemento() { return elemento; }
    public String getCorEscamas() { return corEscamas; }
    public String getGeometriaArea() { return geometriaArea; }
    public String getTipoDano() { return tipoDano; }
    public String getCondicaoTatica() { return condicaoTatica; }
    public boolean isEmLinhaReta() { return emLinhaReta; }
    public double getAlcanceMetros() { return alcanceMetros; }

    public static LinhagemDraconica porNumero(int d8) {
        for (LinhagemDraconica l : values()) {
            if (l.numero1d8 == d8) return l;
        }
        return FOGO;
    }

    public static LinhagemDraconica sortear(Random random) {
        Random r = random != null ? random : new Random();
        return values()[r.nextInt(values().length)];
    }
}
