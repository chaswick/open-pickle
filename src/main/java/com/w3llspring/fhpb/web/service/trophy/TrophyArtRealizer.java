package com.w3llspring.fhpb.web.service.trophy;

import com.w3llspring.fhpb.web.model.Trophy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Generates trophy artwork on-demand the first time a player unlocks it. */
@Service
public class TrophyArtRealizer {

  private static final Logger log = LoggerFactory.getLogger(TrophyArtRealizer.class);

  public TrophyArtRealizer() {}

  @Transactional
  public Trophy ensureImage(Trophy trophy) {
    if (trophy == null || trophy.getId() == null) {
      return trophy;
    }
    if (trophy.hasArt()) {
      return trophy;
    }
    log.debug(
        "Skipping in-app trophy image generation for {} (handled by standalone tooling).",
        trophy.getId());
    return trophy;
  }
}
