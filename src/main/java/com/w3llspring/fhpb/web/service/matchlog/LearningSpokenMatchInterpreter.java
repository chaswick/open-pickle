package com.w3llspring.fhpb.web.service.matchlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.w3llspring.fhpb.web.db.NameCorrectionRepository;
import com.w3llspring.fhpb.web.model.InterpretationEvent;
import com.w3llspring.fhpb.web.model.NameCorrection;
import com.w3llspring.fhpb.web.service.matchlog.SpokenMatchInterpretation.PlayerResolution;
import com.w3llspring.fhpb.web.service.matchlog.SpokenMatchInterpretation.Team;
import jakarta.transaction.Transactional;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service("learningSpokenMatchInterpreter")
public class LearningSpokenMatchInterpreter implements SpokenMatchInterpreter {

  private static final Logger log = LoggerFactory.getLogger(LearningSpokenMatchInterpreter.class);

  private final DefaultSpokenMatchInterpreter delegate;
  private final NameCorrectionRepository nameCorrectionRepository;
  private final InterpretationEventWriter interpretationEventWriter;
  private final ObjectMapper objectMapper;

  public LearningSpokenMatchInterpreter(
      DefaultSpokenMatchInterpreter delegate,
      NameCorrectionRepository nameCorrectionRepository,
      InterpretationEventWriter interpretationEventWriter,
      ObjectMapper objectMapper) {
    this.delegate = delegate;
    this.nameCorrectionRepository = nameCorrectionRepository;
    this.interpretationEventWriter = interpretationEventWriter;
    this.objectMapper = objectMapper;
  }

  /**
   * Backwards-compatible constructor used by tests that supply an InterpretationEventRepository
   * instead of an InterpretationEventWriter. Wrap the repository in an InterpretationEventWriter.
   */
  @org.springframework.beans.factory.annotation.Autowired
  public LearningSpokenMatchInterpreter(
      DefaultSpokenMatchInterpreter delegate,
      NameCorrectionRepository nameCorrectionRepository,
      com.w3llspring.fhpb.web.db.InterpretationEventRepository interpretationEventRepository,
      ObjectMapper objectMapper) {
    this(
        delegate,
        nameCorrectionRepository,
        new InterpretationEventWriter(interpretationEventRepository),
        objectMapper);
  }

