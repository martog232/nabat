package org.example.nabat.incident.domain;

/**
 * A report named a category code that is not a subcategory in the tree — unknown, or a
 * group. A client error: the request is well-formed and names something that cannot hold
 * a report.
 */
public class UnknownCategoryException extends IllegalArgumentException {

    private final int code;

    public UnknownCategoryException(int code) {
        super("Not a report category: " + code);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
