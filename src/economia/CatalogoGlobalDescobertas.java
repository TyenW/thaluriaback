package economia;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Repositório central e canônico de fórmulas, receitas e espécimes botânicos descobertos
 * pelo grupo de aventureiros no mundo de Thaluria.
 * Permite que receitas raras comecem ocultas e sejam destravadas no mercado conforme a exploração avança.
 */
public class CatalogoGlobalDescobertas {

    private static CatalogoGlobalDescobertas instancia;

    private final Set<Integer> receitasDescobertas = new HashSet<>();
    private final Set<Integer> plantasDescobertas = new HashSet<>();
    private final Set<Integer> itensDescobertos = new HashSet<>();

    public CatalogoGlobalDescobertas() {}

    public static synchronized CatalogoGlobalDescobertas getInstancia() {
        if (instancia == null) {
            instancia = new CatalogoGlobalDescobertas();
        }
        return instancia;
    }

    public synchronized void registrarDescobertaReceita(int idMoldeReceita) {
        receitasDescobertas.add(idMoldeReceita);
    }

    public synchronized boolean isReceitaDescoberta(int idMoldeReceita) {
        return receitasDescobertas.contains(idMoldeReceita);
    }

    public synchronized void registrarDescobertaPlanta(int idPlanta) {
        plantasDescobertas.add(idPlanta);
    }

    public synchronized boolean isPlantaDescoberta(int idPlanta) {
        return plantasDescobertas.contains(idPlanta);
    }

    public synchronized void registrarDescobertaItem(int idItem) {
        itensDescobertos.add(idItem);
    }

    public synchronized boolean isItemDescoberto(int idItem) {
        return itensDescobertos.contains(idItem);
    }

    public synchronized void limparDescobertas() {
        receitasDescobertas.clear();
        plantasDescobertas.clear();
        itensDescobertos.clear();
    }

    public synchronized Set<Integer> getReceitasDescobertas() {
        return Collections.unmodifiableSet(new HashSet<>(receitasDescobertas));
    }

    public synchronized Set<Integer> getPlantasDescobertas() {
        return Collections.unmodifiableSet(new HashSet<>(plantasDescobertas));
    }
}
