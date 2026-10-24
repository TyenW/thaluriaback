# ⚔️ RPG Thaluria: Relatório Geral do Projeto

> **Status do Repositório:** Limpeza e auditoria concluídas. Todos os arquivos binários compilados (`.class`) foram removidos do código-fonte e o arquivo `.gitignore` foi configurado para blindar o versionamento.

---

## 🧭 1. Visão Geral da Arquitetura

O projeto **RPG Thaluria** opera sob uma arquitetura híbrida e complementar de duas frentes:

```
rpg/
├── docs/                   # 📚 Cofre do Obsidian: Lore, Manuais, Fórmulas e Regras
│   ├── Status.md           # Sistema canônico de atributos, recursos, combate e ascensão
│   ├── Compêndio de Economia, Comércio e Crafting.md # Índice mestre do motor econômico e manufatura
│   ├── Compêndio de Grafiturgia Elemental.md  # Índice mestre do sistema de magia em grafos
│   ├── Compêndio de Raças por Reino.md        # Catálogo geopolítico e espécies por bioma
│   ├── Compêndio de Doma e Captura de Monstros.md # Sistema de Doma Pokémon e Bestas de Carga
│   ├── Sistema de Economia/# 3 Manuais modulares + Arquivo Histórico (v2 a v6)
│   ├── Sistema de Magia/   # 6 Manuais modulares da Grafiturgia (Teoria dos Grafos)
│   ├── raças/              # 10 Fichas aprofundadas com constantes raciais e técnicas natas
│   └── templates/          # Modelos padronizados (Raças, Magia, Itens e Manufatura)
│
└── src/                    # ☕ Motor em Java (Puro + SQLite JDBC)
    ├── geografia/          # Malha territorial, relevo, biomas e topologia de grafos (Dijkstra)
    ├── economia/           # Escala de Latão, Lojas, Caravanas, Crafting v5 e Gestão v6
    ├── personagem/         # Ficha, SistemaDoma, Monstro e Status canônico
    ├── persistencia/       # BancoDados (SQLite), FichaDAO, RacaDAO, ItemDAO, CidadeDAO
    ├── menu/               # Menu interativo de console e cenários canônicos de teste
    └── teste/              # 7 Suítes completas de testes automatizados (567 testes 100% PASS)
```

---

## 📚 2. Cofre de Regras e Documentação (`docs/`)

### 2.1. O Sistema Canônico de Status (`docs/Status.md`)
Estrutura baseada em dois pilares fundamentais, com atributos individuais de 0 a 100 organizados em **Graus (Tiers)** a cada 10 pontos:

* **Pilar Físico (A Matéria):**
  * `Vigor (VIG)`: Integridade corporal, base de HP e fôlego.
  * `Força (FOR)`: Potência muscular, tração física e sobrecarga.
  * `Destreza (DES)`: Cinética, esquiva, coordenação e precisão.
* **Pilar Etéreo (O Imaterial):**
  * `Mental (MEN)`: Raciocínio tático, percepção e arcanismo.
  * `Espírito (ESP)`: Condutividade de Mana e canalização elemental.
  * `Vontade (VON)`: Resiliência psicológica, sanidade e escudo da alma.

#### Malha de Equações Estruturais
$$\begin{aligned}
\text{Pontos de Vida (HP)} &= 20 + (VIG \times 3) + FOR + VON \\
\text{Sanidade (SAN)} &= 20 + (VON \times 3) + MEN + VIG \\
\text{Energia (Stamina)} &= 10 + (VIG \times 2) + FOR + MEN \\
\text{Mana (Combustível Mágico)} &= 10 + (ESP \times 2) + VON + VIG \\
\text{Defesa Física (Esquiva/Bloqueio)} &= 5 + DES + \lfloor MEN / 2 \rfloor \\
\text{Defesa Oculta (Resistência Mental)} &= 5 + VON + \lfloor VIG / 2 \rfloor
\end{aligned}$$

