# OPDS End-to-End Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make OPDS browse, search, sync, and download work end to end across the Spring Boot backend and Android client.

**Architecture:** Keep the existing `/opds` backend surface and Android OPDS stack. Harden OPDS 1.2 first because it is the compatibility target for KOReader, Moon+ Reader, Librera, and the current Android parser; keep OPDS 2.0 available with correct JSON response metadata. Use TDD for each behavior and avoid unrelated UI redesign.

**Tech Stack:** Java 21, Spring Boot 3.2, Spring Security, JUnit 5, Mockito, Kotlin, Android Gradle modules, kotlin.test, kotlinx-coroutines-test.

## Global Constraints

- Read `/Users/beibei/aiprojects/ai-book/requires_1.md` before implementation.
- Preserve existing user work in the dirty Android tree; do not revert unrelated changes.
- Backend OPDS must support Basic Auth.
- Android button pressed states must not use shadow or press projection effects.
- Git commit messages must be Chinese and use `<类型>: <描述>`.
- Follow TDD: write the failing test, verify failure, implement, verify pass.
- Scope excludes WebDAV, KOReader progress sync, annotations sync, and a new server-side OPDS source management UI.

---

## File Structure

- Modify: `/Users/beibei/aiprojects/ai-book/backend/src/main/java/com/aibook/service/OpdsService.java`
  - Responsibility: OPDS 1.2 Atom XML, OpenSearch XML, feed links, pagination, XML escaping.
- Modify: `/Users/beibei/aiprojects/ai-book/backend/src/main/java/com/aibook/service/Opds2Service.java`
  - Responsibility: OPDS 2.0 JSON catalog and publication objects.
- Modify: `/Users/beibei/aiprojects/ai-book/backend/src/main/java/com/aibook/controller/OpdsController.java`
  - Responsibility: OPDS response content types, OpenSearch endpoint, download headers.
- Create: `/Users/beibei/aiprojects/ai-book/backend/src/test/java/com/aibook/service/OpdsServiceTest.java`
  - Responsibility: fast unit coverage for Atom XML behavior.
- Create: `/Users/beibei/aiprojects/ai-book/backend/src/test/java/com/aibook/controller/OpdsControllerTest.java`
  - Responsibility: MVC coverage for Basic Auth, content type, OpenSearch, and download headers.
- Modify: `/Users/beibei/aiprojects/ai-book/android/core/network/src/main/kotlin/com/aibook/android/core/network/opds/OpdsFeedParser.kt`
  - Responsibility: parse OPDS 1.2 fields needed by browsing and downloads.
- Modify: `/Users/beibei/aiprojects/ai-book/android/core/network/src/main/kotlin/com/aibook/android/core/network/opds/OpdsRequestFactory.kt`
  - Responsibility: resolve OPDS links and generate Basic Auth headers.
- Modify: `/Users/beibei/aiprojects/ai-book/android/core/network/src/main/kotlin/com/aibook/android/core/network/opds/OpdsDownloadNamer.kt`
  - Responsibility: generate stable local filenames from OPDS entries.
- Modify: `/Users/beibei/aiprojects/ai-book/android/app/src/main/kotlin/com/aibook/android/feature/opds/OpdsSyncCollector.kt`
  - Responsibility: recursive catalog sync guardrails.
- Modify existing Android tests under:
  - `/Users/beibei/aiprojects/ai-book/android/core/network/src/test/kotlin/com/aibook/android/core/network/opds/`
  - `/Users/beibei/aiprojects/ai-book/android/app/src/test/kotlin/com/aibook/android/feature/opds/OpdsSyncCollectorTest.kt`

---

### Task 1: Backend OPDS 1.2 Feed Compatibility

**Files:**
- Modify: `/Users/beibei/aiprojects/ai-book/backend/src/main/java/com/aibook/service/OpdsService.java`
- Create: `/Users/beibei/aiprojects/ai-book/backend/src/test/java/com/aibook/service/OpdsServiceTest.java`

**Interfaces:**
- Consumes: `BookRepository`, `Book`, `User`, `MimeTypeUtil.getContentType(String format)`.
- Produces:
  - `String OpdsService.getRootCatalog(User user)`
  - `String OpdsService.getBooksCatalog(User user, int page)`
  - `String OpdsService.searchBooks(User user, String query, int page)`
  - `String OpdsService.getSearchDescription()`

- [ ] **Step 1: Create the failing OPDS service test**

