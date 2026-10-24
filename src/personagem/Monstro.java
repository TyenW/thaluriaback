package personagem;

import economia.EfeitoItem;
import java.util.*;

/**
 * Representa um Monstro ou Criatura no universo de Thaluria RPG (docs/Status.md).
 * Possui atributos fundamentais, cálculo unificado de IA (Índice de Ameaça),
 * Defesa Oculta canônica (5 + VON + ⌊VIG / 2⌋), habilidades de combate inatas,
 * condições ativas e capacidade de domesticação via SistemaDoma.
 */
public class Monstro {

    public enum PorteMonstro {
        MINUSCULO("Minúsculo"),
        PEQUENO("Pequeno"),
        MEDIO("Médio"),
        GRANDE("Grande"),
        COLOSSAL("Colossal");

        private final String nomeFormatado;

        PorteMonstro(String nomeFormatado) {
            this.nomeFormatado = nomeFormatado;
        }

        public String getNomeFormatado() {
            return nomeFormatado;
        }

        public boolean isBestaDeCargaColossal() {
            return this == GRANDE || this == COLOSSAL;
        }

        @Override
        public String toString() {
            return nomeFormatado;
        }
    }

    private int id;
    private String nome;
    private String especie;
    private boolean domesticavel;
    private boolean domado;
    private Ficha dono;
    private PorteMonstro porte;

    // Atributos Fundamentais
    private int vig;
    private int forca;
    private int des;
    private int men;
    private int esp;
    private int von;

    // Recursos
    private int hpMax;
    private int hp;
    private int energiaMax;
    private int energia;
    private int manaMax;
    private int mana;

    // Defesas
    private int defesa;        // Defesa Física
    private int defesaOculta;  // Defesa Oculta: 5 + VON + ⌊VIG / 2⌋

    // Habilidades e Efeitos
    private final List<HabilidadeCombate> habilidadesInatas = new ArrayList<>();
    private final List<EfeitoItem> condicoesAtivas = new ArrayList<>();

    public Monstro(String nome, String especie, PorteMonstro porte, boolean domesticavel,
                   int vig, int forca, int des, int men, int esp, int von) {
        this(0, nome, especie, porte, domesticavel, vig, forca, des, men, esp, von);
    }

    public Monstro(int id, String nome, String especie, PorteMonstro porte, boolean domesticavel,
                   int vig, int forca, int des, int men, int esp, int von) {
        this.id = id;
        this.nome = nome != null ? nome : "Monstro Desconhecido";
        this.especie = especie != null ? especie : "Fera";
        this.porte = porte != null ? porte : PorteMonstro.MEDIO;
        this.domesticavel = domesticavel;
        this.domado = false;
        this.dono = null;

        this.vig = Math.max(1, vig);
        this.forca = Math.max(1, forca);
        this.des = Math.max(1, des);
        this.men = Math.max(1, men);
        this.esp = Math.max(1, esp);
        this.von = Math.max(1, von);

        recalcularEstatisticas();
        restaurarTudo();
    }

    /**
     * Recalcula defesas e recursos com base nos atributos do monstro.
     * Defesa Oculta canônica: 5 + VON + ⌊VIG / 2⌋.
     */
    public void recalcularEstatisticas() {
        this.defesaOculta = 5 + this.von + (this.vig / 2);
        this.defesa = 8 + (this.des / 2) + (this.forca / 4);

        if (this.hpMax <= 0) {
            this.hpMax = 30 + (this.vig * 4) + (this.forca * 2);
        }
        if (this.energiaMax <= 0) {
            this.energiaMax = 20 + (this.vig * 2) + this.des;
        }
        if (this.manaMax <= 0) {
            this.manaMax = 10 + (this.esp * 2) + this.men;
        }
    }

    public void restaurarTudo() {
        this.hp = this.hpMax;
        this.energia = this.energiaMax;
        this.mana = this.manaMax;
    }

    // --- Equação Unificada de IA (Índice de Ameaça) idêntica ao PA ---
    public double calcularIA() {
        double somaQuadrados = (Math.pow(this.forca, 2) + Math.pow(this.des, 2) +
                Math.pow(this.esp, 2) + Math.pow(this.men, 2)) / 2.5;
        double produtoCruzado = (this.vig + this.von) * (this.des + this.men);
        double numerador = somaQuadrados + produtoCruzado;

        int maior = Math.max(this.forca, Math.max(this.des, Math.max(this.esp, Math.max(this.men, Math.max(this.vig, this.von)))));
        int menor = Math.min(this.forca, Math.min(this.des, Math.min(this.esp, Math.min(this.men, Math.min(this.vig, this.von)))));

        double fatorEstabilidade = (maior >= 101) ? 1.0 : (1.0 - ((double)(maior - menor) / 100.0));
        return (numerador * fatorEstabilidade) / 5.6;
    }

    public int getNivelRessonancia() {
        return (int) Math.floor(Math.sqrt(Math.max(0.0, calcularIA())));
    }

