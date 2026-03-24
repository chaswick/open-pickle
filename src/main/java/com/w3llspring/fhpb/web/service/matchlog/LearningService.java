package com.w3llspring.fhpb.web.service.matchlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.w3llspring.fhpb.web.db.InterpretationEventRepository;
import com.w3llspring.fhpb.web.db.MatchRepository;
import com.w3llspring.fhpb.web.db.NameCorrectionRepository;
import com.w3llspring.fhpb.web.db.ScoreCorrectionRepository;
import com.w3llspring.fhpb.web.model.InterpretationEvent;
import com.w3llspring.fhpb.web.model.Match;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LearningService {

  private static final Logger log = LoggerFactory.getLogger(LearningService.class);

  private final NameCorrectionRepository nameCorrectionRepository;
  private final InterpretationEventRepository interpretationEventRepository;
  private final ScoreCorrectionRepository scoreCorrectionRepository;
  private final MatchRepository matchRepository;
  private final ObjectMapper objectMapper;

  @Autowired
  public LearningService(
      NameCorrectionRepository nameCorrectionRepository,
      InterpretationEventRepository interpretationEventRepository,
      ScoreCorrectionRepository scoreCorrectionRepository,
      MatchRepository matchRepository) {
    this.nameCorrectionRepository = nameCorrectionRepository;
    this.interpretationEventRepository = interpretationEventRepository;
    this.scoreCorrectionRepository = scoreCorrectionRepository;
    this.matchRepository = matchRepository;
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Apply corrections for a saved match by looking up the latest InterpretationEvent and using the
   * stored interpretation JSON. This is intended to be called after a human edits/confirms a
   * previously voice-logged match.
   */
  public void applyCorrectionsForMatch(Long matchId) {
    if (matchId == null) return;
    // Find the latest interpretation event for this match
    List<InterpretationEvent> events =
        interpretationEventRepository.findByMatchIdOrderByCreatedAtDesc(matchId);
    if (events == null || events.isEmpty()) {
      if (log.isDebugEnabled())
        log.debug("No InterpretationEvent found for match {} to apply corrections", matchId);
      return;
    }
    InterpretationEvent ev = events.get(0);
    if (ev == null || ev.getInterpretationJson() == null) {
      if (log.isDebugEnabled())
        log.debug("InterpretationEvent has no interpretationJson for match {}", matchId);
      return;
    }
    try {
      SpokenMatchInterpretation interpretation =
          objectMapper.readValue(ev.getInterpretationJson(), SpokenMatchInterpretation.class);
      applyCorrectionsFromInterpretation(interpretation, matchId, ev.getLadderConfigId(), true);
    } catch (Exception e) {
      log.warn(
          "Failed to deserialize interpretation JSON for match {}: {}", matchId, e.getMessage());
    }
  }

  /**
   * Try to link a recent InterpretationEvent to the given match so later corrections can be
   * applied. This is useful when the interpretation ran before the match record was created
   * (voice-first flows).
   */
  public void linkInterpretationEventToMatch(
      Long matchId, Long ladderConfigId, String transcript, Long currentUserId) {
    if (matchId == null || (ladderConfigId == null && (transcript == null || transcript.isBlank())))
      return;
    try {
      // Prefer exact ladder+transcript matches; fallback to transcript-only
      java.util.List<InterpretationEvent> cand = null;
      if (ladderConfigId != null && transcript != null) {
        cand =
            interpretationEventRepository.findByLadderConfigIdAndTranscriptOrderByCreatedAtDesc(
                ladderConfigId, transcript);
      }
      if ((cand == null || cand.isEmpty()) && transcript != null) {
        // last-resort: search by transcript only
        cand =
            interpretationEventRepository.findByLadderConfigIdAndTranscriptOrderByCreatedAtDesc(
                null, transcript);
      }
      if (cand == null || cand.isEmpty()) return;

      // Pick the most recent one that either matches the current user or is recent
      InterpretationEvent chosen = null;
      long now = Instant.now().toEpochMilli();
      for (InterpretationEvent ev : cand) {
        if (ev == null) continue;
        if (ev.getMatchId() != null) continue; // already linked
        if (currentUserId != null
            && ev.getCurrentUserId() != null
            && ev.getCurrentUserId().equals(currentUserId)) {
          chosen = ev;
          break;
        }
        // prefer events within last 10 minutes
        if (chosen == null
            && ev.getCreatedAt() != null
            && (now - ev.getCreatedAt().toEpochMilli()) <= (10 * 60 * 1000)) {
          chosen = ev;
        }
      }
      if (chosen != null) {
        chosen.setMatchId(matchId);
        interpretationEventRepository.save(chosen);
        if (log.isInfoEnabled())
          log.info("Linked InterpretationEvent {} to match {}", chosen.getId(), matchId);
      }
    } catch (Exception e) {
      log.warn("Failed to link InterpretationEvent to match {}: {}", matchId, e.getMessage());
    }
  }

  public void applyCorrectionsFromInterpretation(
      SpokenMatchInterpretation interpretation,
      Long matchId,
      Long ladderConfigId,
      boolean requireUserCorrected) {
    if (interpretation == null) return;
    if (nameCorrectionRepository == null || interpretationEventRepository == null) {
      if (log.isWarnEnabled()) {
        log.warn(
            "Learning pipeline disabled: interpretationEventRepository={} nameCorrectionRepository={}",
            interpretationEventRepository == null ? "MISSING" : "present",
            nameCorrectionRepository == null ? "MISSING" : "present");
      }
      return;
    }

    try {
      Match saved = matchRepository.findById(matchId).orElse(null);
      if (saved == null) return;

      boolean performCorrections = true;
      if (requireUserCorrected) {
        performCorrections = saved.isUserCorrected();
      }
      if (!performCorrections) {
        if (log.isDebugEnabled())
          log.debug(
              "Skipping corrections for match {}: userCorrected=false (requireUserCorrected={})",
              matchId,
              requireUserCorrected);
        return;
      }

      if (log.isInfoEnabled()) {
        log.info(
            "Applying corrections for match {} (requireUserCorrected={}) using savedMatch: a1={} a2={} b1={} b2={} scoreA={} scoreB={}",
            matchId,
            requireUserCorrected,
            saved.getA1() != null ? saved.getA1().getId() : null,
            saved.getA2() != null ? saved.getA2().getId() : null,
            saved.getB1() != null ? saved.getB1().getId() : null,
            saved.getB2() != null ? saved.getB2().getId() : null,
            saved.getScoreA(),
            saved.getScoreB());
      }

      java.util.List<SpokenMatchInterpretation.Team> teams = interpretation.getTeams();
      for (int ti = 0; ti < Math.min(2, teams.size()); ti++) {
        SpokenMatchInterpretation.Team team = teams.get(ti);
        for (int pi = 0; pi < Math.min(2, team.getPlayers().size()); pi++) {
          SpokenMatchInterpretation.PlayerResolution pr = team.getPlayers().get(pi);
          String token = pr.getToken();
          if (token == null || token.isBlank()) continue;
          String norm =
              token
                  .toLowerCase(java.util.Locale.ROOT)
                  .replaceAll("[^a-z0-9\\s]", "")
                  .replaceAll("\\s+", " ")
                  .trim();
          Long finalUserId = null;
          if (ti == 0) {
            finalUserId =
                (pi == 0)
                    ? (saved.getA1() != null ? saved.getA1().getId() : null)
                    : (saved.getA2() != null ? saved.getA2().getId() : null);
          } else {
            finalUserId =
                (pi == 0)
                    ? (saved.getB1() != null ? saved.getB1().getId() : null)
                    : (saved.getB2() != null ? saved.getB2().getId() : null);
          }
          Long interpreted = pr.getMatchedUserId();
          if (!java.util.Objects.equals(interpreted, finalUserId)) {
            Long userIdForCorrection = finalUserId == null ? -1L : finalUserId;
            Long reporterId = null;
            try {
              if (saved.getLoggedBy() != null) reporterId = saved.getLoggedBy().getId();
            } catch (Exception e) {
            }
            String phoneticKey = null;
            try {
              com.w3llspring.fhpb.web.service.matchlog.DoubleMetaphone dm =
                  new com.w3llspring.fhpb.web.service.matchlog.DoubleMetaphone();
              String[] keys = dm.doubleMetaphone(norm);
              if (keys != null && keys.length > 0) phoneticKey = keys[0];
            } catch (Exception ex) {
            }
            try {
              if (log.isInfoEnabled()) {
                log.info(
                    "Incrementing name correction: ladder={} token='{}' phonetic='{}' interpreted={} corrected={} reporter={}",
                    ladderConfigId,
                    norm,
                    phoneticKey,
                    interpreted,
                    userIdForCorrection == -1L ? null : userIdForCorrection,
                    reporterId);
              }
              nameCorrectionRepository.incrementCorrectionCount(
                  ladderConfigId,
                  norm,
                  phoneticKey,
                  userIdForCorrection,
                  reporterId,
                  1,
                  Instant.now());
            } catch (Exception ex) {
              try {
                java.util.List<com.w3llspring.fhpb.web.model.NameCorrection> found =
                    nameCorrectionRepository.findByTokenNormalized(norm);
                com.w3llspring.fhpb.web.model.NameCorrection target = null;
                for (com.w3llspring.fhpb.web.model.NameCorrection nc : found) {
                  if (java.util.Objects.equals(nc.getUserId(), finalUserId)) {
                    target = nc;
                    break;
                  }
                }
                if (target == null) {
                  target = new com.w3llspring.fhpb.web.model.NameCorrection();
                  target.setTokenNormalized(norm);
                  target.setLadderConfigId(ladderConfigId);
                  target.setUserId(userIdForCorrection);
                  target.setReporterUserId(reporterId);
                  target.setPhoneticKey(phoneticKey);
                  target.setCount(0);
                }
                target.setCount(target.getCount() + 1);
                target.setLastConfirmedAt(Instant.now());
                nameCorrectionRepository.save(target);
                if (log.isInfoEnabled()) {
                  log.info(
                      "Persisted fallback name correction: ladder={} token='{}' user={} newCount={} reporter={}",
                      ladderConfigId,
                      norm,
                      target.getUserId(),
                      target.getCount(),
                      reporterId);
                }
              } catch (Exception ex2) {
                log.warn("Fallback persist of name correction failed: {}", ex2.getMessage());
              }
            }
          }
        }
      }

      // Score corrections
      try {
        Integer interpretedA = interpretation.getScoreTeamA();
        Integer interpretedB = interpretation.getScoreTeamB();
        Integer finalA = saved.getScoreA();
        Integer finalB = saved.getScoreB();
        if (scoreCorrectionRepository != null) {
          if (interpretedA != null && !java.util.Objects.equals(interpretedA, finalA)) {
            // If the interpreter flagged that the numeric order was winner-first
            // and the interpreted scores are simply the reverse of the saved
            // scores, treat this as a grammar/order issue and do not create
            // a numeric score correction (which would wrongly suggest the
            // numeric decoding is incorrect).
            if (interpretation.isScoreOrderReversed()
                && interpretedA != null
                && interpretedB != null
                && java.util.Objects.equals(interpretedA, finalB)
                && java.util.Objects.equals(interpretedB, finalA)) {
              if (log.isInfoEnabled()) {
                log.info(
                    "Detected score-order reversal for match {}: interpreted {}-{} vs saved {}-{}; skipping numeric score correction.",
                    matchId,
                    interpretedA,
                    interpretedB,
                    finalA,
                    finalB);
              }
            } else {
              com.w3llspring.fhpb.web.model.ScoreCorrection sc =
                  scoreCorrectionRepository
                      .findTopByLadderConfigIdAndScoreFieldAndInterpretedValueAndCorrectedValueOrderByCountDesc(
                          ladderConfigId, "A", interpretedA, finalA)
                      .orElseGet(
                          () -> {
                            com.w3llspring.fhpb.web.model.ScoreCorrection s =
                                new com.w3llspring.fhpb.web.model.ScoreCorrection();
                            s.setLadderConfigId(ladderConfigId);
                            s.setScoreField("A");
                            s.setInterpretedValue(interpretedA);
                            s.setCorrectedValue(finalA);
                            s.setCount(0);
                            return s;
                          });
              sc.setCount(sc.getCount() + 1);
              sc.setLastConfirmedAt(Instant.now());
              scoreCorrectionRepository.save(sc);
              if (log.isInfoEnabled()) {
                log.info(
                    "Persisted score correction: ladder={} field=A interpreted={} corrected={} newCount={}",
                    ladderConfigId,
                    interpretedA,
                    finalA,
                    sc.getCount());
              }
            }
          }
          if (interpretedB != null && !java.util.Objects.equals(interpretedB, finalB)) {
            // Same reversal guard for team B
            if (interpretation.isScoreOrderReversed()
                && interpretedA != null
                && interpretedB != null
                && java.util.Objects.equals(interpretedA, finalB)
                && java.util.Objects.equals(interpretedB, finalA)) {
              // already handled above
            } else {
              com.w3llspring.fhpb.web.model.ScoreCorrection sc =
                  scoreCorrectionRepository
                      .findTopByLadderConfigIdAndScoreFieldAndInterpretedValueAndCorrectedValueOrderByCountDesc(
                          ladderConfigId, "B", interpretedB, finalB)
                      .orElseGet(
                          () -> {
                            com.w3llspring.fhpb.web.model.ScoreCorrection s =
                                new com.w3llspring.fhpb.web.model.ScoreCorrection();
                            s.setLadderConfigId(ladderConfigId);
                            s.setScoreField("B");
                            s.setInterpretedValue(interpretedB);
                            s.setCorrectedValue(finalB);
                            s.setCount(0);
                            return s;
                          });
              sc.setCount(sc.getCount() + 1);
              sc.setLastConfirmedAt(Instant.now());
              scoreCorrectionRepository.save(sc);
              if (log.isInfoEnabled()) {
                log.info(
                    "Persisted score correction: ladder={} field=B interpreted={} corrected={} newCount={}",
                    ladderConfigId,
                    interpretedB,
                    finalB,
                    sc.getCount());
              }
            }
          }
        }
      } catch (Exception e) {
        log.warn("Failed to persist score correction: {}", e.getMessage());
      }

      // Do not modify the match.userCorrected flag here. We intentionally
      // preserve that flag as a historical record of manual user edits.
      if (log.isInfoEnabled())
        log.info("Applied corrections for match {} (no change to userCorrected flag)", matchId);

    } catch (Exception e) {
      log.warn("Failed to apply learning corrections for match {}: {}", matchId, e.getMessage());
    }
  }
}
