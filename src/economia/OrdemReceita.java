package economia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

/**
 * Lote de uma {@link ReceitaLoja} em produção numa loja. Os insumos e a mão de obra
 * já foram pagos na abertura; ao chegar o dia de conclusão, o resultado entra no estoque.
 * Persistido em loja_ordens_producao.
 *
 * Em receitas com etapas (armas da forja), cada componente fica pronto no seu dia e passa a
 * ocupar a prateleira <b>reservado</b> para esta encomenda: não pode ser vendido. Só volta a
 * ser estoque comum se a ordem for cancelada.
 *
 * Encomenda de jogador guarda a mesa e o prazo no relógio dela (docs/Sistema de Economia/05, seção 5.3):
 * a loja produz no tempo do mundo, mas a entrega acontece quando a mesa chega ao dia combinado.
 * Se a loja termina antes, o resultado fica reservado ({@link #isProntaAguardando()}).
 */
public class OrdemReceita {

    /** Quem mandou produzir: a IA do mercador ou o Mestre agindo como mercador. */
    public enum Origem { IA, MESTRE }

    public enum EstadoEtapa {
        /** Materiais consumidos, peça ainda na bancada. */
        PENDENTE('P'),
        /** Peça pronta, reservada para a encomenda. */
        RESERVADA('R'),
        /** Peça que já estava na prateleira e foi reservada na abertura (poupou o tempo da etapa). */
        DA_PRATELEIRA('S');

        final char sigla;
        EstadoEtapa(char sigla) { this.sigla = sigla; }

        static EstadoEtapa daSigla(char c) {
            for (EstadoEtapa e : values()) if (e.sigla == c) return e;
            return PENDENTE;
        }
    }

    public static final class EtapaOrdem {
        private final int componenteItemId;
        private final int diaConclusao;
        private EstadoEtapa estado;

        public EtapaOrdem(int componenteItemId, int diaConclusao, EstadoEtapa estado) {
            this.componenteItemId = componenteItemId;
            this.diaConclusao = diaConclusao;
            this.estado = estado;
        }

        public int getComponenteItemId() { return componenteItemId; }
        public int getDiaConclusao() { return diaConclusao; }
        public EstadoEtapa getEstado() { return estado; }
        public boolean isReservada() { return estado != EstadoEtapa.PENDENTE; }
        void setEstado(EstadoEtapa estado) { this.estado = estado; }
    }

    private int id;
    private final int receitaId;
    private final int lotes;
    private final int diaInicio;
    private final int diaConclusao;
    private final Origem origem;
    private final boolean insumosPorConta;
    private final List<EtapaOrdem> etapas = new ArrayList<>();
    /** Mesa que encomendou (null = ordem do mundo: IA ou Mestre sem mesa). */
    private Integer mesaId;
    /** Prazo no relógio da mesa. */
    private Integer diaConclusaoMesa;
    /** Personagem que recebe o resultado (null = entra no estoque da loja para o Mestre vender). */
    private Long fichaId;
    /** A loja já terminou; o resultado espera a mesa chegar ao prazo. */
    private boolean prontaAguardando;

    public OrdemReceita(int id, int receitaId, int lotes, int diaInicio, int diaConclusao, Origem origem) {
        this(id, receitaId, lotes, diaInicio, diaConclusao, origem, false, List.of());
    }

    public OrdemReceita(int id, int receitaId, int lotes, int diaInicio, int diaConclusao, Origem origem,
                        boolean insumosPorConta, List<EtapaOrdem> etapas) {
        this.id = id;
        this.receitaId = receitaId;
        this.lotes = Math.max(1, lotes);
        this.diaInicio = diaInicio;
        this.diaConclusao = diaConclusao;
        this.origem = origem != null ? origem : Origem.IA;
        this.insumosPorConta = insumosPorConta;
        if (etapas != null) this.etapas.addAll(etapas);
    }

    public boolean prontaNoDia(int dia) { return dia >= diaConclusao; }

    /** Liga a ordem a uma mesa: o prazo do jogador é contado no relógio dela. */
    public void vincularMesa(Integer mesaId, Integer diaConclusaoMesa, Long fichaId, boolean prontaAguardando) {
        this.mesaId = mesaId;
        this.diaConclusaoMesa = mesaId != null ? diaConclusaoMesa : null;
        this.fichaId = fichaId;
        this.prontaAguardando = mesaId != null && prontaAguardando;
    }

    /** Mesma ordem com outro dia de conclusão no mundo (o Mestre apressou a oficina). */
    OrdemReceita comConclusao(int novoDia) {
        OrdemReceita o = new OrdemReceita(id, receitaId, lotes, diaInicio, novoDia, origem, insumosPorConta, etapas);
        o.vincularMesa(mesaId, diaConclusaoMesa, fichaId, prontaAguardando);
        return o;
    }

    public boolean isDaMesa() { return mesaId != null; }
    public Integer getMesaId() { return mesaId; }
    public Integer getDiaConclusaoMesa() { return diaConclusaoMesa; }
    public Long getFichaId() { return fichaId; }
    public boolean isProntaAguardando() { return prontaAguardando; }
    void marcarProntaAguardando() { prontaAguardando = true; }
    /** O personagem trocou de mesa: a encomenda passa a seguir o relógio da nova. */
    public void moverParaMesa(int novaMesaId) { if (mesaId != null) mesaId = novaMesaId; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getReceitaId() { return receitaId; }
    public int getLotes() { return lotes; }
    public int getDiaInicio() { return diaInicio; }
    public int getDiaConclusao() { return diaConclusao; }
    public Origem getOrigem() { return origem; }
    public boolean isInsumosPorConta() { return insumosPorConta; }
    public List<EtapaOrdem> getEtapas() { return Collections.unmodifiableList(etapas); }

    /** Formato gravado no banco: "itemId:dia:sigla;..." (sigla P, R ou S). */
    public String etapasSerializadas() {
        StringJoiner j = new StringJoiner(";");
        for (EtapaOrdem e : etapas) j.add(e.componenteItemId + ":" + e.diaConclusao + ":" + e.estado.sigla);
        return j.toString();
    }

    public static List<EtapaOrdem> lerEtapas(String texto) {
        List<EtapaOrdem> lista = new ArrayList<>();
        if (texto == null || texto.isBlank()) return lista;
        for (String parte : texto.split(";")) {
            String[] c = parte.split(":");
            if (c.length < 3 || c[2].isEmpty()) continue;
            try {
                lista.add(new EtapaOrdem(Integer.parseInt(c[0].trim()), Integer.parseInt(c[1].trim()), EstadoEtapa.daSigla(c[2].trim().charAt(0))));
            } catch (NumberFormatException ignored) {
                // etapa corrompida: ignorada
            }
        }
        return lista;
    }
}
