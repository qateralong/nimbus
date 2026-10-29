import json, os
ROOT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/resourcepacks/chests")

def regions(u, v, w, h, d):
    f4, f5, f6, f7, f8, f9 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    f10, f11, f12 = v, v + d, v + d + h
    return {"down": (f5, f10, f6, f11), "up": (f6, f10, f7, f11), "west": (f4, f11, f5, f12),
            "north": (f5, f11, f6, f12), "east": (f6, f11, f8, f12), "south": (f8, f11, f9, f12)}

def px(r):
    return [round(x / 4.0, 4) for x in r]

def cube(frm, to, tex, flip_up, lock=False, cull_down=False):
    u, v = tex
    w, h, d = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
    r = regions(u, v, w, h, d)
    up = list(r["up"])
    if flip_up:
        up = [up[2], up[1], up[0], up[3]]
    faces = {
        "north": {"uv": px(r["south"]), "rotation": 180, "texture": "#chest"},
        "east": {"uv": px(r["west"]), "rotation": 180, "texture": "#chest"},
        "south": {"uv": px(r["north"]), "rotation": 180, "texture": "#chest"},
        "west": {"uv": px(r["east"]), "rotation": 180, "texture": "#chest"},
        "up": {"uv": px(up), "texture": "#chest"},
        "down": {"uv": px(r["down"]), "texture": "#chest"},
    }
    if lock:
        faces["down"]["rotation"] = 180
    if cull_down:
        faces["down"]["cullface"] = "down"
    return {"from": frm, "to": to, "faces": faces}

def shape(kind):
    if kind == "single":
        return [cube([1, 0, 1], [15, 10, 15], (0, 19), False, cull_down=True), cube([1, 9, 1], [15, 14, 15], (0, 0), True), cube([7, 7, 0], [9, 11, 1], (0, 0), True, lock=True)]
    if kind == "right":
        return [cube([0, 0, 1], [15, 10, 15], (0, 19), False, cull_down=True), cube([0, 9, 1], [15, 14, 15], (0, 0), True), cube([0, 7, 0], [1, 11, 1], (0, 0), True, lock=True)]
    return [cube([1, 0, 1], [16, 10, 15], (0, 19), False, cull_down=True), cube([1, 9, 1], [16, 14, 15], (0, 0), True), cube([15, 7, 0], [16, 11, 1], (0, 0), True, lock=True)]

def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=1)

write(os.path.join(ROOT, "pack.mcmeta"), {"pack": {"pack_format": 34, "description": "Nimbus chest models"}})
write(os.path.join(ROOT, "assets/minecraft/atlases/blocks.json"), {"sources": [{"type": "directory", "source": "entity/chest", "prefix": "entity/chest/"}, {"type": "directory", "source": "entity/bed", "prefix": "entity/bed/"}, {"type": "directory", "source": "entity/shulker", "prefix": "entity/shulker/"}, {"type": "directory", "source": "entity/signs", "prefix": "entity/signs/"}, {"type": "directory", "source": "entity/decorated_pot", "prefix": "entity/decorated_pot/"}]})
for kind in ("single", "left", "right"):
    write(os.path.join(ROOT, f"assets/nimbus/models/block/template_chest_{kind}.json"),
          {"parent": "block/block", "texture_size": [64, 64], "textures": {"particle": "#particle"}, "elements": shape(kind)})
blocks = {"chest": ("normal", "block/oak_planks"), "trapped_chest": ("trapped", "block/oak_planks"), "ender_chest": ("ender", "block/obsidian")}
yrot = {"north": 0, "east": 90, "south": 180, "west": 270}
for block, (tex, particle) in blocks.items():
    variants = {}
    kinds = ("single",) if block == "ender_chest" else ("single", "left", "right")
    for kind in kinds:
        name = f"{block}_{kind}"
        texture = f"minecraft:entity/chest/{tex}" + ("" if kind == "single" else f"_{kind}")
        write(os.path.join(ROOT, f"assets/nimbus/models/block/{name}.json"),
              {"parent": f"nimbus:block/template_chest_{kind}", "textures": {"chest": texture, "particle": particle}})
        for facing, y in yrot.items():
            key = f"facing={facing}" + ("" if block == "ender_chest" else f",type={kind}")
            entry = {"model": f"nimbus:block/{name}"}
            if y:
                entry["y"] = y
            variants[key] = entry
    write(os.path.join(ROOT, f"assets/minecraft/blockstates/{block}.json"), {"variants": variants})
print("ok")
