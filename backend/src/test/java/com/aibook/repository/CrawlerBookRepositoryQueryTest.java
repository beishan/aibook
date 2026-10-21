package com.aibook.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.aibook.model.entity.CrawlerBook;
import com.aibook.model.entity.User;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

class CrawlerBookRepositoryQueryTest {
    @Test
    void blacklistQueryIncludesOwnerAndStatusWithoutExcludingUncrawledBooks() throws Exception {
        Method method = CrawlerBookRepository.class.getMethod("searchBlacklistBooks",
                User.class, CrawlerBook.DiscoveryStatus.class, String.class, Long.class, Pageable.class);
        Query query = method.getAnnotation(Query.class);
        assertThat(query.value()).contains("b.site.user = :user", "b.discoveryStatus = :status",
                ":siteId is null", "b.bookName", "b.author", "b.site.siteName")
                .doesNotContain("crawlStatus");
        assertThat(namedParameters(query.value())).containsExactlyInAnyOrder(
                "user", "status", "keyword", "siteId");
    }


    private Query managedBookQuery() throws NoSuchMethodException {
        Method method = CrawlerBookRepository.class.getMethod("searchManagedBooks",
                User.class, CrawlerBook.DiscoveryStatus.class, CrawlerBook.CrawlStatus.class,
                String.class, Long.class, CrawlerBook.CrawlStatus.class,
                CrawlerBook.ImportStatus.class, boolean.class, String.class, Integer.class,
                Collection.class, Pageable.class);
        Query query = method.getAnnotation(Query.class);
        Set<String> declaredParameters = Arrays.stream(method.getParameters())
                .filter(parameter -> parameter.isAnnotationPresent(Param.class))
                .map(parameter -> parameter.getAnnotation(Param.class).value())
                .collect(Collectors.toSet());
        assertThat(namedParameters(query.value())).isEqualTo(declaredParameters);
        assertThat(namedParameters(query.countQuery())).isEqualTo(declaredParameters);
        return query;
    }

    @Test
    void failedImportUpdateIsAccountScopedAndDoesNotUnlinkLibraryBook() throws Exception {
        Method method = CrawlerBookRepository.class.getMethod("updateImportFailure", Long.class,
                User.class, CrawlerBook.ImportStatus.class);
        assertThat(method.getAnnotation(Query.class).value())
                .contains("b.importStatus = :status", "b.id = :id", "b.site.user = :user")
                .doesNotContain("libraryBook", "crawlStatus");
        assertThat(method.getAnnotation(org.springframework.transaction.annotation.Transactional.class)
                .propagation()).isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW);
    }

    @Test
    void chapterCountFilterIsStrictAndOptionalInContentAndCountQueries() throws Exception {
        Query query = managedBookQuery();
        String predicate = "(:chapterCountGreaterThan is null or b.chapterCount > :chapterCountGreaterThan)";
        assertThat(query.value()).contains(predicate).doesNotContain("b.chapterCount >=");
        assertThat(query.countQuery()).contains(predicate);
    }

    @Test
    void countQueryBindsAllParametersIncludingRunningStatusesWithoutSorting() throws Exception {
        Query query = managedBookQuery();
        assertThat(query.countQuery()).startsWith("select count(b)")
                .doesNotContain("order by")
                .contains("b.crawlStatus in :runningStatuses",
                        "b.crawlStatus not in :runningStatuses", "b.crawlStatus is null");
        assertThat(query.value()).contains(
                "order by case when b.crawlStatus in :runningStatuses then 0 else 1 end");
    }

    @Test
    void countQueryRetainsEveryAccountAndBookFilterFromContentQuery() throws Exception {
        Query query = managedBookQuery();
        String contentFilters = query.value().substring(query.value().indexOf("where"),
                query.value().indexOf("order by")).strip();
        String countFilters = query.countQuery().substring(query.countQuery().indexOf("where"),
                query.countQuery().indexOf("and (b.crawlStatus in :runningStatuses")).strip();
        assertThat(countFilters).isEqualTo(contentFilters);
    }

    @Test
    void discoveryAndManagedFilterQueriesParseWithPostgresDialect() throws Exception {
        var registry = new org.hibernate.boot.registry.StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", false)
                .build();
        try {
            var sources = new org.hibernate.boot.MetadataSources(registry);
            var scanner = new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(
                    jakarta.persistence.Entity.class));
            for (var entity : scanner.findCandidateComponents("com.aibook.model.entity")) {
                sources.addAnnotatedClass(Class.forName(entity.getBeanClassName()));
            }
            try (var factory = sources.buildMetadata().buildSessionFactory();
                    var session = factory.openSession()) {
                Method method = CrawlerBookRepository.class.getMethod("searchFilteredDiscoveredBooks",
                        User.class, CrawlerBook.DiscoveryStatus.class, CrawlerBook.CrawlStatus.class,
                        String.class, Long.class, boolean.class, String.class, String.class, Pageable.class);
                var query = session.createQuery(method.getAnnotation(Query.class).value(), CrawlerBook.class);
                assertThat(query.getParameters()).extracting(jakarta.persistence.Parameter::getName)
                        .containsExactlyInAnyOrder("user", "discoveryStatus", "crawlStatus", "keyword",
                                "siteId", "favoriteOnly", "category", "tag");
                Method candidates = CrawlerBookRepository.class.getMethod("searchDiscoveryCandidates",
                        User.class, CrawlerBook.DiscoveryStatus.class, CrawlerBook.CrawlStatus.class,
                        String.class, Long.class, Collection.class, Pageable.class);
                String candidateQuery = candidates.getAnnotation(Query.class).value();
                assertThat(candidateQuery).contains("b.id not in :excludedIds", "b.site.user = :user",
                        "b.discoveryStatus = :discoveryStatus", "b.crawlStatus = :crawlStatus");
                assertThat(session.createQuery(candidateQuery, CrawlerBook.class).getParameters())
                        .extracting(jakarta.persistence.Parameter::getName)
                        .containsExactlyInAnyOrder("user", "discoveryStatus", "crawlStatus", "keyword",
                                "siteId", "excludedIds");
                Method blacklist = CrawlerBookRepository.class.getMethod("searchBlacklistBooks",
                        User.class, CrawlerBook.DiscoveryStatus.class, String.class, Long.class,
                        Pageable.class);
                assertThat(session.createQuery(blacklist.getAnnotation(Query.class).value(),
                        CrawlerBook.class).getParameters())
                        .extracting(jakarta.persistence.Parameter::getName)
                        .containsExactlyInAnyOrder("user", "status", "keyword", "siteId");
                Query managed = managedBookQuery();
                assertThat(session.createQuery(managed.value(), CrawlerBook.class).getParameters())
                        .extracting(jakarta.persistence.Parameter::getName).contains("tag");
                assertThat(session.createQuery(managed.countQuery(), Long.class).getParameters())
                        .extracting(jakarta.persistence.Parameter::getName).contains("tag");
            }
        } finally {
            org.hibernate.boot.registry.StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private Set<String> namedParameters(String query) {
        String withoutLiterals = query.replaceAll("'(?:''|[^'])*'", "''");
        return Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)").matcher(withoutLiterals).results()
                .map(match -> match.group(1))
                .collect(Collectors.toSet());
    }
}
