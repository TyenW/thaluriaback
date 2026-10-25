package economia;

/**
 * Gerencia o sistema financeiro, saldo e moedas de personagens ou comércios em
 * Thaluria.
 * Escala monetária:
 * 1 édito = 1000 Talento
 * 1 Talento = 10 diamantes
 * 1 diamante = 10 cobalto
 * 1 cobalto = 10 platina
 * 1 platina = 10 ouro
 * 1 ouro = 10 prata
 * 1 prata = 10 cobre
 * 1 cobre = 10 ferro
 * 1 ferro = 10 latao
 */
public class Carteira {

    /**
     * Tipos de moeda de Thaluria, da menor para a maior, com seu valor em latão
     * (a menor unidade).
     */
    public enum Moeda {
        LATAO("Latão", "PL", 1L),
        FERRO("Ferro", "PF", 10L),
        COBRE("Cobre", "PC", 100L),
        PRATA("Prata", "PP", 1_000L),
        OURO("Ouro", "PO", 10_000L),
        PLATINA("Platina", "PT", 100_000L),
        COBALTO("Cobalto", "CB", 1_000_000L),
        DIAMANTE("Diamante", "DI", 10_000_000L),
        TALENTO("Talento", "TL", 100_000_000L),
        EDITO("Édito", "ED", 100_000_000_000L);

        private final String nome;
        private final String sigla;
        private final long valorEmLatao;

        Moeda(String nome, String sigla, long valorEmLatao) {
            this.nome = nome;
            this.sigla = sigla;
            this.valorEmLatao = valorEmLatao;
        }

        public String getNome() {
            return nome;
        }

        public String getSigla() {
            return sigla;
        }

        public long getValorEmLatao() {
            return valorEmLatao;
        }
    }

    /** Fator de conversão de 1 moeda de ouro para latão. */
    private static final long LATAO_POR_OURO = Moeda.OURO.getValorEmLatao();

    private int edito;
    private int talento;
    private int diamante;
    private int cobalto;
    private int platina;
    private int ouro;
    private int prata;
    private int cobre;
    private int ferro;
    private int latao;

    public Carteira() {
    }

    public Carteira(int ouro) {
        this(ouro, 0, 0);
    }

    public Carteira(int ouro, int prata, int cobre) {
        adicionarMoedas(ouro, prata, cobre);
    }

    // ------------------------------------------------------------------
    // Conversões internas
    // ------------------------------------------------------------------

    /**
     * Converte um valor em ouro (PO, pode ter fração) para latão, a menor unidade.
     * Arredonda para o latão mais próximo (1 latão = 0,0001 PO).
     */
    public static long ouroParaLatao(double valorEmOuro) {
        return Math.round(valorEmOuro * LATAO_POR_OURO);
    }

    /**
     * Converte um valor em latão para ouro (PO) decimal.
     */
    public static double lataoParaOuro(long valorEmLatao) {
        return valorEmLatao / (double) LATAO_POR_OURO;
    }

    /**
     * Cria uma carteira contendo exatamente o valor informado em latão,
     * distribuído nas maiores moedas possíveis.
     */
    public static Carteira deLatao(long valorEmLatao) {
        Carteira c = new Carteira();
        c.distribuir(valorEmLatao);
        return c;
    }

    /**
     * Descreve um valor em latão usando as moedas do sistema monetário
     * (ex.: {@code 14600 -> "1 PO, 4 PP, 6 PC"}).
     */
    public static String formatarLatao(long valorEmLatao) {
        return deLatao(valorEmLatao).descreverMoedas();
    }

    /**
     * Redistribui um valor total em latão entre todas as moedas, usando sempre
     * a maior denominação possível.
     */
    private void distribuir(long totalEmLatao) {
        long resto = Math.max(0L, totalEmLatao);
        Moeda[] moedas = Moeda.values();
        for (int i = moedas.length - 1; i >= 0; i--) {
            Moeda m = moedas[i];
            long qtd = resto / m.getValorEmLatao();
            resto %= m.getValorEmLatao();
            definirCampo(m, (int) Math.min(Integer.MAX_VALUE, qtd));
        }
    }

