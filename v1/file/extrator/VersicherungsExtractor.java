package file.extrator;

import model.VersicherungsData;
import file.reader.PdfReader;
import model.RowData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extraktor für Versicherungsbestätigungen.
 * Diese Klasse implementiert das {@link DataExtractor}-Interface, um strukturierte Daten
 * (wie Versicherungsnummer, Versicherungsnehmerdaten) aus PDF-Dokumenten von
 * Versicherungsbestätigungen mittels regulärer Ausdrücke zu extrahieren.
 *
 * @author Stephane Dongmo
 * @since 16/07/2025
 */
public class VersicherungsExtractor implements DataExtractor {

    private static final Logger logger = LoggerFactory.getLogger(VersicherungsExtractor.class);

    /**
     * Regex-Muster zum Extrahieren der Versicherungsnummer (VSN) aus dem Dateinamen.
     * Erwartet ein Format wie "12345_restlicherName.pdf".
     */
    private static final Pattern VSN_FILENAME = Pattern.compile("(\\d+)_.*\\.pdf", Pattern.CASE_INSENSITIVE);
    /**
     * Regex-Muster zum Extrahieren der Versicherungsnummer (VSN) aus dem Textinhalt.
     * Sucht nach "Versicherungsschein Nr.?: XXXX".
     */
    private static final Pattern VSN_CONTENT = Pattern.compile("Versicherungsschein Nr\\.?:\\s*(\\d+)");


    /**
     * Regex-Muster zum Extrahieren der vollständigen Versicherungsnehmerdaten
     * (Name, Straße, PLZ, Ort, Land) aus dem Textinhalt.
     */
    private static final Pattern VERSICHERUNGSNEHMER = Pattern.compile(
            "Versicherungsnehmer:\\s*([^,]+),\\s*([^,]+),\\s*(\\d{5})\\s+([^,]+),\\s*([^\\n\\r]+)"
    );



    private final PdfReader pdfReader;

    /**
     * Konstruktor für den VersicherungsExtractor.
     * Initialisiert den internen {@link PdfReader}.
     */
    public VersicherungsExtractor() {
        this.pdfReader = new PdfReader();
    }

    /**
     * Extrahiert Versicherungsdaten aus der angegebenen PDF-Datei.
     * Der Prozess umfasst das Lesen des PDF-Inhalts, die Extraktion der VSN (zuerst aus dem Dateinamen,
     * dann aus dem Inhalt), die Extraktion der Versicherungsnehmerdaten und eine grundlegende Validierung.
     *
     * @param filePath Der Pfad zur PDF-Datei, aus der Daten extrahiert werden sollen.
     * @return Eine Liste von {@link VersicherungsData}-Objekten, die die extrahierten Daten enthalten.
     * Die Liste ist leer, wenn keine Daten extrahiert werden konnten oder die Daten ungültig sind.
     */
    @Override
    public List<VersicherungsData> extractData(String filePath) {
        logger.info("🔍 Extraktion starten: {}", filePath);
        List<VersicherungsData> results = new ArrayList<>();

        try {
            // PDF-Inhalt lesen
            List<RowData> pdfContent = pdfReader.read(filePath);
            String fullText = extractFullText(pdfContent);

            // VersicherungsData-Objekt erstellen
            VersicherungsData data = new VersicherungsData();

            // VSN extrahieren
            extractVSN(fullText, filePath, data);

            // Versicherungsnehmer-Daten extrahieren
            extractVersicherungsnehmerData(fullText, data);

            // Weitere Felder extrahieren
            extractVersicherungsart(fullText, data);
            extractPoliceInfo(fullText, data);
            extractMaxJahresleistung(fullText, data);
            extractMaxSchadenleistung(fullText, data);
            extractGesellschaft(fullText, data);
            extractWaehrung(data);

            // Validierung und Hinzufügung
            if (isValidData(data)) {
                results.add(data);
                logger.info("✅ Extrahierung erledigt: VSN={}, Name={}",
                        data.getVersicherungsscheinNr(), data.getFirmaName());
            } else {
                logger.warn("⚠️ Unvollständige Daten extrahiert für: {}", filePath);
            }

        } catch (Exception e) {
            logger.error("❌ Fehler bei PDF-Extrahierung: {}", filePath, e);
        }

        return results;
    }

