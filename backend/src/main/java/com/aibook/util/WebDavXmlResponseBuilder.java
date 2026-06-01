package com.aibook.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * WebDAV XML 响应构建器
 * 构建符合 DAV: 命名空间的 XML 响应
 */
public final class WebDavXmlResponseBuilder {

    private static final DateTimeFormatter DAV_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US)
                    .withZone(ZoneId.of("GMT"));

    private WebDavXmlResponseBuilder() {}

    /**
     * 构建 PROPFIND multi-status 响应
     */
    public static String buildMultiStatus(String href, List<WebDavResource> resources) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<D:multistatus xmlns:D=\"DAV:\">\n");

        // 添加当前资源自身（Depth: 0 或 Depth: 1 的根）
        if (!href.endsWith("/")) {
            href = href + "/";
        }

        for (WebDavResource resource : resources) {
            xml.append(buildResponse(href, resource));
        }

        xml.append("</D:multistatus>");
        return xml.toString();
    }

    /**
     * 构建单个资源的 PROPFIND 响应
     */
    public static String buildPropResponse(String href, WebDavResource resource) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<D:multistatus xmlns:D=\"DAV:\">\n");
        xml.append(buildResponse(href, resource));
        xml.append("</D:multistatus>");
        return xml.toString();
    }

    /**
     * 构建单个 response 元素
     */
    private static String buildResponse(String baseHref, WebDavResource resource) {
        StringBuilder xml = new StringBuilder();
        String responseHref = baseHref + resource.name();
        if (resource.isDirectory() && !responseHref.endsWith("/")) {
            responseHref = responseHref + "/";
        }

        xml.append("  <D:response>\n");
        xml.append("    <D:href>").append(responseHref).append("</D:href>\n");
        xml.append("    <D:propstat>\n");
        xml.append("      <D:prop>\n");

        // displayname
        xml.append("        <D:displayname>").append(escapeXml(resource.name())).append("</D:displayname>\n");

        // resourcetype
        if (resource.isDirectory()) {
            xml.append("        <D:resourcetype><D:collection/></D:resourcetype>\n");
        } else {
            xml.append("        <D:resourcetype/>\n");
        }

        // getcontentlength（仅文件）
        if (!resource.isDirectory() && resource.size() != null) {
            xml.append("        <D:getcontentlength>").append(resource.size()).append("</D:getcontentlength>\n");
        }

        // getcontenttype（仅文件）
        if (!resource.isDirectory() && resource.contentType() != null) {
            xml.append("        <D:getcontenttype>").append(resource.contentType()).append("</D:getcontenttype>\n");
        }

        // getlastmodified
        if (resource.lastModified() != null) {
            String formatted = DAV_DATE_FORMAT.format(resource.lastModified());
            xml.append("        <D:getlastmodified>").append(formatted).append("</D:getlastmodified>\n");
        }

        // getetag
        if (resource.etag() != null) {
            xml.append("        <D:getetag>").append(escapeXml(resource.etag())).append("</D:getetag>\n");
        }

        xml.append("      </D:prop>\n");
        xml.append("      <D:status>HTTP/1.1 200 OK</D:status>\n");
        xml.append("    </D:propstat>\n");
        xml.append("  </D:response>\n");

        return xml.toString();
    }

    /**
     * 构建错误响应
     */
    public static String buildErrorResponse(int statusCode, String message) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<D:multistatus xmlns:D=\"DAV:\">\n");
        xml.append("  <D:response>\n");
        xml.append("    <D:propstat>\n");
        xml.append("      <D:prop/>\n");
        xml.append("      <D:status>HTTP/1.1 ").append(statusCode).append(" ").append(escapeXml(message)).append("</D:status>\n");
        xml.append("    </D:propstat>\n");
        xml.append("  </D:response>\n");
        xml.append("</D:multistatus>");
        return xml.toString();
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    /**
     * WebDAV 资源数据
     */
    public record WebDavResource(
            String name,
            boolean isDirectory,
            Long size,
            String contentType,
            LocalDateTime lastModified,
            String etag
    ) {}
}
