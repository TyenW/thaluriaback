package economia;

/**
 * Gerencia o sistema financeiro, saldo e moedas de personagens ou comércios em Thaluria.
 * Escala monetária:
 * 1 Moeda de Ouro (PO) = 10 Moedas de Prata (PP) = 100 Moedas de Cobre (PC).
 */
public class Carteira {
    private int ouro;
    private int prata;
    private int cobre;

    public Carteira() {
        this(0, 0, 0);
    }

    public Carteira(int ouro) {
        this(ouro, 0, 0);
    }

    public Carteira(int ouro, int prata, int cobre) {
        this.ouro = Math.max(0, ouro);
        this.prata = Math.max(0, prata);
        this.cobre = Math.max(0, cobre);
        normalizarMoedas();
    }

    /**
     * Converte excedentes de cobre e prata em moedas superiores.
     */
    public void normalizarMoedas() {
        if (this.cobre >= 100) {
            this.ouro += this.cobre / 100;
            this.cobre = this.cobre % 100;
        }
        if (this.cobre >= 10) {
            this.prata += this.cobre / 10;
            this.cobre = this.cobre % 10;
        }
        if (this.prata >= 10) {
            this.ouro += this.prata / 10;
            this.prata = this.prata % 10;
        }
    }

    public void adicionarOuro(int valor) {
        if (valor > 0) this.ouro += valor;
    }

    public void adicionarPrata(int valor) {
        if (valor > 0) {
            this.prata += valor;
            normalizarMoedas();
        }
    }

    public void adicionarCobre(int valor) {
        if (valor > 0) {
            this.cobre += valor;
            normalizarMoedas();
        }
    }

    public void adicionarMoedas(int ouro, int prata, int cobre) {
        if (ouro > 0) this.ouro += ouro;
        if (prata > 0) this.prata += prata;
        if (cobre > 0) this.cobre += cobre;
        normalizarMoedas();
    }

    /**
     * Calcula o valor líquido total convertido em cobre.
     */
    public int getValorTotalEmCobre() {
        return (this.ouro * 100) + (this.prata * 10) + this.cobre;
    }

    /**
     * Calcula o valor total equivalente em moedas de ouro (decimal).
     */
    public double getValorTotalEmOuro() {
        return this.ouro + (this.prata / 10.0) + (this.cobre / 100.0);
    }

    /**
     * Verifica se a carteira possui saldo suficiente para determinado valor em ouro.
     */
    public boolean temSaldo(double valorEmOuro) {
        int custoEmCobre = (int) Math.round(valorEmOuro * 100);
        return getValorTotalEmCobre() >= custoEmCobre;
    }

    /**
     * Realiza um pagamento, deduzindo do saldo total disponível com conversão de troco.
     * @return true se o pagamento foi bem-sucedido, false se faltar saldo.
     */
    public boolean pagar(double valorEmOuro) {
        int custoEmCobre = (int) Math.round(valorEmOuro * 100);
        int saldoCobre = getValorTotalEmCobre();

        if (saldoCobre < custoEmCobre) {
            return false;
        }

        int restante = saldoCobre - custoEmCobre;
        this.ouro = restante / 100;
        int resto = restante % 100;
        this.prata = resto / 10;
        this.cobre = resto % 10;
        return true;
    }

    public int getOuro() {
        return ouro;
    }

    public void setOuro(int ouro) {
        this.ouro = Math.max(0, ouro);
    }

    public int getPrata() {
        return prata;
    }

    public void setPrata(int prata) {
        this.prata = Math.max(0, prata);
        normalizarMoedas();
    }

    public int getCobre() {
        return cobre;
    }

    public void setCobre(int cobre) {
        this.cobre = Math.max(0, cobre);
        normalizarMoedas();
    }

    @Override
    public String toString() {
        return String.format("%d PO, %d PP, %d PC (Equivalente: %.2f PO)", ouro, prata, cobre, getValorTotalEmOuro());
    }
}
