'use strict';
/**
 * MARU media/story backend. Source only until deployed to a private server.
 * WARNING: requires HTTPS reverse proxy, persistent volume and malware scanner.
 * Does NOT publish media automatically. One admin + public listeners.
 */
const express=require('express');
const helmet=require('helmet');
const multer=require('multer');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const crypto=require('node:crypto');
const {spawnSync}=require('node:child_process');
const app=express();
const adminToken=process.env.MARU_ADMIN_TOKEN||'';
const salt=process.env.MARU_HASH_SALT||'';
if(adminToken.length<32 || salt.length<32)throw new Error('Set independent 32+ character MARU_ADMIN_TOKEN and MARU_HASH_SALT.');
const dir=path.resolve(process.env.MARU_STORAGE_DIR||path.join(process.cwd(),'.private-media'));
const mediaDir=path.join(dir,'originals');
const quarantine=path.join(dir,'quarantine');
for(const d of [dir,mediaDir,quarantine])fs.mkdirSync(d,{recursive:true,mode:0o700});
const dbFile=path.join(dir,'data.json');
const makeDb=()=>({tracks:[],stories:[],comments:[],likes:[],views:[],storyComments:[],storyLikes:[],storyViews:[],audit:[]});
let db=makeDb();
if(fs.existsSync(dbFile)){
 const parsed=JSON.parse(fs.readFileSync(dbFile,'utf8'));
 for(const key of Object.keys(db))if(Array.isArray(parsed[key]))db[key]=parsed[key];
}
function persist(){
 const temp=path.join(dir,'data.'+process.pid+'.tmp');
 fs.writeFileSync(temp,JSON.stringify(db),{encoding:'utf8',mode:0o600});
 fs.renameSync(temp,dbFile);
}
function clean(s,max){
 return String(s||'').replace(/[\u0000-\u001f\u007f]/g,' ').replace(/\s+/g,' ').trim().slice(0,max);
}
function safeCompare(a,b){
 const x=Buffer.from(String(a||'')),y=Buffer.from(String(b||''));
 return x.length===y.length&&crypto.timingSafeEqual(x,y);
}
function admin(req,res,next){
 if(!safeCompare(req.get('X-MARU-ADMIN'),adminToken))
   return res.status(401).json({error:'관리자 인증 필요'});
 next();
}
function ipHash(req){
 const ip=req.socket.remoteAddress||'unknown';
 return crypto.createHash('sha256').update(salt+'|'+ip).digest('hex');
}
const hits=new Map();
function limiter(label,count,period){
 return (req,res,next)=>{
  const now=Date.now(),key=label+':'+ipHash(req);
  const x=hits.get(key)||{n:0,start:now};
  if(now-x.start>period){x.n=0;x.start=now;}
  x.n++;hits.set(key,x);
  if(x.n>count)return res.status(429).json({error:'요청이 너무 많습니다. 나중에 다시 시도하세요.'});
  next();
 };
}
function hashFile(filename){
 const data=fs.createReadStream(filename);
 return new Promise((resolve,reject)=>{
  const h=crypto.createHash('sha256');let n=0;
  data.on('data',b=>{n+=b.length;h.update(b);});
  data.on('error',reject);data.on('end',()=>resolve({sha:h.digest('hex'),bytes:n}));
 });
}
function magic(filename,ext){
 const fd=fs.openSync(filename,'r');
 const b=Buffer.alloc(20);
 fs.readSync(fd,b,0,20,0);fs.closeSync(fd);
 if(ext==='.mp3')return b.subarray(0,3).toString()==='ID3'||b[0]===0xff&&(b[1]&0xe0)===0xe0;
 if(ext==='.m4a'||ext==='.mp4')return b.subarray(4,8).toString()==='ftyp';
 if(ext==='.png')return b.subarray(0,8).equals(Buffer.from('89504e470d0a1a0a','hex'));
 if(ext==='.jpg'||ext==='.jpeg')return b[0]===0xff&&b[1]===0xd8;
 if(ext==='.webp')return b.subarray(0,4).toString()==='RIFF'&&b.subarray(8,12).toString()==='WEBP';
 if(ext==='.pdf')return b.subarray(0,5).toString()==='%PDF-';
 return false;
}
function scan(filename){
 const cmd=process.env.CLAMSCAN_CMD||'clamscan';
 const r=spawnSync(cmd,['--no-summary','--infected',filename],{encoding:'utf8',timeout:35000,maxBuffer:2048});
 if(r.error)return {ok:false,detail:'백신 검사 도구 미설치 또는 검사 실패'};
 if(r.status!==0)return {ok:false,detail:'검사 불통과 또는 악성 파일 탐지'};
 return {ok:true,detail:'ClamAV 검사 완료'};
}
const disk=multer.diskStorage({destination:(req,f,cb)=>cb(null,quarantine),
  filename:(req,f,cb)=>cb(null,crypto.randomUUID()+'.incoming')});
