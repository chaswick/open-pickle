package com.w3llspring.fhpb.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.w3llspring.fhpb.web.config.BrandingProperties;
import com.w3llspring.fhpb.web.config.OperatorProperties;
import com.w3llspring.fhpb.web.service.site.PublicSeoService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.HtmlUtils;

@SpringBootTest(
    properties = {
      "fhpb.public.base-url=https://public.example",
      "fhpb.operator.public-url=https://operator.example",
      "fhpb.branding.app-name=Court Club"
    })
@AutoConfigureMockMvc
class PublicSeoIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void landingMetadataMatchesTheRenderedTitleAndUsesOneCanonicalAcrossTrackingVariants()
      throws Exception {
    String html =
        mvc.perform(
                get("/")
                    .param("utm_source", "invite")
                    .with(
                        request -> {
                          request.setServerName("internal-proxy");
                          return request;
                        }))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("X-Robots-Tag"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String head = html.substring(0, html.indexOf("</head>"));
    var title = Pattern.compile("<title[^>]*>([^<]+)</title>").matcher(head);
    assertThat(title.find()).isTrue();
    String renderedTitle = HtmlUtils.htmlUnescape(title.group(1));
    assertThat(renderedTitle).contains("pickleball", "round robin scheduler", "Court Club");
    assertThat(meta(head, "property", "og:title")).isEqualTo(renderedTitle);
    assertThat(meta(head, "name", "twitter:title")).isEqualTo(renderedTitle);
    assertThat(meta(head, "name", "description")).contains("round robin schedules", "waitlists");
    assertThat(meta(head, "property", "og:description"))
        .isEqualTo(meta(head, "name", "description"));
    assertThat(meta(head, "property", "og:url")).isEqualTo("https://public.example/");
    assertThat(meta(head, "property", "og:image")).startsWith("https://public.example/");
    assertThat(head).contains("rel=\"canonical\" href=\"https://public.example/\"");
    assertThat(head)
        .doesNotContain("utm_source", "internal-proxy", "your-domain.example", "operator.example");
    assertThat(Pattern.compile("<meta\\b[^>]*name=\"description\"").matcher(head).results().count())
        .isEqualTo(1);
    assertThat(Pattern.compile("<h1\\b").matcher(html).results().count()).isEqualTo(1);
    var script =
        Pattern.compile(
                "<script[^>]*type=\"application/ld\\+json\"[^>]*>(.*?)</script>", Pattern.DOTALL)
            .matcher(head);
    assertThat(script.find()).isTrue();
    var data = json.readTree(script.group(1));
    assertThat(data.get("@type").asText()).isEqualTo("WebSite");
    assertThat(data.get("name").asText()).isEqualTo("Court Club");
    assertThat(data.get("url").asText()).isEqualTo("https://public.example/");
    assertThat(html).contains("schedule a pickleball session for later", "waitlist fill openings");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/terms", "/privacy"})
  void legalPagesHaveTheirOwnMetadataWithoutReturningPrivateNavigationParameters(String path)
      throws Exception {
    String html =
        mvc.perform(get(path).param("returnTo", "/groups/42?inviteCode=PRIVATE"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String head = html.substring(0, html.indexOf("</head>"));
    assertThat(head).contains("href=\"https://public.example" + path + "\"");
    assertThat(head).doesNotContain("PRIVATE", "returnTo", "inviteCode", "round robin schedules");
    assertThat(meta(head, "name", "description")).contains("Court Club");
    assertThat(meta(head, "name", "robots")).isEqualTo("index, follow");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"/login", "/register", "/forgot-password", "/recover-player", "/join/session"})
  void utilityAndInvitePagesAreNotIndexable(String path) throws Exception {
    String html =
        mvc.perform(get(path).param("inviteCode", "PRIVATE"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String head = html.substring(0, html.indexOf("</head>"));
    assertThat(meta(head, "name", "robots")).isEqualTo("noindex, nofollow");
    assertThat(head)
        .doesNotContain("rel=\"canonical\"", "property=\"og:url\"", "application/ld+json");
  }

  @Test
  void privateRedirectsCarryNoindexWhilePublicImagesAndStylesRemainCrawlable() throws Exception {
    mvc.perform(get("/groups/42"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
    mvc.perform(get("/css/site.css"))
        .andExpect(status().isOk())
        .andExpect(header().doesNotExist("X-Robots-Tag"));
    mvc.perform(get("/images/landing/op_logo.png"))
        .andExpect(status().isOk())
        .andExpect(header().doesNotExist("X-Robots-Tag"));
  }

  @Test
  void robotsAndSitemapAreAccessibleAnonymouslyAndOnlyAdvertisePublicCanonicalPages()
      throws Exception {
    String robots =
        mvc.perform(get("/robots.txt"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(robots).contains("Sitemap: https://public.example/sitemap.xml");
    assertThat(robots).doesNotContain("Disallow: /login", "Disallow: /css", "Disallow: /images");
    String xml =
        mvc.perform(get("/sitemap.xml"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/xml"))
            .andExpect(header().doesNotExist("X-Robots-Tag"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    var document =
        factory
            .newDocumentBuilder()
            .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    var locations = document.getElementsByTagName("loc");
    assertThat(locations.getLength()).isEqualTo(3);
    assertThat(locations.item(0).getTextContent()).isEqualTo("https://public.example/");
    assertThat(locations.item(1).getTextContent()).isEqualTo("https://public.example/terms");
    assertThat(locations.item(2).getTextContent()).isEqualTo("https://public.example/privacy");
    assertThat(xml).doesNotContain("/groups", "/account", "/login", "lastmod");
  }

  @Test
  void customizedSearchCopyStaysAlignedAcrossMetadataAndStructuredData() throws Exception {
    var branding = new BrandingProperties();
    branding.setAppName("Hosted Club");
    branding.setLandingSearchTitle("Plan games with your club");
    branding.setLandingMetaDescription("A private deployment's own pickleball description.");
    var service =
        new PublicSeoService(branding, new OperatorProperties(), json, "https://club.example");
    var metadata = service.describe(new MockHttpServletRequest("GET", "/"));
    assertThat(metadata.title()).isEqualTo("Plan games with your club | Hosted Club");
    assertThat(metadata.description()).isEqualTo(branding.getLandingMetaDescription());
    assertThat(json.readTree(metadata.structuredData()).get("description").asText())
        .isEqualTo(metadata.description());
  }

  @Test
  void structuredDataEscapesScriptTerminatorsWithoutChangingTheBrand() throws Exception {
    var branding = new BrandingProperties();
    branding.setAppName("Club </script><script>alert(1)</script>");
    var service =
        new PublicSeoService(branding, new OperatorProperties(), json, "https://public.example");
    var request = new MockHttpServletRequest("GET", "/");
    String data = service.describe(request).structuredData();
    assertThat(data).doesNotContain("</script>");
    assertThat(json.readTree(data).get("name").asText()).isEqualTo(branding.getAppName());
  }

  private String meta(String html, String attribute, String name) {
    var pattern =
        Pattern.compile(
            "<meta\\b(?=[^>]*"
                + attribute
                + "=\""
                + Pattern.quote(name)
                + "\")[^>]*\\bcontent=\"([^\"]*)\"[^>]*>");
    var match = pattern.matcher(html);
    assertThat(match.find()).as(name).isTrue();
    return HtmlUtils.htmlUnescape(match.group(1));
  }
}
