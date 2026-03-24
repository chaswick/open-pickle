package com.w3llspring.fhpb.web.service.trophy;

import com.w3llspring.fhpb.web.db.TrophyArtRepository;
import com.w3llspring.fhpb.web.model.TrophyArt;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrophyArtService {

  private final TrophyArtRepository trophyArtRepository;

  public TrophyArtService(TrophyArtRepository trophyArtRepository) {
    this.trophyArtRepository = trophyArtRepository;
  }

  @Transactional
  public TrophyArt resolveOrCreate(String imageUrl, byte[] imageBytes) {
    String normalizedUrl = normalizeUrl(imageUrl);
    byte[] normalizedBytes = normalizeBytes(imageBytes);
    if (normalizedUrl == null && normalizedBytes == null) {
      return null;
    }

    String storageKey = storageKey(normalizedUrl, normalizedBytes);
    return trophyArtRepository
        .findByStorageKey(storageKey)
        .orElseGet(() -> createOrReuse(storageKey, normalizedUrl, normalizedBytes));
  }

  public String storageKey(String imageUrl, byte[] imageBytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      if (imageBytes != null && imageBytes.length > 0) {
        digest.update(imageBytes);
      }
      digest.update((byte) '|');
      if (imageUrl != null) {
        digest.update(imageUrl.getBytes(StandardCharsets.UTF_8));
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 digest unavailable", ex);
    }
  }

  private TrophyArt createOrReuse(String storageKey, String imageUrl, byte[] imageBytes) {
    TrophyArt art = new TrophyArt();
    art.setStorageKey(storageKey);
    art.setImageUrl(imageUrl);
    art.setImageBytes(imageBytes);
    try {
      return trophyArtRepository.save(art);
    } catch (DataIntegrityViolationException ex) {
      return trophyArtRepository.findByStorageKey(storageKey).orElseThrow(() -> ex);
    }
  }

  private String normalizeUrl(String imageUrl) {
    if (imageUrl == null || imageUrl.isBlank()) {
      return null;
    }
    return imageUrl.trim();
  }

  private byte[] normalizeBytes(byte[] imageBytes) {
    if (imageBytes == null || imageBytes.length == 0) {
      return null;
    }
    return Arrays.copyOf(imageBytes, imageBytes.length);
  }
}
