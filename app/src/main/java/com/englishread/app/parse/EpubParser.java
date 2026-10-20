package com.englishread.app.parse;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * EPUB 解析：META-INF/container.xml → OPF → manifest/spine → 逐章抽块。
 *
 * <p>用平台自带的 java.util.zip + XmlPullParser，不引 jsoup（见 HANDOFF 第 1 节）。
 * 单个章节解析失败只跳过该章，不让整本失败（R3）。
 */
public final class EpubParser {

    private EpubParser() {
    }

    /** 解析结果里额外带一个「封面已落地到哪」的路径。 */
    public static ParsedBook parse(File epubFile, String fallbackTitle, File coverOut) throws IOException {
        try (ZipFile zip = new ZipFile(epubFile)) {
            String opfPath = findOpfPath(zip);
            if (opfPath == null) {
                throw new IOException("container.xml 里找不到 OPF");
            }
            Opf opf = parseOpf(zip, opfPath);

            ParsedBook pb = ParsedBook.of("epub");
            pb.title = opf.title != null ? opf.title : fallbackTitle;
            pb.author = opf.author;

            if (opf.coverHref != null && coverOut != null) {
                pb.coverPath = extractCover(zip, opf.coverHref, coverOut);
            }

            String baseDir = dirOf(opfPath);
            for (int i = 0; i < opf.spine.size(); i++) {
                String href = opf.spine.get(i);
                ZipEntry e = zip.getEntry(join(baseDir, href));
                if (e == null) {
                    continue;
                }
                String xhtml;
                try (InputStream in = zip.getInputStream(e)) {
                    xhtml = new String(TxtParser.readAll(in), StandardCharsets.UTF_8);
                }

                List<String> paras = XhtmlBlockParser.parse(xhtml);
                if (paras.isEmpty()) {
                    continue;
                }
                String title = titleFor(xhtml, paras, i);
                // h1 已经当章节名用了，正文里再来一遍就是重复
                if (!paras.isEmpty() && paras.get(0).equals(title)) {
                    paras.remove(0);
                }
                if (paras.isEmpty()) {
                    continue;
                }
                ParsedBook.Chapter ch = new ParsedBook.Chapter(title, href);
                ch.paragraphs.addAll(paras);
                pb.chapters.add(ch);
            }

            if (pb.chapters.isEmpty()) {
                throw new IOException("EPUB 里没有可读章节");
            }
            return pb;
        }
    }

