package file.reader;
import model.RowData;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
/**
 * Liest Textdateien Zeile für Zeile für das VIAS Export Tool.
 * Diese Klasse ist darauf spezialisiert, den Inhalt einer Textdatei
 * zu lesen und jede Zeile als ein {@link RowData}-Objekt zu repräsentieren.
 *
 * @author Stephane Dongmo
 * @since 15.07.2025
 */
public class TxtReader {

    /**
     * Liest den Inhalt einer Textdatei und gibt ihn als Liste von {@link RowData}-Objekten zurück.
     * Jedes {@link RowData}-Objekt enthält die Zeilennummer und den vollständigen Inhalt der Zeile.
     *
     * @param filePath Der Pfad zur Textdatei, die gelesen werden soll.
     * @return Eine Liste von {@link RowData}-Objekten, die jede Zeile der Textdatei repräsentieren.
     * @throws RuntimeException Wenn ein {@link IOException} während des Lesevorgangs auftritt.
     */
    public List<RowData> read(String filePath) {
        List<RowData> data = new ArrayList<>();

        // Verwendet try-with-resources, um sicherzustellen, dass der BufferedReader korrekt geschlossen wird.
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNumber = 1; // Startet die Zeilennummerierung bei 1

            // Liest jede Zeile der Datei, bis das Ende der Datei erreicht ist (readLine() gibt null zurück).
            while ((line = reader.readLine()) != null) {
                RowData row = new RowData();
                // Speichert die aktuelle Zeilennummer im RowData-Objekt.
                row.put("Line", String.valueOf(lineNumber));
                // Speichert den vollständigen Inhalt der Zeile im RowData-Objekt.
                row.put("Content", line);
                data.add(row); // Fügt das RowData-Objekt zur Ergebnisliste hinzu.
                lineNumber++; // Erhöht den Zeilenzähler.
            }

        } catch (IOException e) {
            // Fängt IOException ab und wirft eine RuntimeException, um den Fehler zu propagieren.
            throw new RuntimeException("Text Lesefehler: " + filePath, e);
        }

        return data; // Gibt die Liste der gelesenen Daten zurück.
    }
}