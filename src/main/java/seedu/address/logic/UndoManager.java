package seedu.address.logic;

import static seedu.address.model.Model.PREDICATE_SHOW_ALL_PERSONS;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import seedu.address.logic.commands.AddCommand;
import seedu.address.logic.commands.ClearCommand;
import seedu.address.logic.commands.Command;
import seedu.address.logic.commands.CommandResult;
import seedu.address.logic.commands.ConfirmationRequest;
import seedu.address.logic.commands.DeleteCommand;
import seedu.address.logic.commands.DropCommand;
import seedu.address.logic.commands.EditCommand;
import seedu.address.logic.commands.IntroduceCommand;
import seedu.address.logic.commands.exceptions.CommandException;
import seedu.address.model.ContactFile;
import seedu.address.model.Model;
import seedu.address.model.person.Person;

/**
 * Stores successful data-changing command history for one application session.
 */
public class UndoManager {
    public static final String MESSAGE_NOTHING_TO_UNDO = "There is nothing to undo.";
    public static final String MESSAGE_CONFIRM_UNDO = "Undo the following %1$s?\n%2$s";
    public static final String MESSAGE_UNDO_SUCCESS = "Restored contact:\n%1$s";
    public static final String MESSAGE_UNDO_CONFLICT =
            "The contact cannot be restored because it would violate contact uniqueness rules. No changes were made.";
    public static final String MESSAGE_HISTORY_EMPTY = "There is no data-changing command history.";

    private final List<HistoryEntry> history = new ArrayList<>();
    private HistoryEntry undoableEntry;
    private HistoryEntry pendingEntry;
    private HistoryEntry uncommittedUndo;

    /**
     * Records a successful data-changing command and updates the available undo action.
     */
    public void recordChange(Command command, List<Person> before, List<Person> after, String feedback) {
        if (before.equals(after)) {
            return;
        }
        String action = getHistoryAction(command);
        HistoryEntry entry = action == null ? null : new HistoryEntry(action, feedback, before, after);
        if (entry != null) {
            history.add(entry);
        }
        if (command instanceof EditCommand || command instanceof DeleteCommand) {
            undoableEntry = entry;
        } else {
            undoableEntry = null;
        }
        pendingEntry = null;
    }

    private String getHistoryAction(Command command) {
        if (command instanceof AddCommand) {
            return "addition";
        } else if (command instanceof EditCommand) {
            return "edit";
        } else if (command instanceof DeleteCommand) {
            return "deletion";
        } else if (command instanceof DropCommand) {
            return "drop";
        } else if (command instanceof ClearCommand) {
            return "clear";
        } else if (command instanceof IntroduceCommand) {
            return "introduce";
        }
        return null;
    }

    /**
     * Starts an undo operation and waits for confirmation.
     */
    public CommandResult executeUndo() {
        if (undoableEntry == null) {
            pendingEntry = null;
            return new CommandResult(MESSAGE_NOTHING_TO_UNDO, false, false, false);
        }
        pendingEntry = undoableEntry;
        ConfirmationRequest request = new ConfirmationRequest(String.format(MESSAGE_CONFIRM_UNDO,
                undoableEntry.action, getDisplayFeedback(undoableEntry)), new Command() {
                    @Override
                    public CommandResult execute(Model model) throws CommandException {
                        return confirmUndo(model);
                    }
                });
        return new CommandResult(request.getPrompt(), request);
    }

    private String getDisplayFeedback(HistoryEntry entry) {
        if (!"deletion".equals(entry.action)) {
            return entry.feedback;
        }
        int lastLineStart = entry.feedback.lastIndexOf('\n');
        return lastLineStart < 0 ? entry.feedback : entry.feedback.substring(0, lastLineStart);
    }

