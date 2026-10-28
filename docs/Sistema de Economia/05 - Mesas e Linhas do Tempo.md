# 05. Mesas e Linhas do Tempo — Thaluria RPG

> Retornar ao [[Compêndio de Economia, Comércio e Crafting]] · Anterior: [[04 - Origem dos Itens, Moldes de Arma e Disseminação]]

Este documento define como o Mestre conduz **várias mesas ao mesmo tempo, cada uma num dia diferente**, todas compartilhando o mesmo mundo econômico. As regras foram decididas numa sessão de perguntas e respostas com o Mestre (outubro de 2026). Elas foram confirmadas e não devem ser reinterpretadas sem perguntar.

| Fase | Assunto | Estado |
| :--- | :--- | :--- |
| 1 | Mesas, relógio por mesa, personagem em mesa | **Planejada** |
| 2 | Snapshots diários do mercado e leitura do passado | **Planejada** |
| 3 | Operações da mesa atrasada (compra, venda, encomenda) | **Planejada** |

---

## 1. O problema

Hoje o mundo tem dois relógios no [`MotorEconomico`](../../src/economia/MotorEconomico.java):

- `diaAtual` / `horaAtual`: o **relógio do mercado**, que anda sempre que o Mestre avança o tempo;
- `diaCampanha`: o **relógio da campanha**, que só anda com dia jogado. A disseminação de conhecimento segue este relógio.

Um único `diaCampanha` só comporta uma mesa. O Mestre quer mestrar, por exemplo, a mesa *Norte* no dia 8 e a mesa *Sul* no dia 10, e cada uma precisa ver os preços, estoques e prazos do **seu** dia.

---

## 2. A regra central: o mundo segue a mesa mais à frente

```
Mundo (mercado)   dia 10  ← = mesa ativa mais à frente (ou além, se o Mestre aquecer o mercado)
 ├─ Mesa "Sul"    dia 10  ← vê o mercado ao vivo
 └─ Mesa "Norte"  dia 8   ← vê o snapshot do dia 8 (o passado)
```

1. **O motor só anda para frente.** O dia do mundo é sempre **maior ou igual** ao dia da mesa ativa mais à frente.
2. **Uma mesa que passa o mundo o puxa automaticamente.** Se a Norte avança do dia 8 para o dia 12 e o mundo está no 10, o motor simula os dias 10 → 12.
3. **Uma mesa atrasada lê o passado.** Preço, estoque e conhecimento que ela vê vêm do **snapshot diário** do dia dela (seção 4), não do motor ao vivo.
4. **Uma mesa atrasada age no presente.** O que ela compra, vende ou encomenda altera o mundo **de agora**, não reescreve a história (seção 5).
5. **Aquecer o mercado continua valendo.** O Mestre pode simular o mercado à frente de todas as mesas, como hoje. Nesse caso nenhuma mesa vê o mercado ao vivo, e todas leem snapshots.

### 2.1. Por que não "mundo = mesa mais atrasada"

Esse modelo foi considerado primeiro e **descartado**. Nele, a mesa adiantada vive no futuro do mercado. Isso exige reservas de estoque, preço estimado com acerto posterior ("paga agora, recalcula no dia 10") e dívidas quando o preço sobe. Além disso, uma mesa abandonada trava o mundo inteiro. No modelo escolhido, o motor só anda para frente, a mesa da frente vê dados reais, e mesa parada não trava nada.

O custo do modelo escolhido é o **paradoxo da mesa atrasada** (seção 5.1), considerado aceitável.

---

## 3. Mesas e personagens

### 3.1. A mesa

| Campo | Sentido |
| :--- | :--- |
| `id`, `nome` | Identificação ("Norte", "Sul") |
| `dia`, `hora` | Relógio próprio. **Dia + hora**, não só dia: escolta e viagens andam hora a hora |
| `status` | `ATIVA` ou `ARQUIVADA` |
| `mestre_id` | Quem conduz (opcional; hoje só existe um Mestre) |

- Uma mesa `ARQUIVADA` não conta para nada: nem para puxar o mundo, nem para a retenção de snapshots.
- Não há limite de distância entre mesas. Uma mesa pode ficar qualquer número de dias atrás do mundo.

### 3.2. Personagem em mesa

- **Uma mesa por vez.** Cada ficha tem `mesa_id` (pode ser nulo: personagem fora de jogo).
- Cada ficha guarda seu **próprio relógio** (`dia_personagem`, `hora_personagem`), que é o momento mais à frente em que ele já esteve.
- **Trocar de mesa só para frente:** o personagem só entra numa mesa cujo (dia, hora) seja ≥ o dele. Ir para uma mesa atrasada seria viagem no tempo. O Mestre pode forçar, e a troca forçada fica registrada.
- Ao entrar numa mesa à frente, as pendências do personagem (encomendas) passam a usar o relógio da nova mesa. O que vencia nesse intervalo já está pronto.
- Ao avançar uma mesa, o relógio de todos os personagens dela avança junto.

