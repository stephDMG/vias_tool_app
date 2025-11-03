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
 * Builder für adaptive TreeTable-Konfiguration.
 * Lädt UniversalTreeTableView.fxml und passt es an die Anforderungen an.
 */
public class TreeTableViewBuilder {

    private final VBox treeContainer;
    private final TreeTableView<ObservableList<String>> treeTableView;
    private final TextField searchField;
    private final Button deleteColumnsButton;
    private final Pagination pagination;
    private final Label resultsCountLabel;
    private final Button exportCsvButton;
    private final Button exportXlsxButton;
    private final Button cleanColumnsButton;
    private final Button expandAllButton;
    private final Button collapseAllButton;

    // Modelle d'état partagés
    private ColumnStateModel columnStateModel = null;
    private ResultContextModel resultContextModel = null;
    private TableStateModel tableStateModel = null;


    private TreeTableViewBuilder() throws IOException {
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/components/UniversalTreeTableView.fxml")));
        this.treeContainer = loader.load();

        this.treeTableView = (TreeTableView<ObservableList<String>>) treeContainer.lookup("#treeTableView");
        this.searchField = (TextField) treeContainer.lookup("#searchField");
        this.deleteColumnsButton = (Button) treeContainer.lookup("#deleteColumnsButton");
        this.pagination = (Pagination) treeContainer.lookup("#pagination");
        this.resultsCountLabel = (Label) treeContainer.lookup("#resultsCountLabel");
        this.exportCsvButton = (Button) treeContainer.lookup("#exportCsvButton");
        this.exportXlsxButton = (Button) treeContainer.lookup("#exportXlsxButton");
        this.cleanColumnsButton = (Button) treeContainer.lookup("#cleanColumnsButton");
        this.expandAllButton = (Button) treeContainer.lookup("#expandAllButton");
        this.collapseAllButton = (Button) treeContainer.lookup("#collapseAllButton");

        alignHBox("actionsSection", Pos.CENTER_RIGHT);
        alignHBox("exportSection",  Pos.CENTER_RIGHT);
        alignHBox("searchSection",  Pos.CENTER_LEFT);

        if (this.treeTableView != null) this.treeTableView.setFixedCellSize(24);
    }

    private void alignHBox(String id, Pos pos) {
        Node n = treeContainer.lookup("#" + id);
        if (n instanceof HBox box) box.setAlignment(pos);
    }

    public static TreeTableViewBuilder create() {
        try {
            return new TreeTableViewBuilder();
        } catch (IOException e) {
            throw new RuntimeException("Could not load UniversalTreeTableView.fxml", e);
        }
    }

    // NOUVEAU: Signature correcte avec les 3 modèles (résout l'erreur de withModels)
    public TreeTableViewBuilder withModels(ColumnStateModel csm, ResultContextModel rcm, TableStateModel tsm) {
        this.columnStateModel = csm;
        this.resultContextModel = rcm;
        this.tableStateModel = tsm;
        return this;
    }


    public TreeTableViewBuilder withFeatures(Feature... features) {
        var set = Arrays.asList(features);
        configureSection("searchSection", set.contains(Feature.SEARCH));
        configureSection("actionsSection", set.contains(Feature.SELECTION));
        configureSection("pagination", set.contains(Feature.PAGINATION));
        configureSection("exportSection", set.contains(Feature.EXPORT));
        return this;
    }

    public TreeTableViewBuilder withExportLabel(String labelText) {
        Label exportLabel = (Label) treeContainer.lookup("#exportLabel");
        if (exportLabel != null) exportLabel.setText(labelText);
        return this;
    }

    private void configureSection(String sectionId, boolean enabled) {
        Node section = treeContainer.lookup("#" + sectionId);
        if (section != null) {
            section.setVisible(enabled);
            section.setManaged(enabled);
        }
    }

    public TreeTableManager buildManager() {
        TreeTableManager manager;

        // Logique pour le constructeur compatible 5 args ou le nouveau complet
        if (columnStateModel != null && resultContextModel != null && tableStateModel != null) {
            manager = new TreeTableManager(
                    treeTableView,
                    searchField,
                    deleteColumnsButton,
                    pagination,
                    resultsCountLabel,
                    columnStateModel,
                    resultContextModel,
                    tableStateModel
            );
        } else {
            // Fallback pour la rétrocompatibilité
            manager = new TreeTableManager(
                    treeTableView,
                    searchField,
                    deleteColumnsButton,
                    pagination,
                    resultsCountLabel
            );
        }

        if (cleanColumnsButton != null) manager.setCleanButton(cleanColumnsButton);
        if (exportCsvButton  != null) manager.setExportCsvButton(exportCsvButton);
        if (exportXlsxButton != null) manager.setExportXlsxButton(exportXlsxButton);
        if (expandAllButton  != null) manager.setExpandAllButton(expandAllButton);
        if (collapseAllButton!= null) manager.setCollapseAllButton(collapseAllButton);

        return manager;
    }

    public VBox getTreeContainer() { return treeContainer; }
    public TreeTableView<ObservableList<String>> getTreeTableView() { return treeTableView; }
    public TextField getSearchField() { return searchField; }
    public Button getDeleteColumnsButton() { return deleteColumnsButton; }
    public Pagination getPagination() { return pagination; }
    public Label getResultsCountLabel() { return resultsCountLabel; }
    public Button getExportCsvButton() { return exportCsvButton; }
    public Button getExportXlsxButton() { return exportXlsxButton; }
    public Button getCleanColumnsButton() { return cleanColumnsButton; } // AJOUTÉ (Résout l'erreur 413)
    public Button getExpandAllButton() { return expandAllButton; }
    public Button getCollapseAllButton() { return collapseAllButton; }
    public Button getCleanButton() { return cleanColumnsButton; }

    public enum Feature { SEARCH, SELECTION, PAGINATION, EXPORT }
}