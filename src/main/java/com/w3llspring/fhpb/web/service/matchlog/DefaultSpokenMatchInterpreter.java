package com.w3llspring.fhpb.web.service.matchlog;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.LadderMembership;
import com.w3llspring.fhpb.web.model.LadderSeason;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.service.PhoneticMatchingService;
import com.w3llspring.fhpb.web.service.UserCorrectionLearner;
import com.w3llspring.fhpb.web.service.matchlog.SpokenMatchLearningSample.PlayerIssue;
import com.w3llspring.fhpb.web.service.user.CourtNameService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Primary
@Service
public class DefaultSpokenMatchInterpreter implements SpokenMatchInterpreter {

  private static final String WORD_NUMBER_FRAGMENT =
      "[a-z]+(?:-[a-z]+)?(?:\\s+[a-z]+(?:-[a-z]+)?){0,2}";
  private static final String DIGIT_TOKEN = "\\d{1,2}(?:st|nd|rd|th)?";
  // Match common numeric score patterns. Allow an optional leading "by a score of"
  // before the first number (e.g. "... by a score of 11 to 3") so that the
  // parser can remove that prefix from the remainder and avoid treating it as
  // part of a player name.
  private static final Pattern NUMERIC_SCORE_PATTERN =
      Pattern.compile(
          "(?i)(?:\\bby\\s+(?:a\\s+)?score\\s+of\\s*)?\\b("
              + DIGIT_TOKEN
              + ")\\b\\s*(?:(?:-|to|over|:|\\s)|(?:by\\s+(?:a\\s+)?score\\s+of)|(?:finished|ended|final(?:ly)?\\s+(?:at|with)?))\\s*\\b("
              + DIGIT_TOKEN
              + ")\\b",
          Pattern.CASE_INSENSITIVE);
  // ALWAYS_LOG_PHASE2: Fallback pattern for single score at end of transcript
  // (e.g., "me and dave beat eddit and irena 11")
  private static final Pattern SINGLE_SCORE_PATTERN =
      Pattern.compile("(?i)\\b(" + DIGIT_TOKEN + ")\\s*$", Pattern.CASE_INSENSITIVE);
  // ALWAYS_LOG_PHASE2: Pattern for margin/difference expressions
  // (e.g., "we won by 5", "I lost by 3", "beat them by 4")
  private static final Pattern MARGIN_PATTERN =
      Pattern.compile(
          "(?i)\\b(?:won|beat|lost|defeated)\\s+(?:by|them\\s+by)?\\s*(" + DIGIT_TOKEN + ")\\b",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern DIGIT_WITH_SUFFIX_PATTERN =
      Pattern.compile("^(\\d{1,2})(?:st|nd|rd|th)$", Pattern.CASE_INSENSITIVE);
  private static final Map<String, Integer> NUMBER_WORD_MAP = buildNumberWordMap();

  private static final Pattern TEAM_SPLIT_PATTERN =
      Pattern.compile("(?i)\\b( vs | versus | against )\\b");
  // Pattern to identify "X and I" or "me and X" constructs
  private static final Pattern SELF_PAIR_PATTERN =
      Pattern.compile("(?i)(\\w+)\\s+and\\s+(?:I|me)\\b|(?:I|me)\\s+and\\s+(\\w+)\\b");
  private static final Pattern TOKEN_SPLIT_PATTERN =
      Pattern.compile(
          "(?i)\\s*(?:,|\\s+(?<!\\b(?:I|me))and(?!\\s+(?:I|me)\\b)\\s+| with | & | plus )\\s*");
  private static final Pattern FILLER_WORD_PATTERN =
      Pattern.compile(
          "(?i)\\b(?:um|uh|uhh|uhm|erm|er|hmm|like|anyway|well|maybe|probably|so|huh|yeah|yea|yep|yes|nah|nope|ok|okay|just|now|then|there|right|alright|all right)\\b");
  private static final Pattern FILLER_PHRASE_PATTERN =
      Pattern.compile(
          "(?i)\\b(?:you\\s+know|i\\s+mean|let\\s+me\\s+think|sort\\s+of|kind\\s+of|who\\s+was\\s+it\\s+again|what\\s+was\\s+it\\s+again|who\\s+was\\s+that\\s+again|i\\s+think\\s+it\\s+was|i\\s+think\\s+it\\s+is|i\\s+think\\s+it\\s+might\\s+be)\\b");

  private static final Set<String> SELF_TOKENS = Set.of("me", "myself", "i", "us", "we");

  private static final List<KeywordRule> KEYWORD_RULES =
      List.of(
          new KeywordRule(
              "\\b(?:beat|beats|beating|defeated|defeats|defeating|upset|upsets|upsetting|took down|takes down|take down|took out|knocked off|knocks off|edged|edging|edged out|best(?:ed)?|toppled|topples|handled|handles|dominated|dominates|outlasted|outlasts|swept|sweeps)\\b",
              0),
          // ALWAYS_LOG_PHASE2: Accept standalone "won" without requiring preposition
          // This allows "me and dave won" to work (opponents can be guests/unknown)
          new KeywordRule("\\b(?:won|wins|winning)\\b", 0),
          new KeywordRule(
              "\\b(?:lost to|lost against|lost versus|lost vs\\.?|fell to|fell against|went down to|went down against|dropped to|dropping to|dropped against|took the loss to|took the loss against)\\b",
              1),
          // ALWAYS_LOG_PHASE2: Accept standalone "lost" without requiring preposition
          // This allows "we lost" or "i lost" to work (opponents can be guests/unknown)
          new KeywordRule("\\b(?:lost|loses|losing)\\b", 1),
          // ALWAYS_LOG_PHASE2: Accept opponent victory keywords (they won = we lost)
          // This allows "they won" or "he won" to indicate speakers lost
          new KeywordRule("\\b(?:they|he|she|them)\\s+(?:won|wins)\\b", 1));

  private static final String[] WIN_SENTENCE_HINTS = {
    " we won", " we took it", " we took the win", " we got the win", " we came out on top",
        " we pulled it out",
    " we pulled out the win", " victory", " won the match", " came back and won", " closed it out",
        " sealed it",
    " final score", " ended", " finished"
  };

  private static final String[] LOSE_SENTENCE_HINTS = {
    " we lost",
    " i lost",
    " they lost",
    " fell to",
    " went down",
    " dropped it",
    " took the loss",
    " came up short",
    " couldn't pull it out",
    " lost the match",
    " got beat",
    " got defeated"
  };

  private static final String[] AGAINST_HINTS = {
    " against ", " versus ", " vs ", " vs. ", " versus the ", " against the "
  };

  private static final String[] TEAM_LIST_HINTS = {
    " and ", " with ", " plus ", " & ", ",", ";", " featuring "
  };

  private static final String[] SELF_CONTEXT_HINTS = {" me ", " myself ", " i ", " we ", " us "};

  private static Map<String, Integer> buildNumberWordMap() {
    Map<String, Integer> map = new HashMap<>();
    registerNumberWord(map, 0, "zero", "nil", "love", "nothing", "zip");
    registerNumberWord(map, 1, "one", "first");
    registerNumberWord(map, 2, "two", "second");
    registerNumberWord(map, 3, "three", "third");
    registerNumberWord(map, 4, "four", "fourth");
    registerNumberWord(map, 5, "five", "fifth");
    registerNumberWord(map, 6, "six", "sixth");
    registerNumberWord(map, 7, "seven", "seventh");
    registerNumberWord(map, 8, "eight", "eighth");
    registerNumberWord(map, 9, "nine", "ninth");
    registerNumberWord(map, 10, "ten", "tenth");
    registerNumberWord(map, 11, "eleven", "eleventh");
    registerNumberWord(map, 12, "twelve", "twelfth");
    registerNumberWord(map, 13, "thirteen", "thirteenth");
    registerNumberWord(map, 14, "fourteen", "fourteenth");
    registerNumberWord(map, 15, "fifteen", "fifteenth");
    registerNumberWord(map, 16, "sixteen", "sixteenth");
    registerNumberWord(map, 17, "seventeen", "seventeenth");
    registerNumberWord(map, 18, "eighteen", "eighteenth");
    registerNumberWord(map, 19, "nineteen", "nineteenth");
    registerNumberWord(map, 20, "twenty", "twentieth");
    registerNumberWord(map, 30, "thirty", "thirtieth");

    registerCompoundNumberWords(map, 20, "twenty");
    registerCompoundNumberWords(map, 30, "thirty");

    return Map.copyOf(map);
  }

  private static void registerNumberWord(Map<String, Integer> map, int value, String... forms) {
    for (String form : forms) {
      if (form == null) {
        continue;
      }
      String key = form.toLowerCase(Locale.US).replace("-", " ").trim();
      key = key.replaceAll("\\s+", " ");
      if (!key.isEmpty()) {
        map.putIfAbsent(key, value);
      }
    }
  }

  private static void registerCompoundNumberWords(
      Map<String, Integer> map, int baseValue, String baseWord) {
    String[] cardinalUnits = {
      "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"
    };
    String[] ordinalUnits = {
      "", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth"
    };
    for (int i = 1; i < cardinalUnits.length; i++) {
      int value = baseValue + i;
      registerNumberWord(map, value, baseWord + " " + cardinalUnits[i]);
      registerNumberWord(map, value, baseWord + " " + ordinalUnits[i]);
    }
  }

  private static final Pattern NUMBER_WORD_PATTERN = buildNumberWordPattern();
  private static final Pattern GLOBAL_ORDINAL_PATTERN =
      Pattern.compile("\\b(\\d{1,2})(?:st|nd|rd|th)\\b", Pattern.CASE_INSENSITIVE);

  private static Pattern buildNumberWordPattern() {
    if (NUMBER_WORD_MAP.isEmpty()) {
      return Pattern.compile("$^", Pattern.CASE_INSENSITIVE);
    }
    String alternation =
        NUMBER_WORD_MAP.keySet().stream()
            .sorted((a, b) -> Integer.compare(b.length(), a.length()))
            .map(DefaultSpokenMatchInterpreter::toNumberWordRegex)
            .collect(Collectors.joining("|"));
    return Pattern.compile("\\b(" + alternation + ")\\b", Pattern.CASE_INSENSITIVE);
  }

  private static String toNumberWordRegex(String key) {
    String[] parts = key.split(" ");
    return Arrays.stream(parts).map(Pattern::quote).collect(Collectors.joining("(?:\\s+|-)"));
  }

  private static String normalizeNumberWordKey(String raw) {
    return raw.toLowerCase(Locale.US).replace('-', ' ').replaceAll("\\s+", " ").trim();
  }

  // Restore thresholds expected by existing unit tests
  private static final double MIN_ASSIGN_CONFIDENCE = 0.40;
  private static final double MIN_CONFIDENCE = 0.55;
  private static final double STRONG_CONFIDENCE = 0.82;
  private static final double AMBIGUITY_DELTA = 0.10;

  private static final Logger log = LoggerFactory.getLogger(DefaultSpokenMatchInterpreter.class);

  private static final List<TeamPattern> TEAM_PATTERNS =
      List.of(
          new TeamPattern(
              "(?i)^(?<winners>.+?)\\s+(?:beat|beats|defeated|defeats|killed|kills|took down|took out|upset|upsets|edged|edges|handled|handles|dominated|dominates|outlasted|outlasts|swept|sweeps|won against|won over)\\s+(?<losers>.+)$",
              "winners",
              "losers",
              0),
          // ALWAYS_LOG_PHASE2: "lost to" pattern - losers (speakers) will be Team A, winners
          // (opponents) Team B
          // Since losers are now added first, winningTeamIndex=1 means Team B (winners) won
          new TeamPattern(
              "(?i)^(?<losers>.+?)\\s+(?:lost to|lost against|fell to|fell against|went down to|went down against|dropped to|dropped against)\\s+(?<winners>.+)$",
              "winners",
              "losers",
              1),
          // ALWAYS_LOG_PHASE2: Opponent victory pattern - must come BEFORE speaker victory pattern
          // Matches: "Tim won", "Guest won", "Dave and Young won", "they won", etc.
          // Excludes speaker-first statements (we/us/i/me) which are handled by speaker victory
          // pattern below
          // This captures opponents as winners, speakers are implicit losers (Team A empty, filled
          // automatically)
          new TeamPattern(
              "(?i)^(?<winners>(?!we\\b|us\\b|i\\b|me\\b).+?)\\s+(?:won|wins)\\s*$",
              "winners",
              null,
              1),
          // ALWAYS_LOG_PHASE2: Speaker victory pattern - comes AFTER opponent pattern
          // Matches when speakers won: "we won", "I won", "me and tim won"
          // Only matches speaker-related pronouns to avoid conflicting with opponent pattern above
          new TeamPattern(
              "(?i)^(?<winners>(?:we|us|i|me).+?)\\s+(?:won|wins)\\s*$", "winners", null, 0),
          // ALWAYS_LOG_PHASE2: Allow standalone "lost" for speakers (opponents will be
          // guests/unknown)
          // Matches when speakers lost: "we lost", "I lost", "me and tim lost"
          new TeamPattern("(?i)^(?<winners>.+?)\\s+(?:lost|loses|losing)\\s*$", "winners", null, 1),
          new TeamPattern(
              "(?i)^(?<winners>.+?)\\s+(?:vs\\.?|versus|against)\\s+(?<losers>.+)$",
              "winners",
              "losers",
              null));

  private final UserRepository userRepository;
  private final LadderSeasonRepository seasonRepository;
  private final LadderMembershipRepository membershipRepository;
  private final CourtNameService courtNameService;
  private final Supplier<SpokenMatchLearningSink> learningSinkSupplier;
  private final UserCorrectionLearner correctionLearner;
  private final PhoneticMatchingService phoneticService;

  public DefaultSpokenMatchInterpreter(
      UserRepository userRepository,
      LadderSeasonRepository seasonRepository,
      LadderMembershipRepository membershipRepository,
      CourtNameService courtNameService,
      ObjectProvider<SpokenMatchLearningSink> learningSinkProvider,
      UserCorrectionLearner correctionLearner,
      PhoneticMatchingService phoneticService) {
    this.userRepository = userRepository;
    this.seasonRepository = seasonRepository;
    this.membershipRepository = membershipRepository;
    this.courtNameService = courtNameService;
    this.learningSinkSupplier =
        learningSinkProvider != null ? learningSinkProvider::getIfAvailable : () -> null;
    this.correctionLearner = correctionLearner;
    this.phoneticService = phoneticService;
  }

  @Override
  @Transactional(readOnly = true)
  public SpokenMatchInterpretation interpret(SpokenMatchInterpretationRequest request) {
    SpokenMatchInterpretation interpretation = new SpokenMatchInterpretation();
    if (request == null || !StringUtils.hasText(request.getTranscript())) {
      interpretation.addWarning("Speech-to-text result was empty.");
      return interpretation;
    }

    String transcript = request.getTranscript().trim();
    interpretation.setTranscript(transcript);

    String normalizedTranscript = normalizeSpokenNumbers(transcript);

    Long ladderConfigId = resolveLadderConfigId(request.getLadderConfigId(), request.getSeasonId());
    ContextData context = loadContext(ladderConfigId, request.getCurrentUserId());
    if (context.usersById.isEmpty()) {
      interpretation.addWarning("No eligible players found for this ladder context.");
    }

    Set<Long> mentionBoost = detectMentionedUsers(transcript, context);
    ScoreParseResult scoreResult = parseScores(normalizedTranscript, interpretation);
    // Propagate whether the interpreter detected a winner-first numeric order
    try {
      interpretation.setScoreOrderReversed(scoreResult.isReversed());
    } catch (Exception ex) {
      // ignore - defensive
    }
    String workingTranscript = scoreResult.remainder;
    SegmentResult segments = segmentTeams(workingTranscript);

    List<List<String>> tokenizedTeams = new ArrayList<>();
    for (String segment : segments.teamSegments) {
      tokenizedTeams.add(extractTokens(segment));
    }

    // If only one team is present and a speaker-victory pattern is likely, set winningTeamIndex=0
    // and add Team B as guests
    if (tokenizedTeams.size() == 1) {
      tokenizedTeams.add(new ArrayList<>());
    }

    // Guarantee: If transcript matches a speaker-victory or speaker-lost pattern and at least one
    // player is identified, set winningTeamIndex accordingly
    if (segments.winningTeamIndex == null
        && request != null
        && request.getCurrentUserId() != null) {
      String lowerTranscript =
          request.getTranscript() != null ? request.getTranscript().toLowerCase() : "";
      // Speaker victory patterns: 'i won', 'me and <user> won', 'we won', etc.
      boolean speakerVictory =
          lowerTranscript.matches(".*\\b(i|me|we|us)(\\s+and\\s+\\w+)?\\s+won\\b.*")
              || lowerTranscript.matches(".*\\b(i|me|we|us)(\\s+and\\s+\\w+)?\\s+beat\\b.*")
              || lowerTranscript.matches(".*\\b(i|me|we|us)(\\s+and\\s+\\w+)?\\s+defeated\\b.*");
      // Speaker lost patterns: 'i lost', 'we got beat by <user>', 'i was defeated by', etc.
      boolean speakerLost =
          lowerTranscript.matches(".*\\b(i|me|we|us)(\\s+and\\s+\\w+)?\\s+(lost|loses|losing)\\b.*")
              || lowerTranscript.matches(
                  ".*\\b(i|me|we|us)(\\s+and\\s+\\w+)?\\s+(got beat by|was defeated by|were defeated by|got beaten by|were beaten by)\\b.*");
      boolean hasIdentifiedPlayer = false;
      for (List<String> team : tokenizedTeams) {
        for (String token : team) {
          if (SELF_TOKENS.contains(token.toLowerCase())) {
            hasIdentifiedPlayer = true;
            break;
          }
        }
        if (hasIdentifiedPlayer) break;
      }
      if (speakerVictory && hasIdentifiedPlayer && tokenizedTeams.size() >= 2) {
        segments.winningTeamIndex = 0;
      } else if (speakerLost && hasIdentifiedPlayer && tokenizedTeams.size() >= 2) {
        segments.winningTeamIndex = 1;
      }
    }

    if (tokenizedTeams.isEmpty()) {
      interpretation.addWarning("Unable to detect players in the provided phrase.");
      tokenizedTeams.add(new ArrayList<>());
      tokenizedTeams.add(new ArrayList<>());
    }

    rebalanceTeamTokens(tokenizedTeams, interpretation);

    Set<Long> usedUserIds = new HashSet<>();
    List<TokenMatchPlan> allPlans = new ArrayList<>();

    for (int i = 0; i < tokenizedTeams.size(); i++) {
      List<String> tokens = tokenizedTeams.get(i);
      boolean winner = segments.winningTeamIndex != null && segments.winningTeamIndex == i;
      SpokenMatchInterpretation.Team team = interpretation.addTeam(i, winner);
      if (tokens.isEmpty()) {
        interpretation.addWarning("Team " + (i + 1) + " had no recognizable players.");
      }
      List<TokenMatchPlan> plans = new ArrayList<>();
      for (String token : tokens) {
        SpokenMatchInterpretation.PlayerResolution resolution = team.addPlayer();
        resolution.setToken(token);
        TokenMatchPlan plan =
            prepareTokenMatch(
                token,
                context,
                mentionBoost,
                request.getCurrentUserId(),
                usedUserIds,
                resolution,
                interpretation);
        plans.add(plan);
      }
      while (plans.size() < 2) {
        SpokenMatchInterpretation.PlayerResolution placeholder = team.addPlayer();
        placeholder.setToken(null);
        placeholder.setNeedsReview(true);
        interpretation.addWarning("Player spot on Team " + (i + 1) + " needs a selection.");
        plans.add(new TokenMatchPlan(placeholder, null));
      }
      allPlans.addAll(plans);
    }

    interpretation.setWinningTeamIndex(segments.winningTeamIndex);

    // Assign parsed scores now that team segmentation (and winning team) is known.
    try {
      if (scoreResult.scoreA != null && scoreResult.scoreB != null) {
        // If the interpreter detected winner-first numeric order, map tokens to
        // the correct team based on the discovered winningTeamIndex.
        if (scoreResult.isReversed()) {
          if (segments.winningTeamIndex != null) {
            if (segments.winningTeamIndex == 1) {
              // token1 was winner -> belongs to Team B
              interpretation.setScoreTeamA(scoreResult.scoreB);
              interpretation.setScoreTeamB(scoreResult.scoreA);
            } else {
              // token1 was winner -> belongs to Team A
              interpretation.setScoreTeamA(scoreResult.scoreA);
              interpretation.setScoreTeamB(scoreResult.scoreB);
            }
          } else {
            // No winning team discovered; conservatively assume token1 is winner
            interpretation.setScoreTeamA(scoreResult.scoreB);
            interpretation.setScoreTeamB(scoreResult.scoreA);
          }
        } else {
          interpretation.setScoreTeamA(scoreResult.scoreA);
          interpretation.setScoreTeamB(scoreResult.scoreB);
        }
      } else {
        if (scoreResult.scoreA != null) interpretation.setScoreTeamA(scoreResult.scoreA);
        if (scoreResult.scoreB != null) interpretation.setScoreTeamB(scoreResult.scoreB);
      }
    } catch (Exception ex) {
      // defensive: fall back to original parsed assignment
      if (scoreResult.scoreA != null) interpretation.setScoreTeamA(scoreResult.scoreA);
      if (scoreResult.scoreB != null) interpretation.setScoreTeamB(scoreResult.scoreB);
    }
    assignTokenMatches(allPlans, context, usedUserIds, interpretation);

    // Phase D: ML post-processing - Learn from user corrections to improve low-confidence matches
    improveWithUserHistory(interpretation, request.getCurrentUserId(), context);

    boolean scoresKnown = scoreResult.scoreA != null && scoreResult.scoreB != null;
    if (!scoresKnown) {
      interpretation.addWarning("Could not determine the score from the phrase.");
    }

    // Ensure both teams have at least 2 players (fill with guests as needed) if a winner is
    // detected
    if (segments.winningTeamIndex != null && interpretation.getTeams().size() >= 2) {
      for (int t = 0; t < 2; t++) {
        List<SpokenMatchInterpretation.PlayerResolution> players =
            interpretation.getTeams().get(t).getPlayers();
        while (players.size() < 2) {
          SpokenMatchInterpretation.PlayerResolution guestRes =
              interpretation.getTeams().get(t).addPlayer();
          guestRes.setMatchedUserId(null);
          guestRes.setToken("Guest");
          guestRes.setConfidence(1.0);
          guestRes.setNeedsReview(false);
        }
      }
    }

    // If a winner is detected but no score, assign default 11-9 (winner gets 11)
    if (segments.winningTeamIndex != null
        && (interpretation.getScoreTeamA() == null || interpretation.getScoreTeamB() == null)) {
      if (segments.winningTeamIndex == 0) {
        interpretation.setScoreTeamA(11);
        interpretation.setScoreTeamB(9);
      } else if (segments.winningTeamIndex == 1) {
        interpretation.setScoreTeamA(9);
        interpretation.setScoreTeamB(11);
      }
    }

    maybeRecordLearningOpportunity(request, ladderConfigId, interpretation);

    return interpretation;
  }

  private String normalizeSpokenNumbers(String input) {
    if (!StringUtils.hasText(input)) {
      return input;
    }
    String ordinalNormalized = GLOBAL_ORDINAL_PATTERN.matcher(input).replaceAll("$1");
    Matcher matcher = NUMBER_WORD_PATTERN.matcher(ordinalNormalized);
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      String key = normalizeNumberWordKey(matcher.group(1));
      Integer value = NUMBER_WORD_MAP.get(key);
      if (value != null) {
        matcher.appendReplacement(buffer, Matcher.quoteReplacement(value.toString()));
      }
    }
    matcher.appendTail(buffer);
    // Collapse accidental repeated connectors like "to to" -> "to" which can
    // happen when speech-to-text duplicates short words. This helps numeric
    // score patterns like "11 to to 3" normalize into "11 to 3" so they can
    // be parsed correctly.
    String normalized = buffer.toString();
    normalized = normalized.replaceAll("(?i)\\bto(?:\\s+to)+\\b", "to");
    return normalized;
  }