    /**
     * Überprüft, ob dieser Extraktor das angegebene Dokument verarbeiten kann.
     * Die Prüfung erfolgt anhand der Dateiexistenz, Lesbarkeit und des Dateinamens (Schlüsselwörter oder VSN-Muster).
     *
     * @param file Die zu prüfende Datei.
     * @return {@code true}, wenn der Extraktor die Datei verarbeiten kann, sonst {@code false}.
     */
    @Override
    public boolean canExtract(File file) {
        if (!file.exists() || !file.canRead()) {
            return false;
        }

        String name = file.getName().toLowerCase();
        return name.contains("versicherungsbestätigung") ||
                name.contains("versicherung") ||
                VSN_FILENAME.matcher(file.getName()).matches();
    }

    /**
     * Gibt den unterstützten Dokumententyp zurück.
     *
     * @return Eine Zeichenkette, die den unterstützten Dokumententyp beschreibt, z.B. "Versicherungsbestätigung".
     */
    @Override
    public String getSupportedDocumentType() {
        return "Versicherungsbestätigung";
    }

    // ==================== PRIVATE METHODEN ====================
    /**
     * Extrahiert die Versicherungsnummer (VSN) aus dem Text und dem Dateinamen.
     * Zuerst wird versucht, die VSN aus dem Dateinamen zu extrahieren, dann aus dem Textinhalt.
     *
     * @param text Der gesamte Textinhalt der PDF-Datei.
     * @param filePath Der Pfad zur PDF-Datei, um den Dateinamen zu extrahieren.
     * @param data Das {@link VersicherungsData}-Objekt, in das die VSN gespeichert wird.
     */
    private void extractVSN(String text, String filePath, VersicherungsData data) {
        String vsn = extractVSNFromFilename(filePath);
        if (vsn == null) {
            vsn = extractVSNFromContent(text);
        }
        data.setVersicherungsscheinNr(vsn);
        logger.debug("📄 VSN extrahiert: {}", vsn);
    }

    /**
     * Extrahiert die Versicherungsnehmerdaten aus dem Text.
     * Die Daten umfassen Firma/Name, Straße, PLZ, Ort und Land.
     * Die Straße und Hausnummer werden separat extrahiert.
     *
     * @param text Der gesamte Textinhalt der PDF-Datei.
     * @param data Das {@link VersicherungsData}-Objekt, in das die extrahierten Daten gespeichert werden.
     */
    private void extractVersicherungsnehmerData(String text, VersicherungsData data) {
        Matcher matcher = VERSICHERUNGSNEHMER.matcher(text);
        if (matcher.find()) {
            // Firma/Name
            data.setFirmaName(matcher.group(1).trim());

            // Strasse und StrasseNr SEPARAT extrahieren
            String fullAddress = matcher.group(2).trim();
            extractSeparateAddress(fullAddress, data);

            // PLZ
            data.setPlz(matcher.group(3).trim());

            // Ort
            data.setOrt(matcher.group(4).trim());

            // Land - KORRIGIERT für DB-Format
            String land = matcher.group(5).trim();
            if (land.equalsIgnoreCase("Deutschland")) {
                data.setLand("DEUTSCHLAND");  // DB-Format
            } else {
                data.setLand(land.toUpperCase()); // Generell Großbuchstaben
            }

            logger.debug("👤 Versicherungsnehmer extrahiert: {}", data.getFirmaName());
        }
    }

