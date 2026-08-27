shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx, cull_back;

uniform vec4 base_color : source_color = vec4(1.0);
uniform float roughness_value : hint_range(0.0, 1.0) = 0.16;
uniform float specular_value : hint_range(0.0, 1.0) = 0.75;
uniform float emission_value : hint_range(0.0, 0.3) = 0.04;

void fragment() {
    ALBEDO = base_color.rgb;
    ROUGHNESS = roughness_value;
    SPECULAR = specular_value;
    EMISSION = base_color.rgb * emission_value;
}
