package geografia;

import economia.RotaComercial;
import java.util.*;

/**
 * Utilitário para navegação e busca de caminhos no grafo de cidades e conexões de Thaluria.
 * Utiliza o algoritmo de Dijkstra ponderando distância e o nível de perigo das rotas.
 */
public class NavegadorGrafo {

    /**
     * Encontra a melhor rota entre duas cidades usando Dijkstra com peso de perigo padrão (0.25).
     *
     * @param origem  cidade de partida
     * @param destino cidade de chegada
     * @return RotaComercial com o caminho ótimo de cidades e arestas, ou null se não houver caminho
     */
    public static RotaComercial calcularMelhorRota(Cidade origem, Cidade destino) {
        return calcularMelhorRota(origem, destino, 0.25);
    }

    /**
     * Encontra a melhor rota entre duas cidades usando Dijkstra (ponderando distância e perigo)
     * e retorna uma instância da classe RotaComercial v2.
     *
     * @param origem     cidade de partida
     * @param destino    cidade de chegada
     * @param pesoPerigo multiplicador de peso para cada ponto de perigo da aresta (ex: 0.25)
     * @return RotaComercial pronta com indicadores agregados, ou null se não houver caminho
     */
    public static RotaComercial calcularMelhorRota(Cidade origem, Cidade destino, double pesoPerigo) {
        if (origem == null || destino == null || origem.equals(destino)) {
            return null;
        }

        Map<Cidade, Double> custos = new HashMap<>();
        Map<Cidade, Cidade> anteriorCidade = new HashMap<>();
        Map<Cidade, Conexao> anteriorAresta = new HashMap<>();
        Set<Cidade> visitados = new HashSet<>();

        PriorityQueue<Cidade> fila = new PriorityQueue<>(
                Comparator.comparingDouble(c -> custos.getOrDefault(c, Double.MAX_VALUE))
        );

        custos.put(origem, 0.0);
        fila.add(origem);

        while (!fila.isEmpty()) {
            Cidade atual = fila.poll();

            if (!visitados.add(atual)) continue;
            if (atual.equals(destino)) break;

            for (Conexao conexao : atual.getConexoes()) {
                if (conexao.isBloqueada()) continue;

                Cidade vizinha = conexao.outraPonta(atual);
                if (visitados.contains(vizinha)) continue;

                double custoAresta = conexao.getDistanciaKm() * (1.0 + (conexao.getPerigo() * pesoPerigo));
                double novoCusto = custos.get(atual) + custoAresta;

                if (novoCusto < custos.getOrDefault(vizinha, Double.MAX_VALUE)) {
                    custos.put(vizinha, novoCusto);
                    anteriorCidade.put(vizinha, atual);
                    anteriorAresta.put(vizinha, conexao);
                    fila.add(vizinha);
                }
            }
        }

        if (!anteriorCidade.containsKey(destino)) {
            return null; // Sem caminho possível ou rotas bloqueadas
        }

        LinkedList<Cidade> caminhoCidades = new LinkedList<>();
        LinkedList<Conexao> caminhoArestas = new LinkedList<>();
        Cidade passo = destino;

        while (passo != null) {
            caminhoCidades.addFirst(passo);
            Conexao aresta = anteriorAresta.get(passo);
            if (aresta != null) {
                caminhoArestas.addFirst(aresta);
            }
            passo = anteriorCidade.get(passo);
        }

        return new RotaComercial(caminhoCidades, caminhoArestas);
    }
}
