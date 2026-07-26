# Vantage

A entity rendering optimization mod for [Fabric](https://fabricmc.net). Vantage reduces render load
from distant or off-screen entities using distance and frustum based
culling, with a GPU compute-shader LOD pass.

## Features

- **LOD system** — Entities get classified into three tiers -  FULL, SIMPLIFIED or SKIP based on distance and camera frustum. For more information, visit the [Features Wiki](https://github.com/Im-JustAChillGuy/vantage/wiki/features) and for developers, click the link on this page or click [here](https://github.com/Im-JustAChillGuy/vantage/wiki).
- **Threat-aware** — [Mobs attacking or targeting you](https://cdn.modrinth.com/data/cached_images/570214fd1e5b160549ddb449e0fbd00097ab121a_0.webp) are exempt
  from culling.
- **GPU compute LOD pass (experimental)** — on supported hardware
  (OpenGL 4.3+), entity visibility and LOD tier are computed on the GPU
  once per tick instead of the CPU. Falls back if unsupported. This feature is NOT compatible with [VulkanMod](https://modrinth.com/mod/vulkanmod).
- **Entity Culling compatible** — Vantage detects tr7zw's [Entity Culling](https://modrinth.com/mod/entityculling) mod at load time and steps back automatically.


<details>
<summary>Compatibility/Requirements</summary>

## Compatibility

- Fabric 
- 1.20 - 1.21.11
- Works alongside [Sodium](https://modrinth.com/mod/sodium) and [Iris](https://modrinth.com/mod/iris)
- Detects and responds to [Entity Culling](https://modrinth.com/mod/entityculling)


</details>



<details>
<summary>Limitations</summary>

## Known limitations

This is an early release. The GPU LOD pass in particular is newer and
less tested than the CPU path — if you notice entities behaving
oddly, reporting it helps a LOT.

Feedback and bug reports are welcome. Report them on my [Discord server](https://discord.gg/mfZczRhVqf) or the [Github issues page](https://github.com/Im-JustAChillGuy/vantage/issues)


**Note on the 1.20 build:** This version includes all the features, but does not include animation reducer for SIMPLIFIED-tier entities — that feature relies on a render-state hook that doesn't exist yet in 1.20's rendering pipeline.Only fully culled (SKIP-tier) entities see a performance benefit. Full feature parity, including animation reduction, is available on the 1.21 build.

</details>


<details>
<summary>Licence</summary>

## License

Vantage is released under the [MIT License](https://opensource.org/license/mit). You're free to use, copy, modify, merge, publish, distribute, and include it in commercial projects, as long as the original copyright notice stays attached.The software is provided as-is. The full license text is also included in the [repository](https://github.com/Im-JustAChillGuy/vantage).
</details>



![A banner with the word "Vantage" written in blue](https://cdn.modrinth.com/data/cached_images/2ba5000e792fc8e27db7b039be5013711c82d51a.png)

Copyright © 2026, ChillGuy.
