# CAT & RING — design patterns and GRASP

A worked reference for the **Sviluppo delle Applicazioni Software** course at the University of Turin. The implementation covers the *Gestire menù* and *Gestire compiti cucina* use cases and applies five patterns from the curriculum: **Singleton**, **Composite**, **Strategy**, **Observer**, and **Pure Fabrication** (GRASP).

After reading the slides for a pattern, you can open one or two files here and see exactly how that pattern looks when it lives inside a running Java codebase.

This document explains, for each pattern: what problem it solves, why it's used here, where to read it, what it costs, the common mistake to avoid, and a short exercise.

---

## Quick start

```bash
mvn compile        # build
mvn test           # 52 / 52 tests pass
mvn exec:java      # runs catering.businesslogic.CatERing.main
```

Requires JDK 17+ and Maven. The SQLite database lives at `database/catering.db` and is regenerated from `database/catering_init_sqlite.sql` only when the file is absent. **If you change the schema, delete the `.db` file first** — there is no migration mechanism.

---

## Pattern 1 — Singleton

### The general idea
A class that should have *exactly one* instance for the lifetime of the program. Common cases: a registry, a configuration loader, a system facade. The pattern enforces uniqueness and provides a global access point.

### Why we use it here
`CatERing` is a *facade* (GRASP **Controller**, facade variant) that aggregates all the use-case controllers — `MenuManager`, `RecipeManager`, `EventManager`, etc. The rest of the code calls `CatERing.getInstance().getMenuManager()` to reach a manager. There must be only one `CatERing` because:
- The managers hold mutable state (the "current menu" being edited).
- The Strategy persistence wiring is built **once** in the constructor; building it twice would create parallel persistence chains pointing at the same SQLite file.

### Where to read it
[`src/main/java/catering/businesslogic/CatERing.java`](src/main/java/catering/businesslogic/CatERing.java) — about 70 lines. The shape is:

```java
public class CatERing {
    private static CatERing instance = null;

    public static CatERing getInstance() {
        if (instance == null) {
            instance = new CatERing();
        }
        return instance;
    }

    private CatERing() { ... }   // private — no one else can construct
}
```

This is the **lazy null-check** variant taught in `teoria/13-GoF.pdf` and shown in [`teoria/JavaGoF/.../singleton/static_method/PrinterSpooler_b.java`](../../teoria/JavaGoF/src/creazionali/singleton/static_method/PrinterSpooler_b.java). Compare line by line — the shape is identical.

### Trade-offs
- ✅ Guaranteed uniqueness across the runtime.
- ✅ Lazy: instance is created on first access, not at class load.
- ⚠️ **Hidden dependency**: every call site that uses `CatERing.getInstance()` is implicitly coupled to the singleton. You can't substitute it for a test double without machinery.
- ⚠️ **Not thread-safe by default**. The course skips this on purpose; for single-threaded test runs it's fine. Production code would need a `synchronized` `getInstance()`, an `enum` Singleton, or the holder-class idiom.

### Common mistake
Adding `synchronized` to `getInstance()` because "it might be called from multiple threads". For this codebase, that's premature pessimization — the slide reference doesn't, and uncalled synchronization is dead code that misleads readers about the concurrency model. **Match the slide.** If the class actually became multi-threaded, the right fix is to *change* the variant, not to bolt synchronization onto the lazy variant.

### Try this
The constructor is private. How would you write a unit test that needs a fresh `CatERing` instance for isolation? Why is that hard? *(Hint: every test that uses `CatERing.getInstance()` shares state with every other test that does — see how `MenuTest.java` and `SummarySheetTest.java` interact.)*

---

## Pattern 2 — Composite

### The general idea
You want to treat individual objects and *groups* of those objects uniformly. The classic example is a file system: a folder contains files and other folders; you can ask either for its size, name, etc., without caring whether it's a leaf or a node. The pattern: define a single abstract type (the **Component**) that both leaves and composites extend.

### Why we use it here
A `Recipe` is a sequence of `Preparation`s — the course's "Mansione di cucina" hierarchy. When the kitchen-task scheduler walks all the work that needs doing for a menu, it doesn't want to write `if (item instanceof Recipe) { ... } else if (item instanceof Preparation) { ... }` everywhere. Both should answer the same query: *"give me your name, your description, your children if you have any"*.

