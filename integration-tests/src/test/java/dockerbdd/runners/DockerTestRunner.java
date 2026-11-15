package dockerbdd.runners;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(
        features = "src/test/resources/docker-features",
        glue = {"dockerbdd.definitions", "dockerbdd.config"},
        plugin = {"pretty", "summary"}
)
public class DockerTestRunner {
}
