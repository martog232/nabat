package org.example.nabat.jurisdiction.application;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.jurisdiction.application.port.in.AssignAuthorityUseCase;
import org.example.nabat.jurisdiction.application.port.in.ListAuthoritiesUseCase;
import org.example.nabat.jurisdiction.domain.Authority;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The routing in V29 against the real tables.
 *
 * <p>Two things hold it together that no foreign key can: {@code category_authorities} names
 * categories of another module, and an authority code is looked up as a district first and an
 * institution second, which is only unambiguous while the two never share a code.
 */
@SpringBootTest
class AuthorityRoutingIntegrationTest extends PostgresTestSupport {

    @Autowired private AssignAuthorityUseCase assign;
    @Autowired private ListAuthoritiesUseCase authorities;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void aCategoryAnInstitutionTakesGoesToItWhereverTheReportIs() {
        assertThat(assign.responsibleFor("LOZENETS", 1024)).isEqualTo("CGM");
        assertThat(assign.responsibleFor("VITOSHA", 1024)).isEqualTo("CGM");
        assertThat(assign.responsibleFor("SREDETS", 1034)).isEqualTo("EKORAVNOVESIE");
        assertThat(assign.responsibleFor("SREDETS", 1036)).isEqualTo("STOLICHEN_INSPEKTORAT");
    }

    /** A pothole's owner depends on the street, which the category cannot say. */
    @Test
    void everythingElseStaysWithTheDistrict() {
        assertThat(assign.responsibleFor("LOZENETS", 1007)).isEqualTo("LOZENETS");
        assertThat(assign.responsibleFor("LOZENETS", 1001)).isEqualTo("LOZENETS");
    }

    @Test
    void everyRoutedCodeIsASubcategoryNotAGroup() {
        List<Integer> stray = jdbc.queryForList("""
            SELECT ca.category_code FROM category_authorities ca
            LEFT JOIN report_categories rc ON rc.code = ca.category_code
            WHERE rc.code IS NULL OR rc.group_code IS NULL
            """, Integer.class);

        assertThat(stray).isEmpty();
    }

    @Test
    void noInstitutionSharesACodeWithADistrict() {
        Integer shared = jdbc.queryForObject(
            "SELECT count(*) FROM institutions i JOIN districts d ON d.code = i.code", Integer.class);

        assertThat(shared).isZero();
    }

    @Test
    void listsTheDistrictsThenTheInstitutions() {
        List<Authority> all = authorities.listAuthorities();

        assertThat(all).hasSize(27);
        assertThat(all.subList(0, 24)).allMatch(a -> a.kind() == Authority.Kind.DISTRICT);
        assertThat(all.subList(24, 27)).allMatch(a -> a.kind() == Authority.Kind.INSTITUTION);
        assertThat(authorities.findAuthority("CGM")).map(Authority::mailbox)
            .contains("cgm@institution.nabat.test");
    }
}
