class_name SurfacePolish
extends Node

@export var model_root_path: NodePath

var fur_shader: Shader

func _ready() -> void:
    _build_fur_shader()
    var root: Node = get_node_or_null(model_root_path) if not model_root_path.is_empty() else get_parent()
    if root == null:
        return
    await get_tree().process_frame
    _polish_recursive(root)

func _build_fur_shader() -> void:
    fur_shader = Shader.new()
    fur_shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;

uniform vec4 base_color : source_color = vec4(0.27, 0.28, 0.31, 1.0);
uniform float detail_strength = 0.105;
uniform float rim_strength = 0.075;
varying vec3 object_pos;

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
    object_pos = VERTEX;
}

void fragment() {
    float broad = value_noise(object_pos * 10.0);
    float fine = value_noise(object_pos * 46.0 + vec3(7.1, 3.4, 11.7));
    float micro = value_noise(object_pos * 92.0 + vec3(19.2, 5.8, 2.6));
    float tonal = 0.925 + (broad - 0.5) * 0.10 + (fine - 0.5) * detail_strength + (micro - 0.5) * 0.030;
    ALBEDO = base_color.rgb * tonal;
    ROUGHNESS = clamp(0.82 + (1.0 - fine) * 0.12 + (micro - 0.5) * 0.035, 0.78, 0.94);
    SPECULAR = 0.20;
    float rim = pow(1.0 - max(dot(normalize(NORMAL), normalize(VIEW)), 0.0), 3.2);
    EMISSION = base_color.rgb * rim * rim_strength;
}
"""

func _polish_recursive(node: Node) -> void:
    if node is MeshInstance3D:
        _polish_mesh(node as MeshInstance3D)
    for child: Node in node.get_children():
        _polish_recursive(child)

func _polish_mesh(mi: MeshInstance3D) -> void:
    if mi.mesh == null:
        return
    var lname: String = mi.name.to_lower()
    for surface: int in range(mi.mesh.get_surface_count()):
        var src: Material = mi.get_active_material(surface)
        if src == null:
            continue
        var mat: Material = src.duplicate(true)
        if mat is StandardMaterial3D:
            var sm: StandardMaterial3D = mat as StandardMaterial3D
            if _has_any(lname, ["eye", "iris", "pupil", "cornea", "highlight"]):
                sm.roughness = 0.08
                sm.metallic = 0.0
                sm.metallic_specular = 0.92
                sm.clearcoat_enabled = true
                sm.clearcoat = 0.48
                sm.clearcoat_roughness = 0.07
                mi.set_surface_override_material(surface, sm)
            elif _has_any(lname, ["fur", "muzzle", "stripe"]):
                var fm: ShaderMaterial = ShaderMaterial.new()
                fm.shader = fur_shader
                fm.set_shader_parameter("base_color", sm.albedo_color)
                fm.set_shader_parameter("detail_strength", 0.085 if lname.contains("muzzle") else 0.105)
                fm.set_shader_parameter("rim_strength", 0.060 if lname.contains("muzzle") else 0.075)
                mi.set_surface_override_material(surface, fm)
            elif _has_any(lname, ["nose", "snout"]):
                sm.roughness = 0.34
                sm.metallic_specular = 0.54
                mi.set_surface_override_material(surface, sm)
            elif _has_any(lname, ["metal", "buckle", "button", "headphone", "mic", "logo", "cord"]):
                sm.metallic = maxf(sm.metallic, 0.58)
                sm.roughness = 0.28
                sm.metallic_specular = 0.72
                mi.set_surface_override_material(surface, sm)
            else:
                sm.roughness = clampf(sm.roughness + 0.10, 0.42, 0.92)
                sm.metallic_specular = minf(sm.metallic_specular, 0.34)
                mi.set_surface_override_material(surface, sm)

func _has_any(value: String, tokens: Array[String]) -> bool:
    for token: String in tokens:
        if value.contains(token):
            return true
    return false