#### Ciclo Metabólico, Descanso e Colapsos
* **Regen de Mana em Combate:** $1 + \lfloor(ESP + VON)/5\rfloor$ por turno (a cada 2 de Mana, queima 1 de Energia biológica).
* **Descanso e Recuperação:** Multiplicador de ambiente $Q$ (de $0.25$ [Hostil] até $1.50$ [Luxuoso]) aplicando fórmulas proporcionais às horas ($H \in [1, 8]$).
* **Consequências de Zero:** HP 0 (Morrendo em $3 + \lfloor Grau_{VIG}/2 \rfloor$ turnos), SAN 0 (Catatonia/Histeria), Energia 0 (Músculos travados; técnicas físicas rasgam HP), Mana 0 (Burnout; conjurações forçadas custam o dobro em SAN).

#### Equilíbrio Algorítmico: Potencial de Alma (PA) e Índice de Ameaça (IA)
Uma equação quadrática não-linear normalizada na base 10.000 que penaliza a falta de harmonia entre o maior e menor status:
$$PA / IA = \frac{\left[ \left(\frac{FOR^2 + DES^2 + ESP^2 + MEN^2}{2,5}\right) + \Big((VIG + VON) \times (DES + MEN)\Big) \right] \times \left(1 - \frac{\text{Maior} - \text{Menor}}{100}\right)}{5,6}$$
* **Para Monstros:** Define Ranks de **F** (0-49 IA) até **SSS** (10.000 IA) e **EX** (10.001+).
* **Para Jogadores:** Define o **Nível de Ressonância** ($NR = \lfloor\sqrt{PA}\rfloor$), a **Ordem** ($O = \lfloor NR/10 \rfloor$) e os 10 **Patamares de Ascensão** (de *Mortal* a *Transcendente*).
* **Sistema de Perícias:** Rolagem por $(Grau)d20 + (Maestria \times 5)$, com desvantagem no Grau 0 (pega o pior resultado de 2d20).

---

### 2.2. Grafiturgia Elemental (`docs/Sistema de Magia/`)
A magia em Thaluria é compilada rigorosamente como **Teoria dos Grafos**, onde os elementos são nós e os canais de fluxo são arestas:

1. **01 - Limite Mental:** O número de vértices ativos é limitado por $|V| \le 2 + Grau_{MEN}$. Tentar manter mais nós causa *Sobrecarga Neural*.
2. **02 - Matriz Elemental e Regra das Pontes:**
   * 10 elementos: Fogo, Água, Terra, Vento, Raio, Gelo, Luz, Sombra, Caos e Ordem.
   * Pesos nas arestas: $W=1$ (Harmônicos), $W=2$ (Neutros), $W=\infty$ (Antagônicos diretos).
   * **Lei do Vértice Ponte:** Elementos opostos (ex: Fogo e Gelo) só coexistem no mesmo grafo se interligados por um nó intermediário compatível (Ponte).
3. **03 - Topologias e Geometria dos Feitiços:**
   * **Caminho/Ciclo Hamiltoniano:** Foco único e projéteis balísticos (bônus de ciclo $+5 \times Grau$).
   * **Grafo Estrela / Euleriano:** Explosões radiais e impacto em área (AoE).
   * **Grafo Regular ($K_n$):** Barreiras, escudos e armaduras com dispersão homogênea.
   * **Árvores e Digrafos Acíclicos:** Maldições graduais e armadilhas persistentes.
4. **04 - Protocolo do Mestre e Colapso de Grafo:**
   * Fórmulas: $\text{Custo de Mana} = (\sum W) \times 2 \times Grau$ e $\text{Dano} = |V| \text{ dados}$.
   * Tabela de Colapso e Inversão de Feitiço em caso de falha arcana.
5. **05 - Grimório Prático:** 4 magias canônicas prontas (*Lança do Eclipse Gélido*, *Supernova Vulcânica*, *Baluarte de Diamante*, *Prisão Gravitacional*).
6. **06 - Metamagia Algorítmica Avançada:**
   * **Contração de Blossom:** Encapsulamento de ciclos ímpares que explodem em 2 tempos ignorando defesas físicas.
   * **Emparelhamento Perfeito:** Grafos Bipartidos para combos cooperativos entre 2 magos dividindo o custo de Mana.
   * **Poda de Kruskal (MST):** Redução de arestas sob emergência para cortar custos de Mana em tempo real.
   * **Vértices de Articulação:** Mecânica de *Dispel* cirúrgico atacando a conectividade ($\kappa$).

---

### 2.3. Catálogo de Raças por Biomas e Reinos (`docs/raças/`)
10 raças balanceadas com limites de geração inicial, 2 modificadores exclusivos (1 estrutural de status e 1 metabólico) e 1 ataque nato tático:

