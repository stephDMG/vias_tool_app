package gui.cover;

import gui.controller.manager.DataLoader;
import gui.controller.manager.EnhancedTableManager;
import gui.controller.manager.TableViewBuilder;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.RowData;
import model.contract.filters.CoverFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ServiceFactory;
import service.contract.CoverService;
import service.rbac.LoginService;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class CoverAngebotswesenController {
    private static final Logger log = LoggerFactory.getLogger(CoverAngebotswesenController.class);

    @FXML public HBox parameter;

    @FXML private ChoiceBox<String> kernfrageChoice;
    @FXML private ComboBox<String> vertragsstandCombo;
    @FXML private ListView<String> bearbeitungsstandList;
    @FXML private Label bastandSelectedLabel;
    @FXML private VBox resultsContainer;
    @FXML private HBox dateBox;
    @FXML private DatePicker abDatePicker;
    @FXML private DatePicker bisDatePicker;
    @FXML private Button ausfuehrenButton;

    private EnhancedTableManager tableManager;
    private CoverService coverService;
    private String username;

    private Map<String, String> dictSta;
    private Map<String, String> dictBastand;

    private Button exportCsvButton;
    private Button exportXlsxButton;

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private static final String KF_START = "Wählen Sie ihre Kernfragen...";
    private static final String KF_UNBEARBEITET = "Unbearbeitete Angebote";
    private static final String KF_ANNAHME = "Angenommene Angebote (werden policiert)";
    private static final String KF_ABGELEHNT = "Abgelehnte/Storno Angebote";

    @FXML
    private void initialize() {
        try {
            coverService = ServiceFactory.getContractService();
            username = new LoginService().getCurrentWindowsUsername();
        } catch (Exception e) {
            log.error("CoverService/LoginService nicht verfügbar", e);
        }

        setupKernfragenChoice();
        setupParams();
        setupTable();
        setupBindings();
    }

    private void setupKernfragenChoice() {
        kernfrageChoice.setItems(FXCollections.observableArrayList(
                KF_START,
                KF_UNBEARBEITET,
                KF_ANNAHME,
                KF_ABGELEHNT
        ));
        kernfrageChoice.getSelectionModel().selectFirst();

        kernfrageChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            resetParamSelection();

            if (newV == null || KF_START.equals(newV)) {
                parameter.setVisible(false);
                ausfuehrenButton.setDisable(true);
                vertragsstandCombo.setDisable(true);
                bearbeitungsstandList.setDisable(true);
                dateBox.setVisible(false);
                dateBox.setManaged(false);
                return;
            }

            ausfuehrenButton.setDisable(false);
            vertragsstandCombo.setDisable(false);
            bearbeitungsstandList.setDisable(false);
            parameter.setVisible(true);

            if (KF_UNBEARBEITET.equals(newV)) {
                selectComboById(vertragsstandCombo, findIdByText(dictSta, "Angebot"));
                selectListByIds(bearbeitungsstandList, List.of("0", "1"));
                dateBox.setVisible(false);
                dateBox.setManaged(false);
                runKernfrage();
            }
            else if (KF_ANNAHME.equals(newV)) {
                selectComboById(vertragsstandCombo, findIdByText(dictSta, "Aktiv"));
                selectListByIds(bearbeitungsstandList, List.of("2", "4", "5"));
                dateBox.setVisible(true);
                dateBox.setManaged(true);
                runKernfrage();
            }
            else if (KF_ABGELEHNT.equals(newV)) {
                selectComboById(vertragsstandCombo, findIdByText(dictSta, "Angebot abgelehnt"));
                bearbeitungsstandList.getSelectionModel().clearSelection();
                dateBox.setVisible(true);
                dateBox.setManaged(true);
                runKernfrage();
            }
        });
    }

    private void setupParams() {
        try {
            dictSta = coverService.getDictionary(username, "MAP_ALLE_STA");
            dictBastand = coverService.getDictionary(username, "MAP_ALLE_BASTAND");

            var staItems = dictSta.entrySet().stream()
                    .map(e -> e.getKey() + " - " + e.getValue())
                    .sorted()
                    .collect(Collectors.toList());
            vertragsstandCombo.setItems(FXCollections.observableArrayList(staItems));

            bearbeitungsstandList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            var bastandItems = dictBastand.entrySet().stream()
                    .map(e -> e.getKey() + " - " + e.getValue())
                    .sorted()
                    .collect(Collectors.toList());
            bearbeitungsstandList.setItems(FXCollections.observableArrayList(bastandItems));

        } catch (Exception ex) {
            log.error("Fehler beim Laden der Dictionaries", ex);
        }
    }

    private void setupTable() {
        TableViewBuilder builder = TableViewBuilder.create()
                .withFeatures(
                        TableViewBuilder.Feature.SELECTION,
                        TableViewBuilder.Feature.PAGINATION,
                        TableViewBuilder.Feature.EXPORT,
                        TableViewBuilder.Feature.SEARCH
                )
                .withExportLabel("Als Datei exportieren:")
                .withActionsLabel("Ausgewählte Spalten löschen:");

        this.tableManager = builder.buildManager()
                .enableSelection()
                .enableSearch()
                .enableCleanTable();

        this.exportCsvButton = builder.getExportCsvButton();
        this.exportXlsxButton = builder.getExportXlsxButton();
        Button deleteButton = builder.getDeleteColumnsButton();
        deleteButton.setOnAction(e -> {
            System.out.println("Delete button clicked - calling tableManager");
            tableManager.handleDeleteSelectedColumns();
        });

        // Event-Handler für Export-Buttons
        //exportCsvButton.setOnAction(this::exportFullReport);
        //exportXlsxButton.setOnAction(this::exportFullReport);

        // Tabellen-Container in die UI einbinden
        resultsContainer.getChildren().clear();
        resultsContainer.getChildren().add(builder.getTableContainer());

        // resultsContainer.getChildren().setAll(builder.getTableContainer());
    }




    private void setupBindings() {
        bearbeitungsstandList.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<String>) change -> {
                    String selected = String.join(", ", bearbeitungsstandList.getSelectionModel().getSelectedItems());
                    bastandSelectedLabel.setText("Ausgewählt: " + (selected.isEmpty() ? "-" : selected));
                }
        );
    }


    @FXML
    private void runKernfrage() {
        String selectedKF = kernfrageChoice.getSelectionModel().getSelectedItem();
        if (selectedKF == null || KF_START.equals(selectedKF)) return;

        CoverFilter filter = new CoverFilter();
        String staId = extractSelectedId(vertragsstandCombo.getSelectionModel().getSelectedItem());
        List<String> bastandIds = bearbeitungsstandList.getSelectionModel().getSelectedItems()
                .stream().map(this::extractSelectedId).filter(Objects::nonNull).collect(Collectors.toList());

        if (KF_UNBEARBEITET.equals(selectedKF)) {
            if (staId == null) staId = findIdByText(dictSta, "Angebot");
            filter.setStatus(staId);
            if (bastandIds.isEmpty()) bastandIds = List.of("0", "1");
        }
        else if (KF_ANNAHME.equals(selectedKF)) {
            staId = findIdByText(dictSta, "Aktiv");
            filter.setStatus(staId);
            bastandIds = List.of("2", "4", "5");

            if (abDatePicker.getValue() != null) filter.setAbDate(abDatePicker.getValue());
            if (bisDatePicker.getValue() != null) filter.setBisDate(bisDatePicker.getValue());
        }
        else if (KF_ABGELEHNT.equals(selectedKF)) {
            String oId = findIdByText(dictSta, "Angebot abgelehnt");
            String sId = findIdByText(dictSta, "Beendet/Storno");

            List<String> finalBastandIds = new ArrayList<>(bastandIds);

            EXECUTOR.submit(() -> {
                List<RowData> combined = new ArrayList<>();
                if (oId != null) {
                    CoverFilter f1 = new CoverFilter();
                    f1.setStatus(oId);
                    f1.setBearbeitungsstandIds(finalBastandIds);
                    combined.addAll(coverService.searchRaw(username, f1, 0, 1000).getRows());
                }
                if (sId != null) {
                    CoverFilter f2 = new CoverFilter();
                    f2.setStatus(sId);
                    f2.setBearbeitungsstandIds(finalBastandIds);
                    combined.addAll(coverService.searchRaw(username, f2, 0, 1000).getRows());
                }

                if (abDatePicker.getValue() != null) filter.setAbDate(abDatePicker.getValue());
                if (bisDatePicker.getValue() != null) filter.setBisDate(bisDatePicker.getValue());

                Platform.runLater(() -> tableManager.populateTableView(combined));
            });
            return;
        }

        filter.setBearbeitungsstandIds(bastandIds);

        int total = coverService.count(username, filter);
        DataLoader loader = (pageIndex, rowsPerPage) ->
                coverService.searchRaw(username, filter, pageIndex, rowsPerPage).getRows();

        tableManager.loadDataFromServer(total, loader);
    }

    private void resetParamSelection() {
        vertragsstandCombo.getSelectionModel().clearSelection();
        bearbeitungsstandList.getSelectionModel().clearSelection();
        abDatePicker.setValue(null);
        bisDatePicker.setValue(null);
        bastandSelectedLabel.setText("Ausgewählt: -");
    }

    @FXML
    private void closeWindow() {
        Stage st = (Stage) resultsContainer.getScene().getWindow();
        st.close();
    }

    // ======= Helpers =======
    private String findIdByText(Map<String,String> dict, String textContains) {
        if (dict == null) return null;
        String needle = textContains == null ? "" : textContains.toLowerCase(Locale.ROOT);
        for (var e : dict.entrySet()) {
            if (e.getValue() != null && e.getValue().toLowerCase(Locale.ROOT).contains(needle)) {
                return e.getKey();
            }
        }
        for (var e : dict.entrySet()) {
            if (e.getKey() != null && e.getKey().toLowerCase(Locale.ROOT).contains(needle)) {
                return e.getKey();
            }
        }
        return null;
    }

    private void selectComboById(ComboBox<String> combo, String id) {
        if (id == null) return;
        for (String s : combo.getItems()) {
            if (s != null && s.startsWith(id + " ")) {
                combo.getSelectionModel().select(s);
                return;
            }
            if (s != null && s.equals(id)) {
                combo.getSelectionModel().select(s);
                return;
            }
        }
    }

    private void selectListByIds(ListView<String> list, List<String> ids) {
        for (String id : ids) {
            for (String item : list.getItems()) {
                if (item != null && (item.equals(id) || item.startsWith(id + " "))) {
                    list.getSelectionModel().select(item);
                }
            }
        }
    }

    private String extractSelectedId(String comboValue) {
        if (comboValue == null) return null;
        int idx = comboValue.indexOf(" - ");
        return (idx > 0) ? comboValue.substring(0, idx) : comboValue;
    }
}
