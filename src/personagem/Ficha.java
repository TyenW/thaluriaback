package personagem;

import economia.*;
import java.util.*;

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

    // Sistema de Equipamento Tático v4 e Efeitos v4.1
    private final Map<FichaArmadura.SlotArmadura, Item> armadurasEquipadas = new EnumMap<>(FichaArmadura.SlotArmadura.class);
    private final Map<FichaAcessorio.SlotAcessorio, Item> acessoriosEquipados = new EnumMap<>(FichaAcessorio.SlotAcessorio.class);
    private Item maoPrincipal;
    private Item maoSecundaria;
    private final List<EfeitoItem> efeitosAtivos = new ArrayList<>();
    private final Map<EfeitoItem, Integer> turnosRestantesEfeito = new LinkedHashMap<>();

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

    public void sofrerDano(int dano) {
        if (dano > 0) this.hp = Math.max(0, this.hp - dano);
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

    // --- Gestão de Capacidade da Mochila e Peso ---
    public double getCapacidadeCargaKg() {
        return 50.0 + (this.forca * 3.0) + (this.vig * 2.0);
    }

    public double getCapacidadeMochilaSlots() {
        return 50.0;
    }

    public double getPesoTotalInventario() {
        double total = 0.0;
        for (Item it : inventario) {
            total += it.getPesoTotal();
        }
        return total;
    }

    public double getVolumeTotalInventario() {
        double total = 0.0;
        for (Item it : inventario) {
            total += it.getEspacoTotal();
        }
        return total;
    }

    public boolean adicionarAoInventarioComValidacao(Item item) {
        if (item == null) return false;
        if (item.getTamanho() == TamanhoItem.COLOSSAL) return false;
        if (getPesoTotalInventario() + item.getPesoTotal() > getCapacidadeCargaKg()) return false;
        if (getVolumeTotalInventario() + item.getEspacoTotal() > getCapacidadeMochilaSlots()) return false;
        this.inventario.add(item);
        return true;
    }

    // --- Sistema de Equipamento de Armadura (13 Slots Multi-Ocupação) ---
    public boolean equiparArmadura(Item armadura) {
        if (armadura == null || armadura.getFichaArmadura() == null) return false;
        Set<FichaArmadura.SlotArmadura> slots = armadura.getFichaArmadura().getSlotsOcupados();
        if (slots.isEmpty()) return false;

        // Valida se todos os slots exigidos pela peça estão livres
        for (FichaArmadura.SlotArmadura s : slots) {
            if (armadurasEquipadas.containsKey(s)) {
                return false; // Conflito: ao menos um slot já está ocupado
            }
        }

        // Ocupa todos os slots
        for (FichaArmadura.SlotArmadura s : slots) {
            armadurasEquipadas.put(s, armadura);
        }
        removerItem(armadura);
        return true;
    }

    public Item desequiparArmadura(FichaArmadura.SlotArmadura slot) {
        Item item = armadurasEquipadas.get(slot);
        if (item != null) {
            armadurasEquipadas.values().removeIf(it -> it.equals(item));
            adicionarItem(item);
            return item;
        }
        return null;
    }

    public int calcularReducaoDanoTotalArmaduras() {
        Set<Item> unicos = new HashSet<>(armadurasEquipadas.values());
        int rdTotal = 0;
        for (Item it : unicos) {
            if (it.getFichaArmadura() != null) {
                rdTotal += it.getFichaArmadura().getReducaoDeDanoEfetiva();
            }
        }
        return rdTotal;
    }

    // --- Sistema de Equipamento de Acessórios (5 Slots Exclusivos) ---
    public boolean equiparAcessorio(Item acessorio) {
        if (acessorio == null || acessorio.getFichaAcessorio() == null) return false;
        FichaAcessorio.SlotAcessorio slot = acessorio.getFichaAcessorio().getSlotExclusivo();
        if (acessoriosEquipados.containsKey(slot)) {
            return false; // Slot de acessório já ocupado
        }
        acessoriosEquipados.put(slot, acessorio);
        removerItem(acessorio);
        return true;
    }

    public Item desequiparAcessorio(FichaAcessorio.SlotAcessorio slot) {
        Item it = acessoriosEquipados.remove(slot);
        if (it != null) {
            adicionarItem(it);
            return it;
        }
        return null;
    }

    // --- Sistema de Empunhadura de Combate (Armas e Escudos) ---
    public boolean equiparMaoPrincipal(Item arma) {
        if (arma == null) return false;
        boolean exigeDuasMaos = false;
        if (arma.getFichaArmaBranca() != null) {
            exigeDuasMaos = (arma.getFichaArmaBranca().getEmpunhadura() == FichaArmaBranca.Empunhadura.DUAS_MAOS);
        } else if (arma.getFichaArmaLongoAlcance() != null) {
            exigeDuasMaos = (arma.getFichaArmaLongoAlcance().getEmpunhadura() == FichaArmaBranca.Empunhadura.DUAS_MAOS);
        }

        if (exigeDuasMaos && this.maoSecundaria != null) {
            return false; // Mão secundária ocupada impede arma de duas mãos
        }

        if (this.maoPrincipal != null) {
            desequiparMaoPrincipal();
        }

        this.maoPrincipal = arma;
        removerItem(arma);
        return true;
    }

    public boolean equiparMaoSecundaria(Item itemOuEscudo) {
        if (itemOuEscudo == null) return false;
        if (this.maoPrincipal != null) {
            boolean principalDuasMaos = false;
            if (this.maoPrincipal.getFichaArmaBranca() != null) {
                principalDuasMaos = (this.maoPrincipal.getFichaArmaBranca().getEmpunhadura() == FichaArmaBranca.Empunhadura.DUAS_MAOS);
            } else if (this.maoPrincipal.getFichaArmaLongoAlcance() != null) {
                principalDuasMaos = (this.maoPrincipal.getFichaArmaLongoAlcance().getEmpunhadura() == FichaArmaBranca.Empunhadura.DUAS_MAOS);
            }
            if (principalDuasMaos) {
                return false; // Arma de duas mãos na mão principal impede empunhar mão secundária
            }
        }

        if (itemOuEscudo.getFichaEscudo() != null && itemOuEscudo.getFichaEscudo().getEmpunhadura() == FichaArmaBranca.Empunhadura.DUAS_MAOS) {
            if (this.maoPrincipal != null) {
                return false; // Escudo torre de duas mãos exige que ambas as mãos estejam livres
            }
        }

        if (this.maoSecundaria != null) {
            desequiparMaoSecundaria();
        }

        this.maoSecundaria = itemOuEscudo;
        removerItem(itemOuEscudo);
        return true;
    }

    public Item desequiparMaoPrincipal() {
        Item it = this.maoPrincipal;
        if (it != null) {
            this.maoPrincipal = null;
            adicionarItem(it);
        }
        return it;
    }

    public Item desequiparMaoSecundaria() {
        Item it = this.maoSecundaria;
        if (it != null) {
            this.maoSecundaria = null;
            adicionarItem(it);
        }
        return it;
    }

    public Map<FichaArmadura.SlotArmadura, Item> getArmadurasEquipadas() {
        return Collections.unmodifiableMap(armadurasEquipadas);
    }

    public Map<FichaAcessorio.SlotAcessorio, Item> getAcessoriosEquipados() {
        return Collections.unmodifiableMap(acessoriosEquipados);
    }

    public Item getMaoPrincipal() { return maoPrincipal; }
    public Item getMaoSecundaria() { return maoSecundaria; }

    // --- Execução de Passivas e Efeitos Ativos (Item v4.1) ---
    /**
     * Avalia todas as passivas dos acessórios/equipamentos equipados para um determinado gatilho.
     * Se a condição bater, ativa a passiva e aplica o EfeitoItem na Ficha!
     */
    public List<EfeitoItem> verificarEGatilharPassivas(PassivaItem.TipoCondicaoPassiva evento) {
        List<EfeitoItem> recemAtivados = new ArrayList<>();
        double pctVida = this.hpMax > 0 ? ((double) this.hp / this.hpMax) : 1.0;

        for (Item acessorio : acessoriosEquipados.values()) {
            if (acessorio.getFichaAcessorio() == null) continue;

            int reducaoCd = (acessorio.isCraftado() && acessorio.getQualidadeCraft() != null)
                    ? acessorio.getQualidadeCraft().getReducaoCooldownPassivaTurnos() : 0;

            for (PassivaItem passiva : acessorio.getFichaAcessorio().getPassivas()) {
                EfeitoItem efeitoDisparado = passiva.tentarAtivar(evento, pctVida, reducaoCd);
                if (efeitoDisparado != null) {
                    aplicarEfeitoNaFicha(efeitoDisparado);
                    recemAtivados.add(efeitoDisparado);
                }
            }
        }
        return recemAtivados;
    }

    public void aplicarEfeitoNaFicha(EfeitoItem efeito) {
        if (efeito == null) return;
        if (!efeitosAtivos.contains(efeito)) {
            efeitosAtivos.add(efeito);
        }
        turnosRestantesEfeito.put(efeito, efeito.getDuracaoTurnos());

        if (efeito.getCuraInstantaneaHp() > 0) {
            this.hp = Math.min(this.hpMax, this.hp + efeito.getCuraInstantaneaHp());
        }
    }

    public void removerEfeitoDaFicha(EfeitoItem efeito) {
        if (efeito != null) {
            efeitosAtivos.remove(efeito);
            turnosRestantesEfeito.remove(efeito);
        }
    }

    public void avancarTurnoEfeitos() {
        Iterator<Map.Entry<EfeitoItem, Integer>> it = turnosRestantesEfeito.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<EfeitoItem, Integer> entry = it.next();
            int dur = entry.getValue();
            if (dur > 0) {
                dur--;
                if (dur == 0) {
                    efeitosAtivos.remove(entry.getKey());
                    it.remove();
                } else {
                    entry.setValue(dur);
                }
            }
        }
    }

    /**
     * Retorna o Multiplicador Total de Dano do personagem (Ex: 2.0 se uma passiva dobrou o dano!).
     */
    public double getMultiplicadorDanoTotal() {
        double mult = 1.0;
        for (EfeitoItem ef : efeitosAtivos) {
            mult *= ef.getMultiplicadorDano();
        }
        return mult;
    }

    /**
     * Consulta qualquer status ("FORCA", "DESTREZA", "VIGOR", "MENTE", "ESPIRITO", "VONTADE") somando:
     * Base da Ficha + Bônus de Acessórios (escalados por QualidadeCraft) + Efeitos de Passivas Ativas!
     */
    public int getAtributoEfetivo(String nomeAtributo) {
        if (nomeAtributo == null) return 0;
        int valor = switch (nomeAtributo.toUpperCase().trim()) {
            case "FORCA", "FOR" -> this.forca;
            case "DESTREZA", "DES" -> this.des;
            case "VIGOR", "VIG" -> this.vig;
            case "MENTE", "MEN", "INTELIGENCIA" -> this.men;
            case "ESPIRITO", "ESP" -> this.esp;
            case "VONTADE", "VON" -> this.von;
            default -> 0;
        };

        // Soma bônus dos acessórios equipados (escalados se o acessório tiver QualidadeCraft)
        for (Item acc : acessoriosEquipados.values()) {
            if (acc.getFichaAcessorio() != null) {
                int bonusBase = acc.getFichaAcessorio().getBonusAtributos().getOrDefault(nomeAtributo.toUpperCase().trim(), 0);
                double multQualidade = (acc.isCraftado() && acc.getQualidadeCraft() != null)
                        ? acc.getQualidadeCraft().getMultiplicadorAtributosEDano() : 1.0;
                valor += (int) Math.round(bonusBase * multQualidade);
            }
        }

        // Soma bônus dos EfeitoItem disparados por passivas ou consumíveis ativos
        for (EfeitoItem ef : efeitosAtivos) {
            valor += ef.getBonusStatus().getOrDefault(nomeAtributo.toUpperCase().trim(), 0);
        }

        return valor;
    }

    public List<EfeitoItem> getEfeitosAtivos() {
        return Collections.unmodifiableList(efeitosAtivos);
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


