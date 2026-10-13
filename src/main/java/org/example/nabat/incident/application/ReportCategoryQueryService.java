package org.example.nabat.incident.application;

import org.example.nabat.incident.application.port.in.ListReportCategoriesUseCase;
import org.example.nabat.incident.application.port.out.ReportCategoryRepository;
import org.example.nabat.incident.domain.ReportCategory;
import org.example.nabat.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@UseCase
public class ReportCategoryQueryService implements ListReportCategoriesUseCase {

    private static final Comparator<ReportCategory> DISPLAY_ORDER =
        Comparator.comparingInt(ReportCategory::sortOrder).thenComparingInt(ReportCategory::code);

    private final ReportCategoryRepository categories;

    public ReportCategoryQueryService(ReportCategoryRepository categories) {
        this.categories = categories;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryGroup> listCategories() {
        List<ReportCategory> all = categories.findAll();
        Map<Integer, List<ReportCategory>> byGroup = all.stream()
            .filter(category -> !category.isGroup())
            .collect(Collectors.groupingBy(ReportCategory::groupCode));
        return all.stream()
            .filter(ReportCategory::isGroup)
            .sorted(DISPLAY_ORDER)
            .map(group -> new CategoryGroup(group, byGroup.getOrDefault(group.code(), List.of()).stream()
                .sorted(DISPLAY_ORDER)
                .toList()))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CategoryGroup.Placed> findSubcategory(int code) {
        return categories.findByCode(code)
            .filter(category -> !category.isGroup())
            .flatMap(category -> categories.findByCode(category.groupCode())
                .map(group -> new CategoryGroup.Placed(group, category)));
    }
}
