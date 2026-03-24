package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.NameCorrection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NameCorrectionRepository
    extends JpaRepository<NameCorrection, Long>, NameCorrectionRepositoryCustom {

  Optional<NameCorrection> findTopByTokenNormalizedAndLadderConfigIdOrderByCountDesc(
      String tokenNormalized, Long ladderConfigId);

  Optional<NameCorrection> findTopByTokenNormalizedOrderByCountDesc(String tokenNormalized);

  List<NameCorrection> findByTokenNormalized(String tokenNormalized);

  // Find candidates by exact token or by phonetic key
  List<NameCorrection> findByTokenNormalizedOrPhoneticKey(
      String tokenNormalized, String phoneticKey);
}
