package com.github.slmpc.lumingraphics.core;

import com.github.slmpc.lumingraphics.core.exception.LuminValidationException;
import com.github.slmpc.lumingraphics.core.geometry.CoordinateConverter;
import com.github.slmpc.lumingraphics.core.geometry.CoordinateOrigin;
import com.github.slmpc.lumingraphics.core.geometry.FramebufferRect;
import com.github.slmpc.lumingraphics.core.geometry.LogicalPoint;
import com.github.slmpc.lumingraphics.core.geometry.LogicalRect;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import com.github.slmpc.lumingraphics.core.geometry.ScissorPolicy;
import com.github.slmpc.lumingraphics.core.geometry.SurfaceMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometryBehaviorTest {
    @Test
    void preservesEpsilonTopLeftFlipAndClampForIntegralInputs() {
        SurfaceMetrics metrics = new SurfaceMetrics(200, 100, 2.0);
        FramebufferRect converted = CoordinateConverter.toFramebufferScissor(
                new LogicalRect(10, 5, 30, 20), metrics,
                CoordinateOrigin.TOP_LEFT, ScissorPolicy.CLAMP_TO_FRAMEBUFFER
        );
        assertEquals(new FramebufferRect(20, 50, 60, 40), converted);
        assertEquals(
                new FramebufferRect(0, 0, 20, 10),
                CoordinateConverter.toFramebufferScissor(
                        new LogicalRect(-5, 45, 15, 10), metrics,
                        CoordinateOrigin.TOP_LEFT, ScissorPolicy.CLAMP_TO_FRAMEBUFFER
                )
        );
    }

    @Test
    void outwardRoundsFractionalLogicalBoundsAndSupportsBottomLeftOrigin() {
        SurfaceMetrics metrics = new SurfaceMetrics(100, 100, 1.5);
        assertEquals(
                new FramebufferRect(1, 3, 4, 4),
                CoordinateConverter.toFramebufferScissor(
                        new LogicalRect(1.1, 2.2, 1.7, 2.4), metrics,
                        CoordinateOrigin.BOTTOM_LEFT, ScissorPolicy.REJECT_OUT_OF_BOUNDS
                )
        );
    }

    @Test
    void rejectsOutOfBoundsNegativeNonFiniteAndOverflowBeforeConversion() {
        SurfaceMetrics metrics = new SurfaceMetrics(100, 100, 1.0);
        assertThrows(LuminValidationException.class, () -> CoordinateConverter.toFramebufferScissor(
                new LogicalRect(-1, 0, 2, 2), metrics,
                CoordinateOrigin.TOP_LEFT, ScissorPolicy.REJECT_OUT_OF_BOUNDS
        ));
        assertThrows(LuminValidationException.class, () -> new LogicalRect(0, 0, -1, 1));
        assertThrows(LuminValidationException.class, () -> new LogicalRect(Double.NaN, 0, 1, 1));
        assertThrows(LuminValidationException.class, () -> new LogicalRect(Double.MAX_VALUE, 0, Double.MAX_VALUE, 1));
        assertThrows(LuminValidationException.class, () -> new SurfaceMetrics(10, 10, Double.POSITIVE_INFINITY));
        assertThrows(LuminValidationException.class, () -> new SurfaceMetrics(10, 10, 0));
        assertThrows(LuminValidationException.class, () -> new SurfaceMetrics(10, 10, Double.MIN_VALUE));
        assertThrows(LuminValidationException.class, () -> CoordinateConverter.toFramebufferScissor(
                null, metrics, CoordinateOrigin.TOP_LEFT, ScissorPolicy.CLAMP_TO_FRAMEBUFFER
        ));
        assertThrows(LuminValidationException.class, () -> new FramebufferRect(0, 0, 0, 1).toRhi());
    }

    @Test
    void preservesInclusiveContainsAndExplicitlyModelsEmptyIntersection() {
        LogicalRect rect = new LogicalRect(2, 3, 4, 5);
        assertTrue(rect.contains(new LogicalPoint(6, 8)));
        assertTrue(rect.intersection(new LogicalRect(20, 20, 1, 1)).isEmpty());
        assertThrows(LuminValidationException.class, () -> rect.intersection(null));
    }

    @Test
    void convertsColorWithoutLosingEightBitChannels() {
        assertEquals(0x12345678, LuminColor.fromArgb(0x78123456).toRgba8());
        assertThrows(LuminValidationException.class, () -> new LuminColor(Float.NaN, 0, 0, 1));
    }

    @Test
    void fullLogicalViewportRoundTripsWithinFramebufferAtNonBinaryScalePointThree() {
        SurfaceMetrics metrics = new SurfaceMetrics(100, 100, 0.3);
        assertEquals(
                new FramebufferRect(0, 0, 100, 100),
                CoordinateConverter.toFramebufferScissor(
                        metrics.logicalViewport(), metrics,
                        CoordinateOrigin.TOP_LEFT, ScissorPolicy.REJECT_OUT_OF_BOUNDS
                )
        );
    }

    @ParameterizedTest
    @MethodSource("surfaceRoundTripCases")
    void fullLogicalViewportRoundTripsAcrossScalesOddSizesAndOrigins(
            double scale, int width, int height, CoordinateOrigin origin
    ) {
        SurfaceMetrics metrics = new SurfaceMetrics(width, height, scale);
        assertEquals(
                new FramebufferRect(0, 0, width, height),
                CoordinateConverter.toFramebufferScissor(
                        metrics.logicalViewport(), metrics, origin,
                        ScissorPolicy.REJECT_OUT_OF_BOUNDS
                )
        );
    }

    @Test
    void boundarySnapDoesNotAcceptGenuineOutOfBoundsGeometry() {
        SurfaceMetrics metrics = new SurfaceMetrics(100, 100, 0.3);
        double outsideWidth = metrics.logicalSize().width();
        for (int index = 0; index < 32; index++) {
            outsideWidth = Math.nextUp(outsideWidth);
        }
        double genuinelyOutside = outsideWidth;
        assertThrows(LuminValidationException.class, () -> CoordinateConverter.toFramebufferScissor(
                new LogicalRect(0, 0, genuinelyOutside, metrics.logicalSize().height()),
                metrics, CoordinateOrigin.TOP_LEFT, ScissorPolicy.REJECT_OUT_OF_BOUNDS
        ));
    }

    private static Stream<Arguments> surfaceRoundTripCases() {
        return Stream.of(0.1, 0.2, 0.3, 1.25, 1.5, 2.5)
                .flatMap(scale -> Stream.of(
                        Arguments.of(scale, 101, 99, CoordinateOrigin.TOP_LEFT),
                        Arguments.of(scale, 99, 103, CoordinateOrigin.BOTTOM_LEFT)
                ));
    }
}
