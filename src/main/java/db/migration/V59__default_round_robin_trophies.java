package db.migration;

import com.w3llspring.fhpb.web.service.trophy.RoundRobinTrophyTemplates;
import java.sql.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V59__default_round_robin_trophies extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    Connection db = context.getConnection();
    Long fallback;
    try (PreparedStatement q =
        db.prepareStatement("SELECT id FROM trophy_art WHERE storage_key = 'rr-fallback-v1'")) {
      try (ResultSet rows = q.executeQuery()) {
        fallback = rows.next() ? rows.getLong(1) : null;
      }
    }
    if (fallback == null) {
      try (PreparedStatement insert =
          db.prepareStatement(
              "INSERT INTO trophy_art (storage_key, image_url, created_at, updated_at) VALUES ('rr-fallback-v1', '/images/trophy/fallback.png', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
              Statement.RETURN_GENERATED_KEYS)) {
        insert.executeUpdate();
        try (ResultSet keys = insert.getGeneratedKeys()) {
          keys.next();
          fallback = keys.getLong(1);
        }
      }
    }
    int order;
    try (Statement q = db.createStatement();
        ResultSet row =
            q.executeQuery(
                "SELECT COALESCE(MAX(display_order), 0) + 1 FROM trophy_catalog WHERE is_default_template = 1")) {
      row.next();
      order = row.getInt(1);
    }
    for (var definition : RoundRobinTrophyTemplates.definitions()) {
      Long art = fallback, badge = fallback;
      try (PreparedStatement q =
          db.prepareStatement(
              "SELECT art_id, badge_art_id FROM trophy_catalog WHERE title = ? ORDER BY CASE WHEN season_id IS NULL THEN 0 ELSE 1 END, id LIMIT 1")) {
        q.setString(1, definition.artSource());
        try (ResultSet rows = q.executeQuery()) {
          if (rows.next()) {
            long image = rows.getLong(1);
            if (!rows.wasNull()) art = image;
            long badgeImage = rows.getLong(2);
            badge = rows.wasNull() ? art : badgeImage;
          }
        }
      }
      try (PreparedStatement insert =
          db.prepareStatement(
              """
          INSERT INTO trophy_catalog
            (title, summary, unlock_condition, unlock_expression, rarity, slug, art_id, badge_art_id,
             ai_provider, generation_seed, is_limited, is_repeatable, evaluation_scope,
             is_default_template, is_round_robin_trophy, story_mode_tracker, badge_selectable_by_all,
             display_order, created_at, updated_at)
          VALUES (?, 'Awarded for a finished round robin.', ?, ?, ?, ?, ?, ?, 'fallback', ?,
                  0, 1, 'USER', 1, 1, 0, 0, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
          """)) {
        insert.setString(1, definition.title());
        insert.setString(2, definition.condition());
        insert.setString(3, definition.expression());
        insert.setString(4, definition.rarity());
        insert.setString(5, definition.slug());
        insert.setLong(6, art);
        insert.setLong(7, badge);
        insert.setString(8, "round-robin-v1:" + definition.slug());
        insert.setInt(9, order++);
        insert.executeUpdate();
      }
    }
  }
}
