package com.aibook.service.crawler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Minimal RFC 9309 robots.txt evaluator for the crawler's stable product token. */
final class CrawlerRobotsPolicy {
    static final CrawlerRobotsPolicy ALLOW_ALL = new CrawlerRobotsPolicy(List.of());
    static final CrawlerRobotsPolicy DISALLOW_ALL = new CrawlerRobotsPolicy(
            List.of(new Rule(false, Pattern.compile("^/.*"), 1)));

    private final List<Rule> rules;

    private CrawlerRobotsPolicy(List<Rule> rules) {
        this.rules = rules;
    }

    static CrawlerRobotsPolicy parse(String content, String productToken) {
        List<Group> groups = new ArrayList<>();
        Group current = new Group();
        boolean hasRules = false;
        for (String originalLine : content.replace("\r", "").split("\n")) {
            String line = stripComment(originalLine).trim();
            if (line.isEmpty()) {
                if (!current.agents.isEmpty() && hasRules) {
                    groups.add(current);
                    current = new Group();
                    hasRules = false;
                }
                continue;
            }
            int separator = line.indexOf(':');
            if (separator < 0) continue;
            String field = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separator + 1).trim();
            if (field.equals("user-agent")) {
                if (hasRules) {
                    groups.add(current);
                    current = new Group();
                    hasRules = false;
                }
                if (!value.isEmpty()) current.agents.add(value.toLowerCase(Locale.ROOT));
            } else if ((field.equals("allow") || field.equals("disallow"))
                    && !current.agents.isEmpty()) {
                hasRules = true;
                if (!value.isEmpty()) current.rules.add(rule(field.equals("allow"), value));
            }
        }
        if (!current.agents.isEmpty()) groups.add(current);

        String token = productToken.toLowerCase(Locale.ROOT);
        List<Group> specific = groups.stream()
                .filter(group -> group.agents.stream().anyMatch(agent -> !agent.equals("*")
                        && token.equals(agent)))
                .toList();
        List<Group> selected = specific.isEmpty()
                ? groups.stream().filter(group -> group.agents.contains("*")).toList()
                : specific;
        return new CrawlerRobotsPolicy(selected.stream().flatMap(group -> group.rules.stream()).toList());
    }

    boolean allows(URI uri) {
        String target = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) target += "?" + uri.getRawQuery();
        Rule selected = null;
        for (Rule rule : rules) {
            if (!rule.pattern.matcher(target).find()) continue;
            if (selected == null || rule.specificity > selected.specificity
                    || (rule.specificity == selected.specificity && rule.allow)) {
                selected = rule;
            }
        }
        return selected == null || selected.allow;
    }

    private static Rule rule(boolean allow, String path) {
        boolean endAnchored = path.endsWith("$");
        String value = endAnchored ? path.substring(0, path.length() - 1) : path;
        StringBuilder regex = new StringBuilder("^");
        for (String part : value.split("\\*", -1)) {
            if (regex.length() > 1) regex.append(".*");
            regex.append(Pattern.quote(part));
        }
        if (endAnchored) regex.append('$');
        int specificity = value.replace("*", "").length();
        return new Rule(allow, Pattern.compile(regex.toString()), specificity);
    }

    private static String stripComment(String line) {
        int comment = line.indexOf('#');
        return comment < 0 ? line : line.substring(0, comment);
    }

    private static final class Group {
        private final List<String> agents = new ArrayList<>();
        private final List<Rule> rules = new ArrayList<>();
    }

    private record Rule(boolean allow, Pattern pattern, int specificity) { }
}
