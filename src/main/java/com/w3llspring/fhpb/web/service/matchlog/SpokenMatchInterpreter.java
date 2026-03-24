package com.w3llspring.fhpb.web.service.matchlog;

/** Provides a best-effort interpretation of spoken or transcribed match results. */
public interface SpokenMatchInterpreter {

  SpokenMatchInterpretation interpret(SpokenMatchInterpretationRequest request);
}
