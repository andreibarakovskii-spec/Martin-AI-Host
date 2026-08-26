class_name MartinV2Visual
extends Node3D

# Martin v2.2 — clean stylized mobile character.
# The visible model is deliberately original; the old Cat Pilot is only an invisible compatibility rig.

var fur_mat: StandardMaterial3D
var fur_light_mat: StandardMaterial3D
var fur_dark_mat: StandardMaterial3D
var hoodie_mat: StandardMaterial3D
var hoodie_detail_mat: StandardMaterial3D
var black_mat: StandardMaterial3D
var eye_white_mat: StandardMaterial3D
var iris_mat: StandardMaterial3D
var pupil_mat: StandardMaterial3D
var highlight_mat: StandardMaterial3D
var gold_mat: StandardMaterial3D
var inner_ear_mat: StandardMaterial3D
var whisker_mat: StandardMaterial3D

var head_group: Node3D
var body_group: Node3D
var left_eye_group: Node3D
var right_eye_group: Node3D
var elapsed := 0.0
var blink_clock := 0.0
var next_blink := 3.3
var blink_phase := 0.0
var blinking := false

func _ready() -> void:
    _make_materials()
    _build_character()

func _process(delta: float) -> void:
    elapsed += delta
    blink_clock += delta
    if head_group != null:
        head_group.rotation.z = sin(elapsed * 0.65) * 0.004
        head_group.rotation.y = sin(elapsed * 0.37) * 0.006
        head_group.position.y = sin(elapsed * 1.05) * 0.003
    if body_group != null:
        body_group.scale.y = 1.0 + sin(elapsed * 1.15) * 0.0025
    if blink_clock >= next_blink and not blinking:
        blinking = true
        blink_phase = 0.0
        blink_clock = 0.0
        next_blink = randf_range(2.8, 5.4)
    if blinking:
        blink_phase += delta * 11.0
        var amount := sin(minf(blink_phase, PI))
        var sy := lerpf(1.0, 0.08, amount)
        if left_eye_group != null:
            left_eye_group.scale.y = sy
        if right_eye_group != null:
            right_eye_group.scale.y = sy
        if blink_phase >= PI:
            blinking = false
            left_eye_group.scale.y = 1.0
            right_eye_group.scale.y = 1.0

func _make_materials() -> void:
    fur_mat = _mat(Color(0.265, 0.278, 0.315, 1.0), 0.88, 0.0, 0.18)
    fur_light_mat = _mat(Color(0.405, 0.415, 0.445, 1.0), 0.88, 0.0, 0.16)
    fur_dark_mat = _mat(Color(0.105, 0.115, 0.145, 1.0), 0.90, 0.0, 0.14)
    hoodie_mat = _mat(Color(0.065, 0.073, 0.092, 1.0), 0.70, 0.0, 0.24)
    hoodie_detail_mat = _mat(Color(0.100, 0.112, 0.140, 1.0), 0.68, 0.0, 0.25)
    black_mat = _mat(Color(0.012, 0.014, 0.018, 1.0), 0.30, 0.0, 0.62)
    eye_white_mat = _mat(Color(0.91, 0.90, 0.84, 1.0), 0.16, 0.0, 0.72)
    iris_mat = _mat(Color(0.92, 0.46, 0.045, 1.0), 0.12, 0.03, 0.90)
    pupil_mat = _mat(Color(0.005, 0.004, 0.003, 1.0), 0.10, 0.0, 0.90)
    highlight_mat = _mat(Color(1.0, 0.99, 0.96, 1.0), 0.05, 0.0, 0.95)
    gold_mat = _mat(Color(0.72, 0.36, 0.055, 1.0), 0.27, 0.58, 0.74)
    inner_ear_mat = _mat(Color(0.38, 0.20, 0.22, 1.0), 0.82, 0.0, 0.15)
    whisker_mat = _mat(Color(0.78, 0.80, 0.82, 1.0), 0.72, 0.0, 0.20)

func _mat(color: Color, roughness: float, metallic: float, specular: float) -> StandardMaterial3D:
    var m := StandardMaterial3D.new()
    m.albedo_color = color
    m.roughness = roughness
    m.metallic = metallic
    m.metallic_specular = specular
    return m

