package com.baromemo.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 메모에 붙은 사진을 다른 앱에 붙여넣을 수 있게 읽기 전용으로 내어 준다.
 * 밖에서 마음대로 열 수는 없고, 사용자가 "사진 복사"를 한 사진만 붙여넣는 앱에 잠깐 허용된다.
 */
public class PhotoProvider extends ContentProvider {
    public static final String AUTHORITY = "com.baromemo.app.photos";

    public static Uri uriFor(String photoName) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(photoName).build();
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        // 파일 이름만 받는다. 폴더를 벗어나는 경로는 거절
        if (name == null || name.contains("/") || name.contains("\\") || name.contains("..")
                || uri.getPathSegments().size() != 1) {
            throw new FileNotFoundException("잘못된 사진 주소");
        }
        File f = NoteStore.photoFile(getContext(), name);
        if (!f.isFile()) throw new FileNotFoundException("사진이 없어요");
        return f;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        String name = String.valueOf(uri.getLastPathSegment()).toLowerCase();
        return name.endsWith(".png") ? "image/png" : "image/jpeg";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        String[] cols = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(cols);
        try {
            File f = fileFor(uri);
            Object[] row = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
                else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
            }
            cursor.addRow(row);
        } catch (FileNotFoundException ignored) {
            // 없는 사진이면 빈 결과
        }
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
