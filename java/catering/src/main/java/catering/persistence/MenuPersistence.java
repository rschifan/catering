package catering.persistence;

import java.util.ArrayList;

import catering.businesslogic.menu.Menu;
import catering.businesslogic.menu.MenuEventReceiver;
import catering.businesslogic.menu.MenuItem;
import catering.businesslogic.menu.Section;
import catering.persistence.strategy.MenuItemPersister;
import catering.persistence.strategy.MenuPersister;
import catering.persistence.strategy.SectionPersister;

/**
 * Observer (MenuEventReceiver) that persists changes notified by MenuManager.
 * Holds the three persisters as injected dependencies (Strategy pattern, per
 * {@code teoria/JavaGoF/.../strategy/myarray/MyArray.java}).
 */
public class MenuPersistence implements MenuEventReceiver {

    private final MenuPersister menuPersister;
    private final SectionPersister sectionPersister;
    private final MenuItemPersister itemPersister;

    public MenuPersistence(MenuPersister menuPersister,
                           SectionPersister sectionPersister,
                           MenuItemPersister itemPersister) {
        this.menuPersister = menuPersister;
        this.sectionPersister = sectionPersister;
        this.itemPersister = itemPersister;
    }

    @Override
    public void updateMenuCreated(Menu m) {
        menuPersister.insert(m);
    }

    @Override
    public void updateSectionAdded(Menu m, Section sec) {
        sectionPersister.insert(m.getId(), sec, m.getSectionPosition(sec));
    }

    @Override
    public void updateMenuItemAdded(Menu m, MenuItem mi) {
        Section sec = m.getSection(mi);
        int sec_id = (sec == null ? 0 : sec.getId());
        int pos = (sec == null ? m.getFreeItemPosition(mi) : sec.getItemPosition(mi));
        itemPersister.insert(m.getId(), sec_id, mi, pos);
    }

    @Override
    public void updateMenuFeaturesChanged(Menu m) {
        menuPersister.updateFeatures(m);
    }

    @Override
    public void updateMenuTitleChanged(Menu m) {
        menuPersister.updateTitle(m);
    }

    @Override
    public void updateMenuPublishedState(Menu m) {
        menuPersister.updatePublished(m);
    }

    @Override
    public void updateMenuDeleted(Menu m) {
        menuPersister.delete(m);
    }

    @Override
    public void updateSectionDeleted(Menu m, Section s, boolean itemsDeleted) {
        sectionPersister.delete(s);
        if (!itemsDeleted)
            itemPersister.insert(m.getId(), 0, new ArrayList<>(s.getItems()));
    }

    @Override
    public void updateSectionChangedName(Menu m, Section s) {
        sectionPersister.update(s);
    }

    @Override
    public void updateMenuItemChanged(Menu m, Section s, MenuItem mi) {
        int sid = (s == null ? 0 : s.getId());
        itemPersister.update(sid, mi);
    }

    @Override
    public void updateMenuItemDescriptionChanged(Menu m, MenuItem mi) {
        itemPersister.update(mi);
    }

    @Override
    public void updateMenuItemDeleted(Menu m, Section sec, MenuItem mi) {
        itemPersister.delete(mi);
    }
}
