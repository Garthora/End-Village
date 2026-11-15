"""Builds the End Village structure files (.nbt) used by the mod.

Run:  python3 tools/make_village.py
Writes src/main/resources/data/endvillage/structures/end_village/{small,large}.nbt
"""
import gzip
import io
import math
import os
import random
import struct

DATA_VERSION = 3465  # Minecraft 1.20.1

# ---------------------------------------------------------------- NBT writer
class Byte(int): pass
class Int(int): pass
class Long(int): pass
class Double(float): pass
class IntList(list): pass
class DoubleList(list): pass
class CompoundList(list): pass


def _name(out, s):
    b = s.encode("utf-8")
    out.write(struct.pack(">H", len(b)))
    out.write(b)


def _tag_id(v):
    if isinstance(v, Byte): return 1
    if isinstance(v, Long): return 4
    if isinstance(v, Int): return 3
    if isinstance(v, Double): return 6
    if isinstance(v, str): return 8
    if isinstance(v, (IntList, DoubleList, CompoundList)): return 9
    if isinstance(v, dict): return 10
    if isinstance(v, bool): return 1
    if isinstance(v, int): return 3
    if isinstance(v, float): return 6
    raise TypeError(type(v))


def _payload(out, v):
    t = _tag_id(v)
    if t == 1: out.write(struct.pack(">b", int(v)))
    elif t == 3: out.write(struct.pack(">i", int(v)))
    elif t == 4: out.write(struct.pack(">q", int(v)))
    elif t == 6: out.write(struct.pack(">d", float(v)))
    elif t == 8: _name(out, v)
    elif t == 9:
        inner = 3 if isinstance(v, IntList) else 6 if isinstance(v, DoubleList) else 10
        if not v and isinstance(v, CompoundList): inner = 10
        out.write(struct.pack(">bi", inner, len(v)))
        for item in v:
            if inner == 3: out.write(struct.pack(">i", int(item)))
            elif inner == 6: out.write(struct.pack(">d", float(item)))
            else: _compound_body(out, item)
    elif t == 10: _compound_body(out, v)


def _compound_body(out, d):
    for k, v in d.items():
        out.write(struct.pack(">b", _tag_id(v)))
        _name(out, k)
        _payload(out, v)
    out.write(b"\x00")


def write_nbt(path, root):
    buf = io.BytesIO()
    buf.write(b"\x0a")
    _name(buf, "")
    _compound_body(buf, root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, "wb") as f:
        f.write(buf.getvalue())


# ---------------------------------------------------------------- building
COLOR_IDS = {"white": 0, "light_blue": 3, "yellow": 4, "lime": 5, "light_gray": 8, "black": 15}
PROFESSION_COLOR = {
    "chorus_farmer": "lime",
    "purpur_mason": "light_gray",
    "void_librarian": "white",
    "shell_keeper": "black",
    "star_cartographer": "light_blue",
    "dragon_cleric": "yellow",
}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}
STEP = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}


