package com.baromemo.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 메모를 폰 안의 파일(notes.json)에 저장한다. 최신 메모가 맨 앞. */
public final class NoteStore {
    private static final String FILE_NAME = "notes.json";

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
                notes.add(new Note(o.getLong("t"), o.getString("x")));
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
        if (raw == null) return false;
        String text = raw.toString().trim();
        if (text.isEmpty()) return false;
        synchronized (NoteStore.class) {
            List<Note> notes = load(ctx);
            notes.add(0, new Note(System.currentTimeMillis(), text));
            save(ctx, notes);
        }
        NoteWidget.updateAll(ctx);
        return true;
    }

    /** 메모 내용 수정. 빈 글이면 그대로 둔다. */
    public static void update(Context ctx, long time, CharSequence raw) {
        if (raw == null) return;
        String text = raw.toString().trim();
        if (text.isEmpty()) return;
        synchronized (NoteStore.class) {
            List<Note> notes = load(ctx);
            for (int i = 0; i < notes.size(); i++) {
                if (notes.get(i).time == time) {
                    notes.set(i, new Note(time, text));
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
                    notes.remove(i);
                    break;
                }
            }
            save(ctx, notes);
        }
        NoteWidget.updateAll(ctx);
    }

    public static String shortTime(long t) {
        return new SimpleDateFormat("M/d (E) HH:mm", Locale.KOREA).format(new Date(t));
    }

    /** Claude 등에 붙여넣어 분석하기 좋은 텍스트 (오래된 순). */
    public static String exportText(Context ctx) {
        List<Note> notes = load(ctx);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd (E) HH:mm", Locale.KOREA);
        StringBuilder sb = new StringBuilder();
        for (int i = notes.size() - 1; i >= 0; i--) {
            Note n = notes.get(i);
            sb.append(fmt.format(new Date(n.time))).append("  ").append(n.text).append('\n');
        }
        return sb.toString();
    }
}
