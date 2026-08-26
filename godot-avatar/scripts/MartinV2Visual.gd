class_name MartinV2Visual
extends Node3D

# Martin v2: original stylized mobile character aimed at polished Talking-Tom-class readability.
# Smooth forms, restrained surface texture, layered eyes and small idle motions.

const FUR_SHADER := preload("res://shaders/martin_stylized_fur.gdshader")
const EYE_SHADER := preload("res://shaders/martin_eye.gdshader")

var fur_mat: Material
var muzzle_mat: StandardMaterial3D
var stripe_mat: StandardMaterial3D
var hoodie_mat: StandardMaterial3D
var hoodie_detail_mat: StandardMaterial3D
var black_mat: StandardMaterial3D
var iris_mat: Material
var pupil_mat: Material
var eye_white_mat: Material
var highlight_mat: Material
var gold_mat: StandardMaterial3D
var ear_mat: StandardMaterial3D
var sole_mat: StandardMaterial3D
var whisker_mat: StandardMaterial3D

var head_group: Node3D
var body_group: Node3D
var left_eye_group: Node3D
var right_eye_group: Node3D
var elapsed: float = 0.0
var blink_clock: float = 0.0
var next_blink: float = 3.0
var blink_phase: float = 0.0
var blinking: bool = false

func _ready() -> void:
    _make_materials()
    _build_character()

func _process(delta: float) -> void:
    elapsed += delta
    blink_clock += delta
    if head_group != null:
        head_group.rotation.z = sin(elapsed * 0.72) * 0.006
        head_group.rotation.y = sin(elapsed * 0.41) * 0.005
        head_group.position.y = sin(elapsed * 1.05) * 0.0035
    if body_group != null:
        var breathe: float = 1.0 + sin(elapsed * 1.18) * 0.0028
        body_group.scale = Vector3(1.0, breathe, 1.0)
    if blink_clock >= next_blink and not blinking:
        blinking = true
        blink_phase = 0.0
        blink_clock = 0.0
        next_blink = randf_range(2.8, 5.2)
    if blinking:
        blink_phase += delta * 11.0
        var amount: float = sin(minf(blink_phase, PI))
        var sy: float = lerpf(1.0, 0.10, amount)
        if left_eye_group != null:
            left_eye_group.scale.y = sy
        if right_eye_group != null:
            right_eye_group.scale.y = sy
        if blink_phase >= PI:
            blinking = false
            if left_eye_group != null:
                left_eye_group.scale.y = 1.0
            if right_eye_group != null:
                right_eye_group.scale.y = 1.0

func _make_materials() -> void:
    var fur_shader_mat := ShaderMaterial.new()
    fur_shader_mat.shader = FUR_SHADER
    fur_shader_mat.set_shader_parameter("base_color", Color(0.245, 0.255, 0.285, 1.0))
    fur_shader_mat.set_shader_parameter("texture_scale", 34.0)
    fur_shader_mat.set_shader_parameter("texture_strength", 0.050)
    fur_shader_mat.set_shader_parameter("rim_strength", 0.045)
    fur_mat = fur_shader_mat

    muzzle_mat = _mat(Color(0.36, 0.365, 0.39, 1.0), 0.80, 0.0, 0.22)
    stripe_mat = _mat(Color(0.125, 0.135, 0.165, 1.0), 0.82, 0.0, 0.18)
    hoodie_mat = _mat(Color(0.038, 0.043, 0.055, 1.0), 0.62, 0.0, 0.26)
    hoodie_detail_mat = _mat(Color(0.070, 0.076, 0.095, 1.0), 0.58, 0.0, 0.28)
    black_mat = _mat(Color(0.014, 0.015, 0.019, 1.0), 0.26, 0.0, 0.58)
    gold_mat = _mat(Color(0.82, 0.48, 0.10, 1.0), 0.23, 0.72, 0.86)
    ear_mat = _mat(Color(0.34, 0.17, 0.20, 1.0), 0.74, 0.0, 0.20)
    sole_mat = _mat(Color(0.17, 0.18, 0.21, 1.0), 0.72, 0.0, 0.22)
    whisker_mat = _mat(Color(0.75, 0.77, 0.79, 1.0), 0.64, 0.0, 0.28)

    eye_white_mat = _eye_mat(Color(0.88, 0.86, 0.79, 1.0), 0.18, 0.72, 0.025)
    iris_mat = _eye_mat(Color(0.88, 0.45, 0.055, 1.0), 0.14, 0.82, 0.055)
    pupil_mat = _eye_mat(Color(0.006, 0.004, 0.003, 1.0), 0.10, 0.88, 0.0)
    highlight_mat = _eye_mat(Color(1.0, 0.98, 0.93, 1.0), 0.06, 0.90, 0.18)

