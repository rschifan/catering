package catering.businesslogic.kitchen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import catering.businesslogic.CatERing;
import catering.businesslogic.UseCaseLogicException;
import catering.businesslogic.event.Event;
import catering.businesslogic.event.Service;
import catering.persistence.SQLitePersistenceManager;

/**
 * System-operation tests for the <em>Gestire compiti cucina</em> use case
 * (DSD: {@code slides/class-14-16-progettazione/gestire-compiti-cucina/generateSummarySheet.uxf}).
 * <p>
 * Each test exercises the happy path or one clause of the DSD's ALT-block
 * precondition.
 */
class KitchenTaskManagerTest {

    private static CatERing app;

    private Event event;
    private Service service;

    @BeforeAll
    static void initializeRuntime() {
        SQLitePersistenceManager.initializeDatabase("database/catering_init_sqlite.sql");
        app = CatERing.getInstance();
    }

    @BeforeEach
    void resetSession() throws UseCaseLogicException {
        app.getUserManager().fakeLogin("Antonio"); // chef of the seeded event
        event = Event.loadByName("Gala Aziendale Annuale");
        service = Service.loadByName("Pranzo Buffet Aziendale");
    }

    @Test
    void generateSummarySheet_happyPath_oneTaskPerKitchenProcess() throws UseCaseLogicException {
        int expected = service.getMenu().getKitchenProcesses().size();

        SummarySheet sheet = app.getKitchenTaskManager().generateSummarySheet(event, service);

        assertNotNull(sheet);
        assertEquals(expected, sheet.getTaskList().size(),
                "one KitchenTask per element of getKitchenProcesses()");
        assertSame(sheet, app.getKitchenTaskManager().getCurrentSummarySheet());
    }

    @Test
    void generateSummarySheet_nonChefCaller_throwsUseCaseLogicException() throws UseCaseLogicException {
        app.getUserManager().fakeLogin("Luca"); // cook, not chef

        assertThrows(UseCaseLogicException.class,
                () -> app.getKitchenTaskManager().generateSummarySheet(event, service));
    }

    @Test
    void generateSummarySheet_chefIsNotEventChef_throwsUseCaseLogicException() throws UseCaseLogicException {
        app.getUserManager().fakeLogin("Chiara"); // chef, but not the event's chef

        assertThrows(UseCaseLogicException.class,
                () -> app.getKitchenTaskManager().generateSummarySheet(event, service));
    }

    @Test
    void generateSummarySheet_serviceNotInEvent_throwsUseCaseLogicException() {
        Service detached = new Service("Detached"); // never added to event

        assertThrows(UseCaseLogicException.class,
                () -> app.getKitchenTaskManager().generateSummarySheet(event, detached));
    }

    @Test
    void generateSummarySheet_serviceWithoutMenu_throwsUseCaseLogicException() {
        Service emptyService = new Service("No menu");
        event.addService(emptyService);

        assertThrows(UseCaseLogicException.class,
                () -> app.getKitchenTaskManager().generateSummarySheet(event, emptyService));
    }

    @Test
    void generateSummarySheet_nullEvent_throwsUseCaseLogicException() {
        assertThrows(UseCaseLogicException.class,
                () -> app.getKitchenTaskManager().generateSummarySheet(null, service));
    }

    @Test
    void generateSummarySheet_nullService_throwsUseCaseLogicException() {
        assertThrows(UseCaseLogicException.class,
                () -> app.getKitchenTaskManager().generateSummarySheet(event, null));
    }
}
