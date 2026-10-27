package teste;

import economia.*;
import economia.ForjaArmas.*;
import geografia.*;
import persistencia.BancoDados;
import persistencia.ComercioDAO;
import persistencia.ForjaDAO;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

/**
 * Forja de armas: cálculo (ligas, dano, durabilidade, raridade), compilação para o catálogo
 * e as receitas, e a produção por etapas com peças reservadas e cancelamento.
 *
 * Usa um banco próprio (thaluria_teste_forja.db), apagado no início e no fim.
 */
public class TesteForja {

    private static final String BANCO_TESTE = "thaluria_teste_forja.db";
    private static int total = 0;
    private static int passaram = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("=== FORJA DE ARMAS ===\n");
        testarCalculo();
        testarProducaoPorEtapas();
        new File(BANCO_TESTE).delete();
        BancoDados.setUrlBanco("jdbc:sqlite:" + BANCO_TESTE);
        try {
            BancoDados.inicializarEsquema();
            testarCompilacao();
        } finally {
            BancoDados.setUrlBanco("jdbc:sqlite:thaluria.db");
            new File(BANCO_TESTE).delete();
        }
        System.out.printf("%n%d/%d testes passaram.%n", passaram, total);
        if (passaram != total) System.exit(1);
    }

    private static void afirme(boolean condicao, String mensagem) {
        total++;
        if (condicao) {
            passaram++;
            System.out.println("  [OK] " + mensagem);
        } else {
            System.err.println("  [FALHOU] " + mensagem);
            throw new AssertionError("Falha: " + mensagem);
        }
    }

    // ------------------------------------------------------------------

    private static final Map<Integer, Material> MATERIAIS = Map.of(
            1, new Material(1, "Ferro", CategoriaMaterial.METAL, 2, 80, 1.0, 100, Item.RaridadeItem.COMUM),
            2, new Material(2, "Cobre", CategoriaMaterial.METAL, 1, 60, 1.0, 80, Item.RaridadeItem.COMUM),
            3, new Material(3, "Aço Negro", CategoriaMaterial.METAL, 4, 120, 1.2, 1000, Item.RaridadeItem.RARO),
            4, new Material(4, "Couro", CategoriaMaterial.COURO, 0, 40, 0.2, 50, Item.RaridadeItem.COMUM),
            5, new Material(5, "Seda da Aranha-Tecelã", CategoriaMaterial.FIBRA, 0, 70, 0.1, 500, Item.RaridadeItem.EPICO),
            6, new Material(6, "Linho", CategoriaMaterial.FIBRA, 0, 30, 0.1, 20, Item.RaridadeItem.COMUM),
            7, new Material(7, "Teixo", CategoriaMaterial.MADEIRA, 1, 60, 0.8, 120, Item.RaridadeItem.COMUM));

    private static Molde espada() {
        return new Molde(1, "Espada", Lojas.TipoEstabelecimento.FORJA, 1, 8, List.of(
                new Parte(10, "Lâmina", 2, 16, List.of(new Opcao(1, CategoriaMaterial.METAL, null, 0, 0, 0))),
                new Parte(11, "Cabo", 1, 4, List.of(new Opcao(2, CategoriaMaterial.COURO, null, 0, 0, 0)))));
    }

    private static void testarCalculo() {
        System.out.println("-- Cálculo da arma");
        TipoArma longa = new TipoArma(1, "Espada Longa", 1, Item.TipoItem.ARMA_BRANCA, 1, 8, 0);

        Arma liga = ForjaArmas.calcular("Teste", longa, espada(), List.of(new Escolha(10, 1, 1), new Escolha(10, 2, 1), new Escolha(11, 4, 1)), MATERIAIS);
        afirme(liga.valida(), "Espada com lâmina de dois metais e cabo de couro é válida");
        afirme("Lâmina de Liga Metálica de Cobre e Ferro".equals(liga.componentes().get(0).nome()), "Metais diferentes viram liga, em ordem alfabética");
        Arma inversa = ForjaArmas.calcular("Teste", longa, espada(), List.of(new Escolha(10, 2, 1), new Escolha(10, 1, 1), new Escolha(11, 4, 1)), MATERIAIS);
        afirme(liga.componentes().get(0).chave().equals(inversa.componentes().get(0).chave()), "Ferro + Cobre e Cobre + Ferro são a mesma peça");
        afirme("1d8+2".equals(liga.dano()), "Dano = dado do tipo + média da liga (1,5) + cabo (0), arredondado: 1d8+2");
        afirme(liga.durabilidade() == 40, "Durabilidade da arma é a da peça mais fraca (o cabo de couro)");
        afirme(Math.abs(liga.pesoKg() - 2.2) < 1e-9, "Peso é a soma dos materiais");
        long lamina = Math.round(180 * 1.1), cabo = Math.round(50 * 1.1);
        afirme(liga.precoLatao() == Math.round((lamina + cabo) * 1.1), "Preço = materiais + mão de obra na peça e na montagem");
        afirme(liga.tempoHoras() == 16 + 4 + 8, "Tempo = subcrafts + montagem");

        Arma ferro = ForjaArmas.calcular("T", longa, espada(), List.of(new Escolha(10, 1, 2), new Escolha(11, 4, 1)), MATERIAIS);
        afirme("Lâmina de Ferro".equals(ferro.componentes().get(0).nome()), "O mesmo metal repetido não é liga");

        Arma rara = ForjaArmas.calcular("T", longa, espada(), List.of(new Escolha(10, 3, 2), new Escolha(11, 4, 1)), MATERIAIS);
        afirme(rara.raridade() == Item.RaridadeItem.INCOMUM, "Raridade ponderada pela quantidade: 2× Raro + 1× Comum = Incomum (2,33)");
        afirme("1d8+4".equals(rara.dano()), "Sem teto: material melhor, arma mais forte");

        Arma incompleta = ForjaArmas.calcular("T", longa, espada(), List.of(new Escolha(10, 1, 1), new Escolha(11, 4, 1)), MATERIAIS);
        afirme(!incompleta.valida() && incompleta.problemas().get(0).contains("Lâmina"), "Parte com material a menos é recusada");
        Arma errada = ForjaArmas.calcular("T", longa, espada(), List.of(new Escolha(10, 1, 2), new Escolha(11, 1, 1)), MATERIAIS);
        afirme(!errada.valida() && errada.problemas().get(0).contains("não é aceito"), "Material de categoria não aceita na parte é recusado");

        // Arco: a seda específica na corda dá bônus próprio, só naquela parte
        Molde arco = new Molde(2, "Arco", Lojas.TipoEstabelecimento.FORJA, 1, 8, List.of(
                new Parte(20, "Corpo", 1, 12, List.of(new Opcao(3, CategoriaMaterial.MADEIRA, null, 0, 0, 0), new Opcao(4, CategoriaMaterial.METAL, null, 0, 0, 0))),
                new Parte(21, "Corda", 1, 4, List.of(new Opcao(5, CategoriaMaterial.FIBRA, null, 0, 0, 0), new Opcao(6, null, 5, 2, 0, 20)))));
        TipoArma arcoLongo = new TipoArma(2, "Arco Longo", 2, Item.TipoItem.ARMA_LONGO_ALCANCE, 1, 8, 60);
        Arma comSeda = ForjaArmas.calcular("T", arcoLongo, arco, List.of(new Escolha(20, 7, 1), new Escolha(21, 5, 1)), MATERIAIS);
        Arma comLinho = ForjaArmas.calcular("T", arcoLongo, arco, List.of(new Escolha(20, 7, 1), new Escolha(21, 6, 1)), MATERIAIS);
        afirme(comSeda.alcanceM() == 80 && "1d8+3".equals(comSeda.dano()), "Opção de item específico (seda) soma bônus próprio: +2 dano, +20 m");
        afirme(comLinho.alcanceM() == 60 && "1d8+1".equals(comLinho.dano()), "Fibra comum usa a opção da categoria, sem bônus extra");
        Arma arcoMetal = ForjaArmas.calcular("T", arcoLongo, arco, List.of(new Escolha(20, 1, 1), new Escolha(21, 6, 1)), MATERIAIS);
        afirme(arcoMetal.valida(), "Arco pode ter corpo de metal quando o molde aceita");
    }

    // ------------------------------------------------------------------

    private static void testarProducaoPorEtapas() {
        System.out.println("-- Produção por etapas");
        Cidade c = new Cidade(9001, "Forjaria T", Cidade.TipoAssentamento.CIDADE, Regiao.TEMPERADO, Geologia.PLANICIE, Aquifero.RIO, false, false, null);
        Item ferro = new Item(1, "Ferro", "", 0.01, 1, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 1);
        Item couro = new Item(4, "Couro", "", 0.005, 0.2, Item.TipoItem.MATERIAL, Item.RaridadeItem.COMUM, 1);
        Item lamina = new Item(50, "Lâmina de Ferro", "", 0.022, 2, Item.TipoItem.COMPONENTE_ARMA, Item.RaridadeItem.COMUM, 1);
        Item cabo = new Item(51, "Cabo de Couro", "", 0.0055, 0.2, Item.TipoItem.COMPONENTE_ARMA, Item.RaridadeItem.COMUM, 1);
        Item espada = new Item(52, "Espada de Ferro", "", 0.03, 2.2, Item.TipoItem.ARMA_BRANCA, Item.RaridadeItem.COMUM, 1);
        Map<Integer, Item> catalogo = Map.of(1, ferro, 4, couro, 50, lamina, 51, cabo, 52, espada);
        ReceitaLoja receita = new ReceitaLoja(7, "Espada de Ferro", 52, 1, 16 + 4 + 8, Lojas.TipoEstabelecimento.FORJA,
                List.of(new ReceitaLoja.Insumo(1, 2), new ReceitaLoja.Insumo(4, 1)),
                List.of(new ReceitaLoja.Etapa(50, "Lâmina", 16, List.of(new ReceitaLoja.Insumo(1, 2))),
                        new ReceitaLoja.Etapa(51, "Cabo", 4, List.of(new ReceitaLoja.Insumo(4, 1)))), false);
        Map<Integer, ReceitaLoja> receitas = Map.of(7, receita);

        Lojas forja = new Lojas("Forja T", "Brom", c, false, null, Lojas.TipoEstabelecimento.FORJA);
        forja.setCaixa(Carteira.deLatao(100_000));
        forja.adicionarItem(Lojas.clonarItem(ferro, 2));
        afirme(!forja.insumosFaltantes(receita, 1).isEmpty(), "Sem couro (nem cabo pronto), faltam insumos");
        forja.adicionarItem(Lojas.clonarItem(couro, 1));
        OrdemReceita ordem = forja.iniciarReceita(receita, espada, 1, 1, 8, OrdemReceita.Origem.MESTRE, false);
        afirme(ordem != null && ordem.getEtapas().size() == 2, "A ordem da espada abre com uma etapa por parte");
        afirme(forja.getQuantidadeEstoque(1) == 0 && forja.getQuantidadeEstoque(4) == 0, "Os materiais saem do estoque na abertura");
        afirme(ordem.getEtapas().get(0).getDiaConclusao() == 2 && ordem.getEtapas().get(1).getDiaConclusao() == 2 && ordem.getDiaConclusao() == 3,
                "Lâmina (16h) e cabo (+4h) ficam prontos no dia 2; a montagem (+8h) termina no dia 3");

        forja.concluirOrdensProntas(2, 8, receitas, catalogo);
        afirme(forja.getReservados().getOrDefault(50, 0) == 1 && forja.getQuantidadeEstoque(50) == 0,
                "A lâmina pronta fica reservada para a encomenda e não entra no estoque vendável");

        OrdemReceita copia = new OrdemReceita(0, 7, 1, 1, 3, OrdemReceita.Origem.MESTRE, false, OrdemReceita.lerEtapas(ordem.etapasSerializadas()));
        afirme(copia.getEtapas().get(0).isReservada() && copia.getEtapas().get(0).getComponenteItemId() == 50, "As etapas sobrevivem à gravação no banco");

        forja.cancelarOrdem(ordem, 2, 9, receitas, catalogo);
        afirme(forja.getQuantidadeEstoque(50) == 1 && forja.getQuantidadeEstoque(51) == 1 && forja.getReservados().isEmpty(),
                "Cancelada a encomenda, as peças prontas viram estoque comum");

        // Com as peças já na prateleira, a nova ordem só espera a montagem
        OrdemReceita rapida = forja.iniciarReceita(receita, espada, 1, 2, 8, OrdemReceita.Origem.MESTRE, false);
        afirme(rapida != null && rapida.getDiaConclusao() == 3 && forja.getQuantidadeEstoque(50) == 0,
                "Peças livres na prateleira são usadas direto e poupam o tempo das etapas");
        forja.concluirOrdensProntas(3, 8, receitas, catalogo);
        afirme(forja.getQuantidadeEstoque(52) == 1 && forja.getReservados().isEmpty(), "Na montagem, as peças reservadas viram a espada");

        // Cancelar com materiais ainda na bancada devolve os materiais
        forja.adicionarItem(Lojas.clonarItem(ferro, 2));
        forja.adicionarItem(Lojas.clonarItem(couro, 1));
        OrdemReceita outra = forja.iniciarReceita(receita, espada, 1, 3, 8, OrdemReceita.Origem.IA, false);
        forja.cancelarOrdem(outra, 3, 9, receitas, catalogo);
        afirme(forja.getQuantidadeEstoque(1) == 2 && forja.getQuantidadeEstoque(4) == 1, "Etapas não começadas devolvem os materiais ao cancelar");

        ReceitaLoja soEncomenda = new ReceitaLoja(8, "Peça", 50, 1, 16, Lojas.TipoEstabelecimento.FORJA, List.of(new ReceitaLoja.Insumo(1, 2)), List.of(), true);
        forja.definirDemanda(lamina, 5);
        afirme(IALojistaDeterministica.decidirProducaoDoDia(forja, List.of(soEncomenda), catalogo, 3, 8) == null,
                "A IA do mercador não produz receitas só por encomenda");
    }

    // ------------------------------------------------------------------

    private static void sql(String... comandos) throws Exception {
        try (Connection c = BancoDados.getConnection(); Statement st = c.createStatement()) {
            for (String s : comandos) st.executeUpdate(s);
        }
    }

    private static long contar(String sql) throws Exception {
        try (Connection c = BancoDados.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static void testarCompilacao() throws Exception {
        System.out.println("-- Sementes e compilação");
        sql("INSERT INTO itens (id, nome, preco_latao, peso_kg, tipo, raridade, tier) VALUES (1, 'Lingote de Ferro', 100, 1, 'MATERIAL', 'COMUM', 1)",
            "INSERT INTO itens (id, nome, preco_latao, peso_kg, tipo, raridade, tier) VALUES (2, 'Barra de Cobre', 80, 1, 'MATERIAL', 'COMUM', 1)",
            "INSERT INTO itens (id, nome, preco_latao, peso_kg, tipo, raridade, tier) VALUES (3, 'Couro Curtido', 50, 0.2, 'MATERIAL', 'COMUM', 1)",
            "INSERT INTO itens (id, nome, preco_latao, peso_kg, tipo, raridade, tier) VALUES (4, 'Saco de Trigo', 40, 5, 'MATERIAL', 'COMUM', 1)");
        ForjaDAO.semear();
        ForjaDAO dao = new ForjaDAO();
        ForjaDAO.Catalogo c = dao.carregarCatalogo();
        afirme(c.moldes().values().stream().anyMatch(m -> m.nome().equals("Espada") && m.partes().size() == 4), "Molde Espada semeado com Lâmina, Guarda, Cabo e Pomo");
        TipoArma katana = c.tipos().values().stream().filter(t -> t.nome().equals("Katana")).findFirst().orElseThrow();
        afirme(katana.facesDado() == 10, "Tipos de arma vêm das armas brancas do sistema (Katana 1d10)");
        afirme(c.materiais().get(1).categoria() == CategoriaMaterial.METAL && c.materiais().get(3).categoria() == CategoriaMaterial.COURO
                && c.materiais().get(4).categoria() == null, "Materiais existentes ganham categoria pelo nome (trigo não é material de arma)");

        Molde espada = c.moldes().get(katana.moldeId());
        Map<String, Integer> partes = new HashMap<>();
        espada.partes().forEach(p -> partes.put(p.nome(), p.id()));
        List<Escolha> escolhas = List.of(new Escolha(partes.get("Lâmina"), 1, 1), new Escolha(partes.get("Lâmina"), 2, 1),
                new Escolha(partes.get("Guarda"), 1, 1), new Escolha(partes.get("Cabo"), 3, 1), new Escolha(partes.get("Pomo"), 1, 1));
        ForjaDAO.ReceitaArma r = dao.salvarReceita(null, "Katana do Teste", katana.id(), escolhas, false, null);
        afirme(r.receitaId() != null && r.itemId() != null, "A receita de arma é compilada para uma receita comum e um item");
        afirme(contar("SELECT COUNT(*) FROM itens WHERE tipo = 'COMPONENTE_ARMA'") == 4, "Cada parte vira um item Componente de Arma");
        afirme(contar("SELECT COUNT(*) FROM itens WHERE nome = 'Lâmina de Liga Metálica de Barra de Cobre e Lingote de Ferro'") == 1, "A lâmina de dois metais é batizada como liga");
        afirme(contar("SELECT COUNT(*) FROM receita_etapas WHERE receita_id = " + r.receitaId()) == 4, "A receita da arma tem uma etapa por parte");
        afirme(contar("SELECT COUNT(*) FROM receitas WHERE somente_encomenda = 1 AND chave_forja LIKE 'RC:%'") == 4, "Os subcrafts das peças são só por encomenda");

        dao.salvarReceita(r.id(), "Katana do Teste", katana.id(), escolhas, false, "Recompilada");
        dao.recompilarTudo();
        afirme(contar("SELECT COUNT(*) FROM itens WHERE tipo = 'COMPONENTE_ARMA'") == 4 && contar("SELECT COUNT(*) FROM receitas") == 5,
                "Recompilar não duplica peças nem receitas");

        ReceitaLoja comum = new ComercioDAO().listarReceitas().stream().filter(x -> x.id() == r.receitaId()).findFirst().orElseThrow();
        afirme(comum.etapas().size() == 4 && comum.insumos().stream().anyMatch(i -> i.itemId() == 1 && i.quantidade() == 3),
                "O motor lê a receita com etapas e soma os materiais (3× ferro)");

        int enc1 = dao.compilarEncomenda(katana.id(), escolhas);
        int enc2 = dao.compilarEncomenda(katana.id(), escolhas);
        afirme(enc1 == enc2 && enc1 != r.receitaId(), "Encomenda personalizada com a mesma composição reaproveita a receita");
        afirme(new ComercioDAO().receitasInternasDaForja().contains(enc1), "Encomendas avulsas não poluem a lista de receitas da oficina");

        sql("UPDATE itens SET bonus_dano = 10 WHERE id = 1");
        dao.recompilarTudo();
        afirme(contar("SELECT bonus_dano FROM item_arma WHERE item_id = " + r.itemId()) > 10, "Mudar o bônus do material recalcula a arma");

        try {
            dao.excluirTipo(katana.id());
            afirme(false, "Tipo com receitas não pode ser excluído");
        } catch (RuntimeException e) {
            afirme(true, "Tipo com receitas não pode ser excluído");
        }
        dao.excluirReceita(r.id());
        afirme(contar("SELECT COUNT(*) FROM receitas WHERE id = " + r.receitaId()) == 0, "Excluir a receita de arma remove a receita comum");
    }
}
