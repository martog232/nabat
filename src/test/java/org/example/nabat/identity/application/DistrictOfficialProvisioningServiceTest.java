package org.example.nabat.identity.application;

import org.example.nabat.identity.application.port.in.ProvisionDistrictOfficialsUseCase.Result;
import org.example.nabat.identity.application.port.out.DistrictDirectoryPort;
import org.example.nabat.identity.application.port.out.DistrictDirectoryPort.DistrictEntry;
import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.User;
import org.example.nabat.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistrictOfficialProvisioningServiceTest {

    private static final String PASSWORD = "Rayon2026pass";
    private static final DistrictEntry LOZENETS =
        new DistrictEntry("LOZENETS", "Район Лозенец", "lozenets@rayon.nabat.test");
    private static final DistrictEntry SREDETS =
        new DistrictEntry("SREDETS", "Район Средец", "sredets@rayon.nabat.test");

    @Mock
    private UserRepository userRepository;

    @Mock
    private DistrictDirectoryPort districts;

    @Mock
    private PasswordEncoder passwordEncoder;

    private DistrictOfficialProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new DistrictOfficialProvisioningService(userRepository, districts, passwordEncoder);
    }

    @Test
    void createsAVerifiedOfficialSigningInWithTheDistrictMailbox() {
        when(districts.all()).thenReturn(List.of(LOZENETS));
        when(userRepository.findByEmail(LOZENETS.mailbox())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");

        Result result = service.provision(PASSWORD);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User official = saved.getValue();
        assertEquals("lozenets@rayon.nabat.test", official.email());
        assertEquals("Район Лозенец", official.displayName());
        assertEquals("hashed", official.password());
        assertEquals(Role.DISTRICT_OFFICIAL, official.role());
        assertTrue(official.actsForDistrict("LOZENETS"));
        assertTrue(official.emailVerified());
        assertTrue(official.enabled());
        assertEquals(new Result(1, 0, List.of()), result);
    }

    /** A second start must not reset a password the official may have changed since. */
    @Test
    void leavesAnExistingOfficialUntouched() {
        User existing = Fixtures.user(LOZENETS.mailbox()).appointToDistrict("LOZENETS");
        when(districts.all()).thenReturn(List.of(LOZENETS));
        when(userRepository.findByEmail(LOZENETS.mailbox())).thenReturn(Optional.of(existing));

        Result result = service.provision(PASSWORD);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
        assertEquals(new Result(0, 1, List.of()), result);
    }

    /** Promoting somebody's account because it happens to hold the address is a grant nobody made. */
    @Test
    void doesNotPromoteAnAccountThatAlreadyHoldsTheMailbox() {
        User someone = Fixtures.user(SREDETS.mailbox());
        when(districts.all()).thenReturn(List.of(LOZENETS, SREDETS));
        when(userRepository.findByEmail(LOZENETS.mailbox())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(SREDETS.mailbox())).thenReturn(Optional.of(someone));
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");

        Result result = service.provision(PASSWORD);

        verify(userRepository, times(1)).save(argThat(u -> u.email().equals(LOZENETS.mailbox())));
        assertEquals(new Result(1, 0, List.of("SREDETS")), result);
    }

    @Test
    void refusesAPasswordThePolicyRefuses() {
        assertThrows(IllegalArgumentException.class, () -> service.provision("short1"));

        verifyNoInteractions(districts, userRepository, passwordEncoder);
    }
}