func _mat(color: Color, roughness: float, metallic: float, specular: float) -> StandardMaterial3D:
    var m := StandardMaterial3D.new()
    m.albedo_color = color
    m.roughness = roughness
    m.metallic = metallic
    m.metallic_specular = specular
    return m

func _eye_mat(color: Color, roughness: float, specular: float, emission: float) -> ShaderMaterial:
    var m := ShaderMaterial.new()
    m.shader = EYE_SHADER
    m.set_shader_parameter("base_color", color)
    m.set_shader_parameter("roughness_value", roughness)
    m.set_shader_parameter("specular_value", specular)
    m.set_shader_parameter("emission_value", emission)
    return m

func _build_character() -> void:
    body_group = Node3D.new()
    body_group.name = "BodyGroup"
    add_child(body_group)

    # Compact, friendly proportions with a large but not oversized head.
    _capsule("Hoodie_Torso", Vector3(0.0, 1.25, 0.0), 0.50, 1.18, Vector3(1.04, 1.0, 0.84), hoodie_mat, body_group)
    _sphere("Hoodie_Belly", Vector3(0.0, 1.14, 0.18), 0.44, Vector3(1.08, 0.88, 0.84), hoodie_mat, body_group)
    _torus("Hood_Rim", Vector3(0.0, 1.77, 0.03), 0.35, 0.43, hoodie_detail_mat, body_group).rotation_degrees.x = 90.0

    _capsule("Fur_Leg_L", Vector3(-0.245, 0.55, 0.00), 0.19, 0.69, Vector3(1.0, 1.0, 0.94), fur_mat, body_group)
    _capsule("Fur_Leg_R", Vector3(0.245, 0.55, 0.00), 0.19, 0.69, Vector3(1.0, 1.0, 0.94), fur_mat, body_group)
    _shoe(-0.255)
    _shoe(0.255)

    var arm_l := _capsule("Hoodie_Arm_L", Vector3(-0.56, 1.31, 0.05), 0.175, 0.78, Vector3(0.95, 1.0, 0.92), hoodie_mat, body_group)
    arm_l.rotation_degrees.z = -10.0
    var arm_r := _capsule("Hoodie_Arm_R", Vector3(0.56, 1.31, 0.05), 0.175, 0.78, Vector3(0.95, 1.0, 0.92), hoodie_mat, body_group)
    arm_r.rotation_degrees.z = 10.0
    _sphere("Fur_Paw_L", Vector3(-0.62, 0.96, 0.17), 0.195, Vector3(1.0, 0.92, 0.88), fur_mat, body_group)
    _sphere("Fur_Paw_R", Vector3(0.62, 0.96, 0.17), 0.195, Vector3(1.0, 0.92, 0.88), fur_mat, body_group)
    _tail()
    _hoodie_details()

    head_group = Node3D.new()
    head_group.name = "HeadGroup"
    add_child(head_group)

    # Softer feline face: one main skull volume plus smaller cheeks and muzzle.
    _sphere("Fur_Head", Vector3(0.0, 2.32, 0.00), 0.625, Vector3(1.04, 1.00, 0.91), fur_mat, head_group)
    _sphere("Fur_Cheek_L", Vector3(-0.29, 2.15, 0.34), 0.245, Vector3(1.06, 0.88, 0.82), fur_mat, head_group)
    _sphere("Fur_Cheek_R", Vector3(0.29, 2.15, 0.34), 0.245, Vector3(1.06, 0.88, 0.82), fur_mat, head_group)
    _sphere("Muzzle_L", Vector3(-0.135, 2.09, 0.55), 0.185, Vector3(1.08, 0.78, 0.72), muzzle_mat, head_group)
    _sphere("Muzzle_R", Vector3(0.135, 2.09, 0.55), 0.185, Vector3(1.08, 0.78, 0.72), muzzle_mat, head_group)

    _ear(-0.40, -7.0)
    _ear(0.40, 7.0)
    _forehead_stripes()
    left_eye_group = _eye(-0.235)
    right_eye_group = _eye(0.235)

    _sphere("Nose", Vector3(0.0, 2.105, 0.705), 0.072, Vector3(1.10, 0.76, 0.62), black_mat, head_group)
    var mouth_l := _box("Mouth_L", Vector3(-0.047, 2.025, 0.692), Vector3(0.090, 0.012, 0.012), black_mat, head_group)
    mouth_l.rotation_degrees.z = -17.0
    var mouth_r := _box("Mouth_R", Vector3(0.047, 2.025, 0.692), Vector3(0.090, 0.012, 0.012), black_mat, head_group)
    mouth_r.rotation_degrees.z = 17.0

    _whiskers()
    _headphones()

