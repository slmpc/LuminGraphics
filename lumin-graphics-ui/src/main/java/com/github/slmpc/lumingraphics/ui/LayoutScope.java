package com.github.slmpc.lumingraphics.ui;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import java.util.function.Consumer;
public final class LayoutScope {
    private final UiTree.Scope draw; private final UiRect bounds;
    LayoutScope(UiTree.Scope draw,UiRect bounds){this.draw=draw;this.bounds=bounds;}
    public UiRect bounds(){return bounds;} public UiTree.Scope draw(){return draw;}
    public void layer(int layer,Consumer<LayoutScope> content){draw.layer(layer,scope->content.accept(new LayoutScope(scope,bounds)));}
    public void box(Insets insets,Consumer<LayoutScope> content){content.accept(new LayoutScope(draw,insets.apply(bounds)));}
    public void column(float gap,Consumer<LinearScope> content){content.accept(new LinearScope(draw,bounds,Axis.VERTICAL,gap));}
    public void row(float gap,Consumer<LinearScope> content){content.accept(new LinearScope(draw,bounds,Axis.HORIZONTAL,gap));}
    public void roundRect(float radius,LuminColor color){draw.roundRect(bounds.x(),bounds.y(),bounds.width(),bounds.height(),radius,color);}
    public void text(String text,float scale,LuminColor color){draw.text(text,bounds.x(),bounds.centerY(),scale,color);}
}
