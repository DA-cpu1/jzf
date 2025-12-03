package com.example.textsimplifier.model;

public class SimplifyRequest {
    public String text;
    public int compressRatio = 40; // remove 40% by default
    public double dupThreshold = 0.8;
    public boolean dedupe = true;
    public boolean preserveOrder = true;
    public boolean cleanFillers = true;

    public SimplifyRequest() {}
}