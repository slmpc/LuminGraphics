package com.github.slmpc.lumingraphics.ui.control;
import com.github.slmpc.lumingraphics.ui.UiRect;
import com.github.slmpc.lumingraphics.ui.UiTheme;
import java.util.Objects;
import java.util.function.LongSupplier;
public final class UiScrollBar {
    public static final float WIDTH=2,RIGHT_INSET=2.5f,MIN_THUMB_HEIGHT=10,HIT_WIDTH=10,HOVER_WIDTH=2.5f,TOTAL_WIDTH=HIT_WIDTH;
    private final UiTheme theme; private final LongSupplier clock; private boolean dragging; private float dragOffset,hover,targetHover; private long changedAt;
    public UiScrollBar(UiTheme theme,LongSupplier clock){this.theme=Objects.requireNonNull(theme);this.clock=Objects.requireNonNull(clock);changedAt=clock.getAsLong();}
    public boolean isDragging(){return dragging;}
    public float hoverProgress(){long duration=theme.hoverAnimationDuration();if(duration==0)return targetHover;float d=Math.min(1,(clock.getAsLong()-changedAt)/(float)duration);return hover+(targetHover-hover)*d;}
    public void updateHover(boolean value){float current=hoverProgress();float target=value||dragging?1:0;if(target!=targetHover){hover=current;targetHover=target;changedAt=clock.getAsLong();}}
    public boolean mouseClicked(double x,double y,UiRect viewport,float scroll,float maxScroll,float contentHeight){Geometry g=computeGeometry(viewport,scroll,maxScroll,contentHeight);if(g==null||!g.trackContains(x,y))return false;dragging=true;dragOffset=g.thumbContains(x,y)?(float)y-g.thumbY:g.thumbHeight/2;updateHover(true);return true;}
    public float mouseDragged(double y,UiRect viewport,float maxScroll,float contentHeight){return !dragging||maxScroll<=0?-1:scrollFromThumbTopY((float)y-dragOffset,viewport,maxScroll,contentHeight);}
    public boolean mouseReleased(){if(!dragging)return false;dragging=false;return true;}
    public void reset(){dragging=false;hover=0;targetHover=0;changedAt=clock.getAsLong();}
    public static Geometry computeGeometry(UiRect viewport,float scroll,float maxScroll,float contentHeight){if(maxScroll<=0||contentHeight<=viewport.height()||viewport.height()<=.5f)return null;float height=Math.min(viewport.height(),Math.max(MIN_THUMB_HEIGHT,viewport.height()*viewport.height()/contentHeight));float travel=viewport.height()-height;float ratio=Math.max(0,Math.min(1,scroll/maxScroll));return new Geometry(viewport.right()-RIGHT_INSET,viewport.y()+travel*ratio,WIDTH,height,viewport.right()-HIT_WIDTH,viewport.y(),HIT_WIDTH,viewport.height());}
    public static float scrollFromThumbTopY(float top,UiRect viewport,float maxScroll,float contentHeight){Geometry g=computeGeometry(viewport,0,maxScroll,contentHeight);if(g==null)return 0;float travel=g.trackHeight-g.thumbHeight;if(travel<=0)return 0;return Math.max(0,Math.min(1,(top-g.trackY)/travel))*maxScroll;}
    public record Geometry(float thumbX,float thumbY,float thumbWidth,float thumbHeight,float trackX,float trackY,float trackWidth,float trackHeight){public boolean thumbContains(double x,double y){return x>=trackX&&x<=trackX+trackWidth&&y>=thumbY&&y<=thumbY+thumbHeight;}public boolean trackContains(double x,double y){return x>=trackX&&x<=trackX+trackWidth&&y>=trackY&&y<=trackY+trackHeight;}}
}
