# 04. Origem dos Itens, Moldes de Arma e Disseminação de Conhecimento — Thaluria RPG

> Retornar ao [[Compêndio de Economia, Comércio e Crafting]] · Anterior: [[03 - Itens, Crafting e Gestão de Lojas v4-v6]]

Este documento reúne três regras novas da economia, decididas numa sessão de perguntas e respostas com o Mestre (outubro de 2026), e o estado de cada uma no código:

| Fase | Assunto | Estado |
| :--- | :--- | :--- |
| 1 | Origem dos itens, drops ligados ao catálogo, desrevelar monstros, loja visual | **Implementada** |
| 2 | Moldes de arma: partes, subcraft, ligas e componentes | **Implementada** |
| 3 | Disseminação de conhecimento entre cidades | **Implementada** |

As três fases estão implementadas. As regras de cada seção foram confirmadas pelo Mestre e não devem ser reinterpretadas sem perguntar.

---

## 1. Origem dos itens (fase 1, implementada)

### 1.1. A regra

Todo item do catálogo chega ao mundo por pelo menos uma destas origens, e pode ter várias ao mesmo tempo:

| Origem | Condição |
| :--- | :--- |
| **Coletado** | `itens.coletavel = 1` **e** ao menos uma linha em `item_locais_coleta` |
| **Dropado** | Ao menos uma linha em `monstro_drops` com `item_id` do item |
| **Craftado** | Ao menos uma receita com `item_resultado_id` do item |

Um item sem origem **não é bloqueado**: o Mestre recebe um aviso e atalhos para resolver. Bloquear atrapalharia a ordem natural de cadastro (cria-se o item antes do monstro ou da receita).

### 1.2. Locais de coleta

Tabela `item_locais_coleta`, filha de `itens` no CRUD do Mestre (entidade `item_locais_coleta`, visível só com *Coletável* marcado):

| Coluna | Sentido |
| :--- | :--- |
| `cidade_id` | Opcional; restringe aos arredores de uma cidade |
| `regiao`, `geologia`, `aquifero` | Opcionais; vazio = qualquer |
| `probabilidade` | Chance de achar, 0 a 1 |
| `qtd_min`, `qtd_max` | Quantidade por coleta (mínimo ≤ máximo) |

### 1.3. Drops ligados ao catálogo

`monstro_drops` ganhou `item_id`. O Mestre escolhe o item numa lista; o servidor grava também `item_nome` (coluna legada). Drops antigos, gravados só pelo nome, são ligados automaticamente por `BancoDados.vincularDropsPorNome` (nome igual, sem diferenciar maiúsculas), na subida do servidor e a cada auditoria. Os que não batem com nenhum item aparecem como **drops órfãos**. Um item usado como drop não pode ser excluído.

### 1.4. Onde está no código

| Peça | Arquivo |
| :--- | :--- |
| Auditoria (`GET /api/mestre/itens/origens`) | [`OrigemItensDAO.java`](../../src/persistencia/OrigemItensDAO.java), rota em [`RotasComercio.java`](../../src/servidor/RotasComercio.java) |
| Esquema, validações, exclusão | [`AdminCrud.java`](../../src/persistencia/AdminCrud.java) |
| Tabela e migrações | [`BancoDados.java`](../../src/persistencia/BancoDados.java) |
| Testes | [`TesteOrigemItens.java`](../../src/teste/TesteOrigemItens.java) |

### 1.5. Outras mudanças da fase 1

- **Desrevelar monstro:** ação `OCULTAR_MONSTRO` em `/api/mestre/controle-mundo`. Na visão do Mestre, o bestiário traz `reveladoJogador` (antes o Mestre recebia tudo como revelado e não conseguia ver o que os jogadores enxergam).
- **Campos condicionais no CRUD:** `Campo.visivelSe(coluna)` e `Entidade.visivelSePai`. *Nome da rede* só aparece em loja de rede; o servidor apaga o nome da rede de loja que não é de rede e recusa loja de rede sem nome.
- **Loja visual:** o detalhe da loja envia `imagemUrl` e `descricao` de cada mercadoria; o front mostra cartões quadrados com a ilustração.

