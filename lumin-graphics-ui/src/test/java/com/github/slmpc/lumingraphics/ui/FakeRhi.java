package com.github.slmpc.lumingraphics.ui;

import com.github.slmpc.lumingraphics.render.RenderExecution;
import com.github.slmpc.lumingraphics.render.RenderResources;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.prismrhi.backend.BackendApi;
import com.github.slmpc.prismrhi.command.RhiCommandBuffer;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSet;
import com.github.slmpc.prismrhi.device.RhiDevice;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipeline;
import com.github.slmpc.prismrhi.resource.RhiBuffer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class FakeRhi {
    private final List<String> trace=new ArrayList<>(); private int failDrawAttempt,drawAttempts,closedBuffers;
    private final RhiDevice device=proxy(RhiDevice.class,(self,method,args)->switch(method.getName()){
        case "api"->BackendApi.VULKAN;case "createBuffer"->buffer(((com.github.slmpc.prismrhi.resource.RhiBufferCreateInfo)args[0]).size());case "close"->null;default->defaultValue(method);});
    RenderResources resources(){return new RenderResources(){public RhiDevice device(){return device;}public RhiGraphicsPipeline requirePipeline(String id){trace.add("pipeline="+id);return proxy(RhiGraphicsPipeline.class,FakeRhi::resourceCall);}public RhiDescriptorSet requireTextureDescriptor(Render2DTexture texture){String id=texture instanceof Render2DTexture.Resource value?value.id():"lumin";trace.add("texture="+id);return proxy(RhiDescriptorSet.class,FakeRhi::resourceCall);}public RhiDescriptorSet requireSegmentedShadowDescriptor(Render2DCommand.SegmentedShadow shadow){trace.add("segments="+shadow.segmentCount());return proxy(RhiDescriptorSet.class,FakeRhi::resourceCall);}};}
    RenderExecution execution(){return new RenderExecution(commandBuffer(),resources(),1,0,320,240);}
    List<String> trace(){return List.copyOf(trace);}int closedBuffers(){return closedBuffers;}int drawAttempts(){return drawAttempts;}
    void failNextDraw(){failDrawAttempt=drawAttempts+1;}void failDraw(int attempt){if(attempt<=0)throw new IllegalArgumentException("attempt must be positive");failDrawAttempt=attempt;}
    private RhiBuffer buffer(long size){AtomicBoolean closed=new AtomicBoolean();return proxy(RhiBuffer.class,(self,method,args)->switch(method.getName()){
        case "api"->BackendApi.VULKAN;case "size"->size;case "write"->{ByteBuffer value=((ByteBuffer)args[1]).slice();trace.add("write="+value.remaining());yield null;}case "close"->{if(closed.compareAndSet(false,true))closedBuffers++;yield null;}default->defaultValue(method);});}
    private RhiCommandBuffer commandBuffer(){return proxy(RhiCommandBuffer.class,(self,method,args)->switch(method.getName()){
        case "api"->BackendApi.VULKAN;case "setScissor"->{var rect=(com.github.slmpc.prismrhi.rendering.RhiRect2D)args[0];trace.add("scissor="+rect.offset().x()+","+rect.offset().y()+","+rect.extent().width()+","+rect.extent().height());yield null;}case "draw"->{drawAttempts++;if(drawAttempts==failDrawAttempt)throw new IllegalStateException("backend draw failed at attempt "+drawAttempts);trace.add("draw");yield null;}case "bindGraphicsPipeline","bindDescriptorSet","bindVertexBuffer","setViewport","begin","end","close"->null;case "level"->com.github.slmpc.prismrhi.command.RhiCommandBufferLevel.PRIMARY;default->defaultValue(method);});}
    private static Object resourceCall(Object self,Method method,Object[] args){if(method.getName().equals("api"))return BackendApi.VULKAN;if(method.getName().equals("close"))return null;return defaultValue(method);}
    private static Object defaultValue(Method method){Class<?> type=method.getReturnType();if(!type.isPrimitive())return null;if(type==boolean.class)return false;if(type==long.class)return 0L;if(type==int.class)return 0;return null;}
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,InvocationHandler handler){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);}
}
