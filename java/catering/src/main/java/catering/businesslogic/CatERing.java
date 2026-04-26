package catering.businesslogic;

import catering.businesslogic.event.EventManager;
import catering.businesslogic.kitchen.KitchenTaskManager;
import catering.businesslogic.menu.MenuManager;
import catering.businesslogic.recipe.RecipeManager;
import catering.businesslogic.shift.ShiftManager;
import catering.businesslogic.user.UserManager;
import catering.persistence.KitchenTaskPersistence;
import catering.persistence.MenuPersistence;
import catering.persistence.strategy.MenuItemPersister;
import catering.persistence.strategy.MenuPersister;
import catering.persistence.strategy.SectionPersister;
import catering.persistence.strategy.impl.SQLiteMenuItemPersister;
import catering.persistence.strategy.impl.SQLiteMenuPersister;
import catering.persistence.strategy.impl.SQLiteSectionPersister;

public class CatERing {
    private static CatERing instance = null;

    public static CatERing getInstance() {
        if (instance == null) {
            instance = new CatERing();
        }
        return instance;
    }

    private MenuManager menuMgr;
    private RecipeManager recipeMgr;
    private UserManager userMgr;
    private EventManager eventMgr;
    private KitchenTaskManager kitchenTaskMgr;
    private ShiftManager shiftMgr;

    private MenuPersistence menuPersistence;
    private KitchenTaskPersistence kitchenTaskPersistence;

    private CatERing() {
        // Strategy composition: build leaf persisters first, then composite ones.
        MenuItemPersister menuItemPersister = new SQLiteMenuItemPersister();
        SectionPersister sectionPersister = new SQLiteSectionPersister(menuItemPersister);
        MenuPersister menuPersister = new SQLiteMenuPersister(sectionPersister, menuItemPersister);

        menuMgr = new MenuManager(menuPersister);
        recipeMgr = new RecipeManager();
        userMgr = new UserManager();
        eventMgr = new EventManager();
        kitchenTaskMgr = new KitchenTaskManager();
        shiftMgr = new ShiftManager();

        menuPersistence = new MenuPersistence(menuPersister, sectionPersister, menuItemPersister);
        kitchenTaskPersistence = new KitchenTaskPersistence();

        menuMgr.addEventReceiver(menuPersistence);
        kitchenTaskMgr.addEventReceiver(kitchenTaskPersistence);
    }

    public KitchenTaskManager getKitchenTaskManager() {
        return kitchenTaskMgr; // Return the field that was properly initialized
    }

    public ShiftManager getShiftManager() {
        return shiftMgr;
    }

    public void setShiftManager(ShiftManager shiftMgr) {
        this.shiftMgr = shiftMgr;
    }

    public MenuManager getMenuManager() {
        return menuMgr;
    }

    public void setMenuManager(MenuManager menuMgr) {
        this.menuMgr = menuMgr;
    }

    public RecipeManager getRecipeManager() {
        return recipeMgr;
    }

    public void setRecipeManager(RecipeManager recipeMgr) {
        this.recipeMgr = recipeMgr;
    }

    public UserManager getUserManager() {
        return userMgr;
    }

    public void setUserManager(UserManager userMgr) {
        this.userMgr = userMgr;
    }

    public EventManager getEventManager() {
        return eventMgr;
    }

    public void setEventManager(EventManager eventMgr) {
        this.eventMgr = eventMgr;
    }

    public void setKitchenTaskManager(KitchenTaskManager kitchenTaskMgr) {
        this.kitchenTaskMgr = kitchenTaskMgr;
    }

}
