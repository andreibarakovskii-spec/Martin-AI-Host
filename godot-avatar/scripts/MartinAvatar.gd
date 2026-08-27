extends Node3D

@onready var rig: Node3D = $MartinRig
@onready var body: Node3D = $MartinRig/BodyRoot
@onready var head: Node3D = $MartinRig/Head
@onready var jaw: Node3D = $MartinRig/Head/Jaw
@onready var ear_l: Node3D = $MartinRig/Head/EarL
@onready var ear_r: Node3D = $MartinRig/Head/EarR
@onready var arm_l: Node3D = $MartinRig/ArmL
@onready var arm_r: Node3D = $MartinRig/ArmR
@onready var tail: Node3D = $MartinRig/Tail
@onready var eye_l: Node3D = $MartinRig/Head/EyeL
@onready var eye_r: Node3D = $MartinRig/Head/EyeR

var t := 0.0
var current_state := "idle"
var emotion := "neutral"
var emotion_intensity := 0.0
var speech_level := 0.0
var look_target := Vector2.ZERO
var energy := 0.5
var gesture := "none"
var gesture_started := 0.0
var smoke_index := 0
var smoke_states := ["idle", "listening", "thinking", "talking", "happy", "game", "toast", "dj", "dance"]
var smoke_elapsed := 0.0
var smoke_mode := false
var smoke_step_duration := 0.42

func _ready() -> void:
    MartinBridge.state_changed.connect(_on_state_changed)
    MartinBridge.emotion_changed.connect(_on_emotion_changed)
    MartinBridge.action_requested.connect(_on_action_requested)
    MartinBridge.speech_level_changed.connect(_on_speech_level_changed)
    MartinBridge.look_changed.connect(_on_look_changed)
    MartinBridge.energy_changed.connect(_on_energy_changed)
    _on_state_changed(MartinBridge.current_state)
    smoke_mode = "--avatar-smoke" in OS.get_cmdline_user_args()
    if smoke_mode:
        print("MARTIN_SMOKE state=idle")
        _on_state_changed(smoke_states[0])

func _process(delta: float) -> void:
    t += delta
    if smoke_mode:
        smoke_elapsed += delta
        if smoke_elapsed > smoke_step_duration:
            smoke_elapsed = 0.0
            smoke_index += 1
            if smoke_index >= smoke_states.size():
                print("MARTIN_SMOKE_OK states=", smoke_states.size())
                get_tree().quit(0)
                return
            _on_state_changed(smoke_states[smoke_index])
            speech_level = 0.75 if current_state == "talking" else 0.0
            look_target = Vector2(0.35, -0.12) if current_state == "listening" else Vector2.ZERO
            print("MARTIN_SMOKE state=", current_state)
    _animate_character(delta)

