/**
 * The event contract shared with nabat-voting, generated from {@code src/main/avro}.
 *
 * <p>An open module, and the second sink in the dependency graph after {@code shared}. Any
 * module that consumes a topic will need these types, and there is nothing to hide behind a
 * named interface: the whole package is generated from a schema that another repository also
 * owns a copy of, so its shape is settled somewhere else entirely.
 *
 * <p>It became a module by accident — Modulith treats every direct sub-package of the root as
 * one, and the Avro namespace put the generated classes here. Declaring what it is beats
 * moving the namespace outside {@code org.example.nabat}: a record's fully-qualified name is
 * part of its Avro schema, so that rename would be an incompatible schema change, made to
 * satisfy a package convention rather than a consumer.
 */
@org.springframework.modulith.ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.OPEN,
    displayName = "Vote event contract (generated)"
)
package org.example.nabat.events;
