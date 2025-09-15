package service.op.kunde.hartrodt;

import service.op.kunde.IKundeStrategy;
import service.op.kunde.Kunde;
import service.op.kunde.KundeRepository;
import util.FileUtil;

import java.util.List;
import java.util.Map;

/**
 * Eine konkrete Implementierung der {@link IKundeStrategy} für den Kunden Hartrodt.
 * Diese Klasse enthält die gesamte spezifische Logik für Hartrodt,
 * einschließlich des Datenbank-Abfragenamens und des eindeutigen Speicherpfads.
 */
public class Hartrodt implements IKundeStrategy {

    /**
     * {@inheritDoc}
     * <p>
     * Gibt den für Hartrodt spezifischen Suchbegriff für die Datenbankabfrage zurück.
     */
    @Override
    public String getKundeNameForQuery() {
        return "Hartrodt";
    }

    @Override
    public Map<String, Map<String, List<Kunde>>> loadGroups(KundeRepository repository) throws Exception {
        return repository.getGroupedByLandAndPolicy(getKundeNameForQuery());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Gibt den dedizierten Speicherpfad für Dokumente von Sabine Blume zurück,
     * die Hartrodt betreffen.
     */
    @Override
    public String getSavePath() {
        return "X:/FREIE ZONE/Blume, Sabine/Hartrodt/Master - FOS/w076 1412 ws - Transport/";
    }


    @Override
    public String buildFileName(String policeNr, String land, String ort, String extension) {
        String safePolicy = FileUtil.sanitizeFileName(policeNr);
        String safeLand = FileUtil.sanitizeFileName(land);
        String safeOrt = FileUtil.sanitizeFileName(ort);
        return String.format("%s_%s_%s.%s", safePolicy, safeLand, safeOrt, extension);
    }

}