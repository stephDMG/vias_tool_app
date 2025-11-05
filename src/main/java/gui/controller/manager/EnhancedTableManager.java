package gui.controller.manager;

// Correction de l'import manquant
import formatter.ColumnValueFormatter;
import gui.controller.dialog.Dialog;
import gui.controller.manager.base.AbstractTableManager;
import gui.controller.model.ColumnStateModel;
import gui.controller.model.ResultContextModel;
import gui.controller.model.TableStateModel;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import model.RowData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Tabellen-Manager für TableView<ObservableList<String>>.
 *
 * <p>Implementiert die Table-spezifischen UI-Hooks auf Basis von AbstractTableManager
 * und behält die komplette öffentliche API für die Rückwärtskompatibilität.</p>
 */
public class EnhancedTableManager extends AbstractTableManager {

    private static final Logger log = LoggerFactory.getLogger(EnhancedTableManager.class);

    // UI (partenaires)
    private final TableView<ObservableList<String>> tableView;
    private final Button deleteButton;
    private Button cleanColumnsButton;

    // Données (pour le mode client-side search)
    private List<RowData> originalData = new ArrayList<>();

    // État
    private boolean selectionEnabled = false;

    // --- Rétrocompatibilité Constructors ---

    public EnhancedTableManager(TableView<ObservableList<String>> tableView,
                                TextField searchField,
                                Button deleteButton,
                                Pagination pagination,
                                Label resultsCountLabel) {
        this(tableView, searchField, deleteButton, pagination, resultsCountLabel,
                new TableStateModel(), new ColumnStateModel(), new ResultContextModel());
        log.warn("EnhancedTableManager instanziiert ohne Models. Lokale Modelle erstellt. Nur für Tests/Legacy-Kontext verwenden!");
    }

    // Le vrai constructeur (privé)
    private EnhancedTableManager(TableView<ObservableList<String>> tableView,
                                 TextField searchField,
                                 Button deleteButton,
                                 Pagination pagination,
                                 Label resultsCountLabel,
                                 TableStateModel stateModel,
                                 ColumnStateModel columnModel,
                                 ResultContextModel resultModel) {
        super(searchField, pagination, resultsCountLabel, stateModel, columnModel, resultModel);
        this.tableView = Objects.requireNonNull(tableView, "tableView");
        this.deleteButton = deleteButton;
        if (deleteButton != null) deleteButton.setDisable(true);
        installGroupRowFactory();
    }


    // NOUVEAU CONSTRUCTEUR COMPLET (utilisé par le Builder)
    public EnhancedTableManager(TableView<ObservableList<String>> tableView,
                                TextField searchField,
                                Button deleteButton,
                                Pagination pagination,
                                Label resultsCountLabel,
                                ColumnStateModel columnStateModel,
                                ResultContextModel resultContextModel,
                                TableStateModel tableStateModel) { // NOUVEAU
        super(searchField, pagination, resultsCountLabel, tableStateModel, columnStateModel, resultContextModel);
        this.tableView = Objects.requireNonNull(tableView, "tableView");
        this.deleteButton = deleteButton;
        if (deleteButton != null) deleteButton.setDisable(true);
        installGroupRowFactory();
    }



    // Ancien constructeur 7 args obsolète (pour la compatibilité de l'ancienne implémentation du Builder)
    @Deprecated
    public EnhancedTableManager(TableView<ObservableList<String>> tableView,
                                TextField searchField,
                                Button deleteButton,
                                Pagination pagination,
                                Label resultsCountLabel,
                                Object ignoredColumnStateModel,
                                Object ignoredResultContextModel) {
        // Tente de caster les arguments en modèles, sinon utilise des modèles par défaut
        this(tableView, searchField, deleteButton, pagination, resultsCountLabel,
                (ignoredColumnStateModel instanceof ColumnStateModel) ? (ColumnStateModel) ignoredColumnStateModel : new ColumnStateModel(),
                (ignoredResultContextModel instanceof ResultContextModel) ? (ResultContextModel) ignoredResultContextModel : new ResultContextModel(),
                new TableStateModel()
        );
    }

    public EnhancedTableManager(TableView<ObservableList<String>> tableView) {
        this(tableView, null, null, null, null);
    }
    // ---------- Méthodes publiques pour Rétrocompatibilité ----------

    public EnhancedTableManager enableSearch() {
        return (EnhancedTableManager) super.enableSearch();
    }

