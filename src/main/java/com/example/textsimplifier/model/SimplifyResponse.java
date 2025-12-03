package com.example.textsimplifier.model;

import java.util.List;

public class SimplifyResponse {
    public String text;
    public List<String> removed;
    public int origLen;
    public int newLen;

    public SimplifyResponse() {}
    public SimplifyResponse(String text, List<String> removed, int origLen, int newLen) {
        this.text = text;
        this.removed = removed;
        this.origLen = origLen;
        this.newLen = newLen;
    }
}