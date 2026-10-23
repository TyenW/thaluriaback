# 📦 REGISTRO DE ITEM E MANUFATURA: (NOME DO ITEM)
*"(Frase de apresentação, lenda ou inscrição gravada no item)"*

---

## 1. IDENTIFICAÇÃO E NÚCLEO COMERCIAL (`Item.java`)

- **Nome Canônico:** (Ex: Manopla Farpada de Javali da Tundra)
- **Tipo de Item (`TipoItem`):**
  - [ ] `ARMA_BRANCA`
  - [ ] `ARMA_LONGO_ALCANCE`
  - [ ] `PROJETIL`
  - [ ] `ARMADURA`
  - [ ] `ESCUDO`
  - [ ] `CONSUMIVEL`
  - [ ] `ACESSORIO`
  - [ ] `MATERIAL`
  - [ ] `MAGICO`
  - [ ] `OUTRO`
- **Origem do Item (`OrigemItem`):**
  - ( ) `COLETAVEL_OU_DROP` $\to$ Grau de Raridade: `COMUM` | `INCOMUM` | `RARO` | `EPICO` | `LENDARIO` | `MITICO`
  - ( ) `MANUFATURADO_CRAFT` $\to$ Qualidade: `PLEBEU` | `ARTESANAL` | `REFINADO` | `NOBRE` | `REAL` | `IMPERIAL` | `REGIO_ANCESTRAL`
- **Preço Base de Tabela:** `______ PO` *(Equivalente interno em Latão: `________ PL`)*
- **Peso Físico:** `____ kg`
- **Volumetria (`TamanhoItem`):**
  - ( ) `MINUSCULO` (0.1 slots) — Anéis, gemas, agulhas
  - ( ) `PEQUENO` (1.0 slot) — Adagas, poções, grimórios de bolso
  - ( ) `MEDIO` (3.0 slots) — Espadas longas, elmos, lingotes
  - ( ) `GRANDE` (8.0 slots) — Montantes, peitorais pesados
  - ( ) `COLOSSAL` (25.0+ slots) — Crânios de dragão, bigornas *(exige carroça/caravana)*
- **Perecibilidade Orgânica:**
  - `perecivel`: [ ] Sim | [ ] Não
  - `diasValidade`: `___ dias` *(Gera perda por deterioração ao vencer)*

---

## 2. ASSINATURA GEOGRÁFICA NATIVA ("Regra do Camarão")

- **Região Climática (`Regiao`):** (POLAR / TUNDRA / TAIGA / TEMPERADO / TROPICAL / SAVANA / DESERTO)
- **Geologia do Terreno (`Geologia`):** (MONTANHA / COLINA / PLANICIE / CAVERNA / VALE / CANYON / FLORESTA)
- **Aquífero Local (`Aquifero`):** (LAGO_PEQUENO / LAGO_GRANDE / RIO / OCEANO_COSTEIRO / OCEANO_PROFUNDO / PANTANO)
- **Comportamento Regional de Mercado:**
  - *No bioma nativo:* Multiplicador `0.80x` (-20% abundância)
  - *Em biomas antagônicos / extremos:* Até `1.85x` (+85% especiaria exótica)

---

## 3. FICHA TÉCNICA ESPECÍFICA (ESCOLHER UMA)

### ⚔️ Se for Arma Branca (`FichaArmaBranca`)
- **Subtipo (33 canônicas):** (Ex: `ESPADA_LONGA`, `KATANA`, `MACHADO_DE_BATALHA`, `LANCA_LONGA`)
- **Empunhadura:** ( ) Uma Mão | ( ) Duas Mãos
- **Dado Base de Dano:** (Ex: 1d8, 1d10, 2d6)
- **Bônus de Faces / Fixos:** (Concedido por QualidadeCraft ou metal refinado)
- **Slots de Efeito:** `___ slots disponíveis`

