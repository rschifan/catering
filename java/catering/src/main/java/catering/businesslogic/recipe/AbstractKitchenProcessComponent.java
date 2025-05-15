package catering.businesslogic.recipe;

import java.util.*;

public abstract class AbstractKitchenProcessComponent implements KitchenProcessComponent {
    protected int id;
    protected String name;
    protected String description;
    protected List<KitchenProcessComponent> children = new ArrayList<>();

    public AbstractKitchenProcessComponent(int id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public AbstractKitchenProcessComponent() {
        this(0, "", "");
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRecipe() {
        return false;
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public boolean isLeaf() {
        return !hasChildren();
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public List<KitchenProcessComponent> getChildren() {
        return children;
    }

    @Override
    public void add(KitchenProcessComponent p) {
        children.add(p);
    }

    @Override
    public void remove(KitchenProcessComponent p) {
        children.remove(p);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        AbstractKitchenProcessComponent other = (AbstractKitchenProcessComponent) obj;
        // If both have valid IDs, compare by ID
        if (getId() > 0 && other.getId() > 0) {
            return getId() == other.getId();
        }
        // Compare name + description
        if (!Objects.equals(getName(), other.getName()) ||
                !Objects.equals(getDescription(), other.getDescription())) {
            return false;
        }
        // Compare children (order-insensitive)
        Set<KitchenProcessComponent> s1 = new HashSet<>(getChildren());
        Set<KitchenProcessComponent> s2 = new HashSet<>(other.getChildren());
        return s1.equals(s2);
    }

    @Override
    public int hashCode() {
        if (getId() > 0) {
            return Integer.hashCode(getId());
        }
        // Include children set in hash
        Set<KitchenProcessComponent> childSet = new HashSet<>(getChildren());
        return Objects.hash(getName(), getDescription(), childSet);
    }

}
