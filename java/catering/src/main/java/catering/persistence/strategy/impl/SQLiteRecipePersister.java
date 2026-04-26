package catering.persistence.strategy.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import catering.businesslogic.recipe.KitchenProcessComponent;
import catering.businesslogic.recipe.Preparation;
import catering.businesslogic.recipe.Recipe;
import catering.persistence.SQLitePersistenceManager;
import catering.persistence.strategy.PreparationPersister;
import catering.persistence.strategy.RecipePersister;

/**
 * SQLite implementation of the {@link RecipePersister} Strategy.
 * Composes {@link PreparationPersister} (constructor-injected) to load the
 * children of a Recipe, mirroring how {@link SQLiteMenuPersister} composes
 * {@link catering.persistence.strategy.SectionPersister}.
 */
public class SQLiteRecipePersister implements RecipePersister {

    private static final class SQL {
        static final String SELECT_BY_ID = "SELECT * FROM Recipes WHERE id = ?";
        static final String SELECT_ALL = "SELECT * FROM Recipes";
        static final String INSERT = "INSERT INTO Recipes (name, description) VALUES (?, ?)";
        static final String UPDATE = "UPDATE Recipes SET name = ?, description = ? WHERE id = ?";
        static final String DELETE = "DELETE FROM Recipes WHERE id = ?";
        static final String SELECT_PREPARATION_IDS = "SELECT preparation_id FROM RecipePreparations WHERE recipe_id = ?";
        static final String DELETE_PREPARATIONS = "DELETE FROM RecipePreparations WHERE recipe_id = ?";
        static final String INSERT_PREPARATION = "INSERT INTO RecipePreparations (recipe_id, preparation_id) VALUES (?, ?)";
    }

    private final PreparationPersister preparationPersister;

    public SQLiteRecipePersister(PreparationPersister preparationPersister) {
        this.preparationPersister = preparationPersister;
    }

    @Override
    public void insert(Recipe recipe) {
        if (recipe.getId() != 0) {
            return; // already persisted
        }
        int newId = SQLitePersistenceManager.executeInsert(
                SQL.INSERT, recipe.getName(), recipe.getDescription());
        recipe.setId(newId);
        savePreparationLinks(recipe);
    }

    @Override
    public void update(Recipe recipe) {
        SQLitePersistenceManager.executeUpdate(
                SQL.UPDATE, recipe.getName(), recipe.getDescription(), recipe.getId());
        savePreparationLinks(recipe);
    }

    @Override
    public Recipe load(int id) {
        Recipe[] holder = new Recipe[1];
        SQLitePersistenceManager.executeQuery(SQL.SELECT_BY_ID, rs -> {
            holder[0] = new Recipe(rs.getInt("id"), rs.getString("name"), rs.getString("description"));
        }, id);
        if (holder[0] != null) {
            attachPreparations(holder[0]);
        }
        return holder[0];
    }

    @Override
    public List<Recipe> loadAll() {
        List<Recipe> recipes = new ArrayList<>();
        SQLitePersistenceManager.executeQuery(SQL.SELECT_ALL, rs -> {
            recipes.add(new Recipe(rs.getInt("id"), rs.getString("name"), rs.getString("description")));
        });
        for (Recipe recipe : recipes) {
            attachPreparations(recipe);
        }
        Collections.sort(recipes, Comparator.comparing(Recipe::getName));
        return recipes;
    }

    @Override
    public void delete(Recipe recipe) {
        SQLitePersistenceManager.executeUpdate(SQL.DELETE_PREPARATIONS, recipe.getId());
        SQLitePersistenceManager.executeUpdate(SQL.DELETE, recipe.getId());
    }

    private void attachPreparations(Recipe recipe) {
        List<Integer> prepIds = new ArrayList<>();
        SQLitePersistenceManager.executeQuery(SQL.SELECT_PREPARATION_IDS, rs -> {
            prepIds.add(rs.getInt("preparation_id"));
        }, recipe.getId());
        for (int prepId : prepIds) {
            Preparation prep = preparationPersister.load(prepId);
            if (prep != null) {
                // recipe.add does not declare throws — only Preparation.add (the leaf) throws.
                recipe.add(prep);
            }
        }
    }

    private void savePreparationLinks(Recipe recipe) {
        SQLitePersistenceManager.executeUpdate(SQL.DELETE_PREPARATIONS, recipe.getId());
        for (KitchenProcessComponent child : recipe.getChildren()) {
            if (child.getId() > 0) {
                SQLitePersistenceManager.executeUpdate(SQL.INSERT_PREPARATION, recipe.getId(), child.getId());
            }
        }
    }
}
