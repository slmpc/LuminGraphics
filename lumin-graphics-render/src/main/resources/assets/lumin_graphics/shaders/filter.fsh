#version 410 core

#ifdef LUMIN_VULKAN
#define LUMIN_BINDING(index) layout(set = 0, binding = index)
#define LUMIN_VERTEX_ID gl_VertexIndex
#else
#define LUMIN_BINDING(index)
#define LUMIN_VERTEX_ID gl_VertexID
#endif

LUMIN_BINDING(0) uniform sampler2D InputSampler;

layout(std140) LUMIN_BINDING(1) uniform FilterColor {
    vec4 TintColor;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 sceneColor = texture(InputSampler, texCoord);
    vec3 tinted = mix(sceneColor.rgb, TintColor.rgb, clamp(TintColor.a, 0.0, 1.0));
    fragColor = vec4(tinted, sceneColor.a);
}
