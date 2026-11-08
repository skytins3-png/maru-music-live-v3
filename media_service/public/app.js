'use strict';
const $=id=>document.getElementById(id);
let tracks=[],selected=null,selectedStory=null;
const pages=['songs','detail','story','publicStories','storyDetail','submit','downloads'];
function message(s){$('notice').textContent=s;}
function view(name){
 for(const p of pages)$(p+'Panel').classList.toggle('hidden',p!==name);
 if(name==='downloads')renderDownloads();
}
async function api(path,opts){
 const r=await fetch(path,opts);
 const data=await r.json().catch(()=>({error:'서버 응답 형식 오류'}));
 if(!r.ok)throw new Error(data.error||'서버 오류 '+r.status);
 return data;
}
async function refresh(){
 try{
  const d=await api('/api/tracks');tracks=d.tracks||[];
  renderList();message('공개 승인된 노래 '+tracks.length+'곡');
 }catch(e){message('목록을 불러올 수 없습니다: '+e.message);}
}
function matching(){
 const q=$('search').value.trim().toLowerCase(),category=$('language').value;
 return tracks.filter(t=>(!category||t.language===category||t.genre===category)
   &&(!q||(t.title+' '+t.artist).toLowerCase().includes(q)));
}
function renderList(){
 const box=$('songList');box.replaceChildren();
 const matches=matching();
 if(!matches.length){const n=document.createElement('p');n.textContent='아직 공개된 곡이 없습니다.';box.append(n);return;}
 for(const t of matches){
  const card=document.createElement('div');card.className='song';
  const info=document.createElement('div');info.className='info';
  const name=document.createElement('strong');name.textContent=t.title;
  const sub=document.createElement('small');sub.textContent=(t.artist||'MARU')+' · '+t.language+' · 조회 '+t.views+' · ♥ '+t.likes;
  info.append(name,sub);
  const b=document.createElement('button');b.textContent='상세 보기';b.addEventListener('click',()=>detail(t.id));
  card.append(info,b);box.append(card);
 }
}
async function detail(id){
 try{
  selected=await api('/api/tracks/'+encodeURIComponent(id));
  $('detailTitle').textContent=selected.title;
  $('detailMeta').textContent=(selected.artist||'MARU')+' · '+selected.language+' · '+selected.genre;
  $('statsViews').textContent='조회 '+selected.views;
  $('statsLikes').textContent='좋아요 '+selected.likes;
  $('player').src='/api/audio/'+encodeURIComponent(id);
  $('downloadLink').hidden=!selected.downloadAllowed;
  $('downloadLink').href='/api/download/'+encodeURIComponent(id);
  $('scoreLink').hidden=!selected.hasScore;
  $('scoreLink').href='/api/score/'+encodeURIComponent(id);
  $('comments').replaceChildren();
  view('detail');
  await api('/api/view/'+encodeURIComponent(id),{method:'POST'}).then(x=>$('statsViews').textContent='조회 '+x.views).catch(()=>{});
  const data=await api('/api/comments/'+encodeURIComponent(id));
  for(const c of data.comments||[]){
    const el=document.createElement('div');el.className='comment';
    const strong=document.createElement('strong');strong.textContent=c.author;
    const p=document.createElement('p');p.textContent=c.body;el.append(strong,p);$('comments').append(el);
  }
 }catch(e){message('상세화면 오류: '+e.message);}
}
async function submitForm(form,url){
 const b=form.querySelector('button[type=submit]');b.disabled=true;
 try{
  const r=await fetch(url,{method:'POST',body:new FormData(form)});
  const d=await r.json().catch(()=>({}));
  if(!r.ok)throw new Error(d.error||'접수 실패');
  form.reset();message('접수 완료 · 운영자 검토 대기 · 번호 '+(d.receipt||d.id||''));
 }catch(e){message('접수 실패: '+e.message);}finally{b.disabled=false;}
}
document.querySelectorAll('[data-page]').forEach(b=>b.addEventListener('click',()=>view(b.dataset.page)));
$('refresh').addEventListener('click',refresh);
$('search').addEventListener('input',renderList);
$('language').addEventListener('change',renderList);
$('backToSongs').addEventListener('click',()=>view('songs'));
$('storyForm').addEventListener('submit',e=>{e.preventDefault();submitForm(e.currentTarget,'/api/stories');});
$('browseStories').addEventListener('click',loadPublicStories);
$('backStoryWrite').addEventListener('click',()=>view('story'));
$('backStoryList').addEventListener('click',loadPublicStories);
async function loadPublicStories(){
 try{
  const data=await api('/api/stories/public');
  const area=$('publicStoryList');area.replaceChildren();
  for(const s of data.stories||[]){
   const card=document.createElement('div');card.className='song';
   const info=document.createElement('div');info.className='info';
   const name=document.createElement('strong');name.textContent=s.author+'님의 사연';
   const summary=document.createElement('small');
   summary.textContent=s.body.slice(0,85)+(s.body.length>85?'…':'');
   info.append(name,summary);
   const button=document.createElement('button');button.textContent='미리보기 · 댓글';
   button.addEventListener('click',()=>openStory(s.id));
   card.append(info,button);area.append(card);
  }
  if(!(data.stories||[]).length){
    const empty=document.createElement('p');empty.textContent='승인된 공개 사연이 없습니다.';area.append(empty);
  }
  view('publicStories');
 }catch(e){message('사연 불러오기 실패: '+e.message);}
}
async function openStory(id){
 try{
  selectedStory=await api('/api/stories/public/'+encodeURIComponent(id));
  const s=selectedStory;
  $('publicStoryTitle').textContent=s.author+'님의 사연';
  $('publicStoryBody').textContent=s.body;
  $('storyViews').textContent='조회 '+s.views;
  $('storyLikes').textContent='좋아요 '+s.likes;
  $('storyDownload').hidden=!s.downloadAllowed;
  $('storyDownload').href='/api/story-download/'+encodeURIComponent(id);
  $('storyReplyId').value='';
  view('storyDetail');
  await api('/api/story-view/'+encodeURIComponent(id),{method:'POST'})
   .then(x=>$('storyViews').textContent='조회 '+x.views).catch(()=>{});
  await refreshStoryComments();
 }catch(e){message('사연 미리보기 오류: '+e.message);}
}
async function refreshStoryComments(){
 const box=$('storyComments');box.replaceChildren();
 if(!selectedStory)return;
 const data=await api('/api/story-comments/'+encodeURIComponent(selectedStory.id));
 for(const c of data.comments||[]){
  const row=document.createElement('div');row.className='comment';
  const name=document.createElement('strong');name.textContent=(c.parentId?'↳ ':'')+c.author;
  const body=document.createElement('p');body.textContent=c.body;
  const reply=document.createElement('button');reply.className='small';reply.textContent='답글';
  reply.addEventListener('click',()=>{
    $('storyReplyId').value=c.id;
    $('storyCommentBody').focus();
    message(c.author+'님의 댓글에 답글을 작성합니다.');
  });
  row.append(name,body,reply);box.append(row);
 }
}
$('storyLikeBtn').addEventListener('click',async()=>{
 if(!selectedStory)return;
 try{
   const d=await api('/api/story-like/'+encodeURIComponent(selectedStory.id),{method:'POST'});
   $('storyLikes').textContent='좋아요 '+d.likes;
 }catch(e){message(e.message);}
});
$('storyShare').addEventListener('click',()=>{
 if(!selectedStory)return;
 const url=location.origin+'/index.html#story-'+encodeURIComponent(selectedStory.id);
 if(navigator.share)navigator.share({title:'MARU RADIO 사연',url}).catch(()=>{});
 else navigator.clipboard?.writeText(url).then(()=>message('사연 주소를 복사했습니다.')).catch(()=>message(url));
});
$('storyCommentForm').addEventListener('submit',async e=>{
 e.preventDefault();if(!selectedStory)return;
 try{
  await api('/api/story-comments/'+encodeURIComponent(selectedStory.id),{
   method:'POST',headers:{'Content-Type':'application/json'},
   body:JSON.stringify({author:$('storyCommentAuthor').value,
    body:$('storyCommentBody').value,parentId:$('storyReplyId').value})
  });
  $('storyCommentBody').value='';$('storyReplyId').value='';
  message('댓글·답글이 접수됐습니다. 운영자 승인 후 공개됩니다.');
 }catch(err){message(err.message);}
});
$('songForm').addEventListener('submit',e=>{e.preventDefault();submitForm(e.currentTarget,'/api/guest-upload');});
$('likeBtn').addEventListener('click',async()=>{
 if(!selected)return;
 try{const d=await api('/api/like/'+encodeURIComponent(selected.id),{method:'POST'});
  $('statsLikes').textContent='좋아요 '+d.likes;}catch(e){message(e.message);}
});
$('shareBtn').addEventListener('click',()=>{
 const url=location.href.split('#')[0]+'#song-'+(selected?selected.id:'');
 if(navigator.share)navigator.share({title:selected?.title||'MARU RADIO',url}).catch(()=>{});
 else navigator.clipboard?.writeText(url).then(()=>message('공유 주소 복사')).catch(()=>message(url));
});
$('commentForm').addEventListener('submit',async e=>{
 e.preventDefault();if(!selected)return;
 try{await api('/api/comments/'+encodeURIComponent(selected.id),{
   method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({
    author:$('commentAuthor').value,body:$('commentBody').value})
  });
  $('commentBody').value='';message('댓글이 접수됐습니다. 승인 후 공개됩니다.');
 }catch(err){message(err.message);}
});
function renderDownloads(){
 const box=$('downloads');box.replaceChildren();
 for(const t of tracks.filter(t=>t.downloadAllowed)){
  const line=document.createElement('div');line.className='song';
  const label=document.createElement('span');label.textContent=t.title;
  const a=document.createElement('a');a.textContent='저장';a.href='/api/download/'+encodeURIComponent(t.id);a.setAttribute('download','');
  line.append(label,a);box.append(line);
 }
 if(!box.childNodes.length){const n=document.createElement('p');n.textContent='다운로드 허용된 곡이 없습니다.';box.append(n);}
}
refresh().then(()=>{
 const id=location.hash.startsWith('#song-')?location.hash.slice(6):'';
 if(id)detail(id);
 else if(location.hash.startsWith('#story-'))openStory(location.hash.slice(7));
});