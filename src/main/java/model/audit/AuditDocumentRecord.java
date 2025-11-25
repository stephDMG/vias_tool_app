package model.audit; // Bitte anpassen

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstrakte Basisklasse für Dokumenten-Audit-Einträge (Vertrag oder Schaden).
 * Kapselt die allgemeinen Dokumenteninformationen, die per SQL-Abfrage aus VIAS
 * (DC2, DC3, SAB) extrahiert werden.
 */
public abstract class AuditDocumentRecord {

    private static final Logger logger = LoggerFactory.getLogger(AuditDocumentRecord.class);

    // PATTERN ROBUST: Sucht nach dem VIAS-Prefix "/FN:" und erfasst den gesamten
    // Windows-Pfad (Gruppe 1) bis zum nächsten Leerzeichen oder dem Ende des Strings.
    // Wir verwenden die Korrektur, um das Muster an beliebiger Stelle zu finden.
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
            // Sucht nach der Literalfolge '/FN:', gefolgt von optionalen Leerzeichen ('\\s*')
            // Gruppe 1: ([a-zA-Z]:\\[^\\s]*\.(...)) -> Erfasst den Windows-Pfad V:\... bis zur Extension
            "/FN:\\s*([a-zA-Z]:\\\\[^\\s]*\\.(pdf|msg|xlsx|docx|doc|xls|jpg|png|tif|jpeg|bmp|tiff|bcxml|eml|dcp|ll|mp4|odt|pps|pptx|txt|rtf|zip|csv))",
            Pattern.CASE_INSENSITIVE
    );

    // Konstante für den Basis-Dokumentpfad (für die Fallback-Rekonstruktion)
    private static final String DOCPLACE_BASE_PATH = "V:\\VIAS_CS\\DOCPLACE\\";


    // Sachbearbeiter (SAB)
    private final String nachname;
    private final String vorname;

    // Dokumenten-Metadaten (DC2/DC3)
    private final String beschreibung;
    private final String parameter;
    private final String extension;
    private final String betreff;

    // Zeitstempel
    private final LocalDateTime bezugsdatum;
    private final LocalDateTime uhrzeit;

    private final String dateiName;

    /**
     * Konstruktor für AuditDocumentRecord.
     */
    public AuditDocumentRecord(String nachname, String vorname, String beschreibung, String parameter,
                               String extension, String betreff, LocalDateTime bezugsdatum, LocalDateTime uhrzeit, String dateiName) {
        this.nachname = nachname;
        this.vorname = vorname;
        this.beschreibung = beschreibung;
        this.parameter = parameter;
        this.extension = extension;
        this.betreff = betreff;
        this.bezugsdatum = bezugsdatum;
        this.uhrzeit = uhrzeit;
        this.dateiName = dateiName;
    }


    /**
     * Extrahiert den Pfad zur physischen Datei (V:\...).
     * <p>
     * 1. Primär: Nutzt Regex, um den Pfad aus dem komplexen 'parameter'-String zu extrahieren.
     * 2. Fallback: Wenn 'parameter' leer/ungültig ist, wird der Pfad aus 'dateiName' (VIAS-Konvention) rekonstruiert.
     * </p>
     *
     * @return Der vollständige physische Dateipfad, oder ein leerer String, wenn kein Pfad gefunden wird.
     */
    public String getFilePath() {
        // Bereinigt den Parameter-String von Leerzeichen (Padding)
        String cleanedParameter = (parameter != null) ? parameter.trim() : "";

        // --- 1. PRIMÄRE LOGIK: REGEX-EXTRAKTION AUS PARAMETER ---

        // Prüfe, ob der Parameter-String vorhanden ist und den Pfad enthalten könnte
        if (!cleanedParameter.isEmpty()) {
            Matcher matcher = FILE_PATH_PATTERN.matcher(cleanedParameter);

            if (matcher.find()) {
                // matcher.group(1) ist die Gruppe, die den Windows-Pfad (V:\...) enthält.
                // Dies löst das Problem des doppelten Pfad-Präfixes (V:\VIAS_CS\DOCPLACE\V:\...)
                return matcher.group(1);
            }
        }


        // --- 2. FALLBACK-LOGIK: REKONSTRUKTION AUS DATEINAME ---

        String cleanedDateiName = (dateiName != null) ? dateiName.trim() : "";

        if (!cleanedDateiName.isEmpty()) {
            // Bestimme die Dateierweiterung (aus DC2.Extension).
            String extensionPart = (extension != null && !extension.trim().isEmpty())
                    ? extension.trim()
                    : "";

            // Entferne die Extension, falls sie bereits im DateiNamen enthalten ist
            String baseName = cleanedDateiName;
            int lastDot = baseName.lastIndexOf('.');
            if (lastDot > 0) {
                baseName = baseName.substring(0, lastDot);
            }

            // NOUVEAU: EXTRAKTION DES VIAS-SUBFOLDERS (Zeichen 5 und 6)
            String subFolderName = "";
            if (baseName.length() >= 6) {
                try {
                    // Extrahiert z.B. '5F' aus '03D25F00'
                    subFolderName = baseName.substring(4, 6);
                } catch (StringIndexOutOfBoundsException e) {
                    logger.warn("Fehler bei der Subfolder-Extraktion aus DateiName: {}. Nutze kompletten Namen.", baseName);
                    subFolderName = baseName;
                }
            } else {
                subFolderName = baseName;
            }

            // Konstruiere den vollständigen Pfad: V:\VIAS_CS\DOCPLACE\5F\03D25F00.PDF
            return DOCPLACE_BASE_PATH + subFolderName + "\\" + baseName + "." + extensionPart;
        }

        // 3. Weder Parameter noch DateiName liefern einen gültigen Pfad.
        return "";
    }

    // --- Getters ---

    public String getNachname() {
        return nachname;
    }

    public String getVorname() {
        return vorname;
    }

    public String getBeschreibung() {
        return beschreibung;
    }

    public String getParameter() {
        return parameter;
    }

    public String getExtension() {
        return extension;
    }

    public String getBetreff() {
        return betreff;
    }

    public LocalDateTime getBezugsdatum() {
        return bezugsdatum;
    }

    public LocalDateTime getUhrzeit() {
        return uhrzeit;
    }

    public String getDateiName() {
        return dateiName;
    }
}