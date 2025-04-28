package catering.businesslogic.menu;

import java.util.List;

import catering.businesslogic.recipe.Recipe;
import catering.persistence.strategy.MenuItemPersister;
import catering.persistence.strategy.impl.SQLiteMenuItemPersister;

public class MenuItem {

    private static MenuItemPersister persister = new SQLiteMenuItemPersister();

    public static void insert(int menuId, int sectionId, List<MenuItem> items) {
        persister.insert(menuId, sectionId, items);
    }

    public static void insert(int menuId, int sectionId, MenuItem item, int position) {
        persister.insert(menuId, sectionId, item, position);
    }

    public static List<MenuItem> loadMenuItems(int menuId, int sectionId) {
        return persister.load(menuId, sectionId);
    }

    public static void saveSection(int sectionId, MenuItem item) {
        persister.update(sectionId, item);
    }

    public static void saveDescription(MenuItem item) {
        persister.update(item);
    }

    public static void removeItem(MenuItem item) {
        persister.delete(item);
    }

    public static MenuItem create(Recipe rec) {
        return new MenuItem(rec);
    }

    public static MenuItem create(Recipe rec, String desc) {
        return new MenuItem(rec, desc);
    }

    public static MenuItem create(MenuItem toCopy) {
        return new MenuItem(toCopy);
    }

    public static MenuItem create() {
        return new MenuItem();
    }

    private int id;
    private String description;
    private Recipe recipe;

    private MenuItem(Recipe rec) {
        this(rec, rec.getName());
    }

    private MenuItem(Recipe rec, String desc) {
        id = 0;
        recipe = rec;
        description = desc;
    }

    private MenuItem(MenuItem toCopy) {
        this(toCopy.recipe, toCopy.description);
    }

    private MenuItem() {
        this(null, null);
    }

    public int getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public void setRecipe(Recipe itemRecipe) {
        this.recipe = itemRecipe;
    }

    public MenuItem deepCopy() {

        MenuItem copy = new MenuItem(this.recipe, this.description);

        copy.id = this.id;

        return copy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // Add the description
        sb.append(description);

        // Add recipe information if available
        if (recipe != null) {
            sb.append(" (Recipe: ");
            sb.append(recipe.getName());
            sb.append(")");
        } else {
            sb.append(" (No recipe assigned)");
        }

        // Add ID if the item has been persisted
        if (id > 0) {
            sb.append(" [ID: ");
            sb.append(id);
            sb.append("]");
        }

        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;

        MenuItem other = (MenuItem) obj;

        // If both items have valid IDs, compare by ID
        if (this.id > 0 && other.id > 0) {
            return this.id == other.id;
        }

        // Otherwise, compare by description and recipe
        boolean descriptionMatch = (this.description == null && other.description == null) ||
                (this.description != null && this.description.equals(other.description));

        boolean recipeMatch = (this.recipe == null && other.recipe == null) ||
                (this.recipe != null && this.recipe.equals(other.recipe));

        return descriptionMatch && recipeMatch;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;

        // Use ID if it's valid
        if (id > 0) {
            result = prime * result + id;
        } else {
            // Otherwise use description and recipe
            result = prime * result + (description != null ? description.hashCode() : 0);
            result = prime * result + (recipe != null ? recipe.hashCode() : 0);
        }

        return result;
    }

    public void setId(int iid) {
        this.id = iid;
    }

}
