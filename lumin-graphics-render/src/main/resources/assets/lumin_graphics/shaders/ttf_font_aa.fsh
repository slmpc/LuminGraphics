#version 410 core
#extension GL_ARB_shading_language_420pack : require

#ifdef LUMIN_VULKAN
#define LUMIN_BINDING(index) layout(set = 0, binding = index)
#define LUMIN_VERTEX_ID gl_VertexIndex
#else
#define LUMIN_BINDING(index) layout(binding = index)
#define LUMIN_VERTEX_ID gl_VertexID
#endif

layout(location = 0) in vec4 v_Color;
layout(location = 1) in vec2 v_TexCoord;

LUMIN_BINDING(1) uniform sampler2D Sampler0;

layout(location = 0) out vec4 f_Color;

void main() {
    float alpha = texture(Sampler0, v_TexCoord).r;
    f_Color = vec4(v_Color.rgb, v_Color.a * alpha);

    if (f_Color.a < 0.005) {
        discard;
    }
}