  private ScoreParseResult parseScores(
      String transcript, SpokenMatchInterpretation interpretation) {
    if (!StringUtils.hasText(transcript)) {
      return new ScoreParseResult("", null, null, false);
    }

    // Try full two-score pattern first (e.g., "11 to 8", "11-9")
    Matcher matcher = NUMERIC_SCORE_PATTERN.matcher(transcript);
    while (matcher.find()) {
      Integer rawScoreA = parseScoreToken(matcher.group(1));
      Integer rawScoreB = parseScoreToken(matcher.group(2));
      if (rawScoreA == null || rawScoreB == null) {
        continue;
      }

      int normalizedA = normalizeScore(rawScoreA, rawScoreB, interpretation);
      int normalizedB = normalizeScore(rawScoreB, rawScoreA, interpretation);

      // Detect whether the spoken phrase likely presented scores in
      // winner-first order (e.g. "I lost to Dave 11 5"), in which case
      // the numeric order does not map directly to Team A/Team B.
      boolean reversed = detectScoreOrderReversal(transcript, matcher.start());
      if (reversed) {
        interpretation.addWarning(
            "Detected score order likely given as winner-first; please verify team/score ordering.");
      }

      String remainder =
          (transcript.substring(0, matcher.start()) + " " + transcript.substring(matcher.end()))
              .trim();
      return new ScoreParseResult(remainder, normalizedA, normalizedB, reversed);
    }

    // ALWAYS_LOG_PHASE2: Try margin pattern (e.g., "won by 5", "lost by 3")
    Matcher marginMatcher = MARGIN_PATTERN.matcher(transcript);
    if (marginMatcher.find() && interpretation.getWinningTeamIndex() != null) {
      Integer margin = parseScoreToken(marginMatcher.group(1));
      if (margin != null && margin >= 2 && margin <= 11) { // Reasonable pickleball margin
        // Default winning score to 11 (standard game), calculate losing score from margin
        int winningScore = 11;
        int losingScore = winningScore - margin;

        // If margin would result in invalid score, adjust winning score up
        // (e.g., "won by 3" → 11-8, but "won by 10" → 11-1 is valid, "won by 11" → 11-0 shutout)
        if (losingScore < 0) {
          losingScore = 0; // Shutout
        }

        // Normalize based on which team won
        int normalizedA = interpretation.getWinningTeamIndex() == 0 ? winningScore : losingScore;
        int normalizedB = interpretation.getWinningTeamIndex() == 0 ? losingScore : winningScore;

        String remainder =
            (transcript.substring(0, marginMatcher.start())
                    + " "
                    + transcript.substring(marginMatcher.end()))
                .trim();
        interpretation.addWarning(
            "Score estimated from "
                + margin
                + "-point margin. Assuming "
                + winningScore
                + "-"
                + losingScore
                + ".");
        return new ScoreParseResult(remainder, normalizedA, normalizedB, false);
      }
    }

    // ALWAYS_LOG_PHASE2: Try single score at end (e.g., "me and dave beat eddit and irena 11")
    // Assume winning team scored that amount, losing team scored (winner - 2) for minimum
    // pickleball margin
    Matcher singleMatcher = SINGLE_SCORE_PATTERN.matcher(transcript);
    if (singleMatcher.find() && interpretation.getWinningTeamIndex() != null) {
      Integer winningScore = parseScoreToken(singleMatcher.group(1));
      if (winningScore != null && winningScore >= 11) { // Valid pickleball winning score
        int losingScore = Math.max(0, winningScore - 2); // Default to minimum 2-point margin

        // Normalize based on which team won
        int normalizedA = interpretation.getWinningTeamIndex() == 0 ? winningScore : losingScore;
        int normalizedB = interpretation.getWinningTeamIndex() == 0 ? losingScore : winningScore;

        String remainder = transcript.substring(0, singleMatcher.start()).trim();
        interpretation.addWarning(
            "Only one score provided. Assuming losing team scored " + losingScore + ".");
        return new ScoreParseResult(remainder, normalizedA, normalizedB, false);
      }
    }

    return new ScoreParseResult(transcript, null, null, false);
  }

