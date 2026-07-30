package com.github.slmpc.lumingraphics.ui;

public sealed interface UiNode permits Layered, Layer, Scissor, Shadow, SegmentedShadow, RoundRect,
        RoundRectGradient, Rect, RectGradient, RectOutline, Outline, Text, RotatedText, MarqueeText,
        Texture, RotatedTexture, Button, Switch, FilledField, Input, AssistChip, SegmentedControl,
        IconButton, PopupCard, Slider, Triangle, Viewport { }
