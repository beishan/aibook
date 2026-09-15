package com.aibook.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.aibook.model.entity.CrawlerTask;
import com.aibook.model.entity.User;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

class CrawlerTaskRepositoryQueryTest {

    private static final Pattern NAMED_PARAMETER = Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)");

    @Test
    void runningFirstCountQueryDeclaresEveryNamedParameter() throws NoSuchMethodException {
        Method method = CrawlerTaskRepository.class.getMethod(
                "findByUserRunningFirst", User.class, CrawlerTask.TaskStatus.class, Pageable.class);

        assertCountQueryParameters(method, Set.of("user", "runningStatus"));
    }

    @Test
    void runningFirstByTypeCountQueryDeclaresEveryNamedParameter() throws NoSuchMethodException {
        Method method = CrawlerTaskRepository.class.getMethod(
                "findByUserAndTypeRunningFirst",
                User.class,
                CrawlerTask.TaskType.class,
                CrawlerTask.TaskStatus.class,
                Pageable.class);

        assertCountQueryParameters(method, Set.of("user", "type", "runningStatus"));
    }

    private void assertCountQueryParameters(Method method, Set<String> expectedParameters) {
        Query query = method.getAnnotation(Query.class);

        assertEquals(expectedParameters, namedParameters(query.countQuery()));
        assertFalse(query.countQuery().toLowerCase().contains("order by"));
    }

    private Set<String> namedParameters(String query) {
        Set<String> parameters = new LinkedHashSet<>();
        Matcher matcher = NAMED_PARAMETER.matcher(query);
        while (matcher.find()) {
            parameters.add(matcher.group(1));
        }
        return parameters;
    }
}
