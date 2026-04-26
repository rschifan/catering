# Full code review — `patterns` branch

Branch reviewed: `patterns` (working tree, 6 commits + uncommitted edits, after the three fixes already applied — see §0).
Reference materials: `teoria/` slides (Unified Process, GRASP, GoF, contracts, testing), `JavaGoF/src/` reference code, project DCD/DSD diagrams under `catering/umlet/`, course glossary under `slides/class-01-glossario/`, exam deliverables list `exam-artifacts.md`.

Severity legend:
- **H** = clear bug, broken contract, or major design violation of taught rule
- **M** = best-practice violation or deviation from course style
- **L** = polish / consistency

---

## 0. Status of fixes already applied to this branch

These three fixes from an earlier review pass are already in the working tree:

- ✅ **Singleton** simplified — `CatERing.getInstance()` matches `JavaGoF/.../PrinterSpooler_b` (no `synchronized`, lazy null-check).
- ✅ **Composite** restructured — `KitchenProcessComponent` is now a single abstract-class root (no separate interface), modeled on `JavaGoF/strutturali/composite/structure_abstractclass/Component.java`. `children` lives only in `Recipe` (composite); `Preparation` (leaf) throws checked `KitchenProcessException` on `add`/`remove`. The `if (p.isRecipe()) throw` boolean-guard polymorphism violation is gone — rejection happens by override on the leaf.
- ✅ **Test package hygiene** — empty placeholder removed; `SQLiteMenuPersisterTest` moved from `bridge/impl/` to `strategy/impl/` with matching package.

Build status after fixes: `mvn -q test` → 10 tests pass. Everything below is *still outstanding* and refers to the current working-tree state.

---

## 1. Pattern conformance (GoF / GRASP)

### 1.1 Strategy — Service-Locator instead of injection — **H**

The professor's `JavaGoF/.../strategy/myarray/MyArray.java` holds the strategy as an *instance field* (`private ArrayDisplayFormat format;`) settable via constructor or `setDisplayFormat(...)`. The branch uses **static** `Menu.persister`, `Section.persister`, `MenuItem.persister` set globally from `CatERing` (`Menu.java:15-19`, `Section.java:10-14`, `MenuItem.java:10-14`, wiring at `CatERing.java:39-41`). That is Service Locator, not Strategy: caller cannot choose the persister, every test mutates global state, and the persister is *known to the domain class* (the DCD never shows this dependency).

**Fix.** Move the persisters to private final fields on `MenuManager` (constructor-injected from `CatERing`). Drop the static `persister` field and `setPersister` from `Menu`/`Section`/`MenuItem`. Drop the static facade methods on those classes — `MenuManager` calls the persisters directly, exactly like `MyArray.display() → format.printData(...)`.

### 1.2 Strategy — circular call into the model layer — **H**

`SQLiteMenuPersister.java:82` calls `Section.insert(menu.getId(), menu.getSections())`, line 86 calls `MenuItem.insert(...)`, lines 150-151 call `Section.loadSections(...)` / `MenuItem.loadMenuItems(...)`. `SQLiteSectionPersister.java:54, 70, 87, 102` similarly calls `MenuItem.loadMenuItems(...)` / `MenuItem.insert(...)`. `SQLiteMenuItemPersister.java:110` calls `Recipe.loadRecipe(...)`. The Strategy implementations depend on model-class statics that themselves delegate back to persisters: `SQLiteMenuPersister → Section.insert → SectionPersister → SQLiteSectionPersister → MenuItem.insert → ...`. Strategy implementations should compose other strategies directly, not bounce through the model layer.

**Fix.** After 1.1 removes the model statics, each `SQLite*Persister` holds the sub-persisters it needs as constructor-injected fields. Wiring lives in `CatERing`.

### 1.3 Strategy — Recipe / Preparation persistence not migrated — **H**

`Recipe.java:33-206` and `Preparation.java:50-156` retain inline `SQLitePersistenceManager.executeQuery / executeInsert / executeUpdate` calls. The branch's stated purpose (commit `ccb170c` "separate model and persistence in menu") was applied to Menu but stopped short of Recipe/Preparation, leaving the refactoring half-finished and inconsistent. GRASP **Pure Fabrication** + **Protected Variations**: persistence is not a domain responsibility.

**Fix.** Add `RecipePersister` / `PreparationPersister` interfaces under `persistence/strategy/` and SQLite impls under `impl/`. Move all SQL out of `Recipe`/`Preparation`. `RecipeManager` holds the persisters via constructor injection from `CatERing`.

