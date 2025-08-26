package console;

import gui.VIASGuiApplication;
import model.enums.ExportFormat;
import service.ExtractionService;
import service.ServiceFactory;
import service.interfaces.AiService;
import service.interfaces.FileService;
import util.FileSearchTool;
import util.FileUtil;

import java.util.List;

/**
 * Die Main-Klasse dient als Einstiegspunkt für die Anwendung.
 * Sie initialisiert die benötigten Services und führt Test-Exports durch.
 *
 * @author Stephane Dongmo
 * @version 1.0
 * @since 09/07/2025
 */
public class Main {
    /**
     * Die Hauptmethode der Anwendung.
     * Initialisiert die Services und startet den automatischen PDF-Extraktionstest.
     *
     * @param args Befehlszeilenargumente (werden in dieser Anwendung nicht verwendet).
     */
    public static void main(String[] args) {
        VIASGuiApplication.main(args);
        System.out.println("\n🎉 Alle Tests abgeschlossen!");
    }

}