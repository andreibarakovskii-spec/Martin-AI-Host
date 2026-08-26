class_name MartinV2Visual
extends Node3D

# Original Martin v2 visual. The goal is a polished, expressive mobile character:
# smooth silhouette, readable feline face, layered glossy eyes and restrained fuzz.

var fur_mat: StandardMaterial3D
var muzzle_mat: StandardMaterial3D
var hoodie_mat: StandardMaterial3D
var black_mat: StandardMaterial3D
var iris_mat: StandardMaterial3D
var pupil_mat: StandardMaterial3D
var eye_white_mat: StandardMaterial3D
var gold_mat: StandardMaterial3D
var ear_mat: StandardMaterial3D
var sole_mat: StandardMaterial3D

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
        head_group.rotation.z = sin(elapsed * 0.72) * 0.007
        head_group.position.y = sin(elapsed * 1.05) * 0.004
    if body_group != null:
        var breathe: float = 1.0 + sin(elapsed * 1.18) * 0.0035
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
            left_eye_group.scale.y = 1.0
            right_eye_group.scale.y = 1.0

func _make_materials() -> void:
    fur_mat = _mat(Color(0.235, 0.245, 0.275, 1.0), 0.82, 0.0, 0.22)
    muzzle_mat = _mat(Color(0.325, 0.325, 0.345, 1.0), 0.78, 0.0, 0.20)
    hoodie_mat = _mat(Color(0.018, 0.020, 0.027, 1.0), 0.68, 0.0, 0.22)
    black_mat = _mat(Color(0.008, 0.008, 0.009, 1.0), 0.28, 0.0, 0.62)
    iris_mat = _mat(Color(0.92, 0.49, 0.055, 1.0), 0.16, 0.06, 0.92)
    pupil_mat = _mat(Color(0.002, 0.002, 0.002, 1.0), 0.08, 0.0, 0.95)
    eye_white_mat = _mat(Color(0.94, 0.92, 0.86, 1.0), 0.20, 0.0, 0.80)
    gold_mat = _mat(Color(0.76, 0.40, 0.065, 1.0), 0.24, 0.68, 0.86)
    ear_mat = _mat(Color(0.34, 0.14, 0.16, 1.0), 0.72, 0.0, 0.20)
    sole_mat = _mat(Color(0.78, 0.76, 0.70, 1.0), 0.72, 0.0, 0.18)

func _mat(color: Color, roughness: float, metallic: float, specular: float) -> StandardMaterial3D:
    var m: StandardMaterial3D = StandardMaterial3D.new()
    m.albedo_color = color
    m.roughness = roughness
    m.metallic = metallic
    m.specular = specular
    return m

func _build_character() -> void:
    body_group = Node3D.new()
    body_group.name = "BodyGroup"
    add_child(body_group)

    # Compact anthropomorphic proportions: large readable head, short torso and feet.
    _capsule("Hoodie_Torso", Vector3(0.0, 1.27, 0.0), 0.54, 1.28, Vector3(1.04, 1.0, 0.84), hoodie_mat, body_group)
    _sphere("Hoodie_Belly", Vector3(0.0, 1.16, 0.17), 0.50, Vector3(1.06, 0.88, 0.83), hoodie_mat, body_group)

    _capsule("Fur_Leg_L", Vector3(-0.27, 0.55, 0.00), 0.205, 0.72, Vector3(1.0, 1.0, 0.94), fur_mat, body_group)
    _capsule("Fur_Leg_R", Vector3(0.27, 0.55, 0.00), 0.205, 0.72, Vector3(1.0, 1.0, 0.94), fur_mat, body_group)
    _shoe(-0.28)
    _shoe(0.28)

    var arm_l: MeshInstance3D = _capsule("Hoodie_Arm_L", Vector3(-0.60, 1.34, 0.06), 0.19, 0.82, Vector3(0.94, 1.0, 0.92), hoodie_mat, body_group)
    arm_l.rotation_degrees.z = -11.0
    var arm_r: MeshInstance3D = _capsule("Hoodie_Arm_R", Vector3(0.60, 1.34, 0.06), 0.19, 0.82, Vector3(0.94, 1.0, 0.92), hoodie_mat, body_group)
    arm_r.rotation_degrees.z = 11.0
    _sphere("Fur_Paw_L", Vector3(-0.67, 0.98, 0.17), 0.215, Vector3(1.0, 0.92, 0.88), fur_mat, body_group)
    _sphere("Fur_Paw_R", Vector3(0.67, 0.98, 0.17), 0.215, Vector3(1.0, 0.92, 0.88), fur_mat, body_group)
    _tail()
    _hoodie_logo()

    head_group = Node3D.new()
    head_group.name = "HeadGroup"
    add_child(head_group)

    # Overlapping smooth volumes create soft cheeks instead of the old angular Cat Pilot face.
    _sphere("Fur_Head", Vector3(0.0, 2.34, 0.01), 0.69, Vector3(1.06, 0.98, 0.91), fur_mat, head_group)
    _sphere("Fur_Cheek_L", Vector3(-0.34, 2.19, 0.37), 0.33, Vector3(1.06, 0.86, 0.82), fur_mat, head_group)
    _sphere("Fur_Cheek_R", Vector3(0.34, 2.19, 0.37), 0.33, Vector3(1.06, 0.86, 0.82), fur_mat, head_group)
    _sphere("Muzzle_L", Vector3(-0.17, 2.10, 0.60), 0.245, Vector3(1.08, 0.76, 0.70), muzzle_mat, head_group)
    _sphere("Muzzle_R", Vector3(0.17, 2.10, 0.60), 0.245, Vector3(1.08, 0.76, 0.70), muzzle_mat, head_group)

    _ear(-0.45, -8.0)
    _ear(0.45, 8.0)
    left_eye_group = _eye(-0.275)
    right_eye_group = _eye(0.275)

    _sphere("Nose", Vector3(0.0, 2.13, 0.81), 0.097, Vector3(1.08, 0.72, 0.60), black_mat, head_group)
    var mouth_l: MeshInstance3D = _box("Mouth_L", Vector3(-0.062, 2.02, 0.785), Vector3(0.12, 0.016, 0.016), black_mat, head_group)
    mouth_l.rotation_degrees.z = -18.0
    var mouth_r: MeshInstance3D = _box("Mouth_R", Vector3(0.062, 2.02, 0.785), Vector3(0.12, 0.016, 0.016), black_mat, head_group)
    mouth_r.rotation_degrees.z = 18.0

    _whiskers()
    _headphones()

