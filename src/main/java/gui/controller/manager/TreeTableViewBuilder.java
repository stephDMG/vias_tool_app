package gui.controller.manager;

import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Builder für eine universelle TreeTableView-Konfiguration.
 *
 * <p><b>Ziel:</b> Dieses FXML-Fragment (UniversalTreeTableView.fxml) wird geladen
 * und ein fertig verkabelter {@link TreeTableManager} bereitgestellt.
 * Falls das FXML nicht geladen werden kann, baut der Builder eine einfache
 * Fallback-UI, damit die Baumansicht nicht „verschwindet“.</p>
 *
 * <p><b>Erwartete IDs im FXML:</b> (nur #treeTableView zwingend)</p>
 * <ul>
 *   <li>#treeTableView – TreeTableView&lt;ObservableList&lt;String&gt;&gt;</li>
 *   <li>#searchField – TextField</li>
 *   <li>#deleteColumnsButton – Button</li>
 *   <li>#pagination – Pagination</li>
 *   <li>#resultsCountLabel – Label</li>
 *   <li>#exportCsvButton – Button</li>
 *   <li>#exportXlsxButton – Button</li>
 *   <li>#cleanColumnsButton – Button</li>
 *   <li>#expandAllButton – Button</li>
 *   <li>#collapseAllButton – Button</li>
 *   <li>#searchSection, #actionsSection, #exportSection – HBox</li>
 * </ul>
 */
public class TreeTableViewBuilder {

    private final VBox treeContainer;
    private final TextField searchField;
    private final Button deleteColumnsButton;
    private final Pagination pagination;
    private final Label resultsCountLabel;
    private final Button exportCsvButton;
    private final Button exportXlsxButton;
    private final Button cleanColumnsButton;
    private final Button expandAllButton;
    private final Button collapseAllButton;

    private final TreeTableView<ObservableList<String>> treeTableView;

    // Optionen
    private boolean optAutoExpandRoot = false;
    private boolean optShowRoot = false;

    /** Privater Konstruktor: versucht FXML zu laden; Fallback bei Fehlschlag. */
    private TreeTableViewBuilder(boolean buildFallback) {
        if (buildFallback) {
            // --- Fallback-UI ---
            this.treeContainer = new VBox(10);

            TextField sf = new TextField();
            sf.setPromptText("Suchbegriff eingeben...");
            Label count = new Label();
            HBox searchSection = new HBox(10, new Label("Suchen:"), sf, count);
            searchSection.setId("searchSection");
            HBox.setHgrow(sf, javafx.scene.layout.Priority.ALWAYS);

            TreeTableView<ObservableList<String>> ttv = new TreeTableView<>();
            ttv.setId("treeTableView");

            Button del = new Button("Löschen");
            Button clean = new Button("Bereinigen");
            Button expAll = new Button("Alle öffnen");
            Button collAll = new Button("Alle schließen");
            HBox actionsSection = new HBox(10, new Label("Aktionen:"), del, clean, expAll, collAll);
            actionsSection.setId("actionsSection");

            Pagination pag = new Pagination();
            pag.setId("pagination");
            pag.setMaxPageIndicatorCount(10);
            pag.setVisible(false);
            pag.setManaged(false);

            Label exportLabel = new Label("Vollständigen Bericht exportieren als:");
            Button btnCsv = new Button("CSV");
            Button btnXlsx = new Button("XLSX");
            HBox exportSection = new HBox(10, exportLabel, btnCsv, btnXlsx);
            exportSection.setId("exportSection");

            this.treeContainer.getChildren().addAll(searchSection, ttv, actionsSection, pag, exportSection);

            this.treeTableView = ttv;
            this.searchField = sf;
            this.resultsCountLabel = count;
            this.deleteColumnsButton = del;
            this.cleanColumnsButton = clean;
            this.expandAllButton = expAll;
            this.collapseAllButton = collAll;
            this.pagination = pag;
            this.exportCsvButton = btnCsv;
            this.exportXlsxButton = btnXlsx;

            this.treeTableView.setFixedCellSize(24);
            return;
        }

        // --- Normalfall: FXML laden ---
        VBox container;
        try {
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                    getClass().getResource("/fxml/components/UniversalTreeTableView.fxml"),
                    "Ressource /fxml/components/UniversalTreeTableView.fxml wurde nicht gefunden"
            ));
            container = loader.load();
        } catch (IOException | RuntimeException ex) {
            throw new RuntimeException("FXML-Load fehlgeschlagen", ex);
        }

        this.treeContainer = container;

        // Pflicht: #treeTableView
        Node n = treeContainer.lookup("#treeTableView");
        if (!(n instanceof TreeTableView)) {
            throw new RuntimeException("#treeTableView nicht gefunden oder falscher Typ");
        }
        this.treeTableView = (TreeTableView<ObservableList<String>>) n;

        // Optionale Knoten
        this.searchField         = (TextField)          treeContainer.lookup("#searchField");
        this.deleteColumnsButton = (Button)             treeContainer.lookup("#deleteColumnsButton");
        this.pagination          = (Pagination)         treeContainer.lookup("#pagination");
        this.resultsCountLabel   = (Label)              treeContainer.lookup("#resultsCountLabel");
        this.exportCsvButton     = (Button)             treeContainer.lookup("#exportCsvButton");
        this.exportXlsxButton    = (Button)             treeContainer.lookup("#exportXlsxButton");
        this.cleanColumnsButton  = (Button)             treeContainer.lookup("#cleanColumnsButton");
        this.expandAllButton     = (Button)             treeContainer.lookup("#expandAllButton");
        this.collapseAllButton   = (Button)             treeContainer.lookup("#collapseAllButton");

        this.treeTableView.setFixedCellSize(24);
    }

    /** Fabrikmethode mit Fallback. */
    public static TreeTableViewBuilder create() {
        try {
            return new TreeTableViewBuilder(false);
        } catch (RuntimeException ex) {
            return new TreeTableViewBuilder(true);
        }
    }

    // ---------------------- Features / Optionen ----------------------

    /** Sektionen ein-/ausblenden. */
    public TreeTableViewBuilder withFeatures(Feature... features) {
        var set = Arrays.asList(features);
        configureSection("searchSection", set.contains(Feature.SEARCH));
        configureSection("actionsSection", set.contains(Feature.SELECTION));
        configureSection("pagination",   set.contains(Feature.PAGINATION));
        configureSection("exportSection", set.contains(Feature.EXPORT));
        return this;
    }

    /** Root automatisch expandieren. */
    public TreeTableViewBuilder autoExpandRoot(boolean enabled) {
        this.optAutoExpandRoot = enabled;
        return this;
    }

    /** Root-Knoten sichtbar machen. */
    public TreeTableViewBuilder showRoot(boolean show) {
        this.optShowRoot = show;
        return this;
    }

    public Button getCleanButton() {
        return this.cleanColumnsButton;
    }

    /** Export-Label überschreiben. */
    public TreeTableViewBuilder withExportLabel(String labelText) {
        Label exportLabel = (Label) treeContainer.lookup("#exportLabel");
        if (exportLabel != null) exportLabel.setText(labelText);
        return this;
    }

    /** Actions-Label überschreiben. */
    public TreeTableViewBuilder withActionsLabel(String labelText) {
        Label actionsLabel = (Label) treeContainer.lookup("#actionsLabel");
        if (actionsLabel != null) actionsLabel.setText(labelText);
        return this;
    }

    // ---------------------- Manager-Erzeugung ----------------------

    /** Erzeugt den Manager und übergibt vorhandene Buttons/Optionen. */
    public TreeTableManager buildManager() {
        TreeTableManager manager = new TreeTableManager(
                treeTableView,
                searchField,
                deleteColumnsButton,
                pagination,
                resultsCountLabel
        );

        if (cleanColumnsButton  != null) manager.setCleanButton(cleanColumnsButton);
        if (exportCsvButton     != null) manager.setExportCsvButton(exportCsvButton);
        if (exportXlsxButton    != null) manager.setExportXlsxButton(exportXlsxButton);
        if (expandAllButton     != null) manager.setExpandAllButton(expandAllButton);
        if (collapseAllButton   != null) manager.setCollapseAllButton(collapseAllButton);

        manager.setAutoExpandRoot(optAutoExpandRoot);
        manager.setShowRoot(optShowRoot);

        return manager;
    }

    /** Root-Container für die Scene. */
    public VBox getTreeContainer() {
        return treeContainer;
    }

    // ---------------------- interne Helfer ----------------------

    private void configureSection(String sectionId, boolean enabled) {
        Node section = treeContainer.lookup("#" + sectionId);
        if (section != null) {
            section.setVisible(enabled);
            section.setManaged(enabled);
        }
    }

    // ---------------------- Feature-Enum ----------------------

    public enum Feature {
        SEARCH,
        SELECTION,
        PAGINATION,
        EXPORT
    }
}