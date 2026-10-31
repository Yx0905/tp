package seedu.address.logic;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.ObservableList;
import seedu.address.commons.core.GuiSettings;
import seedu.address.commons.core.LogsCenter;
import seedu.address.logic.commands.Command;
import seedu.address.logic.commands.CommandResult;
import seedu.address.logic.commands.ExitCommand;
import seedu.address.logic.commands.PendingRequest;
import seedu.address.logic.commands.exceptions.CommandException;
import seedu.address.logic.parser.AliasCommandParser;
import seedu.address.logic.parser.ContactFileParser;
import seedu.address.logic.parser.exceptions.ParseException;
import seedu.address.model.FieldOrder;
import seedu.address.model.Model;
import seedu.address.model.ModelSnapshot;
import seedu.address.model.person.Person;
import seedu.address.storage.Storage;

/**
 * The main LogicManager of the app.
 */
public class LogicManager implements Logic {
    public static final String FILE_OPS_ERROR_FORMAT = "Could not save data due to the following error: %s";

    public static final String FILE_OPS_PERMISSION_ERROR_FORMAT =
            "Could not save data to file %s due to insufficient permissions to write to the file or the folder.";

    /** Shown before the result of a command entered through a shortcut, so the user can see what ran. */
    public static final String MESSAGE_SHORTCUT_EXPANDED = "Ran: %s\n";

    private final Logger logger = LogsCenter.getLogger(LogicManager.class);

    private final Model model;
    private final Storage storage;
    private final UndoManager undoManager;
    private final ContactFileParser contactFileParser;

    private PendingRequest pendingRequest;
    private ModelSnapshot pendingSnapshot;
    private Command pendingCommand;
    private List<Person> pendingPersonsBeforeCommand;

    /**
     * Constructs a {@code LogicManager} with the given {@code Model} and {@code Storage}.
     */
    public LogicManager(Model model, Storage storage) {
        this.model = model;
        this.storage = storage;
        undoManager = new UndoManager();
        contactFileParser = new ContactFileParser(undoManager);
        // Shortcuts loaded from a hand-edited preferences file have not been checked yet.
        model.setCommandAliases(AliasCommandParser.validAliases(model.getCommandAliases()));
    }

