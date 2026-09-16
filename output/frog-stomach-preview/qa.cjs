const {chromium}=require('playwright-core');
const path=require('path');
const fs=require('fs');
(async()=>{
  const browser=await chromium.launch({headless:true,executablePath:'C:/Users/Nobodiiiii/AppData/Local/ms-playwright/chromium-1234/chrome-win64/chrome.exe',args:['--use-angle=swiftshader','--enable-unsafe-swiftshader','--no-sandbox']});
  const page=await browser.newPage({viewport:{width:940,height:1000},deviceScaleFactor:1});
  const errors=[],requests=[];page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(r.url().startsWith('http'))requests.push(r.url());});
  try {
    await page.goto('file:///'+path.join(__dirname,'preview-standalone.html').replaceAll('\\','/'));
    await page.waitForSelector('iframe');
    const frame=page.frames().find(f=>f.parentFrame());
    await frame.waitForFunction(()=>Boolean(window.FROG_PREVIEW),null,{timeout:30000});
    await frame.waitForFunction(()=>window.FROG_PREVIEW.renderer.info.render.calls>0);
    const initial=await frame.evaluate(()=>{
      const p=window.FROG_PREVIEW;
      return {dimensions:p.dimensions,stats:p.stats,textures:Object.values(p.textures).flatMap(set=>Object.values(set)).map(t=>[t.image.width,t.image.height,t.magFilter,t.minFilter,t.generateMipmaps]),canvas:[p.renderer.domElement.width,p.renderer.domElement.height],portals:[p.get(22,22,1),p.get(22,6,46)]};
    });
    if(initial.dimensions.join()!=='48,32,48')throw Error('Room bounds mismatch');
    if(initial.textures.some(t=>t[0]!==16||t[1]!==16||t[2]!==1003||t[3]!==1003||t[4]))throw Error('Texture sampling mismatch');
    if(initial.portals.some(x=>x!==11))throw Error('Portal placement mismatch');
    await page.screenshot({path:path.join(__dirname,'qa-cutaway.png'),fullPage:true});
    for(const mode of ['full','top','inside','cut']){
      await frame.locator('#frog-view').selectOption(mode);
      await frame.waitForFunction(m=>window.FROG_PREVIEW.stats.mode===m,mode);
      await frame.evaluate(()=>new Promise(requestAnimationFrame));
      if(mode==='inside')await page.screenshot({path:path.join(__dirname,'qa-inside.png'),fullPage:true});
    }
    await frame.locator('#frog-palette').selectOption('original');
    const changed=await frame.evaluate(()=>window.FROG_PREVIEW.geometryGroup.children.every(m=>m.material===window.FROG_PREVIEW.materials.original[m.userData.textureName]));
    if(!changed)throw Error('Palette did not update all meshes');
    await frame.locator('#frog-palette').selectOption('concept');
    const before=await frame.evaluate(()=>window.FROG_PREVIEW.camera.position.toArray());
    await frame.locator('#frog-left').click();
    await frame.evaluate(()=>new Promise(requestAnimationFrame));
    const after=await frame.evaluate(()=>window.FROG_PREVIEW.camera.position.toArray());
    if(before.join()===after.join())throw Error('Orbit control did not update camera');
    await frame.locator('#frog-right').click();
    await frame.locator('#frog-zoom').fill('75');
    await frame.locator('#frog-zoom').dispatchEvent('input');
    await frame.evaluate(()=>new Promise(requestAnimationFrame));
    await page.screenshot({path:path.join(__dirname,'qa-detail.png'),fullPage:true});
    await page.setViewportSize({width:360,height:920});
    await frame.locator('#frog-view').selectOption('top');
    await frame.locator('#frog-view').selectOption('cut');
    await frame.evaluate(()=>new Promise(requestAnimationFrame));
    const overflow=await frame.evaluate(()=>document.documentElement.scrollWidth>document.documentElement.clientWidth+1);
    if(overflow)throw Error('Mobile horizontal overflow');
    await page.screenshot({path:path.join(__dirname,'qa-mobile.png'),fullPage:true});
    if(errors.length)throw Error(errors.join('\n'));
    if(requests.length)throw Error('Unexpected network dependencies: '+requests.join());
    const report={passed:true,room:initial.dimensions,textureCount:initial.textures.length,textureSize:'16x16',nearestFiltering:true,mipmaps:false,initialStats:initial.stats,portals:'matched',views:4,paletteSwitch:true,orbit:true,zoom:true,mobileWidth:360,offline:true,errors};
    fs.writeFileSync(path.join(__dirname,'qa-report.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));
  } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
