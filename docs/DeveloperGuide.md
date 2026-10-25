---
layout: page
title: Developer Guide
---
* Table of Contents
{:toc}

--------------------------------------------------------------------------------------------------------------------

## **Acknowledgements**

* _{List the sources of reused or adapted ideas, code, documentation, and third-party libraries here, with links to the originals.}_

--------------------------------------------------------------------------------------------------------------------

## **Setting up, getting started**

Refer to the guide [_Setting up and getting started_](SettingUp.md).

--------------------------------------------------------------------------------------------------------------------

## **Design**

<div markdown="span" class="alert alert-primary">

:bulb: **Tip:** The `.puml` files used to create diagrams are in `docs/diagrams`. Refer to the [_PlantUML Tutorial_ at se-edu/guides](https://se-education.org/guides/tutorials/plantUml.html) to learn how to create and edit diagrams.
</div>

### Architecture

<img src="images/ArchitectureDiagram.png" width="280" />

The ***Architecture Diagram*** given above explains the high-level design of the App.

The following provides a quick overview of the main components and their interactions.

**Main components of the architecture**

**`Main`** (consisting of classes [`Main`](https://github.com/se-edu/addressbook-level3/tree/master/src/main/java/seedu/address/Main.java) and [`MainApp`](https://github.com/se-edu/addressbook-level3/tree/master/src/main/java/seedu/address/MainApp.java)) is in charge of the app launch and shut down.
* At app launch, it initializes the other components in the correct sequence, and connects them up with each other.
* At shut down, it shuts down the other components and invokes cleanup methods where necessary.

The bulk of the app's work is done by the following four components:

* [**`UI`**](#ui-component): The UI of the App.
* [**`Logic`**](#logic-component): The command executor.
* [**`Model`**](#model-component): Holds the data of the App in memory.
* [**`Storage`**](#storage-component): Reads data from, and writes data to, the hard disk.

[**`Commons`**](#common-classes) represents a collection of classes used by multiple other components.

**How the architecture components interact with each other**

The *Sequence Diagram* below shows how the components interact with each other for the scenario where the user issues the command `delete 1`.

<img src="images/ArchitectureSequenceDiagram.png" width="574" />

Each of the four main components (also shown in the diagram above),

* defines its *API* in an `interface` with the same name as the Component.
* provides its functionality through a concrete `{Component Name}Manager` class that implements the corresponding API interface.

For example, the `Logic` component defines its API in `Logic.java` and implements it in `LogicManager.java`. Other components interact with a component through its interface rather than its concrete class, preventing them from coupling to that component's implementation, as illustrated in the following partial class diagram.

<img src="images/ComponentManagers.png" width="300" />

The sections below give more details of each component.

### UI component

The **API** of this component is specified in [`Ui.java`](https://github.com/se-edu/addressbook-level3/tree/master/src/main/java/seedu/address/ui/Ui.java)

![Structure of the UI Component](images/UiClassDiagram.png)

The UI consists of a `MainWindow` and its parts, such as `CommandBox`, `ResultDisplay`, `PersonListPanel`, and `StatusBarFooter`. All of these, including `MainWindow`, inherit from the abstract `UiPart` class, which captures common behavior among classes that represent visible GUI parts.

The `UI` component uses the JavaFX UI framework. The layouts of these UI parts are defined in matching `.fxml` files in `src/main/resources/view`. For example, [`MainWindow.fxml`](https://github.com/se-edu/addressbook-level3/tree/master/src/main/resources/view/MainWindow.fxml) specifies the layout of [`MainWindow`](https://github.com/se-edu/addressbook-level3/tree/master/src/main/java/seedu/address/ui/MainWindow.java).

The `UI` component,

* executes user commands using the `Logic` component.
* listens for changes to `Model` data so that the UI can be updated with the modified data.
* keeps a reference to the `Logic` component, because the `UI` relies on the `Logic` to execute commands.
* depends on some classes in the `Model` component because it displays `Person` objects from the model.

### Logic component

**API** : [`Logic.java`](https://github.com/se-edu/addressbook-level3/tree/master/src/main/java/seedu/address/logic/Logic.java)

Here's a (partial) class diagram of the `Logic` component:

<img src="images/LogicClassDiagram.png" width="550"/>

The sequence diagram below illustrates the interactions within the `Logic` component, taking `execute("delete 1")` API call as an example.

![Interactions Inside the Logic Component for the `delete 1` Command](images/DeleteSequenceDiagram.png)

<div markdown="span" class="alert alert-info">:information_source: **Note:** The lifeline for `DeleteCommandParser` should end at the destroy marker (X), but due to a limitation of PlantUML, it continues to the end of the diagram.
</div>

How the `Logic` component works:

1. When `Logic` is called upon to execute a command, the command is passed to a `ContactFileParser` object, which in turn creates a parser that matches the command (e.g., `DeleteCommandParser`) and uses it to parse the command.
1. This results in a `Command` object (more precisely, an object of one of its subclasses e.g., `DeleteCommand`) which is executed by the `LogicManager`.
1. The command can communicate with the `Model` when it is executed (e.g. to delete a person).<br>
   Note that although this is shown as a single step in the diagram above for simplicity, the code can require several interactions between the command object and the `Model` to complete the operation.
1. The result of the command execution is encapsulated as a `CommandResult` object which is returned from `Logic`.

Here are the other classes in `Logic` (omitted from the class diagram above) that are used for parsing a user command:

<img src="images/ParserClasses.png" width="600"/>

How the parsing works:
* When called upon to parse a user command, the `ContactFileParser` class creates an `XYZCommandParser` (`XYZ` is a placeholder for the specific command name, e.g., `AddCommandParser`). The parser uses the other classes shown above to parse the user command and create an `XYZCommand` object (e.g., `AddCommand`). The `ContactFileParser` returns that object as a `Command` object.
* All `XYZCommandParser` classes, such as `AddCommandParser` and `DeleteCommandParser`, implement the `Parser` interface so they can be treated similarly where appropriate, for example during testing.

### Model component
**API** : [`Model.java`](https://github.com/se-edu/addressbook-level3/tree/master/src/main/java/seedu/address/model/Model.java)

<img src="images/ModelClassDiagram.png" width="450" />


The `Model` component,

* stores the contact file data i.e., all `Person` objects (which are contained in a `UniquePersonList` object).
* a `Person` always has a `Name`, and may additionally have a `Company`, `Role`, `Phone`, `Email` and `Linkedin`. Every field other than the name is optional, because a contact met at a networking event is often captured with only the details that were exchanged. Optional fields are exposed as `Optional<...>` so callers must handle an absent value.
* two `Person` objects are currently treated as duplicates when they have the same `Name`. Keying on the contact number or email instead is planned for a later iteration.
* stores the `Person` objects selected by the current filter, such as search results, in a separate _filtered_ list. It exposes this list as an unmodifiable `ObservableList<Person>` that the UI can observe and bind to, so the UI updates when the list changes.
* stores a `UserPrefs` object that represents the user’s preferences (currently, just the GUI settings). This is exposed to the outside as a `ReadOnlyUserPrefs` object.
* does not depend on any of the other three components (as the `Model` represents data entities of the domain, they should make sense on their own without depending on other components)

<div markdown="span" class="alert alert-info">:information_source: **Note:** The alternative, arguably more object-oriented, design below keeps a unique list of tags in `ContactFile`, and each `Person` references tags from that list. This lets `ContactFile` maintain one `Tag` object per unique tag instead of each `Person` holding its own `Tag` objects.<br>

<img src="images/BetterModelClassDiagram.png" width="450" />

</div>


### Storage component

**API** : [`Storage.java`](https://github.com/se-edu/addressbook-level3/tree/master/src/main/java/seedu/address/storage/Storage.java)

<img src="images/StorageClassDiagram.png" width="550" />

The `Storage` component,
* can save both contact file data and user preference data in JSON format, and read them back into corresponding objects.
* is implemented by `StorageManager`, which delegates the actual JSON file access to `JsonContactFileStorage` and `JsonUserPrefsStorage` (one class per data file).
* depends on some classes in the `Model` component (because the `Storage` component's job is to save/retrieve objects that belong to the `Model`)

### Common classes

Classes used by multiple components are in the `seedu.address.commons` package.

--------------------------------------------------------------------------------------------------------------------

## **Implementation**

This section describes some noteworthy details on how certain features are implemented.

### Identifying a contact by name

`find`, `edit` and `delete` all let the user refer to a contact by name instead of by its index in the
displayed list. Left to themselves, each command would need its own answer to what naming a contact
means: how much of the name has to be given, whether case matters, and what to do when a name fits more
than one contact. Three answers that are meant to agree but are written three times are three answers
that will eventually disagree, and a user who is told `find Meier` matches two contacts but
`delete Meier` deletes one would be right to call that a bug.

So there is one answer, in two pieces:

* `NameContainsKeywordsPredicate` (in `model.person`) decides whether a contact's name matches a query.
  Both sides are trimmed, their runs of whitespace collapsed and their case folded, and the query must
  then appear in the name as a contiguous run. `find` applies this predicate directly.
* `PersonNameResolver` (in `logic.commands`) turns a name into the single contact it refers to, by
  applying that predicate and looking at how many contacts came back. `edit` and `delete` go through it.

<img src="images/NameResolutionClassDiagram.png" width="550" />

`PersonNameResolver#resolve` returns a sealed `Outcome` of either `Unique`, carrying the one contact
that matched, or `Several`, carrying a numbered list of the matches for the command to show. A name
matching nothing is a `CommandException` instead, since there is no contact for the caller to act on.
Making the ambiguous case a value rather than an error is what stops a command from quietly picking the
first match: there is no `Person` to reach for until the user has narrowed it down.

Two consequences are worth noting:

* **Resolving a name searches every contact, not only the ones on display.** Naming someone therefore
  reaches them even while a `find` filter is hiding them, which is what a user who has just typed a
  full name expects. As a side effect the displayed list is narrowed to the matches, so that the
  contacts under discussion are the contacts on screen; a command that goes on to change the address
  book restores the full list once it is done.
* **The resolver is told which command word to name in its message**, so the list of matches ends with
  "To delete one of them…" or "To edit one of them…" as appropriate. This is the only thing the
  resolver needs to know about its caller, which keeps the dependency pointing one way: commands depend
  on the resolver, and the resolver depends on nothing but `Model` and the predicate.

Deciding *whether* the user typed an index or a name happens earlier, in the parsers, and is shared the
same way: `ParserUtil#isIndexInput` reports whether the first word of the argument is a number.
`EditCommandParser` and `DeleteCommandParser` both consult it, so no input can be read as an index by
one command and as a name by another.

#### Alternatives considered

* **Matching full names exactly, instead of reusing `find`'s partial matching.** An earlier draft of
  `delete` did this, on the grounds that an irreversible command should not act on a fuzzy match. It was
  dropped because it gave Astra two meanings for the same gesture, and because the ambiguous case is
  handled by *refusing to delete* and asking the user to pick, which is just as safe and far less
  surprising than `delete Meier` reporting that nobody is called that.
* **A `PersonSelector` type with index and name implementations**, which commands would hold instead of
  a nullable index and a nullable name. This removes the remaining nullable-field pair from `EditCommand`
  and `DeleteCommand` and would let `view` gain name support for free. It is the natural next step once
  a third command needs it; it was not done here to keep the change to `edit` small.

### \[Proposed\] Undo/redo feature

#### Proposed Implementation

The proposed undo/redo mechanism is facilitated by `VersionedContactFile`. It extends `ContactFile` with an undo/redo history, stored internally as a `contactFileStateList` and `currentStatePointer`. Additionally, it implements the following operations:

* `VersionedContactFile#commit()` — Saves the current contact file state in its history.
* `VersionedContactFile#undo()` — Restores the previous contact file state from its history.
* `VersionedContactFile#redo()` — Restores a previously undone contact file state from its history.

These operations are exposed in the `Model` interface as `Model#commitContactFile()`, `Model#undoContactFile()` and `Model#redoContactFile()` respectively.

Given below is an example usage scenario and how the undo/redo mechanism behaves at each step.

Step 1. The user launches the application for the first time. The `VersionedContactFile` will be initialized with the initial contact file state, and the `currentStatePointer` pointing to that single contact file state.

![UndoRedoState0](images/UndoRedoState0.png)

Step 2. The user executes `delete 5` command to delete the 5th person in the contact file. The `delete` command calls `Model#commitContactFile()`, causing the modified state of the contact file after the `delete 5` command executes to be saved in the `contactFileStateList`, and the `currentStatePointer` is shifted to the newly inserted contact file state.

![UndoRedoState1](images/UndoRedoState1.png)

Step 3. The user executes `add n/David …​` to add a new person. The `add` command also calls `Model#commitContactFile()`, causing another modified contact file state to be saved into the `contactFileStateList`.

![UndoRedoState2](images/UndoRedoState2.png)

<div markdown="span" class="alert alert-info">:information_source: **Note:** If a command fails its execution, it will not call `Model#commitContactFile()`, so the contact file state will not be saved into the `contactFileStateList`.

</div>

Step 4. The user now decides that adding the person was a mistake, and decides to undo that action by executing the `undo` command. The `undo` command will call `Model#undoContactFile()`, which will shift the `currentStatePointer` once to the left, pointing it to the previous contact file state, and restores the contact file to that state.

![UndoRedoState3](images/UndoRedoState3.png)

<div markdown="span" class="alert alert-info">:information_source: **Note:** If the `currentStatePointer` is at index 0, pointing to the initial ContactFile state, then there are no previous ContactFile states to restore. The `undo` command uses `Model#canUndoContactFile()` to check if this is the case. If so, it will return an error to the user rather
than attempting to perform the undo.

</div>

The following sequence diagram shows how an undo operation goes through the `Logic` component:

![UndoSequenceDiagram](images/UndoSequenceDiagram-Logic.png)

<div markdown="span" class="alert alert-info">:information_source: **Note:** The lifeline for `UndoCommand` should end at the destroy marker (X), but due to a limitation of PlantUML, it continues to the end of the diagram.

</div>

Similarly, how an undo operation goes through the `Model` component is shown below:

![UndoSequenceDiagram](images/UndoSequenceDiagram-Model.png)

The `redo` command does the opposite — it calls `Model#redoContactFile()`, which shifts the `currentStatePointer` once to the right, pointing to the previously undone state, and restores the contact file to that state.

<div markdown="span" class="alert alert-info">:information_source: **Note:** If the `currentStatePointer` is at index `contactFileStateList.size() - 1`, pointing to the latest contact file state, then there are no undone ContactFile states to restore. The `redo` command uses `Model#canRedoContactFile()` to check if this is the case. If so, it will return an error to the user rather than attempting to perform the redo.

</div>

Step 5. The user then decides to execute the command `list`. Commands that do not modify the contact file, such as `list`, will usually not call `Model#commitContactFile()`, `Model#undoContactFile()` or `Model#redoContactFile()`. Thus, the `contactFileStateList` remains unchanged.

![UndoRedoState4](images/UndoRedoState4.png)

Step 6. The user executes `clear`, which calls `Model#commitContactFile()`. Since the `currentStatePointer` is not pointing at the end of the `contactFileStateList`, all contact file states after the `currentStatePointer` will be purged. Reason: It no longer makes sense to redo the `add n/David …​` command. This is the behavior that most modern desktop applications follow.

![UndoRedoState5](images/UndoRedoState5.png)

The following activity diagram summarizes what happens when a user executes a new command:

<img src="images/CommitActivityDiagram.png" width="250" />

#### Design considerations:

**Aspect: How undo & redo execute:**

* **Alternative 1 (current choice):** Saves the entire contact file.
  * Pros: Easy to implement.
  * Cons: May have performance issues in terms of memory usage.

* **Alternative 2:** Individual command knows how to undo/redo by
  itself.
  * Pros: Will use less memory (e.g. for `delete`, just save the person being deleted).
  * Cons: We must ensure that the implementation of each individual command is correct.

_{more aspects and alternatives to be added}_

### \[Proposed\] Data archiving

_{Explain here how the data archiving feature will be implemented}_


--------------------------------------------------------------------------------------------------------------------

## **Documentation, logging, testing, dev-ops**

* [Documentation guide](Documentation.md)
* [Testing guide](Testing.md)
* [Logging guide](Logging.md)
* [DevOps guide](DevOps.md)

--------------------------------------------------------------------------------------------------------------------

## **Appendix: Requirements**

### Product scope

**Target user profile**:

* attends networking events, career fairs or conferences regularly
* meets many new people in a short span and struggles to recall who was who afterwards
* wants to capture contact details (company, role phone number, email, LinkedIn) and context in seconds, mid-conversation or right after
* prefers desktop apps over other types of applications
* can type fast and prefers typing to mouse interactions
* is reasonably comfortable using CLI apps

**Value proposition**: Networking events generate more contacts than anyone can remember and more context than a phone contacts app can hold. Astra lets a fast typist capture each new person (role, company, LinkedIn, who introduced them, and what was discussed) in the seconds between conversations, and search any of it back up instantly, so no one you met becomes "that person from the fair".

### User stories

Priorities: High (must have) - `* * *`, Medium (nice to have) - `* *`, Low (unlikely to have) - `*`

| Priority | As a …​                                    | I want to …​                     | So that I can…​                                                        |
| -------- | ------------------------------------------ | ------------------------------ | ---------------------------------------------------------------------- |
| `* * *`  | user                                       | add a new contact with their name, company, role and contact details               | remember and reach out to people  meet                                                                        |
| `* * *` | user                                        | view all the information stored about a contact in one place   | refresh my memory before meeting them again                      |
|`* * *`| user                                | record a contact's LinkedInprofile                   |
| `* * *`  | user                                       | delete a person                | remove entries that I no longer need                                   |
| `* * *`  | user                                       | find a person by name          | locate details of persons without having to go through the entire list |
| `* * *`  | user                                     | edit a contact's details                                            | fix mistakes or add details I learn later                         |
| `* * *`  | frequent user                              | delete contacts I no longer need | reduce clutter                                                         |
| `* * *`  | user                                       | clear all contacts | start over when I no longer need the list                              |
| `* *`    | careless user                              | be asked to confirm before contacts are deleted or cleared | avoid wiping out contacts by a mistyped command                  |
| `* * *`  | user                                       | exit the app with all saved changes kept | close it without worrying about losing data   
| `* * *`  | user                                       | find a contact by name, phone number or email                       | retrieve their details quickly                                    |
| `* * *`  | user                                       | find a contact using only part of their name                        | look someone up when I only remember their first or last name     |
| `* * *`  | user                                       | list all contacts                                                   | browse my contact list                                            |
| `* * *`  | user                                     | retrieve my contacts after closing and reopening the app            | keep what I have recorded                                         |
| `* * *`  | new user                                 | view help for the available commands                                | learn the commands without memorising them                        |
|`* * `| user                                          | record where I met the contact and what we discussed       | have context for the next conversation                         |
|`* * `| user                                          | record who introduced me to a contact                     | remember how I am connected to them |
| `* *`    | user who struggles with names              | find someone without typing the exact spelling of their name        | look them up even when I misremember their name                   |
| `* *`    | user                                       | filter contacts by categories or custom tags                        | narrow down a large list quickly                                  |
| `* *`    | frequent user                              | star important contacts                                             | revisit them quickly                                              |
| `* *`    | user                                       | hide private contact details   | minimize chance of someone else seeing them by accident                |
| `* *`    | user                                       | record follow-up actions for a contact | remember what I need to do after meeting them                          |
| `* *`    | user                                       | mark a follow-up action as completed | tell pending tasks apart from finished ones                            |
| `* *`    | user                                       | see when I last interacted with someone | know how long it has been since we last met                            |
| `* *`    | careless user                            | undo an accidental deletion or edit                                 | avoid losing important information                                |
| `* *`    | potential user exploring the app         | see the app populated with sample data                              | get a feel for how the app looks in use                           |
| `*`      | user with many persons in the contact file | sort persons by name           | locate a person easily                                                 |
| `*`      | user                                     | see which contact entries are incomplete                            | remember to fill in missing details later                         |
| `*`      | frequent user                            | reorder the information shown for a contact                         | see the details I care about first                                |
| `*`      | expert user                              | define shortcuts for commands                                       | add contacts faster than typing commands in full                  |
| `*`      | frequent user                            | reuse templates of common fields when adding contacts               | enter repetitive data faster                                      |
| `*`      | first-time user                          | follow a tutorial of the main features                              | start using the app confidently                                   |
| `*`      | user                                     | protect my contact data with a password                             | stop other people using my device from viewing it                 |

*{More to be added}*

### Use cases

(For all use cases below, the **System** is `Astra` and the **Actor** is the `user`, unless specified otherwise)

**Use case: UC01 - Add a contact**

**MSS**

1. User enters an add command with the contact's name, company, role, contact number and email, and optionally a LinkedIn URL.
2. Astra validates and normalises the details.
3. Astra adds the contact, saves the contact list and shows the saved details. 

   Use case ends.

**Extensions**

* 1a. A required field is missing, a prefix is unsupported or repeated, or a value is invalid.
    * 1a1. Astra shows the relevant error message and accepted format.

      Use case ends.

* 2a. The name matches the full name or first name of an existing contact.
    * 2a1. Astra shows the matching contacts and asks whether to add a separate contact.
    * 2a2. User confirms.
      
      Use case resumes at step 3.

* 2b. The contact number or email belongs to exactly one existing contact.
    * 2b1. Astra shows the existing and incoming details and offers keep, replace, merge or cancel.
    * 2b2. User chooses replace or merge (choosing a name if the two names differ).
    * 2b3. Astra shows a preview of the resulting contact and asks for confirmation.
    * 2b4. User confirms.
    * 2b5. Astra updates the existing contact in its original position and saves the contact list.

      Use case ends.

* 2c. The contact number and email belong to two different existing contacts.
    * 2c1. Astra rejects the command and identifies the conflicting contacts.
      
      Use case ends.

* 2d. All normalised fields already match one existing contact.
    * 2d1. Astra shows that the contact already exists and makes no changes.

      Use case ends.

* 3a. Saving fails.
    * 3a1. Astra shows a save error. The contact list and file are unchanged.
      
      Use case ends.

* *a. At any prompt, the user chooses keep, cancel or n.
    * *a1. Astra leaves the contact list unchanged.

      Use case ends.


**Use case: UC02 - Delete a contact**

**MSS**

1. User enters a delete command, identifying the contact either by its index in the displayed list or by name.
2. Astra deletes the contact, saves the contact list and shows the details of the contact it deleted.

   Use case ends.

**Extensions**

* 1a. The contact list is empty.
    * 1a1. Astra shows that there are no contacts to delete.

      Use case ends.

* 1b. The input is empty, is a malformed index, or is not a valid name.
    * 1b1. Astra shows the command usage.

      Use case ends.

* 1c. The given index is not in the displayed list.
    * 1c1. Astra shows that the index is invalid.

      Use case ends.

* 1d. The given name matches no contact.
    * 1d1. Astra shows that no contact of that name was found, and deletes nothing.

      Use case ends.

* 1e. Several contacts match the given name.
    * 1e1. Astra shows a numbered list of the matching contacts with their names, companies and emails, and deletes nothing.
    * 1e2. User enters the command again with the index of the contact they meant.

      Use case resumes at step 2.

**Deferred to a later version**

* Confirming before the contact is deleted. v1.3 deletes immediately; the prompt arrives with the
  pending-command layer, which UC05 will share.
* Deleting by email or contact number, and deleting several contacts in one command. These wait on the
  matching for those fields so that it is shared with `find` rather than written twice.
* Reporting a failed save and leaving the contact list and file unchanged. This needs atomic saves in
  `Storage` to be meaningful, since a half-written file is worse than a reported failure.

**Use case: UC03 - Find and view a contact**

**MSS**

1. User enters a find command with a name, a contact number or an email.
2. Astra finds exactly one matching contact and shows all of its stored information.

   Use case ends.

**Extensions**

* 1a. The contact list is empty.
    * 1a1. Astra shows that there are no contacts to view.

      Use case ends.

* 1b. The search value is missing, or the contact number or email is malformed.
    * 1b1. Astra shows the relevant error message.

      Use case ends.

* 2a. No contact matches.
    * 2a1. Astra shows that the contact was not found.

      Use case ends.

* 2b. Several contacts match.
    * 2b1. Astra shows the number of matches and a ranked, numbered list with names, companies and emails.
    * 2b2. User selects a contact by index.
    * 2b3. Astra shows all stored information of the selected contact.

      Use case ends.

    * 2b2a. User enters an invalid index.
        * 2b2a1. Astra shows an error message.

          Use case resumes at step 2b2.

    * 2b2b. User enters cancel.

      Use case ends.

**Use case: UC04 - Edit a contact**

**MSS**

1. User enters an edit command, identifying the contact either by its index in the displayed list or by name, with the new values of one or more fields.
2. Astra finds the contact and validates the new values.
3. Astra updates the contact, saves the contact list and lists every updated field.

   Use case ends.

**Extensions**

* 1a. No field to change is given.
    * 1a1. Astra shows that at least one field must be provided.

      Use case ends.

* 1b. The command format is invalid, a prefix is repeated, or a value is malformed.
    * 1b1. Astra shows the relevant error message, with the command usage if the format is invalid.

      Use case ends.

* 1c. A company, role, contact number, email or LinkedIn prefix is given with no value, e.g. `c/`.
    * 1c1. Astra treats it as removing that field from the contact.

      Use case resumes at step 2.

* 2a. The given index is not in the displayed list.
    * 2a1. Astra shows that the index is invalid.

      Use case ends.

* 2b. The given name matches no contact.
    * 2b1. Astra shows that no contact of that name was found, and changes nothing.

      Use case ends.

* 2c. Several contacts match the given name.
    * 2c1. Astra shows a numbered list of the matching contacts with their names, companies and emails, and changes nothing.
    * 2c2. User enters the command again with the index of the contact they meant.

      Use case resumes at step 2.

* 2d. The new contact number or email already belongs to another contact.
    * 2d1. Astra rejects the whole edit, names the other contact, and changes nothing.

      Use case ends.

* 2e. The new name is exactly the name of another contact.
    * 2e1. Astra rejects the edit and changes nothing.

      Use case ends.

* 2f. Every new value is identical to the current one.
    * 2f1. Astra shows that there are no changes to apply and does not save.

      Use case ends.

**Deferred to a later version**
* Choosing one of several matching contacts at a prompt instead of entering the command again, and cancelling at
  any prompt. These arrive with the pending-command layer that UC02 and UC05 will share.
* Warning and asking for confirmation when the new name or LinkedIn URL matches another contact, instead of
  rejecting an identical name outright. This also needs the pending-command layer.
* Reporting a failed save and leaving the contact list and file unchanged. As for UC02, this needs atomic saves in
  `Storage` to be meaningful.

**Use case: UC05 - Clear all contacts**

**MSS**

1. User requests to clear all contacts.
2. Astra saves an empty contact list and shows how many contacts it cleared and that 0 remain.

   Use case ends.

**Extensions**

* 1a. The contact list is empty.
    * 1a1. Astra shows that there are no contacts to clear.

      Use case ends.

* 1b. The command has extra arguments.
    * 1b1. Astra shows the command usage.

      Use case ends.

**Deferred to a later version**

* Confirming before the list is cleared, together with UC02's prompt and sharing the same
  pending-command layer.

* 3b. User enters a response other than y or n.
    * 3b1. Astra asks the user to enter y or n.

      Use case resumes at step 3.

* 4a. Saving fails.
    * 4a1. Astra shows a save error. The contact list and file are unchanged.

      Use case ends.

**Use case: UC06 - Start Astra and restore saved contacts**

**MSS**

1. User launches Astra.
2. Astra reads the contact file and loads every contact in stored order.
3. Astra shows how many contacts were loaded and from which file, then waits for a command.

   Use case ends.

**Extensions**

* 2a. The contact file does not exist (first launch).
    * 2a1. Astra loads sample contacts to help the user explore the app.
    * 2a2. Astra explains that these are sample contacts and that the user
      can remove them using the `clear` command.

      Use case ends.

* 2b. The contact file can be parsed but contains invalid or conflicting records.
    * 2b1. Astra validates records in stored order, retaining valid contacts
      and the first valid occurrence of each conflicting contact.
    * 2b2. Astra preserves the original file unchanged in a uniquely named,
      timestamped recovery folder.
    * 2b3. Astra writes a report listing the original positions and reasons
      for skipped records.
    * 2b4. Astra safely replaces the active contact file with the recovered contacts.
    * 2b5. Astra shows the recovered and skipped counts, backup location,
      and report location.

      Use case ends.

    * 2b4a. No valid contacts can be recovered.
        * 2b4a1. Astra leaves the active file unchanged, shows the backup
          and report locations, and offers the fresh-start choice described in 2c.

          Use case ends.

* 2c. The contact file cannot be read or reliably parsed, or recovery cannot
  create its backup/report or safely replace the active file.
    * 2c1. If Astra can read the failed contact file, it preserves its exact bytes
      in a uniquely named incident folder under `data/reports` and writes a failure report.
    * 2c2. Astra shows the error, confirms where the original file is preserved,
      and offers **Start anew** or **Exit**. Exit is the default choice.
    * 2c3. If the user chooses Start anew, Astra verifies the backup and checks
      that the active file has not changed, then atomically replaces it with an
      empty contact file and opens the command interface. The original backup remains unchanged.
    * 2c4. Astra shows an empty contact list and reminds the user of the backup and report locations.

    * 2c2a. The original file cannot be read or safely preserved under reports.
        * 2c2a1. Astra explains that starting anew is unavailable and stops without clearing the contact file.
    * 2c2b. The user chooses Exit or dismisses the dialog.
        * 2c2b1. Astra stops without replacing the active file.
    * 2c3a. Backup verification or atomic replacement fails, or the active file changes.
        * 2c3a1. Astra shows the failure and available archive locations and stops without opening the command interface.

      Use case ends.
      
* 2d. Another Astra instance is already using the contact file.
    * 2d1. Astra shows that the storage is in use and the second instance stops.

      Use case ends.

*{More to be added}*

### Non-Functional Requirements

1.  Should work on any _mainstream OS_ as long as it has Java `25` or above installed.
2.  Should hold up to 1000 contacts and respond to any command within 1 second on a typical laptop, so that the user can keep up while meeting people in quick succession.
3.  A user with above average typing speed for regular English text (i.e. not code, not system admin commands) should be able to accomplish most of the tasks faster using commands than using the mouse.
4.  Should work without an internet connection, and should never send contact data over the network.
5. Should store data locally in a human-readable _JSON Lines_ file, without needing a database management system.
6. Changes from a successful command should survive closing and reopening the app. A crash during a save should leave either the complete old file or the complete new file, never a partially written one.
7. Should never silently discard data from a _corrupted contact file_: the original file is backed up before anything is replaced.
8.  Should allow only one running instance to use the contact file at a time.
9.  Should accept names containing non-English (Unicode) letters
10. Every error message should state what was wrong and, where relevant, the accepted format.
11. Should be packaged as a single JAR file that runs without an installer.

*{More to be added}*

### Glossary

* **Mainstream OS**: Windows, Linux, Unix, or macOS
* **Contact**: A record of one person. Only the name is required; a company, a role, a contact number, an email address, a LinkedIn URL and any number of tags are each optional, and the single-valued ones hold at most one value apiece
* **Contact number**: A phone number made up of at least three digits, which may also use spaces, hyphens, a leading plus sign for a country code, and balanced parentheses
* **Prefix**: A field marker in a command, such as `c/` for a company or `e/` for an email. Prefixes are case-sensitive
* **Multi-value field**: A field that can hold several comma-separated values. Every field except the name is a multi-value field
* **Private contact detail**: A contact detail that is not meant to be shared with others
* **Normalisation**: Trimming outer spaces and collapsing repeated spaces before a value is stored or compared
* **Display value**: The normalised spelling that Astra stores and shows to the user
* **Unique key**: A contact number or email. No two contacts may share one
* **Duplicate contact**: An incoming contact whose contact number or email already belongs to an existing contact
* **Merge**: Combining the distinct values of an existing and an incoming contact into one record, keeping the existing values first
* **MSS (Main Success Scenario)**: The most common path through a use case, where everything goes as expected
* **CLI (Command Line Interface)**: A text-based interface where the user types commands instead of clicking
* **Comparison value**: The form of a value used for matching, such as the lowercase name or the digits-only contact number
* **Name-match ranking**: The eight-level order (exact full name, first, last, middle; then partial first, last, middle, full) used to sort contacts that match a name
* **Candidate list**: The numbered list of matching contacts shown when a name matches several contacts. Indices start at 1
* **Contact file**: The file that stores all contacts, at `<user-home>/.contacts-cli/contacts.jsonl`
* **JSON Lines (JSONL)**: A text format with one JSON object per line
* **Corrupted contact file**: A contact file with records that are malformed, have missing fields or invalid values, or conflict with earlier records
* **Backup file**: An unchanged copy of a corrupted contact file, saved as `contacts-corrupted-<timestamp>.jsonl`

--------------------------------------------------------------------------------------------------------------------

## **Appendix: Instructions for manual testing**

Given below are instructions to test the app manually.

<div markdown="span" class="alert alert-info">:information_source: **Note:** These instructions only provide a starting point for testers to work on;
testers are expected to do more *exploratory* testing.

</div>

### Launch and shutdown

1. Initial launch

   1. Download the JAR file and copy it into an empty folder.

   1. Double-click the JAR file.<br>
      Expected: The GUI opens with a set of sample contacts. The window size may not be optimal.

1. Saving window preferences

   1. Resize the window to an optimal size. Move the window to a different location. Close the window.

   1. Relaunch the app by double-clicking the JAR file.<br>
       Expected: The most recent window size and location are retained.

1. _{ more test cases …​ }_

### Deleting a person

1. Deleting a person by index while all persons are being shown

   1. Prerequisites: List all persons using the `list` command, with multiple persons in the list.

   1. Test case: `delete 1`<br>
      Expected: The first contact is deleted from the list. The status message shows the deleted contact's details.

   1. Test case: `delete 0`<br>
      Expected: No person is deleted. The status message shows the command usage.

   1. Other incorrect delete commands to try: `delete`, `delete 1 abc`, `delete x` (where x is larger than the list size)<br>
      Expected: No person is deleted. The status message shows error details.

1. Deleting a person by index while the list is filtered

   1. Prerequisites: Filter the list with `find Meier`, so that fewer persons are shown than are stored.

   1. Test case: `delete 1`<br>
      Expected: The first contact *of the filtered results* is deleted. The remaining results stay on display.

   1. Test case: `delete x` (where x is larger than the number of results but no larger than the number stored)<br>
      Expected: No person is deleted. The status message says the index is invalid, since the index refers to what is displayed.

1. Deleting a person by name

   1. Prerequisites: List all persons using the `list` command. The sample data has one `Alice Pauline`, and both a `Benson Meier` and a `Daniel Meier`.

   1. Test case: `delete Alice Pauline`<br>
      Expected: Alice Pauline is deleted. The status message shows her details, and every remaining contact is shown again.

   1. Test case: `delete alice pauline`<br>
      Expected: As above; letter case is ignored.

   1. Test case: `delete Pauline`<br>
      Expected: As above; a partial name is enough when only one contact matches it.

   1. Test case: `delete Meier`<br>
      Expected: **Nothing is deleted.** The status message lists both Meiers, numbered, with their companies and emails, and asks for the command to be entered again with an index. Following it with `delete 2` deletes the second one listed.

   1. Test case: `delete Zed`<br>
      Expected: No person is deleted. The status message says no contact named Zed was found, and the full list is still shown.

   1. Test case: `find Alice` followed by `delete Carl Kurz`<br>
      Expected: Carl Kurz is deleted even though he was not among the displayed results, since naming a contact searches every contact.

   1. Other invalid names to try: `delete @#$`, `delete -Alice`<br>
      Expected: No person is deleted. The status message shows the command usage.

1. Deleting from an empty contact file

   1. Prerequisites: Remove every contact with the `clear` command.

   1. Test case: `delete 1`, and `delete Alice Pauline`<br>
      Expected: No person is deleted. Both report that there are no contacts to delete, rather than complaining about the index or the name.

### Clearing all entries

1. Clearing a list that has contacts in it

   1. Prerequisites: List all persons using the `list` command, with multiple persons in the list.

   1. Test case: `clear`<br>
      Expected: Every contact is removed. The status message reports how many were cleared and that 0 remain.

   1. Test case: `clear` again, on the now empty list<br>
      Expected: Nothing changes. The status message reports that there are no contacts to clear.

   1. Test case: `clear 3`<br>
      Expected: Nothing is cleared. The status message shows the command usage, since `clear` takes no parameters.

### Saving data

1. Dealing with missing/corrupted data files

   1. _{Explain how to simulate missing or corrupted data files and state the expected behavior.}_

1. _{ more test cases …​ }_