    public EnhancedTableManager enableSelection() {
        if (deleteButton == null) {
            log.warn("Selection angefordert, aber kein Delete-Button vorhanden.");
            return this;
        }
        selectionEnabled = true;
        tableView.getSelectionModel().setCellSelectionEnabled(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        tableView.getSelectionModel().getSelectedCells().addListener(
                (ListChangeListener<TablePosition>) c ->
                        deleteButton.setDisable(tableView.getSelectionModel().getSelectedCells().isEmpty())
        );
        deleteButton.setOnAction(e -> handleDeleteSelectedColumns());
        return this;
    }

    public EnhancedTableManager enablePagination(int rowsPerPage) {
        return (EnhancedTableManager) super.enablePagination(rowsPerPage);
    }

    public EnhancedTableManager setOnServerSearch(Consumer<String> handler) {
        return (EnhancedTableManager) super.setOnServerSearch(handler);
    }

    public EnhancedTableManager enableGroupStripingByHeader(String headerName) {
        // La logique est dans la classe mère, on appelle la méthode de la classe mère
        super.enableGroupStripingByHeader(headerName);
        return this;
    }

    public void populateTableView(List<RowData> data) {
        this.originalData = new ArrayList<>(data);
        super.populateTableView(data); // Appelle la logique de base
    }

    public void configureGrouping(String headerName, Color colorA, Color colorB) {
        // La logique est dans la classe mère, on appelle la méthode de la classe mère
        super.configureGrouping(headerName, colorA, colorB);
    }

    public void disableGrouping() {
        // La logique est dans la classe mère, on appelle la méthode de la classe mère
        super.disableGrouping();
    }

    public void bindAutoRowsPerPage(Region observedRegion) {
        super.bindAutoRowsPerPage(observedRegion);
    }

    // Verdrahtung von Buttons (Builder/Controller) - sans changer de signature
    public void setCleanButton(Button cleanButton) {
        this.cleanColumnsButton = cleanButton;
        if (cleanButton != null) {
            // La logique d'activation/désactivation est gérée par le modèle d'état des colonnes
            cleanButton.setOnAction(e -> cleanColumnsAllPages());
            // Problème 2-a/4-a: Désactiver si déjà nettoyé
            columnModel.cleanedProperty().addListener((obs, oldV, newV) ->
                    cleanColumnsButton.setDisable(newV || !hasData())
            );
            hasDataProperty().addListener((obs, oldV, newV) ->
                    cleanColumnsButton.setDisable(columnModel.isCleaned() || !newV)
            );
            // État initial
            cleanColumnsButton.setDisable(!hasData() || columnModel.isCleaned());
        }
    }

    public void setExportCsvButton(Button b) {
        // Dans l'architecture Cover, les boutons d'export sont bindés dans CoverDomainController.
        // Ici, on maintient l'API mais le binding du `hasDataProperty()` n'est pas le seul facteur.
    }
    public void setExportXlsxButton(Button b) {
        // Idem
    }

    public void enableCleanTable() {
        // Rétrocompatibilité, l'activation est gérée par setCleanButton
    }


    // --- Fonctions publiques (gestion des colonnes/export) ---

    public void handleDeleteSelectedColumns() {
        if (!selectionEnabled) return;

        ObservableList<TablePosition> selectedCells = tableView.getSelectionModel().getSelectedCells();
        if (selectedCells.isEmpty()) {
            Dialog.showErrorDialog("Keine Auswahl", "Bitte Spalten zum Löschen auswählen.");
            return;
        }

        List<TableColumn<ObservableList<String>, ?>> selectedColumns = selectedCells.stream()
                .map(pos -> (TableColumn<ObservableList<String>, ?>) pos.getTableColumn())
                .distinct()
                .collect(Collectors.toList());

        if (Dialog.showWarningDialog("Spalten löschen",
                selectedColumns.size() + " Spalte(n) löschen?")) {
            deleteColumns(selectedColumns);
        }
    }

    private void deleteColumns(List<TableColumn<ObservableList<String>, ?>> columnsToDelete) {
        Set<String> originalKeysToDelete = columnsToDelete.stream()
                .map(col -> String.valueOf(col.getUserData()))
                .collect(Collectors.toSet());

        // Mise à jour du ColumnStateModel (3-a: affecte les deux vues)
        Platform.runLater(() -> originalKeysToDelete.forEach(columnModel::addHiddenKey));

        // Note: La suppression physique et le rafraîchissement est géré par refreshView()
        // qui est appelé après la mise à jour du modèle.
    }

    // Correction: Méthode deleteColumn manquante (utilisée dans addContextMenuToColumn)
    private void deleteColumn(TableColumn<ObservableList<String>, String> column) {
        if (Dialog.showWarningDialog("Spalte löschen",
                "Möchten Sie die Spalte '" + column.getText() + "' wirklich löschen?")) {
            deleteColumns(List.of(column));
        }
    }

    public List<String> getDisplayHeaders() {
        return tableView.getColumns().stream().map(TableColumn::getText).collect(Collectors.toList());
    }

    public List<String> getOriginalKeys() {
        return tableView.getColumns().stream()
                .map(col -> String.valueOf(col.getUserData()))
                .collect(Collectors.toList());
    }

    @Override
    public void loadDataFromServer(int totalCount, DataLoader dataLoader) {
        // Nur Logging + Delegation an AbstractTableManager
        log.info("TABLE.loadDataFromServer: total={}, rowsPerPage={}", totalCount, stateModel.getRowsPerPage());
        super.loadDataFromServer(totalCount, dataLoader);
    }


    // Rétrocompatibilité: la fonction locale cleanTable n'est plus utilisée (Bereinigen est global)
    public void cleanTable() {
        cleanColumnsAllPages();
    }

    // ---------- Implémentation des Hooks abstraits ----------

    @Override
    protected List<RowData> getOriginalDataForClientFilter() {
        return originalData;
    }

    @Override
    protected void configureSearchSection(boolean visible) {
        // La visibilité du searchField est gérée dans le FXML/Builder
    }

    @Override
    protected List<String> currentHeaders() {
        return currentHeaders;
    }

    @Override
    protected int getVisibleRowCount() {
        return tableView.getItems() == null ? 0 : tableView.getItems().size();
    }

    @Override
    protected String getVisibleCellValue(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || columnIndex < 0) return null;
        var items = tableView.getItems();
        if (items == null || rowIndex >= items.size()) return null;
        var row = items.get(rowIndex);
        return (row != null && columnIndex < row.size()) ? row.get(columnIndex) : null;
    }

