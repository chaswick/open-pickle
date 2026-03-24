package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.TrophyArt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrophyArtRepository extends JpaRepository<TrophyArt, Long> {

  Optional<TrophyArt> findByStorageKey(String storageKey);
}