    /**
     * Extrahiert die Straße und Hausnummer aus einer Adresse.
     * Das Muster erwartet, dass die Straße und Hausnummer durch ein Leerzeichen getrennt sind.
     * Beispiel: "Wensebrocker Str. 2" wird in "Wensebrocker Str." und "2" aufgeteilt.
     *
     * @param address Die vollständige Adresse als String.
     * @param data Das {@link VersicherungsData}-Objekt, in das die extrahierten Daten gespeichert werden.
     */
    private void extractSeparateAddress(String address, VersicherungsData data) {
        Pattern addressPattern = Pattern.compile("(.+?)\\s+(\\d+.*)$");
        Matcher matcher = addressPattern.matcher(address);

        if (matcher.find()) {
            data.setStrasse(matcher.group(1).trim());  // "Wensebrocker Str."
            data.setStrasseNr(matcher.group(2).trim()); // "2"
        } else {
            data.setStrasse(address);
            data.setStrasseNr("");
        }
    }

    /**
     * Extrahiert die Versicherungsart aus dem Text.
     * Das Muster sucht nach "Verkehrshaftungs-Versicherung".
     *
     * @param text Der gesamte Textinhalt der PDF-Datei.
     * @param data Das {@link VersicherungsData}-Objekt, in das die Versicherungsart gespeichert wird.
     */
    private void extractVersicherungsart(String text, VersicherungsData data) {
        Pattern pattern = Pattern.compile("(VERKEHRSHAFTUNGS-VERSICHERUNG)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            data.setVersicherungsart("Verkehrshaftungsversicherung");
            logger.debug("🚗 Versicherungsart: Verkehrshaftungsversicherung");
        }
    }

