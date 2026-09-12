# Surgical Table and Bionic Creature Player Guide

[English](surgery-and-bionic-creatures.md) · [Chinese](surgery-and-bionic-creatures.zh-CN.md)

This feature is unfinished, and future releases will change it in ways that are not compatible with older versions, so playing with it in a long-term survival world is **not recommended**. It is a fairly large system whose Ponder scenes are not finished yet; this guide covers how it actually plays.

You can use the Surgical Table to take Slime Mimic Creatures apart and reassemble them. Parts can be rearranged and mixed with parts from other creatures; once joints are fitted, the head, arms and legs can move. Pack the finished body into a box and release it to get the bionic creature "Slime?".

How the result performs depends on which parts you keep, how you assemble them, and where the joints go. Some donors have properties of their own (unfinished).

This system may conflict with client-side mods that modify entity models on their own.

## Quick Start

1. Grow a Mimic Creature with a **Petri Dish**; see the Petri Dish's Ponder scene for details.
2. Capture the Mimic Creature in a Cardboard Box.
3. Lay out Surgical Tables. Tables that are horizontally adjacent and at the same height join into a single work surface, and a complex body needs a large one.
4. Place the box holding the Mimic Creature on the surface to turn it into parts. This cannot be undone.
5. Cut, arrange and glue the parts you need, then fit the joints.
6. Pack the whole body with an empty Cardboard Box, hold `Alt` to inspect its stats, and sneak-right-click to release the result.
7. A finished "Slime?" can also go back on the Surgical Table for further work.

## Controls

- While holding the Surgical Kit, hold the kit key (`Alt` by default), move the cursor onto the tool you want, and release the key to switch to it.
- Tools inside the kit only work on a Surgical Table; the matching standalone tools and materials can be used directly as well.
- Aim at a part, seam, joint or tabletop and the screen shows which actions are currently available and which keys they need.
- Powering any Surgical Table of a connected work surface with redstone temporarily shows each part's original creature appearance, which helps identify where it came from; it does not change the assembled result.

## The Tools

> For a tool's exact keys, selection order and current step, follow the on-screen prompt while your crosshair is on a target. The table below only describes what each tool is for.

| Tool | What it does |
| --- |--- |
| Shears | Cut a creature's original seams or the glue joins you added later; also dissolves or detaches a fused combination as a whole. |
| Shovel | Discard the whole connected part you are aiming at and recover some slime balls; can also clear every part intersecting one tabletop. |
| Super Glue / Smart Super Glue | Bond the selected positions of two parts together. Smart Super Glue also lets you nudge the position and tilt before confirming. |
| Honey Bottle | Fuse the entire connected part you are aiming at into one rigid combination, which is handy for building a torso, an arm or a leg segment first. Cutting and joint calculations treat that combination as a single part. |
| Slime Ball | Add a glue seam between two cubes that already touch but are not yet directly connected. |
| Wand of Symmetry | Mirror an existing part across the centre face you pick, following the glue relations already there, and glue it in place; it does not conjure up new parts. |
| Wrench | Remove the joint you are aiming at and recover the joint item. |
| Temporary Cardboard Box | Hold one whole connected part aside, to reposition it or move it to another Surgical Table. |
| Empty Cardboard Box | Pack the whole connected part you are aiming at as a finished creature. Anything left unattached stays on the table. |

Cutting, gluing and honey-fusing normally cost no materials and no kit durability; a server owner can change that rule. Fitting a joint consumes the joint item, which you get back when you remove it.

Joints can be fitted to make parts move. Joints decide which parts count as a head, an arm or a leg, and give them the matching abilities and animation.

| Joint | Effect and limits |
| --- |--- |
| Neck Joint | Turns the head to follow where the body looks; one body recognises at most three. |
| Shoulder Joint | Marks the moving part as an arm so it joins in on attacks; at most eight. |
| Elbow Joint | Bends the forearm along with the upper arm; it can attach to exactly one shoulder joint. |
| Hip Joint | Marks the moving part as a leg so it joins the gait; at most eight. |
| Knee Joint | Bends the lower leg through each stride; it can attach to exactly one hip joint. |

A first-level joint (neck, shoulder, hip) cannot be chained below another first-level joint, and one moving part cannot be driven by more than one joint. A joint only fits between two parts that are directly connected but not honey-fused into the same combination.

Joints adjust their animation to the shape you actually built.

## How the Finished Stats Are Decided

Maximum health follows the final model's non-overlapping volume, with diminishing gains as the body grows. Movement and attacks follow the final shape and working joints; donor stats are not inherited.

This part is still unfinished; suggestions are welcome in the comments on the mod and the videos.

### Reading the Stats

With the bionic creature packed into a box, hold `Alt` to expand "Base Values". It lists maximum health, damage (DPS), movement speed, armour, knockback-related values, and how many heads, arms and legs are working. DPS assumes normal intelligence and all arms available, excluding weapons, enchantments, status effects and the target's position.

Movement speed is affected by whether the legs reach the ground, how long they are, whether they have knees, and how much of the whole body they make up. Melee reach follows the arm's static shoulder-to-tip length. Arm volume and thickness determine base damage and cadence; additional arms that can engage the current target provide a limited cadence benefit.

### Melee Direction and Cadence

A creature turns toward its target before choosing a ready arm. Its attack covers 35° to either side and 45° above or below its aim, intersected with the arm's actual reach and mounted activity range, and clamped to the body's forward 180°. Tall targets do not widen the attack. Aim locks after preparation, allowing targets to evade to the side, behind the attacker, or out of reach. Bodies without arm geometry use a short body strike capped at 1.5 blocks.

Mounted posture specializes coverage: hanging arms allow 70° down and 35° up, level arms allow 50° each way, and raised arms allow 35° down and 80° up. Sideways arms favor their own side; diagonal mounts interpolate continuously. Arm selection considers these preferences, while attack intervals and recovery receive no posture modifier and coordination keeps its previous eligibility rules. Newly packed creatures save their mounted directions; older creatures without that data retain the generic range.

Each swing prepares for 3–6 ticks, then checks contact for 2 ticks and attempts damage at most once. Arms recover separately, while the whole body waits at least 12 ticks (0.6 seconds) between swings. Extra eligible arms shorten the global interval by at most 20%. Misses and blocked hits still consume recovery; switching targets does not reset it.

Simple, normal and advanced intelligence apply interval factors of 1.1, 1.0 and 0.9, with small differences in turning and preparation-time aim correction. A single standard zombie arm therefore attacks every 22, 20 or 18 ticks. Intelligence grants no extra reach or damage. Multiple recognized heads use their highest tier; no recognized head defaults to simple intelligence.

The server controls contact and recovery independently of animation. Animation curves, playback duration and the visible hand path cannot change reach or damage timing.
