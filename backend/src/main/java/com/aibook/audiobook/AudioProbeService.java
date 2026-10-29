package com.aibook.audiobook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AudioProbeService {
    private static final int MAX_OUTPUT = 2 * 1024 * 1024;
    private final ObjectMapper mapper;
    @Value("${audio.ffprobe:ffprobe}")
    private String executable;

    public AudioTypes.ProbeResult probe(Path source) throws IOException {
        Path output = Files.createTempFile("audio-probe-", ".json");
        Process process = null;
        try {
            process = new ProcessBuilder(executable, "-v", "error", "-protocol_whitelist", "file", "-format_whitelist", "mp3,mov",
                    "-show_format", "-show_streams", "-show_chapters", "-of", "json",
                    source.toAbsolutePath().toString())
                    .redirectOutput(output.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (Files.size(output) > MAX_OUTPUT || System.nanoTime() > deadline) {
                    throw new IOException("音频探测超时或资料过大");
                }
            }
            if (process.exitValue() != 0 || Files.size(output) > MAX_OUTPUT) {
                throw new IOException("文件损坏、受保护或无法识别");
            }
            return parse(mapper.readTree(Files.readString(output)));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("音频探测已取消", exception);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try { process.waitFor(2, TimeUnit.SECONDS); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            }
            Files.deleteIfExists(output);
        }
    }

    AudioTypes.ProbeResult parse(JsonNode root) throws IOException {
        JsonNode audio = null;
        for (JsonNode stream : root.path("streams")) {
            if ("audio".equals(stream.path("codec_type").asText())) {
                if (audio != null) throw new IOException("暂不支持多音频流文件");
                audio = stream;
            }
        }
        if (audio == null) throw new IOException("文件不含音轨");
        String codec = audio.path("codec_name").asText();
        String container = root.path("format").path("format_name").asText().toLowerCase(Locale.ROOT);
        String mime;
        if ("mp3".equals(codec) && container.contains("mp3")) mime = "audio/mpeg";
        else if ("aac".equals(codec) && (container.contains("mp4") || container.contains("mov"))) {
            mime = "audio/mp4";
        } else throw new IOException("首期支持 MP3 或 AAC 编码的 M4A/M4B，当前编码：" + codec);
        long duration = milliseconds(root.path("format").path("duration").asText("0"));
        if (duration <= 0 || duration > TimeUnit.DAYS.toMillis(30)) throw new IOException("音频时长无效");
        var chapters = new ArrayList<AudioTypes.ProbeChapter>();
        for (JsonNode chapter : root.path("chapters")) {
            long start = milliseconds(chapter.path("start_time").asText("0"));
            long end = milliseconds(chapter.path("end_time").asText("0"));
            if (start < 0 || start >= end || end > duration + 1000) continue;
            chapters.add(new AudioTypes.ProbeChapter(
                    clip(chapter.path("tags").path("title").asText("章节 " + (chapters.size() + 1)),500),
                    start, Math.min(end, duration)));
            if (chapters.size() > 10000) throw new IOException("章节数量超过限制");
        }
        JsonNode tags = root.path("format").path("tags");
        return new AudioTypes.ProbeResult(duration, mime, codec,
                clip(tags.path("title").asText(""),500), clip(tags.path("artist").asText(""),200),
                tagNumber(tags.path("disc").asText("")), tagNumber(tags.path("track").asText("")), List.copyOf(chapters));
    }

    private static String clip(String value,int length) { return value.length() <= length ? value : value.substring(0,length); }

    static Integer tagNumber(String value) {
        try {
            int number = Integer.parseInt(value.split("/",2)[0].trim());
            return number > 0 && number <= 999999 ? number : null;
        } catch (RuntimeException exception) { return null; }
    }

    static long milliseconds(String value) throws IOException {
        try {
            return new BigDecimal(value).multiply(BigDecimal.valueOf(1000))
                    .setScale(0,java.math.RoundingMode.DOWN).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IOException("音频时间格式无效", exception);
        }
    }
}
