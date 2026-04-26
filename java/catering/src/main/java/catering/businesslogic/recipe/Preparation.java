package catering.businesslogic.recipe;

import java.util.Collections;
import java.util.List;

/**
 * Leaf of the kitchen-process Composite hierarchy. Pure domain class —
 * persistence is owned by
 * {@link catering.persistence.strategy.PreparationPersister} (Strategy pattern,
 * wired in {@link catering.businesslogic.CatERing}).
 */
public class Preparation extends KitchenProcessComponent {

    public Preparation(String name) {
        super();
        this.name = name;
    }

    /** Hydration constructor used by the persister. */
    public Preparation(int id, String name, String description) {
        super(id, name, description);
    }

    @Override
    public void add(KitchenProcessComponent p) throws KitchenProcessException {
        throw new KitchenProcessException("Cannot add child to a Preparation");
    }

    @Override
    public void remove(KitchenProcessComponent p) throws KitchenProcessException {
        throw new KitchenProcessException("Cannot remove child from a Preparation");
    }

    @Override
    public List<KitchenProcessComponent> getChildren() {
        return Collections.emptyList();
    }
}