### Where to read it
- [`KitchenProcessComponent.java`](src/main/java/catering/businesslogic/recipe/KitchenProcessComponent.java) — the **abstract root**. Declares `add`, `remove`, `getChildren` as `abstract`, plus shared state (`id`, `name`, `description`).
- [`Recipe.java`](src/main/java/catering/businesslogic/recipe/Recipe.java) — the **composite node**. Holds a `List<KitchenProcessComponent> children`; `add` / `remove` mutate the list.
- [`Preparation.java`](src/main/java/catering/businesslogic/recipe/Preparation.java) — the **leaf**. Throws `KitchenProcessException` from `add` / `remove`; `getChildren()` returns `Collections.emptyList()`.
- [`KitchenProcessException.java`](src/main/java/catering/businesslogic/recipe/KitchenProcessException.java) — the checked exception leaves throw.

This is the **transparent variant** of Composite (the abstract root declares the structural operations on the API; leaves throw at runtime instead of structurally rejecting at compile time). The course's reference is [`teoria/JavaGoF/.../composite/structure_abstractclass/Component.java`](../../teoria/JavaGoF/src/strutturali/composite/structure_abstractclass/Component.java) — same shape.

### Trade-offs
- ✅ Caller code can iterate, query, compute over the tree without type checks.
- ✅ Adding new node types extends the hierarchy without touching callers.
- ⚠️ **Leaves carry vestigial methods**: `Preparation.add()` exists only to throw. The transparent variant moves the safety check from compile-time to runtime. The course's "safety variant" puts structural ops on a separate interface — read both and decide which you'd prefer.
- ⚠️ **The symmetric API tempts misuse**. Code like `recipe.getChildren().get(0).add(prep)` works on a Recipe child but blows up on a Preparation child.

### Common mistake
**Sneaking a boolean type tag back in.** After the refactor, `Recipe` and `Preparation` look symmetric — but the temptation is to write `kp.isRecipe()` and switch on it. That's a textbook **GRASP Polymorphism** violation — exactly what the pattern was meant to eliminate. The codebase has *one* surviving instance of this anti-pattern in `KitchenTask.boolean type` (used as a persistence discriminator) — a known limitation kept because removing it requires a schema change. **Don't replicate it elsewhere.**

### Try this
Why is `Recipe.add()` declared without `throws`, but `KitchenProcessComponent.add()` is `abstract void add(...) throws KitchenProcessException;`? *(Hint: Java allows a subclass to override a method without re-declaring the parent's checked exception — the subtype's contract is narrower than the parent's. But a caller holding a `KitchenProcessComponent` reference must still handle the parent's `throws`.)*

---

## Pattern 3 — Strategy

This is the most important pattern in this codebase. It restructures the whole persistence layer.

### The general idea
You want to **swap an algorithm at runtime** without the caller knowing which one is in use. Define an interface for the algorithm; pass an implementation in at construction time; the caller (the **context**) delegates to it.

### Why we use it here
A naïve persistence design tangles SQL into the domain classes themselves — a `Menu.save()` method that opens a connection, runs `INSERT INTO Menus ...`, etc. That coupling makes the domain class:
- Hard to unit-test without a database.
- Tied to a specific storage technology (try swapping SQLite for an in-memory store).
- Hard to evolve — each domain class statically knows its own SQL strings.

The Strategy pattern lifts persistence out of the domain. The domain class becomes pure (state and behaviour); a separate persister class holds the SQL. The use-case controller (`MenuManager`) is the **context** — it holds a reference to the persister via its constructor and delegates to it.

### Where to read it

The Strategy *interfaces* (no SQL, just shape):
- [`EntityPersister<T>`](src/main/java/catering/persistence/strategy/EntityPersister.java) — generic root: `update`, `load`, `loadAll`, `delete`.
- [`MenuPersister`](src/main/java/catering/persistence/strategy/MenuPersister.java) — adds `insert(Menu)`, `updateTitle`, `updatePublished`, `updateFeatures`.
- Same for [`SectionPersister`](src/main/java/catering/persistence/strategy/SectionPersister.java), [`MenuItemPersister`](src/main/java/catering/persistence/strategy/MenuItemPersister.java), [`RecipePersister`](src/main/java/catering/persistence/strategy/RecipePersister.java), [`PreparationPersister`](src/main/java/catering/persistence/strategy/PreparationPersister.java).