---

## 2. Moldes de arma (fase 2, implementada)

### 2.1. A cadeia

```
Material  ──(subcraft)──▶  Componente  ──(montagem)──▶  Arma
Minério de Ferro ×2          Lâmina de Ferro              Espada Longa de Ferro
```

Vale **só para armas** (brancas e de longo alcance) por enquanto. Armaduras e escudos ficam para depois.

### 2.2. Moldes e partes

Cada **família de arma** tem um molde com partes fixas, **editáveis pelo Mestre** numa tela própria. Padrões que já vêm cadastrados:

| Família | Partes |
| :--- | :--- |
| Espada / lâminas curtas | Lâmina · Guarda · Cabo · Pomo |
| Machado | Cabeça · Cabo |
| Contusão (maça, martelo) | Cabeça · Cabo |
| Haste (lança, alabarda) | Ponta · Haste |
| Arco | Corpo · Corda |
| Besta | Corpo · Corda · Mecanismo |

O molde também define **quantos slots de efeito** a arma tem.

### 2.3. Opções de cada parte

Cada parte tem uma **lista de opções aceitas**, e cada opção é:

- uma **categoria de material**: Metal, Madeira, Couro, Fibra, Gema, Osso, Escama, Outro (campo novo nos itens do tipo Material); ou
- um **item específico** (ex.: *Seda da Aranha-Tecelã* na Corda).

Não há tabela fixa de "a lâmina usa metal": um arco pode ter corpo de metal, uma corda pode ser de seda única. Cada parte define também **quantos materiais** consome (ex.: a lâmina consome 2).

### 2.4. Propriedades e bônus

1. **O material** tem propriedades gerais, válidas em qualquer parte: **bônus de dano**, **durabilidade**, **peso**, **preço**, **raridade**.
2. **A opção da parte** pode somar um **bônus próprio**, que só vale naquela parte (ex.: a Seda da Aranha-Tecelã *como Corda* dá +2 de dano e +20 m de alcance).
3. **Não há teto**: quanto mais o jogador investir em material, mais forte a arma.

### 2.5. Ligas

Uma parte feita de metais diferentes vira **liga**:

- Nome: **"Liga Metálica de A e B"** (três ou mais: "de A, B e C"), metais em **ordem alfabética**, para que Ferro + Cobre e Cobre + Ferro sejam a mesma liga.
- O mesmo metal repetido não é liga: duas unidades de Ferro dão "Ferro".
- Propriedades da liga: **média** do bônus de dano e da durabilidade; **soma** de peso e preço; raridade pela regra de 2.6.
- O componente leva o nome da parte: *Lâmina de Liga Metálica de Cobre e Ferro*.

### 2.6. Fórmula da arma

| Atributo | Regra |
| :--- | :--- |
| Dano | Dado base do subtipo (ex.: Espada Longa 1d8) + **soma** dos bônus de dano de todas as partes e opções |
| Durabilidade | A **menor** entre os componentes (a peça mais fraca quebra primeiro) |
| Peso | **Soma** dos componentes |
| Preço de referência | **Soma dos preços de mercado** dos materiais + **custo de produção** (mão de obra). O balcão aplica a oscilação de oferta e procura por cima |
| Bônus por parte | Média do bônus dos materiais da parte + média do bônus das opções que cada material usou (o mesmo vale para a durabilidade e o alcance) |
| Raridade | **Média ponderada pela quantidade de material**: a raridade vira número (Comum = 1, Incomum = 2...), cada material pesa a quantidade em que entra na arma (a lâmina com 2 metais pesa 2), e o resultado é arredondado |
| Slots de efeito | Slots do molde + ajuste da tabela `QualidadeCraft`, cujo grau vem do **nível da oficina** da loja (1 a 5) |

Exemplo: Espada Longa (1d8) com Lâmina de Liga de Cobre e Ferro (+2), Guarda de Ferro (+1), Cabo de Couro (+0) e Pomo de Ferro (+1) → **1d8+4**; durabilidade do cabo (a menor); peso e preço somados.

