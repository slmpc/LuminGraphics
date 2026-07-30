package com.github.slmpc.lumingraphics.render.pipeline;

import com.github.slmpc.lumingraphics.core.vertex.LuminVertexFormats;
import com.github.slmpc.lumingraphics.core.vertex.VertexSchema;
import com.github.slmpc.prismrhi.command.RhiPrimitiveTopology;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Backend-neutral descriptions consumed by renderers when creating Prism pipelines.
 * Retained GLSL 410 sources compile directly on GL41/DSA. The build tool changes only
 * the version header to 450 and defines {@code LUMIN_VULKAN=1}, enabling explicit
 * descriptor bindings and Vulkan's vertex-index spelling before Prism Shaderc runs.
 */
public final class LuminPipelineCatalog {
    public static final FrameAbi FRAME_ABI = new FrameAbi("LuminFrame", 0, "Projection", "Viewport");

    private static final List<PipelineDescriptor> ENTRIES = buildEntries();

    private LuminPipelineCatalog() {
    }

    public static List<PipelineDescriptor> entries() {
        return ENTRIES;
    }

    public static PipelineDescriptor require(String id) {
        return ENTRIES.stream().filter(entry -> entry.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown pipeline: " + id));
    }

    private static List<PipelineDescriptor> buildEntries() {
        List<PipelineDescriptor> entries = new ArrayList<>();
        entries.add(draw("rectangle", "rectangle.vsh", "rectangle.fsh", VertexLayout.POSITION_COLOR));
        entries.add(sampledDraw("ttf-font-aa", "ttf_font.vsh", "ttf_font_aa.fsh", VertexLayout.POSITION_UV_COLOR, "Sampler0"));
        entries.add(sampledDraw("ttf-font-no-aa", "ttf_font.vsh", "ttf_font_no_aa.fsh", VertexLayout.POSITION_UV_COLOR, "Sampler0"));
        entries.add(draw("round-rectangle", "round_rectangle.vsh", "round_rectangle.fsh", VertexLayout.ROUND_RECT));
        entries.add(draw("round-rectangle-outline", "round_rectangle_outline.vsh", "round_rectangle_outline.fsh", VertexLayout.ROUND_RECT_OUTLINE));
        entries.add(draw("shadow", "shadow.vsh", "shadow.fsh", VertexLayout.ROUND_RECT));
        entries.add(draw("segmented-shadow", "segmented_shadow.vsh", "segmented_shadow.fsh", VertexLayout.POSITION));
        entries.add(sampledDraw("texture", "texture.vsh", "texture.fsh", VertexLayout.TEXTURE, "Sampler0"));
        entries.add(triangles("triangle", "triangle.vsh", "triangle.fsh", VertexLayout.POSITION_COLOR));
        entries.add(sampled("blur", "blur.vsh", "blur.fsh", VertexLayout.FULLSCREEN, "InputSampler"));
        entries.add(sampledDraw("blur-3d-box", "blur_3d_box.vsh", "blur_3d_box.fsh", VertexLayout.POSITION_COLOR, "InputSampler"));
        entries.add(sampled("filter", "blur.vsh", "filter.fsh", VertexLayout.FULLSCREEN, "InputSampler"));
        entries.add(sampled("fxaa", "blur.vsh", "fxaa.fsh", VertexLayout.FULLSCREEN, "InputSampler"));

        for (String effect : List.of("shader_copy", "shader_fade", "shader_glow", "shader_gradient",
                "shader_outline", "shader_smoke", "shader_snow")) {
            entries.add(sampled("effect-" + effect.substring(7).replace('_', '-'), "blur.vsh", effect + ".fsh",
                    VertexLayout.FULLSCREEN, "InputSampler"));
        }
        for (String menu : List.of("alien_terrain", "black_hole", "clouds", "inferno", "voxel_landscape", "planet", "sea_level")) {
            entries.add(fullscreen("menu-" + menu.replace('_', '-'), "blur.vsh", "menu/" + menu + ".fsh"));
        }
        return List.copyOf(entries);
    }

    private static PipelineDescriptor draw(String id, String vertex, String fragment, VertexLayout layout) {
        return descriptor(id, vertex, fragment, layout, GeometryExpansion.QUAD_TO_INDEXED_TRIANGLES,
                Set.of(), "draw pipeline");
    }

    private static PipelineDescriptor triangles(String id, String vertex, String fragment, VertexLayout layout) {
        return descriptor(id, vertex, fragment, layout, GeometryExpansion.NONE, Set.of(), "triangle draw pipeline");
    }

    private static PipelineDescriptor sampled(String id, String vertex, String fragment, VertexLayout layout, String sampler) {
        return descriptor(id, vertex, fragment, layout, GeometryExpansion.NONE, Set.of(sampler), "sampled effect");
    }

    private static PipelineDescriptor sampledDraw(String id, String vertex, String fragment, VertexLayout layout, String sampler) {
        return descriptor(id, vertex, fragment, layout, GeometryExpansion.QUAD_TO_INDEXED_TRIANGLES,
                Set.of(sampler), "sampled draw pipeline");
    }

    private static PipelineDescriptor fullscreen(String id, String vertex, String fragment) {
        return descriptor(id, vertex, fragment, VertexLayout.FULLSCREEN, GeometryExpansion.NONE,
                Set.of(), "sandbox effect");
    }

    private static PipelineDescriptor descriptor(String id, String vertex, String fragment, VertexLayout layout,
                                                  GeometryExpansion expansion, Set<String> samplers, String role) {
        return new PipelineDescriptor(id, ShaderRef.vertex(vertex), ShaderRef.fragment(fragment), layout,
                RhiPrimitiveTopology.TRIANGLE_LIST, expansion, Blend.TRANSLUCENT, Depth.DISABLED, Raster.NO_CULL,
                FRAME_ABI, drawAbi(layout), samplers, role);
    }

    private static DrawAbi drawAbi(VertexLayout layout) {
        List<String> attributes = switch (layout) {
            case POSITION -> List.of("Position");
            case POSITION_COLOR -> List.of("Position", "Color");
            case POSITION_UV_COLOR -> List.of("Position", "UV0", "Color");
            case ROUND_RECT -> List.of("Position", "Color", "InnerRect", "Radius");
            case ROUND_RECT_OUTLINE -> List.of("Position", "Color", "InnerRect", "Radius", "OutlineWidth");
            case TEXTURE -> List.of("Position", "Color", "UV0", "InnerRect", "Radius");
            case FULLSCREEN -> List.of();
        };
        return new DrawAbi(true, attributes);
    }

    public enum ShaderStage { VERTEX, FRAGMENT }
    public enum GeometryExpansion { NONE, QUAD_TO_INDEXED_TRIANGLES }
    public enum Blend { TRANSLUCENT }
    public enum Depth { DISABLED }
    public enum Raster { NO_CULL }

    public enum VertexLayout {
        POSITION(null, List.of(0)),
        POSITION_COLOR(null, List.of(0, 1)),
        POSITION_UV_COLOR(null, List.of(0, 1, 2)),
        FULLSCREEN(null, List.of()),
        ROUND_RECT(LuminVertexFormats.ROUND_RECT),
        ROUND_RECT_OUTLINE(LuminVertexFormats.ROUND_RECT_OUTLINE),
        TEXTURE(LuminVertexFormats.TEXTURE);

        private final VertexSchema schema;
        private final List<Integer> locations;

        VertexLayout(VertexSchema schema) {
            this(schema, schema.elements().stream().map(element -> element.location()).toList());
        }

        VertexLayout(VertexSchema schema, List<Integer> locations) {
            this.schema = schema;
            this.locations = List.copyOf(locations);
        }

        public VertexSchema schema() {
            return schema;
        }

        public List<Integer> locations() {
            return locations;
        }
    }

    public record ShaderRef(String sourcePath, String spirvPath, ShaderStage stage, String entryPoint) {
        private static ShaderRef vertex(String path) {
            return of(path, ShaderStage.VERTEX);
        }

        private static ShaderRef fragment(String path) {
            return of(path, ShaderStage.FRAGMENT);
        }

        private static ShaderRef of(String path, ShaderStage stage) {
            return new ShaderRef(path, "spirv/" + path + ".spv", stage, "main");
        }
    }

    public record FrameAbi(String block, int binding, String projection, String viewport) { }
    public record DrawAbi(boolean cpuTransformedPositions, List<String> attributes) {
        public DrawAbi {
            attributes = List.copyOf(attributes);
        }
    }

    public record PipelineDescriptor(
            String id,
            ShaderRef vertex,
            ShaderRef fragment,
            VertexLayout vertexLayout,
            RhiPrimitiveTopology topology,
            GeometryExpansion geometryExpansion,
            Blend blend,
            Depth depth,
            Raster raster,
            FrameAbi frameAbi,
            DrawAbi drawAbi,
            Set<String> samplers,
            String role
    ) {
        public PipelineDescriptor {
            samplers = Set.copyOf(samplers);
        }
    }
}