The *concrete strategies* (where the SQL lives):
- [`SQLiteMenuPersister`](src/main/java/catering/persistence/strategy/impl/SQLiteMenuPersister.java)
- [`SQLiteSectionPersister`](src/main/java/catering/persistence/strategy/impl/SQLiteSectionPersister.java)
- [`SQLiteMenuItemPersister`](src/main/java/catering/persistence/strategy/impl/SQLiteMenuItemPersister.java)
- [`SQLiteRecipePersister`](src/main/java/catering/persistence/strategy/impl/SQLiteRecipePersister.java)
- [`SQLitePreparationPersister`](src/main/java/catering/persistence/strategy/impl/SQLitePreparationPersister.java)

The *composition root* — where they're built and wired together — is in `CatERing.java`:
```java
PreparationPersister preparationPersister = new SQLitePreparationPersister();
RecipePersister recipePersister = new SQLiteRecipePersister(preparationPersister);
MenuItemPersister menuItemPersister = new SQLiteMenuItemPersister(recipePersister);
SectionPersister sectionPersister = new SQLiteSectionPersister(menuItemPersister);
MenuPersister menuPersister = new SQLiteMenuPersister(sectionPersister, menuItemPersister);

menuMgr = new MenuManager(menuPersister);
recipeMgr = new RecipeManager(recipePersister, preparationPersister);
```
**Note the order**: leaves first (Preparation has no children), then composites. Persisters that load children take the child persister as a constructor argument. Reading top-to-bottom matches the dependency graph.

The *context* (where the strategy is used):
- [`MenuManager`](src/main/java/catering/businesslogic/menu/MenuManager.java) holds `private final MenuPersister menuPersister;` and only calls `menuPersister.load(...)` — never SQL directly.
- [`MenuPersistence`](src/main/java/catering/persistence/MenuPersistence.java) is the Observer that turns events into persister calls (see Pattern 4).

The course's reference: [`teoria/JavaGoF/.../strategy/myarray/`](../../teoria/JavaGoF/src/comportamentali/strategy/myarray/). Same shape — `MyArray` (context) holds an `ArrayDisplayFormat` (strategy interface) field, set via `setDisplayFormat()`. Read both side by side.

### Trade-offs
- ✅ **Domain classes are pure** — open `Menu.java`, look for `import catering.persistence` — there isn't one.
- ✅ **The persistence backend is swappable** — write `InMemoryMenuPersister` for tests, or `MongoMenuPersister` for a different deployment, no domain code changes.
- ✅ **Testing is easier** — provide a mock persister, no database needed for unit tests.
- ⚠️ **More files**: every domain class with persistence gets a `*Persister` interface and an `Sqlite*Persister` impl. The codebase grows.
- ⚠️ **Wiring is centralised** — `CatERing` becomes the composition root; constructor parameter lists grow. The trade-off: locality of cause. If something is wired wrong, exactly one file is at fault.

### Common mistake
**Service Locator instead of Strategy.** A tempting shortcut is to put the strategy on a static field:
```java
public class Menu {
    private static MenuPersister persister;          // ← wrong
    public static void setPersister(MenuPersister p) { ... }
}
```
This looks like Strategy from a distance — there's an interface, there's a concrete impl — but it's *Service Locator*, not Strategy. Why?
- The strategy is **global**, not per-instance — every test mutates shared state.
- The domain class has a **direct dependency** on the persister field, even if it's typed to an interface.
- There's **no clear composition** — the persister is "set somewhere", not "passed in".

The cure: **constructor injection**. The persister lives on the *context* (`MenuManager`), not the domain class (`Menu`). The domain class is pure data + behaviour.

The slide reference, `MyArray`, does this correctly: `private ArrayDisplayFormat format;` as an instance field, set via `setDisplayFormat(...)` or constructor.

