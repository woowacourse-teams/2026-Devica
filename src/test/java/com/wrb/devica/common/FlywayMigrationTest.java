package com.wrb.devica.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("flyway-test")
@Import(FlywayTestConfiguration.class)
public class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void 전체_마이그레이션을_적용하고_엔티티_매핑을_검증한다() {
        // when
        var pendingMigrations = flyway.info().pending();

        // then
        assertThat(pendingMigrations).isEmpty();
    }
}