func _build_character() -> void:
    body_group = Node3D.new()
    body_group.name = "BodyGroup"
    add_child(body_group)

    # Torso: slightly tapered, rounded hoodie silhouette.
    _capsule("Hoodie_Torso", Vector3(0.0, 1.25, 0.01), 0.48, 1.18, Vector3(1.02, 1.0, 0.78), hoodie_mat, body_group)
    _sphere("Hoodie_Chest", Vector3(0.0, 1.35, 0.17), 0.41, Vector3(1.08, 0.94, 0.72), hoodie_mat, body_group)
    _torus("Hood_Rim", Vector3(0.0, 1.79, 0.02), 0.33, 0.405, hoodie_detail_mat, body_group).rotation_degrees.x = 90.0

    var arm_l := _capsule("Hoodie_Arm_L", Vector3(-0.50, 1.28, 0.08), 0.16, 0.72, Vector3(0.94, 1.0, 0.90), hoodie_mat, body_group)
    arm_l.rotation_degrees.z = -9.0
    var arm_r := _capsule("Hoodie_Arm_R", Vector3(0.50, 1.28, 0.08), 0.16, 0.72, Vector3(0.94, 1.0, 0.90), hoodie_mat, body_group)
    arm_r.rotation_degrees.z = 9.0
    _sphere("Fur_Paw_L", Vector3(-0.55, 0.96, 0.19), 0.175, Vector3(0.98, 0.90, 0.82), fur_mat, body_group)
    _sphere("Fur_Paw_R", Vector3(0.55, 0.96, 0.19), 0.175, Vector3(0.98, 0.90, 0.82), fur_mat, body_group)

    _capsule("Fur_Leg_L", Vector3(-0.23, 0.55, -0.005), 0.17, 0.67, Vector3(0.96, 1.0, 0.90), fur_mat, body_group)
    _capsule("Fur_Leg_R", Vector3(0.23, 0.55, -0.005), 0.17, 0.67, Vector3(0.96, 1.0, 0.90), fur_mat, body_group)
    _shoe(-0.235)
    _shoe(0.235)
    _hoodie_details()
    _tail()

    head_group = Node3D.new()
    head_group.name = "HeadGroup"
    add_child(head_group)

    # One dominant skull volume; the smaller forms sit mostly inside it so the face reads as one piece.
    _sphere("Fur_Head", Vector3(0.0, 2.30, 0.00), 0.61, Vector3(1.03, 0.98, 0.90), fur_mat, head_group)
    _sphere("Fur_Jaw", Vector3(0.0, 2.05, 0.24), 0.39, Vector3(1.06, 0.72, 0.72), fur_mat, head_group)
    _sphere("Muzzle_L", Vector3(-0.125, 2.10, 0.505), 0.175, Vector3(1.05, 0.70, 0.64), fur_light_mat, head_group)
    _sphere("Muzzle_R", Vector3(0.125, 2.10, 0.505), 0.175, Vector3(1.05, 0.70, 0.64), fur_light_mat, head_group)

    _ear(-0.405, -6.0)
    _ear(0.405, 6.0)
    _cheek_tufts(-1.0)
    _cheek_tufts(1.0)
    _forehead_markings()

    left_eye_group = _eye(-0.225)
    right_eye_group = _eye(0.225)

    _sphere("Nose", Vector3(0.0, 2.105, 0.666), 0.065, Vector3(1.15, 0.72, 0.58), black_mat, head_group)
    _mouth()
    _whiskers()
    _headphones()

func _eye(x: float) -> Node3D:
    var group := Node3D.new()
    group.name = "EyeGroup_L" if x < 0.0 else "EyeGroup_R"
    head_group.add_child(group)

    # Flatter eye layers avoid the previous stacked-ball look.
    _sphere("Eye_Rim", Vector3(x, 2.405, 0.475), 0.165, Vector3(1.00, 1.08, 0.42), fur_dark_mat, group)
    _sphere("Eye_White", Vector3(x, 2.405, 0.515), 0.144, Vector3(0.92, 1.02, 0.34), eye_white_mat, group)
    _sphere("Iris_Amber", Vector3(x, 2.405, 0.595), 0.078, Vector3(0.92, 1.02, 0.22), iris_mat, group)
    _sphere("Pupil", Vector3(x, 2.405, 0.642), 0.038, Vector3(0.48, 1.12, 0.18), pupil_mat, group)
    _sphere("Eye_Highlight", Vector3(x - 0.024, 2.452, 0.661), 0.017, Vector3.ONE, highlight_mat, group)
    _sphere("Eye_Highlight_Small", Vector3(x + 0.025, 2.382, 0.662), 0.007, Vector3.ONE, highlight_mat, group)

    # Soft upper lid, thinner than an eyebrow.
    var lid := _capsule("Fur_UpperLid", Vector3(x, 2.545, 0.515), 0.030, 0.245, Vector3(1.0, 1.0, 0.70), fur_mat, group)
    lid.rotation_degrees.z = 83.0 if x < 0.0 else -83.0
    return group

