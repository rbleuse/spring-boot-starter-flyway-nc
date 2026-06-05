package io.github.rbleuse.flywaync;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class FlywayNcPropertiesJavaInteropTest {

    @Test
    void exposesFlywayNcPropertiesAccessorsToJava() {
        var properties = new FlywayNcProperties(
                "native://localhost:1234/test",
                null,
                null,
                List.of("classpath:db/migration"),
                List.of(),
                null
        );

        assertThat(properties.getUrl()).isEqualTo("native://localhost:1234/test");
        assertThat(properties.getLocations()).containsExactly("classpath:db/migration");
    }
}
