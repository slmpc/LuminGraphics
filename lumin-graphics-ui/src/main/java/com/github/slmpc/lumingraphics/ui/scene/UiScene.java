package com.github.slmpc.lumingraphics.ui.scene;

import com.github.slmpc.lumingraphics.render.RenderExecution;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScheduler;
import com.github.slmpc.lumingraphics.ui.UiTheme;
import com.github.slmpc.lumingraphics.ui.UiTree;
import com.github.slmpc.lumingraphics.ui.render.LuminUiRenderer;
import com.github.slmpc.lumingraphics.ui.render.UiRenderBatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class UiScene implements AutoCloseable {
    private final Render2DScheduler scheduler; private final UiLayerStack layers=new UiLayerStack(); private final UiTheme theme; private final LuminUiRenderer renderer; private final Lifecycle lifecycle=new Lifecycle();
    private final List<UiRenderBatch> frameBatches=new ArrayList<>(); private boolean closed;
    public UiScene(Render2DScheduler scheduler,UiTheme theme,LuminUiRenderer renderer){this.scheduler=Objects.requireNonNull(scheduler);this.theme=Objects.requireNonNull(theme);this.renderer=Objects.requireNonNull(renderer);}
    public void beginFrame(){ensureOpen();lifecycle.begin();try{scheduler.clear();renderer.textRenderer().clear();}catch(RuntimeException failure){lifecycle.abort();throw failure;}}
    public UiRenderBatch batch(UiLayer layer){ensureActive();return track(new UiRenderBatch(scheduler,layers.resolve(layer),theme,renderer));}
    public UiRenderBatch batch(UiLayer layer,int relative){ensureActive();return track(new UiRenderBatch(scheduler,layers.resolve(layer,relative),theme,renderer));}
    public void submit(UiLayer layer,UiTree tree){batch(layer).render(tree);} public void submit(UiLayer layer,int relative,UiTree tree){batch(layer,relative).render(tree);}
    public void endFrame(RenderExecution execution){
        ensureActive();RuntimeException failure=null;
        try{renderer.textRenderer().draw();scheduler.flush(execution);}catch(RuntimeException error){failure=error;}
        try{scheduler.clear();}catch(RuntimeException error){failure=merge(failure,error);}
        try{renderer.textRenderer().clear();}catch(RuntimeException error){failure=merge(failure,error);}
        failure=releaseFrameBatches(failure);lifecycle.end();if(failure!=null)throw failure;
    }
    public void abortFrame(){
        ensureActive();RuntimeException failure=null;
        try{scheduler.clear();}catch(RuntimeException error){failure=error;}
        try{renderer.textRenderer().clear();}catch(RuntimeException error){failure=merge(failure,error);}
        failure=releaseFrameBatches(failure);lifecycle.abort();if(failure!=null)throw failure;
    }
    public Render2DScheduler scheduler(){return scheduler;} public UiLayerStack layers(){return layers;} public boolean frameActive(){return lifecycle.active();}
    private UiRenderBatch track(UiRenderBatch batch){frameBatches.add(batch);return batch;}
    private RuntimeException releaseFrameBatches(RuntimeException failure){List<UiRenderBatch> batches=List.copyOf(frameBatches);frameBatches.clear();for(int index=batches.size()-1;index>=0;index--)try{batches.get(index).releaseRetainedTextDraws();}catch(RuntimeException error){failure=merge(failure,error);}return failure;}
    private static RuntimeException merge(RuntimeException failure,RuntimeException cleanup){if(failure==null)return cleanup;failure.addSuppressed(cleanup);return failure;}
    private void ensureOpen(){if(closed)throw new IllegalStateException("UI scene is closed");} private void ensureActive(){ensureOpen();if(!lifecycle.active())throw new IllegalStateException("UI scene frame is not active");}
    @Override public void close(){if(closed)return;RuntimeException failure=null;if(lifecycle.active())try{abortFrame();}catch(RuntimeException error){failure=error;}closed=true;try{scheduler.close();}catch(RuntimeException error){failure=merge(failure,error);}try{renderer.textRenderer().close();}catch(RuntimeException error){failure=merge(failure,error);}if(failure!=null)throw failure;}
    public static final class Lifecycle { private boolean active; public void begin(){if(active)throw new IllegalStateException("frame already active");active=true;} public void end(){if(!active)throw new IllegalStateException("frame is not active");active=false;} public void abort(){active=false;} public boolean active(){return active;} }
}
