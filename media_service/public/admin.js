'use strict';
const $=id=>document.getElementById(id);
let secret='';
function message(s){$('message').textContent=s;}
async function api(url,method='GET',body){
 const r=await fetch(url,{method,headers:{'X-MARU-ADMIN':secret,
  ...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});
 const data=await r.json().catch(()=>({}));
 if(!r.ok)throw new Error(data.error||'서버 오류 '+r.status);
 return data;
}
function node(type,text,cls){
 const n=document.createElement(type);if(cls)n.className=cls;if(text!==undefined)n.textContent=text;return n;
}
function button(container,text,fn){
 const b=node('button',text);b.style.margin='6px 6px 3px 0';
 b.addEventListener('click',async()=>{
   b.disabled=true;
   try{await fn();await refresh();}
   catch(e){message(e.message);}
   finally{b.disabled=false;}
 });
 container.append(b);
}
function fillTracks(list){
 const container=$('tracks');container.replaceChildren();
 for(const t of list){
  const card=node('div',undefined,'song');const info=node('div',undefined,'info');
  info.append(node('strong',t.title),
   node('small','분류 '+t.language+' / '+t.genre+' · 상태 '+t.status+' · 검사 '+(t.scanClean?'완료':'미완료')+' · '+t.scanResult));
  const actions=node('div');
  const download=document.createElement('label');download.style.display='block';
  const checkbox=document.createElement('input');checkbox.type='checkbox';checkbox.checked=false;
  checkbox.style.width='auto';download.append(checkbox,node('span',' 다운로드 허용'));actions.append(download);
  button(actions,'공개 승인',()=>api('/api/admin/tracks/'+encodeURIComponent(t.id)+'/review','POST',{action:'publish',downloadAllowed:checkbox.checked}));
  button(actions,'거절',()=>api('/api/admin/tracks/'+encodeURIComponent(t.id)+'/review','POST',{action:'reject'}));
  const score=node('input');score.type='file';score.accept='.pdf,.png,.jpg,.jpeg,.webp';score.setAttribute('aria-label','악보 첨부');
  actions.append(score);
  button(actions,'악보 등록',async()=>{
    const file=score.files[0];if(!file)throw new Error('악보 파일을 선택하세요');
    const form=new FormData();form.append('score',file);
    const r=await fetch('/api/admin/tracks/'+encodeURIComponent(t.id)+'/score',{
      method:'POST',headers:{'X-MARU-ADMIN':secret},body:form});
    if(!r.ok){const d=await r.json().catch(()=>({}));throw new Error(d.error||'악보 등록 실패');}
  });
  card.append(info,actions);container.append(card);
 }
 if(!list.length)container.append(node('p','접수된 음원이 없습니다.'));
}
function fillStories(list){
 const c=$('stories');c.replaceChildren();
 for(const s of list){
  const card=node('div',undefined,'song');
  const area=node('div',undefined,'info');
  area.append(node('strong',s.author+' · '+s.status),
    node('p',s.body),node('small','신청곡: '+(s.requestTitle||'없음')+' / 첨부: '+(s.hasAttachment?'있음':'없음')+' / 검사: '+(s.scanClean?'통과':'미확인')));
  const actions=node('div');
  button(actions,'승인',()=>api('/api/admin/stories/'+encodeURIComponent(s.id)+'/review','POST',{action:'approve'}));
  button(actions,'거절',()=>api('/api/admin/stories/'+encodeURIComponent(s.id)+'/review','POST',{action:'reject'}));
  button(actions,'방송 완료',()=>api('/api/admin/stories/'+encodeURIComponent(s.id)+'/review','POST',{action:'broadcast'}));
  if(s.hasAttachment){
    const a=node('a','첨부파일 다운로드');a.href='/api/admin/attachment/'+encodeURIComponent(s.id);
    // Admin endpoints require X-MARU-ADMIN header, so use authorized fetch, not naked link.
    a.removeAttribute('href');a.addEventListener('click',async e=>{
      e.preventDefault();try{
        const r=await fetch('/api/admin/attachment/'+encodeURIComponent(s.id),{headers:{'X-MARU-ADMIN':secret}});
        if(!r.ok)throw new Error('첨부 열기 실패');
        const blob=await r.blob();const url=URL.createObjectURL(blob);const dl=document.createElement('a');
        dl.href=url;dl.download='MARU-story-attachment';dl.click();
        setTimeout(()=>URL.revokeObjectURL(url),30000);
      }catch(err){message(err.message);}
    });actions.append(a);
  }
  card.append(area,actions);c.append(card);
 }
 if(!list.length)c.append(node('p','접수된 사연이 없습니다.'));
}
function fillComments(list){
 const c=$('comments');c.replaceChildren();
 for(const item of list){
  const card=node('div',undefined,'song');const area=node('div',undefined,'info');
  area.append(node('strong',item.author),node('p',item.body));
  const actions=node('div');
  button(actions,'댓글 승인',()=>api('/api/admin/comments/'+encodeURIComponent(item.id)+'/review','POST',{action:'approve'}));
  button(actions,'거절',()=>api('/api/admin/comments/'+encodeURIComponent(item.id)+'/review','POST',{action:'reject'}));
  card.append(area,actions);c.append(card);
 }
 if(!list.length)c.append(node('p','검토 대기 댓글이 없습니다.'));
}
async function refresh(){
 const [t,s,c]=await Promise.all([
   api('/api/admin/tracks'),api('/api/admin/stories'),api('/api/admin/comments')
 ]);
 fillTracks(t.tracks||[]);fillStories(s.stories||[]);fillComments(c.comments||[]);
 $('tracksPanel').classList.remove('hidden');$('storiesPanel').classList.remove('hidden');
 $('commentsPanel').classList.remove('hidden');
 message('검토 자료 불러오기 완료 · 공개 전 검사 상태를 확인하세요.');
}
$('login').addEventListener('click',async()=>{
 secret=$('token').value.trim();$('token').value='';
 if(secret.length<32){message('32자리 이상의 관리자 키가 필요합니다.');return;}
 try{await refresh();}catch(e){secret='';message('로그인 실패: '+e.message);}
});