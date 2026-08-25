extends Node3D
class_name AvatarRuntimeLoader

signal avatar_loaded(root: Node3D)
signal avatar_failed(reason: String)

@export var model_path := "res://models/martin.glb"
@export var fallback_path := NodePath("../MartinAvatar")
var current_avatar: Node3D
var adapter: AvatarModelAdapter

func _ready() -> void:
    load_avatar(model_path)

func load_avatar(path: String) -> void:
    if path.is_empty() or not FileAccess.file_exists(path):
        _use_fallback("model missing: %s" % path)
        return
    var scene := load(path)
    if scene == null or not (scene is PackedScene):
        _use_fallback("unsupported avatar resource: %s" % path)
        return
    var root := (scene as PackedScene).instantiate() as Node3D
    if root == null:
        _use_fallback("avatar root is not Node3D")
        return
    add_child(root)
    current_avatar = root
    adapter = AvatarModelAdapter.new()
    root.add_child(adapter)
    adapter.bind_model(root)
    avatar_loaded.emit(root)

func _use_fallback(reason: String) -> void:
    push_warning("AvatarRuntimeLoader: " + reason)
    var fallback := get_node_or_null(fallback_path) as Node3D
    if fallback:
        current_avatar = fallback
        adapter = AvatarModelAdapter.new()
        fallback.add_child(adapter)
        adapter.bind_model(fallback)
        avatar_loaded.emit(fallback)
    else:
        avatar_failed.emit(reason)

func drive(state: String, emotion: String, gesture: String, energy: float, look: Vector2, speech_level: float) -> void:
    if adapter:
        adapter.apply_ai_frame(state, emotion, gesture, energy, look, speech_level)
