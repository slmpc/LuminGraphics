#version 410 core

#ifdef LUMIN_VULKAN
#define LUMIN_BINDING(index) layout(set = 0, binding = index)
#define LUMIN_VERTEX_ID gl_VertexIndex
#else
#define LUMIN_BINDING(index)
#define LUMIN_VERTEX_ID gl_VertexID
#endif

LUMIN_BINDING(0) uniform sampler2D InputSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    // This pass is intentionally kept: testing showed higher FPS than using a direct texture copy command.
    fragColor = texture(InputSampler, texCoord);
}
