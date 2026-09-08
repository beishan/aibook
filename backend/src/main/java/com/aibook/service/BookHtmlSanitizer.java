package com.aibook.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

/** 将用户书籍中的 HTML 收敛为适合在同源阅读页嵌入的静态内容。 */
@Service
public class BookHtmlSanitizer {

    private final Cleaner cleaner = new Cleaner(Safelist.relaxed()
            .addTags("article", "section", "main", "figure", "figcaption", "details", "summary")
            .addAttributes(":all", "id", "class", "title")
            .addProtocols("img", "src", "data"));

    public String sanitize(String html) {
        Document source = Jsoup.parse(html == null ? "" : html);
        Document clean = cleaner.clean(source);
        clean.outputSettings().prettyPrint(false);
        clean.select("a[href]").attr("target", "_blank").attr("rel", "noopener noreferrer");
        clean.select("img[src]").forEach(image -> {
            String src = image.attr("src").trim();
            if (!src.regionMatches(true, 0, "data:image/", 0, "data:image/".length())) {
                image.removeAttr("src");
            }
        });
        return clean.body().html();
    }
}
