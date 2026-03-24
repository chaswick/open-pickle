package com.w3llspring.fhpb.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.w3llspring.fhpb.web.controller.meetups.MeetupsEmailUnsubscribeController;
import com.w3llspring.fhpb.web.service.meetups.MeetupsEmailDigestService;
import com.w3llspring.fhpb.web.service.meetups.MeetupsEmailLinkSigner;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

class MeetupsEmailUnsubscribeControllerTest {

  @Test
  void getUnsubscribeShowsConfirmationWithoutMutatingPreference() {
    TrackingMeetupsEmailDigestService digests = new TrackingMeetupsEmailDigestService();
    MeetupsEmailLinkSigner signer = new MeetupsEmailLinkSigner("secret");
    MeetupsEmailUnsubscribeController controller =
        new MeetupsEmailUnsubscribeController(digests, signer);
    String token = signer.signUnsubscribe(42L, Instant.now().plusSeconds(300), "abc123");
    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.unsubscribe(token, model);

    assertThat(view).isEqualTo("public/unsubscribe_confirm");
    assertThat(model.get("token")).isEqualTo(token);
    assertThat(digests.recordedUserId).isNull();
  }

  @Test
  void postUnsubscribeConsumesToken() {
    TrackingMeetupsEmailDigestService digests = new TrackingMeetupsEmailDigestService();
    MeetupsEmailLinkSigner signer = new MeetupsEmailLinkSigner("secret");
    MeetupsEmailUnsubscribeController controller =
        new MeetupsEmailUnsubscribeController(digests, signer);
    String token = signer.signUnsubscribe(43L, Instant.now().plusSeconds(300), "xyz789");
    ExtendedModelMap model = new ExtendedModelMap();

    String view = controller.unsubscribePost(token, model);

    assertThat(view).isEqualTo("public/unsubscribe_success");
    assertThat(model.get("success")).isEqualTo(true);
    assertThat(digests.recordedUserId).isEqualTo(43L);
    assertThat(digests.recordedOptIn).isFalse();
  }

  private static final class TrackingMeetupsEmailDigestService extends MeetupsEmailDigestService {
    private Long recordedUserId;
    private Boolean recordedOptIn;

    private TrackingMeetupsEmailDigestService() {
      super(null, null, null, null, null, null, null, null, new MeetupsEmailLinkSigner("secret"));
    }

    @Override
    public void recordOptIn(Long userId, boolean optIn) {
      recordedUserId = userId;
      recordedOptIn = optIn;
    }
  }
}