  /**
   * Heuristic to detect if the numeric score tokens in the transcript were spoken in winner-first
   * order (e.g. "I lost to Dave 11 5"). This inspects the region immediately preceding the numeric
   * tokens for common losing verbs/phrases.
   */
  private boolean detectScoreOrderReversal(String transcript, int scoreStartIndex) {
    if (transcript == null || scoreStartIndex <= 0) return false;
    int window = 80; // chars to look back
    int start = Math.max(0, scoreStartIndex - window);
    String context = transcript.substring(start, scoreStartIndex).toLowerCase(Locale.ROOT);
    // Common phrases that imply the speaker lost (so numbers after likely start with winner)
    String[] loserPhrases =
        new String[] {
          " lost to ",
          " lost against ",
          " was defeated by ",
          " got beat by ",
          " were beaten by ",
          " fell to ",
          " dropped to ",
          " took the loss to ",
          " took the loss against ",
          " got beat "
        };
    for (String p : loserPhrases) {
      if (context.contains(p.trim())) return true;
    }
    // Also consider plain 'lost' as a strong signal (covers "I lost Lamar 11 to 9")
    if (context.matches(".*\\blost\\b.*")) return true;
    // Also check for patterns like "they won" or "he won" immediately before scores
    if (context.matches(".*\\b(they|he|she|them|him|her)\\s+won\\b.*")) return true;
    return false;
  }

  private Integer parseScoreToken(String raw) {
    if (!StringUtils.hasText(raw)) {
      return null;
    }
    String token = raw.trim();
    if (!StringUtils.hasText(token)) {
      return null;
    }
    if (token.matches("\\d{1,2}")) {
      return Integer.parseInt(token);
    }
    Matcher ordinalMatcher = DIGIT_WITH_SUFFIX_PATTERN.matcher(token);
    if (ordinalMatcher.matches()) {
      return Integer.parseInt(ordinalMatcher.group(1));
    }
    String lettersOnly =
        token
            .toLowerCase(Locale.US)
            .replaceAll("[^a-z\\s-]", "")
            .replace('-', ' ')
            .replaceAll("\\s+", " ")
            .trim();
    if (!lettersOnly.isEmpty()) {
      Integer mapped = NUMBER_WORD_MAP.get(lettersOnly);
      if (mapped != null) {
        return mapped;
      }
    }
    return null;
  }

  private int normalizeScore(
      Integer candidate, Integer otherScore, SpokenMatchInterpretation interpretation) {
    if (candidate == null) {
      return 0;
    }
    int score = candidate;
    if (score <= 21) {
      return score;
    }

    int other = otherScore != null ? otherScore : 0;
    boolean suspiciousGap = other == 0 || Math.abs(score - other) > 8;
    int tens = score / 10;
    int ones = score % 10;
    if (suspiciousGap && tens >= 2 && tens <= 4 && ones <= 11 && ones >= 0) {
      int guess = (ones == 0) ? 0 : ones;
      if (guess <= 21 && guess != score) {
        interpretation.addWarning(
            "Adjusted a suspicious score '" + score + "' to '" + guess + "'. Please confirm.");
        log.info(
            "Adjusted suspicious spoken score from {} to {} (otherScore={})",
            score,
            guess,
            otherScore);
        return guess;
      }
    }

    interpretation.addWarning(
        "Score '" + score + "' looked too large to be real — please confirm.");
    log.info(
        "Possible invalid spoken score {}; leaving as-is for confirmation (otherScore={})",
        score,
        otherScore);
    return score;
  }

  private SegmentResult segmentTeams(String working) {
    SegmentResult structured = segmentUsingStructuredPatterns(working);
    if (structured != null) {
      return structured;
    }

    SegmentResult keywordResult = segmentUsingKeywordRules(working);
    if (keywordResult != null) {
      return keywordResult;
    }

    SegmentResult vsResult = segmentUsingVsConnector(working);
    if (vsResult != null) {
      return vsResult;
    }

    SegmentResult sentenceResult = segmentUsingSentenceContext(working);
    if (sentenceResult != null) {
      return sentenceResult;
    }

    SegmentResult fallback = new SegmentResult();
    fallback.teamSegments.add(working.trim());
    return fallback;
  }

