# 🔮 Compêndio de Grafiturgia Elemental: Magia em Grafos

Este documento é o **Índice Mestre** do sistema de magia de Thaluria baseado na **Teoria dos Grafos**. O sistema foi modularizado em manuais específicos para facilitar a consulta rápida pelo Mestre e pelos jogadores.

---

## 📚 Estrutura Modular da Grafiturgia

Para aprofundar em cada aspecto das regras arcanas, consulte os manuais dedicados:

1. **[[Sistema de Magia/01 - Fundamentos e Limite Mental|🧠 01. Fundamentos e Limite Mental]]**
   * Conceito de Vértices ($V$), Arestas ($E$) e Pesos ($W$).
   * Fórmula do Limite Cognitivo do Conjurador: $|V| \le 2 + \text{Grau}_{\text{MEN}}$.
   * Mecânica de Sobrecarga Neural e sustentação de nós.

2. **[[Sistema de Magia/02 - Matriz Elemental e Regra das Pontes|🧪 02. Matriz Elemental e Regra das Pontes]]**
   * As 10 Essências Primordiais (Fogo, Água, Terra, Vento, Raio, Gelo, Luz, Sombra, Caos, Ordem).
   * Matriz de Adjacência com Pesos de Resistência ($W=1$, $W=2$, $W=\infty$).
   * A **Lei do Vértice Ponte**: como contornar e estabilizar elementos antagônicos.

3. **[[Sistema de Magia/03 - Topologias e Geometria dos Feitiços|📐 03. Topologias e Geometria dos Feitiços]]**
   * **Caminho/Ciclo Hamiltoniano:** Disparos e projéteis de foco único (bônus de ciclo fechado $+5 \times G$).
   * **Grafo Estrela / Euleriano:** Detonações e magias em área centrífugas (AoE).
   * **Grafo Regular ($K_n$):** Barreiras, escudos e armaduras com deflexão uniforme de impacto.
   * **Árvore / Ciclo com Ordem:** Maldições, dano contínuo e armadilhas persistentes.

4. **[[Sistema de Magia/04 - Protocolo do Mestre e Colapso de Grafo|⚖️ 04. Protocolo do Mestre e Colapso de Grafo]]**
   * O algoritmo de 4 passos para "compilar" feitiços de jogadores em mesa.
   * Equações canônicas de Mana e Dano:
     $$\text{Custo de Mana} = \left(\sum W\right) \times 2 \times \text{Grau}_{\text{magia}} \qquad\Big\vert\qquad \text{Dano} = |V| \text{ dados}$$
   * Tabela de Consequências para **Colapso de Grafo** e **Inversão de Feitiço**.

5. **[[Sistema de Magia/05 - Grimório Prático e Exemplos Compilados|📖 05. Grimório Prático e Exemplos Compilados]]**
   * 4 feitiços completos prontos para uso em combate: *Lança do Eclipse Gélido*, *Supernova Vulcânica*, *Baluarte de Diamante* e *Prisão Gravitacional*.

6. **[[Sistema de Magia/06 - Metamagia e Operações Avançadas de Grafo|🌸 06. Metamagia e Operações Avançadas de Grafo]]**
   * **Contração de Blossom:** Encapsulamento de ciclos ímpares em 2 estágios que ignoram armaduras.
   * **Emparelhamento Perfeito:** Combos cooperativos e fusão de feitiços entre dois conjuradores.
   * **Poda de Kruskal / MST:** Redução emergencial de arestas para economia instantânea de Mana.
   * **Vértices de Articulação:** Mecânica de *Dispel* cirúrgico e quebra de conectividade ($\kappa$).

7. **[[Ficha de Cadastro de Magia (Grafo Arcano)|📜 Template: Ficha de Cadastro de Magia]]**
   * Modelo em branco padronizado para jogadores e Mestres registrarem novas criações no Grimório.

---

## ⚡ Tabela Rápida de Adjacência Elemental

| Vértice Elemental | Natureza do Dano / Alvo | Harmônicos ($W=1$) | Neutros ($W=2$) | Opostos Diretos ($W=\infty$) |
| :--- | :--- | :--- | :--- | :--- |
| **🔥 Fogo (Ignis)** | Elemental $\rightarrow$ **HP** | Vento, Raio, Caos | Fogo, Terra, Luz, Sombra | **Água, Gelo** |
| **💧 Água (Aqua)** | Elemental $\rightarrow$ **HP** | Gelo, Terra, Ordem | Água, Vento, Raio, Luz | **Fogo, Raio\*** _(se pura)_ |
| **🌱 Terra (Terra)** | Elemental $\rightarrow$ **HP** | Água, Fogo, Ordem | Terra, Gelo, Luz, Sombra | **Vento, Raio** |
| **🌪️ Vento (Aer)** | Elemental $\rightarrow$ **HP** | Fogo, Raio, Gelo | Vento, Água, Caos, Luz | **Terra, Ordem** |
| **⚡ Raio (Fulmen)** | Elemental $\rightarrow$ **HP** | Fogo, Vento, Água | Raio, Luz, Caos, Ordem | **Terra** |
| **❄️ Gelo (Glacies)** | Elemental $\rightarrow$ **HP** | Água, Vento, Ordem | Gelo, Terra, Sombra | **Fogo, Caos** |
| **☀️ Luz (Lux)** | Elemental / Oculto $\rightarrow$ **HP** | Fogo, Raio, Ordem | Luz, Água, Terra, Vento | **Sombra, Caos** |
| **🌑 Sombra (Umbra)** | Oculto / Psíquico $\rightarrow$ **HP ou SAN** | Gelo, Caos | Sombra, Terra, Fogo, Água | **Luz, Ordem** |
| **👁️ Caos (Psicose)** | Psíquico $\rightarrow$ **SAN** | Fogo, Sombra | Caos, Vento, Raio | **Ordem, Gelo, Luz** |
| **⚖️ Ordem (Vetor)** | Mental / Neutro _(Estrutural)_ | Água, Terra, Gelo, Luz | Ordem, Raio, Fogo, Sombra | **Caos, Vento** |

---

## 🔍 Resumo do Protocolo de Compilação

```mermaid
graph LR
    P1["Passo 1: Limite Mental (|V|)"] --> P2["Passo 2: Topologia (Formato)"]
    P2 --> P3["Passo 3: Arestas e Custos (Mana & Dano)"]
    P3 --> P4["Passo 4: Veredito e Efeito Narrativo"]
```

> [!TIP]
> Para registrar novos feitiços no grimório do seu personagem ou catalogar magias de NPCs, utilize a [[Ficha de Cadastro de Magia (Grafo Arcano)]].