package catering.businesslogic.menu;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;

import catering.businesslogic.recipe.Recipe;
import catering.businesslogic.user.User;

public class MenuTest {

    private Menu testMenu;
    private User testOwner;
    private Recipe testRecipe;

    @BeforeEach
    public void setUp() {
        // Create test user with chef role
        testOwner = new User("TestChef");
        testOwner.addRole(User.Role.CHEF);

        // Create test menu
        testMenu = Menu.create(testOwner, "Test Menu");

        // Mock a test recipe
        testRecipe = Recipe.loadRecipe(1); // Assuming recipe ID 1 exists in test DB
    }

    @Test
    @DisplayName("Menu creation with correct properties")
    public void testCreateMenu() {
        assertNotNull(testMenu, "Menu should be created");
        assertEquals("Test Menu", testMenu.getTitle(), "Menu name should match");
        assertEquals(testOwner, testMenu.getOwner(), "Menu owner should match");
        assertTrue(testMenu.getSections().isEmpty(), "New menu should have no sections");
        assertTrue(testMenu.getFreeItems().isEmpty(), "New menu should have no free items");

    }

}
