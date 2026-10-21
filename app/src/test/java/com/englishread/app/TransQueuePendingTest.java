package com.englishread.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.englishread.app.model.Paragraph;
import com.englishread.app.trans.TransQueue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * 补发只发缺的段落。
 *
 * <p>这是 M3 成本控制的命门：模型漏了 1 段却整批重发，就是为已经读好的段落付两次钱。
 * 逻辑在 {@link TransQueue#pendingOf}，纯函数，不需要真网络。
 */
public class TransQueuePendingTest {

    private static Paragraph done(long id) {
        Paragraph p = new Paragraph();
        p.id = id;
        p.trans = "译文" + id;
        p.transStatus = Paragraph.STATUS_DONE;
        return p;
    }

    private static Paragraph none(long id) {
        Paragraph p = new Paragraph();
        p.id = id;
        p.transStatus = Paragraph.STATUS_NONE;
        return p;
    }

    private static Paragraph failed(long id) {
        Paragraph p = new Paragraph();
        p.id = id;
        p.transStatus = Paragraph.STATUS_FAILED;
        return p;
    }

    @Test
    public void onlyUntranslatedParagraphsAreResent() {
        List<Paragraph> batch = Arrays.asList(done(1), none(2), done(3));
        List<Paragraph> again = TransQueue.pendingOf(batch);

        assertEquals("只该补发第 2 段", 1, again.size());
        assertEquals(2, again.get(0).id);
    }

    @Test
    public void failedParagraphsNeedWork() {
        // 失败的可重试，不能当成「已完成」跳过
        assertEquals(1, TransQueue.pendingOf(Arrays.asList(failed(5))).size());
    }

    @Test
    public void doneButNullTranslationStillNeedsWork() {
        // status 说完成但译文是 null：脏数据，必须补，不能信标志位
        Paragraph p = new Paragraph();
        p.id = 9;
        p.transStatus = Paragraph.STATUS_DONE;
        p.trans = null;
        assertTrue(TransQueue.needsWork(p));
    }

    @Test
    public void fullyTranslatedBatchResendsNothing() {
        assertTrue(TransQueue.pendingOf(Arrays.asList(done(1), done(2))).isEmpty());
    }
}