  private SegmentResult segmentUsingStructuredPatterns(String working) {
    if (!StringUtils.hasText(working)) {
      return null;
    }

    String normalized = working.trim();
    for (TeamPattern pattern : TEAM_PATTERNS) {
      Matcher matcher = pattern.pattern.matcher(normalized);
      if (!matcher.find()) {
        continue;
      }
      String winners = cleanSegment(safeGroup(matcher, pattern.winnersGroup));
      String losers = cleanSegment(safeGroup(matcher, pattern.losersGroup));
      // ALWAYS_LOG_PHASE2: Allow patterns without losers group (standalone "won")
      // In this case, winners play against unknown/guest opponents
      if (!StringUtils.hasText(winners)) {
        continue;
      }
      if (pattern.losersGroup != null && !StringUtils.hasText(losers)) {
        continue;
      }
      SegmentResult result = new SegmentResult();
      // ALWAYS_LOG_PHASE2: Always add losers first (speakers), then winners (opponents)
      // This ensures Team A = speakers (who logged the match) for consistent team ordering
      if (StringUtils.hasText(losers)) {
        // Determine which matched group represents the speakers (Team A). Prefer the group
        // that explicitly contains a self token (me/i/we/us). This handles patterns where
        // the named groups (winners/losers) don't match the natural speaker/opponent order.
        boolean winnersHaveSelf = containsSelfToken(winners);
        boolean losersHaveSelf = containsSelfToken(losers);

        if (winnersHaveSelf && !losersHaveSelf) {
          result.teamSegments.add(winners); // Team A = speakers
          result.teamSegments.add(losers); // Team B = opponents
          // winners variable ended up at index 0
          result.winningTeamIndex = (pattern.winningTeamIndex == null) ? null : 0;
        } else if (losersHaveSelf && !winnersHaveSelf) {
          result.teamSegments.add(losers); // Team A = speakers
          result.teamSegments.add(winners); // Team B = opponents
          // winners variable ended up at index 1
          result.winningTeamIndex = (pattern.winningTeamIndex == null) ? null : 1;
        } else {
          // Default: preserve original ordering where losers (speakers) are first
          result.teamSegments.add(losers); // Team A = speakers/losers
          result.teamSegments.add(winners); // Team B = opponents/winners
          result.winningTeamIndex = pattern.winningTeamIndex;
        }
      } else {
        // Standalone patterns like "we won" or "Tim won"
        // Check if winningTeamIndex indicates speakers lost (opponent victory)
        if (pattern.winningTeamIndex != null && pattern.winningTeamIndex == 1) {
          // Opponent victory: "Tim won" means speakers (implicit) lost to Tim
          // Add empty placeholder for speakers (Team A), then opponents (Team B)
          result.teamSegments.add(
              ""); // Team A = speakers (implicit, will be filled with user+guests)
          result.teamSegments.add(winners); // Team B = opponents who won
        } else {
          // Speaker victory: "we won" - only winners group captured
          result.teamSegments.add(winners); // Team A = speakers/winners
        }
      }
      result.winningTeamIndex = pattern.winningTeamIndex;
      return result;
    }
    return null;
  }

  private boolean containsSelfToken(String segment) {
    if (!StringUtils.hasText(segment)) return false;
    String lower = segment.toLowerCase(Locale.US);
    for (String t : SELF_TOKENS) {
      if (lower.contains(" " + t + " ")
          || lower.startsWith(t + " ")
          || lower.endsWith(" " + t)
          || lower.equals(t)) {
        return true;
      }
    }
    // also look for 'me and' or 'and me' patterns
    if (lower.matches(".*\\b(me|i|we|us)\\b.*")) {
      return true;
    }
    return false;
  }

