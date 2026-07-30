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

layout(location = 0) out vec2 f_Position;

void main() {
    gl_Position = Projection * vec4(Position.xy, 0.0, 1.0);
    f_Position = Position.xy;
}