    private void definirCampo(Moeda moeda, int quantidade) {
        switch (moeda) {
            case EDITO -> this.edito = quantidade;
            case TALENTO -> this.talento = quantidade;
            case DIAMANTE -> this.diamante = quantidade;
            case COBALTO -> this.cobalto = quantidade;
            case PLATINA -> this.platina = quantidade;
            case OURO -> this.ouro = quantidade;
            case PRATA -> this.prata = quantidade;
            case COBRE -> this.cobre = quantidade;
            case FERRO -> this.ferro = quantidade;
            case LATAO -> this.latao = quantidade;
        }
    }

    // ------------------------------------------------------------------
    // Operações
    // ------------------------------------------------------------------

    /**
     * Converte excedentes de moedas menores em moedas superiores.
     */
    public void normalizarMoedas() {
        distribuir(getValorTotalEmLatao());
    }

    /**
     * Adiciona uma quantidade de um tipo de moeda, normalizando o saldo.
     */
    public void adicionar(Moeda moeda, long quantidade) {
        if (moeda == null || quantidade <= 0)
            return;
        distribuir(getValorTotalEmLatao() + quantidade * moeda.getValorEmLatao());
    }

    public void adicionarOuro(int valor) {
        adicionar(Moeda.OURO, valor);
    }

    public void adicionarPrata(int valor) {
        adicionar(Moeda.PRATA, valor);
    }

    public void adicionarCobre(int valor) {
        adicionar(Moeda.COBRE, valor);
    }

    public void adicionarMoedas(int ouro, int prata, int cobre) {
        long total = getValorTotalEmLatao();
        if (ouro > 0)
            total += ouro * Moeda.OURO.getValorEmLatao();
        if (prata > 0)
            total += prata * Moeda.PRATA.getValorEmLatao();
        if (cobre > 0)
            total += cobre * Moeda.COBRE.getValorEmLatao();
        distribuir(total);
    }

    /**
     * Recebe um valor expresso em ouro (pode ter fração), convertendo-o
     * corretamente para todas as moedas menores sem perda de valor.
     */
    public void receber(double valorEmOuro) {
        receberLatao(ouroParaLatao(valorEmOuro));
    }

    /**
     * Recebe um valor exato em latão (menor unidade), sem qualquer arredondamento.
     * Valores menores ou iguais a zero são ignorados.
     */
    public void receberLatao(long valorEmLatao) {
        if (valorEmLatao <= 0)
            return;
        distribuir(getValorTotalEmLatao() + valorEmLatao);
    }

    /**
     * Calcula o valor líquido total convertido em latão (menor unidade).
     */
    public long getValorTotalEmLatao() {
        return (long) edito * Moeda.EDITO.getValorEmLatao()
                + (long) talento * Moeda.TALENTO.getValorEmLatao()
                + (long) diamante * Moeda.DIAMANTE.getValorEmLatao()
                + (long) cobalto * Moeda.COBALTO.getValorEmLatao()
                + (long) platina * Moeda.PLATINA.getValorEmLatao()
                + (long) ouro * Moeda.OURO.getValorEmLatao()
                + (long) prata * Moeda.PRATA.getValorEmLatao()
                + (long) cobre * Moeda.COBRE.getValorEmLatao()
                + (long) ferro * Moeda.FERRO.getValorEmLatao()
                + (long) latao;
    }

    /**
     * Calcula o valor total equivalente em moedas de ouro (decimal).
     */
    public double getValorTotalEmOuro() {
        return getValorTotalEmLatao() / (double) LATAO_POR_OURO;
    }

    /**
     * Verifica se a carteira possui saldo suficiente para determinado valor em
     * ouro.
     */
    public boolean temSaldo(double valorEmOuro) {
        return temSaldoEmLatao(ouroParaLatao(valorEmOuro));
    }

    /**
     * Verifica se a carteira possui saldo suficiente para um valor exato em latão.
     */
    public boolean temSaldoEmLatao(long valorEmLatao) {
        return getValorTotalEmLatao() >= valorEmLatao;
    }

    /**
     * Realiza um pagamento, deduzindo do saldo total disponível com conversão de
     * troco.
     * 
     * @param valorEmOuro valor a pagar, em ouro (PO)
     * @return true se o pagamento foi bem-sucedido, false se faltar saldo.
     */
    public boolean pagar(double valorEmOuro) {
        if (valorEmOuro < 0)
            return false;
        return pagarLatao(ouroParaLatao(valorEmOuro));
    }

