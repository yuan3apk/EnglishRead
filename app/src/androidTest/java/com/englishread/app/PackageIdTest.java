package com.englishread.app;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PackageIdTest {
    @Test
    public void packageId_matchesBuildConfig() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.englishread.app", ctx.getPackageName());
    }
}
