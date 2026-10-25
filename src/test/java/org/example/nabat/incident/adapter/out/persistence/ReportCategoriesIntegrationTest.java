package org.example.nabat.incident.adapter.out.persistence;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.incident.application.ReportCategoryQueryService;
import org.example.nabat.incident.application.port.in.ListReportCategoriesUseCase;
import org.example.nabat.incident.application.port.in.ListReportCategoriesUseCase.CategoryGroup;
import org.example.nabat.incident.domain.ReportCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tree V23 loads, through the service that serves it. Against the real migration,
 * because what can go wrong is in the data: a subcategory under the wrong group, a group
 * without a colour, a code that is not Call Sofia's.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReportCategoryQueryService.class, ReportCategoryRepositoryAdapter.class})
class ReportCategoriesIntegrationTest extends PostgresTestSupport {

    @Autowired
    private ListReportCategoriesUseCase categories;

    @Test
    void holdsCallSofiasTwelveGroupsInItsOrder() {
        List<CategoryGroup> groups = categories.listCategories();

        assertThat(groups).extracting(group -> group.group().code())
            .containsExactly(1000, 1006, 1012, 1018, 1021, 1027, 1032, 1035, 1038, 1042, 1046, 1049);
        assertThat(groups).allSatisfy(group -> {
            assertThat(group.group().color()).matches("#[0-9A-F]{6}");
            assertThat(group.categories()).isNotEmpty();
        });
    }

    /**
     * Every subcategory carries a hint — the reason they exist is that the official names
     * do not say what they cover — and every code is one Call Sofia uses.
     */
    @Test
    void everySubcategoryIsCallSofiasAndSaysWhatItCovers() {
        List<ReportCategory> subcategories = categories.listCategories().stream()
            .flatMap(group -> group.categories().stream())
            .toList();

        assertThat(subcategories).hasSize(38);
        assertThat(subcategories).allSatisfy(category -> {
            assertThat(category.code()).isBetween(1001, 1059);
            assertThat(category.hint()).isNotBlank();
            assertThat(category.color()).isNull();
        });
        assertThat(subcategories).extracting(ReportCategory::code)
            .doesNotContain(1026, 1050)
            .doesNotHaveDuplicates();
    }

    @Test
    void namesASubcategoryWithItsGroup() {
        assertThat(categories.findSubcategory(1007))
            .map(CategoryGroup.Placed::label)
            .contains("Пътища и шахти — Улично платно");
    }

    @Test
    void aGroupIsNotASubcategory() {
        assertThat(categories.findSubcategory(1006)).isEmpty();
        assertThat(categories.findSubcategory(4242)).isEmpty();
    }
}
