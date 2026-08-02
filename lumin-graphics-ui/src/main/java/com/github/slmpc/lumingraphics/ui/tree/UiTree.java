package com.github.slmpc.lumingraphics.ui.tree;
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
import com.github.slmpc.lumingraphics.ui.control.SelectionRange;
import com.github.slmpc.lumingraphics.ui.control.Switch;
import com.github.slmpc.lumingraphics.ui.control.SwitchElement;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.layout.LayoutScope;
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
import com.github.slmpc.lumingraphics.ui.viewport.UiViewportTarget;
import com.github.slmpc.lumingraphics.ui.viewport.Viewport;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

/**
 * 一帧 UI 节点的不可变快照。
 *
 * <p>使用 {@link #build(Consumer)} 或 {@link #layout(UiRect, Consumer)} 构建树。{@link Scope} 中的
 * 坐标相对当前 bound；容器、裁剪和 viewport 节点会在 {@link #validate()} 和 renderer 中递归处理。</p>
 */
public final class UiTree {
    private final List<UiNode> nodes;
    private final boolean activeAnimations;
    private UiTree(List<UiNode> nodes, boolean activeAnimations) { this.nodes = UiNodes.copy(nodes); this.activeAnimations = activeAnimations; }
    /** 从已有节点列表创建快照。 */
    public static UiTree of(List<UiNode> nodes) { return new UiTree(nodes, false); }
    /** 获取 scope 当前状态的快照。 */
    public static UiTree from(Scope scope) { return Objects.requireNonNull(scope).snapshot(); }
    /** 使用根 scope 构建一棵 UI 树。 */
    public static UiTree build(Consumer<Scope> content) { Scope scope = new Scope(); content.accept(scope); return scope.snapshot(); }
    /** 在给定根区域内构建一棵 UI 树。 */
    public static UiTree layout(UiRect bounds, Consumer<LayoutScope> content) {
        Scope scope = new Scope(); content.accept(new LayoutScope(scope, bounds)); return scope.snapshot();
    }
    public List<UiNode> nodes() { return nodes; }
    public int nodeCount() { return count(nodes); }
    public boolean hasActiveAnimations() { return activeAnimations; }
    public void walk(Consumer<UiNode> visitor) { walk(nodes, Objects.requireNonNull(visitor)); }
    private static void walk(List<UiNode> values, Consumer<UiNode> visitor) { for (UiNode node : values) { visitor.accept(node); if(node instanceof Layer layer)walk(layer.children(),visitor);else if(node instanceof Layered layered)walk(List.of(layered.child()),visitor);else if(node instanceof Scissor scissor)walk(scissor.children(),visitor);else if(node instanceof Viewport viewport)walk(viewport.children(),visitor); } }
    /**
     * 校验容器树。裁剪只限制最终可见的像素，不能限制子节点的布局范围。
     */
    public void validate() { validate(nodes); }
    private static int count(List<UiNode> values) {
        int count = 0;
        for (UiNode node : values) {
            count++;
            if (node instanceof Layer layer) count += count(layer.children());
            else if (node instanceof Layered layered) count += count(List.of(layered.child()));
            else if (node instanceof Scissor scissor) count += count(scissor.children());
            else if (node instanceof Viewport viewport) count += count(viewport.children());
        }
        return count;
    }
    private static void validate(List<UiNode> values) {
        for (UiNode node : values) {
            if (node instanceof Layer layer) validate(layer.children());
            else if (node instanceof Layered layered) validate(List.of(layered.child()));
            else if (node instanceof Scissor scissor) validate(scissor.children());
            else if (node instanceof Viewport viewport) validate(viewport.children());
        }
    }
    static UiRect bounds(UiNode node) {
        if (node instanceof Rect value) return value.bounds();
        if (node instanceof RoundRect value) return value.bounds();
        if (node instanceof RoundRectGradient value) return value.bounds();
        if (node instanceof RectGradient value) return value.bounds();
        if (node instanceof RectOutline value) return value.bounds();
        if (node instanceof Outline value) return value.bounds();
        if (node instanceof Shadow value) return value.bounds();
        if (node instanceof SegmentedShadow value) return value.bounds();
        if (node instanceof Texture value) return value.bounds();
        if (node instanceof RotatedTexture value) return value.bounds();
        if (node instanceof FilledField value) return value.bounds();
        if (node instanceof AssistChip value) return value.bounds();
        if (node instanceof SegmentedControl value) return value.bounds();
        if (node instanceof IconButton value) return value.bounds();
        if (node instanceof PopupCard value) return value.bounds();
        if (node instanceof Slider value) return value.bounds();
        if (node instanceof Button value) return value.element().bounds();
        if (node instanceof Switch value) return value.element().bounds();
        if (node instanceof Input value) return value.element().bounds();
        return null;
    }

