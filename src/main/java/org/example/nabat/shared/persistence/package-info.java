/**
 * Database capability detection, used by every module that runs geo queries.
 *
 * <p>Exposed as part of this module's API. Everything else under the module —
 * services and adapters — stays internal, and Spring Modulith fails the build if
 * another module reaches for it.
 */
@org.springframework.modulith.NamedInterface("persistence")
package org.example.nabat.shared.persistence;
