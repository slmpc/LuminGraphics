package com.github.slmpc.lumingraphics.ui.render;
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
import com.github.slmpc.lumingraphics.ui.resource.UiResourceResolver;
import com.github.slmpc.lumingraphics.ui.theme.UiTheme;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import com.github.slmpc.lumingraphics.ui.viewport.Viewport;

import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DBounds;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScheduler;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScissor;
import com.github.slmpc.lumingraphics.text.render.TextRenderer;
import java.util.List;
import java.util.Objects;

/**
 * 将 {@link UiTree} 转换为 {@link Render2DScheduler} 命令的 UI renderer。
 *
 * <p>纹理和字体始终由调用方的 {@link UiResourceResolver} 提供。每次 {@link #render(UiTree, UiRenderBatch, int)}
 * 都会先验证树，文本批次则在配置了 {@link SchedulerTextBatchSink} 时绑定到当前 scheduler 层。</p>
 */
public final class LuminUiRenderer {
    private final TextRenderer text;
    private final UiResourceResolver resources;
    private final SchedulerTextBatchSink textSink;
    /** 创建不自动绑定 scheduler 文本 sink 的 UI renderer。 */
    public LuminUiRenderer(TextRenderer text,UiResourceResolver resources){this(text,null,resources);}
    /** 创建可将文本绘制绑定到 scheduler 层的 UI renderer。 */
    public LuminUiRenderer(TextRenderer text,SchedulerTextBatchSink textSink,UiResourceResolver resources){this.text=Objects.requireNonNull(text);this.textSink=textSink;this.resources=Objects.requireNonNull(resources);}
    public TextRenderer textRenderer(){return text;}
    /**
     * 校验并渲染一棵 UI 树。
     *
     * @param tree 当前帧 UI 快照
     * @param batch 提供 scheduler、主题和基础层的批次
     * @param relativeLayer 相对于 batch 基础层的附加层号
     */
    public void render(UiTree tree,UiRenderBatch batch,int relativeLayer){Objects.requireNonNull(tree).validate();renderNodes(tree.nodes(),batch,batch.baseLayer()+relativeLayer,null);}
    private void renderNodes(List<UiNode> nodes,UiRenderBatch batch,int layer,UiRect clip){for(UiNode node:nodes)renderNode(node,batch,layer,clip);}
    private void renderNode(UiNode node,UiRenderBatch batch,int layer,UiRect clip){
        if(node instanceof Layer value){renderNodes(value.children(),batch,layer+value.layer(),clip);return;}
        if(node instanceof Layered value){renderNode(value.child(),batch,layer+value.layer(),clip);return;}
        if(node instanceof Scissor value){UiRect nested=clip==null?value.clip():clip.intersect(value.clip());if(nested!=null)renderNodes(value.children(),batch,layer,nested);return;}
        Render2DScheduler.LayerHandle target=batch.absoluteLayer(layer);
        if(clip!=null&&!pushScissor(clip,target))return;
        boolean clipped=clip!=null;
        try {
        if(node instanceof Shadow value){target.addShadow(bounds(value.bounds()),value.radiusTopLeft(),value.radiusTopRight(),value.radiusBottomRight(),value.radiusBottomLeft(),value.blurRadius(),value.color());return;}
        if(node instanceof SegmentedShadow value){float[] r=value.radii();target.addSegmentedShadow(bounds(value.bounds()),r[0],r[1],r[2],r[3],value.blurRadius(),value.color(),value.segmentRects(),value.segmentRadii(),value.segmentCount());return;}
        if(node instanceof RoundRect value){target.addRoundRect(bounds(value.bounds()),value.radiusTopLeft(),value.radiusTopRight(),value.radiusBottomRight(),value.radiusBottomLeft(),value.color());return;}
        if(node instanceof RoundRectGradient value){target.addRoundRectGradient(bounds(value.bounds()),value.radiusTopLeft(),value.radiusTopRight(),value.radiusBottomRight(),value.radiusBottomLeft(),value.topLeft(),value.bottomLeft(),value.bottomRight(),value.topRight());return;}
        if(node instanceof Rect value){target.addRect(bounds(value.bounds()),value.color());return;}
        if(node instanceof RectGradient value){target.addRectGradient(bounds(value.bounds()),value.topLeft(),value.bottomLeft(),value.bottomRight(),value.topRight());return;}
        if(node instanceof RectOutline value){target.addRectOutline(bounds(value.bounds()),value.outlineWidth(),value.color());return;}
        if(node instanceof Outline value){target.addOutline(bounds(value.bounds()),value.radiusTopLeft(),value.radiusTopRight(),value.radiusBottomRight(),value.radiusBottomLeft(),value.outlineWidth(),value.color());return;}
        if(node instanceof Text value){bind(target,batch);text.add(value.text(),value.x(),value.y(),value.scale(),value.color(),resources.font(value.fontId()));drawBound();return;}
        if(node instanceof RotatedText value){bind(target,batch);text.addRotated(value.text(),value.x(),value.y(),value.scale(),value.color(),resources.font(value.fontId()),value.originX(),value.originY(),value.rotationDegrees());drawBound();return;}
        if(node instanceof MarqueeText value){if(clip!=null&&clip.intersect(value.clip())==null)return;if(!pushScissor(value.clip(),target))return;try{bind(target,batch);text.add(value.text(),value.x(),value.y(),value.scale(),value.color(),resources.font(value.fontId()));drawBound();}finally{target.popScissor();}return;}
        if(node instanceof Texture value){target.addRoundedTexture(bounds(value.bounds()),resources.texture(value.textureId()),value.radiusTopLeft(),value.radiusTopRight(),value.radiusBottomRight(),value.radiusBottomLeft(),value.u0(),value.v0(),value.u1(),value.v1(),value.color());return;}
        if(node instanceof RotatedTexture value){target.addRotatedTexture(bounds(value.bounds()),resources.texture(value.textureId()),value.u0(),value.v0(),value.u1(),value.v1(),value.color(),value.originX(),value.originY(),value.rotationDegrees());return;}
        if(node instanceof Button value){renderButton(value.element(),target,batch);return;}
        if(node instanceof Switch value){renderSwitch(value,target,batch.theme());return;}
        if(node instanceof FilledField value){renderFilledField(value.bounds(),value.focused(),value.hoverProgress(),target,batch.theme());return;}
        if(node instanceof Input value){renderInput(value.element(),target,batch);return;}
        if(node instanceof AssistChip value){renderAssistChip(value,target,batch);return;}
        if(node instanceof SegmentedControl value){renderSegmentedControl(value,target,batch);return;}
        if(node instanceof IconButton value){renderIconButton(value,target,batch);return;}
        if(node instanceof PopupCard value){target.addShadow(bounds(value.bounds()),value.radius(),value.blurRadius(),value.shadowColor());target.addRoundRect(bounds(value.bounds()),value.radius(),value.surfaceColor());return;}
        if(node instanceof Slider value){renderSlider(value,target);return;}
        if(node instanceof Triangle value){target.addChevronTriangle(value.centerX(),value.centerY(),value.size(),value.progress(),value.color());return;}
        if(node instanceof Viewport value){value.buffer().begin(value.viewport());value.buffer().render(UiTree.of(value.children()));value.buffer().queue(value.viewport(),value.scroll(),value.maxScroll(),value.contentHeight(),value.mouseX(),value.mouseY());}
        } finally { if(clipped)target.popScissor(); }
    }
    private void renderButton(ButtonElement value,Render2DScheduler.LayerHandle target,UiRenderBatch batch){target.addRoundRect(bounds(value.bounds()),value.radius(),value.background());var size=measure(value.label(),value.labelScale(),null);addText(value.label(),value.bounds().x()+(value.bounds().width()-size.width())/2,value.bounds().y()+(value.bounds().height()-size.height())/2,value.labelScale(),value.labelColor(),null,target,batch);}
    private static void renderFilledField(UiRect bounds,boolean focused,float hover,Render2DScheduler.LayerHandle target,UiTheme theme){target.addRoundRect(bounds(bounds),theme.controlRadius(),theme.filledFieldSurface(focused,hover));}
    private static void renderSwitch(Switch value,Render2DScheduler.LayerHandle target,UiTheme theme){UiRect b=value.element().bounds();float toggle=value.element().toggleProgress(),hover=value.element().hoverProgress();target.addRoundRect(bounds(b),b.height()/2,theme.switchTrack(toggle));LuminColor outline=theme.switchTrackOutline(toggle,hover);if(outline.alpha()>0)target.addOutline(bounds(b),b.height()/2,theme.switchTrackOutlineWidth(toggle),outline);float size=theme.switchHandleSizeOff()+(theme.switchHandleSizeOn()-theme.switchHandleSizeOff())*toggle;float width=size+3.5f*(4*toggle*(1-toggle));float inset=theme.switchHandleInsetOff()+(theme.switchHandleInsetOn()-theme.switchHandleInsetOff())*toggle;float min=b.x()+inset+width/2,max=b.right()-inset-width/2,cx=min+(max-min)*toggle;if(hover>.02f){float halo=theme.switchStateLayerSize();target.addRoundRect(new Render2DBounds(cx-halo/2,b.centerY()-halo/2,halo,halo),halo/2,theme.stateLayer(theme.textPrimary(),hover,18));}target.addRoundRect(new Render2DBounds(cx-width/2,b.centerY()-size/2,width,size),size/2,theme.switchKnob(toggle));}
    private void renderInput(InputElement e,Render2DScheduler.LayerHandle target,UiRenderBatch batch){UiRect b=e.bounds();renderFilledField(b,e.focused(),e.hoverProgress(),target,batch.theme());if(e.focusRingProgress()>.01f&&e.focusRingInset()>0){float inset=e.focusRingInset()*e.focusRingProgress();target.addRoundRect(new Render2DBounds(b.x()-inset,b.y()-inset,b.width()+2*inset,b.height()+2*inset),batch.theme().controlRadius()+inset,batch.theme().withAlpha(e.focusRingColor(),48/255f*e.focusRingProgress()));}String value=e.text();float textX=b.x()+e.textInset();if(value!=null&&!value.isEmpty()){float textY=b.y()+(b.height()-measure(value,e.textScale(),null).height())/2;if(e.selection()!=null&&e.selectionColor()!=null){int start=Math.max(0,Math.min(value.length(),e.selection().start())),end=Math.max(start,Math.min(value.length(),e.selection().end()));if(end>start){float x=textX+measure(value.substring(0,start),e.textScale(),null).width(),w=measure(value.substring(start,end),e.textScale(),null).width();target.addRect(new Render2DBounds(x,b.y()+3,w,b.height()-6),e.selectionColor());}}addText(value,textX,textY,e.textScale(),e.textColor(),null,target,batch);if(e.caretIndex()!=null&&e.caretColor()!=null){int index=Math.max(0,Math.min(value.length(),e.caretIndex()));float x=textX+measure(value.substring(0,index),e.textScale(),null).width();target.addRect(new Render2DBounds(x,b.y()+4,1,b.height()-8),e.caretColor());}}if(e.trailingHint()!=null&&!e.trailingHint().isBlank()&&e.trailingHintColor()!=null){var size=measure(e.trailingHint(),e.trailingHintScale(),null);addText(e.trailingHint(),b.right()-e.textInset()-size.width(),b.y()+(b.height()-size.height())/2,e.trailingHintScale(),e.trailingHintColor(),null,target,batch);}}
    private void renderAssistChip(AssistChip value,Render2DScheduler.LayerHandle target,UiRenderBatch batch){target.addRoundRect(bounds(value.bounds()),batch.theme().controlRadius(),value.background());var label=measure(value.label(),value.textScale(),null);addText(value.label(),value.bounds().x()+8,value.bounds().y()+(value.bounds().height()-label.height())/2,value.textScale(),value.foreground(),null,target,batch);if(value.trailingIcon()!=null&&!value.trailingIcon().isEmpty()&&value.trailingIconFontId()!=null){var icon=measure(value.trailingIcon(),value.trailingIconScale(),value.trailingIconFontId());addText(value.trailingIcon(),value.bounds().right()-8-icon.width(),value.bounds().y()+(value.bounds().height()-icon.height())/2,value.trailingIconScale(),value.foreground(),value.trailingIconFontId(),target,batch);}}
    private void renderIconButton(IconButton value,Render2DScheduler.LayerHandle target,UiRenderBatch batch){target.addRoundRect(bounds(value.bounds()),value.bounds().height()/2,batch.theme().stateLayer(value.tone(),value.hoverProgress(),32));LuminColor color=batch.theme().lerp(batch.theme().textMuted(),value.tone(),value.hoverProgress());var size=measure(value.label(),value.scale(),null);addText(value.label(),value.bounds().x()+(value.bounds().width()-size.width())/2,value.bounds().y()+(value.bounds().height()-size.height())/2,value.scale(),color,null,target,batch);}
    private void renderSegmentedControl(SegmentedControl value,Render2DScheduler.LayerHandle target,UiRenderBatch batch){UiRect b=value.bounds();UiTheme theme=batch.theme();float radius=theme.controlRadius(),ix=b.x()+1,iy=b.y()+1,iw=b.width()-2,ih=b.height()-2,segment=iw/2,indicatorX=ix+1.5f+segment*value.progress(),indicatorY=iy+1.5f,indicatorW=segment-3,indicatorH=ih-3,labelScale=.52f,labelY=iy+(ih-measure("Mg",labelScale,null).height())/2;target.addRoundRect(bounds(b),radius,theme.outlineSoft());target.addRoundRect(new Render2DBounds(ix,iy,iw,ih),Math.max(radius-1,1),theme.segmentedControlSurface());if(value.hoverProgress()>.01f)target.addRoundRect(new Render2DBounds(ix,iy,iw,ih),Math.max(radius-1,1),theme.stateLayer(theme.textPrimary(),value.hoverProgress(),theme.light()?10:14));target.addRect(new Render2DBounds(ix+segment-.5f,iy+3,1,ih-6),theme.outlineSoft());target.addRoundRect(new Render2DBounds(indicatorX,indicatorY,indicatorW,indicatorH),Math.max(4,radius-2),theme.segmentedControlIndicator());var leading=measure(value.leadingLabel(),labelScale,null);var trailing=measure(value.trailingLabel(),labelScale,null);addText(value.leadingLabel(),ix+(segment-leading.width())/2,labelY,labelScale,theme.lerp(theme.segmentedControlActiveLabel(),theme.segmentedControlInactiveLabel(),value.progress()),null,target,batch);addText(value.trailingLabel(),ix+segment+(segment-trailing.width())/2,labelY,labelScale,theme.lerp(theme.segmentedControlInactiveLabel(),theme.segmentedControlActiveLabel(),value.progress()),null,target,batch);}
    private static void renderSlider(Slider value,Render2DScheduler.LayerHandle target){UiRect b=value.bounds();float handleWidth=Math.max(1,value.handleWidth()),handleX=b.x()+b.width()*value.progress()-handleWidth/2,handleY=b.centerY()-value.handleHeight()/2,width=Math.max(value.activeMinWidth(),b.width()*value.progress()-value.activeEndInset());target.addRoundRect(bounds(b),value.trackRadius(),value.trackColor());if(width>0)target.addRoundRect(new Render2DBounds(b.x(),b.y(),Math.min(b.width(),width),b.height()),value.trackRadius(),1,1,value.trackRadius(),value.activeColor());target.addRoundRect(new Render2DBounds(handleX,handleY,handleWidth,value.handleHeight()),value.handleRadius(),value.handleColor());}
    private com.github.slmpc.lumingraphics.text.layout.TextMeasurement measure(String value,float scale,String fontId){return text.measure(value,scale,resources.font(fontId));}
    private void addText(String value,float x,float y,float scale,LuminColor color,String fontId,Render2DScheduler.LayerHandle target,UiRenderBatch batch){bind(target,batch);text.add(value,x,y,scale,color,resources.font(fontId));drawBound();}
    private static boolean pushScissor(UiRect clip,Render2DScheduler.LayerHandle target){int left=Math.max(0,Math.round(clip.x())),top=Math.max(0,Math.round(clip.y())),right=Math.round(clip.right()),bottom=Math.round(clip.bottom());if(right<=left||bottom<=top)return false;target.pushScissor(new Render2DScissor(left,top,right-left,bottom-top));return true;}
    private void bind(Render2DScheduler.LayerHandle target, UiRenderBatch batch){if(textSink!=null)textSink.bind(target,batch);}
    private void drawBound(){if(textSink!=null)text.draw();}
    private static Render2DBounds bounds(UiRect value){return new Render2DBounds(value.x(),value.y(),value.width(),value.height());}
}

