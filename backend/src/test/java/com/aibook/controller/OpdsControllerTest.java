package com.aibook.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.User;
import com.aibook.repository.BookRepository;
import com.aibook.security.BasicAuthFilter;
import com.aibook.service.Opds2Service;
import com.aibook.service.OpdsService;
import com.aibook.service.UserService;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OpdsControllerTest {

    private MockMvc mockMvc;
    private StubOpdsService opdsService;
    private StubOpds2Service opds2Service;
    private StubBookLookup bookLookup;

    @BeforeEach
    void setUp() {
        opdsService = new StubOpdsService();
        opds2Service = new StubOpds2Service();
        bookLookup = new StubBookLookup();
        UserService userService = new StubUserService();
        BasicAuthFilter basicAuthFilter = new BasicAuthFilter(new StubUserDetailsService(), new PlainPasswordEncoder());
        OpdsController controller = new OpdsController(
            opdsService,
            opds2Service,
            bookLookup.repository(),
            userService
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(
                new MappingJackson2HttpMessageConverter(),
                new ResourceHttpMessageConverter(),
                new StringHttpMessageConverter()
            )
            .addFilters(basicAuthFilter)
            .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void opdsRequiresBasicAuth() throws Exception {
        mockMvc.perform(get("/opds"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("WWW-Authenticate", "Basic realm=\"Aibook\""));
    }

    @Test
    void searchDescriptionUsesOpenSearchContentType() throws Exception {
        opdsService.searchDescription = "<OpenSearchDescription/>";

        mockMvc.perform(get("/opds/search.xml")
                .with(httpBasic("reader", "secret"))
                .principal(authentication()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/opensearchdescription+xml"));
    }

    @Test
    void opds2RootUsesOpdsJsonContentType() throws Exception {
        opds2Service.root = Map.of("metadata", Map.of("title", "书库"));

        mockMvc.perform(get("/opds/v2")
                .with(httpBasic("reader", "secret"))
                .principal(authentication()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/opds+json"));
    }

    @Test
    void downloadUsesMimeLengthAndEncodedFilename() throws Exception {
        Path file = Files.createTempFile("aibook-opds", ".epub");
        Files.writeString(file, "epub bytes");
        Book book = Book.builder()
            .id(7L)
            .title("三体")
            .format("epub")
            .filePath(file.toString())
            .user(User.builder().id(1L).username("reader").build())
            .build();
        bookLookup.book = book;

        mockMvc.perform(get("/opds/books/7/download")
                .with(httpBasic("reader", "secret"))
                .principal(authentication()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/epub+zip"))
            .andExpect(header().string("Content-Length", "10"))
            .andExpect(header().string(
                "Content-Disposition",
                Matchers.containsString("filename*=UTF-8''%E4%B8%89%E4%BD%93.epub")
            ));
    }

    private static User reader() {
        return User.builder().id(1L).username("reader").build();
    }

    private static UsernamePasswordAuthenticationToken authentication() {
        return new UsernamePasswordAuthenticationToken("reader", null);
    }

    private static class StubOpdsService extends OpdsService {
        private String searchDescription = "";

        StubOpdsService() {
            super(null);
        }

        @Override
        public String getRootCatalog(User user) {
            return "<feed/>";
        }

        @Override
        public String getSearchDescription() {
            return searchDescription;
        }
    }

    private static class StubOpds2Service extends Opds2Service {
        private Map<String, Object> root = Map.of();

        StubOpds2Service() {
            super(null);
        }

        @Override
        public Map<String, Object> getRootCatalog(User user) {
            return root;
        }
    }

    private static class StubUserService extends UserService {
        StubUserService() {
            super(null);
        }

        @Override
        public UserDetails loadUserByUsername(String username) {
            return new StubUserDetailsService().loadUserByUsername(username);
        }

        @Override
        public User findByUsername(String username) {
            return reader();
        }
    }

    private static class StubUserDetailsService implements UserDetailsService {
        @Override
        public UserDetails loadUserByUsername(String username) {
            return org.springframework.security.core.userdetails.User.withUsername(username)
                .password("secret")
                .roles("USER")
                .build();
        }
    }

    private static class PlainPasswordEncoder implements PasswordEncoder {
        @Override
        public String encode(CharSequence rawPassword) {
            return rawPassword.toString();
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return rawPassword.toString().equals(encodedPassword);
        }
    }

    private static class StubBookLookup {
        private Book book;

        BookRepository repository() {
            return (BookRepository) Proxy.newProxyInstance(
                BookRepository.class.getClassLoader(),
                new Class<?>[] {BookRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        return Optional.ofNullable(book);
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
            );
        }
    }
}
