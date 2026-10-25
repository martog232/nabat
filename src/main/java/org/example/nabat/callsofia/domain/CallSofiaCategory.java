package org.example.nabat.callsofia.domain;

/**
 * An entry in Call Sofia's category index. Codes below 1000 are groups; a larger code is a
 * subcategory of {@code code / 1000}.
 */
public record CallSofiaCategory(int code, String name) {
}
