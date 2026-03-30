package com.w3llspring.fhpb.web.config;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.filter.TermsAcceptanceEnforcementFilter;
import com.w3llspring.fhpb.web.service.auth.AuthenticatedUserService;
import com.w3llspring.fhpb.web.service.auth.CustomUserDetailsService;
import com.w3llspring.fhpb.web.service.user.UserAccountSettingsService;
import com.w3llspring.fhpb.web.util.ReturnToSanitizer;
import com.w3llspring.fhpb.web.util.SecurityRequestMatchers;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(WebSecurityConfig.class);

  private final UserRepository userRepository;

  public WebSecurityConfig(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Value("${fhpb.security.h2-console-access:false}")
  private boolean h2ConsoleAccessEnabled;

  @Value("${fhpb.security.remember-me.token-validity-seconds:2592000}")
  private int rememberMeTokenValiditySeconds;

  @Value("${fhpb.security.remember-me.key:}")
  private String rememberMeKey;

  @Bean
  public UserDetailsService userDetailsService() {
    return new CustomUserDetailsService();
  }

  @Bean
  public BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public DaoAuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService());
    authProvider.setPasswordEncoder(passwordEncoder());
    return authProvider;
  }

  @Bean
  public AuthenticationSuccessHandler termsAcknowledgementAuthenticationSuccessHandler(
      UserAccountSettingsService userAccountSettingsService,
      TermsAcceptancePolicy termsAcceptancePolicy) {
    TermsAcknowledgementAuthenticationSuccessHandler handler =
        new TermsAcknowledgementAuthenticationSuccessHandler(
            userAccountSettingsService, termsAcceptancePolicy, requestCache());
    handler.setDefaultTargetUrl("/home");
    handler.setAlwaysUseDefaultTargetUrl(false);
    return handler;
  }

  @Bean
  public TermsAwareAuthenticationFailureHandler termsAwareAuthenticationFailureHandler() {
    return new TermsAwareAuthenticationFailureHandler();
  }

  @Bean
  public RequestCache requestCache() {
    HttpSessionRequestCache cache = new HttpSessionRequestCache();
    cache.setRequestMatcher(
        request ->
            "GET".equalsIgnoreCase(request.getMethod())
                && StringUtils.hasText(
                    ReturnToSanitizer.sanitize(ReturnToSanitizer.toAppRelativePath(request))));
    return cache;
  }

  @Bean
  public TermsAcceptanceEnforcementFilter termsAcceptanceEnforcementFilter(
      AuthenticatedUserService authenticatedUserService,
      TermsAcceptancePolicy termsAcceptancePolicy) {
    return new TermsAcceptanceEnforcementFilter(authenticatedUserService, termsAcceptancePolicy);
  }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      AuthenticationSuccessHandler termsAcknowledgementAuthenticationSuccessHandler,
      TermsAwareAuthenticationFailureHandler termsAwareAuthenticationFailureHandler,
      TermsAcceptanceEnforcementFilter termsAcceptanceEnforcementFilter)
      throws Exception {
    http.headers(
            headers -> {
              if (h2ConsoleAccessEnabled) {
                headers.frameOptions(frameOptions -> frameOptions.sameOrigin());
              }
            })
        .csrf(
            csrf -> {
              csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse());
              if (h2ConsoleAccessEnabled) {
                csrf.ignoringRequestMatchers(SecurityRequestMatchers.path("/h2-console/**"));
              }
              csrf.ignoringRequestMatchers(
                  SecurityRequestMatchers.path(HttpMethod.POST, "/meetups/unsubscribe"));
            })
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers(
                      SecurityRequestMatchers.path("/css/**"),
                      SecurityRequestMatchers.path("/js/**"),
                  SecurityRequestMatchers.path("/images/**"),
                  SecurityRequestMatchers.path("/webjars/**"),
                  SecurityRequestMatchers.path("/error"),
                  SecurityRequestMatchers.path("/sw.js"),
                  SecurityRequestMatchers.path("/favicon.ico"),
                  SecurityRequestMatchers.path("/favicon*.png"),
                      SecurityRequestMatchers.path("/android-chrome-*.png"),
                      SecurityRequestMatchers.path("/apple-touch-icon.png"),
                      SecurityRequestMatchers.path("/apple-touch-icon-precomposed.png"),
                      SecurityRequestMatchers.path("/mstile-*.png"),
                      SecurityRequestMatchers.path("/safari-pinned-tab.svg"),
                      SecurityRequestMatchers.path("/browserconfig.xml"),
                      SecurityRequestMatchers.path("/site.webmanifest"),
                      SecurityRequestMatchers.path("/robots.txt"),
                      SecurityRequestMatchers.path("/.well-known/security.txt"))
                  .permitAll()
                  .requestMatchers(
                      SecurityRequestMatchers.path("/"),
                      SecurityRequestMatchers.path("/login"),
                      SecurityRequestMatchers.path("/register"),
                      SecurityRequestMatchers.path("/registration-success"),
                      SecurityRequestMatchers.path("/forgot-password"),
                      SecurityRequestMatchers.path("/reset-password"),
                      SecurityRequestMatchers.path("/meetups/unsubscribe"),
                      SecurityRequestMatchers.path("/terms"),
                      SecurityRequestMatchers.path("/privacy"))
                  .permitAll();

              if (h2ConsoleAccessEnabled) {
                auth.requestMatchers(SecurityRequestMatchers.path("/h2-console/**")).permitAll();
              }

              auth.anyRequest().authenticated();
            })
        .formLogin(
            form ->
                form.loginPage("/login")
                    .loginProcessingUrl("/login")
                    .usernameParameter("user")
                    .failureHandler(termsAwareAuthenticationFailureHandler)
                    .successHandler(termsAcknowledgementAuthenticationSuccessHandler)
                    .permitAll())
        .requestCache(cache -> cache.requestCache(requestCache()))
        .logout(logout -> logout.invalidateHttpSession(true).logoutSuccessUrl("/").permitAll())
        .anonymous(Customizer.withDefaults());

    http.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);
    http.addFilterAfter(termsAcceptanceEnforcementFilter, AnonymousAuthenticationFilter.class);

    if (StringUtils.hasText(rememberMeKey)) {
      http.rememberMe(
          r ->
              r.key(rememberMeKey.trim())
                  .userDetailsService(userDetailsService())
                  .tokenValiditySeconds(rememberMeTokenValiditySeconds));
    } else {
      log.warn("Remember-me is disabled because fhpb.security.remember-me.key is not configured");
    }

    return http.build();
  }

  @Bean
  public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
    AuthenticationManagerBuilder auth = http.getSharedObject(AuthenticationManagerBuilder.class);
    auth.authenticationProvider(authenticationProvider());
    return auth.build();
  }

  private static final class CsrfCookieFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
      CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
      if (csrfToken != null) {
        // Force the deferred token to load so CookieCsrfTokenRepository writes XSRF-TOKEN
        // on the initial page render instead of after the first failed POST.
        csrfToken.getToken();
      }
      filterChain.doFilter(request, response);
    }
  }
}