### 2.7. Componentes

- Cada componente vira um item do catálogo, do tipo novo **Componente de Arma**, criado **automaticamente** na primeira vez que é fabricado e reaproveitado depois (identificado pela composição, sem duplicar). Por nascer de receita, já tem origem *craftado*.
- O tipo **Componente de Arma Ancestral** fica **só reservado** no enum: armas lendárias que quebram os parâmetros do mundo, geradas apenas pelo Mestre. **Nenhuma lógica deve ser desenvolvida para ele ainda.**
- Componente produzido para uma encomenda fica no estoque **reservado**: não pode ser vendido. Só vira estoque comum se a encomenda for **cancelada**.

### 2.8. Produção

- Subcrafts e montagem entram numa **única ordem**. O tempo é o da montagem mais o dos componentes que ainda faltam; componente livre já na prateleira é usado direto e economiza tempo.
- **Receita pronta** (ex.: "Katana de Aço Negro") é um design fixo, com um material escolhido para cada parte, e é o que a IA lojista fabrica sozinha.
- **Encomenda personalizada**: na tela da loja, o Mestre escolhe o material de cada parte, como um jogador que pede uma arma sob medida ao ferreiro.

### 2.9. Encantamento (próxima fase)

A arma sai da forja com os **slots vazios**. O custo e o tempo da arma já são calculados contando com o encantamento, então **encantar não aumenta o custo** da arma. Encantar na própria ordem, ou levando uma arma existente ao ferreiro, fica para a fase seguinte. Os slots já ficam gravados na arma para não precisar migrar depois.

### 2.10. Como foi implementado

**A forja compila para o comércio que já existia.** Salvar uma receita de arma gera, sem duplicar (coluna `chave_forja`, única):

| O quê | Onde | Chave |
| :--- | :--- | :--- |
| Cada peça (componente) | `itens`, tipo `COMPONENTE_ARMA`, com `bonus_dano` e `durabilidade` | `C:<PARTE>:<itemId>x<qtd>,...` |
| Subcraft de cada peça | `receitas` com `somente_encomenda = 1` | `RC:` + chave da peça |
| A arma | `itens` + números de combate em `item_arma` | `A:RA:<id>` (receita pronta) ou `A:EP:...` (encomenda) |
| A receita da arma | `receitas` (todos os materiais como insumos) + uma linha por parte em `receita_etapas` | `R:RA:<id>` ou `R:EP:...` |

Assim a IA dos mercadores, o livro-caixa, as caravanas e a origem "craftado" funcionam sem saber da forja. A IA ignora receitas `somente_encomenda`. Subcrafts e encomendas avulsas não aparecem na lista de receitas da oficina, só dentro das ordens.

**Recompilação.** Mudar um molde, um tipo ou qualquer item (ex.: o bônus de um metal) recalcula as armas que dependem dele. Uma receita que ficou inválida (ex.: o molde perdeu a parte) mantém o último cálculo e aparece com aviso na tela da Forja.

**Produção por etapas.** `Lojas.iniciarReceita` planeja cada parte: um componente **livre** na prateleira é reservado na hora e poupa a etapa; senão, os materiais dela saem do estoque. As etapas ficam prontas uma depois da outra, e a montagem vem no fim. A ordem guarda o estado de cada etapa em `loja_ordens_producao.etapas` (`itemId:dia:P|R|S`):

| Estado | Sentido |
| :--- | :--- |
| `P` pendente | Materiais consumidos, peça na bancada |
| `R` reservada | Peça pronta, guardada para a encomenda (não vende) |
| `S` da prateleira | Peça que já existia, reservada na abertura |

**Cancelar** (`Lojas.cancelarOrdem`): peças `R`/`S` viram estoque comum, materiais das etapas `P` voltam (se a loja pagou por eles), e a mão de obra não volta. Vale para qualquer ordem, não só de arma.

