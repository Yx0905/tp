package seedu.address.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import seedu.address.commons.core.GuiSettings;
import seedu.address.commons.core.LogsCenter;
import seedu.address.logic.Logic;
import seedu.address.logic.commands.CommandResult;
import seedu.address.logic.commands.ConfirmationRequest;
import seedu.address.logic.commands.exceptions.CommandException;
import seedu.address.logic.parser.exceptions.ParseException;
import seedu.address.model.FollowUpEntry;
import seedu.address.model.person.Person;

/**
 * The Main Window. Provides the basic application layout containing
 * a menu bar and space where other JavaFX elements can be placed.
 */
public class MainWindow extends UiPart<Stage> {

    private static final String FXML = "MainWindow.fxml";

    private final Logger logger = LogsCenter.getLogger(getClass());

    private Stage primaryStage;
    private Logic logic;
    private Path dataFilePath;

    // Independent Ui parts residing in this Ui container
    private PersonListPanel personListPanel;
    private ResultDisplay resultDisplay;
    private ConfirmationPanel confirmationPanel;
    private HelpWindow helpWindow;
    private PersonDetailsPanel personDetailsPanel;
    private Person selectedContact;
    private FollowUpEntry selectedFollowUp;
    private FollowUpListPanel followUpListPanel;
    private boolean isRequestPending;
    private Person selectionBeforeRequest;
    private List<Person> listBeforeRequest;

    @FXML
    private StackPane personDetailsPlaceholder;

    @FXML
    private Label lastCommand;

    @FXML
    private StackPane commandBoxPlaceholder;

    @FXML
    private MenuItem helpMenuItem;

    @FXML
    private StackPane personListPanelPlaceholder;

    @FXML
    private StackPane resultDisplayPlaceholder;

    @FXML
    private StackPane statusbarPlaceholder;

    @FXML
    private StackPane confirmationPanelPlaceholder;

