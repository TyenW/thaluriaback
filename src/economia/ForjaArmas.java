package economia;

import java.util.*;

/**
 * Regras da forja de armas: material → componente (subcraft) → arma (montagem).
 *
 * Cada família de arma tem um {@link Molde} com partes; cada parte consome uma quantidade
 * de materiais e aceita opções (uma categoria de material ou um item específico), e cada
 * opção pode somar um bônus próprio daquela parte. Não há teto: quanto melhor o material,
 * mais forte a arma.
 *
 * Fórmulas (decididas com o Mestre; ver docs/Sistema de Economia/04):
 * <ul>
 *   <li>Componente: média do bônus de dano e da durabilidade dos materiais; soma de peso;
 *       preço = soma dos materiais + mão de obra; raridade = média ponderada pela quantidade.</li>
 *   <li>Arma: dado do tipo + soma dos bônus de dano das partes; durabilidade da peça mais fraca;
 *       peso somado; preço = soma dos componentes + mão de obra; raridade ponderada pela
 *       quantidade de cada material; alcance = base do tipo + bônus das opções.</li>
 * </ul>
 * O bônus das opções vale para a parte dentro da arma, não para o componente: a mesma
 * Lâmina de Ferro serve a qualquer molde.
 */
public final class ForjaArmas {

    private ForjaArmas() {}

    public enum CategoriaMaterial {
        METAL("Metal"), MADEIRA("Madeira"), COURO("Couro"), FIBRA("Fibra"),
        GEMA("Gema"), OSSO("Osso"), ESCAMA("Escama"), OUTRO("Outro");

        private final String rotulo;
        CategoriaMaterial(String rotulo) { this.rotulo = rotulo; }
        public String getRotulo() { return rotulo; }

