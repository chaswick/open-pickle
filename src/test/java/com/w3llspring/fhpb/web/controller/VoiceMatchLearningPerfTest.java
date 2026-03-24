package com.w3llspring.fhpb.web.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.w3llspring.fhpb.web.controller.match.VoiceMatchLogController;
import com.w3llspring.fhpb.web.model.Match;
import com.w3llspring.fhpb.web.model.NameCorrection;
import com.w3llspring.fhpb.web.model.ScoreCorrection;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.MatchFactory;
import com.w3llspring.fhpb.web.service.matchlog.DefaultSpokenMatchInterpreter;
import com.w3llspring.fhpb.web.service.matchlog.LearningSpokenMatchInterpreter;
import com.w3llspring.fhpb.web.service.matchlog.SpokenMatchInterpretation;
import com.w3llspring.fhpb.web.service.matchlog.SpokenMatchInterpretationRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Performance-style test: measure time to learn from confirmed matches (persist corrections) and
 * then predict (apply corrections) for imperfect transcripts.
 */
@Disabled(
    "Legacy performance experiment for the old direct-correction controller path; not part of supported CI behavior")
public class VoiceMatchLearningPerfTest {

  private VoiceMatchLogController controller;
  private LearningSpokenMatchInterpreter learner;
  private com.w3llspring.fhpb.web.db.ScoreCorrectionRepository scoreRepo;

  // in-memory backing store for name corrections (used by mocked repo Answers)
  private final Map<String, List<NameCorrection>> corrections = new HashMap<>();
  private long nextId = 1000L;

