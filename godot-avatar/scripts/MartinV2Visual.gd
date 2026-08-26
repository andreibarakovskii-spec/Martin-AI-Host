class_name MartinV2Visual
extends Node3D

# Original stylised production visual for Martin.
# Target: clean mobile character quality comparable to modern talking-animal apps,
# without copying any existing character design.

var fur_mat: StandardMaterial3D
var muzzle_mat: StandardMaterial3D
var hoodie_mat: StandardMaterial3D
var dark_mat: StandardMaterial3D
var amber_mat: StandardMaterial3D
var pupil_mat: StandardMaterial3D
var white_mat: StandardMaterial3D
var gold_mat: StandardMaterial3D
var pink_mat: StandardMaterial3D
var shoe_mat: StandardMaterial3D
var sole_mat: StandardMaterial3D

var left_eye_group: Node3D
var right_eye_group: Node3D
var head_group: Node3D
var body_group: Node3D
var blink_clock := 0.0
var next_blink := 2.6
var blink_phase := 0.0
var blinking := false
var elapsed := 0.0

func _ready() -> void:
    _make_materials()
    _build_character()

func _process(delta: float) -> void:
    elapsed += delta
    blink_clock += delta
    if head_group != null:
        head_group.rotation.z = sin(elapsed * 0.72) * 0.008
        head_group.position.y = sin(elapsed * 1.18) * 0.004
    if body_group != null:
        body_group.scale.y = 1.0 + sin(elapsed * 1.25) * 0.004
    if blink_clock >= next_blink and not blinking:
        blinking = true
        blink_phase = 0.0
        blink_clock = 0.0
        next_blink = randf_range(2.7, 5.0)
    if blinking:
        blink_phase += delta * 11.0
        var amount := sin(minf(blink_phase, PI))
        var eye_scale_y := lerpf(1.0, 0.10, amount)
        if left_eye_group != null:
            left_eye_group.scale.y = eye_scale_y
        if right_eye_group != null:
            right_eye_group.scale.y = eye_scale_y
        if blink_phase >= PI:
            blinking = false
            if left_eye_group != null:
                left_eye_group.scale.y = 1.0
            if right_eye_group != null:
                right_eye_group.scale.y = 1.0

func _make_materials() -> void:
    fur_mat = _material(Color(0.235, 0.245, 0.275, 1.0), 0.86, 0.0, 0.18)
    muzzle_mat = _material(Color(0.31, 0.315, 0.34, 1.0), 0.82, 0.0, 0.16)
    hoodie_mat = _material(Color(0.018, 0.020, 0.026, 1.0), 0.72, 0.0, 0.20)
    dark_mat = _material(Color(0.018, 0.015, 0.014, 1.0), 0.34, 0.0, 0.52)
    amber_mat = _material(Color(0.95, 0.54, 0.075, 1.0), 0.13, 0.05, 0.88)
    pupil_mat = _material(Color(0.006, 0.006, 0.006, 1.0), 0.05, 0.0, 0.95)
    white_mat = _material(Color(0.93, 0.92, 0.88, 1.0), 0.18, 0.0, 0.72)
    gold_mat = _material(Color(0.76, 0.42, 0.08, 1.0), 0.24, 0.72, 0.82)
    pink_mat = _material(Color(0.38, 0.17, 0.18, 1.0), 0.72, 0.0, 0.18)
    shoe_mat = _material(Color(0.025, 0.027, 0.032, 1.0), 0.48, 0.0, 0.28)
    sole_mat = _material(Color(0.82, 0.80, 0.75, 1.0), 0.66, 0.0, 0.20)

func _material(color: Color, roughness: float, metallic: float, specular: float) -> StandardMaterial3D:
    var mat := StandardMaterial3D.new()
    mat.albedo_color = color
    mat.roughness = roughness
    mat.metallic = metallic
    mat.specular = specular
    return mat