const upload=multer({storage:disk,limits:{fileSize:55*1024*1024,files:1,fields:14,fieldSize:8000}});
app.disable('x-powered-by');
app.use(helmet());
app.use(express.json({limit:'32kb'}));
app.use(express.urlencoded({extended:false,limit:'32kb'}));
app.use(express.static(path.join(__dirname,'public'),{index:'index.html',dotfiles:'deny'}));
app.get('/api/health',(req,res)=>res.json({status:'ok',publicTracks:db.tracks.filter(t=>t.status==='public').length}));
function visibleTrack(t){
 return {id:t.id,title:t.title,artist:t.artist||'',language:t.language,genre:t.genre,
   views:t.views||0,likes:t.likes||0,downloads:t.downloads||0,downloadAllowed:t.downloadAllowed===true,hasScore:!!t.score,
   durationSeconds:t.durationSeconds||null};
}

/** Public radio stories are displayed only after express consent and host approval. */
const visibleStory=s=>s.consent===true&&(s.status==='approved'||s.status==='broadcast');
const storySummary=s=>({
 id:s.id,author:s.author,body:s.body,requestTitle:s.requestTitle||'',
 at:s.at,views:s.views||0,likes:s.likes||0,downloadAllowed:s.downloadAllowed===true,
 status:s.status
});
app.get('/api/stories/public',(req,res)=>{
 res.json({stories:db.stories.filter(visibleStory).slice(-100).reverse().map(storySummary)});
});
app.get('/api/stories/public/:id',(req,res)=>{
 const s=db.stories.find(x=>x.id===req.params.id&&visibleStory(x));
 if(!s)return res.sendStatus(404);
 res.json(storySummary(s));
});
app.post('/api/story-view/:id',limiter('storyView',180,3600000),(req,res)=>{
 const s=db.stories.find(x=>x.id===req.params.id&&visibleStory(x));
 if(!s)return res.sendStatus(404);
 const key=ipHash(req),now=Date.now();
 const existing=db.storyViews.find(x=>x.story===s.id&&x.key===key);
 if(!existing||now-existing.at>6*3600000){
   s.views=(s.views||0)+1;
   if(existing)existing.at=now;else db.storyViews.push({story:s.id,key,at:now});
   db.storyViews=db.storyViews.filter(x=>x.at>now-24*3600000).slice(-20000);
   persist();
 }
 res.json({views:s.views});
});
app.post('/api/story-like/:id',limiter('storyLike',50,3600000),(req,res)=>{
 const s=db.stories.find(x=>x.id===req.params.id&&visibleStory(x));
 if(!s)return res.sendStatus(404);
 const key=ipHash(req);
 const i=db.storyLikes.findIndex(x=>x.story===s.id&&x.key===key);
 if(i<0){db.storyLikes.push({story:s.id,key});s.likes=(s.likes||0)+1;}
 else{db.storyLikes.splice(i,1);s.likes=Math.max(0,(s.likes||0)-1);}
 persist();res.json({likes:s.likes,liked:i<0});
});
app.get('/api/story-comments/:id',(req,res)=>{
 const s=db.stories.find(x=>x.id===req.params.id&&visibleStory(x));
 if(!s)return res.sendStatus(404);
 res.json({comments:db.storyComments.filter(c=>c.story===s.id&&c.status==='public').slice(-100)
  .map(c=>({id:c.id,parentId:c.parentId||null,author:c.author,body:c.body,at:c.at}))});
});
app.post('/api/story-comments/:id',limiter('storyComment',8,3600000),(req,res)=>{
 const s=db.stories.find(x=>x.id===req.params.id&&visibleStory(x));
 if(!s)return res.sendStatus(404);
 const body=clean(req.body.body,700),author=clean(req.body.author,32)||'청취자',
    parentId=clean(req.body.parentId,60);
 if(body.length<2)return res.status(400).json({error:'댓글 내용을 입력해 주세요'});
 if(parentId&&!db.storyComments.some(c=>c.id===parentId&&c.story===s.id&&c.status==='public'))
    return res.status(400).json({error:'답글을 달 수 없는 댓글입니다'});
 db.storyComments.push({id:crypto.randomUUID(),story:s.id,parentId:parentId||null,author,body,at:Date.now(),status:'pending'});
 persist();res.status(201).json({status:'moderation-pending'});
});
app.get('/api/story-download/:id',(req,res)=>{
 const s=db.stories.find(x=>x.id===req.params.id&&visibleStory(x)&&x.downloadAllowed===true);
 if(!s)return res.sendStatus(404);
 const text='MARU RADIO 사연\n작성자: '+s.author+'\n\n'+s.body+'\n';
 res.set('Content-Type','text/plain; charset=utf-8');
 res.set('Content-Disposition','attachment; filename="MARU-story-'+s.id+'.txt"');
 res.send(text);
});
app.get('/api/tracks',(req,res)=>res.json({tracks:db.tracks.filter(t=>t.status==='public').slice(0,400).map(visibleTrack)}));
app.get('/api/tracks/:id',(req,res)=>{
 const t=db.tracks.find(x=>x.id===req.params.id&&x.status==='public');
 if(!t)return res.sendStatus(404);
 res.json(visibleTrack(t));
});
app.get('/api/audio/:id',(req,res)=>{
 const t=db.tracks.find(x=>x.id===req.params.id&&x.status==='public');
 if(!t)return res.sendStatus(404);
 res.type(t.ext==='.mp3'?'audio/mpeg':'audio/mp4');
 res.sendFile(path.join(mediaDir,t.file));
});
app.get('/api/download/:id',(req,res)=>{
 const t=db.tracks.find(x=>x.id===req.params.id&&x.status==='public'&&x.downloadAllowed===true);
 if(!t)return res.sendStatus(404);
 t.downloads=(t.downloads||0)+1;persist();
 res.download(path.join(mediaDir,t.file),t.title+t.ext);
});
app.get('/api/score/:id',(req,res)=>{
 const t=db.tracks.find(x=>x.id===req.params.id&&x.status==='public'&&x.score);
 if(!t)return res.sendStatus(404);
 res.sendFile(path.join(mediaDir,t.score));
});
app.post('/api/view/:id',limiter('view',180,3600000),(req,res)=>{
 const t=db.tracks.find(x=>x.id===req.params.id&&x.status==='public');
 if(!t)return res.sendStatus(404);
 const key=ipHash(req),now=Date.now();
 const idx=db.views.findIndex(x=>x.track===t.id&&x.key===key);
 if(idx<0||now-db.views[idx].at>6*3600000){
  t.views=(t.views||0)+1;if(idx<0)db.views.push({track:t.id,key,at:now});else db.views[idx].at=now;
  db.views=db.views.filter(x=>x.at>now-24*3600000).slice(-20000);persist();
 }
 res.json({views:t.views});
});
app.post('/api/like/:id',limiter('like',50,3600000),(req,res)=>{
 const t=db.tracks.find(x=>x.id===req.params.id&&x.status==='public');
 if(!t)return res.sendStatus(404);
 const key=ipHash(req),exists=db.likes.findIndex(x=>x.track===t.id&&x.key===key);
 if(exists===-1){db.likes.push({track:t.id,key});t.likes=(t.likes||0)+1;}
 else{db.likes.splice(exists,1);t.likes=Math.max(0,(t.likes||0)-1);}
 persist();res.json({likes:t.likes,liked:exists===-1});
});
app.get('/api/comments/:id',(req,res)=>{
 const t=db.tracks.find(x=>x.id===req.params.id&&x.status==='public');
 if(!t)return res.sendStatus(404);
 res.json({comments:db.comments.filter(x=>x.track===t.id&&x.status==='public').slice(-100)
   .map(x=>({id:x.id,author:x.author,body:x.body,at:x.at}))});
});
app.post('/api/comments/:id',limiter('comments',8,3600000),(req,res)=>{
 const t=db.tracks.find(x=>x.id===req.params.id&&x.status==='public');
 if(!t)return res.sendStatus(404);
 const body=clean(req.body.body,700),author=clean(req.body.author,32)||'청취자';
 if(body.length<2)return res.status(400).json({error:'댓글 내용을 입력하세요'});
 db.comments.push({id:crypto.randomUUID(),track:t.id,author,body,at:Date.now(),status:'pending'});
 persist();res.status(201).json({status:'moderation-pending'});
});
app.post('/api/stories',limiter('stories',5,3600000),upload.single('attachment'),async(req,res)=>{
 try{
  const body=clean(req.body.body,2800),author=clean(req.body.author,32)||'익명',
      requestTitle=clean(req.body.songRequest,130);
  if(body.length<5){if(req.file)fs.unlinkSync(req.file.path);return res.status(400).json({error:'사연이 짧습니다'});}
  if(req.body.publishConsent!=='true'){if(req.file)fs.unlinkSync(req.file.path);return res.status(400).json({error:'사연 방송 공개 동의가 필요합니다'});}
  const story={id:crypto.randomUUID(),author,body,requestTitle,at:Date.now(),status:'pending',consent:true,attachment:null,views:0,likes:0,downloadAllowed:false};
  if(req.file){
    const ext=path.extname(req.file.originalname||'').toLowerCase();
    if(!['.mp3','.m4a','.mp4','.jpg','.jpeg','.png','.webp','.pdf'].includes(ext)||
      !magic(req.file.path,ext))throw new Error('첨부 형식 미지원');
    const sc=scan(req.file.path);
    story.attachment={file:path.basename(req.file.path),ext,scanned:sc.ok,scanResult:sc.detail,
      originalName:clean(req.file.originalname,120)};
  }
  db.stories.push(story);persist();res.status(201).json({receipt:story.id,status:'pending'});
 }catch(e){if(req.file&&fs.existsSync(req.file.path))fs.unlinkSync(req.file.path);
   res.status(400).json({error:'첨부 검사 실패 또는 형식 오류'});}
});

