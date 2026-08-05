/**
 * The identity module's domain model: the types other modules may name.
 *
 * <p>Exposed as part of this module's API. Everything else under the module —
 * services and adapters — stays internal, and Spring Modulith fails the build if
 * another module reaches for it.
 */
@org.springframework.modulith.NamedInterface("domain")
package org.example.nabat.identity.domain;
