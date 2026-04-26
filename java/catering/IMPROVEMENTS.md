# What could be done more — `patterns` branch

Companion to `REVIEW-patterns.md`. That document catalogs *what's wrong now*; this one looks forward — what would take the branch from "passes the exam" to "demonstrates real software-engineering judgement," organised by effort and by which part of the course material it satisfies.

Numbering is not severity; items are grouped by theme.

---

## 1. Finish the half-done refactors

These are the loose ends from the work already underway. Nothing exotic — just completing what was started.

### 1.1 Recipe / Preparation persistence Strategy split

`Recipe.java:33-206` and `Preparation.java:50-156` still hold ~250 lines of inline SQL. The Menu side was migrated; the Recipe side stopped. Until this is finished the refactoring is half-applied and the codebase is internally inconsistent.

What it costs: 4 new files (`RecipePersister`, `PreparationPersister`, plus SQLite impls), constructor changes on `RecipeManager`, one updated wiring line in `CatERing`, and the `Recipe.loadRecipe(...)` call inside `SQLiteMenuItemPersister:110` switches to a `RecipePersister` reference.

What it buys: the reference implementation becomes a coherent demonstration of GRASP **Pure Fabrication** + **Protected Variations** applied uniformly to the persistence boundary, instead of "Menu uses Strategy, Recipe doesn't." The student would be able to point at the codebase and say "every domain class is now persistence-agnostic." That is the deliverable.

### 1.2 Replace `KitchenTask.type` boolean with proper polymorphism

After the Composite fix, the `if (p.isRecipe()) throw` guard is gone, but the type discriminator survives in `KitchenTask.type` (`KitchenTask.java:24`) — a `boolean` whose true/false values mean Recipe-vs-Preparation. It is read at `:37, :85, :119-125, :163-165` to drive load-time subtype dispatch.

This is a textbook **type tag stored in a field** anti-pattern. The course's GRASP Polymorphism rule is meant precisely for cases like this. Two clean options:
- Keep one `kitchen_tasks` table with a `type` column, but at load time use a small factory: `String type = rs.getString("type"); KitchenProcessComponent kp = type.equals("RECIPE") ? recipePersister.load(id) : preparationPersister.load(id);`. The boolean field disappears from the in-memory model.
- Split into `recipe_tasks` and `preparation_tasks` tables. Each persister loads its own table; type is determined by which loader produced it.

Either is fine. The first is less work and avoids a schema migration.

### 1.3 Restore the deleted tests

`MenuManagerTest` (259 lines), `EventTest` (125 lines), `KitchenTaskManagerTest` (94 lines) are gone. The course's TDD slide (`teoria/14-CodiceTest.pdf`) is explicit: *tests must be re-run after every refactor*. Deleting tests because the API changed is the failure mode the slide warns against.

The right shape: pull each file from `main`, adapt the constructors to the new `MenuManager(MenuPersister)` signature and the removed static facades, and let any failures *expose actual regressions* rather than be papered over. The hardest will be `MenuManagerTest`, since it covers operations whose internal API has shifted; expect 30-60 minutes of careful adaptation per file.

---

## 2. Architectural opportunities (medium effort, big readability wins)

### 2.1 Repository pattern as evolution of Strategy persistence

Strategy is the right *first* step. The natural next step is **Repository** — a single object per aggregate root that exposes the domain-meaningful operations rather than CRUD: `MenuRepository.findById`, `MenuRepository.findByOwner`, `MenuRepository.save`. The current `MenuPersister` interface is already close: collapse `update`, `updateTitle`, `updatePublished`, `updateFeatures` into one `save(Menu)` and let the impl figure out the dirty fields. Tests stay simple (one mock per repository instead of three).

This is not in the course materials, but `teoria/06-Contratti.pdf`'s emphasis on contract-level operations naturally aligns with a Repository: the contracts say "creazione di un menu" — the persistence shape should match, not "insert row + insert features + insert sections" exposed as separate methods.

### 2.2 Domain events instead of `MenuEventReceiver`

