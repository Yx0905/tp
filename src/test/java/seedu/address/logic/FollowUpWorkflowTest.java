package seedu.address.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static seedu.address.testutil.TypicalPersons.ALICE;
import static seedu.address.testutil.TypicalPersons.BENSON;
import static seedu.address.testutil.TypicalPersons.getTypicalContactFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javafx.collections.ObservableList;
import seedu.address.commons.core.index.Index;
import seedu.address.logic.commands.CommandResult;
import seedu.address.logic.commands.FollowUpCommand;
import seedu.address.logic.commands.exceptions.CommandException;
import seedu.address.logic.parser.exceptions.ParseException;
import seedu.address.model.ContactFile;
import seedu.address.model.ModelManager;
import seedu.address.model.ReadOnlyContactFile;
import seedu.address.model.UserPrefs;
import seedu.address.model.person.FollowUp;
import seedu.address.model.person.Person;
import seedu.address.storage.JsonContactFileStorage;
import seedu.address.storage.JsonUserPrefsStorage;
import seedu.address.storage.StorageManager;
import seedu.address.testutil.PersonBuilder;

class FollowUpWorkflowTest {
    @TempDir
    public Path testFolder;

    private ModelManager model;
    private LogicManager logic;
    private CountingStorage storage;
    private Path file;

    @BeforeEach
    void setUp() throws Exception {
        model = new ModelManager(getTypicalContactFile(), new UserPrefs());
        file = testFolder.resolve("contacts.json");
        new JsonContactFileStorage(file).saveContactFile(model.getContactFile());
        storage = new CountingStorage(file);
        logic = new LogicManager(model, new StorageManager(storage,
                new JsonUserPrefsStorage(testFolder.resolve("prefs.json"))));
    }

    @Test
    void execute_dateFilters_selectHiddenContactsWithoutSaving() throws Exception {
        LocalDate today = LocalDate.now();
        Person alice = new PersonBuilder(ALICE).withFollowUps(
                new FollowUp(7, "Past action", FollowUp.Status.PENDING, today.minusDays(1)),
                new FollowUp(8, "Undated action", FollowUp.Status.PENDING)).build();
        Person benson = new PersonBuilder(BENSON).withFollowUps(
                new FollowUp(7, "Today action", FollowUp.Status.PENDING, today),
                new FollowUp(9, "Future action", FollowUp.Status.PENDING, LocalDate.MAX),
                new FollowUp(10, "Completed action", FollowUp.Status.COMPLETED, today)).build();
        model.setPerson(ALICE, alice);
        model.setPerson(BENSON, benson);
        model.updateFilteredPersonList(person -> person.equals(alice));
        storage.saveContactFile(model.getContactFile());
        storage.saveCount = 0;
        storage.shouldFail = true;
        byte[] original = Files.readAllBytes(file);
        ContactFile before = new ContactFile(model.getContactFile());
        Map<String, Person> contacts = Map.of("overdue", alice, "today", benson, "upcoming", benson);
        Map<String, Integer> ids = Map.of("overdue", 7, "today", 7, "upcoming", 9);

        for (String selector : List.of("overdue", "today", "upcoming")) {
            CommandResult listed = logic.execute("followups " + selector);
            assertTrue(listed.getFeedbackToUser().contains(" (1):"));
            CommandResult invalid = logic.execute("99");
            assertTrue(invalid.getFeedbackToUser().contains(listed.getFeedbackToUser()));
            CommandResult selected = logic.execute("1");
            assertEquals(contacts.get(selector), selected.getPersonForDetails().orElseThrow());
            assertEquals(ids.get(selector).intValue(), selected.getFollowUpForDetails().orElseThrow().getId());
            logic.execute("followups " + selector);
            logic.execute("cancel");
        }
        assertThrows(ParseException.class, () -> logic.execute("followups today extra"));
        assertEquals(before, model.getContactFile());
        assertEquals(List.of(alice), model.getFilteredPersonList());
        assertArrayEquals(original, Files.readAllBytes(file));
        assertEquals(0, storage.saveCount);
    }

    @Test
    void execute_changedDateFilterTarget_rejectsStaleSelectionWithoutRollback() throws Exception {
        Person alice = new PersonBuilder(ALICE).withFollowUps(
                new FollowUp(7, "Call", FollowUp.Status.PENDING, LocalDate.MAX)).build();
        model.setPerson(ALICE, alice);
        logic.execute("followups upcoming");
        Person changed = new PersonBuilder(alice).withFollowUps(
                new FollowUp(7, "Call", FollowUp.Status.PENDING, LocalDate.of(2000, 1, 1))).build();
        model.setPerson(alice, changed);
        storage.shouldFail = true;

        assertThrows(CommandException.class, () -> logic.execute("1"));
        assertEquals(changed, model.getContactFile().getPersonList().getFirst());
        assertThrows(ParseException.class, () -> logic.execute("1"));
        assertEquals(0, storage.saveCount);
    }

    @Test
    void execute_historyViews_preserveFilesFiltersAndSelectionOrder() throws Exception {
        Person alice = new PersonBuilder(ALICE).withFollowUps(
                new FollowUp(9, "Alice done undated", FollowUp.Status.COMPLETED),
                new FollowUp(4, "Alice done first", FollowUp.Status.COMPLETED, LocalDate.of(2000, 1, 1)),
                new FollowUp(2, "Alice done second", FollowUp.Status.COMPLETED, LocalDate.of(2000, 1, 1)),
                new FollowUp(8, "Alice pending", FollowUp.Status.PENDING)).build();
        Person benson = new PersonBuilder(BENSON).withFollowUps(
                new FollowUp(9, "Benson done", FollowUp.Status.COMPLETED),
                new FollowUp(8, "Benson pending", FollowUp.Status.PENDING)).build();
        model.setPerson(ALICE, alice);
        model.setPerson(BENSON, benson);
        model.updateFilteredPersonList(person -> person.equals(alice));
        storage.saveContactFile(model.getContactFile());
        storage.saveCount = 0;
        storage.shouldFail = true;
        byte[] original = Files.readAllBytes(file);
        ContactFile before = new ContactFile(model.getContactFile());

        for (String selector : List.of("completed", "undated")) {
            int count = selector.equals("completed") ? 4 : 2;
            CommandResult listed = logic.execute("followups " + selector);
            assertTrue(listed.getFeedbackToUser().contains(" (" + count + "):"));
            assertTrue(logic.execute("99").getFeedbackToUser().contains(listed.getFeedbackToUser()));
            CommandResult selected = logic.execute(Integer.toString(count));
            assertEquals(benson, selected.getPersonForDetails().orElseThrow());
            assertEquals(selector.equals("completed") ? 9 : 8,
                    selected.getFollowUpForDetails().orElseThrow().getId());
            logic.execute("followups " + selector);
            logic.execute("cancel");
            assertThrows(ParseException.class, () -> logic.execute("1"));
        }
        logic.execute("followups completed");
        assertTrue(logic.execute("followups undated").getFeedbackToUser().startsWith("Undated pending follow-ups"));
        logic.execute("cancel");
        assertEquals(before, model.getContactFile());
        assertEquals(List.of(alice), model.getFilteredPersonList());
        assertArrayEquals(original, Files.readAllBytes(file));

        ModelManager reloaded = new ModelManager(storage.readContactFile().orElseThrow(), new UserPrefs());
        LogicManager restarted = new LogicManager(reloaded, new StorageManager(storage,
                new JsonUserPrefsStorage(testFolder.resolve("restart-prefs.json"))));
        assertTrue(restarted.execute("followups completed").getFeedbackToUser().contains(" (4):"));
        assertEquals(benson, restarted.execute("4").getPersonForDetails().orElseThrow());
        assertTrue(restarted.execute("followups undated").getFeedbackToUser().contains(" (2):"));
        assertEquals(0, storage.saveCount);
    }

