package com.aibook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates and renders the small, explicit subset of Tiptap JSON used by rewriting. */
@Component
@RequiredArgsConstructor
public class RewriteContentCodec {

    public record TextReplacement(String document, int matches) { }

    private static final Set<String> NODE_TYPES = Set.of("doc", "paragraph", "heading",
            "blockquote", "bulletList", "orderedList", "listItem", "horizontalRule",
            "text", "hardBreak");
    private static final Set<String> MARK_TYPES = Set.of(
            "bold", "italic", "underline", "strike", "code", "link");

    private final ObjectMapper objectMapper;

    public String normalizeDocument(String value) {
        try {
            JsonNode root = objectMapper.readTree(value);
            validateNode(root, "", 0, new int[] {0});
            return objectMapper.writeValueAsString(root);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw badRequest("富文本正文不是有效的 Tiptap JSON 文档");
        }
    }

    public String plainText(String value) {
        JsonNode root = read(value);
        if (!"doc".equals(root.path("type").asText())) return "";
        return childrenText(root, "\n\n");
    }

    public String markdown(String value) {
        JsonNode root = read(value);
        List<String> blocks = new ArrayList<>();
        for (JsonNode child : root.path("content")) {
            String block = markdownBlock(child);
            if (!block.isBlank()) blocks.add(block);
        }
        return String.join("\n\n", blocks);
    }

    public String markdownTitle(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return escapeMarkdown(normalized);
    }

    public boolean isDocument(String value) {
        try {
            return objectMapper.readTree(value).path("type").asText().equals("doc");
        } catch (Exception exception) {
            return false;
        }
    }

    public List<Map<String, String>> renderedBlocks(String value) {
        JsonNode root = read(value);
        List<Map<String, String>> blocks = new ArrayList<>();
        JsonNode children = root.path("content");
        for (JsonNode child : children) {
            String text = nodeText(child);
            String html = renderNode(child);
            if (!text.isBlank() || "horizontalRule".equals(child.path("type").asText())) {
                blocks.add(Map.of("text", text, "html", html));
            }
        }
        return blocks;
    }

