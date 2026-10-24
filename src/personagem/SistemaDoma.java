package personagem;

import economia.EfeitoItem;
import economia.FichaMaterial;
import economia.Item;

import java.util.*;

/**
 * SistemaDoma.java — Captura e Doma Estilo Pokémon no RPG Thaluria.
 * Implementa:
 * - Teste de Captura via Perícia Adestramento (ESP) com mecânica d20 + (Maestria x 5)
 * - Cálculo da DT de Captura com Defesa Oculta (5 + VON + ⌊VIG/2⌋), Vitalidade (⌊(HP/HPMax)*30⌋) e Grau/Ordem
 * - Modificadores Táticos: HP enfraquecido (<= 15% e <= 5%), Condições Negativas de Controle e Iscas/Comidas
 * - Gestão do Monstro Domado na Ficha do Jogador (Combate e Besta de Carga Colossal)
 */
public class SistemaDoma {

    /**
     * Resultado descritivo e mensurável de uma tentativa de captura/doma.
     */
    public static class ResultadoDoma {
        private final boolean sucesso;
        private final boolean tentativaValida;
        private final String motivoFalhaValidacao;
        private final int rolagemAdestramento;
        private final int dtFinal;
        private final int defesaOcultaBase;
        private final int fatorVitalidade;
        private final int ordemMonstro;
        private final int bonusEnfraquecido;
        private final int redutorCondicoes;
        private final int redutorIsca;
        private final String mensagem;
        private final Monstro monstro;

        public ResultadoDoma(boolean sucesso, boolean tentativaValida, String motivoFalhaValidacao,
                              int rolagemAdestramento, int dtFinal, int defesaOcultaBase,
                              int fatorVitalidade, int ordemMonstro, int bonusEnfraquecido,
                              int redutorCondicoes, int redutorIsca, String mensagem, Monstro monstro) {
            this.sucesso = sucesso;
            this.tentativaValida = tentativaValida;
            this.motivoFalhaValidacao = motivoFalhaValidacao;
            this.rolagemAdestramento = rolagemAdestramento;
            this.dtFinal = dtFinal;
            this.defesaOcultaBase = defesaOcultaBase;
            this.fatorVitalidade = fatorVitalidade;
            this.ordemMonstro = ordemMonstro;
            this.bonusEnfraquecido = bonusEnfraquecido;
            this.redutorCondicoes = redutorCondicoes;
            this.redutorIsca = redutorIsca;
            this.mensagem = mensagem;
            this.monstro = monstro;
        }

        public boolean isSucesso() { return sucesso; }
        public boolean isTentativaValida() { return tentativaValida; }
        public String getMotivoFalhaValidacao() { return motivoFalhaValidacao; }
        public int getRolagemAdestramento() { return rolagemAdestramento; }
        public int getDtFinal() { return dtFinal; }
        public int getDefesaOcultaBase() { return defesaOcultaBase; }
        public int getFatorVitalidade() { return fatorVitalidade; }
        public int getOrdemMonstro() { return ordemMonstro; }
        public int getBonusEnfraquecido() { return bonusEnfraquecido; }
        public int getRedutorCondicoes() { return redutorCondicoes; }
        public int getRedutorIsca() { return redutorIsca; }
        public String getMensagem() { return mensagem; }
        public Monstro getMonstro() { return monstro; }

        @Override
        public String toString() {
            if (!tentativaValida) {
                return String.format("[DOMA INVÁLIDA] %s", motivoFalhaValidacao);
            }
            return String.format("[%s] Teste Adestramento: %d vs DT %d (Def.Oculta: %d + Vit: %d + Ordem: %d - Enfraq: %d - Cond: %d - Isca: %d) | %s",
                    sucesso ? "SUCESSO" : "FALHA", rolagemAdestramento, dtFinal,
                    defesaOcultaBase, fatorVitalidade, ordemMonstro, bonusEnfraquecido,
                    redutorCondicoes, redutorIsca, mensagem);
        }
    }

    /**
     * Resultado da execução de um comando tático de combate ordenado pelo jogador ao monstro.
     */
    public static class ResultadoComandoCombate {
        private final boolean executado;
        private final String motivoFalha;
        private final int danoBruto;
        private final int danoReal;
        private final int energiaGasta;
        private final int manaGasta;
        private final String descricao;

