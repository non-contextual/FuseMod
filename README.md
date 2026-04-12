# FuseMod

> Zelda: Tears of the Kingdom's Fuse mechanic, brought to Minecraft 1.21.11.

Attach materials to your weapons, tools, and shields to gain real combat and mining bonuses. Unfuse anytime to get your material back.

---

## How It Works

**Fuse** — Hold a fuseable material in your off-hand, then **Sneak + Right-Click** while holding a compatible weapon, tool, or shield in your main hand.

**Unfuse** — **Sneak + Right-Click** again with the fused item in your main hand (off-hand can be empty). The material is returned to your inventory, or dropped at your feet if your inventory is full.

Each item can only hold one fused material at a time. Fusing replaces any existing fusion — unfuse first if you want to swap.

---

## Supported Equipment

| Equipment Type | Effect |
|---|---|
| Swords, Axes, Tridents | `+X attack damage` on the weapon |
| Pickaxes, Shovels, Hoes | `+X.X mining efficiency` |
| Shields | Visual tag (full effect in v1.2.0) |

Effects are added on top of the item's existing stats, including enchantments and other modifiers. They are fully removed when you unfuse.

---

## Materials (30 total)

### Weapons / Combat

| Material | Attack | Fire | Knockback |
|---|---|---|---|
| Rotten Flesh | +1 | — | — |
| Spider Eye | +1 | — | — |
| String | +1 | — | — |
| Gold Ingot | +1 | — | — |
| Blaze Powder | +2 | 1s | — |
| Copper Ingot | +2 | — | — |
| Flint | +2 | — | — |
| Iron Ingot | +2 | — | — |
| Ghast Tear | +2 | — | — |
| Chorus Fruit | +2 | — | kb+2 |
| Rabbit Foot | +2 | — | kb+1 |
| Slime Ball | +1 | — | kb+2 |
| Prismarine Crystals | +2 | — | — |
| Magma Cream | +3 | 1.5s | — |
| Magma Block | +3 | 1s | — |
| Prismarine Shard | +3 | — | — |
| Amethyst Shard | +3 | — | — |
| Emerald | +3 | — | kb+1 |
| Ender Pearl | +3 | — | kb+2 |
| Bone | +4 | — | — |
| Blaze Rod | +4 | 2s | — |
| Lightning Rod | +4 | — | — |
| Shulker Shell | +4 | — | kb+1 |
| Dragon Breath | +5 | 1s | — |
| Echo Shard | +5 | — | kb+1 |
| Diamond | +5 | — | — |
| Obsidian | +6 | — | — |
| Wither Skeleton Skull | +7 | — | — |
| Netherite Ingot | +8 | — | — |
| Nether Star | +10 | — | kb+3 |

### Mining Efficiency Bonus (on tools)

| Material | Speed Bonus |
|---|---|
| String | +1.0 |
| Gold Ingot | +1.0 |
| Bone | +1.0 |
| Rabbit Foot | +1.5 |
| Iron Ingot | +2.0 |
| Flint | +2.0 |
| Copper Ingot | +2.0 |
| Prismarine Crystals | +2.0 |
| Emerald | +2.0 |
| Ghast Tear | +2.5 |
| Prismarine Shard | +3.0 |
| Lightning Rod | +3.0 |
| Amethyst Shard | +3.0 |
| Diamond | +4.0 |
| Echo Shard | +4.0 |
| Netherite Ingot | +6.0 |
| Nether Star | +8.0 |

Materials not listed in the mining table have no mining bonus (e.g., Blaze Rod gives fire but no speed).

---

## Tooltip

Fused items show a **Fused: `<material>` [bonus]** line in gold/yellow, plus a reminder of how to unfuse.

Unfused compatible items show a **"Hold material in off-hand, sneak + right-click to fuse"** hint in dark gray.

---

## Roadmap

**v1.2.0 — Visual Fusion**
- Fused items get a visual model overlay (the material appears attached to the weapon/tool)
- Shield fusion receives actual damage-reduction effects

---

## Requirements

- Minecraft **1.21.11**
- [Fabric Loader](https://fabricmc.net/) **0.18.6+**
- [Fabric API](https://modrinth.com/mod/fabric-api) **0.141.3+1.21.11**

---

## Building from Source

```bash
git clone https://github.com/milazy/fusemod
cd fusemod
./gradlew build
```

Output JAR is at `build/libs/fusemod-1.0.0.jar`.