/** Listener song intake, never auto-published. No admin token in public UI. */
app.post('/api/guest-upload',limiter('guestUpload',3,3600000),upload.single('audio'),async(req,res)=>{
 try{
  if(!req.file)return res.status(400).json({error:'원곡 파일 없음'});
  if(req.body.rights_confirmed!=='true')throw new Error('rights');
  const ext=path.extname(req.file.originalname||'').toLowerCase();
  if(!['.mp3','.m4a'].includes(ext)||!magic(req.file.path,ext))throw new Error('bad audio');
  const {sha,bytes}=await hashFile(req.file.path);
  const already=db.tracks.find(t=>t.sha===sha);
  if(already){fs.unlinkSync(req.file.path);return res.status(200).json({receipt:already.id,status:'already-received'});}
  const result=scan(req.file.path);
  const id=crypto.randomUUID(),saved=id+ext;
  fs.renameSync(req.file.path,path.join(mediaDir,saved));
  db.tracks.push({id,sha,file:saved,ext,bytes,
   title:clean(req.body.title,140)||'제목 미확인',
   artist:clean(req.body.artist,60)||'익명 청취자',
   language:'검토필요',genre:clean(req.body.genre,40)||'미확인',
   rightsConfirmed:true,scanClean:result.ok,scanResult:result.detail,
   status:'pending',downloadAllowed:false,views:0,likes:0,downloads:0,
   score:null,createdAt:Date.now(),origin:'listener'});
  persist();res.status(201).json({receipt:id,status:'pending',scanClean:result.ok});
 }catch(e){
  if(req.file&&fs.existsSync(req.file.path))fs.unlinkSync(req.file.path);
  res.status(400).json({error:'원곡 형식 또는 권리 동의를 확인하세요'});
 }
});
app.post('/api/upload',admin,limiter('adminUpload',350,3600000),upload.single('audio'),async(req,res)=>{
 try{
  if(!req.file)return res.status(400).json({error:'원곡 파일 없음'});
  if(String(req.body.rights_confirmed)!=='true')throw new Error('rights');
  const ext=path.extname(req.file.originalname||'').toLowerCase();
  if(!['.mp3','.m4a'].includes(ext)||!magic(req.file.path,ext))throw new Error('invalid audio');
  const {sha,bytes}=await hashFile(req.file.path);
  if(sha!==req.body.sha256)throw new Error('sha mismatch');
  const duplicate=db.tracks.find(t=>t.sha===sha);
  if(duplicate){fs.unlinkSync(req.file.path);return res.status(200).json({id:duplicate.id,duplicate:true,status:duplicate.status});}
  const sc=scan(req.file.path);
  const id=crypto.randomUUID(),newName=id+ext;
  fs.renameSync(req.file.path,path.join(mediaDir,newName));
  const t={id,sha,file:newName,ext,bytes,title:clean(req.body.title,140)||'제목 없음',
    artist:'',language:clean(req.body.language,32)||'검토필요',
    genre:clean(req.body.genre,40)||'미확인',rightsConfirmed:true,scanClean:sc.ok,
    scanResult:sc.detail,status:'pending',downloadAllowed:false,
    views:0,likes:0,downloads:0,score:null,createdAt:Date.now()};
  db.tracks.push(t);persist();res.status(201).json({id,status:t.status,scanClean:sc.ok});
 }catch(e){if(req.file&&fs.existsSync(req.file.path))fs.unlinkSync(req.file.path);
   res.status(400).json({error:'파일 형식, 권리, SHA-256 또는 백신 검사를 확인하세요.'});}
});
app.get('/api/admin/tracks',admin,(req,res)=>res.json({tracks:db.tracks.map(t=>({
 id:t.id,title:t.title,language:t.language,genre:t.genre,status:t.status,
 rightsConfirmed:t.rightsConfirmed,scanClean:t.scanClean,scanResult:t.scanResult,downloads:t.downloads
}))}));
app.post('/api/admin/tracks/:id/review',admin,(req,res)=>{
 const t=db.tracks.find(x=>x.id===req.params.id);if(!t)return res.sendStatus(404);
 const publish=String(req.body.action)==='publish';
 if(publish&&(!t.rightsConfirmed||!t.scanClean))
  return res.status(409).json({error:'저작권 또는 보안 검사가 통과되지 않았습니다'});
 t.status=publish?'public':'rejected';
 t.downloadAllowed=publish&&req.body.downloadAllowed===true;
 db.audit.push({id:t.id,action:t.status,at:Date.now()});persist();
 res.json({status:t.status,downloadAllowed:t.downloadAllowed});
});

