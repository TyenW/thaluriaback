package personagem;

import java.util.*;

/**
 * Representa Técnicas e Habilidades de Combate Inatas ou Marciais (Ficha de Cadastro de Raça).
 * Respeita a regra canônica: o dado de impacto é específico da fonte (1d4 desarmado,
 * dado da arma equipada, ou dado fixo da habilidade inata como 2d6 ou 1d8), somando os atributos pertinentes.
 */
public class HabilidadeCombate {

    public enum TipoAcaoCombate {
        ATAQUE_CORPORAL("Ataque Corporal"),
        PROJETIL("Disparo / Projétil"),
        SOPRO_ELEMENTAL("Sopro Elemental em Área"),
        EXPLOSAO_MAGICA("Explosão Mágica"),
        GOLPE_TATICO("Golpe Tático / Evasivo");

        private final String descricao;
        TipoAcaoCombate(String descricao) { this.descricao = descricao; }
        public String getDescricao() { return descricao; }
    }

    private final String nome;
    private final TipoAcaoCombate tipoAcao;
    private final int custoEnergia;
    private final int custoMana;
    private final double alcanceMetros;
    private final String dadoBase; // ex: "1d4", "2d6", "1d8", ou null para "arma equipada"
    private final List<Atributo> atributosDano;
    private final String descricaoEfeito;
    private Integer afinidadeDraconica1d8; // 1: Fogo, 2: Gelo, 3: Raio, 4: Ácido, 5: Vento, 6: Veneno, 7: Luz, 8: Sombra
    private LinhagemDraconica linhagemDraconica;

    public HabilidadeCombate(String nome, TipoAcaoCombate tipoAcao, int custoEnergia, int custoMana,
                            double alcanceMetros, String dadoBase, List<Atributo> atributosDano,
                            String descricaoEfeito) {
        this(nome, tipoAcao, custoEnergia, custoMana, alcanceMetros, dadoBase, atributosDano, descricaoEfeito, null);
    }

    public HabilidadeCombate(String nome, TipoAcaoCombate tipoAcao, int custoEnergia, int custoMana,
                            double alcanceMetros, String dadoBase, List<Atributo> atributosDano,
                            String descricaoEfeito, Integer afinidadeDraconica1d8) {
        this.nome = nome;
        this.tipoAcao = tipoAcao;
        this.custoEnergia = Math.max(0, custoEnergia);
        this.custoMana = Math.max(0, custoMana);
        this.alcanceMetros = alcanceMetros;
        this.dadoBase = dadoBase;
        this.atributosDano = atributosDano != null ? new ArrayList<>(atributosDano) : Collections.emptyList();
        this.descricaoEfeito = descricaoEfeito;
        this.afinidadeDraconica1d8 = afinidadeDraconica1d8;
        this.linhagemDraconica = afinidadeDraconica1d8 != null ? LinhagemDraconica.porNumero(afinidadeDraconica1d8) : LinhagemDraconica.FOGO;
    }

    public String getNome() { return nome; }
    public TipoAcaoCombate getTipoAcao() { return tipoAcao; }
    public int getCustoEnergia() { return custoEnergia; }
    public int getCustoMana() { return custoMana; }
    public double getAlcanceMetros() { return alcanceMetros; }
    public String getDadoBase() { return dadoBase; }
    public List<Atributo> getAtributosDano() { return Collections.unmodifiableList(atributosDano); }
    public String getDescricaoEfeito() { return descricaoEfeito; }
    public Integer getAfinidadeDraconica1d8() { return afinidadeDraconica1d8; }
    public void setAfinidadeDraconica1d8(Integer afinidade) {
        this.afinidadeDraconica1d8 = afinidade;
        if (afinidade != null) {
            this.linhagemDraconica = LinhagemDraconica.porNumero(afinidade);
        }
    }

    public LinhagemDraconica getLinhagemDraconica() {
        if (linhagemDraconica == null && afinidadeDraconica1d8 != null) {
            linhagemDraconica = LinhagemDraconica.porNumero(afinidadeDraconica1d8);
        }
        return linhagemDraconica != null ? linhagemDraconica : LinhagemDraconica.FOGO;
    }

    public void setLinhagemDraconica(LinhagemDraconica linhagem) {
        this.linhagemDraconica = linhagem;
        if (linhagem != null) {
            this.afinidadeDraconica1d8 = linhagem.getNumero1d8();
        }
    }

    /**
     * Calcula o dano total da habilidade somando o dado rolado com os atributos da Ficha.
     */
    public int calcularDanoTotal(Ficha atacante, int rolagemDado) {
        int total = Math.max(0, rolagemDado);
        if (atacante != null) {
            for (Atributo a : atributosDano) {
                total += atacante.getAtributoEfetivo(a.name());
            }
        }
        return total;
    }

