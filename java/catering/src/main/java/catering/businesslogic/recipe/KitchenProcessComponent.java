package catering.businesslogic.recipe;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/**
 * Component role of the Composite pattern. Modeled on the safety variant in
 * {@code teoria/JavaGoF/src/strutturali/composite/structure_abstractclass/Component.java}:
 * a single abstract class as the root, structural operations declared abstract,
 * leaves throw the checked {@link KitchenProcessException} from add/remove.
 */
public abstract class KitchenProcessComponent {

    protected int id;
    protected String name;
    protected String description;

    protected KitchenProcessComponent(int id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    protected KitchenProcessComponent() {
        this(0, "", "");
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /** Discriminator used by KitchenTask for persistence type encoding. */
    public boolean isRecipe() {
        return false;
    }

    public abstract void add(KitchenProcessComponent p) throws KitchenProcessException;

    public abstract void remove(KitchenProcessComponent p) throws KitchenProcessException;

    public abstract List<KitchenProcessComponent> getChildren();

    public boolean hasChildren() {
        return !getChildren().isEmpty();
    }

    public boolean isLeaf() {
        return !hasChildren();
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        KitchenProcessComponent other = (KitchenProcessComponent) obj;
        if (getId() > 0 && other.getId() > 0) {
            return getId() == other.getId();
        }
        if (!Objects.equals(getName(), other.getName()) ||
                !Objects.equals(getDescription(), other.getDescription())) {
            return false;
        }
        Set<KitchenProcessComponent> s1 = new HashSet<>(getChildren());
        Set<KitchenProcessComponent> s2 = new HashSet<>(other.getChildren());
        return s1.equals(s2);
    }

    @Override
    public int hashCode() {
        if (getId() > 0) {
            return Integer.hashCode(getId());
        }
        Set<KitchenProcessComponent> childSet = new HashSet<>(getChildren());
        return Objects.hash(getName(), getDescription(), childSet);
    }
}
