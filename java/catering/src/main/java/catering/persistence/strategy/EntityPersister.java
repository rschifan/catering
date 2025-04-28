package catering.persistence.strategy;

import java.util.List;

/**
 * Generic interface for entity persistence operations
 * 
 * @param <T> The entity type this persister handles
 */
public interface EntityPersister<T> {
    /**
     * Inserts a new entity into the persistence store
     * 
     * @param entity The entity to insert
     * @return The ID of the inserted entity
     */
    int insert(T entity);

    /**
     * Updates an existing entity in the persistence store
     * 
     * @param entity The entity to update
     */
    void update(T entity);

    /**
     * Loads an entity by its ID
     * 
     * @param id The ID of the entity to load
     * @return The loaded entity or null if not found
     */
    T load(int id);

    /**
     * Loads all entities of this type
     * 
     * @return A list of all entities
     */
    List<T> loadAll();

    /**
     * Deletes an entity from the persistence store
     * 
     * @param entity The entity to delete
     */
    void delete(T entity);
}
