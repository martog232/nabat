package org.example.nabat.incident.application.port.out;

import org.example.nabat.incident.domain.ReportCategory;

import java.util.List;
import java.util.Optional;

public interface ReportCategoryRepository {

    /** Groups and subcategories alike, each level in its display order. */
    List<ReportCategory> findAll();

    Optional<ReportCategory> findByCode(int code);
}
