package catering.persistence.strategy;

import catering.businesslogic.recipe.Preparation;

/**
 * Strategy role for Preparation persistence. Implementations move all SQL out
 * of the {@link Preparation} domain class. Modeled on {@link MenuPersister}.
 */
public interface PreparationPersister extends EntityPersister<Preparation> {

    void insert(Preparation preparation);
}
