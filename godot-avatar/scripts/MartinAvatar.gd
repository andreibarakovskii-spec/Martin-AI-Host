extends Node3D

@onready var animation_tree: AnimationTree = $AnimationTree
@onready var state_machine = animation_tree.get("parameters/playback")
@onready var head: Node3D = $MartinRig/Head
@onready var jaw: Node3D = $MartinRig/Head/Jaw

var idle_time := 0.0
var current_state := "idle"

func _ready() -> void:
    animation_tree.active = true
    MartinBridge.state_changed.connect(_on_state_changed)
    MartinBridge.emotion_changed.connect(_on_emotion_changed)
    MartinBridge.action_requested.connect(_on_action_requested)
    MartinBridge.speech_level_changed.connect(_on_speech_level_changed)
    MartinBridge.look_changed.connect(_on_look_changed)
    _on_state_changed(MartinBridge.current_state)

func _process(delta: float) -> void:
    idle_time += delta
    if current_state == "idle":
        # Procedural micro-movement keeps Martin alive even when no clip is playing.
        rotation.y = sin(idle_time * 0.35) * 0.015
        position.y = sin(idle_time * 1.1) * 0.006

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
    animation_tree.set("parameters/emotion_blend/blend_amount", intensity)
    animation_tree.set("parameters/emotion_mode/transition_request", emotion)

func _on_action_requested(action: String) -> void:
    animation_tree.set("parameters/action_one_shot/request", AnimationNodeOneShot.ONE_SHOT_REQUEST_FIRE)
    animation_tree.set("parameters/action_selector/transition_request", action)

func _on_speech_level_changed(level: float) -> void:
    # Works immediately with a simple jaw bone, later replace with viseme blendshapes.
    if jaw:
        jaw.rotation.x = deg_to_rad(clampf(level, 0.0, 1.0) * 18.0)
    animation_tree.set("parameters/speech/blend_amount", clampf(level, 0.0, 1.0))

func _on_look_changed(x: float, y: float) -> void:
    if head:
        head.rotation.y = deg_to_rad(x * 12.0)
        head.rotation.x = deg_to_rad(-y * 8.0)