`MenuEventReceiver` is a hand-rolled Observer with 13 typed methods. Adding a new event type requires touching the interface, every implementor, and every notification site. A simpler shape:

```java
abstract class MenuEvent { final Menu menu; ... }
class MenuCreated extends MenuEvent { ... }
class SectionAdded extends MenuEvent { final Section section; ... }
// etc.

interface MenuEventListener {
    void on(MenuEvent event);  // typically a switch / pattern match
}
```

Or, since Java 17+ has sealed classes and pattern matching, `sealed` `MenuEvent` with concrete subtypes makes the listener `switch (event)` exhaustive at compile time. This collapses 13 methods into 1, makes adding events trivial, and matches what production codebases look like. The trade-off is one level of indirection per notification — minor.

### 2.3 Atomicity at use-case boundaries

`MenuManager.publish()` updates the in-memory menu, then notifies listeners, which write to SQLite — but there's no transaction. If `updateMenuPublishedState` fails mid-write, the in-memory state is "published" while the DB row is not.

The minimum step is wrapping each manager method's notification phase in a `SQLitePersistenceManager.executeInTransaction(...)` helper. Adding such a helper (BEGIN / COMMIT / ROLLBACK around a `Runnable`) is ~20 lines and gives every use case atomicity without ceremony.

The course's `06-Contratti.pdf` postconditions are written as if they hold *after* the operation completes — atomicity is what makes that contract true under failure. Worth mentioning in the report even if not implemented.

### 2.4 Resource management

`SQLitePersistenceManager.executeQuery` / `executeUpdate` / `executeInsert` use plain `try / catch`, not `try-with-resources`. `Connection`, `PreparedStatement`, and `ResultSet` should all be in the resource list:

```java
try (Connection c = getConnection();
     PreparedStatement ps = c.prepareStatement(sql);
     ResultSet rs = ps.executeQuery()) { ... }
```

The current `finally { ... close(); }` blocks (around `:135-150`) suppress secondary exceptions and are easy to get wrong. This is one of those low-glamour fixes that makes the persistence layer 30% smaller and removes a class of bugs.

### 2.5 Stop swallowing `SQLException`

The single most damaging pattern in the persistence layer (REVIEW §3.1 H-items): catch `SQLException`, log it, return `0` / empty / null. The right shape is a `PersistenceException extends RuntimeException` that wraps the cause and propagates. Callers that genuinely want to handle DB errors can catch it; callers that don't get a clear stack trace instead of a phantom "no rows affected".

Combined with 2.4, this turns the persistence layer from an exception-swallowing maze into something the student can defend in an exam.

---

## 3. Test architecture

The current test suite is a mixture of:
- One unit test (`MenuTest`, 1 method)
- One integration test (`SummarySheetTest`, mislabeled as a unit test)
- One persistence test (`SQLiteMenuPersisterTest`, correctly scoped)

Three deleted test files for the most-touched managers. Treat the rebuild as a chance to fix the architecture, not just restore lines.

### 3.1 Two source roots: `src/test/unit/` and `src/test/integration/`

Or two Maven profiles. Unit tests must not touch SQLite. Integration tests do, and only run on `mvn verify`. This is a five-line `pom.xml` change. The point: a CI run takes 2 seconds for unit tests; integration runs separately and can be slow.

### 3.2 Test data builders

`SummarySheetTest:47-61` loads users by hard-coded names, an event by name, a service by name. Replace with builders:

```java
User chef = aUser().withName("Antonio").withRole(CHEF).build();
Event event = anEvent().withChef(chef).withService("Lunch").build();
```

A 60-line `TestData` class eliminates the seed-data dependency entirely and makes tests independent of `database/catering_init_sqlite.sql`. The course's testing slide (`14-CodiceTest.pdf`) shows tests with "Preparazione" — the builder *is* the Preparazione phase.

### 3.3 In-memory SQLite for integration tests

`jdbc:sqlite::memory:` gives each test a fresh DB. Combined with builders, every integration test becomes self-contained. The current `SQLiteMenuPersisterTest` would no longer need `@TestMethodOrder` to keep its inserts from colliding.

