package com.github.slmpc.lumingraphics.ui.render;

import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScheduler;
import com.github.slmpc.lumingraphics.text.render.TextDraw;
import com.github.slmpc.lumingraphics.ui.theme.UiTheme;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class UiRenderBatch implements AutoCloseable {
    private final Render2DScheduler scheduler; private final boolean ownsScheduler; private final int baseLayer;
    private final UiTheme theme; private final LuminUiRenderer renderer; private final Set<Integer> touched=new HashSet<>();
    private final List<TextDraw> retainedTextDraws=new ArrayList<>(); private boolean closed;
    public UiRenderBatch(Render2DScheduler scheduler,int baseLayer,UiTheme theme,LuminUiRenderer renderer){this(scheduler,false,baseLayer,theme,renderer);}
    public static UiRenderBatch owned(Render2DScheduler scheduler,int baseLayer,UiTheme theme,LuminUiRenderer renderer){return new UiRenderBatch(scheduler,true,baseLayer,theme,renderer);}
    public UiRenderBatch(Render2DScheduler scheduler,boolean ownsScheduler,int baseLayer,UiTheme theme,LuminUiRenderer renderer){this.scheduler=Objects.requireNonNull(scheduler);this.ownsScheduler=ownsScheduler;this.baseLayer=baseLayer;this.theme=Objects.requireNonNull(theme);this.renderer=Objects.requireNonNull(renderer);}
    public UiRenderBatch view(int relativeBaseLayer){ensureOpen();return new UiRenderBatch(scheduler,false,baseLayer+relativeBaseLayer,theme,renderer);}
    public Render2DScheduler scheduler(){return scheduler;} public int baseLayer(){return baseLayer;} public UiTheme theme(){return theme;} public boolean ownsScheduler(){return ownsScheduler;}
    public LuminUiRenderer renderer(){return renderer;}
    public Render2DScheduler.LayerHandle layerHandle(int relativeLayer){return absoluteLayer(baseLayer+relativeLayer);}
    Render2DScheduler.LayerHandle absoluteLayer(int layer){ensureOpen();touched.add(layer);return scheduler.layer(layer);}
    void retainTextDraw(TextDraw draw){ensureOpen();retainedTextDraws.add(Objects.requireNonNull(draw));}
    public int retainedTextDrawCount(){return retainedTextDraws.size();}
    public void render(UiTree tree){render(tree,0);} public void render(UiTree tree,int relativeLayer){ensureOpen();try{renderer.render(tree,this,relativeLayer);}catch(RuntimeException failure){try{clear();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}throw failure;}}
    public List<Integer> touchedLayers(){List<Integer> values=new ArrayList<>(touched);Collections.sort(values);return List.copyOf(values);}
    public void flush(RenderExecution execution){
        ensureOpen();
        RuntimeException failure=null;
        try{renderer.textRenderer().draw();if(ownsScheduler)scheduler.flush(execution);else for(int layer:touchedLayers())scheduler.flushLayer(layer,execution);}
        catch(RuntimeException error){failure=error;}
        failure=releaseTextDraws(failure);
        if(failure!=null)throw failure;
    }
    public void clear(){
        ensureOpen();
        RuntimeException failure=null;
        try{if(ownsScheduler)scheduler.clear();else for(int layer:touchedLayers())scheduler.clearLayer(layer);}
        catch(RuntimeException error){failure=error;}
        finally{touched.clear();}
        try{renderer.textRenderer().clear();}catch(RuntimeException error){failure=merge(failure,error);}
        failure=releaseTextDraws(failure);
        if(failure!=null)throw failure;
    }
    public void flushAndClear(RenderExecution execution){
        RuntimeException failure=null;
        try{flush(execution);}catch(RuntimeException error){failure=error;}
        try{clear();}catch(RuntimeException error){failure=merge(failure,error);}
        if(failure!=null)throw failure;
    }
    private void ensureOpen(){if(closed)throw new IllegalStateException("UI render batch is closed");}
    public void releaseRetainedTextDraws(){RuntimeException failure=releaseTextDraws(null);if(failure!=null)throw failure;}
    private RuntimeException releaseTextDraws(RuntimeException failure){
        List<TextDraw> draws=List.copyOf(retainedTextDraws);retainedTextDraws.clear();
        for(int index=draws.size()-1;index>=0;index--)try{draws.get(index).close();}catch(RuntimeException error){failure=merge(failure,error);}
        return failure;
    }
    private static RuntimeException merge(RuntimeException failure,RuntimeException cleanup){if(failure==null)return cleanup;failure.addSuppressed(cleanup);return failure;}
    @Override public void close(){
        if(closed)return;
        RuntimeException failure=null;
        try{clear();}catch(RuntimeException error){failure=error;}
        closed=true;
        if(ownsScheduler){try{scheduler.close();}catch(RuntimeException error){failure=merge(failure,error);}try{renderer.textRenderer().close();}catch(RuntimeException error){failure=merge(failure,error);}}
        if(failure!=null)throw failure;
    }
}

