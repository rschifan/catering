package catering.businesslogic.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import catering.businesslogic.recipe.KitchenProcessComponent;
import catering.businesslogic.recipe.Recipe;
import catering.businesslogic.user.User;
import catering.persistence.strategy.MenuPersister;
import catering.persistence.strategy.impl.SQLiteMenuPersister;

public class Menu {

    private static final MenuPersister persister = new SQLiteMenuPersister();

    // Feature constants
    public static final String FEATURE_NEEDS_COOK = "needsCook";
    public static final String FEATURE_FINGER_FOOD = "fingerFood";
    public static final String FEATURE_BUFFET = "buffet";
    public static final String FEATURE_WARM_DISHES = "warmDishes";
    public static final String FEATURE_NEEDS_KITCHEN = "needsKitchen";

    public static final String[] DEFAULT_FEATURES = {
            FEATURE_NEEDS_COOK,
            FEATURE_FINGER_FOOD,
            FEATURE_BUFFET,
            FEATURE_WARM_DISHES,
            FEATURE_NEEDS_KITCHEN
    };

    public static Menu create() {
        return new Menu();
    }

    public static Menu create(int menuId) {
        return new Menu(menuId);
    }

    public static Menu create(User owner, String title) {
        return new Menu(owner, title);
    }

    public static Menu create(User owner, String title, String[] features) {
        return new Menu(owner, title, features);
    }

    public static Menu create(int id, User owner, String title, String[] features) {
        Menu menu = new Menu(id, owner, title, false, false, features);
        return menu;
    }

    public static Menu create(int id, User owner, String title, boolean published, boolean inUse,
            String[] features) {
        Menu menu = new Menu(id, owner, title, published, inUse, features);
        return menu;
    }

    public static void insert(Menu m) {
        persister.insert(m);
    }

    public static Menu load(int id) {
        return persister.load(id);
    }

    public static void delete(Menu m) {
        persister.delete(m);
    }

    public static void saveTitle(Menu m) {
        persister.update(m);
    }

    public static void savePublished(Menu m) {
        persister.update(m);
    }

    public static void saveFeatures(Menu m) {
        persister.update(m);
    }

    private int id;

    private String title;

    private boolean published;

    private boolean inUse;

    private HashMap<String, Boolean> features;

    private List<MenuItem> freeItems;

    private List<Section> sections;

    private User owner;

    private Menu(int id, User owner, String title, boolean published, boolean inUse, String[] features) {
        this.id = id;
        this.title = title;
        this.owner = owner;
        this.published = published;
        this.inUse = inUse;

        this.sections = new ArrayList<Section>();
        this.freeItems = new ArrayList<MenuItem>();

        this.features = new HashMap<String, Boolean>();

        if (features == null || features.length == 0)
            features = DEFAULT_FEATURES;

        for (String feature : features) {
            this.features.put(feature, false);
        }
    }

    private Menu(User owner, String title, String[] menuFeatures) {
        this(0, owner, title, false, false, menuFeatures);
    }

    private Menu(User owner, String title) {
        this(owner, title, DEFAULT_FEATURES);
    }

    private Menu(String title) {
        this(null, title);
    }

    private Menu(int id) {
        this(id, null, null, false, false, null);
    }

    private Menu() {
    }

    public Section addSection(String name) {
        Section sec = Section.create(name);
        this.sections.add(sec);
        return sec;
    }

    public Section getSection(int position) {
        if (position < 0 || position >= sections.size()) {
            throw new IndexOutOfBoundsException("Invalid section position");
        }
        return this.sections.get(position);
    }

    public Section getSectionById(int id) {
        for (Section sec : sections) {
            if (sec.getId() == id) {
                return sec;
            }
        }
        return null;
    }

    public Section getSection(String name) {
        for (Section sec : sections) {
            if (sec.getName().equals(name)) {
                return sec;
            }
        }
        return null;
    }

    public Section getSection(MenuItem mi) {
        for (Section sec : sections) {
            if (sec.getItemPosition(mi) >= 0) {
                return sec;
            }
        }
        if (freeItems.contains(mi)) {
            return null;
        }
        throw new IllegalArgumentException("MenuItem not found in this menu");
    }

