package economia;

import java.util.List;

/**
 * Motor de Inteligência Artificial Determinística para Tomada de Decisão de Lojas e Oficinas.
 * Não utiliza geração procedural ou aleatoriedade rasa; toma decisões puramente baseadas
 * na maximização do Índice de Rentabilidade Diária (IRD) e na urgência de insumos perecíveis.
 */
public class IALojistaDeterministica {

    /**
     * Calcula o Índice de Rentabilidade Diária (IRD) em Latão por dia de mesa:
     * IRD = (Preço de Venda Esperado - Custo dos Insumos - Mão de Obra) / Dias de Produção
     * Insumos perecíveis prestes a vencer no armazém recebem forte bônus de prioridade
     * para evitar prejuízo por perda e deterioração contábil.
     */
    public static double calcularIRD(Lojas loja, MoldeReceita molde) {
        if (loja == null || molde == null) return 0.0;

        // Estima o preço de venda esperado com base na categoria e qualidade do molde
        long precoVendaEsperado = Math.max(10_000L, molde.getCustoMaoDeObraEmLatao() * 3 + (molde.getNivelReceita() * 20_000L));
        long custoMaoDeObra = molde.getCustoMaoDeObraEmLatao();

        long custoInsumos = 0L;
        boolean temInsumoPrestesAVencer = false;

        for (FiltroInsumoReceita filtro : molde.getFiltrosInsumo()) {
            Item disponivel = null;
            for (Item it : loja.getEstoque()) {
                if (filtro.aceitaMaterial(it) && it.getQuantidade() >= filtro.getQuantidadeExigida()) {
                    disponivel = it;
                    break;
                }
            }
            if (disponivel != null) {
                custoInsumos += disponivel.getPrecoEmLatao() * filtro.getQuantidadeExigida();
                if (disponivel.isPerecivel() && disponivel.getDiasValidadeRestantes() <= 2) {
                    temInsumoPrestesAVencer = true;
                }
            } else {
                custoInsumos += 15_000L * filtro.getQuantidadeExigida();
            }
        }

        long lucroEsperado = precoVendaEsperado - custoInsumos - custoMaoDeObra;
        int diasProducao = Math.max(1, molde.getDiasParaConcluirBase());

        double ird = (double) lucroEsperado / diasProducao;

        // Bônus prioritário para salvar insumos perecíveis da deterioração
        if (temInsumoPrestesAVencer) {
            ird += 100_000.0;
        }

        return ird;
    }

    /**
     * Seleciona deterministicamente a receita de maior IRD disponível para a loja.
     * Receitas avançadas (Nv. 4 e 5) podem exigir validação prévia no CatalogoGlobalDescobertas.
     */
    public static MoldeReceita selecionarMelhorReceita(Lojas loja, CatalogoGlobalDescobertas catalogo) {
        if (loja == null || loja.getLivroDeReceitas().isEmpty()) return null;

        MoldeReceita melhorReceita = null;
        double maiorIRD = -Double.MAX_VALUE;

        for (MoldeReceita receita : loja.getLivroDeReceitas()) {
            if (catalogo != null && receita.getNivelReceita() >= 4) {
                if (!catalogo.isReceitaDescoberta(receita.getIdMolde())) {
                    continue;
                }
            }

            double ird = calcularIRD(loja, receita);
            if (ird > maiorIRD) {
                maiorIRD = ird;
                melhorReceita = receita;
            }
        }

        return melhorReceita;
    }

    /**
     * Executa o ciclo de decisão diária da loja através da IA.
     */
    public static boolean executarDecisaoDiaria(Lojas loja, CatalogoGlobalDescobertas catalogo, int dia, int hora) {
        if (loja == null) return false;

        MoldeReceita melhorReceita = selecionarMelhorReceita(loja, catalogo);
        if (melhorReceita == null) return false;

        return loja.iniciarOrdemProducao(melhorReceita, dia, hora);
    }
}
