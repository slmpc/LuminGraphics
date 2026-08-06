package com.github.slmpc.lumingraphics.text.atlas;

import com.github.slmpc.lumingraphics.text.font.FontException;

public final class GlyphUploadException extends FontException {
    private static final long serialVersionUID = 1L;

    public GlyphUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