    public boolean hasSection(Section sec) {
        return this.sections.contains(sec);
    }

    public int getSectionPosition(Section sec) {
        return this.sections.indexOf(sec);
    }

    public List<Section> getSections() {
        return this.sections;
    }

    public void removeSection(Section s, boolean deleteItems) {
        if (!deleteItems) {
            this.freeItems.addAll(s.getItems());
        }
        this.sections.remove(s);
    }

    public int getSectionCount() {
        return sections.size();
    }

    public MenuItem addItem(Recipe recipe, Section sec, String desc) {
        MenuItem mi = MenuItem.create(recipe, desc);
        if (sec != null) {
            sec.addItem(mi);
        } else {
            this.freeItems.add(mi);
        }
        return mi;
    }

    public List<MenuItem> getFreeItems() {
        return this.freeItems;
    }

    public int getFreeItemPosition(MenuItem mi) {
        return freeItems.indexOf(mi);
    }

    public int getFreeItemCount() {
        return freeItems.size();
    }

    public void moveFreeItem(MenuItem mi, int position) {

    }

    public void changeItemSection(MenuItem mi, Section oldSec, Section newSec) {
        if (oldSec == null) {
            freeItems.remove(mi);
        } else {
            oldSec.removeItem(mi);
        }

        if (newSec == null) {
            freeItems.add(mi);
        } else {
            newSec.addItem(mi);
        }
    }

    public void removeItem(MenuItem mi) {
        Section sec = getSection(mi);
        if (sec == null) {
            freeItems.remove(mi);
        } else {
            sec.removeItem(mi);
        }
    }

    public void updateFreeItems(ArrayList<MenuItem> newItems) {
        ArrayList<MenuItem> updatedList = new ArrayList<>();
        for (MenuItem mi : newItems) {
            MenuItem prev = findItemById(mi.getId());
            if (prev == null) {
                updatedList.add(mi);
            } else {
                prev.setDescription(mi.getDescription());
                prev.setRecipe(mi.getRecipe());
                updatedList.add(prev);
            }
        }
        this.freeItems.clear();
        this.freeItems.addAll(updatedList);
    }

    public ArrayList<MenuItem> getItems() {
        ArrayList<MenuItem> allItems = new ArrayList<>();
        allItems.addAll(this.freeItems);

        for (Section section : this.sections) {
            allItems.addAll(section.getItems());
        }

        return allItems;
    }

    public ArrayList<KitchenProcessComponent> getKitchenProcesses() {

        System.out.println("cazzo: " + this.title);

        ArrayList<KitchenProcessComponent> allKitchenProcesses = new ArrayList<>();

        for (MenuItem item : this.getItems()) {
            System.out.println("Item: " + item.getDescription());
            System.out.println("Recipe: " + item.getRecipe());
            Recipe recipe = item.getRecipe();
            allKitchenProcesses.add(recipe);
            allKitchenProcesses.addAll(recipe.getChildren());
        }

        return allKitchenProcesses;
    }

    public void initializeDefaultFeatures() {
        for (String feature : DEFAULT_FEATURES) {
            if (!features.containsKey(feature)) {
                features.put(feature, false);
            }
        }
    }

    public Map<String, Boolean> getFeatures() {
        return this.features;
    }

    public boolean getFeature(String feature) {
        return this.features.getOrDefault(feature, false);
    }

    public void setFeature(String feature, boolean val) {
        if (this.features.containsKey(feature)) {
            this.features.put(feature, val);
        }
    }

    public void setFeatures(Map<String, Boolean> features) {
        for (Map.Entry<String, Boolean> entry : features.entrySet()) {
            setFeature(entry.getKey(), entry.getValue());
        }
    }

    // Specific feature getters and setters
    public boolean needsCook() {
        return getFeature(FEATURE_NEEDS_COOK);
    }

    public void setNeedsCook(boolean value) {
        setFeature(FEATURE_NEEDS_COOK, value);
    }

    public boolean isFingerFood() {
        return getFeature(FEATURE_FINGER_FOOD);
    }

    public void setFingerFood(boolean value) {
        setFeature(FEATURE_FINGER_FOOD, value);
    }