func _eye(x: float) -> Node3D:
    var group: Node3D = Node3D.new()
    group.name = "EyeGroup_L" if x < 0.0 else "EyeGroup_R"
    head_group.add_child(group)
    _sphere("Eye_White", Vector3(x, 2.45, 0.56), 0.225, Vector3(0.93, 1.06, 0.72), eye_white_mat, group)
    _sphere("Iris_Amber", Vector3(x, 2.445, 0.735), 0.145, Vector3(0.92, 1.02, 0.34), iris_mat, group)
    _sphere("Pupil", Vector3(x, 2.445, 0.832), 0.072, Vector3(0.86, 1.12, 0.30), pupil_mat, group)
    _sphere("Eye_Highlight", Vector3(x - 0.041, 2.502, 0.866), 0.029, Vector3.ONE, eye_white_mat, group)
    _sphere("Eye_Highlight_Small", Vector3(x + 0.033, 2.404, 0.868), 0.013, Vector3.ONE, eye_white_mat, group)
    return group

func _ear(x: float, tilt: float) -> void:
    var outer: MeshInstance3D = _cone("Fur_Ear_L" if x < 0.0 else "Fur_Ear_R", Vector3(x, 2.95, 0.0), 0.32, 0.61, fur_mat, head_group)
    outer.rotation_degrees.z = tilt
    outer.rotation_degrees.y = 30.0
    var inner: MeshInstance3D = _cone("InnerEar_L" if x < 0.0 else "InnerEar_R", Vector3(x, 2.95, 0.20), 0.19, 0.40, ear_mat, head_group)
    inner.rotation_degrees.z = tilt
    inner.rotation_degrees.y = 30.0

func _tail() -> void:
    var root: Node3D = Node3D.new()
    root.name = "TailGroup"
    body_group.add_child(root)
    var positions: Array[Vector3] = [Vector3(0.60, 0.69, -0.22), Vector3(0.81, 0.88, -0.27), Vector3(0.96, 1.15, -0.25), Vector3(0.99, 1.43, -0.19)]
    var radii: Array[float] = [0.19, 0.18, 0.17, 0.15]
    var heights: Array[float] = [0.58, 0.56, 0.52, 0.46]
    var angles: Array[float] = [-28.0, -48.0, -69.0, -92.0]
    for i: int in range(positions.size()):
        var seg: MeshInstance3D = _capsule("Fur_Tail_%d" % i, positions[i], radii[i], heights[i], Vector3.ONE, fur_mat, root)
        seg.rotation_degrees.z = angles[i]

func _shoe(x: float) -> void:
    var shoe: MeshInstance3D = _capsule("Shoe_L" if x < 0.0 else "Shoe_R", Vector3(x, 0.19, 0.20), 0.22, 0.48, Vector3(1.16, 0.72, 1.48), black_mat, body_group)
    shoe.rotation_degrees.x = 90.0
    _box("Sole_L" if x < 0.0 else "Sole_R", Vector3(x, 0.09, 0.31), Vector3(0.48, 0.09, 0.60), sole_mat, body_group)

