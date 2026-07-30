package com.github.slmpc.lumingraphics.ui.render;

import com.github.slmpc.lumingraphics.render.RenderExecution;
import com.github.slmpc.lumingraphics.ui.RoundRect;
import com.github.slmpc.lumingraphics.ui.Scissor;
import com.github.slmpc.lumingraphics.ui.control.UiScrollBar;
import com.github.slmpc.lumingraphics.ui.MarqueeText;
import com.github.slmpc.lumingraphics.ui.UiRect;
import com.github.slmpc.lumingraphics.ui.UiTree;
import com.github.slmpc.lumingraphics.ui.UiViewportTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class UiContentBuffer implements AutoCloseable, UiViewportTarget {
    public static final String CONTENT="content",SCROLLBAR="scrollbar",MARQUEE="marquee";
    private final UiRenderBatch content,scrollbar,marquee; private final List<MarqueeText> marqueeNodes=new ArrayList<>(); private UiRect viewport; private boolean pending,closed;
    public UiContentBuffer(UiRenderBatch base){content=Objects.requireNonNull(base);scrollbar=base.view(8);marquee=base.view(9);}
    public UiRenderBatch contentBatch(){return content;} public UiRenderBatch scrollbarBatch(){return scrollbar;} public UiRenderBatch marqueeBatch(){return marquee;}
    public void begin(UiRect value){ensureOpen();viewport=Objects.requireNonNull(value);if(value.width()<=0||value.height()<=0)throw new IllegalArgumentException("viewport is empty");}
    public void render(UiTree tree){ensureOpen();if(viewport==null)throw new IllegalStateException("viewport was not begun");content.render(UiTree.of(List.of(new Scissor(viewport,tree.nodes()))));}
    public void addMarquee(MarqueeText value){ensureOpen();marqueeNodes.add(Objects.requireNonNull(value));}
    public void queue(UiRect value,float scroll,float maxScroll,float contentHeight,int mouseX,int mouseY){ensureOpen();viewport=Objects.requireNonNull(value);scrollbar.clear();UiScrollBar.Geometry geometry=UiScrollBar.computeGeometry(value,scroll,maxScroll,contentHeight);if(geometry!=null)scrollbar.render(UiTree.of(List.of(new RoundRect(new UiRect(geometry.thumbX(),geometry.thumbY(),geometry.thumbWidth(),geometry.thumbHeight()),geometry.thumbWidth()/2,content.theme().scrollBar(0)))));pending=true;}
    public boolean pending(){return pending;} public UiRect pendingViewport(){return viewport;}
    public void flush(RenderExecution execution){
        ensureOpen();if(!pending)return;RuntimeException failure=null;
        try{for(MarqueeText value:marqueeNodes){UiRect clip=value.clip().intersect(viewport);if(clip!=null)marquee.render(UiTree.of(List.of(new MarqueeText(value.text(),value.x(),value.y(),value.scale(),value.color(),value.fontId(),clip))));}}
        catch(RuntimeException error){failure=error;}
        if(failure==null)try{content.flush(execution);}catch(RuntimeException error){failure=error;}
        if(content.ownsScheduler()){
            try{marquee.releaseRetainedTextDraws();}catch(RuntimeException error){failure=merge(failure,error);}
            try{scrollbar.releaseRetainedTextDraws();}catch(RuntimeException error){failure=merge(failure,error);}
        }else{
            if(failure==null)try{scrollbar.flush(execution);}catch(RuntimeException error){failure=error;}
            if(failure==null)try{marquee.flush(execution);}catch(RuntimeException error){failure=error;}
            if(failure!=null){try{marquee.releaseRetainedTextDraws();}catch(RuntimeException error){failure=merge(failure,error);}try{scrollbar.releaseRetainedTextDraws();}catch(RuntimeException error){failure=merge(failure,error);}}
        }
        if(failure!=null){
            try{marquee.clear();}catch(RuntimeException error){failure=merge(failure,error);}
            try{scrollbar.clear();}catch(RuntimeException error){failure=merge(failure,error);}
            try{content.clear();}catch(RuntimeException error){failure=merge(failure,error);}
            viewport=null;
        }
        marqueeNodes.clear();pending=false;if(failure!=null)throw failure;
    }
    public void clear(){
        ensureOpen();RuntimeException failure=null;
        try{marquee.clear();}catch(RuntimeException error){failure=error;}
        try{scrollbar.clear();}catch(RuntimeException error){failure=merge(failure,error);}
        try{content.clear();}catch(RuntimeException error){failure=merge(failure,error);}
        marqueeNodes.clear();pending=false;viewport=null;if(failure!=null)throw failure;
    }
    public void flushAndClear(RenderExecution execution){RuntimeException failure=null;try{flush(execution);}catch(RuntimeException error){failure=error;}try{clear();}catch(RuntimeException error){failure=merge(failure,error);}if(failure!=null)throw failure;}
    private void ensureOpen(){if(closed)throw new IllegalStateException("content buffer is closed");}
    @Override public void close(){
        if(closed)return;
        RuntimeException failure=null;
        try{clear();}catch(RuntimeException error){failure=error;}
        closed=true;
        try{marquee.close();}catch(RuntimeException error){failure=merge(failure,error);}
        try{scrollbar.close();}catch(RuntimeException error){failure=merge(failure,error);}
        try{content.close();}catch(RuntimeException error){failure=merge(failure,error);}
        if(failure!=null)throw failure;
    }
    private static RuntimeException merge(RuntimeException failure,RuntimeException cleanup){if(failure==null)return cleanup;failure.addSuppressed(cleanup);return failure;}
}
