package economia;

import geografia.Cidade;
import geografia.Conexao;
import java.util.List;

public class RotaComercial {
    private final List<Cidade> caminhoCidades;
    private final List<Conexao> arestas;
    private final int distanciaTotalKm;
    private final double perigoMedio;
    private final int perigoMaximo;

    public RotaComercial(List<Cidade> caminhoCidades, List<Conexao> arestas) {
        this.caminhoCidades = caminhoCidades;
        this.arestas = arestas;

        int somaDist = 0;
        double somaPerigoPonderado = 0;
        int maxPerigo = 0;

        for (Conexao c : arestas) {
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
     * Calcula o custo do frete em Ouro (PO) com base na distância, peso da carga e periculosidade.
     */
    public double calcularCustoFrete(double pesoTotalKg) {
        double taxaBasePorKm = 0.02; // 2 PC por km base
        double fatorPeso = 1.0 + (pesoTotalKg / 50.0); // A cada 50kg encarece o transporte
        double fatorRisco = 1.0 + (perigoMedio * 0.15); // +15% por nível de perigo médio
        return Math.round((distanciaTotalKm * taxaBasePorKm * fatorPeso * fatorRisco) * 100.0) / 100.0;
    }

    /**
     * Calcula o custo para contratar uma escolta com base na distância e no risco da rota.
     */
    public double calcularCustoEscolta() {
        double basePorKm = 0.05;
        return Math.round((distanciaTotalKm * basePorKm * (1.0 + (perigoMedio * 0.25))) * 100.0) / 100.0;
    }

    /**
     * Retorna se a rota exige escolta (ex: perigo médio >= 4.0 ou pico de perigo >= 6).
     */
    public boolean recomendaEscolta() {
        return perigoMedio >= 4.0 || perigoMaximo >= 6;
    }

    public List<Cidade> getCaminhoCidades() { return caminhoCidades; }
    public List<Conexao> getArestas() { return arestas; }
    public int getDistanciaTotalKm() { return distanciaTotalKm; }
    public double getPerigoMedio() { return perigoMedio; }
    public int getPerigoMaximo() { return perigoMaximo; }
}