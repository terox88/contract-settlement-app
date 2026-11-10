package pl.company.settlements.user.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.company.settlements.audit.domain.AuditEvent;
import pl.company.settlements.audit.model.AuditFieldChange;
import pl.company.settlements.audit.model.AuditOperation;
import pl.company.settlements.audit.service.AuditWriter;
import pl.company.settlements.common.config.PasswordConfig;
import pl.company.settlements.user.domain.AppUser;
import pl.company.settlements.user.model.AppUserPrincipal;
import pl.company.settlements.user.model.UserAdminResponse;
import pl.company.settlements.user.model.UserCreateRequest;
import pl.company.settlements.user.model.UserPasswordResetRequest;
import pl.company.settlements.user.model.UserRole;
import pl.company.settlements.user.model.UserUpdateRequest;
import pl.company.settlements.user.repository.AppUserRepository;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Testcontainers
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        UserAdminService.class,
        PasswordService.class,
        BootstrapAdminService.class,
        PasswordConfig.class,
        UserAuditService.class,
        AuditWriter.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserAuditIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("audit_test")
                    .withUsername("audit_test")
                    .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    private AppUserRepository repository;

    @Autowired
    private UserAdminService service;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private BootstrapAdminService bootstrapService;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void cleanUsers() {
        transaction().executeWithoutResult(
                status -> repository.deleteAllInBatch()
        );
    }

    @Test
    void creationStoresAuthorAndSafeFieldsWithoutCredentials() {
        AppUser admin = seed("admin", UserRole.ADMIN, false);

        UserAdminResponse created = service.create(
                new AppUserPrincipal(admin),
                createRequest("new.user")
        );

        AuditEvent event = latest(created.id());

        assertEquals(AuditOperation.USER_CREATED, event.getOperation());
        assertEquals(admin.getId(), event.getActorId());
        assertEquals(admin.getUsername(), event.getActorUsername());
        assertEquals(admin.getDisplayName(), event.getActorDisplayName());
        assertFalse(event.isSystemActor());
        assertNotNull(event.getOccurredAt());
        assertEquals(5, event.getChanges().size());

        String json = jdbc.queryForObject(
                "select changes::text from audit_event where id = ?",
                String.class,
                event.getId()
        );

        assertNotNull(json);
        assertFalse(json.contains("Nowe1234"));
        assertFalse(json.contains("passwordHash"));
        assertFalse(json.contains("temporaryPassword"));

        assertFalse(json.contains(
                repository.findById(created.id())
                        .orElseThrow()
                        .getPasswordHash()
        ));
    }

    @Test
    void updateStoresOnlyChangedFieldsAndNoopAddsNoEvent() {
        AppUser admin = seed("admin", UserRole.ADMIN, false);
        AppUser editor = seed("editor", UserRole.EDITOR, false);
        AppUserPrincipal actor = new AppUserPrincipal(admin);

        service.update(
                actor,
                editor.getId(),
                new UserUpdateRequest(
                        editor.getDisplayName(),
                        editor.getRole(),
                        editor.isEnabled(),
                        editor.getVersion()
                )
        );

        assertEquals(0, eventCount(editor.getId()));

        service.update(
                actor,
                editor.getId(),
                new UserUpdateRequest(
                        "Nowa nazwa",
                        UserRole.READ_ONLY,
                        false,
                        editor.getVersion()
                )
        );

        AuditEvent event = latest(editor.getId());

        assertEquals(AuditOperation.USER_UPDATED, event.getOperation());
        assertEquals(3, event.getChanges().size());
        assertEquals("Edycja", change(event, "role").beforeValue());
        assertEquals("Odczyt", change(event, "role").afterValue());
        assertEquals("Tak", change(event, "enabled").beforeValue());
        assertEquals("Nie", change(event, "enabled").afterValue());
    }

    @Test
    void resetIsRecordedEvenWhenPasswordChangeWasAlreadyRequired() {
        AppUser admin = seed("admin", UserRole.ADMIN, false);
        AppUser editor = seed("editor", UserRole.EDITOR, true);

        UserPasswordResetRequest request =
                new UserPasswordResetRequest();

        request.setVersion(editor.getVersion());
        request.setTemporaryPassword("Nowe1234");

        service.resetPassword(
                new AppUserPrincipal(admin),
                editor.getId(),
                request
        );

        AuditEvent event = latest(editor.getId());

        assertEquals(
                AuditOperation.USER_PASSWORD_RESET,
                event.getOperation()
        );
        assertTrue(event.getChanges().isEmpty());
        assertEquals(1, eventCount(editor.getId()));
    }

    @Test
    void ownPasswordChangeRecordsTheAuthenticatedUser() {
        AppUser user = seed("editor", UserRole.EDITOR, true);

        passwordService.changePassword(
                new AppUserPrincipal(user),
                "Start123",
                "Nowe1234"
        );

        AuditEvent event = latest(user.getId());

        assertEquals(
                AuditOperation.USER_PASSWORD_CHANGED,
                event.getOperation()
        );
        assertEquals(user.getId(), event.getActorId());
        assertEquals(
                "Tak",
                change(event, "passwordChangeRequired").beforeValue()
        );
        assertEquals(
                "Nie",
                change(event, "passwordChangeRequired").afterValue()
        );
    }

    @Test
    void outerRollbackRemovesBothAccountAndAuditEvent() {
        AppUser admin = seed("admin", UserRole.ADMIN, false);
        long before = totalEventCount();

        transaction().executeWithoutResult(status -> {
            service.create(
                    new AppUserPrincipal(admin),
                    createRequest("rolled.back")
            );

            assertEquals(before + 1, totalEventCount());
            status.setRollbackOnly();
        });

        assertFalse(repository.existsByUsername("rolled.back"));
        assertEquals(before, totalEventCount());
    }

    @Test
    void auditDatabaseFailureRollsBackAccountAndPasswordChanges() {
        AppUser admin = seed("admin", UserRole.ADMIN, false);
        AppUser editor = seed("editor", UserRole.EDITOR, true);
        long before = totalEventCount();

        // Wyłącznie w osobnej bazie kontenera testowego.
        jdbc.execute("""
                ALTER TABLE audit_event
                ADD CONSTRAINT chk_test_audit_rejected
                CHECK (false) NOT VALID
                """);

        try {
            RuntimeException failure = assertThrows(
                    RuntimeException.class,
                    () -> service.create(
                            new AppUserPrincipal(admin),
                            createRequest("audit.failed")
                    )
            );

            Throwable cause = failure;

            while (cause.getCause() != null) {
                cause = cause.getCause();
            }

            SQLException sql =
                    assertInstanceOf(SQLException.class, cause);

            assertEquals("23514", sql.getSQLState());
            assertTrue(
                    sql.getMessage().contains("chk_test_audit_rejected")
            );

            assertFalse(repository.existsByUsername("audit.failed"));

            assertThrows(
                    RuntimeException.class,
                    () -> passwordService.changePassword(
                            new AppUserPrincipal(editor),
                            "Start123",
                            "Nowe1234"
                    )
            );

            AppUser unchanged = repository.findById(editor.getId())
                    .orElseThrow();

            assertEquals(
                    editor.getPasswordHash(),
                    unchanged.getPasswordHash()
            );
            assertTrue(unchanged.isPasswordChangeRequired());
            assertEquals(
                    editor.getSecurityVersion(),
                    unchanged.getSecurityVersion()
            );
            assertEquals(before, totalEventCount());
        } finally {
            jdbc.execute("""
                    ALTER TABLE audit_event
                    DROP CONSTRAINT chk_test_audit_rejected
                    """);
        }
    }

    @Test
    void firstAdminCreationIsSystemEventAndExistingAccountsAreNotBackfilled() {
        assertTrue(bootstrapService.createFirstAdmin(
                "first.admin",
                "Pierwszy administrator",
                "Start123"
        ));

        AppUser admin = repository.findByUsername("first.admin")
                .orElseThrow();

        AuditEvent event = latest(admin.getId());

        assertEquals(
                AuditOperation.BOOTSTRAP_ADMIN_CREATED,
                event.getOperation()
        );
        assertTrue(event.isSystemActor());
        assertNull(event.getActorId());
        assertNull(event.getActorUsername());

        long before = totalEventCount();

        assertFalse(bootstrapService.createFirstAdmin(
                "other.admin",
                "Inny administrator",
                "Start123"
        ));

        assertEquals(before, totalEventCount());
    }

    private AppUser seed(
            String username,
            UserRole role,
            boolean changeRequired
    ) {
        return transaction().execute(status -> {
            AppUser user = new AppUser(
                    username,
                    username,
                    encoder.encode("Start123"),
                    role
            );

            if (!changeRequired) {
                user.changePassword(user.getPasswordHash());
            }

            return repository.saveAndFlush(user);
        });
    }

    private UserCreateRequest createRequest(String username) {
        UserCreateRequest request = new UserCreateRequest();

        request.setUsername(username);
        request.setDisplayName("Nowy użytkownik");
        request.setRole(UserRole.EDITOR);
        request.setTemporaryPassword("Nowe1234");

        return request;
    }

    private AuditEvent latest(Long userId) {
        Long id = jdbc.queryForObject("""
                select id from audit_event
                where entity_type = 'APP_USER' and entity_id = ?
                order by id desc limit 1
                """, Long.class, userId);

        return transaction().execute(
                status -> entityManager.find(AuditEvent.class, id)
        );
    }

    private long eventCount(Long userId) {
        return jdbc.queryForObject("""
                select count(*) from audit_event
                where entity_type = 'APP_USER' and entity_id = ?
                """, Long.class, userId);
    }

    private long totalEventCount() {
        return jdbc.queryForObject(
                "select count(*) from audit_event",
                Long.class
        );
    }

    private AuditFieldChange change(
            AuditEvent event,
            String fieldName
    ) {
        return event.getChanges().stream()
                .filter(change -> change.fieldName().equals(fieldName))
                .findFirst()
                .orElseThrow();
    }

    private TransactionTemplate transaction() {
        TransactionTemplate template =
                new TransactionTemplate(transactionManager);

        template.setIsolationLevel(
                TransactionDefinition.ISOLATION_READ_COMMITTED
        );

        return template;
    }
}