func _animate_character(delta: float) -> void:
    var tempo := lerpf(0.75, 1.75, energy)
    var breath := sin(t * 1.55 * tempo)
    var sway := sin(t * 0.55 * tempo)
    var beat := sin(t * 5.2)
    rig.position.y = -0.18 + breath * 0.012
    body.scale = Vector3(1.0 + breath * 0.006, 1.0 + breath * 0.012, 1.0 + breath * 0.006)

    var target_head_x := deg_to_rad(-look_target.y * 8.0)
    var target_head_y := deg_to_rad(look_target.x * 13.0)
    var target_head_z := 0.0
    if current_state == "thinking":
        target_head_x += deg_to_rad(-7.0)
        target_head_y += deg_to_rad(7.0 + sway * 3.0)
    elif current_state == "listening":
        target_head_x += deg_to_rad(3.0)
        target_head_y += deg_to_rad(sway * 2.0)
    elif current_state == "dj" or current_state == "dance":
        target_head_y += deg_to_rad(beat * 5.0)
        target_head_x += deg_to_rad(abs(beat) * 3.0)

    if gesture == "head_shake":
        target_head_y += deg_to_rad(sin((t - gesture_started) * 10.0) * 12.0)
    elif gesture == "question_pose":
        target_head_z += deg_to_rad(-5.0)
        target_head_x += deg_to_rad(-3.0)
    elif gesture == "point_forward":
        target_head_x += deg_to_rad(3.5)
    elif gesture == "celebrate":
        target_head_x += deg_to_rad(-4.0 + abs(beat) * 3.0)

    head.rotation.x = lerp_angle(head.rotation.x, target_head_x, minf(1.0, delta * 6.0))
    head.rotation.y = lerp_angle(head.rotation.y, target_head_y, minf(1.0, delta * 6.0))
    head.rotation.z = lerp_angle(head.rotation.z, target_head_z, minf(1.0, delta * 6.0))

    var ear_pitch := 0.0
    if current_state == "listening": ear_pitch = deg_to_rad(-14.0)
    if current_state == "happy": ear_pitch = deg_to_rad(-7.0)
    if current_state == "sleeping": ear_pitch = deg_to_rad(18.0)
    if emotion == "curious": ear_pitch -= deg_to_rad(4.0 * maxf(0.35, emotion_intensity))
    ear_l.rotation.z = lerp_angle(ear_l.rotation.z, deg_to_rad(-7.0) + ear_pitch, minf(1.0, delta * 7.0))
    ear_r.rotation.z = lerp_angle(ear_r.rotation.z, deg_to_rad(7.0) - ear_pitch, minf(1.0, delta * 7.0))

    jaw.rotation.x = deg_to_rad(clampf(speech_level, 0.0, 1.0) * 20.0)
    var talk_gesture := clampf(speech_level, 0.0, 1.0)
    var left_target := 0.0
    var right_target := 0.0
    var left_pitch := 0.0
    var right_pitch := 0.0

    if current_state == "talking":
        left_target = deg_to_rad(-12.0 - talk_gesture * 18.0 + sway * 5.0)
        right_target = deg_to_rad(12.0 + talk_gesture * 15.0 - sway * 4.0)
    elif current_state == "toast":
        right_target = deg_to_rad(-72.0)
        left_target = deg_to_rad(-8.0)
    elif current_state == "game":
        left_target = deg_to_rad(-36.0 + sway * 10.0)
        right_target = deg_to_rad(28.0 - sway * 8.0)
    elif current_state == "dj" or current_state == "dance":
        left_target = deg_to_rad(-28.0 + beat * 20.0)
        right_target = deg_to_rad(28.0 - beat * 20.0)
    elif current_state == "happy":
        left_target = deg_to_rad(-18.0)
        right_target = deg_to_rad(18.0)

    match gesture:
        "explain_two_hands":
            left_target = deg_to_rad(-48.0 + sway * 5.0)
            right_target = deg_to_rad(48.0 - sway * 5.0)
            left_pitch = deg_to_rad(-12.0)
            right_pitch = deg_to_rad(-12.0)
        "explain_one_hand":
            right_target = deg_to_rad(-48.0 + sway * 5.0)
            left_target = deg_to_rad(-8.0)
            right_pitch = deg_to_rad(-14.0)
        "point_forward":
            right_target = deg_to_rad(-76.0)
            right_pitch = deg_to_rad(-58.0)
            left_target = deg_to_rad(-6.0)
        "question_pose":
            left_target = deg_to_rad(-22.0)
            right_target = deg_to_rad(38.0)
            right_pitch = deg_to_rad(-28.0)
        "celebrate":
            left_target = deg_to_rad(-82.0 + beat * 7.0)
            right_target = deg_to_rad(82.0 - beat * 7.0)
        "raise_glass":
            right_target = deg_to_rad(-78.0)
            right_pitch = deg_to_rad(-26.0)
            left_target = deg_to_rad(-6.0)
        "open_hands":
            left_target = deg_to_rad(-42.0)
            right_target = deg_to_rad(42.0)
        "talk_neutral":
            left_target += deg_to_rad(sway * 4.0)
            right_target -= deg_to_rad(sway * 4.0)

    arm_l.rotation.z = lerp_angle(arm_l.rotation.z, left_target, minf(1.0, delta * 6.0))
    arm_r.rotation.z = lerp_angle(arm_r.rotation.z, right_target, minf(1.0, delta * 6.0))
    arm_l.rotation.x = lerp_angle(arm_l.rotation.x, left_pitch, minf(1.0, delta * 6.0))
    arm_r.rotation.x = lerp_angle(arm_r.rotation.x, right_pitch, minf(1.0, delta * 6.0))

    var tail_amp := deg_to_rad(10.0 + energy * 16.0)
    if current_state == "happy": tail_amp *= 1.45
    if current_state == "dj" or current_state == "dance": tail_amp *= 1.7
    if emotion == "happy": tail_amp *= 1.15
    tail.rotation.y = sin(t * (1.6 + energy * 1.5)) * tail_amp
    tail.rotation.z = deg_to_rad(18.0) + cos(t * 1.1) * deg_to_rad(5.0)

    eye_l.position.x = -0.245 + look_target.x * 0.014
    eye_r.position.x = 0.245 + look_target.x * 0.014
    eye_l.position.y = 0.105 - look_target.y * 0.010
    eye_r.position.y = 0.105 - look_target.y * 0.010

func _on_state_changed(value: String) -> void:
    current_state = value.to_lower()
    if current_state == "dj" or current_state == "dance": energy = maxf(energy, 0.82)
    elif current_state == "sleeping": energy = 0.12

func _on_emotion_changed(value: String, intensity: float) -> void:
    emotion = value
    emotion_intensity = clampf(intensity, 0.0, 1.0)

func _on_action_requested(action: String) -> void:
    gesture = action.to_lower()
    gesture_started = t
    if gesture == "toast" or gesture == "raise_glass": _on_state_changed("toast")
    elif gesture == "dance": _on_state_changed("dance")
    elif gesture == "dj": _on_state_changed("dj")
    elif gesture in ["explain_two_hands", "explain_one_hand", "question_pose", "point_forward"]: _on_state_changed("game")
    elif gesture == "celebrate": _on_state_changed("happy")

func _on_speech_level_changed(level: float) -> void:
    speech_level = clampf(level, 0.0, 1.0)

func _on_look_changed(x: float, y: float) -> void:
    look_target = Vector2(clampf(x, -1.0, 1.0), clampf(y, -1.0, 1.0))

func _on_energy_changed(value: float) -> void:
    energy = clampf(value, 0.0, 1.0)