### 1.4 Polymorphism residue: `KitchenTask.type` boolean field — **H**

The Composite fix removed the `if (p.isRecipe())` structural guard, but `KitchenTask.java:24` still stores a `boolean type` field encoding "is this task wrapping a Recipe (true) or Preparation (false)". This boolean is read at `:37, :85, :119-125, :163-165` to drive load-time type discrimination from the DB. Persisting the type discriminator and switching on it at reload is the GRASP Polymorphism violation that the abstract-class refactor was meant to eliminate.

**Fix.** Either (a) split the `kitchen_tasks` table into two (one per concrete type) and let each loader return the correct subtype, or (b) store a string discriminator and use a small factory rather than a boolean. The boolean-field-as-type-tag is the worst of both worlds.

### 1.5 Composite shape — already aligned — **OK** (post-fix §0)

After the recent fix, `KitchenProcessComponent` matches the prof's safety variant. One residual: the project DCD (`catering/umlet/dcdACnew.uxf`) still labels the type as `«interface» KitchenProcess`. The label needs updating to `KitchenProcessComponent` (abstract class) for code/diagram consistency — but the diagram is out-of-scope for this work per the original plan.

### 1.6 Singleton — already aligned — **OK** (post-fix §0)

---

## 2. Theory alignment (slides ↔ implementation)

### 2.1 Domain model fidelity

Reading the class-08 domain model (`slides/class-06-08-md-ssd/`) against the code:

