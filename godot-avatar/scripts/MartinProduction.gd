extends Node3D

const MODEL_PATH := "res://models/production/martin.glb"
const MARTIN_V2_VISUAL := preload("res://scripts/MartinV2Visual.gd")

@onready var model_mount: Node3D = $ModelMount
@onready var camera: Camera3D = $Camera3D

var compatibility_container: Node3D
var model_instance: Node3D
var visual_v2: Node3D
var adapter: AvatarModelAdapter
var current_state := "idle"
var speech_level := 0.0
var look_target := Vector2.ZERO
var energy := 0.5
var elapsed := 0.0
var blink_clock := 0.0
var next_blink := 3.2
var android_host

func _ready() -> void:
    var packed: PackedScene = load(MODEL_PATH) as PackedScene
    if packed == null:
        push_error("MARTIN_PRODUCTION_MODEL_MISSING %s" % MODEL_PATH)
        return

    # Keep the proven Cat Pilot skeleton/animations strictly as an invisible compatibility rig.
    # The wrapper remains hidden even when legacy animation tracks toggle child mesh visibility.
    compatibility_container = Node3D.new()
    compatibility_container.name = "CompatibilityRigHidden"
    model_mount.add_child(compatibility_container)

    model_instance = packed.instantiate() as Node3D
    if model_instance == null:
        push_error("MARTIN_PRODUCTION_MODEL_INVALID")
        return
    model_instance.name = "MartinCompatibilityRig"
    compatibility_container.add_child(model_instance)
    _fit_model_to_stage()
    _hide_source_meshes(model_instance)
    compatibility_container.visible = false

    visual_v2 = MARTIN_V2_VISUAL.new() as Node3D
    visual_v2.name = "MartinV2"
    model_mount.add_child(visual_v2)

    adapter = AvatarModelAdapter.new()
    adapter.name = "AvatarModelAdapter"
    adapter.model_root_path = NodePath("../ModelMount")
    add_child(adapter)

    await get_tree().process_frame
    if adapter == null or not adapter.is_production_model_ready():
        push_error("MARTIN_PRODUCTION_NO_SKELETON")
        return

    MartinBridge.state_changed.connect(_on_state_changed)
    MartinBridge.speech_level_changed.connect(_on_speech_level)
    MartinBridge.look_changed.connect(_on_look_changed)
    MartinBridge.energy_changed.connect(_on_energy_changed)
    MartinBridge.emotion_changed.connect(_on_emotion_changed)
    MartinBridge.action_requested.connect(_on_action_requested)
    _connect_android_host()

    _on_state_changed(MartinBridge.current_state)
    _on_speech_level(MartinBridge.speech_level)
    _on_look_changed(MartinBridge.look.x, MartinBridge.look.y)
    _on_energy_changed(MartinBridge.energy)

    var caps: Dictionary = adapter.get_capabilities()
    print("MARTIN_PRODUCTION_READY skeleton=%s face=%s animations=%s morphs=%s visual=v2 legacy_visible=%s" % [
        caps.get("skeleton", false),
        caps.get("face_mesh", false),
        caps.get("animations", []),
        caps.get("blend_shapes", []),
        compatibility_container.visible,
    ])

    if "--avatar-smoke" in OS.get_cmdline_user_args():
        call_deferred("_run_smoke")

func _hide_source_meshes(root: Node) -> void:
    if root is MeshInstance3D:
        (root as MeshInstance3D).visible = false
    for child in root.get_children():
        _hide_source_meshes(child)

func _connect_android_host() -> void:
    android_host = Engine.get_singleton("MartinHostPlugin")
    if android_host == null:
        print("MARTIN_ANDROID_PLUGIN desktop_or_headless")
        return
    android_host.connect("state_changed", _on_host_state)
    android_host.connect("speech_level_changed", _on_host_speech)
    android_host.connect("action_requested", _on_host_action)
    android_host.connect("look_changed", _on_host_look)
    print("MARTIN_ANDROID_PLUGIN_CONNECTED")

func _on_host_state(value: String) -> void:
    MartinBridge.set_state(value)

func _on_host_speech(value: float) -> void:
    MartinBridge.set_speech_level(value)

func _on_host_action(value: String) -> void:
    MartinBridge.trigger_action(value)

func _on_host_look(x: float, y: float) -> void:
    MartinBridge.set_look(x, y)

func _process(delta: float) -> void:
    if model_instance == null:
        return
    # Legacy visibility tracks are allowed to animate internally, but the wrapper is always hidden.
    if compatibility_container != null and compatibility_container.visible:
        compatibility_container.visible = false

    elapsed += delta
    blink_clock += delta

    var idle_sway: float = sin(elapsed * 1.35) * 0.006
    var performance_sway: float = 0.0
    if current_state == "talking":
        performance_sway = sin(elapsed * 6.0) * (0.003 + speech_level * 0.006)
    elif current_state == "dj":
        performance_sway = sin(elapsed * 3.6) * 0.012
    model_mount.rotation.z = lerpf(model_mount.rotation.z, idle_sway + performance_sway, minf(1.0, delta * 4.2))

    var target_pitch: float = look_target.y * 0.022
    var target_yaw: float = look_target.x * 0.040
    model_mount.rotation.x = lerpf(model_mount.rotation.x, target_pitch, minf(1.0, delta * 3.8))
    model_mount.rotation.y = lerpf(model_mount.rotation.y, target_yaw, minf(1.0, delta * 3.8))

    if adapter != null:
        if current_state == "talking":
            adapter.drive_simple_lipsync(speech_level)
        if blink_clock >= next_blink:
            _blink()
            blink_clock = 0.0
            next_blink = randf_range(2.8, 5.4)

