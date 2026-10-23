package economia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa uma ordem de serviço em andamento em uma oficina ou loja de Thaluria.
 * O tempo de manufatura é calculado em dias de mesa. Quando diasRestantes chega a 0,
 * a peça é concluída e transferida para a prateleira de vendas.
 */
public class OrdemProducao {

    private final int idOrdem;
    private final Lojas loja;
    private final MoldeReceita molde;
    private final List<Item> materiaisConsumidos = new ArrayList<>();
    private final int idItemResultado;
    private final int diasParaConcluirBase;
    private int diasRestantes;
    private boolean concluida;
    private Item itemProduzido;
    private boolean obraPrima;
    private boolean producaoEmMassa;

    public OrdemProducao(int idOrdem, Lojas loja, MoldeReceita molde, List<Item> materiaisConsumidos, int idItemResultado) {
        this(idOrdem, loja, molde, materiaisConsumidos, idItemResultado,
                (molde != null ? molde.getDiasParaConcluirBase() : 1));
    }

    public OrdemProducao(int idOrdem, Lojas loja, MoldeReceita molde, List<Item> materiaisConsumidos,
                         int idItemResultado, int diasParaConcluir) {
        this.idOrdem = idOrdem;
        this.loja = loja;
        this.molde = molde;
        if (materiaisConsumidos != null) {
            this.materiaisConsumidos.addAll(materiaisConsumidos);
        }
        this.idItemResultado = idItemResultado;
        this.diasParaConcluirBase = Math.max(1, diasParaConcluir);
        this.diasRestantes = this.diasParaConcluirBase;
        this.concluida = false;

        // Verifica talentos e melhorias da loja
        if (loja != null) {
            if (loja.temTalento(TalentoLojista.PRODUCAO_EM_MASSA)) {
                this.producaoEmMassa = true;
                int reducao = (int) Math.round(this.diasRestantes * 0.25);
                this.diasRestantes = Math.max(1, this.diasRestantes - Math.max(1, reducao));
            }
            if (loja.temTalento(TalentoLojista.MESTRE_ARTESAO)) {
                this.obraPrima = true;
            }
            if (loja.getArvoreEvolucao() != null
                    && loja.getArvoreEvolucao().temMelhoria(ArvoreEvolucaoLoja.MelhoriaLoja.LABORATORIO_DESTILACAO)
                    && molde != null && molde.getTipoItemGerado() == Item.TipoItem.CONSUMIVEL) {
                this.diasRestantes = Math.max(1, this.diasRestantes / 2);
            }
        }
    }

    /**
     * Avança 1 dia na ordem de produção. Quando o prazo zera, conclui a manufatura.
     */
    public boolean avancarDia(int diaMundo, int horaMundo) {
        if (concluida) return false;
        diasRestantes--;
        if (diasRestantes <= 0) {
            concluir(diaMundo, horaMundo);
            return true;
        }
        return false;
    }

    /**
     * Conclui a produção do item através de OficinaCrafting e entrega à prateleira da loja.
     */
    public Item concluir(int diaMundo, int horaMundo) {
        if (concluida) return itemProduzido;

        this.itemProduzido = OficinaCrafting.fabricarItem(loja, molde, materiaisConsumidos, idItemResultado);
        if (itemProduzido != null) {
            if (obraPrima) {
                itemProduzido.setObraPrima(true);
            }
            if (producaoEmMassa && itemProduzido.getQualidadeCraft() != null) {
                int grauAtual = itemProduzido.getQualidadeCraft().getGrau();
                if (grauAtual > 1) {
                    itemProduzido.setQualidadeCraft(QualidadeCraft.doGrau(grauAtual - 1));
                }
            }
            if (loja != null) {
                loja.adicionarItemProntoPrateleira(itemProduzido);
                loja.registrarMovimentacao(diaMundo, horaMundo, RegistroMovimentacao.TipoOperacao.PRODUCAO_CRAFTING,
                        itemProduzido.getNome(), itemProduzido.getQuantidade(), (molde != null ? molde.getCustoMaoDeObraEmLatao() : 0L),
                        "Conclusão de Ordem de Produção #" + idOrdem + (obraPrima ? " [Obra-Prima 3x]" : ""));
            }
        }
        this.concluida = true;
        return itemProduzido;
    }

    public int getIdOrdem() {
        return idOrdem;
    }

    public Lojas getLoja() {
        return loja;
    }

    public MoldeReceita getMolde() {
        return molde;
    }

    public List<Item> getMateriaisConsumidos() {
        return Collections.unmodifiableList(materiaisConsumidos);
    }

    public int getIdItemResultado() {
        return idItemResultado;
    }

    public int getDiasParaConcluirBase() {
        return diasParaConcluirBase;
    }

    public int getDiasRestantes() {
        return diasRestantes;
    }

    public void setDiasRestantes(int diasRestantes) {
        this.diasRestantes = Math.max(0, diasRestantes);
    }

    public boolean isConcluida() {
        return concluida;
    }

    public Item getItemProduzido() {
        return itemProduzido;
    }

    public boolean isObraPrima() {
        return obraPrima;
    }

    public void setObraPrima(boolean obraPrima) {
        this.obraPrima = obraPrima;
    }

    public boolean isProducaoEmMassa() {
        return producaoEmMassa;
    }
}
