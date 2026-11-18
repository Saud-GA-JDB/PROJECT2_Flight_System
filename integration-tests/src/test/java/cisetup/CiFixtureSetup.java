package cisetup;

import com.ga.saudsFlightSystem.SaudsFlightSystemApplication;
import com.ga.saudsFlightSystem.support.MaintenanceTestConfiguration;
import config.CucumberSpringConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Explicit CI bootstrap; its name excludes it from Maven's default test discovery. */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SaudsFlightSystemApplication.class, properties = {
        "app.seed.enabled=true", "app.scheduling.enabled=false"
})
@Import({MaintenanceTestConfiguration.class, CucumberSpringConfiguration.FeignFixtureConfiguration.class})
@ContextConfiguration(initializers = CiFixtureSetup.DisposableDatabaseOnly.class)
public class CiFixtureSetup {
    @Autowired JdbcTemplate jdbc;

    public static class DisposableDatabaseOnly implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            String url = context.getEnvironment().getProperty("spring.datasource.url", "");
            if (!url.equals("jdbc:postgresql://docker:55432/ci_aviation")) {
                throw new IllegalStateException("CI seeding is restricted to the disposable CI Docker database");
            }
        }
    }

    @Test
    public void preparesExistingTestFixturesInDisposableDatabase() {
        assertEquals("ci_aviation", jdbc.queryForObject("select current_database()", String.class));
        assertTrue(jdbc.queryForObject("select count(*) from users", Integer.class) > 0);
        assertEquals(Integer.valueOf(1), jdbc.queryForObject(
                "select count(*) from bookings where id = 900001", Integer.class));
    }
}
