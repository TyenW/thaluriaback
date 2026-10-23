package personagem;

import economia.*;
import java.util.*;

/**
 * Representa a Ficha de Personagem no sistema RPG Thaluria.
 * Implementa as equações canônicas de Recursos, Defesas, Ciclo Metabólico de docs/Status.md
 * e a mecânica de Sobrescrita Racial de docs/raças/ e docs/Compêndio de Raças por Reino.md.
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

    // Raça e Constantes Raciais (Camada v7)
    private Raca raca;
    private ParametrosFormulaRacial parametrosFormula;

    // Progressão por Pontos de Essência (PE) e Perícias
    private int pontosEssencia = 0;
    private final Map<Pericia, Integer> grausPericia = new EnumMap<>(Pericia.class);
    private final Map<Pericia, Integer> maestriasPericia = new EnumMap<>(Pericia.class);

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
        this(vig, forca, des, men, esp, von, Raca.HUMANO);
    }

    public Ficha(int vig, int forca, int des, int men, int esp, int von, Raca raca) {
        this.vig = Math.max(0, vig);
        this.forca = Math.max(0, forca);
        this.des = Math.max(0, des);
        this.men = Math.max(0, men);
        this.esp = Math.max(0, esp);
        this.von = Math.max(0, von);

        this.raca = raca != null ? raca : Raca.HUMANO;
        this.parametrosFormula = this.raca.getParametros() != null ? this.raca.getParametros() : ParametrosFormulaRacial.padrao();

        this.carteira = new Carteira();
        recalcularEstatisticas();
        restaurarTudo();
    }

    public Map<Atributo, Integer> getMapaAtributosBase() {
        Map<Atributo, Integer> m = new EnumMap<>(Atributo.class);
        m.put(Atributo.VIG, this.vig);
        m.put(Atributo.FOR, this.forca);
        m.put(Atributo.DES, this.des);
        m.put(Atributo.MEN, this.men);
        m.put(Atributo.ESP, this.esp);
        m.put(Atributo.VON, this.von);
        return m;
    }

    public Map<Atributo, Integer> getMapaGraus() {
        Map<Atributo, Integer> m = new EnumMap<>(Atributo.class);
        m.put(Atributo.VIG, getGrauVig());
        m.put(Atributo.FOR, getGrauFor());
        m.put(Atributo.DES, getGrauDes());
        m.put(Atributo.MEN, getGrauMen());
        m.put(Atributo.ESP, getGrauEsp());
        m.put(Atributo.VON, getGrauVon());
        return m;
    }

    /**
     * Recalcula a Malha de Recursos e Defesas conforme o documento canônico de Status
     * aplicando as constantes raciais ativas em ParametrosFormulaRacial.
     */
    public void recalcularEstatisticas() {
        if (this.parametrosFormula == null) {
            this.parametrosFormula = ParametrosFormulaRacial.padrao();
        }

        Map<Atributo, Integer> mapa = getMapaAtributosBase();

        // HP, SAN, Energia e Mana
        this.hpMax = this.parametrosFormula.getHp().avaliar(mapa);
        this.sanidadeMax = this.parametrosFormula.getSanidade().avaliar(mapa);
        this.energiaMax = this.parametrosFormula.getEnergia().avaliar(mapa);
        this.manaMax = this.parametrosFormula.getMana().avaliar(mapa);

        // Defesa Física e Defesa Oculta
        this.defesa = this.parametrosFormula.getDefFisica().avaliar(mapa);
        this.defesaOculta = this.parametrosFormula.getDefOculta().avaliar(mapa);

        // Regeneração de Mana
        this.manaRegen = this.parametrosFormula.getRegenManaBase() +
                ((this.esp + this.von) / this.parametrosFormula.getRegenManaDivisor());
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
     * Queima 1 ponto de Energia para cada X pontos de Mana gerados (padrão: 2; Genasi Gelo/Água: 4).
     */
    public int regenerarManaPorTurno(boolean permitirCustoBiologico) {
        if (!permitirCustoBiologico || this.energia <= 0) {
            return 0;
        }

        int manaParaGerar = Math.min(this.manaRegen, this.manaMax - this.mana);
        if (manaParaGerar <= 0) return 0;

        int manaPorEnergia = this.parametrosFormula.getManaPorEnergia();
        int custoEnergia = Math.max(1, (int) Math.ceil((double) manaParaGerar / manaPorEnergia));
        int energiaGasta = Math.min(custoEnergia, this.energia);

        int manaRealGerada = Math.min(manaParaGerar, energiaGasta * manaPorEnergia);
        this.mana = Math.min(this.manaMax, this.mana + manaRealGerada);
        this.energia = Math.max(0, this.energia - energiaGasta);

        return manaRealGerada;
    }

    /**
     * Ciclo de Descanso com Horas (H in [1, 8]) e Conforto (Q in [0.25, 1.50]).
     */
    public void descansar(int horas, ConfortoDescanso conforto) {
        if (conforto == null) conforto = ConfortoDescanso.PADRAO;
        int h = Math.min(8, Math.max(1, horas));
        int qCentesimos = this.parametrosFormula.getQCentesimos(conforto);
        double q = qCentesimos / 100.0;

        // 1. Energia (H >= 1h - se H == 8 e Q >= 1.0, 100%)
        if (h >= 1) {
            if (h == 8 && qCentesimos >= 100) {
                this.energia = this.energiaMax;
            } else {
                int base = this.parametrosFormula.getEnergiaDescansoBase();
                int multVig = this.parametrosFormula.getEnergiaDescansoMultVig();
                int recEnergia = (int) Math.floor((base + (this.vig * multVig)) * h * q);
                this.energia = Math.min(this.energiaMax, this.energia + recEnergia);
            }
        }

        // 2. Mana (H >= 2h)
        if (h >= 2) {
            int recMana = (int) Math.floor(this.manaMax * (h / 8.0) * q);
            this.mana = Math.min(this.manaMax, this.mana + recMana);
        }

        // 3. HP (H >= minHoras, padrão 4h)
        if (h >= this.parametrosFormula.getHpDescansoMinHoras()) {
            int recHp = (int) Math.floor(this.hpMax * (h / (double) this.parametrosFormula.getHpDescansoDivisor()) * q);
            this.hp = Math.min(this.hpMax, this.hp + recHp);
        }

        // 4. SAN (H >= minHoras, padrão 4h; Drow 2h)
        if (h >= this.parametrosFormula.getSanDescansoMinHoras()) {
            int recSan = (int) Math.floor(this.sanidadeMax * (h / (double) this.parametrosFormula.getSanDescansoDivisor()) * q);
            this.sanidade = Math.min(this.sanidadeMax, this.sanidade + recSan);
        }
    }

    /**
     * Turnos até morte definitiva se HP == 0 (Padrão 3 + ⌊Grau_VIG / 2⌋; Anões 5 + Grau_VIG).
     */
    public int getTurnosMorte() {
        return this.parametrosFormula.getTurnosMorte().avaliar(getMapaGraus());
    }

    /**
     * Forçar técnica física com Energia 0 desconta no HP (dividido pela constante racial).
     */
    public int forcarHabilidadeFisicaSemEnergia(int custoEnergiaFaltante) {
        if (custoEnergiaFaltante <= 0) return 0;
        int danoHp = Math.max(1, custoEnergiaFaltante / this.parametrosFormula.getDivisorHpTecnicaFisica());
        this.hp = Math.max(0, this.hp - danoHp);
        return danoHp;
    }

    /**
     * Forçar magia com Mana 0 desconta na SAN (multiplicado pela constante racial).
     */
    public int forcarMagiaSemMana(int custoManaFaltante) {
        if (custoManaFaltante <= 0) return 0;
        int danoSan = custoManaFaltante * this.parametrosFormula.getMultiplicadorSanBurnout();
        this.sanidade = Math.max(0, this.sanidade - danoSan);
        return danoSan;
    }

    // --- Equação Unificada de Poder (PA para Jogador / IA para Monstro) ---
    public double calcularPA() {
        double somaQuadrados = (Math.pow(this.forca, 2) + Math.pow(this.des, 2) +
                Math.pow(this.esp, 2) + Math.pow(this.men, 2)) / 2.5;
        double produtoCruzado = (this.vig + this.von) * (this.des + this.men);
        double numerador = somaQuadrados + produtoCruzado;

        int maior = Math.max(this.forca, Math.max(this.des, Math.max(this.esp, Math.max(this.men, Math.max(this.vig, this.von)))));
        int menor = Math.min(this.forca, Math.min(this.des, Math.min(this.esp, Math.min(this.men, Math.min(this.vig, this.von)))));

        double fatorEstabilidade = (maior >= 101) ? 1.0 : (1.0 - ((double)(maior - menor) / 100.0));
        return (numerador * fatorEstabilidade) / 5.6;
    }

    public double calcularIA() {
        return calcularPA();
    }

    public int getNivelRessonancia() {
        return (int) Math.floor(Math.sqrt(Math.max(0.0, calcularPA())));
    }

    public int getOrdem() {
        return getNivelRessonancia() / 10;
    }

    public PatamarAscensao getPatamarAscensao() {
        return PatamarAscensao.dePA(calcularPA());
    }

    public RankAmeaca getRankAmeaca() {
        return RankAmeaca.deIA(calcularIA());
    }

    public boolean temSintoniaDeGrupoCom(Ficha outro) {
        if (outro == null) return false;
        return Math.abs(this.getNivelRessonancia() - outro.getNivelRessonancia()) <= 5;
    }

    // --- Progressão por Pontos de Essência (PE) e Trava dos 50 Pontos ---
    public int getPontosEssencia() { return pontosEssencia; }
    public void adicionarPontosEssencia(int pe) { if (pe > 0) this.pontosEssencia += pe; }
    public void setPontosEssencia(int pe) { this.pontosEssencia = Math.max(0, pe); }

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

    public void setValorAtributo(Atributo a, int valor) {
        if (a == null) return;
        switch (a) {
            case VIG -> setVig(valor);
            case FOR -> setForca(valor);
            case DES -> setDes(valor);
            case MEN -> setMen(valor);
            case ESP -> setEsp(valor);
            case VON -> setVon(valor);
        }
    }

    public int calcularCustoPEParaEvoluir(Atributo a) {
        int atual = getValorAtributo(a);
        if (atual % 10 == 9) {
            return 2 * atual; // Pedágio de Quebra de Grau (9->10 custa 18, 19->20 custa 38)
        }
        return (atual / 10) + 1; // Grau Atual + 1
    }

    public boolean podeEvoluirAtributo(Atributo a) {
        if (a == null) return false;
        int atual = getValorAtributo(a);
        int novoValor = atual + 1;

        // Se está no teto mortal (<= 100), checa a Trava dos 50 Pontos
        if (novoValor <= 100) {
            Map<Atributo, Integer> mapa = getMapaAtributosBase();
            mapa.put(a, novoValor);
            int maior = Collections.max(mapa.values());
            int menor = Collections.min(mapa.values());
            if (maior - menor > 50) {
                return false; // Trava dos 50 pontos violada
            }
        }
        return true;
    }

    public boolean evoluirAtributoComPE(Atributo a) {
        if (!podeEvoluirAtributo(a)) return false;
        int custo = calcularCustoPEParaEvoluir(a);
        if (this.pontosEssencia < custo) return false;

        this.pontosEssencia -= custo;
        setValorAtributo(a, getValorAtributo(a) + 1);
        return true;
    }

    // --- Sistema de Perícias Canônicas ---
    public int getGrauPericia(Pericia p) {
        return grausPericia.getOrDefault(p, 0);
    }

    public int getMaestriaPericia(Pericia p) {
        return maestriasPericia.getOrDefault(p, 0);
    }

    public boolean setPericia(Pericia p, int grau, int maestria) {
        if (p == null) return false;
        int grauAtributo = getValorAtributo(p.getAtributoBase()) / 10;
        if (!p.validarTravaAtributo(grau, grauAtributo)) return false;
        if (!Pericia.validarMaestria(maestria)) return false;

        grausPericia.put(p, grau);
        maestriasPericia.put(p, maestria);
        return true;
    }

    public int rolarPericia(Pericia p, Random r) {
        if (p == null) return 0;
        return p.rolar(getGrauPericia(p), getMaestriaPericia(p), r);
    }

    public int rolarPericiaDeterministica(Pericia p, int... dados) {
        if (p == null) return 0;
        return Pericia.rolarDeterministico(getGrauPericia(p), getMaestriaPericia(p), dados);
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

        for (FichaArmadura.SlotArmadura s : slots) {
            if (armadurasEquipadas.containsKey(s)) {
                return false;
            }
        }

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
            return false;
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
            return false;
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
                return false;
            }
        }

        if (itemOuEscudo.getFichaEscudo() != null && itemOuEscudo.getFichaEscudo().getEmpunhadura() == FichaArmaBranca.Empunhadura.DUAS_MAOS) {
            if (this.maoPrincipal != null) {
                return false;
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

    public double getMultiplicadorDanoTotal() {
        double mult = 1.0;
        for (EfeitoItem ef : efeitosAtivos) {
            mult *= ef.getMultiplicadorDano();
        }
        return mult;
    }

    /**
     * Consulta qualquer status somando Base da Ficha + Acessórios + Passivas/Efeitos Ativos.
     * Suporta aliases legados v4 (CONSTITUICAO, INTELIGENCIA, SABEDORIA).
     */
    public int getAtributoEfetivo(String nomeAtributo) {
        if (nomeAtributo == null) return 0;
        Atributo a = Atributo.parse(nomeAtributo);
        int valor = 0;
        if (a != null) {
            valor = getValorAtributo(a);
        }

        // Soma bônus dos acessórios equipados
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

    // Raça e Parâmetros
    public Raca getRaca() { return raca; }
    public void setRaca(Raca raca) {
        this.raca = raca != null ? raca : Raca.HUMANO;
        this.parametrosFormula = this.raca.getParametros() != null ? this.raca.getParametros() : ParametrosFormulaRacial.padrao();
        recalcularEstatisticas();
    }

    public ParametrosFormulaRacial getParametrosFormula() { return parametrosFormula; }
    public void setParametrosFormula(ParametrosFormulaRacial parametrosFormula) {
        this.parametrosFormula = parametrosFormula != null ? parametrosFormula : ParametrosFormulaRacial.padrao();
        recalcularEstatisticas();
    }

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
    public void setHp(int hp) { this.hp = Math.max(0, Math.min(this.hpMax, hp)); }
    public int getHpMax() { return hpMax; }

    public int getSanidade() { return sanidade; }
    public void setSanidade(int sanidade) { this.sanidade = Math.max(0, Math.min(this.sanidadeMax, sanidade)); }
    public int getSanidadeMax() { return sanidadeMax; }

    public int getEnergia() { return energia; }
    public void setEnergia(int energia) { this.energia = Math.max(0, Math.min(this.energiaMax, energia)); }
    public int getEnergiaMax() { return energiaMax; }

    public int getMana() { return mana; }
    public void setMana(int mana) { this.mana = Math.max(0, Math.min(this.manaMax, mana)); }
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
        return String.format("Ficha [%s | HP: %d/%d | SAN: %d/%d | Energia: %d/%d | Mana: %d/%d | Def.Fís: %d | Def.Oculta: %d]\n" +
                        "Atributos [VIG: %d (G%d) | FOR: %d (G%d) | DES: %d (G%d) | MEN: %d (G%d) | ESP: %d (G%d) | VON: %d (G%d)] | PA: %.1f (NR: %d)",
                raca != null ? raca.getNome() : "Sem Raça",
                hp, hpMax, sanidade, sanidadeMax, energia, energiaMax, mana, manaMax, defesa, defesaOculta,
                vig, getGrauVig(), forca, getGrauFor(), des, getGrauDes(), men, getGrauMen(), esp, getGrauEsp(), von, getGrauVon(),
                calcularPA(), getNivelRessonancia());
    }
}