    @Test
    void execute_changedCompletedStatus_rejectsStaleSelectionAndRefreshesViews() throws Exception {
        Person alice = new PersonBuilder(ALICE).withFollowUps(
                new FollowUp(1, "Call", FollowUp.Status.COMPLETED)).build();
        model.setPerson(ALICE, alice);
        logic.execute("followups completed");
        Person reopened = new PersonBuilder(alice).withFollowUps(
                new FollowUp(1, "Call", FollowUp.Status.PENDING)).build();
        model.setPerson(alice, reopened);
        storage.shouldFail = true;
        byte[] original = Files.readAllBytes(file);

        assertThrows(CommandException.class, () -> logic.execute("1"));
        assertEquals(reopened, model.getContactFile().getPersonList().getFirst());
        assertTrue(logic.execute("followups completed").getPendingRequest().isEmpty());
        assertTrue(logic.execute("followups undated").getPendingRequest().isPresent());
        assertEquals(FollowUp.Status.PENDING, logic.execute("1").getFollowUpForDetails().orElseThrow().getStatus());
        assertArrayEquals(original, Files.readAllBytes(file));
        assertEquals(0, storage.saveCount);
    }

    @Test
    void execute_emptyDateFilter_doesNotAskForSelectionOrSave() throws Exception {
        byte[] original = Files.readAllBytes(file);
        storage.shouldFail = true;
        for (String selector : List.of("overdue", "today", "upcoming", "completed", "undated")) {
            assertTrue(logic.execute("followups " + selector).getPendingRequest().isEmpty());
            assertThrows(ParseException.class, () -> logic.execute("1"));
        }
        assertArrayEquals(original, Files.readAllBytes(file));
        assertEquals(0, storage.saveCount);
    }

    @Test
    void execute_starEditAndUnstar_preservesHistoryAcrossReloadAndStartup() throws Exception {
        Person original = new PersonBuilder(ALICE).withFollowUps(
                new FollowUp(7, "Send portfolio", FollowUp.Status.PENDING),
                new FollowUp(2, "Call", FollowUp.Status.COMPLETED)).build();
        model.setPerson(ALICE, original);
        logic.execute("find Alice");
        logic.execute("star 1");
        assertEquals(original.withStarred(true), model.getFilteredPersonList().getFirst());
        assertEquals(original.withStarred(true), storage.readContactFile().orElseThrow().getPersonList().getFirst());
        logic.execute("edit 1 p/76543210");
        logic.execute("y");
        Person edited = new PersonBuilder(original).withStarred(true).withPhone("76543210").build();
        assertEquals(edited, model.getFilteredPersonList().getFirst());
        assertEquals(edited, storage.loadForStartup().contacts().getFirst());
        logic.execute("unstar 1");
        assertEquals(edited.withStarred(false), storage.readContactFile().orElseThrow().getPersonList().getFirst());
        assertEquals(original.getFollowUps(), model.getFilteredPersonList().getFirst().getFollowUps());
        assertEquals(3, storage.saveCount);
    }

    @Test
    void execute_followUpOnStarredContact_retainsStarAndSkipsNoOpSaves() throws Exception {
        logic.execute("star 1");
        logic.execute("followup 1 a/Send portfolio");
        Person updated = model.getFilteredPersonList().getFirst();
        assertTrue(updated.isStarred());
        assertEquals(List.of(new FollowUp(1, "Send portfolio", FollowUp.Status.PENDING)), updated.getFollowUps());
        assertEquals(updated, storage.loadForStartup().contacts().getFirst());
        byte[] saved = Files.readAllBytes(file);
        logic.execute("star 1");
        logic.execute("view 1");
        assertEquals(2, storage.saveCount);
        assertArrayEquals(saved, Files.readAllBytes(file));
        logic.execute("unstar 1");
        logic.execute("unstar 1");
        assertEquals(3, storage.saveCount);
    }

    @Test
    void execute_failedStarAndUnstar_restoresHistoryFilterAndFile() throws Exception {
        Person original = new PersonBuilder(ALICE).withFollowUps(
                new FollowUp(7, "Send portfolio", FollowUp.Status.PENDING)).build();
        model.setPerson(ALICE, original);
        logic.execute("find Alice");
        storage.saveContactFile(model.getContactFile());
        for (boolean starred : List.of(false, true)) {
            storage.shouldFail = false;
            if (starred) {
                logic.execute("star 1");
            }
            byte[] saved = Files.readAllBytes(file);
            ContactFile before = new ContactFile(model.getContactFile());
            storage.shouldFail = true;
            assertThrows(CommandException.class, () -> logic.execute(starred ? "unstar 1" : "star 1"));
            assertEquals(before, model.getContactFile());
            assertEquals(List.of(original.withStarred(starred)), model.getFilteredPersonList());
            assertArrayEquals(saved, Files.readAllBytes(file));
        }
    }

