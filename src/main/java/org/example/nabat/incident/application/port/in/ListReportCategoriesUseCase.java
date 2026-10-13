package org.example.nabat.incident.application.port.in;

import org.example.nabat.incident.domain.ReportCategory;

import java.util.List;
import java.util.Optional;

/**
 * The report category tree — Call Sofia's groups and subcategories.
 */
public interface ListReportCategoriesUseCase {

    /** Every group, in display order, each with its subcategories in display order. */
    List<CategoryGroup> listCategories();

    /**
     * The subcategory with this code, with its group — for anything that has to name a
     * report's category in words, such as the mail to the district.
     *
     * @return empty for an unknown code and for a group code
     */
    Optional<CategoryGroup.Placed> findSubcategory(int code);

    record CategoryGroup(ReportCategory group, List<ReportCategory> categories) {

        /** A subcategory and the group it is under. */
        public record Placed(ReportCategory group, ReportCategory category) {

            /** „Пътища и шахти — Улично платно“: how the category reads in full. */
            public String label() {
                return group.name() + " — " + category.name();
            }
        }
    }
}