app.post('/api/admin/tracks/:id/score',admin,upload.single('score'),(req,res)=>{
 const t=db.tracks.find(x=>x.id===req.params.id);
 if(!t){if(req.file)fs.unlinkSync(req.file.path);return res.sendStatus(404);}
 try{
   if(!req.file)throw new Error('no score');
   if(req.file.size>15*1024*1024)throw new Error('too big');
   const ext=path.extname(req.file.originalname||'').toLowerCase();
   if(!['.pdf','.png','.jpg','.jpeg','.webp'].includes(ext)||!magic(req.file.path,ext))throw new Error('bad magic');
   const result=scan(req.file.path);
   if(!result.ok)throw new Error('scanner unavailable or unsafe');
   const saved=crypto.randomUUID()+ext;
   fs.renameSync(req.file.path,path.join(mediaDir,saved));
   if(t.score){try{fs.unlinkSync(path.join(mediaDir,t.score));}catch(e){}}
   t.score=saved;persist();res.json({status:'ok'});
 }catch(e){
   if(req.file&&fs.existsSync(req.file.path))fs.unlinkSync(req.file.path);
   res.status(409).json({error:'악보 파일 형식·백신 검사 확인 필요'});
 }
});
app.get('/api/admin/stories',admin,(req,res)=>res.json({stories:db.stories.map(s=>({
 id:s.id,author:s.author,body:s.body,requestTitle:s.requestTitle,status:s.status,
 hasAttachment:!!s.attachment,scanClean:s.attachment?s.attachment.scanned:true,
 consent:s.consent===true,views:s.views||0,likes:s.likes||0,downloadAllowed:s.downloadAllowed===true
}))}));
app.post('/api/admin/stories/:id/review',admin,(req,res)=>{
 const s=db.stories.find(x=>x.id===req.params.id);if(!s)return res.sendStatus(404);
 const action=clean(req.body.action,12);
 if(!['approve','reject','broadcast'].includes(action))return res.sendStatus(400);
 if(action!=='reject'&&(!s.consent||(s.attachment&&!s.attachment.scanned)))return res.status(409).json({error:'사연 동의 또는 첨부 보안 검사 미통과'});
 s.status=action==='approve'?'approved':action==='broadcast'?'broadcast':'rejected';
 s.downloadAllowed=action!=='reject'&&req.body.downloadAllowed===true;
 db.audit.push({id:s.id,action:s.status,at:Date.now()});persist();res.json({status:s.status,downloadAllowed:s.downloadAllowed});
});
app.get('/api/admin/comments',admin,(req,res)=>res.json({comments:db.comments.filter(c=>c.status==='pending')}));
app.post('/api/admin/comments/:id/review',admin,(req,res)=>{
 const c=db.comments.find(x=>x.id===req.params.id);if(!c)return res.sendStatus(404);
 c.status=req.body.action==='approve'?'public':'rejected';persist();res.json({status:c.status});
});
app.get('/api/admin/story-comments',admin,(req,res)=>{
 res.json({comments:db.storyComments.filter(x=>x.status==='pending')});
});
app.post('/api/admin/story-comments/:id/review',admin,(req,res)=>{
 const c=db.storyComments.find(x=>x.id===req.params.id);
 if(!c)return res.sendStatus(404);
 const action=req.body.action;
 if(action!=='approve'&&action!=='reject')return res.sendStatus(400);
 c.status=action==='approve'?'public':'rejected';
 db.audit.push({id:c.id,action:c.status,at:Date.now()});persist();
 res.json({status:c.status});
});
app.get('/api/admin/attachment/:id',admin,(req,res)=>{
 const s=db.stories.find(x=>x.id===req.params.id);
 if(!s||!s.attachment||!s.attachment.scanned)return res.sendStatus(404);
 res.type('application/octet-stream');
 res.download(path.join(quarantine,s.attachment.file),s.attachment.originalName);
});
app.use((err,req,res,next)=>res.status(err instanceof multer.MulterError?413:500)
   .json({error:'파일 크기/요청 형식 오류'}));
const port=parseInt(process.env.PORT||'8080',10);
if(require.main===module)app.listen(port,()=>console.log('MARU server listening on '+port));
module.exports=app;