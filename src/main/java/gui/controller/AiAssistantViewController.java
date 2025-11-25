package gui.controller;

import gui.controller.manager.EnhancedTableManager;
import gui.controller.manager.TableViewBuilder;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import model.RowData;
import model.enums.ExportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import static gui.controller.dialog.Dialog.showErrorDialog;
import static gui.controller.dialog.Dialog.showSuccessDialog;
import static gui.controller.service.FormatterService.exportWithFormat;

/**
 * Controller für die KI-Assistent-Ansicht mit verbesserter Tabellenverwaltung.
 *
 * <p><strong>Funktionalität:</strong></p>
 * <ul>
 *   <li>Natürlichsprachliche SQL-Generierung über KI-Service</li>
 *   <li>Direkte Ausführung der generierten Queries gegen die Datenbank</li>
 *   <li>Erweiterte Tabellenverwaltung mit Suche, Spaltenmanagement und Export</li>
 *   <li>Kontextuelle Hilfe-Chips mit vorgefertigten Beispiel-Prompts</li>
 *   <li>Paginierung für große Ergebnismengen</li>
 * </ul>
 *
 * <p><strong>Technische Verbesserungen:</strong></p>
 * <ul>
 *   <li>Verwendung des {@link EnhancedTableManager} für vereinheitlichte Tabellenfunktionen</li>
 *   <li>Integrierte Suchfunktionalität über alle Spalten</li>
 *   <li>Automatische Spalten-Kontextmenüs (Umbenennen, Löschen)</li>
 *   <li>Optimierte Export-Funktionalität mit benutzerdefinierten Headers</li>
 * </ul>
 *
 * <p><strong>KI-Integration:</strong></p>
 * <ul>
 *   <li><strong>Query-Generierung:</strong> Natürlichsprachliche Eingabe wird zu SQL konvertiert</li>
 *   <li><strong>Kontext-Awareness:</strong> Versteht VIAS-spezifische Tabellen und Felder</li>
 *   <li><strong>Error-Handling:</strong> Intelligente Fehlermeldungen bei ungültigen Queries</li>
 *   <li><strong>Beispiel-Prompts:</strong> Vorgefertigte Prompts für häufige Anwendungsfälle</li>
 * </ul>
 *
 * <p><strong>Unterstützte Features:</strong></p>
 * <ul>
 *   <li>Echtzeit-Suche über alle Tabellendaten</li>
 *   <li>Spaltenauswahl und -löschung</li>
 *   <li>Spaltenumbenennung für Export</li>
 *   <li>Paginierung (100 Datensätze pro Seite)</li>
 *   <li>Export in CSV/XLSX mit gefilterten Spalten</li>
 * </ul>
 *
 * @author VIAS Export Tool Team
 * @version 2.0
 * @since 1.0
 */
