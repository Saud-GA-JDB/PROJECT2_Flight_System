package dockerbdd.config;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

// Only fixture setup/cleanup runs here. All application requests use the running Docker services.
@CucumberContextConfiguration
@ContextConfiguration(classes = DockerCucumberConfiguration.FixtureConfiguration.class)
public class DockerCucumberConfiguration {
    public static String setting(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @Configuration
    public static class FixtureConfiguration {
        @Bean
        public DataSource dataSource() {
            String password = setting("DOCKER_TEST_DATABASE_PASSWORD", "");
            if (password.isBlank()) throw new IllegalStateException("Set DOCKER_TEST_DATABASE_PASSWORD for the local Docker test database.");
            return new DriverManagerDataSource(
                    setting("DOCKER_TEST_DATABASE_URL", "jdbc:postgresql://localhost:5433/aviation"),
                    setting("DOCKER_TEST_DATABASE_USERNAME", "postgres"), password);
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public TransactionTemplate transactionTemplate(DataSource dataSource) {
            return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        }
    }
}
