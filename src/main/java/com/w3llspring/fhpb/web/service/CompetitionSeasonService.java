package com.w3llspring.fhpb.web.service;

import com.w3llspring.fhpb.web.db.LadderConfigRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.model.LadderConfig;
import com.w3llspring.fhpb.web.model.LadderSeason;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CompetitionSeasonService {

  private final LadderConfigRepository ladderConfigRepository;
  private final LadderSeasonRepository ladderSeasonRepository;
  private final Long configuredCompetitionLadderId;

  public CompetitionSeasonService(
      LadderConfigRepository ladderConfigRepository,
      LadderSeasonRepository ladderSeasonRepository,
      @Value("${fhpb.competition.ladder-id:#{null}}") Long configuredCompetitionLadderId) {
    this.ladderConfigRepository = ladderConfigRepository;
    this.ladderSeasonRepository = ladderSeasonRepository;
    this.configuredCompetitionLadderId = configuredCompetitionLadderId;
  }

  public LadderSeason resolveActiveCompetitionSeason() {
    if (configuredCompetitionLadderId != null) {
      LadderSeason active =
          ladderSeasonRepository.findActive(configuredCompetitionLadderId).orElse(null);
      if (active == null || active.getId() == null) {
        return null;
      }
      return ladderSeasonRepository.findByIdWithLadderConfig(active.getId()).orElse(active);
    }

    LadderConfig competition =
        ladderConfigRepository
            .findFirstByTypeOrderByIdAsc(LadderConfig.Type.COMPETITION)
            .orElse(null);
    if (competition == null || competition.getId() == null) {
      return null;
    }
    LadderSeason active = ladderSeasonRepository.findActive(competition.getId()).orElse(null);
    if (active == null || active.getId() == null) {
      return null;
    }
    return ladderSeasonRepository.findByIdWithLadderConfig(active.getId()).orElse(active);
  }

  public LadderSeason resolveTargetSeason(LadderConfig ladderConfig) {
    if (ladderConfig == null) {
      return null;
    }
    if (ladderConfig.getTargetSeasonId() != null) {
      return ladderSeasonRepository
          .findByIdWithLadderConfig(ladderConfig.getTargetSeasonId())
          .orElse(null);
    }
    if (ladderConfig.isCompetitionType() && ladderConfig.getId() != null) {
      LadderSeason active = ladderSeasonRepository.findActive(ladderConfig.getId()).orElse(null);
      if (active == null || active.getId() == null) {
        return null;
      }
      return ladderSeasonRepository.findByIdWithLadderConfig(active.getId()).orElse(active);
    }
    return null;
  }
}
