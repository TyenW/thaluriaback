package economia;

import geografia.Cidade;
import java.util.*;

/**
 * Fábrica inteligente de manufatura que orquestra o Crafting (v5) em Thaluria.
 * Valida moldes, consome insumos com FichaMaterial, calcula o grau exato de QualidadeCraft (1 a 7),
 * aplica ergonomia de peso, bônus de dados/RD, herança geográfica ("Regra do Camarão")
 * e injeta efeitos nos slots da peça fabricada.
 */
public class OficinaCrafting {

    /**
     * Valida se a loja possui especialização compatível e nível de ferramental suficiente.
     */
    public static boolean lojaAptaParaMolde(Lojas loja, MoldeReceita molde) {
        if (loja == null || molde == null) return false;

        boolean areaCompativel = (loja.getTipoEstabelecimento() == molde.getAreaCrafting())
                || (loja.getTipoEstabelecimento() == Lojas.TipoEstabelecimento.ALQUIMIA
                    && molde.getAreaCrafting() == Lojas.TipoEstabelecimento.BOTANICA)
                || (loja.getTipoEstabelecimento() == Lojas.TipoEstabelecimento.BOTANICA
                    && molde.getAreaCrafting() == Lojas.TipoEstabelecimento.ALQUIMIA);

        return areaCompativel && (loja.getNivelLoja() >= molde.getNivelReceita());
    }

