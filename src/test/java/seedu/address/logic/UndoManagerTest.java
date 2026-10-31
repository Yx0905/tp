package seedu.address.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static seedu.address.testutil.TypicalPersons.ALICE;
import static seedu.address.testutil.TypicalPersons.BENSON;
import static seedu.address.testutil.TypicalPersons.CARL;

import java.util.List;

import org.junit.jupiter.api.Test;

import seedu.address.commons.core.index.Index;
import seedu.address.logic.commands.DeleteCommand;
import seedu.address.logic.commands.EditCommand;
import seedu.address.logic.commands.PendingRequest;
import seedu.address.model.ContactFile;
import seedu.address.model.Model;
import seedu.address.model.ModelManager;
import seedu.address.model.UserPrefs;
import seedu.address.model.person.Person;
import seedu.address.testutil.PersonBuilder;

public class UndoManagerTest {

    @Test
    public void executeUndo_confirmationForStaleEntry_reportsNothingToUndo() throws Exception {
        UndoManager undoManager = new UndoManager();
        DeleteCommand deleteAlice = new DeleteCommand(Index.fromOneBased(1));
        undoManager.recordChange(deleteAlice, List.of(ALICE), List.of(), "Deleted Alice");
        Model model = new ModelManager();
        PendingRequest request = undoManager.executeUndo().getPendingRequest().orElseThrow();
        DeleteCommand deleteBenson = new DeleteCommand(Index.fromOneBased(1));
        undoManager.recordChange(deleteBenson, List.of(BENSON), List.of(), "Deleted Benson");

        assertEquals(UndoManager.MESSAGE_NOTHING_TO_UNDO,
                request.respond("y", model).getFeedbackToUser());
    }

    @Test
    public void executeUndo_confirmationAfterUndo_reportsNothingToUndo() throws Exception {
        UndoManager undoManager = new UndoManager();
        undoManager.recordChange(new DeleteCommand(Index.fromOneBased(1)), List.of(ALICE), List.of(), "Deleted Alice");
        Model model = createModel(List.of());
        PendingRequest request = undoManager.executeUndo().getPendingRequest().orElseThrow();

        request.respond("y", model);
        undoManager.commitUndo();
        assertEquals(UndoManager.MESSAGE_NOTHING_TO_UNDO,
                request.respond("y", model).getFeedbackToUser());
        assertEquals(List.of(ALICE), model.getContactFile().getPersonList());
        assertTrue(undoManager.getHistory().contains("deletion (undone): Deleted Alice"));
    }

    @Test
    public void executeUndo_missingRestoredPerson_reportsConflict() throws Exception {
        UndoManager undoManager = new UndoManager();
        List<Person> before = List.of(ALICE, BENSON);
        List<Person> after = List.of(BENSON, ALICE);
        undoManager.recordChange(new EditCommand(Index.fromOneBased(1), new EditCommand.EditPersonDescriptor()),
                before, after, "Reordered contacts");
        Model model = createModel(after);
        PendingRequest request = undoManager.executeUndo().getPendingRequest().orElseThrow();

        assertEquals(UndoManager.MESSAGE_UNDO_CONFLICT,
                request.respond("y", model).getFeedbackToUser());
    }

    @Test
    public void executeUndo_replacementPersonMissing_reportsConflict() throws Exception {
        UndoManager undoManager = new UndoManager();
        undoManager.recordChange(new EditCommand(Index.fromOneBased(1), new EditCommand.EditPersonDescriptor()),
                List.of(ALICE), List.of(BENSON), "Replaced Alice");
        Model model = createModel(List.of(CARL));
        PendingRequest request = undoManager.executeUndo().getPendingRequest().orElseThrow();

        assertEquals(UndoManager.MESSAGE_UNDO_CONFLICT,
                request.respond("y", model).getFeedbackToUser());
    }

    @Test
    public void executeUndo_batchDeletion_restoresAllDeletedContacts() throws Exception {
        UndoManager undoManager = new UndoManager();
        List<Person> before = List.of(ALICE, BENSON, CARL);
        List<Person> after = List.of(CARL);
        undoManager.recordChange(new DeleteCommand(Index.fromOneBased(1)), before, after, "Deleted Alice and Benson");
        Model model = createModel(after);
        PendingRequest request = undoManager.executeUndo().getPendingRequest().orElseThrow();

        request.respond("y", model);
        assertEquals(List.of(ALICE, BENSON, CARL), model.getContactFile().getPersonList());
    }

    @Test
    public void executeUndo_restoredContactWithDuplicatePhone_reportsConflict() throws Exception {
        Person duplicatePhone = new PersonBuilder(ALICE).withName("Different Person")
                .withPhone(ALICE.getPhone().orElseThrow().value).withEmail("different@example.com").build();
        assertDeletionUndoConflict(duplicatePhone);
    }

    @Test
    public void executeUndo_restoredContactWithSameNameAndEmail_reportsConflict() throws Exception {
        Person samePerson = new PersonBuilder(ALICE).withPhone("11111111").build();
        assertDeletionUndoConflict(samePerson);
    }

    @Test
    public void executeUndo_restoredContactWithDuplicateEmail_reportsConflict() throws Exception {
        Person duplicateEmail = new PersonBuilder(ALICE).withName("Different Person")
                .withPhone("11111111").build();
        assertDeletionUndoConflict(duplicateEmail);
    }

    private void assertDeletionUndoConflict(Person conflictingPerson) throws Exception {
        UndoManager undoManager = new UndoManager();
        undoManager.recordChange(new DeleteCommand(Index.fromOneBased(1)), List.of(ALICE), List.of(), "Deleted Alice");
        Model model = createModel(List.of(conflictingPerson));
        PendingRequest request = undoManager.executeUndo().getPendingRequest().orElseThrow();

        assertEquals(UndoManager.MESSAGE_UNDO_CONFLICT,
                request.respond("y", model).getFeedbackToUser());
    }

    private Model createModel(List<Person> persons) {
        ContactFile contactFile = new ContactFile();
        contactFile.setPersons(persons);
        return new ModelManager(contactFile, new UserPrefs());
    }
}
