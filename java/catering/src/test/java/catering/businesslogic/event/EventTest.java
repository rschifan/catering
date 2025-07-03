package catering.businesslogic.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import catering.businesslogic.user.User;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

class EventTest {

    private Event event;
    private Service service1;
    private Service service2;

    @BeforeEach
    void setUp() {
        event = new Event("Test Event");
        service1 = new Service();
        service2 = new Service();
        // Assume Service has setId for testing equality
        service1.setId(1);
        service2.setId(2);
    }

    @Test
    void testConstructorAndGettersSetters() {
        assertEquals("Test Event", event.getName());
        event.setId(42);
        assertEquals(42, event.getId());

        Date now = Date.valueOf("2024-05-29");
        event.setDateStart(now);
        event.setDateEnd(now);
        assertEquals(now, event.getDateStart());
        assertEquals(now, event.getDateEnd());

        User chef = new User();
        chef.setId(7);
        event.setChef(chef);
        assertEquals(chef, event.getChef());
        assertEquals(7, event.getChefId());
    }

    @Test
    void testServiceManagement() {
        assertTrue(event.getServices().isEmpty());
        event.addService(service1);
        assertTrue(event.containsService(service1));
        assertFalse(event.containsService(service2));
        event.addService(service2);
        assertTrue(event.containsService(service2));
        event.removeService(service1);
        assertFalse(event.containsService(service1));
        assertTrue(event.containsService(service2));
    }

    @Test
    void testSetServices() {
        ArrayList<Service> services = new ArrayList<>();
        services.add(service1);
        event.setServices(services);
        assertEquals(1, event.getServices().size());
        assertTrue(event.containsService(service1));
    }

    @Test
    void testToString() {
        event.setId(1);
        event.setName("Party");
        event.addService(service1);
        String str = event.toString();
        assertTrue(str.contains("Party"));
        assertTrue(str.contains("services=1"));
    }

    // Database operation stubs (no DB interaction)
    @Test
    void testSaveUpdateDeleteEventNoException() {
        // These methods require a working PersistenceManager and DB, so just check no
        // exception thrown
        event.setChef(new User());
        event.setDateStart(Date.valueOf("2024-05-29"));
        event.setDateEnd(Date.valueOf("2024-05-30"));
        try {
            event.saveNewEvent();
            event.updateEvent();
            event.deleteEvent();
        } catch (Exception e) {
            fail("Database methods should not throw: " + e.getMessage());
        }
    }

    @Test
    void testLoadAllEventsFromDb() {
        List<Event> events = Event.loadAllEvents();
        assertNotNull(events);
        assertFalse(events.isEmpty(), "There should be at least one event in the DB");
        Event e = events.get(0);
        assertNotNull(e.getName());
        assertNotNull(e.getDateStart());
        assertNotNull(e.getChef());
        assertNotNull(e.getServices());
    }

    @Test
    void testLoadByIdAndByNameFromDb() {
        List<Event> events = Event.loadAllEvents();
        assertFalse(events.isEmpty(), "There should be at least one event in the DB");
        Event first = events.get(0);

        Event byId = Event.loadById(first.getId());
        assertNotNull(byId);
        assertEquals(first.getId(), byId.getId());
        assertEquals(first.getName(), byId.getName());

        Event byName = Event.loadByName(first.getName());
        assertNotNull(byName);
        assertEquals(first.getName(), byName.getName());
    }
}
