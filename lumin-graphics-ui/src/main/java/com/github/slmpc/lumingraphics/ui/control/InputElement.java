package com.github.slmpc.lumingraphics.ui.control;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
public record InputElement(UiRect bounds, boolean focused, float hoverProgress, float focusRingProgress,
        LuminColor focusRingColor, float focusRingInset, float textInset, String text, float textScale,
        LuminColor textColor, SelectionRange selection, LuminColor selectionColor, Integer caretIndex,
        LuminColor caretColor, String trailingHint, float trailingHintScale, LuminColor trailingHintColor) { }

