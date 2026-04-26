package catering.persistence.strategy.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import catering.businesslogic.menu.Menu;
import catering.businesslogic.menu.MenuItem;
import catering.businesslogic.menu.Section;
import catering.businesslogic.user.User;
import catering.persistence.BatchUpdateHandler;
import catering.persistence.SQLitePersistenceManager;
import catering.persistence.strategy.MenuItemPersister;
import catering.persistence.strategy.MenuPersister;
import catering.persistence.strategy.SectionPersister;
import catering.util.LogManager;

/**
 * SQLite implementation of the MenuPersister interface.
 * Handles CRUD operations for Menu objects in SQLite database.
 */
public class SQLiteMenuPersister implements MenuPersister {

    private static final Logger LOGGER = LogManager.getLogger(SQLiteMenuPersister.class);

    private final SectionPersister sectionPersister;
    private final MenuItemPersister itemPersister;

    public SQLiteMenuPersister(SectionPersister sectionPersister, MenuItemPersister itemPersister) {
        this.sectionPersister = sectionPersister;
        this.itemPersister = itemPersister;
    }

    // Organize SQL queries by operation type
    private static final class SQL {
        // Insert operations
        static final String INSERT_MENU = "INSERT INTO Menus (title, owner_id, published) VALUES (?, ?, ?);";
        static final String INSERT_MENU_FEATURE = "INSERT INTO MenuFeatures (menu_id, name, value) VALUES (?, ?, ?)";

        // Update operations
        static final String UPDATE_MENU_TITLE = "UPDATE Menus SET title = ? WHERE id = ?";
        static final String UPDATE_MENU_PUBLISHED = "UPDATE Menus SET published = ? WHERE id = ?";
        static final String UPDATE_MENU_OWNER = "UPDATE Menus SET owner_id = ? WHERE id = ?";

        // Delete operations
        static final String DELETE_MENU_FEATURES = "DELETE FROM MenuFeatures WHERE menu_id = ?";
        static final String DELETE_MENU_ITEMS = "DELETE FROM MenuItems WHERE menu_id = ?";
        static final String DELETE_MENU_SECTIONS = "DELETE FROM MenuSections WHERE menu_id = ?";
        static final String DELETE_MENU = "DELETE FROM Menus WHERE id = ?";

        // Select operations
        static final String SELECT_MENU = "SELECT * FROM Menus WHERE id = ?";
        static final String SELECT_ALL_MENU_IDS = "SELECT id FROM Menus";
        static final String SELECT_MENU_FEATURES = "SELECT * FROM MenuFeatures WHERE menu_id = ?";
        static final String SELECT_MENU_IN_USE = "SELECT COUNT(*) as count FROM services WHERE approved_menu_id = ?";
    }

    @Override
    public int insert(Menu menu) {

        insertMenuBasicInfo(menu);
        updateFeatures(menu);
        insertSectionsAndItems(menu);
        return menu.getId();
    }

    private void insertMenuBasicInfo(Menu menu) {
        SQLitePersistenceManager.executeBatchUpdate(SQL.INSERT_MENU, 1, new BatchUpdateHandler() {
            @Override
            public void handleBatchItem(PreparedStatement ps, int batchCount) throws SQLException {
                ps.setString(1, menu.getTitle());
                ps.setInt(2, menu.getOwner().getId());
                ps.setBoolean(3, menu.isPublished());
            }

            @Override
            public void handleGeneratedIds(ResultSet rs, int count) throws SQLException {
                if (count == 0) {
                    menu.setId(rs.getInt(1));
                }
            }
        });
    }

    private void insertSectionsAndItems(Menu menu) {
        if (!menu.getSections().isEmpty()) {
            sectionPersister.insert(menu.getId(), new ArrayList<>(menu.getSections()));
        }

        if (!menu.getFreeItems().isEmpty()) {
            itemPersister.insert(menu.getId(), 0, menu.getFreeItems());
        }
    }

    @Override
    public void update(Menu menu) {
        updateTitle(menu);
        updatePublished(menu);
        updateFeatures(menu);
        updateSectionPositions(menu);
        updateFreeItemPositions(menu);
    }

    @Override
    public void updateTitle(Menu menu) {
        SQLitePersistenceManager.executeUpdate(SQL.UPDATE_MENU_TITLE, menu.getTitle(), menu.getId());
    }

