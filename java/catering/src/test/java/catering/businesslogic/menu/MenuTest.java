package catering.businesslogic.menu;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import catering.businesslogic.recipe.Recipe;
import catering.businesslogic.user.User;

/**
 * Domain-level tests for the {@link Menu} aggregate. Each test focuses on
 * one behaviour of the aggregate root in isolation — no manager, no
 * persistence, no observer.
 */
class MenuTest {

    private User chef;
    private Menu menu;

    @BeforeEach
    void setUp() {
        chef = new User("chef");
        chef.setId(1);
        chef.addRole(User.Role.CHEF);
        menu = Menu.create(chef, "Test Menu");
    }

    @Test
    void isOwner_sameUser_returnsTrue() {
        assertTrue(menu.isOwner(chef));
    }

    @Test
    void isOwner_differentUser_returnsFalse() {
        User other = new User("other");
        other.setId(2);
        assertFalse(menu.isOwner(other));
    }

    @Test
    void addSection_appendsSectionAndIncrementsCount() {
        Section s = menu.addSection("Antipasti");

        assertEquals("Antipasti", s.getName());
        assertEquals(1, menu.getSectionCount());
        assertTrue(menu.hasSection(s));
    }

    @Test
    void hasSection_orphanSection_returnsFalse() {
        Section orphan = Section.create("Orphan");
        assertFalse(menu.hasSection(orphan));
    }

    @Test
    void addItem_withSection_attachesItemToSection() {
        Section s = menu.addSection("Antipasti");
        Recipe r = new Recipe("Bruschetta");

        MenuItem mi = menu.addItem(r, s, "House bruschetta");

        assertEquals(1, s.getItems().size());
        assertEquals(0, menu.getFreeItems().size());
        assertEquals(s, menu.getSection(mi));
    }

    @Test
    void addItem_nullSection_attachesItemToFreeItems() {
        Recipe r = new Recipe("Olives");

        MenuItem mi = menu.addItem(r, null, "Olives");

        assertEquals(1, menu.getFreeItems().size());
        assertNull(menu.getSection(mi));
    }

    @Test
    void getSectionCount_freshMenu_returnsZero() {
        assertEquals(0, menu.getSectionCount());
    }

    @Test
    void isInUse_freshMenu_returnsFalse() {
        assertFalse(menu.isInUse());
    }

    @Test
    void deepCopy_clonesStructureButSharesRecipes() {
        Section s = menu.addSection("Antipasti");
        Recipe r = new Recipe("Bruschetta");
        MenuItem original = menu.addItem(r, s, "Tomato bruschetta");

        Menu copy = menu.deepCopy();

        assertEquals(menu.getSectionCount(), copy.getSectionCount());
        MenuItem copiedItem = copy.getSections().get(0).getItems().get(0);
        assertNotSame(original, copiedItem,
                "deepCopy must clone the menu structure (fresh MenuItem instance)");
        assertEquals(original.getDescription(), copiedItem.getDescription());
        assertSame(original.getRecipe(), copiedItem.getRecipe(),
                "Recipe is shared by design: deepCopy clones the menu, not the recipe book");
    }
}
