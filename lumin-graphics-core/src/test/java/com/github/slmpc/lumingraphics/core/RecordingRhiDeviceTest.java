package com.github.slmpc.lumingraphics.core;

import com.github.slmpc.lumingraphics.testkit.RecordingRhiDevice;
import com.github.slmpc.lumingraphics.testkit.UnsupportedRhiCallException;
import com.github.slmpc.prismrhi.resource.RhiNativeObjectType;
import com.github.slmpc.prismrhi.resource.RhiOwnership;
import com.github.slmpc.prismrhi.resource.RhiResource;
import com.github.slmpc.prismrhi.command.RhiCommandBuffer;
import com.github.slmpc.prismrhi.command.RhiCommandBufferLevel;
import com.github.slmpc.prismrhi.command.RhiCommandPool;
import com.github.slmpc.prismrhi.command.RhiCommandPoolCreateInfo;
import com.github.slmpc.prismrhi.command.RhiDrawCommand;
import com.github.slmpc.prismrhi.queue.RhiQueueType;
import com.github.slmpc.prismrhi.rendering.RhiRect2D;
import com.github.slmpc.prismrhi.rendering.RhiViewport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecordingRhiDeviceTest {
    @Test
    void recordsStableSemanticNativeOwnershipTraceAndFailsUnsupportedCalls() {
        RecordingRhiDevice fake = new RecordingRhiDevice(20, "trace");
        RhiResource resource = fake.resource(RhiResource.class, "atlas", RhiOwnership.OWNED);
        RhiNativeObjectType type = new RhiNativeObjectType(99, "test-handle");

        assertEquals(101, resource.getNativeObject(type).orElseThrow().value());
        resource.close();
        resource.close();
        RhiCommandPool pool = fake.device().createCommandPool(
                new RhiCommandPoolCreateInfo(RhiQueueType.GRAPHICS, true, true)
        );
        RhiCommandBuffer commands = pool.allocateCommandBuffer(RhiCommandBufferLevel.PRIMARY);
        commands.begin();
        commands.setViewport(RhiViewport.of(320, 200));
        commands.setScissor(RhiRect2D.of(2, 3, 40, 50));
        commands.draw(new RhiDrawCommand(6, 1, 0, 0));
        commands.end();
        commands.close();
        pool.close();
        fake.device().waitIdle();
        assertThrows(UnsupportedRhiCallException.class, () -> fake.device().queue(null));

        assertEquals(List.of(
                "resource.create name=atlas ownership=OWNED native=101",
                "resource.close name=atlas ownership=OWNED deleter=1",
                "commandPool.create queue=GRAPHICS transient=true reset=true",
                "command.allocate level=PRIMARY",
                "command.begin level=PRIMARY",
                "command.viewport x=0.0 y=0.0 width=320.0 height=200.0",
                "command.scissor x=2 y=3 width=40 height=50",
                "command.draw vertices=6 instances=1 firstVertex=0 firstInstance=0",
                "command.end",
                "command.close",
                "commandPool.close",
                "device.waitIdle"
        ), fake.trace());
        assertEquals(1, fake.deleterCount("atlas"));
    }
}
