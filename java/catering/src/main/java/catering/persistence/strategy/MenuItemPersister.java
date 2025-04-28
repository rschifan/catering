package catering.persistence.strategy;

import java.util.List;

import catering.businesslogic.menu.MenuItem;

public interface MenuItemPersister extends EntityPersister<MenuItem> {

    public void insert(int menuid, int sectionid, List<MenuItem> items);

    public void insert(int menuId, int sectionId, MenuItem item, int position);

    public void update(int sectionId, MenuItem item);

    public List<MenuItem> load(int menuId, int sectionId);

}
