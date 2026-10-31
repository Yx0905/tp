package seedu.address.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static seedu.address.testutil.TypicalPersons.BENSON;
import static seedu.address.testutil.TypicalPersons.DANIEL;
import static seedu.address.testutil.TypicalPersons.getTypicalContactFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import javafx.event.ActionEvent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import seedu.address.logic.LogicManager;
import seedu.address.model.ContactFile;
import seedu.address.model.ModelManager;
import seedu.address.model.ReadOnlyContactFile;
import seedu.address.model.UserPrefs;
import seedu.address.model.person.Person;
import seedu.address.storage.JsonContactFileStorage;
import seedu.address.storage.JsonUserPrefsStorage;
import seedu.address.storage.StorageManager;
import seedu.address.testutil.FxTestUtil;

@Timeout(20)
class CancelledRequestSelectionUiTest {
    @TempDir
    public Path testFolder;

    @BeforeAll
    static void startToolkit() throws Exception {
        FxTestUtil.initialize();
    }

    @Test
    void window_cancelledOrFailedRequest_keepsPreviouslySelectedContact() throws Exception {
        ModelManager model = new ModelManager(getTypicalContactFile(), new UserPrefs());
        ContactFile original = new ContactFile(model.getContactFile());
        Path contacts = testFolder.resolve("contacts.json");
        Path preferences = testFolder.resolve("preferences.json");
        LogicManager logic = new LogicManager(model, new StorageManager(
                new FailingContactFileStorage(contacts), new JsonUserPrefsStorage(preferences)));
        FxTestUtil.runOnFxThread(() -> {
            Stage stage = new Stage();
            try {
                MainWindow window = new MainWindow(stage, logic, contacts);
                window.fillInnerParts("Loaded");
                window.show();
                TextField input = (TextField) stage.getScene().lookup("#commandTextField");

                execute(input, "view 2");
                execute(input, "delete Alice");
                execute(input, "n");
                assertSelected(window, stage, BENSON);

                execute(input, "view Daniel");
                execute(input, "delete Meier");
                execute(input, "cancel");
                assertSelected(window, stage, DANIEL);

                execute(input, "list");
                execute(input, "view 2");
                execute(input, "add n/New Contact");
                assertSelected(window, stage, BENSON);

                execute(input, "delete 1");
                execute(input, "y");
                assertSelected(window, stage, BENSON);
            } finally {
                stage.hide();
            }
        });
        assertEquals(original, model.getContactFile());
        assertFalse(Files.exists(contacts));
    }

    private static void assertSelected(MainWindow window, Stage stage, Person expected) {
        assertEquals(expected, window.getPersonListPanel().selectedPersonProperty().get());
        Label phone = (Label) stage.getScene().lookup("#phoneValue");
        assertEquals(expected.getPhone().orElseThrow().value, phone.getText());
    }

    private static void execute(TextField input, String command) {
        input.setText(command);
        input.fireEvent(new ActionEvent());
    }

    /**
     * Rejects every save so that each data change is rolled back.
     */
    private static final class FailingContactFileStorage extends JsonContactFileStorage {
        private FailingContactFileStorage(Path path) {
            super(path);
        }

        @Override
        public void saveContactFile(ReadOnlyContactFile contactFile) throws IOException {
            throw new IOException("Simulated save failure");
        }
    }
}