    @Override
    public void updatePublished(Menu menu) {
        SQLitePersistenceManager.executeUpdate(SQL.UPDATE_MENU_PUBLISHED, menu.isPublished(), menu.getId());
    }

    private static final String UPDATE_SECTION_POSITION = "UPDATE MenuSections SET position = ? WHERE id = ?";
    private static final String UPDATE_FREE_ITEM_POSITION = "UPDATE MenuItems SET position = ? WHERE id = ?";

    private void updateSectionPositions(Menu menu) {
        java.util.List<Section> sections = menu.getSections();
        for (int i = 0; i < sections.size(); i++) {
            SQLitePersistenceManager.executeUpdate(UPDATE_SECTION_POSITION, i, sections.get(i).getId());
        }
    }

    private void updateFreeItemPositions(Menu menu) {
        java.util.List<MenuItem> items = menu.getFreeItems();
        for (int i = 0; i < items.size(); i++) {
            SQLitePersistenceManager.executeUpdate(UPDATE_FREE_ITEM_POSITION, i, items.get(i).getId());
        }
    }

    @Override
    public Menu load(int menuId) {
        try {

            Menu menu = Menu.create(menuId);
            final boolean[] found = new boolean[1];

            SQLitePersistenceManager.executeQuery(SQL.SELECT_MENU, rs -> {
                populateMenu(rs, menu, menuId);
                found[0] = true;
            }, menuId);

            return found[0] ? menu : null;
        } catch (Exception e) {
            LOGGER.severe("Failed to load menu #" + menuId + ": " + e.getMessage());
            return null;
        }
    }

    private void populateMenu(ResultSet rs, Menu menu, int menuId) throws SQLException {
        menu.setTitle(rs.getString("title"));
        menu.setOwner(User.load(rs.getInt("owner_id")));
        menu.setPublished(rs.getBoolean("published"));
        menu.setIntInUse(isMenuInUse(menuId));
        menu.setSections(sectionPersister.loadAll(menuId));
        menu.setFreeItems(itemPersister.load(menuId, 0));
        menu.setFeatures(loadMenuFeatures(menuId));
    }

    @Override
    public List<Menu> loadAll() {
        List<Menu> menus = new ArrayList<>();
        SQLitePersistenceManager.executeQuery(SQL.SELECT_ALL_MENU_IDS, rs -> {
            menus.add(load(rs.getInt("id")));
        });
        return menus;
    }

    @Override
    public void delete(Menu menu) {
        int menuId = menu.getId();
        // Delete in dependency order
        SQLitePersistenceManager.executeUpdate(SQL.DELETE_MENU_ITEMS, menuId);
        SQLitePersistenceManager.executeUpdate(SQL.DELETE_MENU_SECTIONS, menuId);
        SQLitePersistenceManager.executeUpdate(SQL.DELETE_MENU_FEATURES, menuId);
        SQLitePersistenceManager.executeUpdate(SQL.DELETE_MENU, menuId);
    }

    @Override
    public void updateFeatures(Menu menu) {
        SQLitePersistenceManager.executeUpdate(SQL.DELETE_MENU_FEATURES, menu.getId());
        insertFeatures(menu);
    }

    private void insertFeatures(Menu menu) {
        Map<String, Boolean> features = menu.getFeatures();
        if (features == null || features.isEmpty()) {
            return;
        }

        String[] featureNames = features.keySet().toArray(new String[0]);
        SQLitePersistenceManager.executeSimpleBatchUpdate(
                SQL.INSERT_MENU_FEATURE,
                featureNames.length,
                (ps, index) -> {
                    String name = featureNames[index];
                    ps.setInt(1, menu.getId());
                    ps.setString(2, name);
                    ps.setBoolean(3, features.get(name));
                });
    }

    private HashMap<String, Boolean> loadMenuFeatures(int menuId) {
        HashMap<String, Boolean> features = new HashMap<>();
        SQLitePersistenceManager.executeQuery(SQL.SELECT_MENU_FEATURES, rs -> {
            features.put(rs.getString("name"), rs.getBoolean("value"));
        }, menuId);
        return features;
    }

    private boolean isMenuInUse(int menuId) {
        final int[] count = { 0 };
        SQLitePersistenceManager.executeQuery(SQL.SELECT_MENU_IN_USE, rs -> {
            count[0] = rs.getInt("count");
        }, menuId);
        return count[0] > 0;
    }
}