    @Override
    public CommandResult execute(String commandText) throws CommandException, ParseException {
        assertPendingState();
        logger.fine("Executing input; pending=" + (pendingRequest != null));

        ModelSnapshot snapshot = pendingRequest == null || pendingRequest.isReadOnly()
                ? new ModelSnapshot(model) : pendingSnapshot;
        List<Person> personsBeforeCommand = pendingRequest != null && !pendingRequest.isReadOnly()
                ? pendingPersonsBeforeCommand : List.copyOf(model.getContactFile().getPersonList());
        Command command = pendingRequest == null ? null : pendingCommand;
        try {
            CommandResult commandResult;
            if (pendingRequest != null) {
                String response = commandText.trim();
                if (response.equalsIgnoreCase("n") || response.equalsIgnoreCase("cancel")) {
                    snapshot.restore(model);
                    clearPendingRequest();
                    logger.info("Pending request cancelled; model and view restored");
                    return new CommandResult("Request cancelled. No contacts changed.");
                }
                if (isExitCommand(response) || pendingRequest.shouldRunAsCommand(response)) {
                    clearPendingRequest();
                    command = contactFileParser.parseCommand(commandText, model.getCommandAliases());
                    personsBeforeCommand = List.copyOf(model.getContactFile().getPersonList());
                    logger.fine("Executing " + command.getClass().getSimpleName());
                    commandResult = command.execute(model);
                } else {
                    commandResult = pendingRequest.respond(response, model);
                }
            } else {
                String shortcutNote = shortcutNote(commandText);
                try {
                    command = contactFileParser.parseCommand(commandText, model.getCommandAliases());
                    logger.fine("Executing " + command.getClass().getSimpleName());
                    commandResult = command.execute(model).withFeedbackPrefix(shortcutNote);
                } catch (ParseException pe) {
                    throw shortcutNote.isEmpty() ? pe : new ParseException(shortcutNote + pe.getMessage(), pe);
                } catch (CommandException ce) {
                    throw shortcutNote.isEmpty() ? ce : new CommandException(shortcutNote + ce.getMessage(), ce);
                }
            }

            if (commandResult.getPendingRequest().isPresent()) {
                assert !snapshot.hasDataChanged(model) : "Pending requests must not mutate contact data";
                assert !snapshot.hasPreferencesChanged(model) : "Pending requests must not mutate preferences";
                pendingRequest = commandResult.getPendingRequest().orElseThrow();
                pendingSnapshot = snapshot;
                pendingCommand = command;
                pendingPersonsBeforeCommand = personsBeforeCommand;
                logger.fine("Awaiting " + pendingRequest.getClass().getSimpleName());
            } else {
                if (commandResult.shouldSaveContactFile() && snapshot.hasDataChanged(model)) {
                    storage.saveContactFile(model.getContactFile());
                    logger.info("Contact transaction saved for " + command.getClass().getSimpleName() + ": "
                            + personsBeforeCommand.size() + " -> "
                            + model.getContactFile().getPersonList().size() + " contacts");
                }
                // Shortcuts and the field order live in the preferences file; save them now rather than
                // only when the app closes. A failed save restores the snapshot, so nothing is half-applied.
                if (snapshot.hasPreferencesChanged(model)) {
                    storage.saveUserPrefs(model.getUserPrefs());
                    logger.info("Preference transaction saved");
                }
                undoManager.recordChange(command, personsBeforeCommand, model.getContactFile().getPersonList(),
                        commandResult.getFeedbackToUser());
                clearPendingRequest();
            }
            assertPendingState();
            return commandResult;
        } catch (AccessDeniedException e) {
            logger.log(Level.WARNING, "Save permission denied; rolling back transaction", e);
            restoreFailedRequest(snapshot);
            throw new CommandException(String.format(FILE_OPS_PERMISSION_ERROR_FORMAT, e.getMessage()), e);
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "Save failed; rolling back transaction", ioe);
            restoreFailedRequest(snapshot);
            throw new CommandException(String.format(FILE_OPS_ERROR_FORMAT, ioe.getMessage()), ioe);
        } catch (CommandException | ParseException e) {
            logger.fine("Request rejected; restoring model and clearing pending state");
            restoreFailedRequest(snapshot);
            throw e;
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Unexpected command failure; rolling back transaction", e);
            restoreFailedRequest(snapshot);
            throw e;
        }
    }

    /**
     * Returns the note shown before the result of a command entered through a shortcut, or an empty string if
     * {@code commandText} does not start with one. A shortcut's result, and any error it causes, starts with
     * the command it expanded to, since the user never typed that command.
     */
    private String shortcutNote(String commandText) {
        String expandedText = ContactFileParser.expandShortcut(commandText, model.getCommandAliases());
        return expandedText.equals(commandText) ? "" : String.format(MESSAGE_SHORTCUT_EXPANDED, expandedText.trim());
    }

    /**
     * Returns whether {@code response} runs the exit command, including through a shortcut or with ignored
     * arguments, so that exiting is never blocked by a pending request.
     */
    private boolean isExitCommand(String response) {
        String expandedText = ContactFileParser.expandShortcut(response, model.getCommandAliases()).trim();
        String commandWord = expandedText.split("\\s+", 2)[0];
        return commandWord.equals(ExitCommand.COMMAND_WORD) || response.equalsIgnoreCase(ExitCommand.COMMAND_WORD);
    }

    private void restoreFailedRequest(ModelSnapshot snapshot) {
        snapshot.restore(model);
        clearPendingRequest();
    }

    private void assertPendingState() {
        assert (pendingRequest == null) == (pendingSnapshot == null) : "Pending request needs its snapshot";
        assert (pendingRequest == null) == (pendingPersonsBeforeCommand == null)
                : "Pending request needs its original contact list";
    }

    private void clearPendingRequest() {
        pendingRequest = null;
        pendingSnapshot = null;
        pendingCommand = null;
        pendingPersonsBeforeCommand = null;
    }

    @Override
    public ObservableList<Person> getFilteredPersonList() {
        return model.getFilteredPersonList();
    }

    @Override
    public ReadOnlyObjectProperty<FieldOrder> getFieldOrderProperty() {
        return model.getFieldOrderProperty();
    }

    @Override
    public GuiSettings getGuiSettings() {
        return model.getGuiSettings();
    }

    @Override
    public void setGuiSettings(GuiSettings guiSettings) {
        model.setGuiSettings(guiSettings);
    }
}
