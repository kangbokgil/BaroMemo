package com.baromemo.app;

public class Note {
    public final long time;
    public final String text;
    /** 첨부 사진 파일 이름 (앱 내부 photos 폴더). 없으면 null. */
    public final String photo;

    public Note(long time, String text) {
        this(time, text, null);
    }

    public Note(long time, String text, String photo) {
        this.time = time;
        this.text = text;
        this.photo = photo;
    }
}
