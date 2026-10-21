package economia;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Modela um efeito mecânico especial concedido por um item, material, consumível, encantamento ou passiva.
 * Suporta modificadores diretos de status, multiplicação global de dano e cura instantânea.
 */
public class EfeitoItem {

    public enum GatilhoEfeito {
        AO_ACERTAR("Ao Acertar Ataque"),
        AO_SOFRER_DANO("Ao Sofrer Dano / Espinhos"),
        AO_CONSUMIR("Ao Consumir"),
        PASSIVO_CONTINUO("Passivo Contínuo"),
        CURA_CONDICAO("Cura Condição / Antídoto"),
        ATIVACAO_DE_PASSIVA("Ativação de Passiva");

        private final String descricao;

        GatilhoEfeito(String descricao) {
            this.descricao = descricao;
        }

        public String getDescricao() {
            return descricao;
        }

        @Override
        public String toString() {
            return descricao;
        }
    }

    private final int id;
    private final String nome;
    private final GatilhoEfeito gatilho;
    private final String dadoDanoOuCura; // Ex: "1d4", "2d6", "+10"
    private final String elementoOuStatus; // Ex: "Gelo / Congelamento", "Fogo", "Fúria"
    private final int duracaoTurnos; // -1 = permanente enquanto equipado; > 0 = turnos
    private final String descricao;

    // Campos mecânicos avançados (Item v4.1)
    private final Map<String, Integer> bonusStatus; // Ex: {"FORCA": 8, "DESTREZA": 4, "RD": 5}
    private final double multiplicadorDano;         // Ex: 1.0 (normal), 2.0 (DOBRA O DANO!)
    private final int curaInstantaneaHp;

    public EfeitoItem(int id, String nome, GatilhoEfeito gatilho, String dadoDanoOuCura,
                      String elementoOuStatus, int duracaoTurnos) {
        this(id, nome, gatilho, dadoDanoOuCura, elementoOuStatus, duracaoTurnos, "",
                Collections.emptyMap(), 1.0, 0);
    }

    public EfeitoItem(int id, String nome, GatilhoEfeito gatilho, String dadoDanoOuCura,
                      String elementoOuStatus, int duracaoTurnos, String descricao) {
        this(id, nome, gatilho, dadoDanoOuCura, elementoOuStatus, duracaoTurnos, descricao,
                Collections.emptyMap(), 1.0, 0);
    }

    public EfeitoItem(int id, String nome, GatilhoEfeito gatilho, String dadoDanoOuCura,
                      String elementoOuStatus, int duracaoTurnos,
                      Map<String, Integer> bonusStatus, double multiplicadorDano, int curaInstantaneaHp) {
        this(id, nome, gatilho, dadoDanoOuCura, elementoOuStatus, duracaoTurnos, "",
                bonusStatus, multiplicadorDano, curaInstantaneaHp);
    }

    public EfeitoItem(int id, String nome, GatilhoEfeito gatilho, String dadoDanoOuCura,
                      String elementoOuStatus, int duracaoTurnos, String descricao,
                      Map<String, Integer> bonusStatus, double multiplicadorDano, int curaInstantaneaHp) {
        this.id = id;
        this.nome = (nome != null && !nome.trim().isEmpty()) ? nome.trim() : "Efeito Geral";
        this.gatilho = (gatilho != null) ? gatilho : GatilhoEfeito.PASSIVO_CONTINUO;
        this.dadoDanoOuCura = (dadoDanoOuCura != null) ? dadoDanoOuCura.trim() : "0";
        this.elementoOuStatus = (elementoOuStatus != null) ? elementoOuStatus.trim() : "Físico";
        this.duracaoTurnos = duracaoTurnos;
        this.descricao = (descricao != null) ? descricao : "";
        this.bonusStatus = (bonusStatus != null) ? new LinkedHashMap<>(bonusStatus) : new LinkedHashMap<>();
        this.multiplicadorDano = Math.max(0.1, multiplicadorDano);
        this.curaInstantaneaHp = Math.max(0, curaInstantaneaHp);
    }

    public int getId() { return id; }
    public String getNome() { return nome; }
    public GatilhoEfeito getGatilho() { return gatilho; }
    public String getDadoDanoOuCura() { return dadoDanoOuCura; }
    public String getElementoOuStatus() { return elementoOuStatus; }
    public int getDuracaoTurnos() { return duracaoTurnos; }
    public String getDescricao() { return descricao; }
    public Map<String, Integer> getBonusStatus() { return Collections.unmodifiableMap(bonusStatus); }
    public double getMultiplicadorDano() { return multiplicadorDano; }
    public int getCuraInstantaneaHp() { return curaInstantaneaHp; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EfeitoItem that = (EfeitoItem) o;
        return id == that.id && Objects.equals(nome, that.nome);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, nome);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(nome)
                .append(" [").append(gatilho).append(" | ")
                .append(dadoDanoOuCura).append(" ").append(elementoOuStatus);
        if (multiplicadorDano != 1.0) {
            sb.append(" | Dano x").append(multiplicadorDano);
        }
        if (!bonusStatus.isEmpty()) {
            sb.append(" | Status: ").append(bonusStatus);
        }
        if (curaInstantaneaHp > 0) {
            sb.append(" | Cura: +").append(curaInstantaneaHp).append(" HP");
        }
        if (duracaoTurnos > 0) {
            sb.append(" por ").append(duracaoTurnos).append(" turno(s)");
        }
        sb.append("]");
        return sb.toString();
    }
}