    public TextReplacement replaceText(String value, Pattern pattern, String replacement) {
        JsonNode root = read(value).deepCopy();
        int matches = replaceTextNodes(root, pattern, replacement);
        try {
            return new TextReplacement(objectMapper.writeValueAsString(root), matches);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "富文本替换结果无法保存", exception);
        }
    }

    public String plainTextDocument(String value) {
        try {
            ObjectNode document = objectMapper.createObjectNode();
            document.put("type", "doc");
            ArrayNode paragraphs = document.putArray("content");
            String normalized = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
            String[] sourceParagraphs = normalized.split("\\n\\s*\\n+", -1);
            for (String sourceParagraph : sourceParagraphs) {
                ObjectNode paragraph = paragraphs.addObject();
                paragraph.put("type", "paragraph");
                ArrayNode inline = paragraph.putArray("content");
                String[] lines = sourceParagraph.split("\\n", -1);
                for (int index = 0; index < lines.length; index++) {
                    if (index > 0) inline.addObject().put("type", "hardBreak");
                    if (!lines[index].isEmpty()) inline.addObject()
                            .put("type", "text").put("text", lines[index]);
                }
            }
            return objectMapper.writeValueAsString(document);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "纯文本无法转换为富文本文档", exception);
        }
    }

    public String mergeDocuments(String first, int firstFormatVersion,
                                 String second, int secondFormatVersion) {
        JsonNode firstDocument = firstFormatVersion == 1 ? read(first)
                : read(plainTextDocument(first));
        JsonNode secondDocument = secondFormatVersion == 1 ? read(second)
                : read(plainTextDocument(second));
        ObjectNode merged = objectMapper.createObjectNode();
        merged.put("type", "doc");
        ArrayNode content = merged.putArray("content");
        firstDocument.path("content").forEach(node -> content.add(node.deepCopy()));
        secondDocument.path("content").forEach(node -> content.add(node.deepCopy()));
        try {
            return objectMapper.writeValueAsString(merged);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "合并富文本章节失败", exception);
        }
    }

    private int replaceTextNodes(JsonNode node, Pattern pattern, String replacement) {
        int count = 0;
        JsonNode content = node.path("content");
        if (content instanceof ArrayNode children) {
            for (int index = children.size() - 1; index >= 0; index--) {
                JsonNode child = children.get(index);
                if ("text".equals(child.path("type").asText())
                        && child instanceof ObjectNode object) {
                    String text = object.path("text").asText();
                    Matcher matcher = pattern.matcher(text);
                    int childMatches = 0;
                    while (matcher.find()) childMatches++;
                    String replaced = pattern.matcher(text).replaceAll(replacement);
                    count += childMatches;
                    if (replaced.isEmpty()) children.remove(index);
                    else object.put("text", replaced);
                } else {
                    count += replaceTextNodes(child, pattern, replacement);
                }
            }
        }
        return count;
    }

    private void validateNode(JsonNode node, String parentType, int depth, int[] count) {
        if (node == null || !node.isObject() || depth > 32 || ++count[0] > 100_000) {
            throw badRequest("富文本结构超出允许范围");
        }
        String type = node.path("type").asText("");
        if (!NODE_TYPES.contains(type)) throw badRequest("正文包含不支持的节点：" + type);
        Set<String> allowedFields = type.equals("text") ? Set.of("type", "text", "marks")
                : Set.of("type", "content", "attrs");
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            if (!allowedFields.contains(fields.next())) throw badRequest("富文本节点包含未知属性");
        }
        if ("doc".equals(type) && !parentType.isEmpty()) throw badRequest("文档根节点位置无效");
        if ("text".equals(type)) {
            if (!node.path("text").isTextual() || node.path("text").asText().length() > 1_000_000) {
                throw badRequest("文本节点内容无效");
            }
        } else if (!"horizontalRule".equals(type) && !"hardBreak".equals(type)) {
            if (!node.path("content").isArray()) {
                if (!Set.of("paragraph", "heading").contains(type)) {
                    throw badRequest("富文本节点缺少内容");
                }
            } else {
                for (JsonNode child : node.path("content")) {
                    validateNode(child, type, depth + 1, count);
                }
            }
        }
        validateAttrs(type, node.path("attrs"));
        if (node.has("marks")) validateMarks(node.path("marks"));
        validateParent(parentType, type);
    }

    private void validateParent(String parent, String child) {
        if (parent.isEmpty()) {
            if (!"doc".equals(child)) throw badRequest("富文本根节点必须是 doc");
            return;
        }
        boolean valid = switch (parent) {
            case "doc" -> Set.of("paragraph", "heading", "blockquote", "bulletList",
                    "orderedList", "horizontalRule").contains(child);
            case "paragraph", "heading" -> Set.of("text", "hardBreak").contains(child);
            case "blockquote" -> Set.of("paragraph", "heading", "bulletList",
                    "orderedList", "horizontalRule").contains(child);
            case "listItem" -> Set.of("paragraph", "bulletList", "orderedList").contains(child);
            case "bulletList", "orderedList" -> "listItem".equals(child);
            default -> false;
        };
        if (!valid) throw badRequest("富文本节点层级无效");
    }

    private void validateAttrs(String type, JsonNode attrs) {
        if (attrs.isMissingNode() || attrs.isNull()) return;
        if (!attrs.isObject()) throw badRequest("富文本节点属性无效");
        switch (type) {
            case "heading" -> {
                onlyFields(attrs, Set.of("level"));
                int level = attrs.path("level").asInt(2);
                if (level != 2 && level != 3) throw badRequest("标题层级仅支持二级和三级");
            }
            case "orderedList" -> {
                onlyFields(attrs, Set.of("start", "type"));
                if (attrs.has("start") && (attrs.path("start").asInt() < 1
                        || attrs.path("start").asInt() > 9999)) {
                    throw badRequest("有序列表起始编号无效");
                }
                String listType = attrs.path("type").asText("");
                if (!listType.isEmpty() && !Set.of("1", "a", "A", "i", "I").contains(listType)) {
                    throw badRequest("有序列表编号类型无效");
                }
            }
            case "text", "paragraph", "blockquote", "bulletList", "listItem",
                    "horizontalRule", "hardBreak", "doc" ->
                    throw badRequest("此类富文本节点不允许属性");
            default -> { }
        }
    }

    private void validateMarks(JsonNode marks) {
        if (!marks.isArray() || marks.size() > 8) throw badRequest("正文标记无效");
        for (JsonNode mark : marks) {
            String type = mark.path("type").asText("");
            if (!MARK_TYPES.contains(type)) throw badRequest("正文包含不支持的格式标记");
            if ("link".equals(type)) {
                onlyFields(mark, Set.of("type", "attrs"));
                JsonNode attrs = mark.path("attrs");
                if (!attrs.isObject()) throw badRequest("链接属性无效");
                onlyFields(attrs, Set.of("href"));
                String href = attrs.path("href").asText("");
                if (href.length() > 2048 || !safeHref(href)) throw badRequest("链接地址不安全");
            } else {
                onlyFields(mark, Set.of("type"));
                if (mark.has("attrs")) throw badRequest("此类格式标记不允许属性");
            }
        }
    }

    private void onlyFields(JsonNode node, Set<String> allowed) {
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            if (!allowed.contains(fields.next())) throw badRequest("富文本属性包含不允许的字段");
        }
    }

    private boolean safeHref(String href) {
        try {
            if (href.startsWith("#")) {
                return href.length() > 1 && href.matches("#[A-Za-z0-9_.:-]+$");
            }
            URI uri = URI.create(href);
            String scheme = uri.getScheme();
            if (scheme == null) return false;
            if ("mailto".equalsIgnoreCase(scheme)) return !uri.getSchemeSpecificPart().isBlank();
            return Set.of("http", "https").contains(scheme.toLowerCase())
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (Exception exception) {
            return false;
        }
    }

    private String renderNode(JsonNode node) {
        String type = node.path("type").asText();
        String children = renderChildren(node);
        return switch (type) {
            case "doc" -> children;
            case "paragraph" -> "<p>" + children + "</p>";
            case "heading" -> {
                int level = node.path("attrs").path("level").asInt(2);
                yield "<h" + level + ">" + children + "</h" + level + ">";
            }
            case "blockquote" -> "<blockquote>" + children + "</blockquote>";
            case "bulletList" -> "<ul>" + children + "</ul>";
            case "orderedList" -> {
                int start = node.path("attrs").path("start").asInt(1);
                yield (start == 1 ? "<ol>" : "<ol start=\"" + start + "\">")
                        + children + "</ol>";
            }
            case "listItem" -> "<li>" + children + "</li>";
            case "horizontalRule" -> "<hr>";
            case "hardBreak" -> "<br>";
            case "text" -> renderMarks(escape(node.path("text").asText()), node.path("marks"));
            default -> "";
        };
    }

    private String renderMarks(String value, JsonNode marks) {
        String rendered = value;
        if (marks.isArray()) {
            for (JsonNode mark : marks) {
                rendered = switch (mark.path("type").asText()) {
                    case "bold" -> "<strong>" + rendered + "</strong>";
                    case "italic" -> "<em>" + rendered + "</em>";
                    case "underline" -> "<u>" + rendered + "</u>";
                    case "strike" -> "<s>" + rendered + "</s>";
                    case "code" -> "<code>" + rendered + "</code>";
                    case "link" -> "<a href=\"" + escapeAttribute(
                            mark.path("attrs").path("href").asText()) + "\" rel=\"noopener noreferrer\">"
                            + rendered + "</a>";
                    default -> rendered;
                };
            }
        }
        return rendered;
    }

    private String renderChildren(JsonNode node) {
        StringBuilder result = new StringBuilder();
        for (JsonNode child : node.path("content")) result.append(renderNode(child));
        return result.toString();
    }

    private String nodeText(JsonNode node) {
        String type = node.path("type").asText();
        if ("text".equals(type)) return node.path("text").asText();
        if ("hardBreak".equals(type)) return "\n";
        if ("horizontalRule".equals(type)) return "⁂";
        String separator = Set.of("bulletList", "orderedList", "blockquote").contains(type)
                ? "\n" : "";
        return childrenText(node, separator);
    }

    private String markdownBlock(JsonNode node) {
        String type = node.path("type").asText();
        return switch (type) {
            case "paragraph" -> markdownInline(node);
            case "heading" -> "#".repeat(node.path("attrs").path("level").asInt(2) + 1)
                    + " " + markdownInline(node);
            case "blockquote" -> markdownChildren(node).lines()
                    .map(line -> "> " + line).collect(java.util.stream.Collectors.joining("\n"));
            case "bulletList" -> markdownList(node, false);
            case "orderedList" -> markdownList(node, true);
            case "horizontalRule" -> "---";
            default -> "";
        };
    }

    private String markdownChildren(JsonNode node) {
        List<String> blocks = new ArrayList<>();
        for (JsonNode child : node.path("content")) {
            String block = markdownBlock(child);
            if (!block.isBlank()) blocks.add(block);
        }
        return String.join("\n\n", blocks);
    }

    private String markdownList(JsonNode list, boolean ordered) {
        StringBuilder result = new StringBuilder();
        int number = list.path("attrs").path("start").asInt(1);
        for (JsonNode item : list.path("content")) {
            if (!result.isEmpty()) result.append('\n');
            String marker = ordered ? number++ + ". " : "- ";
            boolean firstBlock = true;
            for (JsonNode child : item.path("content")) {
                String rendered = "paragraph".equals(child.path("type").asText())
                        ? markdownInline(child) : markdownBlock(child);
                if (rendered.isBlank()) continue;
                if (firstBlock) {
                    result.append(marker).append(rendered.replace("\n", "\n  "));
                    firstBlock = false;
                } else {
                    result.append('\n').append("  ")
                            .append(rendered.replace("\n", "\n  "));
                }
            }
        }
        return result.toString();
    }

    private String markdownInline(JsonNode node) {
        StringBuilder result = new StringBuilder();
        for (JsonNode child : node.path("content")) {
            if ("hardBreak".equals(child.path("type").asText())) {
                result.append("  \n");
            } else if ("text".equals(child.path("type").asText())) {
                String text = escapeMarkdown(child.path("text").asText());
                for (JsonNode mark : child.path("marks")) {
                    String type = mark.path("type").asText();
                    text = switch (type) {
                        case "bold" -> "**" + text + "**";
                        case "italic" -> "*" + text + "*";
                        case "underline" -> "<u>" + text + "</u>";
                        case "strike" -> "~~" + text + "~~";
                        case "code" -> "`" + text.replace("`", "\\`") + "`";
                        case "link" -> "[" + text + "](<"
                                + mark.path("attrs").path("href").asText() + ">)";
                        default -> text;
                    };
                }
                result.append(text);
            }
        }
        return result.toString();
    }

    private String escapeMarkdown(String value) {
        return value.replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("<", "\\<")
                .replace(">", "\\>")
                .replace("#", "\\#")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace(".", "\\.")
                .replace("!", "\\!")
                .replace("|", "\\|");
    }

    private String childrenText(JsonNode node, String separator) {
        List<String> values = new ArrayList<>();
        for (JsonNode child : node.path("content")) values.add(nodeText(child));
        return String.join(separator, values);
    }

    private JsonNode read(String value) {
        try {
            JsonNode root = objectMapper.readTree(value);
            if (!root.path("type").asText().equals("doc")) throw badRequest("富文本文档无效");
            validateNode(root, "", 0, new int[] {0});
            return root;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "富文本文档无法读取", exception);
        }
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String escapeAttribute(String value) {
        return escape(value);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