class Village:
    def __init__(self, size, height, surface, seed):
        self.W = size
        self.H = height
        self.S = surface
        self.c = size // 2
        self.rng = random.Random(seed)
        self.blocks = {}
        self.entities = []
        self.footprint = set()

    def set(self, x, y, z, name, props=None, nbt=None):
        if 0 <= x < self.W and 0 <= y < self.H and 0 <= z < self.W:
            self.blocks[(x, y, z)] = ("minecraft:" + name, props or {}, nbt)

    def get(self, x, y, z):
        b = self.blocks.get((x, y, z))
        return b[0][10:] if b else None

    # -- the floating island
    def island(self):
        c, S = self.c, self.S
        p1, p2 = self.rng.uniform(0, 6.28), self.rng.uniform(0, 6.28)
        for x in range(self.W):
            for z in range(self.W):
                dx, dz = x - c, z - c
                d = math.hypot(dx, dz)
                th = math.atan2(dz, dx)
                r = c - 2.5 + 1.4 * math.sin(3 * th + p1) + 1.0 * math.cos(5 * th + p2)
                if d > r:
                    continue
                self.footprint.add((x, z))
                f = 1 - (d / r) ** 1.6
                depth = int(2 + (S - 2) * f + self.rng.uniform(-1.2, 1.2))
                depth = max(1, min(S, depth))
                for y in range(S - depth, S + 1):
                    self.set(x, y, z, "end_stone")
                for y in range(S + 1, self.H):
                    self.set(x, y, z, "air")

    def on_island(self, x, z, margin=0):
        return all((x + a, z + b) in self.footprint
                   for a in range(-margin, margin + 1) for b in range(-margin, margin + 1))

    # -- paths
    def path(self, x0, z0, x1, z1):
        n = max(abs(x1 - x0), abs(z1 - z0), 1)
        for i in range(n + 1):
            x = round(x0 + (x1 - x0) * i / n)
            z = round(z0 + (z1 - z0) * i / n)
            for ox, oz in ((0, 0), (1, 0), (0, 1)):
                if self.get(x + ox, self.S, z + oz) == "end_stone":
                    self.set(x + ox, self.S, z + oz, "end_stone_bricks")

    def lamp(self, x, z):
        S = self.S
        self.set(x, S, z, "end_stone_bricks")
        self.set(x, S + 1, z, "purpur_pillar", {"axis": "y"})
        self.set(x, S + 2, z, "purpur_pillar", {"axis": "y"})
        self.set(x, S + 3, z, "end_rod", {"facing": "up"})

    # -- central plaza
    def plaza(self):
        c, S = self.c, self.S
        for x in range(c - 4, c + 5):
            for z in range(c - 4, c + 5):
                if math.hypot(x - c, z - c) <= 4.3:
                    self.set(x, S, z, "purpur_block" if (x + z) % 2 == 0 else "end_stone_bricks")
        for y in range(S + 1, S + 5):
            self.set(c, y, c, "purpur_pillar", {"axis": "y"})
        self.set(c, S + 5, c, "end_rod", {"facing": "up"})
        for dx, dz, f in ((1, 0, "east"), (-1, 0, "west"), (0, 1, "south"), (0, -1, "north")):
            self.set(c + dx, S + 1, c + dz, "purpur_stairs",
                     {"facing": OPPOSITE[f], "half": "bottom", "shape": "straight", "waterlogged": "false"})
            self.set(c + dx, S + 4, c + dz, "end_rod", {"facing": f})
        for dx, dz in ((3, 3), (-3, 3), (3, -3), (-3, -3)):
            self.lamp(c + dx, c + dz)

    # -- a house with a Shulker Villager inside
    def house(self, cx, cz, profession):
        S = self.S
        c = self.c
        dx, dz = c - cx, c - cz
        if abs(dx) > abs(dz):
            door = "east" if dx > 0 else "west"
        else:
            door = "south" if dz > 0 else "north"
        sx, sz = STEP[door]          # towards the door
        lx, lz = -sz, sx             # sideways
        x0, x1, z0, z1 = cx - 3, cx + 3, cz - 3, cz + 3

        # a little end stone pad so the house never hangs over the edge
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                for y in range(S - 2, S + 1):
                    if self.get(x, y, z) is None:
                        self.set(x, y, z, "end_stone")
                for y in range(S + 1, self.H):
                    self.set(x, y, z, "air")
                self.footprint.add((x, z))

        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge_x = x in (x0, x1)
                edge_z = z in (z0, z1)
                self.set(x, S, z, "end_stone_bricks")
                for y in range(S + 1, S + 5):
                    if edge_x and edge_z:
                        self.set(x, y, z, "purpur_pillar", {"axis": "y"})
                    elif edge_x or edge_z:
                        mid = (x == cx or z == cz)
                        if mid and y in (S + 2, S + 3):
                            self.set(x, y, z, "purple_stained_glass")
                        else:
                            self.set(x, y, z, "purpur_block")
                self.set(x, S + 5, z, "purpur_block" if (edge_x or edge_z) else "purpur_slab",
                         None if (edge_x or edge_z) else {"type": "bottom", "waterlogged": "false"})

        # roof: stair rim + slab cap + light
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                if x0 <= x <= x1 and z0 <= z <= z1:
                    continue
                if x < x0: f = "east"
                elif x > x1: f = "west"
                elif z < z0: f = "south"
                else: f = "north"
                self.set(x, S + 5, z, "purpur_stairs",
                         {"facing": f, "half": "bottom", "shape": "straight", "waterlogged": "false"})
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                self.set(x, S + 6, z, "purpur_slab", {"type": "bottom", "waterlogged": "false"})
        self.set(cx, S + 6, cz, "purpur_block")
        self.set(cx, S + 7, cz, "end_rod", {"facing": "up"})
        self.set(cx, S + 4, cz, "end_rod", {"facing": "down"})

        # doorway
        doorx, doorz = cx + 3 * sx, cz + 3 * sz
        self.set(doorx, S + 1, doorz, "air")
        self.set(doorx, S + 2, doorz, "air")
        self.set(doorx + sx, S, doorz + sz, "end_stone_bricks")
        front = (doorx + sx, doorz + sz)

        # the villager sits at the back, facing the door
        bx, bz = cx - 2 * sx, cz - 2 * sz
        color = PROFESSION_COLOR[profession]
        self.entities.append({
            "pos": DoubleList([bx + 0.5, S + 1.0, bz + 0.5]),
            "blockPos": IntList([bx, S + 1, bz]),
            "nbt": {
                "id": "endvillage:shulker_villager",
                "Profession": profession,
                "Color": Byte(COLOR_IDS[color]),
                "AttachFace": Byte(0),
                "Peek": Byte(0),
                "PersistenceRequired": Byte(1),
            },
        })

        # job blocks either side of the villager, a chest by the door
        left = (bx + lx * 2, bz + lz * 2)
        right = (bx - lx * 2, bz - lz * 2)
        face_door = door
        stations = {
            "chorus_farmer": [("composter", {"level": "3"}), ("barrel", {"facing": "up", "open": "false"})],
            "purpur_mason": [("stonecutter", {"facing": face_door}),
                             ("purpur_stairs", {"facing": OPPOSITE[face_door], "half": "bottom",
                                                "shape": "straight", "waterlogged": "false"})],
            "void_librarian": [("lectern", {"facing": face_door, "has_book": "false", "powered": "false"}),
                               ("bookshelf", None)],
            "shell_keeper": [("smithing_table", None), ("anvil", {"facing": "north"})],
            "star_cartographer": [("cartography_table", None), ("barrel", {"facing": "up", "open": "false"})],
            "dragon_cleric": [("brewing_stand", {"has_bottle_0": "false", "has_bottle_1": "false",
                                                 "has_bottle_2": "false"}),
                              ("cauldron", None)],
        }[profession]
        self.set(left[0], S + 1, left[1], stations[0][0], stations[0][1])
        self.set(right[0], S + 1, right[1], stations[1][0], stations[1][1])
        if profession == "void_librarian":
            for k in (-1, 0, 1):
                for side in (1, -1):
                    wx = cx + side * lx * 2 + k * sx
                    wz = cz + side * lz * 2 + k * sz
                    if (wx, wz) not in (left, right):
                        self.set(wx, S + 1, wz, "bookshelf")
        chest = (cx + 2 * sx + 2 * lx, cz + 2 * sz + 2 * lz)
        self.set(chest[0], S + 1, chest[1], "chest",
                 {"facing": OPPOSITE[door], "type": "single", "waterlogged": "false"},
                 {"id": "minecraft:chest", "LootTable": "endvillage:chests/end_village_house"})

        if profession == "chorus_farmer":
            # a chorus garden next to the house
            for k in range(-2, 3, 2):
                gx = cx + lx * 5 + k * sx
                gz = cz + lz * 5 + k * sz
                if self.on_island(gx, gz):
                    self.set(gx, S, gz, "end_stone")
                    self.set(gx, S + 1, gz, "chorus_flower", {"age": "0"})
        return front

    def scatter_chorus(self, count):
        c, S = self.c, self.S
        spots = [p for p in self.footprint
                 if math.hypot(p[0] - c, p[1] - c) > c - 6 and self.get(p[0], S, p[1]) == "end_stone"
                 and self.get(p[0], S + 1, p[1]) == "air"]
        self.rng.shuffle(spots)
        placed = 0
        for x, z in spots:
            if placed >= count:
                break
            if any(self.get(x + a, S + 1, z + b) not in ("air", None) for a in (-1, 0, 1) for b in (-1, 0, 1)):
                continue
            self.set(x, S + 1, z, "chorus_plant",
                     {"down": "true", "up": "true", "north": "false", "south": "false",
                      "east": "false", "west": "false"})
            self.set(x, S + 2, z, "chorus_flower", {"age": "5"})
            placed += 1

    def build(self, professions, radius):
        c = self.c
        self.island()
        self.plaza()
        n = len(professions)
        start = self.rng.uniform(0, math.pi * 2 / n)
        fronts = []
        for i, prof in enumerate(professions):
            a = start + i * 2 * math.pi / n
            hx = round(c + radius * math.cos(a))
            hz = round(c + radius * math.sin(a))
            fronts.append((self.house(hx, hz, prof), a))
        for (fx, fz), a in fronts:
            px = round(c + 4 * math.cos(a))
            pz = round(c + 4 * math.sin(a))
            self.path(px, pz, fx, fz)
            mx, mz = (px + fx) // 2, (pz + fz) // 2
            lx, lz = round(mx - 2 * math.sin(a)), round(mz + 2 * math.cos(a))
            if self.get(lx, self.S + 1, lz) == "air" and self.on_island(lx, lz, 1):
                self.lamp(lx, lz)
        self.scatter_chorus(6)

    def to_nbt(self):
        palette, index, blocks = [], {}, []
        for (x, y, z), (name, props, nbt) in sorted(self.blocks.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
            key = (name, tuple(sorted(props.items())))
            if key not in index:
                index[key] = len(palette)
                entry = {"Name": name}
                if props:
                    entry["Properties"] = {k: str(v) for k, v in props.items()}
                palette.append(entry)
            b = {"pos": IntList([x, y, z]), "state": Int(index[key])}
            if nbt:
                b["nbt"] = nbt
            blocks.append(b)
        return {
            "DataVersion": Int(DATA_VERSION),
            "size": IntList([self.W, self.H, self.W]),
            "palette": CompoundList(palette),
            "blocks": CompoundList(blocks),
            "entities": CompoundList(self.entities),
        }


def main():
    root = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "data",
                        "endvillage", "structures", "end_village")
    large = Village(size=47, height=30, surface=14, seed=2026)
    large.build(["chorus_farmer", "purpur_mason", "void_librarian",
                 "shell_keeper", "star_cartographer", "dragon_cleric"], radius=13)
    write_nbt(os.path.join(root, "large.nbt"), large.to_nbt())

    small = Village(size=37, height=30, surface=14, seed=77)
    small.build(["chorus_farmer", "void_librarian", "star_cartographer", "dragon_cleric"], radius=10)
    write_nbt(os.path.join(root, "small.nbt"), small.to_nbt())

    for name, v in (("large", large), ("small", small)):
        solid = sum(1 for b in v.blocks.values() if b[0] != "minecraft:air")
        print(f"{name}: {v.W}x{v.H}x{v.W}, {solid} blocks, {len(v.entities)} shulker villagers")


if __name__ == "__main__":
    main()