public class AiAssistantViewController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(AiAssistantViewController.class);

    // === FXML-Komponenten (bestehend) ===
    @FXML
    private TextArea questionTextArea;
    @FXML
    private Button generateSqlButton;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private TextArea sqlTextArea;
    @FXML
    private Button executeQueryButton;
    @FXML
    private Label statusLabel;
    @FXML
    private VBox resultsContainer;


    // Hilfe & Dokumentation
    @FXML
    private ComboBox<String> helpContextCombo;
    @FXML
    private FlowPane helpChipsPane;

    // === Services ===
    private service.interfaces.AiService aiService;
    private service.interfaces.DatabaseService databaseService;
    private List<RowData> fullResults = new ArrayList<>();

    // === Neue Tabellenverwaltung ===
    private EnhancedTableManager tableManager;
    private Button exportCsvButton;
    private Button exportXlsxButton;

    /**
     * Initialisiert den Controller und die UI-Komponenten.
     *
     * <p>Führt folgende Initialisierung durch:</p>
     * <ol>
     *   <li>Service-Injection über ServiceFactory</li>
     *   <li>Setup der erweiterten Tabellenverwaltung mit allen Features</li>
     *   <li>Konfiguration der Tastaturkürzel (Strg+Enter)</li>
     *   <li>Initialisierung der Hilfe-Chips und Beispiel-Prompts</li>
     * </ol>
     *
     * @param location  Die FXML-Ressourcen-URL (automatisch injiziert)
     * @param resources Die Lokalisierungsressourcen (automatisch injiziert)
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initialisiere AiAssistantViewController mit verbesserter Tabellenverwaltung");

        // Service-Injection
        this.aiService =  service.ServiceFactory.getAiService();
        this.databaseService = service.ServiceFactory.getDatabaseService();

        // === Erweiterte Tabellenverwaltung Setup ===
        setupAdvancedTableManagement();

        // === UI-Event-Handler Setup ===
        setupInputHandlers();

        // === Hilfe-System initialisieren ===
        populateHelp();
        logger.warn(String.valueOf(resultsContainer.getHeight()));

        logger.info("AiAssistantViewController erfolgreich initialisiert");
    }

    /**
     * Konfiguriert die erweiterte Tabellenverwaltung mit allen Features.
     *
     * <p>Erstellt eine adaptive Tabellenkomponente mit:</p>
     * <ul>
     *   <li>Echtzeit-Suchfunktionalität</li>
     *   <li>Spaltenauswahl und -löschung</li>
     *   <li>Paginierung (100 Zeilen pro Seite)</li>
     *   <li>Export-Funktionalität (CSV/XLSX)</li>
     *   <li>Automatische Ergebnis-Zählung</li>
     * </ul>
     */
    private void setupAdvancedTableManagement() {
        TableViewBuilder builder = TableViewBuilder.create()
                .withFeatures(TableViewBuilder.Feature.SEARCH, TableViewBuilder.Feature.SELECTION,
                        TableViewBuilder.Feature.PAGINATION, TableViewBuilder.Feature.EXPORT)
                .withExportLabel("KI-Bericht exportieren als:")
                .withActionsLabel("Ausgewählte Spalten löschen:");

        this.tableManager = builder.buildManager()
                .enableSearch()
                .enableSelection()
                .enablePagination(100);

        // ✅ CONFIGURATION UNIVERSELLE
        VBox tableContainer = builder.getTableContainer();
        VBox.setVgrow(tableContainer, javafx.scene.layout.Priority.ALWAYS);
        tableContainer.setMaxHeight(Double.MAX_VALUE);

        // 🔑 CLÉ: Forcer le conteneur parent
        resultsContainer.setMinHeight(400);
        resultsContainer.setPrefHeight(Region.USE_COMPUTED_SIZE);

        resultsContainer.getChildren().clear();
        resultsContainer.getChildren().add(tableContainer);

        // ✅ Configuration finale après chargement
        Platform.runLater(() -> {
            tableManager.getTableView().setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
            tableManager.getTableView().getColumns().forEach(col -> {
                col.setPrefWidth(150);
                col.setMinWidth(80);
            });
            resultsContainer.requestLayout();
        });

        // Reste du code (buttons, etc.)
        Button deleteButton = builder.getDeleteColumnsButton();
        deleteButton.setOnAction(e -> tableManager.handleDeleteSelectedColumns());

        exportCsvButton = builder.getExportCsvButton();
        exportXlsxButton = builder.getExportXlsxButton();
        exportCsvButton.setOnAction(this::exportFullReport);
        exportXlsxButton.setOnAction(this::exportFullReport);
    }

    /**
     * Konfiguriert Input-Handler und Tastaturkürzel.
     *
     * <p>Setup:</p>
     * <ul>
     *   <li>Button-Aktivierung basierend auf Eingabe-Status</li>
     *   <li>Strg+Enter Tastaturkürzel für SQL-Generierung</li>
     *   <li>Automatische Status-Updates</li>
     * </ul>
     */
    private void setupInputHandlers() {
        // Button-Aktivierung basierend auf Eingabe
        questionTextArea.textProperty().addListener((obs, ov, nv) -> {
            boolean isEmpty = nv == null || nv.trim().isEmpty();
            generateSqlButton.setDisable(isEmpty);
            statusLabel.setText(isEmpty
                    ? "Warte auf Eingabe..."
                    : "Bereit. Klicken Sie auf 'SQL generieren' oder drücken Sie Strg+Enter.");
        });

        // Strg+Enter Tastaturkürzel
        questionTextArea.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && event.isControlDown()) {
                if (!generateSqlButton.isDisabled()) {
                    generateSqlButton.fire();
                    event.consume();
                }
            }
        });

        logger.debug("Input-Handler und Tastaturkürzel konfiguriert");
    }

    /**
     * Generiert SQL basierend auf der natürlichsprachlichen Benutzereingabe.
     *
     * <p><strong>Ablauf:</strong></p>
     * <ol>
     *   <li>Eingabe-Validierung</li>
     *   <li>Asynchrone KI-Anfrage über Task</li>
     *   <li>SQL-Darstellung im Editor</li>
     *   <li>Aktivierung des "Ausführen"-Buttons bei gültigem SQL</li>
     * </ol>
     *
     * <p><strong>Error-Handling:</strong></p>
     * <ul>
     *   <li>Ungültige/unklare Anfragen werden erkannt</li>
     *   <li>KI-Hinweise werden entsprechend dargestellt</li>
     *   <li>Service-Fehler werden benutzerfreundlich angezeigt</li>
     * </ul>
     */
    @FXML
    private void generateSql() {
        String userQuestion = questionTextArea.getText();
        if (userQuestion == null || userQuestion.trim().isEmpty()) return;

        setProcessing(true, "KI analysiert Anfrage und generiert SQL...");

        Task<String> generateTask = new Task<>() {
            @Override
            protected String call() {
                logger.info("Sende KI-Anfrage: {}", userQuestion.substring(0, Math.min(50, userQuestion.length())));
                return aiService.generateQuery(userQuestion);
            }
        };

        generateTask.setOnSucceeded(event -> {
            String generatedSql = generateTask.getValue();
            sqlTextArea.setText(generatedSql);

            if (generatedSql != null && (generatedSql.startsWith("-- KI-Hinweis:") ||
                    generatedSql.startsWith("-- LOKALER KI-FEHLER"))) {
                setProcessing(false, "Anfrage unklar. Bitte Vorschläge beachten oder Parameter präzisieren.");
                logger.warn("KI konnte Anfrage nicht verarbeiten");
            } else {
                setProcessing(false, "SQL-Abfrage generiert. Bitte prüfen und ausführen.");
                logger.info("SQL erfolgreich generiert ({} Zeichen)", generatedSql.length());
            }
        });

        generateTask.setOnFailed(event -> {
            logger.error("KI-Service Fehler", generateTask.getException());
            setProcessing(false, "Fehler bei der Kommunikation mit dem KI-Service.");
            showErrorDialog("Service-Fehler",
                    generateTask.getException() != null ?
                            generateTask.getException().getMessage() : "Unbekannter Fehler");
        });

        new Thread(generateTask, "ai-generate-sql").start();
    }

    /**
     * Führt die im SQL-Editor stehende Abfrage gegen die Datenbank aus.
     *
     * <p><strong>Ablauf:</strong></p>
     * <ol>
     *   <li>SQL-Validierung (keine Kommentare/Hinweise)</li>
     *   <li>Cache-Invalidierung für aktuelle Daten</li>
     *   <li>Asynchrone Datenbankabfrage</li>
     *   <li>Ergebnisse an EnhancedTableManager übergeben</li>
     *   <li>Export-Buttons aktivieren bei erfolgreichen Ergebnissen</li>
     * </ol>
     *
     * <p><strong>Fehlerbehandlung:</strong></p>
     * <ul>
     *   <li>Ungültige SQL-Statements werden abgefangen</li>
     *   <li>Datenbankfehler werden benutzerfreundlich angezeigt</li>
     *   <li>Leere Ergebnismengen werden korrekt behandelt</li>
     * </ul>
     */
    @FXML
    private void executeQuery() {
        String sql = sqlTextArea.getText();
        if (sql == null || sql.trim().isEmpty() || sql.startsWith("--")) {
            showErrorDialog("Ungültige Abfrage",
                    "Es ist keine gültige SQL-Abfrage zum Ausführen vorhanden.");
            return;
        }

        setProcessing(true, "Führe Datenbankabfrage aus...");

        // Cache invalidieren für aktuelle Daten
        databaseService.invalidateCache();

        Task<List<RowData>> queryTask = new Task<>() {
            @Override
            protected List<RowData> call() throws Exception {
                logger.info("Führe SQL aus: {}", sql.substring(0, Math.min(100, sql.length())));
                return databaseService.executeRawQuery(sql);
            }

            @Override
            protected void succeeded() {
                fullResults = getValue() != null ? getValue() : new ArrayList<>();

                if (!fullResults.isEmpty()) {
                    // Daten an EnhancedTableManager übergeben
                    tableManager.populateTableView(fullResults);

                    // Export-Buttons aktivieren
                    exportCsvButton.setDisable(false);
                    exportXlsxButton.setDisable(false);

                    logger.info("Query erfolgreich: {} Datensätze geladen", fullResults.size());
                } else {
                    logger.info("Query ergab keine Ergebnisse");
                }

                setProcessing(false, fullResults.size() + " Zeilen gefunden.");
            }

            @Override
            protected void failed() {
                logger.error("Datenbankabfrage fehlgeschlagen", getException());
                setProcessing(false, "Fehler bei der Datenbankabfrage.");
                showErrorDialog("Datenbankfehler",
                        getException() != null ? getException().getMessage() : "Unbekannter Fehler");
            }
        };

        new Thread(queryTask, "ai-exec-sql").start();
    }

    /**
     * Exportiert die KI-generierten Ergebnisse im ausgewählten Format.
     *
     * <p><strong>Export-Features:</strong></p>
     * <ul>
     *   <li>Verwendet nur die sichtbaren/nicht-gelöschten Spalten</li>
     *   <li>Berücksichtigt benutzerdefinierte Spaltennamen (Umbenennungen)</li>
     *   <li>Formatierung über FormatterService (Datum, Geld, etc.)</li>
     *   <li>Respektiert aktuelle Suchfilter</li>
     * </ul>
     *
     * @param event ActionEvent vom Export-Button (bestimmt das Format)
     */
    @FXML
    private void exportFullReport(javafx.event.ActionEvent event) {
        if (fullResults == null || fullResults.isEmpty()) {
            logger.warn("Export aufgerufen, aber keine Daten vorhanden");
            showErrorDialog("Keine Daten", "Es sind keine Daten zum Exportieren vorhanden.");
            return;
        }

        // Export-Header aus der aktuellen Tabellenansicht extrahieren
        List<String> displayHeaders = tableManager.getDisplayHeaders();
        List<String> originalKeys = tableManager.getOriginalKeys();
        List<RowData> exportData = tableManager.getFilteredData(); // Verwende gefilterte Daten

        // Format basierend auf Button bestimmen
        Button sourceButton = (Button) event.getSource();
        ExportFormat format = sourceButton.getId().toLowerCase().contains("csv") ?
                ExportFormat.CSV : ExportFormat.XLSX;

        // FileChooser konfigurieren
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("KI-Bericht exportieren");
        fileChooser.setInitialFileName("ki_export." + format.getExtension());
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(format.name() + "-Dateien", "*." + format.getExtension())
        );

        File file = fileChooser.showSaveDialog(generateSqlButton.getScene().getWindow());
        if (file == null) return;

        // Asynchroner Export
        setProcessing(true, "Exportiere " + format.name() + "-Datei...");

        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                logger.info("Starte KI-Export: {} Datensätze, {} Spalten, Format: {}",
                        exportData.size(), displayHeaders.size(), format);
                exportWithFormat(exportData, displayHeaders, originalKeys, file, format);
                return null;
            }

            @Override
            protected void succeeded() {
                setProcessing(false, "Export erfolgreich abgeschlossen.");
                showSuccessDialog("Export erfolgreich",
                        "Der KI-Bericht wurde erfolgreich gespeichert:\n" + file.getName());
                logger.info("KI-Export erfolgreich abgeschlossen: {}", file.getAbsolutePath());
            }

            @Override
            protected void failed() {
                logger.error("KI-Export fehlgeschlagen", getException());
                setProcessing(false, "Export fehlgeschlagen.");
                showErrorDialog("Exportfehler",
                        "Der Export ist fehlgeschlagen:\n" + getException().getMessage());
            }
        };

        new Thread(exportTask, "ai-export").start();
    }

    /**
     * Initialisiert das Hilfe-System mit Kontext-ComboBox und Beispiel-Chips.
     * Stellt vorgefertigte Prompts für häufige VIAS-Anwendungsfälle bereit.
     */
    private void populateHelp() {
        helpContextCombo.getItems().setAll("Cover");
        helpContextCombo.getSelectionModel().select("Cover");
        helpContextCombo.valueProperty().addListener((obs, oldV, newV) -> renderHelpChips(newV));
        renderHelpChips(helpContextCombo.getValue());

        logger.debug("Hilfe-System initialisiert mit {} Kontexten", helpContextCombo.getItems().size());
    }

    // === Hilfe & Beispiele ===

    /**
     * Rendert Hilfe-Chips basierend auf dem ausgewählten Kontext.
     *
     * @param context Der ausgewählte Hilfe-Kontext (z.B. "Cover")
     */
    private void renderHelpChips(String context) {
        helpChipsPane.getChildren().clear();
        if (context == null) return;

        List<Example> examples = switch (context.toLowerCase()) {
            case "cover" -> coverExamples();
            default -> List.of();
        };

        for (Example ex : examples) {
            Button chip = new Button(ex.label());
            chip.getStyleClass().add("chip");
            chip.setOnAction(e -> {
                // Prompt einfügen und automatisch SQL generieren
                questionTextArea.setText(ex.promptTemplate());
                questionTextArea.positionCaret(ex.promptTemplate().length());
                generateSqlButton.fire();
                logger.debug("Beispiel-Prompt verwendet: {}", ex.label());
            });
            helpChipsPane.getChildren().add(chip);
        }

        logger.debug("Hilfe-Chips gerendert: {} Beispiele für Kontext '{}'", examples.size(), context);
    }

    /**
     * Liefert vorgefertigte Beispiel-Prompts für Cover/Verträge-Bereich.
     *
     * @return Liste der verfügbaren Beispiele mit Labels und Prompt-Templates
     */
    private List<Example> coverExamples() {
        return List.of(
                new Example("VSN exakt", "Verträge mit VSN 10011202"),
                new Example("Makler-ID", "Verträge für Makler 100120"),
                new Example("Makler-Name + Felder", "Verträge für Makler Name Gründemann% mit Feldern Firma, Land"),
                new Example("Beginn-Zeitraum + Status + Land",
                        "Verträge mit Beginn zwischen 01.01.2023 und 31.12.2023 und mit Status A und in Land Deutschland und zwar Land, vsn, Stand, anfang und beginn"),
                new Example("Sortierung mehrfach", "Verträge für Makler Name Gründemann* order by Firma asc, Land desc"),
                new Example("Exclude + Limit", "Verträge für Makler 100120 außer Firma, Land limit 50")
        );
    }

    /**
     * Setzt den UI-Zustand für Processing-Operationen.
     *
     * @param isProcessing {@code true} wenn eine Operation läuft, {@code false} sonst
     * @param status       Statustext für den Benutzer
     */
    private void setProcessing(boolean isProcessing, String status) {
        progressBar.setVisible(isProcessing);
        questionTextArea.setDisable(isProcessing);
        generateSqlButton.setDisable(isProcessing ||
                questionTextArea.getText() == null || questionTextArea.getText().isBlank());

        String currentSql = sqlTextArea.getText();
        boolean validSql = currentSql != null && !currentSql.isBlank() && !currentSql.startsWith("--");
        executeQueryButton.setDisable(isProcessing || !validSql);

        // Export-Buttons nur aktivieren wenn Daten vorhanden und nicht processing
        boolean hasData = fullResults != null && !fullResults.isEmpty();
        exportCsvButton.setDisable(isProcessing || !hasData);
        exportXlsxButton.setDisable(isProcessing || !hasData);

        statusLabel.setText(status);
    }

    // === UI-Helfer ===

    // === Beispiel-Datenstrukturen ===
    private record Example(String label, String promptTemplate) {
    }
}