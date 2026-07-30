package com.github.slmpc.lumingraphics.testkit;

import com.github.slmpc.prismrhi.backend.BackendApi;
import com.github.slmpc.prismrhi.command.RhiCommandBuffer;
import com.github.slmpc.prismrhi.command.RhiCommandBufferLevel;
import com.github.slmpc.prismrhi.command.RhiCommandPool;
import com.github.slmpc.prismrhi.command.RhiCommandPoolCreateInfo;
import com.github.slmpc.prismrhi.command.RhiDrawCommand;
import com.github.slmpc.prismrhi.rendering.RhiRect2D;
import com.github.slmpc.prismrhi.rendering.RhiViewport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.function.Consumer;

final class RecordingCommandHandlers {
    private RecordingCommandHandlers() {
    }

    static RhiCommandPool commandPool(RhiCommandPoolCreateInfo info, Consumer<String> record) {
        if (info == null) {
            throw new IllegalArgumentException("command pool create info must not be null");
        }
        record.accept("commandPool.create queue=" + info.queueType()
                + " transient=" + info.transientPool() + " reset=" + info.resetCommandBuffer());
        return proxy(RhiCommandPool.class, new PoolHandler(record));
    }

    private static final class PoolHandler implements InvocationHandler {
        private final Consumer<String> record;
        private boolean closed;

        private PoolHandler(Consumer<String> record) {
            this.record = record;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "api" -> BackendApi.VULKAN;
                case "getNativeObject" -> Optional.empty();
                case "allocateCommandBuffer" -> allocate((RhiCommandBufferLevel) arguments[0]);
                case "close" -> close();
                case "toString" -> "RecordingRhiCommandPool";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw RecordingRhiDevice.unsupported("RhiCommandPool", method);
            };
        }

        private RhiCommandBuffer allocate(RhiCommandBufferLevel level) {
            requireOpen();
            if (level == null) {
                throw new IllegalArgumentException("command buffer level must not be null");
            }
            record.accept("command.allocate level=" + level);
            return proxy(RhiCommandBuffer.class, new BufferHandler(level, record));
        }

        private Object close() {
            if (!closed) {
                closed = true;
                record.accept("commandPool.close");
            }
            return null;
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("recording command pool is closed");
            }
        }
    }

    private static final class BufferHandler implements InvocationHandler {
        private final RhiCommandBufferLevel level;
        private final Consumer<String> record;
        private boolean recording;
        private boolean closed;

        private BufferHandler(RhiCommandBufferLevel level, Consumer<String> record) {
            this.level = level;
            this.record = record;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "api" -> BackendApi.VULKAN;
                case "getNativeObject" -> Optional.empty();
                case "level" -> level;
                case "begin" -> begin();
                case "reset" -> reset();
                case "end" -> end();
                case "setViewport" -> viewport((RhiViewport) arguments[0]);
                case "setScissor" -> scissor((RhiRect2D) arguments[0]);
                case "draw" -> draw((RhiDrawCommand) arguments[0]);
                case "close" -> close();
                case "toString" -> "RecordingRhiCommandBuffer[" + level + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw RecordingRhiDevice.unsupported("RhiCommandBuffer", method);
            };
        }

        private Object begin() {
            requireOpen();
            if (recording) {
                throw new IllegalStateException("recording command buffer is already begun");
            }
            recording = true;
            record.accept("command.begin level=" + level);
            return null;
        }

        private Object reset() {
            requireOpen();
            recording = false;
            record.accept("command.reset");
            return null;
        }

        private Object end() {
            requireRecording();
            recording = false;
            record.accept("command.end");
            return null;
        }

        private Object viewport(RhiViewport viewport) {
            requireRecording();
            if (viewport == null) {
                throw new IllegalArgumentException("viewport must not be null");
            }
            record.accept("command.viewport x=" + viewport.x() + " y=" + viewport.y()
                    + " width=" + viewport.width() + " height=" + viewport.height());
            return null;
        }

        private Object scissor(RhiRect2D scissor) {
            requireRecording();
            if (scissor == null) {
                throw new IllegalArgumentException("scissor must not be null");
            }
            record.accept("command.scissor x=" + scissor.offset().x() + " y=" + scissor.offset().y()
                    + " width=" + scissor.extent().width() + " height=" + scissor.extent().height());
            return null;
        }

        private Object draw(RhiDrawCommand draw) {
            requireRecording();
            if (draw == null) {
                throw new IllegalArgumentException("draw command must not be null");
            }
            record.accept("command.draw vertices=" + draw.vertexCount() + " instances=" + draw.instanceCount()
                    + " firstVertex=" + draw.firstVertex() + " firstInstance=" + draw.firstInstance());
            return null;
        }

        private Object close() {
            if (!closed) {
                closed = true;
                recording = false;
                record.accept("command.close");
            }
            return null;
        }

        private void requireRecording() {
            requireOpen();
            if (!recording) {
                throw new IllegalStateException("recording command buffer is not begun");
            }
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("recording command buffer is closed");
            }
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