    @Test
    void execute_starredFilter_followUpEditUnstarAndRollbackRetainMetadata() throws Exception {
        logic.execute("star 1");
        logic.execute("find Benson");
        logic.execute("starred");
        assertEquals(List.of(ALICE.withStarred(true)), model.getFilteredPersonList());
        logic.execute("followup 1 a/Call Alice");
        Person withAction = ALICE.withStarred(true).appendFollowUp(
                new FollowUp(1, "Call Alice", FollowUp.Status.PENDING));
        assertEquals(List.of(withAction), model.getFilteredPersonList());
        logic.execute("edit 1 p/76543210");
        logic.execute("y");
        Person edited = new PersonBuilder(withAction).withPhone("76543210").build();
        logic.execute("starred");
        byte[] saved = Files.readAllBytes(file);
        assertEquals(3, storage.saveCount);
        storage.shouldFail = true;
        assertThrows(CommandException.class, () -> logic.execute("unstar 1"));
        assertEquals(List.of(edited), model.getFilteredPersonList());
        assertArrayEquals(saved, Files.readAllBytes(file));
        storage.shouldFail = false;
        logic.execute("unstar 1");
        assertTrue(model.getFilteredPersonList().isEmpty());
        assertEquals(edited.withStarred(false), storage.readContactFile().orElseThrow().getPersonList().getFirst());
        logic.execute("list");
        assertEquals(edited.withStarred(false), model.getFilteredPersonList().getFirst());
    }

    @Test
    void execute_newAction_savesOnceAndDisplaysUpdatedOrderedList() throws Exception {
        List<Person> original = List.copyOf(model.getContactFile().getPersonList());
        CommandResult result = logic.execute("followup 1 a/  Send portfolio  ");
        Person updated = model.getFilteredPersonList().getFirst();
        assertEquals(List.of(new FollowUp(1, "Send portfolio", FollowUp.Status.PENDING)), updated.getFollowUps());
        assertTrue(result.getFeedbackToUser().contains("#1 [PENDING] Send portfolio"));
        assertFalse(result.getPendingRequest().isPresent());
        assertEquals(1, storage.saveCount);
        assertEquals(original.subList(1, original.size()), model.getContactFile().getPersonList().subList(1,
                original.size()));
        assertEquals(model.getContactFile().getPersonList(), storage.readContactFile().orElseThrow().getPersonList());
        assertTrue(logic.execute("view 1").getFeedbackToUser().contains("#1 [PENDING] Send portfolio"));
        assertEquals(1, storage.saveCount);
    }

    @Test
    void execute_datedAction_acceptsPastDateAndPersistsIt() throws Exception {
        CommandResult result = logic.execute("followup 1 a/Call d/29 Feb 2000");
        assertTrue(result.getFeedbackToUser().contains("#1 [PENDING] Call | Due: 2000-02-29 [OVERDUE]"));
        assertTrue(logic.execute("view 1").getFeedbackToUser().contains("Due: 2000-02-29 [OVERDUE]"));
        FollowUp action = model.getFilteredPersonList().getFirst().getFollowUps().getFirst();
        assertEquals(LocalDate.of(2000, 2, 29), action.getDueDate().orElseThrow());
        assertTrue(action.isOverdue());
        assertEquals(model.getContactFile().getPersonList(), storage.loadForStartup().contacts());
        assertEquals(1, storage.saveCount);
    }

    @Test
    void execute_followups_includesHiddenContactsWithoutSavingAndRefreshesAfterCreation() throws Exception {
        assertEquals("No pending follow-ups remain.", logic.execute("followups").getFeedbackToUser());
        assertEquals(0, storage.saveCount);
        logic.execute("followup 1 a/Call d/29 Feb 2000");
        logic.execute("find Benson");
        ContactFile before = new ContactFile(model.getContactFile());
        byte[] bytes = Files.readAllBytes(file);
        storage.shouldFail = true;
        CommandResult result = logic.execute("followups");
        assertTrue(result.getFeedbackToUser().contains(ALICE.getName() + " | #1 [PENDING] Call"));
        assertTrue(result.getFeedbackToUser().contains("Due: 2000-02-29 [OVERDUE]"));
        assertEquals(before, model.getContactFile());
        assertEquals(List.of(BENSON), model.getFilteredPersonList());
        assertEquals(1, storage.saveCount);
        assertArrayEquals(bytes, Files.readAllBytes(file));
        storage.shouldFail = false;
        logic.execute("cancel");
        logic.execute("followup 1 a/Send portfolio");
        String refreshed = logic.execute("followups").getFeedbackToUser();
        assertTrue(refreshed.startsWith("Pending follow-ups (2):"));
        assertTrue(refreshed.contains(BENSON.getName() + " | #1 [PENDING] Send portfolio | No due date"));
        assertEquals(2, storage.saveCount);
    }

    @Test
    void execute_datedDuplicate_preservesRequestedDateAndExistingAction() throws Exception {
        logic.execute("followup 1 a/Call d/2026-10-25");
        CommandResult prompt = logic.execute("followup 1 a/Call d/01/11/2026");
        assertTrue(prompt.getPendingRequest().isPresent());
        assertTrue(prompt.getFeedbackToUser().contains("#1 [PENDING] Call | Due: 2026-10-25"));
        assertTrue(prompt.getFeedbackToUser().contains("Requested due date: 2026-11-01"));
        assertEquals(1, storage.saveCount);
        assertTrue(logic.execute("y").getFeedbackToUser().contains("#2 [PENDING] Call | Due: 2026-11-01"));
        assertEquals(List.of(new FollowUp(1, "Call", FollowUp.Status.PENDING, LocalDate.of(2026, 10, 25)),
                new FollowUp(2, "Call", FollowUp.Status.PENDING, LocalDate.of(2026, 11, 1))),
                model.getFilteredPersonList().getFirst().getFollowUps());
        assertEquals(model.getContactFile().getPersonList(), storage.loadForStartup().contacts());
        assertEquals(2, storage.saveCount);
    }

