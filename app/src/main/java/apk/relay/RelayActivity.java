// SPDX-License-Identifier: Unlicense
package apk.relay;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Transparent entry point for APK file intents. No launcher or application screen. */
public final class RelayActivity extends Activity {
    private static final int PERMISSION = 1, INSTALL = 2;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private File staged;
    private int waitingFor;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null && saved.getString("staged") != null) {
            try {
                File directory = incoming().getCanonicalFile();
                File file = new File(directory, saved.getString("staged")).getCanonicalFile();
                if (!directory.equals(file.getParentFile()) || !file.isFile()) {
                    fail(R.string.read_failed);
                    return;
                }
                staged = file;
                waitingFor = saved.getInt("waitingFor");
                // The framework delivers the outstanding child activity result after recreation.
                if (waitingFor == 0) requestInstall();
            } catch (IOException e) { fail(R.string.read_failed); }
        } else receive(getIntent());
    }

    private File incoming() throws IOException {
        File directory = new File(getFilesDir(), "incoming");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create APK directory");
        return directory;
    }

    private void receive(Intent intent) {
        try {
            Uri uri = null;
            if (Intent.ACTION_VIEW.equals(intent.getAction())) uri = intent.getData();
            else if (Intent.ACTION_SEND.equals(intent.getAction())) {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (uri == null && intent.getClipData() != null && intent.getClipData().getItemCount() == 1)
                    uri = intent.getClipData().getItemAt(0).getUri();
            }
            if (uri == null || !"content".equals(uri.getScheme())) { fail(R.string.file_required); return; }
            stage(uri);
        } catch (RuntimeException e) { fail(R.string.read_failed); }
    }

    private void stage(Uri uri) {
        worker.execute(() -> {
            File file = null;
            try {
                File directory = incoming();
                // Remove old interrupted copies as well as completed APKs.
                File[] previous = directory.listFiles();
                if (previous != null) for (File old : previous)
                    if (old.lastModified() < System.currentTimeMillis() - 7L * 86400000) old.delete();
                file = new File(directory, UUID.randomUUID() + ".apk");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(file)) {
                    ApkCopy.copy(in, out);
                    out.getFD().sync();
                }
                PackageInfo info = getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0);
                if (info == null || info.applicationInfo == null || (info.splitNames != null && info.splitNames.length != 0)) {
                    file.delete();
                    runOnUiThread(() -> { if (!isDestroyed()) fail(R.string.invalid_apk); });
                    return;
                }
                File ready = file;
                runOnUiThread(() -> {
                    if (!isDestroyed()) { staged = ready; requestInstall(); }
                });
            } catch (Exception e) {
                if (file != null) file.delete();
                runOnUiThread(() -> { if (!isDestroyed()) fail(R.string.read_failed); });
            }
        });
    }

    private void requestInstall() {
        if (staged == null || waitingFor != 0) return;
        try {
            if (!getPackageManager().canRequestPackageInstalls()) {
                waitingFor = PERMISSION;
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())), PERMISSION);
                return;
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", staged);
            Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_RETURN_RESULT, true);
            install.setClipData(ClipData.newRawUri("APK", uri));
            waitingFor = INSTALL;
            startActivityForResult(install, INSTALL);
        } catch (RuntimeException e) { fail(R.string.install_failed); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        waitingFor = 0;
        if (request == PERMISSION) {
            if (getPackageManager().canRequestPackageInstalls()) requestInstall();
            else fail(R.string.permission_required);
        } else if (request == INSTALL) {
            if (result == RESULT_FIRST_USER) fail(R.string.installation_failed);
            else {
                if (result == RESULT_OK)
                    Toast.makeText(this, R.string.installation_succeeded, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void fail(int message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (staged != null) out.putString("staged", staged.getName());
        out.putInt("waitingFor", waitingFor);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