        public static CategoriaMaterial de(String nome) {
            if (nome == null || nome.isBlank()) return null;
            try {
                return valueOf(nome.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** Propriedades de forja de um item do catálogo usado como material. */
    public record Material(int itemId, String nome, CategoriaMaterial categoria, double bonusDano, double durabilidade,
                           double pesoKg, long precoLatao, Item.RaridadeItem raridade) {}

    /** O que uma parte aceita: uma categoria de material ou um item específico, com bônus próprio. */
    public record Opcao(int id, CategoriaMaterial categoria, Integer itemId, int bonusDano, int bonusDurabilidade, int bonusAlcanceM) {
        public boolean aceita(Material m) {
            if (itemId != null) return itemId == m.itemId();
            return categoria != null && categoria == m.categoria();
        }
    }

    public record Parte(int id, String nome, int qtdMateriais, double tempoHoras, List<Opcao> opcoes) {
        public Parte {
            opcoes = List.copyOf(opcoes);
            qtdMateriais = Math.max(1, qtdMateriais);
        }

        /** Opção que vale para o material: a de item específico tem prioridade sobre a de categoria. */
        public Opcao opcaoPara(Material m) {
            Opcao porCategoria = null;
            for (Opcao o : opcoes) {
                if (o.itemId() != null && o.aceita(m)) return o;
                if (o.itemId() == null && o.aceita(m) && porCategoria == null) porCategoria = o;
            }
            return porCategoria;
        }
    }

    public record Molde(int id, String nome, Lojas.TipoEstabelecimento tipoLoja, int slotsEfeito, double tempoMontagemHoras, List<Parte> partes) {
        public Molde {
            partes = List.copyOf(partes);
        }
    }

    public record TipoArma(int id, String nome, int moldeId, Item.TipoItem tipoItem, int qtdDados, int facesDado, int alcanceM) {
        public String dado() { return qtdDados + "d" + facesDado; }
    }

    /** Quanto de um material vai numa parte. */
    public record Escolha(int parteId, int itemId, int quantidade) {}

    public record Componente(Parte parte, String nome, String chave, Map<Integer, Integer> materiais,
                             double bonusDano, double durabilidade, double pesoKg, long precoLatao, Item.RaridadeItem raridade) {}

    public record Arma(String nome, String dano, int bonusDano, int durabilidade, double pesoKg, long precoLatao,
                       Item.RaridadeItem raridade, int alcanceM, int slotsEfeito, double tempoHoras,
                       List<Componente> componentes, List<String> problemas) {
        public boolean valida() { return problemas.isEmpty(); }
    }

    /**
     * Calcula a arma montada com os materiais escolhidos. Nunca lança exceção por escolha
     * inválida: devolve os problemas encontrados (parte incompleta, material não aceito...).
     */
    public static Arma calcular(String nome, TipoArma tipo, Molde molde, List<Escolha> escolhas, Map<Integer, Material> materiais) {
        List<String> problemas = new ArrayList<>();
        List<Componente> componentes = new ArrayList<>();
        double somaBonus = 0, alcance = tipo.alcanceM();
        double menorDurabilidade = Double.MAX_VALUE;
        double peso = 0, tempo = molde.tempoMontagemHoras();
        long precoComponentes = 0;
        Map<Item.RaridadeItem, Integer> pesoRaridade = new EnumMap<>(Item.RaridadeItem.class);

        for (Parte parte : molde.partes()) {
            Map<Integer, Integer> usados = new TreeMap<>();
            for (Escolha e : escolhas) {
                if (e.parteId() == parte.id() && e.quantidade() > 0) usados.merge(e.itemId(), e.quantidade(), Integer::sum);
            }
            int total = usados.values().stream().mapToInt(Integer::intValue).sum();
            if (total != parte.qtdMateriais()) {
                problemas.add(parte.nome() + ": usa " + parte.qtdMateriais() + " material(is), " + (total == 0 ? "nenhum escolhido" : total + " escolhido(s)") + ".");
                continue;
            }

            double bonusMat = 0, durMat = 0, bonusOp = 0, durOp = 0, alcOp = 0, pesoParte = 0;
            long precoParte = 0;
            boolean ok = true;
            for (Map.Entry<Integer, Integer> u : usados.entrySet()) {
                Material m = materiais.get(u.getKey());
                if (m == null) {
                    problemas.add(parte.nome() + ": material #" + u.getKey() + " não existe mais no catálogo.");
                    ok = false;
                    continue;
                }
                Opcao o = parte.opcaoPara(m);
                if (o == null) {
                    problemas.add(parte.nome() + ": " + m.nome() + " não é aceito nesta parte.");
                    ok = false;
                    continue;
                }
                int q = u.getValue();
                bonusMat += m.bonusDano() * q;
                durMat += m.durabilidade() * q;
                bonusOp += o.bonusDano() * q;
                durOp += o.bonusDurabilidade() * q;
                alcOp += o.bonusAlcanceM() * q;
                pesoParte += m.pesoKg() * q;
                precoParte += m.precoLatao() * q;
                pesoRaridade.merge(m.raridade(), q, Integer::sum);
            }
            if (!ok) continue;

            int n = parte.qtdMateriais();
            Componente c = new Componente(parte, nomeComponente(parte.nome(), usados, materiais), chaveComponente(parte.nome(), usados),
                    usados, bonusMat / n, durMat / n, pesoParte, comMaoDeObra(precoParte), raridadePonderada(raridadesDe(usados, materiais)));
            componentes.add(c);
            somaBonus += c.bonusDano() + bonusOp / n;
            menorDurabilidade = Math.min(menorDurabilidade, c.durabilidade() + durOp / n);
            alcance += alcOp / n;
            peso += c.pesoKg();
            precoComponentes += c.precoLatao();
            tempo += parte.tempoHoras();
        }

        int bonus = (int) Math.round(somaBonus);
        String dano = tipo.dado() + (bonus > 0 ? "+" + bonus : bonus < 0 ? String.valueOf(bonus) : "");
        return new Arma(nome, dano, bonus, menorDurabilidade == Double.MAX_VALUE ? 0 : (int) Math.round(menorDurabilidade),
                Math.round(peso * 100.0) / 100.0, comMaoDeObra(precoComponentes), raridadePonderada(pesoRaridade),
                (int) Math.round(alcance), molde.slotsEfeito(), tempo, List.copyOf(componentes), List.copyOf(problemas));
    }

    /** Slots extras pela qualidade da oficina que fabrica (nível da loja = grau de feitura). */
    public static int slotsPelaQualidade(int nivelLoja) {
        return QualidadeCraft.doGrau(nivelLoja).getSlotsEfeitoMaximos();
    }

    static long comMaoDeObra(long precoMateriais) {
        return Math.round(precoMateriais * (1 + ReceitaLoja.FRACAO_MAO_DE_OBRA));
    }

    /** Raridade média ponderada pela quantidade: Comum = 1, Incomum = 2... arredondada. */
    static Item.RaridadeItem raridadePonderada(Map<Item.RaridadeItem, Integer> pesos) {
        double soma = 0, total = 0;
        for (Map.Entry<Item.RaridadeItem, Integer> e : pesos.entrySet()) {
            soma += (e.getKey().ordinal() + 1) * (double) e.getValue();
            total += e.getValue();
        }
        if (total == 0) return Item.RaridadeItem.COMUM;
        int nivel = (int) Math.floor(soma / total + 0.5);
        Item.RaridadeItem[] valores = Item.RaridadeItem.values();
        return valores[Math.max(1, Math.min(valores.length, nivel)) - 1];
    }

    private static Map<Item.RaridadeItem, Integer> raridadesDe(Map<Integer, Integer> usados, Map<Integer, Material> materiais) {
        Map<Item.RaridadeItem, Integer> r = new EnumMap<>(Item.RaridadeItem.class);
        usados.forEach((id, q) -> r.merge(materiais.get(id).raridade(), q, Integer::sum));
        return r;
    }

    /**
     * "Lâmina de Ferro"; metais diferentes viram liga: "Lâmina de Liga Metálica de Cobre e Ferro".
     * Os nomes vão em ordem alfabética para Ferro + Cobre e Cobre + Ferro darem a mesma peça.
     */
    public static String nomeComponente(String parte, Map<Integer, Integer> usados, Map<Integer, Material> materiais) {
        TreeSet<String> nomes = new TreeSet<>(java.text.Collator.getInstance(Locale.forLanguageTag("pt-BR")));
        boolean todosMetal = true;
        for (Integer id : usados.keySet()) {
            Material m = materiais.get(id);
            nomes.add(m.nome());
            if (m.categoria() != CategoriaMaterial.METAL) todosMetal = false;
        }
        String lista = juntar(new ArrayList<>(nomes));
        if (nomes.size() > 1 && todosMetal) return parte + " de Liga Metálica de " + lista;
        return parte + " de " + lista;
    }

    /** Identidade da peça: parte + materiais e quantidades. A mesma composição nunca vira dois itens. */
    public static String chaveComponente(String parte, Map<Integer, Integer> usados) {
        StringJoiner j = new StringJoiner(",", "C:" + parte.trim().toUpperCase(Locale.ROOT) + ":", "");
        new TreeMap<>(usados).forEach((id, q) -> j.add(id + "x" + q));
        return j.toString();
    }

    static String juntar(List<String> nomes) {
        if (nomes.size() <= 1) return nomes.isEmpty() ? "" : nomes.get(0);
        return String.join(", ", nomes.subList(0, nomes.size() - 1)) + " e " + nomes.get(nomes.size() - 1);
    }
}
