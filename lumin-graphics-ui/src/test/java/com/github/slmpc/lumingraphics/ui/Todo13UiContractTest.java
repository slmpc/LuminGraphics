package com.github.slmpc.lumingraphics.ui;
import com.github.slmpc.lumingraphics.ui.animation.UiAnimation;
import com.github.slmpc.lumingraphics.ui.control.AssistChip;
import com.github.slmpc.lumingraphics.ui.control.Button;
import com.github.slmpc.lumingraphics.ui.control.ButtonElement;
import com.github.slmpc.lumingraphics.ui.control.FilledField;
import com.github.slmpc.lumingraphics.ui.control.IconButton;
import com.github.slmpc.lumingraphics.ui.control.Input;
import com.github.slmpc.lumingraphics.ui.control.InputElement;
import com.github.slmpc.lumingraphics.ui.control.PopupCard;
import com.github.slmpc.lumingraphics.ui.control.SegmentedControl;
import com.github.slmpc.lumingraphics.ui.control.Slider;
import com.github.slmpc.lumingraphics.ui.control.Switch;
import com.github.slmpc.lumingraphics.ui.control.SwitchElement;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.node.container.Layer;
import com.github.slmpc.lumingraphics.ui.node.container.Layered;
import com.github.slmpc.lumingraphics.ui.node.container.Scissor;
import com.github.slmpc.lumingraphics.ui.node.primitive.MarqueeText;
import com.github.slmpc.lumingraphics.ui.node.primitive.Outline;
import com.github.slmpc.lumingraphics.ui.node.primitive.Rect;
import com.github.slmpc.lumingraphics.ui.node.primitive.RectGradient;
import com.github.slmpc.lumingraphics.ui.node.primitive.RectOutline;
import com.github.slmpc.lumingraphics.ui.node.primitive.RotatedText;
import com.github.slmpc.lumingraphics.ui.node.primitive.RotatedTexture;
import com.github.slmpc.lumingraphics.ui.node.primitive.RoundRect;
import com.github.slmpc.lumingraphics.ui.node.primitive.RoundRectGradient;
import com.github.slmpc.lumingraphics.ui.node.primitive.SegmentedShadow;
import com.github.slmpc.lumingraphics.ui.node.primitive.Shadow;
import com.github.slmpc.lumingraphics.ui.node.primitive.Text;
import com.github.slmpc.lumingraphics.ui.node.primitive.Texture;
import com.github.slmpc.lumingraphics.ui.node.primitive.Triangle;
import com.github.slmpc.lumingraphics.ui.resource.UiResourceNotFoundException;
import com.github.slmpc.lumingraphics.ui.resource.UiResourceResolver;
import com.github.slmpc.lumingraphics.ui.text.UiTextMetrics;
import com.github.slmpc.lumingraphics.ui.theme.UiTheme;
import com.github.slmpc.lumingraphics.ui.tree.UiMalformedTreeException;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import com.github.slmpc.lumingraphics.ui.viewport.UiViewportTarget;
import com.github.slmpc.lumingraphics.ui.viewport.Viewport;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import com.github.slmpc.lumingraphics.ui.control.UiScrollBar;
import com.github.slmpc.lumingraphics.ui.scene.UiLayer;
import com.github.slmpc.lumingraphics.ui.scene.UiLayerStack;
import com.github.slmpc.lumingraphics.ui.scene.UiScene;
import com.github.slmpc.lumingraphics.ui.state.UiInvalidationState;
import com.github.slmpc.lumingraphics.ui.render.LuminUiRenderer;
import com.github.slmpc.lumingraphics.ui.render.SchedulerTextBatchSink;
import com.github.slmpc.lumingraphics.ui.render.UiContentBuffer;
import com.github.slmpc.lumingraphics.ui.render.UiRenderBatch;
import com.github.slmpc.lumingraphics.render.renderer.RendererSet;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScheduler;
import com.github.slmpc.lumingraphics.text.layout.TextLayout;
import com.github.slmpc.lumingraphics.text.render.TextDraw;
import com.github.slmpc.lumingraphics.text.layout.TextRenderBatch;
import com.github.slmpc.lumingraphics.text.ttf.TtfGlyph;
import com.github.slmpc.lumingraphics.text.atlas.TtfGlyphAtlas;
import com.github.slmpc.lumingraphics.text.atlas.GlyphAtlasUpload;
import com.github.slmpc.lumingraphics.text.layout.GlyphPlacement;
import com.github.slmpc.lumingraphics.text.layout.TextMeasurement;
import com.github.slmpc.lumingraphics.text.render.TextRenderer;
import com.github.slmpc.lumingraphics.text.atlas.TtfFontLoader;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

