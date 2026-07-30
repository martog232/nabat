package org.example.nabat.identity.application.port.out;

import org.example.nabat.identity.domain.VerificationToken;
import org.example.nabat.identity.domain.VerificationTokenType;
import org.example.nabat.identity.domain.UserId;

import java.util.Optional;

public interface VerificationTokenRepository {
    VerificationToken save(VerificationToken token);
    Optional<VerificationToken> findByIdAndType(String tokenId, VerificationTokenType type);
    void deleteByUserId(UserId userId, VerificationTokenType type);
}