### 🏹 Se for Arma de Disparo (`FichaArmaLongoAlcance`)
- **Subtipo:** (ARCO_CURTO / ARCO_LONGO / BESTA_LEVE / BESTA_PESADA / ZARABATANA / ESTILINGUE)
- **Multiplicador de Amplificação:** `___x` sobre o projétil
- **Alcance Efetivo:** `___ metros`

### 🛡️ Se for Armadura (`FichaArmadura`)
- **Slots Corporais Ocupados (`EnumSet<SlotArmadura>`):**
  - [ ] CABECA | [ ] ROSTO | [ ] PESCOCO | [ ] OMBROS | [ ] PEITO
  - [ ] COSTAS | [ ] BRACOS | [ ] COTOVELOS | [ ] MAOS | [ ] CINTURA
  - [ ] PERNAS | [ ] JOELHOS | [ ] PES
- **Redução de Dano (RD Físico):** `+____`
- **Durabilidade Máxima:** `____ pontos`

### 🛡️ Se for Escudo (`FichaEscudo`)
- **Empunhadura:** ( ) Uma Mão (Broquel/Redondo) | ( ) Duas Mãos (Torre/Pavis)
- **HP de Bloqueio:** `____ HP`
- **Limite de Absorção por Golpe:** `____ dano máx/golpe` *(excedente vaza ao defensor)*

### 💍 Se for Acessório (`FichaAcessorio`)
- **Slot Especial:** (ORELHAS / OLHOS / ANTEBRACOS / ANEIS / ACESSORIO_GERAL)
- **Bônus de Status:** (Ex: +2 FOR, +1 DES)
- **Passiva Vinculada (`PassivaItem`):** Condição e Efeito mecânico

### 🧪 Se for Consumível (`FichaConsumivel`)
- **Restauração Imediata:** HP: `___` | Mana: `___` | Energia: `___`
- **Condições Curadas:** (Ex: Veneno, Sangramento, Exaustão)

### 🌿 Se for Material ou Espólio (`FichaMaterial`)
- **Subtipo do Material:** (`METAL_LINGOTE`, `MINERIO_BRUTO`, `GEMA_CRISTAL`, `MADEIRA_FIBRA`, `COURO_PELE`, `PLANTA_ERVA`, `COMIDA_INSUMO`, `ESPOLIO_MONSTRO`)
- **Qualidade Natural:** Nível 1 a 5
- **Pool de Efeitos de Drop (`Random`):** Lista de efeitos mágicos possíveis ao abater a criatura

---

## 4. MOLDE DE RECEITA / CRAFTING (`MoldeReceita.java`)
*(Preencher apenas se o item for manufaturável em oficinas)*

- **Área de Manufatura (`TipoEstabelecimento`):**
  - ( ) `FORJA`
  - ( ) `ALFAIATARIA`
  - ( ) `ALQUIMIA`
  - ( ) `BOTANICA`
  - ( ) `JOALHERIA`
  - ( ) `TAVERNA`
- **Nível Mínimo da Oficina:** Nível `___` (1 a 5)
- **Dias de Produção em Mesa (`OrdemProducao`):** `___ dias`
- **Custo de Mão de Obra:** `________ Latão`
- **Insumos Necessários:**
  1. Insumo 1: `SubtipoMaterial` / Qty: `___` / Qualidade Mínima: `___`
     - **Modo de Filtro:**
       - ( ) `ABERTO_SUBSISTEMA` — Aceita qualquer material do mesmo subtipo
       - ( ) `SUBCONJUNTO_RESTRITO` — Aceita apenas subconjunto autorizado de IDs: `[... , ...]`
       - ( ) `ITEM_EXATO` — Exige estritamente o item de ID: `_______`
  2. Insumo 2: `SubtipoMaterial` / Qty: `___` / Qualidade Mínima: `___`
     - **Modo de Filtro:** ( ) Aberto | ( ) Restrito | ( ) Exato

---

> Retornar ao [[Compêndio de Economia, Comércio e Crafting]]
