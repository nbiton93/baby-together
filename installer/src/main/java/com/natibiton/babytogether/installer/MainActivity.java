package com.natibiton.babytogether.installer;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_BACKUP = 100, EXPORT_APK = 101;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button choose, install, save;
    private File signed;
    private boolean busy, waitingForPermission;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setNavigationBarColor(Color.rgb(255, 245, 251));
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[] {Color.rgb(255, 231, 244), Color.rgb(236, 226, 255), Color.rgb(255, 248, 252)}));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        body.setPadding(dp(26), dp(32), dp(26), dp(32));
        scroll.addView(body);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        body.addView(label("♡", 64, true));
        body.addView(label("Baby Together", 30, true));
        body.addView(label("העדכון הוורוד שלנו", 24, true));
        body.addView(label("גרסה 0.3.0 · העיצוב החדש והיומן המשותף", 17, false));
        body.addView(label("בחר את קובץ הגיבוי של החתימה. העדכון יוכן כאן בטלפון, ואז Android יבקש ממך לאשר את ההתקנה.", 17, false));
        body.addView(label("קובץ הגיבוי נשאר במכשיר. למתקין אין הרשאת אינטרנט.", 15, false));
        choose = button("בחירת קובץ הגיבוי", body);
        choose.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                .addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, PICK_BACKUP);
        });
        status = label("בחר baby-together-private-signing-backup.zip", 16, false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body.addView(status);
        install = button("התקנת העדכון", body);
        install.setVisibility(View.GONE);
        install.setOnClickListener(v -> installUpdate());
        save = button("שמירת קובץ ההתקנה", body);
        save.setVisibility(View.GONE);
        save.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/vnd.android.package-archive").putExtra(Intent.EXTRA_TITLE, "baby-together-0.3.0.apk");
            startActivityForResult(intent, EXPORT_APK);
        });
        body.addView(label("זהו מתקין עזר נפרד. לאחר העדכון אפשר להסיר אותו ולהמשיך להשתמש באפליקציית Baby Together הרגילה.", 14, false));
        setContentView(scroll);
        if (state != null) waitingForPermission = state.getBoolean("waiting");
        File previous = new File(getCacheDir(), "signed/baby-together-0.3.0.apk");
        if (previous.exists()) {
            setBusy(true, "בודק את קובץ ההתקנה...");
            worker.execute(() -> {
                try {
                    LocalSigner.verify(previous, LocalSigner.ORIGINAL_SHA1);
                    LocalSigner.checkIdentity(this, previous);
                    showReady(previous);
                } catch (Exception e) {
                    previous.delete();
                    showError("בחר שוב את קובץ הגיבוי כדי להכין את העדכון.");
                }
            });
        }
    }

    private TextView label(String text, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(69, 35, 85));
        view.setTextDirection(View.TEXT_DIRECTION_RTL);
        view.setPadding(0, dp(8), 0, dp(8));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String text, LinearLayout body) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(17);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] {Color.rgb(165, 82, 181), Color.rgb(203, 89, 157)});
        background.setCornerRadius(dp(20));
        button.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(60));
        params.topMargin = dp(16);
        body.addView(button, params);
        return button;
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null || busy) return;
        Uri uri = data.getData();
        if (request == PICK_BACKUP) {
            signed = null;
            install.setVisibility(View.GONE);
            save.setVisibility(View.GONE);
            setBusy(true, "מכין את העדכון...");
            worker.execute(() -> {
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IOException("Cannot open backup");
                    showReady(LocalSigner.prepareUpdate(getApplicationContext(), input));
                } catch (Exception e) {
                    showError(e instanceof SecurityException
                        ? "הגיבוי אינו תואם לחתימה המקורית של האפליקציה, או שקובץ העדכון אינו תקין. בחר את הגיבוי המקורי."
                        : "לא ניתן לקרוא את הגיבוי. בחר את קובץ ה־ZIP המקורי המכיל את החתימה והסיסמה.");
                }
            });
        } else if (request == EXPORT_APK && signed != null) {
            File prepared = signed;
            setBusy(true, "שומר את קובץ ההתקנה...");
            worker.execute(() -> {
                try (InputStream input = new FileInputStream(prepared);
                     OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new IOException("Cannot save APK");
                    LocalSigner.copy(input, output);
                    post(() -> setBusy(false, "קובץ ההתקנה נשמר. אפשר לפתוח אותו גם בטלפון השני."));
                } catch (Exception e) {
                    post(() -> setBusy(false, "השמירה לא הצליחה. אפשר לנסות שוב או ללחוץ על התקנת העדכון."));
                }
            });
        }
    }

    private void showReady(File file) {
        post(() -> {
            signed = file;
            setBusy(false, "העדכון מוכן והחתימה המקורית אומתה ✓");
            install.setVisibility(View.VISIBLE);
            save.setVisibility(View.VISIBLE);
        });
    }

    private void showError(String message) { post(() -> setBusy(false, message)); }
    private void post(Runnable task) { runOnUiThread(() -> { if (!isDestroyed()) task.run(); }); }
    private void setBusy(boolean value, String message) {
        busy = value;
        choose.setEnabled(!value);
        install.setEnabled(!value);
        save.setEnabled(!value);
        status.setText(message);
    }

    private void installUpdate() {
        if (signed == null || busy) return;
        if (!getPackageManager().canRequestPackageInstalls()) {
            waitingForPermission = true;
            status.setText("אפשר התקנה מהמתקין הזה בהגדרות Android, ואז חזור לכאן.");
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", signed);
        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("Baby Together update", uri));
        try {
            startActivity(intent);
            status.setText("אשר את העדכון במסך Android. לאחר ההתקנה פתח את Baby Together הרגילה.");
        } catch (ActivityNotFoundException e) {
            status.setText("לא נמצא מתקין במכשיר. לחץ על שמירת קובץ ההתקנה ופתח אותו דרך מנהל הקבצים.");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (waitingForPermission && signed != null && !busy && getPackageManager().canRequestPackageInstalls()) {
            waitingForPermission = false;
            installUpdate();
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("waiting", waitingForPermission);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
