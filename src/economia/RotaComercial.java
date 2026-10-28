package economia;

import geografia.Cidade;
import geografia.Conexao;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa uma rota comercial entre cidades de Thaluria: uma sequência
 * ordenada de cidades ligada por {@link Conexao}s (as "arestas" do grafo de
 * viagem).
 *
 * <p>
 * No momento da construção a rota calcula e guarda seus indicadores
 * agregados (imutáveis):
 * <ul>
 * <li><b>Distância total</b>: soma da distância de todas as conexões, em km.</li>
 * <li><b>Perigo médio</b>: média do perigo das conexões <i>ponderada pela
 * distância</i> — trechos longos pesam mais que trechos curtos.</li>
 * <li><b>Perigo máximo</b>: o maior nível de perigo entre as conexões, ou seja,
 * o "pior trecho" da viagem.</li>
 * </ul>
 *
 * <p>
 * Com esses indicadores a rota estima o custo de frete, o custo de escolta e se
 * a escolta é recomendada. Os custos são calculados internamente em
 * <b>latão</b> ({@link Carteira.Moeda#LATAO}, a menor moeda do sistema
 * monetário), sem perda de precisão, e podem ser obtidos de três formas:
 * <ul>
 * <li>{@code ...EmLatao()}: valor exato ({@code long});</li>
 * <li>{@code calcularCusto...()}: valor em <b>ouro (PO)</b>, a mesma unidade de
 * {@link Item#getPreco()};</li>
 * <li>{@code descrever...()}: texto nas moedas do sistema (ex.:
 * "4 PT, 2 PO").</li>
 * </ul>
 * Os custos também podem ser cobrados direto de uma {@link Carteira} por
 * {@link #pagarFrete(Carteira, double)} e {@link #pagarEscolta(Carteira)}.
 *
 * <p>
 * Exemplo:
 * 
 * <pre>{@code
 * RotaComercial rota = new RotaComercial(cidades, conexoes);
 * System.out.println(rota.descreverCustoFrete(120.0)); // carga de 120 kg
 * if (rota.pagarFrete(carteira, 120.0) && rota.recomendaEscolta()) {
 *     rota.pagarEscolta(carteira);
 * }
 * }</pre>
 */
public class RotaComercial {

    /** Frete base por km, em latão: 2 cobres (PC) por km. */
    private static final long TAXA_BASE_FRETE_LATAO_POR_KM = 2 * Carteira.Moeda.COBRE.getValorEmLatao();
    /** Peso (kg) a cada qual o frete é encarecido em um passo extra de 100% da base. */
    private static final double KG_POR_FATOR_DE_PESO = 50.0;
    /** Acréscimo no frete por nível de perigo médio (15%). */
    private static final double ACRESCIMO_FRETE_POR_PERIGO = 0.15;

    /** Custo base da escolta por km, em latão: 5 cobres (PC) por km. */
    private static final long TAXA_BASE_ESCOLTA_LATAO_POR_KM = 5 * Carteira.Moeda.COBRE.getValorEmLatao();
    /** Acréscimo no custo da escolta por nível de perigo médio (25%). */
    private static final double ACRESCIMO_ESCOLTA_POR_PERIGO = 0.25;

    /** Perigo médio a partir do qual a escolta é recomendada. */
    private static final double LIMITE_PERIGO_MEDIO_ESCOLTA = 4.0;
    /** Perigo máximo (em um único trecho) a partir do qual a escolta é recomendada. */
    private static final int LIMITE_PERIGO_MAXIMO_ESCOLTA = 6;

    private final List<Cidade> caminhoCidades;
    private final List<Conexao> arestas;
    private final int distanciaTotalKm;
    private final double perigoMedio;
    private final int perigoMaximo;

    /**
     * Cria uma rota e calcula seus indicadores de distância e perigo.
     *
     * @param caminhoCidades cidades visitadas, na ordem do percurso (origem
     *                       primeiro, destino por último)
     * @param arestas        conexões percorridas entre as cidades, na ordem do
     *                       percurso
     * @throws IllegalArgumentException se alguma das listas for nula ou se
     *                                  {@code arestas} contiver um elemento nulo
     */
    public RotaComercial(List<Cidade> caminhoCidades, List<Conexao> arestas) {
        if (caminhoCidades == null || arestas == null) {
            throw new IllegalArgumentException("O caminho de cidades e as conexões não podem ser nulos");
        }
        // Cópias defensivas: a rota não é afetada por alterações posteriores nas listas originais.
        this.caminhoCidades = new ArrayList<>(caminhoCidades);
        this.arestas = new ArrayList<>(arestas);

        int somaDist = 0;
        double somaPerigoPonderado = 0;
        int maxPerigo = 0;

        for (Conexao c : this.arestas) {
            if (c == null) {
                throw new IllegalArgumentException("A lista de conexões não pode conter elementos nulos");
            }
            somaDist += c.getDistanciaKm();
            somaPerigoPonderado += c.getPerigo() * c.getDistanciaKm();
            if (c.getPerigo() > maxPerigo) {
                maxPerigo = c.getPerigo();
            }
        }

        this.distanciaTotalKm = somaDist;
        this.perigoMedio = somaDist > 0 ? (somaPerigoPonderado / somaDist) : 0;
        this.perigoMaximo = maxPerigo;
    }

    /**
     * Calcula o custo do frete (transporte da carga) em Ouro (PO).
     *
     * @param pesoTotalKg peso total da carga, em kg (valores negativos são
     *                    tratados como 0)
     * @return custo do frete em PO (equivalente exato de
     *         {@link #calcularCustoFreteEmLatao(double)})
     * @see #calcularCustoFreteEmLatao(double)
     */
    public double calcularCustoFrete(double pesoTotalKg) {
        return Carteira.lataoParaOuro(calcularCustoFreteEmLatao(pesoTotalKg));
    }

    /**
     * Calcula o custo do frete (transporte da carga) em latão, a menor unidade
     * do sistema monetário.
     *
     * <p>
     * Fórmula: {@code distância × 200 × (1 + peso/50) × (1 + perigoMédio × 0,15)}
     * <ul>
     * <li>Base: 2 PC (200 latão) por km.</li>
     * <li>Peso: a cada 50 kg de carga o fator de peso sobe em 1,0.</li>
     * <li>Risco: +15% por nível de perigo médio da rota.</li>
     * </ul>
     * O resultado é arredondado para o latão mais próximo.
     *
     * @param pesoTotalKg peso total da carga, em kg (valores negativos são
     *                    tratados como 0)
     * @return custo do frete em latão
     */
    public long calcularCustoFreteEmLatao(double pesoTotalKg) {
        return calcularCustoFreteEmLatao(pesoTotalKg, ServicoTransporte.COMUM);
    }

    /**
     * Frete em latão pelo serviço de transporte escolhido: a fórmula base vezes o
     * fator de frete do serviço (Comum = 1,0).
     */
    public long calcularCustoFreteEmLatao(double pesoTotalKg, ServicoTransporte servico) {
        double peso = Math.max(0.0, pesoTotalKg);
        double fatorPeso = 1.0 + (peso / KG_POR_FATOR_DE_PESO);
        double fatorRisco = 1.0 + (perigoMedio * ACRESCIMO_FRETE_POR_PERIGO);
        return Math.round(distanciaTotalKm * TAXA_BASE_FRETE_LATAO_POR_KM * fatorPeso * fatorRisco * servico.getFatorFrete());
    }

    /**
     * Calcula o custo de contratar uma escolta armada para a rota, em Ouro (PO).
     *
     * @return custo da escolta em PO (equivalente exato de
     *         {@link #calcularCustoEscoltaEmLatao()})
     * @see #calcularCustoEscoltaEmLatao()
     */
    public double calcularCustoEscolta() {
        return Carteira.lataoParaOuro(calcularCustoEscoltaEmLatao());
    }

    /**
     * Calcula o custo de contratar uma escolta armada para a rota, em latão.
     *
     * <p>
     * Fórmula: {@code distância × 500 × (1 + perigoMédio × 0,25)}
     * <ul>
     * <li>Base: 5 PC (500 latão) por km.</li>
     * <li>Risco: +25% por nível de perigo médio da rota.</li>
     * </ul>
     * O resultado é arredondado para o latão mais próximo.
     *
     * @return custo da escolta em latão
     */
    public long calcularCustoEscoltaEmLatao() {
        double fatorRisco = 1.0 + (perigoMedio * ACRESCIMO_ESCOLTA_POR_PERIGO);
        return Math.round(distanciaTotalKm * TAXA_BASE_ESCOLTA_LATAO_POR_KM * fatorRisco);
    }

    /**
     * Descreve o custo do frete nas moedas do sistema (ex.: "4 PT, 2 PO").
     *
     * @param pesoTotalKg peso total da carga, em kg
     * @return custo do frete formatado em moedas
     */
    public String descreverCustoFrete(double pesoTotalKg) {
        return Carteira.formatarLatao(calcularCustoFreteEmLatao(pesoTotalKg));
    }

    /**
     * Descreve o custo da escolta nas moedas do sistema.
     *
     * @return custo da escolta formatado em moedas
     */
    public String descreverCustoEscolta() {
        return Carteira.formatarLatao(calcularCustoEscoltaEmLatao());
    }

    /**
     * Cobra o frete da carteira informada, com conversão automática de troco entre
     * as moedas.
     *
     * @param carteira    carteira de quem paga
     * @param pesoTotalKg peso total da carga, em kg
     * @return {@code true} se o pagamento foi feito; {@code false} se a carteira
     *         for nula ou não tiver saldo (nada é debitado)
     */
    public boolean pagarFrete(Carteira carteira, double pesoTotalKg) {
        return pagarFrete(carteira, pesoTotalKg, ServicoTransporte.COMUM);
    }

    /** Cobra o frete do serviço escolhido; {@code false} (nada debitado) se faltar saldo. */
    public boolean pagarFrete(Carteira carteira, double pesoTotalKg, ServicoTransporte servico) {
        return carteira != null && carteira.pagarLatao(calcularCustoFreteEmLatao(pesoTotalKg, servico));
    }

    /**
     * Cobra a escolta da carteira informada, com conversão automática de troco
     * entre as moedas.
     *
     * @param carteira carteira de quem paga
     * @return {@code true} se o pagamento foi feito; {@code false} se a carteira
     *         for nula ou não tiver saldo (nada é debitado)
     */
    public boolean pagarEscolta(Carteira carteira) {
        return carteira != null && carteira.pagarLatao(calcularCustoEscoltaEmLatao());
    }

    /**
     * Indica se a rota exige (ou recomenda fortemente) uma escolta.
     *
     * @return {@code true} se o perigo médio for {@code >= 4.0} <b>ou</b> se
     *         algum trecho tiver perigo {@code >= 6}; {@code false} caso contrário
     */
    public boolean recomendaEscolta() {
        return recomendaEscolta(ServicoTransporte.COMUM);
    }

    /**
     * Escolta recomendada para o serviço escolhido: os limites valem sobre o perigo
     * já multiplicado pelo fator de risco do serviço (um comboio blindado dispensa
     * escolta onde um expresso precisaria dela).
     */
    public boolean recomendaEscolta(ServicoTransporte servico) {
        double f = servico.getFatorRisco();
        return perigoMedio * f >= LIMITE_PERIGO_MEDIO_ESCOLTA || perigoMaximo * f >= LIMITE_PERIGO_MAXIMO_ESCOLTA;
    }

    /** Horas de viagem da origem ao destino com o serviço escolhido. */
    public double horasDeViagem(ServicoTransporte servico) {
        double horas = 0;
        for (Conexao a : arestas) horas += a.getDistanciaKm() / servico.velocidadeKmH(a);
        return horas;
    }

    /**
     * Chance (0 a 1) de a carga ser atacada em algum ponto da viagem, com o mesmo
     * cálculo hora a hora de {@link Caravana#avancarHoras}.
     *
     * @param fatorProtecao multiplicador do risco pela escolta (1 = desprotegida)
     */
    public double chanceDeAtaque(ServicoTransporte servico, double fatorProtecao) {
        double chanceIntacta = 1.0;
        for (Conexao a : arestas) {
            double riscoHora = Math.min(1.0, servico.riscoPorHora(a) * fatorProtecao);
            chanceIntacta *= Math.pow(1.0 - riscoHora, a.getDistanciaKm() / servico.velocidadeKmH(a));
        }
        return 1.0 - chanceIntacta;
    }

    /**
     * @return visão somente leitura das cidades do percurso, na ordem de viagem
     */
    public List<Cidade> getCaminhoCidades() {
        return Collections.unmodifiableList(caminhoCidades);
    }

    /**
     * @return visão somente leitura das conexões percorridas, na ordem de viagem
     */
    public List<Conexao> getArestas() {
        return Collections.unmodifiableList(arestas);
    }

    /**
     * @return soma da distância de todas as conexões, em km
     */
    public int getDistanciaTotalKm() {
        return distanciaTotalKm;
    }

    /**
     * @return perigo médio da rota (escala 1 a 10) ponderado pela distância de
     *         cada trecho; {@code 0} se a rota não tiver distância
     */
    public double getPerigoMedio() {
        return perigoMedio;
    }

    /**
     * @return maior nível de perigo (1 a 10) entre as conexões da rota; {@code 0}
     *         se não houver conexões
     */
    public int getPerigoMaximo() {
        return perigoMaximo;
    }
}
