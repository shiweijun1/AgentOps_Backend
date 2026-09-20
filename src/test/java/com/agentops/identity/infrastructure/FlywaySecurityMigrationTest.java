package com.agentops.identity.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.DriverManager;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class FlywaySecurityMigrationTest {

    private static final String ADMIN_PASSWORD = "Admin@123456";
    private static final String ADMIN_PASSWORD_HASH =
            "$2a$12$4RjCpvCT7V6ZKG/cEWsbq.UB.s/hHLNDypgrN3CjCd1sQ.yZl1o2S";

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer(
            DockerImageName.parse("mysql:8.4")
    ).withDatabaseName("agentops");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of(
                        "dev_seed_enabled", "true",
                        "dev_admin_password_hash", ADMIN_PASSWORD_HASH
                ))
                .load()
                .migrate();
    }

    @Test
    void v2ShouldCreateAdminWithBcryptHashAndPermission() throws Exception {
        try (var connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.prepareStatement("""
                     SELECT u.password_hash, p.code
                     FROM app_user u
                     JOIN user_role ur ON ur.user_id = u.id
                     JOIN role_permission rp ON rp.role_id = ur.role_id
                     JOIN iam_permission p ON p.id = rp.permission_id
                     WHERE u.tenant_id = 'default'
                       AND u.username = 'admin'
                       AND p.code = 'admin:security-check'
                     """);
             var resultSet = statement.executeQuery()) {
            assertThat(resultSet.next()).isTrue();
            String storedHash = resultSet.getString("password_hash");
            assertThat(storedHash).startsWith("$2");
            assertThat(storedHash).isNotEqualTo(ADMIN_PASSWORD);
            assertThat(new BCryptPasswordEncoder().matches(ADMIN_PASSWORD, storedHash)).isTrue();
            assertThat(resultSet.getString("code")).isEqualTo("admin:security-check");
        }
    }
}
