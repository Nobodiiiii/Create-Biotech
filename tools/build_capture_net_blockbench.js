(function () {
    const root = globalThis.captureNetProjectRoot;
    if (!root) throw new Error('Set captureNetProjectRoot to the repository path before running this script');
    const fs = require('fs');
    if (Project.name !== 'capture_net') {
        throw new Error('Run in the capture_net Generic Model project');
    }
    const created = [...Outliner.elements], textures = [...Texture.all], groups = [...Group.all], animations = [...Animation.all];
    Undo.initEdit({elements: created, textures, groups, outliner: true, animations});
    Animation.all.slice().forEach(a => a.remove(false));
    Outliner.root.slice().forEach(g => g.remove(false));
    Texture.all.slice().forEach(t => t.remove(true));
    created.length = textures.length = groups.length = animations.length = 0;
    Project.texture_width = 64;
    Project.texture_height = 64;
    Project.box_uv = false;
    const atlas = new Texture({name: 'iron.png', uv_width: 64, uv_height: 64})
        .fromDataURL('data:image/png;base64,' + fs.readFileSync(root + '/src/main/resources/assets/create_biotech/textures/item/capture_net/iron.png').toString('base64')).add(false);
    const mesh = new Texture({name: 'mesh.png', uv_width: 16, uv_height: 16})
        .fromDataURL('data:image/png;base64,' + fs.readFileSync(root + '/src/main/resources/assets/create_biotech/textures/item/capture_net/mesh.png').toString('base64')).add(false);
    textures.push(atlas, mesh);
    function group(name, origin, parent) {
        const g = new Group({name, origin}).addTo(parent).init();
        groups.push(g);
        return g;
    }
    const grip = group('grip', [8, -3, 8]);
    const frame = group('frame', [8, -3, 8], grip);
    const bag = group('bag', [8, 14, 9], grip);
    function cube(name, from, to, material, parent, rotation = [0, 0, 0], origin = [8, 8, 8]) {
        const c = new Cube({name, from, to, rotation, origin, autouv: 0, box_uv: false}).addTo(parent).init();
        created.push(c);
        const [x, y, z] = to.map((v, i) => v - from[i]);
        const sizes = {north: [x, y], south: [x, y], east: [z, y], west: [z, y], up: [x, z], down: [x, z]};
        Object.entries(sizes).forEach(([face, [w, h]]) => {
            c.faces[face].texture = material === 'mesh' ? mesh.uuid : atlas.uuid;
            c.faces[face].uv = [0, 0, w, h];
        });
        return c;
    }
    const f = (n, a, b, m = 'material') => cube(n, a, b, m, frame);
    f('wooden_handle', [7, -8, 7], [9, 5, 9], 'wood');
    f('simple_metal_socket', [7, 5, 7], [9, 8, 9]);
    f('square_hoop_top', [2, 19, 7], [14, 20, 9]);
    f('square_hoop_bottom', [2, 8, 7], [14, 9, 9]);
    f('square_hoop_left', [2, 9, 7], [3, 19, 9]);
    f('square_hoop_right', [13, 9, 7], [14, 19, 9]);
    const net = cube('single_vertical_net_pouch', [3, 9, 9], [13, 19, 15], 'mesh', bag);
    // This is one cuboid. Only its outside faces carry the transparent weave.
    // Stop at the frame's lower edge; the front stays open.
    net.faces.north.texture = null;
    net.faces.west.uv = [6, 0, 0, 10];
    net.faces.up.uv = [0, 6, 10, 0];
    // Independent native-resolution islands, with a one-texel gutter on every edge.
    const islands = frame.children.flatMap(c => Object.entries(c.faces).map(([direction, face]) => ({
        face, key: c.name + '/' + direction, w: Math.abs(face.uv[2] - face.uv[0]), h: Math.abs(face.uv[3] - face.uv[1])
    }))).sort((a, b) => b.h - a.h || b.w - a.w || a.key.localeCompare(b.key));
    let u = 1, v = 1, rowHeight = 0;
    islands.forEach(({face, w, h}) => {
        if (u + w > 63) { u = 1; v += rowHeight + 2; rowHeight = 0; }
        if (v + h > 63) throw new Error('Frame UV islands exceed the 64x64 atlas');
        face.uv = [u, v, u + w, v + h];
        u += w + 2;
        rowHeight = Math.max(rowHeight, h);
    });
    function clip(name, length, loop, tracks) {
        const a = new Animation({name, length, loop, snapping: 100}).add();
        animations.push(a);
        for (const [boneName, channels] of Object.entries(tracks)) {
            const animator = a.getBoneAnimator(groups.find(g => g.name === boneName));
            for (const [channel, keys] of Object.entries(channels)) {
                keys.forEach(([time, x, y, z]) => animator.addKeyframe({channel, time,
                    interpolation: 'linear', data_points: [{x, y, z}]}));
            }
        }
        return a;
    }
    clip('capture', 0.6, 'once', {
        grip: {rotation: [[0,0,0,0],[0.1,20,-8,-8],[0.16,24,-10,-10],[0.24,-68,16,12],[0.32,-48,10,8],[0.46,8,-3,-3],[0.6,0,0,0]],
            position: [[0,0,0,0],[0.16,0,-1,1],[0.24,0,1,-3],[0.32,0,1,-2],[0.46,0,0,0],[0.6,0,0,0]]}
    });
    clip('release', 0.6, 'once', {
        grip: {rotation: [[0,0,0,0],[0.12,-16,6,5],[0.2,-20,8,6],[0.3,52,-12,-8],[0.38,35,-8,-5],[0.5,-7,2,2],[0.6,0,0,0]],
            position: [[0,0,0,0],[0.2,0,1,1],[0.3,0,-1,-2],[0.38,0,-1,-1],[0.5,0,0,0],[0.6,0,0,0]]}
    });
    clip('occupied', 2, 'loop', {
        grip: {rotation: [[0,0,0,0],[0.28,0,0,0],[0.36,1.2,0.3,0.6],[0.43,-1.5,-0.4,-0.8],
            [0.50,0.9,0.2,0.5],[0.59,-0.4,0,-0.2],[0.70,0,0,0],[1.22,0,0,0],
            [1.32,-0.8,-0.3,0.5],[1.39,1.3,0.4,-0.7],[1.47,-0.7,-0.2,0.4],[1.57,0.3,0,-0.1],
            [1.70,0,0,0],[2,0,0,0]]}
    });
    Undo.finishEdit('Rebuild square capture net from the supplied reference');
    Canvas.updateAll();
    Project.save_path = root + '/art/capture_net.bbmodel';
    fs.writeFileSync(Project.save_path, Codecs.project.compile());
    Project.saved = true;
    return {cubes: created.length, groups: groups.map(g => g.name), animations: animations.map(a => a.name), path: Project.save_path};
})()
