# CAT & RING — `patterns` branch
## A worked tutorial in design patterns and GRASP

This branch is a **teaching artifact**: a small but realistic Java backend that has been refactored to apply the canonical patterns the course covers. After reading the slides for a pattern, you can open one or two files here and see exactly how that pattern looks when it lives inside running code.

The companion `main` branch is the same project *before* the refactor — useful for the "diff" view: open both side by side and you can see the exact moves that turn an ad-hoc layered application into a pattern-driven one.

This document explains, for each pattern:
1. **What problem the pattern solves** in general terms.
2. **The specific symptom in this codebase** that motivated applying it.
3. **Where to read the code**.
4. **Trade-offs** — every pattern costs something.
5. **Common mistakes** students often make.
6. **A "try this"** prompt to test your understanding.

Read the patterns in the order presented. **Singleton** is the simplest; **Composite**, **Strategy**, **Observer**, and **Pure Fabrication** build progressively, and the last section shows how they all compose for one full use case.

---

## Quick start

```bash
mvn compile        # build
mvn test           # 10 tests pass on a clean checkout
mvn exec:java      # runs catering.businesslogic.CatERing.main
```

Requires JDK 17+ and Maven. The SQLite database lives at `database/catering.db` and is regenerated from `database/catering_init_sqlite.sql` only when absent. **If you change the schema, delete the `.db` file first** — there is no migration mechanism.

---

## Pattern 1 — Singleton

### The general idea
A class that should have *exactly one* instance for the lifetime of the program. Common cases: a registry, a configuration loader, a system facade. The pattern enforces uniqueness and provides a global access point.

### Why we use it here
`CatERing` is a *facade* (GRASP **Controller**, facade variant) that aggregates all the use-case controllers — `MenuManager`, `RecipeManager`, `EventManager`, etc. The rest of the code calls `CatERing.getInstance().getMenuManager()` to reach a manager. There must be only one `CatERing` because:
- The managers hold mutable state (the "current menu" being edited).
- The Strategy persistence wiring is built **once** in the constructor; building it twice would create parallel persistence chains pointing at the same SQLite file — recipe for race conditions and confused state.

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
- ✅ Guaranteed uniqueness across the whole runtime.
- ✅ Lazy: instance is created on first access, not at class load.
- ⚠️ **Hidden dependency**: every call site that uses `CatERing.getInstance()` is implicitly coupled to the singleton. You can't substitute it for a test double without machinery (resetting a static field, or a more elaborate test harness).
- ⚠️ **Not thread-safe by default**. The course skips this on purpose; for single-threaded test runs it's fine. Production code would need either a `synchronized` `getInstance()`, an `enum` Singleton, or holder-class idiom.

### Common mistake
Adding `synchronized` to `getInstance()` because "it might be called from multiple threads". For this codebase, that's premature pessimization — the slide reference doesn't, and uncalled synchronization is dead code that misleads readers about the concurrency model. **Match the slide.** If the class actually became multi-threaded, the right fix is to *change* the variant, not to bolt synchronization onto the lazy variant.

### Try this
1. The constructor is private. How would you write a unit test that needs a fresh `CatERing` instance for isolation? Why is that hard? *(Hint: every test that uses `CatERing.getInstance()` shares state with every other test that does — see how `MenuTest.java` and `SummarySheetTest.java` interact.)*
2. The `main` branch has a different `CatERing` shape — go look at it and identify what changed.

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
1. Why is `Recipe.add()` declared without `throws`, but `KitchenProcessComponent.add()` is `abstract void add(...) throws KitchenProcessException;`? *(Hint: Java allows a subclass to override a method without re-declaring the parent's checked exception — the subtype's contract is narrower than the parent's. But a caller holding a `KitchenProcessComponent` reference must still handle the parent's `throws`.)*
2. The professor also covers a **safety variant** of Composite, where structural ops are not on the root. Sketch how `Recipe` and `Preparation` would look in that variant. Which is preferable here, and why?

