package personagem;

import economia.Carteira;
import economia.Item;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa a Ficha de Personagem no sistema RPG Thaluria.
 * Implementa as equações canônicas de Recursos, Defesas e Ciclo Metabólico de docs/Status.md.
 */
public class Ficha {
    // Pilar Físico (A Matéria)
    private int vig;   // Vigor (Base do HP e Energia)
    private int forca; // Força (Impacto bruto e tração)
    private int des;   // Destreza (Cinética, reflexos e esquiva)

    // Pilar Etéreo (O Imaterial)
    private int men;   // Mental (Processamento lógico e arcanismo)
    private int esp;   // Espírito (Condutividade de mana e magia)
    private int von;   // Vontade (Escudo da alma e sanidade)

    // Recursos Máximos Calculados
    private int hpMax;
    private int sanidadeMax;
    private int energiaMax;
    private int manaMax;

    // Recursos Atuais em Jogo
    private int hp;
    private int sanidade;
    private int energia;
    private int mana;

    // Defesas Passivas
    private int defesa;        // Defesa Física (Esquiva/Bloqueio)
    private int defesaOculta;  // Defesa Oculta (Resistência Mental)
    private int manaRegen;     // Regeneração automática de mana por turno

    // Inventário e Economia
    private Carteira carteira;
    private final List<Item> inventario = new ArrayList<>();

    // Localização Geográfica do Personagem no Mundo
    private geografia.Cidade cidadeAtual;

    public Ficha() {
        this(10, 10, 10, 10, 10, 10);
    }

    public Ficha(int vig, int forca, int des, int men, int esp, int von) {
        this.vig = Math.max(0, vig);
        this.forca = Math.max(0, forca);
        this.des = Math.max(0, des);
        this.men = Math.max(0, men);
        this.esp = Math.max(0, esp);
        this.von = Math.max(0, von);

        this.carteira = new Carteira();
        recalcularEstatisticas();
        restaurarTudo();
    }

    /**
     * Recalcula a Malha de Recursos e Defesas conforme o documento canônico de Status.
     */
    public void recalcularEstatisticas() {
        // Fórmulas Oficiais de Thaluria:
        // HP: 20 + (VIG * 3) + FOR + VON
        this.hpMax = 20 + (this.vig * 3) + this.forca + this.von;

        // SAN: 20 + (VON * 3) + MEN + VIG
        this.sanidadeMax = 20 + (this.von * 3) + this.men + this.vig;

        // Energia: 10 + (VIG * 2) + FOR + MEN
        this.energiaMax = 10 + (this.vig * 2) + this.forca + this.men;

        // Mana: 10 + (ESP * 2) + VON + VIG
        this.manaMax = 10 + (this.esp * 2) + this.von + this.vig;

        // Defesa Física: 5 + DES + ⌊MEN / 2⌋
        this.defesa = 5 + this.des + (this.men / 2);

        // Defesa Oculta: 5 + VON + ⌊VIG / 2⌋
        this.defesaOculta = 5 + this.von + (this.vig / 2);

        // Regeneração de Mana: 1 + ⌊(ESP + VON) / 5⌋
        this.manaRegen = 1 + ((this.esp + this.von) / 5);
    }

    /**
     * Restaura todos os pontos de vida, sanidade, mana e energia para seus valores máximos.
     */
    public void restaurarTudo() {
        this.hp = this.hpMax;
        this.sanidade = this.sanidadeMax;
        this.energia = this.energiaMax;
        this.mana = this.manaMax;
    }

    /**
     * Aplica dano físico reduzido pela Defesa Física.
     * @return Dano real sofrido no HP.
     */
    public int sofrerDanoFisico(int danoBruto) {
        int danoReal = Math.max(1, danoBruto - this.defesa);
        this.hp = Math.max(0, this.hp - danoReal);
        return danoReal;
    }

    /**
     * Aplica dano psíquico/oculto reduzido pela Defesa Oculta.
     * @return Dano real sofrido na Sanidade.
     */
    public int sofrerDanoOculto(int danoBruto) {
        int danoReal = Math.max(1, danoBruto - this.defesaOculta);
        this.sanidade = Math.max(0, this.sanidade - danoReal);
        return danoReal;
    }

