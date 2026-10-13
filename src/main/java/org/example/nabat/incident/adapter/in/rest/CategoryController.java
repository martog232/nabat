package org.example.nabat.incident.adapter.in.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.example.nabat.incident.application.port.in.ListReportCategoriesUseCase;
import org.example.nabat.incident.application.port.in.ListReportCategoriesUseCase.CategoryGroup;
import org.example.nabat.incident.domain.ReportCategory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final ListReportCategoriesUseCase listCategories;

    public CategoryController(ListReportCategoriesUseCase listCategories) {
        this.listCategories = listCategories;
    }

    /**
     * The category tree, so a client can offer it when reporting and name a report's
     * {@code categoryCode}. Public and cacheable for a day, like the districts: it changes
     * with a migration, not with use.
     */
    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Report categories — Call Sofia's groups and subcategories, with its codes")
    public ResponseEntity<List<CategoryGroupResponse>> list() {
        List<CategoryGroupResponse> body = listCategories.listCategories().stream()
            .map(CategoryGroupResponse::from)
            .toList();
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
            .body(body);
    }

    @Schema(description = "A group of report categories")
    public record CategoryGroupResponse(
        @Schema(description = "Group code; usable as the nearby `category` filter", example = "1006") int code,
        @Schema(description = "Name of the group", example = "Пътища и шахти") String name,
        @Schema(description = "Call Sofia's colour for the group", example = "#1E3A8A") String color,
        @Schema(description = "The subcategories a report can be filed under, in display order") List<CategoryResponse> categories
    ) {
        static CategoryGroupResponse from(CategoryGroup group) {
            return new CategoryGroupResponse(
                group.group().code(),
                group.group().name(),
                group.group().color(),
                group.categories().stream().map(CategoryResponse::from).toList());
        }
    }

    @Schema(description = "A category a report can be filed under")
    public record CategoryResponse(
        @Schema(description = "Code, as sent in categoryCode", example = "1007") int code,
        @Schema(description = "Name, as Call Sofia has it", example = "Улично платно") String name,
        @Schema(description = "What it covers, in everyday words", example = "дупки, пропаднала настилка, шахти без капак") String hint
    ) {
        static CategoryResponse from(ReportCategory category) {
            return new CategoryResponse(category.code(), category.name(), category.hint());
        }
    }
}
