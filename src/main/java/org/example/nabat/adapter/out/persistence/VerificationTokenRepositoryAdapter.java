package org.example.nabat.adapter.out.persistence;

import org.example.nabat.application.port.out.VerificationTokenRepository;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.VerificationToken;
import org.example.nabat.domain.model.VerificationTokenType;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class VerificationTokenRepositoryAdapter implements VerificationTokenRepository {

    private final VerificationTokenJpaRepository jpa;

    public VerificationTokenRepositoryAdapter(VerificationTokenJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public VerificationToken save(VerificationToken token) {
        return jpa.save(VerificationTokenJpaEntity.from(token)).toDomain();
    }

    @Override
    public Optional<VerificationToken> findByIdAndType(String tokenId, VerificationTokenType type) {
        return jpa.findByIdAndType(tokenId, type).map(VerificationTokenJpaEntity::toDomain);
    }

    @Override
    public void deleteByUserId(UserId userId, VerificationTokenType type) {
        jpa.deleteByUserIdAndType(userId.value(), type);
    }
}

