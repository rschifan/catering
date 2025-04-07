package catering.businesslogic.recipe;

import java.util.ArrayList;

public class RecipeManager {

    public RecipeManager() {

    }

    public ArrayList<Recipe> getRecipes() {
        return Recipe.getAllRecipes();
    }
}
