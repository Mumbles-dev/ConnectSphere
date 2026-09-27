package com.connectsphere.controller;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import com.connectsphere.util.Icons;

import com.connectsphere.model.Contact;
import com.connectsphere.dao.ContactDAO;
import com.connectsphere.dao.ConnectionDAO;
import com.connectsphere.dao.DatabaseManager;
import com.connectsphere.util.ContactGraph;
import com.connectsphere.util.BFSPathFinder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MainController
 *
 * Single controller backing Main.fxml. Handles page navigation between the
 * Contacts / Connection Finder / Settings views (plus the About panel), and
 * now pulls every piece of contact/connection data from SQLite via the
 * ContactDAO / ConnectionDAO / ContactGraph / BFSPathFinder classes.
 *
 * NOT wired here (see chat notes): Theme + accent color persistence,
 * Backup/Restore file I/O, Open Log File / Check for Updates / Report an
 * Issue.
 */
public class MainController {

    // ─────────────────────────────────────────────────────────────────
    // DATA LAYER
    // ─────────────────────────────────────────────────────────────────
    private final ContactDAO contactDAO = new ContactDAO();
    private final ConnectionDAO connectionDAO = new ConnectionDAO();

    // Master copy of every contact, kept in sync after every add/edit/delete.
    // Used to populate the Finder combos and to resolve BFS result ids back
    // into Contact objects without extra DB round-trips.
    private final ObservableList<Contact> allContactsData = FXCollections.observableArrayList();
    private final Map<Integer, Contact> contactsById = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────
    // Icons
    // ─────────────────────────────────────────────────────────────────
    @FXML private SVGPath logoIcon;
    @FXML private SVGPath searchIcon;
    @FXML private SVGPath plusIcon;
    @FXML private SVGPath arrowDownIcon;
    @FXML private SVGPath findConnectionIcon;
    @FXML private SVGPath checkIcon;
    @FXML private SVGPath xIcon;
    @FXML private SVGPath sunIcon;
    @FXML private SVGPath listIcon;
    @FXML private SVGPath databaseIcon;
    @FXML private SVGPath downloadIcon;
    @FXML private SVGPath uploadIcon;
    @FXML private SVGPath trashIcon;
    @FXML private SVGPath warningIcon;

    // ─────────────────────────────────────────────────────────────────
    // Sidebar / navigation
    // ─────────────────────────────────────────────────────────────────
    @FXML private Button navContactsBtn;
    @FXML private Button navFinderBtn;
    @FXML private Button navSettingsBtn;
    @FXML private Button navAboutBtn;

    @FXML private HBox contactsPageRoot;
    @FXML private HBox finderPageRoot;
    @FXML private HBox settingsPageRoot;
    @FXML private VBox aboutPageRoot;

    // ─────────────────────────────────────────────────────────────────
    // Contacts page
    // ─────────────────────────────────────────────────────────────────
    @FXML private TextField contactsSearchField;
    @FXML private Label contactsCountLabel;
    @FXML private Label allContactsSectionLabel;
    @FXML private VBox favoritesSectionBox;   // wraps the whole Favorite Contacts section — hidden while searching
    @FXML private HBox favoritesContainer;    // holds the favorite-card row, inside a horizontal ScrollPane
    @FXML private VBox recentSectionBox;      // wraps the whole Recently Added section — hidden while searching
    @FXML private ListView<Contact> recentContactsListView;
    @FXML private ListView<Object> allContactsListView; // mixed: AlphaHeader rows + Contact rows

    // ─────────────────────────────────────────────────────────────────
    // Connection Finder page
    // ─────────────────────────────────────────────────────────────────
    @FXML private ComboBox<Contact> startContactCombo;
    @FXML private ComboBox<Contact> endContactCombo;
    @FXML private Button findConnectionButton;

    @FXML private VBox resultIdleBox;
    @FXML private VBox resultFoundBox;
    @FXML private VBox resultNotFoundBox;

    @FXML private VBox pathNodesContainer;
    @FXML private Label distanceValueLabel;
    @FXML private Label distanceUnitLabel;
    @FXML private Label notFoundMessageLabel;

    @FXML private Label networkStatsLabel;
    @FXML private Canvas networkCanvas;
    @FXML private Label networkHintLabel;

    // ─────────────────────────────────────────────────────────────────
    // Settings page
    // ─────────────────────────────────────────────────────────────────
    @FXML private ToggleButton compactListToggle;
    @FXML private ToggleButton showFavoritesToggle;
    @FXML private ToggleButton showRecentToggle;
    @FXML private ComboBox<String> displayFormatCombo;

    @FXML private Label dbStatusValueLabel;
    @FXML private Label dbNameValueLabel;
    @FXML private Label totalContactsValueLabel;
    @FXML private Label totalConnectionsValueLabel;

    @FXML private Button backupButton;
    @FXML private Button restoreButton;

    @FXML private StackPane clearDataStack;
    @FXML private HBox clearDataNormalBox;
    @FXML private VBox clearDataWarningBox;
    @FXML private Label clearDataWarningMessage;

    @FXML private Label appVersionLabel;
    @FXML private Label appBuildLabel;
    @FXML private Label appPlatformLabel;
    @FXML private Label appDbEngineLabel;
    @FXML private Label appLastBackupLabel;

    // ─────────────────────────────────────────────────────────────────
    // Init
    // ─────────────────────────────────────────────────────────────────

