package catering.businesslogic;

import catering.businesslogic.kitchen.SummarySheet;
import catering.businesslogic.menu.Menu;
import catering.businesslogic.menu.MenuItem;
import catering.businesslogic.menu.Section;
import catering.businesslogic.user.User;

/**
 * Pure Fabrication (GRASP) — centralises operation-contract precondition
 * checks shared across use-case controllers. Each method maps to a
 * precondition clause from {@code teoria/06-Contratti.pdf}.
 * <p>
 * Replaces the previous pattern of inline {@code if}-check + bare
 * {@code throw new UseCaseLogicException()} at every call site, which left
 * the contract layer invisible from the code and let the diagnostic style
 * drift from one manager to another. With this helper, every use-case
 * controller speaks the same precondition vocabulary.
 * <p>
 * Each {@code requireXxx} method throws {@link UseCaseLogicException} with a
 * descriptive message naming the violated precondition.
 */
public final class Preconditions {

    private Preconditions() {
        // utility class — not instantiable
    }

    /** precondition: actor must be a Chef. */
    public static void requireChef(User user) throws UseCaseLogicException {
        if (user == null || !user.isChef()) {
            throw new UseCaseLogicException("precondition: actor must be a Chef");
        }
    }

    /** precondition: a menu must be in definition (currentMenu non-null). */
    public static void requireCurrentMenu(Menu currentMenu) throws UseCaseLogicException {
        if (currentMenu == null) {
            throw new UseCaseLogicException("precondition: a menu must be in definition");
        }
    }

    /**
     * precondition: section must belong to the menu.
     * A {@code null} section is treated as not-in-menu and rejected; callers
     * for whom section is optional should guard the call with their own
     * {@code if (section != null)}.
     */
    public static void requireSectionInMenu(Menu menu, Section section) throws UseCaseLogicException {
        if (section == null || menu.getSectionPosition(section) < 0) {
            throw new UseCaseLogicException("precondition: section must belong to the menu");
        }
    }

    /**
     * precondition: item must belong to the menu (in a section or as a free
     * item). Catches the {@link IllegalArgumentException} that
     * {@link Menu#getSection(MenuItem)} raises for items it does not own and
     * rethrows it as a precondition violation.
     */
    public static void requireItemInMenu(Menu menu, MenuItem item) throws UseCaseLogicException {
        try {
            Section sec = menu.getSection(item);
            if (sec == null && menu.getFreeItemPosition(item) < 0) {
                throw new UseCaseLogicException("precondition: item must belong to the menu");
            }
        } catch (IllegalArgumentException e) {
            throw new UseCaseLogicException("precondition: item must belong to the menu");
        }
    }

    /** precondition: a summary sheet must be active. */
    public static void requireCurrentSummarySheet(SummarySheet sheet) throws UseCaseLogicException {
        if (sheet == null) {
            throw new UseCaseLogicException("precondition: a summary sheet must be active");
        }
    }
}
