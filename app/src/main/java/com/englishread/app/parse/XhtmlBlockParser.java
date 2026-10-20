package com.englishread.app.parse;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 把一章 XHTML 抽成段落列表。
 *
 * <p>EPUB 里的 XHTML 十有八九不是合法 XML：命名实体、未闭合 br/img、DOCTYPE 外部实体。
 * 所以先做一次文本级清洗，再喂 XmlPullParser。清洗不追求完美，解析失败就降级为
 * 「剥标签取纯文本」（见 {@link #extractPlain}），整本不因此失败。
 */
public final class XhtmlBlockParser {

    /** 这些标签的一整个元素内容都不是正文（脚注锚点文本仍保留，故不含 a）。 */
    private static final Set<String> SKIP = Set.of("script", "style", "head", "svg", "nav");

    private static final Set<String> BLOCK = Set.of(
            "p", "div", "h1", "h2", "h3", "h4", "h5", "h6",
            "li", "blockquote", "td", "dd", "dt", "pre", "figcaption");

    private static final Pattern DOCTYPE = Pattern.compile("<!DOCTYPE[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern XML_DECL = Pattern.compile("<\\?xml[^>]*\\?>");
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern CDATA = Pattern.compile("<!\\[CDATA\\[.*?]]>", Pattern.DOTALL);
    private static final Pattern CONTROL = Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    private XhtmlBlockParser() {
    }

    /**
     * 文本级预清洗。返回的串若仍不是合法 XML，交给调用方降级。
     *
     * <p>`&nbsp;` 之类的命名实体在 XML 里非法，统一换成数字实体；
     * 自闭合的 br/img 必须补 `/`，否则 XmlPullParser 报 tag mismatch。
     */
    static String sanitize(String xhtml) {
        String s = xhtml;
        s = XML_DECL.matcher(s).replaceAll("");
        s = DOCTYPE.matcher(s).replaceAll("");
        s = COMMENT.matcher(s).replaceAll("");
        s = CDATA.matcher(s).replaceAll("");
        s = CONTROL.matcher(s).replaceAll("");

        s = s.replace("&nbsp;", "&#160;")
                .replace("&mdash;", "&#8212;")
                .replace("&ndash;", "&#8211;")
                .replace("&hellip;", "&#8230;")
                .replace("&ldquo;", "&#8220;")
                .replace("&rdquo;", "&#8221;")
                .replace("&lsquo;", "&#8216;")
                .replace("&rsquo;", "&#8217;")
                .replace("&copy;", "&#169;")
                .replace("&reg;", "&#174;")
                .replace("&trade;", "&#8482;");

        // 裸 <br> / <img ...> / <hr> 补自闭合。先把已有斜杠吃掉再统一补，避免 <br /> 被改成 <br />/>。
        s = s.replaceAll("(?i)<(br|hr)([^>]*?)\\s*/?>", "<$1$2/>");
        s = s.replaceAll("(?i)<img([^>]*?)\\s*/?>", "<img$1/>");

        // 标签/属性里的裸 & 是常见错误；已有的合法实体（含上面刚生成的数字实体）原样保留
        s = s.replaceAll("&(?!(?:#\\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);)", "&amp;");
        return s;
    }

    /** 解析一章，返回段落列表（已合并空白、去空段）。 */
    public static List<String> parse(String xhtml) {
        String clean = sanitize(xhtml);
        try {
            return parseStrict(clean);
        } catch (Exception e) {
            // 降级：整章剥标签，至少正文可读（HANDOFF R3）
            return extractPlain(xhtml);
        }
    }

    private static List<String> parseStrict(String clean) throws Exception {
        XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
        p.setInput(new StringReader(clean));

        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        int skipDepth = 0;

        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
            switch (ev) {
                case XmlPullParser.START_TAG: {
                    String name = p.getName().toLowerCase();
                    if (SKIP.contains(name)) {
                        skipDepth++;
                    } else if (BLOCK.contains(name) && buf.length() > 0) {
                        flush(out, buf);
                    }
                    break;
                }
                case XmlPullParser.END_TAG: {
                    String name = p.getName().toLowerCase();
                    if (SKIP.contains(name)) {
                        if (skipDepth > 0) {
                            skipDepth--;
                        }
                    } else if (BLOCK.contains(name)) {
                        flush(out, buf);
                    }
                    break;
                }
                case XmlPullParser.TEXT: {
                    if (skipDepth == 0) {
                        String t = p.getText();
                        if (t != null && !t.trim().isEmpty()) {
                            if (buf.length() > 0 && !endsWithSpace(buf)) {
                                buf.append(' ');
                            }
                            buf.append(t.trim());
                        }
                    }
                    break;
                }
                default:
                    break;
            }
        }
        flush(out, buf);
        return out;
    }

    private static boolean endsWithSpace(StringBuilder b) {
        return b.length() > 0 && Character.isWhitespace(b.charAt(b.length() - 1));
    }

    private static void flush(List<String> out, StringBuilder buf) {
        String s = buf.toString().replaceAll("\\s+", " ").trim();
        if (s.length() >= 2) {
            out.add(s);
        }
        buf.setLength(0);
    }

    /** 取第一个 h1-h6 的文本，作为章节标题。没有标题标签就返回 null。 */
    public static String firstHeading(String xhtml) {
        try {
            XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
            p.setInput(new StringReader(sanitize(xhtml)));
            boolean inHeading = false;
            StringBuilder buf = new StringBuilder();
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (ev == XmlPullParser.START_TAG) {
                    String name = p.getName().toLowerCase();
                    if (name.matches("h[1-6]")) {
                        inHeading = true;
                        buf.setLength(0);
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    if (inHeading && p.getName().toLowerCase().matches("h[1-6]")) {
                        String s = buf.toString().replaceAll("\\s+", " ").trim();
                        return s.isEmpty() ? null : s;
                    }
                } else if (ev == XmlPullParser.TEXT && inHeading) {
                    buf.append(p.getText());
                }
            }
        } catch (Exception ignored) {
            // 降级路径：从剥标签文本里找短路
        }
        for (String line : extractPlain(xhtml)) {
            if (line.length() > 0 && line.length() <= 60) {
                return line;
            }
        }
        return null;
    }

    /** 剥标签降级：按块级标签断段，实体还原，尽量别把正文搞成一大坨。 */
    static List<String> extractPlain(String xhtml) {
        String s = DOCTYPE.matcher(xhtml).replaceAll("");
        s = XML_DECL.matcher(s).replaceAll("");
        s = COMMENT.matcher(s).replaceAll("");
        s = s.replaceAll("(?i)</(p|div|h[1-6]|li|blockquote|td|dd|dt|pre|br)>", "\n");
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = TAG.matcher(s).replaceAll(" ");
        s = unescape(s);

        List<String> out = new ArrayList<>();
        for (String line : s.split("\n")) {
            String t = line.replaceAll("\\s+", " ").trim();
            if (t.length() >= 2) {
                out.add(t);
            }
        }
        return out;
    }

    private static String unescape(String s) {
        return s.replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replace("&hellip;", "…")
                .replace("&ldquo;", "\u201C").replace("&rdquo;", "\u201D")
                .replace("&lsquo;", "\u2018").replace("&rsquo;", "\u2019")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }
}
