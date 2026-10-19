package org.example.nabat.identity.application;

import lombok.extern.slf4j.Slf4j;
import org.example.nabat.identity.application.port.in.ProvisionDistrictOfficialsUseCase;
import org.example.nabat.identity.application.port.out.DistrictDirectoryPort;
import org.example.nabat.identity.application.port.out.DistrictDirectoryPort.DistrictEntry;
import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.identity.domain.PasswordPolicy;
import org.example.nabat.identity.domain.User;
import org.example.nabat.shared.UseCase;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@UseCase
@Slf4j
public class DistrictOfficialProvisioningService implements ProvisionDistrictOfficialsUseCase {

    private final UserRepository userRepository;
    private final DistrictDirectoryPort districts;
    private final PasswordEncoder passwordEncoder;

    public DistrictOfficialProvisioningService(
        UserRepository userRepository,
        DistrictDirectoryPort districts,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.districts = districts;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public Result provision(String password) {
        PasswordPolicy.check(password).ifPresent(violation -> {
            throw new IllegalArgumentException("District account password rejected: " + violation);
        });

        int created = 0;
        int present = 0;
        List<String> conflicts = new ArrayList<>();
        for (DistrictEntry district : districts.all()) {
            Optional<User> existing = userRepository.findByEmail(district.mailbox());
            if (existing.isEmpty()) {
                userRepository.save(official(district, password));
                created++;
            } else if (existing.get().actsForDistrict(district.code())) {
                present++;
            } else {
                log.warn("{} already belongs to an account that does not act for {}; left alone",
                    district.mailbox(), district.code());
                conflicts.add(district.code());
            }
        }
        return new Result(created, present, List.copyOf(conflicts));
    }

    /**
     * Verified from the start. The address is the district's own mailbox, set by whoever runs
     * nabat rather than claimed by a stranger, and the verification link proves only the
     * latter.
     */
    private User official(DistrictEntry district, String password) {
        return User.create(district.mailbox(), passwordEncoder.encode(password), district.name())
            .verifyEmail()
            .appointToDistrict(district.code());
    }
}
