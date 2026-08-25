extends Node

signal state_changed(state: String)
signal emotion_changed(emotion: String, intensity: float)
signal action_requested(action: String)
signal speech_level_changed(level: float)
signal look_changed(x: float, y: float)
signal energy_changed(value: float)

var current_state := "idle"
var current_emotion := "neutral"
var emotion_intensity := 0.0
var speech_level := 0.0
var look := Vector2.ZERO
var energy := 0.5

func set_state(value: String) -> void:
    current_state = value.to_lower()
    state_changed.emit(current_state)

func set_emotion(value: String, intensity := 1.0) -> void:
    current_emotion = value.to_lower()
    emotion_intensity = clampf(intensity, 0.0, 1.0)
    emotion_changed.emit(current_emotion, emotion_intensity)

func trigger_action(value: String) -> void:
    action_requested.emit(value.to_lower())

func set_speech_level(value: float) -> void:
    speech_level = clampf(value, 0.0, 1.0)
    speech_level_changed.emit(speech_level)

func set_look(x: float, y: float) -> void:
    look = Vector2(clampf(x, -1.0, 1.0), clampf(y, -1.0, 1.0))
    look_changed.emit(look.x, look.y)

func set_energy(value: float) -> void:
    energy = clampf(value, 0.0, 1.0)
    energy_changed.emit(energy)

func apply_ai_directive(directive: Dictionary) -> void:
    if directive.has("state"):
        set_state(str(directive["state"]))
    if directive.has("emotion"):
        set_emotion(str(directive["emotion"]), float(directive.get("emotion_intensity", 1.0)))
    if directive.has("energy"):
        set_energy(float(directive["energy"]))
    if directive.has("gesture"):
        trigger_action(str(directive["gesture"]))
    if directive.has("look") and directive["look"] is Dictionary:
        var l: Dictionary = directive["look"]
        set_look(float(l.get("x", 0.0)), float(l.get("y", 0.0)))
