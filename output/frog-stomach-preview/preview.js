(() => {
  'use strict';
  const root = document.getElementById('frog-room-preview');
  const stage = root.querySelector('.frog-stage');
  const status = root.querySelector('.frog-status');
  try {
    const T = window.THREE;
    if (!T) throw new Error('Three.js 未加载');
    const W=48, H=32, D=48, N=W*H*D;
    const AIR=0,WALL=1,MUCOSA=2,FOLD=3,STEM=4,CAP=5,GILL=6,LIGHT=7,SECRETION=8,WATER=9,FRAME=10,PORTAL=11;
    const grid=new Uint8Array(N);
    const cell=(x,y,z)=>x+W*(z+D*y);
    const valid=(x,y,z)=>x>=0&&x<W&&y>=0&&y<H&&z>=0&&z<D;
    const get=(x,y,z)=>valid(x,y,z)?grid[cell(x,y,z)]:0;
    const put=(x,y,z,id)=>{if(valid(x,y,z)) grid[cell(x,y,z)]=id;};
    function box(x,y,z,w,h,d,id,onlyAir=false) {
      for(let a=x;a<x+w;a++)for(let b=y;b<y+h;b++)for(let c=z;c<z+d;c++)
        if(!onlyAir||get(a,b,c)===0)put(a,b,c,id);
    }
    function hash(x,y=0,z=0) { let n=(x*374761393+y*668265263+z*2147483647+711)|0; n=Math.imul(n^(n>>>13),1274126177);return ((n^(n>>>16))>>>0)/4294967296; }
    const surface=new Int8Array(W*D);
    function poolAt(x,z) {
      const a=((x-21)/9.2)**2+((z-27)/10.2)**2;
      const b=((x-27)/5.8)**2+((z-32)/6.7)**2;
      return Math.min(a,b)<1+0.07*Math.sin(x*1.7+z*.4);
    }
    function protectedApproach(x,y,z) {return x>=21&&x<=26&&((z<=6&&y>=21&&y<=26)||(z>=41&&y>=5&&y<=10));}
    // Room bounds and portal coordinates match FrogStomachSpace at the default configuration.
    for(let x=0;x<W;x++)for(let z=0;z<D;z++) {
      put(x,0,z,WALL);
      let h=3+Math.floor((Math.sin(x*.19)+Math.cos(z*.21)+2)*.68);
      if(x>=31&&x<=42&&z>=24&&z<=38)h=5; // usable 12x15 construction shelf
      if(poolAt(x,z))h=2;
      surface[x+W*z]=h;
      for(let y=1;y<=h;y++)put(x,y,z,MUCOSA);
      if(poolAt(x,z))for(let y=h+1;y<=4;y++)put(x,y,z,WATER);
      put(x,H-1,z,WALL);
      if(x>0&&x<W-1&&z>0&&z<D-1) {
        const thick=1+Math.floor((Math.sin(x*.3)+Math.cos(z*.29)+2)*.48);
        for(let y=H-1-thick;y<H-1;y++)put(x,y,z,MUCOSA);
      }
    }
    for(let y=1;y<H;y++) {
      for(let k=0;k<W;k++){put(0,y,k,WALL);put(W-1,y,k,WALL);put(k,y,0,WALL);put(k,y,D-1,WALL);}
      for(let k=1;k<W-1;k++) {
        const n=1+Math.floor((Math.sin(k*.45+y*.09)+1)*.9);
        for(let t=1;t<=n;t++){put(t,y,k,MUCOSA);put(W-1-t,y,k,MUCOSA);put(k,y,t,MUCOSA);put(k,y,D-1-t,MUCOSA);}
      }
    }
    // Direct export of FrogStomachFoldGeometry: NORTH, SOUTH, WEST, EAST.
    const foldMasks=window.FROG_FOLD_DEPTHS;
    for(let wall=0;wall<4;wall++)for(let along=1;along<W-1;along++)
      for(let y=1;y<H-1;y++)for(let depth=1;depth<=foldMasks[wall][along][y];depth++) {
        const x=wall===2?depth:wall===3?W-1-depth:along;
        const z=wall===0?depth:wall===1?D-1-depth:along;
        const id=get(x,y,z);
        if(!protectedApproach(x,y,z)&&(id===AIR||id===MUCOSA||id===FOLD))put(x,y,z,FOLD);
      }
    function ground(x,z) {for(let y=H-4;y>=0;y--){const id=get(x,y,z);if(id&&id!==WATER&&id!==PORTAL)return y+1;}return 1;}
    function floorY(x,z){return surface[Math.floor(x)+W*Math.floor(z)]+1;}
    function mushroom(x,z,height,radius) {
      const base=floorY(x,z),top=base+height;
      for(let y=base;y<=top;y++){
        const shifted=y-base>height*.65;
        put(x+(shifted?1:0),y,z,STEM);
        if(shifted&&y-1-base<=height*.65)put(x,y,z,STEM);
      }
      if(height>7){put(x-1,base,z,STEM);put(x,base,z+1,STEM);}
      for(let dx=-radius;dx<=radius;dx++)for(let dz=-radius;dz<=radius;dz++) {
        if(Math.abs(dx)+Math.abs(dz)>radius*1.6)continue;
        put(x+1+dx,top,z+dz,GILL);put(x+1+dx,top+1,z+dz,CAP);
        if(Math.max(Math.abs(dx),Math.abs(dz))<=radius-2)put(x+1+dx,top+2,z+dz,CAP);
        if(hash(x+dx,top,z+dz)>.95)put(x+1+dx,top,z+dz,LIGHT);
      }
    }
    mushroom(10,15,8,4);mushroom(12,34,6,3);mushroom(30,10,9,4);mushroom(39,18,5,3);
    // Attached fan fungi at intermediate heights, tied to the back and left walls.
    for(const [cx,cy,cz] of [[5,18,25],[5,22,27],[34,20,4]])
      for(let dx=-3;dx<=3;dx++)for(let dz=-3;dz<=3;dz++)
        if(dx*dx+dz*dz<=12&&valid(cx+dx,cy,cz+dz)&&!get(cx+dx,cy,cz+dz)){
          put(cx+dx,cy,cz+dz,GILL);put(cx+dx,cy+1,cz+dz,CAP);
        }
    for(const [cx,cz,r] of [[12,24,2],[29,22,2],[29,37,2],[8,38,2]])
      for(let dx=-r;dx<=r;dx++)for(let dz=-r;dz<=r;dz++)if(dx*dx+dz*dz<=r*r)
        put(cx+dx,floorY(cx+dx,cz+dz),cz+dz,SECRETION);
    const glandCenters=[[8,3,22],[17,3,25],[3,21,22],[35,3,24]];
    for(const [x,z,bottom] of glandCenters) {
      for(let y=bottom;y<29;y++)put(x,y,z,STEM);
      for(let y=bottom;y<=bottom+2;y++) {
        const r=y===bottom?0:1;
        for(let dx=-r;dx<=r;dx++)for(let dz=-r;dz<=r;dz++)
          if(Math.abs(dx)+Math.abs(dz)<=1)put(x+dx,y,z+dz,y===bottom?LIGHT:SECRETION);
      }
    }
    const makePortal=(z,bottom)=>{
      const front=z===1?1:-1;
      for(let depth=1;depth<=5;depth++)box(21,bottom-1,z+front*depth,6,6,1,AIR);
      for(let x=21;x<=26;x++)for(let y=bottom-1;y<=bottom+4;y++)
        put(x,y,z,(x===21||x===26||y===bottom-1||y===bottom+4)?FRAME:PORTAL);
    };
    makePortal(1,22);makePortal(46,6);
    const renderer=new T.WebGLRenderer({antialias:true,alpha:true,powerPreference:'low-power'});
    renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,2));
    renderer.outputColorSpace=T.SRGBColorSpace;
    renderer.setClearColor(0,0);
    renderer.domElement.setAttribute('role','img');
    renderer.domElement.setAttribute('aria-label','48×48×32 格方形胃室，包含黏膜褶皱、四株大型胃菇、浅池、腺体、史莱姆和1.8格人形尺度参照。');
    stage.prepend(renderer.domElement);
    const scene=new T.Scene();
    const camera=new T.PerspectiveCamera(39,1,.1,400);
    scene.add(new T.AmbientLight(0xffffff,1.2));
    const sun=new T.DirectionalLight(0xfff0df,2.1);sun.position.set(25,70,55);scene.add(sun);
    const fill=new T.DirectionalLight(0xe2eaff,.6);fill.position.set(-30,25,-15);scene.add(fill);
    const geometryGroup=new T.Group();scene.add(geometryGroup);
    const detailGroup=new T.Group();scene.add(detailGroup);
    const dimensions=new T.Group();scene.add(dimensions);
    const names={1:'wall',2:'mucosa',3:'fold',4:'stem',5:'cap',6:'gill',7:'light',8:'secretion',9:'water',10:'frame',11:'portal'};
    const textures={};const materials={};
    for(const palette of ['concept','original']) {
      textures[palette]={};materials[palette]={};
      for(const [name,encoded] of Object.entries(window.FROG_TEXTURES[palette])) {
        const binary=atob(encoded),rgba=new Uint8Array(binary.length);
        for(let i=0;i<binary.length;i++)rgba[i]=binary.charCodeAt(i);
        const tex=new T.DataTexture(rgba,16,16,T.RGBAFormat);
        tex.magFilter=T.NearestFilter;tex.minFilter=T.NearestFilter;
        tex.wrapS=T.RepeatWrapping;tex.wrapT=T.RepeatWrapping;tex.generateMipmaps=false;
        tex.colorSpace=T.SRGBColorSpace;tex.flipY=true;tex.needsUpdate=true;
        textures[palette][name]=tex;
        const mat=new T.MeshLambertMaterial({map:tex,vertexColors:true});
        if(name==='light'||name==='portal') {mat.emissive=new T.Color(name==='light'?0xa86e21:0x92472a);mat.emissiveMap=tex;mat.emissiveIntensity=.6;}
        if(name==='water'){mat.transparent=true;mat.opacity=.77;mat.depthWrite=false;}
        if(name==='secretion'){mat.transparent=true;mat.opacity=.92;}
        materials[palette][name]=mat;
      }
    }
    let mode='cut',palette='concept',distance=103,yaw=.76,elevation=.53;
    const target=new T.Vector3(24,13,24);
    const faces=[
      {n:[1,0,0],v:[[1,0,1],[1,0,0],[1,1,0],[1,1,1]],shade:.87},
      {n:[-1,0,0],v:[[0,0,0],[0,0,1],[0,1,1],[0,1,0]],shade:.78},
      {n:[0,1,0],v:[[0,1,1],[1,1,1],[1,1,0],[0,1,0]],shade:1},
      {n:[0,-1,0],v:[[0,0,0],[1,0,0],[1,0,1],[0,0,1]],shade:.74},
      {n:[0,0,1],v:[[0,0,1],[1,0,1],[1,1,1],[0,1,1]],shade:.91},
      {n:[0,0,-1],v:[[1,0,0],[0,0,0],[0,1,0],[1,1,0]],shade:.82}
    ];
    function shown(x,y,z) {
      const id=get(x,y,z);if(!id)return 0;
      if(mode==='inside'||mode==='full')return id;
      if(mode==='top')return y>=H-3?0:id;
      if(id===FRAME||id===PORTAL)return id;
      if((x>=40||z>=40)&&y>Math.max(surface[x+W*z],1)&&(id===WALL||id===MUCOSA||id===FOLD))return 0;
      if(y>=H-3&&x>4&&z>4)return 0;
      return id;
    }
    const transparent=id=>id===WATER||id===SECRETION||id===PORTAL;
    const opaque=(x,y,z)=>{const id=shown(x,y,z);return id!==0&&!transparent(id);};
    function faceMaterial(id,fi){if(id===STEM&&fi===2)return 'stem_top';if(id===FOLD&&(fi===2||fi===3))return 'fold_top';if(id===CAP&&fi===3)return 'gill';return names[id];}
    let voxelCount=0,faceCount=0;
    function rebuild() {
      while(geometryGroup.children.length){const child=geometryGroup.children[0];geometryGroup.remove(child);child.geometry.dispose();}
      const batches={};voxelCount=0;faceCount=0;
      for(let y=0;y<H;y++)for(let z=0;z<D;z++)for(let x=0;x<W;x++) {
        const id=shown(x,y,z);if(!id)continue;voxelCount++;
        for(let fi=0;fi<6;fi++) {
          const f=faces[fi],n=f.n,neighbor=shown(x+n[0],y+n[1],z+n[2]);
          if(neighbor&&(neighbor===id||!transparent(neighbor)))continue;
          const name=faceMaterial(id,fi),b=batches[name]||(batches[name]={p:[],n:[],uv:[],c:[],i:[]});
          const start=b.p.length/3;
          const tangents=[0,1,2].filter(k=>n[k]===0);
          for(let k=0;k<4;k++) {
            const v=f.v[k],q=[x+n[0],y+n[1],z+n[2]],a=tangents[0],c=tangents[1];
            const qa=[...q],qb=[...q],qc=[...q];
            qa[a]+=v[a]===0?-1:1;qb[c]+=v[c]===0?-1:1;qc[a]=qa[a];qc[c]=qb[c];
            const occ=Number(opaque(...qa))+Number(opaque(...qb))+Number(opaque(...qc));
            const shade=(id===LIGHT||id===PORTAL?1:f.shade)*(1-occ*.085);
            const yy=y+v[1]-(id===WATER&&v[1]===1?.1:0);
            b.p.push(x+v[0],yy,z+v[2]);b.n.push(...n);b.uv.push(k===1||k===2?1:0,k>=2?1:0);b.c.push(shade,shade,shade);
          }
          b.i.push(start,start+1,start+2,start,start+2,start+3);faceCount++;
        }
      }
      for(const [name,b] of Object.entries(batches)) {
        const geo=new T.BufferGeometry();geo.setAttribute('position',new T.Float32BufferAttribute(b.p,3));geo.setAttribute('normal',new T.Float32BufferAttribute(b.n,3));geo.setAttribute('uv',new T.Float32BufferAttribute(b.uv,2));geo.setAttribute('color',new T.Float32BufferAttribute(b.c,3));geo.setIndex(b.i);geo.computeBoundingSphere();
        const mesh=new T.Mesh(geo,materials[palette][name]);mesh.userData.textureName=name;geometryGroup.add(mesh);
      }
      dimensions.visible=mode!=='inside';
      root.querySelectorAll('.frog-dim').forEach(el=>el.hidden=mode==='inside');
      render();
    }
    // Sub-block models use 1/16-block coordinates and UVs proportional to face size.
    function miniBox(x,y,z,w,h,d,name,parent=detailGroup) {
      const g=new T.BoxGeometry(w,h,d);const uv=g.attributes.uv;
      const dims=[[d,h],[d,h],[w,d],[w,d],[w,h],[w,h]];
      for(let side=0;side<6;side++)for(let v=0;v<4;v++){const i=side*4+v;uv.setXY(i,uv.getX(i)*dims[side][0],uv.getY(i)*dims[side][1]);}
      const color=new Float32Array(g.attributes.position.count*3).fill(1);g.setAttribute('color',new T.BufferAttribute(color,3));
      const mesh=new T.Mesh(g,materials[palette][name]);mesh.position.set(x+w/2,y+h/2,z+d/2);mesh.userData.textureName=name;parent.add(mesh);return mesh;
    }
    for(const [cx,cz] of [[10,22],[13,19],[13,38],[7,29],[31,19],[33,14],[37,22],[30,38]]) {
      for(let i=0;i<4;i++) {
        const x=cx+(hash(cx,i,cz)*3|0),z=cz+(hash(cz,i,cx)*3|0),y=floorY(x,z);
        if(get(x,y,z)!==0||protectedApproach(x,y,z))continue;
        const high=(5+Math.floor(hash(x,i,z)*6))/16;
        miniBox(x+.4375,y,z+.4375,.125,high,.125,'stem');
        miniBox(x+.25,y+high,z+.25,.5,.125,.5,'cap');
        miniBox(x+.3125,y+high+.125,z+.3125,.375,.0625,.375,'cap');
      }
    }
    for(const [x,z,size] of [[19,29,1],[26,34,.5],[12,26,.75]]) {
      const y=poolAt(x,z)?4.9:floorY(x,z);miniBox(x,y,z,size,size,size,'slime');
      const eye=size/8;for(const s of [.2,.63])miniBox(x+size-.01,y+size*.55,z+size*s,.032,eye,eye,'eye');
    }
    // Player-sized mannequin: 1.8 blocks tall, not an enlarged decorative character.
    const px=35,py=6,pz=32;
    miniBox(px,py,pz,.25,.75,.25,'pants');miniBox(px+.3125,py,pz,.25,.75,.25,'pants');
    miniBox(px-.03125,py+.75,pz-.03125,.625,.65,.3125,'shirt');
    miniBox(px+.08125,py+1.4,pz-.075,.4,.4,.4,'skin');
    miniBox(px-.21875,py+.75,pz,.1875,.65,.25,'skin');miniBox(px+.59375,py+.75,pz,.1875,.65,.25,'skin');
    const dimPoints=[[-2,0,50],[48,0,50],[-2,0,50],[-2,0,48],[48,0,50],[48,0,48],[50,0,0],[50,0,48],[50,0,0],[48,0,0],[50,0,48],[48,0,48],[50,0,49],[50,32,49],[50,0,49],[48,0,49],[50,32,49],[48,32,49]];
    const dimMat=new T.LineBasicMaterial({color:0x888888,transparent:true,opacity:.55});
    const dimGeo=new T.BufferGeometry().setFromPoints(dimPoints.map(p=>new T.Vector3(...p)));dimensions.add(new T.LineSegments(dimGeo,dimMat));
    const outline=new T.EdgesGeometry(new T.BoxGeometry(48,32,48));
    const outlineMat=new T.LineBasicMaterial({color:0x888888,transparent:true,opacity:.18});
    const bounds=new T.LineSegments(outline,outlineMat);bounds.position.set(24,16,24);dimensions.add(bounds);
    const labels=[['.frog-width',[22,0,51]],['.frog-depth',[52,0,22]],['.frog-height',[52,16,49]]];
    let scheduled=false;
    function render(){if(scheduled)return;scheduled=true;requestAnimationFrame(()=>{scheduled=false;draw();});}
    function draw() {
      const rect=stage.getBoundingClientRect();if(!rect.width||!rect.height)return;
      camera.position.set(target.x+distance*Math.cos(elevation)*Math.sin(yaw),target.y+distance*Math.sin(elevation),target.z+distance*Math.cos(elevation)*Math.cos(yaw));camera.lookAt(target);camera.updateMatrixWorld();
      renderer.render(scene,camera);
      for(const [selector,p]of labels){const v=new T.Vector3(...p).project(camera),el=root.querySelector(selector);el.style.left=((v.x+1)*rect.width/2)+'px';el.style.top=((-v.y+1)*rect.height/2)+'px';el.style.visibility=Math.abs(v.x)>1||Math.abs(v.y)>1?'hidden':'visible';}
    }
    function resize(){const r=stage.getBoundingClientRect();renderer.setSize(r.width,r.height,false);camera.aspect=r.width/r.height;camera.updateProjectionMatrix();render();}
    const observer=new ResizeObserver(resize);observer.observe(stage);
    const zoom=root.querySelector('#frog-zoom');
    const updateZoom=()=>{zoom.value=Math.round(distance);render();};
    root.querySelector('#frog-view').addEventListener('change',e=>{
      mode=e.target.value;
      if(mode==='inside'){target.set(19,10,17);distance=25;elevation=-.065;yaw=.35;}
      else if(mode==='top'){target.set(24,10,24);distance=102;elevation=1.55;yaw=0;}
      else {target.set(24,13,24);distance=103;elevation=.53;yaw=.76;}
      updateZoom();rebuild();
    });
    root.querySelector('#frog-palette').addEventListener('change',e=>{palette=e.target.value;for(const group of [geometryGroup,detailGroup])group.traverse(m=>{if(m.isMesh)m.material=materials[palette][m.userData.textureName];});render();});
    root.querySelector('#frog-left').addEventListener('click',()=>{yaw-=Math.PI/8;render();});
    root.querySelector('#frog-right').addEventListener('click',()=>{yaw+=Math.PI/8;render();});
    zoom.addEventListener('input',()=>{distance=Number(zoom.value);render();});
    const canvas=renderer.domElement;const pointers=new Map();let dragged=false;
    canvas.addEventListener('contextmenu',e=>e.preventDefault());
    canvas.addEventListener('pointerdown',e=>{pointers.set(e.pointerId,{x:e.clientX,y:e.clientY,button:e.button});canvas.setPointerCapture(e.pointerId);dragged=false;});
    canvas.addEventListener('pointermove',e=>{
      const old=pointers.get(e.pointerId);if(!old)return;
      const dx=e.clientX-old.x,dy=e.clientY-old.y;
      if(Math.abs(dx)+Math.abs(dy)>2)dragged=true;
      if(pointers.size===2){const other=[...pointers.entries()].find(([id])=>id!==e.pointerId)[1];const before=Math.hypot(old.x-other.x,old.y-other.y),after=Math.hypot(e.clientX-other.x,e.clientY-other.y);distance=T.MathUtils.clamp(distance*before/Math.max(after,1),24,170);updateZoom();}
      else if(old.button===2||e.shiftKey){const scale=distance*.00075;target.x-=dx*Math.cos(yaw)*scale;target.z+=dx*Math.sin(yaw)*scale;target.y+=dy*scale;}
      else{yaw-=dx*.007;elevation=T.MathUtils.clamp(elevation+dy*.006,-.2,1.56);}
      pointers.set(e.pointerId,{...old,x:e.clientX,y:e.clientY});render();
    });
    const raycaster=new T.Raycaster();
    canvas.addEventListener('pointerup',e=>{
      pointers.delete(e.pointerId);
      if(!dragged&&e.button===0){const r=canvas.getBoundingClientRect();raycaster.setFromCamera(new T.Vector2((e.clientX-r.left)/r.width*2-1,-(e.clientY-r.top)/r.height*2+1),camera);const hit=raycaster.intersectObjects([...geometryGroup.children,...detailGroup.children])[0];if(hit){const p=hit.point.clone().addScaledVector(hit.face.normal,-.001);const labels={wall:'胃袋壁',mucosa:'胃黏膜',fold:'黏膜褶皱',stem:'胃菇菌柄',stem_top:'胃菇菌柄',cap:'胃菇菌盖',gill:'胃菇菌褶',light:'发光菌体',water:'琥珀浅池',secretion:'胃袋分泌物',frame:'消化道框架',portal:'消化道入口',slime:'史莱姆',eye:'史莱姆',shirt:'1.8 格人形参照',pants:'1.8 格人形参照',skin:'1.8 格人形参照'};root.querySelector('.frog-detail').textContent=(labels[hit.object.userData.textureName]||'方块')+' · 位置 '+[p.x,p.y,p.z].map(Math.floor).join(', ')+' · 16×16 材质';}}
    });
    canvas.addEventListener('pointercancel',e=>pointers.delete(e.pointerId));
    canvas.addEventListener('wheel',e=>{e.preventDefault();distance=T.MathUtils.clamp(distance*Math.exp(e.deltaY*.001),24,170);updateZoom();},{passive:false});
    function theme(){const probe=document.createElement('span');probe.style.color='var(--muted-foreground)';root.append(probe);const color=getComputedStyle(probe).color;probe.remove();try{dimMat.color.setStyle(color);outlineMat.color.setStyle(color);}catch{}render();}
    const themeObserver=new MutationObserver(theme);themeObserver.observe(document.documentElement,{attributes:true,attributeFilter:['class','style','data-theme']});
    matchMedia('(prefers-color-scheme: dark)').addEventListener('change',theme);
    canvas.addEventListener('webglcontextlost',e=>{e.preventDefault();status.hidden=false;status.textContent='图形上下文已中断，请重新打开预览。';});
    window.FROG_PREVIEW={dimensions:[W,H,D],grid,get,renderer,camera,textures,materials,geometryGroup,detailGroup,get stats(){return {mode,palette,voxels:voxelCount,faces:faceCount,drawCalls:renderer.info.render.calls};}};
    resize();rebuild();theme();status.hidden=true;
  } catch(error){status.hidden=false;status.setAttribute('role','alert');status.textContent='无法显示 3D 预览：'+error.message;console.error(error);}
})();