final class Todo13UiContractTest {
    private static final LuminColor WHITE = new LuminColor(1, 1, 1, 1);

    @Test void migUiRect() { // MIG-UI-RECT
        UiRect rect = new UiRect(2, 3, 8, 4);
        assertAll(
                () -> assertTrue(rect.contains(2, 3)),
                () -> assertTrue(rect.contains(10, 7)),
                () -> assertEquals(new UiRect(6, 5, 0, 0), rect.inset(5)),
                () -> assertEquals(new UiRect(8, 5, 2, 2), rect.intersect(new UiRect(8, 5, 9, 9))),
                () -> assertNull(rect.intersect(new UiRect(10, 7, 1, 1))),
                () -> assertThrows(IllegalArgumentException.class, () -> new UiRect(Float.NaN, 0, 1, 1)));
    }

    @Test void textMetrics() { // MIG-UI-TEXT-METRICS
        UiResourceResolver resources = new UiResourceResolver() { public com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture texture(String id){throw new AssertionError();} public TtfFontLoader font(String id){return null;} };
        UiTextMetrics metrics = UiTextMetrics.renderer(new NoopTextRenderer(), resources);
        assertEquals(new UiTextMetrics.Measurement(6, 18), metrics.measure("abc", 2, "body"));
        assertThrows(IllegalArgumentException.class, () -> metrics.measure("x", 0, "body"));
    }

    @Test void theme() { // MIG-UI-THEME
        UiTheme theme = UiTheme.defaults();
        assertAll(() -> assertTrue(theme.controlRadius() > 0),
                () -> assertEquals(0.5f, theme.withAlpha(WHITE, 0.5f).alpha()),
                () -> assertNotEquals(theme.textPrimary(), theme.textMuted()));
    }

    @Test void tree() { // MIG-UI-TREE
        Set<String> expected = Set.of("Layered", "Layer", "Scissor", "Shadow", "SegmentedShadow",
                "RoundRect", "RoundRectGradient", "Rect", "RectGradient", "RectOutline", "Outline", "Text",
                "RotatedText", "MarqueeText", "Texture", "RotatedTexture", "Button", "Switch", "FilledField",
                "Input", "AssistChip", "SegmentedControl", "IconButton", "PopupCard", "Slider", "Triangle", "Viewport");
        UiRect b = new UiRect(1, 1, 10, 10);
        UiViewportTarget viewportTarget = new UiViewportTarget() { public void begin(UiRect v){} public void render(UiTree t){} public void queue(UiRect v,float s,float m,float h,int x,int y){} };
        InputElement input = new InputElement(b,false,0,0,WHITE,0,1,"",1,WHITE,null,null,null,null,null,1,null);
        UiTree built=UiTree.build(scope->{scope.rect(1,0,0,1,1,WHITE);scope.layer(1,nested->{});scope.scissor(b,nested->{});
            scope.shadow(1,1,10,10,1,1,1,1,2,WHITE);scope.segmentedShadow(b,new float[]{1,1,1,1},2,WHITE,new float[]{1,1,2,2},new float[]{1},1);
            scope.roundRect(1,1,10,10,1,WHITE);scope.roundRectGradient(1,1,10,10,1,WHITE,WHITE,WHITE,WHITE);scope.rect(1,1,10,10,WHITE);
            scope.rectGradient(1,1,10,10,WHITE,WHITE,WHITE,WHITE);scope.rectOutline(1,1,10,10,1,WHITE);scope.outline(1,1,10,10,1,1,WHITE);
            scope.text("x",1,1,1,WHITE);scope.rotatedText("x",1,1,1,WHITE,null,1,1,0);scope.marqueeText("x",1,1,1,WHITE,null,b);
            scope.texture("t",b,0,0,0,0,0,0,1,1,WHITE);scope.rotatedTexture("t",b,0,0,1,1,WHITE,1,1,0);
            scope.button(new ButtonElement(b,1,WHITE,"x",1,WHITE));scope.toggle(new SwitchElement(b,0,0));scope.filledField(b,false,0);scope.input(input);
            scope.assistChip(b,"x",1,WHITE,WHITE,null,1,null);scope.segmentedControl(b,"a","b",0,0);scope.iconButton(b,"x",1,WHITE,0);
            scope.popupCard(b,1,2,WHITE,WHITE);scope.slider(b,0,1,WHITE,0,0,WHITE,1,1,1,WHITE);scope.triangle(1,1,1,0,WHITE);
            scope.viewport(viewportTarget,b,0,0,10,nested->{});});
        List<UiNode> variants=built.nodes();
        List<String> visited = new ArrayList<>();
        UiTree.of(variants).walk(node -> visited.add(node.getClass().getSimpleName()));
        try (UiRenderBatch batch = batch()) {
            assertDoesNotThrow(() -> batch.render(UiTree.of(variants)));
            assertAll(() -> assertEquals(expected, Set.copyOf(visited)),
                    () -> assertEquals(28,built.nodeCount(),"the Layered wrapper and its child are both retained nodes"),
                    () -> assertEquals(List.of(0, 1), batch.touchedLayers()));
        }
        List<UiRect> layoutBounds = new ArrayList<>();
        UiTree.layout(new UiRect(10,20,100,40), layout -> layout.row(5, row -> {
            row.item(20, item -> item.draw().rect(item.bounds().x(),item.bounds().y(),item.bounds().width(),item.bounds().height(),WHITE));
            row.fill(item -> item.draw().rect(item.bounds().x(),item.bounds().y(),item.bounds().width(),item.bounds().height(),WHITE));
        })).walk(node -> { if(node instanceof Rect rect) layoutBounds.add(rect.bounds()); });
        UiAnimation animation = new UiAnimation(){public float advance(float target){return target;}public boolean active(){return true;}};
        assertAll(() -> assertEquals(List.of(new UiRect(10,20,20,40),new UiRect(35,20,75,40)),layoutBounds),
                () -> assertTrue(UiTree.build(scope -> scope.animate(animation,true)).hasActiveAnimations()));
    }

