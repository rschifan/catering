# Student Readiness Assessment — `patterns` branch

Reviewed: 2026-04-26. Build status: 10 tests pass (`mvn test` → BUILD SUCCESS).
Reference materials: `../../exam-artifacts.md`, `../../teoria/`, three prior review docs at the project root (`REVIEW-patterns.md`, `IMPROVEMENTS.md`, `CODEX-REVIEW.md`).

---

## Verdict

**Not ready to hand to students tomorrow; one more focused pass is needed.** The branch gets the three headline patterns substantially right — Singleton matches the reference, Composite is correctly restructured, and Strategy injection into `MenuManager` is clean and legible. The Observer wiring in `MenuPersistence` is the best-written file in the codebase. However, three problems combine to make it risky as a teaching reference right now: (1) `src/test/java/catering/businesslogic/README.md` promises `MenuManagerTest` and `KitchenTaskManagerTest` — neither file exists, so a student who runs `mvn test -Dtest=MenuManagerTest` gets confusing silent skips; (2) the pattern story is visibly asymmetric — Strategy applies to Menu/Section/MenuItem but `Recipe.java` and `Preparation.java` still open with `import catering.persistence.SQLitePersistenceManager` and ~150 lines of inline SQL each, with no comment anywhere explaining why; (3) the primary non-trivial test (`SummarySheetTest`) teaches the wrong testing lesson: order-dependent, `@TestMethodOrder`, no teardown, and `new Shift(date, start, end)` with `shift_id = 0` writes corrupt data to the DB silently while passing.

---

## What works

**`CatERing.java:38-56` — Strategy composition root.** The constructor comment "Strategy composition: build leaf persisters first, then composite ones" is the exact orientation a student needs. The three-line build (`menuItemPersister → sectionPersister → menuPersister`) followed by `menuMgr = new MenuManager(menuPersister)` makes the injection pattern immediately legible.

**`MenuPersistence.java:1-97` — Observer + Strategy intersection.** The class-level JavaDoc ("Observer that persists changes notified by MenuManager; holds the three persisters as injected dependencies — Strategy pattern") is the only file in the codebase that explicitly names both patterns. It reads like a textbook exhibit.

**`KitchenProcessComponent.java` and `Recipe`/`Preparation`.** The abstract-class root, the JavaDoc citing the JavaGoF reference, and `Preparation.add` throwing `KitchenProcessException` are clear. A student can trace the Composite pattern end-to-end without ambiguity.

**`EntityPersister<T>` interface.** Clean, minimal, directly comparable to `ArrayDisplayFormat` in the prof's reference. Students can line them up side by side.

**`MenuManager` contract enforcement.** All four contracts spot-checked in REVIEW-patterns.md are honoured. Precondition checks (`!user.isChef()`, `currentMenu == null`, section-membership guard) map exactly to the slides.

**`SQLiteMenuPersisterTest` (7 tests).** Well-structured, `@DisplayName` on every test, covers insert / load / update / features / complex structure / free items. The most pedagogically clean test class in the suite.

---

## What will confuse students

**1. Missing test files vs README that promises them.**
`src/test/java/catering/businesslogic/README.md` provides a full DSD↔test mapping table pointing to `MenuManagerTest`, `KitchenTaskManagerTest`, and `EventTest`. None of these files exist. Running `mvn test -Dtest=MenuManagerTest` doesn't fail — Maven silently skips it. A student who reads the README first will think the codebase is broken or that they have the wrong branch.

**2. Half-applied Strategy with no explanation.**
`Recipe.java:1-5` imports `SQLitePersistenceManager` directly; `Preparation.java:1-5` does the same. Both have ~150 lines of inline SQL. The contrast with `Menu`/`Section`/`MenuItem` (zero SQL, only `EntityPersister` calls) is stark. There is no comment in either file, no comment in `RecipeManager.java`, and no comment in `CatERing.java` explaining that Recipe/Preparation persistence is intentionally out of scope. A student will conclude either (a) the refactor is unfinished, or (b) inline SQL in domain classes is correct for "supporting" code — both wrong lessons.

