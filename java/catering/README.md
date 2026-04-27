# CAT & RING — `main` branch
## A worked starting point for software engineering students

This branch is the **baseline implementation** of the CAT & RING use cases (*Gestire menù*, *Gestire compiti cucina*, plus supporting infrastructure for events, shifts, users) — written *before* the GoF and GRASP patterns of the course are applied.

The companion **[`patterns` branch](https://github.com/rschifan/catering/tree/patterns)** is the same project after the refactor. The intended workflow:

1. Read `main` first. Run the tests. Skim the code. **Notice what bothers you** — places where the code is repetitive, where responsibilities feel mislocated, where a small change would force you to touch a lot of files.
2. Read the slides for each pattern as the course covers them.
3. Open `patterns` and see how each pattern resolves the specific symptom you noticed in `main`.
4. *(Optional but recommended)* Try the refactor yourself on a fresh copy of `main` before reading the `patterns` solution.

This README is written for step 1: it orients you in `main`, points out the **design pressures** that motivate each pattern in the curriculum, and frames them as questions you can engage with before being shown the answers.

---

## Quick start

```bash
mvn compile        # build
mvn test           # 48 / 48 tests pass
mvn exec:java      # runs catering.businesslogic.CatERing.main
```

Requires JDK 17+ and Maven. The SQLite database lives at `database/catering.db` and is regenerated from `database/catering_init_sqlite.sql` only when absent. **If you change the schema, delete the `.db` file first** — there is no migration mechanism.

---

## Architecture as it stands

The code is layered but the layers leak into each other in ways that the patterns curriculum exists to fix.

**`businesslogic/`** — domain classes mixed with the use-case controllers.
- `CatERing` is a **manual Singleton facade** that holds all the use-case managers. It exposes setters for every sub-manager (a hint of trouble — see *design pressure #1* below).
- `MenuManager`, `RecipeManager`, `EventManager`, `KitchenTaskManager`, `ShiftManager`, `UserManager` — one per use case area.
- `Menu`, `Section`, `MenuItem`, `Recipe`, `Preparation`, `Event`, `Service`, `KitchenTask`, `SummarySheet`, `Assignment`, `Shift`, `User` — the domain entities.

**`persistence/`** — JDBC infrastructure and the *event-receiver* implementations that listen to manager notifications and persist them.
- `PersistenceManager` is a static utility for SQL execution.
- `MenuPersistence`, `EventPersistence`, `KitchenTaskPersistence` listen to their respective managers' events.

The manager-to-persistence pathway is wired through *MenuEventReceiver / EventReceiver / KitchenTaskEventReceiver* interfaces and registered in `CatERing`'s constructor:
```java
menuMgr.addEventReceiver(menuPersistence);
kitchenTaskMgr.addEventReceiver(kitchenTaskPersistence);
eventMgr.addEventReceiver(eventPersistence);
```
This is already a hand-rolled **Observer** pattern. (Worth noticing: when the patterns curriculum gets to Observer, you'll find it's already here — only its *intent* and *trade-offs* are taught at that point.)

---

## Design pressures to notice

Each subsection below describes one *symptom* of the current code. The title in parentheses names the pattern that resolves it on the `patterns` branch — but **try to think about each smell on its own first** before peeking at the answer.

### 1. The Singleton has setters (*Singleton*)

Open [`CatERing.java`](src/main/java/catering/businesslogic/CatERing.java). The class enforces a single instance via `getInstance()` (lines 14-21) — but it also exposes `setMenuManager`, `setRecipeManager`, `setUserManager`, `setEventManager`, `setKitchenTaskManager`, `setShiftManager` (lines 75-113). Any caller can replace any sub-manager *after* construction.

**Question:** what does "Singleton" actually guarantee here? If `setMenuManager` exists, the Singleton's *identity* is unique but its *state* is not. Is that what you want? What does the slide reference (`teoria/JavaGoF/.../singleton/static_method/PrinterSpooler_b.java`) do differently?

### 2. `KitchenProcess` is an interface, but `Recipe` and `Preparation` aren't really compatible (*Composite*)

Open [`KitchenProcess.java`](src/main/java/catering/businesslogic/recipe/KitchenProcess.java) — it's a small interface (`getId`, `getName`, `getDescription`, `isRecipe`, `setId`). Both `Recipe` and `Preparation` implement it. Now look at `Recipe.java`:

```java
public class Recipe implements KitchenProcess {
    private ArrayList<Preparation> preparations;   // ← Recipe contains Preparations

    public void addPreparation(Preparation preparation) { ... }
    public ArrayList<Preparation> getPreparations() { ... }
}
```

`Preparation` does **not** have a `preparations` field. So `Recipe` is *almost* a tree node and `Preparation` is *almost* a leaf — but they share only a thin interface, not a tree structure. Code that wants to walk "everything that needs cooking for this menu" can't treat them uniformly. Look at `KitchenTask.loadAllTasksBySumSheetId` — see the `if (types.get(i)) { Recipe.loadRecipe(...) } else { Preparation.loadPreparationById(...) }` switch. That's a type tag, persisted to the database, decoded at load time. Where else does this kind of branching show up?

**Question:** what would a unified hierarchy look like? What would have to change in `KitchenProcess`, `Recipe`, `Preparation`, and `KitchenTask` to make the type tag unnecessary?

### 3. SQL lives inside the domain classes (*Strategy*)

Open [`Menu.java`](src/main/java/catering/businesslogic/menu/Menu.java). Find `Menu.create(Menu m)` at line 40 — it builds and executes an `INSERT INTO Menus`. Find `Menu.load(Integer id)` — it executes a `SELECT * FROM Menus`. Find `Menu.delete`, `Menu.saveTitle`, `Menu.savePublished`, `Menu.saveFeatures`. The same pattern appears in [`Section.java`](src/main/java/catering/businesslogic/menu/Section.java), [`MenuItem.java`](src/main/java/catering/businesslogic/menu/MenuItem.java), [`Recipe.java`](src/main/java/catering/businesslogic/recipe/Recipe.java), [`Preparation.java`](src/main/java/catering/businesslogic/recipe/Preparation.java).

The domain classes are *dual-purpose*: they hold state **and** know how to persist themselves. That coupling makes them:
- Hard to unit-test without a database.
- Tied to a specific storage technology (try swapping SQLite for an in-memory store).
- Each statically holds SQL strings that drift from the schema.

`MenuPersistence` (the Observer) just *delegates* to these statics — `Menu.create(m)`, `Section.create(...)`, `MenuItem.create(...)`. The SQL itself is not in `MenuPersistence`; it's in the model.

**Question:** if you wanted to run `MenuManager`'s tests without SQLite, what would you have to do? Can the domain class be *purely* about state and behaviour, with persistence somewhere else?

### 4. Bare `throw new UseCaseLogicException()` everywhere (*Pure Fabrication / Preconditions*)

Open [`MenuManager.java`](src/main/java/catering/businesslogic/menu/MenuManager.java). Count the `throw new UseCaseLogicException()` calls — about 20, most without a message. Each one is a *precondition violation* from the operation contracts (`teoria/06-Contratti.pdf`):
- *"actor is Chef"* (`createMenu`, `deleteMenu`, ...)
- *"a menu is in definition"* (`defineSection`, `insertItem`, ...)
- *"section belongs to menu"* (`deleteSection`, `changeSectionName`, ...)

Now open [`KitchenTaskManager.java`](src/main/java/catering/businesslogic/kitchen/KitchenTaskManager.java) — the same patterns appear (`if (currentSummarySheet == null) throw ...`, `if (!user.isChef()) throw ...`), and *some* of those throws have descriptive messages while others don't. Two managers, same kinds of preconditions, three different styles.

**Question:** if you were writing a new manager tomorrow, where would you copy the precondition-checking style from? Should every manager invent its own? Is there a place where a precondition like *"actor is Chef"* could live such that any manager could just say `Preconditions.requireChef(user)` and get a uniform behaviour and message?

### 5. Manager calls model statics for persistence (*Strategy / Observer interaction*)

You'll have noticed that `MenuPersistence.updateMenuCreated(m)` (Observer) calls `Menu.create(m)` (model static) which contains the SQL. Now look at `EventManager.modifyService` (line 225): it calls `Menu.load(menuId)` directly — a manager reaching into model statics for persistence. `Service.loadServicesForEvent` does the same at line 198. There is no Strategy seam: the persister isn't a separate object you can mock; it's a static method on the domain class.

**Question:** what would it take to give `MenuManager` a `MenuPersister` field that it could swap for tests? Where would the persister be constructed? Who would inject it?

---

## Where this leads (and why these pressures matter)

Each pressure above is the symptom that motivates a specific pattern in the curriculum:

| Pressure (in `main`) | Pattern that resolves it (in `patterns`) | GRASP principle invoked |
|---|---|---|
| 1 — Singleton with setters | **Singleton** (without setters) | *Controller (facade)* |
| 2 — Recipe / Preparation split with type tags | **Composite** (single abstract root) | *Polymorphism* |
| 3 — SQL inside domain classes | **Strategy** (`*Persister` interfaces injected into managers) | *Pure Fabrication, Protected Variations, Low Coupling* |
| 4 — Bare `throw` calls scattered across managers | **Pure Fabrication** (`Preconditions` helper) | *Pure Fabrication, Information Expert* |
| 5 — Managers calling persistence statics directly | **Strategy + Observer composition** | *Indirection, Low Coupling* |

If you read the `patterns` branch's `README.md` after working through these questions, the pattern explanations should land harder — you'll have *felt* the problem the pattern solves before being shown the solution.

---

## A note on the tests

`mvn test` runs 48 tests on `main`. They're organised by use case:
- `MenuTest` — domain tests for the `Menu` aggregate.
- `MenuManagerTest` — use-case-level tests with `@Nested` classes per system operation (`CreateMenu`, `DefineSection`, `InsertItem`, `MoveSection`, `ChooseMenu`, `ChooseMenuForCopy`, `DeleteMenu`).
- `EventTest` — domain tests for events.
- `KitchenTaskManagerTest` — use-case-level tests for kitchen task operations.
- `SummarySheetTest` — integration test for summary-sheet generation and task assignment.

The `MenuManagerTest` `@Nested` structure is worth studying — it's the cleanest example in the codebase of how to organise tests around the system operations of a single use case, mirroring the SSDs from `teoria/05-DiagrammaDiSequenzaDiSistema.pdf`.

When you move to the `patterns` branch, you'll notice these tests are *missing* (only 10 tests survive on that branch). Treat that as another design pressure: **a refactor that drops behavioural test coverage is not a successful refactor**. The `patterns` branch documents this gap explicitly — restoring the tests is one of the recommended next steps.

---

## Project layout

```
src/main/java/catering/
  businesslogic/
    CatERing.java                     ← manual Singleton facade (with setters — see pressure #1)
    UseCaseLogicException.java
    menu/
      Menu, Section, MenuItem         ← domain + SQL mixed (see pressure #3)
      MenuManager                     ← use-case controller (with bare throws — see pressure #4)
      MenuEventReceiver               ← Observer interface (already in place)
    recipe/
      KitchenProcess (interface)      ← thin shared API; Recipe/Preparation are independent classes (see pressure #2)
      Recipe, Preparation             ← domain + SQL mixed
      RecipeManager
    event/, kitchen/, shift/, user/   ← supporting infrastructure
  persistence/
    PersistenceManager                ← static JDBC utility
    MenuPersistence                   ← Observer impl, calls model statics
    EventPersistence
    KitchenTaskPersistence
src/test/java/catering/
  businesslogic/menu/                 ← MenuTest + MenuManagerTest (with @Nested)
  businesslogic/event/EventTest.java
  businesslogic/kitchen/              ← KitchenTaskManagerTest, SummarySheetTest
```

---

## Conventions on this branch

- The course is in Italian; identifier vocabulary in code is anglicised (`Menu` for *Menù*, `Section` for *Sezione*, `Recipe` for *Ricetta*, etc.).
- `CatERing.getInstance()` is the entry point for any code that needs to use the system — there is no public constructor on `CatERing`.
- Domain classes hold their own SQL via static methods; the Observer (`MenuPersistence` and friends) calls those statics.
- All managers throw `UseCaseLogicException` (sometimes with a message, sometimes without) when a precondition fails.
- The build outputs to `target/`, the SQLite DB lives at `database/catering.db` (regenerated from `database/catering_init_sqlite.sql` when missing).

---

## After you've read `main`

Open the `patterns` branch and read its README. For every "design pressure" above, you'll find the corresponding pattern explanation, with `before` (this branch) and `after` (that branch) references. The five patterns covered there — *Singleton*, *Composite*, *Strategy*, *Observer*, *Pure Fabrication* — each correspond to one of the pressures listed in this document.

Good luck.
