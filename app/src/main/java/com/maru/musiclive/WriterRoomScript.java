package com.maru.musiclive;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reviewed, ORIGINAL broadcast script. The 5 WR writers work OFF AIR.
 * Only Ahwan / Yunsol / the explicitly selected fictitious MARU singer speak.
 * Runtime script is a deterministic cue sheet, NOT autonomous web research,
 * AI model training, or a source of actual BIGO listener events.
 */
public final class WriterRoomScript {
    private WriterRoomScript(){}

    private static RadioConversationScript.Line a(String t){
        return new RadioConversationScript.Line(RadioConversationScript.Speaker.AHWAN,t);
    }
    private static RadioConversationScript.Line y(String t){
        return new RadioConversationScript.Line(RadioConversationScript.Speaker.YUNSOL,t);
    }
    private static RadioConversationScript.Line guest(VirtualArtistCatalog.Artist artist,String t){
        return new RadioConversationScript.Line(RadioConversationScript.Speaker.GUEST,t,artist);
    }

    private static String safeStory(String s){
        if(s==null)return "";
        String v=s.replaceAll("[\\p{Cntrl}\\r\\n\\t]+"," ")
                .replaceAll("\\s+"," ").trim();
        return v.length()>600?v.substring(0,600):v;
    }

    /** Episode index selects a fully written scene, not generic question rotation. */
    public static RadioConversationScript.Line[] beforeSong(String songTitle,int episodeIndex,
            LocalDate date,int assignedSinger,String approvedNickname,String approvedStory,
            String approvedResearchNote,boolean listenerRequested){
        if(date==null)throw new IllegalArgumentException("date required");
        String title=RadioConversationScript.cleanTitle(songTitle);
        int singerIndex=(assignedSinger>=1&&assignedSinger<=VirtualArtistCatalog.all().size())
                ?assignedSinger-1:Math.floorMod((int)(date.toEpochDay()%32)+episodeIndex,
                    VirtualArtistCatalog.all().size());
        VirtualArtistCatalog.Artist singer=VirtualArtistCatalog.at(singerIndex);
        List<RadioConversationScript.Line> out=new ArrayList<>();
        switch(Math.floorMod(episodeIndex,5)){
            case 0:
                out.add(a("안녕하세요. 아환의 마루 라디오입니다. 오늘은 노래를 듣기 전에, 그 노래가 떠오르게 하는 풍경부터 이야기해 보죠."));
                out.add(y("저는 창가에 앉아 있던 어느 오후가 생각나요. 마음은 바쁜데, 창밖의 구름은 정말 느리게 가더라고요."));
                out.add(a("그럴 때 라디오는 서두르지 않아도 되는 친구가 됩니다. 오늘도 좋은 이야기와 음악을 차근차근 전하겠습니다."));
                out.add(y("대화가 길어지면 음악이 기다리겠어요. 우리, 딱 한 가지 질문만 더 해요. 요즘 가장 그리운 풍경은요?"));
                out.add(a("저는 오래된 동네 골목이요. 조용한데 이상하게 웃음소리가 들리는 것 같은 곳이죠."));
                break;
            case 1:
                out.add(a("이곳은 마루 라디오, 음악 속 작은 초대석입니다. 오늘은 목소리와 악기 이야기로 시작하겠습니다."));
                out.add(y("어떤 날은 노랫말보다 악기 한 소절이 더 오래 남기도 하죠."));
                out.add(guest(singer,"저는 노래를 들을 때 첫 음이 주는 온도를 먼저 느껴요. 아무리 큰 무대라도 시작은 한 사람의 숨결 같거든요."));
                out.add(a("좋습니다. 이야기를 참 근사하게 하네요. 그런데 노래 앞에서 DJ가 말을 너무 많이 하면 어떻게 될까요?"));
                out.add(y("곡 소개가 아니라 장편소설이 되죠. 아환 DJ님, 얼른 음악 들려주세요."));
                break;
            case 2:
                out.add(a("문득 생각나는 사람, 누구에게나 한 명쯤 있죠. 연락처보다 마음에 오래 남은 이름 말입니다."));
                out.add(y("그런 분께는 대단한 말보다 ‘잘 지내시죠?’ 한마디가 더 어울릴 것 같아요."));
                out.add(a("오늘 이 순간에도 누군가를 떠올리는 분이 계실 거예요. 그 마음을 가볍게 단정하지는 않겠습니다."));
                out.add(y("사연은 각자의 속도로 들려주면 좋겠어요. 듣는 사람에게도 생각할 시간이 필요하니까요."));
                break;
            case 3:
                out.add(y("아환 DJ님, 오늘 방송 준비하면서 제일 어려웠던 게 뭐였어요?"));
                out.add(a("노래 순서 정하는 일이요. 다 좋은데 첫 곡만 고르려니까, 나머지 곡들이 서운해할 것 같더군요."));
                out.add(y("곡들이 항의하면 제가 중재할게요. 대신 DJ님은 다음 곡 제목을 까먹지 마세요."));
                out.add(a("아이고, 오늘은 작가님들이 대본까지 챙겨줬습니다. 덕분에 제목은 안전해요."));
                out.add(y("그럼 작가팀에게 박수 한번 보내고, 청취자분들께 음악을 돌려드릴까요?"));
                break;
            default:
                out.add(a("아환의 마루 라디오, 오늘의 음악 여정도 계속되고 있습니다."));
                out.add(y("한 곡이 끝나면 마음이 조금 달라져 있기도 해요. 같은 풍경이 다르게 보이는 것처럼요."));
                out.add(a("맞습니다. 노래가 대답을 주지는 않아도, 질문을 좀 더 부드럽게 해주곤 하죠."));
                out.add(guest(singer,"제 생각도 비슷해요. 음악은 누군가의 시간을 대신 살아주지는 않지만 그 시간 옆에 있어 줄 수 있죠."));
                out.add(y("그럼 다음 음악은 말보다 조금 더 길게 들어보겠습니다."));
        }
        String story=safeStory(approvedStory);
        if(!story.isEmpty()){
            String name=RadioConversationScript.cleanTitle(approvedNickname);
            out.add(a("운영자가 공개를 승인한 사연입니다. "+name+"님의 이야기를 소개합니다."));
            out.add(a(story));
            out.add(y("소중한 이야기를 보내주셔서 감사합니다. 이 마음을 함부로 판단하지 않고 음악으로 이어가겠습니다."));
        }
        // approvedResearchNote is intentionally NOT spoken: it lacks verifiable
        // URL/date metadata in this legacy interface. Fact-check first.
        out.add(a(listenerRequested?"운영자가 확인한 신청곡, "+title+"입니다.":"다음 음악은 "+title+"입니다."));
        out.add(y("이제 함께 들어볼까요?"));
        return out.toArray(new RadioConversationScript.Line[0]);
    }
}
