package catering.businesslogic.recipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Composite node of the kitchen-process hierarchy. Holds {@link Preparation}
 * children. Pure domain class — persistence is owned by
 * {@link catering.persistence.strategy.RecipePersister} (Strategy pattern,
 * wired in {@link catering.businesslogic.CatERing}).
 */
public class Recipe extends KitchenProcessComponent {

    private final List<KitchenProcessComponent> children = new ArrayList<>();

    public Recipe(String name) {
        super();
        this.name = name;
    }

    /** Hydration constructor used by the persister. */
    public Recipe(int id, String name, String description) {
        super(id, name, description);
    }

    @Override
    public boolean isRecipe() {
        return true;
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
    public List<KitchenProcessComponent> getChildren() {
        return children;
    }
}
