package pl.company.settlements.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import pl.company.settlements.audit.domain.AuditEvent;
import pl.company.settlements.audit.model.AuditEntityType;
import pl.company.settlements.audit.model.AuditFieldChange;
import pl.company.settlements.audit.model.AuditOperation;
import pl.company.settlements.audit.service.AuditWriter;
import pl.company.settlements.user.domain.AppUser;
import pl.company.settlements.user.model.AppUserPrincipal;
import pl.company.settlements.user.model.UserAuditSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserAuditService {

    private final AuditWriter auditWriter;

    public void created(
            AppUserPrincipal actor,
            AppUser account
    ) {
        record(actor, AuditOperation.USER_CREATED, null, account);
    }

    public void updated(
            AppUserPrincipal actor,
            UserAuditSnapshot before,
            AppUser account
    ) {
        if (!before.equals(UserAuditSnapshot.from(account))) {
            record(actor, AuditOperation.USER_UPDATED, before, account);
        }
    }

    public void passwordReset(
            AppUserPrincipal actor,
            UserAuditSnapshot before,
            AppUser account
    ) {
        record(
                actor,
                AuditOperation.USER_PASSWORD_RESET,
                before,
                account
        );
    }

    public void passwordChanged(
            AppUserPrincipal actor,
            UserAuditSnapshot before,
            AppUser account
    ) {
        record(
                actor,
                AuditOperation.USER_PASSWORD_CHANGED,
                before,
                account
        );
    }

    public void bootstrapCreated(AppUser account) {
        auditWriter.append(
                event(
                        AuditOperation.BOOTSTRAP_ADMIN_CREATED,
                        null,
                        account
                )
                        .systemActor(true)
                        .actorDisplayName("Inicjalizacja aplikacji")
                        .build()
        );
    }

    private void record(
            AppUserPrincipal actor,
            AuditOperation operation,
            UserAuditSnapshot before,
            AppUser account
    ) {
        if (actor == null || actor.getUserId() == null) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Brak uwierzytelnionego autora operacji"
            );
        }

        auditWriter.append(
                event(operation, before, account)
                        .actorId(actor.getUserId())
                        .actorUsername(actor.getUsername())
                        .actorDisplayName(actor.getDisplayName())
                        .build()
        );
    }

    private AuditEvent.AuditEventBuilder event(
            AuditOperation operation,
            UserAuditSnapshot before,
            AppUser account
    ) {
        return AuditEvent.builder()
                .operation(operation)
                .entityType(AuditEntityType.APP_USER)
                .entityId(account.getId())
                .entityLabel(account.getUsername())
                .changes(changes(
                        before,
                        UserAuditSnapshot.from(account)
                ));
    }

    private List<AuditFieldChange> changes(
            UserAuditSnapshot before,
            UserAuditSnapshot after
    ) {
        List<AuditFieldChange> changes = new ArrayList<>();

        add(
                changes,
                "username",
                "Login",
                before == null ? null : before.username(),
                after.username()
        );

        add(
                changes,
                "displayName",
                "Nazwa użytkownika",
                before == null ? null : before.displayName(),
                after.displayName()
        );

        add(
                changes,
                "role",
                "Rola",
                before == null ? null : before.role(),
                after.role()
        );

        add(
                changes,
                "enabled",
                "Konto aktywne",
                before == null ? null : yesNo(before.enabled()),
                yesNo(after.enabled())
        );

        add(
                changes,
                "passwordChangeRequired",
                "Wymagana zmiana hasła",
                before == null
                        ? null
                        : yesNo(before.passwordChangeRequired()),
                yesNo(after.passwordChangeRequired())
        );

        return List.copyOf(changes);
    }

    private void add(
            List<AuditFieldChange> changes,
            String fieldName,
            String fieldLabel,
            String before,
            String after
    ) {
        if (!Objects.equals(before, after)) {
            changes.add(
                    new AuditFieldChange(
                            fieldName,
                            fieldLabel,
                            before,
                            after
                    )
            );
        }
    }

    private String yesNo(boolean value) {
        return value ? "Tak" : "Nie";
    }
}