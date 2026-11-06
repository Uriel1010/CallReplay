package com.kitsumed.shizucallrecorder;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.database.Cursor;
import android.provider.OpenableColumns;
import java.io.InputStream;
import java.security.MessageDigest;

/** Independent APK process: framework/Java only, without the target's runtime. */
public class ShareAttachmentReceiver extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Intent reply = new Intent(getIntent().getStringExtra("reply"))
                .setPackage("io.github.uriel1010.callreplay");
        try {
            Uri uri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder hash = new StringBuilder();
            for (byte b : digest.digest()) hash.append(String.format("%02x", b));
            try (Cursor cursor = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
                if (!cursor.moveToFirst()) throw new IllegalStateException("Missing file metadata");
                reply.putExtra("name", cursor.getString(0)).putExtra("size", cursor.getLong(1));
            }
            reply.putExtra("hash", hash.toString()).putExtra("mime", getContentResolver().getType(uri));
            reply.putExtra("receiverUid", android.os.Process.myUid());
            try {
                getContentResolver().openOutputStream(uri, "wa").close();
                reply.putExtra("writeDenied", false);
            } catch (SecurityException expected) { reply.putExtra("writeDenied", true); }
        } catch (Exception failure) { reply.putExtra("failed", true); }
        sendBroadcast(reply);
        finish();
    }
}
