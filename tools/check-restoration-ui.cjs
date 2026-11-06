// Node.js + Playwright. Network and user data are deterministic fixtures.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {chromium}=require('playwright');
const root=path.resolve(__dirname,'../src/main/resources/static');
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true});
 try{
  const page=await browser.newPage({viewport:{width:1366,height:900}});
  const errors=[],missing=[],calls=[];let reviews=[];let favorite=false;let registered=true;
  page.on('pageerror',e=>errors.push(e.message));
  page.on('dialog',dialog=>dialog.accept());
  await page.route('**/*',async route=>{
   const url=new URL(route.request().url());const p=url.pathname;calls.push(p);
   if(url.hostname!=='synchro.test')return route.abort();
   const json=body=>route.fulfill({json:body});
   if(p==='/auth/cookie')return route.fulfill({body:'ok'});
   if(p==='/users/me')return json({username:'tester',lastFmConnected:false,data:{}});
   if(p==='/catalog/users/2')return json({username:'visitor',lastFmConnected:false,data:{}});
   if(p==='/users/me/profile')return json({biography:'Minha biografia',profileImageUrl:null});
   if(p==='/users/me/banner')return json({bannerImageUrl:null});
   if(p==='/last-fm/me'||p.startsWith('/last-fm/me/top'))return route.fulfill({status:409,json:{error:'Sem conta'}});
   if(p==='/last-fm/discovery') {
    const method=url.searchParams.get('method')?.toLowerCase();
    if(method==='track.getinfo')return json({track:{name:'Track',artist:{name:'Artist'},listeners:'100',playcount:'200',duration:'120000',toptags:{tag:[]},wiki:{summary:'Track description'}}});
    if(method==='artist.getinfo')return json({artist:{name:'Artist',bio:{summary:'Artist description'},stats:{listeners:'100',playcount:'200'},tags:{tag:[]},image:[]}});
    if(method==='album.getinfo')return json({album:{name:'Album',artist:'Artist',tracks:{track:[]},image:[]}});
    return json({toptracks:{track:[]},topalbums:{album:[]},results:{albummatches:{album:[]}}});
   }
   if(p==='/last-fm/artist/search')return json({results:{artistmatches:{artist:[{name:'Artist',image:[],listeners:'100'}]}}});
   if(p==='/last-fm/track/search')return json({results:{trackmatches:{track:[{name:'Track',artist:'Artist',image:[]}]}}});
   if(p==='/artista')return json([]);
   if(p.startsWith('/musicbrainz/'))return json({releaseGroups:[],albums:[],tracks:[]});
   if(p.startsWith('/cover-art/'))return route.fulfill({status:404,json:{}});
   if(p==='/catalog/interactions'){
    const item=route.request().postDataJSON();
    if(item.type==='track')registered=true;
    return json({type:item.type,id:item.type==='track'?12:item.type==='album'?42:4});
   }
   if(p==='/musica/identificar'&&!registered)return route.fulfill({status:404,json:{}});
   if(p==='/musica/identificar'||p==='/musica/registrar'){if(p.endsWith('/registrar'))registered=true;return json({id:12,titulo:'Track',artistas:[{nome:'Artist'}]});}
   if(p==='/catalog/songs/12')return json({id:12,titulo:'Track',artistas:[{id:4,nome:'Artist'}],lastFm:{tags:[]}});
   if(p==='/catalog/songs/12/refresh')return route.fulfill({status:204,body:''});
   if(p==='/catalog/artists/4/refresh')return route.fulfill({status:204,body:''});
   if(p==='/catalog/artists/4')return json({artist:{id:4,nome:'Artist',lastFm:{tags:[]}},musicas:[],albums:[]});
   if(p==='/album/id/42')return json({id:42,titulo:'Album',artistas:[{nome:'Artist'}],musicas:[]});
   if(p==='/playlists/me')return json([{id:3,titulo:'Minha playlist',musicas:[]}]);
   if(p==='/playlists/3/midias/12')return route.fulfill({status:204,body:''});
   if(p.startsWith('/favorites/')){
    const method=route.request().method();if(method==='POST')favorite=true;if(method==='DELETE')favorite=false;
    if(p.endsWith('/check')||method!=='GET')return json({favorite});
    return json([]);
   }
   if(p==='/review'||p==='/review/me')return json(reviews);
   if(p.endsWith('/rating'))return json({media:reviews.length?8:0,total:reviews.length});
   if(/^\/review\/(midia\/\d+|\d+)$/.test(p)){
    if(route.request().method()==='DELETE')reviews=[];
    else reviews=[{id:1,midiaId:Number(p.split('/').pop()),author_name:'tester',rating:8,review:route.request().postDataJSON().review}];
    return route.request().method()==='DELETE'?route.fulfill({status:204,body:''}):json(reviews[0]);
   }
   const f=path.resolve(root,'.'+p);if(!f.startsWith(root+path.sep)||!fs.existsSync(f)||fs.statSync(f).isDirectory()){missing.push(p);return route.fulfill({status:404,body:''});}
   const type={'.js':'text/javascript','.css':'text/css','.html':'text/html','.svg':'image/svg+xml','.png':'image/png','.jpg':'image/jpeg','.mp4':'video/mp4'}[path.extname(f)]||'application/octet-stream';
   return route.fulfill({contentType:type,body:fs.readFileSync(f)});
  });
  async function visit(file){await page.goto('http://synchro.test/frontend/pages/'+file);await page.getByRole('navigation',{name:'Biblioteca Synchro'}).waitFor();}
  await visit('detalhes-album.html?id=42');
  await page.locator('#albumName').filter({hasText:'Album'}).waitFor();
  await page.locator('#albumReviewText').fill('<img src=x onerror="window.reviewInjected=true">');
  await page.locator('#saveAlbumReview').click();
  await page.locator('#albumReviewList').getByText('<img src=x onerror="window.reviewInjected=true">',{exact:true}).waitFor();
  assert.equal(await page.evaluate(()=>window.reviewInjected),undefined);
  assert.equal(await page.locator('#albumReviewList img').count(),0);
  assert.ok(!calls.includes('/auth/me'));
  await visit('detalhes-musica.html?artist=Artist&track=Track');
  await page.locator('#addToPlaylistBtn:not([disabled])').waitFor();
  await page.locator('#addToPlaylistBtn').click();
  await page.locator('#songPlaylistCards input').check();
  await page.locator('#songPlaylistSubmit').click();
  await page.locator('#songPlaylistFeedback').filter({hasText:'Música adicionada'}).waitFor();
  assert.ok(calls.includes('/playlists/3/midias/12'));
  registered=false;
  const registrations=calls.filter(p=>p==='/catalog/interactions').length;
  await visit('detalhes-musica.html?artist=Artist&track=NewTrack');
  await page.locator('#addToPlaylistBtn:not([disabled])').waitFor();
  assert.equal(calls.filter(p=>p==='/catalog/interactions').length,registrations+1,'Viewing a song registers it in the catalog');
  await page.locator('#addToPlaylistBtn').click();
  await page.locator('#songPlaylistDialog[open]').waitFor();
  assert.equal(calls.filter(p=>p==='/catalog/interactions').length,registrations+1,'Adding a viewed song to a playlist reuses its saved ID');
  await visit('musica.html?id=12');
  await page.waitForURL('**/detalhes-musica.html?id=12');
  await page.locator('#musicName').filter({hasText:'Track'}).waitFor();
  assert.equal(await page.locator('#addToPlaylistBtn').getAttribute('data-midia-id'),'12');
  await page.locator('#favoriteTrackBtn').click();
  await page.locator('#favoriteTrackBtn[aria-pressed="true"]').waitFor();
  await page.reload();
  await page.locator('#favoriteTrackBtn[aria-pressed="true"]').waitFor();
  assert.ok(calls.includes('/favorites/tracks'));
  await visit('detalhes-musica.html?id=99999');
  await page.locator('#musicName').filter({hasText:'Registro não encontrado no catálogo.'}).waitFor();
  await visit('artista.html?id=4');
  await page.locator('.artist-header[aria-busy="false"]').waitFor();
  await page.locator('#favoriteBtn[aria-pressed="true"]').waitFor();
  await page.locator('.artist-header .feature-detail-link').click();
  assert.match(page.url(),/detalhes-artista.html\?artist=Artist/);
  await visit('recentes.html');
  await page.locator('#recentesGrid a').first().waitFor();
  assert.match(await page.locator('#recentesGrid').innerText(),/Track/);
  for(const file of ['explorar.html','favoritos.html','configuracoes.html','detalhes-artista.html?artist=Artist','album-explorar.html?artist=Artist&album=Album'])await visit(file);
  await page.goto('http://synchro.test/index.html');
  await page.locator('a.user[aria-label="Meu perfil"]').click();
  await page.waitForURL('**/frontend/pages/perfil.html');
  await page.locator('.playlist').getByText('Minha playlist',{exact:true}).waitFor();
  await page.locator('#profileBiography').filter({hasText:'Minha biografia'}).waitFor();
  await visit('configuracoes.html');
  await page.locator('#themePreference').selectOption('light');
  await page.locator('#historyEnabled').uncheck();
  await visit('explorar.html');
  assert.equal(await page.locator('html').evaluate(el=>el.classList.contains('synchro-light')),true);
  const historyBefore=await page.evaluate(()=>localStorage.getItem('synchro:recentes:v1'));
  await visit('detalhes-artista.html?artist=Another');
  assert.equal(await page.evaluate(()=>localStorage.getItem('synchro:recentes:v1')),historyBefore);
  await visit('configuracoes.html');
  await page.locator('#themePreference').selectOption('dark');
  await page.setViewportSize({width:390,height:844});
  await visit('favoritos.html');
  await page.screenshot({path:'target/restoration-mobile.png',fullPage:true});
  await page.setViewportSize({width:1366,height:900});await visit('explorar.html');
  await page.screenshot({path:'target/restoration-desktop.png',fullPage:true});
  const publicStart = calls.length;
  await page.goto('http://synchro.test/frontend/pages/user.html?user=2');
  await page.waitForURL('**/perfil.html?user=2');
  await page.getByText('@visitor', {exact:true}).waitFor();
  assert.equal(await page.locator('#editBiographyButton').isVisible(), false);
  assert.equal(await page.getByRole('link', {name:'Conta e Last.fm'}).count(), 0);
  assert.ok(!calls.slice(publicStart).some(p=>/^\/(users|playlists|review|last-fm)\/me\b/.test(p)));
  await page.route('**/users/me',route=>route.fulfill({json:{username:'tester',lastFmConnected:true,lastFmUsername:'listener',data:{artistas:4,lastFmAlbums:2}}}));
  let artistCalls=0;
  await page.route('**/last-fm/me/top/**',route=>{
   const url=new URL(route.request().url());
   assert.equal(url.searchParams.get('period'),'7day');
   if(url.pathname.endsWith('/artists')){
    artistCalls++;
    if(artistCalls===1)return route.fulfill({status:504,json:{error:'Last.fm indisponível'}});
    return route.fulfill({json:{topartists:{artist:[{name:'Artista recuperado',playcount:0}]}}});
   }
   if(url.pathname.endsWith('/albums'))return route.fulfill({json:{topalbums:{album:[{name:'Álbum disponível',playcount:4}]}}});
   return route.fulfill({json:{toptracks:{track:[]}}});
  });
  await visit('perfil.html');
  await page.locator('#rankingStatus').getByText('O Last.fm demorou para responder.').waitFor();
  await page.locator('.report-albums').getByText('Álbum disponível').waitFor();
  assert.equal(artistCalls,1);
  await page.getByRole('button',{name:'Tentar novamente',exact:true}).click();
  await page.locator('.report-artists').getByText('Artista recuperado').waitFor();
  assert.equal(artistCalls,2);
  await page.route('**/users/me',route=>route.fulfill({status:401,json:{}}));
  await page.goto('http://synchro.test/frontend/pages/perfil.html');
  await page.waitForURL('**/frontend/pages/login.html');
  assert.deepEqual(errors,[],'JavaScript errors');
  assert.deepEqual([...new Set(missing.filter(p=>/\.(html|js|css)$/.test(p)))],[],'Missing internal assets/pages');
  console.log('PASS: discovery pages, album review/session and HTML escaping, playlists, recent history, desktop/mobile and internal assets.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
