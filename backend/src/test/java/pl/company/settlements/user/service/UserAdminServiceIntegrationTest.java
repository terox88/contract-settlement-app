package pl.company.settlements.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.CredentialsExpiredException;
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
import pl.company.settlements.common.config.PasswordConfig;
import pl.company.settlements.common.error.ConflictException;
import pl.company.settlements.user.domain.AppUser;
import pl.company.settlements.user.model.AppUserPrincipal;
import pl.company.settlements.user.model.UserAdminResponse;
import pl.company.settlements.user.model.UserCreateRequest;
import pl.company.settlements.user.model.UserPasswordResetRequest;
import pl.company.settlements.user.model.UserRole;
import pl.company.settlements.user.model.UserUpdateRequest;
import pl.company.settlements.user.repository.AppUserRepository;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
        PasswordConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserAdminServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("users_test")
                    .withUsername("users_test")
                    .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );
        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );
        registry.add(
                "spring.flyway.url",
                POSTGRES::getJdbcUrl
        );
        registry.add(
                "spring.flyway.user",
                POSTGRES::getUsername
        );
        registry.add(
                "spring.flyway.password",
                POSTGRES::getPassword
        );
    }

    @Autowired
    private AppUserRepository repository;

    @Autowired
    private UserAdminService service;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        transaction().executeWithoutResult(
                status -> repository.deleteAllInBatch()
        );
    }

    @Test
    void createsAccountWithEightCharacterTemporaryPassword() {
        AppUser admin = seed("admin", UserRole.ADMIN, true);

        UserCreateRequest request = new UserCreateRequest();
        request.setUsername(" NOWY.USER ");
        request.setDisplayName("Nowy użytkownik");
        request.setRole(UserRole.EDITOR);
        request.setTemporaryPassword("Nowe1234");

        UserAdminResponse response =
                service.create(new AppUserPrincipal(admin), request);

        AppUser saved = account(response.id());

        assertEquals("nowy.user", saved.getUsername());
        assertTrue(saved.isEnabled());
        assertTrue(saved.isPasswordChangeRequired());
        assertTrue(passwordEncoder.matches(
                "Nowe1234",
                saved.getPasswordHash()
        ));
    }

    @Test
    void cannotDemoteLastActiveAdmin() {
        AppUser admin = seed("admin", UserRole.ADMIN, true);

        UserUpdateRequest request = new UserUpdateRequest(
                admin.getDisplayName(),
                UserRole.EDITOR,
                true,
                admin.getVersion()
        );

        assertThrows(
                ConflictException.class,
                () -> service.update(
                        new AppUserPrincipal(admin),
                        admin.getId(),
                        request
                )
        );

        assertEquals(
                UserRole.ADMIN,
                account(admin.getId()).getRole()
        );
    }

    @Test
    void inactiveAdminDoesNotAllowBlockingLastActiveAdmin() {
        AppUser admin = seed("admin", UserRole.ADMIN, true);
        seed("inactive.admin", UserRole.ADMIN, false);

        UserUpdateRequest request = new UserUpdateRequest(
                admin.getDisplayName(),
                UserRole.ADMIN,
                false,
                admin.getVersion()
        );

        assertThrows(
                ConflictException.class,
                () -> service.update(
                        new AppUserPrincipal(admin),
                        admin.getId(),
                        request
                )
        );

        assertTrue(account(admin.getId()).isEnabled());
    }

    @Test
    void passwordResetRequiresChangeAndRevokesPreviousSessions() {
        AppUser admin = seed("admin", UserRole.ADMIN, true);
        AppUser editor = seed("editor", UserRole.EDITOR, true);

        UserPasswordResetRequest request = resetRequest(
                editor.getVersion()
        );

        service.resetPassword(
                new AppUserPrincipal(admin),
                editor.getId(),
                request
        );

        AppUser saved = account(editor.getId());

        assertTrue(saved.isPasswordChangeRequired());
        assertEquals(
                editor.getSecurityVersion() + 1,
                saved.getSecurityVersion()
        );
        assertTrue(passwordEncoder.matches(
                "Nowe1234",
                saved.getPasswordHash()
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.resetPassword(
                        new AppUserPrincipal(admin),
                        admin.getId(),
                        resetRequest(admin.getVersion())
                )
        );
    }

    @Test
    void rejectsStaleVersionsAndStaleOrUnauthorizedPrincipals() {
        AppUser admin = seed("admin", UserRole.ADMIN, true);
        AppUser editor = seed("editor", UserRole.EDITOR, true);

        AppUserPrincipal adminPrincipal = new AppUserPrincipal(admin);
        AppUserPrincipal oldEditorPrincipal =
                new AppUserPrincipal(editor);

        UserUpdateRequest request = new UserUpdateRequest(
                "Zmieniona nazwa",
                UserRole.READ_ONLY,
                true,
                editor.getVersion()
        );

        service.update(adminPrincipal, editor.getId(), request);

        assertThrows(
                ConflictException.class,
                () -> service.update(
                        adminPrincipal,
                        editor.getId(),
                        request
                )
        );

        assertThrows(
                CredentialsExpiredException.class,
                () -> service.list(oldEditorPrincipal)
        );

        assertThrows(
                AccessDeniedException.class,
                () -> service.list(
                        new AppUserPrincipal(account(editor.getId()))
                )
        );
    }

    @Test
    void concurrentChangesCannotDemoteBothActiveAdmins()
            throws Exception {
        AppUser firstAdmin = seed(
                "first.admin",
                UserRole.ADMIN,
                true
        );
        AppUser secondAdmin = seed(
                "second.admin",
                UserRole.ADMIN,
                true
        );

        CountDownLatch firstChanged = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() ->
                    transaction().executeWithoutResult(status -> {
                        service.update(
                                new AppUserPrincipal(firstAdmin),
                                firstAdmin.getId(),
                                demotionRequest(firstAdmin)
                        );

                        firstChanged.countDown();

                        // Transakcja pozostaje otwarta wraz z blokadą.
                        await(releaseFirst);
                    })
            );

            assertTrue(firstChanged.await(10, TimeUnit.SECONDS));

            Future<Throwable> second = executor.submit(() -> {
                secondStarted.countDown();

                try {
                    service.update(
                            new AppUserPrincipal(secondAdmin),
                            secondAdmin.getId(),
                            demotionRequest(secondAdmin)
                    );
                    return null;
                } catch (RuntimeException exception) {
                    return exception;
                }
            });

            assertTrue(secondStarted.await(10, TimeUnit.SECONDS));

            assertThrows(
                    TimeoutException.class,
                    () -> second.get(300, TimeUnit.MILLISECONDS)
            );

            releaseFirst.countDown();

            first.get(10, TimeUnit.SECONDS);

            assertInstanceOf(
                    ConflictException.class,
                    second.get(10, TimeUnit.SECONDS)
            );

            assertEquals(
                    1L,
                    repository.countByRoleAndEnabledTrue(
                            UserRole.ADMIN
                    )
            );
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private AppUser seed(
            String username,
            UserRole role,
            boolean enabled
    ) {
        return transaction().execute(status -> {
            AppUser user = new AppUser(
                    username,
                    username,
                    passwordEncoder.encode("Start123"),
                    role
            );

            user.changePassword(user.getPasswordHash());

            if (!enabled) {
                user.updateAccount(username, role, false);
            }

            assertFalse(user.isPasswordChangeRequired());

            return repository.saveAndFlush(user);
        });
    }

    private AppUser account(Long id) {
        return repository.findById(id).orElseThrow();
    }

    private UserUpdateRequest demotionRequest(AppUser user) {
        return new UserUpdateRequest(
                user.getDisplayName(),
                UserRole.EDITOR,
                true,
                user.getVersion()
        );
    }

    private UserPasswordResetRequest resetRequest(Long version) {
        UserPasswordResetRequest request =
                new UserPasswordResetRequest();

        request.setVersion(version);
        request.setTemporaryPassword("Nowe1234");

        return request;
    }

    private TransactionTemplate transaction() {
        TransactionTemplate template =
                new TransactionTemplate(transactionManager);

        template.setIsolationLevel(
                TransactionDefinition.ISOLATION_READ_COMMITTED
        );

        return template;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Przekroczono czas oczekiwania w teście"
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Przerwano oczekiwanie w teście",
                    exception
            );
        }
    }
}