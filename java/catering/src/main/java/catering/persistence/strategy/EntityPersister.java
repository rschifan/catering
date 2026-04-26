package catering.persistence.strategy;

import java.util.List;

/**
 * Strategy role for persistence: defines the operations every entity persister
 * must support. Entity-specific insert overloads (those that need extra
 * parameters like a parent id or a position) live on the subtype interfaces.
 *
 * @param <T> The entity type this persister handles
 */
public interface EntityPersister<T> {

    void update(T entity);

    T load(int id);

    List<T> loadAll();

    void delete(T entity);
}