    private static String findOpfPath(ZipFile zip) throws IOException {
        ZipEntry e = zip.getEntry("META-INF/container.xml");
        if (e == null) {
            return null;
        }
        String xml;
        try (InputStream in = zip.getInputStream(e)) {
            xml = new String(TxtParser.readAll(in), StandardCharsets.UTF_8);
        }
        try {
            XmlPullParser p = newPullParser(xml);
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (ev == XmlPullParser.START_TAG && "rootfile".equals(p.getName())) {
                    String path = p.getAttributeValue(null, "full-path");
                    if (path != null && !path.trim().isEmpty()) {
                        return path.trim();
                    }
                }
            }
        } catch (Exception ignored) {
            // 走下面的兜底
        }
        return null;
    }

    private static final class Opf {
        String title;
        String author;
        String coverHref;
        final List<String> spine = new ArrayList<>();
        final Map<String, String> manifest = new HashMap<>();
    }

    private static Opf parseOpf(ZipFile zip, String opfPath) throws IOException {
        ZipEntry e = zip.getEntry(opfPath);
        if (e == null) {
            throw new IOException("OPF 不存在: " + opfPath);
        }
        String xml;
        try (InputStream in = zip.getInputStream(e)) {
            xml = new String(TxtParser.readAll(in), StandardCharsets.UTF_8);
        }

        Opf opf = new Opf();
        List<String> spineRefs = new ArrayList<>();
        String coverId = null;

        try {
            XmlPullParser p = newPullParser(xml);
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (ev != XmlPullParser.START_TAG) {
                    continue;
                }
                String name = p.getName();
                if ("item".equals(name)) {
                    String id = p.getAttributeValue(null, "id");
                    String href = p.getAttributeValue(null, "href");
                    String mediaType = p.getAttributeValue(null, "media-type");
                    String properties = p.getAttributeValue(null, "properties");
                    if (id != null && href != null) {
                        opf.manifest.put(id, href);
                        if (properties != null && properties.contains("cover-image")) {
                            opf.coverHref = href;
                        }
                        if (mediaType != null && mediaType.startsWith("image/")
                                && "cover".equalsIgnoreCase(id) && opf.coverHref == null) {
                            opf.coverHref = href;
                        }
                    }
                } else if ("itemref".equals(name)) {
                    String idref = p.getAttributeValue(null, "idref");
                    if (idref != null) {
                        spineRefs.add(idref);
                    }
                } else if ("meta".equals(name)) {
                    String metaName = p.getAttributeValue(null, "name");
                    String content = p.getAttributeValue(null, "content");
                    if ("cover".equalsIgnoreCase(metaName) && content != null) {
                        coverId = content;
                    }
                } else if ("title".equals(name) && opf.title == null) {
                    opf.title = textOf(p);
                } else if ("creator".equals(name) && opf.author == null) {
                    opf.author = textOf(p);
                }
            }
        } catch (Exception ex) {
            throw new IOException("OPF 解析失败", ex);
        }

        // EPUB2 的封面走 <meta name="cover" content="cover-id">
        if (opf.coverHref == null && coverId != null) {
            opf.coverHref = opf.manifest.get(coverId);
        }

        for (String idref : spineRefs) {
            String href = opf.manifest.get(idref);
            if (href != null) {
                opf.spine.add(href);
            }
        }
        return opf;
    }

    private static String textOf(XmlPullParser p) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int ev = p.next(); ev != XmlPullParser.END_TAG; ev = p.next()) {
            if (ev == XmlPullParser.TEXT) {
                sb.append(p.getText());
            }
        }
        return sb.toString().trim();
    }

    /** 封面按扩展名落地，找不到图片就返回 null（书架上退化成色块，不算错误）。 */
    private static String extractCover(ZipFile zip, String href, File coverOut) throws IOException {
        ZipEntry e = zip.getEntry(href);
        if (e == null) {
            return null;
        }
        String ext = extOf(href);
        File out = new File(coverOut, "cover" + ext);
        try (InputStream in = zip.getInputStream(e);
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                fos.write(buf, 0, n);
            }
        }
        return out.getAbsolutePath();
    }

    private static String extOf(String href) {
        int dot = href.lastIndexOf('.');
        return dot >= 0 ? href.substring(dot).toLowerCase() : ".img";
    }

    /**
     * 章节标题：取该章第一个 h1-h6 的文本，拿不到才退回「第 N 章」。
     * 用正文标题而不是 OPF 的 nav，是因为没 toc 的 EPUB2 后者为空。
     */
    private static String titleFor(String xhtml, List<String> paras, int index) {
        String heading = XhtmlBlockParser.firstHeading(xhtml);
        if (heading != null) {
            return heading;
        }
        return "第 " + (index + 1) + " 章";
    }

    private static String dirOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(0, slash + 1) : "";
    }

    private static String join(String base, String href) {
        if (href.startsWith("/")) {
            return href.substring(1);
        }
        return base + href;
    }

    private static XmlPullParser newPullParser(String xml) throws Exception {
        XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
        p.setInput(new StringReader(clean(xml)));
        return p;
    }

    /** OPF 不允许命名实体，但现实里常出现 &nbsp;；顺手去掉 BOM 和控制字符。 */
    private static String clean(String xml) {
        String s = xml;
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            s = s.substring(1);
        }
        s = s.replaceAll("<\\?xml[^>]*\\?>", "");
        s = s.replaceAll("<!DOCTYPE[^>]*>", "");
        s = s.replaceAll("<!--.*?-->", "");
        s = s.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]", "");
        s = s.replace("&nbsp;", "&#160;");
        s = s.replaceAll("&(?!(?:#\\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);)", "&amp;");
        return s;
    }
}
