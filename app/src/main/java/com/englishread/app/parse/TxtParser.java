package com.englishread.app.parse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 纯文本解析：编码探测 → 切章 → 空行切段。
 *
 * <p>纯 Java、只吃 byte[]，方便在 JVM 单测里喂样本。
 */
public final class TxtParser {

    /** 大小写不敏感：Chapter 1 / CHAPTER I / chapter 12 都要认。 */
    private static final Pattern CHAPTER_LINE = Pattern.compile(
            "(?i)^\\s*chapter\\s+([0-9ivxlcdm]+)\\b.*$"
                    + "|^\\s*(?:第\\s*[0-9一二三四五六七八九十百]+\\s*[章节回])\\s*.*$");

    private static final Pattern CHAPTER_RULE = Pattern.compile("^\\s*[=*#~\\-]{4,}\\s*$");

    private TxtParser() {
    }

    /**
     * 编码探测：BOM 优先；否则严格 UTF-8 解码，失败退 GB18030。
     * GB18030 是 UTF-8 的超集式猜测里最稳的兜底（中文 TXT 大多是它或 GBK）。
     */
    public static String decode(byte[] raw) {
        if (raw.length >= 3 && (raw[0] & 0xFF) == 0xEF
                && (raw[1] & 0xFF) == 0xBB && (raw[2] & 0xFF) == 0xBF) {
            return new String(raw, 3, raw.length - 3, StandardCharsets.UTF_8);
        }
        if (raw.length >= 2 && (raw[0] & 0xFF) == 0xFF && (raw[1] & 0xFF) == 0xFE) {
            return new String(raw, 2, raw.length - 2, StandardCharsets.UTF_16LE);
        }
        if (raw.length >= 2 && (raw[0] & 0xFF) == 0xFE && (raw[1] & 0xFF) == 0xFF) {
            return new String(raw, 2, raw.length - 2, StandardCharsets.UTF_16BE);
        }
        try {
            CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return dec.decode(ByteBuffer.wrap(raw)).toString();
        } catch (CharacterCodingException e) {
            return new String(raw, Charset.forName("GB18030"));
        }
    }

    public static ParsedBook parse(byte[] raw, String fallbackTitle) {
        return parse(decode(raw), fallbackTitle);
    }

    /**
     * 切章规则（按优先级）：
     * 1. `Chapter N` / `第N章` 独立成行；
     * 2. 否则找 `====`/`***`/`----` 之类的分隔线，其一章。
     * 都找不到就整本一章，不猜。
     */
    public static ParsedBook parse(String text, String fallbackTitle) {
        ParsedBook pb = ParsedBook.of("txt");
        pb.title = fallbackTitle;
        text = text.replace("\r\n", "\n").replace('\r', '\n');

        String[] lines = text.split("\n", -1);
        ParsedBook.Chapter current = null;
        StringBuilder body = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            boolean isHeading = CHAPTER_LINE.matcher(trimmed).matches()
                    || (CHAPTER_RULE.matcher(trimmed).matches() && body.length() > 0);

            if (isHeading) {
                flush(pb, current, body);
                current = new ParsedBook.Chapter();
                // 分隔线不是标题，别把它当章节名
                current.title = CHAPTER_RULE.matcher(trimmed).matches()
                        ? "第 " + (pb.chapters.size() + 1) + " 章"
                        : trimmed;
                body.setLength(0);
                continue;
            }
            body.append(line).append('\n');
        }
        flush(pb, current, body);

        if (pb.chapters.isEmpty()) {
            ParsedBook.Chapter only = new ParsedBook.Chapter(fallbackTitle, null);
            addParagraphs(only, body.toString());
            pb.chapters.add(only);
        }
        return pb;
    }

    private static void flush(ParsedBook pb, ParsedBook.Chapter ch, StringBuilder body) {
        if (ch == null) {
            // 第一个标题之前的内容：有字才单列一章，全空就丢掉（常见于版权页前空行）
            if (body.toString().trim().isEmpty()) {
                return;
            }
            ch = new ParsedBook.Chapter(pb.title, null);
        }
        addParagraphs(ch, body.toString());
        if (!ch.paragraphs.isEmpty()) {
            pb.chapters.add(ch);
        }
        body.setLength(0);
    }

    /**
     * 空行切段；单换行在 TXT 里常是排版换行，合并成空格。
     * 太短的段（< 2 字符）当噪音丢弃，避免目录行、页码进正文。
     */
    static void addParagraphs(ParsedBook.Chapter ch, String body) {
        for (String block : body.split("\n\\s*\n")) {
            String para = block.replaceAll("\\s*\\n\\s*", " ").trim();
            if (para.length() >= 2) {
                ch.paragraphs.add(para);
            }
        }
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
