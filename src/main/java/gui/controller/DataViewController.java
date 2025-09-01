package gui.controller;

import gui.controller.manager.TableViewBuilder;
import gui.controller.utils.EnhancedTableManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import model.RowData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ServiceFactory;
import service.interfaces.FileService;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import static gui.controller.utils.Dialog.showErrorDialog;
import static gui.controller.utils.Dialog.showSuccessDialog;

/**
 * Controller für die Datenansicht.
 *
 * <p><strong>Funktionalität:</strong></p>
 * <ul>
 * <li>Laden von CSV/XLSX-Dateien über einen FileChooser</li>
 * <li>Verwaltung der geladenen Daten über den {@link EnhancedTableManager}</li>
 * <li>Paginierung und Echtzeit-Volltextsuche in der Tabelle</li>
 * <li>Spaltenmanagement, Umbenennung und Löschung</li>
 * <li>Export der gefilterten Daten (optional, kann über FXML aktiviert werden)</li>
 * </ul>
 *
 * <p><strong>Technische Verbesserungen:</strong></p>
 * <ul>
 * <li>Verwendet {@link TableViewBuilder} und {@link EnhancedTableManager} für eine standardisierte Tabellenlogik.</li>
 * <li>Der Code ist schlanker, da die Logik für Suche, Pagination und Spaltenverwaltung ausgelagert ist.</li>
 * <li>Asynchroner Dateiladevorgang über einen {@link Task}, um die UI nicht zu blockieren.</li>
 * </ul>
 *
 * @author Stephane Dongmo
 * @version 2.0
 * @since 1.0
 */
public class DataViewController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(DataViewController.class);

    // === FXML-Komponenten ===
    @FXML private Button loadFileButton;
    @FXML private VBox resultsContainer; // Container für die Tabelle aus dem Builder

    // === Services und Datenmodell ===
    private FileService fileService;
    private List<RowData> fullDataList = new ArrayList<>();

    // === Tabellenverwaltung ===
    private EnhancedTableManager tableManager;

    /**
     * Initialisiert den Controller und die UI-Komponenten.
     *
     * <p>Führt folgende Initialisierung durch:</p>
     * <ol>
     * <li>Service-Injection über ServiceFactory.</li>
     * <li>Setup der erweiterten Tabellenverwaltung mit Such- und Paginations-Features.</li>
     * </ol>
     *
     * @param location  Die FXML-Ressourcen-URL (automatisch injiziert).
     * @param resources Die Lokalisierungsressourcen (automatisch injiziert).
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initialisiere DataViewController mit verbesserter Tabellenverwaltung");

        // Service-Injection
        this.fileService = ServiceFactory.getFileService();

        // Setup der adaptiven Tabellenverwaltung
        setupAdvancedTableManagement();

        logger.info("DataViewController erfolgreich initialisiert");
    }

    /**
     * Konfiguriert die erweiterte Tabellenverwaltung.
     *
     * <p>Erstellt eine adaptive Tabellenkomponente mit:</p>
     * <ul>
     * <li>Echtzeit-Suchfunktionalität.</li>
     * <li>Spaltenauswahl und -löschung.</li>
     * <li>Paginierung (100 Zeilen pro Seite).</li>
     * <li>Dynamischer Ergebniszähler.</li>
     * </ul>
     */
    private void setupAdvancedTableManagement() {
        TableViewBuilder builder = TableViewBuilder.create()
                .withFeatures(
                        TableViewBuilder.Feature.SEARCH,
                        TableViewBuilder.Feature.SELECTION,
                        TableViewBuilder.Feature.PAGINATION,
                        TableViewBuilder.Feature.EXPORT
                        // Könnte bei Bedarf hinzugefügt werden.
                )
                .withActionsLabel("Ausgewählte Spalten löschen:");

        this.tableManager = builder.buildManager()
                .enableSearch()
                .enableSelection()
                .enablePagination(100);

        // Tabellen-Container in die UI einbinden
        resultsContainer.getChildren().clear();
        resultsContainer.getChildren().add(builder.getTableContainer());

        // Event-Handler für den Delete-Button aus dem Builder
        Button deleteButton = builder.getDeleteColumnsButton();
        if (deleteButton != null) {
            deleteButton.setOnAction(e -> tableManager.handleDeleteSelectedColumns());
        }

        logger.debug("Erweiterte Tabellenverwaltung für DataView konfiguriert");
    }

    /**
     * Öffnet einen {@link FileChooser}, um eine Datei auszuwählen, und lädt die Daten asynchron.
     *
     * <p><strong>Ablauf:</strong></p>
     * <ol>
     * <li>Konfiguriert den FileChooser für CSV- und XLSX-Dateien.</li>
     * <li>Startet einen Hintergrund-Task, um die Datei zu laden.</li>
     * <li>Aktualisiert die UI bei Erfolg oder zeigt eine Fehlermeldung bei Misserfolg.</li>
     * </ol>
     */
    @FXML
    private void loadFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Datei auswählen");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Tabellendateien", "*.csv", "*.xlsx"),
                new FileChooser.ExtensionFilter("CSV-Dateien", "*.csv"),
                new FileChooser.ExtensionFilter("Excel-Dateien", "*.xlsx")
        );
        File file = fileChooser.showOpenDialog(loadFileButton.getScene().getWindow());

        if (file != null) {
            Task<List<RowData>> loadTask = new Task<>() {
                @Override
                protected List<RowData> call() throws Exception {
                    return fileService.readFile(file.getAbsolutePath());
                }
            };

            loadTask.setOnSucceeded(e -> {
                fullDataList = loadTask.getValue();
                logger.info("{} Zeilen aus {} geladen.", fullDataList.size(), file.getName());
                tableManager.populateTableView(fullDataList);
                showSuccessDialog("Laden erfolgreich", "Die Datei wurde erfolgreich geladen.");
            });

            loadTask.setOnFailed(e -> {
                logger.error("Fehler beim Laden der Datei", loadTask.getException());
                showErrorDialog("Fehler beim Laden", "Die Datei konnte nicht gelesen werden:\n" + loadTask.getException().getMessage());
            });

            // Starten Sie den Task in einem neuen Thread
            new Thread(loadTask, "file-load-thread").start();
        }
    }
}