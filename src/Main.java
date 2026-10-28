import menu.MenuComercio;
import teste.TesteEndToEnd;

/**
 * ============================================================================
 *                    THALURIA RPG — SISTEMA ECONÔMICO & GEOGRÁFICO v3
 * ============================================================================
 * 
 * Ponto de entrada executável unificado do ecossistema econômico e comercial.
 * Todo o sistema foi construído em Java puro (sem frameworks pesados ou web)
 * e homologado através de uma bateria completa de Testes End-to-End (E2E).
 * 
 * ----------------------------------------------------------------------------
 * 📊 RESUMO DOS MÓDULOS E FUNCIONALIDADES VALIDADAS:
 * ----------------------------------------------------------------------------
 * 1. ESCALA MONETÁRIA (10 MOEDAS EM LATÃO):
 *    - 1 Édito = 1000 Talentos; demais moedas na proporção estrita 10:1.
 *    - Todas as operações contábeis, estoques e fretes são baseados em Latão (`long`),
 *      eliminando qualquer risco de inconsistência por ponto flutuante.
 * 
 * 2. SEIS TIPOS CANÔNICOS DE ESTABELECIMENTOS:
 *    - Forja, Alfaiataria, Loja Geral, Alquimia, Joalheria e Taverna (comidas/bebidas).
 *    - Estoques dinâmicos e precificação de balcão direta com aventureiros.
 * 
 * 3. PRECIFICAÇÃO DINÂMICA DE MERCADO:
 *    - Preço = PreçoBase * sqrt(Demanda / Oferta), limitado entre 40% (piso) e 250% (teto).
 *    - Compra de NPCs locais a 65% do preço dinâmico de venda.
 * 
 * 4. NAVEGAÇÃO TERRITORIAL NO GRAFO (DIJKSTRA):
 *    - Cidades como nós e Estradas/Rotas Marítimas como arestas ponderadas por
 *      custo = distKm * (1.0 + perigo * pesoPerigo).
 *    - Desvio dinâmico de conexões bloqueadas ou de perigo extremo.
 * 
 * 5. CARAVANAS E COMÉRCIO INTERMUNICIPAL:
 *    - Deslocamento físico hora a hora, na velocidade do serviço de transporte
 *      (Comum 6 km/h, Expresso 10, Comboio Blindado 4, Guarda Real 9); rotas marítimas bloqueadas.
 *    - Desconto de rede de 25% entre lojas da mesma aliança mercantil.
 *    - Modos de proteção: Desprotegida, Escolta NPC (-75% risco) e Escolta Jogador (100% segura).
 * 
 * 6. RISCOS, EXTRAVIOS E IMPACTO DINÂMICO NO MAPA:
 *    - Caravana emboscada tem localização exata retida na aresta do grafo.
 *    - Resgate bem-sucedido pacifica a estrada (-1 no nível de perigo).
 *    - Resgate fracassado fortalece os salteadores (+1 no nível de perigo).
 * 
 * 7. AUDITORIA CONTÁBIL (LIVRO-CAIXA):
 *    - Rastreamento imutável de todas as transações com carimbo [Dia X, Yh].
 * 
 * ----------------------------------------------------------------------------
 * 🚀 COMO EXECUTAR:
 * ----------------------------------------------------------------------------
 * Compilar:
 *   javac -d bin -sourcepath src src/Main.java src/menu/*.java src/economia/*.java src/geografia/*.java src/personagem/*.java src/teste/*.java
 * 
 * Executar Menu Interativo:
 *   java -cp bin Main
 * 
 * Executar Suite de Testes E2E (60/60 asserções validadas com 100% de sucesso):
 *   java -cp bin Main --test
 *   java -cp bin Main test
 *   java -cp bin Main -t
 * ============================================================================
 */
public class Main {

    public static void main(String[] args) {
        if (args.length > 0) {
            String arg = args[0].toLowerCase();
            if (arg.equals("--test") || arg.equals("test") || arg.equals("-t") || arg.equals("--e2e")) {
                TesteEndToEnd.main(args);
                return;
            } else if (arg.equals("--help") || arg.equals("-h") || arg.equals("help")) {
                exibirAjuda();
                return;
            }
        }

        // Executa o menu de console interativo padrão
        MenuComercio.main(args);
    }

    private static void exibirAjuda() {
        System.out.println("======================================================================");
        System.out.println("   ⚔️  THALURIA RPG — SISTEMA ECONÔMICO E GEOGRÁFICO v3               ");
        System.out.println("======================================================================");
        System.out.println("Comandos disponíveis:");
        System.out.println("  java -cp bin Main          -> Inicia o Menu Interativo no Terminal");
        System.out.println("  java -cp bin Main --test   -> Executa a bateria completa de Testes E2E (60 testes)");
        System.out.println("  java -cp bin Main --help   -> Exibe esta mensagem de ajuda");
        System.out.println("======================================================================");
    }
}
