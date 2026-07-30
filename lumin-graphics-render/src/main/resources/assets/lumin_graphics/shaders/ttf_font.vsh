#version 410 core

#ifdef LUMIN_VULKAN
#define LUMIN_BINDING(index) layout(set = 0, binding = index)
#define LUMIN_VERTEX_ID gl_VertexIndex
#else
#define LUMIN_BINDING(index)
#define LUMIN_VERTEX_ID gl_VertexID
#endif

layout(std140) LUMIN_BINDING(0) uniform LuminFrame {
    mat4 Projection;
    vec4 Viewport;
};


layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(location = 0) out vec4 v_Color;
layout(location = 1) out vec2 v_TexCoord;

void main() {
    gl_Position = Projection * vec4(Position, 1.0);

    v_Color = Color;
    v_TexCoord = UV0;
}
