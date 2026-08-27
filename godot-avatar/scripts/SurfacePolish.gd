class_name SurfacePolish
extends Node

@export var model_root_path: NodePath
@export_range(0, 4, 1) var fur_shell_count: int = 3

var fur_shader: Shader
var shell_shader: Shader

func _ready() -> void:
    _build_fur_shader()
    shell_shader = load("res://shaders/martin_fur_shell.gdshader") as Shader
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

uniform vec4 base_color : source_color = vec4(0.18, 0.19, 0.21, 1.0);
uniform float detail_strength = 0.115;
uniform float rim_strength = 0.085;
uniform float fiber_strength = 0.055;
varying vec3 object_pos;
varying vec3 object_normal;

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
    object_normal = NORMAL;
}

void fragment() {
    float broad = value_noise(object_pos * 9.0);
    float fine = value_noise(object_pos * 48.0 + vec3(7.1, 3.4, 11.7));
    float micro = value_noise(object_pos * 112.0 + vec3(19.2, 5.8, 2.6));
    float fiber = sin((object_pos.z * 92.0) + object_pos.x * 31.0 + fine * 5.0) * 0.5 + 0.5;
    float tonal = 0.925 + (broad - 0.5) * 0.11 + (fine - 0.5) * detail_strength + (micro - 0.5) * 0.035;
    tonal *= 1.0 + (fiber - 0.5) * fiber_strength;
    ALBEDO = base_color.rgb * tonal;
    ROUGHNESS = clamp(0.86 + (1.0 - fine) * 0.10 + (micro - 0.5) * 0.025, 0.80, 0.95);
    SPECULAR = 0.16;
    float rim = pow(1.0 - max(dot(normalize(NORMAL), normalize(VIEW)), 0.0), 3.0);
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
                sm.roughness = 0.055
                sm.metallic = 0.0
                sm.metallic_specular = 0.96
                sm.clearcoat_enabled = true
                sm.clearcoat = 0.58
                sm.clearcoat_roughness = 0.045
                mi.set_surface_override_material(surface, sm)
            elif _has_any(lname, ["fur", "muzzle"]):
                var fm: ShaderMaterial = ShaderMaterial.new()
                fm.shader = fur_shader
                fm.set_shader_parameter("base_color", sm.albedo_color)
                fm.set_shader_parameter("detail_strength", 0.080 if lname.contains("muzzle") else 0.115)
                fm.set_shader_parameter("rim_strength", 0.060 if lname.contains("muzzle") else 0.085)
                fm.set_shader_parameter("fiber_strength", 0.030 if lname.contains("muzzle") else 0.055)
                var shell_chain: Material = _build_shell_chain(sm.albedo_color, lname)
                if shell_chain != null:
                    fm.next_pass = shell_chain
                mi.set_surface_override_material(surface, fm)
            elif _has_any(lname, ["nose", "nostril"]):
                sm.roughness = 0.30
                sm.metallic_specular = 0.62
                sm.clearcoat_enabled = true
                sm.clearcoat = 0.18
                sm.clearcoat_roughness = 0.20
                mi.set_surface_override_material(surface, sm)
            elif _has_any(lname, ["metal", "buckle", "button", "headphone", "mic", "logo", "cord", "gold"]):
                sm.metallic = maxf(sm.metallic, 0.58)
                sm.roughness = 0.28
                sm.metallic_specular = 0.72
                mi.set_surface_override_material(surface, sm)
            else:
                sm.roughness = clampf(sm.roughness + 0.08, 0.42, 0.94)
                sm.metallic_specular = minf(sm.metallic_specular, 0.34)
                mi.set_surface_override_material(surface, sm)

func _build_shell_chain(base_color: Color, lname: String) -> Material:
    if shell_shader == null or fur_shell_count <= 0:
        return null
    var count: int = fur_shell_count
    if lname.contains("muzzle") or lname.contains("lid"):
        count = mini(count, 2)
    var fur_length: float = 0.010
    if lname.contains("head") or lname.contains("cheek"):
        fur_length = 0.009
    elif lname.contains("tail"):
        fur_length = 0.013
    elif lname.contains("paw") or lname.contains("leg") or lname.contains("thigh") or lname.contains("shin"):
        fur_length = 0.008
    elif lname.contains("muzzle"):
        fur_length = 0.005

    var first: ShaderMaterial = null
    var previous: ShaderMaterial = null
    for index: int in range(count):
        var ratio: float = float(index + 1) / float(count)
        var shell: ShaderMaterial = ShaderMaterial.new()
        shell.shader = shell_shader
        shell.set_shader_parameter("fur_color", base_color)
        shell.set_shader_parameter("shell_offset", fur_length * ratio)
        shell.set_shader_parameter("shell_alpha", lerpf(0.38, 0.18, ratio))
        shell.set_shader_parameter("strand_density", 62.0 if lname.contains("head") else 54.0)
        shell.set_shader_parameter("scruffiness", 0.34 if lname.contains("tail") else 0.24)
        shell.set_shader_parameter("root_darkening", 0.22)
        shell.set_shader_parameter("rim_strength", 0.12)
        shell.set_shader_parameter("shell_ratio", ratio)
        if first == null:
            first = shell
        if previous != null:
            previous.next_pass = shell
        previous = shell
    return first

func _has_any(value: String, tokens: Array[String]) -> bool:
    for token: String in tokens:
        if value.contains(token):
            return true
    return false
