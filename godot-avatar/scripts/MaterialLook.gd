extends Node

var fur_shader := Shader.new()
var cloth_shader := Shader.new()
var fur_material := ShaderMaterial.new()
var cloth_material := ShaderMaterial.new()
var eye_material := StandardMaterial3D.new()
var nose_material := StandardMaterial3D.new()

func _ready() -> void:
    fur_shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform vec4 base_color : source_color = vec4(0.37, 0.39, 0.45, 1.0);
uniform float fiber_scale = 88.0;
uniform float rim_strength = 0.24;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void fragment() {
    vec2 u = UV * vec2(fiber_scale * 0.58, fiber_scale);
    float strand = 0.5 + 0.5 * sin((u.y + sin(u.x * 0.16) * 0.42) * 6.28318);
    float fine = 0.5 + 0.5 * sin((u.y * 2.35 + u.x * 0.19) * 6.28318);
    float n = hash21(floor(u * 0.38));
    float tonal = 0.87 + strand * 0.07 + fine * 0.025 + (n - 0.5) * 0.065;
    ALBEDO = base_color.rgb * tonal;
    ROUGHNESS = clamp(0.70 + (1.0 - strand) * 0.14 + (n - 0.5) * 0.055, 0.58, 0.90);
    SPECULAR = 0.30;
    NORMAL = normalize(NORMAL + TANGENT * (strand - 0.5) * 0.050 + BINORMAL * (fine - 0.5) * 0.026);
    float rim = pow(1.0 - max(dot(NORMAL, VIEW), 0.0), 3.2);
    EMISSION = base_color.rgb * rim * rim_strength;
}
"""
    cloth_shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform vec4 base_color : source_color = vec4(0.014, 0.017, 0.028, 1.0);
uniform float weave_scale = 132.0;

void fragment() {
    vec2 u = UV * weave_scale;
    float wx = 0.5 + 0.5 * sin(u.x * 6.28318);
    float wy = 0.5 + 0.5 * sin(u.y * 6.28318);
    float weave = mix(wx, wy, step(0.5, fract(u.x * 0.5 + u.y * 0.5)));
    float micro = 0.5 + 0.5 * sin((u.x + u.y) * 12.56636);
    ALBEDO = base_color.rgb * (0.84 + weave * 0.15 + micro * 0.022);
    ROUGHNESS = clamp(0.80 + (1.0 - weave) * 0.11, 0.76, 0.94);
    SPECULAR = 0.18;
    NORMAL = normalize(NORMAL + TANGENT * (wx - 0.5) * 0.026 + BINORMAL * (wy - 0.5) * 0.026);
}
"""
    fur_material.shader = fur_shader
    cloth_material.shader = cloth_shader

    eye_material.albedo_color = Color(0.16, 0.82, 0.08, 1.0)
    eye_material.metallic = 0.0
    eye_material.roughness = 0.10
    eye_material.emission_enabled = true
    eye_material.emission = Color(0.025, 0.20, 0.01, 1.0)
    eye_material.emission_energy_multiplier = 0.55

    nose_material.albedo_color = Color(0.74, 0.27, 0.32, 1.0)
    nose_material.roughness = 0.28
    nose_material.metallic = 0.0

    call_deferred("_apply")

func _apply() -> void:
    await get_tree().process_frame
    var root := get_tree().current_scene
    if root == null:
        return

    var counters := {"fur": 0, "cloth": 0, "eye": 0, "nose": 0}
    _walk_and_apply(root, counters)
    print("MARTIN_MATERIALS_OK fur=%d cloth=%d eye=%d nose=%d" % [counters.fur, counters.cloth, counters.eye, counters.nose])

func _walk_and_apply(node: Node, counters: Dictionary) -> void:
    if node is MeshInstance3D:
        var mesh_node := node as MeshInstance3D
        var label := mesh_node.name.to_lower()
        var parent_label := mesh_node.get_parent().name.to_lower() if mesh_node.get_parent() != null else ""
        var key := label + " " + parent_label

        if _contains_any(key, ["eye", "iris"]):
            mesh_node.material_override = eye_material
            counters.eye += 1
        elif _contains_any(key, ["nose", "snout_nose"]):
            mesh_node.material_override = nose_material
            counters.nose += 1
        elif _contains_any(key, ["shirt", "hood", "cloth", "jacket", "body", "chest", "suit", "vest"]):
            mesh_node.material_override = cloth_material
            counters.cloth += 1
        elif _contains_any(key, ["fur", "head", "muzzle", "ear", "paw", "arm", "leg", "tail", "cat", "face"]):
            mesh_node.material_override = fur_material
            counters.fur += 1

    for child in node.get_children():
        _walk_and_apply(child, counters)

func _contains_any(text: String, tokens: Array) -> bool:
    for token in tokens:
        if text.contains(String(token)):
            return true
    return false