func _build_character() -> void:
    body_group = Node3D.new()
    body_group.name = "BodyGroup"
    add_child(body_group)

    # Hoodie torso: round and compact, not low-poly.
    _capsule("Hoodie_Torso", Vector3(0, 1.32, 0), 0.56, 1.36, Vector3(1.05, 1.0, 0.86), hoodie_mat, body_group)
    _sphere("Hoodie_Belly", Vector3(0, 1.18, 0.18), 0.54, Vector3(1.02, 0.88, 0.82), hoodie_mat, body_group)

    # Legs and paws.
    _capsule("Fur_Leg_L", Vector3(-0.27, 0.55, 0.02), 0.205, 0.72, Vector3(1.0, 1.0, 0.92), fur_mat, body_group)
    _capsule("Fur_Leg_R", Vector3(0.27, 0.55, 0.02), 0.205, 0.72, Vector3(1.0, 1.0, 0.92), fur_mat, body_group)
    _shoe(-0.28)
    _shoe(0.28)

    # Arms with exposed furry hands.
    var arm_l := _capsule("Hoodie_Arm_L", Vector3(-0.63, 1.35, 0.06), 0.19, 0.86, Vector3(0.92, 1.0, 0.92), hoodie_mat, body_group)
    arm_l.rotation_degrees.z = -12.0
    var arm_r := _capsule("Hoodie_Arm_R", Vector3(0.63, 1.35, 0.06), 0.19, 0.86, Vector3(0.92, 1.0, 0.92), hoodie_mat, body_group)
    arm_r.rotation_degrees.z = 12.0
    _sphere("Fur_Paw_L", Vector3(-0.70, 0.96, 0.16), 0.22, Vector3(1.0, 0.92, 0.88), fur_mat, body_group)
    _sphere("Fur_Paw_R", Vector3(0.70, 0.96, 0.16), 0.22, Vector3(1.0, 0.92, 0.88), fur_mat, body_group)

    # Tail: segmented smooth curve visible beside the body.
    _tail()

    head_group = Node3D.new()
    head_group.name = "HeadGroup"
    add_child(head_group)

    # Main head and cheek volumes give a soft, premium silhouette.
    _sphere("Fur_Head", Vector3(0, 2.37, 0.02), 0.70, Vector3(1.04, 0.98, 0.92), fur_mat, head_group)
    _sphere("Fur_Cheek_L", Vector3(-0.35, 2.22, 0.38), 0.34, Vector3(1.05, 0.86, 0.82), fur_mat, head_group)
    _sphere("Fur_Cheek_R", Vector3(0.35, 2.22, 0.38), 0.34, Vector3(1.05, 0.86, 0.82), fur_mat, head_group)
    _sphere("Muzzle_L", Vector3(-0.18, 2.13, 0.61), 0.255, Vector3(1.08, 0.78, 0.70), muzzle_mat, head_group)
    _sphere("Muzzle_R", Vector3(0.18, 2.13, 0.61), 0.255, Vector3(1.08, 0.78, 0.70), muzzle_mat, head_group)

    _ear(-0.46, -8.0)
    _ear(0.46, 8.0)

    left_eye_group = _eye(-0.285)
    right_eye_group = _eye(0.285)

    # Small feline nose and a subtle smile.
    _sphere("Nose", Vector3(0, 2.15, 0.82), 0.105, Vector3(1.08, 0.72, 0.62), dark_mat, head_group)
    var mouth_l := _box("Mouth_L", Vector3(-0.067, 2.035, 0.785), Vector3(0.13, 0.018, 0.018), dark_mat, head_group)
    mouth_l.rotation_degrees.z = -18.0
    var mouth_r := _box("Mouth_R", Vector3(0.067, 2.035, 0.785), Vector3(0.13, 0.018, 0.018), dark_mat, head_group)
    mouth_r.rotation_degrees.z = 18.0

    _whiskers()
    _headphones()
    _hoodie_logo()

