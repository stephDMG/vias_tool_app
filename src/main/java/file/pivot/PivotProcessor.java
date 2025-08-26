package file.pivot;

import config.ApplicationConfig;


import model.PivotConfig;
import model.RowData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * High-Level Prozessor für Pivot-Operationen.
 * Diese Klasse ist ein Wrapper um {@link PivotTransformer} und bietet Methoden zur
 * Transformation von Daten, Generierung von Headern, Validierung von Pivot-Konfigurationen
 * und zur Bereitstellung von Informationen über die Pivot-Transformation.
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public class PivotProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PivotProcessor.class);

    /**
     * Transformiert Daten basierend auf einer gegebenen {@link PivotConfig}.
     * Diese Methode überprüft die Gültigkeit der Eingabedaten und der Konfiguration,
     * führt eine Validierung der Konfiguration gegen die Daten durch und delegiert
     * dann die eigentliche Transformation an den {@link PivotTransformer}.
     *
     * @param data   Die Originaldaten als Liste von {@link RowData}-Objekten.
     * @param config Die {@link PivotConfig}, die definiert, wie die Daten pivotisiert werden sollen.
     * @return Eine Liste von {@link RowData}-Objekten, die die transformierten (pivotisierten) Daten enthält.
     * @throws IllegalArgumentException Wenn die Eingabedaten oder die Konfiguration ungültig sind oder
     * die Pivot-Konfiguration nicht mit den Daten kompatibel ist.
     */
    public List<RowData> transform(List<RowData> data, PivotConfig config) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Daten dürfen nicht leer sein");
        }

        if (config == null) {
            throw new IllegalArgumentException("PivotConfig darf nicht null sein");
        }

        // Validierung der Konfiguration gegen die Daten
        validatePivotConfig(data, config);

        // Transformation durchführen
        return PivotTransformer.transformToHorizontal(
                data,
                config.getGroupByColumn(),
                config.getPivotColumn(),
                config.getKeepColumns()
        );
    }

    /**
     * Generiert eine Liste von Headern für die transformierten Daten.
     * Diese Methode überprüft die Gültigkeit der Eingabedaten und der Konfiguration
     * und delegiert dann die Header-Generierung an den {@link PivotTransformer}.
     *
     * @param data   Die Originaldaten als Liste von {@link RowData}-Objekten.
     * @param config Die {@link PivotConfig}, basierend auf der die Header generiert werden sollen.
     * @return Eine Liste von Strings, die die Header für die pivotisierten Daten darstellen.
     * @throws IllegalArgumentException Wenn die Eingabedaten oder die Konfiguration ungültig sind.
     */
    public List<String> generateHeaders(List<RowData> data, PivotConfig config) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Daten dürfen nicht leer sein");
        }

        if (config == null) {
            throw new IllegalArgumentException("PivotConfig darf nicht null sein");
        }

        return PivotTransformer.generateHorizontalHeaders(
                data,
                config.getGroupByColumn(),
                config.getPivotColumn(),
                config.getKeepColumns()
        );
    }

    /**
     * Prüft, ob eine Pivot-Transformation mit den gegebenen Daten und der Konfiguration möglich ist.
     * Diese Methode versucht, die Konfiguration zu validieren; wenn keine Ausnahmen auftreten,
     * wird {@code true} zurückgegeben, ansonsten {@code false}.
     *
     * @param data   Die zu prüfenden Daten als Liste von {@link RowData}-Objekten.
     * @param config Die zu prüfende {@link PivotConfig}.
     * @return {@code true}, wenn die Transformation möglich ist, sonst {@code false}.
     */
    public boolean canPivot(List<RowData> data, PivotConfig config) {
        try {
            validatePivotConfig(data, config);
            return true;
        } catch (IllegalArgumentException e) {
            logger.warn("Kann nicht pivotieren: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gibt detaillierte Informationen zur geplanten Pivot-Transformation zurück,
     * einschließlich Statistiken und der verwendeten Konfiguration.
     *
     * @param data   Die Originaldaten.
     * @param config Die {@link PivotConfig}.
     * @return Ein formatierter String mit Pivot-Informationen, oder eine Fehlermeldung,
     * wenn die Transformation nicht möglich ist.
     */
    public String getPivotInfo(List<RowData> data, PivotConfig config) {
        if (!canPivot(data, config)) {
            return "Pivot-Transformation nicht möglich.";
        }

        String statistics = PivotTransformer.getPivotStatistics(data, config.getGroupByColumn());

        return String.format(
                "%s\n" +
                        "Konfiguration:\n" +
                        "- Gruppierung: %s\n" +
                        "- Pivot-Spalte: %s\n" +
                        "- Beibehaltene Spalten: %s",
                statistics,
                config.getGroupByColumn(),
                config.getPivotColumn(),
                String.join(", ", config.getKeepColumns())
        );
    }

    /**
     * Erstellt eine Standard-{@link PivotConfig} speziell für Dokumentdaten.
     *
     * @return Eine vorkonfigurierte {@link PivotConfig} für Dokumente.
     */
    public static PivotConfig createDocumentPivotConfig() {
        return PivotConfig.forDocuments();
    }

    /**
     * Erstellt eine angepasste {@link PivotConfig} mit den angegebenen Spalten.
     *
     * @param groupBy     Der Name der Spalte, nach der gruppiert werden soll.
     * @param pivot       Der Name der Spalte, die pivotisiert werden soll (als neue Spaltenüberschriften).
     * @param keepColumns Eine Liste von Spaltennamen, die in den pivotisierten Daten beibehalten werden sollen.
     * @return Eine neue {@link PivotConfig}-Instanz mit den benutzerdefinierten Einstellungen.
     */
    public static PivotConfig createCustomPivotConfig(String groupBy, String pivot, List<String> keepColumns) {
        return new PivotConfig(groupBy, pivot, keepColumns);
    }

    /**
     * Validiert die gegebene {@link PivotConfig} gegen die bereitgestellten Daten.
     * Diese Methode überprüft, ob die für die Pivot-Operation erforderlichen Spalten
     * in den Daten vorhanden sind und ob die Anzahl der eindeutigen Gruppen nicht zu groß ist,
     * um mögliche Performance-Probleme zu vermeiden.
     *
     * @param data   Die Daten, gegen die die Konfiguration validiert werden soll.
     * @param config Die zu validierende {@link PivotConfig}.
     * @throws IllegalArgumentException Wenn die Datenliste leer ist, die GroupBy- oder Pivot-Spalten
     * nicht vorhanden oder leer sind, oder wenn benötigte "Keep"-Spalten fehlen.
     */
    private void validatePivotConfig(List<RowData> data, PivotConfig config) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Daten-Liste ist leer");
        }

        String groupBy = config.getGroupByColumn();
        String pivot = config.getPivotColumn();
        List<String> keepColumns = config.getKeepColumns();

        if (groupBy == null || groupBy.trim().isEmpty()) {
            throw new IllegalArgumentException("GroupBy-Spalte darf nicht leer sein");
        }

        if (pivot == null || pivot.trim().isEmpty()) {
            throw new IllegalArgumentException("Pivot-Spalte darf nicht leer sein");
        }

        // Prüfen, ob die Spalten in den Daten existieren
        RowData firstRow = data.get(0);
        if (!firstRow.getValues().containsKey(groupBy)) {
            throw new IllegalArgumentException("GroupBy-Spalte '" + groupBy + "' nicht in Daten gefunden");
        }

        if (!firstRow.getValues().containsKey(pivot)) {
            throw new IllegalArgumentException("Pivot-Spalte '" + pivot + "' nicht in Daten gefunden");
        }

        // Prüfen, ob alle "Keep"-Spalten existieren
        for (String keepColumn : keepColumns) {
            if (!firstRow.getValues().containsKey(keepColumn)) {
                throw new IllegalArgumentException("Keep-Spalte '" + keepColumn + "' nicht in Daten gefunden");
            }
        }

        // Prüfen auf zu viele Pivot-Einträge (Performance-Schutz)
        long distinctGroups = data.stream()
                .map(row -> row.getValues().get(groupBy))
                .distinct()
                .count();

        if (distinctGroups > ApplicationConfig.MAX_ROWS_PER_FILE / 10) {
            logger.warn("⚠️ Warnung: Sehr viele Gruppen ({}) - Performance könnte leiden", distinctGroups);
        }
    }
}