    @Test
    void execute_createReviewSelect_preservesFilterAndFileAcrossInvalidReplies() throws Exception {
        logic.execute("followup 1 a/Portfolio d/31 Dec 9999");
        logic.execute("followup 2 a/Call d/29 Feb 2000");
        logic.execute("find Alice");
        ContactFile before = new ContactFile(model.getContactFile());
        byte[] bytes = Files.readAllBytes(file);
        String prompt = logic.execute("followups").getFeedbackToUser();
        assertTrue(prompt.indexOf("Call") < prompt.indexOf("Portfolio"));
        storage.shouldFail = true;
        for (String reply : List.of("0", "-1", "3", "2147483648", "1.5")) {
            assertTrue(logic.execute(reply).getPendingRequest().isPresent());
            assertEquals(before, model.getContactFile());
            assertArrayEquals(bytes, Files.readAllBytes(file));
        }
        CommandResult selected = logic.execute(" 1 ");
        assertEquals(BENSON.getName(), selected.getPersonForDetails().orElseThrow().getName());
        assertEquals(seedu.address.logic.Messages.format(selected.getPersonForDetails().orElseThrow(),
                model.getFieldOrder()), selected.getFeedbackToUser());
        assertEquals("Call", selected.getFollowUpForDetails().orElseThrow().getDescription());
        assertTrue(selected.getPendingRequest().isEmpty());
        assertEquals(List.of(before.getPersonList().getFirst()), model.getFilteredPersonList());
        assertEquals(before, model.getContactFile());
        assertArrayEquals(bytes, Files.readAllBytes(file));
        assertEquals(2, storage.saveCount);
        assertThrows(ParseException.class, () -> logic.execute("1"));
    }

    @Test
    void execute_shortcutLeavingSelection_showsExpansionInFeedbackAndErrors() throws Exception {
        logic.execute("followup 1 a/Call");
        logic.execute("alias s star");
        logic.execute("followups");
        assertTrue(logic.execute("s 1").getFeedbackToUser().startsWith("Ran: star 1\n"));
        for (String reply : List.of("s 999", "s")) {
            logic.execute("followups");
            Exception e = assertThrows(Exception.class, () -> logic.execute(reply));
            assertTrue(e.getMessage().startsWith("Ran: star"), e.getMessage());
        }
    }

    @Test
    void execute_selectionCancellation_clearsRequestAndAllowsNewCommands() throws Exception {
        logic.execute("followup 1 a/Call");
        byte[] bytes = Files.readAllBytes(file);
        for (String reply : List.of("n", "N", "cancel", " CANCEL ")) {
            logic.execute("followups");
            assertTrue(logic.execute(reply).getPendingRequest().isEmpty());
            assertThrows(ParseException.class, () -> logic.execute("1"));
            assertArrayEquals(bytes, Files.readAllBytes(file));
        }
        logic.execute("followup 2 a/Portfolio d/01 Jan 2000");
        logic.execute("followups");
        CommandResult selected = logic.execute("1");
        assertEquals(BENSON.getName(), selected.getPersonForDetails().orElseThrow().getName());
        assertEquals(2, storage.saveCount);
    }

    @Test
    void execute_nonNumberDuringSelection_runsNormalCommandsAndClearsSelection() throws Exception {
        logic.execute("followup 1 a/Call");
        logic.execute("find Benson");
        logic.execute("followups");
        assertTrue(logic.execute(" list ").getPendingRequest().isEmpty());
        assertEquals(model.getContactFile().getPersonList(), model.getFilteredPersonList());
        assertThrows(ParseException.class, () -> logic.execute("1"));
        logic.execute("followups");
        assertEquals(ALICE.getName(), logic.execute("view 1").getPersonForDetails().orElseThrow().getName());
        logic.execute("followups");
        logic.execute("followup 2 a/Portfolio");
        assertEquals(1, model.getContactFile().getPersonList().get(1).getFollowUps().size());
        assertEquals(2, storage.saveCount);
        logic.execute("followups");
        assertTrue(logic.execute("followups").getPendingRequest().isPresent());
        assertTrue(logic.execute("help").isShowHelp());
        assertThrows(ParseException.class, () -> logic.execute("1"));
        logic.execute("followups");
        assertTrue(logic.execute("exit").isExit());
        assertEquals(2, storage.saveCount);
    }

    @Test
    void execute_invalidCommandDuringSelection_reportsNormalErrorAndEndsSelection() throws Exception {
        logic.execute("followup 1 a/Call");
        byte[] bytes = Files.readAllBytes(file);
        for (String input : List.of("unknown", "y", "", "1 extra", "followup 1 a/Call d/bad")) {
            logic.execute("followups");
            assertThrows(ParseException.class, () -> logic.execute(input));
            assertThrows(ParseException.class, () -> logic.execute("1"));
            assertArrayEquals(bytes, Files.readAllBytes(file));
        }
        assertEquals(1, storage.saveCount);
    }

    @Test
    void execute_commandDuringSelection_canStartConfirmationAndRollBackFailedSave() throws Exception {
        logic.execute("followup 1 a/Call");
        logic.execute("followups");
        assertTrue(logic.execute("followup 1 a/Call").getPendingRequest().isPresent());
        assertTrue(logic.execute("list").getPendingRequest().isPresent());
        assertEquals(1, storage.saveCount);
        logic.execute("y");
        assertEquals(2, model.getContactFile().getPersonList().getFirst().getFollowUps().size());
        logic.execute("followups");
        ContactFile before = new ContactFile(model.getContactFile());
        byte[] bytes = Files.readAllBytes(file);
        storage.shouldFail = true;
        assertThrows(CommandException.class, () -> logic.execute("followup 2 a/Fail save"));
        assertEquals(before, model.getContactFile());
        assertArrayEquals(bytes, Files.readAllBytes(file));
        assertThrows(ParseException.class, () -> logic.execute("1"));
    }

    @Test
    void execute_restart_requiresNewListThenSelectsPersistedAction() throws Exception {
        logic.execute("followup 1 a/Call d/29/02/2000");
        logic.execute("followups");
        ModelManager reloaded = new ModelManager(storage.readContactFile().orElseThrow(), new UserPrefs());
        LogicManager restarted = new LogicManager(reloaded, new StorageManager(storage,
                new JsonUserPrefsStorage(testFolder.resolve("prefs.json"))));
        assertThrows(ParseException.class, () -> restarted.execute("1"));
        restarted.execute("followups");
        CommandResult selected = restarted.execute("1");
        assertEquals(reloaded.getContactFile().getPersonList().getFirst(),
                selected.getPersonForDetails().orElseThrow());
        assertTrue(selected.getFeedbackToUser().contains("Due: 2000-02-29 [OVERDUE]"));
        assertEquals(1, storage.saveCount);
    }

