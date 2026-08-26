shader_type spatial;
render_mode blend_mix, depth_draw_alpha_prepass, cull_back, diffuse_burley, specular_schlick_ggx;

uniform vec4 fur_color : source_color = vec4(0.22, 0.24, 0.29, 1.0);
uniform float shell_offset : hint_range(0.0, 0.03) = 0.008;
uniform float shell_alpha : hint_range(0.0, 1.0) = 0.22;
uniform float strand_density : hint_range(8.0, 96.0) = 52.0;
uniform float scruffiness : hint_range(0.0, 1.0) = 0.28;
uniform float root_darkening : hint_range(0.0, 1.0) = 0.20;
uniform float rim_strength : hint_range(0.0, 1.0) = 0.16;
uniform float shell_ratio : hint_range(0.0, 1.0) = 0.5;

varying vec3 world_normal;
varying vec3 world_pos;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

void vertex() {
    float irregular = 1.0 + sin((VERTEX.x + VERTEX.y * 1.7 + VERTEX.z * 0.73) * 27.0) * scruffiness * 0.18;
    VERTEX += NORMAL * shell_offset * irregular;
    world_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
    world_normal = normalize((MODEL_MATRIX * vec4(NORMAL, 0.0)).xyz);
}

void fragment() {
    vec3 n = normalize(world_normal);
    vec3 an = abs(n);
    vec2 uvx = world_pos.yz * strand_density;
    vec2 uvy = world_pos.xz * strand_density;
    vec2 uvz = world_pos.xy * strand_density;
    float wx = an.x / max(an.x + an.y + an.z, 0.001);
    float wy = an.y / max(an.x + an.y + an.z, 0.001);
    float wz = an.z / max(an.x + an.y + an.z, 0.001);
    float grain = hash21(floor(uvx)) * wx + hash21(floor(uvy)) * wy + hash21(floor(uvz)) * wz;
    float strand = smoothstep(0.32 + shell_ratio * 0.22, 0.86, grain);
    float root = mix(1.0 - root_darkening, 1.0, shell_ratio);
    ALBEDO = fur_color.rgb * root;
    ROUGHNESS = 0.84;
    SPECULAR = 0.24;
    float rim = pow(1.0 - max(dot(normalize(NORMAL), normalize(VIEW)), 0.0), 3.0) * rim_strength;
    EMISSION = fur_color.rgb * rim;
    ALPHA = strand * shell_alpha * (0.68 + 0.32 * shell_ratio);
}
