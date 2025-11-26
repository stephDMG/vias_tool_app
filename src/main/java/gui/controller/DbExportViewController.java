package gui.controller;

import gui.controller.manager.EnhancedTableManager;
import gui.controller.manager.TableLayoutHelper;
import gui.controller.manager.TableViewBuilder;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import model.RowData;
import model.enums.ExportFormat;
import model.enums.QueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ServiceFactory;
import service.interfaces.DatabaseService;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import static gui.controller.dialog.Dialog.showErrorDialog;
import static gui.controller.dialog.Dialog.showSuccessDialog;
import static gui.controller.service.FormatterService.exportWithFormat;

/**
 * Controller für den Datenbank-Exportbereich mit verbesserter Tabellenverwaltung.
 *
 * <p><strong>Funktionalität:</strong></p>
 * <ul>
 *   <li>Vordefinierte Datenbankabfragen mit flexibler Parametereingabe</li>
 *   <li>Grid-basierte Parameter (Standard) und Listen-basierte Parameter (spezielle Queries)</li>
 *   <li>Erweiterte Tabellenverwaltung mit Spaltenauswahl, Umbenennung und Löschung</li>
 *   <li>Pagination für große Ergebnismengen (100 Datensätze pro Seite)</li>
 *   <li>Export in CSV/XLSX mit benutzerdefinierten Spalten</li>
 * </ul>
 *
 * <p><strong>Technische Verbesserungen:</strong></p>
 * <ul>
 *   <li>Verwendung des {@link EnhancedTableManager} für vereinheitlichte Tabellenfunktionen</li>
 *   <li>Dynamische UI-Anpassung basierend auf Query-Typ (Grid vs. Liste)</li>
 *   <li>Zentrale Export-Header-Verwaltung</li>
 *   <li>Automatische Spalten-Kontextmenüs (Umbenennen, Löschen)</li>
 * </ul>
 *
 * <p><strong>Unterstützte Query-Typen:</strong></p>
 * <ul>
 *   <li><strong>Standard-Queries:</strong> Parameter über GridPane-Eingabefelder</li>
 *   <li><strong>Listen-Queries:</strong> Multiple Parameter über ListView (z.B. Schadennummern)</li>
 * </ul>
 *
 * @author Stephane Dongmo
 * @version 2.0
 * @since 1.0
 */
