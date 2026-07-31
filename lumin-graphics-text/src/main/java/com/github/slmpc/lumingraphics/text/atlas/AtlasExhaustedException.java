package com.github.slmpc.lumingraphics.text.atlas;
import com.github.slmpc.lumingraphics.text.font.FontException;

public final class AtlasExhaustedException extends FontException {
    private static final long serialVersionUID = 1L;
    public AtlasExhaustedException(String message) { super(message); }
}
