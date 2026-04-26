package catering.businesslogic.menu;

import java.util.ArrayList;

import catering.businesslogic.CatERing;
import catering.businesslogic.Preconditions;
import catering.businesslogic.UseCaseLogicException;
import catering.businesslogic.recipe.Recipe;
import catering.businesslogic.user.User;
import catering.persistence.strategy.MenuPersister;

/**
 * Use-case controller (GRASP) for "Gestire menù". Holds the
 * {@link MenuPersister} Strategy via constructor injection and notifies any
 * registered {@link MenuEventReceiver} observers after every state change.
 * <p>
 * Precondition checks delegate to {@link Preconditions} so that the contract
 * layer reads as the contract slide it enforces.
 */
public class MenuManager {

    private final MenuPersister menuPersister;
    private Menu currentMenu;
    private final ArrayList<MenuEventReceiver> eventReceivers;

    public MenuManager(MenuPersister menuPersister) {
        this.menuPersister = menuPersister;
        this.eventReceivers = new ArrayList<>();
    }

    public Menu loadMenu(int id) {
        return menuPersister.load(id);
    }

    public Menu createMenu() throws UseCaseLogicException {
        return createMenu(null);
    }

    public Menu createMenu(String title) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        Preconditions.requireChef(user);

        Menu m = Menu.create(user, title);
        setCurrentMenu(m);
        notifyMenuCreated(m);
        return m;
    }

    public Section defineSection(String name) throws UseCaseLogicException {
        Preconditions.requireCurrentMenu(currentMenu);

        Section newSection = currentMenu.addSection(name);
        notifySectionAdded(currentMenu, newSection);
        return newSection;
    }

    public MenuItem insertItem(Recipe recipe, Section sec, String desc) throws UseCaseLogicException {
        Preconditions.requireCurrentMenu(currentMenu);
        if (sec != null) {
            Preconditions.requireSectionInMenu(currentMenu, sec);
        }

        MenuItem mi = currentMenu.addItem(recipe, sec, desc);
        notifyMenuItemAdded(mi);
        return mi;
    }

    public MenuItem insertItem(Recipe recipe, Section sec) throws UseCaseLogicException {
        return insertItem(recipe, sec, recipe.getName());
    }

    public MenuItem insertItem(Recipe rec) throws UseCaseLogicException {
        return insertItem(rec, null, rec.getName());
    }

    public MenuItem insertItem(Recipe rec, String desc) throws UseCaseLogicException {
        return insertItem(rec, null, desc);
    }

    public void addMenuFeatures(String[] features, boolean[] values) throws UseCaseLogicException {
        Preconditions.requireCurrentMenu(currentMenu);
        if (features.length != values.length) {
            throw new UseCaseLogicException("precondition: features and values arrays must have the same length");
        }

        for (int i = 0; i < features.length; i++) {
            currentMenu.setFeature(features[i], values[i]);
        }
        notifyMenuFeaturesChanged();
    }

    public void changeTitle(String title) throws UseCaseLogicException {
        Preconditions.requireCurrentMenu(currentMenu);

        currentMenu.setTitle(title);
        notifyMenuTitleChanged();
    }

    public void publish() throws UseCaseLogicException {
        Preconditions.requireCurrentMenu(currentMenu);

        currentMenu.setPublished(true);
        notifyMenuPublishedState();
    }

    public void deleteMenu(Menu m) throws UseCaseLogicException, MenuException {
        User u = CatERing.getInstance().getUserManager().getCurrentUser();
        Preconditions.requireChef(u);
        if (m.isInUse() || !m.isOwner(u)) {
            throw new MenuException();
        }
        notifyMenuDeleted(m);
    }

    public void chooseMenu(Menu m) throws UseCaseLogicException, MenuException {
        User u = CatERing.getInstance().getUserManager().getCurrentUser();
        Preconditions.requireChef(u);
        if (m.isInUse() || !m.isOwner(u)) {
            throw new MenuException();
        }
        currentMenu = m;
    }

    public Menu chooseMenuForCopy(Menu toCopy) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        Preconditions.requireChef(user);

        Menu m = toCopy.deepCopy();
        m.setOwner(user);
        setCurrentMenu(m);
        notifyMenuCreated(m);
        return m;
    }

    public void deleteSection(Section s, boolean deleteItems) throws UseCaseLogicException {
        Preconditions.requireCurrentMenu(currentMenu);
        Preconditions.requireSectionInMenu(currentMenu, s);

        currentMenu.removeSection(s, deleteItems);
        notifySectionDeleted(s, deleteItems);
    }

    public void changeSectionName(Section s, String name) throws UseCaseLogicException {
        Preconditions.requireCurrentMenu(currentMenu);
        Preconditions.requireSectionInMenu(currentMenu, s);

        s.setName(name);
        notifySectionChangedName(s);
    }

    public void assignItemToSection(MenuItem mi, Section sec) throws UseCaseLogicException {
        Preconditions.requireCurrentMenu(currentMenu);
        if (sec != null) {
            Preconditions.requireSectionInMenu(currentMenu, sec);
        }
        Preconditions.requireItemInMenu(currentMenu, mi);

        Section oldsec = currentMenu.getSection(mi);
        if (sec == oldsec) {
            return;
        }

        currentMenu.changeItemSection(mi, oldsec, sec);
        notifyItemSectionChanged(mi, sec);
    }

    public void editMenuItemDescription(MenuItem mi, String desc) throws UseCaseLogicException {
        Preconditions.requireCurrentMenu(currentMenu);
        Preconditions.requireItemInMenu(currentMenu, mi);

        mi.setDescription(desc);
        notifyItemDescriptionChanged(mi);
    }

    public void deleteItem(MenuItem mi) throws UseCaseLogicException {
        Preconditions.requireCurrentMenu(currentMenu);
        Preconditions.requireItemInMenu(currentMenu, mi);

        Section sec = currentMenu.getSection(mi);
        currentMenu.removeItem(mi);
        notifyItemDeleted(sec, mi);
    }

    public void setCurrentMenu(Menu m) {
        this.currentMenu = m;
    }

    public Menu getCurrentMenu() {
        return currentMenu;
    }

    public void addEventReceiver(MenuEventReceiver rec) {
        eventReceivers.add(rec);
    }

    public void removeEventReceiver(MenuEventReceiver rec) {
        eventReceivers.remove(rec);
    }

    private void notifyItemDeleted(Section sec, MenuItem mi) {
        for (MenuEventReceiver er : eventReceivers) {
            er.updateMenuItemDeleted(currentMenu, sec, mi);
        }
    }

    private void notifyItemDescriptionChanged(MenuItem mi) {
        for (MenuEventReceiver er : eventReceivers) {
            er.updateMenuItemDescriptionChanged(currentMenu, mi);
        }
    }

    private void notifyItemSectionChanged(MenuItem mi, Section s) {
        for (MenuEventReceiver er : eventReceivers) {
            er.updateMenuItemChanged(currentMenu, s, mi);
        }
    }

    private void notifySectionChangedName(Section s) {
        for (MenuEventReceiver er : eventReceivers) {
            er.updateSectionChangedName(currentMenu, s);
        }
    }

    private void notifySectionDeleted(Section s, boolean itemsDeleted) {
        for (MenuEventReceiver er : eventReceivers) {
            er.updateSectionDeleted(currentMenu, s, itemsDeleted);
        }
    }

    private void notifyMenuDeleted(Menu m) {
        for (MenuEventReceiver er : eventReceivers) {
            er.updateMenuDeleted(m);
        }
    }

    private void notifyMenuPublishedState() {
        for (MenuEventReceiver er : eventReceivers) {
            er.updateMenuPublishedState(currentMenu);
        }
    }

    private void notifyMenuTitleChanged() {
        for (MenuEventReceiver er : eventReceivers) {
            er.updateMenuTitleChanged(currentMenu);
        }
    }

    private void notifyMenuFeaturesChanged() {
        for (MenuEventReceiver er : eventReceivers) {
            er.updateMenuFeaturesChanged(currentMenu);
        }
    }

    private void notifyMenuItemAdded(MenuItem mi) {
        for (MenuEventReceiver er : eventReceivers) {
            er.updateMenuItemAdded(currentMenu, mi);
        }
    }

    private void notifySectionAdded(Menu m, Section sec) {
        for (MenuEventReceiver er : eventReceivers) {
            er.updateSectionAdded(m, sec);
        }
    }

    private void notifyMenuCreated(Menu m) {
        for (MenuEventReceiver er : eventReceivers) {
            er.updateMenuCreated(m);
        }
    }
}
