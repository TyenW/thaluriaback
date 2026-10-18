# 🧬 REGISTRO DE RAÇA: Homens-Lagarto (Lizardfolk)
*"A duna engole quem chora e quem hesita; carne é combustível e osso é ferramenta para alcançar o próximo nascer do sol."*

## 1. IDENTIDADE E BIOLOGIA OCULTA
- **Distribuição e Habitats Típicos:** Zonas áridas, desfiladeiros rochosos, pântanos tropicais, mangues e deltas fluviais em toda Thaluria; com tribos proeminentes e alianças de clãs no Reino de Aurelia e em ermos inóspitos pelo mundo.
- **Classificação Ontológica:** (X) Dominância Física (Matéria) | ( ) Dominância Etérea (Imaterial) | ( ) Híbrida / Equilibrada
- **Expectativa de Vida e Porte:** 80 a 120 anos | Altura média: 1,85m - 2,20m | Peso: 95kg - 160kg (escamas grossas de queratina córnea, mandíbula potente com fileiras de dentes serrilhados, cauda pesada de contrapeso e nado, e garras afiadas).
- **Descrição e Sociedade:** Os homens-lagarto são sobreviventes e predadores primitivos dotados de adaptação biológica assombrosa a climas severos e escassez de recursos. Seus corpos são blindados por escamas espessas de queratina que retêm umidade interna em ambientes secos e protegem contra parasitas e mordidas em charcos pantanosos. Possuem metabolismo reptiliano capaz de desacelerar batimentos em situações críticas e órgãos sensoriais nas narinas e palato capazes de rastrear presas a quilômetros. Vivem em tribos seminômades reunidas em oásis, cavernas rochosas ou palafitas fluviais. Sua cultura é pragmática, funcional e desprovida de sentimentalismo superficial: valorizam a força física, a destreza de caça e a preservação do bando. Para um homem-lagarto, a natureza não possui moralidade; tudo o que perece é fonte de alimento, couro e ferramentas para garantir a próxima geração.

---

## 2. LIMITES DE ALEATORIEDADE INICIAL
Cada raça possui um intervalo mínimo e máximo para geração de status inicial (a maioria nasce no Grau 0 (0-9); valores 10+ representam prodígios de nascença no Grau 1):

- **Vigor (VIG):** 5 -> 11 *(Prodígio nato: 10 ou 11 nasce com couraça espessa quase impenetrável)*
    
- **Força (FOR):** 5 -> 10 *(Prodígio nato: 10 nasce com mandíbula e músculos de predador alfa)*
    
- **Destreza (DES):** 3 -> 7
    
- **Mental (MEN):** 1 -> 5 *(Pensamento utilitário primário, avesso a teorias)*
    
- **Espírito (ESP):** 1 -> 5
    
- **Vontade (VON):** 4 -> 9

---

## 3. CONSTANTES RACIAIS (SOBRESCRITA DE FÓRMULA)
*(Cada raça altera exatamente 2 constantes das equações globais do sistema)*

### 🔹 Modificador Estrutural (Malha de Recursos, Defesas ou PA)
- **Nome do Traço:** Carapaça Escamada Primitiva
- **Equação Afetada:** (X) HP | ( ) SAN | ( ) Energia | ( ) Mana | ( ) Def. Física | ( ) Def. Oculta | ( ) Cálculo de PA
- **Mudança na Fórmula:**
  * **Padrão do Sistema:** `HP = 20 + (VIG × 3) + FOR + VON`
  * **Fórmula da Raça:** `HP = 25 + (VIG × 4) + FOR + VON`
  *(Nota: A constante base sobe de 20 para 25 e o multiplicador biológico de Vigor aumenta de ×3 para ×4).*

### 🔹 Modificador Metabólico (Regeneração, Descanso ou Colapso)
- **Nome do Traço:** Torpor Bradiquárdico (Sangue Frio)
- **Equação Afetada:** ( ) Regen. de Combate | ( ) Custo Biológico | ( ) Descanso (H/Q) | (X) Colapso (Recurso 0)
- **Mudança na Fórmula:**
  * **Padrão do Sistema:** `Turnos até Morte Definitiva (HP 0) = 3 + ⌊Grau_VIG / 2⌋`
  * **Fórmula da Raça:** `Turnos até Morte Definitiva (HP 0) = 4 + ⌊Grau_VIG / 2⌋ + ⌊Grau_VON / 2⌋`
  *(Nota: O metabolismo reptiliano desacelera as batidas cardíacas em choque traumático, aumentando a base de 3 para 4 e adicionando a frieza de Vontade na contagem de morte).*

---

## 4. TÉCNICAS E HABILIDADES RACIAIS (ATAQUES & AÇÕES DE ESPÉCIE)

### ⚔️ Ataque Nato: Bote da Mandíbula Voraz (Abocanhar Predatório)
- **Tipo de Ação:** Ataque Corporal / Presa & Dilaceração
- **Custo:** 3 de Energia
- **Alcance / Área:** Corpo a corpo (1,5m)
- **Dado & Dano:** `Dado específico de mordida predadora (1d6) + FOR + VIG` de dano perfurante e dilacerante.
- **Descrição & Efeito Provisório:** O homem-lagarto projeta seu torso com um estalo violento de sua cauda e crava suas mandíbulas serrilhadas no pescoço ou membro do alvo. Se o ataque acertar, o homem-lagarto trava suas presas, impondo a condição **Agarrado / Preso**; enquanto o alvo estiver preso, o homem-lagarto pode gastar apenas 1 de Energia por turno para causar o dano automaticamente e rasgar carne fresca, recuperando `5 + Grau de VIG` de sua própria Energia.