---

## Pattern 3 — Strategy

This is the most important pattern on this branch. The Recipe/Preparation Composite is a small detail; the Strategy refactor restructures the whole persistence layer.

### The general idea
You want to **swap an algorithm at runtime** without the caller knowing which one is in use. Define an interface for the algorithm; pass an implementation in at construction time; the caller (the **context**) delegates to it.

### Why we use it here

In the `main` branch, `Menu.java` is full of code like:
```java
public boolean save() {
    String query = "INSERT INTO Menus (title, owner_id, ...) VALUES ...";
    SQLitePersistenceManager.executeUpdate(query, ...);
    ...
}
```
Persistence logic is tangled into the domain class. Every domain class has its own SQL, its own column names. Changing the database backend (or just unit-testing the domain logic without a database) means rewriting every model class.

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

The course's reference: [`teoria/JavaGoF/.../strategy/myarray/`](../../teoria/JavaGoF/src/comportamentali/strategy/myarray/). Same shape — `MyArray` (context) holds a `ArrayDisplayFormat` (strategy interface) field, set via `setDisplayFormat()`. Read both side by side.

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
An earlier version of this branch did exactly that. It looks like Strategy from a distance — there's an interface, there's a concrete impl — but it's *Service Locator*, not Strategy. Why?
- The strategy is **global**, not per-instance — every test mutates shared state.
- The domain class has a **direct dependency** on the persister field, even if it's typed to an interface.
- There's **no clear composition** — the persister is "set somewhere", not "passed in".

The cure: **constructor injection**. The persister lives on the *context* (`MenuManager`), not the domain class (`Menu`). The domain class is pure data + behaviour.

The slide reference, `MyArray`, does this correctly: `private ArrayDisplayFormat format;` as an instance field, set via `setDisplayFormat(...)` or constructor.

### Try this
1. Trace a `MenuManager.loadMenu(7)` call all the way down to a `SELECT * FROM Menus WHERE id = ?` query. How many objects collaborate? How many of them know about SQL?
2. Write an `InMemoryMenuPersister` (15-20 lines, just a `HashMap<Integer, Menu>`) and substitute it in `CatERing`. What changes in `MenuManager`? In `Menu`? *(Spoiler: nothing in either of them. That's the point.)*
3. The course's `MyArray.setDisplayFormat()` allows the strategy to be **swapped at runtime**. `MenuManager` makes the persister `final` — no swapping. Why might that be the right choice here, and why might it be wrong?

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
- ⚠️ **Many methods on the interface**: 13 here. Adding a new event type requires touching the interface, every implementor, and every notification site. (Java 17+ sealed classes + pattern matching offer a more concise alternative shape, but require Java-17-only language features.)
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

In the `main` branch, every manager wrote these checks inline:
```java
if (!user.isChef()) throw new UseCaseLogicException();           // bare throw, no message
if (currentMenu == null) throw new UseCaseLogicException();
if (currentMenu.getSectionPosition(sec) < 0) throw new UseCaseLogicException();
```

Three problems:
1. **Duplication** — the chef check appears in 4+ different methods.
2. **No diagnostic** — `UseCaseLogicException()` with no message tells the caller nothing.
3. **No vocabulary** — every manager invents its own ad-hoc strings; the contract layer is invisible from the code.

These are cross-cutting concerns. None of `Menu`, `User`, `Section` is the natural Information Expert for *"validate the preconditions of a use case"*. The expert is the *contract*, and contracts have no class. So we **invent one**: `Preconditions`.

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

Both `MenuManager` and `KitchenTaskManager` use it. The contract layer is now visible from `grep "Preconditions\."` in one place.

### Trade-offs
- ✅ **Single source of truth** for each precondition's wording and behaviour.
- ✅ **Self-documenting at the call site** — `requireChef(user)` reads as the slide.
- ✅ **Reusable across managers** — adding a new manager that needs the chef check is a one-liner.
- ⚠️ **Indirection**: a reader has to open `Preconditions` to see the actual logic. Small but real.
- ⚠️ **It's a utility class** — all-static, no state. Some style guides discourage these in favour of injectable instances. For preconditions, the lack of state means a static helper is a reasonable choice.

### Common mistake
**Over-fabricating.** Pure Fabrication is for *responsibilities that no domain class naturally owns*. If the responsibility *does* fit a domain class, putting it on a fabrication is itself a violation of Information Expert. Don't invent `MenuValidator` to do work that `Menu` could do (e.g., *"is this menu publishable?"* — that's `Menu.canBePublished()`, not a fabrication).

