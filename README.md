[![CI Status](https://github.com/se-edu/addressbook-level3/workflows/Java%20CI/badge.svg)](https://github.com/se-edu/addressbook-level3/actions)

[![codecov](https://codecov.io/gh/AY2627S1-CS2103-F13-2/tp/graph/badge.svg?token=AH8IY34U2W)](https://codecov.io/gh/AY2627S1-CS2103-F13-2/tp)

![Ui](docs/images/Ui.png)

# Astra

**Astra is a command-line contact book for people who network a lot.** Capture each new person you meet (name, company, role, phone, email and LinkedIn) in the seconds between conversations, then pull them up again before you next meet.

* **Who it is for:** students and professionals who attend networking events, career fairs and conferences, type fast, and are comfortable with a CLI.
* **What it does:**
  * `/add` a contact, with multiple companies, roles, numbers and emails per person
  * `/find` a contact by name (partial matches allowed), phone number or email, and view everything stored about them
  * `/edit` details that were mistyped or have changed
  * `/delete` contacts by email, phone number or name, after confirmation
  * `/list` all contacts, `/help` for command formats, `/clear` the whole list, and `/exit`
  * Detects duplicate phone numbers and emails, and lets you keep, replace or merge the records
  * Saves automatically after every change, and recovers what it can from a corrupted data file
