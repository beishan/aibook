package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerSiteRule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class BookContentCleaner {
    private final ObjectMapper objectMapper;

    public String clean(Element source, CrawlerSiteRule rule) {
        Element body = source.clone();
        try {
            for (String selector : tokens(rule.getRemoveSelectors())) body.select(selector).remove();
            for (String xpath : lines(rule.getXpathRemoveSelectors())) body.selectXpath(xpath).remove();
            body.select("script,style,noscript,nav").remove();
            body.select("br").after("\n");
            body.select("p,div,li").append("\n");
            String content = Jsoup.parse(body.html()).wholeText();
            for (var entry : replacements(rule.getStringReplacementsJson()).entrySet())
                content = content.replace(entry.getKey(), entry.getValue());
            for (var entry : replacements(rule.getRegexReplacementsJson()).entrySet())
                content = content.replaceAll(entry.getKey(), entry.getValue());
            content = content.replace('\u00a0', ' ').replace("\r\n", "\n").replace('\r', '\n')
                    .replaceAll("(?m)^[ \\t]+|[ \\t]+$", "");
            if (Boolean.TRUE.equals(rule.getRemoveBlankLines())) content = content.replaceAll("\n{2,}", "\n");
            else content = content.replaceAll("\n{3,}", "\n\n");
            return content.trim();
        } catch (Exception exception) {
            throw new IllegalArgumentException("正文清洗配置包含无效的 Selector、XPath、JSON 或正则表达式", exception);
        }
    }

    private Map<String, String> replacements(String json) throws Exception {
        return json == null || json.isBlank() ? Map.of() : objectMapper.readValue(
                json, new TypeReference<LinkedHashMap<String, String>>() { });
    }
    private List<String> tokens(String value) { return value == null ? List.of() : Arrays.stream(value.split("[\\r\\n,]+")).map(String::trim).filter(s -> !s.isBlank()).toList(); }
    private List<String> lines(String value) { return value == null ? List.of() : Arrays.stream(value.split("[\\r\\n]+")).map(String::trim).filter(s -> !s.isBlank()).toList(); }
}