    public int getOrdem() {
        return getNivelRessonancia() / 10;
    }

    public RankAmeaca getRankAmeaca() {
        return RankAmeaca.deIA(calcularIA());
    }

    public boolean isVivo() {
        return this.hp > 0;
    }

    public int sofrerDanoFisico(int danoBruto) {
        int danoReal = Math.max(1, danoBruto - this.defesa);
        this.hp = Math.max(0, this.hp - danoReal);
        return danoReal;
    }

    public int sofrerDanoOculto(int danoBruto) {
        int danoReal = Math.max(1, danoBruto - this.defesaOculta);
        this.hp = Math.max(0, this.hp - danoReal);
        return danoReal;
    }

    public void curarHp(int cura) {
        if (cura > 0) {
            this.hp = Math.min(this.hpMax, this.hp + cura);
        }
    }

    public boolean gastarEnergia(int custo) {
        if (custo > 0 && this.energia >= custo) {
            this.energia -= custo;
            return true;
        }
        return false;
    }

    public boolean gastarMana(int custo) {
        if (custo > 0 && this.mana >= custo) {
            this.mana -= custo;
            return true;
        }
        return false;
    }

    public int getValorAtributo(Atributo a) {
        if (a == null) return 0;
        return switch (a) {
            case VIG -> this.vig;
            case FOR -> this.forca;
            case DES -> this.des;
            case MEN -> this.men;
            case ESP -> this.esp;
            case VON -> this.von;
        };
    }

    public int getAtributoEfetivo(String nomeAtributo) {
        if (nomeAtributo == null) return 0;
        Atributo a = Atributo.parse(nomeAtributo);
        int base = a != null ? getValorAtributo(a) : 0;
        for (EfeitoItem ef : condicoesAtivas) {
            base += ef.getBonusStatus().getOrDefault(nomeAtributo.toUpperCase().trim(), 0);
        }
        return base;
    }

    // --- Habilidades Inatas e Execução ---
    public void adicionarHabilidade(HabilidadeCombate habilidade) {
        if (habilidade != null && !habilidadesInatas.contains(habilidade)) {
            habilidadesInatas.add(habilidade);
        }
    }

    public List<HabilidadeCombate> getHabilidadesInatas() {
        return Collections.unmodifiableList(habilidadesInatas);
    }

    public void adicionarCondicao(EfeitoItem condicao) {
        if (condicao != null && !condicoesAtivas.contains(condicao)) {
            condicoesAtivas.add(condicao);
        }
    }

    public boolean removerCondicao(EfeitoItem condicao) {
        return condicoesAtivas.remove(condicao);
    }

    public List<EfeitoItem> getCondicoesAtivas() {
        return Collections.unmodifiableList(condicoesAtivas);
    }

    /**
     * Calcula o dano total somando o dado rolado com os atributos de dano do monstro.
     */
    public int calcularDanoTotal(HabilidadeCombate habilidade, int rolagemDado) {
        int total = Math.max(0, rolagemDado);
        if (habilidade != null) {
            for (Atributo a : habilidade.getAtributosDano()) {
                total += getValorAtributo(a);
            }
        }
        return total;
    }

    /**
     * Executa ataque inato contra uma Ficha defensora.
     */
    public int executarAtaqueContra(HabilidadeCombate habilidade, Ficha defensor, int rolagemDado) {
        if (habilidade == null || defensor == null) return 0;
        gastarEnergia(habilidade.getCustoEnergia());
        gastarMana(habilidade.getCustoMana());

        int danoTotal = calcularDanoTotal(habilidade, rolagemDado);
        if (habilidade.getTipoAcao() == HabilidadeCombate.TipoAcaoCombate.EXPLOSAO_MAGICA) {
            return defensor.sofrerDanoOculto(danoTotal);
        }
        return defensor.sofrerDanoFisico(danoTotal);
    }

    /**
     * Executa ataque inato contra outro Monstro defensor.
     */
    public int executarAtaqueContra(HabilidadeCombate habilidade, Monstro defensor, int rolagemDado) {
        if (habilidade == null || defensor == null) return 0;
        gastarEnergia(habilidade.getCustoEnergia());
        gastarMana(habilidade.getCustoMana());

        int danoTotal = calcularDanoTotal(habilidade, rolagemDado);
        if (habilidade.getTipoAcao() == HabilidadeCombate.TipoAcaoCombate.EXPLOSAO_MAGICA) {
            return defensor.sofrerDanoOculto(danoTotal);
        }
        return defensor.sofrerDanoFisico(danoTotal);
    }

    // Getters e Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEspecie() { return especie; }
    public void setEspecie(String especie) { this.especie = especie; }
    public boolean isDomesticavel() { return domesticavel; }
    public void setDomesticavel(boolean domesticavel) { this.domesticavel = domesticavel; }
    public boolean isDomado() { return domado; }
    public void setDomado(boolean domado) { this.domado = domado; }
    public Ficha getDono() { return dono; }
    public void setDono(Ficha dono) { this.dono = dono; }
    public PorteMonstro getPorte() { return porte; }
    public void setPorte(PorteMonstro porte) { this.porte = porte; }