**Slots de efeito.** O item da arma guarda os slots do molde. O extra da qualidade da oficina (`QualidadeCraft` pelo nível da loja: 0, 1, 1, 2, 2) é mostrado na encomenda, mas ainda não fica gravado por unidade: isso chega com o encantamento, quando cada arma fabricada vira uma instância própria.

**Sementes** (`ForjaDAO.semear`, uma vez): os seis moldes da tabela 2.2 (sem bônus nas opções), os tipos de arma a partir das armas brancas e de longo alcance do sistema (chicote, garras, soqueiras, manoplas, zarabatana e estilingue ficam sem molde: o Mestre cria se quiser) e a categoria dos materiais existentes, deduzida pelo começo das palavras do nome ("Lingote de Ferro" → Metal; "Saco de Trigo" fica sem categoria), com bônus e durabilidade pela raridade (Comum +1/50 até Mítico +8/220).

| Peça | Arquivo |
| :--- | :--- |
| Fórmulas (pura, sem banco) | [`ForjaArmas.java`](../../src/economia/ForjaArmas.java) |
| Moldes, tipos, receitas, compilação, sementes | [`ForjaDAO.java`](../../src/persistencia/ForjaDAO.java) |
| Rotas `/api/mestre/forja/*` | [`RotasForja.java`](../../src/servidor/RotasForja.java) |
| Encomenda e cancelamento na loja | [`RotasComercio.java`](../../src/servidor/RotasComercio.java) (`encomendar-arma`, `cancelar`) |
| Etapas e reservas | [`Lojas.java`](../../src/economia/Lojas.java), [`OrdemReceita.java`](../../src/economia/OrdemReceita.java), [`ReceitaLoja.java`](../../src/economia/ReceitaLoja.java) |
| Testes | [`TesteForja.java`](../../src/teste/TesteForja.java) e a parte de API em [`TesteComercioVivo.java`](../../src/teste/TesteComercioVivo.java) |

---

## 3. Disseminação de conhecimento (fase 3, implementada)

### 3.1. O que se espalha

Um **conhecimento** é qualquer uma destas coisas:

- um **tipo de arma** (molde): saber fazer Katanas;
- uma **receita pronta** de qualquer coisa: arma, remédio, cosmético, armadura...

As receitas que já existem são **comuns**: valem no mundo todo, como hoje. Só as que o Mestre marcar como **regionais** se espalham.

### 3.2. Origem

O Mestre (e só ele) concede o conhecimento a uma **cidade de origem**. As lojas da especialidade daquela cidade passam a conhecê-lo na hora.

### 3.3. Estágios por cidade

| Estágio | O que muda |
| :--- | :--- |
| **Rumor** | A cidade sabe que existe. Suas lojas podem **encomendar** o item de cidades produtoras |
| **Comércio** | As lojas da especialidade **e** as Lojas Gerais mantêm o item na prateleira por conta própria e revendem |
| **Produção** | As lojas da especialidade fabricam |

O item **só circula** (caravana ou venda entre lojas) para cidades que estejam **pelo menos no Rumor**.

### 3.4. Tempo de espalhamento (determinístico)

O conhecimento anda pelo **grafo de rotas** (caminho mais curto; rotas bloqueadas não propagam), contra uma **resistência**, como se cada cidade tentasse manter o segredo:

```
dia do Rumor = dia da concessão
             + sigilo inicial
             + Σ por trecho do caminho ( km/100 × dias por 100 km  +  resistência por salto )
dia do Comércio = dia do Rumor + intervalo Rumor→Comércio
dia da Produção = dia do Comércio + intervalo Comércio→Produção
```

Padrões, editáveis por conhecimento:

| Parâmetro | Padrão |
| :--- | :--- |
| Sigilo inicial (a origem segura o segredo) | 7 dias |
| Velocidade | 2 dias por 100 km |
| Resistência por salto (cada cidade segura antes de passar adiante) | 3 dias |
| Rumor → Comércio | +10 dias |
| Comércio → Produção | +20 dias |

Por ser determinístico, o Mestre vê no painel a **data prevista** de cada estágio em cada cidade.

