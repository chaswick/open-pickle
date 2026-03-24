package com.w3llspring.fhpb.web.model;

/**
 * Defines ladder match confirmation modes.
 *
 * <p>Canonical modes: - STANDARD: Opposing team confirms the match. - SELF_CONFIRM: Match logger
 * confirms their own match.
 *
 * <p>Legacy values NONE and HIGH are retained for backward compatibility and are normalized to
 * STANDARD by business logic.
 */
public enum LadderSecurity {
  /** Legacy alias for STANDARD. */
  @Deprecated
  NONE,

  /** Canonical mode: opponent team confirmation is required. */
  STANDARD,

  /** Legacy alias for STANDARD. */
  @Deprecated
  HIGH,

  /** Match logger confirms their own match. */
  SELF_CONFIRM;

  public static LadderSecurity normalize(LadderSecurity security) {
    if (security == SELF_CONFIRM) {
      return SELF_CONFIRM;
    }
    return STANDARD;
  }

  public boolean isSelfConfirm() {
    return normalize(this) == SELF_CONFIRM;
  }

  public boolean requiresOpponentConfirmation() {
    return normalize(this) == STANDARD;
  }
}