### 3.3. O relógio da campanha

O `diaCampanha` global deixa de existir como fonte da verdade. No lugar dele:

- **No motor** (produção das lojas, receitas liberadas no ciclo diário), vale o dia da **mesa ativa mais à frente**. Assim, aquecer o mercado continua sem espalhar rumores, como hoje.
- **Nas consultas de uma mesa** (`receitaLiberada`, `estagioDoItem`, `estagioDoTipo`), vale o **dia da mesa**. Antes da fase 1, é preciso confirmar que essas funções da `RedeConhecimento` são puras em relação ao dia, isto é, que não dependem de estado acumulado.

---

## 4. Snapshots diários do mercado

### 4.1. O que é gravado

No fim de cada `executarCicloDiario`, o motor grava a foto do mercado daquele dia:

```
mercado_snapshot (
  dia            INTEGER,
  loja_id        INTEGER,
  item_id        INTEGER,
  quantidade     INTEGER,
  preco_venda_latao  INTEGER,   -- calcularPrecoDinamicoVendaEmLatao
  preco_compra_latao INTEGER,   -- calcularPrecoCompraNpcEmLatao
  demanda        INTEGER,
  PRIMARY KEY (dia, loja_id, item_id)
)
```

O livro-caixa (`livro_caixa_movimentacoes`) já tem `dia_mundo` e continua sendo o histórico de movimentações. O snapshot existe para que ler "como estava a loja no dia 8" seja uma consulta direta, sem reconstruir o estoque andando para trás.

O snapshot é **por dia**, não por hora. Uma mesa no dia 8, 15h, lê o snapshot do dia 8.

### 4.2. Retenção

Guarda-se **desde o dia da mesa ativa mais atrasada**. O resto é apagado a cada ciclo diário. Mesas arquivadas não seguram o histórico. Se uma mesa arquivada for reativada num dia sem snapshot, ela passa a ler o presente, e o Mestre recebe um aviso.

### 4.3. Quem lê o quê

| Situação da mesa | Fonte de preço e estoque |
| :--- | :--- |
| (dia) = dia do mundo | Motor ao vivo |
| (dia) < dia do mundo | `mercado_snapshot` do dia da mesa |

As rotas de comércio (`/api/mestre/comercio/*`) passam a receber `mesaId` e escolhem a fonte por essa regra.

---

## 5. Operações da mesa atrasada

Exemplo usado nesta seção: mundo no dia 10, Norte no dia 8.

### 5.1. Compra

- **Preço:** o do snapshot do dia 8. Não tem recálculo nem acerto posterior.
- **Disponível:** `min(quantidade no snapshot do dia 8, estoque atual do mundo)`.
- **Efeito:** sai do estoque **atual** (dia 10), e o dinheiro entra no caixa atual da loja.
- **Paradoxo aceito:** se o snapshot mostra 3 espadas e a Sul já comprou as 3 no dia 10, a Norte vê "esgotado". O Mestre narra que acabou. Isso é raro, porque exige duas mesas na mesma loja disputando o mesmo item.
- **Reescrever a história** (voltar ao dia 8, aplicar a compra e re-simular) foi **descartado**: exigiria motor determinístico e mudaria coisas que a mesa da frente já jogou.

### 5.2. Venda

- **Preço:** o do snapshot do dia 8, pago na hora.
- **Efeito:** o item entra no estoque **atual** (dia 10), imediatamente.

### 5.3. Encomenda (produção)

A Norte encomenda no dia 8 uma arma de 5 dias:

- A ordem entra na fila da loja **agora** (mundo no dia 10) e ocupa capacidade de produção normalmente. Os insumos são pagos na abertura, como já funciona em [`OrdemReceita`](../../src/economia/OrdemReceita.java).
- A ordem guarda `mesa_id`. O prazo do jogador é **dia 13 da mesa** (8 + 5).
- Quando a Norte chega ao dia 13, **a arma é entregue mesmo que a loja ainda não tenha terminado** (mundo < 15): "no passado, ela já estava pronta". A ordem é concluída antecipadamente na loja.
- Se a loja termina antes (mundo ≥ 15 e Norte < 13), o item fica **reservado** para a encomenda e só é entregue quando a mesa chegar ao dia 13.
- Para a mesa da frente, nada muda: o prazo da mesa coincide com o do mundo.

Regra geral: **tudo que é do jogador e tem prazo guarda `mesa_id` e é avaliado contra o relógio da mesa.** Tudo que é do mundo (NPCs, produção automática, B2B) usa o relógio do mundo.

### 5.4. Caravanas e escolta

- Missões de escolta e caravanas **só aparecem para mesas no dia do mundo**. O motor já simulou as caravanas do passado, então mexer nelas reescreveria a história.
- A mesa atrasada **vê** o histórico das caravanas, mas não interage.
- Se a mesa da frente estiver escoltando e outra mesa a ultrapassar, a escolta continua no relógio do mundo. A mesa que escolta passa a ser "atrasada" só para as **novas** operações.

