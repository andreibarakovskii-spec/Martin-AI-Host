class_name SurfacePolish
extends Node

@export var model_root_path: NodePath

func _ready() -> void:
    var root := get_node_or_null(model_root_path) if not model_root_path.is_empty() else get_parent()
    if root == null:
        return
    await get_tree().process_frame
    _polish_recursive(root)

func _polish_recursive(node: Node) -> void:
    if node is MeshInstance3D:
        _polish_mesh(node as MeshInstance3D)
    for child in node.get_children():
        _polish_recursive(child)

func _polish_mesh(mi: MeshInstance3D) -> void:
    if mi.mesh == null:
        return
    var lname := mi.name.to_lower()
    for surface in range(mi.mesh.get_surface_count()):
        var src := mi.get_active_material(surface)
        if src == null:
            continue
        var mat := src.duplicate(true)
        if mat is StandardMaterial3D:
            var sm := mat as StandardMaterial3D
            if _has_any(lname, ["eye", "iris", "pupil", "cornea"]):
                sm.roughness = 0.08
                sm.metallic = 0.0
                sm.specular = 0.92
                sm.clearcoat_enabled = true
                sm.clearcoat = 0.55
                sm.clearcoat_roughness = 0.06
            elif _has_any(lname, ["nose", "muzzle", "snout"]):
                sm.roughness = 0.46
                sm.specular = 0.52
            elif _has_any(lname, ["fur", "head", "ear", "tail", "paw", "hand"]):
                sm.roughness = 0.88
                sm.specular = 0.18
            elif _has_any(lname, ["metal", "buckle", "button", "headphone", "mic"]):
                sm.metallic = 0.72
                sm.roughness = 0.26
                sm.specular = 0.72
            else:
                sm.roughness = clampf(sm.roughness + 0.10, 0.38, 0.90)
                sm.specular = minf(sm.specular, 0.34)
            mi.set_surface_override_material(surface, sm)

func _has_any(value: String, tokens: Array[String]) -> bool:
    for token in tokens:
        if value.contains(token):
            return true
    return false