    /**
     * Extrahiert die Police-Informationen aus dem Text.
     * Das Muster sucht nach "CS - FF 1234".
     *
     * @param text Der gesamte Textinhalt der PDF-Datei.
     * @param data Das {@link VersicherungsData}-Objekt, in das die Police-Informationen gespeichert werden.
     */
    private void extractPoliceInfo(String text, VersicherungsData data) {
        Pattern pattern = Pattern.compile("(CS\\s*-\\s*FF\\s*(\\d{4}))");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String year = matcher.group(2);  // "2018"

            String policeInfo = "CS-FF " + year;
            data.setPoliceInfo(policeInfo);
            logger.debug("📋 PoliceInfo: {}", policeInfo);
        }
    }

    /**
     * Extrahiert die maximale Schadenleistung aus dem Text.
     * Das Muster sucht nach "je Schadenereignis EUR 1234,56".
     *
     * @param text Der gesamte Textinhalt der PDF-Datei.
     * @param data Das {@link VersicherungsData}-Objekt, in das die maximale Schadenleistung gespeichert wird.
     */
    private void extractMaxSchadenleistung(String text, VersicherungsData data) {
        Pattern pattern = Pattern.compile("je Schadenereignis EUR ([\\d.,]+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String amount = matcher.group(1);
            // DB-Format: "2500000" (ohne EUR, Punkte, Kommas)
            String cleanAmount = amount.replace(".", "").replace(",00", "");
            data.setMaxSchadenleistung(cleanAmount);
            logger.debug("💰 Max Schadenleistung: {}", cleanAmount);
        }
    }

    private String cleanAmountForDatabase(String amount) {
        // "7.500.000,00" → "7500000"
        return amount.replace(".", "").replace(",00", "");
    }

    /**
     * Extrahiert die maximale Jahresleistung aus dem Text.
     * Das Muster sucht nach "je Versicherungsjahr EUR 1234,56".
     *
     * @param text Der gesamte Textinhalt der PDF-Datei.
     * @param data Das {@link VersicherungsData}-Objekt, in das die maximale Jahresleistung gespeichert wird.
     */
    private void extractMaxJahresleistung(String text, VersicherungsData data) {
        Pattern pattern = Pattern.compile("je Versicherungsjahr EUR ([\\d.,]+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String amount = matcher.group(1);
            // DB-Format: "7500000" (ohne EUR, Punkte, Kommas)
            String cleanAmount = amount.replace(".", "").replace(",00", "");
            data.setMaxJahresleistung(cleanAmount);
            logger.debug("💰 Max Jahresleistung: {}", cleanAmount);
        }
    }

    /**
     * Extrahiert den Namen der Versicherungsgesellschaft aus dem Text.
     * Das Muster sucht nach "Gesellschaft Insurance AG".
     *
     * @param text Der gesamte Textinhalt der PDF-Datei.
     * @param data Das {@link VersicherungsData}-Objekt, in das der Name der Gesellschaft gespeichert wird.
     */
    private void extractGesellschaft(String text, VersicherungsData data) {
        if (text.contains("Zurich Insurance Europe AG")) {
            // DB-Format: "Zurich Insurance Europe AG Frankfurt am Main"
            data.setGesellschaft("Zurich Insurance Europe AG Frankfurt am Main");
            logger.debug("🏢 Gesellschaft: Zurich Insurance Europe AG Frankfurt am Main");
        }
    }

    /**
     * Setzt die Währung auf "EUR".
     * Diese Methode ist eine Platzhalter-Implementierung, da die Währung in diesem Kontext immer EUR ist.
     *
     * @param data Das {@link VersicherungsData}-Objekt, in das die Währung gespeichert wird.
     */
    private void extractWaehrung(VersicherungsData data) {
        data.setWaehrung("EUR");
    }

    /**
     * Extrahiert den gesamten Textinhalt aus der Liste von RowData-Objekten.
     * Diese Methode kombiniert die Inhalte aller Zeilen in einen einzigen String.
     *
     * @param pdfContent Die Liste von RowData, die den Inhalt der PDF repräsentiert.
     * @return Ein String, der den kombinierten Textinhalt aller Zeilen enthält.
     */
    private String extractFullText(List<RowData> pdfContent) {
        StringBuilder fullText = new StringBuilder();
        for (RowData row : pdfContent) {
            String content = row.getValues().get("Content");
            if (content != null) {
                fullText.append(content).append("\n");
            }
        }
        return fullText.toString();
    }

    /**
     * Extrahiert die Versicherungsnummer (VSN) aus dem Dateinamen.
     * Das Muster sucht nach einer Zahl am Anfang des Dateinamens, gefolgt von einem Unterstrich und beliebigem Text.
     *
     * @param filePath Der Pfad zur Datei, aus der die VSN extrahiert werden soll.
     * @return Die extrahierte VSN als String oder {@code null}, wenn keine VSN gefunden wurde.
     */
    private String extractVSNFromFilename(String filePath) {
        String fileName = new File(filePath).getName();
        Matcher matcher = VSN_FILENAME.matcher(fileName);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Extrahiert die Versicherungsnummer (VSN) aus dem Textinhalt.
     * Das Muster sucht nach "Versicherungsschein Nr.?: XXXX".
     *
     * @param text Der gesamte Textinhalt der PDF-Datei.
     * @return Die extrahierte VSN als String oder {@code null}, wenn keine VSN gefunden wurde.
     */
    private String extractVSNFromContent(String text) {
        Matcher matcher = VSN_CONTENT.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Validiert die extrahierten Daten.
     * Überprüft, ob die Versicherungsnummer und der Firmenname nicht leer sind.
     *
     * @param data Das {@link VersicherungsData}-Objekt, das validiert werden soll.
     * @return {@code true}, wenn die Daten gültig sind, sonst {@code false}.
     */
    private boolean isValidData(VersicherungsData data) {
        return data.getVersicherungsscheinNr() != null &&
                !data.getVersicherungsscheinNr().isEmpty() &&
                data.getFirmaName() != null &&
                !data.getFirmaName().isEmpty();
    }
}