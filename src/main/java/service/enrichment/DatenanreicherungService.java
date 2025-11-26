package service.enrichment;

import model.RowData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.interfaces.DatabaseService;
import service.interfaces.FileService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service für die Anreicherung von Daten mit Informationen aus der VIAS-Datenbank.
 *
 * @author Stephane Dongmo
 * @version 1.0
 * @since 22/07/2025
 */
public class DatenanreicherungService {
    private static final Logger logger = LoggerFactory.getLogger(DatenanreicherungService.class);


    private final FileService fileService;
    private final DatabaseService databaseService;

    /**
     * Erstellt den Service zur Datenanreicherung.
     *
     * @param fileService     Dienst zum Lesen/Schreiben von Dateien
     * @param databaseService Dienst für Datenbankabfragen (VIAS)
     */
    public DatenanreicherungService(FileService fileService, DatabaseService databaseService) {
        this.fileService = fileService;
        this.databaseService = databaseService;
    }


    /**
     * Liest eine Datei, reichert die Daten an und gibt das Ergebnis zurück.
     * Diese Methode ist für die Vorschau in der GUI gedacht.
     *
     * @param inputFilePath Der Pfad zur Eingabedatei (XLS oder XLSX).
     * @return Eine Liste der angereicherten RowData-Objekte.
     * @throws Exception bei Fehlern im Prozess.
     */
    public List<RowData> enrichDataFromFile(String inputFilePath) throws Exception {
        logger.info("🚀 Datenanreicherung gestartet für: {}", inputFilePath);

        // 1. Quelldatei lesen
        List<RowData> sourceData = fileService.readFile(inputFilePath);
        if (sourceData.isEmpty()) {
            throw new Exception("Die Quelldatei ist leer.");
        }
        logger.info("📖 {} Zeilen aus der Quelldatei geladen.", sourceData.size());

        // 2. OPTIMIERT: Alle benötigten IDs auf einmal sammeln
        List<String> snrMaklerList = sourceData.stream()
                .map(row -> row.getValues().get("LU_SNR"))
                .filter(snr -> snr != null && !snr.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());

        logger.info("🔍 Rufe Daten für {} eindeutige Schadennummern aus VIAS ab...", snrMaklerList.size());

        // 3. OPTIMIERT: Eine einzige Datenbankabfrage für alle IDs
        Map<String, RowData> dbDataMap = databaseService.getSchadenDetailsByMaklerSnrBulk(snrMaklerList);

        logger.info("📊 {} passende Einträge in der Datenbank gefunden.", dbDataMap.size());

        // 4. OPTIMIERT: Daten in-memory zusammenführen
        for (RowData sourceRow : sourceData) {
            String snrMakler = sourceRow.getValues().get("LU_SNR");
            String vsnRaw = sourceRow.getValues().get("LU_VSN");
            //VSN bereinigen
            String vsnClean = formatVsn(vsnRaw);
            //ersetzen
            sourceRow.put("LU_VSN", vsnClean);

            if (snrMakler != null) {
                RowData dbRow = dbDataMap.get(snrMakler.trim());
                if (dbRow != null) {
                    dbRow.getValues().remove("LU_SNR_MAKLER");

                    sourceRow.getValues().putAll(dbRow.getValues());
                } else {
                    logger.warn("❓ Für Schadennummer Makler '{}' wurden keine Daten in VIAS gefunden.", snrMakler.trim());
                }
            }
        }

        logger.info("✅ Anreicherungsprozess abgeschlossen.");
        return sourceData; // Gibt die modifizierte Originalliste zurück
    }

    /**
     * Bereinigt die VSN, indem die Suffixe "KO" oder "KS" am Ende entfernt werden.
     * Die Groß- und Kleinschreibung wird dabei ignoriert.
     *
     * @param rawVsn Die rohe VSN aus der Datei (z.B. "W4031170KS").
     * @return Die bereinigte VSN (z.B. "W4031170").
     */
    private String formatVsn(String rawVsn) {
        if (rawVsn == null || rawVsn.trim().isEmpty()) {
            return "";
        }

        String cleanedVsn = rawVsn.trim();
        String upperCaseVsn = cleanedVsn.toUpperCase();

        // Prüft, ob die VSN mit "KO" oder "KS" endet
        if (upperCaseVsn.endsWith("KO") || upperCaseVsn.endsWith("KS")) {
            // Entfernt die letzten beiden Zeichen
            return cleanedVsn.substring(0, cleanedVsn.length() - 2);
        }

        // Wenn kein Suffix gefunden wird, wird die ursprüngliche, bereinigte VSN zurückgegeben
        return cleanedVsn;
    }


}
