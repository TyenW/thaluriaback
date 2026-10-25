package economia;

/**
 * Lote de uma {@link ReceitaLoja} em produção numa loja. Os insumos e a mão de obra
 * já foram pagos na abertura; ao chegar o dia de conclusão, o resultado entra no estoque.
 * Persistido em loja_ordens_producao.
 */
public class OrdemReceita {

    /** Quem mandou produzir: a IA do mercador ou o Mestre agindo como mercador. */
    public enum Origem { IA, MESTRE }

    private int id;
    private final int receitaId;
    private final int lotes;
    private final int diaInicio;
    private final int diaConclusao;
    private final Origem origem;

    public OrdemReceita(int id, int receitaId, int lotes, int diaInicio, int diaConclusao, Origem origem) {
        this.id = id;
        this.receitaId = receitaId;
        this.lotes = Math.max(1, lotes);
        this.diaInicio = diaInicio;
        this.diaConclusao = diaConclusao;
        this.origem = origem != null ? origem : Origem.IA;
    }

    public boolean prontaNoDia(int dia) { return dia >= diaConclusao; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getReceitaId() { return receitaId; }
    public int getLotes() { return lotes; }
    public int getDiaInicio() { return diaInicio; }
    public int getDiaConclusao() { return diaConclusao; }
    public Origem getOrigem() { return origem; }
}
