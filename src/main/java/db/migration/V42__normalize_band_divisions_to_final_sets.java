package db.migration;

import com.w3llspring.fhpb.web.service.BandDivisionSupport;
import com.w3llspring.fhpb.web.service.BandDivisionSupport.BandDefinition;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V42__normalize_band_divisions_to_final_sets extends BaseJavaMigration {

  private static final String DEFAULT_SUMMARY = "Band finish accolade for the season.";
  private static final String SEASON_SUMMARY_TEMPLATE = "Band finish accolade for the %s season.";
  private static final String TITLE_SUFFIX = " Finish";

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Map<Long, SeasonBandData> seasonDataById = loadSeasonData(connection);
    renameDefaultTemplates(connection);
    renameSeasonTrophies(connection, seasonDataById);
  }

  private void renameDefaultTemplates(Connection connection) throws SQLException {
    String query =
        """
                SELECT id, title, unlock_expression
                FROM trophy
                WHERE is_default_template = TRUE
                  AND unlock_expression LIKE 'final_band_index == %'
                """;
    try (PreparedStatement statement = connection.prepareStatement(query);
        ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        long trophyId = rs.getLong("id");
        String title = rs.getString("title");
        int bandIndex = parseBandIndex(rs.getString("unlock_expression"));
        if (bandIndex <= 0) {
          continue;
        }
        Optional<BandDefinition> definition =
            BandDivisionSupport.allBandDefinitions().stream()
                .filter(
                    candidate -> candidate.bandCount() > 1 && candidate.bandIndex() == bandIndex)
                .filter(candidate -> candidate.matchesCurrentOrLegacyName(stripFinish(title)))
                .findFirst();
        if (definition.isEmpty()) {
          continue;
        }
        updateBandFinishTrophy(connection, trophyId, definition.get(), DEFAULT_SUMMARY);
      }
    }
  }

  private void renameSeasonTrophies(Connection connection, Map<Long, SeasonBandData> seasonDataById)
      throws SQLException {
    String query =
        """
                SELECT id, season_id, unlock_expression
                FROM trophy
                WHERE is_default_template = FALSE
                  AND unlock_expression LIKE 'final_band_index == %'
                """;
    try (PreparedStatement statement = connection.prepareStatement(query);
        ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        long trophyId = rs.getLong("id");
        long seasonId = rs.getLong("season_id");
        int bandIndex = parseBandIndex(rs.getString("unlock_expression"));
        SeasonBandData seasonData = seasonDataById.get(seasonId);
        if (bandIndex <= 0 || seasonData == null || seasonData.bandCount() <= 1) {
          continue;
        }
        Optional<BandDefinition> definition =
            BandDivisionSupport.findBandDefinition(seasonData.bandCount(), bandIndex);
        if (definition.isEmpty()) {
          continue;
        }
        String seasonSummary =
            seasonData.name() == null || seasonData.name().isBlank()
                ? DEFAULT_SUMMARY
                : String.format(Locale.ENGLISH, SEASON_SUMMARY_TEMPLATE, seasonData.name());
        updateBandFinishTrophy(connection, trophyId, definition.get(), seasonSummary);
      }
    }
  }

  private void updateBandFinishTrophy(
      Connection connection, long trophyId, BandDefinition definition, String summary)
      throws SQLException {
    String update =
        """
                UPDATE trophy
                SET title = ?,
                    summary = ?,
                    unlock_condition = ?,
                    rarity = ?,
                    prompt = ?,
                    updated_at = ?
                WHERE id = ?
                """;
    try (PreparedStatement statement = connection.prepareStatement(update)) {
      statement.setString(1, definition.name() + TITLE_SUFFIX);
      statement.setString(2, summary);
      statement.setString(3, "Finish the season in " + definition.name());
      statement.setString(4, definition.trophyRarity().name());
      statement.setString(5, definition.trophyPrompt());
      statement.setTimestamp(6, Timestamp.from(Instant.now()));
      statement.setLong(7, trophyId);
      statement.executeUpdate();
    }
  }

  private Map<Long, SeasonBandData> loadSeasonData(Connection connection) throws SQLException {
    Map<Long, String> seasonNames = new HashMap<>();
    try (PreparedStatement statement =
            connection.prepareStatement("SELECT id, name FROM ladder_season");
        ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        seasonNames.put(rs.getLong("id"), rs.getString("name"));
      }
    }

    Map<Long, Integer> standingCounts = new HashMap<>();
    String standingQuery =
        """
                SELECT season_id, COUNT(*) AS standing_count
                FROM ladder_standing
                GROUP BY season_id
                """;
    try (PreparedStatement statement = connection.prepareStatement(standingQuery);
        ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        standingCounts.put(rs.getLong("season_id"), rs.getInt("standing_count"));
      }
    }

    Map<Long, SeasonBandData> seasonDataById = new HashMap<>();
    for (Map.Entry<Long, String> entry : seasonNames.entrySet()) {
      int standingCount = standingCounts.getOrDefault(entry.getKey(), 0);
      seasonDataById.put(
          entry.getKey(),
          new SeasonBandData(
              entry.getValue(), BandDivisionSupport.determineBandCount(standingCount)));
    }
    return seasonDataById;
  }

  private int parseBandIndex(String unlockExpression) {
    if (unlockExpression == null) {
      return -1;
    }
    int marker = unlockExpression.indexOf("==");
    if (marker < 0) {
      return -1;
    }
    try {
      return Integer.parseInt(unlockExpression.substring(marker + 2).trim());
    } catch (NumberFormatException ex) {
      return -1;
    }
  }

  private String stripFinish(String title) {
    if (title == null) {
      return null;
    }
    return title.endsWith(TITLE_SUFFIX)
        ? title.substring(0, title.length() - TITLE_SUFFIX.length())
        : title;
  }

  private record SeasonBandData(String name, int bandCount) {}
}
