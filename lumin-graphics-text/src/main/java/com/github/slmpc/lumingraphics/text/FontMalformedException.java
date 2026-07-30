package com.github.slmpc.lumingraphics.text;

public final class FontMalformedException extends FontException {
    private static final long serialVersionUID = 1L;
    public FontMalformedException(String message) { super(message); }
    public FontMalformedException(String message, Throwable cause) { super(message, cause); }
}
