package gui.cover;

import formatter.ColumnValueFormatter;
import gui.controller.manager.*;
import gui.controller.model.ColumnStateModel;
import gui.controller.model.ResultContextModel;
import gui.controller.model.TableStateModel;
import gui.controller.service.FormatterService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
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
import java.util.function.Consumer;
import java.util.function.Function;

import static gui.controller.service.FormatterService.exportWithFormat;


public class CoverDomainController {
    private static final Logger log = LoggerFactory.getLogger(CoverDomainController.class);
    // Ladevorgang
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    // --- ÉTAT GLOBAL PARTAGÉ ENTRE TABLE & TREE ---
    private final TableStateModel tableStateModel = new TableStateModel();
    private final ColumnStateModel columnStateModel = new ColumnStateModel();
    // ----------------------------------------------
    private final ResultContextModel resultContextModel = new ResultContextModel();
    // Gemeinsame Map für Spalten-Anzeigenamen (Key = technischer Spaltenname)
    private final ObservableMap<String, String> sharedColumnDisplayNames =
            FXCollections.observableHashMap();
    // Storno Key→Value
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
            "Versicherungsschein Nr", "Versicherungsnehmer"
    );
    // Persistenz: Gruppierung pro Kernfrage
    private final Map<String, List<String>> groupByMemory = new HashMap<>();
    private final DateTimeFormatter displayFormatter =
            DateTimeFormatter.ofPattern("dd.MM.yyyy").withResolverStyle(ResolverStyle.STRICT);
    // UI
    @FXML
    private Label domainTitle, bastandSelectedLabel, vertragsstandSelectedLabel, messageLabel;
    @FXML
    private ChoiceBox<String> kernfrageChoice;
    @FXML
    private ListView<String> vertragsstandList;
    @FXML
    private ListView<String> bearbeitungsstandList;
    @FXML
    private VBox resultsContainer;
    @FXML
    private HBox parameter, dateBox;
    @FXML
    private DatePicker abDatePicker, bisDatePicker;
    @FXML
    private Button ausfuehrenButton;
    // Gruppierung
    @FXML
    private ListView<String> groupByList;
    @FXML
    private Label groupBySelectedLabel;
    @FXML
    private CheckBox groupByAllCheck;
    // Stornogründe
    @FXML
    private VBox stornoGrundFilterBox;
    @FXML
    private ListView<String> stornoGrundList;
    @FXML
    private StackPane resultsStack;
    @FXML
    private VBox tableHost, treeHost;
    @FXML
    private ToggleButton toggleTreeView;
    @FXML
    private ToggleButton toggleVersionView;
    @FXML
    private ToggleButton toggleFullName;
    private EnhancedTableManager tableManager;
    private TreeTableManager treeManager;
    private TreeTableViewBuilder treeBuilder;
    private CoverService coverService;
    private String username;
    private String currentDomain;
    private ProgressIndicator busy;
    private Map<String, String> dictSta;
    private Map<String, String> dictBastand;
    // Export-Buttons
    private Button exportCsvButton;
    private Button exportXlsxButton;
    private Map<String, String> sbDict = Map.of();
    private volatile boolean dictionariesLoaded = false;
    private String pendingKernfrage = null;

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

        initBusyOverlay();
        showBusy();


        setupParams();
        setupTable();
        setupTree();
        setupUnifiedSearchHandler();

        setupGroupBy();

        installToggleWithDot(toggleTreeView, "Baumansicht");
        toggleTreeView.selectedProperty().addListener((obs, wasTree, isTree) -> {
            treeHost.setVisible(isTree);
            treeHost.setManaged(isTree);
            tableHost.setVisible(!isTree);
            tableHost.setManaged(!isTree);

            resultContextModel.setTreeViewActive(isTree);

            String currentText = tableStateModel.getSearchText();
            if (isTree) {
                TextField treeSearchField = treeBuilder.getSearchField();
                if (treeSearchField != null && !treeSearchField.getText().equals(currentText)) {
                    treeSearchField.setText(currentText);
                }
                treeManager.syncToModelPage();
            } else {
                TextField tableSearchField = tableManager.getSearchField();
                if (tableSearchField != null && !tableSearchField.getText().equals(currentText)) {
                    tableSearchField.setText(currentText);
                }
                tableManager.syncToModelPage();
            }
        });

        if (toggleVersionView != null) {
            installToggleWithDot(toggleVersionView, "Version");
            toggleVersionView.selectedProperty().addListener((obs, oldV, newV) -> {
                columnStateModel.clear();
                Platform.runLater(this::runKernfrage);
            });
        }

        installToggleWithDot(toggleFullName, "Voll. Namen");
        toggleFullName.selectedProperty().addListener((o, ov, nv) -> {
            resultContextModel.setFullNameMode(nv);
            tableManager.rebuildView();
            treeManager.rebuildView();
        });

        setupBindings();
        ColumnValueFormatter.bindFullNameMode(resultContextModel.fullNameModeProperty());
        loadSbDictOnce();
        initFullNameToggle();
        setupKernfragenChoice();
        FormatterService.reloadRuntimeConfig();

        applyGroupingHeaderKeysFromSelection();

        showMessage("Bitte wählen Sie eine Kernfrage aus.");
    }

    private void loadSbDictOnce() {
        EXECUTOR.submit(() -> {
            try {
                Map<String, String> dict = coverService.getDictionary(username, "SACHBEA_FULL");
                Platform.runLater(() -> {
                    ColumnValueFormatter.setSbDictionary(dict == null ? Map.of() : dict);

                    tableManager.rebuildView();
                    treeManager.rebuildView();
                });
            } catch (Exception e) {
                log.warn("SB-Dict nicht geladen", e);
            }
        });
    }

    // 2) Binder le toggle « Voll. Name »
    private void initFullNameToggle() {
        installToggleWithDot(toggleFullName, "Voll. Namen");
        ColumnValueFormatter.bindFullNameMode(resultContextModel.fullNameModeProperty());
        toggleFullName.selectedProperty().addListener((o, ov, nv) -> {
            resultContextModel.setFullNameMode(nv);
            // simple repaint
            tableManager.requestRefresh();
            treeManager.requestRefresh();
        });
    }

    /**
     * Appelée depuis le Dashboard après le chargement du FXML.
     */
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
                columnStateModel.clear();
                tableStateModel.reset();
                Platform.runLater(this::runKernfrage);
            }
        } else {
            pendingKernfrage = kf;
        }
    }

    // =========================
    // BUSY OVERLAY
    // =========================
    private void initBusyOverlay() {
        busy = new ProgressIndicator();
        busy.setMaxSize(90, 90);
        busy.setVisible(false);
        resultsStack.getChildren().add(busy);
        StackPane.setAlignment(busy, Pos.CENTER);
        busy.visibleProperty().bind(resultContextModel.loadingProperty());
        busy.setMouseTransparent(true);
    }

    private void showBusy() {
        resultContextModel.setLoading(true);
    }

    private void hideBusy() {
        resultContextModel.setLoading(false);
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

        // ➜ initialiser la clé de groupe (all selected → pas de clé SB)
        applyGroupingHeaderKeysFromSelection();

        groupByList.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<String>) c -> {
                    updateGroupByLabel();
                    // ➜ mise à jour clé SB lorsque la sélection change
                    applyGroupingHeaderKeysFromSelection();
                    treeManager.onGroupingChanged();
                    Platform.runLater(() -> {
                        boolean allSelected = groupByList.getSelectionModel().getSelectedItems().size() == groupByOptions.size();
                        treeManager.requestRefresh();
                        groupByAllCheck.setSelected(allSelected);
                        runKernfrage();
                    });
                }
        );

        groupByAllCheck.selectedProperty().addListener((obs, was, checked) -> {
            Platform.runLater(() -> {
                if (checked) groupByList.getSelectionModel().selectAll();
                else groupByList.getSelectionModel().clearSelection();
                updateGroupByLabel();
                // ➜ recalculer la clé (si “Tous” → null)
                applyGroupingHeaderKeysFromSelection();
                treeManager.onGroupingChanged();
                Platform.runLater(() -> {
                    treeManager.requestRefresh();   // force repaint
                });
                // ➜ relancer
                runKernfrage();
            });
        });

        makeListViewReorderable(groupByList);

        // ⚠️ Si ton makeListViewReorderable réordonne vraiment les items,
        // et que tu veux que le 1er niveau (sel.get(0)) suive l’ordre visuel
        // après un drag&drop, ajoute un listener sur focus/mouse release:
        groupByList.setOnMouseReleased(e -> {
            // quand l’ordre change, la sélection reste; on réévalue juste la clé
            applyGroupingHeaderKeysFromSelection();
            // pas forcément besoin de relancer ici si tu relances déjà dans d’autres listeners,
            // sinon décommente:
            // runKernfrage();
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

    // =========================
    // PARAMS
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

                    // DatePicker
                    Locale uiLocale = Locale.getDefault();
                    installDatePickerConverter(abDatePicker, uiLocale);
                    installDatePickerConverter(bisDatePicker, uiLocale);

                    dictionariesLoaded = true;

                    String currentKF = kernfrageChoice.getSelectionModel().getSelectedItem();
                    if (pendingKernfrage != null || currentKF != null) {
                        applyKernfrageDefaults(pendingKernfrage != null ? pendingKernfrage : currentKF);
                        pendingKernfrage = null;

                        columnStateModel.clear();
                        tableStateModel.reset();

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

    /**
     * Wendet einen neuen Ergebnis-Kontext (KF oder Suche) atomar auf dem FX-Thread an.
     * Stellt die Synchronisation aller Modelle und Tabellen-Manager sicher.
     */
    private void applyResultContext(CoverFilter filter, DataLoader loader, int total, boolean isSearch, String searchQuery) {
        Objects.requireNonNull(filter, "Filter darf nicht null sein.");

        // 1. Atomare Aktualisierung der Modelle
        resultContextModel.setFilter(filter);
        resultContextModel.setPageLoader(loader);
        resultContextModel.setTotalCount(total);
        tableStateModel.setTotalCount(total);

        // 2. Flags und Paging-Index (setSearchActive steuert die Label-Sichtbarkeit)
        tableStateModel.setSearchActive(isSearch);
        tableStateModel.setSearchText(searchQuery == null ? "" : searchQuery);
        tableStateModel.setCurrentPageIndex(0); // Forciert den Index 0 bei Kontextwechsel

        // 3. Label-Steuerung (messageLabel zeigt nur den Such-Status an)
        if (isSearch) {
            messageLabel.setText("(" + total + " Ergebnis" + (total != 1 ? "se" : "") + " – Suche aktiv)");
        } else {
            showMessage(null); // Versteckt das messageLabel
        }

        // 4. Ladebefehl für BEIDE Manager (Table & Tree)
        List<String> groupSnapshot = new ArrayList<>(groupByList.getSelectionModel().getSelectedItems());
        Function<RowData, List<String>> pathProvider = row -> buildPathFromSnapshot(row, groupSnapshot);

        tableManager.loadDataFromServer(total, loader);
        treeManager.loadDataFromServer(total, loader, pathProvider);

        log.info("Apply Context: Modus={}, Gesamt={}, Aktiv={}, Suche='{}'",
                isSearch ? "SUCHE" : "KF", total, isSearch, searchQuery);
    }


    /**
     * Richtet einen vereinheitlichten Server-Such-Handler ein.
     * Wird von beiden Managern (Table/Tree) aufgerufen.
     */
    private void setupUnifiedSearchHandler() {
        Consumer<String> searchHandler = query -> {
            EXECUTOR.submit(() -> {
                try {
                    Platform.runLater(() -> resultContextModel.setLoading(true));

                    final String q = (query == null) ? "" : query.trim();
                    log.info("Suche EINGABE: q='{}'", q);

                    if (q.isEmpty()) {
                        // Rückkehr zur Kernfrage
                        Platform.runLater(() -> {
                            log.info("Suche GELÖSCHT → starte Kernfrage neu");
                            runKernfrage();
                        });
                        return;
                    }

                    // Suche aktiv
                    CoverFilter filter = buildFilterFromUI();
                    filter.setSearchTerm(q);

                    int total = coverService.count(username, filter);
                    log.info("Suche ZÄHLUNG: q='{}', Gesamt={}", q, total);

                    DataLoader loader = (page, size) ->
                            coverService.searchRaw(username, filter, page, size).getRows();

                    Platform.runLater(() -> {
                        applyResultContext(filter, loader, total, true, q);
                    });

                } catch (Exception e) {
                    log.error("Server-Suche fehlgeschlagen", e);
                } finally {
                    Platform.runLater(() -> resultContextModel.setLoading(false));
                }
            });
        };

        tableManager.setOnServerSearch(searchHandler);
        treeManager.setOnServerSearch(searchHandler);
    }

    // =========================
    // TREE
    // =========================
    private void setupTree() {
        treeBuilder = TreeTableViewBuilder.create()
                .withFeatures(
                        TreeTableViewBuilder.Feature.SEARCH,
                        TreeTableViewBuilder.Feature.SELECTION,
                        TreeTableViewBuilder.Feature.PAGINATION,
                        TreeTableViewBuilder.Feature.EXPORT
                )
                .withExportLabel("Vollständigen Bericht exportieren als:")
                .withModels(columnStateModel, resultContextModel, tableStateModel);

        treeHost.getChildren().setAll(treeBuilder.getTreeContainer());
        treeManager = treeBuilder.buildManager();

        treeManager.setCleanButton(treeBuilder.getCleanButton());
        treeManager.enableSearch();
        treeManager.enableSelection();
        treeManager.enablePagination(100);

        treeManager.setSharedColumnDisplayNames(sharedColumnDisplayNames);


        // KF-Label nur anzeigen, wenn KEINE Suche aktiv ist
        Label treeKFLabel = treeBuilder.getResultsCountLabel();
        if (treeKFLabel != null) {
            treeKFLabel.visibleProperty().bind(tableStateModel.searchActiveProperty().not());
            treeKFLabel.managedProperty().bind(treeKFLabel.visibleProperty());
        }
        if (messageLabel != null) {
            messageLabel.visibleProperty().bind(tableStateModel.searchActiveProperty());
            messageLabel.managedProperty().bind(messageLabel.visibleProperty());
        }

        VBox treeContainer = treeBuilder.getTreeContainer();
        TableLayoutHelper.configureTableContainer(
                treeHost,          // ✅ host de l’arbre
                treeContainer,
                getClass().getSimpleName() + "#Tree"
        );


        //treeManager.withAutoRowsPerPage(treeHost);

        Platform.runLater(() -> {
            var ttv = treeManager.getTreeTableView();
            ttv.setColumnResizePolicy(TreeTableView.UNCONSTRAINED_RESIZE_POLICY);
        });

        // Exports
        treeManager.setOnExportCsv(() -> exportFullReport(ExportFormat.CSV));
        treeManager.setOnExportXlsx(() -> exportFullReport(ExportFormat.XLSX));

        // Toggle aktualisiert die aktive Sicht
        toggleTreeView.selectedProperty().addListener((obs, wasTree, isTree) -> {
            resultContextModel.setTreeViewActive(isTree);
        });
    }

    private void applyGroupingHeaderKeysFromSelection() {
        if (treeManager == null) return;
        var sel = groupByList.getSelectionModel().getSelectedItems();
        List<String> keysPerLevel = (sel == null) ? List.of()
                : sel.stream().map(this::resolveGroupingHeaderKey).toList(); // ex: ["SB_Vertr","SB_Schad", null, ...]
        treeManager.setGroupingHeaderKeys(keysPerLevel);
    }


    // =========================
    // TABLE
    // =========================
    private void setupTable() {
        TableViewBuilder builder = TableViewBuilder.create()
                .withFeatures(
                        TableViewBuilder.Feature.SELECTION,
                        TableViewBuilder.Feature.PAGINATION,
                        TableViewBuilder.Feature.EXPORT,
                        TableViewBuilder.Feature.SEARCH
                )
                .withExportLabel("Vollständigen Bericht exportieren als:")
                .withModels(columnStateModel, resultContextModel, tableStateModel);

        tableManager = builder.buildManager()
                .enableSearch()
                .enableSelection();
        tableManager.setSharedColumnDisplayNames(sharedColumnDisplayNames);


        // KF-Label nur anzeigen, wenn KEINE Suche aktiv ist
        Label tableKFLabel = builder.getResultsCountLabel();
        if (tableKFLabel != null) {
            tableKFLabel.visibleProperty().bind(tableStateModel.searchActiveProperty().not());
            tableKFLabel.managedProperty().bind(tableKFLabel.visibleProperty());
        }
        if (messageLabel != null) {
            messageLabel.visibleProperty().bind(tableStateModel.searchActiveProperty());
            messageLabel.managedProperty().bind(messageLabel.visibleProperty());


            tableHost.getChildren().setAll(builder.getTableContainer());
        }


        VBox tableContainer = builder.getTableContainer();

        TableLayoutHelper.configureTableContainer(
                tableHost,         // ✅ parent = host de la table
                tableContainer,
                getClass().getSimpleName()
        );
        tableManager.withAutoRowsPerPage(tableHost);

        Platform.runLater(() -> {
            var ttv = tableManager.getTableView();
            ttv.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        });


        exportCsvButton = builder.getExportCsvButton();
        exportXlsxButton = builder.getExportXlsxButton();

        if (exportCsvButton != null && exportXlsxButton != null) {
            exportCsvButton.disableProperty().unbind();
            exportXlsxButton.disableProperty().unbind();

            exportCsvButton.disableProperty().bind(
                    resultContextModel.canExportProperty().not().or(resultContextModel.loadingProperty())
            );
            exportXlsxButton.disableProperty().bind(
                    resultContextModel.canExportProperty().not().or(resultContextModel.loadingProperty())
            );

            exportCsvButton.setOnAction(this::exportFullReport);
            exportXlsxButton.setOnAction(this::exportFullReport);
        } else {
            log.warn("Export buttons not available from TableViewBuilder (EXPORT feature missing?)");
        }
    }

    private boolean isTreeActive() {
        try {
            return resultContextModel.isTreeViewActive();
        } catch (Exception ignore) {
            return toggleTreeView != null && toggleTreeView.isSelected();
        }
    }

    // =========================
    // BINDINGS / KERNFRAGEN
    // =========================
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

            columnStateModel.clear();
            tableStateModel.reset();

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
            dateBox.setVisible(true);
            dateBox.setManaged(true);
            stornoGrundFilterBox.setVisible(true);
            stornoGrundFilterBox.setManaged(true);
        } else {
            dateBox.setVisible(false);
            dateBox.setManaged(false);
            stornoGrundFilterBox.setVisible(false);
            stornoGrundFilterBox.setManaged(false);
        }
    }

    /**
     * Construit le CoverFilter à partir de l'état de l'UI.
     */
    private CoverFilter buildFilterFromUI() {
        CoverFilter filter = new CoverFilter();

        if (toggleVersionView != null) {
            filter.setWithVersion(toggleVersionView.isSelected());
        }

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
        if (abDatePicker != null && abDatePicker.getValue() != null) filter.setAbDate(abDatePicker.getValue());
        if (bisDatePicker != null && bisDatePicker.getValue() != null) filter.setBisDate(bisDatePicker.getValue());

        // Gruppierung
        List<String> selectedGroupBys = new ArrayList<>(groupByList.getSelectionModel().getSelectedItems());
        filter.setGroupBy(selectedGroupBys.size() == groupByOptions.size() ? null : selectedGroupBys);

        return filter;
    }

    // =========================
    // AUSFÜHREN (KF)
    // =========================
    @FXML
    private void runKernfrage() {
        showBusy();

        CoverFilter filter = buildFilterFromUI();
        String selectedKF = kernfrageChoice.getSelectionModel().getSelectedItem();
        if (selectedKF == null) {
            hideBusy();
            return;
        }

        groupByMemory.put(selectedKF, new ArrayList<>(groupByList.getSelectionModel().getSelectedItems()));

        EXECUTOR.submit(() -> {
            try {
                int total = coverService.count(username, filter);
                log.info("KF ZÄHLUNG: Gesamt={}", total);

                DataLoader loader = (page, size) ->
                        coverService.searchRaw(username, filter, page, size).getRows();

                Platform.runLater(() -> {
                    applyResultContext(filter, loader, total, false, "");
                });

            } catch (Exception ex) {
                log.error("runKernfrage() fehlgeschlagen", ex);
            } finally {
                Platform.runLater(() -> {
                    PauseTransition pt = new PauseTransition(Duration.millis(220));
                    pt.setOnFinished(ev -> hideBusy());
                    pt.play();
                });
            }
        });
    }

    // =========================
    // EXPORT
    // =========================
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

    /**
     * Startet den asynchronen Export des gesamten Ergebnisses.
     */
    private void exportFullReport(ExportFormat format) {
        CoverFilter filter = resultContextModel.getFilter();
        DataLoader loader = resultContextModel.getPageLoader();
        int total = resultContextModel.getTotalCount();

        if (filter == null || loader == null || total <= 0) {
            new Alert(Alert.AlertType.INFORMATION, "Keine Daten zum Exportieren verfügbar.", ButtonType.OK).showAndWait();
            return;
        }

        List<String> displayHeaders;
        List<String> originalKeys;
        boolean isTreeView = toggleTreeView != null && toggleTreeView.isSelected();

        if (isTreeView) {
            displayHeaders = treeManager.getDisplayHeaders();
            originalKeys = treeManager.getOriginalKeys();
        } else {
            displayHeaders = tableManager.getDisplayHeaders();
            originalKeys = tableManager.getOriginalKeys();
        }

        String kf = (kernfrageChoice.getSelectionModel().getSelectedItem() == null)
                ? "cover_export" : kernfrageChoice.getSelectionModel().getSelectedItem().replaceAll("\\s+", "_");
        String groupSuffix = isTreeView ? "_Group" : "";
        String versionSuffix = (toggleVersionView != null && toggleVersionView.isSelected()) ? "_MitVersion" : "_OhneVersion";
        String staSuffix = "";
        try {
            List<String> staSel = vertragsstandList.getSelectionModel().getSelectedItems();
            if (staSel != null && !staSel.isEmpty()) {
                String id = staSel.get(0);
                int idx = id.indexOf(" - ");
                staSuffix = "_Sta_" + (idx > 0 ? id.substring(0, idx) : id);
            }
        } catch (Exception ignore) {
        }
        String baseName = kf + groupSuffix + versionSuffix + staSuffix;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Datenbankbericht exportieren");
        fileChooser.setInitialFileName(baseName + "." + format.getExtension());
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(format.name() + "-Dateien", "*." + format.getExtension())
        );

        File file = fileChooser.showSaveDialog(ausfuehrenButton.getScene().getWindow());
        if (file == null) {
            return;
        }

        if (file.exists()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Datei existiert bereits");
            confirm.setHeaderText("Die Datei existiert bereits: " + file.getName());
            confirm.setContentText("Möchten Sie die bestehende Datei überschreiben?");

            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
        }

        try {
            final int pageSize = 1000;
            final int finalTotal = total;
            final File finalFile = file;
            final List<String> finalDisplayHeaders = displayHeaders;
            final List<String> finalOriginalKeys = originalKeys;

            showBusy();

            EXECUTOR.submit(() -> {
                List<RowData> all = new ArrayList<>(Math.min(finalTotal, 20000));

                try {
                    int pages = (int) Math.ceil(finalTotal / (double) pageSize);

                    for (int p = 0; p < pages; p++) {
                        List<RowData> chunk = loader.loadPage(p, pageSize);
                        if (chunk != null && !chunk.isEmpty()) {
                            all.addAll(chunk);
                        }
                    }

                    Platform.runLater(() -> {
                        try {
                            if (all.isEmpty()) {
                                new Alert(Alert.AlertType.INFORMATION, "Keine Daten zum Exportieren.", ButtonType.OK).showAndWait();
                                return;
                            }

                            if (isTreeView) {
                                List<String> groupKeys = groupByList.getSelectionModel().getSelectedItems();
                                if (format == ExportFormat.CSV) {
                                    new file.writer.GroupedCsvWriter().writeGrouped(all, groupKeys, finalFile.getAbsolutePath());

                                } else {
                                    new file.writer.GroupedXlsxWriter().writeGrouped(all, groupKeys, finalFile.getAbsolutePath());
                                }
                            } else {
                                boolean fullName = resultContextModel.fullNameModeProperty().get();
                                exportWithFormat(all, finalDisplayHeaders, finalOriginalKeys, finalFile, format, fullName);
                            }

                            new Alert(Alert.AlertType.INFORMATION,
                                    "Export erfolgreich:\n" + finalFile.getName(), ButtonType.OK).showAndWait();

                        } catch (Exception ex) {
                            log.error("Export fehlgeschlagen (Schreibfehler)", ex);
                            new Alert(Alert.AlertType.ERROR, "Exportfehler:\nSchreibfehler: " + ex.getMessage(), ButtonType.OK).showAndWait();
                        } finally {
                            hideBusy();
                        }
                    });

                } catch (Exception e) {
                    log.error("Export Task (Daten-Laden) fehlgeschlagen", e);
                    Platform.runLater(() -> {
                        hideBusy();
                        new Alert(Alert.AlertType.ERROR, "Exportfehler:\nFehler beim Laden der Daten: " + e.getMessage(), ButtonType.OK).showAndWait();
                    });
                }
            });

        } catch (Exception ex) {
            log.error("exportFullReport: Initialisierung fehlgeschlagen", ex);
            new Alert(Alert.AlertType.ERROR, "Exportfehler:\nInitialisierung fehlgeschlagen: " + ex.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    // =========================
    // HELPERS
    // =========================
    private List<String> nonNullList(String singleId) {
        return (singleId == null) ? List.of() : List.of(singleId);
    }

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
                @Override
                protected void updateItem(String item, boolean empty) {
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
            @Override
            public String toString(java.time.LocalDate date) {
                return (date == null) ? "" : displayFmt.format(date);
            }

            @Override
            public java.time.LocalDate fromString(String text) {
                if (text == null) return null;
                String s = text.trim();
                if (s.isEmpty()) return null;
                for (String p : acceptedPatterns) {
                    try {
                        var f = java.time.format.DateTimeFormatter.ofPattern(p)
                                .withResolverStyle(ResolverStyle.SMART)
                                .withLocale(locale == null ? Locale.getDefault() : locale);
                        return java.time.LocalDate.parse(s, f);
                    } catch (Exception ignore) {
                    }
                }
                try {
                    return java.time.LocalDate.parse(s, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (Exception e) {
                    return picker.getValue();
                }
            }
        });

        String sampleDE = "23.10.2025";
        String sampleISO = "2025-10-23";
        String prompt = (locale != null && "de".equalsIgnoreCase(locale.getLanguage()))
                ? "z.B. " + sampleDE + " / " + sampleISO
                : "e.g. " + sampleISO + " / " + sampleDE;
        picker.setPromptText(prompt);
    }


    // ----- helpers de grouping pour Tree -----

    /**
     * Mappt den sichtbaren Gruppen-Namen auf den Header-Alias der SB-Spalte.
     */
    private String resolveGroupingHeaderKey(String groupLabel) {
        if (groupLabel == null) return null;
        return switch (groupLabel) {
            case "Sachbearbeiter (Vertrag)" -> "SB_Vertr";
            case "Sachbearbeiter (Schaden)" -> "SB_Schad";
            case "Sachbearbeiter (Rechnung)" -> "SB_Rechnung";
            case "SB GL/Prokurist", "Sachbearbeiter (GL/Prokurist)" -> "GL_Prokurist";
            case "Sachbearbeiter (Doku)" -> "SB_Doku";
            case "Sachbearbeiter (BuHa)" -> "SB_BuHa";
            default -> null; // andere Gruppen (Makler, Gesellschaft, …) → kein SB-Mapping
        };
    }

    private List<String> buildPathFromSnapshot(RowData row, List<String> groupSnapshot) {
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
                default -> {
                }
            }
        }
        if (path.isEmpty()) path.add("Alle");
        return path;
    }
}
