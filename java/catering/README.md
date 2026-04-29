# CAT & RING

Java backend for the **Sviluppo e Architettura del Software** course case study (catering management) at the University of Turin.

The implementation covers the *Gestire menù* and *Gestire compiti cucina* use cases, plus supporting infrastructure for events, services, shifts, users, recipes, and preparations. There is no UI — the project is exercised from JUnit tests and `main` methods.

---

## Quick start

```bash
mvn compile        # build
mvn test           # 48 / 48 tests pass
mvn exec:java      # runs catering.businesslogic.CatERing.main
```

Requires JDK 17+ and Maven. The SQLite database lives at `database/catering.db` and is regenerated from `database/catering_init_sqlite.sql` only when the file is absent. **If you change the schema, delete the `.db` file first** — there is no migration mechanism.

---

## Architecture

The code is organised in two layers:

**`businesslogic/`** — domain entities and use-case controllers.

- `CatERing` (Singleton facade) is the entry point. It builds and holds the use-case managers and registers the persistence observers in its constructor.
- `MenuManager`, `RecipeManager`, `EventManager`, `KitchenTaskManager`, `ShiftManager`, `UserManager` — one controller per use-case area. Each holds the relevant in-memory state (e.g. `currentMenu`, `currentSummarySheet`) and exposes the system operations from the SSDs in `teoria/05-DiagrammaDiSequenzaDiSistema.pdf`.
- Domain entities — `Menu`, `Section`, `MenuItem`, `Recipe`, `Preparation`, `Event`, `Service`, `KitchenTask`, `SummarySheet`, `Assignment`, `Shift`, `User` — model the concepts named in `teoria/04-ModelloDelDominio.pdf`. Each domain entity exposes static methods for its own persistence (`Menu.create`, `Menu.load`, `Recipe.loadRecipe`, etc.).

**`persistence/`** — JDBC infrastructure and the event-receiver implementations.

- `PersistenceManager` is a static utility for executing parameterised SQL via `BatchUpdateHandler` / `ResultHandler` callbacks.
- `MenuPersistence`, `EventPersistence`, `KitchenTaskPersistence` implement the `*EventReceiver` interfaces declared by the corresponding managers. They subscribe to manager notifications and persist the changes by calling the entity-level static persistence methods.

The notification flow is wired in `CatERing`'s constructor:
```java
menuMgr.addEventReceiver(menuPersistence);
kitchenTaskMgr.addEventReceiver(kitchenTaskPersistence);
eventMgr.addEventReceiver(eventPersistence);
```

---

## Reading path

If you're new to the codebase, follow the wiring outwards from one entry point:

1. **`businesslogic/CatERing.java`** — the singleton, the constructor wiring, the manager accessors.
2. **`businesslogic/menu/MenuManager.java`** — *Gestire menù* operations (`createMenu`, `defineSection`, `insertItem`, `addMenuFeatures`, `publish`, ...). The cleanest example of a use-case controller.
3. **`businesslogic/menu/Menu.java`** — domain class with both behaviour and static persistence methods.
4. **`persistence/MenuPersistence.java`** — the observer that turns `MenuManager` events into persistence calls.
5. **`businesslogic/kitchen/KitchenTaskManager.java`** + **`SummarySheet.java`** — the kitchen-task use case end-to-end.

---

## Tests

`src/test/java/catering/` contains 48 tests organised by use case:

- `businesslogic/menu/MenuTest.java` — domain tests for the `Menu` aggregate.
- `businesslogic/menu/MenuManagerTest.java` — system-operation tests for *Gestire menù*. Uses `@Nested` classes per operation (`CreateMenu`, `DefineSection`, `InsertItem`, `MoveSection`, `ChooseMenu`, `ChooseMenuForCopy`, `DeleteMenu`) — worth studying as a pattern for organising tests around SSD operations.
- `businesslogic/event/EventTest.java` — domain tests for events.
- `businesslogic/kitchen/KitchenTaskManagerTest.java` — system-operation tests for *Gestire compiti cucina*.
- `businesslogic/kitchen/SummarySheetTest.java` — integration test for summary-sheet generation and task assignment (touches the live SQLite database).

---

## Project layout

```
src/main/java/catering/
  businesslogic/
    CatERing.java                     ← Singleton facade, manager wiring
    UseCaseLogicException.java        ← domain-level checked exception
    menu/
      Menu, Section, MenuItem         ← domain entities + static persistence methods
      MenuManager                     ← use-case controller
      MenuEventReceiver               ← observer interface
    recipe/
      KitchenProcess (interface)
      Recipe, Preparation             ← implement KitchenProcess
      RecipeManager
    event/
      Event, Service                  ← domain
      EventManager, EventReceiver
    kitchen/
      KitchenTask, SummarySheet, Assignment
      KitchenTaskManager, KitchenTaskEventReceiver
    shift/
      Shift, ShiftManager
    user/
      User, UserManager
  persistence/
    PersistenceManager                ← static JDBC utility
    MenuPersistence                   ← observer impl
    EventPersistence                  ← observer impl
    KitchenTaskPersistence            ← observer impl
src/test/java/catering/               ← see "Tests" section
database/
  catering_init_sqlite.sql            ← schema + seed data
  catering.db                         ← regenerated when missing
```

---

## Conventions

- The course is in Italian; identifiers in code are anglicised (`Menu` for *Menù*, `Section` for *Sezione*, `Recipe` for *Ricetta*, etc.).
- `CatERing.getInstance()` is the entry point for any code that needs to use the system. The `CatERing` constructor is private.
- Domain entities hold their own SQL via static methods (`Menu.create`, `Section.loadSections`, `Recipe.loadRecipe`, ...). The observers delegate to those statics.
- Managers throw `UseCaseLogicException` when a use-case precondition fails.
- Build outputs go to `target/`. The SQLite DB lives at `database/catering.db` (regenerated from the SQL script when missing).