func _eye(x: float) -> Node3D:
    var group := Node3D.new()
    group.name = "EyeGroup_L" if x < 0.0 else "EyeGroup_R"
    head_group.add_child(group)

    # Dark socket gives an eyelid line, then warm sclera and layered amber iris.
    _sphere("EyeSocket", Vector3(x, 2.43, 0.485), 0.178, Vector3(1.08, 1.16, 0.62), black_mat, group)
    _sphere("Eye_White", Vector3(x, 2.425, 0.535), 0.154, Vector3(0.95, 1.08, 0.56), eye_white_mat, group)
    _sphere("Iris_Amber", Vector3(x, 2.425, 0.645), 0.090, Vector3(0.92, 1.04, 0.30), iris_mat, group)
    _sphere("Pupil", Vector3(x, 2.425, 0.702), 0.041, Vector3(0.66, 1.16, 0.22), pupil_mat, group)
    _sphere("Eye_Highlight", Vector3(x - 0.030, 2.475, 0.724), 0.021, Vector3.ONE, highlight_mat, group)
    _sphere("Eye_Highlight_Small", Vector3(x + 0.027, 2.390, 0.725), 0.009, Vector3.ONE, highlight_mat, group)

    # Upper eyelid/brow volume makes the gaze expressive and less toy-like.
    var brow_x := x + (0.015 if x < 0.0 else -0.015)
    var brow := _capsule("Fur_Brow", Vector3(brow_x, 2.595, 0.535), 0.046, 0.30, Vector3(1.0, 1.0, 0.72), fur_mat, group)
    brow.rotation_degrees.z = 82.0 if x < 0.0 else -82.0
    return group

func _ear(x: float, tilt: float) -> void:
    var outer := _cone("Fur_Ear_L" if x < 0.0 else "Fur_Ear_R", Vector3(x, 2.87, -0.01), 0.245, 0.47, fur_mat, head_group, 12)
    outer.rotation_degrees.z = tilt
    outer.rotation_degrees.y = 24.0
    var inner := _cone("InnerEar_L" if x < 0.0 else "InnerEar_R", Vector3(x, 2.87, 0.135), 0.135, 0.31, ear_mat, head_group, 12)
    inner.rotation_degrees.z = tilt
    inner.rotation_degrees.y = 24.0

