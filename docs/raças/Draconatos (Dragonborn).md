# 🧬 REGISTRO DE RAÇA: Draconatos (Dragonborn)
*"O sangue dos dragões primordiais corre em nossas veias; não importa se herdamos a fúria das chamas, o estalo do trovão ou o gelo dos abismos, nossa honra permanece inquebrável."*

## 1. IDENTIDADE E BIOLOGIA OCULTA
- **Distribuição e Habitats Típicos:** Planaltos escarpados, cordilheiras rochosas, fortalezas de pedra e regiões de forte condensação elemental em toda Thaluria; com bastiões e dinastias lendárias em reinos como Drakos e outras terras imponentes.
- **Classificação Ontológica:** (X) Dominância Física (Matéria) | ( ) Dominância Etérea (Imaterial) | ( ) Híbrida / Equilibrada (Porte titânico, anatomia escamada ancestral e glândula inata de condensação elemental).
- **Expectativa de Vida e Porte:** 100 a 160 anos | Altura média: 1,95m - 2,35m | Peso: 110kg - 180kg (tórax largo blindado por grossas placas de escamas córneas com matizes cromáticos ou metálicos herdados de sua linhagem primordial, chifres ou cristas ósseas na coroa da cabeça e presença marcial avassaladora).
- **Descrição e Sociedade:** Os draconatos são os herdeiros diretos dos dragões primordiais que moldaram as eras antigas do mundo. Dotados de compleição física monumental, sua musculatura densa e sua carapaça de escamas são tão resistentes quanto placas de ferro forjado. Embora não possuam asas, abrigam em sua cavidade torácica uma glândula pulmonar mística — a *Fornalha Draconiana* —, capaz de condensar energia vital e mana para expelir baforadas de tremendo poder destrutivo correspondentes à sua linhagem ancestral (como chamas, geada, eletricidade, ácido, vórtices ou miasmas). Sua sociedade é marcial, meritocrática e estruturada sobre juramentos sagrados e honra clânica. Desprezam a covardia e a deslealdade acima de tudo. Em combate, marcham como muralhas vivas, mesclando golpes pesados com a liberação de seu sopro ancestral.

---

## 2. LIMITES DE ALEATORIEDADE INICIAL
Cada raça possui um intervalo mínimo e máximo para geração de status inicial (a maioria nasce no Grau 0 (0-9); valores 10+ representam prodígios de nascença no Grau 1):

- **Vigor (VIG):** 5 -> 11 
    
- **Força (FOR):** 5 -> 11 
    
- **Destreza (DES):** 2 -> 6 
    
- **Mental (MEN):** 2 -> 7
    
- **Espírito (ESP):** 3 -> 8 
    
- **Vontade (VON):** 4 -> 9 

---

## 3. CONSTANTES RACIAIS (SOBRESCRITA DE FÓRMULA)
*(Cada raça altera exatamente 2 constantes das equações globais do sistema)*

### 🔹 Modificador Estrutural (Malha de Recursos, Defesas ou PA)
- **Nome do Traço:** Chassi Titânico Draconiano
- **Equação Afetada:** (X) HP | ( ) SAN | ( ) Energia | ( ) Mana | ( ) Def. Física | ( ) Def. Oculta | ( ) Cálculo de PA
- **Mudança na Fórmula:**
  * **Padrão do Sistema:** `HP = 20 + (VIG × 3) + FOR + VON`
  * **Fórmula da Raça:** `HP = 25 + (VIG × 3) + (FOR × 2) + VON`
  *(Nota: A constante base sobe de 20 para 25 e o peso muscular de Força entra duplicado no cálculo de integridade vital).*

### 🔹 Modificador Metabólico (Regeneração, Descanso ou Colapso)
- **Nome do Traço:** Músculos em Brasa (Tolerância à Ruptura)
- **Equação Afetada:** ( ) Regen. de Combate | ( ) Custo Biológico | ( ) Descanso (H/Q) | (X) Colapso (Recurso 0)
- **Mudança na Fórmula:**
  * **Padrão do Sistema:** `Dano por forçar técnicas físicas com Energia 0 = Desconta 100% do custo em HP`
  * **Fórmula da Raça:** `Dano por forçar técnicas físicas com Energia 0 = Desconta apenas ⌊Custo / 2⌋ em HP`
  *(Nota: As fibras musculares draconianas resistem ao ácido lático e não se rompem facilmente, cortando pela metade o dano por exaustão mecânica).*

---