    public int getVig() { return vig; }
    public void setVig(int vig) { this.vig = Math.max(1, vig); recalcularEstatisticas(); }
    public int getForca() { return forca; }
    public void setForca(int forca) { this.forca = Math.max(1, forca); }
    public int getDes() { return des; }
    public void setDes(int des) { this.des = Math.max(1, des); recalcularEstatisticas(); }
    public int getMen() { return men; }
    public void setMen(int men) { this.men = Math.max(1, men); }
    public int getEsp() { return esp; }
    public void setEsp(int esp) { this.esp = Math.max(1, esp); }
    public int getVon() { return von; }
    public void setVon(int von) { this.von = Math.max(1, von); recalcularEstatisticas(); }

    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = Math.max(0, Math.min(this.hpMax, hp)); }
    public int getHpMax() { return hpMax; }
    public void setHpMax(int hpMax) { this.hpMax = Math.max(1, hpMax); this.hp = Math.min(this.hp, hpMax); }

    public int getEnergia() { return energia; }
    public void setEnergia(int energia) { this.energia = Math.max(0, Math.min(this.energiaMax, energia)); }
    public int getEnergiaMax() { return energiaMax; }

    public int getMana() { return mana; }
    public void setMana(int mana) { this.mana = Math.max(0, Math.min(this.manaMax, mana)); }
    public int getManaMax() { return manaMax; }

    public int getDefesa() { return defesa; }
    public void setDefesa(int defesa) { this.defesa = defesa; }
    public int getDefesaOculta() { return defesaOculta; }
    public void setDefesaOculta(int defesaOculta) { this.defesaOculta = defesaOculta; }

    // --- Fábrica de Criaturas Canônicas para Testes e Exploração ---
    public static Monstro loboDasNeves() {
        Monstro m = new Monstro("Lobo das Neves", "Lupino Glacial", PorteMonstro.MEDIO, true, 12, 14, 15, 8, 8, 10);
        m.setHpMax(75);
        m.restaurarTudo();
        m.adicionarHabilidade(new HabilidadeCombate(
                "Mordida Perfurante", HabilidadeCombate.TipoAcaoCombate.ATAQUE_CORPORAL,
                2, 0, 1.5, "1d6", List.of(Atributo.FOR), "Mordida selvagem com chance de derrubar"
        ));
        return m;
    }

    public static Monstro grifoAltoVale() {
        Monstro m = new Monstro("Grifo do Alto Vale", "Besta Alada", PorteMonstro.GRANDE, true, 16, 20, 18, 10, 12, 14);
        m.setHpMax(140);
        m.restaurarTudo();
        m.adicionarHabilidade(new HabilidadeCombate(
                "Mergulho Aéreo Rasante", HabilidadeCombate.TipoAcaoCombate.ATAQUE_CORPORAL,
                3, 0, 12.0, "2d6", List.of(Atributo.FOR, Atributo.DES), "Ataque veloz vindo dos céus"
        ));
        return m;
    }

    public static Monstro behemothAncestral() {
        Monstro m = new Monstro("Behemoth das Cordilheiras", "Titã Quadrúpede", PorteMonstro.COLOSSAL, true, 26, 32, 8, 6, 10, 18);
        m.setHpMax(350);
        m.restaurarTudo();
        m.adicionarHabilidade(new HabilidadeCombate(
                "Piso Sísmico Titânico", HabilidadeCombate.TipoAcaoCombate.ATAQUE_CORPORAL,
                5, 0, 4.0, "3d8", List.of(Atributo.FOR, Atributo.VIG), "Onda de choque em área que racha o terreno"
        ));
        return m;
    }

    public static Monstro dragaoVermelhoSelvagem() {
        Monstro m = new Monstro("Dragão Vermelho Ancião", "Dragão Primordial", PorteMonstro.COLOSSAL, false, 30, 35, 16, 18, 20, 22);
        m.setHpMax(600);
        m.restaurarTudo();
        m.adicionarHabilidade(new HabilidadeCombate(
                "Sopro do Inferno Dracônico", HabilidadeCombate.TipoAcaoCombate.SOPRO_ELEMENTAL,
                0, 10, 15.0, "4d8", List.of(Atributo.ESP, Atributo.VON), "Cone devastador de fogo primordial"
        ));
        return m;
    }

    @Override
    public String toString() {
        return String.format("%s (%s) [Porte: %s | HP: %d/%d | Def: %d | Def.Oculta: %d | IA: %.1f | %s%s]",
                nome, especie, porte, hp, hpMax, defesa, defesaOculta, calcularIA(), getRankAmeaca().getRotulo(),
                domado ? " | Domado por " + (dono != null ? dono.getNome() : "Jogador") : (domesticavel ? " | Domesticável" : " | Selvagem"));
    }
}
