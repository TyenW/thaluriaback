package personagem;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Constantes de fórmula do sistema (docs/Status.md) que cada {@link Raca} pode sobrescrever:
 * exatamente 1 constante ESTRUTURAL (recurso/defesa) e 1 constante METABÓLICA (ciclo de descanso,
 * regeneração, colapso). {@link #padrao()} reproduz as equações canônicas.
 */
public final class ParametrosFormulaRacial {
    private static final ParametrosFormulaRacial PADRAO = new Builder().construir();

    // Estruturais
    private final FormulaLinear hp, sanidade, energia, mana, defFisica, defOculta;

    // Metabólicas
    private final int regenManaBase, regenManaDivisor;
    private final int manaPorEnergia;                    // Mana gerada por 1 de Energia queimada
    private final int energiaDescansoBase, energiaDescansoMultVig;
    private final int hpDescansoMinHoras, hpDescansoDivisor;
    private final int sanDescansoMinHoras, sanDescansoDivisor;
    private final Map<ConfortoDescanso, Integer> qConforto;
    private final FormulaLinear turnosMorte;             // avaliada sobre os GRAUS dos atributos
    private final int divisorHpTecnicaFisica;            // custo faltante / divisor desconta do HP
    private final int multiplicadorSanBurnout;           // custo faltante × mult desconta da SAN
    private final int rodadasLucidasSanZero;

    private ParametrosFormulaRacial(Builder b) {
        this.hp = b.hp; this.sanidade = b.sanidade; this.energia = b.energia;
        this.mana = b.mana; this.defFisica = b.defFisica; this.defOculta = b.defOculta;
        this.regenManaBase = b.regenManaBase; this.regenManaDivisor = b.regenManaDivisor;
        this.manaPorEnergia = b.manaPorEnergia;
        this.energiaDescansoBase = b.energiaDescansoBase; this.energiaDescansoMultVig = b.energiaDescansoMultVig;
        this.hpDescansoMinHoras = b.hpDescansoMinHoras; this.hpDescansoDivisor = b.hpDescansoDivisor;
        this.sanDescansoMinHoras = b.sanDescansoMinHoras; this.sanDescansoDivisor = b.sanDescansoDivisor;
        this.qConforto = new EnumMap<>(b.qConforto);
        this.turnosMorte = b.turnosMorte;
        this.divisorHpTecnicaFisica = b.divisorHpTecnicaFisica;
        this.multiplicadorSanBurnout = b.multiplicadorSanBurnout;
        this.rodadasLucidasSanZero = b.rodadasLucidasSanZero;
    }

    public static ParametrosFormulaRacial padrao() { return PADRAO; }

    /** Inicia um builder a partir dos valores atuais (para sobrescrever uma constante). */
    public Builder modificar() { return new Builder(this); }

    public FormulaLinear getHp() { return hp; }
    public FormulaLinear getSanidade() { return sanidade; }
    public FormulaLinear getEnergia() { return energia; }
    public FormulaLinear getMana() { return mana; }
    public FormulaLinear getDefFisica() { return defFisica; }
    public FormulaLinear getDefOculta() { return defOculta; }
    public int getRegenManaBase() { return regenManaBase; }
    public int getRegenManaDivisor() { return regenManaDivisor; }
    public int getManaPorEnergia() { return manaPorEnergia; }
    public int getEnergiaDescansoBase() { return energiaDescansoBase; }
    public int getEnergiaDescansoMultVig() { return energiaDescansoMultVig; }
    public int getHpDescansoMinHoras() { return hpDescansoMinHoras; }
    public int getHpDescansoDivisor() { return hpDescansoDivisor; }
    public int getSanDescansoMinHoras() { return sanDescansoMinHoras; }
    public int getSanDescansoDivisor() { return sanDescansoDivisor; }
    public FormulaLinear getTurnosMorte() { return turnosMorte; }
    public int getDivisorHpTecnicaFisica() { return divisorHpTecnicaFisica; }
    public int getMultiplicadorSanBurnout() { return multiplicadorSanBurnout; }
    public int getRodadasLucidasSanZero() { return rodadasLucidasSanZero; }

    /** Multiplicador Q (em centésimos) efetivo para o nível de conforto, considerando a raça. */
    public int getQCentesimos(ConfortoDescanso conforto) {
        return qConforto.getOrDefault(conforto, conforto.getQPadraoCentesimos());
    }

    /** Quantas das 6 fórmulas estruturais diferem do padrão canônico. */
    public int contarSobrescritasEstruturais() {
        ParametrosFormulaRacial p = PADRAO;
        int n = 0;
        if (!hp.equals(p.hp)) n++;
        if (!sanidade.equals(p.sanidade)) n++;
        if (!energia.equals(p.energia)) n++;
        if (!mana.equals(p.mana)) n++;
        if (!defFisica.equals(p.defFisica)) n++;
        if (!defOculta.equals(p.defOculta)) n++;
        return n;
    }

    /** Quantos grupos metabólicos (descanso, regeneração, colapso) diferem do padrão. */
    public int contarSobrescritasMetabolicas() {
        ParametrosFormulaRacial p = PADRAO;
        int n = 0;
        if (regenManaBase != p.regenManaBase || regenManaDivisor != p.regenManaDivisor) n++;
        if (manaPorEnergia != p.manaPorEnergia) n++;
        if (energiaDescansoBase != p.energiaDescansoBase || energiaDescansoMultVig != p.energiaDescansoMultVig) n++;
        if (hpDescansoMinHoras != p.hpDescansoMinHoras || hpDescansoDivisor != p.hpDescansoDivisor) n++;
        if (sanDescansoMinHoras != p.sanDescansoMinHoras || sanDescansoDivisor != p.sanDescansoDivisor) n++;
        if (!qConforto.equals(p.qConforto)) n++;
        if (!turnosMorte.equals(p.turnosMorte)) n++;
        if (divisorHpTecnicaFisica != p.divisorHpTecnicaFisica) n++;
        if (multiplicadorSanBurnout != p.multiplicadorSanBurnout) n++;
        if (rodadasLucidasSanZero != p.rodadasLucidasSanZero) n++;
        return n;
    }

    public static final class Builder {
        private FormulaLinear hp = FormulaLinear.base(20).mais(Atributo.VIG, 3).mais(Atributo.FOR, 1).mais(Atributo.VON, 1).construir();
        private FormulaLinear sanidade = FormulaLinear.base(20).mais(Atributo.VON, 3).mais(Atributo.MEN, 1).mais(Atributo.VIG, 1).construir();
        private FormulaLinear energia = FormulaLinear.base(10).mais(Atributo.VIG, 2).mais(Atributo.FOR, 1).mais(Atributo.MEN, 1).construir();
        private FormulaLinear mana = FormulaLinear.base(10).mais(Atributo.ESP, 2).mais(Atributo.VON, 1).mais(Atributo.VIG, 1).construir();
        private FormulaLinear defFisica = FormulaLinear.base(5).mais(Atributo.DES, 1).maisDiv(Atributo.MEN, 2).construir();
        private FormulaLinear defOculta = FormulaLinear.base(5).mais(Atributo.VON, 1).maisDiv(Atributo.VIG, 2).construir();
        private int regenManaBase = 1, regenManaDivisor = 5;
        private int manaPorEnergia = 2;
        private int energiaDescansoBase = 5, energiaDescansoMultVig = 2;
        private int hpDescansoMinHoras = 4, hpDescansoDivisor = 16;
        private int sanDescansoMinHoras = 4, sanDescansoDivisor = 16;
        private final Map<ConfortoDescanso, Integer> qConforto = new EnumMap<>(ConfortoDescanso.class);
        private FormulaLinear turnosMorte = FormulaLinear.base(3).maisDiv(Atributo.VIG, 2).construir();
        private int divisorHpTecnicaFisica = 1;
        private int multiplicadorSanBurnout = 2;
        private int rodadasLucidasSanZero = 0;

        public Builder() {}

        private Builder(ParametrosFormulaRacial p) {
            hp = p.hp; sanidade = p.sanidade; energia = p.energia; mana = p.mana;
            defFisica = p.defFisica; defOculta = p.defOculta;
            regenManaBase = p.regenManaBase; regenManaDivisor = p.regenManaDivisor;
            manaPorEnergia = p.manaPorEnergia;
            energiaDescansoBase = p.energiaDescansoBase; energiaDescansoMultVig = p.energiaDescansoMultVig;
            hpDescansoMinHoras = p.hpDescansoMinHoras; hpDescansoDivisor = p.hpDescansoDivisor;
            sanDescansoMinHoras = p.sanDescansoMinHoras; sanDescansoDivisor = p.sanDescansoDivisor;
            qConforto.putAll(p.qConforto);
            turnosMorte = p.turnosMorte;
            divisorHpTecnicaFisica = p.divisorHpTecnicaFisica;
            multiplicadorSanBurnout = p.multiplicadorSanBurnout;
            rodadasLucidasSanZero = p.rodadasLucidasSanZero;
        }

        public Builder hp(FormulaLinear f) { this.hp = f; return this; }
        public Builder sanidade(FormulaLinear f) { this.sanidade = f; return this; }
        public Builder energia(FormulaLinear f) { this.energia = f; return this; }
        public Builder mana(FormulaLinear f) { this.mana = f; return this; }
        public Builder defFisica(FormulaLinear f) { this.defFisica = f; return this; }
        public Builder defOculta(FormulaLinear f) { this.defOculta = f; return this; }
        public Builder regenMana(int base, int divisor) { this.regenManaBase = base; this.regenManaDivisor = divisor; return this; }
        public Builder manaPorEnergia(int v) { this.manaPorEnergia = v; return this; }
        public Builder energiaDescanso(int base, int multVig) { this.energiaDescansoBase = base; this.energiaDescansoMultVig = multVig; return this; }
        public Builder sanDescanso(int minHoras, int divisor) { this.sanDescansoMinHoras = minHoras; this.sanDescansoDivisor = divisor; return this; }
        public Builder conforto(ConfortoDescanso c, int qCentesimos) { this.qConforto.put(c, qCentesimos); return this; }
        public Builder turnosMorte(FormulaLinear f) { this.turnosMorte = f; return this; }
        public Builder divisorHpTecnicaFisica(int v) { this.divisorHpTecnicaFisica = Math.max(1, v); return this; }
        public Builder multiplicadorSanBurnout(int v) { this.multiplicadorSanBurnout = Math.max(0, v); return this; }
        public Builder rodadasLucidasSanZero(int v) { this.rodadasLucidasSanZero = Math.max(0, v); return this; }

        public ParametrosFormulaRacial construir() { return new ParametrosFormulaRacial(this); }
    }
}
