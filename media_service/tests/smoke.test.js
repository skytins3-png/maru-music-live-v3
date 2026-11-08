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
 const f=new FormData();f.set('author','청취자');f.set('body','라디오 방송 듣고 너무 반가웠습니다');f.set('publishConsent','true');
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

test('quarantined attachment cannot be downloaded or approved',async()=>{
 const f=new FormData();f.set('author','검증중');
 f.set('body','악성 파일 차단 여부를 검사하는 가상 사연입니다');f.set('publishConsent','true');
 f.set('attachment',new Blob([Buffer.from('89504e470d0a1a0a00000000','hex')]),'sample.png');
 const r=await get('/api/stories',{method:'POST',body:f});
 assert.equal(r.status,201);
 assert.equal(r.data.status,'pending');
 const attachment=await get('/api/admin/attachment/'+r.data.receipt,
   {headers:{'X-MARU-ADMIN':process.env.MARU_ADMIN_TOKEN}});
 assert.equal(attachment.status,404);
 const review=await get('/api/admin/stories/'+r.data.receipt+'/review',{
   method:'POST',headers:{'X-MARU-ADMIN':process.env.MARU_ADMIN_TOKEN,'Content-Type':'application/json'},
   body:JSON.stringify({action:'approve'})
 });
 assert.equal(review.status,409);
 const rejected=await get('/api/admin/stories/'+r.data.receipt+'/review',{
   method:'POST',headers:{'X-MARU-ADMIN':process.env.MARU_ADMIN_TOKEN,'Content-Type':'application/json'},
   body:JSON.stringify({action:'reject'})
 });
 assert.equal(rejected.status,200);
 assert.equal(rejected.data.status,'rejected');
});
test('authorized upload: correct sha, approved after mocked CLEAN scan; private until then',async()=>{
 process.env.CLAMSCAN_CMD='true'; // Test-only deterministic stub; NOT a production virus scanner.
 const bytes=Buffer.from('ID3\x03\x00\x00\x00\x00\x00\x00this is a harmless dummy test fixture');
 const sha=crypto.createHash('sha256').update(bytes).digest('hex');
 const bad=new FormData();bad.set('title','QA 잘못된 해시');
 bad.set('sha256','0'.repeat(64));bad.set('rights_confirmed','true');
 bad.set('audio',new Blob([bytes]),'wrong.mp3');
 const badAnswer=await get('/api/upload',{method:'POST',
  headers:{'X-MARU-ADMIN':process.env.MARU_ADMIN_TOKEN},body:bad});
 assert.equal(badAnswer.status,400);
 const f=new FormData();f.set('title','테스트 청취용 노래');
 f.set('sha256',sha);f.set('language','한국노래');
 f.set('genre','발라드');f.set('rights_confirmed','true');
 f.set('audio',new Blob([bytes]),'good.mp3');
 const upload=await get('/api/upload',{method:'POST',
  headers:{'X-MARU-ADMIN':process.env.MARU_ADMIN_TOKEN},body:f});
 assert.equal(upload.status,201);
 assert.equal(upload.data.status,'pending');
 const id=upload.data.id;
 assert.ok(id);
 assert.equal((await get('/api/tracks')).data.tracks.length,0);
 assert.equal((await get('/api/audio/'+id)).status,404);
 assert.equal((await get('/api/download/'+id)).status,404);
 const approval=await get('/api/admin/tracks/'+id+'/review',{
   method:'POST',headers:{'X-MARU-ADMIN':process.env.MARU_ADMIN_TOKEN,'Content-Type':'application/json'},
   body:JSON.stringify({action:'publish',downloadAllowed:false})
 });
 assert.equal(approval.status,200);
 assert.equal(approval.data.status,'public');
 assert.equal((await get('/api/tracks')).data.tracks.length,1);
 assert.equal((await get('/api/audio/'+id)).status,200);
 assert.equal((await get('/api/download/'+id)).status,404);
 const firstView=await get('/api/view/'+id,{method:'POST'});
 assert.equal(firstView.status,200);
 const secondView=await get('/api/view/'+id,{method:'POST'});
 assert.equal(secondView.data.views,firstView.data.views);
 const like=await get('/api/like/'+id,{method:'POST'});
 assert.equal(like.data.likes,1);
 const unlike=await get('/api/like/'+id,{method:'POST'});
 assert.equal(unlike.data.likes,0);
 const comment=await get('/api/comments/'+id,{
   method:'POST',headers:{'Content-Type':'application/json'},
   body:JSON.stringify({author:'시험',body:'좋은 곡입니다'})
 });
 assert.equal(comment.status,201);
 assert.equal((await get('/api/comments/'+id)).data.comments.length,0);
 const all=await get('/api/admin/comments',{headers:{'X-MARU-ADMIN':process.env.MARU_ADMIN_TOKEN}});
 assert.equal(all.status,200);
 const item=all.data.comments.find(c=>c.body==='좋은 곡입니다');
 assert.ok(item);
 const answer=await get('/api/admin/comments/'+item.id+'/review',{
   method:'POST',headers:{'X-MARU-ADMIN':process.env.MARU_ADMIN_TOKEN,'Content-Type':'application/json'},
   body:JSON.stringify({action:'approve'})
 });
 assert.equal(answer.status,200);
 assert.equal((await get('/api/comments/'+id)).data.comments.length,1);
 const allow=await get('/api/admin/tracks/'+id+'/review',{
   method:'POST',headers:{'X-MARU-ADMIN':process.env.MARU_ADMIN_TOKEN,'Content-Type':'application/json'},
   body:JSON.stringify({action:'publish',downloadAllowed:true})
 });
 assert.equal(allow.status,200);
 assert.equal((await get('/api/download/'+id)).status,200);
 const score=new FormData();
 score.set('score',new Blob([Buffer.from('%PDF-1.4\n%dummy test only')]),'score.pdf');
 const uploadScore=await get('/api/admin/tracks/'+id+'/score',{
   method:'POST',headers:{'X-MARU-ADMIN':process.env.MARU_ADMIN_TOKEN},body:score
 });
 assert.equal(uploadScore.status,200);
 assert.equal((await get('/api/score/'+id)).status,200);
 process.env.CLAMSCAN_CMD='not-installed-test-clamscan';
});

