package com.maru.musiclive;

public final class SongItem {
    public final String uri;
    public final String title;

    public SongItem(String uri, String title) {
        this.uri = uri;
        this.title = title == null || title.trim().isEmpty() ? "제목 없음" : title;
    }
}
