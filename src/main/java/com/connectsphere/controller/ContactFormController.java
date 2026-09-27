package com.connectsphere.controller;

import com.connectsphere.model.Contact;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller for contact-form.fxml — the Add/Edit Contact dialog.
 *
 * Usage from MainController:
 *   FXMLLoader loader = new FXMLLoader(getClass().getResource(".../contact-form.fxml"));
 *   Parent root = loader.load();
 *   ContactFormController controller = loader.getController();
 *   Stage stage = new Stage();
 *   controller.setDialogStage(stage);
 *   controller.initForAdd(allOtherContacts);              // or:
 *   controller.initForEdit(existingContact, allOtherContacts, existingConnectionIds);
 *   stage.setScene(new Scene(root));
 *   stage.showAndWait();
 *   if (controller.isSaved()) { ...read getName()/getPhone()/getEmail()/getSelectedConnectionIds()... }
 */
public class ContactFormController {

    @FXML private Label formTitleLabel;
    @FXML private TextField nameField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private Label validationLabel;

    @FXML private Label selectedCountLabel;
    @FXML private TextField connectionSearchField;
    @FXML private ListView<Contact> connectionsListView;

    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private Stage dialogStage;
    private boolean saved = false;

    // The full candidate list (every other contact) and the currently
    // checked subset, tracked by contact id so it survives search filtering.
    private final ObservableList<Contact> allCandidates = FXCollections.observableArrayList();
    private final Set<Integer> selectedIds = new HashSet<>();

    @FXML
    private void initialize() {
        connectionsListView.setCellFactory(lv -> new ConnectionCheckCell());
        connectionSearchField.textProperty().addListener((obs, oldVal, newVal) -> applyConnectionFilter(newVal));

        saveButton.disableProperty().bind(nameField.textProperty().isEmpty());

        nameField.textProperty().addListener((obs, oldVal, newVal) -> hideValidationMessage());
    }

    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    /** Configures the dialog for adding a brand-new contact. */
    public void initForAdd(List<Contact> candidateConnections) {
        formTitleLabel.setText("Add Contact");
        saveButton.setText("Add Contact");
        allCandidates.setAll(candidateConnections);
        connectionsListView.setItems(allCandidates);
    }

    /**
     * Configures the dialog for editing an existing contact. Pre-fills the
     * fields and pre-checks any contacts already connected to them.
     */
    public void initForEdit(Contact existing, List<Contact> candidateConnections, Set<Integer> existingConnectionIds) {
        formTitleLabel.setText("Edit Contact");
        saveButton.setText("Save Changes");

        nameField.setText(existing.getName());
        phoneField.setText(existing.getPhone());
        emailField.setText(existing.getEmail());

        allCandidates.setAll(candidateConnections);
        connectionsListView.setItems(allCandidates);
        selectedIds.addAll(existingConnectionIds);
        updateSelectedCountLabel();
        connectionsListView.refresh();
    }

    private void applyConnectionFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) {
            connectionsListView.setItems(allCandidates);
            return;
        }
        List<Contact> filtered = allCandidates.stream()
                .filter(c -> c.getName() != null && c.getName().toLowerCase().contains(q))
                .collect(Collectors.toList());
        connectionsListView.setItems(FXCollections.observableArrayList(filtered));
    }

    private void updateSelectedCountLabel() {
        int n = selectedIds.size();
        selectedCountLabel.setText(n + (n == 1 ? " selected" : " selected"));
    }

    private void hideValidationMessage() {
        validationLabel.setVisible(false);
        validationLabel.setManaged(false);
    }

    private void showValidationMessage(String message) {
        validationLabel.setText(message);
        validationLabel.setVisible(true);
        validationLabel.setManaged(true);
    }

    @FXML
    private void onSave() {
        if (nameField.getText() == null || nameField.getText().trim().isEmpty()) {
            showValidationMessage("Name is required.");
            return;
        }
        saved = true;
        if (dialogStage != null) dialogStage.close();
    }

    @FXML
    private void onCancel() {
        saved = false;
        if (dialogStage != null) dialogStage.close();
    }

    public boolean isSaved() {
        return saved;
    }

    public String getName() {
        return nameField.getText().trim();
    }

    public String getPhone() {
        return phoneField.getText() == null ? "" : phoneField.getText().trim();
    }

    public String getEmail() {
        return emailField.getText() == null ? "" : emailField.getText().trim();
    }

    /** IDs of every existing contact the user checked to connect with. */
    public Set<Integer> getSelectedConnectionIds() {
        return new HashSet<>(selectedIds);
    }

    /** One row in the connections picker: a checkbox-like toggle + avatar + name. */
    private class ConnectionCheckCell extends ListCell<Contact> {
        private final CheckBox checkBox = new CheckBox();
        private final Label avatarLabel = new Label();
        private final Region avatarBox = new Region();
        private final Label nameLabel = new Label();
        private final HBox row = new HBox(10);

        ConnectionCheckCell() {
            avatarBox.getStyleClass().add("connection-avatar");
            avatarLabel.getStyleClass().add("connection-avatar-label");
            nameLabel.getStyleClass().add("connection-name");

            javafx.scene.layout.StackPane avatarStack = new javafx.scene.layout.StackPane(avatarLabel);
            avatarStack.getStyleClass().add("connection-avatar");

            row.getChildren().setAll(checkBox, avatarStack, nameLabel);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("connection-row");
            row.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(nameLabel, Priority.ALWAYS);

            // Clicking anywhere in the row toggles the checkbox, not just
            // the tiny checkbox hitbox.
            row.setOnMouseClicked(e -> {
                if (getItem() == null) return;
                checkBox.setSelected(!checkBox.isSelected());
            });
            checkBox.setOnAction(e -> toggleSelection());
        }

        private void toggleSelection() {
            Contact c = getItem();
            if (c == null) return;
            if (checkBox.isSelected()) {
                selectedIds.add(c.getId());
            } else {
                selectedIds.remove(c.getId());
            }
            updateSelectedCountLabel();
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
            checkBox.setSelected(selectedIds.contains(contact.getId()));
            setGraphic(row);
        }

        private String initialsOf(String name) {
            if (name == null || name.isBlank()) return "?";
            String[] parts = name.trim().split("\\s+");
            String first = parts[0].substring(0, 1);
            String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
            return (first + last).toUpperCase();
        }
    }
}