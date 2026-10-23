package economia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa uma encomenda urgente de manufatura expedida por nobres, guildas ou mercadores.
 * Concede bônus substancial de lucro (+50%) contra prazo rígido em dias de mesa.
 * Se a loja não possuir os insumos a tempo, pode terceirizar a coleta para os aventureiros (co-op).
 */
public class ContratoEncomenda {

    public enum StatusContrato {
        ABERTO,
        EM_PRODUCAO,
        TERCEIRIZADO_JOGADORES,
        CONCLUIDO_SUCESSO,
        FALHADO_EXPIRADO
    }

    private final int idContrato;
    private final String solicitante;
    private final MoldeReceita moldeExigido;
    private final int quantidadeExigida;
    private final int diasPrazoTotal;
    private int diasRestantes;
    private final long recompensaBaseLatao;
    private final double bonusPercentual;
    private StatusContrato status = StatusContrato.ABERTO;
    private double fatiaLucroJogadores = 0.0;
    private final List<Item> insumosFaltantesParaJogadores = new ArrayList<>();

    public ContratoEncomenda(int idContrato, String solicitante, MoldeReceita moldeExigido,
                             int quantidadeExigida, int diasPrazoTotal, long recompensaBaseLatao,
                             double bonusPercentual) {
        this.idContrato = idContrato;
        this.solicitante = (solicitante != null) ? solicitante : "Nobre de Thaluria";
        this.moldeExigido = moldeExigido;
        this.quantidadeExigida = Math.max(1, quantidadeExigida);
        this.diasPrazoTotal = Math.max(1, diasPrazoTotal);
        this.diasRestantes = this.diasPrazoTotal;
        this.recompensaBaseLatao = Math.max(1L, recompensaBaseLatao);
        this.bonusPercentual = Math.max(0.0, bonusPercentual);
    }

    public long getValorTotalRecompensaEmLatao() {
        return (long) Math.ceil(recompensaBaseLatao * (1.0 + bonusPercentual));
    }

    /**
     * Loja avalia e aceita o contrato se tiver capacidade técnica e prazos compatíveis.
     * Se faltarem materiais, publica coop para os jogadores.
     */
    public boolean aceitarEProduzir(Lojas loja, int dia, int hora) {
        if (loja == null || moldeExigido == null || status != StatusContrato.ABERTO) {
            return false;
        }

        if (loja.getNivelLoja() < moldeExigido.getNivelReceita()) {
            return false;
        }

        int tempoEstimado = moldeExigido.getDiasParaConcluirBase();
        if (loja.temTalento(TalentoLojista.PRODUCAO_EM_MASSA)) {
            tempoEstimado = Math.max(1, (int) Math.round(tempoEstimado * 0.75));
        }

        if (tempoEstimado > diasRestantes) {
            return false;
        }

        boolean temMateriais = true;
        for (FiltroInsumoReceita filtro : moldeExigido.getFiltrosInsumo()) {
            int presente = 0;
            for (Item it : loja.getEstoque()) {
                if (filtro.aceitaMaterial(it)) {
                    presente += it.getQuantidade();
                }
            }
            if (presente < filtro.getQuantidadeExigida() * quantidadeExigida) {
                temMateriais = false;
                break;
            }
        }

        if (temMateriais) {
            this.status = StatusContrato.EM_PRODUCAO;
            return true;
        } else {
            terceirizarParaJogadores(loja, 0.40);
            return false;
        }
    }

    public void terceirizarParaJogadores(Lojas loja, double fatiaLucro) {
        this.status = StatusContrato.TERCEIRIZADO_JOGADORES;
        this.fatiaLucroJogadores = Math.max(0.10, Math.min(0.90, fatiaLucro));
        this.insumosFaltantesParaJogadores.clear();
        for (FiltroInsumoReceita filtro : moldeExigido.getFiltrosInsumo()) {
            Item mock = new Item(filtro.getSubtipoExigido().ordinal() + 9100,
                    "Insumo Urgente (" + filtro.getSubtipoExigido() + ")",
                    1.0);
            mock.setFichaMaterial(new FichaMaterial(filtro.getSubtipoExigido(), filtro.getQualidadeMinima()));
            mock.setQuantidade(filtro.getQuantidadeExigida() * quantidadeExigida);
            insumosFaltantesParaJogadores.add(mock);
        }
    }

    public boolean entregarItensProntos(Lojas loja, int dia, int hora) {
        if (loja == null || status == StatusContrato.CONCLUIDO_SUCESSO || status == StatusContrato.FALHADO_EXPIRADO) {
            return false;
        }

        int itensEncontrados = 0;
        List<Item> paraRemover = new ArrayList<>();
        for (Item it : loja.getEstoque()) {
            if (it.getNome().contains(moldeExigido.getNomeBaseItem()) || (it.getTipo() == moldeExigido.getTipoItemGerado())) {
                itensEncontrados += it.getQuantidade();
                paraRemover.add(it);
            }
        }

        if (itensEncontrados >= quantidadeExigida) {
            int restante = quantidadeExigida;
            for (Item it : paraRemover) {
                int deduzir = Math.min(restante, it.getQuantidade());
                loja.removerItem(it.getId(), deduzir);
                restante -= deduzir;
                if (restante <= 0) break;
            }

            long recompensa = getValorTotalRecompensaEmLatao();
            loja.getCaixa().receberLatao(recompensa);
            loja.registrarMovimentacao(dia, hora, RegistroMovimentacao.TipoOperacao.CONTRATO_ENCOMENDA,
                    moldeExigido.getNomeBaseItem(), quantidadeExigida, recompensa,
                    "Contrato cumprido para " + solicitante + " (+50% bônus)");

            this.status = StatusContrato.CONCLUIDO_SUCESSO;
            return true;
        }

        return false;
    }

    public boolean avancarDia(int dia, int hora) {
        if (status == StatusContrato.CONCLUIDO_SUCESSO || status == StatusContrato.FALHADO_EXPIRADO) {
            return false;
        }
        diasRestantes--;
        if (diasRestantes < 0) {
            this.status = StatusContrato.FALHADO_EXPIRADO;
            return true;
        }
        return false;
    }

    public int getIdContrato() {
        return idContrato;
    }

    public String getSolicitante() {
        return solicitante;
    }

    public MoldeReceita getMoldeExigido() {
        return moldeExigido;
    }

    public int getQuantidadeExigida() {
        return quantidadeExigida;
    }

    public int getDiasPrazoTotal() {
        return diasPrazoTotal;
    }

    public int getDiasRestantes() {
        return diasRestantes;
    }

    public long getRecompensaBaseLatao() {
        return recompensaBaseLatao;
    }

    public double getBonusPercentual() {
        return bonusPercentual;
    }

    public StatusContrato getStatus() {
        return status;
    }

    public double getFatiaLucroJogadores() {
        return fatiaLucroJogadores;
    }

    public List<Item> getInsumosFaltantesParaJogadores() {
        return Collections.unmodifiableList(insumosFaltantesParaJogadores);
    }
}
