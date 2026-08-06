package com.github.slmpc.lumingraphics.ui.node.primitive;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiNode;
import com.github.slmpc.lumingraphics.ui.tree.UiNodes;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;

public record SegmentedShadow(UiRect bounds, float[] radii, float blurRadius, LuminColor color, float[] segmentRects,
                              float[] segmentRadii, int segmentCount) implements UiNode {
    public SegmentedShadow {
        UiNodes.require(bounds);
        UiNodes.require(color);
        UiNodes.require(radii);
        UiNodes.require(segmentRects);
        UiNodes.require(segmentRadii);
        if (radii.length != 4 || segmentCount <= 0 || segmentRects.length != segmentCount * 4 || segmentRadii.length != segmentCount)
            throw new IllegalArgumentException("segmented shadow arrays are malformed");
        radii = radii.clone();
        segmentRects = segmentRects.clone();
        segmentRadii = segmentRadii.clone();
        UiNodes.nonNegative(blurRadius);
        UiNodes.nonNegative(radii);
        UiNodes.finite(segmentRects);
        UiNodes.nonNegative(segmentRadii);
    }

    @Override
    public float[] radii() {
        return radii.clone();
    }

    @Override
    public float[] segmentRects() {
        return segmentRects.clone();
    }

    @Override
    public float[] segmentRadii() {
        return segmentRadii.clone();
    }
}