        public ResultadoComandoCombate(boolean executado, String motivoFalha, int danoBruto,
                                       int danoReal, int energiaGasta, int manaGasta, String descricao) {
            this.executado = executado;
            this.motivoFalha = motivoFalha;
            this.danoBruto = danoBruto;
            this.danoReal = danoReal;
            this.energiaGasta = energiaGasta;
            this.manaGasta = manaGasta;
            this.descricao = descricao;
        }

        public boolean isExecutado() { return executado; }
        public String getMotivoFalha() { return motivoFalha; }
        public int getDanoBruto() { return danoBruto; }
        public int getDanoReal() { return danoReal; }
        public int getEnergiaGasta() { return energiaGasta; }
        public int getManaGasta() { return manaGasta; }
        public String getDescricao() { return descricao; }
    }

    // =========================================================================
    // CÁLCULO DA DT DE CAPTURA (ESTILO POKÉMON)
    // =========================================================================

    public static class ParametrosDTCaptura {
        public final int defesaOculta;
        public final int fatorVitalidade;
        public final int ordemMonstro;
        public final int bonusEnfraquecido;
        public final int redutorCondicoes;
        public final int redutorIsca;
        public final int dtFinal;

        public ParametrosDTCaptura(int defesaOculta, int fatorVitalidade, int ordemMonstro,
                                   int bonusEnfraquecido, int redutorCondicoes, int redutorIsca, int dtFinal) {
            this.defesaOculta = defesaOculta;
            this.fatorVitalidade = fatorVitalidade;
            this.ordemMonstro = ordemMonstro;
            this.bonusEnfraquecido = bonusEnfraquecido;
            this.redutorCondicoes = redutorCondicoes;
            this.redutorIsca = redutorIsca;
            this.dtFinal = dtFinal;
        }
    }

    /**
     * Calcula a Dificuldade do Teste (DT) de Captura canônica do monstro.
     * DT = Defesa Oculta (5 + VON + ⌊VIG/2⌋) + ⌊(HP/HPMax)*30⌋ + Ordem - Bônus HP Enfraquecido - Redutor Condições - Redutor Isca.
     */
    public static ParametrosDTCaptura calcularDTCaptura(Monstro monstro, Item isca) {
        if (monstro == null) {
            return new ParametrosDTCaptura(10, 0, 0, 0, 0, 0, 10);
        }

        // 1. Defesa Oculta (5 + VON + ⌊VIG / 2⌋)
        int defOculta = monstro.getDefesaOculta();

        // 2. Grau / Ordem
        int ordem = monstro.getOrdem();

        // 3. Fator de Vitalidade e HP Enfraquecido (<= 15% e <= 5%)
        double pctHp = monstro.getHpMax() > 0 ? ((double) monstro.getHp() / monstro.getHpMax()) : 1.0;
        int fatorVitalidade;
        int bonusEnfraquecido;

        if (pctHp <= 0.05) {
            // HP extremamente crítico: zera vitalidade e concede bônus de 10 na captura
            fatorVitalidade = 0;
            bonusEnfraquecido = 10;
        } else if (pctHp <= 0.15) {
            // HP enfraquecido: zera vitalidade e concede bônus de 5 na captura
            fatorVitalidade = 0;
            bonusEnfraquecido = 5;
        } else {
            fatorVitalidade = (int) Math.floor(pctHp * 30.0);
            bonusEnfraquecido = 0;
        }

        // 4. Condições Negativas de Controle Ativas no Monstro
        int redutorCondicoes = 0;
        for (EfeitoItem cond : monstro.getCondicoesAtivas()) {
            if (isCondicaoControle(cond)) {
                redutorCondicoes += 5; // -5 na DT por condição de controle ativa
            }
        }

        // 5. Iscas e Comidas (COMIDA_INSUMO / FichaConsumivel)
        int redutorIsca = 0;
        if (isca != null) {
            int qualidade = extrairQualidadeIsca(isca);
            redutorIsca = qualidade * 3; // Proporcional à qualidade: Q1=3, Q2=6, Q3=9, Q4=12, Q5=15
        }

        // 6. DT Final Consolidada
        int dtBruta = defOculta + fatorVitalidade + ordem - bonusEnfraquecido - redutorCondicoes - redutorIsca;
        int dtFinal = Math.max(5, dtBruta); // Piso mínimo de DT 5

        return new ParametrosDTCaptura(defOculta, fatorVitalidade, ordem, bonusEnfraquecido, redutorCondicoes, redutorIsca, dtFinal);
    }