    /** 用于构建 UI 树的可变作用域；完成后通过 {@link #snapshot()} 取得不可变快照。 */
    public static final class Scope {
        private List<UiNode> nodes = new ArrayList<>();
        private final List<UiRect> bounds = new ArrayList<>(List.of(new UiRect(0, 0, 0, 0)));
        private boolean active;
        public UiRect bound() { return bounds.get(bounds.size() - 1); }
        public UiTree snapshot() { return new UiTree(nodes, active); }
        public void add(UiNode node) { nodes.add(Objects.requireNonNull(node)); }
        public void clear() { nodes.clear(); active = false; bounds.clear(); bounds.add(new UiRect(0,0,0,0)); }
        /** 在相对当前 bound 的区域内执行内容构建。 */
        public void push(UiRect value, Consumer<Scope> content) { withBound(resolve(value), content); }
        public void push(float x, float y, Consumer<Scope> content) { withBound(new UiRect(rx(x), ry(y), 0, 0), content); }
        public void pushRelative(UiRect value,Consumer<Scope> content){push(value,content);} public void pushAbsolute(UiRect value,Consumer<Scope> content){withBound(value,content);}
        public void pushAbsolute(float x,float y,Consumer<Scope> content){withBound(new UiRect(x,y,0,0),content);}
        public Stack stack(UiRect value){return new Stack(resolve(value));}
        public void stackPush(UiRect value) { bounds.add(resolve(value)); }
        public void stackPop() { if (bounds.size() == 1) throw new IllegalStateException("root bound cannot be popped"); bounds.remove(bounds.size()-1); }
        /** 将子节点放入相对当前层的绘制层。 */
        public void layer(int layer, Consumer<Scope> content) { Capture capture = capture(content); nodes.add(new Layer(layer, capture.nodes)); active |= capture.active; }
        /** 使用相对当前 bound 的区域裁剪子节点。 */
        public void scissor(UiRect clip, Consumer<Scope> content) { Capture capture = capture(content); nodes.add(new Scissor(resolve(clip), capture.nodes)); active |= capture.active; }
        public void scissor(float x,float y,float w,float h,Consumer<Scope> content){scissor(new UiRect(x,y,w,h),content);}
        /** 按条件裁剪；不需要裁剪时内容仍在当前 scope 中构建。 */
        public void scissorIf(boolean required,UiRect clip,Consumer<Scope> content){if(required)scissor(clip,content);else Objects.requireNonNull(content).accept(this);}
        public void scissorIf(boolean required,float x,float y,float w,float h,Consumer<Scope> content){scissorIf(required,new UiRect(x,y,w,h),content);}
        public float animate(UiAnimation animation, boolean target) { return animate(animation, target ? 1 : 0); }
        public float animate(UiAnimation animation, float target) { float value = Objects.requireNonNull(animation).advance(target); active |= animation.active(); return value; }
        public void rect(float x,float y,float w,float h,LuminColor color){nodes.add(new Rect(rect(x,y,w,h),color));}
        public void rect(float x,float y,float w,float h,Color color){rect(x,y,w,h,awt(color));}
        public void rect(int layer,float x,float y,float w,float h,LuminColor color){nodes.add(new Layered(layer,new Rect(rect(x,y,w,h),color)));}
        public void rect(int layer,float x,float y,float w,float h,Color color){rect(layer,x,y,w,h,awt(color));}
        public void roundRect(float x,float y,float w,float h,float radius,LuminColor color){nodes.add(new RoundRect(rect(x,y,w,h),radius,color));}
        public void roundRect(float x,float y,float w,float h,float radius,Color color){roundRect(x,y,w,h,radius,awt(color));}
        public void roundRect(float x,float y,float w,float h,float tl,float tr,float br,float bl,LuminColor color){nodes.add(new RoundRect(rect(x,y,w,h),tl,tr,br,bl,color));}
        public void roundRect(float x,float y,float w,float h,float tl,float tr,float br,float bl,Color color){roundRect(x,y,w,h,tl,tr,br,bl,awt(color));}
        public void roundRectGradient(float x,float y,float w,float h,float radius,LuminColor tl,LuminColor bl,LuminColor br,LuminColor tr){nodes.add(new RoundRectGradient(rect(x,y,w,h),radius,radius,radius,radius,tl,bl,br,tr));}
        public void roundRectGradient(float x,float y,float w,float h,float rtl,float rtr,float rbr,float rbl,LuminColor tl,LuminColor bl,LuminColor br,LuminColor tr){nodes.add(new RoundRectGradient(rect(x,y,w,h),rtl,rtr,rbr,rbl,tl,bl,br,tr));}
        public void roundRectVerticalGradient(float x,float y,float w,float h,float radius,LuminColor top,LuminColor bottom){roundRectGradient(x,y,w,h,radius,top,bottom,bottom,top);}
        public void roundRectHorizontalGradient(float x,float y,float w,float h,float radius,LuminColor left,LuminColor right){roundRectGradient(x,y,w,h,radius,left,left,right,right);}
        public void outline(float x,float y,float w,float h,float radius,float width,LuminColor color){nodes.add(new Outline(rect(x,y,w,h),radius,width,color));}
        public void outline(float x,float y,float w,float h,float radius,float width,Color color){outline(x,y,w,h,radius,width,awt(color));}
        public void outline(float x,float y,float w,float h,float tl,float tr,float br,float bl,float width,LuminColor color){nodes.add(new Outline(rect(x,y,w,h),tl,tr,br,bl,width,color));}
        public void outline(float x,float y,float w,float h,float tl,float tr,float br,float bl,float width,Color color){outline(x,y,w,h,tl,tr,br,bl,width,awt(color));}
        public void rectOutline(float x,float y,float w,float h,float width,LuminColor color){nodes.add(new RectOutline(rect(x,y,w,h),width,color));}
        public void rectOutline(float x,float y,float w,float h,float width,Color color){rectOutline(x,y,w,h,width,awt(color));}
        public void rectGradient(float x,float y,float w,float h,LuminColor tl,LuminColor bl,LuminColor br,LuminColor tr){nodes.add(new RectGradient(rect(x,y,w,h),tl,bl,br,tr));}
        public void rectVerticalGradient(float x,float y,float w,float h,LuminColor top,LuminColor bottom){rectGradient(x,y,w,h,top,bottom,bottom,top);}
        public void rectHorizontalGradient(float x,float y,float w,float h,LuminColor left,LuminColor right){rectGradient(x,y,w,h,left,left,right,right);}
        public void shadow(float x,float y,float w,float h,float radius,float blur,LuminColor color){nodes.add(new Shadow(rect(x,y,w,h),radius,radius,radius,radius,blur,color));}
        public void shadow(float x,float y,float w,float h,float radius,float blur,Color color){shadow(x,y,w,h,radius,blur,awt(color));}
        public void shadow(float x,float y,float w,float h,float tl,float tr,float br,float bl,float blur,LuminColor color){nodes.add(new Shadow(rect(x,y,w,h),tl,tr,br,bl,blur,color));}
        public void shadow(float x,float y,float w,float h,float tl,float tr,float br,float bl,float blur,Color color){shadow(x,y,w,h,tl,tr,br,bl,blur,awt(color));}
        public void segmentedShadow(UiRect value,float[] radii,float blur,LuminColor color,float[] rects,float[] segmentRadii,int count){nodes.add(new SegmentedShadow(resolve(value),radii,blur,color,rects,segmentRadii,count));}
        public void text(String text,float x,float y,float scale,LuminColor color){text(text,x,y,scale,color,null);}
        public void text(String text,float x,float y,float scale,Color color){text(text,x,y,scale,awt(color),null);}
        public void text(String text,float x,float y,float scale,LuminColor color,String fontId){nodes.add(new Text(text,rx(x),ry(y),scale,color,fontId));}
        public void text(String text,float x,float y,float scale,Color color,String fontId){text(text,x,y,scale,awt(color),fontId);}
        public void rotatedText(String text,float x,float y,float scale,LuminColor color,String fontId,float originX,float originY,float degrees){nodes.add(new RotatedText(text,rx(x),ry(y),scale,color,fontId,rx(originX),ry(originY),degrees));}
        public void marqueeText(String text,float x,float y,float scale,LuminColor color,String fontId,UiRect clip){nodes.add(new MarqueeText(text,rx(x),ry(y),scale,color,fontId,resolve(clip)));}
        public void marqueeText(String text,float x,float y,float scale,LuminColor color,UiRect clip){marqueeText(text,x,y,scale,color,null,clip);}
        public void texture(String id,float x,float y,float w,float h,LuminColor color){nodes.add(new Texture(id,rect(x,y,w,h),0,0,0,0,0,0,1,1,color));}
        public void texture(String id,float x,float y,float w,float h,Color color){texture(id,x,y,w,h,awt(color));}
        public void texture(String id,UiRect value,float tl,float tr,float br,float bl,float u0,float v0,float u1,float v1,LuminColor color){nodes.add(new Texture(id,resolve(value),tl,tr,br,bl,u0,v0,u1,v1,color));}
        public void texture(String id,UiRect value,float tl,float tr,float br,float bl,float u0,float v0,float u1,float v1,Color color){texture(id,value,tl,tr,br,bl,u0,v0,u1,v1,awt(color));}
        public void rotatedTexture(String id,UiRect value,float u0,float v0,float u1,float v1,LuminColor color,float originX,float originY,float degrees){nodes.add(new RotatedTexture(id,resolve(value),u0,v0,u1,v1,color,rx(originX),ry(originY),degrees));}
        public void button(ButtonElement element){nodes.add(new Button(resolve(element)));}
        public void button(UiRect value,float radius,LuminColor background,String label,float labelScale,LuminColor labelColor){button(new ButtonElement(value,radius,background,label,labelScale,labelColor));}
        public void switchControl(UiRect value,float toggle,float hover){nodes.add(new Switch(resolve(value),toggle,hover));}
        public void toggle(UiRect value,float toggle,float hover){switchControl(value,toggle,hover);}
        public void toggle(SwitchElement value){toggleSwitch(value);}
        public void toggleSwitch(SwitchElement value){nodes.add(new Switch(new SwitchElement(resolve(value.bounds()),value.toggleProgress(),value.hoverProgress())));}
        public void filledField(UiRect value,boolean focused,float hover){nodes.add(new FilledField(resolve(value),focused,hover));}
        public void input(InputElement element){nodes.add(new Input(new InputElement(resolve(element.bounds()),element.focused(),element.hoverProgress(),element.focusRingProgress(),element.focusRingColor(),element.focusRingInset(),element.textInset(),element.text(),element.textScale(),element.textColor(),element.selection(),element.selectionColor(),element.caretIndex(),element.caretColor(),element.trailingHint(),element.trailingHintScale(),element.trailingHintColor())));}
        public void input(UiRect value,boolean focused,float hover,float textInset,String text,float textScale,LuminColor textColor,Integer caretIndex,LuminColor caretColor,String trailingHint,float trailingHintScale,LuminColor trailingHintColor){input(new InputElement(value,focused,hover,0,new LuminColor(0,0,0,0),0,textInset,text,textScale,textColor,null,null,caretIndex,caretColor,trailingHint,trailingHintScale,trailingHintColor));}
        public void input(UiRect value,boolean focused,float hover,float focusRingProgress,LuminColor focusRingColor,float focusRingInset,float textInset,String text,float textScale,LuminColor textColor,SelectionRange selection,LuminColor selectionColor,Integer caretIndex,LuminColor caretColor,String trailingHint,float trailingHintScale,LuminColor trailingHintColor){input(new InputElement(value,focused,hover,focusRingProgress,focusRingColor,focusRingInset,textInset,text,textScale,textColor,selection,selectionColor,caretIndex,caretColor,trailingHint,trailingHintScale,trailingHintColor));}
        public void assistChip(UiRect value,String label,float scale,LuminColor bg,LuminColor fg,String icon,float iconScale,String font){nodes.add(new AssistChip(resolve(value),label,scale,bg,fg,icon,iconScale,font));}
        public void chip(UiRect value,String label,float scale,LuminColor bg,LuminColor fg,String icon,float iconScale,String font){assistChip(value,label,scale,bg,fg,icon,iconScale,font);}
        public void segmentedControl(UiRect value,String leading,String trailing,float progress,float hover){nodes.add(new SegmentedControl(resolve(value),leading,trailing,progress,hover));}
        public void segmented(UiRect value,String leading,String trailing,float progress,float hover){segmentedControl(value,leading,trailing,progress,hover);}
        public void iconButton(UiRect value,String label,float scale,LuminColor tone,float hover){nodes.add(new IconButton(resolve(value),label,scale,tone,hover));}
        public void popupCard(UiRect value,float radius,float blur,LuminColor shadow,LuminColor surface){nodes.add(new PopupCard(resolve(value),radius,blur,shadow,surface));}
        public void slider(UiRect value,float progress,float trackRadius,LuminColor track,float endInset,float minWidth,LuminColor activeColor,float handleW,float handleH,float handleRadius,LuminColor handle){nodes.add(new Slider(resolve(value),progress,trackRadius,track,endInset,minWidth,activeColor,handleW,handleH,handleRadius,handle));}
        public void triangle(float x,float y,float size,float progress,LuminColor color){nodes.add(new Triangle(rx(x),ry(y),size,progress,color));}
        public void triangle(float x,float y,float size,float progress,Color color){triangle(x,y,size,progress,awt(color));}
        public void viewport(UiViewportTarget buffer,UiRect viewport,float scroll,float maxScroll,float contentHeight,Consumer<Scope> content){
            viewport(buffer,viewport,scroll,maxScroll,contentHeight,Integer.MIN_VALUE,Integer.MIN_VALUE,content);
        }
        public void viewport(UiViewportTarget buffer,UiRect viewport,float scroll,float maxScroll,float contentHeight,int mouseX,int mouseY,Consumer<Scope> content){
            if(!Float.isFinite(scroll)||!Float.isFinite(maxScroll)||!Float.isFinite(contentHeight)||maxScroll<0||contentHeight<0)throw new IllegalArgumentException("viewport values must be finite and non-negative");
            UiRect resolved=resolve(viewport);Capture capture=capture(scope->scope.withBound(new UiRect(resolved.x(),resolved.y()-scroll,resolved.width(),contentHeight),content)); nodes.add(new Viewport(buffer,resolved,scroll,maxScroll,contentHeight,mouseX,mouseY,capture.nodes)); active|=capture.active;
        }
        private ButtonElement resolve(ButtonElement e){return new ButtonElement(resolve(e.bounds()),e.radius(),e.background(),e.label(),e.labelScale(),e.labelColor());}
        private float rx(float x){return bound().x()+x;} private float ry(float y){return bound().y()+y;}
        private UiRect rect(float x,float y,float w,float h){return new UiRect(rx(x),ry(y),w,h);}
        private UiRect resolve(UiRect r){return new UiRect(rx(r.x()),ry(r.y()),r.width(),r.height());}
        private static LuminColor awt(Color color){Color value=Objects.requireNonNull(color,"color");return new LuminColor(value.getRed()/255.0f,value.getGreen()/255.0f,value.getBlue()/255.0f,value.getAlpha()/255.0f);}
        private void withBound(UiRect value,Consumer<Scope> content){bounds.add(value);try{content.accept(this);}finally{bounds.remove(bounds.size()-1);}}
        private Capture capture(Consumer<Scope> content){List<UiNode> parent=nodes;boolean parentActive=active;int depth=bounds.size();nodes=new ArrayList<>();active=false;try{content.accept(this);return new Capture(List.copyOf(nodes),active);}finally{nodes=parent;active=parentActive;while(bounds.size()>depth)bounds.remove(bounds.size()-1);}}
        public final class Stack { private final UiRect area;private float cursor;private Stack(UiRect value){area=value;cursor=value.y();}public UiRect bounds(){return area;}public UiRect item(float height){float h=Math.max(0,height);UiRect result=new UiRect(area.x(),cursor,area.width(),h);cursor+=h;return result;}public UiRect item(float height,float gap){UiRect result=item(height);cursor+=Math.max(0,gap);return result;}public void item(float height,Consumer<Scope> content){pushAbsolute(item(height),content);}public void item(float height,float gap,Consumer<Scope> content){pushAbsolute(item(height,gap),content);}public void item(float height,BiConsumer<UiRect,Scope> content){UiRect value=item(height);pushAbsolute(value,scope->content.accept(value,scope));}public void item(float height,float gap,BiConsumer<UiRect,Scope> content){UiRect value=item(height,gap);pushAbsolute(value,scope->content.accept(value,scope));}public void gap(float value){cursor+=Math.max(0,value);}public float cursor(){return cursor;} }
    }
    private record Capture(List<UiNode> nodes,boolean active) { }
}