    /**
     * Realiza um pagamento de um valor exato em latão, com conversão de troco.
     * 
     * @param valorEmLatao valor a pagar, em latão (menor unidade)
     * @return true se o pagamento foi bem-sucedido, false se faltar saldo ou se o
     *         valor for negativo.
     */
    public boolean pagarLatao(long valorEmLatao) {
        if (valorEmLatao < 0)
            return false;
        long saldo = getValorTotalEmLatao();
        if (saldo < valorEmLatao)
            return false;
        distribuir(saldo - valorEmLatao);
        return true;
    }

    // ------------------------------------------------------------------
    // Getters / Setters
    // ------------------------------------------------------------------

    public int getQuantidade(Moeda moeda) {
        return switch (moeda) {
            case EDITO -> edito;
            case TALENTO -> talento;
            case DIAMANTE -> diamante;
            case COBALTO -> cobalto;
            case PLATINA -> platina;
            case OURO -> ouro;
            case PRATA -> prata;
            case COBRE -> cobre;
            case FERRO -> ferro;
            case LATAO -> latao;
        };
    }

    public void setQuantidade(Moeda moeda, int quantidade) {
        definirCampo(moeda, Math.max(0, quantidade));
        normalizarMoedas();
    }

    // ------------------------------------------------------------------
    // Moedas físicas (sem conversão automática)
    // ------------------------------------------------------------------

    /**
     * Define a quantidade física de uma moeda SEM normalizar o saldo: 15 PP
     * continuam sendo 15 PP. Usado pela ficha do personagem, onde cada
     * denominação é contada separadamente.
     */
    public void definirQuantidadeFisica(Moeda moeda, int quantidade) {
        if (moeda == null)
            return;
        definirCampo(moeda, Math.max(0, quantidade));
    }

    /**
     * Serializa as moedas físicas no formato {@code "LATAO=3,FERRO=0,...,EDITO=0"}.
     */
    public String serializarMoedasFisicas() {
        StringBuilder sb = new StringBuilder();
        for (Moeda m : Moeda.values()) {
            if (sb.length() > 0)
                sb.append(',');
            sb.append(m.name()).append('=').append(getQuantidade(m));
        }
        return sb.toString();
    }

    /**
     * Reconstrói uma carteira a partir de {@link #serializarMoedasFisicas()},
     * preservando cada denominação exatamente como foi salva. Entradas
     * desconhecidas ou inválidas são ignoradas.
     */
    public static Carteira deMoedasFisicas(String serializado) {
        Carteira c = new Carteira();
        if (serializado == null || serializado.isBlank())
            return c;
        for (String par : serializado.split(",")) {
            String[] kv = par.split("=", 2);
            if (kv.length != 2)
                continue;
            try {
                c.definirQuantidadeFisica(Moeda.valueOf(kv[0].trim()), Integer.parseInt(kv[1].trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return c;
    }

    public int getEdito() {
        return edito;
    }

    public int getTalento() {
        return talento;
    }

    public int getDiamante() {
        return diamante;
    }

    public int getCobalto() {
        return cobalto;
    }

    public int getPlatina() {
        return platina;
    }

    public int getOuro() {
        return ouro;
    }

    public void setOuro(int ouro) {
        setQuantidade(Moeda.OURO, ouro);
    }

    public int getPrata() {
        return prata;
    }

    public void setPrata(int prata) {
        setQuantidade(Moeda.PRATA, prata);
    }

    public int getCobre() {
        return cobre;
    }

    public void setCobre(int cobre) {
        setQuantidade(Moeda.COBRE, cobre);
    }

    public int getFerro() {
        return ferro;
    }

    public int getLatao() {
        return latao;
    }

    /**
     * Descreve apenas as moedas com quantidade maior que zero, da maior para a
     * menor (ex.: {@code "1 PO, 4 PP, 6 PC"}). Carteira vazia retorna "0 PO".
     */
    public String descreverMoedas() {
        StringBuilder sb = new StringBuilder();
        Moeda[] moedas = Moeda.values();
        for (int i = moedas.length - 1; i >= 0; i--) {
            int qtd = getQuantidade(moedas[i]);
            if (qtd > 0) {
                if (sb.length() > 0)
                    sb.append(", ");
                sb.append(qtd).append(' ').append(moedas[i].getSigla());
            }
        }
        if (sb.length() == 0)
            sb.append("0 ").append(Moeda.OURO.getSigla());
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("%s (Equivalente: %.4f PO)", descreverMoedas(), getValorTotalEmOuro());
    }
}