    /**
     * Valida se os materiais entregues atendem a todos os requisitos obrigatórios do molde.
     */
    public static boolean validarInsumos(MoldeReceita molde, List<Item> insumos) {
        if (molde == null || insumos == null) return false;

        if (!molde.getFiltrosInsumo().isEmpty()) {
            for (FiltroInsumoReceita filtro : molde.getFiltrosInsumo()) {
                if (filtro.isSlotCatalisadorOpcional()) continue;

                int qtdPresente = 0;
                for (Item item : insumos) {
                    if (filtro.aceitaMaterial(item)) {
                        qtdPresente += item.getQuantidade();
                    }
                }
                if (qtdPresente < filtro.getQuantidadeExigida()) {
                    return false;
                }
            }
            return true;
        }

        for (MoldeReceita.RequisitoInsumo req : molde.getRequisitos()) {
            if (req.isSlotCatalisadorOpcional()) continue;

            int qtdPresente = 0;
            for (Item item : insumos) {
                if (item != null && item.getFichaMaterial() != null) {
                    FichaMaterial mat = item.getFichaMaterial();
                    if (mat.getSubtipo() == req.getSubtipoExigido() && mat.getQualidade() >= req.getQualidadeMinima()) {
                        qtdPresente += item.getQuantidade();
                    }
                }
            }
            if (qtdPresente < req.getQuantidade()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Calcula o grau canônico de QualidadeCraft (1 a 7) com base na fórmula matemática v5:
     * 1. Q_efetiva = min(nivelLoja + 1, qualidadeMaterial)
     * 2. P_base = (Media(Q_efetiva) * 0.60) + (nivelLoja * 0.40)
     * 3. B_raridade in [-1, +2] (Penalidade improviso, Bônus Excedente Técnico, Bônus Ancestral)
     * 4. Grau Final = clamp(1, 7, round(P_base) + B_raridade)
     */
    public static QualidadeCraft calcularQualidade(Lojas loja, MoldeReceita molde, List<Item> insumos) {
        int nivelLoja = (loja != null) ? loja.getNivelLoja() : 1;
        int nivelReceita = (molde != null) ? molde.getNivelReceita() : 1;

        List<Integer> qualidadesEfetivas = new ArrayList<>();
        boolean apenasQualidade1 = true;
        boolean temEspolioEpicoOuLendario = false;
        boolean temAncestralMestre = false;

        for (Item item : insumos) {
            if (item == null || item.getFichaMaterial() == null) continue;
            FichaMaterial mat = item.getFichaMaterial();
            int qEfetiva = Math.min(nivelLoja + 1, mat.getQualidade());
            qualidadesEfetivas.add(qEfetiva);

            if (mat.getQualidade() > 1) {
                apenasQualidade1 = false;
            }

            boolean ehMaterialRaroOuEspolio = mat.isEspecificoDeOrigem()
                    || mat.getSubtipo() == FichaMaterial.SubtipoMaterial.ESPOLIO_MONSTRO
                    || mat.getSubtipo() == FichaMaterial.SubtipoMaterial.GEMA_CRISTAL;

            if (ehMaterialRaroOuEspolio) {
                if (item.getRaridade() == Item.RaridadeItem.EPICO || item.getRaridade() == Item.RaridadeItem.LENDARIO) {
                    temEspolioEpicoOuLendario = true;
                }
                if (nivelLoja == 5 && mat.getQualidade() == 5
                        && (item.getRaridade() == Item.RaridadeItem.LENDARIO || item.getRaridade() == Item.RaridadeItem.MITICO)) {
                    temAncestralMestre = true;
                }
            }
        }

        double mediaQEfetiva = qualidadesEfetivas.isEmpty() ? 1.0 :
                qualidadesEfetivas.stream().mapToInt(Integer::intValue).average().orElse(1.0);

        double pontuacaoBase = (mediaQEfetiva * 0.60) + (nivelLoja * 0.40);
        int bonusRaridade = 0;

        if (nivelLoja == 1 && apenasQualidade1) {
            bonusRaridade = -1; // Penalidade de improviso
        } else if (temAncestralMestre) {
            bonusRaridade = 2; // Bônus Ancestral exclusivo de Mestres (Nv. 5 + Lendário/Mítico Q5)
        } else if ((nivelLoja - nivelReceita >= 2 && mediaQEfetiva >= 4.0) || temEspolioEpicoOuLendario) {
            bonusRaridade = 1; // Bônus de Excedente Técnico ou Insumo Épico/Lendário
        }

        int grauCalculado = (int) Math.round(pontuacaoBase) + bonusRaridade;
        int grauFinal = Math.max(1, Math.min(7, grauCalculado));

        return QualidadeCraft.values()[grauFinal - 1];
    }

    /**
     * Fabrica o item pronto a partir do Molde e dos Insumos.
     */
    public static Item fabricarItem(Lojas loja, MoldeReceita molde, List<Item> insumosUsados, int idNovoItem) {
        if (!lojaAptaParaMolde(loja, molde) || !validarInsumos(molde, insumosUsados)) {
            return null;
        }

        QualidadeCraft qualidade = calcularQualidade(loja, molde, insumosUsados);

        // 1. Identificar Material Principal para Nomenclatura e Herança Geográfica
        Item materialPrincipal = null;
        if (!molde.getFiltrosInsumo().isEmpty()) {
            for (FiltroInsumoReceita filtro : molde.getFiltrosInsumo()) {
                if (filtro.isDefineNomePrincipal()) {
                    for (Item insumo : insumosUsados) {
                        if (filtro.aceitaMaterial(insumo)) {
                            materialPrincipal = insumo;
                            break;
                        }
                    }
                }
                if (materialPrincipal != null) break;
            }
        }
        if (materialPrincipal == null) {
            for (MoldeReceita.RequisitoInsumo req : molde.getRequisitos()) {
                if (req.isDefineSufixoPrincipal()) {
                    for (Item insumo : insumosUsados) {
                        if (insumo != null && insumo.getFichaMaterial() != null
                                && insumo.getFichaMaterial().getSubtipo() == req.getSubtipoExigido()) {
                            materialPrincipal = insumo;
                            break;
                        }
                    }
                }
                if (materialPrincipal != null) break;
            }
        }
        if (materialPrincipal == null && !insumosUsados.isEmpty()) {
            materialPrincipal = insumosUsados.get(0);
        }

        // 2. Determinar Nome e Geografia ("Regra do Camarão")
        String nomeFinal = molde.getNomeBaseItem();
        boolean herdouGeografiaMaterial = false;
        if (materialPrincipal != null && materialPrincipal.getFichaMaterial() != null) {
            FichaMaterial mat = materialPrincipal.getFichaMaterial();
            if (mat.isEspecificoDeOrigem() || !mat.getSufixoCrafting().isBlank()) {
                String sufixo = mat.getSufixoCrafting().isBlank() ? ("de " + materialPrincipal.getNome()) : mat.getSufixoCrafting();
                if (!sufixo.startsWith("de ") && !sufixo.startsWith("da ") && !sufixo.startsWith("do ")) {
                    sufixo = "de " + sufixo;
                }
                nomeFinal = molde.getNomeBaseItem() + " " + sufixo;
                herdouGeografiaMaterial = true;
            }
        }
        nomeFinal += " [" + qualidade.getNomeFormatado() + "]";

        // 3. Cálculo de Custo Contábil e Preço Base em Latão
        long custoInsumosLatao = 0L;
        for (Item ins : insumosUsados) {
            if (ins != null) {
                custoInsumosLatao += (ins.getPrecoEmLatao() * Math.max(1, ins.getQuantidade()));
            }
        }
        long custoTecnicoLatao = custoInsumosLatao + molde.getCustoMaoDeObraEmLatao();
        long precoBaseLatao = Math.round(custoTecnicoLatao * 1.25); // Margem de oficina 25%
        double precoBaseOuro = Carteira.lataoParaOuro(precoBaseLatao);

        // 4. Instanciação do Item v4.1
        int idItem = (idNovoItem > 0) ? idNovoItem : (int) (System.currentTimeMillis() % 1_000_000);
        Item novoItem = new Item(idItem, nomeFinal, "Manufaturado na oficina de " + loja.getNome(),
                precoBaseOuro, molde.getPesoBaseKg(), molde.getTipoItemGerado(),
                Item.RaridadeItem.COMUM, 1, molde.getTamanhoItem());

        // Assinatura Geográfica
        if (herdouGeografiaMaterial && materialPrincipal != null) {
            novoItem.setRegiaoOrigem(materialPrincipal.getRegiaoOrigem());
            novoItem.setGeologiaOrigem(materialPrincipal.getGeologiaOrigem());
            novoItem.setAquiferoOrigem(materialPrincipal.getAquiferoOrigem());
        } else if (loja.getCidadeSede() != null) {
            Cidade cidade = loja.getCidadeSede();
            novoItem.setRegiaoOrigem(cidade.getRegiao());
            novoItem.setGeologiaOrigem(cidade.getGeologia());
            novoItem.setAquiferoOrigem(cidade.getAquifero());
        }

        // 5. Construção e Injeção da Ficha Técnica Especializada
        int bonusFacesArma = 0;
        int bonusRdArmadura = 0;
        List<EfeitoItem> efeitosInjetaveis = new ArrayList<>();

        for (Item ins : insumosUsados) {
            if (ins != null && ins.getFichaMaterial() != null) {
                FichaMaterial fm = ins.getFichaMaterial();
                bonusFacesArma += fm.getBonusFacesDadoArma();
                bonusRdArmadura += fm.getBonusResistenciaArmadura();
                if (fm.getEfeitoAtribuido() != null) {
                    efeitosInjetaveis.add(fm.getEfeitoAtribuido());
                }
            }
        }

        switch (molde.getTipoItemGerado()) {
            case ARMA_BRANCA -> {
                FichaArmaBranca fa = new FichaArmaBranca(molde.getSubtipoArmaBranca(),
                        molde.getEmpunhaduraArmaBranca(), molde.getQtdDadosArmaBranca(),
                        molde.getFacesDadoBaseArmaBranca(), bonusFacesArma, 0, 1);
                novoItem.setFichaArmaBranca(fa);
            }
            case ARMA_LONGO_ALCANCE -> {
                FichaArmaLongoAlcance fal = new FichaArmaLongoAlcance(molde.getSubtipoArmaDistante(),
                        molde.getAmplificacaoBaseDistante(), molde.getAlcanceMetrosBaseDistante(), 1);
                novoItem.setFichaArmaLongoAlcance(fal);
            }
            case ARMADURA -> {
                FichaArmadura farm = new FichaArmadura(molde.getSlotsArmadura(),
                        molde.getRdBaseArmadura() + bonusRdArmadura, molde.getDurabilidadeBaseArmadura(), 1);
                for (Map.Entry<String, Integer> b : molde.getBonusStatusArmadura().entrySet()) {
                    farm.adicionarBonusStatus(b.getKey(), b.getValue());
                }
                novoItem.setFichaArmadura(farm);
            }
            case ESCUDO -> {
                FichaEscudo fesc = new FichaEscudo(molde.getEmpunhaduraEscudo(),
                        molde.getHpBaseEscudo() + (bonusRdArmadura * 10),
                        molde.getLimiteGolpeBaseEscudo() + bonusRdArmadura);
                novoItem.setFichaEscudo(fesc);
            }
            case ACESSORIO -> {
                FichaAcessorio facc = new FichaAcessorio(molde.getSlotAcessorio());
                for (Map.Entry<String, Integer> b : molde.getBonusAtributosAcessorio().entrySet()) {
                    facc.adicionarBonusAtributo(b.getKey(), b.getValue());
                }
                if (molde.getPassivaBaseAcessorio() != null) {
                    facc.adicionarPassiva(molde.getPassivaBaseAcessorio());
                }
                // Converte efeito de gema/espólio em passiva executável se aplicável
                for (EfeitoItem ef : efeitosInjetaveis) {
                    facc.adicionarPassiva(new PassivaItem("Poder de " + ef.getNome(),
                            "Ao equipar a joia", ef.toString(), 3,
                            PassivaItem.TipoCondicaoPassiva.SEMPRE_ATIVA_AO_EQUIPAR, 1.0, ef));
                }
                novoItem.setFichaAcessorio(facc);
            }
            case CONSUMIVEL -> {
                FichaConsumivel fcon = new FichaConsumivel(molde.getRestauracaoVidaBase(),
                        molde.getRestauracaoManaBase(), molde.getRestauracaoVigorBase());
                for (String c : molde.getCondicoesCuradasBase()) {
                    fcon.adicionarCondicaoRemovida(c);
                }
                for (EfeitoItem ef : efeitosInjetaveis) {
                    fcon.adicionarEfeitoAplicado(ef);
                }
                novoItem.setFichaConsumivel(fcon);
            }
            case MAGICO -> {
                FichaGrimorio fgrim = new FichaGrimorio(molde.getNivelArcanoGrimorio(), molde.getPaginasBaseGrimorio());
                novoItem.setFichaGrimorio(fgrim);
            }
            default -> {}
        }

        // 6. Aplica os 7 Graus de QualidadeCraft (escalonamento de dados, status, peso, preço, slots)
        novoItem.aplicarQualidadeCraft(qualidade);

        // 7. Injeta os efeitos nos slots abertos pela qualidade
        int slotsLivres = qualidade.getSlotsEfeitoMaximos();
        for (EfeitoItem ef : efeitosInjetaveis) {
            if (slotsLivres <= 0) break;
            boolean injetado = false;
            if (novoItem.getFichaArmaBranca() != null) {
                injetado = novoItem.getFichaArmaBranca().adicionarEfeito(ef);
            } else if (novoItem.getFichaArmaLongoAlcance() != null) {
                injetado = novoItem.getFichaArmaLongoAlcance().adicionarEfeito(ef);
            } else if (novoItem.getFichaArmadura() != null) {
                injetado = novoItem.getFichaArmadura().adicionarEfeito(ef);
            }
            if (injetado) {
                slotsLivres--;
            }
        }

        return novoItem;
    }
}
