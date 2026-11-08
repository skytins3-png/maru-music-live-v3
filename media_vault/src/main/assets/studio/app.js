"use strict";
const native=window.Maru||null;
const $=id=>document.getElementById(id);
const icon=(name,cls="")=>'<svg aria-hidden="true" class="icon '+cls+'"><use href="#i-'+name+'"></use></svg>';
const escapeHTML=v=>String(v??"").replace(/[&<>"']/g,ch=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[ch]));
const call=(name,...args)=>{try{return native&&typeof native[name]==="function"?native[name](...args):null;}catch(e){notify("기기 연결 오류: "+e.message);return null;}};
let tracks=[],stories=[],page="home",selectedSong=null,selectedStory=null,trackTab="전체",
    storyTab="전체",detailTab="댓글",downloadTab="다운로드 목록",replyTo=0,storyReplyTo=0,
    query="",uploadId=null,fileLabel="음원 파일을 선택하세요",storyFileLabel="선택한 첨부파일 없음",
    demoMode=false,serverInfo="",toastTimer=null;
const demoSongs=[
 {id:-1,title:"그리운 사람",artist:"아환",views:12548,liked:1236,genre:"발라드",language:"한국노래",size:7800000},
 {id:-2,title:"봄날의 추억",artist:"윤솔",views:8932,liked:892,genre:"발라드",language:"한국노래",size:6100000},
 {id:-3,title:"별이 된 너",artist:"하린이",views:25310,liked:2417,genre:"발라드",language:"한국노래",size:9400000},
 {id:-4,title:"바람의 노래",artist:"지훈",views:6425,liked:535,genre:"포크",language:"한국노래",size:6900000},
 {id:-5,title:"수국의 계절",artist:"세린",views:4976,liked:412,genre:"발라드",language:"한국노래",size:6600000}
];
const demoStories=[
 {id:-1,author:"바람소리",body:"힘이 되는 노래였어요. 요즘 힘든 일이 많았는데 마루라디오에서 좋은 음악을 만나 위로를 받았습니다.",status:"승인",views:4892,liked:256},
 {id:-2,author:"별빛나무",body:"아버지께 드리는 신청곡. 늘 건강하시고 행복하게 노래 들어주세요.",status:"검토대기",views:2417,liked:132},
 {id:-3,author:"고마운 사람에게",body:"항상 곁에서 응원해준 친구에게 전하고 싶은 작은 이야기입니다.",status:"승인",views:6530,liked:318}
];
function liveSongs(){return demoMode?demoSongs:tracks;}
function liveStories(){return demoMode?demoStories:stories;}
function demo(item){return item&&Number(item.id)<0;}
function getSong(id){return liveSongs().find(t=>Number(t.id)===Number(id));}
function getStory(id){return liveStories().find(s=>Number(s.id)===Number(id));}
function readData(){
 try{tracks=JSON.parse(call("tracks")||"[]");stories=JSON.parse(call("stories")||"[]");
 serverInfo=call("serverInfo")||"";}catch(e){tracks=[];stories=[];}
}
function notify(message){
 const t=$("toast");if(!t)return;
 t.textContent=String(message);t.classList.add("visible");
 clearTimeout(toastTimer);toastTimer=setTimeout(()=>t.classList.remove("visible"),3400);
}
window.notifyNative=notify;
window.syncNative=()=>{readData();if(!["upload","story-compose"].includes(page))render();};
window.songChosen=(id,title)=>{
 uploadId=Number(id);fileLabel=String(title||"선택됨");
 const s=$("chosenSong");if(s)s.textContent=fileLabel;
 const titleInput=$("uploadTitle");if(titleInput&&!titleInput.value)titleInput.value=fileLabel;
 notify("음원 검사 완료. 제목·장르를 확인해 등록하세요.");
};
window.storyFileChosen=file=>{storyFileLabel=file;const name=$("storyChosen");if(name)name.textContent=file;};
window.goBack=()=>{if(page==="detail")go("songs");else if(page==="story-detail")go("stories");
 else if(page==="home")notify("MARU RADIO 홈입니다.");else go("home");};
// The top-level go() declaration is already window.go. Never overwrite it.
window.setDemo=on=>{demoMode=!!on;go("home");};
function footer(){
 const active={"home":"home","songs":"songs","detail":"songs","upload":"songs","downloads":"songs",
   "stories":"stories","story-compose":"stories","story-detail":"stories","profile":"profile"}[page]||"home";
 $("bottom-nav").innerHTML=[["home","home","홈"],["songs","music","노래"],["stories","envelope","사연"],["profile","person","내정보"]]
  .map(([dest,glyph,label])=>'<button type="button" data-go="'+dest+'" class="'+(active===dest?"active":"")+'" aria-label="'+label+'">'+icon(glyph)+'<span>'+label+'</span></button>').join("");
 const hide=["upload","story-compose","profile"].includes(page);
 $("bottom-nav").style.display=hide?"none":"";
 for(const id of ["upload","story-compose","profile"]){const el=$("page-"+id);if(el)el.classList.toggle("no-nav",hide&&page===id);}
}
function go(dest,id){
 if(dest==="detail"){
   const found=getSong(id);
   if(!found){notify("노래를 찾지 못했습니다.");return;}
   selectedSong=Number(id);detailTab="댓글";replyTo=0;
   if(!demo(found))call("viewed","song",Number(id));
 }
 if(dest==="story-detail"){
   const found=getStory(id);if(!found){notify("사연을 찾지 못했습니다.");return;}
   selectedStory=Number(id);storyReplyTo=0;
   if(!demo(found))call("viewed","story",Number(id));
 }
 page=dest;render();
}
function render(){
 document.querySelectorAll(".screen").forEach(el=>el.classList.toggle("active",el.id==="page-"+page));
 footer();
 const fn={"home":renderHome,"songs":renderSongs,"detail":renderDetail,
  "upload":renderUpload,"downloads":renderDownloads,"story-compose":renderStoryCompose,
  "stories":renderStories,"story-detail":renderStoryDetail,"profile":renderProfile}[page];
 if(fn)fn();
 const el=$("page-"+page);if(el)el.scrollTop=0;
}
const topbar=(title,right="",back="home")=>'<header class="topbar"><button class="icon-btn" data-go="'+back+'" aria-label="뒤로가기">'+icon("back")+'</button><h2>'+escapeHTML(title)+'</h2>'+(right||'<span class="placeholder"></span>')+'</header>';
const picture=(s,i=0,large=false)=>{
 const cover=!demo(s)&&s&&s.hasCover?call("songCover",Number(s.id)):"";
 const idx=(Math.abs(Number(s?.id||i))||i)%5;
 const style=cover?'style="background-image:url('+escapeHTML(cover)+');background-size:cover;background-position:center"':"";
 return '<div class="song-art '+(cover?"actual":"art-"+["a","b","c","d","e"][idx])+'" '+style+'>'
  +(cover?"":icon("music"))+'</div>';
};
const demoBanner=()=>demoMode?'<div class="demo-flag">디자인 시연용 예시입니다. 실제 음원·조회수·댓글 데이터가 아닙니다.</div>':"";
function renderHome(){
 $("page-home").innerHTML='<div class="exact-home"><img src="../reference_home.webp" alt="MARU RADIO 노래와 사연 센터 홍보 디자인">'
  +'<button aria-label="설정" data-go="profile" style="left:87%;top:0%;width:13%;height:9%"></button>'
  +'<button aria-label="노래 보관함" data-go="songs" style="left:0%;top:34%;width:49%;height:24%"></button>'
  +'<button aria-label="노래 업로드" data-go="upload" style="left:51%;top:34%;width:49%;height:24%"></button>'
  +'<button aria-label="노래 다운로드" data-go="downloads" style="left:0%;top:59.5%;width:49%;height:24.5%"></button>'
  +'<button aria-label="사연 보내기" data-go="story-compose" style="left:51%;top:59.5%;width:49%;height:24.5%"></button>'
  +'<button aria-label="인기 노래 TOP 5" data-action="top5" style="left:0%;top:87.4%;width:100%;height:11%"></button></div>'
  +(tracks.length===0?'<p class="intro-note">등록곡 0곡 · 노래를 가져오면 실제 목록에 표시됩니다.</p>':
    '<p class="intro-note">보관 '+tracks.length+'곡 · 서버 전송 확인 '+tracks.filter(s=>s.uploadState==="UPLOADED").length+'곡</p>');
}
function tile(dest,glyph,title,subtitle,cls){
 return '<button class="home-tile '+cls+'" data-go="'+dest+'">'+icon(glyph,"big")+
  '<strong>'+title+'</strong><small>'+subtitle+'</small></button>';
}
function renderSongs(){
 const all=liveSongs().slice();
 let arr=all;
 if(trackTab==="인기")arr.sort((a,b)=>(b.views||0)-(a.views||0));
 else if(trackTab==="최신")arr.sort((a,b)=>Number(b.id)-Number(a.id));
 else if(trackTab==="내 노래")arr=arr.filter(t=>t.favorite||t.uploadState==="LOCAL");
 else if(!["전체","인기","최신","내 노래"].includes(trackTab))
   arr=arr.filter(t=>t.language===trackTab||t.genre===trackTab);
 if(query)arr=arr.filter(t=>(t.title+" "+t.artist+" "+t.genre).toLowerCase().includes(query.toLowerCase()));
 $("page-songs").innerHTML=topbar("노래 보관함",'<button class="icon-btn" data-action="searchSong">'+icon("search")+'</button>')
  +'<div class="pills">'+["전체","인기","최신","내 노래"].map(p=>'<button data-action="songFilter" data-value="'+p+'" class="'+(trackTab===p?"active":"")+'">'+p+'</button>').join("")+'</div>'
  +'<div class="controls-row"><span class="tiny">보관 '+tracks.length+'곡 · '+escapeHTML(trackTab)+' 분류 · '+arr.length+'곡 표시</span>'
  +'<button class="line-btn" data-action="chooseLanguage">장르·언어 필터 ▾</button></div>'
  +(query?'<p class="tiny">검색: '+escapeHTML(query)+' <button class="line-btn" data-action="clearSearch">해제</button></p>':"")
  +demoBanner()
  +(arr.length?'<div class="song-list">'+arr.map((s,i)=>songRow(s,i)).join("")+'</div>':
     '<div class="empty">'+icon("music","big")+'<h3>노래 보관함이 비어 있어요</h3><p>원곡 MP3·ZIP을 가져오면 여기에 표시됩니다.</p>'
    +'<button class="primary-btn" data-action="importFiles">'+icon("cloud")+' 노래 가져오기</button>'
    +'<button class="secondary-btn" style="margin-top:10px" data-action="importFolder">폴더 불러오기</button></div>')
   +'<div class="row" style="justify-content:center;flex-wrap:wrap;margin-top:12px"><button class="secondary-btn" data-action="importFiles">MP3·ZIP 가져오기</button>'
    +'<button class="secondary-btn" data-action="importManifest">107곡 분류표 적용</button></div>';
}
function songRow(s,i){
 return '<article class="song-card" data-action="openSong" data-id="'+s.id+'">'+picture(s,i)+
 '<div class="song-main" data-action="openSong" data-id="'+s.id+'"><h3>'+escapeHTML(s.title)+'</h3>'
 +'<div class="artist">'+escapeHTML(s.artist||"등록된 노래")+'</div>'
 +'<div class="song-stats"><span>'+icon("eye")+' '+(s.views||0).toLocaleString()+'</span>'
 +'<span class="liked">'+icon("heart")+' '+(s.liked||0).toLocaleString()+'</span></div></div>'
 +'<div class="song-controls"><button class="primary" data-action="download" data-id="'+s.id+'" aria-label="다운로드">'+icon("download")+'</button>'
 +'<button data-action="openSong" data-id="'+s.id+'" aria-label="곡 상세">'+icon("more")+'</button></div></article>';
}
function renderDetail(){
 const song=getSong(selectedSong);
 if(!song){go("songs");return;}
 const cover=!demo(song)&&song.hasCover?call("songCover",song.id):"";
 const detailStyle=cover?'style="background-image:url('+escapeHTML(cover)+')"':'';
 const comments=demo(song)?[]:JSON.parse(call("comments","song",song.id)||"[]");
 $("page-detail").innerHTML=topbar("곡 상세",'<button class="icon-btn" data-action="songOptions" aria-label="곡 관리">'+icon("more")+'</button>',"songs")
 +demoBanner()
 +'<div class="detail-banner" '+detailStyle+'><button class="play-central" data-action="preview" data-id="'+song.id+'">'+icon("play")+'</button>'
 +'<div class="detail-title"><h2>'+escapeHTML(song.title)+'</h2><p>'+escapeHTML(song.artist||"등록 음원")+'</p></div></div>'
 +'<div class="detail-counts"><span>'+icon("eye")+' '+(song.views||0).toLocaleString()+' 조회</span><span>'+icon("heart")+' '+(song.liked||0).toLocaleString()+' 좋아요</span></div>'
 +'<div class="actions-row">'+actionButton("download","다운로드","download",song.id)+actionButton("sheet","악보 보기","score",song.id)
 +actionButton("share","공유","share",song.id)+actionButton("bookmark","보관함 추가","favorite",song.id)+'</div>'
 +'<div class="detail-tabs">'+["댓글","곡정보","관련 노래"].map(t=>'<button data-action="detailTab" data-value="'+t+'" class="'+(detailTab===t?"active":"")+'">'+t+(t==="댓글"?" ("+comments.length+")":"")+'</button>').join("")+'</div>'
 +'<div id="detail-content">'+detailContent(song,comments)+'</div>';
}
function actionButton(glyph,label,action,id){
 return '<button data-action="'+action+'" data-id="'+id+'"><span class="iconframe">'+icon(glyph)+'</span><span>'+label+'</span></button>';
}
function detailContent(song,comments){
 if(detailTab==="곡정보")return '<section class="panel"><h3>곡정보</h3><p>제목: '+escapeHTML(song.title)+'</p>'
   +'<p>가수: '+escapeHTML(song.artist||"미등록")+'</p><p>장르: '+escapeHTML(song.genre||"미확인")+'</p>'
   +'<p>언어: '+escapeHTML(song.language||"미확인")+'</p>'
   +'<p>파일 크기: '+(Number(song.size||0)/1048576).toFixed(1)+'MB</p>'
   +'<div class="status-note">조회수는 이 기기의 미리듣기 기록이며 공개 서버 조회수와 다릅니다.</div></section>';
 if(detailTab==="관련 노래"){
   let other=liveSongs().filter(t=>t.id!==song.id&&t.language===song.language).slice(0,8);
   return other.length?'<div class="song-list">'+other.map(songRow).join("")+'</div>':
     '<div class="empty">같은 분류의 다른 노래가 없습니다.</div>';
 }
 const commentsBlock=comments.length?comments.map(c=>'<article class="comment"><div class="name">✿ '+escapeHTML(c.author||"청취자")+'</div><p>'+
   escapeHTML(c.body)+'</p><button data-action="reply" data-id="'+c.id+'" data-author="'+escapeHTML(c.author)+'">↳ 답글</button>'+
   (c.parent?'<span class="tiny"> · 답글</span>':"")+'</article>').join(""):'<p class="muted">첫 댓글을 남겨주세요.</p>';
 return commentsBlock+'<div class="comment-input"><input id="songComment" aria-label="댓글 입력" placeholder="'+(replyTo?"답글을 입력하세요...":"댓글을 남겨주세요...")+'">'
  +'<button aria-label="댓글 전송" data-action="submitSongComment">'+icon("share")+'</button></div>';
}
function renderUpload(){
 $("page-upload").innerHTML=topbar("노래 업로드","","home")
 +'<div class="filechoose">'+icon("music")+'<div class="info"><strong>음원 파일 선택</strong><small id="chosenSong">'+escapeHTML(fileLabel)+'</small></div>'
 +'<button data-action="chooseUpload">파일 선택</button></div>'
 +'<form id="uploadForm"><div class="field"><label>♬ 곡 제목 <span class="req">*</span></label><input class="textinput" name="title" id="uploadTitle" required maxlength="150" placeholder="곡 제목을 입력하세요"></div>'
 +'<div class="field"><label>♙ 가수(아티스트) <span class="req">*</span></label><input class="textinput" name="artist" required maxlength="60" placeholder="가수명을 입력하세요"></div>'
 +'<div class="field"><label>♧ 장르</label><select name="genre">'+["발라드","트로트","연주","록 발라드","중국 음악","한국 가요","서양 음악","컨트리 팝","미확인"].map(x=>'<option>'+x+'</option>').join("")+'</select></div>'
 +'<div class="field"><label>◎ 언어</label><select name="lang">'+["한국노래","중국노래","서양노래","일본노래","검토필요"].map(x=>'<option>'+x+'</option>').join("")+'</select></div>'
 +'<div class="filechoose">'+icon("image")+'<div class="info"><strong>커버 이미지</strong><small>선택 사항 · JPG, PNG 등</small></div><button type="button" data-action="chooseCover">이미지 선택</button></div>'
 +'<div class="filechoose">'+icon("sheet")+'<div class="info"><strong>악보 첨부</strong><small>선택 사항 · PDF</small></div><button type="button" data-action="chooseScore">파일 선택</button></div>'
 +'<label class="radio-line"><input type="checkbox" name="rights"> 본인 원곡이거나 업로드·방송 사용 권한이 있습니다.</label>'
 +'<div class="controls-row"><strong>업로드 진행률</strong><span class="tiny" id="uploadPct">0%</span></div><div class="progress"><span></span></div>'
 +'<div class="status-note">먼저 내 보관함에 저장합니다. 외부 서버 업로드는 내정보에서 HTTPS 서버와 인증키를 설정한 후 진행합니다.</div>'
 +'<button class="primary-btn" type="submit">'+icon("cloud")+' 업로드 준비 완료 · 보관함 등록</button></form>'
 +'<button class="secondary-btn" style="width:100%;margin-top:12px" data-action="publishSong">HTTPS 서버 업로드 (승인된 원곡)</button>';
}
function renderDownloads(){
 const songs=tracks.slice();
 const downloaded=songs.filter(s=>s.downloaded);
 const listed=downloadTab==="오프라인 저장"?downloaded:songs;
 const info=JSON.parse(call("deviceInfo")||"{}");
 const occupied=info.total?Math.round((info.used||0)/info.total*100):0;
 $("page-downloads").innerHTML=topbar("노래 다운로드","","home")
 +'<div class="pills">'+["다운로드 목록","오프라인 저장","저장 위치"].map(t=>'<button class="'+(downloadTab===t?"active":"")+'" data-action="downloadTab" data-value="'+t+'">'+t+'</button>').join("")+'</div>'
 +'<div class="panel"><div class="storage-line">'+icon("drive")+'<div class="right"><strong>기기 저장공간</strong>'
 +'<div class="tiny">사용 중 '+(info.used?Math.round(info.used/1073741824*10)/10:"?")+'GB / '+(info.total?Math.round(info.total/1073741824):"?")+'GB</div>'
 +'<div class="progress" style="margin-top:8px"><span style="width:'+occupied+'%"></span></div></div></div></div>'
 +(downloadTab==="저장 위치"?'<div class="panel"><h3>저장 위치</h3><p>노래에서 다운로드를 누르면 안드로이드 파일 저장 창이 열립니다. 휴대폰의 원하는 폴더를 선택할 수 있습니다.</p>'
  +'<button class="secondary-btn" data-go="songs">노래 보관함 열기</button></div>':
  '<div class="row-between" style="margin:13px 0"><strong>'+ (downloadTab==="오프라인 저장"?"저장한 노래":"보관된 노래")+' ('+listed.length+')</strong>'
  +'<button class="line-btn" data-action="stop" aria-label="재생 정지">재생 정지</button></div>'
  +(listed.length?'<div class="song-list">'+listed.map((s,i)=>downloadRow(s,i)).join("")+'</div>':
    '<div class="empty">저장된 노래가 없습니다.<br><br><button class="primary-btn" data-go="songs">노래 보관함 열기</button></div>'))
 +'<div class="status-note">원곡이 휴대폰 안에 존재해야 저장할 수 있습니다. 저작권과 다운로드 허용 범위를 확인하세요.</div>';
}
function downloadRow(s,i){
 return '<div class="song-card">'+picture(s,i)+'<div class="song-main"><h3>'+escapeHTML(s.title)+'</h3>'
 +'<div class="artist">'+escapeHTML(s.artist||"등록 음원")+'</div><div class="tiny">♫ '+(Number(s.size||0)/1048576).toFixed(1)+'MB · '+(s.downloaded?"저장됨":"내 보관함 원곡")+'</div></div>'
 +'<div class="song-controls"><button data-action="download" data-id="'+s.id+'" aria-label="파일 저장">'+icon("folder")+'</button>'
 +'<button data-action="openSong" data-id="'+s.id+'" aria-label="곡 상세">'+icon("more")+'</button></div></div>';
}
function renderStoryCompose(){
 $("page-story-compose").innerHTML=topbar("사연 보내기","","stories")
 +'<form id="storyForm"><div class="field"><label>♙ 닉네임 <span class="req">*</span></label>'
 +'<input class="textinput" name="nick" required minlength="2" maxlength="30" placeholder="사용할 닉네임을 입력하세요. (2~30자)"></div>'
 +'<div class="field"><label>▤ 사연 내용 <span class="req">*</span></label>'
 +'<textarea name="body" required minlength="5" maxlength="1000" placeholder="여러분의 이야기, 고민, 감사한 마음 등을 자유롭게 들려주세요. (최대 1,000자)" id="storyBody"></textarea>'
 +'<div class="tiny" style="text-align:right" id="storyLength">0 / 1,000</div></div>'
 +'<div class="field"><label>♬ 신청곡 (선택)</label><input class="textinput" name="request" maxlength="120" placeholder="신청하고 싶은 곡을 입력하세요."></div>'
 +'<div class="filechoose">'+icon("file")+'<div class="info"><strong>첨부 파일 (선택)</strong><small id="storyChosen">'+escapeHTML(storyFileLabel)+'</small></div>'
 +'<button type="button" data-action="chooseStoryFile">파일 선택</button></div>'
 +'<div class="tiny">사진, 음성, 영상, PDF · 최대 25MB · 외부 공개 시 추가 보안검사 필요</div>'
 +'<div class="field" style="margin-top:15px"><label>'+icon("lock")+' 공개 여부</label>'
 +'<label class="radio-line"><input type="radio" name="privacy" value="public" checked> 공개 (검토 후 방송 소개 가능)</label>'
 +'<label class="radio-line"><input type="radio" name="privacy" value="private"> 비공개 (운영자만 확인)</label></div>'
 +'<div class="status-note">현재 앱은 형의 휴대폰 내부에 사연을 저장합니다. 다른 청취자의 원격 접수는 별도 공개 서버가 필요합니다.</div>'
 +'<button class="primary-btn" type="submit">'+icon("envelope")+' 사연 접수</button></form>';
}
function renderStories(){
 const src=liveStories().slice();
 let arr=src;
 if(storyTab==="방송된 사연")arr=arr.filter(s=>["승인","방송완료"].includes(s.status));
 else if(storyTab==="대기중")arr=arr.filter(s=>["검토대기","예약"].includes(s.status));
 $("page-stories").innerHTML=topbar("사연 보관함",'<button class="icon-btn" data-go="story-compose">'+icon("envelope")+'</button>')
 +'<div class="pills">'+["전체","방송된 사연","대기중","내 사연"].map(t=>'<button class="'+(storyTab===t?"active":"")+'" data-action="storyFilter" data-value="'+t+'">'+t+'</button>').join("")+'</div>'
 +demoBanner()
 +(arr.length?'<div class="song-list">'+arr.map((s,i)=>storyRow(s,i)).join("")+'</div>':
  '<div class="empty">'+icon("envelope","big")+'<h3>접수된 사연이 없습니다.</h3><p>사연을 보내면 운영자 검토함에 저장됩니다.</p>'
  +'<button class="primary-btn" data-go="story-compose">사연 보내기</button></div>')
 +'<button class="primary-btn" data-go="story-compose" style="margin-top:14px">'+icon("envelope")+' 새 사연 작성</button>';
}
function storyRow(s,i){
 const st=s.status||"검토대기";
 const tagClass=st==="승인"||st==="방송완료"?"":st==="검토대기"?"blue":"gray";
 return '<article class="story-card" data-action="openStory" data-id="'+s.id+'">'
 +'<div class="song-art story-art art-'+["a","b","c","d","e"][i%5]+'">'+icon("envelope")+'</div>'
 +'<div class="song-main"><div class="row-between"><h3 class="ellip">'+escapeHTML(s.body.slice(0,20))+(s.body.length>20?"…":"")+'</h3>'
 +'<span class="tag '+tagClass+'">'+escapeHTML(st)+'</span></div>'
 +'<p>'+escapeHTML(s.author)+' · '+(st==="승인"?"공개 승인됨":"검토/예약 상태")+'</p>'
 +'<p class="ellip">'+escapeHTML(s.body.slice(0,56))+'</p>'
 +'<div class="story-counts"><span>'+icon("eye")+' '+(s.views||0)+'</span><span>'+icon("heart")+' '+(s.liked||0)+'</span></div></div>'
 +'<span style="font-size:22px">❯</span></article>';
}
function renderStoryDetail(){
 const s=getStory(selectedStory);
 if(!s){go("stories");return;}
 const list=demo(s)?[]:JSON.parse(call("comments","story",s.id)||"[]");
 $("page-story-detail").innerHTML=topbar("사연 상세","","stories")+demoBanner()
 +'<div class="panel"><div class="row-between"><h3>'+escapeHTML(s.author)+'님의 사연</h3><span class="tag">'+escapeHTML(s.status)+'</span></div>'
 +'<p style="white-space:pre-wrap;line-height:1.7">'+escapeHTML(s.body)+'</p><p class="tiny">'+icon("eye")+' '+(s.views||0)+' 조회 · '+icon("heart")+' '+(s.liked||0)+' 좋아요</p>'
 +(s.scheduledAt?'<p>방송 예약 메모: '+escapeHTML(s.scheduledAt)+'</p>':"")
 +'</div><div class="actions-row">'
 +actionButton("heart","좋아요","storyLike",s.id)+actionButton("chat","댓글","storyCommentFocus",s.id)
 +actionButton("share","공유","shareStory",s.id)+actionButton("bookmark","승인·보관","storyManage",s.id)+'</div>'
 +'<div class="panel"><h3>댓글 · 답글 ('+list.length+')</h3>'
 +(list.length?list.map(c=>'<div class="comment"><b>'+escapeHTML(c.author)+'</b><p>'+escapeHTML(c.body)+'</p>'
  +'<button data-action="storyReply" data-id="'+c.id+'">↳ 답글 달기</button></div>').join(""):"아직 댓글이 없습니다.")
 +'<div class="comment-input"><input placeholder="'+(storyReplyTo?"답글 입력...":"댓글을 남겨주세요...")+'" id="storyCommentText">'
 +'<button data-action="submitStoryComment" aria-label="댓글 전송">'+icon("share")+'</button></div></div>'
 +'<div class="panel"><h3>운영자 사연 관리</h3><div class="row" style="flex-wrap:wrap">'
 +["승인","거절","방송완료","보관"].map(x=>'<button class="secondary-btn" data-action="storyState" data-id="'+s.id+'" data-value="'+x+'">'+x+'</button>').join("")
 +'</div><div class="field" style="margin-top:12px"><label>방송 예약 메모 (자동 송출 아님)</label>'
 +'<input id="scheduleAt" class="textinput" placeholder="2026-11-10 19:00" value="'+escapeHTML(s.scheduledAt||"")+'">'
 +'<button class="secondary-btn" style="margin-top:7px" data-action="storySchedule">예약 메모 저장</button></div></div>';
}
function renderProfile(){
 const info=JSON.parse(call("deviceInfo")||"{}");
 const used=info.used?Math.round(info.used/1073741824*10)/10:"?";
 const total=info.total?Math.round(info.total/1073741824):"?";
 $("page-profile").innerHTML=topbar("내정보","","home")
 +'<div class="panel"><div class="row"><div class="profile-avatar">'+icon("person")+'</div>'
 +'<div style="flex:1"><h3 style="margin:0">마루라디오 운영자</h3><div class="tiny">갤럭시 한 대 · 내 기기 관리</div></div>'
 +'<button class="small-btn" data-action="about">앱 정보</button></div></div>'
 +'<div class="panel"><div class="storage-line">'+icon("drive")+'<div class="right"><strong>내 저장공간</strong>'
 +'<div class="tiny">'+used+'GB / '+total+'GB · 등록 '+(info.songCount||0)+'곡</div>'
 +'<div class="progress" style="margin-top:8px"><span style="width:'+(info.total?Math.min(100,(info.used/info.total*100)):0)+'%"></span></div></div></div></div>'
 +'<section class="panel"><div class="profile-row" data-go="downloads">'+icon("download")+'<span>다운로드 · 저장 위치</span> ❯</div>'
 +'<div class="profile-row" data-go="upload">'+icon("cloud")+'<span>내 업로드 관리</span> ❯</div>'
 +'<div class="profile-row" data-go="stories">'+icon("envelope")+'<span>사연 보관함</span> ❯</div>'
 +'<div class="profile-row" data-action="toggleDemo">'+icon("image")+'<span>마케팅 디자인 미리보기</span><span class="hint">'+(demoMode?"ON":"OFF")+'</span> ❯</div></section>'
 +'<div class="profile-section">서버 및 관리</div>'
 +'<div class="panel"><div class="field"><label>HTTPS 방송 서버 주소</label><input id="serverUrl" class="textinput" placeholder="https://example.com" value="'+escapeHTML(serverInfo)+'"></div>'
 +'<div class="field"><label>운영자 인증키 (앱 종료 시 삭제)</label><input type="password" class="textinput" id="serverToken" placeholder="인증키 입력"></div>'
 +'<button class="secondary-btn" style="width:100%" data-action="saveServer">서버 연결 정보 저장</button></div>'
 +'<div class="panel"><div class="profile-row" data-action="about">'+icon("settings")+'<span>앱 설정·버전</span><span class="hint">V1.1.0</span> ❯</div>'
 +'<div class="profile-row" data-action="stop">'+icon("music")+'<span>음원 재생 정지</span> ❯</div></div>'
 +'<button class="logout" data-action="logout">↪ 로그아웃 (임시 인증키 지우기)</button>'
 +'<p class="disclaimer">이 버전의 로컬 관리 앱에는 로그인 계정이 없습니다. 서버 접속은 인증키가 있을 때만 가능합니다. 모든 업로드·사연 데이터는 기본적으로 개인 기기에서 관리됩니다.</p>';
}
document.addEventListener("click",e=>{
 const b=e.target.closest("[data-go],[data-action]");if(!b)return;
 const target=b.dataset;
 if(target.go){go(target.go);return;}
 const act=target.action,id=Number(target.id),value=target.value;
 const song=getSong(id),story=getStory(id);
 if(["download","preview","score","favorite","share"].includes(act)&&demo(song)){notify("홍보용 시연 데이터입니다. 실제 곡을 등록하면 작동합니다.");return;}
 if(act==="top5"){trackTab="인기";go("songs");return;}
 if(act==="importFiles"){call("getFiles");return;}
 if(act==="importFolder"){call("getFolder");return;}
 if(act==="importManifest"){call("getManifest");return;}
 if(act==="songFilter"){trackTab=value;go("songs");return;}
 if(act==="storyFilter"){storyTab=value;go("stories");return;}
 if(act==="downloadTab"){downloadTab=value;go("downloads");return;}
 if(act==="detailTab"){detailTab=value;renderDetail();return;}
 if(act==="clearSearch"){query="";go("songs");return;}
 if(act==="chooseLanguage"){
   let names=["전체","한국노래","중국노래","서양노래","일본노래","발라드","트로트","연주","록 발라드"];
   let chosen=window.prompt("분류를 입력하세요: "+names.join(" / "),trackTab);if(chosen&&names.includes(chosen)){trackTab=chosen;go("songs");}
   return;
 }
 if(act==="searchSong"){
   const t=window.prompt("노래 제목·가수 검색",query);if(t!==null){query=t.trim();go("songs");}return;
 }
 if(act==="openSong"){go("detail",id);return;}
 if(act==="openStory"){go("story-detail",id);return;}
 if(act==="download"){call("download",id);return;}
 if(act==="preview"){call("preview",id);return;}
 if(act==="stop"){call("stop");notify("재생 정지");return;}
 if(act==="score"){call("viewScore",id);return;}
 if(act==="share"){call("shareSong",id);return;}
 if(act==="favorite"){notify(call("favorite",id));return;}
 if(act==="chooseUpload"){call("chooseSong");return;}
 if(act==="chooseCover"){if(uploadId)call("pickArt",uploadId);else notify("먼저 음원 파일을 선택하세요.");return;}
 if(act==="chooseScore"){if(uploadId)call("pickScore",uploadId);else notify("먼저 음원 파일을 선택하세요.");return;}
 if(act==="chooseStoryFile"){call("pickStoryFile");return;}
 if(act==="publishSong"){if(uploadId)call("uploadToServer",uploadId);else notify("먼저 음원 파일을 선택하세요.");return;}
 if(act==="reply"){replyTo=id;notify("답글을 작성하세요.");renderDetail();return;}
 if(act==="submitSongComment"){
   if(demo(getSong(selectedSong))){notify("시연 데이터에는 댓글을 저장하지 않습니다.");return;}
   const content=$("songComment")?.value||"";
   if(content.trim().length<2){notify("댓글을 두 글자 이상 입력하세요.");return;}
   notify(call("comment","song",selectedSong,replyTo,"호스트",content));
   replyTo=0;return;
 }
 if(act==="storyReply"){storyReplyTo=id;renderStoryDetail();return;}
 if(act==="storyCommentFocus"){$("storyCommentText")?.focus();return;}
 if(act==="submitStoryComment"){
   if(demo(getStory(selectedStory))){notify("시연 데이터에는 댓글을 저장하지 않습니다.");return;}
   const content=$("storyCommentText")?.value||"";
   if(content.trim().length<2){notify("댓글을 두 글자 이상 입력하세요.");return;}
   notify(call("comment","story",selectedStory,storyReplyTo,"호스트",content));storyReplyTo=0;return;
 }
 if(act==="storyLike"){if(!demo(story)){notify(call("like","story",id));}else notify("홍보용 시연 데이터입니다.");return;}
 if(act==="shareStory"){
   if(story)navigator.clipboard?.writeText(story.author+"님의 사연\n"+story.body)
     .then(()=>notify("사연 내용을 복사했습니다.")).catch(()=>notify("복사할 수 없습니다."));return;
 }
 if(act==="storyState"){if(demo(story)){notify("시연 사연입니다.");return;}notify(call("storyStatus",id,value));return;}
 if(act==="storySchedule"){
   const when=$("scheduleAt")?.value||"";notify(call("schedule",selectedStory,when));return;
 }
 if(act==="storyManage"){$("page-story-detail").scrollTop=99999;return;}
 if(act==="songOptions"){
   if(!selectedSong||demo(getSong(selectedSong))){notify("실제 곡을 등록하면 수정 가능합니다.");return;}
   const option=window.prompt("곡 관리: 1 표지 첨부 / 2 악보 첨부 / 3 재생 정지","1");
   if(option==="1")call("pickArt",selectedSong);
   else if(option==="2")call("pickScore",selectedSong);
   else if(option==="3")call("stop");return;
 }
 if(act==="saveServer"){notify(call("setServer",$("serverUrl").value,$("serverToken").value));return;}
 if(act==="toggleDemo"){demoMode=!demoMode;notify(demoMode?"디자인 시연 켜짐: 실제 데이터가 아닙니다.":"디자인 시연 꺼짐");renderProfile();return;}
 if(act==="about"){notify("MARU RADIO V1.1.0 · 8화면 · 개인 보관함 · 외부 송출 별도");return;}
 if(act==="logout"){call("logout");return;}
});
document.addEventListener("input",e=>{
 if(e.target.id==="storyBody")$("storyLength").textContent=e.target.value.length+" / 1,000";
});
document.addEventListener("submit",e=>{
 e.preventDefault();
 if(e.target.id==="uploadForm"){
   const f=new FormData(e.target);
   if(!uploadId){notify("먼저 음원 파일을 선택하세요.");return;}
   const response=call("submitSong",uploadId,String(f.get("title")||""),String(f.get("artist")||""),
      String(f.get("genre")||""),String(f.get("lang")||""),f.get("rights")!==null);
   notify(response);return;
 }
 if(e.target.id==="storyForm"){
   const f=new FormData(e.target);
   const response=call("submitStory",String(f.get("nick")||""),String(f.get("body")||""),
      String(f.get("request")||""),String(f.get("privacy")||"public"));
   notify(response);
   if(response&&response.startsWith("사연 접수번호")){storyFileLabel="선택한 첨부파일 없음";storyTab="전체";go("stories");}
 }
});
readData();render();
