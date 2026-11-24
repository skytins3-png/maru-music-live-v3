package com.maru.musiclive;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Copies user-accessible audio to Music/MARU_BIGO so the file is visible to
 * Android's public music picker. BIGO may or may not offer an audio picker.
 * No arbitrary filesystem access, accessibility automation or mic injection.
 */
public final class BigoMusicExporter {
    private BigoMusicExporter() {}

    public static final class Result {
        public final int copied;
        public final int alreadyPresent;
        public final int failed;
        public final int unsupported;
        public final int attempted;

        Result(int copied, int alreadyPresent, int failed, int unsupported, int attempted) {
            this.copied=copied;
            this.alreadyPresent=alreadyPresent;
            this.failed=failed;
            this.unsupported=unsupported;
            this.attempted=attempted;
        }
    }

    public static Result export(Context context, List<String> songUris, int maxSongs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            return new Result(0,0,0,songUris.size(),songUris.size());
        ContentResolver resolver=context.getContentResolver();
        int copied=0, present=0, failed=0, unsupported=0, attempted=0;
        for (String raw : songUris) {
            if (attempted >= maxSongs) break;
            attempted++;
            Uri source;
            try { source=Uri.parse(raw); }
            catch (Exception exception) { failed++;continue; }
            String fileName=BigoMusicExportPlan.safeName(fileName(resolver,source),raw);
            if (fileName.isEmpty()) { unsupported++;continue; }
            try {
                if (exists(resolver,fileName)) { present++;continue; }
                ContentValues metadata=new ContentValues();
                metadata.put(MediaStore.Audio.Media.DISPLAY_NAME,fileName);
                metadata.put(MediaStore.Audio.Media.MIME_TYPE,BigoMusicExportPlan.mime(fileName));
                metadata.put(MediaStore.Audio.Media.RELATIVE_PATH,BigoMusicExportPlan.ALBUM_PATH);
                metadata.put(MediaStore.Audio.Media.IS_PENDING,1);
                Uri dest=resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,metadata);
                if (dest == null) { failed++;continue; }
                boolean success=false;
                try {
                    try (InputStream in=resolver.openInputStream(source);
                         OutputStream out=resolver.openOutputStream(dest,"w")) {
                        if (in==null || out==null) throw new IOException("Cannot open audio source or destination");
                        byte[] buffer=new byte[64*1024];
                        int length;
                        long lengthTotal=0L;
                        while ((length=in.read(buffer)) != -1) {
                            if (length==0) continue;
                            out.write(buffer,0,length);
                            lengthTotal+=length;
                        }
                        if (lengthTotal==0L) throw new IOException("Empty original song");
                    }
                    ContentValues ready=new ContentValues();
                    ready.put(MediaStore.Audio.Media.IS_PENDING,0);
                    success=resolver.update(dest,ready,null,null)>0;
                } finally {
                    // Never touch the original; remove a *new* half-written file.
                    if (!success) resolver.delete(dest,null,null);
                }
                if (success) copied++; else failed++;
            } catch (Exception exception) {
                failed++;
            }
        }
        return new Result(copied,present,failed,unsupported,attempted);
    }

    private static String fileName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor=resolver.query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if (cursor!=null && cursor.moveToFirst()) {
                String name=cursor.getString(0);
                if (name!=null && !name.isEmpty())return name;
            }
        } catch (Exception ignored) {}
        String segment=uri.getLastPathSegment();
        return segment==null?"":segment;
    }

    private static boolean exists(ContentResolver resolver,String name) {
        String where=MediaStore.Audio.Media.RELATIVE_PATH+"=? AND "
                +MediaStore.Audio.Media.DISPLAY_NAME+"=?";
        try (Cursor cursor=resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media._ID},
                where,new String[]{BigoMusicExportPlan.ALBUM_PATH,name},null)) {
            return cursor!=null && cursor.moveToFirst();
        }
    }
}
