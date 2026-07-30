/**
 * Inbound ports — what the realtime module can be asked to do.
 *
 * <p>Exposed as part of this module's API. Everything else under the module —
 * services and adapters — stays internal, and Spring Modulith fails the build if
 * another module reaches for it.
 */
@org.springframework.modulith.NamedInterface("port.in")
package org.example.nabat.realtime.application.port.in;