    @Test
    void execute_staleSelection_doesNotResurrectDeletedContactOrSave() throws Exception {
        logic.execute("followup 1 a/Call");
        logic.execute("followups");
        model.deletePerson(model.getContactFile().getPersonList().getFirst());
        ContactFile changed = new ContactFile(model.getContactFile());
        byte[] bytes = Files.readAllBytes(file);
        assertThrows(CommandException.class, () -> logic.execute("1"));
        assertEquals(changed, model.getContactFile());
        assertEquals(1, storage.saveCount);
        assertArrayEquals(bytes, Files.readAllBytes(file));
        assertThrows(ParseException.class, () -> logic.execute("1"));
    }

    @Test
    void execute_readOnlyCancellation_preservesChangesMadeAfterPrompt() throws Exception {
        logic.execute("followup 1 a/Call");
        logic.execute("followups");
        model.setPerson(BENSON, new PersonBuilder(BENSON).withName("Benson changed").build());
        model.updateFilteredPersonList(person -> person.getName().fullName.equals("Benson changed"));
        ContactFile changed = new ContactFile(model.getContactFile());
        byte[] bytes = Files.readAllBytes(file);
        logic.execute("cancel");
        assertEquals(changed, model.getContactFile());
        assertEquals(1, model.getFilteredPersonList().size());
        assertEquals("Benson changed", model.getFilteredPersonList().getFirst().getName().fullName);
        assertEquals(1, storage.saveCount);
        assertArrayEquals(bytes, Files.readAllBytes(file));
    }

    @Test
    void execute_readOnlySelection_doesNotSaveUnrelatedChangesAndRetainsDisplayedOrder() throws Exception {
        logic.execute("followup 1 a/Call d/01 Jan 2000");
        logic.execute("followups");
        Person updated = BENSON.appendFollowUp(
                new FollowUp(1, "Earlier", FollowUp.Status.PENDING, LocalDate.of(1900, 1, 1)));
        model.setPerson(BENSON, updated);
        ContactFile changed = new ContactFile(model.getContactFile());
        byte[] bytes = Files.readAllBytes(file);
        assertTrue(logic.execute("99999999999999999999").getPendingRequest().isPresent());
        CommandResult selected = logic.execute("1");
        assertEquals(ALICE.getName(), selected.getPersonForDetails().orElseThrow().getName());
        assertEquals("Call", selected.getFollowUpForDetails().orElseThrow().getDescription());
        assertEquals(changed, model.getContactFile());
        assertEquals(1, storage.saveCount);
        assertArrayEquals(bytes, Files.readAllBytes(file));
        logic.execute("followups");
        assertEquals(updated, logic.execute("1").getPersonForDetails().orElseThrow());
        assertEquals(1, storage.saveCount);
    }

    @Test
    void execute_invalidDatedAction_doesNotChangeOrSave() throws Exception {
        ContactFile before = new ContactFile(model.getContactFile());
        byte[] bytes = Files.readAllBytes(file);
        for (String suffix : List.of("d/", "d/2026-02-29", "d/25/10/2026 d/26/10/2026")) {
            assertThrows(ParseException.class, () -> logic.execute("followup 1 a/Call " + suffix));
        }
        assertThrows(CommandException.class, () -> logic.execute("followup 99 a/Call d/25 Oct 2026"));
        assertEquals(before, model.getContactFile());
        assertEquals(0, storage.saveCount);
        assertArrayEquals(bytes, Files.readAllBytes(file));
    }

    @Test
    void command_dueDates_participateInValueSemantics() {
        FollowUpCommand command = new FollowUpCommand(Index.fromOneBased(1), "Call", LocalDate.of(2026, 10, 25));
        assertEquals(command, new FollowUpCommand(Index.fromOneBased(1), "Call", LocalDate.of(2026, 10, 25)));
        assertNotEquals(command, new FollowUpCommand(Index.fromOneBased(1), "Call"));
        assertNotEquals(command, new FollowUpCommand(Index.fromOneBased(1), "Call", LocalDate.of(2026, 11, 1)));
        assertTrue(command.toString().contains("dueDate=2026-10-25"));
    }

    @Test
    void formatFollowUp_dateAndStatus_displaysConsistentLabels() {
        assertEquals("#1 [PENDING] Call | No due date",
                Messages.formatFollowUp(new FollowUp(1, "Call", FollowUp.Status.PENDING)));
        assertEquals("#1 [PENDING] Call | Due: 2000-02-29 [OVERDUE]",
                Messages.formatFollowUp(new FollowUp(1, "Call", FollowUp.Status.PENDING, LocalDate.of(2000, 2, 29))));
        assertEquals("#1 [COMPLETED] Call | Due: 2000-02-29",
                Messages.formatFollowUp(new FollowUp(1, "Call", FollowUp.Status.COMPLETED, LocalDate.of(2000, 2, 29))));
        LocalDate today = LocalDate.now();
        assertEquals("#1 [PENDING] Call | Due: " + today,
                Messages.formatFollowUp(new FollowUp(1, "Call", FollowUp.Status.PENDING, today)));
        assertEquals("#1 [PENDING] Call | Due: 9999-12-31",
                Messages.formatFollowUp(new FollowUp(1, "Call", FollowUp.Status.PENDING, LocalDate.of(9999, 12, 31))));
    }

    @Test
    void execute_supportedDateFormats_displaysSameIsoDate() throws Exception {
        for (String date : List.of("2026-10-25", "25/10/2026", "25 Oct 2026")) {
            CommandResult result = logic.execute("followup 1 a/Call " + date + " d/" + date);
            assertTrue(result.getFeedbackToUser().contains("Call " + date + " | Due: 2026-10-25"));
        }
    }

    @Test
    void execute_datedDuplicateCancellationAndFailedSave_preservesOriginal() throws Exception {
        logic.execute("followup 1 a/Call d/25 Oct 2026");
        ContactFile before = new ContactFile(model.getContactFile());
        byte[] bytes = Files.readAllBytes(file);
        CommandResult prompt = logic.execute("followup 1 a/Call");
        assertTrue(prompt.getFeedbackToUser().contains("Requested due date: No due date"));
        logic.execute("n");
        assertEquals(before, model.getContactFile());
        assertArrayEquals(bytes, Files.readAllBytes(file));
        logic.execute("followup 1 a/Call d/01 Nov 2026");
        logic.execute("cancel");
        assertEquals(before, model.getContactFile());
        assertArrayEquals(bytes, Files.readAllBytes(file));
        assertEquals(1, storage.saveCount);
        storage.shouldFail = true;
        logic.execute("followup 1 a/Call d/01 Nov 2026");
        assertThrows(CommandException.class, () -> logic.execute("y"));
        assertEquals(before, model.getContactFile());
        assertArrayEquals(bytes, Files.readAllBytes(file));
    }

