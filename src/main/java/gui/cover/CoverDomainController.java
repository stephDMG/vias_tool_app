package gui.cover;

import gui.controller.manager.DataLoader;
import gui.controller.manager.EnhancedTableManager;
import gui.controller.manager.TableViewBuilder;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.contract.filters.CoverFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ServiceFactory;
import service.contract.CoverService;
import service.rbac.LoginService;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CoverDomainController {
    private static final Logger log = LoggerFactory.getLogger(CoverDomainController.class);

    @FXML private Label domainTitle, bastandSelectedLabel, messageLabel;
    @FXML private ChoiceBox<String> kernfrageChoice;
    @FXML private ComboBox<String> vertragsstandCombo;
    @FXML private ListView<String> bearbeitungsstandList;
    @FXML private VBox resultsContainer;
    @FXML private HBox parameter, dateBox;
    @FXML private DatePicker abDatePicker, bisDatePicker;
    @FXML private Button ausfuehrenButton;

    // Gruppierung
    @FXML private ListView<String> groupByList;
    @FXML private Label groupBySelectedLabel;
    @FXML private CheckBox groupByAllCheck;

    private EnhancedTableManager tableManager;
    private CoverService coverService;
    private String username;
    private String currentDomain;

    private Map<String, String> dictSta;
    private Map<String, String> dictBastand;

    // Options de Gruppierung (labels affichés dans la ListView)
    private final List<String> groupByOptions = List.of(
            "Cover Art",
            "Makler",
            "Gesellschaft",
            "Versicherungsart",
            "Versicherungssparte",
            "Beteiligungsform",
            "Sachbearbeiter (Vertrag)",
            "Sachbearbeiter (Schaden)"
    );

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    // =====================================================
    // INIT
    // =====================================================
    @FXML
    private void initialize() {
        try {
            coverService = ServiceFactory.getContractService();
            username = new LoginService().getCurrentWindowsUsername();
        } catch (Exception e) {
            log.error("CoverService/LoginService nicht verfügbar", e);
        }
        setupGroupBy();
        setupParams();
        setupTable();
        setupBindings();
        showMessage("Bitte wählen Sie eine Kernfrage aus.");
    }

    public void initDomain(String domain) {
        this.currentDomain = domain;
        domainTitle.setText("COVER – " + domain);
        switch (domain) {
            case "angebotswesen" -> setupKernfragen(List.of(
                    "Unbearbeitete Angebote",
                    "Angenommene Angebote (werden policiert)",
                    "Abgelehnte/Storno Angebote"
            ));
            case "vertragsstatus" -> setupKernfragen(List.of(
                    "Alle aktiven Verträge",
                    "Beendete Verträge"
            ));
            case "kuendigungsfrist" -> setupKernfragen(List.of(
                    "Mit Kündigungsfristverkürzung",
                    "Ohne Kündigungsfristverkürzung"
            ));
            case "viasfelder" -> setupKernfragen(List.of(
                    "Vollständige Vertragsliste",
                    "Nach Sparte gruppiert"
            ));
        }
    }

    // =====================================================
    // GRUPPIERUNG
    // =====================================================
    private void setupGroupBy() {
        groupByList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        groupByList.setItems(FXCollections.observableArrayList(groupByOptions));

        // Par défaut → tout sélectionné (Standard)
        groupByList.getSelectionModel().selectAll();
        groupByAllCheck.setSelected(true);
        updateGroupByLabel();

        // Listener sur la liste
        groupByList.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<String>) c -> updateGroupByLabel()
        );

        // Listener sur la checkbox "Alles"
        groupByAllCheck.selectedProperty().addListener((obs, was, isChecked) -> {
            if (isChecked) {
                groupByList.getSelectionModel().selectAll();
            } else {
                groupByList.getSelectionModel().clearSelection();
            }
            updateGroupByLabel();
        });
    }

    private void updateGroupByLabel() {
        List<String> selected = new ArrayList<>(groupByList.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            groupBySelectedLabel.setText("Ausgewählt: -");
            //groupByAllCheck.setSelected(false);
        } else if (selected.size() == groupByOptions.size()) {
            groupBySelectedLabel.setText("Ausgewählt: Standard (all)");
            //groupByAllCheck.setSelected(true);
        } else {
            groupBySelectedLabel.setText("Ausgewählt: " + String.join(", ", selected));
            //groupByAllCheck.setSelected(false);
        }
    }

    private List<String> getSelectedGroupBy() {
        return new ArrayList<>(groupByList.getSelectionModel().getSelectedItems());
    }

    // =====================================================
    // PARAMS + TABLE
    // =====================================================
    private void setupKernfragen(List<String> kfs) {
        kernfrageChoice.setItems(FXCollections.observableArrayList(kfs));
        kernfrageChoice.getSelectionModel().clearSelection();
        ausfuehrenButton.setDisable(true);
        kernfrageChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            resetParamSelection();
            if (newV == null) {
                showMessage("Bitte wählen Sie eine Kernfrage aus.");
                return;
            }
            domainTitle.setText("COVER – " + currentDomain + " • " + newV);
            parameter.setVisible(true);
            parameter.setManaged(true);
            ausfuehrenButton.setDisable(false);
            runKernfrage();
        });
    }

    private void setupParams() {
        try {
            dictSta = coverService.getDictionary(username, "MAP_ALLE_STA");
            dictBastand = coverService.getDictionary(username, "MAP_ALLE_BASTAND");

            var staItems = dictSta.entrySet().stream()
                    .map(e -> e.getKey() + " - " + e.getValue()).sorted().toList();
            vertragsstandCombo.setItems(FXCollections.observableArrayList(staItems));

            bearbeitungsstandList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            var bastandItems = dictBastand.entrySet().stream()
                    .map(e -> e.getKey() + " - " + e.getValue()).sorted().toList();
            bearbeitungsstandList.setItems(FXCollections.observableArrayList(bastandItems));

            bastandSelectedLabel.setText("Ausgewählt: -");
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
                );
        this.tableManager = builder.buildManager()
                .enableSelection()
                .enableSearch()
                .enableCleanTable();
        resultsContainer.getChildren().setAll(builder.getTableContainer());
    }

    private void setupBindings() {
        // Met à jour le label "Bearbeitungsstand" quand l’utilisateur sélectionne
        bearbeitungsstandList.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<String>) change -> {
                    String selected = String.join(", ",
                            bearbeitungsstandList.getSelectionModel().getSelectedItems());
                    bastandSelectedLabel.setText("Ausgewählt: " + (selected.isEmpty() ? "-" : selected));
                }
        );
    }

    // =====================================================
    // ACTIONS
    // =====================================================
    @FXML
    private void runKernfrage() {
        String selectedKF = kernfrageChoice.getSelectionModel().getSelectedItem();
        if (selectedKF == null) return;

        CoverFilter filter = new CoverFilter();

        filter.setStatus(extractSelectedId(vertragsstandCombo.getSelectionModel().getSelectedItem()));
        filter.setBearbeitungsstandIds(bearbeitungsstandList.getSelectionModel().getSelectedItems()
                .stream().map(this::extractSelectedId).filter(Objects::nonNull).toList());
        if (abDatePicker.getValue() != null) filter.setAbDate(abDatePicker.getValue());
        if (bisDatePicker.getValue() != null) filter.setBisDate(bisDatePicker.getValue());
        filter.setGroupBy(getSelectedGroupBy());

        applyKernfrageDefaults(selectedKF, filter);

        int total = coverService.count(username, filter);
        DataLoader loader = (pageIndex, rowsPerPage) ->
                coverService.searchRaw(username, filter, pageIndex, rowsPerPage).getRows();
        tableManager.loadDataFromServer(total, loader);
        showMessage(null);
    }

    private void resetParamSelection() {
        // Réinitialise les filtres "classiques"
        vertragsstandCombo.getSelectionModel().clearSelection();
        bearbeitungsstandList.getSelectionModel().clearSelection();
        abDatePicker.setValue(null);
        bisDatePicker.setValue(null);
        bastandSelectedLabel.setText("Ausgewählt: -");

        // 👉 Au lieu d’appeler une méthode inexistante (setupGroupByMenu),
        // on remet directement la Gruppierung en "Standard (all)"
        groupByList.getSelectionModel().selectAll();
        groupByAllCheck.setSelected(true);
        updateGroupByLabel();
    }

    private void applyKernfrageDefaults(String kernfrage, CoverFilter filter) {
        resetParamSelection();
        if (kernfrage.contains("Unbearbeitete")) {
            String staId = findIdByText(dictSta, "Angebot");
            filter.setStatus(staId);
            selectComboById(vertragsstandCombo, staId);
            selectListByIds(bearbeitungsstandList, List.of("0", "1"));
            filter.setBearbeitungsstandIds(List.of("0", "1"));
            dateBox.setVisible(false);
            dateBox.setManaged(false);
        } else if (kernfrage.contains("Angenommene")) {
            String staId = findIdByText(dictSta, "Aktiv");
            filter.setStatus(staId);
            selectComboById(vertragsstandCombo, staId);
            selectListByIds(bearbeitungsstandList, List.of("2", "4", "5"));
            filter.setBearbeitungsstandIds(List.of("2", "4", "5"));
            dateBox.setVisible(true);
            dateBox.setManaged(true);
            filter.setAbDate(null);
            filter.setBisDate(null);
        } else if (kernfrage.contains("Abgelehnte")) {
            String staId = findIdByText(dictSta, "Beendet");
            filter.setStatus(staId);
            selectComboById(vertragsstandCombo, staId);
            dateBox.setVisible(true);
            dateBox.setManaged(true);
        }
        // Gruppierung reste en "Standard (all)" sauf si l’utilisateur change manuellement
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private void selectComboById(ComboBox<String> combo, String id) {
        if (id == null) return;
        for (String item : combo.getItems()) {
            if (item.startsWith(id + " ") || item.equals(id)) {
                combo.getSelectionModel().select(item);
                return;
            }
        }
    }

    private void selectListByIds(ListView<String> list, List<String> ids) {
        list.getSelectionModel().clearSelection();
        for (String id : ids) {
            for (String item : list.getItems()) {
                if (item.startsWith(id + " ") || item.equals(id)) {
                    list.getSelectionModel().select(item);
                }
            }
        }
    }

    private void showMessage(String msg) {
        messageLabel.setText(msg == null ? "" : msg);
    }

    @FXML
    private void closeWindow() {
        Stage st = (Stage) resultsContainer.getScene().getWindow();
        st.close();
    }

    private String findIdByText(Map<String,String> dict, String textContains) {
        if (dict == null) return null;
        String needle = textContains.toLowerCase(Locale.ROOT);
        for (var e : dict.entrySet()) {
            if (e.getValue() != null && e.getValue().toLowerCase(Locale.ROOT).contains(needle)) return e.getKey();
        }
        return null;
    }

    private String extractSelectedId(String comboValue) {
        if (comboValue == null) return null;
        int idx = comboValue.indexOf(" - ");
        return (idx > 0) ? comboValue.substring(0, idx) : comboValue;
    }
}
