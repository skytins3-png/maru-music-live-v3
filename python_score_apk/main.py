"""MARU SCORE: self-contained Python Kivy Android application.

AUDIO PICK -> native Android decode called from Python -> Python FFT notation ->
in-app preview -> Android Save-As PDF. Works offline after installation.
"""
import os,re,threading
from kivy.app import App
from kivy.clock import Clock
from kivy.graphics import Color,Rectangle,Ellipse,Line
from kivy.core.text import LabelBase
from kivy.metrics import dp
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.progressbar import ProgressBar
from kivy.uix.scrollview import ScrollView
from kivy.uix.widget import Widget
from score_engine import SR,transcribe,pdf_bytes,xml_bytes,midi_bytes,bars,staff_level

PICK=4001;SAVE=4002
ANDROID="ANDROID_ARGUMENT" in os.environ
FONT="Roboto"
if ANDROID:
    for name in ("/system/fonts/NotoSansKR-Regular.otf",
                 "/system/fonts/NotoSansCJK-Regular.ttc",
                 "/system/fonts/NotoSansCJK-Regular.otf"):
        if os.path.exists(name):
            try:
                LabelBase.register(name="MARU-KR",fn_regular=name)
                FONT="MARU-KR";break
            except Exception:pass

DARK=(.027,.075,.14,1)
BLUE=(.055,.63,.95,1)
WHITE=(.92,.96,1,1)

def button(text,color,handler,height=56):
    obj=Button(text=text,font_name=FONT,bold=True,font_size="17sp",
               background_normal="",background_color=color,
               size_hint_y=None,height=dp(height))
    obj.bind(on_release=handler)
    return obj

