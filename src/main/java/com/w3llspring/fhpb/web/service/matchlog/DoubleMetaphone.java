package com.w3llspring.fhpb.web.service.matchlog;

/**
 * Implementation of the Double Metaphone phonetic encoding algorithm, specifically optimized for
 * comparing spoken names.
 */
public class DoubleMetaphone {

  private static final int MAX_CODE_LEN = 4;
  private int maxCodeLen;

  public DoubleMetaphone() {
    this.maxCodeLen = MAX_CODE_LEN;
  }

  public String[] doubleMetaphone(String value) {
    if (value == null || value.length() == 0) {
      return new String[] {"", ""};
    }

    // Clean and normalize the input
    String str = cleanInput(value);
    if (str.length() == 0) {
      return new String[] {"", ""};
    }

    // Track the primary and alternate codes
    StringBuilder primary = new StringBuilder(this.maxCodeLen);
    StringBuilder alternate = new StringBuilder(this.maxCodeLen);

    int current = 0;
    int last = str.length() - 1;
    int length = str.length();

    while ((primary.length() < this.maxCodeLen || alternate.length() < this.maxCodeLen)
        && current < length) {
      char c = str.charAt(current);
      switch (c) {
        case 'A':
        case 'E':
        case 'I':
        case 'O':
        case 'U':
        case 'Y':
          if (current == 0) {
            // Only add vowel at start of word
            addMetaphoneCharacter(primary, alternate, c);
          }
          current++;
          break;

        case 'B':
          addMetaphoneCharacter(primary, alternate, 'P');
          current = handleDoubleConsonant(current, str, 'B');
          break;

        case 'C':
          current = handleC(str, primary, alternate, current);
          break;

        case 'D':
          current = handleD(str, primary, alternate, current);
          break;

        case 'F':
          addMetaphoneCharacter(primary, alternate, 'F');
          current = handleDoubleConsonant(current, str, 'F');
          break;

        case 'G':
          current = handleG(str, primary, alternate, current);
          break;

        case 'H':
          current = handleH(str, primary, alternate, current);
          break;

        case 'J':
          current = handleJ(str, primary, alternate, current);
          break;

        case 'K':
          addMetaphoneCharacter(primary, alternate, 'K');
          current = handleDoubleConsonant(current, str, 'K');
          break;

        case 'L':
          current = handleL(str, primary, alternate, current);
          break;

        case 'M':
          addMetaphoneCharacter(primary, alternate, 'M');
          current = handleDoubleConsonant(current, str, 'M');
          break;

        case 'N':
          addMetaphoneCharacter(primary, alternate, 'N');
          current = handleDoubleConsonant(current, str, 'N');
          break;

        case 'P':
          current = handleP(str, primary, alternate, current);
          break;

        case 'Q':
          addMetaphoneCharacter(primary, alternate, 'K');
          current = handleDoubleConsonant(current, str, 'Q');
          break;

        case 'R':
          current = handleR(str, primary, alternate, current);
          break;

        case 'S':
          current = handleS(str, primary, alternate, current);
          break;

        case 'T':
          current = handleT(str, primary, alternate, current);
          break;

        case 'V':
          addMetaphoneCharacter(primary, alternate, 'F');
          current = handleDoubleConsonant(current, str, 'V');
          break;

        case 'W':
          current = handleW(str, primary, alternate, current);
          break;

        case 'X':
          current = handleX(str, primary, alternate, current);
          break;

        case 'Z':
          current = handleZ(str, primary, alternate, current);
          break;

        default:
          current++;
          break;
      }
    }

    String primaryCode = primary.toString();
    String alternateCode = alternate.toString();
    return new String[] {primaryCode, alternateCode.equals(primaryCode) ? "" : alternateCode};
  }

  private int handleDoubleConsonant(int current, String str, char c) {
    if (current + 1 < str.length() && str.charAt(current + 1) == c) {
      current += 2;
    } else {
      current++;
    }
    return current;
  }

  private void addMetaphoneCharacter(StringBuilder primary, StringBuilder alternate, char c) {
    if (primary.length() < this.maxCodeLen) {
      primary.append(c);
    }
    if (alternate.length() < this.maxCodeLen) {
      alternate.append(c);
    }
  }

  private String cleanInput(String input) {
    if (input == null || input.length() == 0) {
      return "";
    }

    // Convert to uppercase and remove non-letter characters
    StringBuilder cleaned = new StringBuilder();
    for (char c : input.toCharArray()) {
      if (Character.isLetter(c)) {
        cleaned.append(Character.toUpperCase(c));
      }
    }
    return cleaned.toString();
  }

