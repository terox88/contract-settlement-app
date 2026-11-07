package pl.company.settlements.user.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.bootstrap-admin",
        name = "enabled",
        havingValue = "true"
)
public class BootstrapAdminInitializer
        implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(
                    BootstrapAdminInitializer.class
            );

    private final BootstrapAdminService bootstrapAdminService;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        boolean created = bootstrapAdminService.createFirstAdmin(
                environment.getProperty(
                        "app.bootstrap-admin.username",
                        ""
                ),
                environment.getProperty(
                        "app.bootstrap-admin.display-name",
                        ""
                ),
                environment.getProperty(
                        "app.bootstrap-admin.password",
                        ""
                )
        );

        if (created) {
            log.info(
                    "Utworzono pierwsze konto administratora."
            );
        } else {
            log.info(
                    "Pominięto inicjalizację administratora: "
                            + "w bazie istnieją już konta."
            );
        }
    }
}