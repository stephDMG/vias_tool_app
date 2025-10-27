package gui.cover;

import gui.controller.manager.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent; // <-- import pour @FXML handler export
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import model.RowData;
import model.contract.filters.CoverFilter;
import model.enums.ExportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.ServiceFactory;
import service.contract.CoverService;
import service.rbac.LoginService;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static gui.controller.utils.format.FormatterService.exportWithFormat;

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

    @FXML private StackPane resultsStack;
    @FXML private VBox tableHost, treeHost;
    @FXML private ToggleButton toggleTreeView;
    @FXML private ToggleButton toggleVersionView;

    private EnhancedTableManager tableManager;
    private TreeTableManager treeManager;
    private TreeTableViewBuilder treeBuilder;

    private CoverService coverService;
    private String username;
    private String currentDomain;
    private ProgressIndicator busy;

    private Map<String, String> dictSta;
    private Map<String, String> dictBastand;

    // Export-Buttons (fournis par le TableViewBuilder)
    private Button exportCsvButton;
    private Button exportXlsxButton;

    // tout en haut de CoverDomainController
    private boolean isSearchMode = false;


    // Storno Key→Value (hardcoded)
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
            "Sachbearbeiter (Vertrag)", "Sachbearbeiter (Schaden)",
            "Versicherungsschein Nr","Versicherungsnehmer"
    );

    // Busy overlay
    private void initBusyOverlay() {
        busy = new ProgressIndicator();
        busy.setMaxSize(90, 90);
        busy.setVisible(false);
        resultsStack.getChildren().add(busy);
        StackPane.setAlignment(busy, Pos.CENTER);
    }
    private void showBusy() { if (busy != null) busy.setVisible(true); }
    private void hideBusy() { if (busy != null) busy.setVisible(false); }

    // Persistenz: Gruppierung pro KF
    private final Map<String, List<String>> groupByMemory = new HashMap<>();

    // Ladevorgang
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private volatile boolean dictionariesLoaded = false;
    private String pendingKernfrage = null;

    // Formats pour parsing DatePicker (déjà utilisés côté installDatePickerConverter)
    private final DateTimeFormatter displayFormatter =
            DateTimeFormatter.ofPattern("dd.MM.yyyy").withResolverStyle(ResolverStyle.STRICT);

    // =========================
    // INIT
    // =========================
    @FXML
    private void initialize() {
        try {
            coverService = ServiceFactory.getContractService();
            username = new LoginService().getCurrentWindowsUsername();
        } catch (Exception e) {
            log.error("CoverService/LoginService nicht verfügbar", e);
        }

        // Busy overlay (une seule fois)
        initBusyOverlay();
        showBusy();

        setupGroupBy();
        setupParams();
        setupTable();
        setupTree();

        // 🧠 Synchroniser les champs de recherche Table <-> Tree
        if (tableManager != null && treeManager != null &&
                tableManager.getSearchField() != null && treeManager.getSearchField() != null) {

            TextField tableSearch = tableManager.getSearchField();
            TextField treeSearch  = treeManager.getSearchField();

            treeSearch.textProperty().bindBidirectional(tableSearch.textProperty());
        }



        // Toggle "Baumansicht" + pastille
        installToggleWithDot(toggleTreeView, "Baumansicht");

        // Toggle "Mit/Ohne Version" + pastille
        if (toggleVersionView != null) {
            installToggleWithDot(toggleVersionView, "Ohne Version");
            toggleVersionView.selectedProperty().addListener((obs, oldV, newV) -> {
                toggleVersionView.setText(newV ? "Mit Versionen" : "Ohne Version");
                Platform.runLater(this::runKernfrage);
            });
        }

        setupBindings();
        setupKernfragenChoice();
        showMessage("Bitte wählen Sie eine Kernfrage aus.");
    }

    /** Appelée depuis le Dashboard après le chargement du FXML. */
    public void initDomain(String domain) {
        this.currentDomain = domain == null ? "" : domain;

        switch (this.currentDomain.toLowerCase(Locale.ROOT)) {
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

        if (!kernfrageChoice.getItems().isEmpty()) {
            kernfrageChoice.getSelectionModel().selectFirst();
        }

        String kf = kernfrageChoice.getSelectionModel().getSelectedItem();
        if (dictionariesLoaded) {
            if (kf != null) {
                applyKernfrageDefaults(kf);
                Platform.runLater(this::runKernfrage);
            }
        } else {
            pendingKernfrage = kf;
        }
    }


    // =========================
    // GROUPING
    // =========================
    private void setupGroupBy() {
        groupByList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        groupByList.setItems(FXCollections.observableArrayList(groupByOptions));
        groupByList.getSelectionModel().selectAll();
        groupByAllCheck.setSelected(true);
        updateGroupByLabel();

        groupByList.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<String>) c -> {
                    updateGroupByLabel();
                    // 🆕 éviter UnsupportedOperationException
                    Platform.runLater(() -> {
                        boolean allSelected = groupByList.getSelectionModel().getSelectedItems().size() == groupByOptions.size();
                        groupByAllCheck.setSelected(allSelected);
                    });
                }
        );

        groupByAllCheck.selectedProperty().addListener((obs, was, checked) -> {
            Platform.runLater(() -> {
                if (checked) groupByList.getSelectionModel().selectAll();
                else groupByList.getSelectionModel().clearSelection();
                updateGroupByLabel();
            });
        });

        makeListViewReorderable(groupByList); // drag & drop d’ordre
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

    // =========================
    // PARAMS + TABLE
    // =========================
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

                    // Stornogründe
                    stornoGrundList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                    var stornoItems = hardcodedStornoReasons.entrySet().stream()
                            .map(e -> e.getKey() + " - " + e.getValue())
                            .sorted()
                            .toList();
                    stornoGrundList.setItems(FXCollections.observableArrayList(stornoItems));

                    // DatePicker converter (multi-format) — ne touche pas aux promptText bindés
                    Locale uiLocale = Locale.getDefault();
                    installDatePickerConverter(abDatePicker, uiLocale);
                    installDatePickerConverter(bisDatePicker, uiLocale);

                    dictionariesLoaded = true;

                    String currentKF = kernfrageChoice.getSelectionModel().getSelectedItem();
                    if (pendingKernfrage != null || currentKF != null) {
                        applyKernfrageDefaults(pendingKernfrage != null ? pendingKernfrage : currentKF);
                        pendingKernfrage = null;
                        Platform.runLater(this::runKernfrage);
                    } else {
                        hideBusy();
                    }
                });

            } catch (Exception ex) {
                log.error("Fehler beim Laden der Dictionaries", ex);
                Platform.runLater(this::hideBusy);
            }
        });
    }

    // =========================
    // TREE
    // =========================
    private void setupTree() {
        treeBuilder = TreeTableViewBuilder.create()
                .withFeatures(
                        TreeTableViewBuilder.Feature.SEARCH,
                        TreeTableViewBuilder.Feature.SELECTION, // ✅ affichage Bereinigen
                        TreeTableViewBuilder.Feature.PAGINATION,
                        TreeTableViewBuilder.Feature.EXPORT
                )
                .withExportLabel("Vollständigen Bericht exportieren als:");

        treeHost.getChildren().setAll(treeBuilder.getTreeContainer());

        treeManager = treeBuilder.buildManager();

        treeManager.setCleanButton(treeBuilder.getCleanButton()); // ⚙️ void → séparé
        treeManager.enableSearch();
        treeManager.enableSelection();
        treeManager.enablePagination(100);
        treeManager.enableCleanTable();


        // 🧩 relier les exports du TreeTable au contrôleur
        treeManager.setOnExportCsv(() -> exportFullReport(ExportFormat.CSV));
        treeManager.setOnExportXlsx(() -> exportFullReport(ExportFormat.XLSX));

        toggleTreeView.selectedProperty().addListener((obs, wasTree, isTree) -> {
            treeHost.setVisible(isTree);
            treeHost.setManaged(isTree);
            tableHost.setVisible(!isTree);
            tableHost.setManaged(!isTree);
        });

        // Recherche serveur
        treeManager.setOnServerSearch(query -> {
            EXECUTOR.submit(() -> {
                try {
                    CoverFilter filter = new CoverFilter();
                    if (toggleVersionView != null)
                        filter.setWithVersion(toggleVersionView.isSelected());
                    filter.setSearchTerm(query == null || query.isBlank() ? null : query.trim());

                    int total = coverService.count(username, filter);

                    Platform.runLater(() -> {
                        messageLabel.setText("Baumansicht-Suche läuft...");
                        treeManager.loadDataFromServer(total, (page, size) -> {
                            List<RowData> rows = coverService.searchRaw(username, filter, page, size).getRows();
                            if (page == 0) {
                                Platform.runLater(() ->
                                        messageLabel.setText("(" + rows.size() + " Ergebnis" + (rows.size() != 1 ? "se" : "") + " – Suche aktiv)"));
                            }
                            return rows;
                        });
                    });

                } catch (Exception e) {
                    log.error("Server-Suche (Tree) fehlgeschlagen", e);
                }
            });
        });
    }


    private void setupTable() {
        TableViewBuilder builder = TableViewBuilder.create()
                .withFeatures(
                        TableViewBuilder.Feature.SELECTION,
                        TableViewBuilder.Feature.PAGINATION,
                        TableViewBuilder.Feature.EXPORT,
                        TableViewBuilder.Feature.SEARCH
                )
                .withExportLabel("Vollständigen Bericht exportieren als:");

        tableManager = builder.buildManager()
                .enableSearch()
                .enableSelection()
                .enableCleanTable();

        // --- Recherche serveur : relie le champ de recherche à la requête CoverService ---
        tableManager.setOnServerSearch(query -> {
            EXECUTOR.submit(() -> {
                try {
                    Platform.runLater(this::showBusy); // 🆕 indique chargement
                    boolean emptyQuery = (query == null || query.isBlank());
                    isSearchMode = !emptyQuery; // 🆕

                    CoverFilter filter = new CoverFilter();
                    if (toggleVersionView != null) filter.setWithVersion(toggleVersionView.isSelected());

                    List<String> staIds = vertragsstandList.getSelectionModel().getSelectedItems()
                            .stream().map(this::extractSelectedId).filter(Objects::nonNull).toList();
                    filter.setContractStatusList(staIds.isEmpty() ? null : staIds);
                    filter.setBearbeitungsstandIds(bearbeitungsstandList.getSelectionModel().getSelectedItems()
                            .stream().map(this::extractSelectedId).filter(Objects::nonNull).toList());

                    // 🆕 Si vide → revenir à la dernière Kernfrage
                    if (emptyQuery) {
                        Platform.runLater(this::runKernfrage);
                        return;
                    }

                    filter.setSearchTerm(query.trim());
                    int total = coverService.count(username, filter);
                    DataLoader loader = (page, size) -> coverService.searchRaw(username, filter, page, size).getRows();

                    Platform.runLater(() -> {
                        messageLabel.setText("Suche läuft...");
                        tableManager.loadDataFromServer(total, (page, size) -> {
                            List<RowData> rows = coverService.searchRaw(username, filter, page, size).getRows();
                            if (page == 0) { // 🧩 dès la première page, mettre à jour le message
                                Platform.runLater(() -> {
                                    messageLabel.setText("(~ " + rows.size() + " Ergebnis" + (rows.size() != 1 ? "se" : "") + " – Suche aktiv)");
                                });
                            }
                            return rows;
                        });
                        hideBusy();
                    });

                } catch (Exception e) {
                    log.error("Server-Suche fehlgeschlagen", e);
                    Platform.runLater(this::hideBusy);
                }
            });
        });


        tableHost.getChildren().setAll(builder.getTableContainer());

        // Récupère les boutons d’export depuis le builder
        exportCsvButton  = builder.getExportCsvButton();
        exportXlsxButton = builder.getExportXlsxButton();

        if (exportCsvButton != null && exportXlsxButton != null) {
            exportCsvButton.disableProperty().unbind();
            exportXlsxButton.disableProperty().unbind();

            exportCsvButton.disableProperty().bind(
                    tableManager.hasDataProperty().not().or(busy.visibleProperty())
            );
            exportXlsxButton.disableProperty().bind(
                    tableManager.hasDataProperty().not().or(busy.visibleProperty())
            );

            // On relie aux handlers @FXML compatibles FXML/DbExport
            exportCsvButton.setOnAction(this::exportFullReport);
            exportXlsxButton.setOnAction(this::exportFullReport);
        } else {
            log.warn("Export buttons not available from TableViewBuilder (EXPORT feature missing?)");
        }
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

    private void setupKernfragenChoice() {
        kernfrageChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) return;

            restoreGroupByForKF(newV);
            resetParamSelection();

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
        if ("Unbearbeitete Angebote".equals(kf)) {
            selectListByIds(vertragsstandList, nonNullList(findIdByText(dictSta, "Angebot")));
            selectListByIds(bearbeitungsstandList, List.of("0", "1"));
            dateBox.setVisible(false); dateBox.setManaged(false);
            stornoGrundFilterBox.setVisible(false); stornoGrundFilterBox.setManaged(false);
        } else if ("Angenommene Angebote (werden policiert)".equals(kf)) {
            selectListByIds(vertragsstandList, nonNullList(findIdByText(dictSta, "Aktiv")));
            selectListByIds(bearbeitungsstandList, List.of("2", "4", "5"));
            dateBox.setVisible(true); dateBox.setManaged(true);
            stornoGrundFilterBox.setVisible(false); stornoGrundFilterBox.setManaged(false);
        } else if ("Abgelehnte/Storno Angebote".equals(kf)) {
            String oId = findIdByText(dictSta, "Angebot abgelehnt");
            String sId = findIdByText(dictSta, "Beendet");
            List<String> ids = new ArrayList<>();
            if (oId != null) ids.add(oId);
            if (sId != null) ids.add(sId);
            selectListByIds(vertragsstandList, ids);
            dateBox.setVisible(true); dateBox.setManaged(true);
            stornoGrundFilterBox.setVisible(true); stornoGrundFilterBox.setManaged(true);
        } else {
            dateBox.setVisible(false); dateBox.setManaged(false);
            stornoGrundFilterBox.setVisible(false); stornoGrundFilterBox.setManaged(false);
        }
    }

    // =========================
    // AUSFÜHREN
    // =========================
    @FXML
    private void runKernfrage() {
        showBusy();

        CoverFilter filter = new CoverFilter();
        String selectedKF = kernfrageChoice.getSelectionModel().getSelectedItem();
        if (selectedKF == null) { hideBusy(); return; }

        if (toggleVersionView != null) {
            Boolean flag = toggleVersionView.isSelected();
            filter.setWithVersion(flag);
            log.info("runKernfrage -> toggle selected={}, filter.getWithVersion()={}",
                    flag, filter.getWithVersion());
        }

        groupByMemory.put(selectedKF, new ArrayList<>(groupByList.getSelectionModel().getSelectedItems()));

        // Vertragsstand
        List<String> staIds = vertragsstandList.getSelectionModel().getSelectedItems().stream()
                .map(this::extractSelectedId)
                .filter(Objects::nonNull)
                .toList();
        filter.setContractStatusList(staIds.isEmpty() ? null : staIds);

        // Bearbeitungsstand
        filter.setBearbeitungsstandIds(bearbeitungsstandList.getSelectionModel().getSelectedItems()
                .stream().map(this::extractSelectedId).filter(Objects::nonNull).toList());

        // Stornogründe (valeurs)
        List<String> stornoValues = stornoGrundList.getSelectionModel().getSelectedItems().stream()
                .map(this::extractSelectedLabel)
                .filter(Objects::nonNull)
                .toList();
        filter.setStornoGrundIds(stornoValues.isEmpty() ? null : stornoValues);

        // Datum
        if (abDatePicker.getValue() != null) filter.setAbDate(abDatePicker.getValue());
        if (bisDatePicker.getValue() != null) filter.setBisDate(bisDatePicker.getValue());

        // Gruppierung
        List<String> selectedGroupBys = new ArrayList<>(groupByList.getSelectionModel().getSelectedItems());
        filter.setGroupBy(selectedGroupBys.size() == groupByOptions.size() ? null : selectedGroupBys);

        EXECUTOR.submit(() -> {
            try {
                int total = coverService.count(username, filter);
                DataLoader loader = (page, size) -> coverService.searchRaw(username, filter, page, size).getRows();

                Platform.runLater(() -> {
                    tableManager.loadDataFromServer(total, loader);
                    showMessage(null);

                    // snapshot pour le Tree
                    List<String> groupSnapshot = new ArrayList<>(groupByList.getSelectionModel().getSelectedItems());

                    java.util.function.Function<RowData, List<String>> pathProvider = row -> {
                        Map<String, String> v = row.getValues();
                        List<String> path = new ArrayList<>();
                        for (String g : groupSnapshot) {
                            switch (g) {
                                case "Versicherungsschein Nr" -> path.add(v.getOrDefault("Versicherungsschein_Nr", ""));
                                case "Versicherungsnehmer" -> path.add(v.getOrDefault("Versicherungsnehmer_Name", ""));
                                case "Makler" -> path.add(v.getOrDefault("Makler", ""));
                                case "Gesellschaft" -> path.add(v.getOrDefault("Gesellschaft_Name", ""));
                                case "Versicherungsart" -> path.add(v.getOrDefault("Versicherungsart_Text", ""));
                                case "Beteiligungsform" -> path.add(v.getOrDefault("Beteiligungsform_Text", ""));
                                case "Sachbearbeiter (Vertrag)" -> path.add(v.getOrDefault("SB_Vertr", ""));
                                case "Sachbearbeiter (Schaden)" -> path.add(v.getOrDefault("SB_Schad", ""));
                                case "Cover Art" -> path.add(v.getOrDefault("Vertragsparte_Text", ""));
                                case "Versicherungssparte" -> path.add("COVER");
                                default -> {}
                            }
                        }
                        if (path.isEmpty()) path.add("Alle");
                        return path;
                    };

                    treeManager.loadDataFromServer(total, loader, pathProvider);
                });
            } catch (Exception ex) {
                log.error("runKernfrage() fehlgeschlagen", ex);
            } finally {
                Platform.runLater(() -> {
                    PauseTransition pt = new PauseTransition(Duration.millis(220));
                    isSearchMode = false;
                    pt.setOnFinished(ev -> hideBusy());
                    pt.play();
                });
            }
        });
    }

    // =========================
    // EXPORT
    // =========================

    /** Handler compatible FXML/DbExport : déduit le format depuis le bouton source. */
    @FXML
    private void exportFullReport(ActionEvent event) {
        Object src = event.getSource();
        ExportFormat fmt = ExportFormat.CSV;
        if (src instanceof Button b) {
            String txt = (b.getText() == null ? "" : b.getText().toLowerCase(Locale.ROOT));
            if (txt.contains("xlsx") || txt.contains("excel")) fmt = ExportFormat.XLSX;
            else if (txt.contains("csv")) fmt = ExportFormat.CSV;
        }
        exportFullReport(fmt);
    }

    /** Implémentation réelle : charge toutes les pages côté serveur et exporte. */
    private void exportFullReport(ExportFormat format) {
        try {
            CoverFilter filter = new CoverFilter();
            if (toggleVersionView != null) filter.setWithVersion(toggleVersionView.isSelected());

            List<String> staIds = vertragsstandList.getSelectionModel().getSelectedItems().stream()
                    .map(this::extractSelectedId).filter(Objects::nonNull).toList();
            filter.setContractStatusList(staIds.isEmpty() ? null : staIds);

            filter.setBearbeitungsstandIds(bearbeitungsstandList.getSelectionModel().getSelectedItems()
                    .stream().map(this::extractSelectedId).filter(Objects::nonNull).toList());

            List<String> stornoValues = stornoGrundList.getSelectionModel().getSelectedItems().stream()
                    .map(this::extractSelectedLabel).filter(Objects::nonNull).toList();
            filter.setStornoGrundIds(stornoValues.isEmpty() ? null : stornoValues);

            if (abDatePicker.getValue() != null) filter.setAbDate(abDatePicker.getValue());
            if (bisDatePicker.getValue() != null) filter.setBisDate(bisDatePicker.getValue());

            int total = coverService.count(username, filter);
            if (total <= 0) {
                new Alert(Alert.AlertType.INFORMATION, "Keine Daten zum Exportieren.", ButtonType.OK).showAndWait();
                return;
            }

            final int pageSize = 1000;
            List<RowData> all = new ArrayList<>(Math.min(total, 20000));
            int pages = (int) Math.ceil(total / (double) pageSize);

            showBusy();
            EXECUTOR.submit(() -> {
                try {
                    for (int p = 0; p < pages; p++) {
                        List<RowData> chunk = coverService.searchRaw(username, filter, p, pageSize).getRows();
                        if (chunk != null && !chunk.isEmpty()) {
                            all.addAll(chunk);
                        }
                    }

                    Platform.runLater(() -> {
                        if (all == null || all.isEmpty()) {
                            hideBusy();
                            new Alert(Alert.AlertType.INFORMATION, "Keine Daten zum Exportieren.", ButtonType.OK).showAndWait();
                            return;
                        }

                        //List<String> displayHeaders = tableManager.getDisplayHeaders();
                        //List<String> originalKeys = tableManager.getOriginalKeys();
                        /**
                         FileChooser fileChooser = new FileChooser();
                         fileChooser.setTitle("Datenbankbericht exportieren");
                         fileChooser.setInitialFileName("cover_export." + format.getExtension());
                         fileChooser.getExtensionFilters().add(
                         new FileChooser.ExtensionFilter(format.name() + "-Dateien", "*." + format.getExtension())
                         );
                         File file = fileChooser.showSaveDialog(ausfuehrenButton.getScene().getWindow());
                         if (file == null) { hideBusy(); return; }

                         try {
                         exportWithFormat(all, displayHeaders, originalKeys, file, format);
                         new Alert(Alert.AlertType.INFORMATION, "Export erfolgreich:\n" + file.getName(), ButtonType.OK).showAndWait();
                         } catch (Exception ex) {
                         log.error("Export fehlgeschlagen", ex);
                         new Alert(Alert.AlertType.ERROR, "Exportfehler:\n" + ex.getMessage(), ButtonType.OK).showAndWait();
                         } finally {
                         hideBusy();
                         }
                         });**/
                        List<String> displayHeaders;
                        List<String> originalKeys;

                        // 🧠 Si Baumansicht active → utiliser TreeTable + grouped export
                        boolean isTreeView = toggleTreeView != null && toggleTreeView.isSelected();

                        if (isTreeView) {
                            displayHeaders = treeManager.getDisplayHeaders();
                            originalKeys   = treeManager.getOriginalKeys();
                        } else {
                            displayHeaders = tableManager.getDisplayHeaders();
                            originalKeys   = tableManager.getOriginalKeys();
                        }

                        FileChooser fileChooser = new FileChooser();
                        fileChooser.setTitle("Datenbankbericht exportieren");
                        fileChooser.setInitialFileName("cover_export." + format.getExtension());
                        fileChooser.getExtensionFilters().add(
                                new FileChooser.ExtensionFilter(format.name() + "-Dateien", "*." + format.getExtension())
                        );
                        File file = fileChooser.showSaveDialog(ausfuehrenButton.getScene().getWindow());
                        if (file == null) { hideBusy(); return; }

                        try {
                            if (isTreeView) {
                                // 🌳 grouped export (TreeTable)
                                List<String> groupKeys = groupByList.getSelectionModel().getSelectedItems();
                                if (format == ExportFormat.CSV) {
                                    new file.writer.GroupedCsvWriter().writeGrouped(all, groupKeys, file.getAbsolutePath());
                                } else {
                                    new file.writer.GroupedXlsxWriter().writeGrouped(all, groupKeys, file.getAbsolutePath());
                                }
                            } else {
                                // 📄 flat export (normale Tabelle)
                                exportWithFormat(all, displayHeaders, originalKeys, file, format);
                            }

                            new Alert(Alert.AlertType.INFORMATION,
                                    "Export erfolgreich:\n" + file.getName(), ButtonType.OK).showAndWait();

                        } catch (Exception ex) {
                            log.error("Export fehlgeschlagen", ex);
                            new Alert(Alert.AlertType.ERROR, "Exportfehler:\n" + ex.getMessage(), ButtonType.OK).showAndWait();
                        } finally {
                            hideBusy();
                        }
                    });

                } catch (Exception e) {
                    log.error("Export Task fehlgeschlagen", e);
                    Platform.runLater(() -> {
                        isSearchMode = false;
                        hideBusy();
                        new Alert(Alert.AlertType.ERROR, "Exportfehler:\n" + e.getMessage(), ButtonType.OK).showAndWait();
                    });
                }
            });

        } catch (Exception ex) {
            log.error("exportFullReport fehlgeschlagen", ex);
            new Alert(Alert.AlertType.ERROR, "Exportfehler:\n" + ex.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    // =========================
    // HELPERS
    // =========================
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

    private void installToggleWithDot(ToggleButton btn, String initialText) {
        if (btn == null) return;
        Circle dot = new Circle(5);
        btn.setGraphic(dot);
        btn.setContentDisplay(ContentDisplay.LEFT);
        btn.setGraphicTextGap(8);
        btn.setText(initialText);
        updateDot(dot, btn.isSelected());
        btn.selectedProperty().addListener((o, ov, nv) -> updateDot(dot, nv));
    }
    private void updateDot(Circle dot, boolean on) {
        dot.setFill(on ? Color.web("#16a34a") : Color.web("#b91c1c"));
        dot.setStroke(Color.web("#111827"));
        dot.setStrokeWidth(0.6);
    }

    private void makeListViewReorderable(ListView<String> listView) {
        listView.setCellFactory(lv -> {
            ListCell<String> cell = new ListCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            cell.setOnDragDetected(e -> {
                if (cell.isEmpty()) return;
                Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent cc = new ClipboardContent();
                cc.putString(cell.getItem());
                db.setContent(cc);
                e.consume();
            });
            cell.setOnDragOver(e -> {
                if (e.getGestureSource() != cell && e.getDragboard().hasString()) {
                    e.acceptTransferModes(TransferMode.MOVE);
                }
                e.consume();
            });
            cell.setOnDragDropped(e -> {
                Dragboard db = e.getDragboard();
                if (!db.hasString()) return;
                String dragged = db.getString();
                ObservableList<String> items = listView.getItems();
                int draggedIdx = items.indexOf(dragged);
                int thisIdx = cell.getIndex();
                if (draggedIdx >= 0 && thisIdx >= 0 && draggedIdx != thisIdx) {
                    items.remove(draggedIdx);
                    if (thisIdx > items.size()) thisIdx = items.size();
                    items.add(thisIdx, dragged);
                    listView.getSelectionModel().clearSelection();
                    listView.getSelectionModel().select(dragged);
                    updateGroupByLabel();
                }
                e.setDropCompleted(true);
                e.consume();
            });
            return cell;
        });
    }

    // DatePicker converter (multi-format) sans toucher à editor.bind
    private void installDatePickerConverter(DatePicker picker, Locale locale) {
        if (picker == null) return;

        final String[] acceptedPatterns = new String[]{
                "dd.MM.uuuu", "dd.MM.uu",
                "uuuu-MM-dd",
                "dd/MM/uuuu", "MM/dd/uuuu",
                "dd-MM-uuuu", "uuuu/MM/dd"
        };

        final java.time.format.DateTimeFormatter displayFmt =
                (locale != null && "de".equalsIgnoreCase(locale.getLanguage()))
                        ? java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu").withLocale(locale)
                        : java.time.format.DateTimeFormatter.ISO_LOCAL_DATE.withLocale(locale == null ? Locale.getDefault() : locale);

        picker.setConverter(new javafx.util.StringConverter<java.time.LocalDate>() {
            @Override public String toString(java.time.LocalDate date) {
                return (date == null) ? "" : displayFmt.format(date);
            }
            @Override public java.time.LocalDate fromString(String text) {
                if (text == null) return null;
                String s = text.trim();
                if (s.isEmpty()) return null;
                for (String p : acceptedPatterns) {
                    try {
                        var f = java.time.format.DateTimeFormatter.ofPattern(p)
                                .withResolverStyle(java.time.format.ResolverStyle.SMART)
                                .withLocale(locale == null ? Locale.getDefault() : locale);
                        return java.time.LocalDate.parse(s, f);
                    } catch (Exception ignore) {}
                }
                try {
                    return java.time.LocalDate.parse(s, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (Exception e) {
                    return picker.getValue();
                }
            }
        });

        // Prompt (safe) : on le met sur le picker, pas sur l’editor
        String sampleDE = "23.10.2025";
        String sampleISO = "2025-10-23";
        String prompt = (locale != null && "de".equalsIgnoreCase(locale.getLanguage()))
                ? "z.B. " + sampleDE + " / " + sampleISO
                : "e.g. " + sampleISO + " / " + sampleDE;
        picker.setPromptText(prompt);
    }
}