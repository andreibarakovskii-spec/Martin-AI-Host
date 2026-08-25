extends Node

var fur_shader := Shader.new()
var cloth_shader := Shader.new()
var fur_material := ShaderMaterial.new()
var cloth_material := ShaderMaterial.new()

func _ready() -> void:
    fur_shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform vec4 base_color : source_color = vec4(0.39, 0.40, 0.47, 1.0);
uniform float fiber_scale = 72.0;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void fragment() {
    vec2 u = UV * vec2(fiber_scale * 0.55, fiber_scale);
    float strand = 0.5 + 0.5 * sin((u.y + sin(u.x * 0.18) * 0.45) * 6.28318);
    float fine = 0.5 + 0.5 * sin((u.y * 2.7 + u.x * 0.22) * 6.28318);
    float n = hash21(floor(u * 0.45));
    float tonal = 0.90 + strand * 0.055 + fine * 0.025 + (n - 0.5) * 0.055;
    ALBEDO = base_color.rgb * tonal;
    ROUGHNESS = clamp(0.73 + (1.0 - strand) * 0.13 + (n - 0.5) * 0.05, 0.62, 0.92);
    SPECULAR = 0.32;
    NORMAL = normalize(NORMAL + TANGENT * (strand - 0.5) * 0.065 + BINORMAL * (fine - 0.5) * 0.032);
}
"""
    cloth_shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform vec4 base_color : source_color = vec4(0.018, 0.022, 0.035, 1.0);
uniform float weave_scale = 115.0;

void fragment() {
    vec2 u = UV * weave_scale;
    float wx = 0.5 + 0.5 * sin(u.x * 6.28318);
    float wy = 0.5 + 0.5 * sin(u.y * 6.28318);
    float weave = mix(wx, wy, step(0.5, fract(u.x * 0.5 + u.y * 0.5)));
    float micro = 0.5 + 0.5 * sin((u.x + u.y) * 12.56636);
    ALBEDO = base_color.rgb * (0.88 + weave * 0.13 + micro * 0.025);
    ROUGHNESS = clamp(0.79 + (1.0 - weave) * 0.13, 0.74, 0.94);
    SPECULAR = 0.22;
    NORMAL = normalize(NORMAL + TANGENT * (wx - 0.5) * 0.035 + BINORMAL * (wy - 0.5) * 0.035);
}
"""
    fur_material.shader = fur_shader
    cloth_material.shader = cloth_shader
    call_deferred("_apply")

func _apply() -> void:
    await get_tree().process_frame
    var root := get_tree().current_scene
    if root == null:
        return
    var fur_paths := [
        "MartinRig/Head/HeadMesh",
        "MartinRig/ArmL/Upper", "MartinRig/ArmL/Paw",
        "MartinRig/ArmR/Upper", "MartinRig/ArmR/Paw",
        "MartinRig/Tail/Segment"
    ]
    for path in fur_paths:
        var node := root.get_node_or_null(path)
        if node is MeshInstance3D:
            node.material_override = fur_material
    for path in ["MartinRig/BodyRoot/Body", "MartinRig/BodyRoot/Chest"]:
        var node := root.get_node_or_null(path)
        if node is MeshInstance3D:
            node.material_override = cloth_material
    print("MARTIN_MATERIALS_OK fur=", fur_paths.size(), " cloth=2")