    public static boolean isCondicaoControle(EfeitoItem efeito) {
        if (efeito == null) return false;
        String s = (efeito.getNome() + " " + efeito.getElementoOuStatus() + " " + efeito.getDescricao()).toLowerCase(Locale.ROOT);
        return s.contains("paralis") || s.contains("sono") || s.contains("dorm")
                || s.contains("congel") || s.contains("lentid") || s.contains("exaust")
                || s.contains("atordo") || s.contains("imobiliz") || s.contains("caido");
    }

    public static int extrairQualidadeIsca(Item isca) {
        if (isca == null) return 0;
        if (isca.getFichaMaterial() != null) {
            return Math.max(1, Math.min(5, isca.getFichaMaterial().getQualidade()));
        }
        if (isca.getQualidadeCraft() != null) {
            return Math.max(1, Math.min(5, isca.getQualidadeCraft().getGrau()));
        }
        return 1;
    }

    // =========================================================================
    // EXECUÇÃO DO TESTE DE CAPTURA VIA PERÍCIA ADESTRAMENTO (ESP)
    // =========================================================================

    private static ResultadoDoma validarPreRequisitos(Ficha jogador, Monstro monstro) {
        if (jogador == null) {
            return new ResultadoDoma(false, false, "Jogador inexistente", 0, 0, 0, 0, 0, 0, 0, 0, "Falha: jogador nulo", monstro);
        }
        if (monstro == null) {
            return new ResultadoDoma(false, false, "Monstro inexistente", 0, 0, 0, 0, 0, 0, 0, 0, "Falha: monstro nulo", null);
        }
        if (!monstro.isDomesticavel()) {
            return new ResultadoDoma(false, false, "A criatura é selvagem/indomável (domesticavel == false)",
                    0, 0, monstro.getDefesaOculta(), 0, monstro.getOrdem(),
                    0, 0, 0, "Esta espécie rejeita qualquer tentativa de domesticação mortal", monstro);
        }
        if (!monstro.isVivo()) {
            return new ResultadoDoma(false, false, "A criatura está morta (HP <= 0)",
                    0, 0, monstro.getDefesaOculta(), 0, monstro.getOrdem(),
                    0, 0, 0, "Não é possível domar uma carcaça sem vida", monstro);
        }
        return null;
    }

    public static ResultadoDoma tentarCapturar(Ficha jogador, Monstro monstro, Item isca, Random random) {
        ResultadoDoma pre = validarPreRequisitos(jogador, monstro);
        if (pre != null) return pre;

        Random r = random != null ? random : new Random();
        int rolagem = jogador.rolarPericia(Pericia.ADESTRAMENTO, r);
        return processarCaptura(jogador, monstro, isca, rolagem);
    }

    public static ResultadoDoma tentarCapturarDeterministico(Ficha jogador, Monstro monstro, Item isca, int... dados) {
        ResultadoDoma pre = validarPreRequisitos(jogador, monstro);
        if (pre != null) return pre;

        int rolagem;
        if (jogador.getGrauPericia(Pericia.ADESTRAMENTO) <= 0 && (dados == null || dados.length < 2)) {
            rolagem = (dados != null && dados.length > 0) ? dados[0] : 1;
        } else {
            rolagem = jogador.rolarPericiaDeterministica(Pericia.ADESTRAMENTO, dados);
        }
        return processarCaptura(jogador, monstro, isca, rolagem);
    }