### Try this
1. Trace a `MenuManager.loadMenu(7)` call all the way down to a `SELECT * FROM Menus WHERE id = ?` query. How many objects collaborate? How many of them know about SQL?
2. Write an `InMemoryMenuPersister` (15-20 lines, just a `HashMap<Integer, Menu>`) and substitute it in `CatERing`. What changes in `MenuManager`? In `Menu`? *(Spoiler: nothing in either of them. That's the point.)*

---

## Pattern 4 — Observer

### The general idea
An object (the **subject**) needs to notify other objects (the **observers**) when something changes — but it shouldn't know who they are. Define an event interface; let observers register; the subject calls the interface, not specific implementations.

### Why we use it here
When `MenuManager.createMenu(...)` succeeds, several things must happen:
- The new `Menu` must be persisted.
- In a fuller system: a UI must refresh, an audit log must be written, an analytics event must fire.

Hard-coding all those calls into `createMenu` couples the manager to every consumer of the event. The Observer pattern inverts the dependency: `MenuManager` only knows about a `MenuEventReceiver` interface and a list of registered receivers; consumers implement the interface and register themselves.

### Where to read it
- [`MenuEventReceiver.java`](src/main/java/catering/businesslogic/menu/MenuEventReceiver.java) — the **observer interface**. 13 methods, one per event type the manager emits.
- [`MenuManager.java`](src/main/java/catering/businesslogic/menu/MenuManager.java) — the **subject**. Holds `ArrayList<MenuEventReceiver> eventReceivers`, calls `notifyXxx()` after each operation.
- [`MenuPersistence.java`](src/main/java/catering/persistence/MenuPersistence.java) — a **concrete observer**. Implements all 13 callbacks by routing them to the appropriate persister.

The class-level JavaDoc on `MenuPersistence` explicitly names both Observer (this pattern) and Strategy (the persisters it holds) — the only file in the codebase that documents the intersection of two patterns.

### Trade-offs
- ✅ **Loose coupling**: `MenuManager` doesn't know what happens after a notification.
- ✅ **Multiple observers**: the same event can drive persistence, logging, UI updates simultaneously, all wired in `CatERing` and invisible to the manager.
- ⚠️ **Many methods on the interface**: 13 here. Adding a new event type requires touching the interface, every implementor, and every notification site.
- ⚠️ **Hidden execution order**: if observer registration order matters, the architecture is fragile. Currently no observer here depends on another observer's effect, so this isn't a problem — but it's worth being conscious of.

### Common mistake
Calling persistence directly from the manager because "it's simpler". For a one-receiver system, that's true. The Observer pattern earns its complexity when:
- The receiver may not exist (during testing — no observer registered, manager still works).
- Multiple receivers want the same events.
- The receiver is in a different module than the subject.

### Try this
1. In `CatERing.java`, look at the wiring line: `menuMgr.addEventReceiver(menuPersistence);`. What happens if you delete it? Does `MenuManager.createMenu` still work? What stops working?
2. Write a `LoggingMenuObserver implements MenuEventReceiver` that just `LOGGER.info`s every event. Register it in `CatERing` alongside `MenuPersistence`. Run `mvn exec:java`. Verify both observers fire. **No code in `MenuManager` changes.**

---

## Pattern 5 — Pure Fabrication (GRASP)

### The general idea
You have a responsibility that doesn't naturally belong to any domain class. The **Information Expert** (GRASP) pattern says *"give the responsibility to the class that has the data"* — but sometimes there's no good class. **Pure Fabrication** says: invent one. Create a class with no domain meaning whose only job is to hold the responsibility.

### Why we use it here
Every use-case controller (`MenuManager`, `KitchenTaskManager`) needs to validate preconditions before mutating state. The contract slides (`teoria/06-Contratti.pdf`) define these preconditions clause by clause: *"actor is Chef"*, *"menu is in definition"*, *"section belongs to menu"*, etc.

Without a shared helper, every manager duplicates the checks inline (`if (!user.isChef()) throw new UseCaseLogicException();`), each manager invents its own ad-hoc strings, and the contract layer becomes invisible from the code. None of `Menu`, `User`, `Section` is the natural Information Expert for *"validate the preconditions of a use case"* — the expert is the *contract*, and contracts have no class. So we **invent one**: `Preconditions`.

### Where to read it
[`Preconditions.java`](src/main/java/catering/businesslogic/Preconditions.java) — about 75 lines. One static method per recurring precondition clause:

```java
public static void requireChef(User user) throws UseCaseLogicException {
    if (user == null || !user.isChef()) {
        throw new UseCaseLogicException("precondition: actor must be a Chef");
    }
}

public static void requireCurrentMenu(Menu currentMenu)         throws UseCaseLogicException { ... }
public static void requireSectionInMenu(Menu, Section)          throws UseCaseLogicException { ... }
public static void requireItemInMenu(Menu, MenuItem)            throws UseCaseLogicException { ... }
public static void requireCurrentSummarySheet(SummarySheet)     throws UseCaseLogicException { ... }
```

Every call site reads as the contract clause it enforces:
```java
public Menu createMenu(String title) throws UseCaseLogicException {
    User user = CatERing.getInstance().getUserManager().getCurrentUser();
    Preconditions.requireChef(user);                  // ← contract: actor is Chef
    Menu m = Menu.create(user, title);
    setCurrentMenu(m);
    notifyMenuCreated(m);
    return m;
}
```

Both `MenuManager` and `KitchenTaskManager` use it.

### Trade-offs
- ✅ **Single source of truth** for each precondition's wording and behaviour.
- ✅ **Self-documenting at the call site** — `requireChef(user)` reads as the slide.
- ✅ **Reusable across managers** — adding a new manager that needs the chef check is a one-liner.
- ⚠️ **Indirection**: a reader has to open `Preconditions` to see the actual logic. Small but real.
- ⚠️ **It's a utility class** — all-static, no state. Some style guides discourage these in favour of injectable instances. For preconditions, the lack of state means a static helper is a reasonable choice.

### Common mistake
**Over-fabricating.** Pure Fabrication is for *responsibilities that no domain class naturally owns*. If the responsibility *does* fit a domain class, putting it on a fabrication is itself a violation of Information Expert. Don't invent `MenuValidator` to do work that `Menu` could do (e.g., *"is this menu publishable?"* — that's `Menu.canBePublished()`, not a fabrication).

