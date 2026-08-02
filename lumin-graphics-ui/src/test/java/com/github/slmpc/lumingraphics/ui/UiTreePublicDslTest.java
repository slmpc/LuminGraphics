package com.github.slmpc.lumingraphics.ui;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.control.Button;
import com.github.slmpc.lumingraphics.ui.control.Input;
import com.github.slmpc.lumingraphics.ui.node.container.Layer;
import com.github.slmpc.lumingraphics.ui.node.container.Scissor;
import com.github.slmpc.lumingraphics.ui.node.primitive.MarqueeText;
import com.github.slmpc.lumingraphics.ui.node.primitive.Rect;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import com.github.slmpc.lumingraphics.ui.viewport.UiViewportTarget;
import com.github.slmpc.lumingraphics.ui.viewport.Viewport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class UiTreePublicDslTest {
    private static final LuminColor WHITE = new LuminColor(1, 1, 1, 1);

    @Test
    void existingContainersPreserveResolvedBounds() {
        UiViewportTarget target = new NoopViewportTarget();

        UiTree tree = UiTree.build(scope -> scope.pushAbsolute(new UiRect(10, 20, 100, 80), panel -> {
            panel.layer(4, layer -> layer.rect(2, 3, 8, 9, WHITE));
            panel.viewport(target, new UiRect(5, 6, 40, 30), 7, 20, 70,
                    content -> content.rect(1, 2, 3, 4, WHITE));
        }));

        Layer layer = (Layer) tree.nodes().get(0);
        Rect layeredRect = (Rect) layer.children().get(0);
        Viewport viewport = (Viewport) tree.nodes().get(1);
        Rect viewportRect = (Rect) viewport.children().get(0);
        assertEquals(new UiRect(12, 23, 8, 9), layeredRect.bounds());
        assertEquals(new UiRect(15, 26, 40, 30), viewport.viewport());
        assertEquals(new UiRect(16, 21, 3, 4), viewportRect.bounds());
    }

    @Test
    void conditionalScissorKeepsTheSameScopeWhenDisabled() {
        UiTree unclipped = UiTree.build(scope -> scope.pushAbsolute(10, 20, panel ->
                panel.scissorIf(false, new UiRect(1, 2, 30, 40),
                        content -> content.rect(3, 4, 5, 6, WHITE))));
        UiTree clipped = UiTree.build(scope -> scope.pushAbsolute(10, 20, panel ->
                panel.scissorIf(true, 1, 2, 30, 40,
                        content -> content.rect(3, 4, 5, 6, WHITE))));

        assertEquals(new UiRect(13, 24, 5, 6), ((Rect) unclipped.nodes().get(0)).bounds());
        Scissor scissor = (Scissor) clipped.nodes().get(0);
        assertEquals(new UiRect(11, 22, 30, 40), scissor.clip());
        assertEquals(new UiRect(13, 24, 5, 6), ((Rect) scissor.children().get(0)).bounds());
    }

    @Test
    void stackCallbacksReceiveAbsoluteBoundsAndScopedCoordinates() {
        UiTree.Scope scope = new UiTree.Scope();
        UiTree.Scope.Stack stack = scope.stack(new UiRect(10, 20, 50, 100));
        UiRect[] callbackBounds = new UiRect[2];

        stack.item(12, (bounds, item) -> {
            callbackBounds[0] = bounds;
            item.rect(1, 2, 3, 4, WHITE);
        });
        stack.item(8, 5, (bounds, item) -> callbackBounds[1] = bounds);

        assertEquals(new UiRect(10, 20, 50, 12), callbackBounds[0]);
        assertEquals(new UiRect(10, 32, 50, 8), callbackBounds[1]);
        assertEquals(45, stack.cursor());
        assertEquals(new UiRect(11, 22, 3, 4), ((Rect) scope.snapshot().nodes().get(0)).bounds());
    }

    @Test
    void controlAndTextConveniencesCreateGenericNodes() {
        UiTree tree = UiTree.build(scope -> scope.pushAbsolute(10, 20, panel -> {
            panel.button(new UiRect(1, 2, 30, 12), 6, WHITE, "Apply", 0.5f, WHITE);
            panel.input(new UiRect(2, 16, 40, 14), true, 0.7f,
                    5, "value", 0.6f, WHITE, 3, WHITE, "ms", 0.5f, WHITE);
            panel.input(new UiRect(2, 32, 40, 14), true, 0.7f,
                    1, WHITE, 2, 5, "value", 0.6f, WHITE,
                    null, null, 3, WHITE, "ms", 0.5f, WHITE);
            panel.marqueeText("overflow", 3, 50, 0.6f, WHITE, new UiRect(2, 48, 20, 10));
        }));

        assertEquals(new UiRect(11, 22, 30, 12), ((Button) tree.nodes().get(0)).element().bounds());
        assertEquals(new UiRect(12, 36, 40, 14), ((Input) tree.nodes().get(1)).element().bounds());
        assertEquals(new UiRect(12, 52, 40, 14), ((Input) tree.nodes().get(2)).element().bounds());
        MarqueeText marquee = (MarqueeText) tree.nodes().get(3);
        assertNull(marquee.fontId());
        assertEquals(new UiRect(12, 68, 20, 10), marquee.clip());
    }

    @Test
    void viewportForwardsMouseCoordinatesAndRejectsMalformedBounds() {
        UiViewportTarget target = new NoopViewportTarget();
        UiTree tree = UiTree.build(scope -> scope.viewport(target, new UiRect(1, 2, 30, 40),
                5, 20, 80, 123, 456, content -> content.rect(0, 0, 1, 1, WHITE)));

        Viewport viewport = (Viewport) tree.nodes().get(0);
        assertSame(target, viewport.buffer());
        assertEquals(123, viewport.mouseX());
        assertEquals(456, viewport.mouseY());
        assertThrows(IllegalArgumentException.class, () -> UiTree.build(scope ->
                scope.scissor(Float.NaN, 0, 1, 1, ignored -> { })));
        assertThrows(IllegalArgumentException.class, () -> UiTree.build(scope ->
                scope.viewport(target, new UiRect(0, 0, 1, 1), Float.NaN, 0, 1,
                        0, 0, ignored -> { })));
    }

    private static final class NoopViewportTarget implements UiViewportTarget {
        @Override
        public void begin(UiRect viewport) {
        }

        @Override
        public void render(UiTree tree) {
        }

        @Override
        public void queue(UiRect viewport, float scroll, float maxScroll, float contentHeight,
                          int mouseX, int mouseY) {
        }
    }
}
