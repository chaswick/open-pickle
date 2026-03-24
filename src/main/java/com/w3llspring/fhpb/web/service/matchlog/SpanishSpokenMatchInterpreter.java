package com.w3llspring.fhpb.web.service.matchlog;

import com.w3llspring.fhpb.web.db.LadderMembershipRepository;
import com.w3llspring.fhpb.web.db.LadderSeasonRepository;
import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.service.PhoneticMatchingService;
import com.w3llspring.fhpb.web.service.UserCorrectionLearner;
import com.w3llspring.fhpb.web.service.user.CourtNameService;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Lightweight adapter that enables {@link DefaultSpokenMatchInterpreter} to work with Spanish
 * speech-to-text transcripts by normalizing common phrases and number words into their English
 * equivalents before delegating to the default interpreter logic.
 */
@Service("spanishSpokenMatchInterpreter")
public class SpanishSpokenMatchInterpreter extends DefaultSpokenMatchInterpreter {

  private static final Map<Pattern, String> PHRASE_REPLACEMENTS = buildPhraseReplacements();
  private static final Map<String, String> NUMBER_WORD_REPLACEMENTS = buildNumberWordReplacements();
  private static final Map<String, String> WORD_REPLACEMENTS = buildWordReplacements();

  public SpanishSpokenMatchInterpreter(
      UserRepository userRepository,
      LadderSeasonRepository seasonRepository,
      LadderMembershipRepository membershipRepository,
      CourtNameService courtNameService,
      ObjectProvider<SpokenMatchLearningSink> learningSinkProvider,
      UserCorrectionLearner correctionLearner,
      PhoneticMatchingService phoneticService) {
    super(
        userRepository,
        seasonRepository,
        membershipRepository,
        courtNameService,
        learningSinkProvider,
        correctionLearner,
        phoneticService);
  }

  @Override
  public SpokenMatchInterpretation interpret(SpokenMatchInterpretationRequest request) {
    if (request == null || !StringUtils.hasText(request.getTranscript())) {
      return super.interpret(request);
    }

    String originalTranscript = request.getTranscript();
    String normalizedTranscript = normalizeSpanishTranscript(originalTranscript);

    SpokenMatchInterpretationRequest translatedRequest =
        cloneWithTranscript(request, normalizedTranscript);
    SpokenMatchInterpretation interpretation = super.interpret(translatedRequest);
    interpretation.setTranscript(originalTranscript);
    return interpretation;
  }

  private SpokenMatchInterpretationRequest cloneWithTranscript(
      SpokenMatchInterpretationRequest source, String transcript) {
    SpokenMatchInterpretationRequest clone = new SpokenMatchInterpretationRequest();
    clone.setTranscript(transcript);
    clone.setLadderConfigId(source.getLadderConfigId());
    clone.setSeasonId(source.getSeasonId());
    clone.setCurrentUserId(source.getCurrentUserId());
    return clone;
  }

  private String normalizeSpanishTranscript(String rawTranscript) {
    String working = stripDiacritics(rawTranscript);

    for (Entry<Pattern, String> entry : PHRASE_REPLACEMENTS.entrySet()) {
      working = entry.getKey().matcher(working).replaceAll(entry.getValue());
    }

    working = replaceWordBoundaries(working, NUMBER_WORD_REPLACEMENTS);
    working = replaceWordBoundaries(working, WORD_REPLACEMENTS);

    // Normalize common score phrasing like "11 a 3" -> "11 to 3"
    working = working.replaceAll("(?i)(\\d{1,2})\\s+a\\s+(\\d{1,2})", "$1 to $2");

    // Clean up duplicated whitespace generated during replacements.
    working = working.replaceAll("\\s+", " ").trim();
    return working;
  }

  private String stripDiacritics(String value) {
    String normalized = Normalizer.normalize(value, Form.NFD);
    return normalized.replaceAll("\\p{M}+", "");
  }