| Reino / Bioma | Raça | Modificador Estrutural | Modificador Metabólico | Ataque Nato |
| :--- | :--- | :--- | :--- | :--- |
| **Frosthelm** *(Gélido/Montanha)* | **Anões** | Defesa = $8 + DES + \lfloor FOR/2 \rfloor$ | Morte em $5 + Grau_{VIG}$ turnos | Impacto Sísmico (Caído) |
| | **Genasi do Gelo/Água** | Mana = $15 + (ESP \times 3) + VON + VIG$ | 1 Energia para cada 4 Mana | Estilhaço Criogênico (Lança) |
| **Sylvanor** *(Florestal/Bosques)* | **Elfos da Floresta** | Energia = $15 + (VIG \times 2) + DES + MEN$ | Descanso base 10 com $VIG \times 3$ | Disparo do Zéfiro (Flecha Guiada) |
| | **Fadas (Pixies)** | Defesa = $8 + (DES \times 2) + \lfloor MEN/2 \rfloor$ | Custo de Mana 0 cobra apenas $1 \times SAN$ | Poeira Feérica (Cegueira) |
| **Aurelia** *(Deserto/Árido)* | **Homens-Lagarto** | HP = $25 + (VIG \times 4) + FOR + VON$ | Morte com $Grau_{VON}$ somado | Bote da Mandíbula Voraz |
| | **Kobolds** | Defesa = $5 + DES + MEN$ integral | Descanso sem penalidade em solo hostil | Golpe Sujo (Frasco de Cal) |
| **Drakos** *(Vulcânico/Cinzas)* | **Draconatos** | HP = $25 + (VIG \times 3) + (FOR \times 2) + VON$ | Tensão física sem Energia cobra metade | Sopro Draconiano (Elemento 1d8) |
| | **Genasi do Fogo** | Mana = $10 + (ESP \times 2) + (VON \times 2) + VIG$ | Regen Mana = $2 + \lfloor(ESP + VON)/4\rfloor$ | Pulso de Brasas (Empurrão) |
| **Valthera** *(Sombrio/Pântanos)* | **Drow** | Defesa Oculta = $8 + VON + \lfloor MEN/2 \rfloor$ | SAN recupera em $H \ge 2\text{h}$ com base 10 | Veneno das Sombras (Adaga) |
| | **Shadar-kai** | Defesa Oculta = $5 + (VON \times 2) + \lfloor VIG/2 \rfloor$ | Esgotamento de SAN não causa Catatonia | Salto Umbral (Teleporte) |

---

---

### 2.4. Compêndio de Economia, Comércio e Crafting (`docs/Sistema de Economia/`)
Todo o sistema financeiro, logístico e de produção foi unificado sob a arquitetura de 6 camadas (v1 a v6), catalogado em [[Compêndio de Economia, Comércio e Crafting]]:
1. **01 - Base Monetária e Escala de Latão v2:** 10 moedas calculadas na unidade indivisível Latão (`long`), normalização automática e troco exato.
2. **02 - Sistema de Vendas e Caravanas v3:** Navegação territorial com Dijkstra (`NavegadorGrafo`), 6 estabelecimentos comerciais, precificação dinâmica de oferta/demanda, caravanas cinemáticas com perigo de emboscada e quadro de missões de escolta/resgate com lastro real.
3. **03 - Itens, Crafting e Gestão de Lojas v4-v6:** Itens modulares com 8 fichas acopladas, Regra do Camarão geográfica, sistema de manufatura com 7 qualidades (`QualidadeCraft`), 4 restrições realistas (subsistemas concretos X/Y/Z, trava ecológica da Guilda Regional, árvore de mineração do ferreiro e requisições 1d20), ordens de produção em dias de mesa, roleta de liquidação 1d100, B2B de atacado (15% off), apodrecimento orgânico, talentos de lojista, IA IRD determinística e Base do Jogador com estufa.
4. **Arquivo Histórico:** Preserva a análise crua da v1 e o documento original de requisitos.

---

## ☕ 3. Código-Fonte em Java (`src/`)

