#version 410 core

#ifdef LUMIN_VULKAN
#define LUMIN_BINDING(index) layout(set = 0, binding = index)
#define LUMIN_VERTEX_ID gl_VertexIndex
#else
#define LUMIN_BINDING(index)
#define LUMIN_VERTEX_ID gl_VertexID
#endif

LUMIN_BINDING(0) uniform sampler2D InputSampler;

layout(std140) LUMIN_BINDING(1) uniform BoxBlurUniforms {
    vec4 Params;
};

layout(location = 0) out vec4 fragColor;

vec4 blur() {
    #define TAU 6.28318530718

    vec2 inputResolution = Params.xy;
    float quality = Params.z;
    vec2 radius = quality / inputResolution.xy;
    vec2 uv = gl_FragCoord.xy / inputResolution.xy;

    vec4 color = texture(InputSampler, uv);
    float step = TAU / 16.0;

    for (float d = 0.0; d < TAU; d += step) {
        for (float i = 0.2; i <= 1.0; i += 0.2) {
            color += texture(InputSampler, uv + vec2(cos(d), sin(d)) * radius * i);
        }
    }

    return color / 81.0;
}

void main() {
    fragColor = vec4(blur().rgb, 1.0);
}