    private static ResultadoDoma processarCaptura(Ficha jogador, Monstro monstro, Item isca, int rolagemAdestramento) {
        ResultadoDoma pre = validarPreRequisitos(jogador, monstro);
        if (pre != null) return pre;

        ParametrosDTCaptura params = calcularDTCaptura(monstro, isca);
        boolean sucesso = rolagemAdestramento >= params.dtFinal;

        // Isca oferecida é consumida do inventário
        if (isca != null) {
            jogador.removerItem(isca);
        }

        if (sucesso) {
            jogador.adicionarMonstroDomado(monstro);
            String msg = String.format("A criatura %s foi dominada e vinculada ao espírito de %s!",
                    monstro.getNome(), jogador.getNome());
            return new ResultadoDoma(true, true, null, rolagemAdestramento, params.dtFinal,
                    params.defesaOculta, params.fatorVitalidade, params.ordemMonstro,
                    params.bonusEnfraquecido, params.redutorCondicoes, params.redutorIsca, msg, monstro);
        } else {
            String msg = String.format("A criatura %s resistiu ao comando de adestramento (Rolou %d vs DT %d)!",
                    monstro.getNome(), rolagemAdestramento, params.dtFinal);
            return new ResultadoDoma(false, true, null, rolagemAdestramento, params.dtFinal,
                    params.defesaOculta, params.fatorVitalidade, params.ordemMonstro,
                    params.bonusEnfraquecido, params.redutorCondicoes, params.redutorIsca, msg, monstro);
        }
    }

    // =========================================================================
    // ATUAÇÃO EM COMBATE DO MONSTRO DOMADO
    // =========================================================================

    public static ResultadoComandoCombate comandarAtaque(Ficha jogador, Monstro domado,
                                                         HabilidadeCombate habilidade, Monstro alvo, int rolagemDado) {
        if (jogador == null || domado == null) {
            return new ResultadoComandoCombate(false, "Jogador ou Monstro domado nulo", 0, 0, 0, 0, "Comando abortado");
        }
        if (domado.getDono() != jogador && !jogador.getMonstrosDomados().contains(domado)) {
            return new ResultadoComandoCombate(false, "O monstro não pertence a este jogador", 0, 0, 0, 0, "A fera não aceita ordens");
        }
        if (!domado.isVivo()) {
            return new ResultadoComandoCombate(false, "O monstro está incapacitado/morto", 0, 0, 0, 0, "Monstro sem fôlego");
        }
        if (alvo == null || !alvo.isVivo()) {
            return new ResultadoComandoCombate(false, "Alvo inválido ou já abatido", 0, 0, 0, 0, "Alvo derrotado");
        }

        int danoBruto = domado.calcularDanoTotal(habilidade, rolagemDado);
        int danoReal = domado.executarAtaqueContra(habilidade, alvo, rolagemDado);

        String desc = String.format("%s comandou %s a desferir %s! Dano Bruto: %d | Dano Real Infligido: %d no alvo %s (HP restante: %d/%d)",
                jogador.getNome(), domado.getNome(), habilidade.getNome(), danoBruto, danoReal,
                alvo.getNome(), alvo.getHp(), alvo.getHpMax());

        return new ResultadoComandoCombate(true, null, danoBruto, danoReal,
                habilidade.getCustoEnergia(), habilidade.getCustoMana(), desc);
    }

    public static ResultadoComandoCombate comandarAtaque(Ficha jogador, Monstro domado,
                                                         HabilidadeCombate habilidade, Ficha alvo, int rolagemDado) {
        if (jogador == null || domado == null) {
            return new ResultadoComandoCombate(false, "Jogador ou Monstro domado nulo", 0, 0, 0, 0, "Comando abortado");
        }
        if (domado.getDono() != jogador && !jogador.getMonstrosDomados().contains(domado)) {
            return new ResultadoComandoCombate(false, "O monstro não pertence a este jogador", 0, 0, 0, 0, "A fera não aceita ordens");
        }
        if (!domado.isVivo()) {
            return new ResultadoComandoCombate(false, "O monstro está incapacitado/morto", 0, 0, 0, 0, "Monstro sem fôlego");
        }
        if (alvo == null || alvo.getHp() <= 0) {
            return new ResultadoComandoCombate(false, "Alvo inválido ou já abatido", 0, 0, 0, 0, "Alvo derrotado");
        }

        int danoBruto = domado.calcularDanoTotal(habilidade, rolagemDado);
        int danoReal = domado.executarAtaqueContra(habilidade, alvo, rolagemDado);

        String desc = String.format("%s comandou %s a desferir %s! Dano Bruto: %d | Dano Real Infligido: %d no alvo %s (HP restante: %d/%d)",
                jogador.getNome(), domado.getNome(), habilidade.getNome(), danoBruto, danoReal,
                alvo.getNome(), alvo.getHp(), alvo.getHpMax());

        return new ResultadoComandoCombate(true, null, danoBruto, danoReal,
                habilidade.getCustoEnergia(), habilidade.getCustoMana(), desc);
    }
}