**3. `isRecipe()` discriminator on an abstract class that supposedly removed boolean guards.**
`KitchenProcessComponent.java:42-45` declares `public boolean isRecipe() { return false; }` with the comment "Discriminator used by KitchenTask for persistence type encoding." The point of the Composite fix was to eliminate boolean-type guards. A student who read the Composite lecture will ask: if we fixed `if (p.isRecipe()) throw` on `add()`, why does the root still expose `isRecipe()`? `KitchenTask.java:37` and `:85` use it to set and persist a `boolean type` field — so the type tag lives in the domain hierarchy via a different door. The fix removed the symptom (structural guard) but left the cause (type discriminator on the root).

**4. `generateSummarySheet.uxf` DSD vs `SummarySheet` constructor.**
The DSDs at `catering/umlet/generateSummarySheet.uxf` and `slides/class-14-16-progettazione/gestire-compiti-cucina/generateSummarySheet.uxf` show `KitchenTaskManager` driving a loop: `service.getNeededKitchenProcesses()` → `KitchenTask.create(process)` → `newSummarySheet.addTask(t)`. The code puts the entire loop in `SummarySheet`'s constructor (`SummarySheet.java:146-147`). The method name also differs: `getKitchenProcesses()` in code vs `getNeededKitchenProcesses()` in the diagram. A student reconciling diagram and code will think their reading of the DSD is wrong.

**5. `DCD-gestire-menu.uxf` shows fields and methods that don't exist in code.**
`slides/class-14-16-progettazione/gestire-menu/dcd/DCD-gestire-menu.uxf` shows `MenuManager` with `menuFeatures: Map<String,Boolean>` (that field is on `Menu`, not the manager) and `getRecipeBook(): ArrayList<Recipe>` (that method is on `RecipeManager`, not `MenuManager`). A student building their own DCD from this reference will model the wrong responsibility assignment.

**6. `dcdACnew.uxf` still labels the Composite root as `«interface» KitchenProcess`.**
The code has `abstract class KitchenProcessComponent`. The diagram at `catering/umlet/dcdACnew.uxf` still says `«interface» KitchenProcess`. Same repo, contradictory artifacts; a student has no way to know which is authoritative.

**7. `CatERing` setters undermine the Singleton teaching.**
`CatERing.java:66-103` exposes `setShiftManager`, `setMenuManager`, `setRecipeManager`, `setUserManager`, `setEventManager`, `setKitchenTaskManager`. A Singleton whose every sub-manager can be overwritten is not a Singleton — it's a global mutable registry. No comment explains these are leftover. Students will copy the setter pattern.

**8. `UseCaseLogicException()` thrown with no message in MenuManager.**
`MenuManager.java:34, :47, :58, :60, :80, :82, :91, :98, :106, :116, :128, :142, :150, :159, :162, :166, :179, :181, :191, :197` all throw `new UseCaseLogicException()` without a message. The contract slides show precondition violations with diagnostic messages, and `KitchenTaskManager` (the other main manager on this branch) correctly adds messages to every throw. The inconsistency teaches students that messages are optional.

---

## What will mislead students

**Harm 1 — Error swallowing teaches silent failure as acceptable practice.**
`SQLitePersistenceManager.executeQuery`, `executeUpdate`, `executeInsert`, `executeBatchUpdate` all catch `SQLException`, log at SEVERE, and return `0` / empty / null. In 10 passing tests, none of these paths trigger, so the tests provide false confidence. A student who reads this code will learn: "catch the DB exception, log it, return a neutral value — that's how you handle persistence errors." This is the single most damaging pattern in the codebase pedagogically: the log call makes it look deliberate and professional, and every real failure is hidden behind a green test.

