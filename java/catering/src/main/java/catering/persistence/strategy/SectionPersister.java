package catering.persistence.strategy;

import java.util.ArrayList;
import java.util.List;

import catering.businesslogic.menu.Section;

/**
 * Interface for Section persistence operations.
 * Defines methods needed to save and load sections from a data store.
 */
public interface SectionPersister extends EntityPersister<Section> {

    public List<Section> loadAll(int menuId);

    public void insert(int menuId, Section section, int position);

    public void insert(int menuId, ArrayList<Section> sections);
}
