package personagem;

import java.util.EnumMap;
import java.util.Map;

/**
 * Níveis de Conforto do descanso (docs/Status.md) com o multiplicador padrão Q em centésimos
 * (aritmética inteira: sem erros de ponto flutuante).
 */
public enum ConfortoDescanso {
    PESSIMO("Péssimo (Hostil)", 25),
    PRECARIO("Precário (Improvisado)", 50),
    PADRAO("Padrão (Acampamento)", 100),
    CONFORTAVEL("Confortável (Bom)", 125),
    LUXUOSO("Luxuoso / Santuário", 150);

    private final String descricao;
    private final int qPadraoCentesimos;

    ConfortoDescanso(String descricao, int qPadraoCentesimos) {
        this.descricao = descricao;
        this.qPadraoCentesimos = qPadraoCentesimos;
    }

    public String getDescricao() { return descricao; }
    public int getQPadraoCentesimos() { return qPadraoCentesimos; }
}