### 5.5. Livro-caixa

As movimentações da mesa atrasada são gravadas com o `dia_mundo` **atual** (dia 10), com `descricao` indicando a origem: `[Mesa Norte · dia 8]`. Assim, o caixa da loja continua consistente com o tempo do motor.

---

## 6. Avançar o tempo

O `ControleTempo` do front passa a operar **por mesa**:

1. O Mestre escolhe a mesa e quantas horas/dias avançar.
2. O servidor atualiza o relógio da mesa e dos personagens dela.
3. Se o novo (dia, hora) da mesa passar do mundo, o servidor chama `motor.avancarTempo(diferença)`.
4. As encomendas da mesa que venceram no novo relógio são entregues (seção 5.3).
5. O snapshot antigo que ninguém mais usa é apagado (seção 4.2).

O botão "simular mercado" (aquecer) continua existindo e avança só o mundo.

---

## 7. Mudanças previstas no código

### 7.1. Banco ([`BancoDados.java`](../../src/persistencia/BancoDados.java))

| Mudança | Detalhe |
| :--- | :--- |
| Tabela `mesas` | `id, nome, dia, hora, status, mestre_id` |
| `fichas` | `+ mesa_id, dia_personagem, hora_personagem` |
| Tabela `mercado_snapshot` | Seção 4.1 |
| `loja_ordens_producao` | `+ mesa_id` (nulo = ordem do mundo/NPC), `+ dia_conclusao_mesa` |
| Migração | Cria a mesa "Principal" com o `dia_atual` da campanha (`PermissoesMestreDAO`) e coloca todas as fichas nela |

### 7.2. Back

| Peça | Arquivo |
| :--- | :--- |
| Relógio do mundo e snapshot no ciclo diário | [`MotorEconomico.java`](../../src/economia/MotorEconomico.java) |
| `diaCampanha` → mesa mais à frente | `MotorEconomico`, [`ControlePermissoesMestre.java`](../../src/personagem/ControlePermissoesMestre.java) |
| Ação de avançar tempo por mesa | [`ServidorThaluriaAPI.java`](../../src/servidor/ServidorThaluriaAPI.java) (hoje `AVANCAR_DIA` em `/api/mestre/controle-mundo`) |
| `mesaId` nas rotas de comércio e na leitura do snapshot | [`RotasComercio.java`](../../src/servidor/RotasComercio.java), [`ComercioDAO.java`](../../src/persistencia/ComercioDAO.java) |
| Conhecimento pelo dia da mesa | [`RotasConhecimento.java`](../../src/servidor/RotasConhecimento.java) |
| CRUD de mesas e troca de mesa | Novo `RotasMesas.java` + `MesaDAO.java` |

### 7.3. Front

- Seletor de mesa no painel do Mestre. A mesa escolhida vai em todas as chamadas de comércio.
- [`ControleTempo.tsx`](../../../thaluriafront/src/components/comercio/ControleTempo.tsx) mostra o relógio da mesa e o do mundo, e avisa quando a mesa está lendo o passado.
- Na tela de jogadores, coluna "Mesa" e ação "Trocar de mesa" (com aviso quando a troca é forçada).

### 7.4. Bot do Discord (opcional, depois da fase 3)

`/mesa criar`, `/mesa add @jogador`, `/mesa dia`. Cada mesa pode ganhar canal e cargo no servidor.

---

## 8. Testes que precisam existir

- Mesa atrasada lê o preço do snapshot, não o ao vivo.
- Compra da mesa atrasada respeita `min(snapshot, atual)` e falha com "esgotado" quando a mesa da frente já levou tudo.
- Mesa que passa o mundo dispara a simulação só da diferença.
- Encomenda da mesa atrasada é entregue no dia da mesa, mesmo com a loja inacabada.
- Personagem não entra em mesa atrasada sem o Mestre forçar.
- A retenção apaga os snapshots anteriores à mesa ativa mais atrasada e ignora mesas arquivadas.
- Aquecer o mercado não muda o dia de nenhuma mesa nem espalha conhecimento.

---

## 9. Decisões registradas

| Pergunta | Decisão |
| :--- | :--- |
| Qual relógio o mundo segue? | O da mesa ativa mais à frente (ou além, ao aquecer) |
| Mesa atrasada reescreve a história? | Não. Lê o snapshot e age no presente |
| Preço da mesa atrasada | O do snapshot do dia dela, sem acerto posterior |
| Venda da mesa atrasada | Entra no estoque atual na hora |
| Encomenda da mesa atrasada | Pronta no dia da mesa, mesmo se a loja não terminou |
| Escolta/caravana | Só para a mesa no dia do mundo |
| Personagem em várias mesas? | Não. Uma por vez, e só troca para frente (o Mestre pode forçar) |
| Granularidade do relógio | Dia + hora |
| Limite de distância entre mesas | Nenhum |
| Retenção de snapshots | Desde a mesa ativa mais atrasada |
| Aquecer o mercado | Continua existindo |
