class_name MartinNpcBrain
extends Node

## Lightweight Talking-Tom-style NPC coordinator.
## Keeps AI intent independent from the concrete GLB/VRM model.

signal state_changed(state: StringName)
signal action_started(action: StringName)

@export var adapter_path: NodePath
@export var min_idle_variant_time := 4.0
@export var max_idle_variant_time := 9.0

var adapter: AvatarModelAdapter
var state: StringName = &"idle"
var emotion: StringName = &"neutral"
var energy := 0.35
var speech_level := 0.0
var look_target := Vector2.ZERO
var _idle_timer := 0.0
var _blink_timer := 1.5
var _rng := RandomNumberGenerator.new()

const STATE_ACTIONS := {
    &"idle": [&"idle", &"idle_2", &"idle_3"],
    &"listening": [&"listen", &"idle"],
    &"thinking": [&"think", &"idle"],
    &"talking": [&"talk", &"talk_2", &"talk_3"],
    &"happy": [&"happy", &"cheer"],
    &"game": [&"explain", &"interact", &"talk"],
    &"toast": [&"toast", &"interact"],
    &"dj": [&"dj", &"dance"],
    &"dance": [&"dance", &"dance_2", &"dance_3"],
}

func _ready() -> void:
    _rng.randomize()
    adapter = get_node_or_null(adapter_path) as AvatarModelAdapter if not adapter_path.is_empty() else null
    _schedule_idle()

func bind_adapter(value: AvatarModelAdapter) -> void:
    adapter = value

func apply_intent(intent: Dictionary) -> void:
    set_state(StringName(intent.get("state", state)))
    emotion = StringName(intent.get("emotion", emotion))
    energy = clampf(float(intent.get("energy", energy)), 0.0, 1.0)
    var look = intent.get("look", null)
    if look is Vector2:
        look_target = look
    elif look is Dictionary:
        look_target = Vector2(float(look.get("x", 0.0)), float(look.get("y", 0.0)))
    if intent.has("gesture"):
        play_action(StringName(intent["gesture"]))

func set_state(value: StringName) -> void:
    if value == state:
        return
    state = value
    state_changed.emit(state)
    _play_state_variant()

func set_speech_level(value: float) -> void:
    speech_level = clampf(value, 0.0, 1.0)
    if adapter != null:
        adapter.drive_simple_lipsync(speech_level)

func play_action(action: StringName) -> void:
    if adapter == null:
        return
    adapter.travel(String(action))
    action_started.emit(action)

func _process(delta: float) -> void:
    _blink_timer -= delta
    if _blink_timer <= 0.0:
        _blink()
    if state == &"idle":
        _idle_timer -= delta
        if _idle_timer <= 0.0:
            _play_state_variant()
            _schedule_idle()
    if state == &"talking" and adapter != null:
        adapter.drive_simple_lipsync(speech_level)

func _play_state_variant() -> void:
    var variants: Array = STATE_ACTIONS.get(state, [state])
    if variants.is_empty():
        return
    var chosen: StringName = variants[_rng.randi_range(0, variants.size() - 1)]
    play_action(chosen)

func _schedule_idle() -> void:
    _idle_timer = _rng.randf_range(min_idle_variant_time, max_idle_variant_time)

func _blink() -> void:
    _blink_timer = _rng.randf_range(2.2, 5.8)
    if adapter == null:
        return
    adapter.set_expression("blink_l", 1.0)
    adapter.set_expression("blink_r", 1.0)
    await get_tree().create_timer(0.09).timeout
    if is_instance_valid(adapter):
        adapter.set_expression("blink_l", 0.0)
        adapter.set_expression("blink_r", 0.0)