    /**
     * Regeneração de mana por turno com custo biológico de energia:
     * Para cada 2 pontos de Mana gerados, consome 1 ponto de Energia.
     */
    public int regenerarManaPorTurno(boolean permitirCustoBiologico) {
        if (!permitirCustoBiologico || this.energia <= 0) {
            return 0;
        }

        int manaParaGerar = Math.min(this.manaRegen, this.manaMax - this.mana);
        if (manaParaGerar <= 0) return 0;

        int custoEnergia = Math.max(1, manaParaGerar / 2);
        int energiaGasta = Math.min(custoEnergia, this.energia);

        int manaRealGerada = Math.min(manaParaGerar, energiaGasta * 2);
        this.mana = Math.min(this.manaMax, this.mana + manaRealGerada);
        this.energia = Math.max(0, this.energia - energiaGasta);

        return manaRealGerada;
    }

    public boolean gastarMana(int custo) {
        if (custo > 0 && this.mana >= custo) {
            this.mana -= custo;
            return true;
        }
        return false;
    }

    public boolean gastarEnergia(int custo) {
        if (custo > 0 && this.energia >= custo) {
            this.energia -= custo;
            return true;
        }
        return false;
    }

    public void curarHp(int cura) {
        if (cura > 0) this.hp = Math.min(this.hpMax, this.hp + cura);
    }

    public void curarSanidade(int cura) {
        if (cura > 0) this.sanidade = Math.min(this.sanidadeMax, this.sanidade + cura);
    }

    public void adicionarItem(Item item) {
        if (item != null) this.inventario.add(item);
    }

    public boolean removerItem(Item item) {
        return this.inventario.remove(item);
    }

    // Graus (Tiers: a cada 10 pontos exatos = 1 Grau)
    public int getGrauVig() { return this.vig / 10; }
    public int getGrauFor() { return this.forca / 10; }
    public int getGrauDes() { return this.des / 10; }
    public int getGrauMen() { return this.men / 10; }
    public int getGrauEsp() { return this.esp / 10; }
    public int getGrauVon() { return this.von / 10; }

    // Getters e Setters de Atributos com recálculo automático
    public int getVig() { return vig; }
    public void setVig(int vig) { this.vig = Math.max(0, vig); recalcularEstatisticas(); }

    public int getForca() { return forca; }
    public void setForca(int forca) { this.forca = Math.max(0, forca); recalcularEstatisticas(); }

    public int getDes() { return des; }
    public void setDes(int des) { this.des = Math.max(0, des); recalcularEstatisticas(); }

    public int getMen() { return men; }
    public void setMen(int men) { this.men = Math.max(0, men); recalcularEstatisticas(); }

    public int getEsp() { return esp; }
    public void setEsp(int esp) { this.esp = Math.max(0, esp); recalcularEstatisticas(); }

    public int getVon() { return von; }
    public void setVon(int von) { this.von = Math.max(0, von); recalcularEstatisticas(); }

    // Getters de Recursos
    public int getHp() { return hp; }
    public int getHpMax() { return hpMax; }
    public int getSanidade() { return sanidade; }
    public int getSanidadeMax() { return sanidadeMax; }
    public int getEnergia() { return energia; }
    public int getEnergiaMax() { return energiaMax; }
    public int getMana() { return mana; }
    public int getManaMax() { return manaMax; }
    public int getDefesa() { return defesa; }
    public int getDefesaOculta() { return defesaOculta; }
    public int getManaRegen() { return manaRegen; }

    public Carteira getCarteira() { return carteira; }
    public void setCarteira(Carteira carteira) { if (carteira != null) this.carteira = carteira; }

    public List<Item> getInventario() { return Collections.unmodifiableList(inventario); }

    public geografia.Cidade getCidadeAtual() { return cidadeAtual; }
    public void setCidadeAtual(geografia.Cidade cidadeAtual) { this.cidadeAtual = cidadeAtual; }

    @Override
    public String toString() {
        return String.format("Ficha [HP: %d/%d | SAN: %d/%d | Energia: %d/%d | Mana: %d/%d | Def.Fís: %d | Def.Oculta: %d]\n" +
            "Atributos [VIG: %d (G%d) | FOR: %d (G%d) | DES: %d (G%d) | MEN: %d (G%d) | ESP: %d (G%d) | VON: %d (G%d)]",
            hp, hpMax, sanidade, sanidadeMax, energia, energiaMax, mana, manaMax, defesa, defesaOculta,
            vig, getGrauVig(), forca, getGrauFor(), des, getGrauDes(), men, getGrauMen(), esp, getGrauEsp(), von, getGrauVon());
    }
}


