package com.github.slmpc.lumingraphics.ui.scene;
import java.util.Objects;
public final class UiLayerStack { public static final int STRIDE=100; public int resolve(UiLayer layer){return Objects.requireNonNull(layer).baseLayer();} public int resolve(UiLayer layer,int relative){if(relative<=-STRIDE||relative>=STRIDE)throw new IllegalArgumentException("relative layer is outside semantic stride");return resolve(layer)+relative;} }
