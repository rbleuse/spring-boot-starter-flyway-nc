package io.github.rbleuse.flywaync;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class FlywayNcPropertiesJavaInteropTest {

    @Test
    void exposesFlywayNcPropertiesAccessorsToJava() {
        var properties = new FlywayNcProperties();

        assertThat(properties.getUrl()).isNull();
        assertThat(properties.getLocations()).containsExactly("classpath:db/migration");
        assertThat(properties.getBaselineOnMigrate()).isFalse();
        assertThat(properties.getBaselineVersion()).isEqualTo("1");
        assertThat(properties.getValidateOnMigrate()).isTrue();
        assertThat(properties.getConnectRetries()).isZero();
        assertThat(properties.getConnectRetriesInterval()).isEqualTo(Duration.ofSeconds(120));
        assertThat(properties.getValidateMigrationNaming()).isFalse();
        assertThat(properties.getFailOnMissingLocations()).isFalse();
        assertThat(properties.getTarget()).isEqualTo("latest");
        assertThat(properties.getTable()).isEqualTo("flyway_schema_history");
        assertThat(properties.getCreateSchemas()).isTrue();
        assertThat(properties.getOutOfOrder()).isFalse();
    }
}