    @Override
    protected void installGroupRowFactory() {
        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(ObservableList<String> item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("");
                if (empty || item == null || isSelected()) return;
                if (!groupStripingEnabled || groupStripingHeader == null) return;
                if (groupColorA == null && groupColorB == null) return;

                int rowIndex = getIndex();
                if (rowIndex >= 0 && rowIndex < stripeIsA.size()) {
                    boolean isA = stripeIsA.get(rowIndex);
                    Color c = isA ? groupColorA : groupColorB;
                    if (c != null) {
                        int r = (int) Math.round(c.getRed() * 255);
                        int g = (int) Math.round(c.getGreen() * 255);
                        int b = (int) Math.round(c.getBlue() * 255);
                        String a = String.format(Locale.US, "%.3f", c.getOpacity());
                        setStyle("-fx-background-color: rgba(" + r + "," + g + "," + b + "," + a + ");");
                    }
                }
            }
        });
    }

    /**
     * RESTAURÉ: Liefert die aktuell gefilterten Daten der aktiven Seite/des Clients.
     * HINWEIS: Bei Server-Pagination nur die aktuelle Seite!
     * @return gefilterte Daten (der aktuellen Seite im Servermodus)
     */
    public List<RowData> getFilteredData() {
        // Dans le mode serveur, filteredData contient uniquement les données de la page courante.
        // Dans le mode client, filteredData contient toutes les données filtrées.
        return new ArrayList<>(filteredData);
    }


    public List<RowData> getOriginalData() {
        // Si nous sommes en pagination client (originalData existe)
        if (!serverPaginationEnabled) {
            return new ArrayList<>(this.originalData);
        }
        // Si nous sommes en pagination serveur, nous retournons filteredData (la page courante).
        // Ceci est une API cassée, mais nécessaire pour la rétrocompatibilité.
        return new ArrayList<>(filteredData);
    }

    @Override
    public void refreshView() {
        // 1. Déterminer les headers visibles (non masqués par ColumnStateModel)
        Set<String> hiddenKeys = columnModel.getHiddenKeys();

        // 2. Construire les colonnes visibles
        buildTableColumns(filteredData, hiddenKeys);

        // 3. Remplir les données (seulement pour les colonnes visibles)
        populateTableData(filteredData);

        // 4. Mettre à jour la pagination/compteur
        if (serverPaginationEnabled) {
            // La pagination serveur est gérée par loadServerPageData
        } else if (paginationEnabled && pagination != null) {
            int rowsPerPage = stateModel.getRowsPerPage();
            int pageCount = (int) Math.ceil((double) filteredData.size() / rowsPerPage);
            pagination.setPageCount(Math.max(pageCount, 1));
            pagination.setVisible(filteredData.size() > 0);
            pagination.setPageFactory(this::createClientPage);
        }

        recomputeGroupStripes();
        updateResultsCount();
    }

    private Node createClientPage(int pageIndex) {
        int rowsPerPage = stateModel.getRowsPerPage();
        int fromIndex = pageIndex * rowsPerPage;
        int toIndex = Math.min(fromIndex + rowsPerPage, filteredData.size());
        List<RowData> pageData = filteredData.subList(fromIndex, toIndex);

        // 1. Déterminer les headers visibles
        Set<String> hiddenKeys = columnModel.getHiddenKeys();

        // 2. Construire les colonnes visibles
        buildTableColumns(pageData, hiddenKeys);
        populateTableData(pageData);
        recomputeGroupStripes();

        return new Label();
    }

    private void buildTableColumns(List<RowData> data, Set<String> hiddenKeys) {
        if (data == null || data.isEmpty()) {
            tableView.getColumns().clear();
            currentHeaders = List.of();
            return;
        }

        // Obtient tous les headers, filtre les headers cachés
        List<String> allHeaders = new ArrayList<>(data.get(0).getValues().keySet());
        List<String> visibleHeaders = allHeaders.stream()
                .filter(h -> !hiddenKeys.contains(h))
                .toList();

        if (visibleHeaders.equals(currentHeaders) && !tableView.getColumns().isEmpty()) {
            return;
        }

        tableView.getColumns().clear();
        currentHeaders = visibleHeaders;

        for (int i = 0; i < visibleHeaders.size(); i++) {
            final int columnIndex = i;
            final String originalKey = visibleHeaders.get(i);
            TableColumn<ObservableList<String>, String> column = new TableColumn<>(originalKey);
            column.setUserData(originalKey);
            column.setCellValueFactory(param -> {
                ObservableList<String> row = param.getValue();
                return new SimpleStringProperty(
                        (row != null && columnIndex < row.size()) ? row.get(columnIndex) : ""
                );
            });
            addContextMenuToColumn(column);
            tableView.getColumns().add(column);
        }
    }

    private void populateTableData(List<RowData> data) {
        if (data == null || data.isEmpty() || currentHeaders.isEmpty()) {
            tableView.setItems(FXCollections.observableArrayList());
            return;
        }

        ObservableList<ObservableList<String>> tableData = FXCollections.observableArrayList();

        for (RowData row : data) {
            ObservableList<String> rowValues = FXCollections.observableArrayList();
            for (String header : currentHeaders) {
                // Correction: Utilise ColumnValueFormatter.format(row, header)
                String formattedValue = ColumnValueFormatter.format(row, header);
                rowValues.add(formattedValue);
            }
            tableData.add(rowValues);
        }
        tableView.setItems(tableData);
    }

    private void addContextMenuToColumn(TableColumn<ObservableList<String>, String> column) {
        MenuItem renameItem = new MenuItem("Spalte umbenennen");
        MenuItem deleteItem = new MenuItem("Spalte löschen");
        renameItem.setOnAction(e -> renameColumn(column));
        deleteItem.setOnAction(e -> deleteColumn(column));
        ContextMenu contextMenu = new ContextMenu(renameItem, new SeparatorMenuItem(), deleteItem);
        column.setContextMenu(contextMenu);
    }

    private void renameColumn(TableColumn<ObservableList<String>, String> column) {
        TextInputDialog dialog = new TextInputDialog(column.getText());
        dialog.setTitle("Spalte umbenennen");
        dialog.setHeaderText("Neuer Name für '" + column.getText() + "':");
        dialog.setContentText("Name:");
        dialog.showAndWait().ifPresent(newName -> {
            if (!newName.isBlank()) {
                column.setText(newName);
                tableView.refresh();
                log.info("Spalte umbenannt: {} -> {}", column.getUserData(), newName);
            }
        });
    }

    @Override
    protected void updateResultsCount() {
        if (resultsCountLabel == null) return;

        int countToDisplay;

        if (serverPaginationEnabled) {
            // ✅ Im Server-Modus immer das Ergebnis des ResultContextModels verwenden
            countToDisplay = resultModel.getTotalCount();
            // VORHER: if (stateModel.isSearchActive()) suffix = " – Suche aktiv";
        } else {
            // ✅ Im Client-Modus (nicht Server-Modus)
            countToDisplay = getVisibleRowCount();
            // VORHER: if (searchField != null && !searchField.getText().trim().isEmpty()) { // VORHER: suffix = " – Suche aktiv"; }
        }

        // Das KF-Label zeigt NUR die Gesamtanzahl an (ohne Suffix)
        resultsCountLabel.setText("(" + countToDisplay + " Ergebnis" + (countToDisplay == 1 ? "" : "se") + ")");

        log.debug("Update KF-Label: ServerModus={}, Gesamt={}", serverPaginationEnabled, countToDisplay);
    }




    @Override
    protected void clearView() {
        tableView.getColumns().clear();
        tableView.setItems(FXCollections.observableArrayList());
        if (pagination != null) pagination.setVisible(false);
    }

    @Override
    protected void requestRefresh() {
        tableView.refresh();
    }

    @Override
    protected void disableCleanButtonOnCleaned(javafx.collections.SetChangeListener.Change<? extends String> c) {
        // La logique est gérée directement dans setCleanButton pour plus de contrôle
    }


}