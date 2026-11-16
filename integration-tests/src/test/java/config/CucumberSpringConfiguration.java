package config;

import com.ga.saudsFlightSystem.SaudsFlightSystemApplication;
import com.ga.saudsFlightSystem.support.MaintenanceTestConfiguration;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@CucumberContextConfiguration
@SpringBootTest(properties = "app.scheduling.enabled=false", webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = SaudsFlightSystemApplication.class)
@ActiveProfiles("cucumber")
@Import({MaintenanceTestConfiguration.class, CucumberSpringConfiguration.FeignFixtureConfiguration.class})
public class CucumberSpringConfiguration {
    @TestConfiguration
    public static class FeignFixtureConfiguration {
        @Bean
        public static BeanFactoryPostProcessor preferFixtureClients() {
            return beanFactory -> {
                BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
                // Feign clients are primary by default; prefer the existing in-process fixtures.
                for (String client : new String[]{
                        "com.ga.saudsFlightSystem.identity.IdentityServiceClient",
                        "com.ga.saudsFlightSystem.client.NotificationServiceClient"}) {
                    if (registry.containsBeanDefinition(client)) registry.getBeanDefinition(client).setPrimary(false);
                }
            };
        }
    }
}
