package catering.persistence.strategy.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import catering.businesslogic.menu.MenuItem;
import catering.businesslogic.menu.Section;
import catering.persistence.BatchUpdateHandler;
import catering.persistence.ResultHandler;
import catering.persistence.SQLitePersistenceManager;
import catering.persistence.strategy.SectionPersister;

/**
 * SQLite implementation of the SectionPersister interface.
 * Handles database operations for Section objects using SQLite.
 */
public class SQLiteSectionPersister implements SectionPersister {

    // Organize SQL queries by operation type
    private static final class SQL {
        // Select operations
        static final String SELECT_SECTION_BY_ID = "SELECT * FROM MenuSections WHERE id = ?";
        static final String SELECT_ALL_SECTIONS = "SELECT * FROM MenuSections";
        static final String SELECT_SECTIONS_BY_MENU = "SELECT * FROM MenuSections WHERE menu_id = ? ORDER BY position";

        // Insert operations
        static final String INSERT_SECTION = "INSERT INTO MenuSections (menu_id, name, position) VALUES (?, ?, ?)";

        // Update operations
        static final String UPDATE_SECTION_NAME = "UPDATE MenuSections SET name = ? WHERE id = ?";
        static final String UPDATE_MENU_ITEM_POSITION = "UPDATE MenuItems SET position = ? WHERE id = ?";

        // Delete operations
        static final String DELETE_SECTION_ITEMS = "DELETE FROM MenuItems WHERE section_id = ?";
        static final String DELETE_SECTION = "DELETE FROM MenuSections WHERE id = ?";
    }

    @Override
    public Section load(int sectionId) {
        Section[] result = new Section[1];

        SQLitePersistenceManager.executeQuery(SQL.SELECT_SECTION_BY_ID, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                Section s = Section.create(rs.getString("name"));
                s.setId(rs.getInt("id"));
                result[0] = s;
            }
        }, sectionId);

        if (result[0] != null) {
            result[0].setItems(MenuItem.loadMenuItems(-1, result[0].getId()));
        }

        return result[0];
    }

    @Override
    public ArrayList<Section> loadAll() {

        ArrayList<Section> result = new ArrayList<>();

        SQLitePersistenceManager.executeQuery(SQL.SELECT_ALL_SECTIONS, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                Section s = Section.create(rs.getString("name"));
                s.setId(rs.getInt("id"));
                s.setItems(MenuItem.loadMenuItems(rs.getInt("menu_id"), s.getId()));
                result.add(s);
            }
        });

        return result;
    }

    @Override
    public ArrayList<Section> loadAll(int menuId) {
        ArrayList<Section> result = new ArrayList<>();

        SQLitePersistenceManager.executeQuery(SQL.SELECT_SECTIONS_BY_MENU, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                Section s = Section.create(rs.getString("name"));
                s.setId(rs.getInt("id"));
                s.setItems(MenuItem.loadMenuItems(menuId, s.getId()));
                result.add(s);
            }
        }, menuId);

        return result;
    }

    @Override
    public void insert(int menuId, Section section, int position) {

        SQLitePersistenceManager.executeUpdate(SQL.INSERT_SECTION, menuId, section.getName(), position);

        section.setId(SQLitePersistenceManager.getLastId());

        if (section.getItems().size() > 0) {
            MenuItem.insert(menuId, section.getId(), section.getItems());
        }
    }

    @Override
    public void insert(int menuId, ArrayList<Section> sections) {

        for (int i = 0; i < sections.size(); i++) {
            Section section = sections.get(i);
            insert(menuId, section, i);
        }

    }

    @Override
    public void update(Section section) {
        SQLitePersistenceManager.executeUpdate(SQL.UPDATE_SECTION_NAME, section.getName(), section.getId());
    }

    public void updateItemsOrder(Section section) {

        SQLitePersistenceManager.executeBatchUpdate(SQL.UPDATE_MENU_ITEM_POSITION, section.getItems().size(),
                new BatchUpdateHandler() {
                    @Override
                    public void handleBatchItem(PreparedStatement ps, int batchCount) throws SQLException {
                        ps.setInt(1, batchCount);
                        ps.setInt(2, section.getItems().get(batchCount).getId());
                    }

                    @Override
                    public void handleGeneratedIds(ResultSet rs, int count) throws SQLException {
                        // no generated ids to handle
                    }
                });
    }

    @Override
    public void delete(Section section) {
        SQLitePersistenceManager.executeUpdate(SQL.DELETE_SECTION_ITEMS, section.getId());
        SQLitePersistenceManager.executeUpdate(SQL.DELETE_SECTION, section.getId());
    }

    @Override
    public int insert(Section entity) {
        throw new UnsupportedOperationException("Unimplemented method 'insert'");
    }

}