class Preview(Widget):
    def __init__(self,**kw):
        super().__init__(**kw);self.staff=[]
        self.bind(pos=self.redraw,size=self.redraw)
    def show(self,notes):
        self.staff=bars(notes)[:12]
        self.height=dp(175*max(1,(len(self.staff)+1)//2))
        self.redraw()
    def redraw(self,*_):
        self.canvas.clear()
        with self.canvas:
            Color(1,1,1,1);Rectangle(pos=self.pos,size=self.size)
            if not self.staff:return
            half=self.width/2
            for i,bar in enumerate(self.staff):
                row,col=divmod(i,2)
                x0=self.x+dp(8)+col*half
                top=self.top-dp(42)-row*dp(175)
                gap=dp(10)
                Color(.22,.32,.43,1)
                for j in range(5):
                    y=top-j*gap
                    Line(points=[x0,y,x0+half-dp(14),y],width=.8)
                for slot,length,pitch in bar:
                    if pitch is None:continue
                    lev=staff_level(pitch)
                    x=x0+dp(8)+(half-dp(35))*(slot+length/2)/16
                    y=top-4*gap+lev*gap/2
                    Color(.06,.16,.25,1)
                    Ellipse(pos=[x-dp(4),y-dp(3)],size=[dp(9),dp(6)])
                    Line(points=[x+dp(4),y,x+dp(4),y+dp(22)],width=1)
                    if lev<0:
                        for led in range(-2,lev-1,-2):
                            yy=top-4*gap+led*gap/2
                            Line(points=[x-dp(8),yy,x+dp(8),yy],width=1)

class MaruScore(App):
    def build(self):
        self.title="MARU PY SCORE"
        self.result={};self.title_audio="";self.pending=None;self.processing=False
        if ANDROID:
            from android import activity
            activity.bind(on_activity_result=self.on_result)
        root=BoxLayout(orientation="vertical",padding=dp(12),spacing=dp(8))
        with root.canvas.before:
            Color(*DARK)
            self.background=Rectangle(pos=root.pos,size=root.size)
        root.bind(pos=lambda *_:setattr(self.background,"pos",root.pos),
                  size=lambda *_:setattr(self.background,"size",root.size))
        root.add_widget(Label(text="MARU PYTHON SCORE",font_name=FONT,font_size="24sp",
                              bold=True,color=WHITE,size_hint_y=None,height=dp(55)))
        root.add_widget(Label(text="MP3 넣으면 자동으로 악보 생성",
                              font_name=FONT,font_size="16sp",color=(.5,.8,1,1),
                              size_hint_y=None,height=dp(32)))
        root.add_widget(button("음원 선택 / 악보 만들기",BLUE,self.pick))
        self.message=Label(text="휴대폰 안에서만 분석합니다. 인터넷 불필요.",
                           font_name=FONT,color=WHITE,font_size="14sp",
                           size_hint_y=None,height=dp(62),halign="center",valign="middle")
        self.message.bind(size=lambda o,_:setattr(o,"text_size",(o.width,None)))
        root.add_widget(self.message)
        self.progress=ProgressBar(max=100,value=0,size_hint_y=None,height=dp(8))
        root.add_widget(self.progress)
        scroll=ScrollView(do_scroll_x=False)
        self.preview=Preview(size_hint_y=None,height=dp(570))
        scroll.add_widget(self.preview)
        root.add_widget(scroll)
        self.pdf=button("악보 PDF 저장",(.07,.44,.74,1),lambda *_:self.save_as("score.pdf","application/pdf"),52)
        self.pdf.disabled=True;root.add_widget(self.pdf)
        small=BoxLayout(size_hint_y=None,height=dp(50),spacing=dp(8))
        self.xml=button("MusicXML",(.12,.24,.38,1),lambda *_:self.save_as("score.musicxml","application/xml"),48)
        self.mid=button("MIDI",(.12,.24,.38,1),lambda *_:self.save_as("score.mid","audio/midi"),48)
        self.xml.disabled=True;self.mid.disabled=True
        small.add_widget(self.xml);small.add_widget(self.mid);root.add_widget(small)
        root.add_widget(Label(text="완성곡은 주선율 추정 악보입니다. 전체 편곡 총보는 아닙니다.\n기존 105곡 및 추모곡에는 자동 접근하지 않습니다.",
                              font_name=FONT,color=(.65,.75,.83,1),
                              halign="center",font_size="12sp",
                              size_hint_y=None,height=dp(55)))
        return root

    def status(self,message,progress=None):
        def update(_):
            self.message.text=message
            if progress is not None:self.progress.value=progress
        Clock.schedule_once(update,0)

    def pick(self,*_):
        if self.processing:return
        if not ANDROID:
            self.status("안드로이드 APK에서 음원 파일을 선택하세요.")
            return
        from jnius import autoclass
        from android import mActivity
        Intent=autoclass("android.content.Intent")
        intent=Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType("audio/*")
        mActivity.startActivityForResult(intent,PICK)

    def on_result(self,req,res,data):
        if res!=-1 or data is None:return
        uri=data.getData()
        if uri is None:return
        if req==PICK:
            self.processing=True;self.result={}
            for b in (self.pdf,self.xml,self.mid):b.disabled=True
            self.status("음원 읽는 중…",10)
            threading.Thread(target=self.process,args=(uri,),daemon=True).start()
        elif req==SAVE and self.pending is not None:
            path=self.pending;self.pending=None
            threading.Thread(target=self.export,args=(uri,path),daemon=True).start()

    def process(self,uri):
        from android import mActivity
        from audio_android import copy_from_uri,decode
        try:
            resolver=mActivity.getContentResolver()
            name="음원"
            cursor=resolver.query(uri,None,None,None,None)
            if cursor is not None:
                try:
                    if cursor.moveToFirst():
                        index=cursor.getColumnIndex("_display_name")
                        if index>=0:name=str(cursor.getString(index))
                finally:cursor.close()
            self.title_audio=re.sub(r"\.(mp3|wav|m4a|ogg|aac)$","",name,flags=re.I)[:65]
            extension=os.path.splitext(name)[-1].lower() or ".mp3"
            if extension not in (".mp3",".m4a",".wav",".aac"):
                extension=".mp3"
            path=os.path.join(str(mActivity.getCacheDir().getAbsolutePath()),"maru_music"+extension)
            copy_from_uri(uri,path)
            self.status("음원 분석 중… (최대 3분)",30)
            audio=decode(path)
            notes=transcribe(audio,80)
            self.status("오선 악보 생성 중…",85)
            title=self.title_audio or "MARU"
            self.result={"score.pdf":pdf_bytes(notes,80,title),
                         "score.musicxml":xml_bytes(notes,80,title),
                         "score.mid":midi_bytes(notes,80)}
            def finished(_):
                self.preview.show(notes)
                self.message.text=f"{title}\n악보 {len(notes)}개 음구간 · 80 BPM 기준"
                self.progress.value=100
                for b in (self.pdf,self.xml,self.mid):b.disabled=False
                self.processing=False
            Clock.schedule_once(finished,0)
        except Exception as exc:
            self.processing=False
            self.status("악보 생성 실패: "+str(exc)[:130],0)

    def save_as(self,name,mime):
        if not ANDROID or name not in self.result:return
        from jnius import autoclass
        from android import mActivity
        Intent=autoclass("android.content.Intent")
        intent=Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType(mime)
        clean=re.sub(r'[\\/:*?"<>|]','_',self.title_audio or 'MARU')[:50]
        intent.putExtra(Intent.EXTRA_TITLE,clean+"_"+name)
        self.pending=name
        mActivity.startActivityForResult(intent,SAVE)

    def export(self,uri,name):
        try:
            from audio_android import save_uri
            save_uri(uri,self.result[name])
            self.status("저장 완료: "+name,100)
        except Exception as exc:
            self.status("저장 실패: "+str(exc),80)

if __name__=="__main__":MaruScore().run()
