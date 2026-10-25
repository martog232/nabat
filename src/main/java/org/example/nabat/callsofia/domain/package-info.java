/**
 * The callsofia module's domain model: reports filed in Call Sofia, the municipality's own
 * channel, in a form nabat can compare with its own.
 *
 * <p>Exposed as part of this module's API. Everything else under the module —
 * services and adapters — stays internal, and Spring Modulith fails the build if
 * another module reaches for it.
 */
@org.springframework.modulith.NamedInterface("domain")
package org.example.nabat.callsofia.domain;
