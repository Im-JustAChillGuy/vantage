# Vantage (starter scaffold)

Distance/threat-based entity render culling for Fabric 1.21.4.

## Honest heads-up

While scaffolding this I should flag something:
**entity culling mods already exist** — most notably `tr7zw`'s "Entity Culling"
mod, which does occlusion + distance culling and is widely used. So this
isn't literally unprecedented territory. What's still genuinely underexplored
is the **GPU compute-shader LOD/culling pipeline** we talked about (#3) — that
part nobody's shipped in a stable, public MC mod. This scaffold gives you the
CPU-side distance/threat culling skeleton as a working foundation; the GPU
compute pass is the actual novel part and is a much bigger lift (needs
hooking Sodium/Iris's render graph, which isn't a stable public API).

## What's here

- `EntityCullingManager` — pure logic, no rendering code, easy to unit test.
  Currently: full detail close up, simplified/skip-anim mid-range, full skip
  far away — with threatening mobs (attacking/targeting you) exempted from
  being culled so nothing "pops in" unfairly.
- `EntityRendererMixin` — injects into `EntityRenderer#shouldRender` to
  cancel rendering for SKIP-tier entities.


## Entity Culling compatibility

Most users already have tr7zw's "Entity Culling" mod, which does the same
`shouldRender`-level distance culling. Rather than fight over the same
injection point, `VantageMixinPlugin` checks at load time whether
`entityculling` is present on the classpath and, if so, **disables our
render-cull mixin entirely** — Entity Culling keeps doing its job, and our
mod steps back instead of double-injecting or silently overriding it.

Practical effect: if a user has Entity Culling installed, this mod currently
contributes nothing on its own (by design — no conflict, no duplicate work).
Its actual value only shows up once you build out the pieces Entity Culling
doesn't do: the SIMPLIFIED-tier animation reduction and the GPU compute LOD
pass. Worth keeping in mind — as it stands, most users won't get a
measurable difference from installing this over what they already have.

## Known gaps / next steps

1. `shouldRender`'s exact signature can shift between Yarn mapping builds —
   double check against the mappings you resolve before your first build.
2. SIMPLIFIED tier currently does nothing extra yet — wire it into specific
   `LivingEntityRenderer` subclasses to skip limb-swing/animation calculation,
   not just full renders. This is where real differentiation from Entity
   Culling would come from.
3. No tick/AI-side optimization yet — this only saves render-thread cost.
4. **GPU compute LOD pass — scaffolded, UNTESTED.** See
   `GpuLodManager` and `assets/vantage/shaders/compute/entity_lod.comp`.
   This is real compute-shader code following the correct LWJGL 4.3 pattern
   (SSBO upload -> dispatch -> memory barrier -> readback), but it has not
   been run against the actual Minecraft render thread. Before trusting it:
   - Verify it's actually called on the render thread (GL calls off-thread
     will crash or silently no-op)
   - Add a GL debug callback and check `entity_lod.comp` output against a
     known test case (e.g. one entity, hand-computed expected tier)
   - The readback (`glMapBufferRange` with `GL_MAP_READ_BIT`) causes a
     CPU-GPU sync stall — profile whether this actually nets a *win* over
     the CPU path before shipping it. For entity counts this small (hundreds,
     not millions), it's genuinely possible the GPU round-trip costs more
     than it saves. This is the open research question — don't assume it's
     faster, measure it.
   - No indirect-draw integration yet — results are read back to CPU and
     would still need wiring into the render mixin's decision, not yet done
   - No `EntityData` packing code yet (Java side needs to gather entity
     positions/radii into the float array `dispatch()` expects — not
     implemented)
