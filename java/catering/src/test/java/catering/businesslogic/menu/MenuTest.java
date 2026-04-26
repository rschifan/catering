package catering.businesslogic.menu;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;

import catering.businesslogic.CatERing;
import catering.businesslogic.user.User;

public class MenuTest {

    private Menu testMenu;
    private User testOwner;

    @BeforeAll
    public static void initRuntime() {
        CatERing.getInstance();
    }

    @BeforeEach
    public void setUp() {
        testOwner = new User("TestChef");
        testOwner.addRole(User.Role.CHEF);

        testMenu = Menu.create(testOwner, "Test Menu");
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
