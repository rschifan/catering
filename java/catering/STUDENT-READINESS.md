# Student Readiness Assessment — `patterns` branch

Reviewed: 2026-04-26 (post-`Preconditions` refactor). Build status: 10 tests pass (`mvn test` → BUILD SUCCESS).
Reference materials: `../../exam-artifacts.md`, `../../teoria/`, project-root review docs (`REVIEW-patterns.md`, `IMPROVEMENTS.md`).

---

## Verdict

**Ready to ship.** All previously-flagged blockers are resolved. The pattern story is internally consistent end-to-end: Singleton, Composite, Strategy, Observer, and now Pure Fabrication (via the new `Preconditions` helper) are each demonstrated by a clean worked example, and the two managers share the same precondition vocabulary. What remain are acknowledged limitations documented in `IMPROVEMENTS.md` (the `KitchenTask.boolean type` discriminator, the `SQLitePersistenceManager` exception swallowing, encapsulation leaks via `Menu.getSections()`/`getFreeItems()`, and the test-data corruption in `SummarySheetTest`). None of those misrepresent the patterns being taught; they are open questions students can productively engage with rather than landmines that mislead them about correct design.

---

## What changed since the previous review

- **Blocker 1 resolved** — the six `set*Manager` methods on `CatERing` are deleted (`CatERing.java`). The Singleton now exposes only its constructor and accessors. No callers existed; all tests still pass.
- **Blocker 2 resolved by structural refactor, not by patches** — `MenuManager` no longer contains a single `throw new UseCaseLogicException()`. The 20 inline precondition checks are gone. They have been replaced by calls to a new `Preconditions` helper class (`businesslogic/Preconditions.java`) — a Pure Fabrication (GRASP) that holds the cross-cutting responsibility of validating use-case preconditions. `KitchenTaskManager` was migrated to use the same helper for the chef and current-summary-sheet checks. Both managers now speak the same precondition vocabulary; each call site reads as the contract clause it enforces (`Preconditions.requireChef(user)`, `Preconditions.requireCurrentMenu(currentMenu)`, etc.).

A bug fix slipped in during the migration: `KitchenTaskManager.modifyAssignment` previously had inverted boolean logic (`if (cook == null || shift.isBooked(cook)) modify; else throw`) that worked but read backwards from the contract. The new form is direct: `if (cook != null && !shift.isBooked(cook)) throw "Cook X is not booked..."`.

---

## What students will love

**`businesslogic/Preconditions.java` — Pure Fabrication, named.**
Five static methods (`requireChef`, `requireCurrentMenu`, `requireSectionInMenu`, `requireItemInMenu`, `requireCurrentSummarySheet`), one per recurring precondition clause from `teoria/06-Contratti.pdf`. Class-level JavaDoc explicitly names the GRASP pattern. Both managers depend on it. This is the only worked example of Pure Fabrication in the codebase and reads exactly like what the slide shows.

**Manager methods read like contract slides.**
`MenuManager.insertItem(recipe, sec, desc)` is now four lines: `requireCurrentMenu(currentMenu)`, optional `requireSectionInMenu(currentMenu, sec)`, the operation, the notification. A student reading the method can reconstruct the contract without a comment. Same shape across every method.

**`CatERing.java:38-65` — Strategy composition root.**
The leaf-then-composite build order, explicit comment, and constructor-injected wiring make the Strategy pattern immediately legible.

**`MenuPersistence.java` — Observer + Strategy intersection.**
Class-level JavaDoc names both patterns. Reads like a textbook exhibit.

**`RecipeManager.java` — mirrors `MenuManager`.**
Constructor-injected, two persisters by interface type, three methods. The JavaDoc says "mirroring `MenuManager`" which closes the loop after the Recipe Strategy split.

**`KitchenProcessComponent.java` — Composite root.**
Single abstract class, JavaDoc cites the JavaGoF reference path, leaf throws checked `KitchenProcessException`.

**`SQLiteMenuPersisterTest` (7 tests).**
`@DisplayName` everywhere, persister composition wired explicitly in `@BeforeAll` with a "Wire leaves first, then composites" comment. Pedagogically clean.

**`README.md` reading path.**
Six-step guided tour with pattern labels and JavaGoF cross-links.

---

## Acknowledged limitations (not blockers)

These are real defects, but each is either documented in `IMPROVEMENTS.md` or visible enough that students can engage with it as an open question rather than be misled.

**1. `KitchenTask.boolean type` — boolean type tag on a domain object** (`KitchenTask.java:24`).
A surviving Polymorphism anti-pattern, used as a persistence discriminator. The `KitchenProcessComponent.isRecipe()` accessor it depends on is the only remaining type-query on the Composite root. Documented as `IMPROVEMENTS.md §1.2`. A good exam discussion point.

**2. `SummarySheetTest.testTaskAssignment:133` writes `shift_id = 0`.**
The test constructs `new Shift(date, start, end)` and books a cook on it, producing a `INSERT INTO ShiftBookings` with `shift_id = 0`. The test passes; the corruption is silent. Documented; deserves a fix before the test suite is held up as a TDD reference.

**3. `SQLitePersistenceManager` swallows `SQLException`.**
Every `execute*` method catches, logs SEVERE, returns `0`/`null`/empty. Documented as `IMPROVEMENTS.md §2.5`. The most damaging long-term lesson if students treat the codebase as a style reference for production work.

**4. `Menu.getSections()` / `getFreeItems()` return backing lists.**
Encapsulation leak. Documented; minor.

---

## Recommended next pass (no longer urgent)

If a future iteration wants to push from "ready" to "exemplary":

1. **Restore the three deleted manager tests** (`MenuManagerTest`, `EventTest`, `KitchenTaskManagerTest`) and adapt them to the constructor-injected APIs. Brings test coverage in line with what the codebase deserves and gives students worked examples of unit tests for use-case controllers.
2. **Replace the `KitchenTask.boolean type` field** with a typed dispatch (small factory or split tables). Removes the last surviving Polymorphism anti-pattern.
3. **Stop swallowing `SQLException`** in `SQLitePersistenceManager`. Wrap in `PersistenceException extends RuntimeException`. Tests that hit failure paths will then actually fail.
4. **Defensive copies for `Menu.getSections()` / `getFreeItems()`**. One-line changes.

None of these block handing the branch to students for the *patterns* curriculum. They become relevant if the branch is also pitched as a testing or production-readiness exemplar.
