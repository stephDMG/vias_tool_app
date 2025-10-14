package model.op.kunde.gateway;

import model.op.kunde.IKundeStrategy;
import model.op.kunde.Kunde;
import service.op.repository.KundeRepository;
import util.FileUtil;

import java.util.List;
import java.util.Map;

/**
 * Eine konkrete Implementierung der {@link IKundeStrategy} für den Kunden Gateway.
 */
public class Gateway implements IKundeStrategy {

    /**
     * {@inheritDoc}
     */
    @Override
    public String getKundeNameForQuery() {
        return "Gateway";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSavePath() {
        return "X:/FREIE ZONE/Behrendt, Christian/OP Listen/";
    }

    @Override
    public Map<String, Map<String, List<Kunde>>> loadGroups(KundeRepository repository) throws Exception {
        return repository.getGroupedByLandAndPolicy(getKundeNameForQuery());
    }


    @Override
    public String buildFileName(String policeNr, String land, String ort, String extension) {
        String safePolicy = FileUtil.sanitizeFileName(policeNr);
        String safeLand = FileUtil.sanitizeFileName(land);
        String safeOrt = FileUtil.sanitizeFileName(ort);
        return String.format("%s_%s_%s.%s", safePolicy, safeLand, safeOrt, extension);
    }


}