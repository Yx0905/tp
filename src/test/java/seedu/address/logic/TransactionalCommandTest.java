package seedu.address.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static seedu.address.testutil.TypicalPersons.ALICE;
import static seedu.address.testutil.TypicalPersons.BENSON;
import static seedu.address.testutil.TypicalPersons.DANIEL;
import static seedu.address.testutil.TypicalPersons.getTypicalContactFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javafx.collections.ObservableList;
import seedu.address.logic.commands.exceptions.CommandException;
import seedu.address.logic.parser.exceptions.ParseException;
import seedu.address.model.ContactFile;
import seedu.address.model.ModelManager;
import seedu.address.model.ReadOnlyContactFile;
import seedu.address.model.UserPrefs;
import seedu.address.model.person.Person;
import seedu.address.storage.JsonContactFileStorage;
import seedu.address.storage.JsonUserPrefsStorage;
import seedu.address.storage.StorageManager;
import seedu.address.testutil.PersonBuilder;

/**
 * Verifies contact write counts and rollback independently of confirmation workflows.
 */
public class TransactionalCommandTest {

    @TempDir
    public Path testFolder;

    private ModelManager model;
    private LogicManager logic;
    private CountingStorage storage;
    private Path activeFile;

    @BeforeEach
    public void setUp() throws Exception {
        model = new ModelManager(getTypicalContactFile(), new UserPrefs());
        activeFile = testFolder.resolve("contacts.json");
        new JsonContactFileStorage(activeFile).saveContactFile(model.getContactFile());
        storage = new CountingStorage(activeFile);
        logic = new LogicManager(model, new StorageManager(storage,
                new JsonUserPrefsStorage(testFolder.resolve("prefs.json"))));
    }

    @Test
    public void execute_queriesAndNoOpEdit_doNotSave() throws Exception {
        byte[] original = Files.readAllBytes(activeFile);
        for (String command : List.of("list", "find Alice", "view 1", "help")) {
            logic.execute(command);
        }
        assertThrows(CommandException.class, () -> logic.execute("edit 1 n/Alice Pauline"));
        assertEquals(0, storage.saveCount);
        assertArrayEquals(original, Files.readAllBytes(activeFile));
    }

    @Test
    public void execute_viewAmbiguityAndIndexRetry_neverSaves() throws Exception {
        byte[] original = Files.readAllBytes(activeFile);
        ObservableList<Person> binding = model.getFilteredPersonList();
        logic.execute("find Alice");
        assertEquals("2 contacts found:", logic.execute("view mEiEr").getFeedbackToUser().lines().findFirst().get());
        assertEquals(List.of(BENSON, DANIEL), model.getFilteredPersonList());
        assertThrows(CommandException.class, () -> logic.execute("view 3"));
        assertEquals(List.of(BENSON, DANIEL), model.getFilteredPersonList());
        assertEquals(Messages.format(DANIEL), logic.execute("view 2").getFeedbackToUser());
        logic.execute("list");
        assertEquals(getTypicalContactFile().getPersonList(), model.getFilteredPersonList());
        assertEquals(getTypicalContactFile(), model.getContactFile());
        assertSame(binding, model.getFilteredPersonList());
        assertEquals(0, storage.saveCount);
        assertArrayEquals(original, Files.readAllBytes(activeFile));
    }

    @Test
    public void execute_uniqueNameOutsideFilter_showsDetailsWithoutSaving() throws Exception {
        byte[] original = Files.readAllBytes(activeFile);
        logic.execute("find Meier");
        assertEquals(Messages.format(ALICE), logic.execute("view pAuLiNe").getFeedbackToUser());
        assertEquals(List.of(ALICE), model.getFilteredPersonList());
        assertEquals(getTypicalContactFile(), model.getContactFile());
        assertEquals(0, storage.saveCount);
        assertArrayEquals(original, Files.readAllBytes(activeFile));
    }