test('public stories require explicit publication consent, safe approval and moderated subfunctions',async()=>{
 const noConsent=new FormData();noConsent.set('author','시험');noConsent.set('body','동의하지 않은 청취자 사연입니다.');
 const blocked=await get('/api/stories',{method:'POST',body:noConsent});
 assert.equal(blocked.status,400);
 const f=new FormData();f.set('author','가명');f.set('body','승인된 테스트 사연입니다. 실제 개인정보는 없습니다.');
 f.set('publishConsent','true');f.set('songRequest','테스트 음악');
 const submit=await get('/api/stories',{method:'POST',body:f});
 assert.equal(submit.status,201);
 const id=submit.data.receipt;
 assert.ok(!(await get('/api/stories/public')).data.stories.some(s=>s.id===id));
 assert.equal((await get('/api/story-download/'+id)).status,404);
 assert.equal((await get('/api/story-view/'+id,{method:'POST'})).status,404);
 const approved=await get('/api/admin/stories/'+id+'/review',{
   method:'POST',headers:{'X-MARU-ADMIN':process.env.MARU_ADMIN_TOKEN,'Content-Type':'application/json'},
   body:JSON.stringify({action:'approve',downloadAllowed:false})
 });
 assert.equal(approved.status,200);
 assert.equal(approved.data.status,'approved');
 const list=await get('/api/stories/public');
 assert.ok(list.data.stories.some(s=>s.id===id));
 const detail=await get('/api/stories/public/'+id);
 assert.equal(detail.data.body,'승인된 테스트 사연입니다. 실제 개인정보는 없습니다.');
 assert.equal((await get('/api/story-download/'+id)).status,404);
 const view1=await get('/api/story-view/'+id,{method:'POST'});
 const view2=await get('/api/story-view/'+id,{method:'POST'});
 assert.equal(view1.data.views,view2.data.views);
 const like=await get('/api/story-like/'+id,{method:'POST'});
 assert.equal(like.data.likes,1);
 const unlike=await get('/api/story-like/'+id,{method:'POST'});
 assert.equal(unlike.data.likes,0);
 const c=await get('/api/story-comments/'+id,{method:'POST',
   headers:{'Content-Type':'application/json'},
   body:JSON.stringify({author:'댓글작성자',body:'공개 승인 대기 댓글'})});
 assert.equal(c.status,201);
 assert.equal((await get('/api/story-comments/'+id)).data.comments.length,0);
 const pending=await get('/api/admin/story-comments',{headers:{'X-MARU-ADMIN':process.env.MARU_ADMIN_TOKEN}});
 assert.equal(pending.status,200);
 const comment=pending.data.comments.find(c=>c.story===id);
 assert.ok(comment);
 const verified=await get('/api/admin/story-comments/'+comment.id+'/review',{
   method:'POST',headers:{'X-MARU-ADMIN':process.env.MARU_ADMIN_TOKEN,'Content-Type':'application/json'},
   body:JSON.stringify({action:'approve'})
 });
 assert.equal(verified.status,200);
 assert.equal((await get('/api/story-comments/'+id)).data.comments.length,1);
 const reply=await get('/api/story-comments/'+id,{method:'POST',
  headers:{'Content-Type':'application/json'},
  body:JSON.stringify({author:'답글',body:'공개된 댓글에 답장',parentId:comment.id})});
 assert.equal(reply.status,201);
 const allow=await get('/api/admin/stories/'+id+'/review',{
   method:'POST',headers:{'X-MARU-ADMIN':process.env.MARU_ADMIN_TOKEN,'Content-Type':'application/json'},
   body:JSON.stringify({action:'approve',downloadAllowed:true})
 });
 assert.equal(allow.status,200);
 assert.equal((await get('/api/story-download/'+id)).status,200);
});
