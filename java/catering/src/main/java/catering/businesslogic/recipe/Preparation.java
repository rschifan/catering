package catering.businesslogic.recipe;

import catering.persistence.SQLitePersistenceManager;
import catering.persistence.ResultHandler;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Preparation represents an intermediate food preparation step.
 */
public class Preparation extends AbstractKitchenProcess {

    /**
     * Default constructor for loading from DB
     */
    private Preparation() {
    }

    /**
     * Creates a new preparation with the given name
     *
     * @param name The preparation name
     */
    public Preparation(String name) {
        this.id = 0;
        this.name = name;
    }

    @Override
    public void add(KitchenProcess p) {
        throw new UnsupportedOperationException("Cannot add child to a Preparation");
    }

    @Override
    public void remove(KitchenProcess p) {
        throw new UnsupportedOperationException("Cannot remove child from a Preparation");
    }

    @Override
    public List<KitchenProcess> getChildren() {
        return Collections.emptyList();
    }

    /**
     * Loads all preparations from the database
     *
     * @return List of all preparations
     */
    public static ArrayList<Preparation> loadAllPreparations() {
        ArrayList<Preparation> preparations = new ArrayList<>();

        String query = "SELECT * FROM Preparations";
        SQLitePersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                Preparation prep = new Preparation(rs.getString("name"));
                prep.id = rs.getInt("id");

                // Load additional properties if they exist in DB
                try {
                    prep.description = rs.getString("description");
                } catch (SQLException e) {
                    prep.description = "";
                }

                preparations.add(prep);
            }
        });

        return preparations;
    }

    /**
     * Gets all preparations from the database
     *
     * @return List of all preparations
     */
    public static ArrayList<Preparation> getAllPreparations() {
        return loadAllPreparations();
    }

    /**
     * Loads a preparation by its ID
     *
     * @param id The preparation ID
     * @return The loaded preparation or null if not found
     */
    public static Preparation loadPreparationById(int id) {
        Preparation[] prepHolder = new Preparation[1]; // Use array to allow modification in lambda
        String query = "SELECT * FROM Preparations WHERE id = ?";

        SQLitePersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                Preparation prep = new Preparation();
                prep.name = rs.getString("name");
                prep.id = id;
                // Load additional properties if they exist in DB
                try {
                    prep.description = rs.getString("description");
                } catch (SQLException e) {
                    prep.description = "";
                }
                prepHolder[0] = prep;
            }
        }, id); // Pass id as parameter

        return prepHolder[0];
    }

    /**
     * Saves a new preparation to the database
     *
     * @return true if successful, false otherwise
     */
    public boolean save() {
        if (id != 0)
            return false; // Already exists

        String query = "INSERT INTO Preparations (name, description) VALUES(?, ?)";

        SQLitePersistenceManager.executeUpdate(query, name, description);
        id = SQLitePersistenceManager.getLastId();
        return true;
    }

    /**
     * Updates an existing preparation in the database
     *
     * @return true if successful, false otherwise
     */
    public boolean update() {
        if (id == 0)
            return false; // Not in DB

        String query = "UPDATE Preparations SET name = ?, description = ? WHERE id = ?";

        int rows = SQLitePersistenceManager.executeUpdate(query, name, description, id);
        return rows > 0;
    }

    /**
     * Gets recipes that use this preparation
     *
     * @return List of recipes using this preparation
     */
    public List<Recipe> getUsedInRecipes() {
        List<Recipe> result = new ArrayList<>();

        if (id == 0)
            return result; // Not in DB

        String query = "SELECT recipe_id FROM RecipePreparations WHERE preparation_id = ?";
        SQLitePersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                int recipeId = rs.getInt("recipe_id");
                Recipe recipe = Recipe.loadRecipe(recipeId);
                if (recipe != null) {
                    result.add(recipe);
                }
            }
        }, id); // Pass id as parameter

        return result;
    }
}
