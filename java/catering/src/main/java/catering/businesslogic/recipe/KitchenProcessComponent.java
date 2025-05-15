package catering.businesslogic.recipe;

import java.util.List;

/**
 * KitchenProcessComponent represents any food preparation activity in the
 * kitchen.
 */
public interface KitchenProcessComponent {

    int getId();

    String getName();

    String getDescription();

    boolean isRecipe();

    void add(KitchenProcessComponent p);

    void remove(KitchenProcessComponent p);

    List<KitchenProcessComponent> getChildren();

    boolean hasChildren();

    boolean isLeaf();
}