**Harm 2 — `SummarySheetTest.testTaskAssignment:133` teaches that `new Shift(date, start, end)` is valid test setup.**
The test constructs `Shift shift = new Shift(shiftDate, startTime, endTime)` (a public constructor), calls `shift.addBooking(cook)`, which executes `INSERT INTO ShiftBookings` with `shift_id = 0`. The test passes. A student reading this learns: (a) domain objects can be constructed directly for testing without factories; (b) writing to the DB with `id=0` is fine. The `@TestMethodOrder` and shared static `chef`/`cook`/`testEvent`/`testService` teach that order-dependent tests are normal.

**Harm 3 — `KitchenTask.boolean type` teaches that boolean type tags on domain objects are correct persistence design.**
`KitchenTask.java:24` stores `private boolean type` where `true = Recipe, false = Preparation`. The field is set in the constructor via `rec.isRecipe()` (line 37) and persisted directly (line 62, 85). At load time (lines 120-125), a switch on this boolean decides which loader to call. This is the textbook Polymorphism anti-pattern that GRASP Polymorphism is meant to eliminate. After the Composite fix was presented as "we removed the boolean type guard," this surviving pattern sends the message that boolean type tags are acceptable in persistence code.

**Harm 4 — `Menu.getSections()` and `Menu.getFreeItems()` return backing lists.**
`Menu.java:162` returns `this.sections`; `Menu.java:187` returns `this.freeItems`. Callers can add/remove sections without going through `MenuManager`'s notification protocol, silently bypassing the Observer. Students will use this as evidence that returning backing lists is fine in Java.

