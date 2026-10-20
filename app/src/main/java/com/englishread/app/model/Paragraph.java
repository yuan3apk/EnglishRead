package com.englishread.app.model;

/**
 * 段落。{@code text} 从入库到显示全程只含原文——
 * 「段」按钮是独立 View，任何 UI 装饰都不许拼进来（HANDOFF D1）。
 */
public class Paragraph {

    public static final int STATUS_NONE = 0;
    public static final int STATUS_QUEUED = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_FAILED = 3;

    public long id;
    public long chapterId;
    public int idx;
    public String text;
    /** 整段译文；null = 未译 */
    public String trans;
    public int transStatus;

    /** 词起点/终点的缓存，避免每次点击重新正则；由 ReaderAdapter 首次绑定时填。 */
    public transient int[] tokenStarts;
    public transient int[] tokenEnds;
    public transient String[] tokenTexts;
}