public class DbExportViewController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(DbExportViewController.class);
    private final List<TextField> parameterTextFields = new ArrayList<>();
    private final ObservableList<String> listParameters = FXCollections.observableArrayList();
    // === FXML-Komponenten (bestehend) ===
    @FXML
    private ComboBox<String> reportComboBox;
    @FXML
    private Button helpButton;
    @FXML
    private GridPane parameterGrid;
    @FXML
    private Button executeQueryButton;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label statusLabel;
    @FXML
    private VBox resultsContainer;
    @FXML
    private VBox parameterListBox;
    @FXML
    private TextField parameterInputTextField;
    @FXML
    private ListView<String> parameterListView;
    // === Services und Datenmodell ===
    private DatabaseService databaseService;
    private QueryRepository selectedQuery;
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
     *   <li>Befüllung der Query-ComboBox (ohne AI-Queries)</li>
     *   <li>Setup der adaptiven Tabellenverwaltung mit allen Features</li>
     *   <li>Event-Handler-Registrierung für Parameter-Ansichten</li>
     * </ol>
     *
     * @param location  Die FXML-Ressourcen-URL (automatisch injiziert)
     * @param resources Die Lokalisierungsressourcen (automatisch injiziert)
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initialisiere DbExportViewController mit verbesserter Tabellenverwaltung");

        // Service-Injection
        this.databaseService = ServiceFactory.getDatabaseService();

        // Query-ComboBox befüllen (ohne AI-Queries)
        List<String> displayNames = QueryRepository.getDisplayNames().stream()
                .filter(name -> !name.startsWith("AI"))
                .collect(Collectors.toList());
        reportComboBox.setItems(FXCollections.observableArrayList(displayNames));

        // Parameter-Ansicht basierend auf Query-Auswahl umschalten
        reportComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedQuery = QueryRepository.fromDisplayName(newVal);
                updateParameterView();
            }
        });

        // ListView für Listen-basierte Parameter
        parameterListView.setItems(listParameters);

        // === Neue adaptive Tabellenverwaltung ===
        setupTable();

        // Initialzustand setzen
        setInitialState();


        logger.info("DbExportViewController erfolgreich initialisiert");
    }

    /**
     * Konfiguriert die erweiterte Tabellenverwaltung mit allen Features.
     *
     * <p>Erstellt eine adaptive Tabellenkomponente mit:</p>
     * <ul>
     *   <li>Spaltenauswahl und -löschung</li>
     *   <li>Pagination (100 Zeilen pro Seite)</li>
     *   <li>Export-Funktionalität (CSV/XLSX)</li>
     *   <li>Automatische Ergebnis-Zählung</li>
     * </ul>
     */
    private void setupTable() {
        TableViewBuilder builder = TableViewBuilder.create()
                .withFeatures(
                        TableViewBuilder.Feature.SELECTION,
                        TableViewBuilder.Feature.PAGINATION,
                        TableViewBuilder.Feature.EXPORT
                )
                .withExportLabel("Datenbankbericht exportieren als:")
                .withActionsLabel("Ausgewählte Spalten löschen:");

        // TableManager mit allen Features erstellen
        this.tableManager = builder.buildManager()
                .enableSelection()
                .enablePagination(100);

        // Export-Buttons referenzieren
        this.exportCsvButton = builder.getExportCsvButton();
        this.exportXlsxButton = builder.getExportXlsxButton();

        Button deleteButton = builder.getDeleteColumnsButton();
        deleteButton.setOnAction(e -> {
            System.out.println("Delete button clicked - calling tableManager");
            tableManager.handleDeleteSelectedColumns();
        });

        // Event-Handler für Export-Buttons
        exportCsvButton.setOnAction(this::exportFullReport);
        exportXlsxButton.setOnAction(this::exportFullReport);

        VBox tableContainer = builder.getTableContainer();

        TableLayoutHelper.configureTableContainer(
                resultsContainer,  // parent
                tableContainer,     // enfant
                getClass().getSimpleName()
        );

        Platform.runLater(() -> {
            var ttv = tableManager.getTableView();
            ttv.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        });

        logger.debug("Erweiterte Tabellenverwaltung konfiguriert");
    }

    /**
     * Verwaltet die Spaltenauswahl und -löschung über den EnhancedTableManager.
     * Diese Methode wird vom Delete-Button der Tabelle aufgerufen.
     */
    @FXML
    private void handleDeleteSelectedColumns() {
        tableManager.handleDeleteSelectedColumns();
    }

    /**
     * Schaltet die Parameter-UI basierend auf dem ausgewählten Query-Typ um.
     *
     * <p><strong>Zwei Modi werden unterstützt:</strong></p>
     * <ul>
     *   <li><strong>Standard-Grid:</strong> Einzelne Textfelder für jeden Parameter</li>
     *   <li><strong>Listen-Ansicht:</strong> ListView für multiple Parameter (z.B. Schadennummern)</li>
     * </ul>
     *
     * <p>Die Umschaltung erfolgt automatisch basierend auf dem Query-Typ.</p>
     */
    private void updateParameterView() {
        if (selectedQuery == QueryRepository.SCHADEN_DETAILS_BY_MAKLER_SNR) {
            // Listen-basierte Parameter-Eingabe
            parameterGrid.setVisible(false);
            parameterGrid.setManaged(false);
            parameterListBox.setVisible(true);
            parameterListBox.setManaged(true);
            logger.debug("Umschaltung auf Listen-Parameter für Query: {}", selectedQuery.getDisplayName());
        } else {
            // Standard Grid-basierte Parameter-Eingabe
            parameterListBox.setVisible(false);
            parameterListBox.setManaged(false);
            parameterGrid.setVisible(true);
            parameterGrid.setManaged(true);
            populateParameterGrid();
            logger.debug("Umschaltung auf Grid-Parameter für Query: {}", selectedQuery.getDisplayName());
        }
        helpButton.setDisable(false);
    }

    /**
     * Befüllt das Parameter-Grid mit Eingabefeldern basierend auf den Query-Parametern.
     * Jeder Parameter erhält ein Label und ein entsprechendes TextField.
     */
    private void populateParameterGrid() {
        parameterGrid.getChildren().clear();
        parameterTextFields.clear();

        List<String> paramNames = selectedQuery.getParameterNames();
        for (int i = 0; i < paramNames.size(); i++) {
            Label label = new Label(paramNames.get(i) + ":");
            TextField textField = new TextField();
            textField.setPromptText("Wert für " + paramNames.get(i) + " eingeben...");

            parameterGrid.add(label, 0, i);
            parameterGrid.add(textField, 1, i);
            parameterTextFields.add(textField);
        }

        logger.debug("Parameter-Grid befüllt mit {} Parametern", paramNames.size());
    }

    /**
     * Fügt einen Parameter zur Liste hinzu (nur für Listen-basierte Queries).
     * Verhindert doppelte Einträge und leert das Eingabefeld nach erfolgreichem Hinzufügen.
     */
    @FXML
    private void handleAddParameter() {
        String newParam = parameterInputTextField.getText().trim();
        if (!newParam.isEmpty() && !listParameters.contains(newParam)) {
            listParameters.add(newParam);
            parameterInputTextField.clear();
            logger.debug("Parameter hinzugefügt: {}", newParam);
        } else if (listParameters.contains(newParam)) {
            logger.warn("Parameter bereits vorhanden: {}", newParam);
        }
    }

    /**
     * Entfernt den ausgewählten Parameter aus der Liste (nur für Listen-basierte Queries).
     */
    @FXML
    private void handleRemoveParameter() {
        String selectedItem = parameterListView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            listParameters.remove(selectedItem);
            logger.debug("Parameter entfernt: {}", selectedItem);
        }
    }

    /**
     * Extrahiert Parameter aus der jeweils aktiven UI-Ansicht (Grid oder Liste).
     *
     * @return Liste der eingegebenen Parameter, oder {@code null} bei Validierungsfehlern
     */
    private List<String> getParametersFromUi() {
        if (selectedQuery == null) {
            showErrorDialog("Eingabe fehlt", "Bitte wählen Sie zuerst einen Bericht aus.");
            return null;
        }

        List<String> parameters;

        if (selectedQuery == QueryRepository.SCHADEN_DETAILS_BY_MAKLER_SNR) {
            // Parameter aus ListView extrahieren
            parameters = new ArrayList<>(listParameters);
            if (parameters.isEmpty()) {
                showErrorDialog("Eingabe fehlt", "Bitte fügen Sie mindestens eine Schadennummer zur Liste hinzu.");
                return null;
            }
        } else {
            // Parameter aus Grid-TextFields extrahieren
            parameters = parameterTextFields.stream()
                    .map(tf -> tf.getText().trim())
                    .collect(Collectors.toList());
            if (parameters.stream().anyMatch(String::isEmpty)) {
                showErrorDialog("Eingabe fehlt", "Bitte füllen Sie alle Parameterfelder aus.");
                return null;
            }
        }

        logger.info("Parameter extrahiert: {} Werte für Query {}", parameters.size(), selectedQuery.getDisplayName());
        return parameters;
    }

    /**
     * Führt die Datenbankabfrage aus und zeigt die Ergebnisse in der Tabelle an.
     *
     * <p><strong>Ablauf:</strong></p>
     * <ol>
     *   <li>Parameter-Validierung</li>
     *   <li>UI in Processing-Modus setzen</li>
     *   <li>Asynchrone Datenbankabfrage über Task</li>
     *   <li>Ergebnisse in der EnhancedTableManager-Tabelle anzeigen</li>
     *   <li>Export-Buttons aktivieren bei erfolgreichen Ergebnissen</li>
     * </ol>
     */
    @FXML
    private void executeQuery() {
        List<String> parameters = getParametersFromUi();
        if (parameters == null) return;

        setProcessingState(true, "Führe Datenbankabfrage aus...");

        // Cache invalidieren für aktuelle Daten
        databaseService.invalidateCache();

        Task<List<RowData>> queryTask = new Task<>() {
            @Override
            protected List<RowData> call() throws Exception {
                logger.info("Führe Query aus: {} mit {} Parametern", selectedQuery.getDisplayName(), parameters.size());
                return databaseService.executeQuery(selectedQuery, parameters);
            }

            @Override
            protected void succeeded() {
                fullResults = getValue();
                if (fullResults != null && !fullResults.isEmpty()) {
                    // Daten an EnhancedTableManager übergeben
                    tableManager.populateTableView(fullResults);

                    // Export-Buttons aktivieren
                    exportCsvButton.setDisable(false);
                    exportXlsxButton.setDisable(false);

                    logger.info("Query erfolgreich: {} Datensätze geladen", fullResults.size());
                } else {
                    logger.info("Query ergab keine Ergebnisse");
                }
                setProcessingState(false, fullResults.size() + " Zeilen gefunden.");
            }

            @Override
            protected void failed() {
                logger.error("Query fehlgeschlagen", getException());
                setProcessingState(false, "Fehler bei der Datenbankabfrage.");
                showErrorDialog("Datenbankfehler", getException().getMessage());
            }
        };

        new Thread(queryTask, "db-query-thread").start();
    }

    /**
     * Exportiert den vollständigen Bericht im ausgewählten Format.
     *
     * <p><strong>Export-Features:</strong></p>
     * <ul>
     *   <li>Verwendet nur die sichtbaren/nicht-gelöschten Spalten</li>
     *   <li>Berücksichtigt benutzerdefinierte Spaltennamen (Umbenennungen)</li>
     *   <li>Formatierung über FormatterService (Datum, Geld, etc.)</li>
     *   <li>Asynchroner Export mit Fortschrittsanzeige</li>
     * </ul>
     *
     * @param event ActionEvent vom Export-Button (bestimmt das Format)
     */
    @FXML
    private void exportFullReport(ActionEvent event) {
        if (fullResults == null || fullResults.isEmpty()) {
            logger.warn("Export aufgerufen, aber keine Daten vorhanden");
            showErrorDialog("Keine Daten", "Es sind keine Daten zum Exportieren vorhanden.");
            return;
        }

        // Export-Header aus der aktuellen Tabellenansicht extrahieren
        List<String> displayHeaders = tableManager.getDisplayHeaders();
        List<String> originalKeys = tableManager.getOriginalKeys();
        List<RowData> exportData = tableManager.getOriginalData(); // Verwende gefilterte Daten

        // Format basierend auf Button bestimmen
        Button sourceButton = (Button) event.getSource();
        ExportFormat format = sourceButton.getId().contains("Csv") ? ExportFormat.CSV : ExportFormat.XLSX;

        // FileChooser konfigurieren
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Datenbankbericht exportieren");
        fileChooser.setInitialFileName(selectedQuery.name().toLowerCase() + "_export." + format.getExtension());
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(format.name() + "-Dateien", "*." + format.getExtension())
        );

        File file = fileChooser.showSaveDialog(executeQueryButton.getScene().getWindow());
        if (file == null) return;

        // Asynchroner Export
        setProcessingState(true, "Exportiere " + format.name() + "-Datei...");

        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                logger.info("Starte Export: {} Datensätze, {} Spalten, Format: {}",
                        exportData.size(), displayHeaders.size(), format);
                exportWithFormat(exportData, displayHeaders, originalKeys, file, format);
                return null;
            }

            @Override
            protected void succeeded() {
                setProcessingState(false, "Export erfolgreich abgeschlossen.");
                showSuccessDialog("Export erfolgreich",
                        "Der Bericht wurde erfolgreich gespeichert:\n" + file.getName());
                logger.info("Export erfolgreich abgeschlossen: {}", file.getAbsolutePath());
            }

            @Override
            protected void failed() {
                logger.error("Export fehlgeschlagen", getException());
                setProcessingState(false, "Export fehlgeschlagen.");
                showErrorDialog("Exportfehler",
                        "Der Export ist fehlgeschlagen:\n" + getException().getMessage());
            }
        };

        new Thread(exportTask, "db-export-thread").start();
    }

    /**
     * Zeigt kontextuelle Hilfe für die ausgewählte Query an.
     * Zeigt Beschreibung, erwartete Parameter und Beispiele in einem Dialog.
     */
    @FXML
    private void showHelp() {
        if (selectedQuery != null) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Hilfe für: " + selectedQuery.getDisplayName());
            alert.setHeaderText("Beschreibung des Berichts");
            alert.setContentText(selectedQuery.getDescription());
            alert.showAndWait();
            logger.debug("Hilfe angezeigt für Query: {}", selectedQuery.getDisplayName());
        }
    }

    /**
     * Setzt den UI-Zustand für Processing-Operationen.
     *
     * @param isProcessing {@code true} wenn eine Operation läuft, {@code false} sonst
     * @param status    Statustext für den Benutzer
     */
    private void setProcessingState(boolean isProcessing, String status) {
        progressBar.setVisible(isProcessing);
        statusLabel.setText(status);
        executeQueryButton.setDisable(isProcessing);

        // Export-Buttons nur aktivieren wenn Daten vorhanden und nicht processing
        boolean hasData = fullResults != null && !fullResults.isEmpty();
        exportCsvButton.setDisable(isProcessing || !hasData);
        exportXlsxButton.setDisable(isProcessing || !hasData);
    }

    /**
     * Setzt den initialen UI-Zustand beim Start der Anwendung.
     * Alle Export-Controls und Parameter-Views sind initially deaktiviert/versteckt.
     */
    private void setInitialState() {
        helpButton.setDisable(true);

        // Parameter-Views verstecken (werden bei Query-Auswahl eingeblendet)
        parameterGrid.setVisible(false);
        parameterGrid.setManaged(false);
        parameterListBox.setVisible(false);
        parameterListBox.setManaged(false);

        logger.debug("Initialer UI-Zustand gesetzt");
    }
}