### 3.4 Fix the silent corruption test

`SummarySheetTest:133` constructs a `Shift` via `new Shift(...)` and calls `addBooking(cook)`, which writes `INSERT INTO ShiftBookings` with `shift_id = 0`. This is real data corruption disguised as a passing test. Either persist the shift first or use a test double for `Shift` that tracks bookings in memory.

### 3.5 `@DisplayName` everywhere, in Italian

If the audience is the professor and the codebase is bilingual, `@DisplayName("crea un menu solo se l'utente è chef")` reads better than `testCreateMenuFailsForNonChef`. The intent of `14-CodiceTest.pdf`'s "test as documentation" angle lands more clearly in the language of the course.

---

## 4. Code quality systems

### 4.1 SpotBugs / PMD on `pom.xml`

Adding the SpotBugs Maven plugin (`<plugin>` block, ~10 lines) would catch about 60% of the H-items in `REVIEW-patterns.md` automatically: NPE risks, null-on-error returns, error-swallowing catch blocks, mutable state in equals. Worth showing the report at the end of the course as evidence of the `14-CodiceTest.pdf` mantra "fail early."

### 4.2 JaCoCo for test coverage

A coverage report makes the gap from §1.3 visible and quantifiable. With the deleted tests restored, coverage probably climbs to 60-70%; without them, you're sitting around 25%. Showing this number in the exam delivery is strong evidence of TDD discipline.

### 4.3 Checkstyle to keep `final`, naming, ordering consistent

Examples already mismatched in this branch: `Menu.setIntInUse` (suggests `int`, takes `boolean`), `mgr` vs `Manager` abbreviation inconsistency, anonymous-class vs lambda inconsistency for `ResultHandler`. A 30-line `checkstyle.xml` with the Sun convention enforces these without manual review.

### 4.4 GitHub Actions CI

Even a minimal `mvn -B verify` job on push catches the obvious: refactor commits that break tests, formatting drift, missing files. The course materials don't require it; the difference between a green-CI student deliverable and a no-CI one is visible at a glance.

---

## 5. Documentation and diagrams

### 5.1 Update DCD/DSD to match the current code

`catering/umlet/dcdACnew.uxf` labels the Composite root as `«interface» KitchenProcess`; the code is now an abstract class `KitchenProcessComponent`. `catering/umlet/generateSummarySheet.uxf` shows a manager-driven loop; the code does it in the constructor (which is itself a contract-violation per REVIEW §2.1). Decide which one is canonical and reconcile.

The course's exam-artifacts.md is explicit that students submit *both* DCDs and code — a divergence between them is a defect both ways: either the code wandered, or the diagram wasn't updated. In a teaching repo, updating the diagram after every refactor is a habit worth modelling.

### 5.2 JavaDoc on every public manager method

`MenuManager.createMenu` has no JavaDoc. The course's contract slide (`teoria/06-Contratti.pdf`) shows preconditions and postconditions in plain language. The natural place for them in code is `/** @pre ..., @post ... */` as JavaDoc tags. The ~15 manager methods would each get 5 lines of doc that doubles as the operation contract.

### 5.3 A README in `src/main/java/catering/businesslogic/`

One paragraph per package: what it owns, what it depends on, what's stable vs. what's evolving. New readers (and the professor) shouldn't need to read the source to understand the architecture. `src/test/java/catering/businesslogic/README.md` already exists — extend the pattern to the production tree.

---

## 6. Apply more GRASP patterns where they fit

The branch focused on Composite + Strategy. The other GRASP/GoF patterns from the course are also applicable:

### 6.1 **Indirection** — `EventManager` directly calling `Menu.load`

REVIEW §2.2 noted that `EventManager.modifyService` (`:228`) calls `Menu.load(menuId)` (now `MenuManager.loadMenu`) directly. The fact that `EventManager` knows about `MenuManager`'s internals at all is a coupling that GRASP **Indirection** would solve: introduce a small `MenuLookupService` (1 method) that both managers depend on; neither knows the other. The student gets a worked example of *why* Indirection exists, not just the slide definition.

