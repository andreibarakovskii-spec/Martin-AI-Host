extends Node3D

const MODEL_PATH := "res://models/production/martin.glb"

@onready var model_mount: Node3D = $ModelMount
@onready var camera: Camera3D = $Camera3D

var model_instance: Node3D
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

    model_instance = packed.instantiate() as Node3D
    if model_instance == null:
        push_error("MARTIN_PRODUCTION_MODEL_INVALID")
        return
    model_instance.name = "MartinModel"
    model_mount.add_child(model_instance)
    _fit_model_to_stage()

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
    print("MARTIN_PRODUCTION_READY skeleton=%s face=%s animations=%s morphs=%s" % [
        caps.get("skeleton", false),
        caps.get("face_mesh", false),
        caps.get("animations", []),
        caps.get("blend_shapes", []),
    ])

    if "--avatar-smoke" in OS.get_cmdline_user_args():
        call_deferred("_run_smoke")

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
    elapsed += delta
    blink_clock += delta

    # Small performance motion only; no old full-body vertical bobbing.
    var idle_sway: float = sin(elapsed * 1.35) * 0.007
    var performance_sway: float = 0.0
    if current_state == "talking":
        performance_sway = sin(elapsed * 7.0) * (0.004 + speech_level * 0.010)
    elif current_state == "dj":
        performance_sway = sin(elapsed * 4.2) * 0.018
    model_mount.rotation.z = lerpf(model_mount.rotation.z, idle_sway + performance_sway, minf(1.0, delta * 5.0))

    var target_pitch: float = look_target.y * 0.025
    var target_yaw: float = look_target.x * 0.045
    model_mount.rotation.x = lerpf(model_mount.rotation.x, target_pitch, minf(1.0, delta * 4.0))
    model_mount.rotation.y = lerpf(model_mount.rotation.y, target_yaw, minf(1.0, delta * 4.0))

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
    adapter.play_best_animation(current_state)
    if current_state == "happy":
        adapter.set_expression("smile", 1.0)
    elif current_state == "sleeping":
        adapter.set_expression("blink_l", 1.0)
        adapter.set_expression("blink_r", 1.0)

func _on_speech_level(value: float) -> void:
    speech_level = clampf(value, 0.0, 1.0)
    if adapter != null and current_state == "talking":
        adapter.drive_simple_lipsync(speech_level)

func _on_look_changed(x: float, y: float) -> void:
    look_target = Vector2(clampf(x, -1.0, 1.0), clampf(y, -1.0, 1.0))

func _on_energy_changed(value: float) -> void:
    energy = clampf(value, 0.0, 1.0)

func _on_emotion_changed(emotion: String, intensity: float) -> void:
    if adapter == null:
        return
    match emotion.to_lower():
        "happy", "excited", "playful":
            adapter.set_expression("smile", intensity)
        "sleepy":
            adapter.set_expression("blink_l", intensity)
            adapter.set_expression("blink_r", intensity)

func _on_action_requested(action: String) -> void:
    if adapter == null:
        return
    var a: String = action.to_lower()
    if a in ["dance", "dj", "cheer", "celebrate"]:
        adapter.play_best_animation("happy" if a in ["cheer", "celebrate"] else "dj")
    elif a in ["walk", "run"]:
        adapter.play_best_animation(a)

func _blink() -> void:
    if adapter == null or current_state == "sleeping":
        return
    adapter.set_expression("blink_l", 1.0)
    adapter.set_expression("blink_r", 1.0)
    get_tree().create_timer(0.11).timeout.connect(func():
        if adapter != null and current_state != "sleeping":
            adapter.set_expression("blink_l", 0.0)
            adapter.set_expression("blink_r", 0.0)
    )

func _run_smoke() -> void:
    await get_tree().process_frame
    if adapter == null or not adapter.is_production_model_ready():
        push_error("MARTIN_PRODUCTION_SMOKE_NO_SKELETON")
        get_tree().quit(2)
        return
    var caps: Dictionary = adapter.get_capabilities()
    var animations: Array = caps.get("animations", [])
    for test_state in ["idle", "listening", "thinking", "talking", "happy", "dj", "walk", "run"]:
        _on_state_changed(test_state)
        if test_state == "talking":
            _on_speech_level(0.72)
        await get_tree().create_timer(0.12).timeout
    _on_speech_level(0.0)
    _on_state_changed("idle")
    print("MARTIN_PRODUCTION_SMOKE_OK skeleton=true animations=%d model=%s" % [animations.size(), MODEL_PATH])
    await get_tree().create_timer(0.8).timeout
    get_tree().quit()
