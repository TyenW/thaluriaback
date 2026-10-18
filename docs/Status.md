# Status
Status são as essências que revelam o estado atual de um personagem, seus poderes, suas condições e sua conexão com as forças que regem o mundo. São o reflexo de sua vitalidade, habilidades e dos efeitos mágicos ou naturais que agem sobre ele.

## Quais status existem?
Em Thaluria existem 6 Status:

**O Pilar Físico (A Matéria)** Representa a estrutura biológica, a biomecânica e os limites da carne. É o chassi tangível do personagem:

- **Vigor (VIG):** A integridade estrutural. Define o quanto o corpo aguenta apanhar, sangrar e queimar oxigênio antes de colapsar (Base do **HP** e da **Energia**).
    
- **Força (FOR):** A potência muscular. Define o impacto bruto dos golpes, a tração física e o peso de equipamentos que você suporta carregar.
    
- **Destreza (DES):** A cinética e o reflexo. Define a velocidade de movimento, a coordenação motora fina, a esquiva física e a precisão dos ataques.
    

**O Pilar Etéreo (O Imaterial)** Representa a consciência, a psique e a conexão com as forças invisíveis do mundo. É o motor oculto do personagem:

- **Mental (MEN):** O processamento lógico. Define a velocidade de raciocínio, a percepção tática para prever golpes e a capacidade de projetar ataques psíquicos na mente alheia.
    
- **Espírito (ESP):** A condutividade sobrenatural. Define a largura do seu canal místico para armazenar, puxar e moldar **Mana** em elementos físicos (fogo, raio, etc.).
    
- **Vontade (VON):** O escudo da alma. Define a resiliência psicológica, o autocontrole sob pavor extremo e a estabilidade da sua consciência (Base da **Sanidade** e da **Defesa Oculta**).

**A Regra da Interdependência:** Nenhum pilar sobrevive sozinho. **O Corpo sustenta a Mente** (sem Vigor, você não aguenta canalizar Mana e sua Sanidade é frágil) e **a Mente guia o Corpo** (sem Mental e Vontade, sua Esquiva, sua Energia e seu HP despencam).

### Escala de Níveis e Graus (Tiers)

Cada atributo evolui individualmente do **0 ao 100**.

- **De 1 em 1 ponto (Micro-evolução):** Aumenta imediatamente os cálculos de Recursos Secundários e Defesas (HP, SAN, Energia, Mana e Esquiva).

- **De 10 em 10 pontos (Macro-evolução / Grau):** A cada dezena exata (`10, 20, 30...`), o atributo sobe 1 **Grau (Tier)**. O Grau define o dado de dano do atributo, o limite de equipamentos suportados e a quantidade de `d20` rolados em perícias.

## Malha de Recursos e Defesas 

Agora, aventureiro, chegou a hora de descobrir o verdadeiro potencial do seu personagem!  Utilize as fórmulas a seguir para calcular seus atributos e estatísticas, revelando suas capacidades, forças e limitações. Cada valor representa uma parte do poder que habita em você e será essencial para enfrentar os desafios, superar obstáculos e trilhar seu próprio caminho neste mundo de mistérios e magia. Prepare seus cálculos e descubra até onde seu personagem pode chegar!

* **Pontos de Vida (HP):**  $$20+ (VIG \times 3) + FOR + VON$$
* **Sanidade (SAN):** $$ 20 + (VON \times 3) + MEN + VIG$$

* **Energia (Stamina Melee):** $$ 10 + (VIG \times 2) + FOR + MEN$$

* **Mana (Combustível Mágico):** $$ 10 + (ESP \times 2) + VON + VIG$$

* **Defesa Física (Esquiva/Bloqueio):** $$ 5 + DES + (\frac{MEN}{2})$$

* **Defesa Oculta (Resistência Mental):** $$ 5 + VON + (\frac{VIG}{2})$$

*(Nota: Arredonde todas as divisões para baixo).*
### Ciclo de Regeneração (Conversão Metabólica)

