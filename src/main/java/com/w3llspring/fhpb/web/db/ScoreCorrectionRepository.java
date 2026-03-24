package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.ScoreCorrection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScoreCorrectionRepository extends JpaRepository<ScoreCorrection, Long> {

  Optional<ScoreCorrection>
      findTopByLadderConfigIdAndScoreFieldAndInterpretedValueAndCorrectedValueOrderByCountDesc(
          Long ladderConfigId, String scoreField, Integer interpretedValue, Integer correctedValue);
}
