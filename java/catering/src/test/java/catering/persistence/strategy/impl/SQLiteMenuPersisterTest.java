package catering.persistence.strategy.impl;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import catering.businesslogic.CatERing;
import catering.businesslogic.menu.Menu;
import catering.businesslogic.menu.MenuItem;
import catering.businesslogic.menu.Section;
import catering.businesslogic.recipe.Recipe;
import catering.businesslogic.user.User;
import catering.persistence.SQLitePersistenceManager;
import catering.persistence.strategy.MenuItemPersister;
import catering.persistence.strategy.MenuPersister;
import catering.persistence.strategy.PreparationPersister;
import catering.persistence.strategy.RecipePersister;
import catering.persistence.strategy.SectionPersister;

/**
 * Integration tests for {@link SQLiteMenuPersister} against the seeded
 * SQLite database. Each test creates its own menu, exercises one CRUD
 * scenario, and cleans up — so tests are independent and can run in any
 * order.
 */
class SQLiteMenuPersisterTest {

    private static MenuPersister persister;
    private static User chef;
    private static Recipe bruschetta;
    private static Recipe caprese;

    @BeforeAll
    static void initializeRuntime() {
        SQLitePersistenceManager.initializeDatabase("database/catering_init_sqlite.sql");
        persister = wireMenuPersisterChain();
        chef = User.load(5);
        bruschetta = CatERing.getInstance().getRecipeManager().loadRecipe(1);
        caprese = CatERing.getInstance().getRecipeManager().loadRecipe(2);

        assertNotNull(chef, "fixture: chef must exist in the seed data");
        assertTrue(chef.isChef(), "fixture: user 5 must have the Chef role");
        assertNotNull(bruschetta, "fixture: recipe 1 (bruschetta) must exist in the seed data");
        assertNotNull(caprese, "fixture: recipe 2 (caprese) must exist in the seed data");
    }

    /** Wires the Strategy composition leaves-first, mirroring {@code CatERing}. */
    private static MenuPersister wireMenuPersisterChain() {
        PreparationPersister preparationPersister = new SQLitePreparationPersister();
        RecipePersister recipePersister = new SQLiteRecipePersister(preparationPersister);
        MenuItemPersister itemPersister = new SQLiteMenuItemPersister(recipePersister);
        SectionPersister sectionPersister = new SQLiteSectionPersister(itemPersister);
        return new SQLiteMenuPersister(sectionPersister, itemPersister);
    }

    private Menu menu;

    @BeforeEach
    void freshUnsavedMenu() {
        menu = Menu.create(chef, "Test Menu");
        Section appetizers = menu.addSection("Appetizers");
        menu.addItem(bruschetta, appetizers, "Bruschetta");
        menu.addItem(caprese, appetizers, "Caprese");
        menu.setPublished(true);
        menu.setBuffet(true);
    }

    @Test
    void insert_assignsAGeneratedIdAndPersistsTheMenu() {
        int newId = persister.insert(menu);

        assertTrue(newId > 0, "insert must return the generated primary key");
        assertEquals(menu.getId(), newId, "the menu instance must reflect the generated id");
        assertNotNull(persister.load(newId), "the inserted menu must be loadable by id");

        persister.delete(menu);
    }

    @Test
    void load_byKnownId_returnsAnEqualMenu() {
        persister.insert(menu);

        Menu loaded = persister.load(menu.getId());

        assertNotNull(loaded);
        assertEquals(menu.getId(), loaded.getId());
        assertEquals(menu.getTitle(), loaded.getTitle());
        assertEquals(menu, loaded);

        persister.delete(menu);
    }

    @Test
    void load_byUnknownId_returnsNull() {
        assertNull(persister.load(999_999));
    }

    @Test
    void update_persistsTitleAndPublishedChanges() {
        persister.insert(menu);
        menu.setTitle("Updated Title");
        menu.setPublished(false);

        persister.update(menu);

        Menu reloaded = persister.load(menu.getId());
        assertEquals("Updated Title", reloaded.getTitle());
        assertFalse(reloaded.isPublished());

        persister.delete(menu);
    }

    @Test
    void insert_withMultipleSectionsAndFreeItems_roundTripsTheStructure() {
        Menu complex = Menu.create(chef, "Complex Menu");
        Section starters = complex.addSection("Starters");
        Section mains = complex.addSection("Main Dishes");
        Section desserts = complex.addSection("Desserts");
        complex.addItem(bruschetta, starters, "Bruschetta Special");
        complex.addItem(caprese, starters, "Caprese Salad");
        complex.addItem(bruschetta, mains, "Pasta Dish");
        complex.addItem(caprese, mains, "Steak Dish");
        complex.addItem(bruschetta, desserts, "Tiramisu");
        complex.addItem(bruschetta, null, "Free Item 1");
        complex.addItem(caprese, null, "Free Item 2");

        int id = persister.insert(complex);
        Menu loaded = persister.load(id);

        assertEquals(3, loaded.getSectionCount());
        assertEquals(2, loaded.getFreeItemCount());
        assertEquals(2, loaded.getSection(0).getItemsCount());
        assertEquals(2, loaded.getSection(1).getItemsCount());
        assertEquals(1, loaded.getSection(2).getItemsCount());

        persister.delete(loaded);
    }

    @Test
    void insert_withOnlyFreeItems_persistsThemAsFreeItems() {
        Menu freeOnly = Menu.create(chef, "Free Items Only");
        freeOnly.addItem(bruschetta, null, "Free Bruschetta");
        freeOnly.addItem(caprese, null, "Free Caprese");
        freeOnly.addItem(bruschetta, null, "Another Free Item");

        int id = persister.insert(freeOnly);
        Menu loaded = persister.load(id);

        assertEquals(0, loaded.getSectionCount());
        assertEquals(3, loaded.getFreeItemCount());
        assertTrue(containsItemDescribed(loaded, "Free Bruschetta"));
        assertTrue(containsItemDescribed(loaded, "Free Caprese"));

        persister.delete(loaded);
    }

    @Test
    void insert_withFeatures_roundTripsTheFeatureFlags() {
        Menu withFeatures = Menu.create(chef, "Features Test Menu");
        withFeatures.setBuffet(true);
        withFeatures.setFingerFood(true);
        withFeatures.setNeedsCook(false);
        withFeatures.setNeedsKitchen(true);
        withFeatures.setWarmDishes(false);

        int id = persister.insert(withFeatures);
        Menu loaded = persister.load(id);

        assertTrue(loaded.isBuffet());
        assertTrue(loaded.isFingerFood());
        assertFalse(loaded.needsCook());
        assertTrue(loaded.needsKitchen());
        assertFalse(loaded.hasWarmDishes());

        persister.delete(loaded);
    }

    private static boolean containsItemDescribed(Menu menu, String description) {
        for (MenuItem item : menu.getFreeItems()) {
            if (description.equals(item.getDescription())) {
                return true;
            }
        }
        return false;
    }
}
