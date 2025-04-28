package catering.persistence.bridge.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestInstance;

import catering.businesslogic.menu.Menu;
import catering.businesslogic.menu.MenuItem;
import catering.businesslogic.menu.Section;
import catering.businesslogic.recipe.Recipe;
import catering.businesslogic.user.User;
import catering.persistence.strategy.MenuPersister;
import catering.persistence.strategy.impl.SQLiteMenuPersister;
import catering.util.LogManager;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // Allows non-static @BeforeAll methods
public class SQLiteMenuPersisterTest {

    private static final Logger LOGGER = LogManager.getLogger(SQLiteMenuPersisterTest.class);

    private MenuPersister persister;
    private Menu testMenu;
    private int testMenuId;
    private User testChef;
    private Recipe bruschetta;
    private Recipe caprese;

    @BeforeAll
    public void setUpOnce() {
        // Initialize the persister
        persister = new SQLiteMenuPersister();

        // Load an existing chef user from database
        testChef = User.load(5);
        assertNotNull(testChef, "Test setup failed: chef not found in database");
        assertTrue(testChef.isChef(), "Test user must have chef role");

        // Load test recipes
        bruschetta = Recipe.loadRecipe(1);
        caprese = Recipe.loadRecipe(2);

        assertNotNull(bruschetta, "Test setup failed: recipe not found in database");
        assertNotNull(caprese, "Test setup failed: recipe not found in database");

        // Create a test menu
        testMenu = Menu.create(testChef, "SQLite Persister Test Menu");

        // Add sections and items to the menu
        Section appetizers = testMenu.addSection("Appetizers");
        testMenu.addItem(bruschetta, appetizers, "Bruschetta");
        testMenu.addItem(caprese, appetizers, "Caprese");
        testMenu.setPublished(true);
        testMenu.setBuffet(true);
    }

    @BeforeEach
    public void setUp() {
        if (testMenuId > 0) {
            testMenu.setId(testMenuId);
        }
    }

    @Test
    @Order(1)
    @DisplayName("Create a new menu and save it to the database")
    public void testCreateMenu() {

        testMenuId = persister.insert(testMenu);

        assertTrue(testMenuId > 0, "Menu should be inserted successfully");
        assertNotNull(persister.load(testMenuId), "Loaded menu should not be null after insertion");
        assertEquals(testMenu.getId(), testMenuId, "Inserted menu ID should match the test menu ID");
    }

    @Test
    @Order(2)
    @DisplayName("Load menu from database")
    public void testLoadMenu() {

        Menu loadedMenu = persister.load(testMenu.getId());

        assertNotNull(loadedMenu, "Loaded menu should not be null");
        assertEquals(testMenu.getId(), loadedMenu.getId(), "Loaded menu ID should match");
        assertEquals(testMenu.getTitle(), loadedMenu.getTitle(), "Loaded menu title should match");
        assertEquals(testMenu, loadedMenu);
    }

    @Test
    @Order(3)
    @DisplayName("Update menu in the database")
    public void testUpdateMenu() {

        testMenu.setTitle("Updated Menu Title");
        testMenu.setPublished(false);

        persister.update(testMenu);

        Menu updatedMenu = persister.load(testMenu.getId());

        assertNotNull(updatedMenu, "Updated menu should not be null");
        assertEquals("Updated Menu Title", updatedMenu.getTitle(), "Updated menu title should match");
        assertEquals(false, updatedMenu.isPublished(), "Updated menu published status should match");
    }

    @Test
    @Order(5)
    @DisplayName("Load non-existent menu returns null")
    public void testLoadNonExistentMenu() {
        // Try to load a menu with a non-existent ID
        int nonExistentId = 999999;
        Menu loadedMenu = persister.load(nonExistentId);

        // Verify the result is null
        assertNull(loadedMenu, "Loading non-existent menu should return null");
    }

