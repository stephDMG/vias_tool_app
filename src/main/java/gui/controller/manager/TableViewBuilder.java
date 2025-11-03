package gui.controller.manager;

import gui.controller.model.ColumnStateModel;
import gui.controller.model.ResultContextModel;
import gui.controller.model.TableStateModel;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Builder für adaptive Tabellenkonfiguration (TableView).
 * Lädt UniversalTableView.fxml und passt es an die Anforderungen an.
 */
public class TableViewBuilder {

    private final VBox tableContainer;
    private final TextField searchField;
    private final Button deleteColumnsButton;
    private final Pagination pagination;
    private final Label resultsCountLabel;
    private final Button exportCsvButton;
    private final Button exportXlsxButton;
    private final Button cleanColumnsButton;
    private final TableView<ObservableList<String>> tableView;

    private boolean optGroupStriping = false;
    private String groupStripingHeader = null;

    // Modelle d'état partagés (Maintenant 3)
    private ColumnStateModel columnStateModel = null;
    private ResultContextModel resultContextModel = null;
    private TableStateModel tableStateModel = null; // NOUVEAU

    private TableViewBuilder() throws IOException {
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/components/UniversalTableView.fxml")));
        this.tableContainer = loader.load();

        this.tableView = (TableView<ObservableList<String>>) tableContainer.lookup("#tableView");
        this.searchField = (TextField) tableContainer.lookup("#searchField");
        this.deleteColumnsButton = (Button) tableContainer.lookup("#deleteColumnsButton");
        this.pagination = (Pagination) tableContainer.lookup("#pagination");
        this.resultsCountLabel = (Label) tableContainer.lookup("#resultsCountLabel");
        this.exportCsvButton = (Button) tableContainer.lookup("#exportCsvButton");
        this.exportXlsxButton = (Button) tableContainer.lookup("#exportXlsxButton");
        this.cleanColumnsButton = (Button) tableContainer.lookup("#cleanColumnsButton");

        alignHBox("actionsSection", Pos.CENTER_RIGHT);
        alignHBox("exportSection",  Pos.CENTER_RIGHT);
        alignHBox("searchSection",  Pos.CENTER_LEFT);

        if (this.tableView != null) this.tableView.setFixedCellSize(24);
    }

    private void alignHBox(String id, Pos pos) {
        Node n = tableContainer.lookup("#" + id);
        if (n instanceof HBox box) box.setAlignment(pos);
    }

    public static TableViewBuilder create() {
        try {
            return new TableViewBuilder();
        } catch (IOException e) {
            throw new RuntimeException("Could not load UniversalTableView.fxml", e);
        }
    }

    // Signature MODIFIÉE pour accepter TableStateModel (résout l'erreur 478)
    public TableViewBuilder withModels(ColumnStateModel csm, ResultContextModel rcm, TableStateModel tsm) {
        this.columnStateModel = csm;
        this.resultContextModel = rcm;
        this.tableStateModel = tsm; // NOUVEAU
        return this;
    }

    public TableViewBuilder withGroupStriping(String header) {
        this.optGroupStriping = true;
        this.groupStripingHeader = header;
        return this;
    }

    public TableViewBuilder withFeatures(Feature... features) {
        var set = Arrays.asList(features);
        configureSection("searchSection", set.contains(Feature.SEARCH));
        configureSection("actionsSection", set.contains(Feature.SELECTION));
        configureSection("pagination", set.contains(Feature.PAGINATION));
        configureSection("exportSection", set.contains(Feature.EXPORT));
        return this;
    }

    private void configureSection(String sectionId, boolean enabled) {
        Node section = tableContainer.lookup("#" + sectionId);
        if (section != null) {
            section.setVisible(enabled);
            section.setManaged(enabled);
        }
    }

    public TableViewBuilder withExportLabel(String labelText) {
        Label exportLabel = (Label) tableContainer.lookup("#exportLabel");
        if (exportLabel != null) exportLabel.setText(labelText);
        return this;
    }

    public TableViewBuilder withActionsLabel(String labelText) {
        Label actionsLabel = (Label) tableContainer.lookup("#actionsLabel");
        if (actionsLabel != null) actionsLabel.setText(labelText);
        return this;
    }

    public EnhancedTableManager buildManager() {
        EnhancedTableManager manager;

        // Utilise le constructeur complet si des modèles sont fournis
        if (columnStateModel != null || resultContextModel != null || tableStateModel != null) {
            manager = new EnhancedTableManager(
                    tableView,
                    searchField,
                    deleteColumnsButton,
                    pagination,
                    resultsCountLabel,
                    columnStateModel,
                    resultContextModel,
                    tableStateModel // NOUVEAU: Ajout du 3e modèle
            );
        } else {
            // Fallback pour la rétrocompatibilité (constructeur 5 arguments)
            manager = new EnhancedTableManager(
                    tableView,
                    searchField,
                    deleteColumnsButton,
                    pagination,
                    resultsCountLabel
            );
        }


        if (optGroupStriping && groupStripingHeader != null) {
            manager.enableGroupStripingByHeader(groupStripingHeader);
        }

        if (cleanColumnsButton != null) manager.setCleanButton(cleanColumnsButton);
        if (exportCsvButton  != null) manager.setExportCsvButton(exportCsvButton);
        if (exportXlsxButton != null) manager.setExportXlsxButton(exportXlsxButton);

        return manager;
    }

    public VBox getTableContainer() { return tableContainer; }
    public TableView<ObservableList<String>> getTableView() { return tableView; }
    public TextField getSearchField() { return searchField; }
    public Button getDeleteColumnsButton() { return deleteColumnsButton; }
    public Pagination getPagination() { return pagination; }
    public Label getResultsCountLabel() { return resultsCountLabel; }
    public Button getExportCsvButton() { return exportCsvButton; }
    public Button getExportXlsxButton() { return exportXlsxButton; }
    public Button getCleanColumnsButton() { return cleanColumnsButton; } // Déjà présent

    public enum Feature { SEARCH, SELECTION, PAGINATION, EXPORT }
}