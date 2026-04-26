package catering.persistence.strategy;

import catering.businesslogic.menu.Menu;

public interface MenuPersister extends EntityPersister<Menu> {

    int insert(Menu menu);

    void updateTitle(Menu menu);

    void updatePublished(Menu menu);

    void updateFeatures(Menu menu);
}