- **`SummarySheet` constructor conflates two responsibilities** — `SummarySheet.java:139-148`. The DSD `catering/umlet/generateSummarySheet.uxf` shows the loop driven by `KitchenTaskManager`: `serv.getMenu().getNeededKitchenProcesses()` → loop → `KitchenTask.create(process)` → `newSummarySheet.addTask(t)`. The code collapses the entire loop *inside* `SummarySheet`'s constructor (`service.getMenu().getKitchenProcesses().forEach(...)`). This violates the DSD's message sequence and GRASP **Information Expert** — the manager should drive the loop, not the constructor. **Severity: H** (it's both a contract drift and a misallocated responsibility).
- **DSD/code method-name drift** — `Menu.getKitchenProcesses()` in code vs. `getNeededKitchenProcesses()` in `generateSummarySheet.uxf`. **L.**
- **`Tabellone dei Turni` has no Java counterpart** — the domain model includes this entity; the code only exposes `Shift.getShiftTable()` as a static returning a list. **L.**
- **`Cuoco` and `Chef` collapsed into `User.isChef()`** — the domain model distinguishes them as separate concepts; the code merges both behind a single role flag. The contract precondition "l'attore è identificato con un'istanza ch di Chef" cannot be precisely expressed because `User.isChef()` covers both roles. **M.**
- **`Compito` attribute `da preparare`** — the domain model has this as a separate boolean; the code's `KitchenTask` has only `ready` (matching `completato`). **L.**

### 2.2 Controller GRASP

The intended pattern is a **facade controller** (`CatERing`) aggregating per-use-case **use-case controllers** (the `*Manager` classes). This is faithfully implemented for the menu use case and the kitchen-task use case. Two consistency breaks:

- **`EventManager` bypasses the observer-based persistence used elsewhere.** `MenuManager` relies on `MenuEventReceiver` (observer) to persist changes. `EventManager.createEvent` (`EventManager.java:138`) calls `event.saveNewEvent()` directly on the domain object; `EventManager.modifyService` (`:228`) calls `Menu.load(menuId)` — manager-to-domain-static persistence coupling. There is no `EventReceiver` registered. **M** — supporting-infrastructure scope, but the pattern inconsistency is a teaching defect.
- **`SummarySheet` contains static SQL methods** (`loadAllSumSheets`, `saveNewSumSheet`, `loadSummarySheets`, `updateTaskList`). The DCD shows `SummarySheet` as a pure domain class. SQL belongs in `KitchenTaskPersistence`, not in the domain class. **M.**

### 2.3 Operation contracts (4 spot-checks against `slides/class-10-12-contracts/`)

| Method | Slide preconditions | Code | Slide postconditions | Code | Verdict |
|---|---|---|---|---|---|
| `MenuManager.createMenu(title)` | actor is Chef | `!user.isChef() → throw` (`:26`) | menu *m* created; `m.titolo=titolo`; chef owns *m*; `m.pubblicato=no` | `Menu.create(user, title)` sets owner/title; published defaults to false | **OK** |
| `MenuManager.defineSection(name)` | menu *m* in definition | `currentMenu == null → throw` (`:39`) | section created; named; `m contiene sez` | `currentMenu.addSection(name)` | **OK** |
| `MenuManager.insertItem(recipe, sec, desc)` | menu in definition; (if section given) menu contains section | both checked (`:50-53`) | item created; references recipe; placed in section or as free; description set | `currentMenu.addItem(...)` | **OK** — but explicit owner check from the slide's general precondition is missing here |
| `KitchenTaskManager.generateSummarySheet(event, service)` | user is Chef; event contains service; event chef is current user; service has menu | all four checked (`:29-45`) | sheet created; one `KitchenTask` per kitchen process; sheet notified | partially achieved — but loop is inside `SummarySheet` constructor, not driven by manager | **Partial** — postcondition reached, but DSD message sequence broken (see §2.1) |

### 2.4 Diagram drift

- `dcdACnew.uxf`: labels Composite root as `«interface» KitchenProcess`; code now has abstract class `KitchenProcessComponent`. Update the diagram or accept the drift in the report.
- `generateSummarySheet.uxf`: shows `getNeededKitchenProcesses()` and a manager-driven loop; code uses `getKitchenProcesses()` and constructor-driven loop.
- `DCD-gestire-menu.uxf`: shows `MenuManager.menuFeatures: Map`; code stores features on `Menu` not on the manager. Shows `MenuManager.getRecipeBook()`; code delegates to `RecipeManager`.

These divergences should be either reflected back into the diagrams (preferable for a teaching artifact) or noted as acceptable evolution.

### 2.5 Glossary alignment

Mostly correct anglicization (Menù→Menu, Sezione→Section, Ricetta→Recipe, etc.). One real gap is the Cuoco/Chef collapse noted in §2.1. The "MenuItem" anglicization for "Voce" loses the catering metaphor but is acceptable design English.

---

## 3. Code quality

### 3.1 Persistence layer

- **(H) Error swallowing in `SQLitePersistenceManager`** — `executeQuery`, `executeUpdate`, `executeInsert` (around `:120, :209, :237`) all catch `SQLException`, log, and return `0` / empty / fall through. Callers cannot tell a real DB failure from "0 rows / not found". `executeInsert → 0` silently sets persisted-object IDs to 0 (e.g. `Recipe.save:184`, `Shift.createShift:189`), making the object look unsaved. Either rethrow as a runtime wrapper or document `0` semantics so callers can react.
- **(H) NPE in `SQLiteMenuItemPersister.insert(int,int,MenuItem,int)`** — `:47`, `item.getRecipe().getId()` is unconditional. `MenuItem.create()` (no-arg) leaves `recipe == null`. Add a null guard.
- **(H) NPE in `Assignment.saveAllNewAssignment`** — `Assignment.java:154`, `assignmentList.get(batchCount).cook.getId()` lacks a guard; `Assignment` allows `cook == null`. The non-batch `saveNewAssignment` (`:177`) handles it correctly with a ternary — apply the same here.
- **(M) `SQLiteSectionPersister.load` uses sentinel `-1` as menuId** — `:54`, `MenuItem.loadMenuItems(-1, sectionId)`. Relies on no row matching `menu_id = -1`. Use a query that selects by `section_id` only.
- **(M) `SQLiteMenuPersister.loadAll` is N+1** — `:158-160`, one `load(id)` per menu, each opening multiple queries.
- **(M) `SQLiteMenuPersister.insertFeatures` uses `HashMap` iteration order** — `:186`. Switch to `LinkedHashMap` or sort keys.
- **(M) `Shift` has two parallel insert paths** — `Shift.java:187-209`. `createShift` passes typed dates via `setParameters`; `saveShift`/`updateShift` pass `.toString()` — different storage formats possible. Remove the duplicates.
- **(M) `Shift.isBooked(User)` calls `containsValue` (O(n))** — `:292`. The map is keyed by user ID for O(1) lookup; use `containsKey(u.getId())`.
- **(L) Two SQL constants outside the `SQL` inner class** — `SQLiteMenuPersister.java:109-110`. Move them in for consistency.
- **(L) Inconsistent lambda vs anonymous-class `ResultHandler` style** — lambda at `SQLiteMenuPersister.java:133`, anonymous classes at `SQLiteMenuItemPersister.java:69`, `Recipe.java:54`, `Shift.java:66`. Standardise on lambdas; mark `ResultHandler` `@FunctionalInterface`.
- **(L) `SELECT *` everywhere** — `Recipe.java:53`, `Preparation.java:53`, `User.java:118`, `Shift.java:61`, `SummarySheet.java:24`, etc. Enumerate columns to decouple from table evolution.

### 3.2 Encapsulation

- **(H) `Menu.getSections()` and `Menu.getFreeItems()` leak mutable internal lists** — `Menu.java:193, :219`. Return the backing `ArrayList` directly. Callers can mutate without going through `MenuManager`'s notification protocol. Return `Collections.unmodifiableList(...)` or a copy.
- **(M) `Menu.getFeatures()` returns the internal `HashMap`** — `:312`.
- **(M) `Section.getItems()` returns the internal list** — `Section.java:136`.
- **(M) `SummarySheet.getTaskList()` / `getAssignments()` return the backing `ArrayList`** — `SummarySheet.java:175, :197`.
- **(M) `Recipe(String)` and `Preparation(String)` constructors are public** — `Recipe.java:20`, `Preparation.java:25`. The other domain classes (`Menu`, `Section`, `MenuItem`) use private constructors + static factories. Be consistent — package-private + factory.

### 3.3 equals / hashCode

- **(H) `Menu.equals` and `hashCode` include mutable collections** — `:488-540`. Once a menu is added to a `HashSet` and then mutated via `addSection` / `addItem`, its hash changes and lookup breaks. Same problem on `Section.equals/hashCode` (`:177-234`) — includes section items. Base equality on the stable `id`; for unsaved objects (id=0), use `id`+`title` only.
- **(M) `KitchenProcessComponent.equals/hashCode` for id-less components builds `HashSet<children>`** — `:80-82, :90-91`. For unsaved trees this is fragile (recursive hashing) and slow. Use `name`+`description` only when `id == 0`.

### 3.4 Manager / domain logic

- **(M) `MenuManager.currentMenu` and `KitchenTaskManager.currentSumSheet` are shared mutable state on Singleton-managed objects** — `MenuManager.java:12`, `KitchenTaskManager.java:14`. Tests interact through this state, making them order-dependent (see §4). Either pass the current entity explicitly to each operation or scope managers per session.
- **(M) `MenuManager.createMenu` and 9 other methods throw `UseCaseLogicException()` with no message** — `MenuManager.java:26, :40, :51, :53, :75, :84, :99, :136, :159, :173, :183`. Always pass a descriptive message; the slide on contracts shows messages by convention.
- **(M) `KitchenTaskManager.addKitchenTask` does not guard `currentSumSheet == null`** — `:69`. Every other public method does. Inconsistent and yields a raw NPE instead of `UseCaseLogicException`.
- **(L) `Menu.setIntInUse(boolean)` — name suggests an int** — `Menu.java:415`. Rename to `setInUse`.

### 3.5 Recipe / Preparation

- **(H) `Recipe.loadRecipe(String)` silently swallows `SQLException` on description** — `Recipe.java:120-124`. Inner try/catch sets `description = ""` on error. Remove it; `ResultHandler.handle` already declares `SQLException`.
- **(M) `Recipe.getAllRecipes()` is a dead alias for `loadAllRecipes()`** — `:69-71`. Pick one.
- **(M) `Preparation.getUsedInRecipes()` is N+1** — `:142-154`. Loads each recipe (with its preparations) per row. Batch-load.

### 3.6 KitchenTask / SummarySheet

- **(M) `SummarySheet.deleteAssignment` throws `UseCaseLogicException()` with no message** — `:203`.

### 3.7 Shift

- **(H) `ShiftManager.createShift(date, startTime, endTime, workPlace, isKitchen)` silently drops `workPlace` and `isKitchen`** — `ShiftManager.java:56-59`. The signature accepts them, the body logs `workPlace`, then calls `Shift.createShift(date, startTime, endTime)` discarding both. Either remove the parameters or pass them through.
- **(M) `ShiftManager` constructor calls `Shift.loadAllShifts()` and discards the result** — `:24-26`. Pure wasted IO; the same call runs again from `getShiftTable()`.
- **(M) `Shift` exposes a public constructor** — `Shift.java:28`. Tests use it to build transient shifts (`SummarySheetTest.java:133`), which then get assigned with `id == 0` — silent data corruption. Make the constructor package-private; provide a test-only factory.

### 3.8 Event

- **(M) `EventManager.createEvent` and `createService` catch `Exception` and return `null`** — `EventManager.java:147, :189`. Converts any failure into `NullPointerException` downstream. Let domain exceptions propagate.
- **(M) `EventManager.modifyService` writes errors to `System.err`, bypassing `LogManager`** — `:244-247`.

### 3.9 User

- **(M) `User.loadRolesForUser` uses magic-number switch on role IDs** — `:180-193`. Hardcoded 0-3; brittle.
- **(M) `User.load(int)` returns a `User` with `id == 0` on not-found** — `:116-131`. Callers (e.g. `SummarySheet.loadSummarySheets:121`, `Assignment.loadAllAssignmentsBySumSheetId:107`) install this phantom user. Return `null` instead, like `Recipe.loadRecipe`.

### 3.10 Cross-cutting

- **(M) `CatERing` exposes setters for every sub-manager** — `CatERing.java:65-103`. Defeats Singleton immutability. If the only use was test injection, remove them and use proper test fixtures.
- **(M) `DateUtils.getDateFromResultSet` swallows `SQLException`, returns `null`** — `DateUtils.java:54-56`. The method is also currently unused in production code. Either delete it or propagate.

---

## 4. Test discipline

The course's `teoria/14-CodiceTest.pdf` requires Preparazione / Esecuzione / Verifica / Rilascio structure and tests independent of external state.

- **(H) `MenuManagerTest.java` (259 lines), `EventTest.java` (125 lines), `KitchenTaskManagerTest.java` (94 lines) are deleted** — restored from `main` and adapted to the new APIs is the intended fix from the earlier review pass; still pending.
- **(H) `SummarySheetTest` is an integration test masquerading as a unit test** — depends on seeded users ("Antonio", "Luca"), event ("Gala Aziendale Annuale"), service ("Pranzo Buffet Aziendale"). `@TestMethodOrder(OrderAnnotation)` + shared static state (`chef`, `testEvent`, `testService`) makes tests order-dependent. No Rilascio (no teardown). **The test is useful but should be labeled and located as an integration test, with seeding done programmatically in the fixture.**
- **(H) `SummarySheetTest.testTaskAssignment:133` builds a `Shift` via the public constructor without persisting it.** `shift.addBooking(cook)` then writes `INSERT INTO ShiftBookings` with `shift_id = 0` — DB-level data corruption.
- **(M) `MenuTest.java` has 1 test for a domain class with 15+ behaviors.** Stub coverage. The single test exercises happy-path construction only.
- **(M) `MenuTest` triggers full `CatERing` initialization in `@BeforeAll`** (line 15-17), which mutates `Menu.setPersister(...)` globally. A pure unit test for `Menu.create` should not need a database wired up.
- **(M) `SQLiteMenuPersisterTest` order 4 is missing** — suggests a deleted test. Fill or renumber.
- **(L) Conditional assertion in `SummarySheetTest:101-106`** — the `if (sheet.getTaskList().isEmpty())` branch logs a warning instead of asserting, then asserts non-empty two lines later. The log-only path is dead.

---

## 5. Scope notes

- **`ShiftManager` (+194 lines) and `EventManager` (+58 lines) are supporting infrastructure** per `exam-artifacts.md`. Expansion is acceptable; the pattern inconsistencies (no observer for events, parameter-drop bug in `createShift`) are not.
- **`util/DateUtils.java` (new, 85 lines)** is mostly unused and contains the swallowing bug noted in §3.10. Either justify each method by an actual call site or trim.

---

## 6. What is good

- `EntityPersister<T>` is a clean Java interface (matches the prof's `ArrayDisplayFormat` shape). The Strategy *interface* is correct; only the wiring (§1.1) and the inter-persister coupling (§1.2) need fixing.
- The Composite shape is now correct (§0).
- The Singleton matches the prof's reference (§0).
- The Menu use case's contracts (§2.3) are correctly enforced where they're enforced.
- `MenuManager` correctly uses the observer pattern for persistence (`MenuEventReceiver`); the rest of the codebase should follow this lead, not the other way around.

---

## 7. Suggested order if applying fixes

1. **§1.1 + §1.2** as one batch — Strategy injection. Touches Menu/Section/MenuItem/MenuManager/CatERing/SQLite*Persister. Foundational; downstream changes depend on it.
2. **§3.1 H-items + §3.2 H-items + §3.3 H-items** — silent-failure bugs and encapsulation leaks.
3. **§4 H-items** — restore deleted tests against the new API; fix the Shift-corruption test bug.
4. **§1.3** — Recipe/Preparation Strategy split (largest refactor; extracts ~250 lines of SQL).
5. **§1.4** — replace `KitchenTask.type` boolean discriminator (involves DB schema change).
6. **§3 M/L items** as polish.
7. Update `catering/umlet/` diagrams to match (§2.4).
