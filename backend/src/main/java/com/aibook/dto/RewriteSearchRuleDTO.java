package com.aibook.dto;

public record RewriteSearchRuleDTO(
        String name,
        String query,
        String replacement,
        String scope,
        Boolean matchCase,
        Boolean wholeWord,
        Boolean regex) { }
