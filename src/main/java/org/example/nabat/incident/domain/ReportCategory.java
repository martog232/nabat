package org.example.nabat.incident.domain;

import java.util.Objects;

/**
 * An entry in the report category tree — Call Sofia's, with its codes (V23).
 *
 * <p>Two levels. A group ({@code groupCode} null) has a colour and holds subcategories; a
 * report is always filed under a subcategory, never a group. The tree is data, not an
 * enum, because it is someone else's and can change with a migration rather than a release.
 *
 * @param hint what a citizen would call the problem, where the official name would not
 *             tell them — null on groups
 */
public record ReportCategory(
    int code,
    Integer groupCode,
    String name,
    String hint,
    String color,
    int sortOrder
) {
    public ReportCategory {
        Objects.requireNonNull(name, "name");
    }

    public boolean isGroup() {
        return groupCode == null;
    }
}