  @BeforeEach
  void setup() throws Exception {
    // Mock delegate interpreter (will return interpretations we control)
    DefaultSpokenMatchInterpreter delegate = mock(DefaultSpokenMatchInterpreter.class);

    // Create in-memory-mocking NameCorrectionRepository using Mockito answers
    com.w3llspring.fhpb.web.db.NameCorrectionRepository nameRepo =
        mock(com.w3llspring.fhpb.web.db.NameCorrectionRepository.class);
    com.w3llspring.fhpb.web.db.InterpretationEventRepository ieRepo =
        mock(com.w3llspring.fhpb.web.db.InterpretationEventRepository.class);
    com.w3llspring.fhpb.web.db.ScoreCorrectionRepository scRepo =
        mock(com.w3llspring.fhpb.web.db.ScoreCorrectionRepository.class);
    this.scoreRepo = scRepo;

    // findTopByTokenNormalizedAndLadderConfigIdOrderByCountDesc
    when(nameRepo.findTopByTokenNormalizedAndLadderConfigIdOrderByCountDesc(anyString(), any()))
        .thenAnswer(
            inv -> {
              String token = inv.getArgument(0);
              Long ladderId = inv.getArgument(1);
              List<NameCorrection> list = corrections.getOrDefault(token, Collections.emptyList());
              // prefer same ladderId if present
              Optional<NameCorrection> byLadder =
                  list.stream()
                      .filter(nc -> Objects.equals(nc.getLadderConfigId(), ladderId))
                      .max(Comparator.comparingInt(NameCorrection::getCount));
              if (byLadder.isPresent()) return Optional.of(byLadder.get());
              return list.stream()
                  .max(Comparator.comparingInt(NameCorrection::getCount))
                  .map(Optional::of)
                  .orElse(Optional.empty());
            });

    when(nameRepo.findTopByTokenNormalizedOrderByCountDesc(anyString()))
        .thenAnswer(
            inv -> {
              String token = inv.getArgument(0);
              List<NameCorrection> list = corrections.getOrDefault(token, Collections.emptyList());
              return list.stream()
                  .max(Comparator.comparingInt(NameCorrection::getCount))
                  .map(Optional::of)
                  .orElse(Optional.empty());
            });

    when(nameRepo.findByTokenNormalized(anyString()))
        .thenAnswer(
            inv -> {
              String token = inv.getArgument(0);
              return new ArrayList<>(corrections.getOrDefault(token, Collections.emptyList()));
            });

    // New repository method used by the LearningSpokenMatchInterpreter: return same as
    // findByTokenNormalized
    when(nameRepo.findByTokenNormalizedOrPhoneticKey(anyString(), anyString()))
        .thenAnswer(
            inv -> {
              String token = inv.getArgument(0);
              return new ArrayList<>(corrections.getOrDefault(token, Collections.emptyList()));
            });

    when(nameRepo.save(any(NameCorrection.class)))
        .thenAnswer(
            inv -> {
              NameCorrection nc = inv.getArgument(0);
              if (nc.getId() == null) {
                // set id via reflection (field is private)
                try {
                  Field idF = NameCorrection.class.getDeclaredField("id");
                  idF.setAccessible(true);
                  idF.set(nc, nextId++);
                } catch (Exception e) {
                  // ignore
                }
              }
              String key = nc.getTokenNormalized();
              corrections.computeIfAbsent(key, k -> new ArrayList<>());
              // replace existing matching (same userId+ladder) or add
              List<NameCorrection> list = corrections.get(key);
              Optional<NameCorrection> existing =
                  list.stream()
                      .filter(
                          x ->
                              Objects.equals(x.getUserId(), nc.getUserId())
                                  && Objects.equals(x.getLadderConfigId(), nc.getLadderConfigId()))
                      .findFirst();
              if (existing.isPresent()) {
                NameCorrection ex = existing.get();
                ex.setCount(nc.getCount());
                ex.setLastConfirmedAt(nc.getLastConfirmedAt());
                return ex;
              } else {
                list.add(nc);
                return nc;
              }
            });

    // mock the new atomic increment method used by the controller
    when(nameRepo.incrementCorrectionCount(
            any(), anyString(), anyString(), any(), any(), anyInt(), any()))
        .thenAnswer(
            inv -> {
              Long ladderId = inv.getArgument(0);
              String token = inv.getArgument(1);
              String phonetic = inv.getArgument(2);
              Long userId = inv.getArgument(3);
              Long reporter = inv.getArgument(4);
              Integer delta = inv.getArgument(5);
              java.time.Instant lastAt = inv.getArgument(6);
              String key = token;
              List<NameCorrection> list = corrections.computeIfAbsent(key, k -> new ArrayList<>());
              // match existing by userId+ladder
              Optional<NameCorrection> existing =
                  list.stream()
                      .filter(
                          x ->
                              Objects.equals(x.getUserId(), userId)
                                  && Objects.equals(x.getLadderConfigId(), ladderId))
                      .findFirst();
              if (existing.isPresent()) {
                NameCorrection nc = existing.get();
                nc.setCount(
                    (nc.getCount() == null ? 0 : nc.getCount()) + (delta == null ? 1 : delta));
                nc.setLastConfirmedAt(lastAt);
                if (nc.getPhoneticKey() == null && phonetic != null) nc.setPhoneticKey(phonetic);
                if (reporter != null) nc.setReporterUserId(reporter);
                return nc;
              } else {
                NameCorrection nc = new NameCorrection();
                try {
                  java.lang.reflect.Field idF = NameCorrection.class.getDeclaredField("id");
                  idF.setAccessible(true);
                  idF.set(nc, nextId++);
                } catch (Exception e) {
                }
                nc.setTokenNormalized(token);
                nc.setLadderConfigId(ladderId);
                nc.setUserId(userId);
                nc.setReporterUserId(reporter);
                nc.setPhoneticKey(phonetic);
                nc.setCount(delta == null ? 1 : delta);
                nc.setLastConfirmedAt(lastAt);
                list.add(nc);
                return nc;
              }
            });

    // interpretationEvent save noop
    when(ieRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // score corrections - keep simple in-memory store so we can later inspect if needed
    final Map<String, List<ScoreCorrection>> scoreMap = new HashMap<>();
    final long[] nextScoreId = {2000L};
    when(scRepo
            .findTopByLadderConfigIdAndScoreFieldAndInterpretedValueAndCorrectedValueOrderByCountDesc(
                any(), anyString(), any(), any()))
        .thenAnswer(inv -> Optional.empty());
    when(scRepo.save(any()))
        .thenAnswer(
            inv -> {
              ScoreCorrection sc = inv.getArgument(0);
              try {
                Field idF = ScoreCorrection.class.getDeclaredField("id");
                idF.setAccessible(true);
                if (sc.getId() == null) idF.set(sc, nextScoreId[0]++);
              } catch (Exception e) {
              }
              String key =
                  sc.getLadderConfigId()
                      + ":"
                      + sc.getScoreField()
                      + ":"
                      + sc.getInterpretedValue()
                      + ":"
                      + sc.getCorrectedValue();
              scoreMap.computeIfAbsent(key, k -> new ArrayList<>()).add(sc);
              return sc;
            });

    ObjectMapper om = new ObjectMapper();
    learner = new LearningSpokenMatchInterpreter(delegate, nameRepo, ieRepo, om);

    // Create a controller instance and inject the same mocked repositories so the helper
    // persists into our in-memory maps
    com.w3llspring.fhpb.web.service.LadderV2Service lv2 =
        mock(com.w3llspring.fhpb.web.service.LadderV2Service.class);
    com.w3llspring.fhpb.web.service.trophy.TrophyAwardService tas =
        mock(com.w3llspring.fhpb.web.service.trophy.TrophyAwardService.class);
    com.w3llspring.fhpb.web.service.matchlog.MatchValidationService mvs =
        mock(com.w3llspring.fhpb.web.service.matchlog.MatchValidationService.class);
    com.w3llspring.fhpb.web.service.LadderSecurityService lss =
        mock(com.w3llspring.fhpb.web.service.LadderSecurityService.class);
    com.w3llspring.fhpb.web.service.LadderAccessService las =
        mock(com.w3llspring.fhpb.web.service.LadderAccessService.class);
    MatchFactory matchFactory = mock(MatchFactory.class);
    com.w3llspring.fhpb.web.db.MatchRepository matchRepo =
        mock(com.w3llspring.fhpb.web.db.MatchRepository.class);
    com.w3llspring.fhpb.web.db.MatchConfirmationRepository confirmationRepo =
        mock(com.w3llspring.fhpb.web.db.MatchConfirmationRepository.class);
    com.w3llspring.fhpb.web.db.UserRepository userRepo =
        mock(com.w3llspring.fhpb.web.db.UserRepository.class);
    com.w3llspring.fhpb.web.db.LadderSeasonRepository seasonRepo =
        mock(com.w3llspring.fhpb.web.db.LadderSeasonRepository.class);

    controller =
        new VoiceMatchLogController(
            learner,
            mock(DefaultSpokenMatchInterpreter.class),
            userRepo,
            seasonRepo,
            lv2,
            tas,
            mvs,
            lss,
            las,
            matchFactory,
            matchRepo,
            confirmationRepo);

    // inject repositories into controller (nameRepo, ieRepo)
    Field ncField = VoiceMatchLogController.class.getDeclaredField("nameCorrectionRepository");
    ncField.setAccessible(true);
    ncField.set(controller, nameRepo);
    Field ieField = VoiceMatchLogController.class.getDeclaredField("interpretationEventRepository");
    ieField.setAccessible(true);
    ieField.set(controller, ieRepo);
    Field scField = VoiceMatchLogController.class.getDeclaredField("scoreCorrectionRepository");
    scField.setAccessible(true);
    scField.set(controller, scRepo);
  }

  @Test
  void measure_training_and_prediction_latency() throws Exception {
    // Sample imperfect transcripts and expected normalized tokens mapping to user ids
    class Case {
      String transcript;
      String token;
      Long userId;
    }
    List<Case> cases = new ArrayList<>();
    Case c1 = new Case();
    c1.transcript = "Jon Dough beat Jane Smith";
    c1.token = "jon dough";
    c1.userId = 101L;
    cases.add(c1);
    Case c2 = new Case();
    c2.transcript = "Two Pea defeated Bob";
    c2.token = "two pea";
    c2.userId = 102L;
    cases.add(c2);
    Case c3 = new Case();
    c3.transcript = "Jahn Dough vs Bobby";
    c3.token = "jahn dough";
    c3.userId = 101L;
    cases.add(c3);

    // For each case: create an interpretation with a single player token and no matchedUserId
    for (Case cs : cases) {
      SpokenMatchInterpretation interp = new SpokenMatchInterpretation();
      interp.setTranscript(cs.transcript);
      SpokenMatchInterpretation.Team t = interp.addTeam(0, false);
      SpokenMatchInterpretation.PlayerResolution pr = t.addPlayer();
      pr.setToken(cs.token);
      pr.setMatchedUserId(null);

      // Saved match represents final confirmed user for slot A1
      Match saved = new Match();
      // set id and A1 via reflection
      try {
        Field idF = Match.class.getDeclaredField("id");
        idF.setAccessible(true);
        idF.set(saved, 500L + cs.userId);
      } catch (Exception e) {
      }
      User u = new User();
      try {
        Field idF = User.class.getDeclaredField("id");
        idF.setAccessible(true);
        idF.set(u, cs.userId);
      } catch (Exception e) {
      }
      saved.setA1(u);

      // mock matchRepository.findById used inside controller helper
      com.w3llspring.fhpb.web.db.MatchRepository matchRepo =
          mock(com.w3llspring.fhpb.web.db.MatchRepository.class);
      when(matchRepo.findById(anyLong())).thenReturn(Optional.of(saved));
      Field mrField = VoiceMatchLogController.class.getDeclaredField("matchRepository");
      mrField.setAccessible(true);
      mrField.set(controller, matchRepo);

      // measure training (recordInterpretationEventAndCorrections)
      Method m =
          VoiceMatchLogController.class.getDeclaredMethod(
              "recordInterpretationEventAndCorrections",
              SpokenMatchInterpretation.class,
              Long.class,
              Long.class,
              Long.class);
      m.setAccessible(true);
      long startTrain = System.nanoTime();
      // perform two confirmations to ensure promotion thresholds are met in conservative configs
      m.invoke(
          controller,
          interp,
          Long.valueOf(1000L + cs.userId),
          Long.valueOf(1L),
          Long.valueOf(cs.userId));
      m.invoke(
          controller,
          interp,
          Long.valueOf(1000L + cs.userId),
          Long.valueOf(1L),
          Long.valueOf(cs.userId));
      long trainNs = System.nanoTime() - startTrain;

      // Now predict: mock delegate to return the same interp (no matchedUserId)
      DefaultSpokenMatchInterpreter delegate = mock(DefaultSpokenMatchInterpreter.class);
      when(delegate.interpret(any(SpokenMatchInterpretationRequest.class))).thenReturn(interp);
      // create a new LearningSpokenMatchInterpreter that uses the same nameRepo mapping
      java.lang.reflect.Field nameField =
          controller.getClass().getDeclaredField("nameCorrectionRepository");
      nameField.setAccessible(true);
      java.lang.reflect.Field ieField =
          controller.getClass().getDeclaredField("interpretationEventRepository");
      ieField.setAccessible(true);
      LearningSpokenMatchInterpreter testLearner =
          new LearningSpokenMatchInterpreter(
              delegate,
              (com.w3llspring.fhpb.web.db.NameCorrectionRepository) nameField.get(controller),
              (com.w3llspring.fhpb.web.db.InterpretationEventRepository) ieField.get(controller),
              new ObjectMapper());

      long startPredict = System.nanoTime();
      SpokenMatchInterpretation out = testLearner.interpret(new SpokenMatchInterpretationRequest());
      long predictNs = System.nanoTime() - startPredict;

      // After prediction, the first player should have been corrected to the saved user id
      Long matched = out.getTeams().get(0).getPlayers().get(0).getMatchedUserId();
      System.out.println(
          "Case token='"
              + cs.token
              + "' train(ms)="
              + (trainNs / 1_000_000.0)
              + " predict(ms)="
              + (predictNs / 1_000_000.0)
              + " matched="
              + matched);
      assertNotNull(matched, "Learner should have applied a correction");
      assertEquals(
          cs.userId, matched, "Learner matched user id should equal final confirmed user id");
    }
  }

  @Test
  void measure_bulk_training_and_accuracy() throws Exception {
    // Define ground-truth users and common STT variants
    Map<String, Long> truth = new HashMap<>();
    truth.put("eddy", 201L);
    truth.put("john", 202L);
    truth.put("alex", 203L);

    Map<String, List<String>> variants = new HashMap<>();
    variants.put("eddy", Arrays.asList("eddy", "daddy", "eddi", "eddy man", "addy"));
    variants.put("john", Arrays.asList("john", "jon", "jahn", "jawn", "johnny"));
    variants.put("alex", Arrays.asList("alex", "alexa", "alix", "alekz", "a lex"));

    // train: for each truth token, simulate several confirmed matches with variant transcripts
    Method recordM =
        VoiceMatchLogController.class.getDeclaredMethod(
            "recordInterpretationEventAndCorrections",
            SpokenMatchInterpretation.class,
            Long.class,
            Long.class,
            Long.class);
    recordM.setAccessible(true);

    int confirmationsPerVariant = 3;
    long trainStart = System.nanoTime();
    for (Map.Entry<String, Long> e : truth.entrySet()) {
      String canonical = e.getKey();
      Long userId = e.getValue();
      for (String var : variants.get(canonical)) {
        for (int i = 0; i < confirmationsPerVariant; i++) {
          SpokenMatchInterpretation interp = new SpokenMatchInterpretation();
          interp.setTranscript("Match reported: " + var);
          SpokenMatchInterpretation.Team t = interp.addTeam(0, false);
          SpokenMatchInterpretation.PlayerResolution pr = t.addPlayer();
          pr.setToken(var);
          pr.setMatchedUserId(null);

          Match saved = new Match();
          try {
            Field idF = Match.class.getDeclaredField("id");
            idF.setAccessible(true);
            idF.set(saved, 900L + userId);
          } catch (Exception ex) {
          }
          User u = new User();
          try {
            Field idF = User.class.getDeclaredField("id");
            idF.setAccessible(true);
            idF.set(u, userId);
          } catch (Exception ex) {
          }
          saved.setA1(u);

          // inject match repo that returns saved
          com.w3llspring.fhpb.web.db.MatchRepository matchRepo =
              mock(com.w3llspring.fhpb.web.db.MatchRepository.class);
          when(matchRepo.findById(anyLong())).thenReturn(Optional.of(saved));
          Field mrField = VoiceMatchLogController.class.getDeclaredField("matchRepository");
          mrField.setAccessible(true);
          mrField.set(controller, matchRepo);

          recordM.invoke(
              controller,
              interp,
              Long.valueOf(2000L + userId),
              Long.valueOf(1L),
              Long.valueOf(userId));
        }
      }
    }
    long trainNs = System.nanoTime() - trainStart;

    // Also train on some score-mangling examples where the interpreted score differs
    // from the final saved score so the scoreCorrectionRepository should be invoked.
    class ScoreCase {
      int interpretedA;
      int interpretedB;
      int finalA;
      int finalB;
    }
    List<ScoreCase> scoreCases = new ArrayList<>();
    ScoreCase s1 = new ScoreCase();
    s1.interpretedA = 1122;
    s1.interpretedB = 0;
    s1.finalA = 11;
    s1.finalB = 2;
    scoreCases.add(s1);
    ScoreCase s2 = new ScoreCase();
    s2.interpretedA = 11;
    s2.interpretedB = 22;
    s2.finalA = 11;
    s2.finalB = 2;
    scoreCases.add(s2);
    ScoreCase s3 = new ScoreCase();
    s3.interpretedA = 11;
    s3.interpretedB = 2;
    s3.finalA = 11;
    s3.finalB = 2;
    scoreCases.add(s3);

    int expectedScoreMismatches = 0;
    for (ScoreCase sc : scoreCases) {
      SpokenMatchInterpretation interp = new SpokenMatchInterpretation();
      interp.setTranscript("Score reported");
      SpokenMatchInterpretation.Team t = interp.addTeam(0, false);
      SpokenMatchInterpretation.PlayerResolution pr = t.addPlayer();
      pr.setToken("scoretest");
      interp.setScoreTeamA(sc.interpretedA);
      interp.setScoreTeamB(sc.interpretedB);

      Match saved = new Match();
      try {
        Field idF = Match.class.getDeclaredField("id");
        idF.setAccessible(true);
        idF.set(saved, 8000L + sc.finalA);
      } catch (Exception ex) {
      }
      saved.setScoreA(sc.finalA);
      saved.setScoreB(sc.finalB);

      com.w3llspring.fhpb.web.db.MatchRepository matchRepo =
          mock(com.w3llspring.fhpb.web.db.MatchRepository.class);
      when(matchRepo.findById(anyLong())).thenReturn(Optional.of(saved));
      Field mrField = VoiceMatchLogController.class.getDeclaredField("matchRepository");
      mrField.setAccessible(true);
      mrField.set(controller, matchRepo);

      recordM.invoke(
          controller,
          interp,
          Long.valueOf(7000L + sc.finalA),
          Long.valueOf(1L),
          Long.valueOf(201L));
      if (sc.interpretedA != sc.finalA) expectedScoreMismatches++;
      if (sc.interpretedB != sc.finalB) expectedScoreMismatches++;
    }

    // Verify score correction repository was invoked expected number of times
    // (each mismatch should result in one save)
    // Verify the score repo save count equals the number of mismatches we introduced
    verify(scoreRepo, org.mockito.Mockito.times(expectedScoreMismatches))
        .save(any(com.w3llspring.fhpb.web.model.ScoreCorrection.class));

    // prediction: reuse the controller-side name repository and instantiate a new
    // LearningSpokenMatchInterpreter that delegates to a stub returning the token.
    Field nameField = controller.getClass().getDeclaredField("nameCorrectionRepository");
    nameField.setAccessible(true);
    com.w3llspring.fhpb.web.db.NameCorrectionRepository nameRepo =
        (com.w3llspring.fhpb.web.db.NameCorrectionRepository) nameField.get(controller);
    Field ieField = controller.getClass().getDeclaredField("interpretationEventRepository");
    ieField.setAccessible(true);

    int total = 0, correct = 0;
    long predictStart = System.nanoTime();
    for (Map.Entry<String, Long> e : truth.entrySet()) {
      String canonical = e.getKey();
      Long userId = e.getValue();
      for (String var : variants.get(canonical)) {
        // build interp
        SpokenMatchInterpretation req = new SpokenMatchInterpretation();
        SpokenMatchInterpretation.Team t = req.addTeam(0, false);
        SpokenMatchInterpretation.PlayerResolution pr = t.addPlayer();
        pr.setToken(var);
        pr.setMatchedUserId(null);

        // mock delegate to return this req
        DefaultSpokenMatchInterpreter del = mock(DefaultSpokenMatchInterpreter.class);
        when(del.interpret(any(SpokenMatchInterpretationRequest.class))).thenReturn(req);
        LearningSpokenMatchInterpreter ll =
            new LearningSpokenMatchInterpreter(
                del,
                nameRepo,
                (com.w3llspring.fhpb.web.db.InterpretationEventRepository) ieField.get(controller),
                new ObjectMapper());

        SpokenMatchInterpretation out = ll.interpret(new SpokenMatchInterpretationRequest());
        Long matched = out.getTeams().get(0).getPlayers().get(0).getMatchedUserId();
        total++;
        if (matched != null && matched.equals(userId)) correct++;
      }
    }
    long predictNs = System.nanoTime() - predictStart;

    double accuracy = (total == 0) ? 0.0 : (100.0 * correct / total);
    System.out.println(
        "Bulk training: confirmationsPerVariant="
            + confirmationsPerVariant
            + " train(ms)="
            + (trainNs / 1_000_000.0)
            + " predict(ms)="
            + (predictNs / 1_000_000.0)
            + " total="
            + total
            + " correct="
            + correct
            + " accuracy="
            + accuracy
            + "%");

    assertTrue(
        accuracy >= 60.0,
        "Expected at least 60% accuracy on synthetic variants (got "
            + accuracy
            + "%). Adjust threshold as needed.");
  }
}
