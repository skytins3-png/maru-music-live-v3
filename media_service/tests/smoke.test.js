'use strict';
const {test,before,after}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const os=require('node:os');
const crypto=require('node:crypto');
const folder=fs.mkdtempSync(path.join(os.tmpdir(),'maru-media-test-'));
process.env.MARU_STORAGE_DIR=folder;
process.env.MARU_ADMIN_TOKEN='test-admin-token-32-or-more-characters-please';
process.env.MARU_HASH_SALT='other-unique-test-salt-for-32-or-more-characters';
process.env.CLAMSCAN_CMD='not-installed-test-clamscan';
const app=require('../server');
let server,base;
before(async()=>{
 server=await new Promise(resolve=>{
  const running=app.listen(0,'127.0.0.1',()=>resolve(running));
 });
 base='http://127.0.0.1:'+server.address().port;
});
after(async()=>{
 if(server)await new Promise(resolve=>server.close(resolve));
 fs.rmSync(folder,{recursive:true,force:true});
});
async function get(route,options){
 const r=await fetch(base+route,options);
 const data=await r.json().catch(()=>({}));
 return {status:r.status,data};
}
test('health and zero public songs',async()=>{
 const health=await get('/api/health');
 assert.equal(health.status,200);
 assert.equal(health.data.publicTracks,0);
 const t=await get('/api/tracks');assert.equal(t.data.tracks.length,0);
});
test('unknown admin key is rejected',async()=>{
 const r=await get('/api/admin/tracks',{headers:{'X-MARU-ADMIN':'guess'}});
 assert.equal(r.status,401);
});
test('public listener story stays pending',async()=>{
 const f=new FormData();f.set('author','청취자');f.set('body','라디오 방송 듣고 너무 반가웠습니다');
 const r=await get('/api/stories',{method:'POST',body:f});
 assert.equal(r.status,201);assert.equal(r.data.status,'pending');
 const admin=await get('/api/admin/stories',{headers:{'X-MARU-ADMIN':process.env.MARU_ADMIN_TOKEN}});
 assert.equal(admin.data.stories.length,1);
 assert.equal(admin.data.stories[0].status,'pending');
});
test('guest song intake pending until antivirus and owner approval',async()=>{
 const f=new FormData();f.set('title','테스트 곡');f.set('artist','가명');
 f.set('genre','발라드');f.set('rights_confirmed','true');
 f.set('audio',new Blob([Buffer.from('ID3\x03\x00\x00\x00\x00\x00\x00test-song')]),'test.mp3');
 const r=await get('/api/guest-upload',{method:'POST',body:f});
 assert.equal(r.status,201);assert.equal(r.data.status,'pending');
 const list=await get('/api/tracks');assert.equal(list.data.tracks.length,0);
 const id=r.data.receipt;
 const review=await get('/api/admin/tracks/'+id+'/review',{method:'POST',
  headers:{'X-MARU-ADMIN':process.env.MARU_ADMIN_TOKEN,'Content-Type':'application/json'},
  body:JSON.stringify({action:'publish',downloadAllowed:true})});
 assert.equal(review.status,409);
});