    @Test
    void execute_duplicatePendingAction_waitsThenAddsOnlyOnYes() throws Exception {
        logic.execute("followup 1 a/Send portfolio");
        ContactFile before = new ContactFile(model.getContactFile());
        byte[] bytes = Files.readAllBytes(file);
        CommandResult prompt = logic.execute("followup 1 a/  Send portfolio  ");
        assertTrue(prompt.getPendingRequest().isPresent());
        assertTrue(prompt.getFeedbackToUser().contains("#1 [PENDING] Send portfolio"));
        assertTrue(prompt.getFeedbackToUser().contains("Add another?"));
        for (String reply : List.of("maybe", "list", "followup 2 a/Other", "", "yes")) {
            assertTrue(logic.execute(reply).getPendingRequest().isPresent());
            assertEquals(before, model.getContactFile());
            assertArrayEquals(bytes, Files.readAllBytes(file));
            assertEquals(1, storage.saveCount);
        }
        logic.execute(" Y ");
        assertEquals(List.of(new FollowUp(1, "Send portfolio", FollowUp.Status.PENDING),
                new FollowUp(2, "Send portfolio", FollowUp.Status.PENDING)),
                model.getFilteredPersonList().getFirst().getFollowUps());
        assertEquals(2, storage.saveCount);
        assertThrows(ParseException.class, () -> logic.execute("y"));
    }

    @Test
    void execute_duplicateCancellation_preservesFileAndFilteredView() throws Exception {
        logic.execute("find Benson");
        logic.execute("followup 1 a/Call");
        ContactFile before = new ContactFile(model.getContactFile());
        byte[] bytes = Files.readAllBytes(file);
        List<Person> shown = List.copyOf(model.getFilteredPersonList());
        for (String reply : List.of("n", "cancel", " N ", "CANCEL")) {
            logic.execute("followup 1 a/Call");
            logic.execute(reply);
            assertEquals(before, model.getContactFile());
            assertEquals(shown, model.getFilteredPersonList());
            assertArrayEquals(bytes, Files.readAllBytes(file));
            assertEquals(1, storage.saveCount);
            assertThrows(ParseException.class, () -> logic.execute("y"));
        }
    }

    @Test
    void execute_filteredIndex_updatesDisplayedContactAndRetainsFilter() throws Exception {
        logic.execute("find Benson");
        logic.execute("followup 1 a/Call");
        assertEquals(1, model.getFilteredPersonList().size());
        Person updated = model.getFilteredPersonList().getFirst();
        assertEquals(BENSON.getName(), updated.getName());
        assertEquals(List.of(new FollowUp(1, "Call", FollowUp.Status.PENDING)), updated.getFollowUps());
        assertEquals(ALICE, model.getContactFile().getPersonList().getFirst());
    }

    @Test
    void execute_confirmAfterViewChanges_usesRetainedContact() throws Exception {
        logic.execute("followup 1 a/Call");
        logic.execute("followup 1 a/Call");
        model.updateFilteredPersonList(person -> person.equals(BENSON));
        logic.execute("y");
        assertEquals(2, model.getContactFile().getPersonList().getFirst().getFollowUps().size());
        assertTrue(model.getFilteredPersonList().getFirst().getFollowUps().isEmpty());
        assertEquals(2, storage.saveCount);
    }

    @Test
    void execute_completedDuplicateAndSparseIds_addsPendingWithGreatestIdPlusOne() throws Exception {
        Person history = new PersonBuilder(ALICE).withFollowUps(
                new FollowUp(9, "Send portfolio", FollowUp.Status.COMPLETED),
                new FollowUp(3, "Call", FollowUp.Status.PENDING)).build();
        model.setPerson(ALICE, history);
        CommandResult result = logic.execute("followup 1 a/Send portfolio");
        assertTrue(result.getPendingRequest().isEmpty());
        List<FollowUp> actions = model.getFilteredPersonList().getFirst().getFollowUps();
        assertEquals(List.of(9, 3, 10), actions.stream().map(FollowUp::getId).toList());
        assertEquals(FollowUp.Status.PENDING, actions.getLast().getStatus());
        assertEquals(actions, storage.readContactFile().orElseThrow().getPersonList().getFirst().getFollowUps());
    }

    @Test
    void execute_caseDifferentDescription_isNotAnExactDuplicate() throws Exception {
        logic.execute("followup 1 a/Call");
        assertTrue(logic.execute("followup 1 a/call").getPendingRequest().isEmpty());
        assertEquals(2, storage.saveCount);
    }

    @Test
    void execute_invalidRequest_doesNotChangeOrSave() throws Exception {
        ContactFile before = new ContactFile(model.getContactFile());
        byte[] bytes = Files.readAllBytes(file);
        assertThrows(CommandException.class, () -> logic.execute("followup 99 a/Call"));
        assertThrows(ParseException.class, () -> logic.execute("followup 1 a/ "));
        assertThrows(ParseException.class, () -> logic.execute("followup 1 a/Call confirm/yes"));
        assertEquals(before, model.getContactFile());
        assertEquals(0, storage.saveCount);
        assertArrayEquals(bytes, Files.readAllBytes(file));
        model.setContactFile(new ContactFile());
        assertThrows(CommandException.class, () -> logic.execute("followup 1 a/Call"));
    }

    @Test
    void execute_failedSave_restoresHistoryViewBindingAndFile() throws Exception {
        logic.execute("find Alice");
        logic.execute("followup 1 a/Call");
        for (boolean duplicate : List.of(false, true)) {
            ContactFile before = new ContactFile(model.getContactFile());
            byte[] bytes = Files.readAllBytes(file);
            ObservableList<Person> binding = model.getFilteredPersonList();
            storage.shouldFail = true;
            if (duplicate) {
                logic.execute("followup 1 a/Call");
                assertThrows(CommandException.class, () -> logic.execute("y"));
            } else {
                assertThrows(CommandException.class, () -> logic.execute("followup 1 a/Send portfolio"));
            }
            assertEquals(before, model.getContactFile());
            assertEquals(List.of(before.getPersonList().getFirst()), model.getFilteredPersonList());
            assertSame(binding, model.getFilteredPersonList());
            assertArrayEquals(bytes, Files.readAllBytes(file));
            assertThrows(ParseException.class, () -> logic.execute("y"));
            storage.shouldFail = false;
        }
        assertEquals(3, storage.saveCount);
        logic.execute("followup 1 a/Send portfolio");
        assertEquals(2, model.getFilteredPersonList().getFirst().getFollowUps().getLast().getId());
    }

