# Third-Party Notices

## SylviaX-390/createbuttercat

This repository contains material adapted from or derived from:

- Repository: `SylviaX-390/createbuttercat`
- Branches used for integration: `1.20.1-forge`, `1.21.1-neoforge`
- Current 1.21.1 reference copy in this repository: `ref/1.21.1/createbuttercat/`

The adapted portions may include code, assets, data files, localization, recipes, and related resources. Later edits to those adapted portions remain subject to the upstream MIT notice requirement.

Upstream license text:

MIT License

Copyright (c) 2026 SylviaX-390

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## L_Ender's Cataclysm

Some bionic-slime animation parameters and timing curves are adapted from the
source code of L_Ender's Cataclysm:

- Repository: [`lender544/new1.20.1`](https://github.com/lender544/new1.20.1)
- Reference branch and revision: [`1.21` at
  `fe6d06e79d98fcbf22fbd8aee153c65a1d9eb3cd`](https://github.com/lender544/new1.20.1/tree/fe6d06e79d98fcbf22fbd8aee153c65a1d9eb3cd)
- Upstream source files consulted:
  - `Maledictus_Animation.java`
  - `Maledictus_Model.java`
  - `Deepling_Brute_Model.java`
  - `Ender_Golem_Model.java`
- Adapted Create: Biotech code:
  - `SlimeBionicAnimations.java`
  - `SlimeBionicAttackAnimations.java`
  - `SlimeBionicAttackTiming.java`

The adapted data has been reduced to the relevant body and limb rotation
channels, retimed for Create: Biotech's combat cadence, mirrored where needed,
and renamed around its role in the local animation system. No Cataclysm models,
textures, sounds, localization, or other assets are redistributed.
These adaptations and modifications were made for Create: Biotech and were
last materially revised on 2026-09-04.

The Cataclysm `1.21` branch [declares its source code under the GNU Lesser
General Public License version 3.0 and reserves all rights to its
assets](https://github.com/lender544/new1.20.1/blob/fe6d06e79d98fcbf22fbd8aee153c65a1d9eb3cd/gradle.properties#L42).
The Cataclysm-derived animation parameters described above are therefore
provided under the GNU LGPL version 3.0 only. Copies of the
[GNU LGPL version 3.0](LICENSES/LGPL-3.0-only.txt) and the
[GNU GPL version 3.0](LICENSES/GPL-3.0-only.txt) incorporated by that license
are included in this repository and its distributable jar. Copyright in those
adapted portions remains with L_Ender and the Cataclysm contributors. The rest
of Create: Biotech remains under the licenses described in
[LICENSE.md](LICENSE.md).

## yision1/CreatePhantom

The Allay logistics implementation in this repository references source code from:

- Online repository: [`yision1/CreatePhantom`](https://github.com/yision1/CreatePhantom)
- Upstream version caveat: the referenced upstream code targets Minecraft 1.21.1 / NeoForge and was adapted here for Minecraft 1.21.1 / NeoForge.

Only source code was referenced. No assets, data files, localization, recipes, or other resources from Create Phantom were used. Later edits to code adapted from that reference remain subject to the upstream BSD-3-Clause notice requirement.

Upstream license text:

BSD 3-Clause License

Copyright (c) 2025, Yison
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The upstream Create Phantom README also notes that some sections come from
`timplay33/Create-Mobile-Packages`, which is licensed under the MIT License.
That upstream notice is preserved here for the adapted Allay logistics code path.

MIT License

Copyright (c) 2025 Tim Heidler

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## RyanHCode/sable-companion

The distributable jar embeds Sable Companion 1.6.0 through NeoForge JarJar to
provide an optional compatibility facade for Sable sublevels.

MIT License

Copyright (c) 2026 RyanHCode

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
