package com.baromemo.app;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** 메모와 사진을 zip 파일 하나로 백업하고, 그 파일에서 되살린다. */
public final class Backup {
    private Backup() {}

    public static boolean export(Context ctx, Uri target) {
        try (OutputStream os = ctx.getContentResolver().openOutputStream(target);
             ZipOutputStream zip = new ZipOutputStream(os)) {
            zip.putNextEntry(new ZipEntry("notes.json"));
            File notes = new File(ctx.getFilesDir(), "notes.json");
            if (notes.exists()) {
                try (InputStream in = new FileInputStream(notes)) {
                    copy(in, zip);
                }
            } else {
                zip.write("[]".getBytes(StandardCharsets.UTF_8));
            }
            zip.closeEntry();

            File[] photos = new File(ctx.getFilesDir(), "photos").listFiles();
            if (photos != null) {
                for (File f : photos) {
                    if (!f.isFile()) continue;
                    zip.putNextEntry(new ZipEntry("photos/" + f.getName()));
                    try (InputStream in = new FileInputStream(f)) {
                        copy(in, zip);
                    }
                    zip.closeEntry();
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 백업 파일의 메모를 지금 메모에 합친다. 더해진 메모 수, 실패하면 -1. */
    public static int restore(Context ctx, Uri source) {
        try (InputStream is = ctx.getContentResolver().openInputStream(source);
             ZipInputStream zip = new ZipInputStream(is)) {
            File dir = new File(ctx.getFilesDir(), "photos");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            String json = null;
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String name = e.getName();
                if (e.isDirectory()) continue;
                if (name.equals("notes.json")) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    copy(zip, buf);
                    json = new String(buf.toByteArray(), StandardCharsets.UTF_8);
                } else if (name.startsWith("photos/")) {
                    String fileName = name.substring("photos/".length());
                    // 사진 폴더 밖으로 나가는 이름은 받지 않는다
                    if (!fileName.matches("[A-Za-z0-9_.-]+") || fileName.contains("..")) continue;
                    File f = new File(dir, fileName);
                    if (f.exists()) continue;
                    try (OutputStream out = new FileOutputStream(f)) {
                        copy(zip, out);
                    }
                }
            }
            if (json == null) return -1;
            return NoteStore.importNotes(ctx, json);
        } catch (Exception ex) {
            return -1;
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }
}
