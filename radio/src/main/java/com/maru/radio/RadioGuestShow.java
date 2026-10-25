package com.maru.radio;
import java.util.*;
/** Fictional scripted interviews. Device speech is not a Suno voice clone. */
public final class RadioGuestShow {
    private RadioGuestShow() {}
    public static final class Guest {
        public final String name, title, theme, hook; public final float pitch;
        Guest(String n,String t,String m,String h,float p){name=n;title=t;theme=m;hook=h;pitch=p;}
    }
    public static final Guest[] GUESTS = {
        new Guest("강태준", "오래된 약속의 온도", "오래 함께한 두 사람의 약속", "오래된 약속은 따뜻해", .91f),
        new Guest("민도윤", "주머니 속 봄바람", "주머니 속 작은 봄의 설렘", "주머니 속 봄바람", .91f),
        new Guest("윤시후", "달빛 우편함", "멀리 있는 사람에게 보내는 밤의 편지", "달빛 우편함을 열어 줘", .91f),
        new Guest("한도겸", "산 아래 첫 불빛", "산 아래 집으로 돌아오는 마음", "산 아래 첫 불빛", .91f),
        new Guest("차이준", "초록불이 켜지면", "초록불 앞에서 시작하는 용기", "초록불이 켜지면", .91f),
        new Guest("서건우", "아버지의 낡은 운동화", "아버지의 걸음을 뒤늦게 이해하는 마음", "아버지의 낡은 운동화", .91f),
        new Guest("백유찬", "설탕 없이도 달아", "평범한 하루를 달게 만드는 사랑", "설탕 없이도 달아", .91f),
        new Guest("강서율", "눈이 오기 전의 편지", "눈이 오기 전에 전하고 싶은 마음", "눈이 오기 전에 전할게", .91f),
        new Guest("류하준", "웃어라 내 청춘아", "다시 웃으며 걸어가는 청춘", "웃어라 내 청춘아", .91f),
        new Guest("남지후", "막차보다 늦은 고백", "막차를 놓친 밤의 고백", "막차보다 늦은 고백", .91f),
        new Guest("오태린", "파도는 다시 일어난다", "넘어져도 다시 일어나는 마음", "파도는 다시 일어난다", .91f),
        new Guest("임선우", "골목 끝 작은 빵집", "골목 빵집에서 만나는 작은 행복", "골목 끝 작은 빵집", .91f),
        new Guest("정하온", "빈 의자에 남은 노래", "빈자리에 남아 있는 그리움", "빈 의자에 남은 노래", .91f),
        new Guest("유도현", "기차표 한 장의 용기", "기차표 한 장으로 시작하는 여행", "기차표 한 장의 용기", .91f),
        new Guest("문이안", "새벽 두 시의 파란 화면", "늦은 밤 서로에게 건네는 위로", "새벽 두 시의 파란 화면", .91f),
        new Guest("신재온", "비를 맞은 간판", "비 오는 거리에서 발견하는 온기", "비를 맞은 간판도", .91f),
        new Guest("권도하", "별을 건너온 이름", "별처럼 멀어도 기억하는 이름", "별을 건너온 이름", .91f),
        new Guest("장유건", "아무 계획 없는 일요일", "계획 없이 쉬어 가는 일요일", "아무 계획 없는 일요일", .91f),
        new Guest("홍시안", "리듬을 타는 우산", "우산 아래에서 찾은 경쾌한 리듬", "리듬을 타는 우산", .91f),
        new Guest("송태오", "바람이 쉬는 책갈피", "책갈피에 남아 있는 두 사람의 봄", "바람이 쉬는 책갈피", .91f),
        new Guest("서하린", "今晚就跟你走", "오늘 밤 함께 걷고 싶은 설렘", "今晚就跟你走", 1.06f),
        new Guest("윤채린", "붉은 머플러", "오랜만의 만남에 다시 피어나는 마음", "붉은 머플러를 풀면", 1.06f),
        new Guest("한세아", "비 갠 뒤의 정원", "비가 지나간 정원처럼 새로 시작하는 마음", "비 갠 뒤의 정원에", 1.06f),
        new Guest("차나린", "心跳刚刚好", "서로 맞춰 가는 두근거림", "心跳刚刚好", 1.06f),
        new Guest("백소윤", "阳光跟着我们跑", "햇살을 따라 함께 달리는 즐거움", "阳光跟着我们跑", 1.06f),
        new Guest("류서빈", "내 이름을 다시 부른다", "잊었던 내 이름과 자신감을 되찾는 마음", "내 이름을 다시 부른다", 1.06f),
        new Guest("남예린", "귤빛 오후", "귤빛 오후에 느끼는 작은 행복", "귤빛 오후 물든 마음", 1.06f),
        new Guest("오하늬", "한 박자 느린 사랑", "서두르지 않고 맞춰 가는 사랑", "한 박자 느린 사랑", 1.06f),
        new Guest("임다온", "꽃신 신고 마중 간다", "꽃신을 신고 기다리는 반가운 사람", "꽃신 신고 마중 간다", 1.06f),
        new Guest("정유라", "잠든 도시의 자장가", "잠든 도시를 조용히 감싸는 위로", "잠든 도시의 자장가", 1.06f),
    };
    public static Guest find(String title,String artist) {
        String clean=RadioShowLines.tidy(title);
        for(Guest g:GUESTS) if(g.name.equals(artist) && (clean.equals(g.title)||clean.equals(g.title+" — "+g.name))) return g;
        return null;
    }
    private static RadioShowLines.Line line(String who,String text,float pitch) {
        return new RadioShowLines.Line(who,Locale.KOREAN,text,pitch);
    }
    public static List<RadioShowLines.Line> intro(String title,String artist,boolean opening,String previous) {
        List<RadioShowLines.Line> out=new ArrayList<>();
        if(opening)out.add(line("아환","마루 라디오, 아환의 음악 초대석입니다. 가상 가수와 나누는 이야기와 원곡을 함께 들어요.",1f));
        else if(previous!=null&&!previous.isEmpty())out.add(line("아환","방금 들으신 곡은 "+RadioShowLines.tidy(previous)+"였습니다. 다음 초대석을 이어갈게요.",1f));
        Guest g=find(title,artist);
        if(g==null){out.addAll(RadioShowLines.ahwanIntro(title,artist));return Collections.unmodifiableList(out);}
        out.add(line("아환",g.name+" 씨, 어서 오세요. 오늘 어떤 이야기를 들려주실 건가요?",1f));
        out.add(line(g.name,"안녕하세요. "+g.name+"입니다. 이번 노래에는 "+g.theme+"을 담았어요.",g.pitch));
        out.add(line("아환","듣는 분들이 어떤 장면을 떠올리면 좋을까요?",1f));
        out.add(line(g.name,g.theme+"을 떠올리며 편하게 들어 주세요. 제목은 "+g.title+"입니다.",g.pitch));
        out.add(line("아환","그 마음을 노래로 만나 볼게요. "+g.name+"의 "+g.title+", 함께 듣겠습니다.",1f));
        return Collections.unmodifiableList(out);
    }
}
