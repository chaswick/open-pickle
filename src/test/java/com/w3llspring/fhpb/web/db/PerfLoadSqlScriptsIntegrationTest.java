package com.w3llspring.fhpb.web.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:perfloadverify;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "spring.flyway.enabled=false",
      "fhpb.analytics.enabled=false",
      "fhpb.push.enabled=false"
    })
class PerfLoadSqlScriptsIntegrationTest {

  private static final Path REPO_ROOT = Path.of("").toAbsolutePath();

  @Autowired private DataSource dataSource;

  @Autowired private JdbcTemplate jdbcTemplate;

  private static final Pattern SET_VARIABLE =
      Pattern.compile(
          "^SET\\s+@([A-Za-z0-9_]+)\\s*:?=\\s*(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern VARIABLE_REFERENCE = Pattern.compile("@([A-Za-z0-9_]+)");
  private static final Pattern INTERVAL_DAY =
      Pattern.compile("(.+?)\\s*([+-])\\s*INTERVAL\\s+(\\d+)\\s+DAY", Pattern.CASE_INSENSITIVE);
  private static final Pattern INTERVAL_HOUR =
      Pattern.compile("(.+?)\\s*([+-])\\s*INTERVAL\\s+(\\d+)\\s+HOUR", Pattern.CASE_INSENSITIVE);
  private static final Pattern TIMESTAMPADD =
      Pattern.compile(
          "TIMESTAMPADD\\((MINUTE|HOUR|DAY),\\s*([^,]+),\\s*([^)]+)\\)", Pattern.CASE_INSENSITIVE);

  @Test
  void cleanupRemovesWhatSeedAdds() {
    runScript("scripts/db/perf/seed_perf_load_users.sql");

    assertThat(count("SELECT COUNT(*) FROM users WHERE email LIKE 'perfload%@demo.local'"))
        .isEqualTo(20);
    assertThat(count("SELECT COUNT(*) FROM ladder_config WHERE invite_code LIKE 'PERFLOAD-%'"))
        .isEqualTo(2);
    assertThat(
            count(
                "SELECT COUNT(*) FROM ladder_membership lm "
                    + "JOIN users u ON u.id = lm.user_id "
                    + "WHERE u.email LIKE 'perfload%@demo.local'"))
        .isEqualTo(40);
    assertThat(
            count(
                "SELECT COUNT(*) FROM user_court_name ucn "
                    + "JOIN users u ON u.id = ucn.user_id "
                    + "WHERE u.email LIKE 'perfload%@demo.local'"))
        .isEqualTo(60);

    runScript("scripts/db/perf/cleanup_perf_load_users.sql");

    assertThat(count("SELECT COUNT(*) FROM users WHERE email LIKE 'perfload%@demo.local'"))
        .isZero();
    assertThat(count("SELECT COUNT(*) FROM ladder_config WHERE invite_code LIKE 'PERFLOAD-%'"))
        .isZero();
    assertThat(count("SELECT COUNT(*) FROM ladder_membership")).isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM user_court_name WHERE alias LIKE 'Load %' OR alias LIKE 'Alpha%' OR alias LIKE 'Beta%'"))
        .isZero();
  }

  private void runScript(String relativePath) {
    try {
      String raw =
          java.nio.file.Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
      executeMysqlishScript(raw);
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to read script " + relativePath, ex);
    }
  }

  private void executeMysqlishScript(String rawScript) {
    Map<String, String> variables = new LinkedHashMap<>();
    StringBuilder statement = new StringBuilder();

    for (String line : rawScript.split("\\R")) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("--")) {
        continue;
      }
      statement.append(line).append('\n');
      if (trimmed.endsWith(";")) {
        executeStatement(statement.toString(), variables);
        statement.setLength(0);
      }
    }
  }

  private void executeStatement(String statementText, Map<String, String> variables) {
    String statement = statementText.trim();
    if (statement.endsWith(";")) {
      statement = statement.substring(0, statement.length() - 1).trim();
    }
    if (statement.isEmpty()) {
      return;
    }
    if ("START TRANSACTION".equalsIgnoreCase(statement)
        || "COMMIT".equalsIgnoreCase(statement)
        || "ROLLBACK".equalsIgnoreCase(statement)) {
      return;
    }

    Matcher setMatcher = SET_VARIABLE.matcher(statement);
    if (setMatcher.matches()) {
      variables.put(
          setMatcher.group(1), normalizeExpression(setMatcher.group(2).trim(), variables));
      return;
    }

    String normalized = normalizeExpression(statement, variables);
    jdbcTemplate.execute(normalized);
  }

  private String normalizeExpression(String sql, Map<String, String> variables) {
    String normalized = sql;

    Matcher variableMatcher = VARIABLE_REFERENCE.matcher(normalized);
    StringBuffer variableBuffer = new StringBuffer();
    while (variableMatcher.find()) {
      String replacement = variables.get(variableMatcher.group(1));
      if (replacement == null) {
        throw new IllegalStateException("Unknown SQL variable @" + variableMatcher.group(1));
      }
      variableMatcher.appendReplacement(
          variableBuffer, Matcher.quoteReplacement("(" + replacement + ")"));
    }
    variableMatcher.appendTail(variableBuffer);
    normalized = variableBuffer.toString();

    normalized = normalized.replace("UTC_TIMESTAMP(6)", "CURRENT_TIMESTAMP()");
    normalized = normalized.replace("UTC_TIMESTAMP()", "CURRENT_TIMESTAMP()");
    normalized = normalized.replace("CURRENT_DATE", "CURRENT_DATE()");
    normalized = normalized.replace("DROP TEMPORARY TABLE IF EXISTS", "DROP TABLE IF EXISTS");
    normalized = normalized.replace("CREATE TEMPORARY TABLE", "CREATE TABLE");

    normalized = replaceIntervals(normalized, INTERVAL_DAY, "DAY");
    normalized = replaceIntervals(normalized, INTERVAL_HOUR, "HOUR");

    Matcher timestampAddMatcher = TIMESTAMPADD.matcher(normalized);
    StringBuffer tsBuffer = new StringBuffer();
    while (timestampAddMatcher.find()) {
      String unit = timestampAddMatcher.group(1).toUpperCase();
      String amount = timestampAddMatcher.group(2).trim();
      String base = timestampAddMatcher.group(3).trim();
      String replacement = "DATEADD('" + unit + "', " + amount + ", " + base + ")";
      timestampAddMatcher.appendReplacement(tsBuffer, Matcher.quoteReplacement(replacement));
    }
    timestampAddMatcher.appendTail(tsBuffer);
    normalized = tsBuffer.toString();

    return normalized;
  }

  private String replaceIntervals(String sql, Pattern pattern, String unit) {
    String current = sql;
    boolean changed = true;
    while (changed) {
      changed = false;
      Matcher matcher = pattern.matcher(current);
      StringBuffer buffer = new StringBuffer();
      while (matcher.find()) {
        changed = true;
        String base = matcher.group(1).trim();
        String operator = matcher.group(2);
        String amount = matcher.group(3);
        String signedAmount = "-".equals(operator) ? "-" + amount : amount;
        String replacement = "DATEADD('" + unit + "', " + signedAmount + ", " + base + ")";
        matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
      }
      matcher.appendTail(buffer);
      current = buffer.toString();
    }
    return current;
  }

  private long count(String sql) {
    Long value = jdbcTemplate.queryForObject(sql, Long.class);
    return value == null ? 0L : value;
  }
}
