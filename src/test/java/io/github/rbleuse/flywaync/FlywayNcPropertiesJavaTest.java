package io.github.rbleuse.flywaync;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class FlywayNcPropertiesJavaTest {

    @Test
    void exposesFlywayNcPropertiesAsAJavaRecord() {
        var properties = new FlywayNcProperties(
                true,
                "cassandra://localhost:9042/test?localdatacenter=dc1",
                null,
                null,
                List.of("classpath:db/migration"),
                null,
                null
        );

        assertThat(FlywayNcProperties.class.isRecord()).isTrue();
        assertThat(properties.url()).isEqualTo("cassandra://localhost:9042/test?localdatacenter=dc1");
        assertThat(properties.locations()).containsExactly("classpath:db/migration");
    }
}
