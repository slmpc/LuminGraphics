package com.github.slmpc.lumingraphics.core.threading;

import com.github.slmpc.lumingraphics.core.exception.LuminThreadException;
import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;

import java.util.concurrent.Executor;

public final class RenderThreadGate {
    private final Thread renderThread;
    private final Executor executor;

    public RenderThreadGate(Thread renderThread, Executor executor) {
        if (renderThread == null || executor == null) {
            throw new LuminValidationException("render thread and executor must not be null");
        }
        this.renderThread = renderThread;
        this.executor = executor;
    }

    public boolean isRenderThread() {
        return Thread.currentThread() == renderThread;
    }

    public void requireRenderThread() {
        if (!isRenderThread()) {
            throw new LuminThreadException(
                    "render-thread access required; expected " + renderThread.getName()
                            + ", got " + Thread.currentThread().getName()
            );
        }
    }

    public void runNow(Runnable action) {
        requireRenderThread();
        requireAction(action).run();
    }

    public void execute(Runnable action) {
        requireAction(action);
        executor.execute(() -> {
            requireRenderThread();
            action.run();
        });
    }

    private static Runnable requireAction(Runnable action) {
        if (action == null) {
            throw new LuminValidationException("render action must not be null");
        }
        return action;
    }
}
