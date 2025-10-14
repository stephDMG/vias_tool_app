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

public class CoverDomainController {
    private static final Logger log = LoggerFactory.getLogger(CoverDomainController.class);

    // UI
    @FXML private Label domainTitle, bastandSelectedLabel, vertragsstandSelectedLabel, messageLabel;
    @FXML private ChoiceBox<String> kernfrageChoice;
    @FXML private ListView<String> vertragsstandList;
    @FXML private ListView<String> bearbeitungsstandList;
    @FXML private VBox resultsContainer;
    @FXML private HBox parameter, dateBox;
    @FXML private DatePicker abDatePicker, bisDatePicker;
    @FXML private Button ausfuehrenButton;

    // Gruppierung
    @FXML private ListView<String> groupByList;
    @FXML private Label groupBySelectedLabel;
    @FXML private CheckBox groupByAllCheck;

    // Stornogründe
    @FXML private VBox stornoGrundFilterBox;
    @FXML private ListView<String> stornoGrundList;

    private EnhancedTableManager tableManager;
    private CoverService coverService;
    private String username;
    private String currentDomain;

    private Map<String, String> dictSta;
    private Map<String, String> dictBastand;

    // Hardcoded Storno Key→Value; wir senden die VALUES (Labels) an das Backend (LU_AGR speichert Texte)
    private final Map<String, String> hardcodedStornoReasons = Map.ofEntries(
            Map.entry("005", "Ab Beginn aufgehoben"),
            Map.entry("025", "Anteils-/Beteiligungsänderung"),
            Map.entry("035", "Anteilskündigung durch WÜBA"),
            Map.entry("045", "Erloschen (Aufhebung bekannt)"),
            Map.entry("055", "Im gegenseitigen Einvernehmen aufgehoben"),
            Map.entry("065", "Insolvenz des VN"),
            Map.entry("075", "KURZFRISTIGE VERSICHERUNG !!!"),
            Map.entry("085", "Kündig. durch Führende nach Maklerwechsel"),
            Map.entry("095", "Kündigung durch Neumakler"),
            Map.entry("105", "Kündigung im Rahmen der Sanierung"),
            Map.entry("125", "Kündigung im Schadenfall (CS)"),
            Map.entry("135", "Kündigung im Schadenfall (VN)"),
            Map.entry("145", "Kündigung wegen Prämiennichtzahlung"),
            Map.entry("155", "Maklerwechsel"),
            Map.entry("165", "Nichinanspruchnahme"),
            Map.entry("175", "Nicht löschbar, da bereits Prämie gebucht wurde"),
            Map.entry("185", "Ordentliche Kündigung durch CS"),
            Map.entry("195", "Ordentliche Kündigung durch Makler"),
            Map.entry("205", "Ordentliche Kündigung durch VN"),
            Map.entry("215", "Police bereits im Vorjahr erloschen!"),
            Map.entry("225", "Policennummerwechsel"),
            Map.entry("235", "Policennummerzusammenlegung"),
            Map.entry("245", "Risikofall"),
            Map.entry("255", "Rücktritt wegen Nichtzahlung Erstprämie"),
            Map.entry("265", "Sonderkündigung durch Führende"),
            Map.entry("275", "Umdeckung durch Makler"),
            Map.entry("285", "Umdeckung durch VN"),
            Map.entry("295", "Vertrag ist nicht zustande gekommen"),
            Map.entry("305", "Vertrag ruht"),
            Map.entry("315", "Vertrag wurde nicht prolongiert"),
            Map.entry("325", "Zusammenarbeit mit Makler beendet")
    );

    // Gruppierung-Optionen
    private final List<String> groupByOptions = List.of(
            "Cover Art", "Makler", "Gesellschaft", "Versicherungsart",
            "Versicherungssparte", "Beteiligungsform",
            "Sachbearbeiter (Vertrag)", "Sachbearbeiter (Schaden)"
    );

    // Persistenz: Gruppierung pro KF (einfach)
    private final Map<String, List<String>> groupByMemory = new HashMap<>();

