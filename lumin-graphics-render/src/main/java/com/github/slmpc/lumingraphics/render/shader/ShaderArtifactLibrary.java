package com.github.slmpc.lumingraphics.render.shader;

import com.github.slmpc.lumingraphics.render.pipeline.LuminPipelineCatalog.ShaderRef;
import com.github.slmpc.lumingraphics.render.pipeline.LuminPipelineCatalog.ShaderStage;
import com.github.slmpc.prismrhi.device.RhiDevice;
import com.github.slmpc.prismrhi.shader.RhiShader;
import com.github.slmpc.prismrhi.shader.RhiShaderBinaryFormat;
import com.github.slmpc.prismrhi.shader.RhiShaderDesc;
import com.github.slmpc.prismrhi.shader.RhiShaderStage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/** Loads retained source or precompiled bytes; shader compilation is never selected at runtime. */
public final class ShaderArtifactLibrary {
    private static final String ROOT = "/assets/lumin_graphics/shaders/";

    private ShaderArtifactLibrary() {
    }

    public static ByteBuffer load(ShaderRef shader, RhiShaderBinaryFormat format) {
        if (shader == null || format == null) {
            throw new IllegalArgumentException("shader and binary format are required");
        }
        String relative = switch (format) {
            case OPENGL_SOURCE -> shader.sourcePath();
            case SPIRV -> shader.spirvPath();
        };
        try (InputStream input = ShaderArtifactLibrary.class.getResourceAsStream(ROOT + relative)) {
            if (input == null) {
                throw new IllegalArgumentException("shader artifact is missing: " + relative);
            }
            return ByteBuffer.wrap(input.readAllBytes()).asReadOnlyBuffer();
        } catch (IOException error) {
            throw new IllegalStateException("failed to read shader artifact: " + relative, error);
        }
    }

    public static RhiShader create(RhiDevice device, ShaderRef shader, RhiShaderBinaryFormat format) {
        if (device == null) {
            throw new IllegalArgumentException("device is required");
        }
        RhiShaderStage stage = shader.stage() == ShaderStage.VERTEX ? RhiShaderStage.VERTEX : RhiShaderStage.FRAGMENT;
        RhiShaderDesc desc = new RhiShaderDesc(stage, shader.entryPoint(), shader.sourcePath());
        return device.createShader(desc, format, load(shader, format));
    }
}
