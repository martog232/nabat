package org.example.nabat.identity.adapter.in.startup;

import org.example.nabat.identity.application.port.in.ProvisionOfficialsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Creates the missing official accounts at startup, when given a password for them.
 *
 * <p>Off unless {@code NABAT_OFFICIAL_ACCOUNTS_PASSWORD} is set. The password is not committed
 * anywhere, neither in a migration nor as a default, because every account it opens can close
 * the reports of a district or an institution. Synchronous: one bcrypt hash per authority, a
 * few seconds, and an official should be able to sign in as soon as the application reports ready.
 */
@Component
public class OfficialProvisioner {

    private static final Logger log = LoggerFactory.getLogger(OfficialProvisioner.class);

    private final ProvisionOfficialsUseCase provisioning;
    private final String password;

    public OfficialProvisioner(
        ProvisionOfficialsUseCase provisioning,
        @Value("${nabat.official-accounts.password:}") String password
    ) {
        this.provisioning = provisioning;
        this.password = password;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void provisionOnStartup() {
        if (password.isBlank()) {
            return;
        }
        try {
            ProvisionOfficialsUseCase.Result result = provisioning.provision(password);
            log.info("Official accounts: {} created, {} already present, conflicts: {}",
                result.created(), result.alreadyPresent(), result.conflicts());
        } catch (RuntimeException e) {
            // Not fatal: nothing else in the application needs these accounts to run.
            log.error("Official accounts were not provisioned", e);
        }
    }
}
