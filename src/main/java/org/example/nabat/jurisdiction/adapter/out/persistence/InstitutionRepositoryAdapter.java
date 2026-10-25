package org.example.nabat.jurisdiction.adapter.out.persistence;

import org.example.nabat.jurisdiction.application.port.out.InstitutionRepository;
import org.example.nabat.jurisdiction.domain.Institution;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class InstitutionRepositoryAdapter implements InstitutionRepository {

    private final InstitutionJpaRepository jpaRepository;

    public InstitutionRepositoryAdapter(InstitutionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Institution> findAllOrderedByName() {
        return jpaRepository.findAllByOrderByNameAsc().stream()
            .map(InstitutionJpaEntity::toDomain)
            .toList();
    }

    @Override
    public Optional<Institution> findByCode(String code) {
        return jpaRepository.findById(code).map(InstitutionJpaEntity::toDomain);
    }

    @Override
    public Optional<Institution> findForCategory(int categoryCode) {
        return jpaRepository.findForCategory(categoryCode).map(InstitutionJpaEntity::toDomain);
    }
}
