package org.example.nabat.identity.application;

import org.example.nabat.identity.application.port.in.LoginUserUseCase.LoginResult;
import org.example.nabat.identity.application.port.in.SignInLinkUseCase;
import org.example.nabat.identity.application.port.out.AuthorityDirectoryPort;
import org.example.nabat.identity.application.port.out.TokenProvider;
import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.identity.application.port.out.VerificationTokenRepository;
import org.example.nabat.identity.domain.AuthenticationFailedException;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.VerificationToken;
import org.example.nabat.identity.domain.VerificationTokenType;
import org.example.nabat.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Sign-in links for officials, kept in {@code verification_tokens} beside the email and
 * password-reset tokens, and like them only as a hash.
 *
 * <p>Unlike them, a link is neither spent on use nor replaced when another is issued: one goes
 * out with every report mail, and an official may follow last week's as well as today's.
 */
@UseCase
public class SignInLinkService implements SignInLinkUseCase {

    /** One message for every failure, as for passwords and reset tokens. */
    private static final String INVALID_LINK = "Invalid or expired link";

    private final AuthorityDirectoryPort authorities;
    private final UserRepository users;
    private final VerificationTokenRepository tokens;
    private final TokenProvider tokenProvider;

    public SignInLinkService(
        AuthorityDirectoryPort authorities,
        UserRepository users,
        VerificationTokenRepository tokens,
        TokenProvider tokenProvider
    ) {
        this.authorities = authorities;
        this.users = users;
        this.tokens = tokens;
        this.tokenProvider = tokenProvider;
    }

    /**
     * The authority's own account is the one that signs in with its mailbox — the account the
     * provisioning made. Another official appointed to the same authority is not given links
     * to someone else's mailbox.
     */
    @Override
    @Transactional
    public Optional<String> issueFor(String authorityCode) {
        return authorities.all().stream()
            .filter(a -> a.code().equals(authorityCode))
            .findFirst()
            .flatMap(a -> users.findByEmail(a.mailbox()))
            .filter(User::enabled)
            .filter(u -> u.actsFor(authorityCode))
            .map(u -> {
                VerificationToken.Issued issued = VerificationToken.createSignInLink(u.id());
                tokens.save(issued.token());
                return issued.rawValue();
            });
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResult signIn(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AuthenticationFailedException(INVALID_LINK);
        }
        VerificationToken token = tokens
            .findByIdAndType(VerificationToken.hash(rawToken), VerificationTokenType.SIGN_IN_LINK)
            .filter(t -> !t.isExpired())
            .orElseThrow(() -> new AuthenticationFailedException(INVALID_LINK));

        // Re-read now: the account may have been disabled, or moved off the authority, since
        // the link went out, and the link is not a way around either.
        User user = users.findById(token.userId())
            .filter(User::enabled)
            .filter(u -> u.role().canActForAuthority())
            .orElseThrow(() -> new AuthenticationFailedException(INVALID_LINK));

        return new LoginResult(
            tokenProvider.generateAccessToken(user),
            tokenProvider.generateRefreshToken(user),
            tokenProvider.getJwtExpiration(),
            user
        );
    }
}