func _fit_model_to_stage() -> void:
    var bounds: AABB = _collect_bounds(model_instance)
    if bounds.size.y <= 0.001:
        push_error("MARTIN_PRODUCTION_EMPTY_BOUNDS")
        return
    var desired_height: float = 2.55
    var factor: float = desired_height / bounds.size.y
    model_instance.scale = Vector3.ONE * factor
    var center: Vector3 = bounds.position + bounds.size * 0.5
    model_instance.position = Vector3(-center.x * factor, -bounds.position.y * factor, -center.z * factor)
    print("MARTIN_MODEL_FIT source_height=%.3f scale=%.3f" % [bounds.size.y, factor])

func _collect_bounds(root: Node3D) -> AABB:
    var result: AABB = AABB()
    var initialized: bool = false
    var stack: Array[Node] = [root]
    while not stack.is_empty():
        var node: Node = stack.pop_back() as Node
        if node is MeshInstance3D:
            var mesh_node: MeshInstance3D = node as MeshInstance3D
            if mesh_node.mesh != null:
                var local_box: AABB = mesh_node.get_aabb()
                var rel: Transform3D = root.global_transform.affine_inverse() * mesh_node.global_transform
                var corners: Array[Vector3] = [
                    local_box.position,
                    local_box.position + Vector3(local_box.size.x, 0, 0),
                    local_box.position + Vector3(0, local_box.size.y, 0),
                    local_box.position + Vector3(0, 0, local_box.size.z),
                    local_box.position + Vector3(local_box.size.x, local_box.size.y, 0),
                    local_box.position + Vector3(local_box.size.x, 0, local_box.size.z),
                    local_box.position + Vector3(0, local_box.size.y, local_box.size.z),
                    local_box.position + local_box.size,
                ]
                for corner in corners:
                    var p: Vector3 = rel * corner
                    if not initialized:
                        result = AABB(p, Vector3.ZERO)
                        initialized = true
                    else:
                        result = result.expand(p)
        for child in node.get_children():
            stack.push_back(child as Node)
    return result

func _on_state_changed(value: String) -> void:
    current_state = value.to_lower()
    if adapter == null:
        return
    adapter.clear_face()
    adapter.travel(current_state)
    if current_state == "talking":
        adapter.set_expression("brow_up", 0.12 + energy * 0.08)
    elif current_state == "happy":
        adapter.set_expression("smile", 0.65)
    elif current_state == "thinking":
        adapter.set_expression("brow_up", 0.28)

func _on_speech_level(value: float) -> void:
    speech_level = clampf(value, 0.0, 1.0)

func _on_look_changed(x: float, y: float) -> void:
    look_target = Vector2(clampf(x, -1.0, 1.0), clampf(y, -1.0, 1.0))

func _on_energy_changed(value: float) -> void:
    energy = clampf(value, 0.0, 1.0)

func _on_emotion_changed(value: String) -> void:
    if adapter == null:
        return
    match value.to_lower():
        "happy":
            adapter.set_expression("smile", 0.72)
            adapter.set_expression("brow_up", 0.10)
        "excited":
            adapter.set_expression("smile", 0.88)
            adapter.set_expression("brow_up", 0.42)
        "thinking":
            adapter.set_expression("brow_up", 0.30)
        _:
            pass

func _on_action_requested(value: String) -> void:
    if adapter == null:
        return
    adapter.play_best_animation(value)

func _blink() -> void:
    if adapter == null:
        return
    var tween: Tween = create_tween()
    tween.set_parallel(true)
    tween.tween_method(func(v: float): adapter.set_expression("blink_l", v), 0.0, 1.0, 0.065)
    tween.tween_method(func(v: float): adapter.set_expression("blink_r", v), 0.0, 1.0, 0.065)
    await tween.finished
    var open_tween: Tween = create_tween()
    open_tween.set_parallel(true)
    open_tween.tween_method(func(v: float): adapter.set_expression("blink_l", v), 1.0, 0.0, 0.080)
    open_tween.tween_method(func(v: float): adapter.set_expression("blink_r", v), 1.0, 0.0, 0.080)

func _run_smoke() -> void:
    await get_tree().create_timer(0.25).timeout
    var caps: Dictionary = adapter.get_capabilities() if adapter != null else {}
    if not caps.get("skeleton", false):
        push_error("MARTIN_SMOKE_FAIL skeleton")
        get_tree().quit(2)
        return
    if not caps.get("animation_player", false):
        push_error("MARTIN_SMOKE_FAIL animation_player")
        get_tree().quit(3)
        return
    if compatibility_container == null or compatibility_container.visible:
        push_error("MARTIN_SMOKE_FAIL legacy_visibility")
        get_tree().quit(4)
        return
    var animations: Array = caps.get("animations", []) as Array
    for required: String in ["DefaultAnim", "Cheer", "Walk", "Run"]:
        if required not in animations:
            push_error("MARTIN_SMOKE_FAIL animation=%s" % required)
            get_tree().quit(5)
            return
    MartinBridge.set_state("listening")
    MartinBridge.set_state("thinking")
    MartinBridge.set_state("talking")
    MartinBridge.set_speech_level(0.75)
    MartinBridge.trigger_action("happy")
    MartinBridge.set_state("dj")
    MartinBridge.trigger_action("dance")
    MartinBridge.set_look(0.35, -0.18)
    await get_tree().create_timer(0.35).timeout
    print("MARTIN_SMOKE_OK states=idle,listening,thinking,talking,dj model=production visual=v2 legacy_hidden=true")
    get_tree().quit(0)
