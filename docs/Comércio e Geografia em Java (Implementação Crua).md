# ☕ Arquitetura Crua do Java: Comércio e Geografia em Grafos

Este documento detalha **exatamente o que já está implementado em código Java puro** nos pacotes [`src/geografia`](file:///c:/Users/pedro/Desktop/Codigos/rpg/src/geografia) e [`src/economia`](file:///c:/Users/pedro/Desktop/Codigos/rpg/src/economia), analisando as classes, atributos, estruturas de dados, algoritmos embutidos e o fluxo de transações financeiras.

---

## 🗺️ 1. O Grafo Territorial (`src/geografia`)

O território de Thaluria é modelado matematicamente como um **Grafo Ponderado Bidirecional**:
* **Vértices ($V$):** Instâncias de [`Cidade.java`](file:///c:/Users/pedro/Desktop/Codigos/rpg/src/geografia/Cidade.java).
* **Arestas ($E$):** Instâncias de [`Conexao.java`](file:///c:/Users/pedro/Desktop/Codigos/rpg/src/geografia/Conexao.java).

```
   [Cidade A: Frostdale] (Vértice)
       │  ▲
       │  │ Conexao (Aresta Ponderada: 120km, Perigo 3, Terrestre)
       ▼  │
   [Cidade B: Ironhold]  (Vértice)
       ├── Loja 1: "Forja Ancestral" (Estoque + Carteira)
       └── Loja 2: "Empório da Rede Norte" [lojaDeRede = true]
```

---

### 1.1. `Cidade.java` — O Vértice do Grafo e Entreposto
Representa qualquer assentamento humano, élfico, anão ou tribal.

#### Atributos Implementados:
| Modificador | Tipo | Campo | Descrição |
| :--- | :--- | :--- | :--- |
| `private` | `int` | `id` | Identificador único numérico do assentamento |
| `private` | `String` | `nome` | Nome da cidade (padrão: `"Cidade Sem Nome"`) |
| `private` | `TipoAssentamento` | `tipo` | Enum: `CIDADE`, `REINO`, `VILA`, `FORTALEZA` |
| `private` | `Regiao` | `regiao` | Enum do bioma climático principal |
| `private` | `Geologia` | `geologia` | Enum do tipo de relevo |
| `private` | `Aquifero` | `aquifero` | Enum da bacia hidrográfica local |
| `private` | `boolean` | `porto` | Flag que habilita atracamento naval |
| `private` | `boolean` | `fronteira` | Flag indicando se toca outro território |
| `private` | `Regiao` | `regiaofronteira` | Bioma vizinho caso seja fronteira |
| `private final` | `List<Conexao>` | `conexoes` | **Lista de Adjacência do Grafo** |
| `private final` | `List<Lojas>` | `lojas` | Lista de comércios sediados na cidade |

#### Métodos de Grafo e Negócio:
1. **Gerenciamento de Topologia:**
   * `adicionarConexao(Conexao c)`: Insere a aresta na lista de adjacências sem permitir duplicatas.
   * `removerConexao(Conexao c)`: Remove a aresta da cidade.
   * `listarCidadesVizinhas()`: Itera pelas conexões, filtra as ativas (`!c.isBloqueada()`) e invoca `c.outraPonta(this)`, retornando apenas as cidades diretamente alcançáveis.
2. **Gerenciamento de Lojas:**
   * `adicionarLoja(Lojas loja)` / `removerLoja(Lojas loja)` / `getLojas()`.
3. **Algoritmos Nativos Implementados:**
   * **Merge Sort ($O(n \log n)$):** `ordenarCidadesPorId(Cidade[] cidades)` — Ordena vetores de cidades por ID de forma recursiva dividindo em metades e intercalando (`merge`).
   * **Busca Binária ($O(\log n)$):** `buscarCidadePorId(Cidade[] cidades, int id)` — Localiza uma cidade em vetor ordenado dividindo o espaço de busca recursivamente pelo índice médio.

---

### 1.2. `Conexao.java` — A Aresta Ponderada e Restritiva
Modela as estradas terrestres e rotas de navegação marítima entre dois assentamentos.

#### Atributos Implementados:
| Modificador | Tipo | Campo | Descrição |
| :--- | :--- | :--- | :--- |
| `private final` | `Cidade` | `origem` | Vértice $u$ da conexão |
| `private final` | `Cidade` | `destino` | Vértice $v$ da conexão |
| `private final` | `int` | `distanciaKm` | Peso de extensão ($W_1 > 0$) |
| `private final` | `int` | `perigo` | Peso de risco ($W_2 \in [1, 10]$) |
| `private final` | `TipoDeRota` | `tipo` | Enum: `TERRESTRE` ou `MARITIMA` |
| `private` | `boolean` | `bloqueada` | Flag que invalida temporariamente o trânsito |

#### Regras e Travas de Integridade no Construtor:
* **Anti-Nulo:** Lança exceção se `origem` ou `destino` forem nulos.
* **Anti-Laço (No Self-Loops):** Lança exceção se `origem.equals(destino)`.
* **Validação de Pesos:** Obriga `distanciaKm > 0` e `perigo` entre 1 e 10.
* **Consistência Modal (Portos Obrigatórios):** Se `tipo == MARITIMA`, exige obrigatoriamente que `origem.temPorto() && destino.temPorto()`. Caso contrário, recusa a criação da aresta.
* **Auto-Inscrição:** Ao ser instanciada, a conexão se registra automaticamente nas listas de adjacência de ambas as cidades (`origem.adicionarConexao(this)` e `destino.adicionarConexao(this)`).

#### Navegação Simétrica:
* `outraPonta(Cidade c)`: Permite caminhar pela aresta de forma agnóstica: se você fornecer `origem`, ela entrega `destino`; se fornecer `destino`, ela entrega `origem`.
* `equals()` e `hashCode()` Simétricos: `(A <-> B)` é considerado idêntico a `(B <-> A)`.
* `bloquear()` e `desbloquear()`: Permite dinamicamente interromper passagens por guerras ou desmoronamentos.

---

### 1.3. Enums Ambientais Acoplados aos Vértices
* **`Regiao.java`:** 7 Biomas canônicos (`POLAR`, `TUNDRA`, `TAIGA`, `TEMPERADO`, `TROPICAL`, `SAVANA`, `DESERTO`) com descrições e método `parse(String)` case-insensitive.
* **`Geologia.java`:** 7 Relevos (`MONTANHA`, `COLINA`, `PLANICIE`, `CAVERNA`, `VALE`, `CANYON`, `FLORESTA`).
* **`Aquifero.java`:** 6 Bacias (`LAGO_PEQUENO`, `LAGO_GRANDE`, `RIO`, `OCEANO_AGUAS_PROFUNDAS`, `OCEANO_AGUAS_COSTEIRAS`, `PANTANO`).

---

## 💰 2. O Sistema Econômico Cru (`src/economia`)

O módulo econômico provê gestão de liquidez financeira, tipagem de itens físicos e entrepostos de compra e venda.

```
       [Comprador / Aventureiro]                 [Loja / Entreposto]
       Carteira: 12 PO, 4 PP, 8 PC              Caixa: 50 PO (Reserva)
                │                               Estoque: [Espada x1, Poção x5]
                │                                        │
                ├─── 1. venderItem(Espada, Carteira) ────┤
                │    - Carteira paga 15 PO               │
                │    - Caixa recebe 15 PO                │
                │    - Estoque remove 1x Espada          │
                ▼                                        ▼
       [Espada transferida]                     [Caixa: 65 PO]
```

---

### 2.1. `Carteira.java` — Sistema Financeiro Decimal
Modela os fundos de dinheiro de personagens, mercadores ou cofres públicos.

#### Escala Cambial:
$$1\text{ Moeda de Ouro (PO)} = 10\text{ Moedas de Prata (PP)} = 100\text{ Moedas de Cobre (PC)}$$

#### Atributos:
* `private int ouro`
* `private int prata`
* `private int cobre`

#### Lógica de Conversão e Câmbio:
* `normalizarMoedas()`: Converte automaticamente excedentes de moedas inferiores para a unidade de maior grandeza (ex: 250 PC vira 2 PO e 5 PP).
* `getValorTotalEmCobre()`: Retorna o patrimônio líquido total reduzido à menor unidade:
  $$\text{Total Cobre} = (Ouro \times 100) + (Prata \times 10) + Cobre$$
* `getValorTotalEmOuro()`: Retorna o saldo expresso em valor fracionário decimal de PO ($Ouro + \frac{Prata}{10} + \frac{Cobre}{100}$).
* `temSaldo(double valorEmOuro)`: Avalia se o total de cobre acumulado cobre o custo convertido.
* `pagar(double valorEmOuro)`: **Transação Atômica.** Converte o débito em cobre, valida o saldo, deduz do total e reconverte o troco exato redistribuindo entre ouro, prata e cobre. Retorna `false` sem alterar valores se o saldo for insuficiente.

---

### 2.2. `Item.java` — Mercadorias e Equipamentos
Representa os bens que circulam no mundo e ocupam espaço/peso.

#### Atributos:
* `int id`: Identificador do item.
* `String nome`: Nome da mercadoria.
* `String descricao`: Detalhes de manufatura ou lore.
* `double preco`: Preço base de tabela em moedas de ouro (PO).
* `double peso`: Peso unitário em quilogramas (kg).
* `TipoItem tipo`: Categorização em 8 tipos (`ARMA`, `ARMADURA`, `ESCUDO`, `CONSUMIVEL`, `ACESSORIO`, `MATERIAL`, `MAGICO`, `OUTRO`).
* `RaridadeItem raridade`: 5 patamares (`COMUM`, `INCOMUM`, `RARO`, `EPICO`, `LENDARIO`).
* `int quantidade`: Contador de empilhamento do item.

#### Métodos de Inventário:
* `adicionarQuantidade(int qtd)` / `removerQuantidade(int qtd)`: Gerenciam empilhamento sem precisar instanciar múltiplos objetos.
* `getPesoTotal()`: $\text{peso} \times \text{quantidade}$.
* `getValorTotal()`: $\text{preco} \times \text{quantidade}$.
* `equals()` e `hashCode()`: Comparação baseada em `id` e `nome`.

---

### 2.3. `Lojas.java` — O Entreposto Comercial
Representa o agente econômico fixado dentro de uma cidade.

#### Atributos:
* `String nome`: Nome fantasia do estabelecimento (ex: `"Armazém do Porto"`).
* `String mercador`: Nome do lojista ou responsável.
* `boolean lojaDeRede`: Flag indicando se a loja faz parte de uma franquia/guilda comercial.
* `String nomeDaRede`: Nome da corporação de mercadores (ex: `"Companhia das Estepes"`).
* `List<Item> estoque`: Inventário de itens disponíveis para venda.
* `Carteira caixa`: Fundo de caixa próprio (inicializado por padrão com 50 PO).

#### Mecânica de Compra e Venda:
1. **Adição ao Estoque:**
   * `adicionarItem(Item item)`: Percorre o estoque. Se encontrar um item idêntico via `equals()`, apenas incrementa a `quantidade`. Caso contrário, insere na lista.
2. **Venda para Cliente (`venderItem`):**
   * Valida se o item está presente no estoque.
   * Cobra o cliente através de `carteiraCliente.pagar(item.getPreco())`.
   * Se o pagamento for aprovado, o caixa da loja recebe o ouro (`caixa.adicionarMoedas((int) preco, 0, 0)`).
   * Reduz 1 unidade do estoque do item. Se chegar a 0, remove o objeto da lista.
3. **Compra de Cliente (`comprarItem`):**
   * A loja avalia se tem fundos em seu próprio caixa via `caixa.pagar(precoCompra)`.
   * Se puder pagar, repassa o ouro para a carteira do aventureiro/fornecedor (`carteiraCliente.adicionarOuro()`).
   * Adiciona o item recém-comprado ao seu próprio estoque.

---

## ⚡ 3. Demonstração Prática: Como o Código Opera Hoje

O trecho abaixo exemplifica uma execução real utilizando **apenas o código Java que já existe**:

```java
// 1. Criando as Cidades (Vértices)
Cidade frostdale = new Cidade(1, "Frostdale", Cidade.TipoAssentamento.CIDADE, 
                              Regiao.TUNDRA, Geologia.MONTANHA, Aquifero.RIO, 
                              true, false, null);

Cidade portoreal = new Cidade(2, "Porto Real", Cidade.TipoAssentamento.REINO, 
                              Regiao.TEMPERADO, Geologia.PLANICIE, Aquifero.OCEANO_AGUAS_COSTEIRAS, 
                              true, false, null);

// 2. Conectando as Cidades (Aresta Naval)
Conexao rotaMaritima = new Conexao(frostdale, portoreal, 180, 2, Conexao.TipoDeRota.MARITIMA);

// 3. Criando uma Loja de Rede em Frostdale
Lojas empórioNorte = new Lojas("Empório Glacial", "Thorin", true, "Liga Mercantil");
Item ferro = new Item(101, "Lingote de Ferro", 5.0); // 5 PO cada
empórioNorte.adicionarItem(ferro);
frostdale.adicionarLoja(empórioNorte);

// 4. Simulando a Compra por um Personagem
Carteira carteiraJogador = new Carteira(10, 0, 0); // 10 PO
boolean comprou = empórioNorte.venderItem(ferro, carteiraJogador);

// Resultado:
// comprou == true
// carteiraJogador fica com 5 PO
// empórioNorte.caixa fica com 55 PO (50 base + 5 da venda)
```

---

## 🔬 4. Resumo das Capacidades e Gaps do Código Atual

### ✅ O Que Já Está 100% Funcional no Código:
1. **Topologia Básica:** Criação de redes de cidades com adjacências e restrições modais (portos para rotas marítimas).
2. **Navegação Local:** Uma cidade sabe responder quem são suas vizinhas imediatas e desbloqueadas.
3. **Algoritmos Utilitários:** Ordenação rápida de cidades por ID (MergeSort) e busca rápida (Busca Binária).
4. **Matemática Monetária Confiável:** Pagamentos sem erros de arredondamento, cálculo atômico de troco via cobre e conversões de grandeza.
5. **Comércio Local Atômico:** Comprar e vender itens em uma loja física debitando e creditando carteiras e gerenciando estoques empilháveis.

### ❌ O Que Não Está Implementado no Java (Gaps):
1. **Nenhum Algoritmo Global de Grafo:** Não existe uma classe que encontre o caminho entre Frostdale e uma cidade a 4 conexões de distância (falta Dijkstra / Busca em Largura).
2. **Preço Estático:** O preço do `Item` é absoluto (`preco`), não variando se a cidade está no Deserto ou na Floresta.
3. **Sem Transporte:** A mercadoria não "viaja" pelas arestas; não existe entidade `Caravana` com peso de carga, velocidade e risco de saque.
4. **Rede Inativa:** Os atributos `lojaDeRede` e `nomeDaRede` em `Lojas` existem como dados cadastrais, mas não possuem métodos para troca de mercadorias entre lojas irmãs.
