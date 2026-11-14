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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = SaudsFlightSystemApplication.class)
@ActiveProfiles("cucumber")
@Import({MaintenanceTestConfiguration.class, CucumberSpringConfiguration.NoScheduledJobs.class})
public class CucumberSpringConfiguration {
    @TestConfiguration
    public static class NoScheduledJobs {
        // The shared database must not be changed by application-wide background jobs.
        @Bean
        public static BeanFactoryPostProcessor disableScheduledJobs() {
            return beanFactory -> {
                BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
                String name = "org.springframework.context.annotation.internalScheduledAnnotationProcessor";
                if (registry.containsBeanDefinition(name)) registry.removeBeanDefinition(name);
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