    @Test
    void execute_editAndRestart_preservesActionsAndNextId() throws Exception {
        logic.execute("followup 1 a/Send portfolio");
        logic.execute("followup 1 a/Call");
        logic.execute("edit 1 n/Alice Changed p/87654321 c/ t/work");
        logic.execute("y");
        Person edited = model.getFilteredPersonList().getFirst();
        assertEquals(2, edited.getFollowUps().size());
        assertTrue(edited.getCompany().isEmpty());
        assertEquals("Alice Changed", edited.getName().fullName);
        assertEquals(edited, storage.loadForStartup().contacts().getFirst());
        ModelManager reloaded = new ModelManager(storage.readContactFile().orElseThrow(), new UserPrefs());
        LogicManager restarted = new LogicManager(reloaded, new StorageManager(storage,
                new JsonUserPrefsStorage(testFolder.resolve("prefs.json"))));
        restarted.execute("followup 1 a/Send notes");
        assertEquals(3, reloaded.getFilteredPersonList().getFirst().getFollowUps().getLast().getId());
    }

    @Test
    void execute_changedPendingContact_rejectsConfirmationAndClearsRequest() throws Exception {
        logic.execute("followup 1 a/Call");
        logic.execute("followup 1 a/Call");
        Person target = model.getFilteredPersonList().getFirst();
        model.setPerson(target, new PersonBuilder(target).withName("Changed").build());
        assertThrows(CommandException.class, () -> logic.execute("y"));
        assertThrows(ParseException.class, () -> logic.execute("y"));
        assertEquals(1, storage.saveCount);
    }

    @Test
    void execute_exhaustedIds_preservesHistoryWithoutOverflow() throws Exception {
        Person full = new PersonBuilder(ALICE).withFollowUps(
                new FollowUp(Integer.MAX_VALUE, "Call", FollowUp.Status.COMPLETED)).build();
        model.setPerson(ALICE, full);
        assertThrows(CommandException.class, () -> logic.execute("followup 1 a/Call"));
        assertEquals(full, model.getFilteredPersonList().getFirst());
        assertEquals(0, storage.saveCount);
    }

    @Test
    void command_valueSemantics_preservesTrimmedRequest() {
        FollowUpCommand command = new FollowUpCommand(Index.fromOneBased(1), "Call");
        assertEquals(command, command);
        assertEquals(command, new FollowUpCommand(Index.fromOneBased(1), " Call "));
        assertNotEquals(command, new FollowUpCommand(Index.fromOneBased(2), "Call"));
        assertNotEquals(command, new FollowUpCommand(Index.fromOneBased(1), "Other"));
        assertNotEquals(command, null);
        assertNotEquals(command, "Call");
        assertTrue(command.toString().contains("description=Call"));
        assertThrows(NullPointerException.class, () -> command.execute(null));
        assertThrows(NullPointerException.class, () -> new FollowUpCommand(null, "Call"));
        assertThrows(IllegalArgumentException.class, () -> new FollowUpCommand(Index.fromOneBased(1), " "));
    }

    @Test
    void execute_dateBeforeDescription_createsAndConfirmsDuplicateWithRequestedDate() throws Exception {
        logic.execute("followup 1 d/2026-10-30 a/Call back");
        assertEquals(LocalDate.of(2026, 10, 30),
                model.getFilteredPersonList().getFirst().getFollowUps().getFirst().getDueDate().orElseThrow());
        assertTrue(logic.execute("followup 1 d/1 Nov 2026 a/Call back").getPendingRequest().isPresent());
        logic.execute("y");
        assertEquals(List.of(new FollowUp(1, "Call back", FollowUp.Status.PENDING, LocalDate.of(2026, 10, 30)),
                new FollowUp(2, "Call back", FollowUp.Status.PENDING, LocalDate.of(2026, 11, 1))),
                model.getFilteredPersonList().getFirst().getFollowUps());
        assertEquals(model.getContactFile().getPersonList(), storage.loadForStartup().contacts());
        assertEquals(2, storage.saveCount);
    }

    private static final class CountingStorage extends JsonContactFileStorage {
        private int saveCount;
        private boolean shouldFail;

        private CountingStorage(Path path) {
            super(path);
        }

        @Override
        public void saveContactFile(ReadOnlyContactFile contacts) throws IOException {
            saveCount++;
            if (shouldFail) {
                throw new IOException("Simulated follow-up save failure");
            }
            super.saveContactFile(contacts);
        }
    }

    @Test
    void execute_editFollowUpDescription_preservesIdStatusAndDate()
            throws Exception {
        logic.execute(
                "followup 1 a/Call client d/2026-11-05");

        logic.execute(
                "editfollowup 1 i/1 a/Email client");

        FollowUp updated = model.getFilteredPersonList()
                .getFirst()
                .getFollowUps()
                .getFirst();

        assertEquals(1, updated.getId());
        assertEquals("Email client", updated.getDescription());
        assertEquals(FollowUp.Status.PENDING, updated.getStatus());
        assertEquals(
                LocalDate.of(2026, 11, 5),
                updated.getDueDate().orElseThrow());
        assertEquals(2, storage.saveCount);
    }

    @Test
    void execute_editFollowUpDate_preservesIdStatusAndDescription()
            throws Exception {
        logic.execute("followup 1 a/Call client");

        logic.execute(
                "editfollowup 1 i/1 d/2026-12-01");

        FollowUp updated = model.getFilteredPersonList()
                .getFirst()
                .getFollowUps()
                .getFirst();

        assertEquals(1, updated.getId());
        assertEquals("Call client", updated.getDescription());
        assertEquals(FollowUp.Status.PENDING, updated.getStatus());
        assertEquals(
                LocalDate.of(2026, 12, 1),
                updated.getDueDate().orElseThrow());
    }

    @Test
    void execute_editCompletedFollowUp_preservesCompletedStatus()
            throws Exception {
        Person original = new PersonBuilder(ALICE).withFollowUps(
                new FollowUp(
                        7,
                        "Old description",
                        FollowUp.Status.COMPLETED))
                .build();

        model.setPerson(ALICE, original);
        storage.saveContactFile(model.getContactFile());

        logic.execute(
                "editfollowup 1 i/7 a/New description");

        FollowUp updated = model.getFilteredPersonList()
                .getFirst()
                .getFollowUps()
                .getFirst();

        assertEquals(7, updated.getId());
        assertEquals("New description", updated.getDescription());
        assertEquals(FollowUp.Status.COMPLETED, updated.getStatus());
    }

