package catering.persistence.strategy.impl;

import java.util.ArrayList;
import java.util.List;

import catering.businesslogic.recipe.Preparation;
import catering.persistence.SQLitePersistenceManager;
import catering.persistence.strategy.PreparationPersister;

/**
 * SQLite implementation of the {@link PreparationPersister} Strategy.
 * Self-contained: Preparation has no associations to other persisted entities.
 */
public class SQLitePreparationPersister implements PreparationPersister {

    private static final class SQL {
        static final String SELECT_BY_ID = "SELECT * FROM Preparations WHERE id = ?";
        static final String SELECT_ALL = "SELECT * FROM Preparations";
        static final String INSERT = "INSERT INTO Preparations (name, description) VALUES (?, ?)";
        static final String UPDATE = "UPDATE Preparations SET name = ?, description = ? WHERE id = ?";
        static final String DELETE = "DELETE FROM Preparations WHERE id = ?";
    }

    @Override
    public void insert(Preparation preparation) {
        if (preparation.getId() != 0) {
            return; // already persisted
        }
        int newId = SQLitePersistenceManager.executeInsert(
                SQL.INSERT, preparation.getName(), preparation.getDescription());
        preparation.setId(newId);
    }

    @Override
    public void update(Preparation preparation) {
        SQLitePersistenceManager.executeUpdate(
                SQL.UPDATE, preparation.getName(), preparation.getDescription(), preparation.getId());
    }

    @Override
    public Preparation load(int id) {
        Preparation[] holder = new Preparation[1];
        SQLitePersistenceManager.executeQuery(SQL.SELECT_BY_ID, rs -> {
            holder[0] = new Preparation(rs.getInt("id"), rs.getString("name"), rs.getString("description"));
        }, id);
        return holder[0];
    }

    @Override
    public List<Preparation> loadAll() {
        List<Preparation> result = new ArrayList<>();
        SQLitePersistenceManager.executeQuery(SQL.SELECT_ALL, rs -> {
            result.add(new Preparation(rs.getInt("id"), rs.getString("name"), rs.getString("description")));
        });
        return result;
    }

    @Override
    public void delete(Preparation preparation) {
        SQLitePersistenceManager.executeUpdate(SQL.DELETE, preparation.getId());
    }
}
