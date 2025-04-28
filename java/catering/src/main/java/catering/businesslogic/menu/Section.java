package catering.businesslogic.menu;

import java.util.ArrayList;
import java.util.List;

import catering.persistence.strategy.SectionPersister;
import catering.persistence.strategy.impl.SQLiteSectionPersister;

public class Section {

    private static final SectionPersister persister = new SQLiteSectionPersister();

    public static void insert(int menuid, Section sec, int posInMenu) {
        persister.insert(menuid, sec, posInMenu);
    }

    public static void insert(int menuid, List<Section> sections) {
        persister.insert(menuid, new ArrayList<>(sections));
    }

    public static List<Section> loadSections(int menu_id) {
        return persister.loadAll(menu_id);
    }

    public static void deleteSection(Section s) {
        persister.delete(s);
    }

    public static void saveSectionName(Section s) {
        persister.update(s);
    }

    public static void saveItemOrder(Section s) {
        persister.update(s);
    }

    public static Section create(String name) {
        return new Section(name);
    }

    public static Section create(Section original) {
        return original.deepCopy();
    }

    private int id;
    private String name;
    private List<MenuItem> sectionItems;

    public final static int DEFAULT_ID = -1;

    private Section(String name) {
        id = DEFAULT_ID;
        this.name = name;
        sectionItems = new ArrayList<>();
    }

    private Section(Section toCopy) {
        this(toCopy.name);

        for (MenuItem mi : toCopy.sectionItems) {
            this.sectionItems.add(mi.deepCopy());
        }
    }

    public void addItem(MenuItem mi) {
        this.sectionItems.add(mi);
    }

    public void updateItems(ArrayList<MenuItem> newItems) {
        ArrayList<MenuItem> updatedList = new ArrayList<>();
        for (int i = 0; i < newItems.size(); i++) {
            MenuItem mi = newItems.get(i);
            MenuItem prev = this.findItemById(mi.getId());
            if (prev == null) {
                updatedList.add(mi);
            } else {
                prev.setDescription(mi.getDescription());
                prev.setRecipe(mi.getRecipe());
                updatedList.add(prev);
            }
        }
        this.sectionItems.clear();
        this.sectionItems.addAll(updatedList);
    }

    public int getItemPosition(MenuItem mi) {
        return this.sectionItems.indexOf(mi);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // Add section header with ID and name
        sb.append(String.format("Section [id=%d, name='%s']", id, name));

        // Add items count
        sb.append(String.format(" - %d items", sectionItems.size()));

        // Add section items if any exist
        if (!sectionItems.isEmpty()) {
            sb.append(":\n");
            for (int i = 0; i < sectionItems.size(); i++) {
                MenuItem item = sectionItems.get(i);
                sb.append(String.format("  %d. %s", (i + 1), item.toString()));

                // Add newline for all but the last item
                if (i < sectionItems.size() - 1) {
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<MenuItem> getItems() {
        return this.sectionItems;
    }

    public void setItems(List<MenuItem> items) {
        this.sectionItems = items;
    }

    public int getItemsCount() {
        return sectionItems.size();
    }

    public void moveItem(MenuItem mi, int position) {
        sectionItems.remove(mi);
        sectionItems.add(position, mi);
    }

    public void removeItem(MenuItem mi) {
        sectionItems.remove(mi);
    }

    private MenuItem findItemById(int id) {
        for (MenuItem mi : sectionItems) {
            if (mi.getId() == id)
                return mi;
        }
        return null;
    }

    public Section deepCopy() {
        Section copy = new Section(this.name);

        copy.id = this.id;

        for (MenuItem mi : this.getItems()) {
            copy.addItem(mi.deepCopy());
        }
        return copy;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;

        Section other = (Section) obj;

        // If both sections have valid IDs, compare by ID
        if (this.id > 0 && other.id > 0) {
            return this.id == other.id;
        }

        // Otherwise, compare by name and items
        boolean nameMatch = (this.name == null && other.name == null) ||
                (this.name != null && this.name.equals(other.name));

        // If names don't match, sections are not equal
        if (!nameMatch)
            return false;

        // If item counts differ, sections are not equal
        if (this.sectionItems.size() != other.sectionItems.size())
            return false;

        // Compare items in order (assuming order matters)
        for (int i = 0; i < this.sectionItems.size(); i++) {
            MenuItem thisItem = this.sectionItems.get(i);
            MenuItem otherItem = other.sectionItems.get(i);
            if (!thisItem.equals(otherItem))
                return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;

        if (id > 0) {
            result = prime * result + id;
        } else {

            result = prime * result + (name != null ? name.hashCode() : 0);
            result = prime * result + sectionItems.size();

            if (!sectionItems.isEmpty()) {
                result = prime * result + sectionItems.get(0).hashCode();
                if (sectionItems.size() > 1) {
                    result = prime * result + sectionItems.get(sectionItems.size() - 1).hashCode();
                }
            }
        }

        return result;
    }
}