func _ear(x: float, tilt: float) -> void:
    var outer := _cone("Fur_Ear_L" if x < 0.0 else "Fur_Ear_R", Vector3(x, 2.84, -0.015), 0.225, 0.43, fur_mat, head_group, 18)
    outer.rotation_degrees.z = tilt
    outer.rotation_degrees.y = 18.0
    var inner := _cone("InnerEar_L" if x < 0.0 else "InnerEar_R", Vector3(x, 2.835, 0.115), 0.118, 0.275, inner_ear_mat, head_group, 18)
    inner.rotation_degrees.z = tilt
    inner.rotation_degrees.y = 18.0

func _cheek_tufts(side: float) -> void:
    for i in range(2):
        var tuft := _cone("Fur_CheekTuft_%s_%d" % ["L" if side < 0.0 else "R", i], Vector3(side * (0.545 + i * 0.028), 2.12 - i * 0.08, 0.12), 0.075 - i * 0.010, 0.18, fur_mat, head_group, 10)
        tuft.rotation_degrees.z = side * (62.0 + i * 9.0)
        tuft.rotation_degrees.x = 90.0

func _forehead_markings() -> void:
    var center := _capsule("Stripe_Center", Vector3(0.0, 2.65, 0.475), 0.013, 0.16, Vector3.ONE, fur_dark_mat, head_group)
    center.rotation_degrees.z = 90.0
    var left := _capsule("Stripe_L", Vector3(-0.095, 2.63, 0.47), 0.012, 0.13, Vector3.ONE, fur_dark_mat, head_group)
    left.rotation_degrees.z = 69.0
    var right := _capsule("Stripe_R", Vector3(0.095, 2.63, 0.47), 0.012, 0.13, Vector3.ONE, fur_dark_mat, head_group)
    right.rotation_degrees.z = -69.0

func _mouth() -> void:
    var stem := _box("Mouth_Stem", Vector3(0.0, 2.045, 0.657), Vector3(0.010, 0.070, 0.010), black_mat, head_group)
    var left := _capsule("Mouth_L", Vector3(-0.046, 2.005, 0.652), 0.008, 0.105, Vector3.ONE, black_mat, head_group)
    left.rotation_degrees.z = 65.0
    var right := _capsule("Mouth_R", Vector3(0.046, 2.005, 0.652), 0.008, 0.105, Vector3.ONE, black_mat, head_group)
    right.rotation_degrees.z = -65.0

func _whiskers() -> void:
    for side in [-1.0, 1.0]:
        for i in range(3):
            var x := side * 0.36
            var y := 2.12 - float(i) * 0.047
            var w := _box("Whisker_%s_%d" % ["L" if side < 0.0 else "R", i], Vector3(x, y, 0.614), Vector3(0.265, 0.004, 0.004), whisker_mat, head_group)
            w.rotation_degrees.z = side * (3.0 + float(i) * 6.0)

func _headphones() -> void:
    var band := _torus("Headphone_Band", Vector3(0.0, 2.44, -0.11), 0.455, 0.505, black_mat, head_group)
    band.rotation_degrees.x = 90.0
    for side in [-1.0, 1.0]:
        var cup := _cylinder("Headphone_Cup", Vector3(side * 0.565, 2.31, -0.015), 0.175, 0.095, black_mat, head_group)
        cup.rotation_degrees.x = 90.0
        var trim := _torus("Headphone_Trim", Vector3(side * 0.565, 2.31, 0.044), 0.135, 0.158, gold_mat, head_group)
        trim.rotation_degrees.x = 90.0

