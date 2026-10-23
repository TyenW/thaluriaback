# ⚔️ RPG Thaluria: Sistema & Motor

Repositório central do projeto **RPG Thaluria**, estruturado de forma modular em documentação de regras (Obsidian Vault) e implementação em código-fonte (Java puro, zero dependências externas).

---

## 📁 Estrutura do Repositório

```plaintext
rpg/
├── docs/                                     # 📚 Regras, Lore e Manuais (Cofre do Obsidian)
│   ├── Compêndio de Economia, Comércio e Crafting.md # Índice mestre da economia e manufatura
│   ├── Compêndio de Grafiturgia Elemental.md # Índice mestre do sistema de magia
│   ├── Compêndio de Raças por Reino.md       # Catálogo geral de espécies por biomas
│   ├── Relatório Geral do Projeto.md         # Diagnóstico arquitetural consolidado
│   ├── Status.md                             # Atributos, recursos, perícias e combate
│   │
│   ├── Sistema de Economia/                  # 💰 Módulos Econômicos e Manufatura (v2 a v6)
│   │   ├── 01 - Base Monetária e Escala de Latão v2.md
│   │   ├── 02 - Sistema de Vendas e Caravanas v3.md
│   │   ├── 03 - Itens, Crafting e Gestão de Lojas v4-v6.md
│   │   └── Arquivo Histórico/
│   │       ├── Comércio e Geografia em Java (Implementação Crua).md
│   │       └── Visão Inicial e Requisitos de Projeto (Prompt Original).md
│   │
│   ├── Sistema de Magia/                     # 🔮 Módulos de Grafiturgia (Teoria de Grafos)
│   │   ├── 01 - Fundamentos e Limite Mental.md
│   │   ├── 02 - Matriz Elemental e Regra das Pontes.md
│   │   ├── 03 - Topologias e Geometria dos Feitiços.md
│   │   ├── 04 - Protocolo do Mestre e Colapso de Grafo.md
│   │   ├── 05 - Grimório Prático e Exemplos Compilados.md
│   │   └── 06 - Metamagia e Operações Avançadas de Grafo.md
│   │
│   ├── raças/                                # 🧬 Fichas detalhadas de cada raça
│   │   ├── Anões (Dwarves).md
│   │   ├── Draconatos (Dragonborn).md
│   │   ├── Drow (Elfos Negros).md
│   │   ├── Elfos da Floresta (Wood Elves).md
│   │   ├── Fadas (Pixies - Fairy).md
│   │   ├── Genasi do Fogo (Fire Genasi).md
│   │   ├── Genasi do Gelo e Água (Frost and Water Genasi).md
│   │   ├── Homens-Lagarto (Lizardfolk).md
│   │   ├── Kobolds.md
│   │   └── Shadar-kai.md
│   │
│   └── templates/                            # 📜 Fichas modelo para novos cadastros
│       ├── Ficha de Cadastro de Item e Manufatura.md
│       ├── Ficha de Cadastro de Magia (Grafo Arcano).md
│       └── Ficha de Cadastro de Raça.md
│
├── src/                                      # ☕ Código-Fonte em Java (Motor / Entidades)
│   ├── geografia/                            # 🗺️ Assentamentos, rotas, biomas e Dijkstra
│   ├── economia/                             # 💰 10 moedas em Latão, lojas, caravanas, crafting v5 e gestão v6
│   ├── personagem/                           # 👤 Ficha canônica e cálculo de combate
│   ├── menu/                                 # 🎮 Menu de console e cenários de demonstração
│   └── teste/                                # 🧪 4 Suítes automatizadas (307 testes 100% PASS)
│
└── README.md                                 # Guia geral do projeto
```

---

## 🚀 Como Utilizar

### 1. Documentação e Regras (Obsidian)
* Abra o software **Obsidian**.
* Selecione **"Open folder as vault"** e aponte para a pasta `docs/` (ou para a raiz `rpg/`).
* Toda a navegação por wikilinks (`[[...]]`), diagramas Mermaid e fórmulas matemáticas em LaTeX renderizam nativamente.
* Os 3 compêndios na raiz de `docs/` fornecem índices centrais imediatos para Magia, Raças e Economia/Crafting.

### 2. Motor Java (`src/`)
Para compilar todas as classes do motor sem dependências externas:
```powershell
javac -cp "src" -d "out" src/geografia/*.java src/economia/*.java src/personagem/*.java src/menu/*.java src/teste/*.java
```

### 3. Execução dos Testes Automatizados (307 Testes):
```powershell
java -cp "out" teste.TesteEndToEnd
java -cp "out" teste.TesteItemV4
java -cp "out" teste.TesteCraftingV5
java -cp "out" teste.TesteGestaoLojasV6
```