  private String safeGroup(Matcher matcher, String groupName) {
    // ALWAYS_LOG_PHASE2: Handle null group names (for patterns without losers group)
    if (groupName == null) {
      return null;
    }
    try {
      return matcher.group(groupName);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private String cleanSegment(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value.replaceAll("\\s+", " ").trim();
  }

  private SegmentResult segmentUsingKeywordRules(String working) {
    if (!StringUtils.hasText(working)) {
      return null;
    }
    String lower = working.toLowerCase(Locale.US);
    KeywordMatch match = findKeywordMatch(lower);
    if (match == null) {
      return null;
    }

    String before = working.substring(0, match.index).trim();
    String after = working.substring(match.endIndex).trim();

    if (!StringUtils.hasText(before) && !StringUtils.hasText(after)) {
      return null;
    }

    SegmentResult result = new SegmentResult();
    result.teamSegments.add(before);
    result.teamSegments.add(after);
    if (!StringUtils.hasText(before) || !StringUtils.hasText(after)) {
      result.winningTeamIndex = null;
    } else {
      result.winningTeamIndex = match.winnerIndex;
    }
    return result;
  }

  private SegmentResult segmentUsingVsConnector(String working) {
    Matcher vsMatcher = TEAM_SPLIT_PATTERN.matcher(working);
    if (vsMatcher.find()) {
      String first = working.substring(0, vsMatcher.start()).trim();
      String second = working.substring(vsMatcher.end()).trim();
      SegmentResult result = new SegmentResult();
      result.teamSegments.add(first);
      result.teamSegments.add(second);
      result.winningTeamIndex = null;
      return result;
    }
    return null;
  }

  private SegmentResult segmentUsingSentenceContext(String working) {
    String[] sentences = working.split("(?<=[.!?])\\s+");
    if (sentences.length <= 1) {
      return null;
    }

    StringBuilder winners = new StringBuilder();
    StringBuilder losers = new StringBuilder();
    Boolean context = null;
    boolean winnersHaveNames = false;
    boolean losersHaveNames = false;

    for (String raw : sentences) {
      String sentence = raw.trim();
      if (!StringUtils.hasText(sentence)) {
        continue;
      }
      String lowered = " " + sentence.toLowerCase(Locale.US) + " ";
      boolean winnerHint = containsAny(lowered, WIN_SENTENCE_HINTS);
      boolean loserHint = containsAny(lowered, LOSE_SENTENCE_HINTS);
      boolean againstHint = containsAny(lowered, AGAINST_HINTS);
      boolean teamList = looksLikeTeamList(lowered);
      boolean likelyNames = teamList || againstHint;
      boolean selfMention = containsAny(lowered, SELF_CONTEXT_HINTS);

      if (winnerHint) {
        if (teamList) {
          appendSentence(winners, sentence);
          winnersHaveNames = true;
        }
        context = Boolean.TRUE;
        continue;
      }

      if (loserHint || againstHint) {
        if (likelyNames) {
          appendSentence(losers, sentence);
          losersHaveNames = true;
        }
        context = Boolean.FALSE;
        continue;
      }

      if (teamList && context != null) {
        if (context.booleanValue()) {
          appendSentence(winners, sentence);
          winnersHaveNames = true;
        } else {
          appendSentence(losers, sentence);
          losersHaveNames = true;
        }
        continue;
      }

      if (teamList && selfMention) {
        appendSentence(winners, sentence);
        winnersHaveNames = true;
        context = Boolean.TRUE;
        continue;
      }

      if (likelyNames) {
        appendSentence(losers, sentence);
        losersHaveNames = true;
        context = Boolean.FALSE;
      }
    }

    if (winners.length() == 0 && losers.length() == 0) {
      return null;
    }

    SegmentResult result = new SegmentResult();
    if (winners.length() > 0) {
      result.teamSegments.add(winners.toString().trim());
    }
    if (losers.length() > 0) {
      result.teamSegments.add(losers.toString().trim());
    }

    if (result.teamSegments.size() == 1) {
      // ensure second slot exists for downstream logic
      result.teamSegments.add("");
    }

    if (winners.length() > 0 && (losers.length() > 0 || winnersHaveNames)) {
      result.winningTeamIndex = 0;
    }
    return result;
  }

  private void appendSentence(StringBuilder builder, String sentence) {
    if (builder.length() > 0) {
      builder.append(' ');
    }
    builder.append(sentence.trim());
  }

  private boolean containsAny(String haystack, String[] needles) {
    for (String needle : needles) {
      if (haystack.contains(needle)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Computes phonetic similarity between two strings using Double Metaphone. This is particularly
   * effective for matching names that sound similar but may be spelled differently, which is common
   * in speech-to-text input.
   */
  private double computePhoneticSimilarity(String a, String b) {
    if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
      return 0.0;
    }

    // Use centralized phonetic service for basic encoding
    String primaryCodeA = phoneticService.encodePhonetically(a);
    String primaryCodeB = phoneticService.encodePhonetically(b);

    // For now, compare primary codes only
    // TODO: Consider exposing alternate codes from PhoneticMatchingService if needed
    return phoneticService.computeStringSimilarity(primaryCodeA, primaryCodeB);
  }

  private boolean looksLikeTeamList(String loweredSentence) {
    if (containsAny(loweredSentence, TEAM_LIST_HINTS)) {
      return true;
    }
    String stripped = loweredSentence.replaceAll("[^a-z0-9 ]", " ").trim();
    if (!StringUtils.hasText(stripped)) {
      return false;
    }
    int wordCount = stripped.isEmpty() ? 0 : stripped.split("\\s+").length;
    return wordCount >= 3;
  }

  private KeywordMatch findKeywordMatch(String lower) {
    KeywordMatch best = null;
    for (KeywordRule rule : KEYWORD_RULES) {
      Matcher matcher = rule.pattern.matcher(lower);
      while (matcher.find()) {
        KeywordMatch candidate =
            new KeywordMatch(matcher.start(), matcher.end(), matcher.group(), rule.winnerIndex);
        if (best == null
            || candidate.index < best.index
            || (candidate.index == best.index
                && (candidate.endIndex - candidate.index) > (best.endIndex - best.index))) {
          best = candidate;
        }
      }
    }
    return best;
  }

  private void maybeRecordLearningOpportunity(
      SpokenMatchInterpretationRequest request,
      Long ladderConfigId,
      SpokenMatchInterpretation interpretation) {
    SpokenMatchLearningSink sink = learningSinkSupplier.get();
    if (sink == null) {
      return;
    }

    SpokenMatchLearningSample sample = new SpokenMatchLearningSample();
    sample.setTranscript(interpretation.getTranscript());
    sample.setSeasonId(request != null ? request.getSeasonId() : null);
    sample.setLadderConfigId(ladderConfigId);
    sample.setCurrentUserId(request != null ? request.getCurrentUserId() : null);
    // ALWAYS_LOG_PHASE2: Removed complete flag - validation is not interpreter's job
    sample.setScoreMissing(
        interpretation.getScoreTeamA() == null || interpretation.getScoreTeamB() == null);
    sample.setWarnings(interpretation.getWarnings());

    for (SpokenMatchInterpretation.Team team : interpretation.getTeams()) {
      if (team == null) {
        continue;
      }
      for (SpokenMatchInterpretation.PlayerResolution resolution : team.getPlayers()) {
        if (resolution == null) {
          continue;
        }
        if (resolution.getMatchedUserId() != null && !resolution.isNeedsReview()) {
          continue;
        }
        PlayerIssue issue = sample.addPlayerIssue();
        issue.setTeamIndex(team.getIndex());
        issue.setToken(resolution.getToken());
        issue.setMatchedUserId(resolution.getMatchedUserId());
        issue.setMatchedName(resolution.getMatchedName());
        issue.setConfidence(resolution.getConfidence());
        issue.setNeedsReview(resolution.isNeedsReview());
        issue.setAliasUsed(resolution.getMatchedAlias());
      }
    }

    if (!sample.shouldRecord()) {
      return;
    }

    if (!sink.supportsSample(sample)) {
      return;
    }

    sink.record(sample);
  }

  private List<String> extractTokens(String segment) {
    if (!StringUtils.hasText(segment)) {
      return new ArrayList<>();
    }

    // First, clean up common speech patterns and punctuation
    String cleaned = segment;

    // Remove trailing score boundary phrases (e.g., 'by a score of', 'by score of', 'by a score',
    // 'by score')
    cleaned = cleaned.replaceAll("(?i)\\bby( a)? score( of)?\\b.*$", "");

    // Remove leading filler words/affirmatives more aggressively
    cleaned =
        cleaned.replaceAll(
            "^(?i)\\s*(?:yea|yeah|yes|yep|ok|okay|well|so|um|uh|uhh|uhm|erm|er|hmm)\\s*(?:,\\s*(?:yea|yeah|yes|yep|ok|okay|well|so|um|uh|uhh|uhm|erm|er|hmm)\\s*)*,?\\s*",
            "");

    // Standard replacements
    cleaned =
        cleaned
            .replaceAll("(?i)\\bplaying with\\b", " and ")
            .replaceAll("(?i)\\bpartnered with\\b", " and ")
            .replaceAll("(?i)\\bteamed (?:up )?with\\b", " and ")
            .replaceAll("[.!]", " ");

    // Remove filler phrases and words
    cleaned = FILLER_PHRASE_PATTERN.matcher(cleaned).replaceAll(" ");
    cleaned = FILLER_WORD_PATTERN.matcher(cleaned).replaceAll(" ");

    // Cleanup any resulting double spaces
    cleaned = cleaned.replaceAll("\\s+", " ").trim();
    if (!StringUtils.hasText(cleaned)) {
      return new ArrayList<>();
    }

    List<String> tokens = new ArrayList<>();

    // First, check for and preserve self-pairs
    Matcher selfPairMatcher = SELF_PAIR_PATTERN.matcher(cleaned);
    if (selfPairMatcher.find()) {
      // Determine which alternative matched: group(1) is present for "X and I",
      // group(2) is present for "I and X". Preserve the spoken ordering so that
      // "Me and Dave" produces ["I","Dave"] and "Dave and me" produces ["Dave","I"].
      String g1 = selfPairMatcher.group(1);
      String g2 = selfPairMatcher.group(2);
      if (g1 != null) {
        // matched "X and I" -> teammate first, then current
        tokens.add(g1);
        tokens.add("ME");
      } else if (g2 != null) {
        // matched "I and X" -> current first, then teammate
        tokens.add("ME");
        tokens.add(g2);
      }
      // Replace the matched portion to avoid re-processing
      cleaned =
          cleaned.substring(0, selfPairMatcher.start())
              + " "
              + cleaned.substring(selfPairMatcher.end());
    }

    // Process remaining text with regular token splitting
    String[] parts = TOKEN_SPLIT_PATTERN.split(cleaned);
    for (String part : parts) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()
          && !FILLER_WORD_PATTERN.matcher(" " + trimmed + " ").find()
          && !trimmed.equalsIgnoreCase("and")
          && !trimmed.equalsIgnoreCase("with")) {
        tokens.add(trimmed);
      }
    }
    return tokens;
  }

  private void rebalanceTeamTokens(
      List<List<String>> tokenizedTeams, SpokenMatchInterpretation interpretation) {
    // Ensure we have exactly 2 teams
    if (tokenizedTeams.isEmpty()) {
      tokenizedTeams.add(new ArrayList<>());
    }
    while (tokenizedTeams.size() < 2) {
      tokenizedTeams.add(new ArrayList<>());
    }

    // Consolidate overflow (3+ teams) into team B
    if (tokenizedTeams.size() > 2) {
      List<String> overflow = new ArrayList<>();
      for (int i = 2; i < tokenizedTeams.size(); i++) {
        overflow.addAll(tokenizedTeams.get(i));
      }
      tokenizedTeams.subList(2, tokenizedTeams.size()).clear();
      if (!overflow.isEmpty()) {
        tokenizedTeams.get(1).addAll(overflow);
      }
    }

    List<String> teamA = tokenizedTeams.get(0);
    List<String> teamB = tokenizedTeams.get(1);

    // ALWAYS_LOG_PHASE2: Don't auto-rebalance teams - trust user's stated team structure
    // Instead, warn about unusual team sizes so user can verify
    int sizeA = teamA.size();
    int sizeB = teamB.size();
    int total = sizeA + sizeB;

    // Warn if teams are imbalanced AND we have more than 0 players total
    // (Empty teams are OK - they become guests)
    if (sizeA != sizeB && total > 0 && total != 4) {
      interpretation.addWarning(
          "Detected "
              + sizeA
              + " vs "
              + sizeB
              + " player configuration. Please verify team assignments are correct.");
    }

    sanitizeTeamTokens(teamA, 0, interpretation);
    sanitizeTeamTokens(teamB, 1, interpretation);
  }

  // ALWAYS_LOG_PHASE2: Removed redistributeForBalance() and findMovableIndex()
  // No longer auto-rebalancing teams - we trust user's stated team structure

  private void sanitizeTeamTokens(
      List<String> tokens, int teamIndex, SpokenMatchInterpretation interpretation) {
    if (tokens == null || tokens.isEmpty()) {
      return;
    }
    List<String> cleaned = new ArrayList<>(tokens.size());
    Set<String> seen = new LinkedHashSet<>();
    for (String token : tokens) {
      if (!StringUtils.hasText(token)) {
        continue;
      }
      String normalized = normalize(token);
      String key =
          StringUtils.hasText(normalized) ? normalized : token.trim().toLowerCase(Locale.US);
      if (seen.add(key)) {
        cleaned.add(token);
      } else {
        interpretation.addWarning(
            "Ignoring repeated name '" + token + "' for Team " + (teamIndex + 1) + ".");
      }
    }
    tokens.clear();
    tokens.addAll(cleaned);
    if (tokens.size() > 2) {
      interpretation.addWarning(
          "Detected extra names on Team " + (teamIndex + 1) + ". Keeping the first two.");
      while (tokens.size() > 2) {
        tokens.remove(tokens.size() - 1);
      }
    }
  }

  private Set<Long> detectMentionedUsers(String transcript, ContextData context) {
    if (!StringUtils.hasText(transcript) || context.aliasCandidates.isEmpty()) {
      return Set.of();
    }

    List<String> words = toWordList(transcript);
    if (words.isEmpty()) {
      return Set.of();
    }

    Set<Long> matches = new LinkedHashSet<>();
    for (AliasCandidate candidate : context.aliasCandidates) {
      if (candidate.userId == null || candidate.searchTokens.isEmpty()) {
        continue;
      }
      if (hasAliasWindow(words, candidate.searchTokens)) {
        matches.add(candidate.userId);
        continue;
      }
      if (candidate.searchTokens.size() == 1) {
        String needle = candidate.searchTokens.get(0);
        for (String word : words) {
          if (computeSimilarity(word, needle) >= 0.92) {
            matches.add(candidate.userId);
            break;
          }
        }
      }
    }
    return matches;
  }

  private boolean hasAliasWindow(List<String> haystack, List<String> aliasTokens) {
    if (aliasTokens.isEmpty() || aliasTokens.size() > haystack.size()) {
      return false;
    }
    int window = aliasTokens.size();
    for (int i = 0; i <= haystack.size() - window; i++) {
      double score = 0d;
      for (int j = 0; j < window; j++) {
        score += computeSimilarity(haystack.get(i + j), aliasTokens.get(j));
      }
      double average = score / window;
      if (average >= 0.92) {
        return true;
      }
      if (average >= 0.82) {
        String joinedHaystack = String.join("", haystack.subList(i, i + window));
        String joinedAlias = String.join("", aliasTokens);
        if (computeSimilarity(joinedHaystack, joinedAlias) >= 0.85) {
          return true;
        }
      }
    }
    return false;
  }

  private List<String> toWordList(String phrase) {
    String normalized = normalizeForSearch(phrase);
    if (!StringUtils.hasText(normalized)) {
      return List.of();
    }
    String[] pieces = normalized.split(" ");
    if (pieces.length == 0) {
      return List.of();
    }
    return Arrays.asList(pieces);
  }

  private TokenMatchPlan prepareTokenMatch(
      String token,
      ContextData context,
      Set<Long> mentionBoost,
      Long currentUserId,
      Set<Long> usedUserIds,
      SpokenMatchInterpretation.PlayerResolution resolution,
      SpokenMatchInterpretation interpretation) {
    TokenMatchPlan plan = new TokenMatchPlan(resolution, token);

    if (!StringUtils.hasText(token)) {
      resolution.setNeedsReview(true);
      interpretation.addWarning("A player name was blank or not recognized.");
      plan.needsAssignment = false;
      return plan;
    }

    String lower = token.toLowerCase(Locale.US);
    if (SELF_TOKENS.contains(lower) && currentUserId != null) {
      User current = context.usersById.get(currentUserId);
      if (current != null) {
        resolution.setMatchedUserId(current.getId());
        resolution.setMatchedName(defaultDisplayName(current));
        resolution.setMatchedAlias(token.trim());
        resolution.setConfidence(1.0);
        resolution.setNeedsReview(false);
        plan.needsAssignment = false;
        usedUserIds.add(current.getId());
        return plan;
      }
    }

    // ALWAYS_LOG_PHASE2: Map 'i', 'me', 'myself' to current user
    if ((token.equalsIgnoreCase("i")
            || token.equalsIgnoreCase("me")
            || token.equalsIgnoreCase("myself"))
        && currentUserId != null) {
      resolution.setMatchedUserId(currentUserId);
      resolution.setConfidence(1.0);
      resolution.setNeedsReview(false);
      plan.needsAssignment = false;
      return plan;
    }

    String normalizedToken = normalize(token);
    if (!StringUtils.hasText(normalizedToken)) {
      resolution.setNeedsReview(true);
      interpretation.addWarning("Unable to normalize token '" + token + "'.");
      plan.needsAssignment = false;
      return plan;
    }

    String softToken = softNormalize(normalizedToken);
    List<AliasCandidate> prioritized = context.findAliasMatches(normalizedToken);
    if (!Objects.equals(normalizedToken, softToken)) {
      List<AliasCandidate> softMatches = context.findAliasMatches(softToken);
      if (!softMatches.isEmpty()) {
        if (prioritized.isEmpty()) {
          prioritized = new ArrayList<>(softMatches);
        } else {
          for (AliasCandidate candidate : softMatches) {
            if (!prioritized.contains(candidate)) {
              prioritized.add(candidate);
            }
          }
        }
      }
    }

    List<ScoredCandidate> candidates =
        scoreCandidates(
            normalizedToken, softToken, context, prioritized, mentionBoost, usedUserIds);
    plan.candidates = candidates;
    plan.bestScore = candidates.isEmpty() ? 0d : candidates.get(0).score;
    plan.needsAssignment = true;
    return plan;
  }

  private List<ScoredCandidate> scoreCandidates(
      String normalizedToken,
      String softToken,
      ContextData context,
      List<AliasCandidate> prioritized,
      Set<Long> mentionBoost,
      Set<Long> usedUserIds) {
    List<ScoredCandidate> scored = new ArrayList<>();
    LinkedHashSet<AliasCandidate> searchOrder = new LinkedHashSet<>();
    if (prioritized != null && !prioritized.isEmpty()) {
      searchOrder.addAll(prioritized);
    }
    searchOrder.addAll(context.aliasCandidates);

    for (AliasCandidate candidate : searchOrder) {
      if (candidate.userId != null && usedUserIds.contains(candidate.userId)) {
        continue; // already matched to another token
      }
      double score = computeSimilarityWithVariants(normalizedToken, softToken, candidate);
      if (mentionBoost != null
          && candidate.userId != null
          && mentionBoost.contains(candidate.userId)) {
        score = Math.min(1.0, score + 0.07);
      }
      if (score <= 0d) {
        continue;
      }
      ScoredCandidate ranked = new ScoredCandidate(candidate, score);
      scored.add(ranked);
    }

    scored.sort(Comparator.comparingDouble((ScoredCandidate c) -> c.score).reversed());
    return scored;
  }

  private double computeSimilarityWithVariants(
      String normalizedToken, String softToken, AliasCandidate candidate) {
    // First try initial-aware comparison on the original text
    double initialAwareScore = computeNameSimilarityWithInitials(candidate.alias, softToken);

    // Then do the regular variant comparison
    double variantScore = 0d;
    List<String> tokenVariants = buildTokenVariants(normalizedToken, softToken);
    List<String> aliasVariants = buildAliasVariants(candidate);

    for (String tokenVariant : tokenVariants) {
      if (!StringUtils.hasText(tokenVariant)) {
        continue;
      }

      for (String aliasVariant : aliasVariants) {
        if (!StringUtils.hasText(aliasVariant)) {
          continue;
        }

        if (tokenVariant.equals(aliasVariant)) {
          return 1.0;
        }

        double score = computeSimilarity(tokenVariant, aliasVariant);
        if (tokenVariant.contains(aliasVariant) || aliasVariant.contains(tokenVariant)) {
          score = Math.max(score, computeSubstringAffinity(tokenVariant, aliasVariant));
        }
        variantScore = Math.max(variantScore, score);
      }
    }

    // Use the higher of the two scores
    return Math.max(initialAwareScore, variantScore);
  }

  private List<String> buildTokenVariants(String normalizedToken, String softToken) {
    LinkedHashSet<String> variants = new LinkedHashSet<>();
    if (StringUtils.hasText(normalizedToken)) {
      variants.add(normalizedToken);
      variants.add(trimTrailingRepeated(normalizedToken));
      variants.add(removeTrailingSingleLetter(normalizedToken));
    }
    if (StringUtils.hasText(softToken)) {
      variants.add(softToken);
    }
    return variants.stream().filter(StringUtils::hasText).collect(Collectors.toList());
  }

  private List<String> buildAliasVariants(AliasCandidate candidate) {
    LinkedHashSet<String> variants = new LinkedHashSet<>();
    variants.add(candidate.normalizedAlias);
    if (StringUtils.hasText(candidate.softNormalized)) {
      variants.add(candidate.softNormalized);
    }
    variants.add(trimTrailingRepeated(candidate.normalizedAlias));
    return variants.stream().filter(StringUtils::hasText).collect(Collectors.toList());
  }

  private double computeSubstringAffinity(String a, String b) {
    int shorter = Math.min(a.length(), b.length());
    if (shorter == 0) {
      return 0d;
    }
    int longer = Math.max(a.length(), b.length());
    double ratio = (double) shorter / (double) longer;

    // Lowered base scores to be more strict
    double base = shorter >= 5 ? 0.75 : (shorter >= 3 ? 0.65 : 0.50);

    // Reduced the ratio multiplier to put less emphasis on length matching
    double score = Math.min(0.90, base + (0.10 * ratio));

    // Add a penalty for very different lengths
    if (longer > shorter * 1.5) {
      score *= 0.8; // 20% penalty for significant length mismatch
    }

    return score;
  }

  private double computeSimilarity(String a, String b) {
    if (a.equals(b)) {
      return 1.0;
    }
    int maxLength = Math.max(a.length(), b.length());
    if (maxLength == 0) {
      return 0d;
    }

    // Calculate phonetic similarity using Double Metaphone
    double phoneticSimilarity = computePhoneticSimilarity(a, b);

    // Calculate Levenshtein similarity (weighted less now) using centralized service
    double levenshteinSimilarity = phoneticService.computeStringSimilarity(a, b);

    // Weight phonetic similarity more heavily for speech recognition
    double raw = (phoneticSimilarity * 0.7) + (levenshteinSimilarity * 0.3);

    // Add penalties for length differences
    double lengthRatio = (double) Math.min(a.length(), b.length()) / maxLength;
    if (lengthRatio < 0.8) { // If lengths differ by more than 20%
      raw *= lengthRatio; // Apply proportional penalty
    }

    // Add bonus for matching first characters (important for names)
    if (!a.isEmpty() && !b.isEmpty() && a.charAt(0) == b.charAt(0)) {
      raw = raw * 0.9 + 0.1; // 10% bonus for matching first letter
    }

    // Reduce penalty for short strings as phonetic matching helps
    if (maxLength < 4) {
      raw *= 0.9; // Only 10% penalty for short strings now
    }

    // Ensure bounds
    return Math.max(0.0, Math.min(1.0, raw));
  }

  private String trimTrailingRepeated(String value) {
    if (!StringUtils.hasText(value)) {
      return value;
    }
    return value.replaceAll("([a-z])\\1{1,}$", "$1");
  }

  private String removeTrailingSingleLetter(String value) {
    if (!StringUtils.hasText(value) || value.length() <= 3) {
      return value;
    }
    char last = value.charAt(value.length() - 1);
    if (Character.isLetter(last)) {
      return value.substring(0, value.length() - 1);
    }
    return value;
  }

  private String normalize(String value) {
    StringBuilder sb = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (Character.isLetterOrDigit(ch)) {
        sb.append(Character.toLowerCase(ch));
      }
    }
    return sb.toString();
  }

  private String softNormalize(String normalized) {
    if (!StringUtils.hasText(normalized)) {
      return normalized;
    }
    String result = normalized.replaceAll("([a-z])\\1{2,}$", "$1");
    result = result.replaceAll("([a-z])\\1$", "$1");
    if (result.length() > 4) {
      char last = result.charAt(result.length() - 1);
      char prev = result.charAt(result.length() - 2);
      if (isConsonant(last) && isVowel(prev)) {
        result = result.substring(0, result.length() - 2) + last;
      }
    }
    return result;
  }

  /**
   * Checks if a name has a likely initial/abbreviation pattern, including phonetic variations.
   * Examples: "John D", "John Dee", "D Smith", "Dee Smith", "John bee" (for "John B")
   */
  private boolean hasInitialPattern(String name) {
    if (!StringUtils.hasText(name)) {
      return false;
    }

    String[] parts = name.split("\\s+");
    if (parts.length < 2) {
      return false;
    }

    // Check each part for potential initial patterns
    for (int i = 0; i < parts.length; i++) {
      String part = parts[i];
      // Check if this part is a potential initial (either single letter or phonetic representation)
      if (normalizePhoneticLetter(part) != null) {
        // If it's not the last part, it could be a prefix initial (e.g., "D Smith")
        // If it's not the first part, it could be a suffix initial (e.g., "John D")
        return (i > 0) || (i < parts.length - 1);
      }
    }

    return false;
  }

  /**
   * Extracts the likely initial from a name if present, handling phonetic variations. Returns null
   * if no initial pattern is found.
   */
  private String extractInitial(String name) {
    if (!StringUtils.hasText(name)) {
      return null;
    }

    String[] parts = name.split("\\s+");
    if (parts.length < 2) {
      return null;
    }

    // First check for a trailing initial/phonetic letter
    String lastPart = parts[parts.length - 1];
    String lastNormalized = normalizePhoneticLetter(lastPart);
    if (lastNormalized != null) {
      return lastNormalized;
    }

    // Then check for a leading initial/phonetic letter
    String firstPart = parts[0];
    String firstNormalized = normalizePhoneticLetter(firstPart);
    if (firstNormalized != null && parts.length > 1) {
      return firstNormalized;
    }

    return null;
  }

  /**
   * Extracts the main name part (excluding the initial) from a name. Handles both standard initials
   * and phonetic variations.
   */
  private String extractMainName(String name) {
    if (!StringUtils.hasText(name)) {
      return name;
    }

    String[] parts = name.split("\\s+");
    if (parts.length < 2) {
      return name;
    }

    // Check if the last part is a potential initial
    if (normalizePhoneticLetter(parts[parts.length - 1]) != null) {
      return String.join(" ", Arrays.copyOfRange(parts, 0, parts.length - 1));
    }

    // Check if the first part is a potential initial
    if (normalizePhoneticLetter(parts[0]) != null) {
      return String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
    }

    return name;
  }

  /**
   * Computes similarity between names that might have initials or abbreviated forms, including
   * handling phonetic variations from speech-to-text.
   */
  private double computeNameSimilarityWithInitials(String name1, String name2) {
    if (!StringUtils.hasText(name1) || !StringUtils.hasText(name2)) {
      return 0.0;
    }

    // Check if either name has an initial pattern (including phonetic variations)
    boolean name1HasInitial = hasInitialPattern(name1);
    boolean name2HasInitial = hasInitialPattern(name2);

    if (!name1HasInitial && !name2HasInitial) {
      // If neither has initials, use regular similarity but also check for potential missed
      // phonetic patterns
      String[] parts1 = name1.split("\\s+");
      String[] parts2 = name2.split("\\s+");

      // Check last words for potential phonetic match
      if (parts1.length > 0 && parts2.length > 0) {
        String lastPart1 = parts1[parts1.length - 1];
        String lastPart2 = parts2[parts2.length - 1];
        String normalized1 = normalizePhoneticLetter(lastPart1);
        String normalized2 = normalizePhoneticLetter(lastPart2);

        if (normalized1 != null && normalized2 != null) {
          return normalized1.equals(normalized2) ? 0.95 : 0.0;
        }
      }

      return computeSimilarity(name1, name2);
    }

    // Extract components from both names
    String main1 = extractMainName(name1);
    String main2 = extractMainName(name2);
    String initial1 = extractInitial(name1);
    String initial2 = extractInitial(name2);

    // Calculate main name similarity
    double mainNameSimilarity = computeSimilarity(main1, main2);

    // Check for initial matches, including phonetic variations
    boolean initialsMatch = false;
    double initialMatchScore = 0.0;

    if (initial1 != null && initial2 != null) {
      // Direct initial match (including normalized phonetic forms)
      if (initial1.equalsIgnoreCase(initial2)) {
        initialMatchScore = 1.0;
        initialsMatch = true;
      }
    }

    // Check if an initial matches the start of the other name
    if (!initialsMatch && initial1 != null) {
      if (main2.toUpperCase().startsWith(initial1.toUpperCase())) {
        initialMatchScore = 0.9; // Slightly lower score for partial match
        initialsMatch = true;
      }
    }
    if (!initialsMatch && initial2 != null) {
      if (main1.toUpperCase().startsWith(initial2.toUpperCase())) {
        initialMatchScore = 0.9; // Slightly lower score for partial match
        initialsMatch = true;
      }
    }

    if (initialsMatch) {
      // Weight the main name similarity and initial match
      return Math.min(1.0, (mainNameSimilarity * 0.7) + (initialMatchScore * 0.3));
    }

    // If initials are present but don't match, apply a penalty
    if (initial1 != null && initial2 != null) {
      return mainNameSimilarity * 0.7; // 30% penalty for mismatched initials
    }

    // If only one name has an initial, reduce confidence slightly
    return mainNameSimilarity * 0.85; // 15% penalty for inconsistent initial presence
  }

  private String normalizeForSearch(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    String lower = value.toLowerCase(Locale.US);
    lower = lower.replaceAll("[^a-z0-9]+", " ").trim();
    return lower.replaceAll("\\s+", " ");
  }

  private boolean isVowel(char ch) {
    return ch >= 'a'
        && ch <= 'z'
        && (ch == 'a' || ch == 'e' || ch == 'i' || ch == 'o' || ch == 'u');
  }

  private boolean isConsonant(char ch) {
    return ch >= 'a' && ch <= 'z' && !isVowel(ch);
  }

  /**
   * Maps common speech-to-text variations of letter names to their actual letters. For example:
   * "bee" -> "b", "see" -> "c", "eye" -> "i", etc.
   */
  private static final Map<String, String> PHONETIC_LETTER_MAP = buildPhoneticLetterMap();

  private static Map<String, String> buildPhoneticLetterMap() {
    Map<String, String> map = new HashMap<>();
    // Standard letter pronunciations
    map.put("ay", "a");
    map.put("bee", "b");
    map.put("be", "b");
    map.put("see", "c");
    map.put("sea", "c");
    map.put("dee", "d");
    map.put("de", "d");
    map.put("ee", "e");
    map.put("ef", "f");
    map.put("gee", "g");
    map.put("ge", "g");
    map.put("aitch", "h");
    map.put("eye", "i");
    map.put("jay", "j");
    map.put("kay", "k");
    map.put("el", "l");
    map.put("em", "m");
    map.put("en", "n");
    map.put("oh", "o");
    map.put("pee", "p");
    map.put("pe", "p");
    map.put("cue", "q");
    map.put("queue", "q");
    map.put("are", "r");
    map.put("ar", "r");
    map.put("ess", "s");
    map.put("tee", "t");
    map.put("te", "t");
    map.put("you", "u");
    map.put("vee", "v");
    map.put("ve", "v");
    map.put("double you", "w");
    map.put("doubleyou", "w");
    map.put("ex", "x");
    map.put("why", "y");
    map.put("zee", "z");
    map.put("zed", "z");

    // Common speech recognition variations
    map.put("hey", "a"); // "A" misheard
    map.put("be", "b"); // Common short form
    map.put("sea", "c"); // Homophone
    map.put("he", "h"); // Common mishearing
    map.put("i", "i"); // Letter itself
    map.put("o", "o"); // Letter itself
    map.put("our", "r"); // Mishearing of "are"
    map.put("tea", "t"); // Homophone
    map.put("u", "u"); // Letter itself
    map.put("we", "w"); // Common mishearing
    map.put("y", "y"); // Letter itself

    return Collections.unmodifiableMap(map);
  }

  /**
   * Attempts to convert a potential phonetic letter representation to its actual letter. Returns
   * null if the input doesn't match any known letter pronunciation.
   */
  private String normalizePhoneticLetter(String input) {
    if (input == null || input.length() < 1) {
      return null;
    }

    // First check if it's already a single letter
    if (input.length() == 1 && Character.isLetter(input.charAt(0))) {
      return input.toLowerCase();
    }

    // Then check our phonetic mappings
    String normalized = input.toLowerCase().trim();
    String letter = PHONETIC_LETTER_MAP.get(normalized);
    if (letter != null) {
      return letter;
    }

    // For single letters with potential period
    if (normalized.length() <= 2 && normalized.charAt(0) >= 'a' && normalized.charAt(0) <= 'z') {
      return String.valueOf(normalized.charAt(0));
    }

    return null;
  }

  private Long resolveLadderConfigId(Long ladderConfigId, Long seasonId) {
    if (ladderConfigId != null) {
      return ladderConfigId;
    }
    if (seasonId == null) {
      return null;
    }
    Optional<LadderSeason> season = seasonRepository.findById(seasonId);
    return season
        .map(s -> s.getLadderConfig() != null ? s.getLadderConfig().getId() : null)
        .orElse(null);
  }

  private ContextData loadContext(Long ladderConfigId, Long currentUserId) {
    Set<Long> userIds = new LinkedHashSet<>();
    if (ladderConfigId != null) {
      List<LadderMembership> memberships =
          membershipRepository.findByLadderConfigIdAndStateOrderByJoinedAtAsc(
              ladderConfigId, LadderMembership.State.ACTIVE);
      memberships.stream()
          .map(LadderMembership::getUserId)
          .filter(Objects::nonNull)
          .forEach(userIds::add);
    }

    // Never widen voice matching to the global user base when ladder context is missing.
    // At most, keep the current user in scope so self-references like "me" still resolve.
    if (currentUserId != null) {
      userIds.add(currentUserId);
    }

    List<User> users = userIds.isEmpty() ? List.of() : userRepository.findAllById(userIds);
    Map<Long, User> byId =
        users.stream()
            .filter(Objects::nonNull)
            .filter(u -> u.getId() != null)
            .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a, LinkedHashMap::new));

