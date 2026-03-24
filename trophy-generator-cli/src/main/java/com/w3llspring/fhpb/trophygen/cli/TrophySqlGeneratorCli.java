package com.w3llspring.fhpb.trophygen.cli;

import com.w3llspring.fhpb.trophygen.core.GeneratedTrophy;
import com.w3llspring.fhpb.trophygen.core.PromptLibrary;
import com.w3llspring.fhpb.trophygen.core.TrophyGenerationRequest;
import com.w3llspring.fhpb.trophygen.core.TrophyGenerator;
import com.w3llspring.fhpb.trophygen.core.TrophyGeneratorConfig;
import com.w3llspring.fhpb.trophygen.core.TrophyRarity;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public class TrophySqlGeneratorCli {

  public static void main(String[] args) throws Exception {
    Args parsed = Args.parse(args);
    Properties props = loadProperties(parsed.configPath);

    TrophyGeneratorConfig config = buildGeneratorConfig(props);
    PromptLibrary promptLibrary = buildPromptLibrary(props);
    TrophyGenerator generator =
        new TrophyGenerator(config, promptLibrary, parsed.fallbackImageRoot);

    if (parsed.promptRarity != null) {
      TrophyRarity rarity = parseRarity(parsed.promptRarity);
      if (rarity == null) {
        throw new IllegalArgumentException(
            "--prompt-rarity must be one of: common, uncommon, rare, epic, legendary");
      }
      String prompt =
          generator.generatePromptPreview(parsed.seasonName, parsed.seasonStart, rarity);
      System.out.println(prompt);
      return;
    }

    TrophyGenerationRequest request =
        new TrophyGenerationRequest(
            parsed.seasonName,
            parsed.seasonStart,
            parsed.seasonEnd,
            parsed.desiredCount > 0 ? parsed.desiredCount : config.getDesiredCount());

    List<GeneratedTrophy> trophies = generator.generateSeasonTrophies(request);

    String sql = buildSql(parsed.seasonId, parsed.seasonStart, trophies);
    writeOutput(parsed.outputPath, sql);
  }

  private static Properties loadProperties(Path path) throws IOException {
    Properties props = new Properties();
    try (FileInputStream stream = new FileInputStream(path.toFile())) {
      props.load(stream);
    }
    return props;
  }

  private static TrophyGeneratorConfig buildGeneratorConfig(Properties props) {
    TrophyGeneratorConfig config = new TrophyGeneratorConfig();
    config.setEnabled(Boolean.parseBoolean(props.getProperty("fhpb.ai.trophies.enabled", "false")));
    config.setApiKey(props.getProperty("fhpb.ai.trophies.api-key"));
    config.setModel(props.getProperty("fhpb.ai.trophies.model", config.getModel()));
    config.setImageSize(props.getProperty("fhpb.ai.trophies.image-size", config.getImageSize()));
    config.setBaseUrl(props.getProperty("fhpb.ai.trophies.base-url", config.getBaseUrl()));
    config.setPromptTemplate(
        props.getProperty("fhpb.ai.trophies.prompt-template", config.getPromptTemplate()));
    config.setQuality(props.getProperty("fhpb.ai.trophies.quality", config.getQuality()));
    config.setDesiredCount(
        parseInt(props.getProperty("fhpb.ai.trophies.desired-count"), config.getDesiredCount()));
    config.setRequestTimeoutSeconds(
        parseInt(
            props.getProperty("fhpb.ai.trophies.request-timeout-seconds"),
            config.getRequestTimeoutSeconds()));
    config.setDebugPrompts(
        Boolean.parseBoolean(props.getProperty("fhpb.ai.trophies.debug-prompts", "false")));
    return config;
  }

  private static PromptLibrary buildPromptLibrary(Properties props) {
    PromptLibrary library = new PromptLibrary();
    for (Map.Entry<Object, Object> entry : props.entrySet()) {
      String key = String.valueOf(entry.getKey());
      if (!key.startsWith("fhpb.ai.trophies.prompts.")) {
        continue;
      }
      String trimmed = key.substring("fhpb.ai.trophies.prompts.".length());
      int firstDot = trimmed.indexOf('.');
      if (firstDot <= 0) {
        continue;
      }
      String section = trimmed.substring(0, firstDot);
      String rest = trimmed.substring(firstDot + 1);
      int bracketIndex = rest.indexOf('[');
      if (bracketIndex <= 0) {
        continue;
      }
      String rarityToken = rest.substring(0, bracketIndex);
      int endBracket = rest.indexOf(']', bracketIndex);
      if (endBracket < 0) {
        continue;
      }
      int index = parseInt(rest.substring(bracketIndex + 1, endBracket), -1);
      if (index < 0) {
        continue;
      }
      TrophyRarity rarity = parseRarity(rarityToken);
      if (rarity == null) {
        continue;
      }
      String value = String.valueOf(entry.getValue());
      addPrompt(library, section, rarity, index, value);
    }
    return library;
  }

  private static TrophyRarity parseRarity(String token) {
    if (token == null) {
      return null;
    }
    switch (token.toLowerCase(Locale.ENGLISH)) {
      case "common":
        return TrophyRarity.COMMON;
      case "uncommon":
        return TrophyRarity.UNCOMMON;
      case "rare":
        return TrophyRarity.RARE;
      case "epic":
        return TrophyRarity.EPIC;
      case "legendary":
        return TrophyRarity.LEGENDARY;
      default:
        return null;
    }
  }

  private static void addPrompt(
      PromptLibrary library, String section, TrophyRarity rarity, int index, String value) {
    switch (section) {
      case "palette":
        setAtIndex(library.getPalette(), rarity, index, value);
        break;
      case "lighting":
        setAtIndex(library.getLighting(), rarity, index, value);
        break;
      case "material":
        setAtIndex(library.getMaterial(), rarity, index, value);
        break;
      case "border":
        setAtIndex(library.getBorder(), rarity, index, value);
        break;
      case "flourish":
        setAtIndex(library.getFlourish(), rarity, index, value);
        break;
      case "depth":
        setAtIndex(library.getDepth(), rarity, index, value);
        break;
      case "motif":
        setAtIndex(library.getMotif(), rarity, index, value);
        break;
      case "unlock-condition":
        setAtIndex(library.getUnlockCondition(), rarity, index, value);
        break;
      case "unlock-expression":
        setAtIndex(library.getUnlockExpression(), rarity, index, value);
        break;
      default:
        break;
    }
  }

  private static void setAtIndex(
      Map<TrophyRarity, List<String>> map, TrophyRarity rarity, int index, String value) {
    List<String> list = map.computeIfAbsent(rarity, key -> new ArrayList<>());
    while (list.size() <= index) {
      list.add(null);
    }
    list.set(index, value);
  }

  private static String buildSql(
      long seasonId, LocalDate seasonStart, List<GeneratedTrophy> trophies) {
    StringBuilder builder = new StringBuilder();
    builder.append("-- Trophy generation output\n");
    builder.append("-- season_id=" + seasonId + "\n\n");

    LinkedHashMap<String, GeneratedArt> arts = new LinkedHashMap<>();
    List<String> slugs = new ArrayList<>();
    int order = 0;
    for (GeneratedTrophy trophy : trophies) {
      String slug = buildSlug(seasonStart, trophy.getTitle(), order);
      slugs.add(slug);
      String artKey = storageKey(trophy.getImageUrl(), trophy.getImageBytes());
      if (artKey != null) {
        arts.putIfAbsent(
            artKey, new GeneratedArt(artKey, trophy.getImageUrl(), trophy.getImageBytes()));
      }
      order++;
    }

    for (GeneratedArt art : arts.values()) {
      builder.append("INSERT INTO trophy_art ");
      builder.append("(storage_key, image_url, image_bytes, created_at, updated_at)\n");
      builder.append("VALUES (");
      builder.append(sqlString(art.storageKey())).append(", ");
      builder.append(sqlString(art.imageUrl())).append(", ");
      String imageBytes = encodeBase64(art.imageBytes());
      if (imageBytes == null) {
        builder.append("NULL, ");
      } else {
        builder.append("FROM_BASE64(").append(sqlString(imageBytes)).append("), ");
      }
      builder.append("NOW(6), NOW(6))\n");
      builder.append("ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);\n\n");
    }

    order = 0;
    for (GeneratedTrophy trophy : trophies) {
      String slug = slugs.get(order);
      String artKey = storageKey(trophy.getImageUrl(), trophy.getImageBytes());
      String status = artKey != null ? "GENERATED" : "PENDING_IMAGE";

      builder.append("INSERT INTO trophy ");
      builder.append(
          "(season_id, title, summary, unlock_condition, unlock_expression, rarity, status, slug, art_id, ai_provider, generation_seed, prompt, is_limited, max_claims, display_order, generated_at, updated_at, regeneration_count)\n");
      builder.append("VALUES (");
      builder.append(seasonId).append(", ");
      builder.append(sqlString(trophy.getTitle())).append(", ");
      builder.append(sqlString(trophy.getSummary())).append(", ");
      builder.append(sqlString(trophy.getUnlockCondition())).append(", ");
      builder.append(sqlString(trophy.getUnlockExpression())).append(", ");
      builder.append(sqlString(trophy.getRarity().name())).append(", ");
      builder.append(sqlString(status)).append(", ");
      builder.append(sqlString(slug)).append(", ");
      if (artKey == null) {
        builder.append("NULL, ");
      } else {
        builder
            .append("(SELECT id FROM trophy_art WHERE storage_key = ")
            .append(sqlString(artKey))
            .append("), ");
      }
      builder.append(sqlString(trophy.getAiProvider())).append(", ");
      builder.append(sqlString(trophy.getGenerationSeed())).append(", ");
      builder.append(sqlString(trophy.getPrompt())).append(", ");
      builder.append(trophy.isLimited() ? "1" : "0").append(", ");
      if (trophy.getMaxClaims() == null) {
        builder.append("NULL, ");
      } else {
        builder.append(trophy.getMaxClaims()).append(", ");
      }
      builder.append(order).append(", ");
      builder.append("NOW(), NOW(), 0");
      builder.append(");\n\n");
      order++;
    }

    builder.append("-- rollback\n");
    builder
        .append("DELETE FROM trophy WHERE season_id = ")
        .append(seasonId)
        .append(" AND slug IN (");
    for (int i = 0; i < slugs.size(); i++) {
      if (i > 0) {
        builder.append(", ");
      }
      builder.append(sqlString(slugs.get(i)));
    }
    builder.append(");\n");

    if (!arts.isEmpty()) {
      builder.append("DELETE FROM trophy_art WHERE storage_key IN (");
      int artIndex = 0;
      for (String storageKey : arts.keySet()) {
        if (artIndex > 0) {
          builder.append(", ");
        }
        builder.append(sqlString(storageKey));
        artIndex++;
      }
      builder.append(") AND NOT EXISTS (SELECT 1 FROM trophy t WHERE t.art_id = trophy_art.id);\n");
    }

    return builder.toString();
  }

  private static String encodeBase64(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      return null;
    }
    return Base64.getEncoder().encodeToString(bytes);
  }

  private static String storageKey(String imageUrl, byte[] imageBytes) {
    if ((imageUrl == null || imageUrl.isBlank())
        && (imageBytes == null || imageBytes.length == 0)) {
      return null;
    }
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      if (imageBytes != null && imageBytes.length > 0) {
        digest.update(imageBytes);
      }
      digest.update((byte) '|');
      if (imageUrl != null && !imageUrl.isBlank()) {
        digest.update(imageUrl.trim().getBytes(StandardCharsets.UTF_8));
      }
      return java.util.HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 digest unavailable", ex);
    }
  }

  private static String sqlString(String value) {
    if (value == null) {
      return "NULL";
    }
    String normalized = value.replace("\r\n", "\n");
    String escaped = normalized.replace("'", "''");
    return "'" + escaped + "'";
  }

  private static String buildSlug(LocalDate seasonStart, String title, int order) {
    String base =
        (seasonStart != null ? seasonStart.toString() : "season")
            + "-"
            + (title == null ? "trophy" : title);
    String normalized =
        java.text.Normalizer.normalize(base, java.text.Normalizer.Form.NFD)
            .replaceAll("[^\\p{ASCII}]", "")
            .replaceAll("[^a-zA-Z0-9]+", "-")
            .replaceAll("-+", "-")
            .toLowerCase(Locale.ENGLISH)
            .replaceAll("^-", "")
            .replaceAll("-$", "");
    return normalized + "-" + order;
  }

  private static void writeOutput(Path outputPath, String sql) throws IOException {
    if (outputPath == null) {
      System.out.println(sql);
      return;
    }
    Files.createDirectories(outputPath.getParent());
    try (BufferedWriter writer =
        new BufferedWriter(
            new OutputStreamWriter(Files.newOutputStream(outputPath), StandardCharsets.UTF_8))) {
      writer.write(sql);
    }
  }

  private static int parseInt(String raw, int fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static class Args {
    private Path configPath;
    private Path outputPath;
    private long seasonId;
    private String seasonName;
    private LocalDate seasonStart;
    private LocalDate seasonEnd;
    private int desiredCount;
    private Path fallbackImageRoot;
    private String promptRarity;

    private static Args parse(String[] args) {
      Args parsed = new Args();
      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        switch (arg) {
          case "--config":
            parsed.configPath = Path.of(requireValue(args, ++i, "--config"));
            break;
          case "--output":
            parsed.outputPath = Path.of(requireValue(args, ++i, "--output"));
            break;
          case "--season-id":
            parsed.seasonId = Long.parseLong(requireValue(args, ++i, "--season-id"));
            break;
          case "--season-name":
            parsed.seasonName = requireValue(args, ++i, "--season-name");
            break;
          case "--season-start":
            parsed.seasonStart = LocalDate.parse(requireValue(args, ++i, "--season-start"));
            break;
          case "--season-end":
            parsed.seasonEnd = LocalDate.parse(requireValue(args, ++i, "--season-end"));
            break;
          case "--desired-count":
            parsed.desiredCount = Integer.parseInt(requireValue(args, ++i, "--desired-count"));
            break;
          case "--fallback-image-root":
            parsed.fallbackImageRoot = Path.of(requireValue(args, ++i, "--fallback-image-root"));
            break;
          case "--prompt-rarity":
            parsed.promptRarity = requireValue(args, ++i, "--prompt-rarity");
            break;
          default:
            throw new IllegalArgumentException("Unknown argument: " + arg);
        }
      }
      if (parsed.configPath == null) {
        throw new IllegalArgumentException("--config is required");
      }
      if (parsed.fallbackImageRoot == null) {
        parsed.fallbackImageRoot = Path.of("src/main/resources/static");
      }
      if (parsed.promptRarity != null) {
        if (parsed.seasonName == null || parsed.seasonName.isBlank()) {
          parsed.seasonName = "Season";
        }
        if (parsed.seasonStart == null) {
          parsed.seasonStart = LocalDate.now();
        }
        return parsed;
      }
      if (parsed.seasonId <= 0) {
        throw new IllegalArgumentException("--season-id is required");
      }
      if (parsed.seasonName == null || parsed.seasonName.isBlank()) {
        throw new IllegalArgumentException("--season-name is required");
      }
      if (parsed.seasonStart == null) {
        throw new IllegalArgumentException("--season-start is required");
      }
      if (parsed.seasonEnd == null) {
        throw new IllegalArgumentException("--season-end is required");
      }
      return parsed;
    }

    private static String requireValue(String[] args, int index, String flag) {
      if (index >= args.length) {
        throw new IllegalArgumentException("Missing value for " + flag);
      }
      return args[index];
    }
  }

  private record GeneratedArt(String storageKey, String imageUrl, byte[] imageBytes) {}
}
