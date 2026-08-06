package com.github.slmpc.lumingraphics.render.scheduler;

import com.github.slmpc.lumingraphics.render.frame.RenderExecution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Render3DScheduler {
    private final List<Command> commands = new ArrayList<>();
    private long sequence;
    private boolean flushed;

    public void add(int priority, Render3DAction action) {
        if (action == null) throw new IllegalArgumentException("3D action is null");
        commands.add(new Command(priority, sequence++, action));
        flushed = false;
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }

    public void clear() {
        commands.clear();
        sequence = 0;
        flushed = false;
    }

    public void flush(RenderExecution execution) {
        if (commands.isEmpty()) return;
        if (flushed) throw new IllegalStateException("3D commands were already flushed");
        flushed = true;
        commands.stream().sorted(Comparator.comparingInt(Command::priority).thenComparingLong(Command::sequence))
                .forEach(command -> command.action().render(execution));
    }

    public void flushAndClear(RenderExecution execution) {
        try {
            flush(execution);
        } finally {
            clear();
        }
    }

    @FunctionalInterface
    public interface Render3DAction {
        void render(RenderExecution execution);
    }

    private record Command(int priority, long sequence, Render3DAction action) {
    }
}