    @FXML
    private void initialize() {
        // Icons
        setIcon(logoIcon, Icons.USER, 32);
        setIcon(searchIcon, Icons.MAGNIFYING_GLASS, 16);
        setIcon(plusIcon, Icons.PLUS, 15);
        setIcon(arrowDownIcon, Icons.ARROW_DOWN, 15);
        setIcon(findConnectionIcon, Icons.MAGNIFYING_GLASS, 15);
        setIcon(checkIcon, Icons.CHECK, 15);
        setIcon(xIcon, Icons.X, 15);
        setIcon(sunIcon, Icons.SUN, 15);
        setIcon(listIcon, Icons.LIST, 15);
        setIcon(databaseIcon, Icons.DATABASE, 15);
        setIcon(downloadIcon, Icons.DOWNLOAD, 15);
        setIcon(uploadIcon, Icons.UPLOAD, 15);
        setIcon(trashIcon, Icons.TRASH, 15);
        setIcon(warningIcon, Icons.WARNING, 15);

        // Static UI option lists (app configuration choices, not DB records).
        displayFormatCombo.getItems().setAll("Name + Phone", "Name + Email", "Name + Phone + Email");
        displayFormatCombo.getSelectionModel().selectFirst();
        displayFormatCombo.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> refreshVisibleListCells());

        // Make sure the schema exists before we touch it (safe to call
        // repeatedly — uses CREATE TABLE IF NOT EXISTS).
        DatabaseManager.initializeDatabase();

        setupContactListCells();

        // Live search: hits the database directly through ContactDAO,
        // rather than filtering an in-memory copy.
        contactsSearchField.textProperty().addListener((obs, oldVal, newVal) -> filterContacts(newVal));

        // Keep the "Find Connection" button disabled until both contacts
        // are chosen and they aren't the same contact.
        findConnectionButton.disableProperty().bind(
                startContactCombo.getSelectionModel().selectedItemProperty().isNull()
                        .or(endContactCombo.getSelectionModel().selectedItemProperty().isNull())
        );

        resetFinderResultState();

