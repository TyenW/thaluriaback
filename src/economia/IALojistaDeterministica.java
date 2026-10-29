package economia;

import java.util.List;
import java.util.Map;

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

    // =========================================================================
    // RECEITAS DO CATÁLOGO (ReceitaLoja): a IA que de fato roda no ciclo diário
    // =========================================================================

    /** Avaliação de uma receita para uma loja, também exibida ao Mestre. */
    public record AvaliacaoReceita(ReceitaLoja receita, long receitaEsperada, long custoInsumos, long maoDeObra,
                                   int dias, double ird, Map<Integer, Integer> insumosFaltantes, boolean estoqueCheio) {
        public long lucroEsperado() { return receitaEsperada - custoInsumos - maoDeObra; }
        public boolean viavel() { return insumosFaltantes.isEmpty() && !estoqueCheio && ird > 0; }
    }

    /**
     * IRD = (venda esperada do lote - insumos - mão de obra) / dias de produção.
     * Os insumos valem o que a loja pagaria por eles a um fornecedor hoje, e a venda
     * usa o preço dinâmico atual do resultado na própria loja.
     */
    public static AvaliacaoReceita avaliar(Lojas loja, ReceitaLoja receita, Map<Integer, Item> catalogo) {
        Item resultado = catalogo.get(receita.itemResultadoId());
        if (loja == null || resultado == null) {
            return new AvaliacaoReceita(receita, 0, 0, 0, receita.diasProducao(), -Double.MAX_VALUE, Map.of(), false);
        }
        long venda = loja.calcularPrecoDinamicoVendaEmLatao(resultado) * receita.quantidadeResultado();
        long insumos = 0L;
        for (ReceitaLoja.Insumo ins : receita.insumos()) {
            Item modelo = catalogo.get(ins.itemId());
            if (modelo != null) insumos += loja.calcularPrecoCompraNpcEmLatao(modelo) * ins.quantidade();
        }
        long maoDeObra = receita.custoMaoDeObraEmLatao(resultado, 1);
        int dias = receita.diasProducao();
        double ird = (double) (venda - insumos - maoDeObra) / dias;
        boolean cheio = loja.getQuantidadeEstoque(resultado.getId()) >= loja.estoqueAlvo(resultado.getId());
        return new AvaliacaoReceita(receita, venda, insumos, maoDeObra, dias, ird, loja.insumosFaltantes(receita, 1), cheio);
    }

    /**
     * Decisão diária do mercador: com vaga na oficina, produz a receita viável de maior IRD.
     * Se a melhor receita lucrativa travar por falta de insumo, a loja passa a procurar esse
     * insumo (sobe a demanda), e fornecedores e caravanas o trazem nos próximos dias.
     *
     * @return a ordem aberta, ou null se nada valia a pena hoje
     */
    public static OrdemReceita decidirProducaoDoDia(Lojas loja, List<ReceitaLoja> receitas, Map<Integer, Item> catalogo,
                                                    int dia, int hora) {
        if (loja == null || receitas == null || receitas.isEmpty()) return null;
        if (loja.getOrdensEmProducao() >= loja.getOrdensSimultaneasMax()) return null;

        AvaliacaoReceita melhorViavel = null;
        AvaliacaoReceita melhorTravada = null;
        for (ReceitaLoja r : receitas) {
            // Componentes e encomendas personalizadas só saem por ordem do Mestre
            if (!r.conhecidaPor(loja) || r.somenteEncomenda()) continue;
            AvaliacaoReceita a = avaliar(loja, r, catalogo);
            if (a.estoqueCheio() || a.ird() <= 0) continue;
            if (a.insumosFaltantes().isEmpty()) {
                if (melhorViavel == null || a.ird() > melhorViavel.ird()) melhorViavel = a;
            } else if (melhorTravada == null || a.ird() > melhorTravada.ird()) {
                melhorTravada = a;
            }
        }

        if (melhorTravada != null && (melhorViavel == null || melhorTravada.ird() > melhorViavel.ird())) {
            for (Map.Entry<Integer, Integer> falta : melhorTravada.insumosFaltantes().entrySet()) {
                Item modelo = catalogo.get(falta.getKey());
                int atual = loja.getDemandaPorItem().getOrDefault(falta.getKey(), 0);
                if (modelo != null) loja.definirDemanda(modelo, Math.max(atual, falta.getValue() + 2));
            }
        }
        if (melhorViavel == null) return null;
        Item resultado = catalogo.get(melhorViavel.receita().itemResultadoId());
        return loja.iniciarReceita(melhorViavel.receita(), resultado, 1, dia, hora, OrdemReceita.Origem.IA, false);
    }
}
