	# ⚔️ RPG Thaluria: Sistema & Motor

Repositório central do projeto **RPG Thaluria**, estruturado de forma modular em documentação de regras (Obsidian Vault) e implementação em código-fonte (Java).

---

## 📁 Estrutura do Repositório

```plaintext
rpg/
├── docs/                        # 📚 Regras, Lore e Manuais (Cofre do Obsidian)
│   ├── Compêndio de Raças por Reino.md       # Catálogo geral de espécies por biomas
│   ├── Compêndio de Grafiturgia Elemental.md # Índice mestre do sistema de magia
│   ├── Status.md                             # Atributos, recursos, perícias e combate
│   │
│   ├── raças/                   # 🧬 Fichas detalhadas de cada raça
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
│   ├── Sistema de Magia/        # 🔮 Módulos de Grafiturgia (Teoria de Grafos)
│   │   ├── 01 - Fundamentos e Limite Mental.md
│   │   ├── 02 - Matriz Elemental e Regra das Pontes.md
│   │   ├── 03 - Topologias e Geometria dos Feitiços.md
│   │   ├── 04 - Protocolo do Mestre e Colapso de Grafo.md
│   │   ├── 05 - Grimório Prático e Exemplos Compilados.md
│   │   └── 06 - Metamagia e Operações Avançadas de Grafo.md
│   │
│   └── templates/               # 📜 Fichas modelo para novos cadastros
│       ├── Ficha de Cadastro de Raça.md
│       └── Ficha de Cadastro de Magia (Grafo Arcano).md
│
├── src/                         # ☕ Código-Fonte em Java (Motor / Entidades)
│   ├── geografia/               # 🗺️ Mundo, relevo, hidrografia e rotas
│   │   ├── Cidade.java          # Assentamentos, portos, fronteiras e conexões
│   │   ├── Conexao.java         # Rotas terrestres/marítimas, distância e perigo
│   │   ├── Regiao.java          # Biomas (Polar, Tundra, Taiga, Savana, Deserto...)
│   │   ├── Aquifero.java        # Corpos hídricos (Rios, lagos, oceanos, pântano)
│   │   └── Geologia.java        # Relevo (Montanha, colina, planície, caverna...)
│   │
│   ├── economia/                # 💰 Comércio, moedas e itens
│   │   ├── Carteira.java        # Sistema financeiro / conversão de moedas (PO, PP, PC)
│   │   ├── Item.java            # Itens, equipamentos, tipos, raridade e peso
│   │   └── Lojas.java           # Mercadores, redes comerciais, compras e vendas
│   │
│   └── personagem/              # 👤 Atributos e fichas de personagens
│       └── Ficha.java           # Implementação canônica das fórmulas de Status.md
│
└── README.md                    # Guia geral do projeto
```

---

## 🚀 Como Utilizar

### 1. Documentação e Regras (Obsidian)
* Abra o software **Obsidian**.
* Selecione **"Open folder as vault"** e aponte para a pasta `docs/` (ou para a raiz `rpg/`).
* Toda a navegação por wikilinks (`[[...]]`), diagramas Mermaid e fórmulas matemáticas em LaTeX renderizam nativamente.

### 2. Motor Java (`src/`)
Para compilar todas as classes do motor sem dependências externas:
```bash
cd src
javac geografia/*.java economia/*.java personagem/*.java
```