    if (currentUserId != null && !byId.containsKey(currentUserId)) {
      userRepository.findById(currentUserId).ifPresent(user -> byId.put(user.getId(), user));
    }

    Collection<Long> aliasIds = byId.keySet();
    Map<Long, Set<String>> combinedAliasMap =
        aliasIds.isEmpty()
            ? Map.of()
            : courtNameService.gatherCourtNamesForUsers(aliasIds, ladderConfigId);

    Map<Long, Set<String>> globalAliasMap =
        aliasIds.isEmpty() ? Map.of() : courtNameService.gatherCourtNamesForUsers(aliasIds, null);

    AliasBuildResult aliasBuild = buildAliasCandidates(combinedAliasMap, globalAliasMap, byId);

    return new ContextData(byId, aliasBuild.candidates, aliasBuild.primaryAliasByUser);
  }

  private AliasBuildResult buildAliasCandidates(
      Map<Long, Set<String>> aliasMap,
      Map<Long, Set<String>> globalAliasMap,
      Map<Long, User> usersById) {
    List<AliasCandidate> candidates = new ArrayList<>();
    Map<Long, String> primaryAliasByUser = new LinkedHashMap<>();
    Set<String> seen = new HashSet<>();

    aliasMap.forEach(
        (userId, aliases) -> {
          if (userId == null) {
            return;
          }
          Set<String> globalAliases =
              new LinkedHashSet<>(globalAliasMap.getOrDefault(userId, Set.of()));
          String preferredAlias = null;

          for (String alias : aliases) {
            addAliasCandidate(userId, alias, seen, candidates);
            if (preferredAlias == null
                && StringUtils.hasText(alias)
                && !globalAliases.contains(alias)) {
              preferredAlias = alias;
            }
          }

          if (preferredAlias == null) {
            for (String alias : globalAliases) {
              if (StringUtils.hasText(alias)) {
                preferredAlias = alias;
                break;
              }
            }
          }

          if (preferredAlias == null) {
            User user = usersById.get(userId);
            preferredAlias = defaultDisplayName(user);
          }

          if (StringUtils.hasText(preferredAlias)) {
            primaryAliasByUser.put(userId, preferredAlias);
          }
        });

    usersById.forEach(
        (userId, user) -> {
          primaryAliasByUser.computeIfAbsent(
              userId,
              ignored -> {
                String display = defaultDisplayName(user);
                if (!StringUtils.hasText(display) && userId != null) {
                  display = com.w3llspring.fhpb.web.util.UserPublicName.FALLBACK;
                }
                return display;
              });
        });

    return new AliasBuildResult(candidates, primaryAliasByUser);
  }

  private void addAliasCandidate(
      Long userId, String alias, Set<String> seen, List<AliasCandidate> candidates) {
    if (!StringUtils.hasText(alias)) {
      return;
    }
    String normalized = normalize(alias);
    if (!StringUtils.hasText(normalized)) {
      return;
    }
    String softNormalized = softNormalize(normalized);
    String searchNormalized = normalizeForSearch(alias);
    List<String> searchTokens =
        StringUtils.hasText(searchNormalized) ? List.of(searchNormalized.split(" ")) : List.of();

    String key = userId + "::" + normalized;
    if (seen.add(key)) {
      candidates.add(
          new AliasCandidate(
              userId, alias, normalized, softNormalized, searchNormalized, searchTokens));
    }
    String[] parts = alias.split("[\\s\\-_/]+");
    for (String part : parts) {
      String partNormalized = normalize(part);
      if (!StringUtils.hasText(partNormalized)) {
        continue;
      }
      String partSoft = softNormalize(partNormalized);
      String partSearch = normalizeForSearch(part);
      List<String> partTokens =
          StringUtils.hasText(partSearch) ? List.of(partSearch.split(" ")) : List.of();
      String partKey = userId + "::" + partNormalized;
      if (seen.add(partKey)) {
        candidates.add(
            new AliasCandidate(userId, part, partNormalized, partSoft, partSearch, partTokens));
      }
    }
  }

  private String defaultDisplayName(User user) {
    if (user == null) {
      return com.w3llspring.fhpb.web.util.UserPublicName.FALLBACK;
    }
    if (StringUtils.hasText(user.getNickName())) {
      return user.getNickName();
    }
    return com.w3llspring.fhpb.web.util.UserPublicName.forUser(user);
  }

  private static class SegmentResult {
    final List<String> teamSegments = new ArrayList<>();
    Integer winningTeamIndex;
  }

  private static class KeywordMatch {
    final int index;
    final int endIndex;
    final String keyword;
    final int winnerIndex;

    KeywordMatch(int index, int endIndex, String keyword, int winnerIndex) {
      this.index = index;
      this.endIndex = endIndex;
      this.keyword = keyword;
      this.winnerIndex = winnerIndex;
    }
  }

  private static class KeywordRule {
    final Pattern pattern;
    final int winnerIndex;

    KeywordRule(String regex, int winnerIndex) {
      this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
      this.winnerIndex = winnerIndex;
    }
  }

  private static class AliasBuildResult {
    final List<AliasCandidate> candidates;
    final Map<Long, String> primaryAliasByUser;

    AliasBuildResult(List<AliasCandidate> candidates, Map<Long, String> primaryAliasByUser) {
      this.candidates = candidates;
      this.primaryAliasByUser = primaryAliasByUser;
    }
  }

  private static class TeamPattern {
    final Pattern pattern;
    final String winnersGroup;
    final String losersGroup;
    final Integer winningTeamIndex;

    TeamPattern(String regex, String winnersGroup, String losersGroup, Integer winningTeamIndex) {
      this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
      this.winnersGroup = winnersGroup;
      this.losersGroup = losersGroup;
      this.winningTeamIndex = winningTeamIndex;
    }
  }

  private static class AliasCandidate {
    final Long userId;
    final String alias;
    final String normalizedAlias;
    final String softNormalized;
    final String searchNormalized;
    final List<String> searchTokens;

    AliasCandidate(
        Long userId,
        String alias,
        String normalizedAlias,
        String softNormalized,
        String searchNormalized,
        List<String> searchTokens) {
      this.userId = userId;
      this.alias = alias;
      this.normalizedAlias = normalizedAlias;
      this.softNormalized = softNormalized;
      this.searchNormalized = searchNormalized;
      this.searchTokens = searchTokens == null ? List.of() : List.copyOf(searchTokens);
    }
  }

  private static class ScoredCandidate {
    final AliasCandidate candidate;
    final double score;

    ScoredCandidate(AliasCandidate candidate, double score) {
      this.candidate = candidate;
      this.score = score;
    }
  }

  private static class ScoreParseResult {
    final String remainder;
    final Integer scoreA;
    final Integer scoreB;
    final boolean reversed;

    ScoreParseResult(String remainder, Integer scoreA, Integer scoreB, boolean reversed) {
      this.remainder = remainder;
      this.scoreA = scoreA;
      this.scoreB = scoreB;
      this.reversed = reversed;
    }

    public boolean isReversed() {
      return reversed;
    }
  }

  private static class ContextData {
    final Map<Long, User> usersById;
    final List<AliasCandidate> aliasCandidates;
    final Map<String, List<AliasCandidate>> aliasIndex;
    final Map<String, List<AliasCandidate>> keywordIndex;
    final Map<Long, String> primaryAliasByUser;

    ContextData(
        Map<Long, User> usersById,
        List<AliasCandidate> aliasCandidates,
        Map<Long, String> primaryAliasByUser) {
      this.usersById = usersById;
      this.aliasCandidates = aliasCandidates;
      this.primaryAliasByUser = new LinkedHashMap<>(primaryAliasByUser);
      Map<String, List<AliasCandidate>> aliasIdx = new LinkedHashMap<>();
      Map<String, List<AliasCandidate>> keywordIdx = new LinkedHashMap<>();
      for (AliasCandidate candidate : aliasCandidates) {
        aliasIdx.computeIfAbsent(candidate.normalizedAlias, k -> new ArrayList<>()).add(candidate);
        if (StringUtils.hasText(candidate.softNormalized)) {
          aliasIdx.computeIfAbsent(candidate.softNormalized, k -> new ArrayList<>()).add(candidate);
        }
        for (String token : candidate.searchTokens) {
          keywordIdx.computeIfAbsent(token, k -> new ArrayList<>()).add(candidate);
        }
      }
      this.aliasIndex = aliasIdx;
      this.keywordIndex = keywordIdx;
    }

    List<AliasCandidate> findAliasMatches(String key) {
      if (!StringUtils.hasText(key)) {
        return new ArrayList<>();
      }
      LinkedHashSet<AliasCandidate> matches = new LinkedHashSet<>();
      List<AliasCandidate> direct = aliasIndex.get(key);
      if (direct != null) {
        matches.addAll(direct);
      }
      List<AliasCandidate> keywords = keywordIndex.get(key);
      if (keywords != null) {
        matches.addAll(keywords);
      }
      return new ArrayList<>(matches);
    }

    String resolvePrimaryAlias(Long userId, Supplier<String> defaultSupplier) {
      String alias = primaryAliasByUser.get(userId);
      if (StringUtils.hasText(alias)) {
        return alias;
      }
      alias = defaultSupplier.get();
      if (!StringUtils.hasText(alias) && userId != null) {
        alias = com.w3llspring.fhpb.web.util.UserPublicName.FALLBACK;
      }
      return alias;
    }
  }

  private static class TokenMatchPlan {
    final SpokenMatchInterpretation.PlayerResolution resolution;
    final String token;
    List<ScoredCandidate> candidates = List.of();
    double bestScore = 0d;
    boolean assigned = false;
    boolean needsAssignment = false;

    TokenMatchPlan(SpokenMatchInterpretation.PlayerResolution resolution, String token) {
      this.resolution = resolution;
      this.token = token;
    }

    boolean needsAssignment() {
      return needsAssignment;
    }
  }

  private void assignTokenMatches(
      List<TokenMatchPlan> plans,
      ContextData context,
      Set<Long> usedUserIds,
      SpokenMatchInterpretation interpretation) {
    if (plans.isEmpty()) {
      return;
    }

    List<TokenMatchPlan> assignable =
        plans.stream()
            .filter(TokenMatchPlan::needsAssignment)
            .sorted(Comparator.comparingDouble((TokenMatchPlan p) -> p.bestScore).reversed())
            .collect(Collectors.toList());

    for (TokenMatchPlan plan : assignable) {
      boolean matched = false;
      for (ScoredCandidate candidate : plan.candidates) {
        if (candidate.score < MIN_ASSIGN_CONFIDENCE) {
          break;
        }
        Long candidateUserId = candidate.candidate.userId;
        if (candidateUserId != null && usedUserIds.contains(candidateUserId)) {
          continue;
        }
        applyCandidate(plan, candidate, context, usedUserIds, interpretation);
        matched = true;
        break;
      }
      if (!matched) {
        markUnresolved(plan, interpretation);
      }
    }

    for (TokenMatchPlan plan : plans) {
      addAlternatives(plan, context);
    }
  }

  private void applyCandidate(
      TokenMatchPlan plan,
      ScoredCandidate best,
      ContextData context,
      Set<Long> usedUserIds,
      SpokenMatchInterpretation interpretation) {
    SpokenMatchInterpretation.PlayerResolution resolution = plan.resolution;
    resolution.setMatchedUserId(best.candidate.userId);
    User matched = context.usersById.get(best.candidate.userId);
    resolution.setMatchedName(defaultDisplayName(matched));
    resolution.setMatchedAlias(best.candidate.alias);
    resolution.setConfidence(best.score);
    if (best.candidate.userId != null) {
      usedUserIds.add(best.candidate.userId);
    }

    if (best.score < MIN_CONFIDENCE) {
      resolution.setNeedsReview(true);
      interpretation.addWarning(
          "Interpreted '"
              + plan.token
              + "' as "
              + resolution.getMatchedName()
              + ". Please confirm.");
      log.info(
          "Low-confidence spoken match: token='{}' resolvedTo='{}' alias='{}' score={} ladderContextSize={}",
          plan.token,
          resolution.getMatchedName(),
          resolution.getMatchedAlias(),
          best.score,
          context.aliasCandidates.size());
    } else {
      boolean ambiguous =
          plan.candidates.stream()
              .skip(1)
              .anyMatch(
                  c ->
                      c.score >= (best.score - AMBIGUITY_DELTA)
                          && !Objects.equals(c.candidate.userId, best.candidate.userId));
      resolution.setNeedsReview(ambiguous || best.score < STRONG_CONFIDENCE);
    }
    plan.assigned = true;
  }

  private void markUnresolved(TokenMatchPlan plan, SpokenMatchInterpretation interpretation) {
    SpokenMatchInterpretation.PlayerResolution resolution = plan.resolution;
    resolution.setMatchedUserId(null);
    resolution.setMatchedName(null);
    resolution.setMatchedAlias(null);
    resolution.setConfidence(0d);
    resolution.setNeedsReview(true);
    if (plan.bestScore > 0d && !plan.candidates.isEmpty()) {
      interpretation.addWarning(
          "No confident match found for '" + plan.token + "'. They might not have signed up yet.");
      log.info(
          "No confident match for spoken token='{}'; topScore={} options={}",
          plan.token,
          plan.bestScore,
          plan.candidates.size());
    } else {
      interpretation.addWarning(
          "No close match found for '" + plan.token + "'. They might not have signed up yet.");
      log.info("No close match for spoken token='{}'", plan.token);
    }
  }

  private void addAlternatives(TokenMatchPlan plan, ContextData context) {
    SpokenMatchInterpretation.PlayerResolution resolution = plan.resolution;
    LinkedHashSet<Long> seenUserIds = new LinkedHashSet<>();

    plan.candidates.stream()
        .limit(3)
        .forEach(
            candidate -> {
              Long userId = candidate.candidate.userId;
              User user = context.usersById.get(userId);
              resolution.addAlternative(
                  userId, candidate.candidate.alias, defaultDisplayName(user), candidate.score);
              if (userId != null) {
                seenUserIds.add(userId);
              }
            });

    context.usersById.forEach(
        (userId, user) -> {
          if (userId == null || seenUserIds.contains(userId)) {
            return;
          }
          String alias = context.resolvePrimaryAlias(userId, () -> defaultDisplayName(user));
          resolution.addAlternative(userId, alias, defaultDisplayName(user), 0d);
          seenUserIds.add(userId);
        });
  }

  /**
   * Phase D: ML post-processing - Improve low-confidence matches using user correction history.
   * This is called AFTER normal fuzzy matching completes. It only enhances matches that fell below
   * our confidence threshold, never overrides good matches.
   */
  private void improveWithUserHistory(
      SpokenMatchInterpretation interpretation, Long currentUserId, ContextData context) {
    if (currentUserId == null) {
      return; // No user history available for anonymous requests
    }

    // Build phonetic mapping from user's correction history
    Map<String, List<UserCorrectionLearner.PlayerCorrection>> correctionMap =
        correctionLearner.buildUserCorrectionMap(currentUserId);

    if (correctionMap.isEmpty()) {
      return; // User has no correction history yet
    }

    // Process all player resolutions across both teams
    for (SpokenMatchInterpretation.Team team : interpretation.getTeams()) {
      for (SpokenMatchInterpretation.PlayerResolution resolution : team.getPlayers()) {
        String token = resolution.getToken();
        double currentConfidence = resolution.getConfidence();

        // ONLY improve low-confidence matches (< 0.70 as specified in design)
        if (token == null || currentConfidence >= 0.70) {
          continue;
        }

        // Try to find better match using user's correction history
        Long improvedUserId = correctionLearner.findBestMatch(token, correctionMap);
        if (improvedUserId != null) {
          // Found a phonetic match in user's history! Apply it.
          User matched = context.usersById.get(improvedUserId);
          if (matched != null) {
            resolution.setMatchedUserId(improvedUserId);
            resolution.setMatchedName(defaultDisplayName(matched));
            String primaryAlias =
                context.resolvePrimaryAlias(improvedUserId, () -> defaultDisplayName(matched));
            resolution.setMatchedAlias(primaryAlias);

            // Boost confidence by +0.15 as specified in design
            resolution.setConfidence(currentConfidence + 0.15);

            // Still mark for review if below MIN_CONFIDENCE, but user can confirm
            resolution.setNeedsReview(resolution.getConfidence() < MIN_CONFIDENCE);

            log.info(
                "ML improved low-confidence match: token='{}' → userId={} name='{}' "
                    + "confidence: {} → {} (phonetic learning)",
                token,
                improvedUserId,
                resolution.getMatchedName(),
                currentConfidence,
                resolution.getConfidence());
          }
        }
      }
    }
  }
}
