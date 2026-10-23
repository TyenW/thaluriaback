# 📝 Arquivo Histórico: Requisitos Originais do Sistema Comercial

> Retornar ao [[Compêndio de Economia, Comércio e Crafting]]

> [!NOTE]
> **Documento de Registro Histórico:** Este documento registra a visão conceitual e o conjunto de especificações originais fornecidas no início do desenvolvimento do módulo econômico do universo de Thaluria RPG. Todas as 8 etapas conceituais descritas abaixo foram 100% implementadas e validadas por suítes de testes automatizados.

---

## 📜 Visão Conceitual Original

Tenho um sistema de comércio que quero desenvolver para o meu RPG, com funcionamento automático e dinâmico, simulando a economia do mundo.

Explicando melhor a ideia, quero cadastrar as lojas por cidade, registrando também a cidade à qual cada estabelecimento pertence. Posteriormente, vou apresentar os códigos que já tenho para que possamos integrar o sistema à estrutura existente.

A partir disso, quero implementar as seguintes funcionalidades:

---

### 1. Estoque e Movimentação de Mercadorias
- Cada loja terá entradas e saídas de materiais e itens específicos, com quantidades individuais por item.
- As lojas poderão comprar mercadorias de NPCs, pagando os valores definidos pelo próprio sistema.
- O sistema deverá controlar o estoque e o histórico de movimentações de cada estabelecimento.

---

### 2. Geração Automática de Comércio e Demandas
- As lojas deverão gerar demandas aleatórias por determinados itens, assim como ocorre com as entradas de mercadorias.
- Por exemplo: uma loja precisa vender determinado item, mas não possui estoque suficiente para atender à demanda. Nesse caso, ela poderá procurar outra loja que tenha o item disponível e solicitar a compra e o transporte da mercadoria até seu estabelecimento.

---

### 3. Transporte de Mercadorias entre Cidades
- O sistema deverá calcular as rotas de transporte utilizando um grafo ponderado, considerando a distância entre as cidades e a periculosidade de cada trajeto.
- Com base nesses fatores, o sistema deverá calcular os custos e os riscos do transporte. Caso necessário, a loja poderá contratar uma escolta para proteger a carga durante o percurso.

---

### 4. Sistema de Escoltas e Missões para os Jogadores
- As oportunidades de escolta deverão ser disponibilizadas como missões para os jogadores que estiverem na cidade correspondente.
- Se algum jogador aceitar a missão: a carga será transportada com 100% de segurança durante o trajeto.
- Se ninguém aceitar a missão dentro do prazo: o sistema realizará uma verificação percentual para determinar se a escolta foi contratada automaticamente por NPCs.
- Caso a escolta não seja contratada: o transporte seguirá sem proteção, sujeito aos riscos calculados com base na periculosidade da rota.

---

### 5. Extravio de Cargas e Missões de Resgate
- Caso uma carga seja extraviada durante um transporte desprotegido, o sistema deverá:
  1. Calcular, com base no tempo decorrido, no dia e no horário, a posição aproximada da carga no grafo, identificando a cidade ou aresta em que ela provavelmente se encontra.
  2. Gerar automaticamente uma missão de resgate, disponibilizada aos jogadores.
  3. Caso nenhum jogador aceite a missão dentro do prazo, realizar uma verificação percentual para determinar se os NPCs conseguiram recuperar a carga.
  4. Se a recuperação falhar, aumentar a periculosidade da aresta correspondente.
  5. Se a recuperação for bem-sucedida, diminuir a periculosidade dessa aresta.
- Dessa forma, as ações dos jogadores e os acontecimentos gerados pelo sistema influenciarão diretamente a economia e a segurança do mundo.

---

### 6. Sistema de Preços Baseado na Oferta e na Demanda
- Quero que os preços dos itens sejam dinâmicos e considerem a quantidade disponível no mercado.
- Por exemplo, se uma loja acumular uma grande quantidade de determinado item, seu preço deverá diminuir. Por outro lado, se houver escassez e uma demanda elevada, seu preço poderá aumentar.
- O sistema deverá considerar as entradas, as saídas, o estoque e a demanda de cada mercadoria para calcular os preços de maneira coerente.

---

### 7. Sistema de Crafting e Definição de Valores
- Também quero implementar um sistema de crafting, no qual os itens possam ser produzidos a partir de matérias-primas e receitas específicas.
- A intenção é que o valor dos itens fabricados seja definido com base nos materiais utilizados, nos custos de produção e em outros fatores relevantes. Esses valores poderão servir como referência inicial para o sistema de comércio, que posteriormente ajustará os preços de acordo com as condições do mercado.

---

### 8. Desenvolvimento por Etapas
- Minha ideia é desenvolver esse sistema de forma modular, começando pelo comércio e deixando o crafting para uma segunda etapa.

---
> Retornar ao [[Compêndio de Economia, Comércio e Crafting]]
