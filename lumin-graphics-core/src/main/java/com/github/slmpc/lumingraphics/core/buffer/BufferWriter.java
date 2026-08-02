package com.github.slmpc.lumingraphics.core.buffer;

import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Bounds-checked little-endian writer for backend-neutral vertex data. */
public final class BufferWriter {
    private final ByteBuffer buffer;

    public BufferWriter(int capacity) {
        if (capacity <= 0) {
            throw new LuminValidationException("buffer capacity must be positive");
        }
        buffer = ByteBuffer.allocateDirect(capacity).order(ByteOrder.LITTLE_ENDIAN);
    }

    public BufferWriter putFloat(float value) {
        require(4);
        buffer.putFloat(value);
        return this;
    }

    public BufferWriter putInt(int value) {
        require(4);
        buffer.putInt(value);
        return this;
    }

    public BufferWriter putRgba8(int value) {
        require(4);
        buffer.put((byte) (value >>> 24))
                .put((byte) (value >>> 16))
                .put((byte) (value >>> 8))
                .put((byte) value);
        return this;
    }

    public int size() {
        return buffer.position();
    }

    public ByteBuffer bytes() {
        return buffer.asReadOnlyBuffer().flip().order(ByteOrder.LITTLE_ENDIAN);
    }

    private void require(int bytes) {
        if (buffer.remaining() < bytes) {
            throw new LuminValidationException("vertex buffer writer overflow");
        }
    }
}
