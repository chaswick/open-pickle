package com.w3llspring.fhpb.web.config;

import com.w3llspring.fhpb.web.util.UserPublicName;
import org.springframework.stereotype.Component;

@Component
public class UserPublicNameConfig {

  public UserPublicNameConfig(BrandingProperties brandingProperties) {
    UserPublicName.configureFallbackFromAppName(brandingProperties.getAppName());
  }
}
