package org.example.nabat.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.nabat.domain.model.VoteType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_votes", schema = "PUBLIC",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"alert_id", "user_id"},
                name = "uk_alert_votes_alert_user"
        ))
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AlertVoteJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "vote_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private VoteType voteType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

}