- **Regeneração de Mana (Em Combate):** No início de cada turno, o personagem regenera automaticamente **`1 + ((ESP + VON) / 5)`** de Mana.  
    - _Custo Biológico:_ Para cada **2 pontos de Mana** gerados, o corpo queima **1 ponto de Energia**. O jogador pode optar por "fechar o canal espiritual" no início do turno para não regenerar Mana e poupar seu fôlego físico.
      
- **Recuperação de Energia (Fora de Combate):** A Energia **nunca regenera sozinha por turno**. Ela exige:

|**Nível de Conforto**|**Multiplicador (Q)**|**Condições do Ambiente**|
|---|---|---|
|**Péssimo (Hostil)**|**$0,25$**|De armadura pesada, na chuva, frio extremo, chão úmido ou sob perigo iminente.|
|**Precário (Improvisado)**|**$0,50$**|Sem barraca, dormindo sentado, dentro de masmorra ou em turnos de vigia tensos.|
|**Padrão (Acampamento)**|**$1,00$**|Saco de dormir seco, barraca, fogueira acesa ou estalagem simples.|
|**Confortável (Bom)**|**$1,25$**|Quarto privado, colchão macio, ambiente aquecido, seguro e silencioso.|
|**Luxuoso / Santuário**|**$1,50$**|Aposentos reais, fontes termais, tendas mágicas ou locais ricos em Mana com banquete.|
### Fórmulas de Recuperação

Seja **$H$** o número de horas contínuas de descanso (mínimo de $1\text{h}$ e máximo de $8\text{h}$) e **$Q$** o multiplicador da tabela (arredonde resultados para baixo):

- **Energia** _(Exige $H \ge 1\text{h}$ — Se $H = 8$ e $Q \ge 1,0$, recupera **100%**)_:$$\text{Energia Recuperada} = \Big\lfloor \big(5 + (VIG \times 2)\big) \times H \times Q \Big\rfloor$$
- **Mana** _(Exige $H \ge 2\text{h}$)_:$$\text{Mana Recuperada} = \left\lfloor \text{Mana}_{\text{Máx}} \times \left(\frac{H}{8}\right) \times Q \right\rfloor$$
- **HP e Sanidade** _(Exige $H \ge 4\text{h}$)_:$$\text{HP ou SAN Recuperados} = \left\lfloor \text{Máximo} \times \left(\frac{H}{16}\right) \times Q \right\rfloor$$
### Consequências de Esgotamento (Chegar a 0 ou Negativo)

**Se os Recursos chegarem a 0:**
- **HP 0 (Colapso Corporal):** Cai inconsciente e _Morrendo_. Morre definitivamente em 
  $\huge 3+\lfloor\frac{grau\_vigor}{2}\rfloor$  turnos se não for estabilizado.
      
- **Sanidade 0 (Colapso Mental):** O cérebro desliga. Entra em _Catatonia_ (imóvel) ou _Histeria_ (ataca qualquer ser próximo aleatoriamente) até realizar um Descanso Longo ou receber tratamento psíquico.  
    
- **Energia 0 (Exaustão Severa):** Músculos travam por falta de oxigênio. **Proibido correr, esquivar ou usar técnicas físicas**; testes físicos têm o resultado cortado pela metade e a **Regeneração de Mana é interrompida**. Forçar o uso de habilidades físicas custando 0 de Energia desconta o valor diretamente do **HP** (rasgando os músculos).  
    
- **Mana 0 (Burnout Etéreo):** Canal espiritual seca. Proibido conjurar. Forçar magias sem Mana desconta o **dobro do custo** diretamente da **Sanidade (SAN)**.
  
## Progressão, Travas Matemáticas e Índice de Ameaça (Rank)

Os personagens evoluem gastando **Pontos de Essência (PE)** obtidos com feitos.
### As Duas Leis de Evolução de Atributo
1. **Custo Progressivo de PE:** Subir +1 ponto em um atributo custa **`Grau Atual + 1`** em PE (Ex: No Grau 0 custa 1 PE; no Grau 1 custa 2 PE). O ponto exato que fecha uma nova dezena (ex: subir do 9 para o 10, ou do 19 para o 20) exige um **Pedágio de Quebra de Grau**, custando o **dobro** do valor do nivel(exemplo: 9->10 custa 18, 19->20 custa 38).
    
