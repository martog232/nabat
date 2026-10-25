package org.example.nabat.identity.application.port.in;

import java.util.Optional;

/**
 * A link that signs an official in, for the "Отговори" button in a report mail.
 *
 * <p>Without it the button led to a sign-in form for an account whose password only the
 * person running nabat knew; the way in was "forgot password" to the same mailbox, which
 * nobody would think of. The link adds no power the mailbox did not have — the official
 * account signs in with it, so whoever reads it could reset the password from it anyway.
 */
public interface SignInLinkUseCase {

    /**
     * A fresh link secret for the authority's own account — the one that signs in with its
     * mailbox — or empty if it has none, or it is disabled. Only the secret's hash is stored.
     */
    Optional<String> issueFor(String authorityCode);

    /**
     * A session for the account the link was issued to. Not spent on use: every official
     * reading the mailbox may follow it until it expires.
     *
     * @throws org.example.nabat.identity.domain.AuthenticationFailedException for an unknown or
     *         expired link, a disabled account, or one that no longer acts for an authority —
     *         all with one message, so the answer says nothing about which
     */
    LoginUserUseCase.LoginResult signIn(String rawToken);
}
