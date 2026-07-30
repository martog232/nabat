/**
 * Outbound ports — what the voting module needs from its environment.
 *
 * <p>Exposed as part of this module's API. Everything else under the module —
 * services and adapters — stays internal, and Spring Modulith fails the build if
 * another module reaches for it.
 */
@org.springframework.modulith.NamedInterface("port.out")
package org.example.nabat.voting.application.port.out;
