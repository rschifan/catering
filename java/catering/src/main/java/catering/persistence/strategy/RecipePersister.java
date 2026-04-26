package catering.persistence.strategy;

import catering.businesslogic.recipe.Recipe;

/**
 * Strategy role for Recipe persistence. Implementations move all SQL out of
 * the {@link Recipe} domain class. Modeled on {@link MenuPersister}.
 */
public interface RecipePersister extends EntityPersister<Recipe> {

    void insert(Recipe recipe);
}