### Try this
Open `KitchenTaskManager.generateSummarySheet`. Five preconditions are checked there. Three are domain-specific (event contains service, user is the event's chef, service has a menu). Should those go in `Preconditions` too? Why or why not?

---

## How the patterns compose

Read this section after you've understood the five patterns individually.

The **Gestire menù** use case has system operations defined in `teoria/05-DiagrammaDiSequenzaDiSistema.pdf`: `createMenu`, `defineSection`, `insertItem`, etc. Trace `createMenu(title)` through every layer:

```
test or main entry point
        │
        ▼
CatERing.getInstance()              ← Singleton — sole entry to the runtime
        │
        ▼
.getMenuManager()                   ← facade Controller (GRASP) accessor
        │
        ▼
MenuManager.createMenu(title)       ← use-case Controller (GRASP)
        │
        ├─ Preconditions.requireChef(user)        ← Pure Fabrication — contract enforcement
        │
        ├─ Menu.create(user, title)               ← pure domain — no SQL anywhere
        │
        ├─ setCurrentMenu(m)                      ← controller state
        │
        └─ notifyMenuCreated(m)                   ← Subject notifies all observers
                │
                ▼
        for each MenuEventReceiver er:            ← Observer
            er.updateMenuCreated(m)
                │
                ▼  (one of the registered observers is MenuPersistence)
        MenuPersistence.updateMenuCreated(m)
                │
                ▼
        menuPersister.insert(m)                   ← Strategy delegation
                │
                ▼
        SQLiteMenuPersister.insert(m)             ← concrete Strategy — SQL lives here
                │
                ├─ insertMenuBasicInfo            (executeInsert returns generated id)
                ├─ updateFeatures                 (delete + batch insert features)
                └─ insertSectionsAndItems         (composes SectionPersister, MenuItemPersister)
```

**Five patterns, one operation.** Each one earns its presence by absorbing one specific kind of change:
- **Singleton**: only one `CatERing`, no parallel runtimes.
- **Composite**: traversal code (the kitchen-task scheduler) treats Recipe and Preparation uniformly.
- **Strategy**: the persistence backend is swappable; domain classes are testable without SQLite.
- **Observer**: future receivers (audit log, UI refresh, metrics) plug in without touching `MenuManager`.
- **Pure Fabrication**: precondition vocabulary is shared across managers; contract clauses are visible at call sites.

The same shape applies to every other manager method. **Read three or four to convince yourself.** If you can write down each of the five patterns' contribution to one operation, you understand the architecture.

---

## GRASP principles, where to see them

| Principle | Where it shows up |
|---|---|
| **Information Expert** | `Menu.addSection`, `Menu.addItem`, `Section.addItem` — Menu owns its sections; assigning new sections to it is Menu's job, not the Manager's. |
| **Creator** | `Menu.create(user, title)` — Menu's static factory; it has the data needed to construct one. |
| **Controller** (facade) | `CatERing` — single entry point for system operations. |
| **Controller** (use-case) | `MenuManager`, `RecipeManager`, `KitchenTaskManager` — one controller per use case. |
| **Low Coupling** | Domain classes know nothing about persistence; managers know nothing about which `*Persister` impl they hold. |
| **High Cohesion** | Each persister handles one entity. Each manager handles one use case. |
| **Polymorphism** | Composite `Recipe.add` vs `Preparation.add` — same call, different behaviour, no `instanceof`. |
| **Pure Fabrication** | `Preconditions`, `SQLitePersistenceManager`. |
| **Indirection** | `EntityPersister<T>` — domain code depends on the abstraction, not on JDBC concrete. |
| **Protected Variations** | The `MenuPersister` interface protects `MenuManager` from changes in storage technology. |

---

## Project layout

```
src/main/java/catering/
  businesslogic/
    CatERing.java                     ← Singleton facade + Strategy composition root
    Preconditions.java                ← Pure Fabrication — contract enforcement
    UseCaseLogicException.java        ← domain-level checked exception
    menu/
      Menu, Section, MenuItem         ← pure domain
      MenuManager                     ← use-case controller (Strategy context)
      MenuEventReceiver               ← Observer interface
    recipe/
      KitchenProcessComponent         ← Composite root (abstract class)
      Recipe (composite), Preparation (leaf)
      KitchenProcessException         ← checked, thrown by leaf
      RecipeManager                   ← use-case controller, holds RecipePersister + PreparationPersister
    event/, kitchen/, shift/, user/   ← supporting infrastructure
  persistence/
    SQLitePersistenceManager          ← Pure Fabrication: low-level JDBC utility
    MenuPersistence                   ← Observer impl, holds 3 menu-side persisters
    KitchenTaskPersistence            ← Observer impl for kitchen tasks
    strategy/
      EntityPersister<T>              ← Strategy interface root
      Menu/Section/MenuItem/Recipe/PreparationPersister.java
      impl/
        SQLite*Persister.java         ← concrete Strategy implementations
src/test/java/catering/
  businesslogic/menu/
    MenuTest.java                   ← domain unit tests for the Menu aggregate
    MenuManagerTest.java            ← system-operation tests (one @Nested per DSD operation)
  businesslogic/event/EventTest.java          ← Event aggregate + DB loader tests
  businesslogic/kitchen/
    KitchenTaskManagerTest.java     ← system-operation tests for "Gestire compiti cucina"
    SummarySheetTest.java           ← integration test for the generate-and-assign flow
  persistence/strategy/impl/SQLiteMenuPersisterTest.java ← Strategy CRUD round-trips
```

---

## Conventions

- The course is in Italian; identifier vocabulary in code is anglicised (`Menu` for *Menù*, `Section` for *Sezione*, `Recipe` for *Ricetta*, etc.).
- **Domain classes are pure**: state and behaviour, never JDBC. Persistence lives in `persistence/`.
- **Manager constructors take their persister(s) by interface type.** No service locator, no static persister field on a domain class.
- **`CatERing` is the only place where concrete `SQLite*Persister` types appear in production code.** All other code depends on the `*Persister` interfaces only.
- **All preconditions go through `Preconditions`.** No bare `throw new UseCaseLogicException()` calls anywhere — `grep` confirms.
