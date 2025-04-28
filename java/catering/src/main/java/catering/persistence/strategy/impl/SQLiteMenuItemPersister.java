package catering.persistence.strategy.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import catering.businesslogic.menu.MenuItem;
import catering.businesslogic.recipe.Recipe;
import catering.persistence.ResultHandler;
import catering.persistence.SQLitePersistenceManager;
import catering.persistence.strategy.MenuItemPersister;
import catering.util.LogManager;

public class SQLiteMenuItemPersister implements MenuItemPersister {

    private static final Logger LOGGER = LogManager.getLogger(SQLiteMenuItemPersister.class);

    // Organize SQL queries by operation type
    private static final class SQL {
        // Insert operations
        static final String INSERT_MENU_ITEM = "INSERT INTO MenuItems (menu_id, section_id, description, recipe_id, position) VALUES (?, ?, ?, ?, ?)";

        // Update operations
        static final String UPDATE_MENU_ITEM_SECTION = "UPDATE MenuItems SET section_id = ? WHERE id = ?";
        static final String UPDATE_MENU_ITEM_DESCRIPTION = "UPDATE MenuItems SET description = ? WHERE id = ?";

        // Delete operations
        static final String DELETE_MENU_ITEM = "DELETE FROM MenuItems WHERE id = ?";

        // Select operations
        static final String SELECT_MENU_ITEM = "SELECT * FROM MenuItems WHERE id = ?";
        static final String SELECT_ALL_MENU_ITEMS = "SELECT * FROM MenuItems";
        static final String SELECT_MENU_ITEMS_BY_SECTION = "SELECT * FROM MenuItems WHERE menu_id = ? AND section_id = ? ORDER BY position";
    }

    public void insert(int menuId, int sectionId, List<MenuItem> items) {
        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            insert(menuId, sectionId, item, i);
        }
    }

    public void insert(int menuId, int sectionId, MenuItem item, int position) {
        SQLitePersistenceManager.executeUpdate(
                SQL.INSERT_MENU_ITEM,
                menuId,
                sectionId,
                item.getDescription(),
                item.getRecipe().getId(),
                position);

        item.setId(SQLitePersistenceManager.getLastId());
    }

    public void update(int sectionId, MenuItem item) {
        SQLitePersistenceManager.executeUpdate(SQL.UPDATE_MENU_ITEM_SECTION, sectionId, item.getId());
    }

    @Override
    public int insert(MenuItem item) {
        throw new UnsupportedOperationException("Unimplemented method 'insert'");
    }

    @Override
    public void update(MenuItem item) {
        SQLitePersistenceManager.executeUpdate(SQL.UPDATE_MENU_ITEM_DESCRIPTION, item.getDescription(), item.getId());
    }

    @Override
    public void delete(MenuItem item) {
        SQLitePersistenceManager.executeUpdate(SQL.DELETE_MENU_ITEM, item.getId());
    }

    @Override
    public MenuItem load(int id) {
        String queryString = SQL.SELECT_MENU_ITEM;
        MenuItem item = MenuItem.create();
        SQLitePersistenceManager.executeQuery(queryString, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                item.setId(rs.getInt("id"));
                item.setDescription(rs.getString("description"));
            }
        }, id);
        return item;
    }

    @Override
    public List<MenuItem> loadAll() {
        List<MenuItem> result = new ArrayList<>();
        SQLitePersistenceManager.executeQuery(SQL.SELECT_ALL_MENU_ITEMS, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                MenuItem item = MenuItem.create();
                item.setId(rs.getInt("id"));
                item.setDescription(rs.getString("description"));
                result.add(item);
            }
        });
        return result;
    }

    public List<MenuItem> load(int menuId, int sectionId) {
        List<MenuItem> result = new ArrayList<>();
        List<Integer> recids = new ArrayList<>();

        SQLitePersistenceManager.executeQuery(SQL.SELECT_MENU_ITEMS_BY_SECTION, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                MenuItem item = MenuItem.create();
                item.setId(rs.getInt("id"));
                item.setDescription(rs.getString("description"));
                result.add(item);
                recids.add(rs.getInt("recipe_id"));
            }
        }, menuId, sectionId);

        for (int i = 0; i < result.size(); i++) {
            result.get(i).setRecipe(Recipe.loadRecipe(recids.get(i)));
        }

        return result;
    }
}