  private int handleC(String str, StringBuilder primary, StringBuilder alternate, int current) {
    if (conditionCK(str, current)) {
      addMetaphoneCharacter(primary, alternate, 'K');
      current += 2;
    } else if (current > 0
        && current + 1 < str.length()
        && str.substring(current - 1, current + 2).equals("CHI")) {
      addMetaphoneCharacter(primary, alternate, 'X');
      current += 2;
    } else if (current + 1 < str.length()
        && (str.charAt(current + 1) == 'E' || str.charAt(current + 1) == 'I')) {
      addMetaphoneCharacter(primary, alternate, 'S');
      current += 2;
    } else {
      addMetaphoneCharacter(primary, alternate, 'K');
      current++;
    }
    return current;
  }

  private boolean conditionCK(String str, int current) {
    return current + 1 < str.length() && str.charAt(current + 1) == 'K';
  }

  private int handleD(String str, StringBuilder primary, StringBuilder alternate, int current) {
    if (current + 2 < str.length() && str.substring(current, current + 3).equals("DGE")) {
      addMetaphoneCharacter(primary, alternate, 'J');
      current += 3;
    } else {
      addMetaphoneCharacter(primary, alternate, 'T');
      current++;
    }
    return current;
  }

  private int handleG(String str, StringBuilder primary, StringBuilder alternate, int current) {
    if (current + 1 < str.length() && str.charAt(current + 1) == 'H') {
      if (current > 0 && !isVowel(str.charAt(current - 1))) {
        addMetaphoneCharacter(primary, alternate, 'K');
      }
      current += 2;
    } else if (current + 1 < str.length() && str.charAt(current + 1) == 'N') {
      if (current + 2 == str.length() || str.charAt(current + 2) != 'E') {
        addMetaphoneCharacter(primary, alternate, 'K');
      }
      current += 2;
    } else {
      addMetaphoneCharacter(primary, alternate, 'K');
      current++;
    }
    return current;
  }

  private int handleH(String str, StringBuilder primary, StringBuilder alternate, int current) {
    if (current > 0 && !isVowel(str.charAt(current - 1))) {
      current++;
    } else {
      addMetaphoneCharacter(primary, alternate, 'H');
      current++;
    }
    return current;
  }

  private int handleJ(String str, StringBuilder primary, StringBuilder alternate, int current) {
    if (current + 1 < str.length() && str.charAt(current + 1) == 'J') {
      current += 2;
    } else {
      addMetaphoneCharacter(primary, alternate, 'J');
      current++;
    }
    return current;
  }

  private int handleL(String str, StringBuilder primary, StringBuilder alternate, int current) {
    if (current + 1 < str.length() && str.charAt(current + 1) == 'L') {
      if (isComplete(current + 2, str)) {
        primary.append('L');
      } else {
        addMetaphoneCharacter(primary, alternate, 'L');
      }
      current += 2;
    } else {
      addMetaphoneCharacter(primary, alternate, 'L');
      current++;
    }
    return current;
  }

  private int handleP(String str, StringBuilder primary, StringBuilder alternate, int current) {
    if (current + 1 < str.length() && str.charAt(current + 1) == 'H') {
      addMetaphoneCharacter(primary, alternate, 'F');
      current += 2;
    } else {
      addMetaphoneCharacter(primary, alternate, 'P');
      current = handleDoubleConsonant(current, str, 'P');
    }
    return current;
  }

  private int handleR(String str, StringBuilder primary, StringBuilder alternate, int current) {
    if (current == str.length() - 1 && current > 3 && hasEnding(str, current, "IE", "MEIER")) {
      // Special case: Don't encode terminal R after IE unless it's "MEIER"
      // Do nothing
    } else {
      addMetaphoneCharacter(primary, alternate, 'R');
    }
    return handleDoubleConsonant(current, str, 'R');
  }

  private int handleS(String str, StringBuilder primary, StringBuilder alternate, int current) {
    if (current + 1 < str.length()) {
      String substr = str.substring(current, current + 2);
      if (substr.equals("SH")) {
        addMetaphoneCharacter(primary, alternate, 'X');
        current += 2;
      } else if (substr.equals("SC")) {
        current = handleSC(str, primary, alternate, current);
      } else {
        addMetaphoneCharacter(primary, alternate, 'S');
        current = handleDoubleConsonant(current, str, 'S');
      }
    } else {
      addMetaphoneCharacter(primary, alternate, 'S');
      current++;
    }
    return current;
  }