func _eye(x: float) -> Node3D:
    var group := Node3D.new()
    group.name = "EyeGroup_L" if x < 0.0 else "EyeGroup_R"
    head_group.add_child(group)
    _sphere("Eye_White", Vector3(x, 2.49, 0.565), 0.245, Vector3(0.92, 1.08, 0.72), white_mat, group)
    _sphere("Iris_Amber", Vector3(x, 2.485, 0.755), 0.158, Vector3(0.92, 1.02, 0.34), amber_mat, group)
    _sphere("Pupil", Vector3(x, 2.485, 0.858), 0.080, Vector3(0.88, 1.08, 0.30), pupil_mat, group)
    _sphere("Eye_Highlight", Vector3(x - 0.042, 2.545, 0.895), 0.030, Vector3.ONE, white_mat, group)
    _sphere("Eye_Highlight_Small", Vector3(x + 0.035, 2.435, 0.898), 0.014, Vector3.ONE, white_mat, group)
    return group

func _ear(x: float, tilt: float) -> void:
    var outer := _cone("Fur_Ear_L" if x < 0.0 else "Fur_Ear_R", Vector3(x, 2.99, 0.00), 0.34, 0.65, fur_mat, head_group)
    outer.rotation_degrees.z = tilt
    outer.rotation_degrees.y = 30.0
    var inner := _cone("InnerEar_L" if x < 0.0 else "InnerEar_R", Vector3(x, 2.99, 0.205), 0.205, 0.44, pink_mat, head_group)
    inner.rotation_degrees.z = tilt
    inner.rotation_degrees.y = 30.0

func _tail() -> void:
    var root := Node3D.new()
    root.name = "TailGroup"
    body_group.add_child(root)
    var parts := [
        [Vector3(0.61, 0.70, -0.22), 0.19, 0.60, -28.0],
        [Vector3(0.83, 0.89, -0.28), 0.18, 0.58, -48.0],
        [Vector3(0.98, 1.18, -0.26), 0.17, 0.54, -69.0],
        [Vector3(1.00, 1.47, -0.20), 0.15, 0.48, -92.0],
    ]
    for i in range(parts.size()):
        var p: Array = parts[i]
        var seg := _capsule("Fur_Tail_%d" % i, p[0] as Vector3, float(p[1]), float(p[2]), Vector3.ONE, fur_mat, root)
        seg.rotation_degrees.z = float(p[3])

func _shoe(x: float) -> void:
    var shoe := _capsule("Shoe_L" if x < 0.0 else "Shoe_R", Vector3(x, 0.19, 0.20), 0.22, 0.48, Vector3(1.18, 0.72, 1.52), shoe_mat, body_group)
    shoe.rotation_degrees.x = 90.0
    _box("Sole_L" if x < 0.0 else "Sole_R", Vector3(x, 0.09, 0.31), Vector3(0.49, 0.095, 0.62), sole_mat, body_group)
    _box("Toe_L" if x < 0.0 else "Toe_R", Vector3(x, 0.20, 0.50), Vector3(0.42, 0.11, 0.12), sole_mat, body_group)

func _headphones() -> void:
    # Full torus sits mostly behind the head; the head occludes the lower half,
    # leaving a convincing padded headband silhouette.
    var band := _torus("Headphone_Band", Vector3(0, 2.49, -0.03), 0.51, 0.60, dark_mat, head_group)
    band.rotation_degrees.x = 90.0
    var cup_l := _cylinder("Headphone_Cup_L", Vector3(-0.675, 2.33, 0.08), 0.25, 0.13, dark_mat, head_group)
    cup_l.rotation_degrees.x = 90.0
    var cup_r := _cylinder("Headphone_Cup_R", Vector3(0.675, 2.33, 0.08), 0.25, 0.13, dark_mat, head_group)
    cup_r.rotation_degrees.x = 90.0
    var trim_l := _cylinder("Metal_Headphone_Gold_L", Vector3(-0.675, 2.33, 0.155), 0.205, 0.035, gold_mat, head_group)
    trim_l.rotation_degrees.x = 90.0
    var trim_r := _cylinder("Metal_Headphone_Gold_R", Vector3(0.675, 2.33, 0.155), 0.205, 0.035, gold_mat, head_group)
    trim_r.rotation_degrees.x = 90.0

