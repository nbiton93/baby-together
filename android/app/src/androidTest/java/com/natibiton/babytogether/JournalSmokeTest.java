package com.natibiton.babytogether;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Exercises the packaged page, real native bridge, disk persistence and Back handling. */
@RunWith(AndroidJUnit4.class)
public class JournalSmokeTest {
    private ActivityScenario<MainActivity> scenario;

    private String js(String script) throws Exception {
        AtomicReference<String> result=new AtomicReference<>();
        CountDownLatch done=new CountDownLatch(1);
        scenario.onActivity(activity->{
            WebView web=(WebView)((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
            web.evaluateJavascript(script,value->{result.set(value);done.countDown();});
        });
        assertTrue("WebView did not respond",done.await(10,TimeUnit.SECONDS));
        return result.get();
    }
    private void until(String condition) throws Exception {
        long end=SystemClock.uptimeMillis()+20000;
        while(SystemClock.uptimeMillis()<end){
            if("true".equals(js("Boolean("+condition+")")))return;
            SystemClock.sleep(100);
        }
        fail("Page condition timed out: "+condition+"; page="+js("document.body.innerText"));
    }
    private void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        File dir=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"screenshots");
        assertTrue(dir.isDirectory()||dir.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(dir,name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}
        image.recycle();
    }
    @Test public void createEditReopenAndBackPreserveJournal() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        for(String name:new String[]{"journal.json","journal.json.bak","journal.json.new"})new File(context.getNoBackupFilesDir(),name).delete();
        try(ActivityScenario<MainActivity> current=ActivityScenario.launch(MainActivity.class)){
            scenario=current;
            until("document.querySelector('#settings-form')");
            assertEquals("\"rtl\"",js("document.documentElement.dir"));
            js("document.querySelector('#author').value='הורה לבדיקה';document.querySelector('#name').value='תינוקת לבדיקה';document.querySelector('#settings-form').requestSubmit()");
            until("!document.querySelector('#modal').open && document.querySelector('h1').textContent==='תינוקת לבדיקה'");
            js("document.querySelector('[data-action=add][data-kind=feed]').click()");
            until("document.querySelector('#entry-form')");
            js("document.querySelector('#amount').value='60';document.querySelector('#entry-form').requestSubmit()");
            until("!document.querySelector('#modal').open && document.querySelectorAll('.entry.feed').length===1");
            assertTrue(js("document.querySelector('.entry.feed').textContent").contains("60"));
            js("document.querySelector('.entry.feed').click()");
            until("document.querySelector('#amount')");
            js("document.querySelector('#amount').value='90';document.querySelector('#entry-form').requestSubmit()");
            until("!document.querySelector('#modal').open && document.querySelector('.entry.feed').textContent.includes('90')");
            screenshot("journal-before-reopen.png");
            current.recreate();
            until("document.querySelectorAll('.entry.feed').length===1");
            assertTrue(js("document.querySelector('.entry.feed').textContent").contains("90"));
            assertEquals("\"תינוקת לבדיקה\"",js("document.querySelector('h1').textContent"));
            js("document.querySelector('[data-action=add][data-kind=diaper]').click()");
            until("document.querySelector('#modal').open");
            current.onActivity(activity->activity.getOnBackPressedDispatcher().onBackPressed());
            until("!document.querySelector('#modal').open");
            current.onActivity(activity->assertFalse(activity.isFinishing()));
            js("document.querySelector('[data-action=tab][data-tab=family]').click()");
            until("document.querySelector('#app').textContent.includes('הרישומים נשמרים בטלפון בלבד')");
            screenshot("sharing-unconfigured.png");
        }
    }
}