func _hoodie_details() -> void:
    _sphere("Hoodie_Pocket", Vector3(0.0, 1.14, 0.450), 0.245, Vector3(1.05, 0.45, 0.20), hoodie_detail_mat, body_group)
    for side in [-1.0, 1.0]:
        var cord := _capsule("Hoodie_Cord", Vector3(side * 0.085, 1.56, 0.445), 0.010, 0.25, Vector3.ONE, gold_mat, body_group)
        cord.rotation_degrees.z = -side * 4.0
        _sphere("Hoodie_CordTip", Vector3(side * 0.077, 1.435, 0.448), 0.018, Vector3.ONE, gold_mat, body_group)
    _hoodie_logo()

func _hoodie_logo() -> void:
    var z := 0.475
    var y := 1.28
    var left := _box("Logo_L", Vector3(-0.080, y, z), Vector3(0.030, 0.17, 0.018), gold_mat, body_group)
    left.rotation_degrees.z = -8.0
    var right := _box("Logo_R", Vector3(0.080, y, z), Vector3(0.030, 0.17, 0.018), gold_mat, body_group)
    right.rotation_degrees.z = 8.0
    var ml := _box("Logo_Mid_L", Vector3(-0.035, y + 0.012, z + 0.002), Vector3(0.025, 0.115, 0.018), gold_mat, body_group)
    ml.rotation_degrees.z = 31.0
    var mr := _box("Logo_Mid_R", Vector3(0.035, y + 0.012, z + 0.002), Vector3(0.025, 0.115, 0.018), gold_mat, body_group)
    mr.rotation_degrees.z = -31.0

func _tail() -> void:
    var root := Node3D.new()
    root.name = "TailGroup"
    body_group.add_child(root)
    var points: Array[Vector3] = [
        Vector3(0.48, 0.72, -0.25), Vector3(0.62, 0.80, -0.30), Vector3(0.75, 0.94, -0.31),
        Vector3(0.84, 1.10, -0.29), Vector3(0.88, 1.27, -0.24), Vector3(0.86, 1.43, -0.17)
    ]
    for i in range(points.size()):
        var r := 0.145 - float(i) * 0.010
        _sphere("Fur_Tail_%d" % i, points[i], r, Vector3(1.0, 1.18, 0.92), fur_mat, root)

func _shoe(x: float) -> void:
    var shoe := _capsule("Shoe", Vector3(x, 0.19, 0.17), 0.185, 0.41, Vector3(1.08, 0.70, 1.34), black_mat, body_group)
    shoe.rotation_degrees.x = 90.0

func _sphere(name_value: String, pos: Vector3, radius: float, scale_value: Vector3, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh := SphereMesh.new()
    mesh.radius = radius
    mesh.height = radius * 2.0
    mesh.radial_segments = 72
    mesh.rings = 40
    return _instance(name_value, mesh, pos, scale_value, mat, parent)

func _capsule(name_value: String, pos: Vector3, radius: float, height: float, scale_value: Vector3, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh := CapsuleMesh.new()
    mesh.radius = radius
    mesh.height = maxf(height, radius * 2.0 + 0.02)
    mesh.radial_segments = 48
    mesh.rings = 20
    return _instance(name_value, mesh, pos, scale_value, mat, parent)

func _cone(name_value: String, pos: Vector3, radius: float, height: float, mat: Material, parent: Node, segments: int = 18) -> MeshInstance3D:
    var mesh := CylinderMesh.new()
    mesh.top_radius = 0.008
    mesh.bottom_radius = radius
    mesh.height = height
    mesh.radial_segments = segments
    return _instance(name_value, mesh, pos, Vector3.ONE, mat, parent)

func _cylinder(name_value: String, pos: Vector3, radius: float, height: float, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh := CylinderMesh.new()
    mesh.top_radius = radius
    mesh.bottom_radius = radius
    mesh.height = height
    mesh.radial_segments = 48
    return _instance(name_value, mesh, pos, Vector3.ONE, mat, parent)

func _torus(name_value: String, pos: Vector3, inner_radius: float, outer_radius: float, mat: Material, parent: Node) -> MeshInstance3D:
    var mesh := TorusMesh.new()
    mesh.inner_radius = inner_radius
    mesh.outer_radius = outer_radius
    mesh.rings = 64
    mesh.ring_segments = 24
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
    mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON
    parent.add_child(mi)
    return mi