    public boolean isBuffet() {
        return getFeature(FEATURE_BUFFET);
    }

    public void setBuffet(boolean value) {
        setFeature(FEATURE_BUFFET, value);
    }

    public boolean hasWarmDishes() {
        return getFeature(FEATURE_WARM_DISHES);
    }

    public void setWarmDishes(boolean value) {
        setFeature(FEATURE_WARM_DISHES, value);
    }

    public boolean needsKitchen() {
        return getFeature(FEATURE_NEEDS_KITCHEN);
    }

    public void setNeedsKitchen(boolean value) {
        setFeature(FEATURE_NEEDS_KITCHEN, value);
    }

    public boolean requiresKitchenPreparation() {
        return needsKitchen() || needsCook() || hasWarmDishes();
    }

    public int getId() {
        return id;
    }

    // ===== BASIC GETTERS AND SETTERS =====

    public User getOwner() {
        return this.owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isInUse() {
        return this.inUse;
    }

    public void setIntInUse(boolean inUse) {
        this.inUse = inUse;
    }

    public boolean isOwner(User u) {
        return u.getId() == this.owner.getId();
    }

    public void setSections(List<Section> sections) {
        this.sections = sections;
    }

    public void setFreeItems(List<MenuItem> menuItems) {
        this.freeItems = menuItems;
    }

    // ===== UTILITY METHODS =====

    public Menu deepCopy() {

        Menu copy = new Menu(this.owner, this.title, DEFAULT_FEATURES);

        copy.published = this.published;
        copy.inUse = this.inUse;

        for (Map.Entry<String, Boolean> entry : this.features.entrySet())
            copy.features.put(entry.getKey(), entry.getValue());

        for (Section sec : this.sections)
            copy.sections.add(sec.deepCopy());

        for (MenuItem mi : this.freeItems)
            copy.freeItems.add(mi.deepCopy());

        return copy;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(this.title)
                .append(" (autore: ")
                .append(this.owner != null ? this.owner.getUserName() : "unknown")
                .append("), ")
                .append(published ? "pubblicato" : "non pubblicato")
                .append(", ")
                .append(inUse ? "in uso" : "non in uso");

        // Add features information
        for (String f : features.keySet()) {
            result.append("\n").append(f).append(": ").append(features.get(f));
        }

        // Add sections
        if (!sections.isEmpty()) {
            result.append("\n\nSections:");
            for (Section sec : sections) {
                result.append("\n").append(sec.toString());
            }
        }

        // Add free items if any
        if (!freeItems.isEmpty()) {
            result.append("\n\nVOCI LIBERE:");
            for (MenuItem mi : freeItems) {
                result.append("\n\t").append(mi.toString());
            }
        }

        return result.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Menu other = (Menu) obj;
        if (id != other.id)
            return false;
        if (published != other.published)
            return false;
        if (inUse != other.inUse)
            return false;
        if (title == null) {
            if (other.title != null)
                return false;
        } else if (!title.equals(other.title))
            return false;
        if (owner == null) {
            if (other.owner != null)
                return false;
        } else if (!owner.equals(other.owner))
            return false;
        if (features == null) {
            if (other.features != null)
                return false;
        } else if (!features.equals(other.features))
            return false;
        if (freeItems == null) {
            if (other.freeItems != null)
                return false;
        } else if (!freeItems.equals(other.freeItems))
            return false;
        if (sections == null) {
            if (other.sections != null)
                return false;
        } else if (!sections.equals(other.sections))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + id;
        result = 31 * result + (title != null ? title.hashCode() : 0);
        result = 31 * result + Boolean.hashCode(published);
        result = 31 * result + Boolean.hashCode(inUse);
        result = 31 * result + (owner != null ? owner.hashCode() : 0);
        result = 31 * result + (features != null ? features.hashCode() : 0);
        result = 31 * result + (freeItems != null ? freeItems.hashCode() : 0);
        result = 31 * result + (sections != null ? sections.hashCode() : 0);
        return result;
    }

    private MenuItem findItemById(int id) {
        for (MenuItem mi : freeItems) {
            if (mi.getId() == id) {
                return mi;
            }
        }
        return null;
    }

}