## 4. TÉCNICAS E HABILIDADES RACIAIS (ATAQUES & AÇÕES DE ESPÉCIE)

### ⚔️ Ataque Nato: Sopro Primordial Draconiano (Afinidade Elemental)
- **Tipo de Ação:** Sopro Elemental em Área (Inato)
- **Custo:** 3 de Energia e 3 de Mana
- **Determinação de Afinidade Elemental:** Na criação do personagem (ou ao definir sua herança dracônica), o jogador **rola 1d8** (ou seleciona de acordo com as escamas e linhagem do personagem) para determinar o elemento, a geometria da dispersão, o tipo de dano e a condição tática provocada pelo sopro:

| 🎲 1d8 | Linhagem / Elemento       | Cor Típica das Escamas                | Geometria da Área                 | Tipo de Dano    | Efeito Tático / Condição no Alvo                                                                                                                                               |
| :----: | :------------------------ | :------------------------------------ | :-------------------------------- | :-------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **1**  | 🔥 **Fogo / Piromântico** | Carmesim, Rubro ou Cobre              | Cone de 4 metros                  | Dano de Fogo    | **Queimadura Contínua:** Alvos pegos inflamam, sofrendo `⌊Grau de ESP / 2⌋ + 2` de dano de fogo extra no início de seus próximos 2 turnos.                                     |
| **2**  | ❄️ **Gelo / Criogênico**  | Branco, Prateado ou Celeste           | Cone de 4 metros                  | Dano de Gelo    | **Congelamento Articular:** Cristais de gelo travam juntas e armaduras, aplicando **-2 na Defesa Física** e reduzindo o deslocamento pela metade (-50%) por 1 rodada.          |
| **3**  | ⚡ **Raio / Elétrico**     | Azul-Cobalto, Índigo ou Bronze        | Linha reta de 8 metros (1m larg.) | Dano Elétrico   | **Choque e Concussão:** O arco voltaico atordoa o sistema nervoso dos alvos; falha no teste consome **-2 PA** das vítimas em seu próximo turno.                                |
| **4**  | 🧪 **Ácido / Corrosivo**  | Verde-Escuro, Oliva ou Latão          | Linha reta de 8 metros (1m larg.) | Dano Ácido      | **Corrosão de Blindagem:** Vapores e jatos cáusticos dissolvem ligas e couro, reduzindo a **Defesa Física do alvo em -3** por 2 rodadas completas.                             |
| **5**  | 🌪️ **Vento / Cinético**  | Cinza-Tempestade, Platina ou Ouro     | Cone de 4 metros                  | Dano de Impacto | **Onda Concussiva:** Rajada brutal de pressão que empurra os alvos **3 metros para trás** e os derruba no chão (Condição: **Caído**).                                          |
| **6**  | ☠️ **Veneno / Miasma**    | Púrpura-Escuro, Pântano ou Esmeralda  | Cone de 4 metros                  | Dano Biológico  | **Intoxicação Debilitante:** Miasma sufocante que bloqueia a regeneração natural de Energia/Mana dos alvos por 2 rodadas e impõe desvantagem (-2) em testes físicos.           |
| **7**  | ☀️ **Luz / Radiante**     | Dourado Fulgurante ou Alvo Cintilante | Linha reta de 8 metros (1m larg.) | Dano Radiante   | **Clarão Cegante:** Lampejo estelar que sobrecarrega a visão e os sentidos, impondo a condição **Ofuscado / Cego** (-4 em ataques e percepção) por 1 rodada.                   |
| **8**  | 🌑 **Sombra / Vácuo**     | Obsidiana, Preto Fosco ou Ébano       | Cone de 4 metros                  | Dano Necrótico  | **Terror Primordial:** Emanação abissal que devora matéria e vitalidade psíquica, subtraindo `⌊Grau de ESP / 2⌋ + 2` pontos diretos de **Sanidade (SAN)** além do dano físico. |

- **Dado & Dano:** `Dado específico do sopro (2d6) + FOR + ESP` do elemento correspondente.
- **Teste de Resistência dos Alvos:** Todas as criaturas apanhadas na área de efeito devem realizar um teste de Salvamento físico ou mental contra o limiar do Draconato (valor de referência igual à sua `Defesa Física` ou `Defesa Oculta`, o que for maior).
  - *Sucesso no Teste:* O alvo resiste parcialmente, sofrendo apenas **metade do dano** e anulando o efeito tático secundário.
  - *Falha no Teste:* O alvo sofre o **dano total** e fica sob a **condição tática descrita na tabela**.

