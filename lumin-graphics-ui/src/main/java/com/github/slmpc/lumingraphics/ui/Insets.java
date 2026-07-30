package com.github.slmpc.lumingraphics.ui;
public record Insets(float left,float top,float right,float bottom) {
    public Insets { UiNodes.finite(left,top,right,bottom); }
    public static Insets all(float value){return new Insets(value,value,value,value);}
    public static Insets symmetric(float horizontal,float vertical){return new Insets(horizontal,vertical,horizontal,vertical);}
    public UiRect apply(UiRect rect){return new UiRect(rect.x()+left,rect.y()+top,Math.max(0,rect.width()-left-right),Math.max(0,rect.height()-top-bottom));}
}
