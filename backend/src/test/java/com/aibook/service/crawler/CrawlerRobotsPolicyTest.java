package com.aibook.service.crawler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;

class CrawlerRobotsPolicyTest {

    @Test
    void selectsSpecificAgentRulesInsteadOfWildcardRules() {
        CrawlerRobotsPolicy policy = CrawlerRobotsPolicy.parse("""
                User-agent: *
                Disallow: /

                User-agent: AiBookCrawler
                Allow: /novels/
                Disallow: /private/
                """, "AiBookCrawler");

        assertTrue(policy.allows(URI.create("https://novel.example/novels/1")));
        assertFalse(policy.allows(URI.create("https://novel.example/private/1")));
    }

    @Test
    void longestRuleWinsAndAllowWinsAnEqualLengthTie() {
        CrawlerRobotsPolicy policy = CrawlerRobotsPolicy.parse("""
                User-agent: *
                Disallow: /book/
                Allow: /book/public/
                Disallow: /download$
                Allow: /download$
                """, "AiBookCrawler");

        assertTrue(policy.allows(URI.create("https://novel.example/book/public/1")));
        assertFalse(policy.allows(URI.create("https://novel.example/book/private/1")));
        assertTrue(policy.allows(URI.create("https://novel.example/download")));
        assertTrue(policy.allows(URI.create("https://novel.example/download/1")));
    }
}
