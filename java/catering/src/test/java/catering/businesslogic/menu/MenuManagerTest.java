package catering.businesslogic.menu;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import catering.businesslogic.CatERing;
import catering.businesslogic.UseCaseLogicException;
import catering.businesslogic.recipe.Recipe;
import catering.persistence.SQLitePersistenceManager;

/**
 * System-operation tests for the <em>Gestire menù</em> use case
 * (DSDs in {@code slides/class-14-16-progettazione/gestire-menu/dsd/}).
 * <p>
 * Each {@link Nested} class corresponds to one DSD operation. Every test
 * exercises either the happy path or one precondition violation (an
 * ALT-block clause), as the testing slides recommend.
 */
class MenuManagerTest {

    private static CatERing app;

    @BeforeAll
    static void initializeRuntime() {
        SQLitePersistenceManager.initializeDatabase("database/catering_init_sqlite.sql");
        app = CatERing.getInstance();
    }

    @BeforeEach
    void resetSession() throws UseCaseLogicException {
        app.getUserManager().fakeLogin("Antonio"); // Antonio is a chef
        app.getMenuManager().setCurrentMenu(null);
    }

    @Nested
    class CreateMenu {

        @Test
        void asChef_returnsMenuAndSetsItAsCurrent() throws UseCaseLogicException {
            Menu m = app.getMenuManager().createMenu("Pranzo");

            assertNotNull(m);
            assertEquals("Pranzo", m.getTitle());
            assertSame(m, app.getMenuManager().getCurrentMenu());
        }

        @Test
        void asNonChef_throwsUseCaseLogicException() throws UseCaseLogicException {
            app.getUserManager().fakeLogin("Luca"); // Luca is a cook, not a chef
            assertThrows(UseCaseLogicException.class,
                    () -> app.getMenuManager().createMenu("Pranzo"));
        }
    }

    @Nested
    class DefineSection {

        @Test
        void withCurrentMenu_appendsSection() throws UseCaseLogicException {
            app.getMenuManager().createMenu("Pranzo");

            Section s = app.getMenuManager().defineSection("Antipasti");

            assertEquals("Antipasti", s.getName());
            assertEquals(1, app.getMenuManager().getCurrentMenu().getSectionCount());
        }

        @Test
        void withNoCurrentMenu_throwsUseCaseLogicException() {
            assertThrows(UseCaseLogicException.class,
                    () -> app.getMenuManager().defineSection("Antipasti"));
        }
    }

    @Nested
    class InsertItem {

        private Recipe recipe;

        @BeforeEach
        void seedMenu() throws UseCaseLogicException {
            app.getMenuManager().createMenu("Pranzo");
            recipe = new Recipe("Bruschetta");
        }

        @Test
        void intoSection_attachesItemToSection() throws UseCaseLogicException {
            Section s = app.getMenuManager().defineSection("Antipasti");

            MenuItem mi = app.getMenuManager().insertItem(recipe, s, "House bruschetta");

            assertEquals("House bruschetta", mi.getDescription());
            assertEquals(1, s.getItems().size());
        }

        @Test
        void withNullSection_attachesItemToFreeItems() throws UseCaseLogicException {
            app.getMenuManager().insertItem(recipe, null, "Standalone");

            assertEquals(1, app.getMenuManager().getCurrentMenu().getFreeItems().size());
        }

        @Test
        void recipeOnly_defaultsDescriptionToRecipeName() throws UseCaseLogicException {
            MenuItem mi = app.getMenuManager().insertItem(recipe);

            assertEquals(recipe.getName(), mi.getDescription());
        }

        @Test
        void withNoCurrentMenu_throwsUseCaseLogicException() {
            app.getMenuManager().setCurrentMenu(null);

            assertThrows(UseCaseLogicException.class,
                    () -> app.getMenuManager().insertItem(recipe, null, "x"));
        }

        @Test
        void withSectionFromAnotherMenu_throwsUseCaseLogicException() {
            Section orphan = Section.create("Orphan");

            assertThrows(UseCaseLogicException.class,
                    () -> app.getMenuManager().insertItem(recipe, orphan, "x"));
        }
    }

    @Nested
    class ChooseMenu {

        @Test
        void ownedMenu_becomesCurrent() throws UseCaseLogicException, MenuException {
            Menu m = app.getMenuManager().createMenu("Pranzo");
            app.getMenuManager().setCurrentMenu(null);

            app.getMenuManager().chooseMenu(m);

            assertSame(m, app.getMenuManager().getCurrentMenu());
        }

        @Test
        void notOwner_throwsMenuException() throws UseCaseLogicException {
            Menu m = app.getMenuManager().createMenu("Pranzo");
            app.getUserManager().fakeLogin("Chiara"); // chef, but not the owner of m

            assertThrows(MenuException.class,
                    () -> app.getMenuManager().chooseMenu(m));
        }

        @Test
        void asNonChef_throwsUseCaseLogicException() throws UseCaseLogicException {
            Menu m = app.getMenuManager().createMenu("Pranzo");
            app.getUserManager().fakeLogin("Luca"); // cook

            assertThrows(UseCaseLogicException.class,
                    () -> app.getMenuManager().chooseMenu(m));
        }
    }

    @Nested
    class ChooseMenuForCopy {

        @Test
        void asChef_transfersOwnershipToCurrentChef() throws UseCaseLogicException {
            // Antonio creates the source menu...
            Menu source = app.getMenuManager().createMenu("Source");
            app.getMenuManager().defineSection("Antipasti");

            // ...Chiara logs in and copies it: the copy must be owned by Chiara.
            app.getUserManager().fakeLogin("Chiara");
            Menu copy = app.getMenuManager().chooseMenuForCopy(source);

            assertNotSame(source, copy);
            assertEquals(source.getSectionCount(), copy.getSectionCount());
            assertTrue(copy.isOwner(app.getUserManager().getCurrentUser()),
                    "chooseMenuForCopy must transfer ownership to the current chef");
        }

        @Test
        void asNonChef_throwsUseCaseLogicException() throws UseCaseLogicException {
            Menu source = app.getMenuManager().createMenu("Source");
            app.getUserManager().fakeLogin("Luca");

            assertThrows(UseCaseLogicException.class,
                    () -> app.getMenuManager().chooseMenuForCopy(source));
        }
    }

    @Nested
    class DeleteMenu {

        @Test
        void ownedAndNotInUse_doesNotThrow() throws UseCaseLogicException {
            Menu m = app.getMenuManager().createMenu("Pranzo");

            assertDoesNotThrow(() -> app.getMenuManager().deleteMenu(m));
        }

        @Test
        void notOwner_throwsMenuException() throws UseCaseLogicException {
            Menu m = app.getMenuManager().createMenu("Pranzo");
            app.getUserManager().fakeLogin("Chiara"); // chef, but not the owner

            assertThrows(MenuException.class,
                    () -> app.getMenuManager().deleteMenu(m));
        }

        @Test
        void asNonChef_throwsUseCaseLogicException() throws UseCaseLogicException {
            Menu m = app.getMenuManager().createMenu("Pranzo");
            app.getUserManager().fakeLogin("Luca");

            assertThrows(UseCaseLogicException.class,
                    () -> app.getMenuManager().deleteMenu(m));
        }
    }
}
