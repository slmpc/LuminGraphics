package com.github.slmpc.lumingraphics.render.resource;

/**
 * Lumin 渲染资源在写入或提交命令前报告的上下文错误。
 */
public final class RenderResourceException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public enum Code {
        UNKNOWN_PIPELINE,
        TARGET_CONTEXT_MISMATCH,
        TARGET_DIMENSION_MISMATCH,
        MISSING_DESCRIPTOR,
        UNBALANCED_RENDER_PASS,
        FRAME_STATE
    }

    private final Code code;

    public RenderResourceException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public RenderResourceException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
