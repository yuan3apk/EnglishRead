package com.englishread.app.model;

import java.util.List;

/** 章节：目录项，也是阅读器的单位。字段口径见 HANDOFF 第 3 节的 chapter 表。 */
public class Chapter {
    public long id;
    public long bookId;
    /** 从 0 起，spine 顺序 */
    public int idx;
    public String title;
    public String href;
    public int paraCount;

    /** 只用于列表未加载时显示章节标题，不参与渲染。 */
    public transient List<Paragraph> loaded;
}