func _hoodie_logo() -> void:
    var z := 0.585
    var y := 1.42
    var left := _box("Metal_Logo_L", Vector3(-0.12, y, z), Vector3(0.055, 0.28, 0.035), gold_mat, body_group)
    left.rotation_degrees.z = -8.0
    var right := _box("Metal_Logo_R", Vector3(0.12, y, z), Vector3(0.055, 0.28, 0.035), gold_mat, body_group)
    right.rotation_degrees.z = 8.0
    var mid_l := _box("Metal_Logo_Mid_L", Vector3(-0.052, y + 0.02, z + 0.004), Vector3(0.045, 0.19, 0.035), gold_mat, body_group)
    mid_l.rotation_degrees.z = 31.0
    var mid_r := _box("Metal_Logo_Mid_R", Vector3(0.052, y + 0.02, z + 0.004), Vector3(0.045, 0.19, 0.035), gold_mat, body_group)
    mid_r.rotation_degrees.z = -31.0

func _whiskers() -> void:
    for side in [-1.0, 1.0]:
        for i in range(3):
            var y := 2.18 - float(i) * 0.075
            var angle := side * (5.0 + float(i) * 7.0)
            var x := side * 0.46
            var w := _box("Whisker_%s_%d" % ["L" if side < 0.0 else "R", i], Vector3(x, y, 0.78), Vector3(0.42, 0.012, 0.010), white_mat, head_group)
            w.rotation_degrees.z = angle

func _sphere(name_value: String, pos: Vector3, radius: float, scale_value: Vector3, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh := SphereMesh.new()
    mesh.radius = radius
    mesh.height = radius * 2.0
    mesh.radial_segments = 48
    mesh.rings = 24
    var mi := MeshInstance3D.new()
    mi.name = name_value
    mi.mesh = mesh
    mi.position = pos
    mi.scale = scale_value
    mi.material_override = mat
    parent.add_child(mi)
    return mi

func _capsule(name_value: String, pos: Vector3, radius: float, height: float, scale_value: Vector3, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh := CapsuleMesh.new()
    mesh.radius = radius
    mesh.height = maxf(height, radius * 2.0 + 0.02)
    mesh.radial_segments = 32
    mesh.rings = 12
    var mi := MeshInstance3D.new()
    mi.name = name_value
    mi.mesh = mesh
    mi.position = pos
    mi.scale = scale_value
    mi.material_override = mat
    parent.add_child(mi)
    return mi

func _cone(name_value: String, pos: Vector3, radius: float, height: float, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh := CylinderMesh.new()
    mesh.top_radius = 0.015
    mesh.bottom_radius = radius
    mesh.height = height
    mesh.radial_segments = 3
    mesh.rings = 2
    var mi := MeshInstance3D.new()
    mi.name = name_value
    mi.mesh = mesh
    mi.position = pos
    mi.material_override = mat
    parent.add_child(mi)
    return mi

func _cylinder(name_value: String, pos: Vector3, radius: float, height: float, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh := CylinderMesh.new()
    mesh.top_radius = radius
    mesh.bottom_radius = radius
    mesh.height = height
    mesh.radial_segments = 40
    var mi := MeshInstance3D.new()
    mi.name = name_value
    mi.mesh = mesh
    mi.position = pos
    mi.material_override = mat
    parent.add_child(mi)
    return mi

func _torus(name_value: String, pos: Vector3, inner_radius: float, outer_radius: float, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh := TorusMesh.new()
    mesh.inner_radius = inner_radius
    mesh.outer_radius = outer_radius
    mesh.rings = 48
    mesh.ring_segments = 18
    var mi := MeshInstance3D.new()
    mi.name = name_value
    mi.mesh = mesh
    mi.position = pos
    mi.material_override = mat
    parent.add_child(mi)
    return mi

func _box(name_value: String, pos: Vector3, size_value: Vector3, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh := BoxMesh.new()
    mesh.size = size_value
    var mi := MeshInstance3D.new()
    mi.name = name_value
    mi.mesh = mesh
    mi.position = pos
    mi.material_override = mat
    parent.add_child(mi)
    return mi