Create `/Users/beibei/aiprojects/ai-book/backend/src/test/java/com/aibook/service/OpdsServiceTest.java` with:

```java
package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class OpdsServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Test
    void rootCatalogIncludesSelfStartAndSearchLinks() {
        OpdsService service = new OpdsService(bookRepository);

        String xml = service.getRootCatalog(user());

        assertThat(xml).contains("rel=\"self\"");
        assertThat(xml).contains("href=\"/opds\"");
        assertThat(xml).contains("rel=\"start\"");
        assertThat(xml).contains("rel=\"search\"");
        assertThat(xml).contains("href=\"/opds/search.xml\"");
        assertThat(xml).contains("rel=\"subsection\"");
    }

    @Test
    void booksFeedEscapesMetadataAndIncludesAcquisitionAndCoverLinks() {
        Book book = book("三体 & 黑暗森林", "刘<慈欣>", "epub");
        book.setDescription("文明 > 危机");
        book.setCoverUrl("/api/covers/1.jpg");
        when(bookRepository.findByUser(eq(user()), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(book), PageRequest.of(0, 50), 80));
        OpdsService service = new OpdsService(bookRepository);

        String xml = service.getBooksCatalog(user(), 0);

        assertThat(xml).contains("三体 &amp; 黑暗森林");
        assertThat(xml).contains("刘&lt;慈欣&gt;");
        assertThat(xml).contains("文明 &gt; 危机");
        assertThat(xml).contains("rel=\"self\"");
        assertThat(xml).contains("rel=\"next\"");
        assertThat(xml).contains("type=\"application/epub+zip\"");
        assertThat(xml).contains("rel=\"http://opds-spec.org/acquisition/open-access\"");
        assertThat(xml).contains("rel=\"http://opds-spec.org/image\"");
        assertThat(xml).contains("rel=\"http://opds-spec.org/image/thumbnail\"");
    }

    @Test
    void searchPaginationKeepsQueryParameter() {
        when(bookRepository.searchByKeyword(eq(user()), eq("三体"), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(book("三体", "刘慈欣", "epub")), PageRequest.of(1, 50), 120));
        OpdsService service = new OpdsService(bookRepository);

        String xml = service.searchBooks(user(), "三体", 1);

        assertThat(xml).contains("href=\"/opds/search?query=%E4%B8%89%E4%BD%93&amp;page=1\"");
        assertThat(xml).contains("href=\"/opds/search?query=%E4%B8%89%E4%BD%93&amp;page=2\"");
        assertThat(xml).contains("href=\"/opds/search?query=%E4%B8%89%E4%BD%93&amp;page=0\"");
    }

    @Test
    void searchDescriptionExposesOpenSearchTemplate() {
        OpdsService service = new OpdsService(bookRepository);

        String xml = service.getSearchDescription();

        assertThat(xml).contains("<OpenSearchDescription");
        assertThat(xml).contains("template=\"/opds/search?query={searchTerms}\"");
        assertThat(xml).contains("application/atom+xml;profile=opds-catalog");
    }

    private User user() {
        return User.builder().id(1L).username("reader").build();
    }

    private Book book(String title, String author, String format) {
        return Book.builder()
            .id(10L)
            .title(title)
            .author(author)
            .format(format)
            .filePath("/tmp/book." + format)
            .user(user())
            .createdAt(LocalDateTime.of(2026, 7, 7, 12, 0))
            .updatedAt(LocalDateTime.of(2026, 7, 7, 12, 30))
            .build();
    }
}
```

- [ ] **Step 2: Run the new test and verify it fails**

Run:

```bash
cd /Users/beibei/aiprojects/ai-book/backend
mvn -Dtest=OpdsServiceTest test
```

Expected: FAIL because `getSearchDescription()` does not exist and root/search/self/thumbnail links are missing.

- [ ] **Step 3: Implement OPDS 1.2 compatibility**

In `/Users/beibei/aiprojects/ai-book/backend/src/main/java/com/aibook/service/OpdsService.java`:

Add imports:

```java
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
```

Add constants:

```java
private static final String OPDS_TYPE = "application/atom+xml;profile=opds-catalog";
```

Add `getSearchDescription()`:

```java
public String getSearchDescription() {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<OpenSearchDescription xmlns=\"http://a9.com/-/spec/opensearch/1.1/\">\n"
        + "  <ShortName>汗牛充栋</ShortName>\n"
        + "  <Description>搜索私人书库</Description>\n"
        + "  <InputEncoding>UTF-8</InputEncoding>\n"
        + "  <OutputEncoding>UTF-8</OutputEncoding>\n"
        + "  <Url type=\"" + OPDS_TYPE + "\" template=\"/opds/search?query={searchTerms}\"/>\n"
        + "</OpenSearchDescription>";
}
```

Update root feed header links after `<author>`:

```java
xml.append("  <link rel=\"self\" type=\"").append(OPDS_TYPE).append("\" href=\"/opds\"/>\n");
xml.append("  <link rel=\"start\" type=\"").append(OPDS_TYPE).append("\" href=\"/opds\"/>\n");
xml.append("  <link rel=\"search\" type=\"application/opensearchdescription+xml\" href=\"/opds/search.xml\" title=\"搜索\"/>\n");
```

Update `searchBooks` to preserve query:

```java
public String searchBooks(User user, String query, int page) {
    Page<Book> books = bookRepository.searchByKeyword(user, query, PageRequest.of(page, PAGE_SIZE));
    String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
    return buildBooksFeed(books, "搜索: " + query, "urn:aibook:search:" + query,
            "/opds/search?query=" + encodedQuery, page);
}
```

Update `buildBooksFeed` link building so `self`, `next`, and `prev` append `&page=` when `basePath` already has a query string:

```java
String pageSeparator = basePath.contains("?") ? "&amp;page=" : "?page=";
xml.append("  <link rel=\"self\" type=\"").append(OPDS_TYPE).append("\" href=\"")
   .append(basePath).append(pageSeparator).append(currentPage).append("\"/>\n");
xml.append("  <link rel=\"start\" type=\"").append(OPDS_TYPE).append("\" href=\"/opds\"/>\n");
if (page.hasNext()) {
    xml.append("  <link rel=\"next\" type=\"").append(OPDS_TYPE).append("\" href=\"")
       .append(basePath).append(pageSeparator).append(currentPage + 1).append("\"/>\n");
}
if (currentPage > 0) {
    xml.append("  <link rel=\"prev\" type=\"").append(OPDS_TYPE).append("\" href=\"")
       .append(basePath).append(pageSeparator).append(currentPage - 1).append("\"/>\n");
}
```

For cover links, emit both image and thumbnail:

```java
if (book.getCoverUrl() != null) {
    String coverUrl = escapeXml(book.getCoverUrl());
    xml.append("    <link rel=\"http://opds-spec.org/image\" type=\"image/jpeg\" href=\"").append(coverUrl).append("\"/>\n");
    xml.append("    <link rel=\"http://opds-spec.org/image/thumbnail\" type=\"image/jpeg\" href=\"").append(coverUrl).append("\"/>\n");
}
```

- [ ] **Step 4: Run the backend service test and verify it passes**

Run:

```bash
cd /Users/beibei/aiprojects/ai-book/backend
mvn -Dtest=OpdsServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit Task 1**

```bash
git -C /Users/beibei/aiprojects/ai-book add backend/src/main/java/com/aibook/service/OpdsService.java backend/src/test/java/com/aibook/service/OpdsServiceTest.java
git -C /Users/beibei/aiprojects/ai-book commit -m "feat: 完善 OPDS 目录兼容性"
```

---

### Task 2: Backend OPDS Controller, Content Types, Auth, and Download Headers

**Files:**
- Modify: `/Users/beibei/aiprojects/ai-book/backend/src/main/java/com/aibook/controller/OpdsController.java`
- Modify: `/Users/beibei/aiprojects/ai-book/backend/src/main/java/com/aibook/service/Opds2Service.java`
- Create: `/Users/beibei/aiprojects/ai-book/backend/src/test/java/com/aibook/controller/OpdsControllerTest.java`

**Interfaces:**
- Consumes:
  - `OpdsService.getSearchDescription()`
  - `Opds2Service.getRootCatalog(User user)`
  - `BookRepository.findById(Long id)`
- Produces:
  - `GET /opds/search.xml`
  - OPDS 2.0 responses with `application/opds+json`
  - Download response with encoded `Content-Disposition`

- [ ] **Step 1: Create the failing controller test**

Create `/Users/beibei/aiprojects/ai-book/backend/src/test/java/com/aibook/controller/OpdsControllerTest.java` with:

```java
package com.aibook.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.service.Opds2Service;
import com.aibook.service.OpdsService;
import com.aibook.service.UserService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OpdsController.class)
class OpdsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OpdsService opdsService;

    @MockBean
    private Opds2Service opds2Service;

    @MockBean
    private BookRepository bookRepository;

    @MockBean
    private UserService userService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @Test
    void opdsRequiresBasicAuth() throws Exception {
        mockMvc.perform(get("/opds"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("WWW-Authenticate", "Basic realm=\"Aibook\""));
    }

    @Test
    void searchDescriptionUsesOpenSearchContentType() throws Exception {
        mockAuthenticatedUser();
        when(opdsService.getSearchDescription()).thenReturn("<OpenSearchDescription/>");

        mockMvc.perform(get("/opds/search.xml").with(httpBasic("reader", "secret")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/opensearchdescription+xml"));
    }

    @Test
    void opds2RootUsesOpdsJsonContentType() throws Exception {
        mockAuthenticatedUser();
        when(opds2Service.getRootCatalog(any(User.class))).thenReturn(Map.of("metadata", Map.of("title", "书库")));

        mockMvc.perform(get("/opds/v2").with(httpBasic("reader", "secret")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/opds+json"));
    }

    @Test
    void downloadUsesMimeLengthAndEncodedFilename() throws Exception {
        mockAuthenticatedUser();
        Path file = Files.createTempFile("aibook-opds", ".epub");
        Files.writeString(file, "epub bytes");
        Book book = Book.builder()
            .id(7L)
            .title("三体")
            .format("epub")
            .filePath(file.toString())
            .user(User.builder().id(1L).username("reader").build())
            .build();
        when(bookRepository.findById(7L)).thenReturn(Optional.of(book));

        mockMvc.perform(get("/opds/books/7/download").with(httpBasic("reader", "secret")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/epub+zip"))
            .andExpect(header().string("Content-Length", "10"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''%E4%B8%89%E4%BD%93.epub")));
    }

    private void mockAuthenticatedUser() {
        org.springframework.security.core.userdetails.User details =
            org.springframework.security.core.userdetails.User.withUsername("reader")
                .password("$2a$10$abcdefghijklmnopqrstuuqQp6rbTDvxuRciZq09nIBnKXJFeen0a")
                .roles("USER")
                .build();
        when(userDetailsService.loadUserByUsername("reader")).thenReturn(details);
        when(passwordEncoder.matches("secret", details.getPassword())).thenReturn(true);
        when(userService.findByUsername("reader")).thenReturn(User.builder().id(1L).username("reader").build());
    }
}
```

- [ ] **Step 2: Run the controller test and verify it fails**

Run:

```bash
cd /Users/beibei/aiprojects/ai-book/backend
mvn -Dtest=OpdsControllerTest test
```

Expected: FAIL because `/opds/search.xml` is missing, OPDS 2.0 content type is default JSON, or the encoded filename header is missing.

- [ ] **Step 3: Implement controller behavior**

In `/Users/beibei/aiprojects/ai-book/backend/src/main/java/com/aibook/controller/OpdsController.java`, add constants:

```java
private static final String OPDS_ATOM_TYPE = "application/atom+xml;profile=opds-catalog";
private static final String OPDS_JSON_TYPE = "application/opds+json";
private static final String OPENSEARCH_TYPE = "application/opensearchdescription+xml";
```

Add endpoint:

```java
@GetMapping(value = "/search.xml")
public ResponseEntity<String> getSearchDescription(Authentication authentication) {
    getUserFromAuth(authentication);
    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, OPENSEARCH_TYPE)
            .body(opdsService.getSearchDescription());
}
```

Set OPDS 2.0 content type in every `/v2` response:

```java
return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, OPDS_JSON_TYPE)
        .body(opds2Service.getRootCatalog(user));
```

Repeat that response shape for `/v2/formats`, `/v2/books`, `/v2/formats/{format}`, `/v2/favorites`, `/v2/reading`, and `/v2/search`.

Add imports:

```java
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
```

Replace download filename header construction with:

```java
String filename = book.getTitle() + "." + book.getFormat().toLowerCase();
String fallbackFilename = "book-" + book.getId() + "." + book.getFormat().toLowerCase();
String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + fallbackFilename + "\"; filename*=UTF-8''" + encodedFilename)
        .header(HttpHeaders.CONTENT_TYPE, contentType)
        .header("Content-Length", String.valueOf(file.length()))
        .body(resource);
```

- [ ] **Step 4: Run controller tests**

Run:

```bash
cd /Users/beibei/aiprojects/ai-book/backend
mvn -Dtest=OpdsControllerTest test
```

Expected: PASS.

- [ ] **Step 5: Run backend OPDS tests together**

Run:

```bash
cd /Users/beibei/aiprojects/ai-book/backend
mvn -Dtest=OpdsServiceTest,OpdsControllerTest test
```

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

```bash
git -C /Users/beibei/aiprojects/ai-book add backend/src/main/java/com/aibook/controller/OpdsController.java backend/src/main/java/com/aibook/service/Opds2Service.java backend/src/test/java/com/aibook/controller/OpdsControllerTest.java
git -C /Users/beibei/aiprojects/ai-book commit -m "feat: 打通 OPDS 认证和下载响应"
```

---

### Task 3: Android OPDS Parser and URL Hardening

**Files:**
- Modify: `/Users/beibei/aiprojects/ai-book/android/core/network/src/main/kotlin/com/aibook/android/core/network/opds/OpdsFeedParser.kt`
- Modify: `/Users/beibei/aiprojects/ai-book/android/core/network/src/main/kotlin/com/aibook/android/core/network/opds/OpdsRequestFactory.kt`
- Modify: `/Users/beibei/aiprojects/ai-book/android/core/network/src/main/kotlin/com/aibook/android/core/network/opds/OpdsDownloadNamer.kt`
- Modify: `/Users/beibei/aiprojects/ai-book/android/core/network/src/test/kotlin/com/aibook/android/core/network/opds/OpdsFeedParserTest.kt`
- Modify: `/Users/beibei/aiprojects/ai-book/android/core/network/src/test/kotlin/com/aibook/android/core/network/opds/OpdsRequestFactoryTest.kt`
- Modify: `/Users/beibei/aiprojects/ai-book/android/core/network/src/test/kotlin/com/aibook/android/core/network/opds/OpdsDownloadNamerTest.kt`

**Interfaces:**
- Consumes:
  - `OpdsFeedParser.parse(xml: String): OpdsFeed`
  - `OpdsRequestFactory.resolveUrl(connection: OpdsConnection, href: String): String`
  - `OpdsDownloadNamer.fileName(entry: OpdsEntry): String`
- Produces parser behavior for `summary` or `content`, acquisition links, subsection links, cover/thumbnail links, absolute URLs, query URLs, and extension fallback.

- [ ] **Step 1: Add failing parser tests**

Append to `OpdsFeedParserTest`:

```kotlin
@Test
fun `uses content text when summary is absent and parses thumbnail cover`() {
    val xml = """
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>书库</title>
          <entry>
            <title>基地</title>
            <content type="text">银河帝国衰亡史。</content>
            <link rel="http://opds-spec.org/image/thumbnail" href="/covers/foundation.jpg" type="image/jpeg" />
          </entry>
        </feed>
    """.trimIndent()

    val feed = OpdsFeedParser().parse(xml)

    assertEquals("银河帝国衰亡史。", feed.entries.single().summary)
    assertEquals("/covers/foundation.jpg", feed.entries.single().coverLink?.href)
}

@Test
fun `prefers open access acquisition links`() {
    val xml = """
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>书库</title>
          <entry>
            <title>三体</title>
            <link rel="alternate" href="/books/1" />
            <link rel="http://opds-spec.org/acquisition/open-access" href="/opds/books/1/download" type="application/epub+zip" />
          </entry>
        </feed>
    """.trimIndent()

    val feed = OpdsFeedParser().parse(xml)

    assertEquals("/opds/books/1/download", feed.entries.single().acquisitionLink?.href)
    assertEquals("/books/1", feed.entries.single().alternateLink?.href)
}
```

- [ ] **Step 2: Add failing URL and filename tests**

Append to `OpdsRequestFactoryTest`:

```kotlin
@Test
fun `resolves absolute and query opds links`() {
    val connection = OpdsConnection(
        id = "home",
        name = "家庭书库",
        baseUrl = "http://192.168.1.100:8080/opds"
    )

    assertEquals(
        "https://cdn.example/books/1.epub",
        OpdsRequestFactory.resolveUrl(connection, "https://cdn.example/books/1.epub")
    )
    assertEquals(
        "http://192.168.1.100:8080/opds/search?query=%E4%B8%89%E4%BD%93&page=1",
        OpdsRequestFactory.resolveUrl(connection, "/opds/search?query=%E4%B8%89%E4%BD%93&page=1")
    )
}
```

Append to `OpdsDownloadNamerTest`:

```kotlin
@Test
fun `falls back to bin for unknown mime type`() {
    val entry = OpdsEntry(
        title = "无扩展名",
        acquisitionLink = OpdsLink(
            href = "/opds/books/9/download",
            type = "application/octet-stream"
        )
    )

    assertEquals("无扩展名.bin", OpdsDownloadNamer.fileName(entry))
}
```

- [ ] **Step 3: Run Android network OPDS tests and verify failure**

Run:

```bash
cd /Users/beibei/aiprojects/ai-book/android
./gradlew :core:network:test --tests '*opds*'
```

Expected: FAIL for parser summary/content behavior or filename fallback if missing.

- [ ] **Step 4: Implement parser and helper behavior**

In `OpdsFeedParser.kt`, update entry parsing:

```kotlin
val links = entry.links()
OpdsEntry(
    title = entry.directChildText("title").orEmpty(),
    author = entry.directChildren("author").firstOrNull()?.directChildText("name"),
    summary = entry.directChildText("summary") ?: entry.directChildText("content"),
    acquisitionLink = links.firstOrNull {
        it.rel == "http://opds-spec.org/acquisition" ||
            it.rel?.startsWith("http://opds-spec.org/acquisition/") == true
    },
    alternateLink = links.firstOrNull {
        it.rel == "alternate" ||
            it.rel == "subsection" ||
            it.rel == "http://opds-spec.org/sort/new" ||
            it.rel == "http://opds-spec.org/sort/popular"
    },
    coverLink = links.firstOrNull {
        it.rel == "http://opds-spec.org/image" ||
            it.rel == "http://opds-spec.org/image/thumbnail"
    }
)
```

In `OpdsRequestFactory.kt`, keep `URI.resolve`; ensure `baseUrl` gains exactly one trailing slash:

```kotlin
private fun String.ensureTrailingSlash(): String {
    return trim().let { value -> if (value.endsWith('/')) value else "$value/" }
}
```

In `OpdsDownloadNamer.kt`, map unknown MIME types to `bin`:

```kotlin
private fun extensionFromMimeType(type: String?): String {
    return when (type?.lowercase()) {
        "application/epub+zip" -> "epub"
        "application/pdf" -> "pdf"
        "text/plain" -> "txt"
        "application/x-mobipocket-ebook" -> "mobi"
        "application/msword" -> "doc"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "text/html" -> "html"
        else -> "bin"
    }
}
```

- [ ] **Step 5: Run Android network OPDS tests and verify pass**

Run:

```bash
cd /Users/beibei/aiprojects/ai-book/android
./gradlew :core:network:test --tests '*opds*'
```

Expected: PASS.

- [ ] **Step 6: Commit Task 3**

```bash
git -C /Users/beibei/aiprojects/ai-book add android/core/network/src/main/kotlin/com/aibook/android/core/network/opds/OpdsFeedParser.kt android/core/network/src/main/kotlin/com/aibook/android/core/network/opds/OpdsRequestFactory.kt android/core/network/src/main/kotlin/com/aibook/android/core/network/opds/OpdsDownloadNamer.kt android/core/network/src/test/kotlin/com/aibook/android/core/network/opds/OpdsFeedParserTest.kt android/core/network/src/test/kotlin/com/aibook/android/core/network/opds/OpdsRequestFactoryTest.kt android/core/network/src/test/kotlin/com/aibook/android/core/network/opds/OpdsDownloadNamerTest.kt
git -C /Users/beibei/aiprojects/ai-book commit -m "feat: 强化安卓 OPDS 解析和链接处理"
```

---

### Task 4: Android Sync Guardrails and End-to-End Download State

**Files:**
- Modify: `/Users/beibei/aiprojects/ai-book/android/app/src/main/kotlin/com/aibook/android/feature/opds/OpdsSyncCollector.kt`
- Modify: `/Users/beibei/aiprojects/ai-book/android/app/src/main/kotlin/com/aibook/android/feature/opds/OpdsViewModel.kt`
- Modify: `/Users/beibei/aiprojects/ai-book/android/app/src/test/kotlin/com/aibook/android/feature/opds/OpdsSyncCollectorTest.kt`

**Interfaces:**
- Consumes:
  - `OpdsSyncCollector(maxDepth: Int = DEFAULT_MAX_DEPTH, loadFeed: suspend (href: String?) -> OpdsFeed)`
  - `OpdsViewModel.syncConnection(connection: OpdsConnection)`
  - `OpdsViewModel.downloadEntry(entry: OpdsEntry)`
- Produces:
  - Sync does not revisit catalogs.
  - Sync stops at `maxDepth`.
  - Download clears `downloadingTitle` on every result path.

- [ ] **Step 1: Add failing max-depth sync test**

Append to `OpdsSyncCollectorTest`:

```kotlin
@Test
fun collectStopsAtMaxDepth() = runTest {
    val feeds = mapOf(
        null to OpdsFeed("root", listOf(catalog("一层", "/one"))),
        "/one" to OpdsFeed("one", listOf(catalog("二层", "/two"))),
        "/two" to OpdsFeed("two", listOf(book("太深的书", "/deep.epub")))
    )
    val collector = OpdsSyncCollector(maxDepth = 1) { href -> feeds.getValue(href) }

    val result = collector.collect()

    assertEquals(emptyList(), result.acquisitionEntries.map { it.title })
    assertEquals(2, result.catalogCount)
}
```

- [ ] **Step 2: Run app OPDS sync test and verify behavior**

Run:

```bash
cd /Users/beibei/aiprojects/ai-book/android
./gradlew :app:testDebugUnitTest --tests 'com.aibook.android.feature.opds.OpdsSyncCollectorTest'
```

Expected: FAIL if depth counting lets the deep book through, PASS if existing behavior already satisfies the guardrail. If it passes immediately, keep the test as regression coverage and do not change production code for this step.

- [ ] **Step 3: Implement minimal sync guardrail if the test fails**

In `OpdsSyncCollector.kt`, ensure child catalogs are counted but not loaded beyond `maxDepth`:

```kotlin
for (entry in feed.entries.filter { it.acquisitionLink == null }) {
    val childHref = entry.alternateLink?.href?.takeIf { it.isNotBlank() } ?: continue
    if (!visitedCatalogs.add(childHref)) continue
    catalogCount += 1
    if (depth < maxDepth) {
        visit(childHref, depth + 1)
    }
}
```

- [ ] **Step 4: Review download state handling**

In `OpdsViewModel.downloadEntry`, verify every `try` result and `catch` path sets:

```kotlin
downloadingTitle = null
```

If a path does not clear it, update that state copy to include `downloadingTitle = null`.

- [ ] **Step 5: Run app OPDS tests**

Run:

```bash
cd /Users/beibei/aiprojects/ai-book/android
./gradlew :app:testDebugUnitTest --tests '*opds*'
```

Expected: PASS.

- [ ] **Step 6: Commit Task 4**

```bash
git -C /Users/beibei/aiprojects/ai-book add android/app/src/main/kotlin/com/aibook/android/feature/opds/OpdsSyncCollector.kt android/app/src/main/kotlin/com/aibook/android/feature/opds/OpdsViewModel.kt android/app/src/test/kotlin/com/aibook/android/feature/opds/OpdsSyncCollectorTest.kt
git -C /Users/beibei/aiprojects/ai-book commit -m "feat: 完善安卓 OPDS 同步保护"
```

---

### Task 5: Final Verification

**Files:**
- No new files required.
- Read changed files only if a test failure points to them.

**Interfaces:**
- Consumes all tasks above.
- Produces verified backend and Android OPDS behavior.

- [ ] **Step 1: Run backend tests**

Run:

```bash
cd /Users/beibei/aiprojects/ai-book/backend
mvn test
```

Expected: PASS.

- [ ] **Step 2: Run Android OPDS-related tests**

Run:

```bash
cd /Users/beibei/aiprojects/ai-book/android
./gradlew :core:network:test --tests '*opds*'
./gradlew :app:testDebugUnitTest --tests '*opds*'
```

Expected: PASS.

- [ ] **Step 3: Inspect git diff**

Run:

```bash
git -C /Users/beibei/aiprojects/ai-book status --short
git -C /Users/beibei/aiprojects/ai-book diff --stat
```

Expected: Only intended OPDS files and existing unrelated user changes are present.

- [ ] **Step 4: Record verification result in the final response**

No verification-only commit is required. Report the exact backend and Android commands that passed, and mention any command that could not be run.
