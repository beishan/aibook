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

    private Query managedBookQuery() throws NoSuchMethodException {
        Method method = CrawlerBookRepository.class.getMethod("searchManagedBooks",
                User.class, CrawlerBook.DiscoveryStatus.class, CrawlerBook.CrawlStatus.class,
                String.class, Long.class, CrawlerBook.CrawlStatus.class,
                CrawlerBook.ImportStatus.class, boolean.class, Collection.class, Pageable.class);
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

    private Set<String> namedParameters(String query) {
        return Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)").matcher(query).results()
                .map(match -> match.group(1))
                .collect(Collectors.toSet());
    }
}
