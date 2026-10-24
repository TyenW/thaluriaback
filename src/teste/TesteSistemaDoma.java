package teste;

import economia.*;
import personagem.*;
import persistencia.*;

import java.io.File;
import java.util.List;

/**
 * Suite de Testes para o SistemaDoma (Captura e Doma Estilo Pokémon).
 * Valida:
 * - Teste de Adestramento (ESP) e cálculo da DT de captura estilo Pokémon
 * - Modificadores de HP enfraquecido (<= 15% e <= 5%)
 * - Condições de controle (Paralisia, Sono, Congelamento, Lentidão, Exaustão)
 * - Iscas e comidas (COMIDA_INSUMO de Qualidade 1 a 5)
 * - Adição à lista monstrosDomados na Ficha.java sem alterar a classe da instância
 * - Comandos de combate com habilidades inatas
 * - Besta de carga e desbloqueio de itens de TamanhoItem.COLOSSAL
 * - Persistência completa no SQLite (ficha_monstros_domados)
 */
public class TesteSistemaDoma {

    private static int assercoesExecutadas = 0;
    private static int assercoesSucesso = 0;

    public static void main(String[] args) {
        System.out.println("======================================================================");
        System.out.println("  INICIANDO SUITE DE TESTES: SISTEMA DE DOMA ESTILO POKÉMON (v1.0)");
        System.out.println("======================================================================");

        try {
            // Isola banco de teste
            String dbTeste = "thaluria_doma_teste.db";
            File f = new File(dbTeste);
            if (f.exists()) f.delete();
            BancoDados.setUrlBanco("jdbc:sqlite:" + dbTeste);
            BancoDados.inicializarEsquema();
            new RacaDAO().popularRacasPadraoSeVazio();
            new ItemDAO().popularItensPadraoSeVazio();

            testarValidacoesBasicasEPreRequisitos();
            testarCalculoDTCapturaEstiloPokemon();
            testarModificadoresTaticosEnfraquecimentoECondicoes();
            testarIscasEComidasInsumo();
            testarCapturaERolagemAdestramento();
            testarAtuacaoEmCombateMonstroDomado();
            testarBestaDeCargaEVolumeColossal();
            testarPersistenciaMonstrosDomadosSQLite();

            System.out.println("----------------------------------------------------------------------");
            System.out.printf("RESULTADO: %d/%d asserções passaram com 100%% de sucesso!\n", assercoesSucesso, assercoesExecutadas);
            System.out.println("SISTEMA DE DOMA POKÉMON E BESTAS DE CARGA VALIDADO COM PERFEIÇÃO!");
            System.out.println("======================================================================");

            // Limpa banco temporário e restaura thaluria.db
            BancoDados.setUrlBanco("jdbc:sqlite:thaluria.db");
            BancoDados.inicializarEsquema();
            new File(dbTeste).delete();

        } catch (Throwable t) {
            System.err.println("FALHA NA SUITE DE TESTES DE DOMA:");
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static void afirme(boolean condicao, String mensagem) {
        assercoesExecutadas++;
        if (!condicao) {
            System.err.println(" [ERRO] " + mensagem);
            throw new AssertionError("Falha no teste: " + mensagem);
        }
        assercoesSucesso++;
        System.out.println(" [OK] " + mensagem);
    }

    private static void testarValidacoesBasicasEPreRequisitos() {
        System.out.println("\n--- 1. Pré-Requisitos de Doma: Domesticabilidade e Vida ---");
        Ficha treinador = new Ficha(10, 10, 10, 10, 14, 10); // ESP 14 (Grau 1)
        treinador.setNome("Ash de Sylvanor");
        treinador.setPericia(Pericia.ADESTRAMENTO, 1, 0);

        // 1. Criatura selvagem/indomável (domesticavel == false)
        Monstro dragao = Monstro.dragaoVermelhoSelvagem();
        afirme(!dragao.isDomesticavel(), "Dragão Vermelho Ancião possui domesticavel == false.");

        SistemaDoma.ResultadoDoma resDragao = SistemaDoma.tentarCapturarDeterministico(treinador, dragao, null, 20);
        afirme(!resDragao.isSucesso(), "Tentativa de captura do Dragão Selvagem foi rejeitada.");
        afirme(!resDragao.isTentativaValida(), "Tentativa marcada como inválida por falta de domesticabilidade.");

        // 2. Criatura morta (HP <= 0)
        Monstro loboMorto = Monstro.loboDasNeves();
        loboMorto.setHp(0);
        afirme(!loboMorto.isVivo(), "Lobo das Neves está morto (HP = 0).");

        SistemaDoma.ResultadoDoma resMorto = SistemaDoma.tentarCapturarDeterministico(treinador, loboMorto, null, 20);
        afirme(!resMorto.isSucesso(), "Tentativa de domar monstro morto falhou.");
        afirme(!resMorto.isTentativaValida(), "Tentativa marcada como inválida por criatura abatida.");
    }

    private static void testarCalculoDTCapturaEstiloPokemon() {
        System.out.println("\n--- 2. Cálculo da DT de Captura (Defesa Oculta + Vitalidade + Ordem) ---");
        // Lobo das Neves: VIG 12, FOR 14, DES 15, MEN 8, ESP 8, VON 10 | HP 75/75 (100%)
        Monstro lobo = Monstro.loboDasNeves();

        // Defesa Oculta: 5 + VON (10) + ⌊VIG (12) / 2⌋ = 5 + 10 + 6 = 21
        afirme(lobo.getDefesaOculta() == 21, "Defesa Oculta calculada corretamente (5 + 10 + 6 = 21).");

        // Fator Vitalidade com 100% de HP: ⌊(75/75) * 30⌋ = 30
        // Ordem do lobo: IA base
        int ordem = lobo.getOrdem();
        SistemaDoma.ParametrosDTCaptura dtCalculada = SistemaDoma.calcularDTCaptura(lobo, null);

        afirme(dtCalculada.defesaOculta == 21, "Parâmetro Defesa Oculta confere (21).");
        afirme(dtCalculada.fatorVitalidade == 30, "Fator Vitalidade com HP cheio confere (30).");
        afirme(dtCalculada.dtFinal == 21 + 30 + ordem, "DT Final de criatura com HP cheio é " + (21 + 30 + ordem) + ".");
    }

    private static void testarModificadoresTaticosEnfraquecimentoECondicoes() {
        System.out.println("\n--- 3. Modificadores Táticos: HP Enfraquecido e Condições Negativas ---");
        Monstro lobo = Monstro.loboDasNeves(); // HP Max: 75

        // HP reduzido para 10/75 (13.3% <= 15%): Vitalidade zera e concede bônus de 5
        lobo.setHp(10);
        SistemaDoma.ParametrosDTCaptura dt15 = SistemaDoma.calcularDTCaptura(lobo, null);
        afirme(dt15.fatorVitalidade == 0, "HP <= 15% zera a penalidade de vitalidade.");
        afirme(dt15.bonusEnfraquecido == 5, "HP <= 15% concede bônus de captura de 5.");

        // HP reduzido para 3/75 (4.0% <= 5%): Vitalidade zera e concede bônus crítico de 10
        lobo.setHp(3);
        SistemaDoma.ParametrosDTCaptura dt5 = SistemaDoma.calcularDTCaptura(lobo, null);
        afirme(dt5.fatorVitalidade == 0, "HP <= 5% zera a penalidade de vitalidade.");
        afirme(dt5.bonusEnfraquecido == 10, "HP <= 5% concede bônus de captura de 10.");

        // Adicionar Condições Negativas de Controle (Paralisia e Congelamento)
        lobo.adicionarCondicao(new EfeitoItem(1, "Paralisia Eletrostática", EfeitoItem.GatilhoEfeito.AO_ACERTAR, "0", "Paralisia", 3));
        lobo.adicionarCondicao(new EfeitoItem(2, "Congelamento Profundo", EfeitoItem.GatilhoEfeito.AO_ACERTAR, "0", "Congelamento", 2));

        SistemaDoma.ParametrosDTCaptura dtComCondicoes = SistemaDoma.calcularDTCaptura(lobo, null);
        afirme(dtComCondicoes.redutorCondicoes == 10, "2 condições ativas de controle reduziram a DT em 10 pontos (-5 cada).");
        afirme(dtComCondicoes.dtFinal < dt5.dtFinal, "DT final com condições é substancialmente menor.");
    }

    private static void testarIscasEComidasInsumo() {
        System.out.println("\n--- 4. Modificadores de Iscas e Comidas (COMIDA_INSUMO Q1 a Q5) ---");
        Monstro grifo = Monstro.grifoAltoVale();

        // Isca Q1
        Item carneQ1 = new Item(101, "Carne de Caça Comum", 1.0);
        carneQ1.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.COMIDA_INSUMO, 1));
        SistemaDoma.ParametrosDTCaptura dtQ1 = SistemaDoma.calcularDTCaptura(grifo, carneQ1);
        afirme(dtQ1.redutorIsca == 3, "Isca de Qualidade 1 reduziu a DT em 3 pontos.");

        // Isca Q3
        Item banqueteQ3 = new Item(102, "Ensopado de Ervas Arcanas", 5.0);
        banqueteQ3.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.COMIDA_INSUMO, 3));
        SistemaDoma.ParametrosDTCaptura dtQ3 = SistemaDoma.calcularDTCaptura(grifo, banqueteQ3);
        afirme(dtQ3.redutorIsca == 9, "Isca de Qualidade 3 reduziu a DT em 9 pontos.");

        // Isca Q5 (Lendária)
        Item nectarQ5 = new Item(103, "Néctar Feérico Real", 25.0);
        nectarQ5.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.COMIDA_INSUMO, 5));
        SistemaDoma.ParametrosDTCaptura dtQ5 = SistemaDoma.calcularDTCaptura(grifo, nectarQ5);
        afirme(dtQ5.redutorIsca == 15, "Isca de Qualidade 5 reduziu a DT em 15 pontos.");
    }

    private static void testarCapturaERolagemAdestramento() {
        System.out.println("\n--- 5. Tentativa de Captura via Perícia Adestramento (ESP) ---");
        Ficha jogador = new Ficha(10, 10, 10, 10, 20, 10); // ESP 20 (Grau 2)
        jogador.setNome("Domador de Bestas");

        // Aloca Grau 2 e Maestria 2 em Adestramento (+10 fixos)
        jogador.setPericia(Pericia.ADESTRAMENTO, 2, 2);

        Monstro lobo = Monstro.loboDasNeves();
        lobo.setHp(3); // HP <= 5% (bônus de 10)
        Item isca = new Item(104, "Pedaço de Carne Suculenta", 2.0);
        isca.setFichaMaterial(new FichaMaterial(FichaMaterial.SubtipoMaterial.COMIDA_INSUMO, 2)); // Q2 (-6 DT)
        jogador.adicionarItem(isca);

        // Rolagem determinística: dados (15, 8). Grau 2 pega o maior (15) + Maestria 2 (10) = 25
        SistemaDoma.ResultadoDoma res = SistemaDoma.tentarCapturarDeterministico(jogador, lobo, isca, 15, 8);

        afirme(res.getRolagemAdestramento() == 25, "Rolagem de Adestramento calculada corretamente (15 + 10 = 25).");
        afirme(res.isSucesso(), "Captura bem-sucedida! Teste superou a DT Final (" + res.getDtFinal() + ").");
        afirme(lobo.isDomado(), "Monstro agora está marcado como domado.");
        afirme(lobo.getDono() == jogador, "Dono do monstro vinculado ao jogador.");
        afirme(jogador.getMonstrosDomados().contains(lobo), "Monstro adicionado à lista monstrosDomados da Ficha.");
        afirme(!jogador.getInventario().contains(isca), "Isca consumida do inventário do jogador.");
    }

    private static void testarAtuacaoEmCombateMonstroDomado() {
        System.out.println("\n--- 6. Atuação em Combate do Monstro Domado ---");
        Ficha jogador = new Ficha(12, 12, 12, 12, 15, 12);
        Monstro grifo = Monstro.grifoAltoVale(); // FOR 20, DES 18
        jogador.adicionarMonstroDomado(grifo);

        Monstro alvoInimigo = Monstro.loboDasNeves();
        int hpAntes = alvoInimigo.getHp();

        HabilidadeCombate mergulho = grifo.getHabilidadesInatas().get(0); // 2d6 + FOR + DES
        // Rolagem 10 no dado. Dano bruto = 10 + 20 (FOR) + 18 (DES) = 48
        SistemaDoma.ResultadoComandoCombate res = SistemaDoma.comandarAtaque(jogador, grifo, mergulho, alvoInimigo, 10);

        afirme(res.isExecutado(), "Comando de ataque do monstro executado com sucesso.");
        afirme(res.getDanoBruto() == 48, "Dano bruto conferido (10 + 20 + 18 = 48).");
        afirme(res.getDanoReal() > 0, "Dano real infligido no alvo inimigo.");
        afirme(alvoInimigo.getHp() < hpAntes, "HP do alvo inimigo foi reduzido.");
    }

    private static void testarBestaDeCargaEVolumeColossal() {
        System.out.println("\n--- 7. Besta de Carga e Itens de TamanhoItem.COLOSSAL ---");
        Ficha jogador = new Ficha(10, 10, 10, 10, 10, 10); // Capacidade base: 50 + (10*3) + (10*2) = 100 kg | 50 slots

        Item cranioDragao = new Item(201, "Crânio de Dragão Primordial", "Troféu gigantesco", 100.0, 40.0,
                Item.TipoItem.OUTRO, Item.RaridadeItem.RARO, 1, TamanhoItem.COLOSSAL);

        // 1. Sem monstro de porte Grande/Colossal: deve ser rejeitado
        boolean pegouSemBesta = jogador.adicionarAoInventarioComValidacao(cranioDragao);
        afirme(!pegouSemBesta, "Jogador sem besta de carga NÃO pode carregar item de TamanhoItem.COLOSSAL.");
        afirme(!jogador.temBestaDeCargaParaColossal(), "temBestaDeCargaParaColossal() retorna false.");

        // 2. Doma Behemoth Ancestral (Porte COLOSSAL, FOR 32, VIG 26)
        Monstro behemoth = Monstro.behemothAncestral();
        jogador.adicionarMonstroDomado(behemoth);

        afirme(jogador.temBestaDeCargaParaColossal(), "temBestaDeCargaParaColossal() agora retorna true!");
        afirme(jogador.getCapacidadeCargaKg() > 300.0, "Capacidade de carga expandida pela força muscular da besta.");
        afirme(jogador.getCapacidadeMochilaSlots() >= 200.0, "Capacidade de slots expandida pelo porte colossal.");

        // 3. Com a besta de carga domada: o item colossal é aceito!
        boolean pegouComBesta = jogador.adicionarAoInventarioComValidacao(cranioDragao);
        afirme(pegouComBesta, "Item COLOSSAL adicionado ao inventário com sucesso graças à besta de carga domada!");
        afirme(jogador.getInventario().contains(cranioDragao), "Crânio de Dragão está presente no inventário.");
    }

    private static void testarPersistenciaMonstrosDomadosSQLite() {
        System.out.println("\n--- 8. Persistência de Monstros Domados no SQLite ---");
        FichaDAO fichaDAO = new FichaDAO();

        Ficha mestrePokemon = new Ficha(14, 12, 15, 10, 18, 12);
        mestrePokemon.setNome("Red de Pallet");

        Monstro lobo = Monstro.loboDasNeves();
        Monstro grifo = Monstro.grifoAltoVale();

        mestrePokemon.adicionarMonstroDomado(lobo);
        mestrePokemon.adicionarMonstroDomado(grifo);

        // Salva ficha com os 2 monstros no SQLite
        fichaDAO.salvar(mestrePokemon);
        afirme(mestrePokemon.getId() > 0, "Ficha salva com ID gerado: " + mestrePokemon.getId());
        afirme(lobo.getId() > 0, "Lobo domado salvo no SQLite com ID: " + lobo.getId());
        afirme(grifo.getId() > 0, "Grifo domado salvo no SQLite com ID: " + grifo.getId());

        // Recupera do SQLite
        Ficha carregada = fichaDAO.buscarPorId(mestrePokemon.getId());
        afirme(carregada != null, "Ficha recuperada do SQLite.");
        afirme(carregada.getMonstrosDomados().size() == 2, "2 monstros domados carregados da tabela ficha_monstros_domados.");

        Monstro m1 = carregada.getMonstrosDomados().get(0);
        afirme(m1.isDomado(), "Monstro carregado está marcado como domado.");
        afirme(m1.getDono() == carregada, "Dono do monstro carregado vinculado à ficha.");
        afirme(carregada.temBestaDeCargaParaColossal(), "Ficha recuperada mantém o suporte a Besta de Carga.");
    }
}
