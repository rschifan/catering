package catering.businesslogic.event;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import catering.businesslogic.user.User;
import catering.persistence.SQLitePersistenceManager;

/**
 * Tests for {@link Event}: in-memory aggregate behaviour and the static
 * loaders against the seeded SQLite database.
 */
class EventTest {

    @BeforeAll
    static void initializeDatabase() {
        SQLitePersistenceManager.initializeDatabase("database/catering_init_sqlite.sql");
    }

    @Nested
    class Aggregate {

        private Event event;
        private Service first;
        private Service second;

        @BeforeEach
        void setUp() {
            event = new Event("Test Event");
            first = new Service();
            first.setId(1);
            second = new Service();
            second.setId(2);
        }

        @Test
        void name_setInConstructor_isReadable() {
            assertEquals("Test Event", event.getName());
        }

        @Test
        void id_setExplicitly_isReadable() {
            event.setId(42);
            assertEquals(42, event.getId());
        }

        @Test
        void chef_setExplicitly_isReadableAndExposesId() {
            User chef = new User();
            chef.setId(7);

            event.setChef(chef);

            assertEquals(chef, event.getChef());
            assertEquals(7, event.getChefId());
        }

        @Test
        void services_freshEvent_isEmpty() {
            assertTrue(event.getServices().isEmpty());
        }

        @Test
        void addService_recordsContainment() {
            event.addService(first);

            assertTrue(event.containsService(first));
            assertFalse(event.containsService(second));
        }

        @Test
        void removeService_dropsOnlyTheTargetedService() {
            event.addService(first);
            event.addService(second);

            event.removeService(first);

            assertFalse(event.containsService(first));
            assertTrue(event.containsService(second));
        }

        @Test
        void datesSetExplicitly_areReadable() {
            Date day = Date.valueOf("2024-05-29");

            event.setDateStart(day);
            event.setDateEnd(day);

            assertEquals(day, event.getDateStart());
            assertEquals(day, event.getDateEnd());
        }
    }

    @Nested
    class StaticLoaders {

        @Test
        void loadAllEvents_returnsSeededEvents() {
            List<Event> events = Event.loadAllEvents();

            assertNotNull(events);
            assertFalse(events.isEmpty(), "the seed script must populate at least one event");

            Event sample = events.get(0);
            assertNotNull(sample.getName());
            assertNotNull(sample.getDateStart());
            assertNotNull(sample.getChef());
            assertNotNull(sample.getServices());
        }

        @Test
        void loadById_roundTripsTheSameEvent() {
            Event sample = Event.loadAllEvents().get(0);

            Event loaded = Event.loadById(sample.getId());

            assertNotNull(loaded);
            assertEquals(sample.getId(), loaded.getId());
            assertEquals(sample.getName(), loaded.getName());
        }

        @Test
        void loadByName_findsEventByExactName() {
            Event sample = Event.loadAllEvents().get(0);

            Event loaded = Event.loadByName(sample.getName());

            assertNotNull(loaded);
            assertEquals(sample.getName(), loaded.getName());
        }
    }
}
