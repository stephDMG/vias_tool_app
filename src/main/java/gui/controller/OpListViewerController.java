package gui.controller;

import formatter.OpListeFormatter;
import gui.controller.dialog.GroupingDialogController;
import gui.controller.manager.TableViewBuilder;
import gui.controller.dialog.Dialog;
import gui.controller.manager.EnhancedTableManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.RowData;
import service.ServiceFactory;
import service.op.OpRepository;

import java.util.List;
import java.util.stream.Collectors;


/**
 * Controller für die Ansicht der OP-Hauptliste.
 * Lädt Daten asynchron, zeigt Fortschritt an und ermöglicht Filterung nach Policen-Nr.
 * Nutzt eine universelle Tabellenkomponente mit Such-, Paginierungs- und Exportfunktionen.
 *
 * @author Stephane Dongmo
 * @since 15/07/2025
 */
public class OpListViewerController {

    @FXML private VBox resultsContainer;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private Label countLabel;
    @FXML private TextField policyFilterField;

    private OpRepository opRepository;
    private OpListeFormatter formatter;

    private EnhancedTableManager tableManager;

    private List<RowData> full;
    private List<RowData> cur;

    private boolean cfgEnabled = false;
    private String  cfgHeader  = null;
    private javafx.scene.paint.Color cfgA = null;
    private javafx.scene.paint.Color cfgB = null;

    @FXML
    private void initialize() {

        formatter = new OpListeFormatter();
        opRepository = ServiceFactory.getOpRepository();
        //opRepository = new OpRepository(ServiceFactory.getDatabaseService(), formatter);

        TableViewBuilder builder = TableViewBuilder.create()
                .withFeatures(
                        TableViewBuilder.Feature.SELECTION,
                        TableViewBuilder.Feature.PAGINATION,
                        TableViewBuilder.Feature.EXPORT,
                        TableViewBuilder.Feature.SEARCH
                )
                //.withGroupStriping("Rg-NR")
                .withExportLabel("Als Datei exportieren:")
                .withActionsLabel("Ausgewählte Spalten löschen:");

        this.tableManager = builder.buildManager()
                .enableSelection()
                .enablePagination(100)
                .enableSearch();

        resultsContainer.getChildren().clear();
        resultsContainer.getChildren().add(builder.getTableContainer());

        VBox.setVgrow(builder.getTableContainer(), javafx.scene.layout.Priority.ALWAYS);

        tableManager.bindAutoRowsPerPage((javafx.scene.layout.Region) builder.getTableContainer());

        // Charger les données
        loadDataAsync();
    }

    private void loadDataAsync() {
        progressBar.setVisible(true);
        statusLabel.setText("Lade OP-Hauptliste…");

        boolean empty = opRepository.isCacheEmpty();
        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("OP-Liste: cacheEmpty={}, thread={}", empty, Thread.currentThread().getName());

        Task<List<RowData>> task = new Task<>() {
            @Override
            protected List<RowData> call() throws Exception {
                return opRepository.isCacheEmpty()
                        ? opRepository.loadAndCacheMainList()
                        : opRepository.getMainCache();
            }
        };

        task.setOnSucceeded(e -> {
            progressBar.setVisible(false);
            full = task.getValue();
            if (full == null) full = List.of();
            cur = full;
            statusLabel.setText("Geladen: " + full.size() + " Zeilen.");

            // Afficher dans la table universelle
            tableManager.populateTableView(cur);
            updateCount();
        });

        task.setOnFailed(e -> {
            progressBar.setVisible(false);
            statusLabel.setText("❌ Fehler beim Laden der OP-Liste.");
            Throwable ex = task.getException();
            Dialog.showErrorDialog("Datenfehler", ex != null ? ex.getMessage() : "Unbekannter Fehler");
        });

        new Thread(task, "oplist-viewer-load").start();
    }

    private void updateCount() {
        int n = (cur == null) ? 0 : cur.size();
        countLabel.setText(String.valueOf(n));
    }

    @FXML
    private void openGroupingDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/components/GroupingDialog.fxml"));
            Parent root = loader.load();
            GroupingDialogController ctrl = loader.getController();

            var selectable = java.util.List.of(
                    "Makler","Rg-NR","Policen-Nr","Zeichnungsjahr","Versicherungsnehmer",
                    "Rg-Datum","Fälligkeit","courtage","A.LU_VSTLD"
            );

            // Alimenter le dialog avec l’état courant
            ctrl.init(selectable,
                    cfgHeader,
                    cfgA, cfgB,
                    cfgEnabled);

            Stage stage = new Stage();
            stage.setTitle("Gruppierung konfigurieren");
            stage.setScene(new Scene(root));
            stage.initOwner(resultsContainer.getScene().getWindow());
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            stage.showAndWait();

            var res = ctrl.getResult();
            if (res != null) {
                // 1) mémoriser localement pour la prochaine ouverture
                cfgEnabled = res.enabled;
                cfgHeader  = res.header;
                cfgA       = res.colorA;
                cfgB       = res.colorB;

                // 2) appliquer à la table
                tableManager.applyGroupingConfig(cfgEnabled, cfgHeader, cfgA, cfgB);
            }
        } catch (Exception ex) {
            Dialog.showErrorDialog("Dialogfehler", ex.getMessage());
        }
    }




    @FXML
    private void applyFilter() {
        if (full == null) return;
        String vsn = policyFilterField.getText();
        if (vsn == null || vsn.isBlank()) {
            cur = full;
            tableManager.populateTableView(cur);
            statusLabel.setText("Kein Filter. Zeige alle.");
            updateCount();
            return;
        }
        String key = vsn.trim();

        cur = full.stream()
                .filter(r -> key.equals(r.getValues().get("Policen-Nr")))
                .collect(Collectors.toList());

        tableManager.populateTableView(cur);
        statusLabel.setText("Gefiltert nach Policen-Nr: " + key);
        updateCount();
    }

    @FXML
    private void closeWindow() {
        Stage st = (Stage) resultsContainer.getScene().getWindow();
        st.close();
    }
}