func _headphones() -> void:
    var band: MeshInstance3D = _torus("Headphone_Band", Vector3(0.0, 2.47, -0.04), 0.50, 0.59, black_mat, head_group)
    band.rotation_degrees.x = 90.0
    var cup_l: MeshInstance3D = _cylinder("Headphone_Cup_L", Vector3(-0.66, 2.31, 0.08), 0.24, 0.13, black_mat, head_group)
    cup_l.rotation_degrees.x = 90.0
    var cup_r: MeshInstance3D = _cylinder("Headphone_Cup_R", Vector3(0.66, 2.31, 0.08), 0.24, 0.13, black_mat, head_group)
    cup_r.rotation_degrees.x = 90.0
    var trim_l: MeshInstance3D = _cylinder("Metal_Headphone_Gold_L", Vector3(-0.66, 2.31, 0.155), 0.195, 0.034, gold_mat, head_group)
    trim_l.rotation_degrees.x = 90.0
    var trim_r: MeshInstance3D = _cylinder("Metal_Headphone_Gold_R", Vector3(0.66, 2.31, 0.155), 0.195, 0.034, gold_mat, head_group)
    trim_r.rotation_degrees.x = 90.0

func _hoodie_logo() -> void:
    var z: float = 0.575
    var y: float = 1.39
    var left: MeshInstance3D = _box("Metal_Logo_L", Vector3(-0.115, y, z), Vector3(0.052, 0.26, 0.032), gold_mat, body_group)
    left.rotation_degrees.z = -8.0
    var right: MeshInstance3D = _box("Metal_Logo_R", Vector3(0.115, y, z), Vector3(0.052, 0.26, 0.032), gold_mat, body_group)
    right.rotation_degrees.z = 8.0
    var ml: MeshInstance3D = _box("Metal_Logo_Mid_L", Vector3(-0.050, y + 0.02, z + 0.004), Vector3(0.042, 0.18, 0.032), gold_mat, body_group)
    ml.rotation_degrees.z = 31.0
    var mr: MeshInstance3D = _box("Metal_Logo_Mid_R", Vector3(0.050, y + 0.02, z + 0.004), Vector3(0.042, 0.18, 0.032), gold_mat, body_group)
    mr.rotation_degrees.z = -31.0

func _whiskers() -> void:
    var sides: Array[float] = [-1.0, 1.0]
    for side: float in sides:
        for i: int in range(3):
            var y: float = 2.16 - float(i) * 0.072
            var angle: float = side * (5.0 + float(i) * 7.0)
            var x: float = side * 0.45
            var w: MeshInstance3D = _box("Whisker_%s_%d" % ["L" if side < 0.0 else "R", i], Vector3(x, y, 0.77), Vector3(0.40, 0.010, 0.009), eye_white_mat, head_group)
            w.rotation_degrees.z = angle

func _sphere(name_value: String, pos: Vector3, radius: float, scale_value: Vector3, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh: SphereMesh = SphereMesh.new()
    mesh.radius = radius
    mesh.height = radius * 2.0
    mesh.radial_segments = 48
    mesh.rings = 24
    return _instance(name_value, mesh, pos, scale_value, mat, parent)

func _capsule(name_value: String, pos: Vector3, radius: float, height: float, scale_value: Vector3, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh: CapsuleMesh = CapsuleMesh.new()
    mesh.radius = radius
    mesh.height = maxf(height, radius * 2.0 + 0.02)
    mesh.radial_segments = 32
    mesh.rings = 12
    return _instance(name_value, mesh, pos, scale_value, mat, parent)

func _cone(name_value: String, pos: Vector3, radius: float, height: float, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh: CylinderMesh = CylinderMesh.new()
    mesh.top_radius = 0.012
    mesh.bottom_radius = radius
    mesh.height = height
    mesh.radial_segments = 3
    return _instance(name_value, mesh, pos, Vector3.ONE, mat, parent)

func _cylinder(name_value: String, pos: Vector3, radius: float, height: float, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh: CylinderMesh = CylinderMesh.new()
    mesh.top_radius = radius
    mesh.bottom_radius = radius
    mesh.height = height
    mesh.radial_segments = 40
    return _instance(name_value, mesh, pos, Vector3.ONE, mat, parent)

func _torus(name_value: String, pos: Vector3, inner_radius: float, outer_radius: float, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh: TorusMesh = TorusMesh.new()
    mesh.inner_radius = inner_radius
    mesh.outer_radius = outer_radius
    mesh.rings = 48
    mesh.ring_segments = 18
    return _instance(name_value, mesh, pos, Vector3.ONE, mat, parent)

func _box(name_value: String, pos: Vector3, size_value: Vector3, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh: BoxMesh = BoxMesh.new()
    mesh.size = size_value
    return _instance(name_value, mesh, pos, Vector3.ONE, mat, parent)

func _instance(name_value: String, mesh: Mesh, pos: Vector3, scale_value: Vector3, mat: Material, parent: Node) -> MeshInstance3D:
    var mi: MeshInstance3D = MeshInstance3D.new()
    mi.name = name_value
    mi.mesh = mesh
    mi.position = pos
    mi.scale = scale_value
    mi.material_override = mat
    parent.add_child(mi)
    return mi