    @Test
    public void execute_invalidViews_restorePreviousDynamicFilterWithoutSaving() throws Exception {
        byte[] original = Files.readAllBytes(activeFile);
        ObservableList<Person> binding = model.getFilteredPersonList();
        logic.execute("find Alice");
        for (String command : List.of("view Zed", "view 2")) {
            assertThrows(CommandException.class, () -> logic.execute(command));
            assertEquals(List.of(ALICE), model.getFilteredPersonList());
        }
        for (String command : List.of("view", "view 0", "view 1 Alice", "view n/Alice")) {
            assertThrows(ParseException.class, () -> logic.execute(command));
            assertEquals(List.of(ALICE), model.getFilteredPersonList());
        }
        assertSame(binding, model.getFilteredPersonList());
        assertEquals(getTypicalContactFile(), model.getContactFile());
        assertEquals(0, storage.saveCount);
        assertArrayEquals(original, Files.readAllBytes(activeFile));
        Person anotherAlice = new PersonBuilder().withName("Alice New").build();
        model.setPerson(BENSON, anotherAlice);
        assertEquals(List.of(ALICE, anotherAlice), model.getFilteredPersonList());
    }

    @Test
    public void execute_failedAdd_restoresDataBindingAndDynamicFilter() throws Exception {
        logic.execute("find Alice");
        ContactFile original = new ContactFile(model.getContactFile());
        byte[] bytes = Files.readAllBytes(activeFile);
        ObservableList<Person> binding = model.getFilteredPersonList();
        storage.shouldFail = true;
        assertThrows(CommandException.class, () -> logic.execute("add n/New Contact"));
        assertEquals(original, model.getContactFile());
        assertEquals(List.of(ALICE), model.getFilteredPersonList());
        assertSame(binding, model.getFilteredPersonList());
        assertArrayEquals(bytes, Files.readAllBytes(activeFile));
        Person anotherAlice = new PersonBuilder().withName("Alice New").build();
        model.setPerson(BENSON, anotherAlice);
        assertEquals(List.of(ALICE, anotherAlice), model.getFilteredPersonList());
    }

    @Test
    public void execute_failedEdit_restoresOriginalContactAndFile() throws Exception {
        logic.execute("find Alice");
        byte[] bytes = Files.readAllBytes(activeFile);
        ContactFile original = new ContactFile(model.getContactFile());
        storage.shouldFail = true;
        logic.execute("edit 1 p/76543210");
        assertThrows(CommandException.class, () -> logic.execute("y"));
        assertEquals(original, model.getContactFile());
        assertEquals(List.of(ALICE), model.getFilteredPersonList());
        assertArrayEquals(bytes, Files.readAllBytes(activeFile));
        assertEquals(1, storage.saveCount);
    }

    @Test
    public void execute_failedUndoSaves_keepUndoAvailableAndHistoryUnmarked() throws Exception {
        logic.execute("delete 1");
        logic.execute("y");
        logic.execute("edit 1 p/76543210");
        logic.execute("y");
        assertUndoSurvivesFailedSave("edit");

        logic.execute("delete 1");
        logic.execute("y");
        assertUndoSurvivesFailedSave("deletion");
    }

    /**
     * Confirms the latest undo while saving fails, then retries it successfully.
     */
    private void assertUndoSurvivesFailedSave(String action) throws Exception {
        ContactFile beforeUndo = new ContactFile(model.getContactFile());
        byte[] bytes = Files.readAllBytes(activeFile);
        storage.shouldFail = true;
        logic.execute("undo");
        assertThrows(CommandException.class, () -> logic.execute("y"));
        assertEquals(beforeUndo, model.getContactFile());
        assertArrayEquals(bytes, Files.readAllBytes(activeFile));
        String history = logic.execute("history").getFeedbackToUser();
        assertEquals(false, history.contains(action + " (undone)"));

        storage.shouldFail = false;
        logic.execute("undo");
        logic.execute("y");
        assertEquals(true, logic.execute("history").getFeedbackToUser().contains(action + " (undone)"));
        assertEquals(UndoManager.MESSAGE_NOTHING_TO_UNDO, logic.execute("undo").getFeedbackToUser());
    }

    /**
     * Counts save attempts and simulates a storage failure before writing.
     */
    private static final class CountingStorage extends JsonContactFileStorage {
        private int saveCount;
        private boolean shouldFail;

        private CountingStorage(Path path) {
            super(path);
        }

        @Override
        public void saveContactFile(ReadOnlyContactFile contactFile) throws IOException {
            saveCount++;
            if (shouldFail) {
                throw new IOException("Simulated save failure");
            }
            super.saveContactFile(contactFile);
        }
    }
}
