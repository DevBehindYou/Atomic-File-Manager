package com.devbehindyou.atomicfilemanager.data.preview;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Deliberately platform-only Java: this test-APK Activity has its own UID and classloader,
 * without the Kotlin/coroutine libraries supplied by the target APK to instrumentation.
 */
public final class ExternalPngReceiverActivity extends Activity {
    public static final String RESULT_ACTION_SUFFIX = ".test.EXTERNAL_PNG_RESULT.";
    public static final String FIXTURE_PREFIX = "atomic-native-open-";
    private static final int MAX_FIXTURE_BYTES = 512;
    private static final String PROVIDER_SUFFIX = ".fileprovider";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent received = getIntent();
        final Uri source = received == null ? null : received.getData();
        if (source == null || !"content".equals(source.getScheme())
                || source.getAuthority() == null || !source.getAuthority().endsWith(PROVIDER_SUFFIX)
                || source.getLastPathSegment() == null || !source.getLastPathSegment().startsWith(FIXTURE_PREFIX)) {
            finish();
            return;
        }
        final String authority = source.getAuthority();
        final String targetPackage = authority.substring(0, authority.length() - PROVIDER_SUFFIX.length());
        final Intent report = new Intent(targetPackage + RESULT_ACTION_SUFFIX + source.getLastPathSegment());
        report.setPackage(targetPackage);
        report.putExtra("receiverPackage", getPackageName());
        report.putExtra("receiverComponent", getComponentName().flattenToString());
        report.putExtra("receiverUid", Process.myUid());
        report.putExtra("action", received.getAction());
        report.putExtra("mime", received.getType());
        report.putExtra("uri", source.toString());
        report.putExtra("flags", received.getFlags());
        final ClipData clip = received.getClipData();
        report.putExtra("clipCount", clip == null ? 0 : clip.getItemCount());
        if (clip != null && clip.getItemCount() == 1) {
            final Uri clipUri = clip.getItemAt(0).getUri();
            report.putExtra("clipUri", clipUri == null ? null : clipUri.toString());
        }
        report.putExtra("readPermission", checkUriPermission(source, Process.myPid(), Process.myUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION));
        report.putExtra("writePermission", checkUriPermission(source, Process.myPid(), Process.myUid(),
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION));

        final ExecutorService worker = Executors.newSingleThreadExecutor();
        worker.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    report.putExtra("bytes", readFixture(source));
                } catch (Exception failure) {
                    report.putExtra("failure", failure.getClass().getName() + ": " + failure.getMessage());
                } finally {
                    worker.shutdown();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            sendBroadcast(report);
                            finish();
                        }
                    });
                }
            }
        });
    }

    private byte[] readFixture(Uri source) throws IOException {
        try (InputStream input = getContentResolver().openInputStream(source)) {
            if (input == null) throw new IOException("No fixture stream");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[MAX_FIXTURE_BYTES];
            int remaining = MAX_FIXTURE_BYTES;
            while (remaining > 0) {
                int count = input.read(buffer, 0, remaining);
                if (count == -1) return output.toByteArray();
                output.write(buffer, 0, count);
                remaining -= count;
            }
            if (input.read() != -1) throw new IOException("Fixture exceeds bounded read");
            return output.toByteArray();
        }
    }
}
