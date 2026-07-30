#version 410 core

#ifdef LUMIN_VULKAN
#define LUMIN_BINDING(index) layout(set = 0, binding = index)
#define LUMIN_VERTEX_ID gl_VertexIndex
#else
#define LUMIN_BINDING(index)
#define LUMIN_VERTEX_ID gl_VertexID
#endif

layout(location = 0) in vec4 v_Color;

layout(location = 0) out vec4 f_Color;

void main() {
    f_Color = v_Color;
}