  private String replaceWordBoundaries(String input, Map<String, String> replacements) {
    String working = input;
    for (Entry<String, String> entry : replacements.entrySet()) {
      String pattern = "(?i)\\b" + Pattern.quote(entry.getKey()) + "\\b";
      working = working.replaceAll(pattern, entry.getValue());
    }
    return working;
  }

  private static Map<Pattern, String> buildPhraseReplacements() {
    Map<Pattern, String> replacements = new LinkedHashMap<>();
    replacements.put(Pattern.compile("(?i)\\bpor marcador de\\b"), "by a score of");
    replacements.put(Pattern.compile("(?i)\\bmarcador final\\b"), "final score");
    replacements.put(Pattern.compile("(?i)\\bganamos contra\\b"), "we beat");
    replacements.put(Pattern.compile("(?i)\\bganamos a\\b"), "we beat");
    replacements.put(Pattern.compile("(?i)\\bperdimos contra\\b"), "lost to");
    replacements.put(Pattern.compile("(?i)\\bperdimos ante\\b"), "lost to");
    replacements.put(Pattern.compile("(?i)\\bfrente a\\b"), "against");
    replacements.put(Pattern.compile("(?i)\\bjugamos contra\\b"), "we played against");
    return replacements;
  }

  private static Map<String, String> buildNumberWordReplacements() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("cero", "zero");
    map.put("uno", "one");
    map.put("dos", "two");
    map.put("tres", "three");
    map.put("cuatro", "four");
    map.put("cinco", "five");
    map.put("seis", "six");
    map.put("siete", "seven");
    map.put("ocho", "eight");
    map.put("nueve", "nine");
    map.put("diez", "ten");
    map.put("once", "eleven");
    map.put("doce", "twelve");
    map.put("trece", "thirteen");
    map.put("catorce", "fourteen");
    map.put("quince", "fifteen");
    map.put("dieciseis", "sixteen");
    map.put("diecisiete", "seventeen");
    map.put("dieciocho", "eighteen");
    map.put("diecinueve", "nineteen");
    map.put("veinte", "twenty");
    map.put("veintiuno", "twenty one");
    map.put("veintidos", "twenty two");
    map.put("veintitres", "twenty three");
    map.put("veinticuatro", "twenty four");
    map.put("veinticinco", "twenty five");
    map.put("veintiseis", "twenty six");
    map.put("veintisiete", "twenty seven");
    map.put("veintiocho", "twenty eight");
    map.put("veintinueve", "twenty nine");
    map.put("treinta", "thirty");
    return Map.copyOf(map);
  }

  private static Map<String, String> buildWordReplacements() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("ganamos", "we won");
    map.put("gane", "i won");
    map.put("gano", "won");
    map.put("ganaron", "they won");
    map.put("ganaste", "you won");
    map.put("ganaronos", "they won"); // fallback for transcription glitches

    map.put("perdimos", "we lost");
    map.put("perdi", "i lost");
    map.put("perdio", "lost");
    map.put("perdieron", "they lost");
    map.put("perdiste", "you lost");

    map.put("vencimos", "we beat");
    map.put("vencio", "beat");
    map.put("vencieron", "beat");
    map.put("derrotamos", "we beat");
    map.put("derroto", "beat");
    map.put("derrotaron", "beat");

    map.put("iguales", "tied");
    map.put("empate", "tie");
    map.put("empatamos", "we tied");

    map.put("contra", "against");
    map.put("versus", "versus");
    map.put("con", "with");
    map.put("y", "and");
    map.put("equipo", "team");
    map.put("amigos", "friends");
    map.put("jugamos", "we played");
    map.put("jugue", "i played");
    map.put("jugo", "played");
    map.put("jugaron", "played");

    map.put("yo", "i");
    map.put("nosotros", "we");
    map.put("nosotras", "we");
    map.put("ellos", "they");
    map.put("ellas", "they");

    map.put("al", "to the");
    map.put("a", "to");
    return Map.copyOf(map);
  }
}