    // Ladevorgang
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private volatile boolean dictionariesLoaded = false;
    private String pendingKernfrage = null;

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
        setupParams();          // lädt Dictionaries + füllt Listen
        setupTable();
        setupBindings();
        setupKernfragenChoice(); // nur der Listener, Items werden via initDomain gesetzt
        showMessage("Bitte wählen Sie eine Kernfrage aus.");
    }

    // =====================================================
    // INIT DOMAIN (vom Dashboard aufgerufen)
    // =====================================================
    public void initDomain(String domain) {
        this.currentDomain = domain;

        switch (domain.toLowerCase(Locale.ROOT)) {
            case "angebotswesen" -> {
                domainTitle.setText("COVER – Angebotswesen");
                kernfrageChoice.setItems(FXCollections.observableArrayList(
                        "Unbearbeitete Angebote",
                        "Angenommene Angebote (werden policiert)",
                        "Abgelehnte/Storno Angebote"
                ));
            }
            case "vertragsstatus" -> {
                domainTitle.setText("COVER – Vertragsstatus");
                kernfrageChoice.setItems(FXCollections.observableArrayList(
                        "Alle aktiven Verträge",
                        "Beendete Verträge"
                ));
            }
            case "kuendigungsfrist" -> {
                domainTitle.setText("COVER – Kündigungsfristverkürzung");
                kernfrageChoice.setItems(FXCollections.observableArrayList(
                        "Mit Kündigungsfristverkürzung",
                        "Ohne Kündigungsfristverkürzung"
                ));
            }
            case "viasfelder" -> {
                domainTitle.setText("COVER – Relevante VIAS-Felder");
                kernfrageChoice.setItems(FXCollections.observableArrayList(
                        "Vollständige Vertragsliste",
                        "Nach Sparte gruppiert"
                ));
            }
            default -> {
                domainTitle.setText("COVER – Unbekannte Domain");
                kernfrageChoice.setItems(FXCollections.observableArrayList());
            }
        }

        parameter.setVisible(true);
        parameter.setManaged(true);
        ausfuehrenButton.setDisable(false);

        // Automatisch erste KF wählen → Listener feuert (wenn Dictionaries da sind, sonst pending)
        if (!kernfrageChoice.getItems().isEmpty()) {
            kernfrageChoice.getSelectionModel().selectFirst();
        }
    }

    // =====================================================
    // GROUPING
    // =====================================================
    private void setupGroupBy() {
        groupByList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        groupByList.setItems(FXCollections.observableArrayList(groupByOptions));
        groupByList.getSelectionModel().selectAll();
        groupByAllCheck.setSelected(true);
        updateGroupByLabel();

        groupByList.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<String>) c -> updateGroupByLabel()
        );

        groupByAllCheck.selectedProperty().addListener((obs, was, checked) -> {
            if (checked) groupByList.getSelectionModel().selectAll();
            else groupByList.getSelectionModel().clearSelection();
            updateGroupByLabel();
        });
    }

    private void updateGroupByLabel() {
        List<String> selected = new ArrayList<>(groupByList.getSelectionModel().getSelectedItems());
        if (selected.isEmpty())
            groupBySelectedLabel.setText("Ausgewählt: -");
        else if (selected.size() == groupByOptions.size())
            groupBySelectedLabel.setText("Ausgewählt: Standard (all)");
        else
            groupBySelectedLabel.setText("Ausgewählt: " + String.join(", ", selected));
    }

    // =====================================================
    // PARAMS + TABLE
    // =====================================================
    private void setupParams() {
        EXECUTOR.submit(() -> {
            try {
                dictSta = coverService.getDictionary(username, "MAP_ALLE_STA");
                dictBastand = coverService.getDictionary(username, "MAP_ALLE_BASTAND");

                Platform.runLater(() -> {
                    // Vertragsstand
                    vertragsstandList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                    var staItems = dictSta.entrySet().stream()
                            .map(e -> e.getKey() + " - " + e.getValue())
                            .sorted().toList();
                    vertragsstandList.setItems(FXCollections.observableArrayList(staItems));

                    // Bearbeitungsstand
                    bearbeitungsstandList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                    var bastandItems = dictBastand.entrySet().stream()
                            .map(e -> e.getKey() + " - " + e.getValue())
                            .sorted().toList();
                    bearbeitungsstandList.setItems(FXCollections.observableArrayList(bastandItems));

                    // Stornogründe (Key - Value im UI anzeigen)
                    stornoGrundList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                    var stornoItems = hardcodedStornoReasons.entrySet().stream()
                            .map(e -> e.getKey() + " - " + e.getValue())
                            .sorted()
                            .toList();
                    stornoGrundList.setItems(FXCollections.observableArrayList(stornoItems));

                    dictionariesLoaded = true;

                    // Falls der Nutzer bereits eine KF gewählt hat, Defaults anwenden & ausführen
                    String currentKF = kernfrageChoice.getSelectionModel().getSelectedItem();
                    if (pendingKernfrage != null || currentKF != null) {
                        applyKernfrageDefaults(pendingKernfrage != null ? pendingKernfrage : currentKF);
                        pendingKernfrage = null;
                        Platform.runLater(this::runKernfrage);
                    }
                });

            } catch (Exception ex) {
                log.error("Fehler beim Laden der Dictionaries", ex);
            }
        });
    }

    private void setupTable() {
        TableViewBuilder builder = TableViewBuilder.create()
                .withFeatures(TableViewBuilder.Feature.SELECTION,
                        TableViewBuilder.Feature.PAGINATION,
                        TableViewBuilder.Feature.EXPORT,
                        TableViewBuilder.Feature.SEARCH);
        this.tableManager = builder.buildManager().enableSelection().enableSearch().enableCleanTable();
        resultsContainer.getChildren().setAll(builder.getTableContainer());
    }

    private void setupBindings() {
        bearbeitungsstandList.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<String>) c -> {
                    String selected = String.join(", ", bearbeitungsstandList.getSelectionModel().getSelectedItems());
                    bastandSelectedLabel.setText("Ausgewählt: " + (selected.isEmpty() ? "-" : selected));
                });
        vertragsstandList.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<String>) c -> {
                    String selected = String.join(", ", vertragsstandList.getSelectionModel().getSelectedItems());
                    vertragsstandSelectedLabel.setText("Ausgewählt: " + (selected.isEmpty() ? "-" : selected));
                });
    }

    // =====================================================
    // KERNFRAGEN
    // =====================================================
    private void setupKernfragenChoice() {
        kernfrageChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) return;

            // Gruppierung pro KF wiederherstellen (falls vorhanden), sonst "alle"
            restoreGroupByForKF(newV);

            resetParamSelection();

            // Falls Dictionaries noch nicht bereit sind → merken und später anwenden
            if (!dictionariesLoaded) {
                pendingKernfrage = newV;
                return;
            }

            applyKernfrageDefaults(newV);
            Platform.runLater(this::runKernfrage);
        });
    }

    private void restoreGroupByForKF(String kf) {
        List<String> saved = groupByMemory.get(kf);
        groupByList.getSelectionModel().clearSelection();
        if (saved == null || saved.isEmpty()) {
            groupByList.getSelectionModel().selectAll();
            groupByAllCheck.setSelected(true);
        } else {
            for (String g : saved) {
                if (groupByOptions.contains(g))
                    groupByList.getSelectionModel().select(g);
            }
            groupByAllCheck.setSelected(groupByList.getSelectionModel().getSelectedItems().size() == groupByOptions.size());
        }
        updateGroupByLabel();
    }

    private void applyKernfrageDefaults(String kf) {
        // Sichtbarkeit per KF
        if ("Unbearbeitete Angebote".equals(kf)) {
            selectListByIds(vertragsstandList, nonNullList(findIdByText(dictSta, "Angebot")));
            selectListByIds(bearbeitungsstandList, List.of("0", "1"));
            dateBox.setVisible(false);
            dateBox.setManaged(false);
            stornoGrundFilterBox.setVisible(false);
            stornoGrundFilterBox.setManaged(false);
        } else if ("Angenommene Angebote (werden policiert)".equals(kf)) {
            selectListByIds(vertragsstandList, nonNullList(findIdByText(dictSta, "Aktiv")));
            selectListByIds(bearbeitungsstandList, List.of("2", "4", "5"));
            dateBox.setVisible(true);
            dateBox.setManaged(true);
            stornoGrundFilterBox.setVisible(false);
            stornoGrundFilterBox.setManaged(false);
        } else if ("Abgelehnte/Storno Angebote".equals(kf)) {
            String oId = findIdByText(dictSta, "Angebot abgelehnt");
            String sId = findIdByText(dictSta, "Beendet");
            List<String> ids = new ArrayList<>();
            if (oId != null) ids.add(oId);
            if (sId != null) ids.add(sId);
            selectListByIds(vertragsstandList, ids);
            // Bearbeitungsstand hier optional, keine Defaults
            dateBox.setVisible(true);
            dateBox.setManaged(true);
            stornoGrundFilterBox.setVisible(true);
            stornoGrundFilterBox.setManaged(true);
        } else {
            // andere Domains/KF – defaults defensiv
            dateBox.setVisible(false);
            dateBox.setManaged(false);
            stornoGrundFilterBox.setVisible(false);
            stornoGrundFilterBox.setManaged(false);
        }
    }

    // =====================================================
    // AUSFÜHREN
    // =====================================================
    @FXML
    private void runKernfrage() {
        String selectedKF = kernfrageChoice.getSelectionModel().getSelectedItem();
        if (selectedKF == null) return;

        // Gruppierung für diese KF merken
        groupByMemory.put(selectedKF, new ArrayList<>(groupByList.getSelectionModel().getSelectedItems()));

        CoverFilter filter = new CoverFilter();

        // Vertragsstand → IDs (LU_STA)
        List<String> staIds = vertragsstandList.getSelectionModel().getSelectedItems().stream()
                .map(this::extractSelectedId)
                .filter(Objects::nonNull)
                .toList();
        filter.setContractStatusList(staIds.isEmpty() ? null : staIds);

        // Bearbeitungsstand → IDs (LU_BASTAND)
        filter.setBearbeitungsstandIds(bearbeitungsstandList.getSelectionModel().getSelectedItems()
                .stream().map(this::extractSelectedId).filter(Objects::nonNull).toList());

        // Stornogründe → VALUES (Labels!)
        List<String> stornoValues = stornoGrundList.getSelectionModel().getSelectedItems().stream()
                .map(this::extractSelectedLabel)
                .filter(Objects::nonNull)
                .toList();
        filter.setStornoGrundIds(stornoValues.isEmpty() ? null : stornoValues);

        // Datum
        if (abDatePicker.getValue() != null) filter.setAbDate(abDatePicker.getValue());
        if (bisDatePicker.getValue() != null) filter.setBisDate(bisDatePicker.getValue());

        // Gruppierung (null = alles)
        List<String> selectedGroupBys = new ArrayList<>(groupByList.getSelectionModel().getSelectedItems());
        filter.setGroupBy(selectedGroupBys.size() == groupByOptions.size() ? null : selectedGroupBys);

        EXECUTOR.submit(() -> {
            int total = coverService.count(username, filter);
            DataLoader loader = (page, size) -> coverService.searchRaw(username, filter, page, size).getRows();
            Platform.runLater(() -> {
                tableManager.loadDataFromServer(total, loader);
                showMessage(null);
            });
        });
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private void resetParamSelection() {
        if (vertragsstandList != null) vertragsstandList.getSelectionModel().clearSelection();
        if (bearbeitungsstandList != null) bearbeitungsstandList.getSelectionModel().clearSelection();
        if (stornoGrundList != null) stornoGrundList.getSelectionModel().clearSelection();
        if (abDatePicker != null) abDatePicker.setValue(null);
        if (bisDatePicker != null) bisDatePicker.setValue(null);
        if (bastandSelectedLabel != null) bastandSelectedLabel.setText("Ausgewählt: -");
        if (vertragsstandSelectedLabel != null) vertragsstandSelectedLabel.setText("Ausgewählt: -");
    }

    private void selectListByIds(ListView<String> list, List<String> ids) {
        if (list == null || ids == null || ids.isEmpty()) return;
        list.getSelectionModel().clearSelection();
        for (String id : ids) {
            if (id == null) continue;
            for (String item : list.getItems()) {
                if (item != null && (item.startsWith(id + " ") || item.equals(id)))
                    list.getSelectionModel().select(item);
            }
        }
    }

    private List<String> nonNullList(String singleId) {
        return (singleId == null) ? List.of() : List.of(singleId);
    }

    private String findIdByText(Map<String, String> dict, String textContains) {
        if (dict == null || textContains == null) return null;
        String needle = textContains.toLowerCase(Locale.ROOT);
        for (var e : dict.entrySet()) {
            if (e.getValue() != null && e.getValue().toLowerCase(Locale.ROOT).contains(needle))
                return e.getKey();
        }
        return null;
    }

    private String extractSelectedId(String value) {
        if (value == null) return null;
        int idx = value.indexOf(" - ");
        return (idx > 0) ? value.substring(0, idx) : value;
    }

    private String extractSelectedLabel(String value) {
        if (value == null) return null;
        int idx = value.indexOf(" - ");
        return (idx > 0 && idx < value.length() - 3) ? value.substring(idx + 3) : value;
    }

    private void showMessage(String msg) {
        messageLabel.setText(msg == null ? "" : msg);
    }

    @FXML
    private void closeWindow() {
        Stage st = (Stage) resultsContainer.getScene().getWindow();
        st.close();
    }
}