    @Test
    void execute_editFollowUpDuplicate_rejectsChange()
            throws Exception {
        logic.execute("followup 1 a/Call client");
        logic.execute("followup 1 a/Send portfolio");

        List<FollowUp> before = List.copyOf(
                model.getFilteredPersonList()
                        .getFirst()
                        .getFollowUps());

        assertThrows(CommandException.class, () -> logic.execute(
                "editfollowup 1 i/2 a/Call client"));
        assertEquals(
                before,
                model.getFilteredPersonList()
                        .getFirst()
                        .getFollowUps());
        assertEquals(2, storage.saveCount);
    }

    @Test
    void execute_editFollowUpMissingId_rejectsCommand()
            throws Exception {
        assertThrows(CommandException.class, () -> logic.execute(
                "editfollowup 1 i/99 a/Updated description"));
        assertEquals(0, storage.saveCount);
    }

    @Test
    void execute_completeAndReopenFollowUp_changesOnlyStatusAndPersists() throws Exception {
        logic.execute("followup 1 a/Call client d/2020-01-01");
        logic.execute("followup 1 a/Send portfolio");

        CommandResult completed = logic.execute("completefollowup 1 i/1");
        FollowUp done = model.getFilteredPersonList().getFirst().getFollowUps().getFirst();
        assertEquals(new FollowUp(1, "Call client", FollowUp.Status.COMPLETED, LocalDate.of(2020, 1, 1)), done);
        assertFalse(done.isOverdue());
        assertTrue(completed.getFeedbackToUser().contains("#1 [COMPLETED] Call client"));
        assertEquals(List.of(2), model.getPendingFollowUps().stream().map(entry -> entry.followUp().getId()).toList());
        assertEquals(done, storage.loadForStartup().contacts().getFirst().getFollowUps().getFirst());

        logic.execute("reopenfollowup 1 i/1");
        List<FollowUp> reopened = model.getFilteredPersonList().getFirst().getFollowUps();
        assertEquals(new FollowUp(1, "Call client", FollowUp.Status.PENDING, LocalDate.of(2020, 1, 1)),
                reopened.getFirst());
        assertTrue(reopened.getFirst().isOverdue());
        assertEquals(List.of(1, 2), reopened.stream().map(FollowUp::getId).toList());
        assertEquals(reopened, storage.loadForStartup().contacts().getFirst().getFollowUps());
        assertEquals(4, storage.saveCount);
    }

    @Test
    void execute_statusChanges_moveFollowUpsBetweenFollowUpViews() throws Exception {
        logic.execute("followup 1 a/Call client d/2020-01-01");
        logic.execute("followup 1 a/Send portfolio d/2999-01-01");
        logic.execute("followup 1 a/Check in");
        logic.execute("completefollowup 1 i/1");
        logic.execute("completefollowup 1 i/3");

        String completed = logic.execute("followups completed").getFeedbackToUser();
        assertTrue(completed.contains("#1 [COMPLETED] Call client"));
        assertTrue(completed.contains("#3 [COMPLETED] Check in"));
        assertFalse(completed.contains("#2"));
        assertEquals("No overdue follow-ups remain.", logic.execute("followups overdue").getFeedbackToUser());
        assertEquals("No undated pending follow-ups remain.",
                logic.execute("followups undated").getFeedbackToUser());
        String pending = logic.execute("followups").getFeedbackToUser();
        assertTrue(pending.contains("#2 [PENDING] Send portfolio"));
        assertFalse(pending.contains("#1") || pending.contains("#3"));

        logic.execute("reopenfollowup 1 i/1");
        String overdue = logic.execute("followups overdue").getFeedbackToUser();
        assertTrue(overdue.contains("#1 [PENDING] Call client") && overdue.contains("[OVERDUE]"));
        assertFalse(logic.execute("followups completed").getFeedbackToUser().contains("#1"));
    }

    @Test
    void execute_repeatedStatusChange_rejectedWithoutSaving() throws Exception {
        logic.execute("followup 1 a/Call client");
        logic.execute("completefollowup 1 i/1");

        CommandException completedAgain = assertThrows(CommandException.class, () ->
                logic.execute("completefollowup 1 i/1"));
        assertEquals("Follow-up #1 is already completed.", completedAgain.getMessage());
        logic.execute("reopenfollowup 1 i/1");
        CommandException reopenedAgain = assertThrows(CommandException.class, () ->
                logic.execute("reopenfollowup 1 i/1"));
        assertEquals("Follow-up #1 is already pending.", reopenedAgain.getMessage());
        assertEquals(3, storage.saveCount);
    }

    @Test
    void execute_reopenWithPendingDuplicate_rejectedWithoutSaving() throws Exception {
        logic.execute("followup 1 a/Call client");
        logic.execute("completefollowup 1 i/1");
        logic.execute("followup 1 a/Call client");
        List<FollowUp> before = model.getFilteredPersonList().getFirst().getFollowUps();

        CommandException exception = assertThrows(CommandException.class, () ->
                logic.execute("reopenfollowup 1 i/1"));
        assertTrue(exception.getMessage().contains("#2"));
        assertEquals(before, model.getFilteredPersonList().getFirst().getFollowUps());
        assertEquals(3, storage.saveCount);
    }

    @Test
    void execute_statusChangeOnMissingTarget_rejectedWithoutSaving() throws Exception {
        logic.execute("followup 1 a/Call client");
        assertThrows(CommandException.class, () -> logic.execute("completefollowup 1 i/99"));
        assertThrows(CommandException.class, () -> logic.execute("completefollowup 99 i/1"));
        assertThrows(ParseException.class, () -> logic.execute("completefollowup 1"));
        assertEquals(1, storage.saveCount);
    }

    @Test
    void execute_statusChangeSaveFails_restoresPreviousStatus() throws Exception {
        logic.execute("followup 1 a/Call client");
        List<FollowUp> before = model.getFilteredPersonList().getFirst().getFollowUps();
        storage.shouldFail = true;

        assertThrows(CommandException.class, () -> logic.execute("completefollowup 1 i/1"));
        assertEquals(before, model.getFilteredPersonList().getFirst().getFollowUps());
        assertEquals(before, storage.loadForStartup().contacts().getFirst().getFollowUps());
    }
}
