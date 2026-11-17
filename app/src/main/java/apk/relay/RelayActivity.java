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
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.StandardOpenOption;
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
            File partial = null;
            try {
                File directory = incoming();
                removeAbandonedCopies(directory);
                file = new File(directory, UUID.randomUUID() + ".apk");
                partial = new File(directory, file.getName() + ".part");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(partial);
                     FileLock lock = out.getChannel().lock()) {
                    ApkCopy.copy(in, out);
                    out.getFD().sync();
                    // Publish a complete APK before exposing it to the installer.
                    if (!partial.renameTo(file)) throw new IOException("Cannot publish APK copy");
                }
                PackageInfo info = getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0);
                if (info == null || info.applicationInfo == null || (info.splitNames != null && info.splitNames.length != 0)) {
                    file.delete();
                    runOnUiThread(() -> { if (!isDestroyed()) fail(R.string.invalid_apk); });
                    return;
                }
                File ready = file;
                runOnUiThread(() -> {
                    if (!isDestroyed() && !isFinishing()) { staged = ready; requestInstall(); }
                    else ready.delete(); // Never handed to an installer.
                });
            } catch (Exception e) {
                if (partial != null) partial.delete();
                if (file != null) file.delete();
                runOnUiThread(() -> { if (!isDestroyed()) fail(R.string.read_failed); });
            }
        });
    }

    private static void removeAbandonedCopies(File directory) {
        File[] previous = directory.listFiles((dir, name) -> name.endsWith(".apk.part"));
        if (previous == null) return;
        long cutoff = System.currentTimeMillis() - 7L * 86400000;
        for (File file : previous) {
            if (file.lastModified() >= cutoff) continue;
            // A live copy holds this lock. Completed/legacy APKs are never age-deleted:
            // another installer may still use them, even after our process was killed.
            try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.WRITE);
                 FileLock lock = channel.tryLock()) {
                if (lock != null) file.delete();
            } catch (IOException | OverlappingFileLockException ignored) { }
        }
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
        } catch (RuntimeException e) {
            waitingFor = 0; // The child activity was not launched.
            fail(R.string.install_failed);
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != waitingFor) return;
        waitingFor = 0;
        if (request == PERMISSION) {
            if (getPackageManager().canRequestPackageInstalls()) requestInstall();
            else fail(R.string.permission_required);
        } else if (request == INSTALL) {
            discardStaged(); // The installer has returned, including cancellation.
            if (result == RESULT_FIRST_USER) fail(R.string.installation_failed);
            else {
                if (result == RESULT_OK)
                    Toast.makeText(this, R.string.installation_succeeded, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void fail(int message) {
        discardStaged();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    private void discardStaged() {
        if (staged == null || waitingFor == INSTALL) return;
        if (staged.delete() || !staged.exists()) staged = null;
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (staged != null) out.putString("staged", staged.getName());
        out.putInt("waitingFor", waitingFor);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        // Keep the saved file on recreation, or while an installer may still need it.
        if (isFinishing() && !isChangingConfigurations()) discardStaged();
        super.onDestroy();
    }
}