    /**
     * Executa a técnica deduzindo os custos e aplicando o dano no alvo.
     * Retorna o dano real infligido.
     */
    public int executar(Ficha atacante, Ficha alvo, int rolagemDado) {
        if (atacante == null) return 0;

        // Deduz energia
        if (custoEnergia > 0) {
            if (atacante.getEnergia() >= custoEnergia) {
                atacante.gastarEnergia(custoEnergia);
            } else {
                int faltante = custoEnergia - atacante.getEnergia();
                atacante.gastarEnergia(atacante.getEnergia());
                atacante.forcarHabilidadeFisicaSemEnergia(faltante);
            }
        }

        // Deduz mana
        if (custoMana > 0) {
            if (atacante.getMana() >= custoMana) {
                atacante.gastarMana(custoMana);
            } else {
                int faltante = custoMana - atacante.getMana();
                atacante.gastarMana(atacante.getMana());
                atacante.forcarMagiaSemMana(faltante);
            }
        }

        int danoTotal = calcularDanoTotal(atacante, rolagemDado);
        int danoReal = 0;

        if (alvo != null) {
            // Se for ataque psíquico/oculto exclusivo de Sanidade
            if (tipoAcao == TipoAcaoCombate.EXPLOSAO_MAGICA && atributosDano.contains(Atributo.VON) && !atributosDano.contains(Atributo.FOR)) {
                danoReal = alvo.sofrerDanoOculto(danoTotal);
            } else {
                danoReal = alvo.sofrerDanoFisico(danoTotal);
            }
        }

        // Efeitos colaterais inatos de raças específicas
        if ("Bote da Mandíbula Voraz".equalsIgnoreCase(this.nome)) {
            // Homens-lagarto recuperam 5 + Grau_VIG de energia
            int curaEnergia = 5 + atacante.getGrauVig();
            atacante.setEnergia(Math.min(atacante.getEnergiaMax(), atacante.getEnergia() + curaEnergia));
        } else if ("Ceifa Espectral do Vazio".equalsIgnoreCase(this.nome) || "Ceifa Espectral do Vazio (Lâmina da Alma)".equalsIgnoreCase(this.nome)) {
            // Shadar-kai roubam vitalidade curando HP
            atacante.curarHp(Math.max(1, danoReal / 2));
        }

        return danoReal;
    }

    public static class ResultadoSopro {
        private final int danoInfligido;
        private final boolean resistiuMetade;
        private final LinhagemDraconica linhagem;
        private final String condicaoAplicada;
        private final int danoSanidadeExtra;

        public ResultadoSopro(int danoInfligido, boolean resistiuMetade, LinhagemDraconica linhagem,
                              String condicaoAplicada, int danoSanidadeExtra) {
            this.danoInfligido = danoInfligido;
            this.resistiuMetade = resistiuMetade;
            this.linhagem = linhagem;
            this.condicaoAplicada = condicaoAplicada;
            this.danoSanidadeExtra = danoSanidadeExtra;
        }

        public int getDanoInfligido() { return danoInfligido; }
        public boolean isResistiuMetade() { return resistiuMetade; }
        public LinhagemDraconica getLinhagem() { return linhagem; }
        public String getCondicaoAplicada() { return condicaoAplicada; }
        public int getDanoSanidadeExtra() { return danoSanidadeExtra; }

        @Override
        public String toString() {
            return String.format("Sopro Draconiano [%s]: %d dano (%s) | Condição: %s",
                    linhagem.getElemento(), danoInfligido,
                    resistiuMetade ? "Resistiu Metade" : "Dano Total",
                    condicaoAplicada);
        }
    }

    /**
     * Executa o Sopro Primordial Draconiano com teste de resistência contra max(Def. Física, Def. Oculta).
     * - Sucesso no Teste: Alvo sofre apenas metade do dano e anula a condição tática secundária.
     * - Falha no Teste: Alvo sofre o dano total e fica sob a condição tática descrita na tabela 1d8.
     */
    public ResultadoSopro executarSoproDraconiano(Ficha atacante, Ficha alvo, int rolagem2d6, int rolagemResistenciaAlvo) {
        if (atacante == null) {
            return new ResultadoSopro(0, false, getLinhagemDraconica(), "Atacante nulo", 0);
        }

        // Custo canônico: 3 de Energia e 3 de Mana
        int custoE = this.custoEnergia > 0 ? this.custoEnergia : 3;
        int custoM = this.custoMana > 0 ? this.custoMana : 3;

        if (atacante.getEnergia() >= custoE) {
            atacante.gastarEnergia(custoE);
        } else {
            int faltante = custoE - atacante.getEnergia();
            atacante.gastarEnergia(atacante.getEnergia());
            atacante.forcarHabilidadeFisicaSemEnergia(faltante);
        }

        if (atacante.getMana() >= custoM) {
            atacante.gastarMana(custoM);
        } else {
            int faltante = custoM - atacante.getMana();
            atacante.gastarMana(atacante.getMana());
            atacante.forcarMagiaSemMana(faltante);
        }

        LinhagemDraconica linhagem = getLinhagemDraconica();
        int danoTotal = calcularDanoTotal(atacante, rolagem2d6);
        int limiarResistencia = Math.max(atacante.getDefesa(), atacante.getDefesaOculta());

        boolean resistiu = (rolagemResistenciaAlvo >= limiarResistencia);
        int danoFinal = resistiu ? (danoTotal / 2) : danoTotal;
        int danoReal = 0;
        int danoSanExtra = 0;
        String condicao = "Nenhuma";

        if (alvo != null) {
            danoReal = alvo.sofrerDanoFisico(danoFinal);

            if (!resistiu) {
                condicao = linhagem.getCondicaoTatica();
                if (linhagem == LinhagemDraconica.SOMBRA) {
                    danoSanExtra = (atacante.getGrauEsp() / 2) + 2;
                    alvo.sofrerDanoOculto(danoSanExtra);
                }
            } else {
                condicao = "Resistiu: Metade do Dano e Condição Anulada";
            }
        }

        return new ResultadoSopro(danoReal, resistiu, linhagem, condicao, danoSanExtra);
    }
}
