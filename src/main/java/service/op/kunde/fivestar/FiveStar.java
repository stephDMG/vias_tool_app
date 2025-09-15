package service.op.kunde.fivestar;

import service.op.kunde.IKundeStrategy;
import service.op.kunde.Kunde;
import service.op.kunde.KundeRepository;
import util.FileUtil;

import java.util.List;
import java.util.Map;

/**
 * Eine konkrete Implementierung der {@link IKundeStrategy} für den Kunden FiveStart.
 * Diese Klasse definiert das spezifische Verhalten für FiveStart, insbesondere den
 * allgemeinen Speicherpfad.
 */
public class FiveStar implements IKundeStrategy {

    /**
     * {@inheritDoc}
     * <p>
     * Gibt den für FiveStart spezifischen Suchbegriff für die Datenbankabfrage zurück.
     */
    @Override
    public String getKundeNameForQuery() {
        return "Fivestar"; // Oder "%Five Start%", je nach Daten in der DB
    }

    /**
     * {@inheritDoc}
     * <p>
     * Gibt den allgemeinen Speicherpfad für alle "freien" Kunden zurück.
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