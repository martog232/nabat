package org.example.nabat.jurisdiction.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InstitutionJpaRepository extends JpaRepository<InstitutionJpaEntity, String> {

    List<InstitutionJpaEntity> findAllByOrderByNameAsc();

    /** Through {@code category_authorities}, which has no entity of its own: nothing writes it. */
    @Query(value = """
        SELECT i.* FROM institutions i
        JOIN category_authorities ca ON ca.institution_code = i.code
        WHERE ca.category_code = :category
        """, nativeQuery = true)
    Optional<InstitutionJpaEntity> findForCategory(@Param("category") int categoryCode);
}