func _forehead_stripes() -> void:
    var stripe0 := _capsule("Stripe_Center", Vector3(0.0, 2.69, 0.505), 0.020, 0.23, Vector3.ONE, stripe_mat, head_group)
    stripe0.rotation_degrees.z = 90.0
    stripe0.rotation_degrees.y = 0.0
    var stripe_l := _capsule("Stripe_L", Vector3(-0.115, 2.66, 0.50), 0.018, 0.18, Vector3.ONE, stripe_mat, head_group)
    stripe_l.rotation_degrees.z = 68.0
    var stripe_r := _capsule("Stripe_R", Vector3(0.115, 2.66, 0.50), 0.018, 0.18, Vector3.ONE, stripe_mat, head_group)
    stripe_r.rotation_degrees.z = -68.0

func _tail() -> void:
    var root := Node3D.new()
    root.name = "TailGroup"
    body_group.add_child(root)
    var positions: Array[Vector3] = [Vector3(0.55, 0.68, -0.20), Vector3(0.75, 0.87, -0.25), Vector3(0.88, 1.12, -0.23), Vector3(0.90, 1.36, -0.17)]
    var radii: Array[float] = [0.17, 0.16, 0.145, 0.13]
    var heights: Array[float] = [0.52, 0.50, 0.46, 0.40]
    var angles: Array[float] = [-28.0, -48.0, -69.0, -91.0]
    for i: int in range(positions.size()):
        var seg := _capsule("Fur_Tail_%d" % i, positions[i], radii[i], heights[i], Vector3.ONE, fur_mat, root)
        seg.rotation_degrees.z = angles[i]

func _shoe(x: float) -> void:
    var shoe := _capsule("Shoe_L" if x < 0.0 else "Shoe_R", Vector3(x, 0.18, 0.18), 0.205, 0.44, Vector3(1.12, 0.72, 1.42), black_mat, body_group)
    shoe.rotation_degrees.x = 90.0
    _box("Sole_L" if x < 0.0 else "Sole_R", Vector3(x, 0.085, 0.285), Vector3(0.43, 0.075, 0.53), sole_mat, body_group)

func _headphones() -> void:
    var band := _torus("Headphone_Band", Vector3(0.0, 2.47, -0.07), 0.455, 0.515, black_mat, head_group)
    band.rotation_degrees.x = 90.0
    var cup_l := _cylinder("Headphone_Cup_L", Vector3(-0.59, 2.32, 0.035), 0.205, 0.115, black_mat, head_group)
    cup_l.rotation_degrees.x = 90.0
    var cup_r := _cylinder("Headphone_Cup_R", Vector3(0.59, 2.32, 0.035), 0.205, 0.115, black_mat, head_group)
    cup_r.rotation_degrees.x = 90.0
    var trim_l := _cylinder("Metal_Headphone_Gold_L", Vector3(-0.59, 2.32, 0.101), 0.165, 0.028, gold_mat, head_group)
    trim_l.rotation_degrees.x = 90.0
    var trim_r := _cylinder("Metal_Headphone_Gold_R", Vector3(0.59, 2.32, 0.101), 0.165, 0.028, gold_mat, head_group)
    trim_r.rotation_degrees.x = 90.0

func _hoodie_details() -> void:
    _box("Hoodie_Pocket", Vector3(0.0, 1.12, 0.505), Vector3(0.44, 0.20, 0.035), hoodie_detail_mat, body_group)
    var cord_l := _capsule("Hoodie_Cord_L", Vector3(-0.10, 1.58, 0.49), 0.013, 0.30, Vector3.ONE, gold_mat, body_group)
    cord_l.rotation_degrees.z = 4.0
    var cord_r := _capsule("Hoodie_Cord_R", Vector3(0.10, 1.58, 0.49), 0.013, 0.30, Vector3.ONE, gold_mat, body_group)
    cord_r.rotation_degrees.z = -4.0
    _sphere("Hoodie_CordTip_L", Vector3(-0.09, 1.43, 0.50), 0.023, Vector3.ONE, gold_mat, body_group)
    _sphere("Hoodie_CordTip_R", Vector3(0.09, 1.43, 0.50), 0.023, Vector3.ONE, gold_mat, body_group)
    _hoodie_logo()