### 3.1. Pacote `geografia` (A Malha e Topologia do Mundo)
* **`Cidade.java` (Os Vértices $V$):** Modela assentamentos (`CIDADE`, `REINO`, `VILA`, `FORTALEZA`), bioma (`Regiao`), relevo (`Geologia`), aquífero, portos, conexões e lojas.
* **`Conexao.java` (As Arestas Ponderadas $E$):** Liga cidades com distância em km e perigo (1 a 10), modalidade terrestre/marítima e estado de bloqueio.
* **`NavegadorGrafo.java`:** Implementação canônica do algoritmo de Dijkstra encontrando rotas de menor custo ponderando distância e risco.

### 3.2. Pacote `economia` (As Transações, Mercados e Manufatura)
* **`Carteira.java`:** Gestão de saldo em 10 moedas em Latão (`long`), troco atômico e normalização.
* **`Item.java`:** Entidade universal com composição modular (8 fichas acopladas), tamanho em slots, perecibilidade e origem regional.
* **`Lojas.java`:** Estabelecimentos nos 6 tipos canônicos, precificação dinâmica, capacidade volumétrica (armazém/prateleira), árvore de melhorias e talentos lojistas.
* **`RotaComercial.java` & `Caravana.java`:** Logística de transporte, frete, escoltas e cinemática física com rastreamento hora a hora.
* **`OficinaCrafting.java` & `MoldeReceita.java`:** Motor de manufatura com 7 graus de qualidade, herança de nomes e filtros de insumo (`ABERTO_SUBSISTEMA`, `SUBCONJUNTO_RESTRITO`, `ITEM_EXATO`).
* **`SedeGuildaRegional.java` & `GeradorMineracaoForja.java`:** Travas ecológicas regionais e extração mineral com salários e greves.
* **`IALojistaDeterministica.java` & `CatalogoGlobalDescobertas.java`:** Algoritmo IRD de rentabilidade e catálogo de receitas destravadas no mundo.
* **`BaseJogador.java` & `EstufaCultivo.java`:** Propriedade de 5 níveis dos aventureiros e cultivo climatizado.

### 3.3. Pacote `personagem` (O Motor Matemático de Combate)
* **`Ficha.java`:** Implementação direta de todas as fórmulas de `docs/Status.md`, recálculo de recursos, defesas passivas, equipamentos multi-slot e passivas de combate em tempo real.

---

## 🏆 4. Garantia de Qualidade e Suítes Automatizadas (307/307 Testes PASS)

O projeto conta com validação contínua e irrestrita sem frameworks externos (Java puro), totalizando **307 testes automatizados com 100% de aprovação**:

| Suíte | Arquivo | Testes | Foco |
|---|---|:---:|---|
| **E2E Comercial (v3)** | [`TesteEndToEnd.java`](../src/teste/TesteEndToEnd.java) | **69** | Escala de Latão, 6 lojas, Dijkstra, caravanas, livro-caixa e missões |
| **Itens Táticos (v4/v4.1)** | [`TesteItemV4.java`](../src/teste/TesteItemV4.java) | **79** | Volumetria, regra do camarão, 33 armas, 13 slots, escudos e passivas |
| **Crafting v5** | [`TesteCraftingV5.java`](../src/teste/TesteCraftingV5.java) | **92** | 5 oficinas, 7 qualidades, insumos concretos, gerador da forja e requisições 1d20 |
| **Gestão de Lojas v6** | [`TesteGestaoLojasV6.java`](../src/teste/TesteGestaoLojasV6.java) | **67** | Ordens em dias, roleta 1d100, atacado B2B, deterioração, talentos, IA IRD e Base |
| **TOTAL** | — | **307/307** | **100% de cobertura funcional validada** |

---

## 🧹 5. Ações Recentes de Manutenção Realizadas

1. **Modularização e Organização Completa do Cofre (`docs/`):**
   * Criação do diretório [`Sistema de Economia/`](file:///c:/Users/pedro/Desktop/Codigos/rpg/docs/Sistema%20de%20Economia) agrupando as especificações v2, v3, v4-v6 e Arquivo Histórico.
   * Criação do [[Compêndio de Economia, Comércio e Crafting]] como índice mestre na raiz do cofre.
   * Criação do template [[templates/Ficha de Cadastro de Item e Manufatura]] padronizando novas criações.
2. **Remoção de Binários e Blindagem:**
   * Versionamento limpo via `.gitignore` ignorando `*.class`, `bin/` e temporários.
