package org.example.nabat.application.service;

import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.application.port.out.AlertVoteRepository;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class AlertVoteServiceTest {

    @Mock
    private AlertVoteRepository alertVoteRepository;

    @Mock
    private AlertRepository alertRepository;

    private AlertVoteService alertVoteService;

    private AlertId testAlertId;
    private UserId testUserId;

    @BeforeEach
    void setUp() {
        alertVoteService = new AlertVoteService(alertVoteRepository, alertRepository);
        testAlertId = new AlertId(UUID.randomUUID());
        testUserId = new UserId(UUID.randomUUID());
    }

    @Test
    void shouldCreateNewVote() {
        
    }
}
