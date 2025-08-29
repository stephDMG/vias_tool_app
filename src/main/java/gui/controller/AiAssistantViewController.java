package gui.controller;

import gui.controller.utils.TableManager;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import model.RowData;
import model.enums.ExportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ServiceFactory;
import service.interfaces.AiService;
import service.interfaces.DatabaseService;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.ResourceBundle;

import static gui.controller.utils.Dialog.showErrorDialog;
import static gui.controller.utils.Dialog.showSuccessDialog;
import static gui.controller.utils.format.FormatterService.exportWithFormat;

/**
 * Controller für die Ansicht "KI-Assistent".
 * <p>
 * Verantwortlich für die Interaktion mit dem KI-Service zur SQL-Generierung,
 * das Ausführen der Abfrage gegen die Datenbank, die tabellarische Vorschau
 * inklusive Spaltenverwaltung sowie den Export (CSV/XLSX). Bietet Hilfs-Chips
 * mit Beispielprompts und Paginierung großer Ergebnismengen.
 * </p>
 */
public class AiAssistantViewController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger( AiAssistantViewController.class);
    private static final int ROWS_PER_PAGE = 100;

    // --- FXML-Komponenten ---
    @FXML
    private TextArea questionTextArea;
    @FXML private Button generateSqlButton;
    @FXML private ProgressBar progressBar;
    @FXML private TextArea sqlTextArea;
    @FXML private Button executeQueryButton;
    @FXML private Label resultsCountLabel;
    @FXML private TableView<ObservableList<String>> previewTableView;
    @FXML private Pagination pagination;
    @FXML private Button exportCsvButton;
    @FXML private Button exportXlsxButton;
    @FXML private Label statusLabel;
    @FXML private Button deleteColumnsButton;

    // Hilfe & Doku (neue UI)
    @FXML private ComboBox<String> helpContextCombo;
    @FXML private FlowPane helpChipsPane;

    // --- Services ---
    private AiService aiService;
    private DatabaseService databaseService;
    private List<RowData> fullResults = new ArrayList<>();

    // Beispiel-Datensatz
    private record Example(String label, String promptTemplate) {}

    private TableManager tableManager;

    /**
     * Initialisiert die Ansicht: Service-Factories, Paginierung, Tastenkürzel,
     * Tabellen-Manager sowie Hilfe-/Beispielchips.
     *
     * @param location FXML-Standort
     * @param resources Lokalisierungsressourcen
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.aiService = ServiceFactory.getAiService();
        this.databaseService = ServiceFactory.getDatabaseService();

        pagination.setPageFactory(this::createPage);
        pagination.setVisible(false);

        // Eingabe-Status
        questionTextArea.textProperty().addListener((obs, ov, nv) -> {
            boolean isEmpty = nv == null || nv.trim().isEmpty();
            generateSqlButton.setDisable(isEmpty);
            statusLabel.setText(isEmpty
                    ? "Warte auf Eingabe..."
                    : "Bereit. Klicken Sie auf 'SQL generieren' oder drücken Sie Strg+Enter.");
        });

        // Strg+Enter -> SQL generieren
        questionTextArea.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && event.isControlDown()) {
                if (!generateSqlButton.isDisabled()) {
                    generateSqlButton.fire();
                    event.consume();
                }
            }
        });

        //Tabellen-Manager initialisieren
        this.tableManager =  new TableManager(previewTableView);
        this.tableManager.allowSelection(deleteColumnsButton);


        // Hilfe & Beispiele
        populateHelp();
    }

    @FXML
    private void handleDeleteSelectedColumns() {
        tableManager.handleDeleteSelectedColumns(fullResults);
    }


    /**
     * Erzeugt auf Basis der Benutzereingabe eine SQL-Abfrage mittels KI-Service.
     * Setzt Statusmeldungen und behandelt Fehlerfälle.
     */
    @FXML
    private void generateSql() {
        String userQuestion = questionTextArea.getText();
        if (userQuestion == null || userQuestion.trim().isEmpty()) return;

        setProcessing(true, "KI analysiert und generiert SQL...");
        Task<String> generateTask = new Task<>() {
            @Override protected String call() {
                return aiService.generateQuery(userQuestion);
            }
        };

        generateTask.setOnSucceeded(event -> {
            String generatedSql = generateTask.getValue();
            sqlTextArea.setText(generatedSql);

            if (generatedSql != null && (generatedSql.startsWith("-- KI-Hinweis:") || generatedSql.startsWith("-- LOKALER KI-FEHLER"))) {
                setProcessing(false, "Anfrage unklar. Bitte Vorschläge beachten/Parameter präzisieren.");
            } else {
                setProcessing(false, "SQL-Abfrage generiert. Bitte prüfen.");
            }
        });

        generateTask.setOnFailed(event -> {
            setProcessing(false, "Fehler bei der Kommunikation mit dem KI-Service.");
            showErrorDialog("Service-Fehler", generateTask.getException() != null ? generateTask.getException().getMessage() : "Unbekannter Fehler");
        });

        new Thread(generateTask, "ai-generate-sql").start();
    }

    /**
     * Führt die im SQL-Editor stehende Abfrage gegen die Datenbank aus und
     * zeigt eine paginierte Vorschau. Schaltet Export-Buttons frei.
     */
    @FXML
    private void executeQuery() {
        String sql = sqlTextArea.getText();
        if (sql == null || sql.trim().isEmpty() || sql.startsWith("--")) {
            showErrorDialog("Ungültige Abfrage", "Es ist keine gültige SQL-Abfrage zum Ausführen vorhanden.");
            return;
        }

        setProcessing(true, "Führe Abfrage aus...");
        previewTableView.getColumns().clear();
        previewTableView.getItems().clear();
        pagination.setVisible(false);
        resultsCountLabel.setText("");

        databaseService.invalidateCache();

        Task<List<RowData>> queryTask = new Task<>() {
            @Override protected List<RowData> call() throws Exception {
                return databaseService.executeRawQuery(sql);
            }
            @Override protected void succeeded() {
                fullResults = getValue() != null ? getValue() : new ArrayList<>();

                if (!fullResults.isEmpty()) {
                    setupPagination();
                    exportCsvButton.setDisable(false);
                    exportXlsxButton.setDisable(false);
                } else {
                    resultsCountLabel.setText("(0 Zeilen gefunden)");
                }
                setProcessing(false, fullResults.size() + " Zeilen gefunden.");
            }
            @Override protected void failed() {
                setProcessing(false, "Fehler bei der Abfrage.");
                showErrorDialog("Datenbankfehler", getException() != null ? getException().getMessage() : "Unbekannter Fehler");
            }
        };
        new Thread(queryTask, "ai-exec-sql").start();
    }

    /**
     * Exportiert die angezeigten Ergebnisse als CSV oder XLSX, abhängig vom
     * geklickten Button. Nutzt FormatterService für konsistentes Mapping.
     *
     * @param event ActionEvent des Buttons (liefert das gewünschte Format)
     */
    @FXML
    private void exportFullReport(javafx.event.ActionEvent event) {

        if (fullResults == null || fullResults.isEmpty() || fullResults.get(0).getValues().isEmpty()) {
            logger.warn("Warnung: Datenliste ist leer. Header können nicht bestimmt werden.");
            return ;
        }

        // Anzeige-Header (sichtbar) und Original-Keys (für Formatlogik) ermitteln
        List<TableColumn<ObservableList<String>, ?>> cols = previewTableView.getColumns();
        List<String> displayHeaders = cols.stream().map(TableColumn::getText).toList();
        List<String> backingKeys = cols.stream()
                .map(c -> String.valueOf(c.getUserData()))
                .toList();

        Button sourceButton = (Button) event.getSource();
        ExportFormat format = sourceButton.getId().toLowerCase().contains("csv") ? ExportFormat.CSV : ExportFormat.XLSX;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("KI-Bericht exportieren");
        fileChooser.setInitialFileName("ki_export." + format.getExtension());
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(format.name() + "-Dateien", "*." + format.getExtension()));
        File file = fileChooser.showSaveDialog(generateSqlButton.getScene().getWindow());
        if (file == null) return;

        setProcessing(true, "Exportiere " + format.name() + "...");

        Task<Void> exportTask = new Task<>() {
            @Override protected Void call() throws Exception {

                exportWithFormat(fullResults, displayHeaders, backingKeys, file, format);
                return null;
            }
            @Override protected void succeeded() {
                setProcessing(false, "Export erfolgreich abgeschlossen.");
                showSuccessDialog("Export erfolgreich", "Der Bericht wurde gespeichert als:\n" + file.getName());
            }
            @Override protected void failed() {
                setProcessing(false, "Export fehlgeschlagen.");
                showErrorDialog("Exportfehler", getException() != null ? getException().getMessage() : "Unbekannter Fehler");
            }
        };
        new Thread(exportTask, "ai-export").start();
    }

    // --- Hilfe & Beispiele ---

    private void populateHelp() {
        helpContextCombo.getItems().setAll("Cover");
        helpContextCombo.getSelectionModel().select("Cover");
        helpContextCombo.valueProperty().addListener((obs, oldV, newV) -> renderHelpChips(newV));
        renderHelpChips(helpContextCombo.getValue());
    }

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
                // Prompt einfügen + sofort SQL generieren
                questionTextArea.setText(ex.promptTemplate());
                questionTextArea.positionCaret(ex.promptTemplate().length());
                generateSqlButton.fire();
            });
            helpChipsPane.getChildren().add(chip);
        }
    }

    private List<Example> coverExamples() {
        return List.of(
                new Example("VSN exakt", "Verträge mit VSN 10011202"),
                new Example("Makler-ID", "Verträge für Makler 100120"),
                new Example("Makler-Name + Felder", "Verträge für Makler Name Gründemann% mit Feldern Firma, Land"),
                new Example("Beginn-Zeitraum + Status + Land",
                        "Verträge mit Beginn zwischen 01.01.2023 und 31.12.2023 und mit Status A und in Land Deutschland und zwar Land, vsn, Stand, anfang und beginn"),
                new Example("Sortierung mehrfach", "Verträge für Makler Name Gründemann* order by Firma asc, Land desc"),
                new Example("Exclude + Limit", "Verträge für Makler 100120 außer Firma, Land limit 50"),
                new Example("Top 25 - offene Verträge", "alle Verträge mit Status O limit 25")
        );
    }

    // --- UI-Helfer ---

    private void setProcessing(boolean isProcessing, String status) {
        progressBar.setVisible(isProcessing);
        questionTextArea.setDisable(isProcessing);
        generateSqlButton.setDisable(isProcessing || questionTextArea.getText() == null || questionTextArea.getText().isBlank());

        String currentSql = sqlTextArea.getText();
        boolean validSql = currentSql != null && !currentSql.isBlank() && !currentSql.startsWith("--");
        executeQueryButton.setDisable(isProcessing || !validSql);

        exportCsvButton.setDisable(isProcessing || fullResults.isEmpty());
        exportXlsxButton.setDisable(isProcessing || fullResults.isEmpty());

        statusLabel.setText(status);
    }

    private void setupPagination() {
        resultsCountLabel.setText("(" + fullResults.size() + " Zeilen insgesamt)");
        int pageCount = (int) Math.ceil((double) fullResults.size() / ROWS_PER_PAGE);
        pagination.setPageCount(Math.max(pageCount, 1));
        pagination.setCurrentPageIndex(0);
        pagination.setVisible(true);
        createPage(0);
    }

    private Node createPage(int pageIndex) {
        int fromIndex = pageIndex * ROWS_PER_PAGE;
        int toIndex = Math.min(fromIndex + ROWS_PER_PAGE, fullResults.size());
        populateTableView(fullResults.subList(fromIndex, toIndex));
        return new VBox();
    }

    private void populateTableView(List<RowData> data) {
        this.tableManager.populateTableView(data);
    }

}
