package com.natibiton.babytogether.installer;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LocalSignerTest {
    @Test public void signsOnAndroidAndRejectsWrongKey() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context fixtures = InstrumentationRegistry.getInstrumentation().getContext();
        String expected;
        try (InputStream in = fixtures.getAssets().open("fixture-sha1.txt")) {
            expected = new String(LocalSigner.boundedRead(in, 1000), StandardCharsets.UTF_8).trim();
        }
        File input = new File(target.getCacheDir(), "test-unsigned.apk");
        try (InputStream in = target.getAssets().open("baby-together-unsigned.apk"); OutputStream out = new FileOutputStream(input)) {
            LocalSigner.copy(in, out);
        }
        assertEquals(BuildConfig.UPDATE_SHA256.toUpperCase(java.util.Locale.ROOT), LocalSigner.digest(input, "SHA-256"));
        LocalSigner.checkIdentity(target, input);
        File output = new File(target.getExternalFilesDir(null), "test-signed-update.apk");
        try (InputStream backup = fixtures.getAssets().open("synthetic-backup.zip")) {
            LocalSigner.sign(input, output, backup, expected);
        }
        LocalSigner.verify(output, expected);
        LocalSigner.checkIdentity(target, output);
        assertTrue(output.length() > 1000000);
        // Real key gate is mandatory in the public entry point.
        try (InputStream backup = fixtures.getAssets().open("synthetic-backup.zip")) {
            try {
                LocalSigner.prepareUpdate(target, backup);
                fail("Synthetic key must never be accepted by production");
            } catch (SecurityException expectedFailure) { }
        }
        File rejected = new File(target.getCacheDir(), "rejected.apk");
        try {
            LocalSigner.sign(input, rejected, new ByteArrayInputStream(new byte[] {1, 2, 3}), expected);
            fail("Malformed backup was accepted");
        } catch (IOException expectedFailure) { }
        assertFalse(rejected.exists());
        PackageInfo helper = target.getPackageManager().getPackageInfo(target.getPackageName(), PackageManager.GET_PERMISSIONS);
        assertFalse(Arrays.asList(helper.requestedPermissions).contains("android.permission.INTERNET"));
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            activity.onActivity(a -> assertFalse(a.isFinishing()));
            activity.recreate();
        }
    }
}