### 6.2 **Observer** — already used; document it

`MenuEventReceiver` is a hand-rolled Observer per `13-GoF.pdf`. The pattern is correct but undocumented. Adding a class-level JavaDoc on `MenuEventReceiver` and `MenuPersistence` that says "Observer pattern — Menu is the Subject, MenuPersistence is one ConcreteObserver, the event types are MenuEvent variants" makes the implementation legible as a teaching artifact.

### 6.3 **Decorator** — opportunity for logging / auditing

The course covers Decorator (`teoria/GoF Strutturali/05-Decorator/`). A natural application: a `LoggingMenuPersister implements MenuPersister` that wraps another `MenuPersister` and logs every call. Wired in `CatERing` like:
```java
MenuPersister menuPersister = new LoggingMenuPersister(new SQLiteMenuPersister(...));
```
Now every persistence call is auditable, no production code touched. This is a 15-line class that demonstrates the pattern in *this* codebase, not as a toy. Similarly: a `CachingMenuPersister`, a `MetricsMenuPersister`. Choose one; demonstrate composition.

### 6.4 **State** — `Menu.published` and `Menu.inUse` are state machine candidates

`Menu` has implicit state transitions: draft → published → in-use. Each has different rules (you can't edit a published menu; you can't delete one in use). The current code enforces these with scattered boolean checks. The State pattern (`teoria/GoF Comportamentali/07-State/`) would centralise them: `MenuState` interface with `DraftState`, `PublishedState`, `InUseState` implementations. Each state's `addSection`, `delete`, etc. either succeeds or throws. This is the textbook State application.

Probably out of scope for this branch, but worth flagging as the natural *next* refactor — and a strong final-project topic.

---

## 7. What to skip

Some things look like improvements but are anti-value here:

- **Generics gymnastics** on `EntityPersister<T>`. The interface is fine. Don't introduce a `Repository<T extends Entity>` hierarchy with type-bound abstract base classes. Three concrete persisters is the right size.
- **Dependency injection container** (Guice, Spring). For a teaching repo of this size, the constructor wiring in `CatERing` is more legible than annotations and a runtime container.
- **Adding a UI**. Out of scope per `exam-artifacts.md`. The backend-only setup keeps the focus on the use case logic, which is what the course is about.
- **Renaming everything to Italian**. The course explicitly accepts anglicized identifiers. A bilingual codebase is harder to teach with than an English one.
- **Premature abstraction over `Manager`**. There is no `Manager` interface, and that's correct: the managers don't share an API. Resist the temptation to extract one.

---

## 8. Suggested order, if attempted

If you're going to invest more time, this is the order with best return per hour:

1. **§1.3 Restore the deleted tests** (highest-value single change — exposes regressions, restores the TDD baseline).
2. **§2.5 Stop swallowing `SQLException`** + **§2.4 try-with-resources** (turns the persistence layer from a maze into something defensible).
3. **§1.1 Recipe persistence Strategy split** (mechanical but completes the refactoring story).
4. **§3.1 + §3.3 Test architecture** (split unit / integration, in-memory DB).
5. **§5.1 Update DCD/DSD** (small, but the inconsistency between code and diagrams will get flagged).
6. **§1.2 Replace `KitchenTask.type` boolean** (involves a schema choice; defer until 1.1 is done).
7. Rest as time permits.

---

## 9. What this branch already gets right

Worth saying out loud, since most of the document is critical:

- The Strategy interface (`EntityPersister<T>`) is shaped correctly.
- The Composite hierarchy now matches `JavaGoF/strutturali/composite/structure_abstractclass`.
- `MenuManager` correctly drives notifications through `MenuEventReceiver`.
- All 10 surviving tests pass; the build is green.
- The patterns/main split is preserved — `main` was not disturbed.
- Italian-domain anglicization is applied consistently.
- The `CatERing` facade-of-controllers shape matches the course's Controller GRASP teaching.

What's missing is mostly *follow-through*: applying the patterns uniformly (1.1), restoring the tests that prove behavior (1.3), and tightening the seams that the patterns expose (2.4, 2.5).