  @Override
  @Transactional
  public SpokenMatchInterpretation interpret(SpokenMatchInterpretationRequest request) {
    SpokenMatchInterpretation interpretation = delegate.interpret(request);

    // Persist a lightweight interpretation event for later correlation/learning
    try {
      InterpretationEvent ev = new InterpretationEvent();
      ev.setEventUuid(java.util.UUID.randomUUID().toString());
      ev.setCreatedAt(Instant.now());
      ev.setLadderConfigId(request != null ? request.getLadderConfigId() : null);
      ev.setSeasonId(request != null ? request.getSeasonId() : null);
      ev.setCurrentUserId(request != null ? request.getCurrentUserId() : null);
      ev.setTranscript(request != null ? request.getTranscript() : null);
      ev.setAutoSubmitted(false);
      try {
        String json = objectMapper.writeValueAsString(interpretation);
        ev.setInterpretationJson(json);
      } catch (Exception e) {
        log.debug("Failed to serialize interpretation JSON", e);
      }
      ev.setInterpreterVersion("learning-v1");
      try {
        interpretationEventWriter.write(ev);
      } catch (Exception wex) {
        // Do not propagate writer exceptions; they run in a separate transaction
        // and should not interfere with the main interpret flow.
        log.warn("InterpretationEventWriter failed: {}", wex.getMessage());
      }
    } catch (Exception ex) {
      log.warn("Failed to persist interpretation event: {}", ex.getMessage());
    }

    // Apply name corrections (tiered lookup)
    if (interpretation != null && interpretation.getTeams() != null) {
      Long ladderId = request != null ? request.getLadderConfigId() : null;
      for (Team t : interpretation.getTeams()) {
        for (PlayerResolution pr : t.getPlayers()) {
          String token = pr.getToken();
          if (token == null || token.isBlank()) continue;
          String norm = normalizeToken(token);
          try {
            // Promotion and ambiguity thresholds (configurable via system properties)
            final int promotionThreshold =
                Integer.parseInt(
                    System.getProperty(
                        "learning.promotionThreshold", "1")); // default 1 for backwards-compat
            final int gapThreshold =
                Integer.parseInt(System.getProperty("learning.gapThreshold", "0")); // default 0
            final double decayPerMonth =
                Double.parseDouble(
                    System.getProperty("learning.decayPerMonth", "0.5")); // default 0.5
            // Prefer corrections authored by the reporting user (small boost)
            Long currentUser = request != null ? request.getCurrentUserId() : null;
            final int reporterBoost =
                Integer.parseInt(System.getProperty("learning.reporterBoost", "1"));

            // compute phonetic key of the token and fetch candidates by token or phonetic match
            String phoneticKey = null;
            try {
              com.w3llspring.fhpb.web.service.matchlog.DoubleMetaphone dm =
                  new com.w3llspring.fhpb.web.service.matchlog.DoubleMetaphone();
              String[] keys = dm.doubleMetaphone(norm);
              if (keys != null && keys.length > 0) phoneticKey = keys[0];
            } catch (Exception ex) {
              // ignore phonetic failures
            }

            java.util.List<NameCorrection> candidates = null;
            try {
              if (phoneticKey != null && !phoneticKey.isBlank()) {
                candidates =
                    nameCorrectionRepository.findByTokenNormalizedOrPhoneticKey(norm, phoneticKey);
              } else {
                candidates = nameCorrectionRepository.findByTokenNormalized(norm);
              }
            } catch (Exception ex) {
              candidates = nameCorrectionRepository.findByTokenNormalized(norm);
            }

            if (candidates != null && !candidates.isEmpty()) {
              if (log.isDebugEnabled()) {
                log.debug(
                    "Found {} candidate(s) for token='{}' ladder={} - inspecting for promotion",
                    candidates.size(),
                    token,
                    ladderId);
              }
              // Prefer ladder-scoped candidates; if none found, use all candidates
              java.util.List<NameCorrection> ladderCandidates = new java.util.ArrayList<>();
              for (NameCorrection nc : candidates) {
                if (ladderId != null
                    && java.util.Objects.equals(nc.getLadderConfigId(), ladderId)) {
                  ladderCandidates.add(nc);
                }
              }
              java.util.List<NameCorrection> pool =
                  ladderCandidates.isEmpty() ? candidates : ladderCandidates;

              if (log.isDebugEnabled()) {
                log.debug(
                    "Candidate pool size for token='{}' (ladderCandidates.size={} candidates.size={})",
                    token,
                    ladderCandidates.size(),
                    candidates.size());
              }

              // Compute effective counts (apply decay) and find top two
              double bestEff = Double.NEGATIVE_INFINITY;
              double runnerEff = Double.NEGATIVE_INFINITY;
              NameCorrection bestNc = null;
              NameCorrection runnerNc = null;
              long nowMillis = java.time.Instant.now().toEpochMilli();
              for (NameCorrection nc : pool) {
                double base = nc.getCount() != null ? nc.getCount() : 0.0;
                if (nc.getLastConfirmedAt() != null) {
                  long ageMs = nowMillis - nc.getLastConfirmedAt().toEpochMilli();
                  double months = Math.max(0.0, ageMs / (1000.0 * 60 * 60 * 24 * 30));
                  base = base - (months * decayPerMonth);
                }
                double eff = base;
                // runner-up calculation should ignore reporter boost
                double effForSelection = eff;
                if (currentUser != null
                    && nc.getReporterUserId() != null
                    && nc.getReporterUserId().equals(currentUser)) {
                  effForSelection += reporterBoost;
                }
                if (log.isDebugEnabled()) {
                  log.debug(
                      "Candidate id={} userId={} base={} effForSelection={} lastConfirmedAt={}",
                      nc.getId(),
                      nc.getUserId(),
                      base,
                      effForSelection,
                      nc.getLastConfirmedAt());
                }
                if (effForSelection > bestEff) {
                  runnerEff = bestEff;
                  runnerNc = bestNc;
                  bestEff = effForSelection;
                  bestNc = nc;
                } else if (effForSelection > runnerEff) {
                  runnerEff = effForSelection;
                  runnerNc = nc;
                }
              }

              // Only apply if best meets promotion and gap thresholds
              double bestBaseEff = Double.NEGATIVE_INFINITY;
              double runnerBaseEff = Double.NEGATIVE_INFINITY;
              if (bestNc != null) {
                bestBaseEff = (bestNc.getCount() != null ? bestNc.getCount() : 0.0);
                if (bestNc.getLastConfirmedAt() != null) {
                  long ageMs = nowMillis - bestNc.getLastConfirmedAt().toEpochMilli();
                  double months = Math.max(0.0, ageMs / (1000.0 * 60 * 60 * 24 * 30));
                  bestBaseEff = bestBaseEff - (months * decayPerMonth);
                }
              }
              if (runnerNc != null) {
                runnerBaseEff = (runnerNc.getCount() != null ? runnerNc.getCount() : 0.0);
                if (runnerNc.getLastConfirmedAt() != null) {
                  long ageMs = nowMillis - runnerNc.getLastConfirmedAt().toEpochMilli();
                  double months = Math.max(0.0, ageMs / (1000.0 * 60 * 60 * 24 * 30));
                  runnerBaseEff = runnerBaseEff - (months * decayPerMonth);
                }
              }

              if (log.isDebugEnabled()) {
                log.debug(
                    "Promotion check for token='{}' bestCandidate={} bestEff={} runnerCandidate={} runnerEff={} promotionThreshold={} gapThreshold={}",
                    token,
                    bestNc != null
                        ? ("id=" + bestNc.getId() + ",user=" + bestNc.getUserId())
                        : "<none>",
                    bestBaseEff,
                    runnerNc != null
                        ? ("id=" + runnerNc.getId() + ",user=" + runnerNc.getUserId())
                        : "<none>",
                    runnerBaseEff,
                    promotionThreshold,
                    gapThreshold);
              }

              if (bestBaseEff != Double.NEGATIVE_INFINITY
                  && bestBaseEff >= promotionThreshold
                  && (bestBaseEff
                          - (runnerBaseEff == Double.NEGATIVE_INFINITY ? 0.0 : runnerBaseEff))
                      >= gapThreshold) {
                // apply best (guard null)
                if (bestNc != null) {
                  Long previousMatched = pr.getMatchedUserId();
                  double previousConf = pr.getConfidence();
                  pr.setMatchedUserId(bestNc.getUserId());
                  pr.setMatchedAlias("correction");
                  pr.setConfidence(Math.max(pr.getConfidence(), 0.92));
                  if (log.isInfoEnabled()) {
                    log.info(
                        "Promoted name correction applied: token='{}' previousMatched={} previousConf={} -> promotedUser={} promotedConf={} ladder={} effBest={} effRunner={}",
                        token,
                        previousMatched,
                        previousConf,
                        bestNc.getUserId(),
                        pr.getConfidence(),
                        bestNc.getLadderConfigId(),
                        bestBaseEff,
                        runnerBaseEff);
                  } else if (log.isDebugEnabled()) {
                    log.debug(
                        "Applied promoted name correction: token='{}' -> user={} (ladder={}, effBest={}, effRunner={})",
                        token,
                        bestNc.getUserId(),
                        bestNc.getLadderConfigId(),
                        bestBaseEff,
                        runnerBaseEff);
                  }
                }
              } else {
                if (log.isDebugEnabled()) {
                  log.debug(
                      "Did not apply correction for token='{}' bestEff={} runnerEff={} promotionThreshold={} gapThreshold={}",
                      token,
                      bestBaseEff,
                      runnerBaseEff,
                      promotionThreshold,
                      gapThreshold);
                }
              }
            }
          } catch (Exception e) {
            log.warn("Error applying name correction for token '{}': {}", token, e.getMessage());
          }
        }
      }
    }

    return interpretation;
  }

  private String normalizeToken(String token) {
    if (token == null) return null;
    String s = token.toLowerCase(java.util.Locale.ROOT).trim();
    // remove punctuation except spaces
    s = s.replaceAll("[^a-z0-9\\s]", "");
    s = s.replaceAll("\\s+", " ");
    return s;
  }
}
