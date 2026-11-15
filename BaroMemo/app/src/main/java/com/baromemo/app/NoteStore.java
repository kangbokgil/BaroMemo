package com.baromemo.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 메모를 폰 안의 파일(notes.json)에, 사진을 photos 폴더에 저장한다. 최신 메모가 맨 앞. */
public final class NoteStore {
    private static final String FILE_NAME = "notes.json";
    private static final String PHOTO_DIR = "photos";
    private static final int MAX_PHOTO_SIDE = 1600;

    private NoteStore() {}

    public static synchronized List<Note> load(Context ctx) {
        List<Note> notes = new ArrayList<>();
        File f = new File(ctx.getFilesDir(), FILE_NAME);
        if (!f.exists()) return notes;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int off = 0;
            while (off < buf.length) {
                int r = in.read(buf, off, buf.length - off);
                if (r < 0) break;
                off += r;
            }
            JSONArray arr = new JSONArray(new String(buf, 0, off, StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String photo = o.has("p") ? o.getString("p") : null;
                notes.add(new Note(o.getLong("t"), o.getString("x"), photo));
            }
        } catch (IOException | JSONException e) {
            // 읽기 실패 시 빈 목록
        }
        return notes;
    }

    private static void save(Context ctx, List<Note> notes) {
        JSONArray arr = new JSONArray();
        try {
            for (Note n : notes) {
                JSONObject o = new JSONObject();
                o.put("t", n.time);
                o.put("x", n.text);
                if (n.photo != null) o.put("p", n.photo);
                arr.put(o);
            }
        } catch (JSONException e) {
            return;
        }
        File tmp = new File(ctx.getFilesDir(), FILE_NAME + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(new File(ctx.getFilesDir(), FILE_NAME));
    }

    /** 메모 추가 후 위젯도 갱신. 빈 글이면 false. */
    public static boolean add(Context ctx, CharSequence raw) {
        return add(ctx, raw, null);
    }

    /** 사진이 있으면 글이 비어 있어도 저장한다. */
    public static boolean add(Context ctx, CharSequence raw, String photo) {
        String text = raw == null ? "" : raw.toString().trim();
        if (text.isEmpty() && photo == null) return false;
        synchronized (NoteStore.class) {
            List<Note> notes = load(ctx);
            notes.add(0, new Note(System.currentTimeMillis(), text, photo));
            save(ctx, notes);
        }
        NoteWidget.updateAll(ctx);
        return true;
    }

    /** 메모 내용 수정. 사진 없는 메모를 빈 글로 바꾸려 하면 그대로 둔다. */
    public static void update(Context ctx, long time, CharSequence raw) {
        if (raw == null) return;
        String text = raw.toString().trim();
        synchronized (NoteStore.class) {
            List<Note> notes = load(ctx);
            for (int i = 0; i < notes.size(); i++) {
                Note old = notes.get(i);
                if (old.time == time) {
                    if (text.isEmpty() && old.photo == null) return;
                    notes.set(i, new Note(time, text, old.photo));
                    break;
                }
            }
            save(ctx, notes);
        }
        NoteWidget.updateAll(ctx);
    }

    public static void delete(Context ctx, long time) {
        synchronized (NoteStore.class) {
            List<Note> notes = load(ctx);
            for (int i = 0; i < notes.size(); i++) {
                if (notes.get(i).time == time) {
                    Note removed = notes.remove(i);
                    if (removed.photo != null) {
                        //noinspection ResultOfMethodCallIgnored
                        photoFile(ctx, removed.photo).delete();
                    }
                    break;
                }
            }
            save(ctx, notes);
        }
        NoteWidget.updateAll(ctx);
    }

    // ───── 사진 ─────

    public static File photoFile(Context ctx, String name) {
        return new File(new File(ctx.getFilesDir(), PHOTO_DIR), name);
    }

    /** 고른 사진을 줄여서 앱 안으로 복사한다. 성공하면 파일 이름, 실패하면 null. */
    public static String importPhoto(Context ctx, Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > MAX_PHOTO_SIDE) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                bmp = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bmp == null) return null;

            // 카메라 사진의 회전 정보 반영
            int rotation = 0;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in != null) {
                    int o = new ExifInterface(in).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    if (o == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                    else if (o == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                    else if (o == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
                }
            } catch (Exception e) {
                // 회전 정보를 못 읽으면 그대로 둔다
            }
            if (rotation != 0) {
                Matrix m = new Matrix();
                m.postRotate(rotation);
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            }

            File dir = new File(ctx.getFilesDir(), PHOTO_DIR);
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            String name = System.currentTimeMillis() + ".jpg";
            try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out);
            }
            return name;
        } catch (Exception e) {
            return null;
        }
    }

    /** 목록·보기용으로 사진을 줄여서 읽는다. 없거나 실패하면 null. */
    public static Bitmap loadPhoto(Context ctx, String name, int sample) {
        File f = photoFile(ctx, name);
        if (!f.exists()) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
    }

    // ───── 표시·내보내기 ─────

    public static String shortTime(long t) {
        return new SimpleDateFormat("M/d (E) HH:mm", Locale.KOREA).format(new Date(t));
    }

    /** Claude 등에 붙여넣어 분석하기 좋은 텍스트 (오래된 순). 사진은 [사진]으로만 표시. */
    public static String exportText(Context ctx) {
        List<Note> notes = load(ctx);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd (E) HH:mm", Locale.KOREA);
        StringBuilder sb = new StringBuilder();
        for (int i = notes.size() - 1; i >= 0; i--) {
            Note n = notes.get(i);
            sb.append(fmt.format(new Date(n.time))).append("  ").append(n.text);
            if (n.photo != null) sb.append(n.text.isEmpty() ? "[사진]" : " [사진]");
            sb.append('\n');
        }
        return sb.toString();
    }
}