**Harm 5 — `CatERing` setters (see §Confusing #7) also mislead about Singleton design.**

---

## First-touch papercuts

**P1. No README at the project root.** `catering/java/catering/` has `REVIEW-patterns.md`, `IMPROVEMENTS.md`, `CODEX-REVIEW.md`, but no `README.md`. A student cloning the repo sees three internal review documents and a `pom.xml`. There is no file explaining: what this branch demonstrates, how it relates to `main`, which patterns are shown, how to build and run.

**P2. The test README promises non-existent test files.** `src/test/java/catering/businesslogic/README.md` maps DSDs to `MenuManagerTest` and `KitchenTaskManagerTest`. Both are deleted. `mvn test -Dtest=MenuManagerTest` produces no failure (Maven silently skips), which is more confusing than a compile error.

**P3. Schema `;` split is naive.** `SQLitePersistenceManager.java:74` splits the SQL script on `";"`. A SQL comment containing `;` (e.g., `-- version 2.0; schema v3`) silently splits a statement mid-way and corrupts initialization (this just bit me). The current `catering_init_sqlite.sql` doesn't trigger it after my fix, but it's a landmine.

**P4. `database/catering.db` is in the working tree, only `.gitignore`d at repo root.** `SQLitePersistenceManager.ensureDbExists()` only initialises if the file is absent — no re-init on schema change. A student modifying the schema will see no effect until they manually delete `catering.db`.

**P5. `SQLiteMenuPersisterTest` skips Order 4.** Tests are numbered 1, 2, 3, 5, 6, 7, 8. The gap suggests a deleted test; a student skimming the class will either be confused or assume gaps in ordering are acceptable.

**P6. `MenuTest` calls `CatERing.getInstance()` in `@BeforeAll`.** This triggers full database initialization including persister wiring, for a test that only checks `Menu.create()` in memory. The test is not a unit test despite being in `businesslogic/menu/`. It also creates `new User("TestChef")` via a public constructor that bypasses the factory pattern used elsewhere.

---

## Recommended pre-handoff fixes

Ordered by return on effort (highest first):

**Fix 1 — Add a project-root `README.md` (30 minutes).** Explain: (a) this is the `patterns` branch demonstrating Composite/Strategy/Singleton/Observer on the Menu use case; (b) `main` is the pre-refactor baseline; (c) Strategy is applied to Menu/Section/MenuItem persistence — Recipe/Preparation persistence is supporting infrastructure intentionally kept inline for scope reasons; (d) `mvn test` to run all tests; (e) reading path: `CatERing.java` → `MenuManager.java` → `MenuPersistence.java` → `EntityPersister.java` → `SQLiteMenuPersister.java`. This single file eliminates most first-touch confusion.

**Fix 2 — Add scope comments to `Recipe.java` and `Preparation.java` (15 minutes).** At the top of each class, add: `// NOTE: Recipe/Preparation persistence is intentionally kept inline (out of scope for this refactor). // The Menu persistence Strategy (EntityPersister, SQLiteMenuPersister) is the pattern reference. // See exam-artifacts.md §8 for the scope boundary.` Converts a confusing inconsistency into a legible pedagogical choice.

**Fix 3 — Either delete the test README or restore two manager tests (2-4 hours).** The README at `src/test/java/catering/businesslogic/README.md` is more confusing than no README. Either delete it (5 seconds) or restore the two files it promises with 3-4 happy-path tests each. The README maps each test method to a DSD step — that table is gold pedagogically if the tests exist; misleading if they don't.

**Fix 4 — Remove the `CatERing` setters or add explanatory comments (20 minutes).** Either delete the six `set*Manager` methods at `CatERing.java:66-103` (unused in tests), or add a comment: `// Test-injection seam only — do not use in production; breaks Singleton invariant.` Removing is cleaner.

**Fix 5 — Add messages to all `UseCaseLogicException()` throws in `MenuManager` (20 minutes).** Twenty bare `throw new UseCaseLogicException()` calls. Add a one-line message to each (`"precondition: currentMenu must be set"`, `"precondition: user must be Chef"`). Brings `MenuManager` in line with `KitchenTaskManager` (already does this) and with the contract slides.

**Fix 6 — Fix `SummarySheetTest.testTaskAssignment` Shift construction (30 minutes).** Replace `new Shift(shiftDate, startTime, endTime)` with a DB-persisted shift via `ShiftManager`, then run the booking. Removes the `shift_id = 0` DB corruption and removes the bad lesson that public constructors are acceptable for domain objects in tests.

**Fix 7 — Update `dcdACnew.uxf` to relabel `«interface» KitchenProcess` → `abstract class KitchenProcessComponent` (10 minutes in Umlet).** One-element diagram change that eliminates a visible code/diagram contradiction on the pattern just fixed.

**Fix 8 — Add a comment to `KitchenProcessComponent.isRecipe()` explaining it's a known limitation (10 minutes).** `// Persistence discriminator: used only by KitchenTask to determine which loader to call at DB load time. // This is a known limitation — a full fix would replace the boolean field in KitchenTask with a // type-safe factory (see IMPROVEMENTS.md §1.2).` Converts pattern smell from "looks like a bug" to "acknowledged trade-off."

**Fix 9 — Move `SummarySheet`'s constructor loop to `KitchenTaskManager.generateSummarySheet` (1 hour).** The DSD shows the manager driving the loop; the code has the constructor do it. Move `service.getMenu().getKitchenProcesses().forEach(...)` out of `SummarySheet(Service, User)` into `KitchenTaskManager.generateSummarySheet` after `new SummarySheet(service, user)`. Also rename `getKitchenProcesses()` → `getNeededKitchenProcesses()` on `Menu`. Aligns code with both DSDs that show this use case.

**Fix 10 — (Highest pedagogical value, larger effort) Make `SQLitePersistenceManager` propagate exceptions instead of swallowing them.** Wrap the caught `SQLException` in a `PersistenceException extends RuntimeException` and rethrow. Teaches the correct pattern and makes failing tests actually fail instead of silently returning `0`/`null`/empty. Estimated 1-2 hours including updating callers that genuinely want to handle DB errors.

---

## Bottom line

The pattern fixes already done (Singleton, Composite, Strategy injection, Observer wiring) are good — students will learn the right things from those. The remaining gaps are mostly *framing*: a README, two scope comments, a deleted-or-restored test file, removing dead setters, adding throw messages. None of these require new pattern work — just paving the cow paths so a student reading the branch end-to-end forms an accurate mental model. Fixes 1, 2, 3 alone (≈ 3 hours combined) would move the verdict from "not ready" to "ready."
