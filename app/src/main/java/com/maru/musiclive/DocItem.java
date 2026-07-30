package com.maru.musiclive;

public final class DocItem {
    public final String uri;
    public final String name;

    public DocItem(String uri, String name) {
        this.uri = uri;
        this.name = name == null ? "" : name;
    }
}
