package com.connectsphere.controller;

import com.connectsphere.dao.ConnectionDAO;
import com.connectsphere.dao.ContactDAO;
import com.connectsphere.dao.DatabaseManager;
import com.connectsphere.model.Connection;
import com.connectsphere.model.Contact;
import com.connectsphere.util.BFSPathFinder;
import com.connectsphere.util.ContactGraph;
import com.connectsphere.util.PhosphorIcons;
import com.connectsphere.util.SettingsManager;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.collections.transformation.FilteredList;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MainController — single controller behind MainView.fxml.
 *
 * Pages: Contacts / Connection Finder / Settings (switched inside #pageStack).
 *
 * DATA LAYER (carried over from the old controller, unchanged in spirit):
 *  - SQLite through ContactDAO / ConnectionDAO; schema created by DatabaseManager.initializeDatabase().
 *  - The controller never holds a JDBC connection and never closes one: every DAO call is
 *    self-contained, so there is nothing to close here. (Shortest paths: ContactGraph + BFSPathFinder.)
 *  - allContactsData is the single in-memory copy of the contacts table. Favorites, Recently Added,
 *    the directory, search and the Finder combos are all derived from it, so every view shares the
 *    same Contact instances (a favorite toggle mutates one object and every view re-renders from it).
 *  - reloadAllFromDatabase() is the only place that re-queries; call it after any DB mutation.
 */
public class MainController {

    // ═════════════════════════════════════════════════════════════════
    // DATA LAYER
    // ═════════════════════════════════════════════════════════════════
    private final ContactDAO contactDAO = new ContactDAO();
    private final ConnectionDAO connectionDAO = new ConnectionDAO();

    private final ObservableList<Contact> allContactsData = FXCollections.observableArrayList();
    private final Map<Integer, Contact> contactsById = new HashMap<>();

    /** Snapshot of the connections table + adjacency built from it (graph drawing, 2-hop lookups). */
    private List<Connection> connectionsCache = new ArrayList<>();
    private final Map<Integer, Set<Integer>> links = new HashMap<>();

    // Contacts page state
    private int selectedId = -1;
    private boolean favoritesOnly = false;
    private boolean sortAscending = true;
    private String query = "";

    // Settings state
    private enum DisplayFormat { PHONE, EMAIL, BOTH }
    private DisplayFormat displayFormat = DisplayFormat.PHONE;
    private static final String BACKUP_HEADER = "CONTACT_NETWORK_BACKUP_V1";
    /** App-managed folder where uploaded contact photos are stored. */
    private static final String PHOTOS_FOLDER = "data/photos";

    // Connection Finder state
    private Contact lastA, lastB;
    private List<Contact> lastPath;
    private double zoom = 1.0;
    private final Canvas gridCanvas = new Canvas();
    private final Pane zoomLayer = new Pane();

    // Add / Edit form state
    private Contact formContact;                                   // null = adding
    private final Set<Integer> formSelectedIds = new HashSet<>();  // ticked connections
    private Set<Integer> formPreviousIds = Collections.emptySet(); // connections before editing
    private final ObservableList<Contact> formCandidateData = FXCollections.observableArrayList();
    private FilteredList<Contact> formCandidates;
    private Image formPhoto;        // in-memory preview image (transient, for display only)
    private String formPhotoPath;   // persisted path under data/photos/ (null = no photo)
    private static final boolean REQUIRE_PHONE = true;            // the form marks only Email as "Optional"

    // ═════════════════════════════════════════════════════════════════
    // FXML — shell
    // ═════════════════════════════════════════════════════════════════
    @FXML private HBox root;
    @FXML private Button navContactsBtn, navFinderBtn, navSettingsBtn;
    @FXML private Label navContactsCount, statContacts, statConnections;
    @FXML private Label breadcrumbCurrent, headerSummary;
    @FXML private StackPane pageStack;
    @FXML private ScrollPane contactsPage;
    @FXML private VBox finderPage, settingsPage;

    // FXML — Contacts page
    @FXML private Label favoritesCount, recentCount, footerCount;
    @FXML private FlowPane favoritesBox;
    @FXML private VBox recentList, directoryList;
    @FXML private VBox listsSection, favoritesSection, recentSection;
    @FXML private Button sortBtn;
    @FXML private TextField searchField;
    @FXML private ToggleButton tabAll, tabFav;
    @FXML private VBox detailContent, directList, indirectList;
    @FXML private StackPane detailAvatarHolder, detailFavoriteIcon;
    @FXML private Label detailName, detailPhone, detailAdded, directCount, indirectCount, detailFavoriteText;
    @FXML private HBox detailFavoriteTag;

    // FXML — Connection Finder
    @FXML private ComboBox<Contact> fromCombo, toCombo;
    @FXML private Button findBtn;
    @FXML private Label finderSearchHint;
    @FXML private Label resultStatus, resultTitle, resultDesc;
    @FXML private HBox statsBox, pathNote;
    @FXML private Label statDistance, statDistanceLabel, statMutual, statMutualLabel;
    @FXML private Label pathHeader, pathNoteLabel, pathPill, pathSummary, networkSubtitle;
    @FXML private VBox pathList;
    @FXML private Pane graphPane;

    // FXML — Settings
    @FXML private RadioButton fmtPhone, fmtEmail, fmtBoth;
    @FXML private ToggleButton favToggle, recentToggle;
    @FXML private Label favToggleLabel, recentToggleLabel;

    // FXML — Add / Edit contact page
    @FXML private VBox formPage, addSideCard, editSideCard, previewBox;
    @FXML private Button backButton, uploadPhotoButton, cancelButton, saveButton;
    @FXML private Label titleLabel, subtitleLabel, photoInitials, photoHint, photoError;
    @FXML private Label nameError, phoneError, emailError;
    @FXML private Label selectedCountLabel, indirectNoteLabel, footerNoteLabel, summaryLabel;
    @FXML private Label sideAvatarInitials, sideNameLabel, sidePhoneLabel, addedOnLabel;
    @FXML private Label pathTitleLabel, pathDescriptionLabel, pathFootnoteLabel;
    @FXML private StackPane photoPane, sideAvatarPane;
    @FXML private SVGPath photoPlaceholder;
    @FXML private ImageView photoView, sideAvatarView;
    @FXML private TextField nameField, phoneField, emailField, connSearchField;
    @FXML private ToggleButton favoriteToggle;
    @FXML private ListView<Contact> contactListView;
    @FXML private HBox favoriteTagBox;

    // ═════════════════════════════════════════════════════════════════
    // INIT
    // ═════════════════════════════════════════════════════════════════
    @FXML
    private void initialize() {
        applyPhosphorIcons(root);

        ToggleGroup tabs = new ToggleGroup();
        tabAll.setToggleGroup(tabs);
        tabFav.setToggleGroup(tabs);

        ToggleGroup formats = new ToggleGroup();
        fmtPhone.setToggleGroup(formats);
        fmtEmail.setToggleGroup(formats);
        fmtBoth.setToggleGroup(formats);

        // Make sure the schema exists before touching it (CREATE TABLE IF NOT EXISTS — safe to repeat).
        DatabaseManager.initializeDatabase();

        // Restore settings saved from the previous session.  Must run after the
        // ToggleGroups are wired above so selecting a radio fires no stray listeners,
        // and before reloadAllFromDatabase() so the first render uses the right format.
        loadPersistedSettings();

        // Live search over the shared in-memory cache.
        searchField.textProperty().addListener((o, a, b) -> {
            query = b == null ? "" : b.trim().toLowerCase();
            applyListVisibility();
            renderDirectory();
        });

        detailFavoriteTag.setCursor(Cursor.HAND);
        detailFavoriteTag.setOnMouseClicked(e -> { Contact c = selected(); if (c != null) toggleFavorite(c); });

        setupFinder();
        setupContactForm();

        showPage(contactsPage);
        reloadAllFromDatabase();
    }

    private void setupFinder() {
        for (ComboBox<Contact> cb : List.of(fromCombo, toCombo)) {
            cb.setItems(allContactsData);
            cb.setCellFactory(v -> contactCell(false));
            cb.setButtonCell(contactCell(true));
        }
        // "Find connection" stays disabled until both contacts are chosen.
        findBtn.disableProperty().bind(fromCombo.valueProperty().isNull().or(toCombo.valueProperty().isNull()));

        graphPane.getChildren().addAll(gridCanvas, zoomLayer);
        gridCanvas.setMouseTransparent(true);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(graphPane.widthProperty());
        clip.heightProperty().bind(graphPane.heightProperty());
        graphPane.setClip(clip);
        graphPane.widthProperty().addListener((o, a, b) -> onGraphResized());
        graphPane.heightProperty().addListener((o, a, b) -> onGraphResized());
    }

    // ═════════════════════════════════════════════════════════════════
    // NAVIGATION
    // ═════════════════════════════════════════════════════════════════
    @FXML private void onNavContacts() { showPage(contactsPage); }
    @FXML private void onNavFinder()   { showPage(finderPage); }
    @FXML private void onNavSettings() { showPage(settingsPage); }

    private void showPage(Node page) {
        for (Node n : pageStack.getChildren()) {
            boolean on = n == page;
            n.setVisible(on);
            n.setManaged(on);
        }
        // the form is a sub page of Contacts, so keep "Contacts" highlighted
        setActive(navContactsBtn, page == contactsPage || page == formPage);
        setActive(navFinderBtn, page == finderPage);
        setActive(navSettingsBtn, page == settingsPage);

        if (page == contactsPage) { breadcrumbCurrent.setText("Contacts"); contactsPage.setVvalue(0); }
        else if (page == finderPage) { breadcrumbCurrent.setText("Connection Finder"); renderFinder(); }
        else if (page == settingsPage) breadcrumbCurrent.setText("Settings");
        else if (page == formPage) breadcrumbCurrent.setText(formContact == null ? "Add contact" : "Edit contact");
    }

    private void setActive(Button b, boolean active) {
        b.getStyleClass().remove("active");
        if (active) b.getStyleClass().add("active");
    }

    // ═════════════════════════════════════════════════════════════════
    // DATABASE → UI
    // ═════════════════════════════════════════════════════════════════

    /** The ONLY place that re-queries SQLite. Rebuilds the cache, then re-renders every page. */
    private void reloadAllFromDatabase() {
        int fromId = idOf(fromCombo.getValue()), toId = idOf(toCombo.getValue());

        List<Contact> contacts = contactDAO.getAllContacts();
        fromCombo.setValue(null);
        toCombo.setValue(null);
        allContactsData.setAll(contacts);
        contactsById.clear();
        for (Contact c : contacts) contactsById.put(c.getId(), c);

        connectionsCache = connectionDAO.getAllConnections();
        links.clear();
        for (Contact c : contacts) links.put(c.getId(), new LinkedHashSet<>());
        for (Connection cn : connectionsCache) {
            Set<Integer> a = links.get(cn.getContactId1()), b = links.get(cn.getContactId2());
            if (a == null || b == null) continue;
            a.add(cn.getContactId2());
            b.add(cn.getContactId1());
        }

        // keep the Finder picks if those contacts still exist
        fromCombo.setValue(contactsById.get(fromId));
        toCombo.setValue(contactsById.get(toId));

        if (!contactsById.containsKey(selectedId)) selectedId = contacts.isEmpty() ? -1 : contacts.get(0).getId();
        refreshAll();
    }

    private int idOf(Contact c) { return c == null ? -1 : c.getId(); }

    private Contact selected() { return contactsById.get(selectedId); }

    private int connectionCount() { return connectionsCache.size(); }

    /** Re-render everything from the in-memory cache (no DB round trip except the Finder's BFS). */
    private void refreshAll() {
        int total = allContactsData.size();
        long favs = allContactsData.stream().filter(Contact::isFavorite).count();

        navContactsCount.setText(String.valueOf(total));
        statContacts.setText(String.valueOf(total));
        statConnections.setText(String.valueOf(connectionCount()));
        headerSummary.setText(plural(total, "contact") + " · " + plural(connectionCount(), "connection"));
        tabAll.setText("All contacts  " + total);
        tabFav.setText("Favorites  " + favs);

        applyListVisibility();
        renderFavorites();
        renderRecent();
        renderDirectory();
        renderDetails();
        renderFinder();
    }

    // ═════════════════════════════════════════════════════════════════
    // CONTACTS PAGE — actions
    // ═════════════════════════════════════════════════════════════════

    @FXML
    private void onAddContact() {
        openContactForm(null);
    }

    @FXML
    private void onEditContact() {
        Contact c = selected();
        if (c != null) openContactForm(c);
    }

    /** "Explore connections": jump to the Finder with the selected contact as the starting point. */
    @FXML
    private void onExploreConnections() {
        Contact c = selected();
        if (c == null) return;
        fromCombo.setValue(c);
        if (idOf(toCombo.getValue()) == c.getId()) toCombo.setValue(null);
        showPage(finderPage);
    }

    @FXML
    private void onToggleSort() {
        sortAscending = !sortAscending;
        sortBtn.setText(sortAscending ? "Name A–Z" : "Name Z–A");
        renderDirectory();
    }

    @FXML private void onFilterAll()       { favoritesOnly = false; tabAll.setSelected(true); renderDirectory(); }
    @FXML private void onFilterFavorites() { favoritesOnly = true;  tabFav.setSelected(true); renderDirectory(); }

    /** Star toggle: persists through ContactDAO, mutates the shared Contact, re-renders every view. */
    private void toggleFavorite(Contact c) {
        boolean next = !c.isFavorite();
        try {
            contactDAO.setFavorite(c.getId(), next);
            c.setFavorite(next);
        } catch (RuntimeException ex) {
            error("Could not update favorite", ex.getMessage());
            return;
        }
        refreshAll();
    }

    /**
     * Shared Add/Edit dialog (ContactView.fxml + ContactFormController, which also hosts the
     * "connect to existing contacts" picker). Pass null to add, or a Contact to edit in place.
     */
    // ═════════════════════════════════════════════════════════════════
// ADD / EDIT CONTACT PAGE (was ContactFormController + ContactView.fxml)
// Saving reuses the same DAO calls as before: addContact / updateContact /
// setFavorite / applyConnectionSelection, then reloadAllFromDatabase().
// ═════════════════════════════════════════════════════════════════
    private void setupContactForm() {
        formCandidates = new FilteredList<>(formCandidateData, c -> true);
        contactListView.setItems(formCandidates);
        contactListView.setCellFactory(lv -> new ConnectionCheckCell());

        connSearchField.textProperty().addListener((o, a, b) -> {
            String q = b == null ? "" : b.trim().toLowerCase();
            formCandidates.setPredicate(c -> q.isEmpty() || containsIgnoreCase(c.getName(), q));
        });

        // round photo
        photoView.setClip(new Circle(36, 36, 36));

        nameField.textProperty().addListener((o, a, b) -> {
            hideFieldError(nameError);
            updatePhotoDisplay();
            updateFormPreview();
        });
        phoneField.textProperty().addListener((o, a, b) -> hideFieldError(phoneError));
        emailField.textProperty().addListener((o, a, b) -> hideFieldError(emailError));

        for (Label l : List.of(nameError, phoneError, emailError, photoError)) show(l, false);
    }

    /** Pass null to add a new contact, or an existing Contact to edit it. */
    private void openContactForm(Contact existing) {
        boolean editing = existing != null;
        formContact = existing;

        // connections: every other contact is a candidate, never the contact itself
        formPreviousIds = editing
                ? new HashSet<>(connectionDAO.getDirectConnections(existing.getId()))
                : Collections.emptySet();
        formSelectedIds.clear();
        formSelectedIds.addAll(formPreviousIds);
        formCandidateData.setAll(allContactsData.stream()
                .filter(c -> !editing || c.getId() != existing.getId())
                .collect(Collectors.toList()));
        connSearchField.clear();
        contactListView.refresh();

        // fields
        nameField.setText(editing ? nullToEmpty(existing.getName()) : "");
        phoneField.setText(editing ? nullToEmpty(existing.getPhone()) : "");
        emailField.setText(editing ? nullToEmpty(existing.getEmail()) : "");
        favoriteToggle.setSelected(editing && existing.isFavorite());
        for (Label l : List.of(nameError, phoneError, emailError, photoError)) hideFieldError(l);

        // Reset photo state, then restore the saved photo when editing an existing contact.
        formPhoto = null;
        formPhotoPath = null;
        photoView.setImage(null);
        photoHint.setText("JPG or PNG, up to 2 MB");
        if (editing && existing.getPhotoPath() != null) {
            File photoFile = new File(existing.getPhotoPath());
            if (photoFile.exists()) {
                Image img = new Image(photoFile.toURI().toString(), 144, 144, false, true);
                if (!img.isError()) {
                    formPhoto = img;
                    formPhotoPath = existing.getPhotoPath();
                    photoView.setImage(img);
                }
            }
        }
        updatePhotoDisplay();

        // titles / buttons
        titleLabel.setText(editing ? "Edit contact" : "Add contact");
        subtitleLabel.setText(editing
                ? "Update the details and connections for " + existing.getName() + "."
                : "Add a person and connect them to your network.");
        saveButton.setText(editing ? "Save changes" : "Add contact");
        footerNoteLabel.setText(REQUIRE_PHONE ? "Name and phone number are required." : "Name is required.");

        // side cards
        show(addSideCard, !editing);
        show(editSideCard, editing);
        if (editing) {
            sideAvatarInitials.setText(initialsOf(existing.getName()));
            sideAvatarView.setVisible(false);
            sideNameLabel.setText(existing.getName());
            sidePhoneLabel.setText(dash(existing.getPhone()));
            show(favoriteTagBox, existing.isFavorite());
            addedOnLabel.setText(addedText(existing));
        }

        updateFormPreview();
        showPage(formPage);
        nameField.requestFocus();
    }

    @FXML
    private void onCancelContact() {
        showPage(contactsPage);
    }

    @FXML
    private void onSaveContact() {
        String name = trimmed(nameField), phone = trimmed(phoneField), email = trimmed(emailField);

        boolean ok = true;
        if (name.isEmpty()) { fieldError(nameError, "Name is required."); ok = false; }
        if (phone.isEmpty()) {
            if (REQUIRE_PHONE) { fieldError(phoneError, "Phone number is required."); ok = false; }
        } else if (!phone.matches("[0-9+()\\-\\s]{4,20}")) {
            fieldError(phoneError, "Enter a valid phone number."); ok = false;
        }
        if (!email.isEmpty() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            fieldError(emailError, "Enter a valid email address."); ok = false;
        }
        if (!ok) return;

        boolean wantFavorite = favoriteToggle.isSelected();
        try {
            int contactId;
            if (formContact != null) {
                formContact.setName(name);
                formContact.setPhone(phone);
                formContact.setEmail(email);
                formContact.setPhotoPath(formPhotoPath);
                contactDAO.updateContact(formContact);
                if (formContact.isFavorite() != wantFavorite) {
                    contactDAO.setFavorite(formContact.getId(), wantFavorite);
                    formContact.setFavorite(wantFavorite);
                }
                contactId = formContact.getId();
            } else {
                Contact saved = contactDAO.addContact(new Contact(name, phone, email, formPhotoPath));
                contactId = saved.getId();
                if (wantFavorite) contactDAO.setFavorite(contactId, true);
            }

            applyConnectionSelection(contactId, formPreviousIds, new HashSet<>(formSelectedIds));
            selectedId = contactId;
            reloadAllFromDatabase();
            showPage(contactsPage);
        } catch (RuntimeException e) {
            error("Could not save contact", e.getMessage());
        }
    }

    @FXML
    private void onUploadPhoto() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose a photo");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images (*.png, *.jpg, *.jpeg)", "*.png", "*.jpg", "*.jpeg"));
        File f = fc.showOpenDialog(root.getScene().getWindow());
        if (f == null) return;
        if (f.length() > 2L * 1024 * 1024) { fieldError(photoError, "Photo must be 2 MB or smaller."); return; }

        Image img = new Image(f.toURI().toString(), 144, 144, false, true);
        if (img.isError()) { fieldError(photoError, "Could not read that image."); return; }

        // Copy the file into the app-managed photos folder so the photo persists
        // even if the user moves or deletes the original file.
        try {
            File photosDir = new File(PHOTOS_FOLDER);
            if (!photosDir.exists()) photosDir.mkdirs();
            String name = f.getName();
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".jpg";
            File dest = new File(photosDir, UUID.randomUUID() + ext);
            Files.copy(f.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            formPhotoPath = dest.getPath();
        } catch (IOException e) {
            fieldError(photoError, "Could not save photo: " + e.getMessage());
            return;
        }

        hideFieldError(photoError);
        formPhoto = img;
        photoView.setImage(img);
        updatePhotoDisplay();
    }

    /** Photo circle: uploaded image > initials (once a name is typed) > person icon. */
    private void updatePhotoDisplay() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        boolean hasImage = formPhoto != null;
        photoView.setVisible(hasImage);
        photoInitials.setVisible(!hasImage && !name.isEmpty());
        photoPlaceholder.setVisible(!hasImage && name.isEmpty());
        photoInitials.setText(initialsOf(name));
    }

    /** Re-renders the counters, the add-mode summary and the side "connection preview" card. */
    private void updateFormPreview() {
        int n = formSelectedIds.size();
        selectedCountLabel.setText(n + " selected");

        String typed = trimmed(nameField);
        String who = typed.isEmpty() ? "This contact" : typed;

        // contacts reachable through the ticked people (2 hops), excluding self and the ticked ones
        Set<Integer> indirect = new LinkedHashSet<>();
        for (int id : formSelectedIds)
            for (int m : links.getOrDefault(id, Collections.emptySet()))
                if (!formSelectedIds.contains(m) && (formContact == null || m != formContact.getId()))
                    indirect.add(m);

        summaryLabel.setText(n == 0
                ? "No connections selected yet."
                : plural(n, "direct connection") + " · " + plural(indirect.size(), "indirect connection"));

        pathTitleLabel.setText("Connection preview");
        pathDescriptionLabel.setText(n == 0
                ? "Select people on the left to see how " + who + " will fit into your network."
                : who + " will be directly connected to " + plural(n, "contact")
                  + (indirect.isEmpty() ? "." : " and can reach " + plural(indirect.size(), "more") + " through them."));
        pathFootnoteLabel.setText("Indirect connections are found automatically through shared contacts.");

        show(indirectNoteLabel, !indirect.isEmpty());
        indirectNoteLabel.setText("Selecting these people also gives " + who + " "
                + plural(indirect.size(), "indirect connection") + ".");

        previewBox.getChildren().clear();
        Contact me = new Contact(typed.isEmpty() ? "New contact" : typed, "", "");
        previewBox.getChildren().add(previewRow(me, "This contact", true));
        int shown = 0;
        for (int id : formSelectedIds) {
            Contact c = contactsById.get(id);
            if (c == null) continue;
            if (shown++ == 4) break;
            previewBox.getChildren().add(previewRow(c, "Direct connection", false));
        }
        if (n > 4) previewBox.getChildren().add(styled("+ " + (n - 4) + " more", "muted-11"));
    }

    private Node previewRow(Contact c, String sub, boolean filled) {
        VBox text = new VBox(2, styled(c.getName(), "row-name"), styled(sub, "row-phone"));
        HBox row = new HBox(10, avatar(c, 32, 12, filled), text);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void fieldError(Label l, String msg) { l.setText(msg); show(l, true); }
    private void hideFieldError(Label l)         { show(l, false); }
    private String trimmed(TextField f)          { return f.getText() == null ? "" : f.getText().trim(); }
    private String nullToEmpty(String s)         { return s == null ? "" : s; }

    /** One row in the connections picker: checkbox + avatar + name. Click anywhere on the row to toggle. */
    private class ConnectionCheckCell extends ListCell<Contact> {
        private final CheckBox checkBox = new CheckBox();
        private final Label avatarLabel = new Label();
        private final Label nameLabel = new Label();
        private final HBox row = new HBox(10);

        ConnectionCheckCell() {
            avatarLabel.getStyleClass().add("connection-avatar-label");
            nameLabel.getStyleClass().add("connection-name");
            StackPane avatarStack = new StackPane(avatarLabel);
            avatarStack.getStyleClass().add("connection-avatar");

            row.getChildren().setAll(checkBox, avatarStack, nameLabel);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("connection-row");
            row.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(nameLabel, Priority.ALWAYS);

            // the row handles every click, so the checkbox never double-toggles
            checkBox.setMouseTransparent(true);
            row.setOnMouseClicked(e -> {
                Contact c = getItem();
                if (c == null) return;
                checkBox.setSelected(!checkBox.isSelected());
                if (checkBox.isSelected()) formSelectedIds.add(c.getId());
                else formSelectedIds.remove(c.getId());
                updateFormPreview();
            });
        }

        @Override
        protected void updateItem(Contact c, boolean empty) {
            super.updateItem(c, empty);
            if (empty || c == null) { setGraphic(null); return; }
            nameLabel.setText(c.getName());
            avatarLabel.setText(initialsOf(c.getName()));
            checkBox.setSelected(formSelectedIds.contains(c.getId()));
            setGraphic(row);
        }
    }

    /** Adds newly ticked connections and removes unticked ones that used to exist; untouched pairs are skipped. */
    private void applyConnectionSelection(int contactId, Set<Integer> previouslyConnected, Set<Integer> nowSelected) {
        for (int otherId : nowSelected)
            if (!previouslyConnected.contains(otherId)) connectionDAO.addConnection(contactId, otherId);
        for (int otherId : previouslyConnected)
            if (!nowSelected.contains(otherId)) connectionDAO.removeConnection(contactId, otherId);
    }

    private void confirmAndDeleteContact(Contact contact) {
        if (!confirm("Delete Contact",
                "Delete " + contact.getName() + "? This also removes any connections involving them.", "Delete"))
            return;
        try {
            contactDAO.deleteContact(contact.getId());
            if (selectedId == contact.getId()) selectedId = -1;
            reloadAllFromDatabase();
        } catch (RuntimeException e) {
            error("Could not delete contact", e.getMessage());
        }
    }

    /** While a search is active only the results show; otherwise the Settings switches decide. */
    private void applyListVisibility() {
        boolean searching = !query.isEmpty();
        boolean showFav = favToggle.isSelected() && !searching;
        boolean showRecent = recentToggle.isSelected() && !searching;
        show(favoritesSection, showFav);
        show(recentSection, showRecent);
        show(listsSection, showFav || showRecent);
    }

    // ═════════════════════════════════════════════════════════════════
    // CONTACTS PAGE — rendering
    // ═════════════════════════════════════════════════════════════════
    private void renderFavorites() {
        favoritesBox.getChildren().clear();
        List<Contact> favs = allContactsData.stream()
                .filter(Contact::isFavorite)
                .sorted(byName(true))
                .collect(Collectors.toList());
        favoritesCount.setText(plural(favs.size(), "contact"));

        if (favs.isEmpty()) {
            favoritesBox.getChildren().add(styled("No favorites yet. Star a contact to pin them here.", "empty-text"));
            return;
        }
        for (Contact c : favs) favoritesBox.getChildren().add(buildFavoriteCard(c));
    }

    private Node buildFavoriteCard(Contact c) {
        StackPane star = icon("star-fill", 18, "ic-blue");
        star.setMouseTransparent(false);
        star.setCursor(Cursor.HAND);
        star.setOnMouseClicked(e -> { e.consume(); toggleFavorite(c); });

        HBox top = new HBox(avatar(c, 48, 16, true), spacer(), star);
        top.setAlignment(Pos.TOP_LEFT);

        HBox phone = iconLabel("phone", 12, "ic-muted", dash(c.getPhone()), "fav-meta");
        boolean noEmail = c.getEmail() == null || c.getEmail().isBlank();
        HBox email = noEmail
                ? iconLabel("envelope-simple", 12, "ic-soft", "No email added", "fav-meta-muted")
                : iconLabel("envelope-simple", 12, "ic-muted", c.getEmail(), "fav-meta");
        phone.setAlignment(Pos.CENTER);
        email.setAlignment(Pos.CENTER);
        VBox info = new VBox(4, styled(c.getName(), "fav-name"), phone, email);
        info.setAlignment(Pos.TOP_CENTER);

        VBox card = new VBox(8, top, info);
        card.getStyleClass().add("fav-card");
        card.setPadding(new Insets(16, 18, 16, 18));
        card.setPrefWidth(220);
        card.setMinWidth(185);
        wireRow(card, c);
        return card;
    }

    /** Newest first (highest id), same rule as the old controller. */
    private void renderRecent() {
        recentList.getChildren().clear();
        List<Contact> recent = allContactsData.stream()
                .sorted(Comparator.comparing(Contact::getId).reversed())
                .limit(5)
                .collect(Collectors.toList());
        recentCount.setText(plural(recent.size(), "contact"));

        for (int i = 0; i < recent.size(); i++) {
            Contact c = recent.get(i);
            VBox text = new VBox(2, styled(c.getName(), "row-name"), styled(subtitle(c), "row-phone"));
            HBox date = iconLabel("calendar-blank", 12, "ic-soft", addedText(c), "row-date");

            HBox row = new HBox(14, avatar(c, 32, 12, c.isFavorite()), text, spacer(), date,
                    icon("caret-right", 14, c.isFavorite() ? "ic-blue" : "ic-nav"));
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(10, 18, 10, 18));
            row.getStyleClass().add("recent-row");
            if (i == recent.size() - 1) row.getStyleClass().add("last");
            wireRow(row, c);
            recentList.getChildren().add(row);
        }
    }

    private void renderDirectory() {
        directoryList.getChildren().clear();

        List<Contact> view = allContactsData.stream()
                .filter(c -> !favoritesOnly || c.isFavorite())
                .filter(c -> query.isEmpty()
                        || containsIgnoreCase(c.getName(), query)
                        || containsIgnoreCase(c.getPhone(), query)
                        || containsIgnoreCase(c.getEmail(), query))
                .sorted(byName(sortAscending))
                .collect(Collectors.toList());

        for (Contact c : view) {
            VBox text = new VBox(2, styled(c.getName(), "row-name"), styled(subtitle(c), "row-phone"));
            if (c.isFavorite()) text.getChildren().add(iconLabel("star-fill", 10, "ic-blue", "Favorite", "dir-fav-tag"));

            HBox identity = new HBox(10, avatar(c, 36, 13, c.isFavorite()), text);
            identity.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(identity, Priority.ALWAYS);

            Label added = styled(addedText(c), "footer-text");
            added.setStyle("-fx-font-size: 11px;");
            added.setPrefWidth(100); added.setMinWidth(100);

            boolean isSel = c.getId() == selectedId;
            HBox row = new HBox(identity, added, icon("caret-right", 16, isSel ? "ic-blue" : "ic-soft"));
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(0, 20, 0, 20));
            row.setMinHeight(c.isFavorite() ? 62 : 52);
            row.setPrefHeight(c.isFavorite() ? 62 : 52);
            row.getStyleClass().add("dir-row");
            if (isSel) row.getStyleClass().add("selected");
            wireRow(row, c);
            directoryList.getChildren().add(row);
        }

        footerCount.setText(!favoritesOnly && query.isEmpty()
                ? "Showing all " + plural(view.size(), "contact")
                : "Showing " + view.size() + " of " + allContactsData.size() + " contacts");
    }

    private void renderDetails() {
        Contact sel = selected();
        boolean has = sel != null;
        detailContent.setVisible(has);
        detailContent.setManaged(has);
        if (!has) return;

        detailAvatarHolder.getChildren().setAll(avatar(sel, 56, 20, sel.isFavorite()));
        detailName.setText(sel.getName());
        detailPhone.setText(subtitle(sel));
        detailAdded.setText(addedText(sel));
        fillIconHolder(detailFavoriteIcon, sel.isFavorite() ? "star-fill" : "star", 12);
        detailFavoriteText.setText(sel.isFavorite() ? "Favorite contact" : "Add to favorites");

        // direct (1 hop)
        Set<Integer> direct = links.getOrDefault(sel.getId(), Collections.emptySet());
        directList.getChildren().clear();
        directCount.setText(plural(direct.size(), "contact"));
        if (direct.isEmpty()) directList.getChildren().add(styled("No direct connections yet.", "empty-text"));
        for (int id : direct) directList.getChildren().add(connectionItem(contactsById.get(id), "Directly connected"));

        // indirect (2 hops) remembering which direct contact we came through
        Map<Integer, Integer> via = new LinkedHashMap<>();
        for (int n : direct)
            for (int m : links.getOrDefault(n, Collections.emptySet()))
                if (m != sel.getId() && !direct.contains(m)) via.putIfAbsent(m, n);
        indirectList.getChildren().clear();
        indirectCount.setText(plural(via.size(), "contact"));
        if (via.isEmpty()) indirectList.getChildren().add(styled("No indirect connections yet.", "empty-text"));
        via.forEach((m, n) -> indirectList.getChildren().add(
                connectionItem(contactsById.get(m), "Via " + contactsById.get(n).getName())));
    }

    private Node connectionItem(Contact c, String sub) {
        VBox text = new VBox(2, styled(c.getName(), "row-name"), styled(subtitle(c), "conn-phone"), styled(sub, "conn-via"));
        HBox item = new HBox(10, avatar(c, 34, 13, false), text, spacer(), icon("caret-right", 14, "ic-muted"));
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(8, 12, 8, 12));
        item.getStyleClass().add("conn-item");
        item.setOnMouseClicked(e -> select(c));
        return item;
    }

    private void select(Contact c) {
        selectedId = c.getId();
        renderDirectory();
        renderDetails();
    }

    /** Click = select, double-click = edit, right-click = Edit / Delete menu. */
    private void wireRow(Node row, Contact c) {
        ContextMenu menu = new ContextMenu();
        MenuItem edit = new MenuItem("Edit Contact");
        edit.setOnAction(e -> openContactForm(c));
        MenuItem delete = new MenuItem("Delete Contact");
        delete.setOnAction(e -> confirmAndDeleteContact(c));
        menu.getItems().addAll(edit, delete);

        row.setOnContextMenuRequested(e -> { menu.show(row, e.getScreenX(), e.getScreenY()); e.consume(); });
        row.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (e.getClickCount() == 2) openContactForm(c);
            else select(c);
        });
    }

    // ═════════════════════════════════════════════════════════════════
    // CONNECTION FINDER
    // ═════════════════════════════════════════════════════════════════
    @FXML private void onFindConnection() { renderFinder(); }

    @FXML private void onZoomIn()    { setZoom(zoom + 0.1); }
    @FXML private void onZoomOut()   { setZoom(zoom - 0.1); }
    @FXML private void onZoomReset() { setZoom(1.0); }

    private void setZoom(double z) {
        zoom = Math.max(0.5, Math.min(2.0, z));
        zoomLayer.setScaleX(zoom);
        zoomLayer.setScaleY(zoom);
    }

    /**
     * Old algorithm, unchanged: build the graph fresh from the connections table on every search
     * (so it always reflects the latest data) and run BFSPathFinder on it.
     */
    private List<Contact> findShortestPath(Contact start, Contact end) {
        ContactGraph graph = new ContactGraph(connectionDAO.getAllConnections());
        BFSPathFinder finder = new BFSPathFinder(graph);
        BFSPathFinder.PathResult result = finder.findShortestPath(start.getId(), end.getId());
        if (!result.pathExists()) return null;

        List<Contact> path = new ArrayList<>();
        for (int id : result.getPath()) {
            Contact c = contactsById.get(id);
            if (c == null) c = contactDAO.getContactById(id); // fallback, shouldn't normally hit
            path.add(c);
        }
        return path;
    }

    private void renderFinder() {
        Contact a = fromCombo.getValue(), b = toCombo.getValue();
        List<Contact> path = (a != null && b != null && a.getId() != b.getId()) ? findShortestPath(a, b) : null;
        lastA = a; lastB = b; lastPath = path;

        finderSearchHint.setText("Search across all " + allContactsData.size() + " contacts");
        networkSubtitle.setText(plural(allContactsData.size(), "contact") + " · " + plural(connectionCount(), "direct connection"));

        renderResultCard(a, b, path);
        renderGraph();
    }

    private void renderResultCard(Contact a, Contact b, List<Contact> path) {
        boolean found = path != null;
        show(statsBox, found);
        show(pathHeader, found);
        show(pathList, found);
        show(pathNote, found && path.size() > 2);
        show(pathPill, found);
        pathList.getChildren().clear();

        if (a == null || b == null) {
            resultStatus.setText("SELECT CONTACTS");
            resultTitle.setText("Choose two contacts");
            resultDesc.setText("Pick a starting and a destination contact to find the shortest route.");
            pathSummary.setText("—");
            return;
        }
        if (a.getId() == b.getId()) {
            resultStatus.setText("SAME CONTACT");
            resultTitle.setText("Pick two different people");
            resultDesc.setText("The starting and destination contacts need to be different.");
            pathSummary.setText("—");
            return;
        }
        if (!found) {
            resultStatus.setText("NO CONNECTION");
            resultTitle.setText("No connection found");
            resultDesc.setText("There is no connection between " + a.getName() + " and " + b.getName() + ".");
            pathSummary.setText("—");
            return;
        }

        int dist = path.size() - 1;
        List<String> mids = path.subList(1, path.size() - 1).stream().map(Contact::getName).collect(Collectors.toList());

        resultStatus.setText("CONNECTION FOUND");
        resultTitle.setText(dist == 1 ? "Directly connected" : "Connected through " + joinNames(mids));
        resultDesc.setText(dist == 1
                ? a.getName() + " and " + b.getName() + " are directly connected."
                : a.getName() + " and " + b.getName() + " are " + dist + " connections apart.");
        statDistance.setText(String.valueOf(dist));
        statDistanceLabel.setText(dist == 1 ? "Connection" : "Connections");
        statMutual.setText(String.valueOf(mids.size()));
        statMutualLabel.setText(mids.size() == 1 ? "Mutual contact" : "Mutual contacts");
        pathNoteLabel.setText("No direct connection between these contacts.");
        pathSummary.setText(path.stream().map(Contact::getName).collect(Collectors.joining(" → ")));

        for (int i = 0; i < path.size(); i++) {
            Contact c = path.get(i);
            boolean end = i == 0 || i == path.size() - 1;
            String sub = i == 0 ? "Starting contact" : (end ? "Destination contact" : "Mutual contact");

            HBox node = new HBox(12, avatar(c, 36, 14, end),
                    new VBox(4, styled(c.getName(), "path-node-name"), styled(sub, "path-node-sub")));
            node.setAlignment(Pos.CENTER_LEFT);
            pathList.getChildren().add(node);

            if (i < path.size() - 1) {
                Region line = new Region();
                line.getStyleClass().add("path-connector-line");
                line.setMinSize(2, 24); line.setPrefSize(2, 24); line.setMaxSize(2, 24);
                HBox connector = new HBox(29, line, styled("Direct connection", "path-connector-text"));
                connector.setAlignment(Pos.CENTER_LEFT);
                connector.setPadding(new Insets(0, 0, 0, 17));
                connector.setMinHeight(32); connector.setPrefHeight(32);
                pathList.getChildren().add(connector);
            }
        }
    }

    private void onGraphResized() {
        double w = graphPane.getWidth(), h = graphPane.getHeight();
        gridCanvas.setWidth(w);
        gridCanvas.setHeight(h);
        GraphicsContext gc = gridCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);
        gc.setFill(Color.web("#E2E8F1"));
        for (double x = 28; x < w; x += 48)
            for (double y = 25; y < h; y += 48) gc.fillOval(x, y, 2, 2);
        renderGraph();
    }

    /**
     * Layout: with a path, path nodes zig-zag left→right and the other contacts sit on a lower row;
     * otherwise every contact is placed on a circle. Edges come from the connections cache.
     */
    private void renderGraph() {
        double w = graphPane.getWidth(), h = graphPane.getHeight();
        zoomLayer.getChildren().clear();
        if (w <= 0 || h <= 0 || allContactsData.isEmpty()) return;
        zoomLayer.setPrefSize(w, h);
        zoomLayer.resize(w, h);

        boolean pathMode = lastPath != null && lastPath.size() >= 2;
        Map<Integer, double[]> pos = new HashMap<>();   // id -> {cx, cy, diameter}
        Set<Integer> pathIds = new HashSet<>();

        if (pathMode) {
            int n = lastPath.size();
            for (int i = 0; i < n; i++) {
                Contact c = lastPath.get(i);
                pathIds.add(c.getId());
                double x = w * (0.16 + 0.68 * (i / (double) (n - 1)));
                boolean end = i == 0 || i == n - 1;
                double y = (end || n == 2) ? h * 0.50 : h * 0.28;
                pos.put(c.getId(), new double[]{x, y, 76});
            }
            List<Contact> others = allContactsData.stream().filter(c -> !pathIds.contains(c.getId())).collect(Collectors.toList());
            for (int i = 0; i < others.size(); i++)
                pos.put(others.get(i).getId(), new double[]{w * (i + 1) / (others.size() + 1), h * 0.74, 56});
        } else {
            int n = allContactsData.size();
            double d = n > 4 ? 56 : 76;
            double r = Math.min(w, h) * 0.28;
            for (int i = 0; i < n; i++) {
                double ang = -Math.PI / 2 + 2 * Math.PI * i / n;
                double cx = n == 1 ? w / 2 : w / 2 + r * 1.6 * Math.cos(ang);
                double cy = n == 1 ? h * 0.45 : h * 0.46 + r * Math.sin(ang);
                pos.put(allContactsData.get(i).getId(), new double[]{cx, cy, d});
            }
        }

        Set<String> pathEdges = new HashSet<>();
        if (pathMode)
            for (int i = 0; i < lastPath.size() - 1; i++)
                pathEdges.add(edgeKey(lastPath.get(i).getId(), lastPath.get(i + 1).getId()));

        // edges first (under the nodes)
        List<Node> labels = new ArrayList<>();
        for (Connection cn : connectionsCache) {
            double[] p = pos.get(cn.getContactId1()), q = pos.get(cn.getContactId2());
            if (p == null || q == null) continue;
            boolean hot = pathEdges.contains(edgeKey(cn.getContactId1(), cn.getContactId2()));
            Line line = new Line(p[0], p[1], q[0], q[1]);
            line.setStroke(Color.web(hot ? "#2563EB" : "#B9D0FB"));
            line.setStrokeWidth(hot ? 3 : 2);
            line.setMouseTransparent(true);
            zoomLayer.getChildren().add(line);
            if (hot) {
                Label pill = styled("Direct", "edge-label");
                pill.setLayoutX((p[0] + q[0]) / 2 - 22);
                pill.setLayoutY((p[1] + q[1]) / 2 - 11);
                pill.setMouseTransparent(true);
                labels.add(pill);
            }
        }
        zoomLayer.getChildren().addAll(labels);

        for (Contact c : allContactsData) {
            double[] p = pos.get(c.getId());
            boolean end = pathMode && (c.getId() == lastPath.get(0).getId() || c.getId() == lastPath.get(lastPath.size() - 1).getId());
            boolean mid = pathMode && pathIds.contains(c.getId()) && !end;
            boolean pick = !pathMode && (idOf(lastA) == c.getId() || idOf(lastB) == c.getId());
            String ring = (end || pick) ? "endpoint" : (mid ? "mid" : "other");
            String sub;
            if (pathMode) sub = c.getId() == lastPath.get(0).getId() ? "Starting contact" : end ? "Destination contact" : mid ? "Mutual contact" : "Contact";
            else sub = idOf(lastA) == c.getId() ? "Starting contact" : idOf(lastB) == c.getId() ? "Destination contact" : "Contact";
            zoomLayer.getChildren().add(graphNode(c, p[0], p[1], p[2], ring, end || pick, sub));
        }
    }

    private String edgeKey(int a, int b) { return Math.min(a, b) + "-" + Math.max(a, b); }

    private Node graphNode(Contact c, double cx, double cy, double d, String ringClass, boolean filled, String sub) {
        Label initial = new Label(initialsOf(c.getName()));
        initial.getStyleClass().add(filled ? "node-initial-filled" : "node-initial-light");
        initial.setStyle("-fx-font-size: " + Math.round(d * 0.316) + "px;");

        StackPane core = new StackPane(initial);
        core.getStyleClass().add(filled ? "node-core-filled" : "node-core-light");
        double cd = d - 16;
        core.setMinSize(cd, cd); core.setPrefSize(cd, cd); core.setMaxSize(cd, cd);

        StackPane ring = new StackPane(core);
        ring.getStyleClass().addAll("node-ring", ringClass);
        ring.setMinSize(d, d); ring.setPrefSize(d, d); ring.setMaxSize(d, d);

        VBox labels = new VBox(5, styled(c.getName(), "node-name"), styled(sub, "node-sub"));
        labels.setAlignment(Pos.CENTER);

        VBox box = new VBox(10, ring, labels);
        box.setAlignment(Pos.TOP_CENTER);
        box.setFillWidth(false);
        box.setPrefWidth(140); box.setMinWidth(140); box.setMaxWidth(140);
        box.setLayoutX(cx - 70);
        box.setLayoutY(cy - d / 2);
        box.setOnMouseClicked(e -> { fromCombo.setValue(c); renderFinder(); }); // click a node = start from them
        return box;
    }

    private ListCell<Contact> contactCell(boolean buttonCell) {
        return new ListCell<>() {
            @Override protected void updateItem(Contact c, boolean empty) {
                super.updateItem(c, empty);
                setText(null);
                if (empty || c == null) {
                    setGraphic(buttonCell ? styled("Select a contact", "contact-combo-empty") : null);
                    return;
                }
                HBox h = new HBox(10, avatar(c, 30, 14, false), styled(c.getName(), "contact-combo-name"));
                h.setAlignment(Pos.CENTER_LEFT);
                setGraphic(h);
            }
        };
    }

    // ═════════════════════════════════════════════════════════════════
    // SETTINGS
    // ═════════════════════════════════════════════════════════════════

    /**
     * Reads {@code data/settings.properties} (written by {@link SettingsManager}) and
     * applies the saved values to the Settings controls and the in-memory state.
     * Called once from {@link #initialize()}, after the radio ToggleGroup is wired
     * but before the first render.  Falls back to defaults silently if the file
     * is missing (first launch) or unreadable.
     */
    private void loadPersistedSettings() {
        Properties props = SettingsManager.load();

        // ── Display format ──────────────────────────────────────────────
        String fmt = props.getProperty(SettingsManager.KEY_FORMAT, SettingsManager.DEFAULT_FORMAT);
        switch (fmt) {
            case "EMAIL" -> { fmtEmail.setSelected(true); displayFormat = DisplayFormat.EMAIL; }
            case "BOTH"  -> { fmtBoth.setSelected(true);  displayFormat = DisplayFormat.BOTH; }
            default      -> { fmtPhone.setSelected(true);  displayFormat = DisplayFormat.PHONE; }
        }

        // ── Section toggles ─────────────────────────────────────────────
        boolean showFav    = Boolean.parseBoolean(
                props.getProperty(SettingsManager.KEY_SHOW_FAVORITES, SettingsManager.DEFAULT_SHOW_FAVORITES));
        boolean showRecent = Boolean.parseBoolean(
                props.getProperty(SettingsManager.KEY_SHOW_RECENT,    SettingsManager.DEFAULT_SHOW_RECENT));

        favToggle.setSelected(showFav);
        recentToggle.setSelected(showRecent);
        favToggleLabel.setText(showFav    ? "On" : "Off");
        recentToggleLabel.setText(showRecent ? "On" : "Off");
    }

    /** Radio cards + both switches land here. */
    @FXML
    private void onSettingsChanged() {
        displayFormat = fmtEmail.isSelected() ? DisplayFormat.EMAIL
                : fmtBoth.isSelected() ? DisplayFormat.BOTH : DisplayFormat.PHONE;
        favToggleLabel.setText(favToggle.isSelected() ? "On" : "Off");
        recentToggleLabel.setText(recentToggle.isSelected() ? "On" : "Off");
        applyListVisibility();
        renderRecent();
        renderDirectory();
        renderDetails();
        // Persist changes so they survive app restarts.
        SettingsManager.save(displayFormat.name(), favToggle.isSelected(), recentToggle.isSelected());
    }

    @FXML
    private void onBackupData() {
        FileChooser fc = backupChooser("Save backup");
        fc.setInitialFileName("contact-network-backup.cnb");
        File f = fc.showSaveDialog(root.getScene().getWindow());
        if (f == null) return;
        try {
            Files.write(f.toPath(), buildBackup(), StandardCharsets.UTF_8);
            info("Backup saved", "Saved " + plural(allContactsData.size(), "contact") + " and "
                    + plural(connectionCount(), "connection") + " to:\n" + f.getAbsolutePath());
        } catch (IOException e) {
            error("Backup failed", e.getMessage());
        }
    }

    @FXML
    private void onRestoreData() {
        File f = backupChooser("Choose a backup file").showOpenDialog(root.getScene().getWindow());
        if (f == null) return;
        if (!confirm("Restore data", "Restoring replaces all current contacts and connections with the backup. Continue?", "Restore"))
            return;
        try {
            restoreFrom(Files.readAllLines(f.toPath(), StandardCharsets.UTF_8));
            info("Restore complete", "Loaded " + plural(allContactsData.size(), "contact") + " and "
                    + plural(connectionCount(), "connection") + ".");
        } catch (Exception e) {
            error("Restore failed", "Could not restore this file.\n" + e.getMessage());
        }
    }

    @FXML
    private void onClearData() {
        if (!confirm("Clear all data", "This permanently removes all contacts and connections and cannot be undone.", "Clear all data"))
            return;
        try {
            contactDAO.deleteAllContacts(); // cascades to connections
            selectedId = -1;
            reloadAllFromDatabase();
        } catch (RuntimeException e) {
            error("Could not clear data", e.getMessage());
        }
    }

    // backup file: one record per line, tab separated, text fields URL-encoded
    //   C <id> <name> <phone> <email> <favorite> <photo_path>
    //   L <idA> <idB>
    private List<String> buildBackup() {
        List<String> out = new ArrayList<>();
        out.add(BACKUP_HEADER);
        for (Contact c : allContactsData)
            out.add(String.join("\t", "C", String.valueOf(c.getId()), enc(c.getName()), enc(c.getPhone()),
                    enc(c.getEmail()), String.valueOf(c.isFavorite()), enc(c.getPhotoPath())));
        for (Connection cn : connectionsCache)
            out.add("L\t" + cn.getContactId1() + "\t" + cn.getContactId2());
        return out;
    }

    /** Parses + validates the whole file first, so a bad file never leaves the DB half-wiped. */
    private void restoreFrom(List<String> lines) {
        if (lines.isEmpty() || !BACKUP_HEADER.equals(lines.get(0).trim()))
            throw new IllegalArgumentException("Missing backup header.");

        class Rec { int oldId; String name, phone, email, photoPath; boolean fav; }
        List<Rec> recs = new ArrayList<>();
        Set<Integer> knownIds = new HashSet<>();
        List<int[]> edges = new ArrayList<>();

        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] t = line.split("\t", -1);
            if (t[0].equals("C")) {
                Rec r = new Rec();
                r.oldId = Integer.parseInt(t[1]);
                r.name = dec(t[2]); r.phone = dec(t[3]); r.email = dec(t[4]);
                r.fav = Boolean.parseBoolean(t[5]);
                // t[6] is photo_path — present in new backups, absent in old V1 files
                r.photoPath = (t.length > 6 && !t[6].isBlank()) ? dec(t[6]) : null;
                recs.add(r);
                knownIds.add(r.oldId);
            } else if (t[0].equals("L")) {
                edges.add(new int[]{Integer.parseInt(t[1]), Integer.parseInt(t[2])});
            }
        }
        for (int[] e : edges)
            if (!knownIds.contains(e[0]) || !knownIds.contains(e[1]))
                throw new IllegalArgumentException("A connection refers to an unknown contact.");

        // apply: ids are reassigned by the DB, so map old id -> new id
        contactDAO.deleteAllContacts();
        Map<Integer, Integer> idMap = new HashMap<>();
        for (Rec r : recs) {
            Contact saved = contactDAO.addContact(new Contact(r.name, r.phone, r.email, r.photoPath));
            if (r.fav) contactDAO.setFavorite(saved.getId(), true);
            idMap.put(r.oldId, saved.getId());
        }
        for (int[] e : edges) connectionDAO.addConnection(idMap.get(e[0]), idMap.get(e[1]));

        selectedId = -1;
        reloadAllFromDatabase();
    }

    private FileChooser backupChooser(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Contact Network Backup (*.cnb)", "*.cnb"));
        return fc;
    }

    private static String enc(String v) { return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8); }
    private static String dec(String v) { return URLDecoder.decode(v, StandardCharsets.UTF_8); }

    // ═════════════════════════════════════════════════════════════════
    // PhosphorIcons  (Phosphor, 256 viewBox, filled — same convention as the old setIcon: scale = size / 256)
    // ═════════════════════════════════════════════════════════════════

    /** Fills every FXML holder: <StackPane styleClass="icon, ic-nav" userData="address-book:18"/> */
    private void applyPhosphorIcons(Node n) {
        if (n == null) return;
        if (n instanceof StackPane sp && sp.getUserData() instanceof String s && sp.getStyleClass().contains("icon")) {
            String[] t = s.split(":");
            fillIconHolder(sp, t[0], Double.parseDouble(t[1]));
            return;
        }
        if (n instanceof Labeled l) applyPhosphorIcons(l.getGraphic()); // button graphics aren't children until skinned
        if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) applyPhosphorIcons(c);
    }

    /** Icon node for code-built UI. colorClass: ic-white | ic-nav | ic-muted | ic-soft | ic-blue | ic-dark | ic-danger */
    private StackPane icon(String name, double size, String colorClass) {
        StackPane holder = new StackPane();
        holder.getStyleClass().addAll("icon", colorClass);
        fillIconHolder(holder, name, size);
        return holder;
    }

    private void fillIconHolder(StackPane holder, String name, double size) {
        SVGPath p = new SVGPath();
        p.setContent(iconPath(name));
        p.getStyleClass().add("svg-icon");

        Pane box = new Pane(p);                       // 256x256 Phosphor viewBox
        box.setMinSize(256, 256); box.setPrefSize(256, 256); box.setMaxSize(256, 256);
        box.setScaleX(size / 256.0);
        box.setScaleY(size / 256.0);

        holder.getChildren().setAll(new Group(box));  // Group => layout bounds include the scale
        holder.setMinSize(size, size); holder.setPrefSize(size, size); holder.setMaxSize(size, size);
        holder.setMouseTransparent(true);
    }

    /** Your PhosphorIcons class wins where it has the icon; PhosphorPhosphorIcons covers the rest. */
    private String iconPath(String name) {
        return PhosphorIcons.path(name);
    }

    // ═════════════════════════════════════════════════════════════════
    // UI UTILS
    // ═════════════════════════════════════════════════════════════════
    private StackPane avatar(Contact c, double size, double fontSize, boolean filled) {
        Label l = new Label(initialsOf(c.getName()));
        l.getStyleClass().add(filled ? "avatar-text-filled" : "avatar-text-light");
        l.setStyle("-fx-font-size: " + fontSize + "px;");
        StackPane p = new StackPane(l);
        p.getStyleClass().add(filled ? "avatar-filled" : "avatar-light");
        p.setMinSize(size, size); p.setPrefSize(size, size); p.setMaxSize(size, size);
        return p;
    }

    private HBox iconLabel(String iconName, double size, String colorClass, String text, String labelClass) {
        HBox h = new HBox(5, icon(iconName, size, colorClass), styled(text, labelClass));
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private Label styled(String text, String styleClass) {
        Label l = new Label(text);
        l.getStyleClass().add(styleClass);
        return l;
    }

    private Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private void show(Node n, boolean on) { n.setVisible(on); n.setManaged(on); }

    private String initialsOf(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }

    private Comparator<Contact> byName(boolean ascending) {
        Comparator<Contact> cmp = Comparator.comparing(c -> c.getName() == null ? "" : c.getName(), String.CASE_INSENSITIVE_ORDER);
        return ascending ? cmp : cmp.reversed();
    }

    private boolean containsIgnoreCase(String field, String q) { return field != null && field.toLowerCase().contains(q); }

    private String dash(String s) { return (s == null || s.isBlank()) ? "—" : s; }

    private String addedText(Contact c) { return dash(c.getFormattedDateAdded()); }

    /** Detail line under a contact's name, driven by the Settings display format. */
    private String subtitle(Contact c) {
        switch (displayFormat) {
            case EMAIL: return dash(c.getEmail());
            case BOTH:  return dash(c.getPhone()) + "  ·  " + dash(c.getEmail());
            default:    return dash(c.getPhone());
        }
    }

    private String joinNames(List<String> names) {
        if (names.size() <= 1) return String.join("", names);
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    private String plural(long n, String word) { return n + " " + word + (n == 1 ? "" : "s"); }

    // dialogs
    private boolean confirm(String title, String msg, String action) {
        ButtonType yes = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, msg, yes, ButtonType.CANCEL);
        a.setTitle(title);
        a.setHeaderText(null);
        return a.showAndWait().filter(b -> b == yes).isPresent();
    }

    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setTitle(title); a.setHeaderText(title); a.showAndWait();
    }

    private void error(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg == null ? "An unexpected error occurred." : msg, ButtonType.OK);
        a.setHeaderText(title); a.showAndWait();
    }
}