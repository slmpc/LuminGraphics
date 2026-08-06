package com.github.slmpc.lumingraphics.ui.control;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;

public record ButtonElement(UiRect bounds, float radius, LuminColor background, String label, float labelScale,
                            LuminColor labelColor) {
}