2. **Limite de Tensão (Regra dos 50 Pontos):** Para qualquer ser na escala mortal (0 a 100), a diferença entre o seu **Maior Atributo** e o seu **Menor Atributo** nunca pode ultrapassar **50 pontos (5 Graus)**. _(Ex: Para subir Força para o 60, todos os outros 5 atributos precisam estar no mínimo no 10)._

### Fórmula de Medição de Poder: Índice de Ameaça (IA) e Ranks

Para medir o poder real de um personagem ou monstro sem brechas para manipulação de ficha, utiliza-se a equação do **Índice de Ameaça (IA)**, que cruza o pico ofensivo, a sustentação defensiva e a estabilidade do corpo (normalizada na base 10.000):

  

$$IA = \frac{\left[ \left(\frac{FOR^2 + DES^2 + ESP^2 + MEN^2}{2,5}\right) + \Big((VIG + VON) \times (DES + MEN)\Big) \right] \times \left(1 - \frac{Maior\ Status - Menor\ Status}{100}\right)}{5,6}$$

- **Trava de Pareamento de Grupo:** A diferença de Rank/IA entre os membros do grupo deve se manter equilibrada para evitar dissonância tática na mesa.
    
      
    

| **Rank Oficial** | **Pontuação (IA)** | **Grau Equivalente**   | **Representação no Universo e Benchmarks de Combate (Inimigos Médios)**                            |
| ---------------- | ------------------ | ---------------------- | -------------------------------------------------------------------------------------------------- |
| **Rank F**       | `0 a 49`           | Grau 0 _(00-09)_       | **Civis e Criaturas Fracas.** _(HP: 25-40 \| Def: 5-10 \| Dano/Turno: 3-6 \| DT: 10)_              |
| **Rank E**       | `50 a 199`         | Grau 1 _(10-19)_       | **Aventureiro Inicial / Guarda.** _(HP: 60-90 \| Def: 14-18 \| Dano/Turno: 10-15 \| DT: 15)_       |
| **Rank D**       | `200 a 599`        | Grau 2 _(20-29)_       | **Veterano / Monstro Médio.** _(HP: 120-160 \| Def: 25-32 \| Dano/Turno: 30-40 \| DT: 30)_         |
| **Rank C**       | `600 a 1.399`      | Grau 3 e 4 _(30-45)_   | **Elite / Capitão / Quimera.** _(HP: 200-300 \| Def: 40-55 \| Dano/Turno: 65-110 \| DT: 45-60)_    |
| **Rank B**       | `1.400 a 2.799`    | Grau 5 e 6 _(46-60)_   | **Herói Regional / Demônio.** _(HP: 350-500 \| Def: 65-85 \| Dano/Turno: 180-280 \| DT: 75-90)_    |
| **Rank A**       | `2.800 a 4.999`    | Grau 7 _(61-75)_       | **Pilar de Reino / Dragão.** _(HP: 550-700 \| Def: 95-110 \| Dano/Turno: 350-420 \| DT: 105)_      |
| **Rank S**       | `5.000 a 7.499`    | Grau 8 _(76-88)_       | **Lenda Viva / Catástrofe.** _(HP: 750-900 \| Def: 120-135 \| Dano/Turno: 500-600 \| DT: 120)_     |
| **Rank SS**      | `7.500 a 9.999`    | Grau 9 _(89-99)_       | **Semideus Mortal.** _(HP: 950-1100 \| Def: 140-150 \| Dano/Turno: 700-800 \| DT: 135)_            |
| **Rank SSS**     | **`10.000`**       | **Grau 10 (Tudo 100)** | **O Ápice Mortal.** Ser perfeito com os 6 atributos no 100. _(HP: 1200+ \| Dano: 900+ \| DT: 150)_ |
| **Rank EX**      | `10.001+`          | **Grau 11+ (101+)**    | **Extrapolação / Calamidades.** Ignoram o fator de redução de estabilidade da fórmula.             |