func _hoodie_logo() -> void:
    var z: float = 0.545
    var y: float = 1.30
    var left := _box("Metal_Logo_L", Vector3(-0.100, y, z), Vector3(0.040, 0.21, 0.024), gold_mat, body_group)
    left.rotation_degrees.z = -8.0
    var right := _box("Metal_Logo_R", Vector3(0.100, y, z), Vector3(0.040, 0.21, 0.024), gold_mat, body_group)
    right.rotation_degrees.z = 8.0
    var ml := _box("Metal_Logo_Mid_L", Vector3(-0.044, y + 0.015, z + 0.002), Vector3(0.032, 0.145, 0.024), gold_mat, body_group)
    ml.rotation_degrees.z = 31.0
    var mr := _box("Metal_Logo_Mid_R", Vector3(0.044, y + 0.015, z + 0.002), Vector3(0.032, 0.145, 0.024), gold_mat, body_group)
    mr.rotation_degrees.z = -31.0

func _whiskers() -> void:
    var sides: Array[float] = [-1.0, 1.0]
    for side: float in sides:
        for i: int in range(3):
            var y: float = 2.135 - float(i) * 0.055
            var angle: float = side * (4.0 + float(i) * 6.0)
            var x: float = side * 0.38
            var w := _box("Whisker_%s_%d" % ["L" if side < 0.0 else "R", i], Vector3(x, y, 0.665), Vector3(0.30, 0.006, 0.006), whisker_mat, head_group)
            w.rotation_degrees.z = angle

func _sphere(name_value: String, pos: Vector3, radius: float, scale_value: Vector3, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh := SphereMesh.new()
    mesh.radius = radius
    mesh.height = radius * 2.0
    mesh.radial_segments = 56
    mesh.rings = 28
    return _instance(name_value, mesh, pos, scale_value, mat, parent)

func _capsule(name_value: String, pos: Vector3, radius: float, height: float, scale_value: Vector3, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh := CapsuleMesh.new()
    mesh.radius = radius
    mesh.height = maxf(height, radius * 2.0 + 0.02)
    mesh.radial_segments = 36
    mesh.rings = 14
    return _instance(name_value, mesh, pos, scale_value, mat, parent)

func _cone(name_value: String, pos: Vector3, radius: float, height: float, mat: Material, parent: Node, segments: int = 12) -> MeshInstance3D:
    var mesh := CylinderMesh.new()
    mesh.top_radius = 0.010
    mesh.bottom_radius = radius
    mesh.height = height
    mesh.radial_segments = segments
    return _instance(name_value, mesh, pos, Vector3.ONE, mat, parent)

func _cylinder(name_value: String, pos: Vector3, radius: float, height: float, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh := CylinderMesh.new()
    mesh.top_radius = radius
    mesh.bottom_radius = radius
    mesh.height = height
    mesh.radial_segments = 40
    return _instance(name_value, mesh, pos, Vector3.ONE, mat, parent)

func _torus(name_value: String, pos: Vector3, inner_radius: float, outer_radius: float, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh := TorusMesh.new()
    mesh.inner_radius = inner_radius
    mesh.outer_radius = outer_radius
    mesh.rings = 48
    mesh.ring_segments = 18
    return _instance(name_value, mesh, pos, Vector3.ONE, mat, parent)

func _box(name_value: String, pos: Vector3, size_value: Vector3, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh := BoxMesh.new()
    mesh.size = size_value
    return _instance(name_value, mesh, pos, Vector3.ONE, mat, parent)

func _instance(name_value: String, mesh: Mesh, pos: Vector3, scale_value: Vector3, mat: Material, parent: Node) -> MeshInstance3D:
    var mi := MeshInstance3D.new()
    mi.name = name_value
    mi.mesh = mesh
    mi.position = pos
    mi.scale = scale_value
    mi.material_override = mat
    parent.add_child(mi)
    return mi