    @Test void scrollbar() { // MIG-UI-SCROLLBAR
        AtomicLong time = new AtomicLong();
        UiScrollBar bar = new UiScrollBar(UiTheme.defaults(), time::get);
        UiRect viewport = new UiRect(0, 0, 100, 100);
        UiScrollBar.Geometry geometry = UiScrollBar.computeGeometry(viewport, 50, 100, 200);
        assertAll(() -> assertEquals(2, UiScrollBar.WIDTH), () -> assertEquals(2.5f, UiScrollBar.RIGHT_INSET),
                () -> assertEquals(10, UiScrollBar.MIN_THUMB_HEIGHT), () -> assertEquals(10, UiScrollBar.HIT_WIDTH),
                () -> assertEquals(2.5f, UiScrollBar.HOVER_WIDTH), () -> assertNotNull(geometry),
                () -> assertTrue(bar.mouseClicked(99, geometry.thumbY(), viewport, 50, 100, 200)),
                () -> assertEquals(0, bar.mouseDragged(-100, viewport, 100, 200)),
                () -> assertTrue(bar.mouseReleased()), () -> assertFalse(bar.mouseReleased()));
    }

    @Test void renderer() { // MIG-UI-RENDERER
        UiTree tree = UiTree.build(scope -> scope.scissor(new UiRect(0, 0, 10, 10), clipped ->
                clipped.scissor(new UiRect(20, 20, 2, 2), skipped -> skipped.rect(0, 0, 1, 1, WHITE))));
        assertDoesNotThrow(tree::validate);
        try (UiRenderBatch batch = batch()) {
            assertDoesNotThrow(() -> batch.render(tree));
            assertTrue(batch.scheduler().isEmpty());
        }
        UiTree overflowingLeaf = UiTree.of(List.of(new Scissor(
                new UiRect(0, 0, 10, 10), List.of(new Rect(new UiRect(20, 20, 1, 1), WHITE)))));
        assertDoesNotThrow(overflowingLeaf::validate);
        FakeRhi clippedRhi = new FakeRhi();
        try (UiRenderBatch batch = batch(clippedRhi)) {
            assertDoesNotThrow(() -> batch.render(overflowingLeaf));
            batch.flushAndClear(clippedRhi.execution());
        }
        assertTrue(clippedRhi.trace().contains("scissor=0,0,10,10"));
        assertAll(() -> assertThrows(IllegalArgumentException.class, () -> new SegmentedShadow(
                        new UiRect(0,0,1,1),new float[]{1},1,WHITE,new float[0],new float[0],0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Slider(new UiRect(0,0,1,1),
                        Float.NaN,1,WHITE,0,0,WHITE,1,1,1,WHITE)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Texture("t",new UiRect(0,0,1,1),
                        0,0,0,0,.8f,0,.2f,1,WHITE)));
        UiResourceResolver missing = UiResourceResolver.of(java.util.Map.of(),java.util.Map.of(),"body");
        try (UiRenderBatch batch = batch(missing)) {
            assertThrows(UiResourceNotFoundException.class, () -> batch.render(UiTree.of(List.of(
                    new Texture("missing",new UiRect(0,0,1,1),0,0,0,0,0,0,1,1,WHITE)))));
            assertTrue(batch.scheduler().isEmpty());
        }
        try (UiRenderBatch batch = batch(missing)) {
            assertThrows(UiResourceNotFoundException.class, () -> batch.render(UiTree.of(List.of(
                    new Text("missing font",0,0,1,WHITE,"body")))));
            assertTrue(batch.scheduler().isEmpty());
        }
    }

    @Test void scissorsClipOffscreenAnimationBoundsWithoutCrashing() {
        UiTree entering = UiTree.build(scope -> scope.scissor(new UiRect(-6, -5, 10, 10),
                clipped -> clipped.rect(0, 0, 10, 10, WHITE)));
        FakeRhi enteringRhi = new FakeRhi();
        try (UiRenderBatch batch = batch(enteringRhi)) {
            assertDoesNotThrow(() -> batch.render(entering));
            assertDoesNotThrow(() -> batch.flushAndClear(enteringRhi.execution()));
        }
        assertTrue(enteringRhi.trace().contains("scissor=0,0,4,5"));

        UiTree hidden = UiTree.build(scope -> scope.scissor(new UiRect(400, 300, 10, 10),
                clipped -> clipped.rect(0, 0, 10, 10, WHITE)));
        FakeRhi hiddenRhi = new FakeRhi();
        try (UiRenderBatch batch = batch(hiddenRhi)) {
            assertDoesNotThrow(() -> batch.render(hidden));
            assertDoesNotThrow(() -> batch.flushAndClear(hiddenRhi.execution()));
        }
        assertFalse(hiddenRhi.trace().stream().anyMatch(entry -> entry.startsWith("scissor=")));
    }

    @Test void contentBuffer() { // MIG-UI-CONTENT-BUFFER
        FakeRhi rhi=new FakeRhi();
        try (UiRenderBatch batch = batch(rhi); UiContentBuffer buffer = new UiContentBuffer(batch)) {
            buffer.begin(new UiRect(0,0,100,100));
            buffer.render(UiTree.of(List.of(new Rect(new UiRect(1,1,2,2),WHITE))));
            buffer.addMarquee(new MarqueeText("wide",1,1,1,WHITE,null,new UiRect(0,0,20,10)));
            buffer.queue(new UiRect(0,0,100,100),50,100,200,0,0);
            UiTree viewportTree=UiTree.build(scope->scope.viewport(buffer,new UiRect(10,20,50,30),5,10,60,
                    inner->inner.rect(0,0,5,5,WHITE)));
            Viewport viewport=(Viewport)viewportTree.nodes().get(0);Rect child=(Rect)viewport.children().get(0);
            assertAll(() -> assertTrue(buffer.pending()), () -> assertEquals(List.of(0), batch.touchedLayers()),
                    () -> assertEquals(List.of(8), buffer.scrollbarBatch().touchedLayers()),
                    () -> assertEquals(new UiRect(10,15,5,5),child.bounds()));
            buffer.flush(rhi.execution());
            assertAll(() -> assertFalse(buffer.pending()),() -> assertEquals(List.of(9),buffer.marqueeBatch().touchedLayers()),
                    () -> assertTrue(rhi.trace().contains("scissor=0,0,100,100")));
        }
    }

    @Test void contentBufferMarqueeFailureClearsAllRetainedTextAcrossCleanupCombinations() throws Exception {
        for(int cleanupMask=0;cleanupMask<8;cleanupMask++){
        int mask=cleanupMask;
        List<String> expectedCleanup=new ArrayList<>();
        RuntimeException marqueeCleanup=null,scrollbarCleanup=null,contentCleanup=null;
        if((mask&1)!=0){marqueeCleanup=new IllegalStateException("marquee cleanup failed");expectedCleanup.add("marquee cleanup failed");}
        if((mask&2)!=0){scrollbarCleanup=new IllegalStateException("scrollbar cleanup failed");expectedCleanup.add("scrollbar cleanup failed");}
        if((mask&4)!=0){contentCleanup=new IllegalStateException("content cleanup failed");expectedCleanup.add("content cleanup failed");}
        FakeRhi rhi=new FakeRhi();
        IllegalStateException marqueeFailure=new IllegalStateException("marquee render failed");
        TextFixture contentText=new TextFixture(contentCleanup);
        TextFixture scrollbarText=new TextFixture(scrollbarCleanup);
        TextFixture marqueeText=new TextFixture(marqueeCleanup);
        UiRenderBatch batch=batch(rhi,contentText.resources(),new NoopTextRenderer(marqueeFailure));
        UiContentBuffer buffer=new UiContentBuffer(batch);
        try {
            buffer.begin(new UiRect(0,0,100,100));
            buffer.render(UiTree.of(List.of(new Rect(new UiRect(1,1,2,2),WHITE))));
            buffer.addMarquee(new MarqueeText("wide",1,1,1,WHITE,null,new UiRect(0,0,20,10)));
            buffer.queue(new UiRect(0,0,100,100),50,100,200,0,0);
            retainText(buffer.contentBatch(),contentText);
            retainText(buffer.scrollbarBatch(),scrollbarText);
            retainText(buffer.marqueeBatch(),marqueeText);

            RuntimeException failure=assertThrows(RuntimeException.class,()->buffer.flush(rhi.execution()));
            assertAll(() -> assertSame(marqueeFailure,failure,"the render failure must remain primary"),
                    () -> assertEquals(expectedCleanup,cleanupMessages(failure),"cleanup mask "+mask),
                    () -> assertEquals(0,buffer.contentBatch().retainedTextDrawCount()),
                    () -> assertEquals(0,buffer.scrollbarBatch().retainedTextDrawCount()),
                    () -> assertEquals(0,buffer.marqueeBatch().retainedTextDrawCount()),
                    () -> assertTrue(batch.scheduler().isEmpty()),
                    () -> assertFalse(buffer.pending()),
                    () -> assertNull(buffer.pendingViewport()),
                    () -> assertEquals(0,rhi.drawAttempts()),
                    () -> assertEquals(1,contentText.closes()),
                    () -> assertEquals(1,scrollbarText.closes()),
                    () -> assertEquals(1,marqueeText.closes()));

            buffer.begin(new UiRect(0,0,100,100));
            buffer.queue(new UiRect(0,0,100,100),0,0,100,0,0);
            buffer.flush(rhi.execution());
            buffer.close();
            assertAll(() -> assertEquals(1,contentText.closes()),
                    () -> assertEquals(1,scrollbarText.closes()),
                    () -> assertEquals(1,marqueeText.closes()),
                    () -> assertEquals(0,rhi.drawAttempts()));
        } finally {
            try{buffer.close();}catch(RuntimeException ignored){}
            try{contentText.close();}catch(RuntimeException ignored){}
            try{scrollbarText.close();}catch(RuntimeException ignored){}
            try{marqueeText.close();}catch(RuntimeException ignored){}
        }
        }
    }

    @Test void contentBufferContentFlushFailureClearsEveryBatchExactlyOnce() throws Exception {
        FakeRhi rhi=new FakeRhi();
        TextFixture contentText=new TextFixture();
        TextFixture scrollbarText=new TextFixture();
        TextFixture marqueeText=new TextFixture();
        UiRenderBatch owner=batch(rhi,contentText.resources());
        UiContentBuffer buffer=new UiContentBuffer(owner);
        try {
            buffer.begin(new UiRect(0,0,100,100));
            buffer.render(UiTree.of(List.of(new Rect(new UiRect(1,1,2,2),WHITE))));
            buffer.queue(new UiRect(0,0,100,100),50,100,200,0,0);
            buffer.marqueeBatch().render(UiTree.of(List.of(new Rect(new UiRect(4,4,2,2),WHITE))));
            retainText(buffer.contentBatch(),contentText);
            retainText(buffer.scrollbarBatch(),scrollbarText);
            retainText(buffer.marqueeBatch(),marqueeText);
            rhi.failNextDraw();

            IllegalStateException failure=assertThrows(IllegalStateException.class,()->buffer.flush(rhi.execution()));
            assertAll(() -> assertEquals("backend draw failed at attempt 1",failure.getMessage()),
                    () -> assertEquals(0,buffer.contentBatch().retainedTextDrawCount()),
                    () -> assertEquals(0,buffer.scrollbarBatch().retainedTextDrawCount()),
                    () -> assertEquals(0,buffer.marqueeBatch().retainedTextDrawCount()),
                    () -> assertTrue(owner.scheduler().isEmpty()),
                    () -> assertFalse(buffer.pending()),
                    () -> assertNull(buffer.pendingViewport()),
                    () -> assertEquals(1,contentText.closes()),
                    () -> assertEquals(1,scrollbarText.closes()),
                    () -> assertEquals(1,marqueeText.closes()));

            int drawAttempts=rhi.drawAttempts();
            owner.flush(rhi.execution());
            buffer.close();
            assertAll(() -> assertEquals(drawAttempts,rhi.drawAttempts(),"failed transaction drew after cleanup"),
                    () -> assertEquals(1,contentText.closes()),
                    () -> assertEquals(1,scrollbarText.closes()),
                    () -> assertEquals(1,marqueeText.closes()));
        } finally {
            try{buffer.close();}finally{contentText.close();scrollbarText.close();marqueeText.close();}
        }
    }

    @Test void renderBatch() { // MIG-UI-RENDER-BATCH
        FakeRhi rhi=new FakeRhi();
        try (UiRenderBatch batch = batch(rhi)) {
            assertDoesNotThrow(() -> batch.render(UiTree.of(List.of())));
            batch.render(UiTree.build(scope -> scope.scissor(new UiRect(0,0,10,10), clipped -> {
                clipped.rect(0,0,2,2,WHITE);clipped.texture("atlas",2,2,2,2,WHITE);
            })), 3);
            assertAll(() -> assertFalse(batch.scheduler().isEmpty()), () -> assertEquals(List.of(3), batch.touchedLayers()));
            batch.flushAndClear(rhi.execution());
            assertAll(() -> assertTrue(batch.scheduler().isEmpty()), () -> assertTrue(batch.touchedLayers().isEmpty()),
                    () -> assertTrue(rhi.trace().contains("scissor=0,0,10,10")),
                    () -> assertTrue(rhi.trace().contains("texture=atlas")));
        }
        FakeRhi failing=new FakeRhi();
        try(UiRenderBatch batch=batch(failing)){batch.render(UiTree.of(List.of(new Rect(new UiRect(0,0,2,2),WHITE))));failing.failNextDraw();assertThrows(IllegalStateException.class,()->batch.flushAndClear(failing.execution()));assertTrue(batch.scheduler().isEmpty());}
        assertTrue(rhi.closedBuffers()>0);
    }

    @Test void delayedTextRetainsExactUploadUntilSchedulerFlush() throws Exception {
        FakeRhi rhi=new FakeRhi();
        TextFixture text=new TextFixture();
        UiResourceResolver resources=text.resources();
        SchedulerTextBatchSink sink=new SchedulerTextBatchSink(resources);
        try(UiRenderBatch batch=batch(rhi,resources)){
            sink.bind(batch.layerHandle(0),batch);
            TextDraw original=text.draw('A');
            GlyphAtlasUpload queuedUpload=original.batches().get(0).upload();
            sink.draw(List.of(original));
            original.close();
            text.append('B');

            assertAll(() -> assertTrue(original.isClosed()),
                    () -> assertEquals(1,batch.retainedTextDrawCount()),
                    () -> assertFalse(queuedUpload.isClosed(),"atlas mutation must not retire the delayed draw upload"));

            batch.flush(rhi.execution());
            assertAll(() -> assertEquals(0,batch.retainedTextDrawCount()),
                    () -> assertTrue(queuedUpload.isClosed()),
                    () -> assertTrue(rhi.trace().contains("texture=atlas-1")));
        }finally{text.close();}
    }

    @Test void delayedTextClearAndRendererFailureReleaseOnceWithoutDrawing() throws Exception {
        FakeRhi clearRhi=new FakeRhi();
        TextFixture clearText=new TextFixture();
        UiResourceResolver clearResources=clearText.resources();
        SchedulerTextBatchSink clearSink=new SchedulerTextBatchSink(clearResources);
        try(UiRenderBatch batch=batch(clearRhi,clearResources)){
            clearSink.bind(batch.layerHandle(0),batch);
            TextDraw original=clearText.draw('A');
            GlyphAtlasUpload upload=original.batches().get(0).upload();
            clearSink.draw(List.of(original));original.close();clearText.append('B');
            batch.clear();batch.clear();
            assertAll(() -> assertTrue(upload.isClosed()),
                    () -> assertEquals(1,clearText.closes()),
                    () -> assertFalse(clearRhi.trace().contains("draw")));
        }finally{clearText.close();}

        FakeRhi failureRhi=new FakeRhi();
        TextFixture failureText=new TextFixture();
        UiResourceResolver failureResources=failureText.resources();
        SchedulerTextBatchSink failureSink=new SchedulerTextBatchSink(failureResources);
        try(UiRenderBatch batch=batch(failureRhi,failureResources)){
            failureSink.bind(batch.layerHandle(0),batch);
            TextDraw original=failureText.draw('A');
            GlyphAtlasUpload upload=original.batches().get(0).upload();
            failureSink.draw(List.of(original));original.close();failureText.append('B');
            assertThrows(UiResourceNotFoundException.class,()->batch.render(UiTree.of(List.of(
                    new Texture("missing",new UiRect(0,0,1,1),0,0,0,0,0,0,1,1,WHITE)))));
            assertAll(() -> assertTrue(upload.isClosed()),
                    () -> assertEquals(1,failureText.closes()),
                    () -> assertEquals(0,batch.retainedTextDrawCount()));
        }finally{failureText.close();}
    }

    @Test void layer() { // MIG-UI-LAYER
        assertArrayEquals(new int[]{0, 100, 200, 300, 400, 500},
                Arrays.stream(UiLayer.values()).mapToInt(UiLayer::baseLayer).toArray());
    }

    @Test void layerStack() { // MIG-UI-LAYER-STACK
        UiLayerStack stack = new UiLayerStack();
        assertAll(() -> assertEquals(100, UiLayerStack.STRIDE),
                () -> assertEquals(399, stack.resolve(UiLayer.FLOATING, 99)),
                () -> assertThrows(IllegalArgumentException.class, () -> stack.resolve(UiLayer.CONTENT, -100)),
                () -> assertThrows(IllegalArgumentException.class, () -> stack.resolve(UiLayer.CONTENT, 100)));
    }

    @Test void scene() { // MIG-UI-SCENE
        UiScene.Lifecycle lifecycle = new UiScene.Lifecycle();
        assertAll(() -> assertThrows(IllegalStateException.class, lifecycle::end),
                () -> assertDoesNotThrow(lifecycle::begin),
                () -> assertThrows(IllegalStateException.class, lifecycle::begin),
                () -> assertDoesNotThrow(lifecycle::end));
        UiRenderBatch fixture=batch();
        try(UiScene scene=new UiScene(fixture.scheduler(),fixture.theme(),fixture.renderer())){
            scene.beginFrame();assertThrows(IllegalStateException.class,scene::beginFrame);scene.submit(UiLayer.CONTENT,UiTree.of(List.of(new Rect(new UiRect(0,0,2,2),WHITE))));
            assertAll(() -> assertTrue(scene.frameActive()), () -> assertFalse(scene.scheduler().isEmpty()));
            scene.abortFrame();
            assertAll(() -> assertFalse(scene.frameActive()), () -> assertTrue(scene.scheduler().isEmpty()),
                    () -> assertThrows(IllegalStateException.class,scene::abortFrame),
                    () -> assertThrows(IllegalStateException.class,()->scene.endFrame(new FakeRhi().execution())));
        }
    }

    @Test void invalidation() { // MIG-UI-INVALIDATION
        UiInvalidationState state = new UiInvalidationState();
        UiRect bounds = new UiRect(1, 2, 3, 4);
        assertTrue(state.needsRebuild(bounds, 5, 6, 100, 9));
        state.rememberSnapshot(bounds, 5, 6, 100, 9);
        assertFalse(state.needsRebuild(bounds, 5, 6, 100, 9));
        state.beginRebuild();
        state.noteAnimation(true);
        assertTrue(state.needsRebuild(bounds, 5, 6, 100, 9));
        state.beginRebuild();
        assertFalse(state.hasActiveAnimations());
        state.markDirty();
        assertTrue(state.needsRebuild(bounds, 5, 6, 100, 9));
    }

    private static UiRenderBatch batch() {
        return batch(new FakeRhi());
    }

    private static UiRenderBatch batch(FakeRhi rhi) {
        UiResourceResolver resources = new UiResourceResolver() {
            public com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture texture(String id) { return new com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture.Resource(id); }
            public TtfFontLoader font(String id) { return null; }
        };
        return batch(rhi,resources);
    }

    private static UiRenderBatch batch(UiResourceResolver resources) {
        return batch(new FakeRhi(),resources);
    }

    private static UiRenderBatch batch(FakeRhi rhi,UiResourceResolver resources) {
        return batch(rhi,resources,new NoopTextRenderer());
    }

    private static UiRenderBatch batch(FakeRhi rhi,UiResourceResolver resources,TextRenderer textRenderer) {
        RendererSet renderers=RendererSet.create(rhi.resources(),4096);
        Render2DScheduler scheduler = new Render2DScheduler(renderers,16);
        return UiRenderBatch.owned(scheduler,0,UiTheme.defaults(),new LuminUiRenderer(textRenderer,resources));
    }

    private record NoopTextRenderer(RuntimeException addFailure) implements TextRenderer {
            NoopTextRenderer() {
                this(null);
            }

        public TextMeasurement measure(String text, float scale, TtfFontLoader font) {
            return new TextMeasurement(text.length() * scale, 9 * scale, 1);
        }

        public TextLayout add(String text, float x, float y, float scale, TtfFontLoader font) {
            if (addFailure != null) throw addFailure;
            return new TextLayout(0, 0, 0, 0, 0, 0, List.of());
        }

        public TextLayout add(String text, float x, float y, float scale, LuminColor color, TtfFontLoader font) {
            return add(text, x, y, scale, font);
        }

        public TextLayout addRotated(String text, float x, float y, float scale, LuminColor color, TtfFontLoader font, float originX, float originY, float degrees) {
            return add(text, x, y, scale, font);
        }

        public void draw() {
        }

        public void clear() {
        }

        public void close() {
        }
        }

    private static final class TextFixture implements AutoCloseable {
        private final AtomicInteger sequence=new AtomicInteger();
        private final AtomicInteger closes=new AtomicInteger();
        private final RuntimeException firstCloseFailure;
        private final TtfGlyphAtlas atlas;
        private com.github.slmpc.lumingraphics.text.atlas.GlyphUv uv;

        TextFixture(){this(null);}
        TextFixture(RuntimeException firstCloseFailure){
            this.firstCloseFailure=firstCloseFailure;
            atlas=new TtfGlyphAtlas(0,8,8,pixels->{
                int id=sequence.incrementAndGet();
                return new GlyphAtlasUpload(new com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture.Resource("atlas-"+id),()->{closes.incrementAndGet();if(id==1&&firstCloseFailure!=null)throw firstCloseFailure;});
            });
            append('A');
        }
        void append(int codepoint){uv=atlas.append(new TtfGlyph(codepoint,1,1,0,0,1,new byte[]{1}));}
        int closes(){return closes.get();}
        UiResourceResolver resources(){return new UiResourceResolver(){public com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture texture(String id){throw new UiResourceNotFoundException("texture",id);}public TtfFontLoader font(String id){return null;}};}
        TextDraw draw(int codepoint) throws Exception {
            GlyphPlacement glyph=new GlyphPlacement(codepoint,0,0,1,1,uv);
            TextRenderBatch batch=new TextRenderBatch(atlas,List.of(glyph));
            TextLayout layout=new TextLayout(1,1,1,1,atlas.revision(),codepoint,List.of(batch));
            var constructor=TextDraw.class.getDeclaredConstructor(float.class,float.class,float.class,LuminColor.class,float.class,float.class,float.class,TextLayout.class);
            constructor.setAccessible(true);
            return constructor.newInstance(0,0,1,WHITE,0,0,0,layout);
        }
        @Override public void close(){atlas.close();}
    }

    private static GlyphAtlasUpload retainText(UiRenderBatch batch,TextFixture text) throws Exception {
        SchedulerTextBatchSink sink=new SchedulerTextBatchSink(text.resources());
        sink.bind(batch.layerHandle(0),batch);
        TextDraw original=text.draw('A');
        GlyphAtlasUpload upload=original.batches().get(0).upload();
        sink.draw(List.of(original));
        original.close();
        text.append('B');
        return upload;
    }

    private static List<String> cleanupMessages(RuntimeException failure) {
        return Arrays.stream(failure.getSuppressed()).map(error -> error.getCause()==null?error.getMessage():error.getCause().getMessage()).toList();
    }
}