_(Nota para criação de Chefes Solo: Multiplique o HP da tabela por **3x ou 4x**, mantendo o Dano e a Defesa do Grau equivalente)._
### Fórmula de Medição de Poder do Jogador: Ressonância e Ascensão

Para medir o nível real de evolução de um personagem sem brechas para manipulação de ficha, utiliza-se a equação de **Potencial de Alma (PA)**, que possui a mesma estrutura algébrica do Índice de Ameaça, cruzando impacto ofensivo, sustentação vital e harmonia corporal na base 10.000:
SS
  

$$PA = \frac{\left[ \left(\frac{FOR^2 + DES^2 + ESP^2 + MEN^2}{2,5}\right) + \Big((VIG + VON) \times (DES + MEN)\Big) \right] \times \left(1 - \frac{\text{Maior Status} - \text{Menor Status}}{100}\right)}{5,6}$$

A partir do **Potencial de Alma ($PA$)**, extrai-se o **Nível de Ressonância ($NR$)** do jogador (de 0 a 100) aplicando a raiz quadrada, e a sua **Ordem ($O$)** (de 0 a 10):

  

$$\text{Nível de Ressonância (NR)} = \lfloor \sqrt{PA} \rfloor \qquad \big\vert{} \qquad \text{Ordem (O)} = \left\lfloor \frac{NR}{10} \right\rfloor$$

- **Sintonia de Grupo (Trava de Campanha):** A diferença de **Nível de Ressonância ($NR$)** entre os membros do grupo não deve ultrapassar **5 Níveis** (ou um Patamar de Ascensão de distância) para manter o equilíbrio tático na mesa.

### Tabela de Patamares de Ascensão (Exclusiva para Jogadores)

| **Patamar de Ascensão (Título do Jogador)** | **Potencial de Alma (PA)** | **Nível de Ressonância (NR)** | **Ordem / Grau Equivalente** | **Capacidade do Aventureiro e Benchmarks Médios da Ficha**                                                                                   |
| ------------------------------------------- | -------------------------- | ----------------------------- | ---------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| **Mortal**                                  | `0 a 49`                   | **Nível 00 a 06**             | **0ª Ordem** _(00-09)_       | **Desperto / Civil.** Corpo ainda sem treino marcial ou canal de mana aberto. _(HP: 25-40 \| Def: 5-10 \| Dano Médio: 3-6)_                  |
| **Desperto**                                | `50 a 199`                 | **Nível 07 a 14**             | **1ª Ordem** _(10-19)_       | **Vanguarda Novata.** Domina equipamentos básicos e técnicas de 2 dados. _(HP: 60-90 \| Def: 14-18 \| Dano Médio: 10-15)_                    |
| **Adepto**                                  | `200 a 599`                | **Nível 15 a 24**             | **2ª Ordem** _(20-29)_       | **Combatente Calejado.** Suporta armas de aço pesado e feitiços elementais densos. _(HP: 120-160 \| Def: 25-32 \| Dano Médio: 30-40)_        |
| **Especialista**                            | `600 a 1.399`              | **Nível 25 a 37**             | **3ª e 4ª Ordem** _(30-45)_  | **Mestre de Ofício.** Especialista capaz de liderar esquadrões e usar relíquias. _(HP: 200-300 \| Def: 40-55 \| Dano Médio: 65-110)_         |
| **Mestre**                                  | `1.400 a 2.799`            | **Nível 38 a 52**             | **5ª e 6ª Ordem** _(46-60)_  | **Campeão Regional.** Desbloqueia Manifestações de Aura (Haki) e feitos sobre-humanos. _(HP: 350-500 \| Def: 65-85 \| Dano Médio: 180-280)_  |
| **Grão-mestre**                             | `2.800 a 4.999`            | **Nível 53 a 70**             | **7ª Ordem** _(61-75)_       | **Pilar do Mundo.** Poder equivalente a um exército; apto a sustentar artefatos reais. _(HP: 550-700 \| Def: 95-110 \| Dano Médio: 350-420)_ |
| **Ascendente**                              | `5.000 a 7.499`            | **Nível 71 a 86**             | **8ª Ordem** _(76-88)_       | **Abaixo do Céu.** Capaz de impor Expansões de Domínio e alterar o clima/terreno. _(HP: 750-900 \| Def: 120-135 \| Dano Médio: 500-600)_     |
| **Supremo**                                 | `7.500 a 9.999`            | **Nível 87 a 99**             | **9ª Ordem** _(89-99)_       | **Divindade Terrena.** No limiar da biologia mortal; imune a desafios mundanos. _(HP: 950-1100 \| Def: 140-150 \| Dano Médio: 700-800)_      |
| **Ápice**                                   | **`10.000`**               | **Nível 100**                 | **10ª Ordem** _(Tudo 100)_   | **O Ser Perfeito.** Atingiu 100 nos 6 atributos com 100% de harmonia corporal. _(HP: 1220 \| Def: 155 \| Dano Médio: 900+)_                  |
| **Transcendente**                           | `10.001+`                  | **Nível 101+**                | **11ª+ Ordem** _(101+)_      | **Anomalia Viva.** Jogadores que romperam o teto racial ou absorveram núcleos divinos. _(Ignoram o redutor de estabilidade)._                |
|                                             |                            |                               |                              |                                                                                                                                              |