    /**
     * Creates a {@code MainWindow} with the given {@code Stage}, {@code Logic},
     * and the data file path to show in the status bar.
     */
    public MainWindow(Stage primaryStage, Logic logic, Path dataFilePath) {
        super(FXML, primaryStage);

        // Set dependencies
        this.primaryStage = primaryStage;
        this.logic = logic;
        this.dataFilePath = dataFilePath;

        // Configure the UI
        setWindowDefaultSize(logic.getGuiSettings());

        setAccelerators();

        helpWindow = new HelpWindow();
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    private void setAccelerators() {
        setAccelerator(helpMenuItem, KeyCombination.valueOf("F1"));
    }

    /**
     * Sets the accelerator of a MenuItem.
     * @param keyCombination the KeyCombination value of the accelerator
     */
    private void setAccelerator(MenuItem menuItem, KeyCombination keyCombination) {
        menuItem.setAccelerator(keyCombination);

        /*
         * TODO: the code below can be removed once the bug reported here
         * https://bugs.openjdk.java.net/browse/JDK-8131666
         * is fixed in a later version of the SDK.
         *
         * According to the bug report, TextInputControl (TextField, TextArea) will
         * consume function-key events. Because CommandBox contains a TextField and
         * ResultDisplay contains a TextArea, some accelerators (e.g., F1) will
         * not work when the focus is in them because the key event is consumed by
         * the TextInputControl(s).
         *
         * For now, we add the following event filter to capture such key events and open
         * the help window purposely so as to support accelerators even when focus is
         * in CommandBox or ResultDisplay.
         */
        getRoot().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getTarget() instanceof TextInputControl && keyCombination.match(event)) {
                menuItem.getOnAction().handle(new ActionEvent());
                event.consume();
            }
        });
    }

    /**
     * Fills the window's placeholders and displays feedback for a successful
     * contact-file load.
     */
    void fillInnerParts(String startupFeedback) {
        personListPanel = new PersonListPanel(logic.getFilteredPersonList(), logic.getFieldOrderProperty(), true);
        personListPanelPlaceholder.getChildren().add(personListPanel.getRoot());

        personDetailsPanel = new PersonDetailsPanel();
        personDetailsPlaceholder.getChildren().add(personDetailsPanel.getRoot());
        personListPanel.selectedPersonProperty().addListener((observable, previous, current) -> showContact(current));
        logic.getFilteredPersonList().addListener((ListChangeListener<Person>) change -> {
            showContactList();
            personListPanel.selectFirstIfNeeded();
            showContact(personListPanel.selectedPersonProperty().get());
        });
        personListPanel.selectFirstIfNeeded();

        followUpListPanel = new FollowUpListPanel();
        followUpListPanel.getRoot().setVisible(false);
        followUpListPanel.getRoot().setManaged(false);
        personListPanelPlaceholder.getChildren().add(followUpListPanel.getRoot());
        followUpListPanel.selectedEntryProperty().addListener((observable, previous, current) -> {
            if (followUpListPanel.getRoot().isVisible()) {
                showFollowUp(current);
            }
        });

        resultDisplay = new ResultDisplay();
        logic.getFieldOrderProperty().addListener((observable, previous, current) -> {
            refreshDetails();
        });
        resultDisplayPlaceholder.getChildren().add(resultDisplay.getRoot());

        StatusBarFooter statusBarFooter = new StatusBarFooter(dataFilePath);
        statusbarPlaceholder.getChildren().add(statusBarFooter.getRoot());

        CommandBox commandBox = new CommandBox(this::executeCommand);
        commandBoxPlaceholder.getChildren().add(commandBox.getRoot());

        confirmationPanel = new ConfirmationPanel(response -> {
            try {
                executeCommand(response);
            } catch (CommandException | ParseException | RuntimeException e) {
                // executeCommand has already displayed the failure and cleared the panel.
            }
            commandBox.getRoot().lookup("#commandTextField").requestFocus();
        });
        confirmationPanelPlaceholder.getChildren().add(confirmationPanel.getRoot());
        confirmationPanelPlaceholder.managedProperty().bind(confirmationPanel.getRoot().managedProperty());
        confirmationPanelPlaceholder.visibleProperty().bind(confirmationPanel.getRoot().visibleProperty());
        resultDisplay.setFeedbackToUser(startupFeedback);
    }

    /**
     * Sets the default size based on {@code guiSettings}.
     */
    private void setWindowDefaultSize(GuiSettings guiSettings) {
        primaryStage.setHeight(guiSettings.getWindowHeight());
        primaryStage.setWidth(guiSettings.getWindowWidth());
        if (guiSettings.getWindowCoordinates() != null) {
            primaryStage.setX(guiSettings.getWindowCoordinates().getX());
            primaryStage.setY(guiSettings.getWindowCoordinates().getY());
        }
    }

    /**
     * Opens the help window or focuses on it if it's already opened.
     */
    @FXML
    public void handleHelp() {
        if (!helpWindow.isShowing()) {
            helpWindow.show();
        } else {
            helpWindow.focus();
        }
    }

    void show() {
        primaryStage.show();
    }

    /**
     * Closes the application.
     */
    @FXML
    private void handleExit() {
        GuiSettings guiSettings = new GuiSettings(primaryStage.getWidth(), primaryStage.getHeight(),
                (int) primaryStage.getX(), (int) primaryStage.getY());
        logic.setGuiSettings(guiSettings);
        helpWindow.hide();
        primaryStage.hide();
    }

    public PersonListPanel getPersonListPanel() {
        return personListPanel;
    }

    /**
     * Executes the command and returns the result.
     *
     * @see seedu.address.logic.Logic#execute(String)
     */
    private CommandResult executeCommand(String commandText) throws CommandException, ParseException {
        lastCommand.setText(commandText);
        if (!isRequestPending) {
            selectionBeforeRequest = personListPanel.selectedPersonProperty().get();
            listBeforeRequest = List.copyOf(logic.getFilteredPersonList());
        }
        try {
            CommandResult commandResult = logic.execute(commandText);
            logger.fine("Command result displayed; confirmation=" + commandResult.getPendingRequest().isPresent());
            isRequestPending = commandResult.getPendingRequest().isPresent();
            confirmationPanel.showRequest(commandResult.getPendingRequest().orElse(null));
            displayResult(commandResult);
            if (!isRequestPending && commandResult.getPersonForDetails().isEmpty()
                    && commandResult.getFollowUpListing().isEmpty()) {
                restoreSelectionIfViewRestored();
            }

            if (commandResult.isShowHelp()) {
                handleHelp();
            }

            if (commandResult.isExit()) {
                handleExit();
            }

            return commandResult;
        } catch (CommandException | ParseException | RuntimeException e) {
            logger.fine("Command failed: " + e.getClass().getSimpleName());
            isRequestPending = false;
            confirmationPanel.showRequest(null);
            showContactList();
            restoreSelectionIfViewRestored();
            showContact(personListPanel.selectedPersonProperty().get());
            resultDisplay.setFeedbackToUser(e.getMessage() == null ? "Unexpected command failure." : e.getMessage());
            throw e;
        }
    }

    private void displayResult(CommandResult result) {
        if (result.getFollowUpListing().isPresent()) {
            personListPanel.getRoot().setVisible(false);
            personListPanel.getRoot().setManaged(false);
            followUpListPanel.getRoot().setVisible(true);
            followUpListPanel.getRoot().setManaged(true);
            followUpListPanel.showListing(result.getFollowUpListing().orElseThrow());
            showFollowUp(followUpListPanel.selectedEntryProperty().get());
            resultDisplay.setFeedbackToUser(result.getFeedbackToUser().lines().findFirst().orElse(""));
        } else if (result.getFollowUpForDetails().isPresent()) {
            FollowUpEntry entry = new FollowUpEntry(result.getPersonForDetails().orElseThrow(),
                    result.getFollowUpForDetails().orElseThrow());
            followUpListPanel.selectEntry(entry);
            showFollowUp(entry);
            resultDisplay.setFeedbackToUser("Viewing follow-up #" + entry.followUp().getId()
                    + " for " + entry.contact().getName().fullName + ".");
        } else {
            showContactList();
            result.getPersonForDetails().ifPresent(person -> {
                personListPanel.selectPerson(person);
                showContact(person);
            });
            if (result.getPersonForDetails().isPresent()) {
                resultDisplay.setFeedbackToUser("Viewing " + result.getPersonForDetails().orElseThrow().getName()
                        + ".");
            } else if (result.getPendingRequest().orElse(null) instanceof ConfirmationRequest) {
                resultDisplay.setFeedbackToUser("Awaiting confirmation. Review the preview before continuing.");
            } else {
                resultDisplay.setFeedbackToUser(result.getFeedbackToUser());
            }
        }
    }

    /**
     * Reselects the contact chosen before the latest request when the request left the displayed list as it was,
     * such as after a cancellation or a rolled-back save, since restoring the list resets the selection.
     */
    private void restoreSelectionIfViewRestored() {
        if (selectionBeforeRequest != null && logic.getFilteredPersonList().equals(listBeforeRequest)) {
            personListPanel.selectPerson(selectionBeforeRequest);
            showContact(personListPanel.selectedPersonProperty().get());
        }
    }

    private void showContactList() {
        personListPanel.getRoot().setVisible(true);
        personListPanel.getRoot().setManaged(true);
        if (followUpListPanel != null) {
            followUpListPanel.getRoot().setVisible(false);
            followUpListPanel.getRoot().setManaged(false);
        }
        selectedFollowUp = null;
        selectedContact = personListPanel.selectedPersonProperty().get();
        refreshDetails();
    }

    private void showContact(Person person) {
        selectedContact = person;
        selectedFollowUp = null;
        refreshDetails();
    }

    private void showFollowUp(FollowUpEntry entry) {
        selectedFollowUp = entry;
        selectedContact = entry == null ? null : entry.contact();
        refreshDetails();
    }

    private void refreshDetails() {
        if (selectedFollowUp == null) {
            personDetailsPanel.showPerson(selectedContact, logic.getFieldOrderProperty().get());
        } else {
            personDetailsPanel.showFollowUp(selectedFollowUp, logic.getFieldOrderProperty().get());
        }
    }
}