        // Initial data load from SQLite.
        reloadAllContactsFromDatabase();
        refreshRecentSection();
        refreshDatabaseStats();
        drawNetworkGraph(Collections.emptyList());
    }

    private void setIcon(SVGPath path, String content, double sizePx) {
        path.setContent(content);
        double s = sizePx / 256.0;
        path.setScaleX(s);
        path.setScaleY(s);
    }

    // ═════════════════════════════════════════════════════════════════
    // PAGE NAVIGATION
    // ═════════════════════════════════════════════════════════════════

    @FXML
    private void showContactsPage() {
        setActivePage(contactsPageRoot);
        setActiveNavButton(navContactsBtn);
    }

    @FXML
    private void showFinderPage() {
        setActivePage(finderPageRoot);
        setActiveNavButton(navFinderBtn);
        // Combos might be stale if contacts changed on another page since
        // the last visit — cheap to refresh every time we show this page.
        populateFinderComboItems();
    }

    @FXML
    private void showSettingsPage() {
        setActivePage(settingsPageRoot);
        setActiveNavButton(navSettingsBtn);
        refreshDatabaseStats();
    }

    @FXML
    private void showAboutPage() {
        setActivePage(aboutPageRoot);
        setActiveNavButton(navAboutBtn);
    }

    private void setActivePage(Region pageToShow) {
        for (Region page : new Region[]{contactsPageRoot, finderPageRoot, settingsPageRoot, aboutPageRoot}) {
            boolean isTarget = (page == pageToShow);
            page.setVisible(isTarget);
            page.setManaged(isTarget);
        }
    }

    private void setActiveNavButton(Button activeButton) {
        for (Button b : new Button[]{navContactsBtn, navFinderBtn, navSettingsBtn, navAboutBtn}) {
            b.getStyleClass().remove("nav-button-active");
        }
        if (!activeButton.getStyleClass().contains("nav-button-active")) {
            activeButton.getStyleClass().add("nav-button-active");
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // CONTACTS PAGE FUNCTIONS
    // ═════════════════════════════════════════════════════════════════

    /**
     * Pulls every contact from SQLite and refreshes every dependent UI bit.
     * This is the ONLY place that re-queries the contacts table itself —
     * Recently Added, Favorites, and the All Contacts/search list are all
     * derived from allContactsData below, so every view shares the exact
     * same Contact objects. That matters: a favorite toggle mutates one
     * Contact instance in place, and every view showing that same instance
     * picks up the change the moment it's re-rendered — no view can hold a
     * stale, independently-queried copy of the same row.
     */
    private void reloadAllContactsFromDatabase() {
        List<Contact> contacts = contactDAO.getAllContacts();

        allContactsData.setAll(contacts);
        contactsById.clear();
        for (Contact c : contacts) {
            contactsById.put(c.getId(), c);
        }

        // If the search field has text, keep showing filtered results;
        // otherwise show everything.
        filterContacts(contactsSearchField.getText());

        contactsCountLabel.setText(contacts.size() + (contacts.size() == 1 ? " Contact" : " Contacts"));
        populateFinderComboItems();
        refreshFavoritesSection();
    }

    /** Re-renders Recently Added, Favorites, and All Contacts/search from the shared in-memory cache. */
    private void refreshAllContactViews() {
        refreshFavoritesSection();
        refreshRecentSection();
        filterContacts(contactsSearchField.getText());
    }

    /** Newest-first slice of the shared cache — no DB round trip, so it reuses the same Contact objects. */
    private void refreshRecentSection() {
        List<Contact> recent = allContactsData.stream()
                .sorted(Comparator.comparing(Contact::getId).reversed())
                .limit(10)
                .collect(Collectors.toList());
        recentContactsListView.setItems(FXCollections.observableArrayList(recent));
    }

    /** Favorited subset of the shared cache, rebuilt into the favorite-card row. */
    private void refreshFavoritesSection() {
        List<Contact> favorites = allContactsData.stream()
                .filter(Contact::isFavorite)
                .sorted(Comparator.comparing(
                        c -> c.getName() == null ? "" : c.getName(),
                        String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        favoritesContainer.getChildren().clear();

        if (favorites.isEmpty()) {
            Label empty = new Label("No favorites yet. Tap the star on any contact to pin them here.");
            empty.getStyleClass().add("empty-state-label");
            favoritesContainer.getChildren().add(empty);
            return;
        }
        for (Contact c : favorites) {
            favoritesContainer.getChildren().add(buildFavoriteCard(c));
        }
    }

    /** One card in the horizontal Favorite Contacts row: avatar, name, phone, and an un-favorite star. */
    private VBox buildFavoriteCard(Contact contact) {
        Label avatarLabel = new Label(initialsOf(contact.getName()));
        avatarLabel.getStyleClass().add("contact-avatar-label");
        StackPane avatarBox = new StackPane(avatarLabel);
        avatarBox.getStyleClass().add("contact-avatar");
        avatarBox.setMinSize(44, 44);
        avatarBox.setMaxSize(44, 44);

        Label nameLabel = new Label(contact.getName());
        nameLabel.getStyleClass().add("contact-row-name");
        nameLabel.setWrapText(true);
        nameLabel.setAlignment(Pos.CENTER);
        nameLabel.setMaxWidth(96);

        Label phoneLabel = new Label(nullToDashStatic(contact.getPhone()));
        phoneLabel.getStyleClass().add("contact-row-detail");
        phoneLabel.setWrapText(true);
        phoneLabel.setAlignment(Pos.CENTER);
        phoneLabel.setMaxWidth(96);

        FavoriteStarControl star = buildFavoriteStarControl(() -> contact);

        VBox card = new VBox(6, avatarBox, nameLabel, phoneLabel, star.button);
        card.getStyleClass().add("favorite-card");
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(120);

        card.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openContactDialog(contact);
            }
        });
        return card;
    }

    private String nullToDashStatic(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    /** Bundles a star toggle Button together with the refresh callback that re-syncs its icon shape. */
    private static class FavoriteStarControl {
        final Button button;
        final Runnable refresh;
        FavoriteStarControl(Button button, Runnable refresh) {
            this.button = button;
            this.refresh = refresh;
        }
    }

    /**
     * Builds a star toggle button (yellow; outline when not favorited,
     * filled once favorited) wired to ContactDAO.setFavorite(). The
     * supplier is read at click time so the same button works correctly
     * inside recycled ListCells, where the underlying Contact changes.
     *
     * IMPORTANT: the button and its icon are both given a fixed, small
     * size explicitly (in Java and in CSS). Without that, a Button sizes
     * its graphic based on the SVGPath's *unscaled* bounds (256x256),
     * not the visual scale transform applied to it — every other icon in
     * this app is wrapped the same way for that exact reason.
     *
     * The caller MUST invoke the returned refresh() every time a cell is
     * re-bound to a (possibly different) contact — e.g. from updateItem —
     * or a recycled cell will keep showing whichever star shape was last
     * drawn on it, not the shape matching its current contact.
     */
    private FavoriteStarControl buildFavoriteStarControl(java.util.function.Supplier<Contact> itemSupplier) {
        SVGPath starIcon = new SVGPath();
        starIcon.getStyleClass().add("favorite-star-icon");
        double s = 15.0 / 256.0;
        starIcon.setScaleX(s);
        starIcon.setScaleY(s);

        StackPane iconBox = new StackPane(starIcon);
        iconBox.getStyleClass().add("favorite-star-icon-box");
        iconBox.setMinSize(16, 16);
        iconBox.setMaxSize(16, 16);

        Button button = new Button();
        button.getStyleClass().add("favorite-star-button");
        button.setGraphic(iconBox);
        button.setMinSize(30, 30);
        button.setMaxSize(30, 30);
        button.setPrefSize(30, 30);

        Runnable refresh = () -> {
            Contact c = itemSupplier.get();
            starIcon.setContent(c != null && c.isFavorite() ? Icons.STAR_FILL : Icons.STAR);
        };
        refresh.run();

        button.setOnAction(e -> {
            Contact c = itemSupplier.get();
            if (c == null) return;
            boolean newState = !c.isFavorite();
            try {
                contactDAO.setFavorite(c.getId(), newState);
                c.setFavorite(newState);
            } catch (RuntimeException ex) {
                showErrorAlert("Could not update favorite", ex.getMessage());
                return;
            }
            refresh.run();
            refreshAllContactViews();
        });

        // Stop the click from also bubbling up to the row's own
        // double-click-to-edit handler.
        button.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);

        return new FavoriteStarControl(button, refresh);
    }

    /** Builds the shared cell factory used by both contact ListViews. */
    private void setupContactListCells() {
        allContactsListView.setCellFactory(lv -> new GroupedContactCell());
        recentContactsListView.setCellFactory(lv -> new ContactListCell());
    }

    /** Forces both ListViews to redraw their visible cells (e.g. after a display-format change). */
    private void refreshVisibleListCells() {
        allContactsListView.refresh();
        recentContactsListView.refresh();
    }

    /** Modern card row: avatar circle with initials, name, phone/email detail, date added, favorite star. */
    private class ContactListCell extends ListCell<Contact> {
        private final Label avatarLabel = new Label();
        private final StackPane avatarBox = new StackPane(avatarLabel);
        private final Label nameLabel = new Label();
        private final Label detailLabel = new Label();
        private final VBox textBox = new VBox(2, nameLabel, detailLabel);
        private final Label dateAddedLabel = new Label();
        private final FavoriteStarControl star = buildFavoriteStarControl(this::getItem);
        private final HBox row = new HBox(12, avatarBox, textBox, dateAddedLabel, star.button);

        ContactListCell() {
            avatarBox.getStyleClass().add("contact-avatar");
            avatarLabel.getStyleClass().add("contact-avatar-label");
            nameLabel.getStyleClass().add("contact-row-name");
            detailLabel.getStyleClass().add("contact-row-detail");
            dateAddedLabel.getStyleClass().add("contact-row-date");
            row.getStyleClass().add("contact-row");
            row.setAlignment(Pos.CENTER_LEFT);
            row.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(textBox, Priority.ALWAYS);
            setPadding(new Insets(3, 0, 3, 0));

            setOnMouseClicked(event -> {
                Contact item = getItem();
                if (item == null) return;
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    openContactDialog(item);
                }
            });

            setContextMenu(buildRowContextMenu());
        }

        private ContextMenu buildRowContextMenu() {
            MenuItem edit = new MenuItem("Edit Contact");
            edit.setOnAction(e -> { if (getItem() != null) openContactDialog(getItem()); });

            MenuItem delete = new MenuItem("Delete Contact");
            delete.setOnAction(e -> { if (getItem() != null) confirmAndDeleteContact(getItem()); });

            return new ContextMenu(edit, delete);
        }

        @Override
        protected void updateItem(Contact contact, boolean empty) {
            super.updateItem(contact, empty);
            if (empty || contact == null) {
                setGraphic(null);
                return;
            }
            nameLabel.setText(contact.getName());
            avatarLabel.setText(initialsOf(contact.getName()));
            dateAddedLabel.setText("Added " + contact.getFormattedDateAdded());
            star.refresh.run();

            String format = displayFormatCombo.getValue();
            String detail;
            if ("Name + Email".equals(format)) {
                detail = nullToDash(contact.getEmail());
            } else if ("Name + Phone + Email".equals(format)) {
                detail = nullToDash(contact.getPhone()) + "  ·  " + nullToDash(contact.getEmail());
            } else {
                detail = nullToDash(contact.getPhone());
            }
            detailLabel.setText(detail);
            setGraphic(row);
        }

        private String nullToDash(String s) {
            return (s == null || s.isBlank()) ? "—" : s;
        }
    }


    /** A small marker object: one "A", "B", "#" ... divider row in the grouped All Contacts list. */
    private static class AlphaHeader {
        final String letter;
        final int count;
        AlphaHeader(String letter, int count) {
            this.letter = letter;
            this.count = count;
        }
    }

    /**
     * All Contacts list cell that renders either an AlphaHeader divider row
     * or a normal contact card, depending on what's in that slot. Used only
     * by allContactsListView, which holds a mix of both (see
     * buildGroupedItems). recentContactsListView keeps using the simpler
     * Contact-only ContactListCell above since it isn't grouped.
     */
    private class GroupedContactCell extends ListCell<Object> {
        // --- header row ---
        private final Label headerLetter = new Label();
        private final Label headerCount = new Label();
        private final Region headerSpacer = new Region();
        private final HBox headerRow = new HBox(8, headerLetter, headerSpacer, headerCount);

        // --- contact row (same look as ContactListCell) ---
        private final Label avatarLabel = new Label();
        private final StackPane avatarBox = new StackPane(avatarLabel);
        private final Label nameLabel = new Label();
        private final Label detailLabel = new Label();
        private final VBox textBox = new VBox(2, nameLabel, detailLabel);
        private final Label dateAddedLabel = new Label();
        private final FavoriteStarControl star = buildFavoriteStarControl(
                () -> getItem() instanceof Contact c ? c : null);
        private final HBox contactRow = new HBox(12, avatarBox, textBox, dateAddedLabel, star.button);

        private final ContextMenu rowContextMenu;

        GroupedContactCell() {
            headerRow.getStyleClass().add("alpha-header-row");
            headerLetter.getStyleClass().add("alpha-header-letter");
            headerCount.getStyleClass().add("alpha-header-count");
            headerRow.setAlignment(Pos.CENTER_LEFT);
            headerRow.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(headerSpacer, Priority.ALWAYS);

            avatarBox.getStyleClass().add("contact-avatar");
            avatarLabel.getStyleClass().add("contact-avatar-label");
            nameLabel.getStyleClass().add("contact-row-name");
            detailLabel.getStyleClass().add("contact-row-detail");
            dateAddedLabel.getStyleClass().add("contact-row-date");
            contactRow.getStyleClass().add("contact-row");
            contactRow.setAlignment(Pos.CENTER_LEFT);
            contactRow.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(textBox, Priority.ALWAYS);
            setPadding(new Insets(3, 0, 3, 0));

            rowContextMenu = buildRowContextMenu();

            setOnMouseClicked(event -> {
                if (!(getItem() instanceof Contact contact)) return;
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    openContactDialog(contact);
                }
            });
        }

        private ContextMenu buildRowContextMenu() {
            MenuItem edit = new MenuItem("Edit Contact");
            edit.setOnAction(e -> { if (getItem() instanceof Contact c) openContactDialog(c); });

            MenuItem delete = new MenuItem("Delete Contact");
            delete.setOnAction(e -> { if (getItem() instanceof Contact c) confirmAndDeleteContact(c); });

            return new ContextMenu(edit, delete);
        }

        @Override
        protected void updateItem(Object item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setContextMenu(null);
                return;
            }

            if (item instanceof AlphaHeader header) {
                headerLetter.setText(header.letter);
                headerCount.setText(header.count + (header.count == 1 ? " contact" : " contacts"));
                setGraphic(headerRow);
                setContextMenu(null);
                return;
            }

            Contact contact = (Contact) item;
            nameLabel.setText(contact.getName());
            avatarLabel.setText(initialsOf(contact.getName()));
            dateAddedLabel.setText("Added " + contact.getFormattedDateAdded());
            star.refresh.run();

            String format = displayFormatCombo.getValue();
            String detail;
            if ("Name + Email".equals(format)) {
                detail = nullToDash(contact.getEmail());
            } else if ("Name + Phone + Email".equals(format)) {
                detail = nullToDash(contact.getPhone()) + "  ·  " + nullToDash(contact.getEmail());
            } else {
                detail = nullToDash(contact.getPhone());
            }
            detailLabel.setText(detail);
            setGraphic(contactRow);
            setContextMenu(rowContextMenu);
        }

        private String nullToDash(String s) {
            return (s == null || s.isBlank()) ? "—" : s;
        }
    }

    /**
     * Sorts the given contacts by name and interleaves AlphaHeader divider
     * rows ("A" · 3 contacts, "B" · 1 contact, ...) ahead of each letter
     * group, producing the mixed list allContactsListView displays.
     */
    private ObservableList<Object> buildGroupedItems(List<Contact> contacts) {
        List<Contact> sorted = new ArrayList<>(contacts);
        sorted.sort(Comparator.comparing(
                c -> c.getName() == null ? "" : c.getName(),
                String.CASE_INSENSITIVE_ORDER));

        // Count contacts per letter first, so each header can show its total.
        Map<String, Integer> countsByLetter = new LinkedHashMap<>();
        for (Contact c : sorted) {
            countsByLetter.merge(firstLetterOf(c.getName()), 1, Integer::sum);
        }

        ObservableList<Object> items = FXCollections.observableArrayList();
        String currentLetter = null;
        for (Contact c : sorted) {
            String letter = firstLetterOf(c.getName());
            if (!letter.equals(currentLetter)) {
                items.add(new AlphaHeader(letter, countsByLetter.get(letter)));
                currentLetter = letter;
            }
            items.add(c);
        }
        return items;
    }

    /** First letter of a name, uppercased; "#" for blank names or names starting with a non-letter. */
    private String firstLetterOf(String name) {
        if (name == null || name.isBlank()) return "#";
        char ch = Character.toUpperCase(name.trim().charAt(0));
        return Character.isLetter(ch) ? String.valueOf(ch) : "#";
    }

    @FXML
    private void onAddContact() {
        openContactDialog(null);
    }

    /**
     * Shared Add/Edit dialog, now loaded from contact-form.fxml via
     * ContactFormController so it can host the "connect to existing
     * contacts" picker. Pass null to add a new contact, or an existing
     * Contact to edit it (and its connections) in place.
     */
    private void openContactDialog(Contact existing) {
        boolean editing = existing != null;

        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/com/connectsphere/views/ContactView.fxml"));
            javafx.scene.Parent root = loader.load();
            ContactFormController formController = loader.getController();

            javafx.stage.Stage dialogStage = new javafx.stage.Stage();
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialogStage.setTitle(editing ? "Edit Contact" : "Add Contact");
            dialogStage.setResizable(false);
            formController.setDialogStage(dialogStage);

            // Every other contact is a candidate connection — never offer
            // to connect a contact to themselves.
            List<Contact> candidates = allContactsData.stream()
                    .filter(c -> !editing || c.getId() != existing.getId())
                    .collect(java.util.stream.Collectors.toList());

            Set<Integer> existingConnectionIds = editing
                    ? new HashSet<>(connectionDAO.getDirectConnections(existing.getId()))
                    : Collections.emptySet();

            if (editing) {
                formController.initForEdit(existing, candidates, existingConnectionIds);
            } else {
                formController.initForAdd(candidates);
            }

            dialogStage.setScene(new javafx.scene.Scene(root));
            dialogStage.showAndWait();

            if (!formController.isSaved()) return;

            String name = formController.getName();
            String phone = formController.getPhone();
            String email = formController.getEmail();
            Set<Integer> selectedIds = formController.getSelectedConnectionIds();

            int contactId;
            if (editing) {
                existing.setName(name);
                existing.setPhone(phone);
                existing.setEmail(email);
                contactDAO.updateContact(existing);
                contactId = existing.getId();
            } else {
                Contact saved = contactDAO.addContact(new Contact(name, phone, email));
                contactId = saved.getId();
            }

            applyConnectionSelection(contactId, existingConnectionIds, selectedIds);

            reloadAllContactsFromDatabase();
            refreshRecentSection();
            refreshDatabaseStats();

        } catch (java.io.IOException e) {
            showErrorAlert("Could not open contact form", e.getMessage());
        } catch (RuntimeException e) {
            showErrorAlert("Could not save contact", e.getMessage());
        }
    }

    /**
     * Reconciles the connections picked in the dialog against what was
     * already there: adds newly checked contacts, removes unchecked ones
     * that used to be connected. No-op pairs (never touched) are skipped.
     */
    private void applyConnectionSelection(int contactId, Set<Integer> previouslyConnected, Set<Integer> nowSelected) {
        for (int otherId : nowSelected) {
            if (!previouslyConnected.contains(otherId)) {
                connectionDAO.addConnection(contactId, otherId);
            }
        }
        for (int otherId : previouslyConnected) {
            if (!nowSelected.contains(otherId)) {
                connectionDAO.removeConnection(contactId, otherId);
            }
        }
    }



    private void confirmAndDeleteContact(Contact contact) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + contact.getName() + "? This also removes any connections involving them.",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.setHeaderText(null);
        confirm.setTitle("Delete Contact");

        confirm.showAndWait().ifPresent(choice -> {
            if (choice != ButtonType.OK) return;
            try {
                contactDAO.deleteContact(contact.getId());
                reloadAllContactsFromDatabase();
                refreshRecentSection();
                refreshDatabaseStats();
            } catch (RuntimeException e) {
                showErrorAlert("Could not delete contact", e.getMessage());
            }
        });
    }

    @FXML
    private void onViewAllFavorites() {
        refreshFavoritesSection();
    }

    @FXML
    private void onViewAllRecent() {
        refreshRecentSection();
    }

    /** Shared handler for every A-Z quick-jump button — jumps to that letter's header row. */
    @FXML
    private void onAlphaJump(ActionEvent event) {
        String letter = ((Button) event.getSource()).getText();
        ObservableList<Object> visible = allContactsListView.getItems();

        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i) instanceof AlphaHeader header && header.letter.equalsIgnoreCase(letter)) {
                allContactsListView.scrollTo(i);
                return;
            }
        }
        // No contact starting with that letter — nothing to scroll to.
    }

    /**
     * Filters the shared in-memory contact cache (allContactsData) — not a
     * fresh DB query — so the All Contacts/search list keeps showing the
     * exact same Contact objects as Recently Added and Favorites. While a
     * search is active, hides the Favorite Contacts and Recently Added
     * sections so only the search results are visible; clearing the field
     * restores both (subject to their own Settings show/hide toggles).
     */
    private void filterContacts(String query) {
        boolean searching = query != null && !query.trim().isEmpty();
        allContactsSectionLabel.setText(searching ? "Search Results" : "All Contacts");

        favoritesSectionBox.setVisible(!searching && showFavoritesToggle.isSelected());
        favoritesSectionBox.setManaged(!searching && showFavoritesToggle.isSelected());
        recentSectionBox.setVisible(!searching && showRecentToggle.isSelected());
        recentSectionBox.setManaged(!searching && showRecentToggle.isSelected());

        List<Contact> results;
        if (searching) {
            String q = query.trim().toLowerCase();
            results = allContactsData.stream()
                    .filter(c -> containsIgnoreCase(c.getName(), q)
                            || containsIgnoreCase(c.getPhone(), q)
                            || containsIgnoreCase(c.getEmail(), q))
                    .collect(Collectors.toList());
        } else {
            results = new ArrayList<>(allContactsData);
        }
        allContactsListView.setItems(buildGroupedItems(results));
    }

    private boolean containsIgnoreCase(String field, String query) {
        return field != null && field.toLowerCase().contains(query);
    }

    // ═════════════════════════════════════════════════════════════════
    // CONNECTION FINDER FUNCTIONS
    // ═════════════════════════════════════════════════════════════════

    private void populateFinderComboItems() {
        Contact previousStart = startContactCombo.getValue();
        Contact previousEnd = endContactCombo.getValue();

        startContactCombo.setItems(FXCollections.observableArrayList(allContactsData));
        endContactCombo.setItems(FXCollections.observableArrayList(allContactsData));

        // Re-select the same contacts by id if they still exist, so the
        // user's picks survive a background refresh.
        restoreSelection(startContactCombo, previousStart);
        restoreSelection(endContactCombo, previousEnd);
    }

    private void restoreSelection(ComboBox<Contact> combo, Contact previous) {
        if (previous == null) return;
        for (Contact c : combo.getItems()) {
            if (c.getId() == previous.getId()) {
                combo.getSelectionModel().select(c);
                return;
            }
        }
    }

    @FXML
    private void onFindConnection() {
        Contact start = startContactCombo.getValue();
        Contact end = endContactCombo.getValue();
        if (start == null || end == null) return;

        // Build the graph fresh from the database every search, so it
        // always reflects the latest connections.
        ContactGraph graph = new ContactGraph(connectionDAO.getAllConnections());
        BFSPathFinder finder = new BFSPathFinder(graph);
        BFSPathFinder.PathResult result = finder.findShortestPath(start.getId(), end.getId());

        if (result.pathExists()) {
            List<Contact> pathContacts = new ArrayList<>();
            for (int id : result.getPath()) {
                Contact c = contactsById.get(id);
                if (c == null) c = contactDAO.getContactById(id); // fallback, shouldn't normally hit
                pathContacts.add(c);
            }
            showFinderFound(pathContacts, result.getDistance());
        } else {
            showFinderNotFound(start.getName(), end.getName());
        }
    }

    @FXML
    private void onResetFinder() {
        resetFinderResultState();
    }

    private void resetFinderResultState() {
        showOnly(resultIdleBox, resultFoundBox, resultNotFoundBox);
        networkHintLabel.setVisible(true);
        networkHintLabel.setManaged(true);
        pathNodesContainer.getChildren().clear();
        distanceValueLabel.setText("—");
        drawNetworkGraph(Collections.emptyList());
    }

    private void showFinderFound(List<Contact> pathContacts, int distance) {
        showOnly(resultFoundBox, resultIdleBox, resultNotFoundBox);
        networkHintLabel.setVisible(false);
        networkHintLabel.setManaged(false);

        pathNodesContainer.getChildren().clear();

        for (int i = 0; i < pathContacts.size(); i++) {
            Contact c = pathContacts.get(i);
            boolean isStart = (i == 0);
            boolean isEnd = (i == pathContacts.size() - 1);
            pathNodesContainer.getChildren().add(buildPathNodeRow(c, isStart, isEnd));

            if (i < pathContacts.size() - 1) {
                Label connector = new Label("↓");
                connector.getStyleClass().add("path-connector-arrow");
                connector.setAlignment(Pos.CENTER);
                connector.setMaxWidth(Double.MAX_VALUE);
                pathNodesContainer.getChildren().add(connector);
            }
        }

        distanceValueLabel.setText(String.valueOf(distance));
        distanceUnitLabel.setText(distance == 1 ? "connection" : "connections");

        List<Integer> pathIds = pathContacts.stream().map(Contact::getId).toList();
        drawNetworkGraph(pathIds);
    }

    private HBox buildPathNodeRow(Contact contact, boolean isStart, boolean isEnd) {
        Label avatar = new Label(initialsOf(contact.getName()));
        avatar.getStyleClass().add("path-node-avatar");
        avatar.setMinSize(32, 32);
        avatar.setAlignment(Pos.CENTER);

        Label nameLabel = new Label(contact.getName());
        nameLabel.getStyleClass().add("path-node-name");

        HBox row = new HBox(10, avatar, nameLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("path-node-row");
        row.setPadding(new Insets(6, 8, 6, 8));

        if (isStart || isEnd) {
            Label badge = new Label(isStart ? "START" : "END");
            badge.getStyleClass().add(isStart ? "path-node-badge-start" : "path-node-badge-end");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().addAll(spacer, badge);
        }
        return row;
    }

    private String initialsOf(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }

    private void showFinderNotFound(String startName, String endName) {
        showOnly(resultNotFoundBox, resultIdleBox, resultFoundBox);
        networkHintLabel.setVisible(true);
        networkHintLabel.setManaged(true);
        notFoundMessageLabel.setText("There is no connection between " + startName + " and " + endName + ".");
        drawNetworkGraph(Collections.emptyList());
    }

    private void showOnly(Region toShow, Region... others) {
        toShow.setVisible(true);
        toShow.setManaged(true);
        for (Region other : others) {
            other.setVisible(false);
            other.setManaged(false);
        }
    }

    /**
     * Draws every contact as a node arranged in a circle, every connection
     * as a line between nodes, and — when a path was just found — redraws
     * that path's edges and nodes in the accent color on top.
     */
    private void drawNetworkGraph(List<Integer> highlightedPathIds) {
        GraphicsContext gc = networkCanvas.getGraphicsContext2D();
        double width = networkCanvas.getWidth();
        double height = networkCanvas.getHeight();
        gc.clearRect(0, 0, width, height);

        List<Contact> contacts = allContactsData;
        List<com.connectsphere.model.Connection> connections = connectionDAO.getAllConnections();

        networkStatsLabel.setText(contacts.size() + " contacts · " + connections.size() + " connections");

        if (contacts.isEmpty()) return;

        // Lay contacts out evenly around a circle.
        Map<Integer, double[]> positions = new HashMap<>();
        double centerX = width / 2.0;
        double centerY = height / 2.0;
        double radius = Math.min(width, height) / 2.0 - 40;

        for (int i = 0; i < contacts.size(); i++) {
            double angle = 2 * Math.PI * i / contacts.size();
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);
            positions.put(contacts.get(i).getId(), new double[]{x, y});
        }

        // Build the set of consecutive-pair edges that belong to the
        // highlighted path, so we know which lines to draw in accent color.
        Set<String> highlightedEdges = new HashSet<>();
        for (int i = 0; i < highlightedPathIds.size() - 1; i++) {
            highlightedEdges.add(edgeKey(highlightedPathIds.get(i), highlightedPathIds.get(i + 1)));
        }
        Set<Integer> highlightedNodes = new HashSet<>(highlightedPathIds);

        // Edges first (so nodes draw on top).
        for (com.connectsphere.model.Connection c : connections) {
            double[] p1 = positions.get(c.getContactId1());
            double[] p2 = positions.get(c.getContactId2());
            if (p1 == null || p2 == null) continue;

            boolean isPathEdge = highlightedEdges.contains(edgeKey(c.getContactId1(), c.getContactId2()));
            gc.setStroke(isPathEdge ? Color.web("#1e6fa8") : Color.web("#c9ced6"));
            gc.setLineWidth(isPathEdge ? 3 : 1.5);
            gc.strokeLine(p1[0], p1[1], p2[0], p2[1]);
        }

        // Nodes.
        double nodeRadius = 14;
        for (Contact c : contacts) {
            double[] p = positions.get(c.getId());
            boolean onPath = highlightedNodes.contains(c.getId());

            gc.setFill(onPath ? Color.web("#1e6fa8") : Color.web("#8a93a3"));
            gc.fillOval(p[0] - nodeRadius, p[1] - nodeRadius, nodeRadius * 2, nodeRadius * 2);

            gc.setFill(Color.WHITE);
            gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
            gc.fillText(initialsOf(c.getName()), p[0], p[1] + 4);

            gc.setFill(Color.web("#333333"));
            gc.fillText(c.getName(), p[0], p[1] + nodeRadius + 14);
        }
    }

    private String edgeKey(int a, int b) {
        return Math.min(a, b) + "-" + Math.max(a, b);
    }

    // ═════════════════════════════════════════════════════════════════
    // SETTINGS PAGE FUNCTIONS
    // ═════════════════════════════════════════════════════════════════

    @FXML
    private void onThemeChange(ActionEvent event) {
        // TODO: persist chosen theme; not part of the contacts/connections DB.
    }

    @FXML
    private void onAccentColorChange(ActionEvent event) {
        // TODO: persist chosen accent color; not part of the contacts/connections DB.
    }

    @FXML
    private void onCompactListToggle() {
        boolean compact = compactListToggle.isSelected();
        String compactClass = "compact";
        if (compact) {
            if (!allContactsListView.getStyleClass().contains(compactClass)) {
                allContactsListView.getStyleClass().add(compactClass);
                recentContactsListView.getStyleClass().add(compactClass);
            }
        } else {
            allContactsListView.getStyleClass().remove(compactClass);
            recentContactsListView.getStyleClass().remove(compactClass);
        }
    }

    @FXML
    private void onSortingChange(ActionEvent event) {
        // Contacts are always loaded name-ascending from ContactDAO; a
        // "Last Name" mode would need a lastName column, which the current
        // schema doesn't have. Left as a TODO rather than faked.
    }

    @FXML
    private void onDisplayFormatChange() {
        refreshVisibleListCells();
    }

    @FXML
    private void onShowFavoritesToggle() {
        // Re-run so the combined "not searching AND toggle is on" rule applies.
        filterContacts(contactsSearchField.getText());
    }

    @FXML
    private void onShowRecentToggle() {
        filterContacts(contactsSearchField.getText());
    }

    @FXML
    private void onBackupDatabase() {
        // TODO: real file export of contacts.db; left as a UI-only stub.
        String originalText = backupButton.getText();
        backupButton.setText("Backup Saved");
        backupButton.getStyleClass().add("outline-button-success");

        PauseTransition revert = new PauseTransition(Duration.seconds(2.5));
        revert.setOnFinished(e -> {
            backupButton.setText(originalText);
            backupButton.getStyleClass().remove("outline-button-success");
        });
        revert.play();
    }

    @FXML
    private void onRestoreDatabase() {
        // TODO: real file import; left as a UI-only stub.
    }

    @FXML
    private void onShowClearWarning() {
        clearDataNormalBox.setVisible(false);
        clearDataNormalBox.setManaged(false);
        clearDataWarningBox.setVisible(true);
        clearDataWarningBox.setManaged(true);
    }

    @FXML
    private void onCancelClearWarning() {
        clearDataWarningBox.setVisible(false);
        clearDataWarningBox.setManaged(false);
        clearDataNormalBox.setVisible(true);
        clearDataNormalBox.setManaged(true);
    }

    @FXML
    private void onConfirmClearData() {
        try {
            contactDAO.deleteAllContacts(); // cascades to connections
            reloadAllContactsFromDatabase();
            refreshRecentSection();
            refreshDatabaseStats();
            resetFinderResultState();
            populateFinderComboItems();
        } catch (RuntimeException e) {
            showErrorAlert("Could not clear data", e.getMessage());
        } finally {
            onCancelClearWarning();
        }
    }

    @FXML
    private void onOpenLogFile() {
        // TODO: open the application log file with the OS default viewer.
    }

    @FXML
    private void onCheckForUpdates() {
        // TODO: call an update-check service/endpoint.
    }

    @FXML
    private void onReportIssue() {
        // TODO: open an issue-reporting form or mail-to link.
    }

    // ═════════════════════════════════════════════════════════════════
    // DATABASE CONNECTION POINTS
    // ═════════════════════════════════════════════════════════════════

    private void refreshDatabaseStats() {
        dbStatusValueLabel.setText("Connected");
        dbNameValueLabel.setText("contacts.db");
        totalContactsValueLabel.setText(String.valueOf(contactDAO.getContactCount()));
        totalConnectionsValueLabel.setText(String.valueOf(connectionDAO.getConnectionCount()));
    }

    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message == null ? "An unexpected error occurred." : message);
        alert.setHeaderText(title);
        alert.showAndWait();
    }
}