  private int handleSC(String str, StringBuilder primary, StringBuilder alternate, int current) {
    if (current + 2 < str.length()) {
      char next = str.charAt(current + 2);
      if (next == 'H') {
        if (current + 6 < str.length()) {
          // Check for common Germanic surname suffixes
          String nextFour = str.substring(current + 3, current + 7);
          if ("HEIMER".equals(nextFour)
              || "HOEK".equals(nextFour)
              || "HOLZ".equals(nextFour)
              || "HOLTZ".equals(nextFour)) {
            addMetaphoneCharacter(primary, alternate, 'S');
          } else {
            addMetaphoneCharacter(primary, alternate, 'X');
          }
        } else {
          addMetaphoneCharacter(primary, alternate, 'X');
        }
        current += 3;
      } else if (next == 'I' || next == 'E' || next == 'Y') {
        addMetaphoneCharacter(primary, alternate, 'S');
        current += 3;
      } else {
        // Handle SK sound
        addMetaphoneCharacter(primary, alternate, 'S');
        addMetaphoneCharacter(primary, alternate, 'K');
        current += 3;
      }
    } else {
      addMetaphoneCharacter(primary, alternate, 'S');
      current++;
    }
    return current;
  }

  private int handleT(String str, StringBuilder primary, StringBuilder alternate, int current) {
    if (current + 1 < str.length()) {
      String substr = str.substring(current, current + 2);
      if (substr.equals("TH")) {
        addMetaphoneCharacter(primary, alternate, 'T');
        current += 2;
      } else if (substr.equals("TI")
          && current + 2 < str.length()
          && (str.charAt(current + 2) == 'O' || str.charAt(current + 2) == 'A')) {
        addMetaphoneCharacter(primary, alternate, 'X');
        current += 3;
      } else {
        addMetaphoneCharacter(primary, alternate, 'T');
        current = handleDoubleConsonant(current, str, 'T');
      }
    } else {
      addMetaphoneCharacter(primary, alternate, 'T');
      current++;
    }
    return current;
  }

  private int handleW(String str, StringBuilder primary, StringBuilder alternate, int current) {
    if (current + 1 < str.length() && str.charAt(current + 1) == 'H') {
      addMetaphoneCharacter(primary, alternate, 'W');
      current += 2;
    } else if (current + 1 < str.length() && str.charAt(current + 1) == 'R') {
      addMetaphoneCharacter(primary, alternate, 'R');
      current += 2;
    } else {
      addMetaphoneCharacter(primary, alternate, 'W');
      current++;
    }
    return current;
  }

  private int handleX(String str, StringBuilder primary, StringBuilder alternate, int current) {
    if (current == 0) {
      addMetaphoneCharacter(primary, alternate, 'S');
    } else {
      addMetaphoneCharacter(primary, alternate, 'K');
      addMetaphoneCharacter(primary, alternate, 'S');
    }
    if (current + 1 < str.length() && str.charAt(current + 1) == 'X') {
      current += 2;
    } else {
      current++;
    }
    return current;
  }

  private int handleZ(String str, StringBuilder primary, StringBuilder alternate, int current) {
    if (current + 1 < str.length() && str.charAt(current + 1) == 'H') {
      addMetaphoneCharacter(primary, alternate, 'J');
      current += 2;
    } else {
      addMetaphoneCharacter(primary, alternate, 'S');
      current = handleDoubleConsonant(current, str, 'Z');
    }
    return current;
  }

  private boolean isComplete(int current, String str) {
    return current >= str.length() || (current + 1 < str.length() && isVowel(str.charAt(current)));
  }

  private boolean isVowel(char c) {
    return "AEIOUY".indexOf(c) != -1;
  }

  private boolean hasEnding(String str, int current, String shortEnding, String exclusion) {
    if (current < shortEnding.length()) {
      return false;
    }
    // Check for the short ending (e.g. "IE")
    if (!str.substring(current - shortEnding.length(), current).equals(shortEnding)) {
      return false;
    }
    // If we don't have enough characters for the exclusion, it's a match
    if (current < exclusion.length()) {
      return true;
    }
    // Check it's not the exclusion word (e.g. "MEIER")
    return !str.substring(current - exclusion.length(), current).equals(exclusion);
  }
}
