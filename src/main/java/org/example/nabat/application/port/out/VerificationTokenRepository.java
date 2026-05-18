package org.example.nabat.application.port.out;

import org.example.nabat.domain.model.VerificationToken;
import org.example.nabat.domain.model.VerificationTokenType;
import org.example.nabat.domain.model.UserId;

import java.util.Optional;

public interface VerificationTokenRepository {
    VerificationToken save(VerificationToken token);
    Optional<VerificationToken> findByIdAndType(String tokenId, VerificationTokenType type);
    void deleteByUserId(UserId userId, VerificationTokenType type);
}