    @Test
    @Order(6)
    @DisplayName("Insert menu with complex structure")
    public void testInsertComplexMenu() {
        // Create a complex menu with multiple sections and items
        Menu complexMenu = Menu.create(testChef, "Complex Structure Menu");

        // Add multiple sections
        Section appetizers = complexMenu.addSection("Starters");
        Section mainCourse = complexMenu.addSection("Main Dishes");
        Section desserts = complexMenu.addSection("Desserts");

        // Add items to sections (directly without storing references)
        complexMenu.addItem(bruschetta, appetizers, "Bruschetta Special");
        complexMenu.addItem(caprese, appetizers, "Caprese Salad");
        complexMenu.addItem(bruschetta, mainCourse, "Pasta Dish");
        complexMenu.addItem(caprese, mainCourse, "Steak Dish");
        complexMenu.addItem(bruschetta, desserts, "Tiramisu");

        // Add free items (not in any section)
        complexMenu.addItem(bruschetta, null, "Special Item 1");
        complexMenu.addItem(caprese, null, "Special Item 2");

        // Insert the menu
        int menuId = persister.insert(complexMenu);

        // Verify insertion was successful
        assertTrue(menuId > 0, "Complex menu should be inserted successfully");

        // Load the inserted menu
        Menu loadedMenu = persister.load(menuId);
        assertNotNull(loadedMenu, "Loaded menu should not be null");

        // Verify sections count
        assertEquals(3, loadedMenu.getSectionCount(), "Should have 3 sections");

        // Verify free items count
        assertEquals(2, loadedMenu.getFreeItemCount(), "Should have 2 free items");

        // Verify items in sections
        assertEquals(2, loadedMenu.getSection(0).getItemsCount(), "First section should have 2 items");
        assertEquals(2, loadedMenu.getSection(1).getItemsCount(), "Second section should have 2 items");
        assertEquals(1, loadedMenu.getSection(2).getItemsCount(), "Third section should have 1 item");

        // Clean up
        persister.delete(loadedMenu);
    }

    @Test
    @Order(7)
    @DisplayName("Insert menu with only free items")
    public void testInsertMenuWithOnlyFreeItems() {
        // Create a menu with only free items (no sections)
        Menu freeItemsMenu = Menu.create(testChef, "Free Items Only Menu");

        // Add several free items
        freeItemsMenu.addItem(bruschetta, null, "Free Bruschetta");
        freeItemsMenu.addItem(caprese, null, "Free Caprese");
        freeItemsMenu.addItem(bruschetta, null, "Another Free Item");

        // Insert the menu
        int menuId = persister.insert(freeItemsMenu);

        // Verify insertion was successful
        assertTrue(menuId > 0, "Free items menu should be inserted successfully");

        // Load the inserted menu
        Menu loadedMenu = persister.load(menuId);
        assertNotNull(loadedMenu, "Loaded menu should not be null");

        // Verify no sections
        assertEquals(0, loadedMenu.getSectionCount(), "Should have no sections");

        // Verify free items count
        assertEquals(3, loadedMenu.getFreeItemCount(), "Should have 3 free items");

        // Verify free item descriptions
        boolean foundBruschetta = false;
        boolean foundCaprese = false;

        for (MenuItem item : loadedMenu.getFreeItems()) {
            if (item.getDescription().equals("Free Bruschetta")) {
                foundBruschetta = true;
            } else if (item.getDescription().equals("Free Caprese")) {
                foundCaprese = true;
            }
        }

        assertTrue(foundBruschetta, "Should find 'Free Bruschetta' item");
        assertTrue(foundCaprese, "Should find 'Free Caprese' item");

        // Clean up
        persister.delete(loadedMenu);
    }

    @Test
    @Order(8)
    @DisplayName("Test loading menu with features")
    public void testLoadMenuWithFeatures() {
        // Create a menu with specific features enabled
        Menu featuresMenu = Menu.create(testChef, "Features Test Menu");

        // Set specific features
        featuresMenu.setBuffet(true);
        featuresMenu.setFingerFood(true);
        featuresMenu.setNeedsCook(false);
        featuresMenu.setNeedsKitchen(true);
        featuresMenu.setWarmDishes(false);

        // Insert the menu
        int menuId = persister.insert(featuresMenu);

        // Verify insertion was successful
        assertTrue(menuId > 0, "Features menu should be inserted successfully");

        // Load the inserted menu
        Menu loadedMenu = persister.load(menuId);
        assertNotNull(loadedMenu, "Loaded menu should not be null");

        // Verify features were preserved
        assertTrue(loadedMenu.isBuffet(), "Buffet feature should be true");
        assertTrue(loadedMenu.isFingerFood(), "FingerFood feature should be true");
        assertFalse(loadedMenu.needsCook(), "NeedsCook feature should be false");
        assertTrue(loadedMenu.needsKitchen(), "NeedsKitchen feature should be true");
        assertFalse(loadedMenu.hasWarmDishes(), "WarmDishes feature should be false");

        // Clean up
        persister.delete(loadedMenu);
    }
}
