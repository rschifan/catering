package catering.businesslogic.recipe;

import catering.persistence.SQLitePersistenceManager;
import catering.persistence.ResultHandler;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Recipe is a composite of KitchenProcess; holds only Preparation leaves.
 */
public class Recipe extends AbstractKitchenProcess {

    private Recipe() {
        super();
    }

    public Recipe(String name) {
        this();
        this.name = name;
    }

    @Override
    public boolean isRecipe() {
        return true;
    }

    @Override
    public void add(KitchenProcess p) {
        if (p.isRecipe()) {
            throw new UnsupportedOperationException("Cannot add Recipe to Recipe");
        }
        children.add(p);
    }

    /**
     * Loads all recipes from the database
     * 
     * @return List of all recipes
     */
    public static ArrayList<Recipe> loadAllRecipes() {
        ArrayList<Recipe> recipes = new ArrayList<>();

        String query = "SELECT * FROM Recipes";
        SQLitePersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                Recipe rec = new Recipe(rs.getString("name"));
                rec.id = rs.getInt("id");

                // Load additional properties if they exist in DB
                try {
                    rec.description = rs.getString("description");
                } catch (SQLException e) {
                    rec.description = "";
                }
                recipes.add(rec);
            }
        });

        // Load preparations for each recipe
        for (Recipe recipe : recipes) {
            loadPreparationsForRecipe(recipe);
        }

        // Sort recipes by name
        Collections.sort(recipes, new Comparator<Recipe>() {
            @Override
            public int compare(Recipe o1, Recipe o2) {
                return (o1.getName().compareTo(o2.getName()));
            }
        });

        return recipes;
    }

    /**
     * Gets all recipes from the database
     * 
     * @return List of all recipes
     */
    public static ArrayList<Recipe> getAllRecipes() {
        return loadAllRecipes();
    }

    /**
     * Loads a recipe by its ID
     * 
     * @param id The recipe ID
     * @return The loaded recipe or null if not found
     */
    public static Recipe loadRecipe(int id) {
        Recipe[] recHolder = new Recipe[1]; // Use array to allow modification in lambda
        String query = "SELECT * FROM Recipes WHERE id = ?";

        SQLitePersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                Recipe rec = new Recipe();
                rec.name = rs.getString("name");
                rec.id = id;
                // Load additional properties if they exist in DB
                try {
                    rec.description = rs.getString("description");
                } catch (SQLException e) {
                    rec.description = "";
                }
                recHolder[0] = rec;
            }
        }, id); // Pass id as parameter

        // Load preparations for this recipe
        Recipe recipe = recHolder[0];
        if (recipe != null) {
            loadPreparationsForRecipe(recipe);
        }

        return recipe;
    }

    /**
     * Loads a recipe by its name
     * 
     * @param name The recipe name
     * @return The loaded recipe or null if not found
     */
    public static Recipe loadRecipe(String name) {
        Recipe[] recHolder = new Recipe[1]; // Use array to allow modification in lambda
        String query = "SELECT * FROM Recipes WHERE name = ?";

        SQLitePersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                Recipe rec = new Recipe();
                rec.name = rs.getString("name");
                rec.id = rs.getInt("id");

                try {
                    rec.description = rs.getString("description");
                } catch (SQLException e) {
                    rec.description = "";
                }
                recHolder[0] = rec;
            }
        }, name); // Pass name as parameter

        // Load preparations for this recipe
        Recipe recipe = recHolder[0];
        if (recipe != null) {
            loadPreparationsForRecipe(recipe);
        }

        return recipe;
    }

    /**
     * Loads preparations for a specific recipe
     * 
     * @param recipe The recipe to load preparations for
     */
    private static void loadPreparationsForRecipe(Recipe recipe) {
        String query = "SELECT preparation_id FROM RecipePreparations WHERE recipe_id = ?";
        SQLitePersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                Preparation prep = Preparation.loadPreparationById(rs.getInt("preparation_id"));
                if (prep != null) {
                    recipe.add(prep);
                }
            }
        }, recipe.getId());
    }

    /**
     * Saves a new recipe to the database
     * 
     * @return true if successful, false otherwise
     */
    public boolean save() {
        if (id != 0)
            return false; // Already exists

        String query = "INSERT INTO Recipes (name, description) VALUES(?, ?)";

        SQLitePersistenceManager.executeUpdate(query, name, description);
        id = SQLitePersistenceManager.getLastId();

        // Save recipe-preparation relationships
        savePreparationRelationships();

        return true;
    }

    /**
     * Updates an existing recipe in the database
     * 
     * @return true if successful, false otherwise
     */
    public boolean update() {
        if (id == 0)
            return false; // Not in DB

        String query = "UPDATE Recipes SET name = ?, description = ? WHERE id = ?";

        int rows = SQLitePersistenceManager.executeUpdate(query, name, description, id);

        // Update recipe-preparation relationships
        savePreparationRelationships();

        return rows > 0;
    }

    private void savePreparationRelationships() {
        if (id == 0)
            return;
        String deleteQuery = "DELETE FROM RecipePreparations WHERE recipe_id = ?";
        SQLitePersistenceManager.executeUpdate(deleteQuery, id);

        for (KitchenProcess kp : children) {
            if (!kp.isRecipe() && kp.getId() > 0) {
                String insertQuery = "INSERT INTO RecipePreparations (recipe_id, preparation_id) VALUES(?, ?)";
                SQLitePersistenceManager.executeUpdate(insertQuery, id, kp.getId());
            }
        }
    }
}