### Try this
1. Open `KitchenTaskManager.generateSummarySheet`. Five preconditions are checked there. Three are domain-specific (event contains service, user is the event's chef, service has a menu). Should those go in `Preconditions` too? Why or why not?
2. The course also teaches **Indirection** (GRASP). How is `Preconditions` similar to and different from a pure Indirection? *(Hint: the goal is different — Indirection decouples two objects, Pure Fabrication invents a new home for an orphaned responsibility.)*

---

## How the patterns compose

Read this section after you've understood the five patterns individually. Here we look at how they wire together for one full use case.

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

**Five patterns, one operation.** None of them is *necessary* — you could write all of this without any of them. Each one earns its presence by absorbing one specific kind of change:
- **Singleton**: only one `CatERing`, no parallel runtimes.
- **Composite**: traversal code (the kitchen-task scheduler) treats Recipe and Preparation uniformly.
- **Strategy**: the persistence backend is swappable; domain classes are testable without SQLite.
- **Observer**: future receivers (audit log, UI refresh, metrics) plug in without touching `MenuManager`.
- **Pure Fabrication**: precondition vocabulary is shared across managers; contract clauses are visible at call sites.

The same shape applies to every other manager method: `defineSection`, `insertItem`, `addMenuFeatures`, etc. **Read three or four to convince yourself.** If you can write down each of the five patterns' contribution to one operation, you understand the architecture.

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

## What's still open (and why)

The branch is ready for the *patterns* curriculum, but it has acknowledged limitations that aren't lessons in pattern application — they're known design issues being held back for a future pass:

- **`KitchenTask.boolean type`** — a surviving boolean-tag-as-type-discriminator. This is the Polymorphism anti-pattern that the Composite refactor is meant to eliminate; one instance survives because removing it requires a schema change.
- **`SQLitePersistenceManager` swallows `SQLException`** — every database error becomes a silent `0` / `null`. **Don't model your error handling on this.**
- **`Menu.getSections()` and `Menu.getFreeItems()` return backing lists** — encapsulation leak. **Don't model your getters on this.**
- **`SummarySheetTest.testTaskAssignment` writes `shift_id = 0`** to the database via a public constructor on `Shift`. **Don't model your tests on this one.** The other test classes are fine.

Each of these is a productive question to engage with — *why is it wrong, what would the right fix look like* — rather than a lesson to copy.

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
  businesslogic/menu/MenuTest.java                       ← Menu unit test
  businesslogic/kitchen/SummarySheetTest.java            ← integration test (live SQLite)
  persistence/strategy/impl/SQLiteMenuPersisterTest.java ← persistence test
```

---

## Conventions

- The course is in Italian; identifier vocabulary in code is anglicised (`Menu` for *Menù*, `Section` for *Sezione*, `Recipe` for *Ricetta*, etc.) — accepted by `exam-artifacts.md`.
- **Domain classes are pure**: state and behaviour, never JDBC. Persistence lives in `persistence/`.
- **Manager constructors take their persister(s) by interface type.** No service locator, no static persister field on a domain class.
- **`CatERing` is the only place where concrete `SQLite*Persister` types appear in production code.** All other code depends on the `*Persister` interfaces only.
- **All preconditions go through `Preconditions`.** No bare `throw new UseCaseLogicException()` calls anywhere — `grep` confirms.
