package org.example.nabat.incident.adapter.out.persistence;

import org.example.nabat.incident.application.port.out.ReportCategoryRepository;
import org.example.nabat.incident.domain.ReportCategory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ReportCategoryRepositoryAdapter implements ReportCategoryRepository {

    private final ReportCategoryJpaRepository jpaRepository;

    public ReportCategoryRepositoryAdapter(ReportCategoryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<ReportCategory> findAll() {
        return jpaRepository.findAll().stream().map(ReportCategoryJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<ReportCategory> findByCode(int code) {
        return jpaRepository.findById(code).map(ReportCategoryJpaEntity::toDomain);
    }
}
