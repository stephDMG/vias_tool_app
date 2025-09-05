package gui.controller.manager;


import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.Arrays;

/**
 * Builder für adaptive Tabellenkonfiguration.
 * Lädt UniversalTableView.fxml und passt es an die Anforderungen der Klasse an.
 */
public class TableViewBuilder {

    private final VBox tableContainer;
    private TableView<?> tableView = null;
    private final TextField searchField;
    private final Button deleteColumnsButton;
    private final Pagination pagination;
    private final Label resultsCountLabel;
    private final Button exportCsvButton;
    private final Button exportXlsxButton;

    private boolean optGroupStriping = false;
    private String groupStripingHeader = null;

    // Private Konstruktor - nur über build() erreichbar
    private TableViewBuilder() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/components/UniversalTableView.fxml"));
        this.tableContainer = loader.load();

        // FXML-Komponenten extrahieren
        this.tableView = (TableView<?>) tableContainer.lookup("#tableView");
        this.searchField = (TextField) tableContainer.lookup("#searchField");
        this.deleteColumnsButton = (Button) tableContainer.lookup("#deleteColumnsButton");
        this.pagination = (Pagination) tableContainer.lookup("#pagination");
        this.resultsCountLabel = (Label) tableContainer.lookup("#resultsCountLabel");
        this.exportCsvButton = (Button) tableContainer.lookup("#exportCsvButton");
        this.exportXlsxButton = (Button) tableContainer.lookup("#exportXlsxButton");

        if (this.tableView != null) {
            this.tableView.setFixedCellSize(24);
        }

    }

    public TableViewBuilder withGroupStriping(String header) {
        this.optGroupStriping = true;
        this.groupStripingHeader = header;
        return this;
    }



    /**
     * Erstellt Builder-Instanz
     */
    public static TableViewBuilder create() {
        try {
            return new TableViewBuilder();
        } catch (IOException e) {
            throw new RuntimeException("Could not load UniversalTableView.fxml", e);
        }
    }

    /**
     * Konfiguriert Features basierend auf Anforderungen
     */
    public TableViewBuilder withFeatures(Feature... features) {
        var featureSet = Arrays.asList(features);

        // Search Section
        configureSection("searchSection", featureSet.contains(Feature.SEARCH));

        // Actions Section (Selection)
        configureSection("actionsSection", featureSet.contains(Feature.SELECTION));

        // Pagination Section
        configureSection("pagination", featureSet.contains(Feature.PAGINATION));

        // Export Section
        configureSection("exportSection", featureSet.contains(Feature.EXPORT));

        return this;
    }

    /**
     * Konfiguriert Sektion basierend auf Feature-Aktivierung
     */
    private void configureSection(String sectionId, boolean enabled) {
        Node section = tableContainer.lookup("#" + sectionId);
        if (section != null) {
            section.setVisible(enabled);
            section.setManaged(enabled);
        }
    }

    /**
     * Setzt Custom-Labels für Export-Sektion
     */
    public TableViewBuilder withExportLabel(String labelText) {
        Label exportLabel = (Label) tableContainer.lookup("#exportLabel");
        if (exportLabel != null) {
            exportLabel.setText(labelText);
        }
        return this;
    }

    /**
     * Setzt Custom-Labels für Actions-Sektion
     */
    public TableViewBuilder withActionsLabel(String labelText) {
        Label actionsLabel = (Label) tableContainer.lookup("#actionsLabel");
        if (actionsLabel != null) {
            actionsLabel.setText(labelText);
        }
        return this;
    }

    /**
     * Erstellt EnhancedTableManager mit konfigurierten Komponenten
     */
    public EnhancedTableManager buildManager() {
        EnhancedTableManager manager = new EnhancedTableManager(
                (TableView) tableView,
                searchField,
                deleteColumnsButton,
                pagination,
                resultsCountLabel
        );

        if (optGroupStriping && groupStripingHeader != null) {
            manager.enableGroupStripingByHeader(groupStripingHeader);
        }

        return manager;
    }

    /**
     * Gibt das konfigurierte UI-Element zurück
     */
    public VBox getTableContainer() {
        return tableContainer;
    }

    // Getters für einzelne Komponenten (falls benötigt)
    public TableView<?> getTableView() {
        return tableView;
    }

    public TextField getSearchField() {
        return searchField;
    }

    public Button getDeleteColumnsButton() {
        return deleteColumnsButton;
    }

    public Pagination getPagination() {
        return pagination;
    }

    public Label getResultsCountLabel() {
        return resultsCountLabel;
    }

    public Button getExportCsvButton() {
        return exportCsvButton;
    }

    public Button getExportXlsxButton() {
        return exportXlsxButton;
    }

    public enum Feature {
        SEARCH,
        SELECTION,
        PAGINATION,
        EXPORT
    }
}