package com.unofficial.southparktv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import java.io.File;
import java.util.concurrent.*;

public final class UpdateActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Future<?> request;
    private int generation;
    private TextView status;
    private Button action;
    private AppUpdate.Release release;
    private File apk;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(getColor(R.color.tv_background));
        int h = getResources().getDimensionPixelSize(R.dimen.tv_safe_horizontal);
        int v = getResources().getDimensionPixelSize(R.dimen.tv_safe_vertical);
        page.setPaddingRelative(h, v, h, v);
        status = TvUi.text(this, getString(R.string.update_intro), R.dimen.tv_body_type, R.color.tv_text_primary);
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        page.addView(status);
        action = new Button(this); TvUi.styleButton(this, action);
        action.setText(R.string.update_check); page.addView(action);
        action.setOnClickListener(view -> {
            if (apk != null) install();
            else if (release != null) download();
            else check();
        });
        Button back = new Button(this); TvUi.styleButton(this, back);
        back.setText(R.string.update_back); page.addView(back);
        back.setOnClickListener(view -> finish());
        setContentView(page); action.requestFocus();
    }

    private void run(int message, Callable<Runnable> task) {
        int token = ++generation;
        status.setText(message); action.setEnabled(false);
        request = worker.submit(() -> {
            try {
                Runnable result = task.call();
                runOnUiThread(() -> { if (!isDestroyed() && token == generation) { action.setEnabled(true); result.run(); action.requestFocus(); } });
            } catch (Exception e) {
                runOnUiThread(() -> { if (!isDestroyed() && token == generation) {
                    status.setText(R.string.update_error); action.setEnabled(true); action.requestFocus();
                } });
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void check() {
        run(R.string.update_checking, () -> {
            AppUpdate.Release found = AppUpdate.check();
            int installed = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            if (found.sdk() > android.os.Build.VERSION.SDK_INT) throw new java.io.IOException("Unsupported SDK");
            return () -> {
                if (found.code() <= installed) status.setText(R.string.update_current);
                else {
                    release = found;
                    status.setText(getString(R.string.update_available, found.name(), found.size() / (1024.0 * 1024)));
                    action.setText(R.string.update_download);
                }
            };
        });
    }

    private void download() {
        run(R.string.update_downloading, () -> {
            File downloaded = AppUpdate.download(getApplicationContext(), release);
            return () -> { apk = downloaded; status.setText(R.string.update_ready); action.setText(R.string.update_install); };
        });
    }

    private void install() {
        if (!getPackageManager().canRequestPackageInstalls()) {
            AlertDialog dialog = TvUi.showDialog(new AlertDialog.Builder(this)
                    .setMessage(R.string.update_permission)
                    .setPositiveButton(R.string.update_settings, (d, w) -> {
                        try { startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); }
                        catch (android.content.ActivityNotFoundException e) { status.setText(R.string.update_error); }
                    }).setNegativeButton(R.string.update_back, null));
            dialog.setOnDismissListener(d -> action.requestFocus());
            return;
        }
        run(R.string.update_verifying, () -> {
            AppUpdate.verify(getApplicationContext(), apk, release);
            return () -> {
                try {
                    Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".updates", apk);
                    startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                    status.setText(R.string.update_installer);
                } catch (android.content.ActivityNotFoundException | IllegalArgumentException e) { status.setText(R.string.update_error); }
            };
        });
    }

    @Override protected void onStop() {
        super.onStop();
        generation++;
        if (request != null) request.cancel(true);
        action.setEnabled(true);
    }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
