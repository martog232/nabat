/**
 * The realtime transport's delivery API. Producing modules build a WsFrame and hand it over; realtime never inspects the payload.
 *
 * <p>Exposed as part of this module's API. Everything else under the module —
 * services and adapters — stays internal, and Spring Modulith fails the build if
 * another module reaches for it.
 */
@org.springframework.modulith.NamedInterface("spi")
package org.example.nabat.realtime.spi;
