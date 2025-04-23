package catering.businesslogic.recipe;

import java.util.List;

/**
 * KitchenProcess represents any food preparation activity in the kitchen.
 */
public interface KitchenProcess {

    int getId();

    String getName();

    String getDescription();

    boolean isRecipe();

    void add(KitchenProcess p);

    void remove(KitchenProcess p);

    List<KitchenProcess> getChildren();

    boolean hasChildren();

    boolean isLeaf();
}
