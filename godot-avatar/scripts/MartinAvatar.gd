extends Node3D

@onready var animation_tree: AnimationTree = $AnimationTree
@onready var state_machine = animation_tree.get("parameters/playback") if animation_tree else null
@onready var head: Node3D = $MartinRig/Head
@onready var jaw: Node3D = $MartinRig/Head/Jaw

var idle_time := 0.0
var current_state := "idle"
var smoke_enabled := false
var smoke_elapsed := 0.0
var smoke_index := 0
var smoke_states := ["idle", "listening", "thinking", "talking", "happy", "game", "toast", "dj", "dance"]

func _ready() -> void:
    smoke_enabled = "--avatar-smoke" in OS.get_cmdline_user_args()
    if animation_tree and animation_tree.tree_root:
        animation_tree.active = true
    MartinBridge.state_changed.connect(_on_state_changed)
    MartinBridge.emotion_changed.connect(_on_emotion_changed)
    MartinBridge.action_requested.connect(_on_action_requested)
    MartinBridge.speech_level_changed.connect(_on_speech_level_changed)
    MartinBridge.look_changed.connect(_on_look_changed)
    _on_state_changed(MartinBridge.current_state)
    if smoke_enabled:
        MartinBridge.set_state(smoke_states[0])

func _process(delta: float) -> void:
    idle_time += delta
    _procedural_motion()
    if smoke_enabled:
        smoke_elapsed += delta
        if smoke_elapsed >= 2.2:
            smoke_elapsed = 0.0
            smoke_index = (smoke_index + 1) % smoke_states.size()
            var next_state: String = smoke_states[smoke_index]
            MartinBridge.set_state(next_state)
            if next_state == "talking":
                MartinBridge.set_speech_level(0.75)
            else:
                MartinBridge.set_speech_level(0.0)

func _procedural_motion() -> void:
    var bob := sin(idle_time * 1.1) * 0.008
    position.y = bob
    match current_state:
        "idle":
            rotation.y = sin(idle_time * 0.35) * 0.018
        "listening":
            rotation.y = sin(idle_time * 0.55) * 0.028
            head.rotation.z = sin(idle_time * 0.9) * 0.03
        "thinking":
            head.rotation.z = -0.08 + sin(idle_time * 0.6) * 0.02
        "talking":
            rotation.y = sin(idle_time * 1.2) * 0.025
            head.rotation.z = sin(idle_time * 1.5) * 0.018
        "happy":
            position.y = bob + abs(sin(idle_time * 2.2)) * 0.018
        "game":
            rotation.y = sin(idle_time * 1.6) * 0.04
        "toast":
            head.rotation.z = sin(idle_time * 0.8) * 0.015
        "dj", "dance":
            rotation.z = sin(idle_time * 3.2) * 0.045
            position.y = bob + abs(sin(idle_time * 3.2)) * 0.025
        "sleeping":
            rotation.z = -0.035

func _on_state_changed(value: String) -> void:
    current_state = value
    var target := {
        "idle": "Idle",
        "listening": "Listen",
        "thinking": "Think",
        "talking": "Talk",
        "happy": "Happy",
        "game": "Game",
        "toast": "Toast",
        "dj": "DJ",
        "dance": "Dance",
        "sleeping": "Sleep"
    }.get(value, "Idle")
    if state_machine:
        state_machine.travel(target)

func _on_emotion_changed(emotion: String, intensity: float) -> void:
    if animation_tree and animation_tree.tree_root:
        animation_tree.set("parameters/emotion_blend/blend_amount", intensity)
        animation_tree.set("parameters/emotion_mode/transition_request", emotion)

func _on_action_requested(action: String) -> void:
    if animation_tree and animation_tree.tree_root:
        animation_tree.set("parameters/action_one_shot/request", AnimationNodeOneShot.ONE_SHOT_REQUEST_FIRE)
        animation_tree.set("parameters/action_selector/transition_request", action)

func _on_speech_level_changed(level: float) -> void:
    if jaw:
        jaw.rotation.x = deg_to_rad(clampf(level, 0.0, 1.0) * 18.0)
    if animation_tree and animation_tree.tree_root:
        animation_tree.set("parameters/speech/blend_amount", clampf(level, 0.0, 1.0))

func _on_look_changed(x: float, y: float) -> void:
    if head:
        head.rotation.y = deg_to_rad(x * 12.0)
        head.rotation.x = deg_to_rad(-y * 8.0)