    private CommandResult confirmUndo(Model model) {
        if (pendingEntry != undoableEntry || undoableEntry == null) {
            return new CommandResult(MESSAGE_NOTHING_TO_UNDO, false, false, false);
        }
        List<Person> restoredPersons = undoableEntry.getRestoredPersons();
        List<Person> personsToReplace = undoableEntry.getPersonsToReplace();
        List<Person> currentPersons = new ArrayList<>(model.getContactFile().getPersonList());
        if (restoredPersons.isEmpty() || !personsToReplace.stream().allMatch(currentPersons::contains)) {
            pendingEntry = null;
            return new CommandResult(MESSAGE_UNDO_CONFLICT, false, false, false);
        }

        removeReplacedPersons(currentPersons, personsToReplace);
        restorePersons(currentPersons, restoredPersons);
        if (violatesUniqueness(currentPersons)) {
            pendingEntry = null;
            return new CommandResult(MESSAGE_UNDO_CONFLICT, false, false, false);
        }

        updateModel(model, currentPersons);
        // The entry stays available until the restored contacts are saved; see commitUndo and discardUndo.
        uncommittedUndo = undoableEntry;
        pendingEntry = null;
        undoableEntry = null;
        return new CommandResult(String.format(MESSAGE_UNDO_SUCCESS, formatRestoredPersons(restoredPersons)));
    }

    /**
     * Marks a confirmed undo as done once the restored contacts have been saved.
     */
    public void commitUndo() {
        if (uncommittedUndo != null) {
            uncommittedUndo.isUndone = true;
            uncommittedUndo = null;
        }
    }

    /**
     * Keeps an undo that was confirmed but not saved available to undo again, and leaves its history unmarked.
     */
    public void discardUndo() {
        if (uncommittedUndo != null) {
            undoableEntry = uncommittedUndo;
            uncommittedUndo = null;
        }
    }

    private void restorePersons(List<Person> currentPersons, List<Person> restoredPersons) {
        for (Person restored : restoredPersons) {
            int originalIndex = undoableEntry.getOriginalIndex(restored);
            currentPersons.add(Math.min(originalIndex, currentPersons.size()), restored);
        }
    }

    private void removeReplacedPersons(List<Person> currentPersons, List<Person> personsToReplace) {
        for (Person personToReplace : personsToReplace) {
            currentPersons.remove(personToReplace);
        }
    }

    private String formatRestoredPersons(List<Person> restoredPersons) {
        return restoredPersons.stream().map(Messages::format).collect(Collectors.joining("\n"));
    }

    private void updateModel(Model model, List<Person> persons) {
        ContactFile restoredContactFile = new ContactFile();
        restoredContactFile.setPersons(persons);
        model.setContactFile(restoredContactFile);
        model.updateFilteredPersonList(PREDICATE_SHOW_ALL_PERSONS);
    }

    /**
     * Returns the edit and deletion history for this session.
     */
    public String getHistory() {
        if (history.isEmpty()) {
            return MESSAGE_HISTORY_EMPTY;
        }
        List<String> lines = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            HistoryEntry entry = history.get(i);
            lines.add(String.format("%d. %s%s: %s", history.size() - i, entry.action,
                    entry.isUndone ? " (undone)" : "", getDisplayFeedback(entry).replace("\n", " ")));
        }
        return String.join("\n", lines);
    }

    private static boolean violatesUniqueness(List<Person> persons) {
        for (int i = 0; i < persons.size(); i++) {
            Person first = persons.get(i);
            for (int j = i + 1; j < persons.size(); j++) {
                Person second = persons.get(j);
                boolean samePhone = first.getPhone().isPresent() && second.getPhone().isPresent()
                        && first.hasSamePhone(second);
                if (first.isSamePerson(second) || samePhone || first.hasSameEmail(second)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class HistoryEntry {
        private final String action;
        private final String feedback;
        private final List<Person> before;
        private final List<Person> after;
        private boolean isUndone;

        private HistoryEntry(String action, String feedback, List<Person> before, List<Person> after) {
            this.action = action;
            this.feedback = feedback;
            this.before = List.copyOf(before);
            this.after = List.copyOf(after);
        }

        private List<Person> getRestoredPersons() {
            return before.stream().filter(person -> !after.contains(person)).toList();
        }

        private List<Person> getPersonsToReplace() {
            return after.stream().filter(person -> !before.contains(person)).toList();
        }

        private int getOriginalIndex(Person restored) {
            return before.indexOf(restored);
        }
    }
}
