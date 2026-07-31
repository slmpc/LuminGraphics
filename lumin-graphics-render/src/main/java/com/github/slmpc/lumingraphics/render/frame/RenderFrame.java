package com.github.slmpc.lumingraphics.render.frame;

import com.github.slmpc.lumingraphics.render.resource.RenderResources;
import com.github.slmpc.prismrhi.command.RhiCommandBuffer;

/**
 * 一帧 Prism 命令缓冲的 begin/end 配对。
 *
 * <p>构造器立即开始命令缓冲，{@link #close()} 结束它。本类型不会提交命令缓冲、等待队列或拥有
 * Prism 设备；这些仍由调用方负责。</p>
 */
public final class RenderFrame implements AutoCloseable {
    private final RhiCommandBuffer commands;
    private final RenderExecution execution;
    private boolean closed;

    /**
     * 开始一个命令缓冲并创建该帧的执行上下文。
     *
     * @param commands 尚未开始的调用方命令缓冲
     * @param resources 此帧 renderer 使用的资源提供者
     * @param frameId 当前递增帧号
     * @param completedFrameId 最近完成的帧号
     * @param width 当前 framebuffer 宽度
     * @param height 当前 framebuffer 高度
     */
    public RenderFrame(RhiCommandBuffer commands, RenderResources resources, long frameId,
                       long completedFrameId, int width, int height) {
        if (commands == null) throw new IllegalArgumentException("commands must not be null");
        this.commands = commands;
        this.execution = new RenderExecution(commands, resources, frameId, completedFrameId, width, height);
        commands.begin();
    }

    /** 返回供 renderer 写入命令的帧执行上下文。 */
    public RenderExecution execution() {
        if (closed) throw new IllegalStateException("render frame is closed");
        return execution;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        commands.end();
    }
}
