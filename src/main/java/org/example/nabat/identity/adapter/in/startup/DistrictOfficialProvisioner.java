package org.example.nabat.identity.adapter.in.startup;

import org.example.nabat.identity.application.port.in.ProvisionDistrictOfficialsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Creates the missing district official accounts at startup, when given a password for them.
 *
 * <p>Off unless {@code NABAT_DISTRICT_ACCOUNTS_PASSWORD} is set. The password is not committed
 * anywhere, neither in a migration nor as a default, because every account it opens can close
 * the reports of a district. Synchronous: at most 24 bcrypt hashes take a couple of seconds,
 * and an official should be able to sign in as soon as the application reports ready.
 */
@Component
public class DistrictOfficialProvisioner {

    private static final Logger log = LoggerFactory.getLogger(DistrictOfficialProvisioner.class);

    private final ProvisionDistrictOfficialsUseCase provisioning;
    private final String password;

    public DistrictOfficialProvisioner(
        ProvisionDistrictOfficialsUseCase provisioning,
        @Value("${nabat.district-accounts.password:}") String password
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
            ProvisionDistrictOfficialsUseCase.Result result = provisioning.provision(password);
            log.info("District official accounts: {} created, {} already present, conflicts: {}",
                result.created(), result.alreadyPresent(), result.conflicts());
        } catch (RuntimeException e) {
            // Not fatal: nothing else in the application needs these accounts to run.
            log.error("District official accounts were not provisioned", e);
        }
    }
}
