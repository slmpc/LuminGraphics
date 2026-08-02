package com.github.slmpc.lumingraphics.core.buffer;

import com.github.slmpc.lumingraphics.core.exception.LuminResourceClosedException;
import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;
import com.github.slmpc.prismrhi.device.RhiDevice;
import com.github.slmpc.prismrhi.resource.RhiBuffer;
import com.github.slmpc.prismrhi.resource.RhiBufferCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiBufferUsage;
import com.github.slmpc.prismrhi.resource.RhiMemoryUsage;

import java.nio.ByteBuffer;

/** A fixed-capacity frame ring that refuses to overwrite in-flight GPU data. */
public final class LuminRingBuffer implements AutoCloseable {
    private final RhiBuffer[] slots;
    private final long[] lastFrames;
    private final int capacity;
    private boolean closed;
    private boolean frameActive;
    private int currentSlot;
    private int offset;
    private long activeFrameId;
    private long completedFrameId;

    public LuminRingBuffer(RhiDevice device, int capacity, int slotCount) {
        if (device == null || capacity <= 0 || slotCount < 2) {
            throw new LuminValidationException("ring device, capacity, or slot count is invalid");
        }
        this.capacity = capacity;
        this.slots = new RhiBuffer[slotCount];
        this.lastFrames = new long[slotCount];
        java.util.Arrays.fill(lastFrames, Long.MIN_VALUE);
        RhiBufferCreateInfo info = RhiBufferCreateInfo.builder(capacity)
                .usage(RhiBufferUsage.VERTEX_BUFFER)
                .usage(RhiBufferUsage.TRANSFER_DST)
                .memoryUsage(RhiMemoryUsage.CPU_TO_GPU)
                .build();
        int created = 0;
        try {
            for (; created < slotCount; created++) {
                slots[created] = device.createBuffer(info);
            }
        } catch (RuntimeException failure) {
            for (int i = created - 1; i >= 0; i--) {
                slots[i].close();
            }
            throw failure;
        }
    }

    public void beginFrame(long frameId, long completedFrameId) {
        requireOpen();
        if (frameActive || frameId < 0 || completedFrameId >= frameId) {
            throw new IllegalStateException("ring frame transition is invalid");
        }
        int candidate = Math.floorMod(frameId, slots.length);
        if (lastFrames[candidate] > completedFrameId) {
            throw new IllegalStateException("ring slot is still in flight");
        }
        currentSlot = candidate;
        lastFrames[candidate] = frameId;
        offset = 0;
        activeFrameId = frameId;
        this.completedFrameId = completedFrameId;
        frameActive = true;
    }

    public Allocation write(ByteBuffer source, int alignment) {
        requireActive();
        if (source == null || alignment <= 0 || (alignment & (alignment - 1)) != 0) {
            throw new LuminValidationException("ring write source or alignment is invalid");
        }
        int aligned = Math.addExact(offset, alignment - 1) & -alignment;
        int length = source.remaining();
        if (length > capacity) {
            throw new IllegalArgumentException("ring capacity exceeded");
        }
        if (length > capacity - aligned) {
            advanceSlot();
            aligned = 0;
        }
        slots[currentSlot].write(aligned, source.slice());
        offset = aligned + length;
        return new Allocation(slots[currentSlot], currentSlot, aligned, length);
    }

    public void endFrame() {
        requireActive();
        frameActive = false;
    }

    public boolean frameActive() {
        return frameActive;
    }

    private void requireActive() {
        requireOpen();
        if (!frameActive) throw new IllegalStateException("ring frame is not active");
    }

    private void advanceSlot() {
        for (int index = 1; index < slots.length; index++) {
            int candidate = Math.floorMod(currentSlot + index, slots.length);
            if (lastFrames[candidate] <= completedFrameId) {
                currentSlot = candidate;
                lastFrames[candidate] = activeFrameId;
                offset = 0;
                return;
            }
        }
        throw new IllegalArgumentException("ring capacity exceeded");
    }

    private void requireOpen() {
        if (closed) throw new LuminResourceClosedException("ring buffer is closed");
    }

    @Override
    public void close() {
        if (closed) return;
        if (frameActive) throw new IllegalStateException("cannot close ring buffer during a frame");
        closed = true;
        RuntimeException failure = null;
        for (int i = slots.length - 1; i >= 0; i--) {
            try { slots[i].close(); } catch (RuntimeException closeFailure) {
                if (failure == null) failure = closeFailure; else failure.addSuppressed(closeFailure);
            }
        }
        if (failure != null) throw failure;
    }

    public record Allocation(RhiBuffer buffer, int slot, int offset, int length) { }
}
