package org.example.nabat.incident.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.example.nabat.incident.domain.ReportCategory;

/** Read-only: the tree is written by migrations, never by the application. */
@Entity
@Table(name = "report_categories")
@Getter
public class ReportCategoryJpaEntity {

    @Id
    private int code;

    @Column(name = "group_code")
    private Integer groupCode;

    @Column(nullable = false)
    private String name;

    private String hint;

    private String color;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ReportCategoryJpaEntity() {
    }

    ReportCategory toDomain() {
        return new ReportCategory(code, groupCode, name, hint, color, sortOrder);
    }
}
