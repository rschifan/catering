package catering.businesslogic.recipe;

import java.util.List;

import catering.persistence.strategy.PreparationPersister;
import catering.persistence.strategy.RecipePersister;

/**
 * Use-case controller (GRASP) for recipe and preparation operations.
 * Holds the two persister Strategies as constructor-injected dependencies,
 * mirroring {@link catering.businesslogic.menu.MenuManager}.
 */
public class RecipeManager {

    private final RecipePersister recipePersister;
    private final PreparationPersister preparationPersister;

    public RecipeManager(RecipePersister recipePersister, PreparationPersister preparationPersister) {
        this.recipePersister = recipePersister;
        this.preparationPersister = preparationPersister;
    }

    public List<Recipe> getRecipeBook() {
        return recipePersister.loadAll();
    }

    public Recipe loadRecipe(int id) {
        return recipePersister.load(id);
    }

    public Preparation loadPreparation(int id) {
        return preparationPersister.load(id);
    }
}