### Como os dois sistemas conversam na prática (Jogador vs. Monstro)

Como a matemática por trás do **Potencial de Alma ($PA$)** do jogador e do **Índice de Ameaça ($IA$)** do monstro é idêntica, o Mestre tem pareamento direto:

- Um jogador no patamar **Adepto ($NR\text{ 18} \mid 324\text{ PA}$)** tem exatamente a mesma estrutura matemática para enfrentar um monstro de **Rank D ($324\text{ IA}$)** no mano a mano.
    
- Um grupo de 4 jogadores no patamar **Discípulo ($NR\text{ 30} \mid 900\text{ PA}$)** pode enfrentar juntos um Chefe Solo **Rank C ($900\text{ IA}$ com HP multiplicado por 4x)** ou tentar a sorte contra uma criatura **Rank B ($1.500\text{ IA}$)** usando estratégia. A classificação de **Índice de Ameaça (IA)** e **Ranks (F a EX)** serve para catalogar perigos do mundo e monstros, enquanto os jogadores utilizam o sistema de **Nível de Ressonância (NR)** e **Patamares de Ascensão**. Ambos usam **exatamente a mesma equação matemática**, separando a nomenclatura de "caça/ameaça" da nomenclatura de "evolução/protagonismo" dos jogadores.
  
## Sistema de Perícias: `(Grau)d20 + (Nível x 5)`

As perícias representam conhecimentos práticos e sociais e são sempre atreladas a um Atributo Base:

- **FOR:** Atletismo, Intimidação Física.
    
- **DES:** Furtividade, Acrobacia, Prestidigitação (roubo/fechaduras), Pontaria/Luta (para testes de acerto).
    
- **VIG:** Sobrevivência Bruta (fome, clima, fôlego).
    
- **MEN:** Enganação (mentira/disfarce), Persuasão/Diplomacia, Investigação, Medicina/Alquimia.
    
- **VON:** Intimidação Psicológica (pressão de aura), Intuição/Empatia (ler mentiras — oposto à Enganação).
    
- **ESP:** Ocultismo/Arcanismo (ler Índices de Ameaça e identificar magias), Adestramento.

### Mecânica de Rolagem e Evolução

O teste de perícia soma todos os dados de 20 lados equivalentes ao **Grau da Perícia** mais um bônus fixo de **Nível de Maestria**:

  

$$\text{Rolagem de Perícia} = (\text{Grau da Perícia})\text{d20} + (\text{Nível de Maestria} \times 5)$$

1. **Regra do Grau 0:** Se a perícia estiver no Grau 0, o jogador rola **`2d20` e pega o PIOR resultado** (soma +0). Do Grau 1 em diante, rola `1d20`, `2d20`, `3d20`... pegando o maior.
    
2. **Trava de Atributo:** O **Grau da Perícia** nunca pode ultrapassar o **Grau do Atributo Base** correspondente.
    
3. **Trava de Maestria (+3):** O Nível de Maestria vai de **0 a 3** (gerando de `+0` a `+15` fixos). 