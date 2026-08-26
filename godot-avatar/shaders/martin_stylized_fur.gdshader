shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx, cull_back;

uniform vec4 base_color : source_color = vec4(0.235, 0.245, 0.275, 1.0);
uniform float texture_scale : hint_range(2.0, 80.0) = 30.0;
uniform float texture_strength : hint_range(0.0, 0.2) = 0.055;
uniform float rim_strength : hint_range(0.0, 0.3) = 0.055;

varying vec3 world_pos;
varying vec3 world_normal;

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float value_noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash31(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash31(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash31(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash31(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash31(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash31(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash31(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash31(i + vec3(1.0, 1.0, 1.0));
    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);
    float nxy0 = mix(nx00, nx10, f.y);
    float nxy1 = mix(nx01, nx11, f.y);
    return mix(nxy0, nxy1, f.z);
}

void vertex() {
    world_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
    world_normal = normalize((MODEL_MATRIX * vec4(NORMAL, 0.0)).xyz);
}

void fragment() {
    float fine = value_noise(world_pos * texture_scale);
    float broad = value_noise(world_pos * (texture_scale * 0.18));
    float variation = (fine - 0.5) * texture_strength + (broad - 0.5) * texture_strength * 0.55;
    vec3 n = normalize(world_normal);
    float facing = max(dot(normalize(NORMAL), normalize(VIEW)), 0.0);
    float rim = pow(1.0 - facing, 3.0) * rim_strength;
    ALBEDO = base_color.rgb * (1.0 + variation);
    ROUGHNESS = 0.80;
    SPECULAR = 0.24;
    EMISSION = base_color.rgb * rim;
}
