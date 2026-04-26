# CAT & RING — `patterns` branch

Reference implementation for **Sviluppo e Architettura del Software t1** (Software Development and Architecture, University of Turin).
This branch is a worked example of how to apply the GoF and GRASP patterns covered in `teoria/` to the **Gestire menù** + **Gestire compiti cucina** use cases.
The `main` branch is the pre-refactor baseline; `patterns` is what `main` looks like after the patterns are applied.

## What this branch demonstrates

| Pattern | Where to read it | Reference in `teoria/JavaGoF/src/` |
|---|---|---|
| **Singleton** | `businesslogic/CatERing.java` | `creazionali/singleton/static_method/PrinterSpooler_b.java` |
| **Composite** | `businesslogic/recipe/{KitchenProcessComponent, Recipe, Preparation, KitchenProcessException}.java` | `strutturali/composite/structure_abstractclass/{Component, CompoundPart, SinglePart}.java` |
| **Strategy** (persistence) | `persistence/strategy/{EntityPersister, MenuPersister, SectionPersister, MenuItemPersister, RecipePersister, PreparationPersister}.java` and `persistence/strategy/impl/SQLite*Persister.java` | `comportamentali/strategy/myarray/{ArrayDisplayFormat, MyArray, StandardFormat, MathFormat}.java` |
| **Observer** | `businesslogic/menu/MenuEventReceiver.java`, `persistence/MenuPersistence.java` | (handwritten — not in `JavaGoF/`) |

## Suggested reading path

A student opening this branch for the first time should follow the wiring outwards from one entry point:

1. **`businesslogic/CatERing.java`** — the **Singleton facade controller** (GRASP). The constructor is the **Strategy composition root**: it builds the persisters bottom-up (`PreparationPersister` → `RecipePersister` → `MenuItemPersister` → `SectionPersister` → `MenuPersister`) and injects them into the use-case controllers (`MenuManager`, `RecipeManager`) and the Observer (`MenuPersistence`).
2. **`businesslogic/menu/MenuManager.java`** — the **use-case controller** for *Gestire menù*. Holds a `MenuPersister` field via constructor injection (Strategy *context*). Notifies registered `MenuEventReceiver` observers after every state change.
3. **`persistence/MenuPersistence.java`** — the **Observer** that persists `MenuManager` events. Holds the three menu-side persisters and routes each notification to the right one. The class JavaDoc names both patterns explicitly.
4. **`persistence/strategy/EntityPersister.java`** — the **Strategy interface** root, parameterised by entity type. Each concrete entity (Menu, Section, MenuItem, Recipe, Preparation) extends it with its own specific operations.
5. **`persistence/strategy/impl/SQLiteMenuPersister.java`** — a **concrete Strategy**. Composes its sub-persisters via constructor injection, never reaches into model classes for SQL.
6. **`businesslogic/recipe/KitchenProcessComponent.java`** — the **Composite** root (single abstract class), with `Recipe` (composite node) and `Preparation` (leaf, throws `KitchenProcessException` from `add`/`remove`).

## Build and run

Requires JDK 17+ and Maven. From this directory:

```bash
mvn compile        # build
mvn test           # run all tests (10 pass on a clean checkout)
mvn exec:java      # runs catering.businesslogic.CatERing.main
```

The SQLite database is regenerated from `database/catering_init_sqlite.sql` if `database/catering.db` is absent. **Schema changes do not take effect on subsequent runs unless you delete `database/catering.db` first** — there is no migration mechanism.

## Architecture story (one use case end-to-end)

`teoria/06-Contratti.pdf` describes operation contracts for *Gestire menù*. Trace one — `createMenu` — through the code:

```
[Slide contract: precondition: actor is Chef; postcondition: menu m created, m.titolo set, chef owns m, m.pubblicato = no]

User → MenuManager.createMenu(title)         ← use-case controller (GRASP)
       ├─ checks user.isChef()              ← precondition enforcement
       ├─ Menu.create(user, title)          ← factory, returns pure domain object
       ├─ setCurrentMenu(m)                 ← controller state
       └─ notifyMenuCreated(m)              ← Observer notification
              ↓
       MenuPersistence.updateMenuCreated(m) ← Observer
              ↓
       menuPersister.insert(m)              ← Strategy delegation
              ↓
       SQLiteMenuPersister.insert(m)        ← concrete Strategy: SQL lives here
              ├─ insertMenuBasicInfo
              ├─ updateFeatures
              └─ insertSectionsAndItems     ← composes SectionPersister + MenuItemPersister
```

The same shape applies for `defineSection`, `insertItem`, etc. — manager validates the contract, mutates the in-memory aggregate, fires the event; the Observer turns the event into persister calls.

## Layout

```
src/main/java/catering/
  businesslogic/
    CatERing.java                          — Singleton facade + Strategy composition root
    UseCaseLogicException.java             — domain-level checked exception
    menu/
      Menu, Section, MenuItem              — pure domain
      MenuManager                          — use-case controller (Strategy context)
      MenuEventReceiver                    — Observer interface
    recipe/
      KitchenProcessComponent              — Composite root (abstract class)
      Recipe (composite), Preparation (leaf)
      KitchenProcessException              — checked, thrown by leaf
      RecipeManager                        — use-case controller, holds RecipePersister + PreparationPersister
    event/, kitchen/, shift/, user/        — supporting infrastructure
  persistence/
    SQLitePersistenceManager               — Pure Fabrication: low-level JDBC utility
    MenuPersistence                        — Observer impl, holds 3 menu-side persisters
    KitchenTaskPersistence                 — Observer impl for kitchen tasks
    strategy/
      EntityPersister<T>                   — Strategy interface root
      Menu/Section/MenuItem/Recipe/PreparationPersister.java
      impl/
        SQLite*Persister.java              — concrete Strategy implementations
src/test/java/catering/
  businesslogic/menu/MenuTest.java         — Menu unit test
  businesslogic/kitchen/SummarySheetTest.java — integration test (live SQLite)
  persistence/strategy/impl/SQLiteMenuPersisterTest.java — persistence test
```

## Companion documents

- `REVIEW-patterns.md` — catalogue of GoF/GRASP conformance findings (with severities and fix shapes).
- `IMPROVEMENTS.md` — forward-looking opportunities beyond what this branch demonstrates (Repository, domain events, in-memory test DB, etc.).
- `STUDENT-READINESS.md` — assessment of whether the branch is ready to hand to students; lists the framing gaps and recommended pre-handoff fixes.
- `CODEX-REVIEW.md` (untracked, present locally) — independent second-opinion review.

## Conventions

- The course is in Italian; identifier vocabulary in code is anglicised (`Menu` for *Menù*, `Section` for *Sezione*, `Recipe` for *Ricetta*, etc.) — this is the convention `exam-artifacts.md` accepts.
- Domain classes are pure: they hold state and offer behaviour, but they never open a JDBC connection. All persistence lives in `persistence/`.
- Manager constructors take their persister(s) by interface type. There is no service locator and no static persister field on a domain class.
- The `CatERing` singleton is the only place where concrete `SQLite*Persister` types appear in production code. All other code depends on the `*Persister` interfaces only.
