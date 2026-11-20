package at.bbrz.mitarbeiter.repo;

import java.util.List;

import at.bbrz.mitarbeiter.repo.entity.MitarbeiterEntity;
import org.springframework.data.repository.CrudRepository;

public interface MitarbeiterRepository extends CrudRepository<MitarbeiterEntity, Long> {
    List<MitarbeiterEntity> findAllByOrderByRankingAsc();
}