### 3.5. Receita pronta × tipo de arma

Uma loja fabrica uma receita pronta de arma se a cidade estiver em **Produção** da **própria receita** **ou** do **tipo de arma** dela. Quem sabe fazer Katanas faz a "Katana de Aço Negro"; quem só aprendeu essa receita faz só ela. Encomendas personalizadas exigem o **tipo**.

### 3.6. Controles do Mestre

Por conhecimento: **pausar ou retomar** o espalhamento, marcar como **segredo** (não espalha), **adiantar** uma cidade para um estágio e **revogar** uma cidade.

### 3.7. Visibilidade

Só o Mestre vê a disseminação (linha do tempo e mapa). Os jogadores percebem apenas o efeito: o item aparecendo nas lojas.

### 3.8. Como foi implementado

**Cálculo** ([`RedeConhecimento.java`](../../src/economia/RedeConhecimento.java), pura e imutável). Para cada conhecimento, um Dijkstra no grafo de rotas dá o *atraso* de cada cidade, contado no **relógio do conhecimento**:

- a origem entra com atraso = sigilo; cada trecho custa `km/100 × dias por 100 km + resistência`; rotas bloqueadas e cidades revogadas não propagam; com **segredo**, ninguém passa adiante;
- a origem está em Produção desde o atraso 0;
- Comércio = Rumor + intervalo; Produção = Comércio + intervalo.

O relógio é `dia − dia da concessão − dias pausados − (pausa em curso)`. Pausar congela tudo; retomar soma os dias parados (`dias_pausados`).

**Intervenções por cidade** (`conhecimento_cidades`): *adiantar* grava o estágio e o relógio do momento; a cidade passa a ter aquele estágio (e os anteriores) desde então, e também **espalha a partir dali**. *Revogar* apaga o conhecimento da cidade e corta o caminho por ela. *Limpar* devolve ao cálculo normal.

**O que é regional.** Uma receita é regional se tiver conhecimento próprio **ou** se for de um tipo de arma com conhecimento (receitas prontas do tipo e encomendas avulsas `R:EP:<tipo>:…`). O que nunca foi concedido continua comum. Peças de forja e encomendas avulsas não podem ser concedidas sozinhas: seguem a arma.

**Efeito no motor** (`MotorEconomico.executarCicloDiario` e `solicitarCompraEntreCidades`):

| Regra | Onde |
| :--- | :--- |
| A IA só recebe as receitas liberadas na cidade (Produção, ou receita comum) | lista de receitas por cidade passada à IA |
| Fornecedor local não "cria" item regional em cidade abaixo de Produção | passo 4 do ciclo |
| Encomenda entre cidades recusada para destino abaixo do Rumor | começo de `solicitarCompraEntreCidades` |
| No Comércio, lojas da especialidade e Lojas Gerais passam a procurar o item (entra no catálogo com procura) | antes do passo 3 |
| Encomenda sob medida de um tipo regional exige Produção na cidade | rota `encomendar-arma` |

O "Mandar fazer" do Mestre continua livre (o Mestre manda), mas a receita aparece com o aviso *Ainda não se sabe fazer aqui*.

**Quando a rede é remontada**: ao subir o motor, ao mudar receitas/itens/forja e a cada ação na tela de Disseminação.

| Peça | Arquivo |
| :--- | :--- |
| Cálculo e consultas | [`RedeConhecimento.java`](../../src/economia/RedeConhecimento.java) |
| Tabelas `conhecimentos` e `conhecimento_cidades` | [`ConhecimentoDAO.java`](../../src/persistencia/ConhecimentoDAO.java), [`BancoDados.java`](../../src/persistencia/BancoDados.java) |
| Rotas `/api/mestre/conhecimentos/*` | [`RotasConhecimento.java`](../../src/servidor/RotasConhecimento.java) |
| Testes | [`TesteDisseminacao.java`](../../src/teste/TesteDisseminacao.java) e a parte de API em [`TesteComercioVivo.java`](../../src/teste/TesteComercioVivo.java) |
