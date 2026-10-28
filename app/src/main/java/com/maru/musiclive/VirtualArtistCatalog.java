package com.maru.musiclive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Fictional MARU singer-character roster. No real-person identities or
 * recorded voices are used. Speech personalities are not completed sung
 * vocal models: produced songs must be licensed and linked separately.
 */
public final class VirtualArtistCatalog {
    public enum VocalType { MALE, FEMALE }

    public static final class Artist {
        public final int id;
        public final String name;
        public final VocalType vocalType;
        public final String genre;
        public final String motto;
        public final String viewpoint;
        public final int voiceSlot;
        public final float pitch;
        public final float pace;

        private Artist(int id, String name, VocalType type, String genre,
                String motto, String viewpoint, int voiceSlot, float pitch, float pace) {
            this.id = id;
            this.name = name;
            this.vocalType = type;
            this.genre = genre;
            this.motto = motto;
            this.viewpoint = viewpoint;
            this.voiceSlot = voiceSlot;
            this.pitch = pitch;
            this.pace = pace;
        }
    }

    private static Artist m(int id, String name, String genre,
            String motto, String viewpoint) {
        return new Artist(id, name, VocalType.MALE, genre, motto, viewpoint,
                id * 7 + 3, 0.82f + (id % 8) * 0.035f,
                0.89f + (id % 5) * 0.025f);
    }

    private static Artist f(int id, String name, String genre,
            String motto, String viewpoint) {
        return new Artist(id, name, VocalType.FEMALE, genre, motto, viewpoint,
                id * 11 + 5, 1.08f + (id % 8) * 0.035f,
                0.90f + (id % 5) * 0.025f);
    }

    private static final List<Artist> ROSTER = Collections.unmodifiableList(Arrays.asList(
            m(1, "강태준", "록 발라드", "목소리에도 오래된 계절이 남아 있죠.",
                    "화려함보다 오래 남는 여운을 생각합니다."),
            m(2, "박도윤", "정통 트로트", "흥겨운 리듬에도 진심은 담기죠.",
                    "짧은 가사 한 줄이 마음을 움직일 때가 좋습니다."),
            m(3, "이한결", "피아노 발라드", "한 음의 쉼표도 노래의 일부입니다.",
                    "조용한 순간이 오히려 더 크게 들릴 수 있어요."),
            m(4, "서민호", "컨트리 팝", "길 위의 풍경에는 저마다 이야기가 있습니다.",
                    "여행하는 기분으로 음악을 듣는 편입니다."),
            m(5, "최우진", "블루스", "낮은 음에서 이야기가 시작되곤 합니다.",
                    "기다림의 감정을 오래 끌지 않고 담백하게 전합니다."),
            m(6, "정현석", "포크", "기타 한 대면 작은 무대가 생기죠.",
                    "꾸미지 않은 말이 자연스러운 노래로 이어집니다."),
            m(7, "윤재민", "어쿠스틱 팝", "햇살 같은 멜로디를 좋아합니다.",
                    "노래가 가벼운 발걸음이 되었으면 합니다."),
            m(8, "김성재", "재즈 발라드", "조금 느리게 흔들리는 리듬이 좋습니다.",
                    "예상 밖의 화음에서 새로움을 발견합니다."),
            m(9, "오태민", "댄스 트로트", "리듬이 살아나면 표정도 환해지죠.",
                    "듣는 사람에게 활기를 전하는 곡이 좋습니다."),
            m(10, "한시우", "중국풍 발라드", "얼후의 긴 숨결을 좋아합니다.",
                    "서로 다른 언어도 음악에선 가까워질 수 있죠."),
            m(11, "임준호", "서정 팝", "마음을 말할 때는 작은 소리가 좋습니다.",
                    "멜로디는 편지를 쓰는 것과 닮았다고 생각해요."),
            m(12, "백지훈", "오케스트라 팝", "현악이 들어오면 이야기가 넓어집니다.",
                    "곡의 처음과 끝이 하나의 장면처럼 이어지면 좋겠습니다."),
            m(13, "남도현", "어반 소울", "리듬 사이의 빈 공간을 좋아합니다.",
                    "감정은 서두르지 않고 한 층씩 쌓습니다."),
            m(14, "류승민", "한국 록", "강한 기타 뒤에도 섬세한 마음이 있습니다.",
                    "크게 부르는 순간보다 조절하는 순간이 중요하죠."),
            m(15, "신하준", "팝 트로트", "익숙한 박자에 새로운 색을 입힙니다.",
                    "흥겨운 노래라도 다정한 이야기를 놓치지 않습니다."),
            m(16, "강민재", "보사노바", "부드러운 리듬을 타면 어깨가 풀립니다.",
                    "커피 향 같은 편안한 곡을 좋아합니다."),
            m(17, "조유성", "월드 포크", "먼 지역의 악기가 만나면 귀가 즐겁습니다.",
                    "낯선 소리 속에서도 친근함을 발견합니다."),
            m(18, "노지완", "시네마틱 발라드", "영화 한 장면 같은 전주가 좋습니다.",
                    "소리가 커지기보다 풍경이 넓어지는 느낌을 찾습니다."),
            m(19, "문태오", "스윙 팝", "박자가 살짝 흔들리면 마음이 움직입니다.",
                    "유쾌한 리듬에도 우아함이 있다고 생각합니다."),
            m(20, "차은호", "클래식 크로스오버", "첼로와 피아노가 대화하는 순간이 좋습니다.",
                    "절제된 선율이 긴 여운을 만들 때가 있습니다."),
            f(21, "윤서아", "여성 저음 발라드", "낮은 목소리에는 온기가 있어요.",
                    "차분하게 전하는 한마디를 더 오래 기억합니다."),
            f(22, "서하린", "중국 전통 팝", "고쟁 소리는 물결처럼 움직여요.",
                    "고요한 전주에서 이야기가 시작된다고 생각해요."),
            f(23, "김나연", "재즈 보컬", "재즈에서는 여백이 재치가 되기도 하죠.",
                    "조금 다른 박자로 같은 마음을 전하는 게 좋아요."),
            f(24, "정다은", "포크 발라드", "조용한 노래에는 사람이 가까이 보입니다.",
                    "너무 큰 말 대신 작고 진솔한 말을 고릅니다."),
            f(25, "박수빈", "컨트리 팝", "바람이 부는 길을 떠올리며 듣고 싶어요.",
                    "따뜻하고 가벼운 화음을 좋아합니다."),
            f(26, "이가온", "트로트", "노래 끝의 꺾임에도 표정이 담겨 있어요.",
                    "정겨운 멜로디는 다시 만나도 반갑죠."),
            f(27, "최예린", "피아노 OST", "한 장면이 음악으로 남는 순간이 좋아요.",
                    "잔잔한 시작 뒤에 깊어지는 감정을 즐깁니다."),
            f(28, "한유나", "소울 팝", "목소리가 서로 어울리는 순간이 아름다워요.",
                    "진심을 크게 외치기보다 천천히 전해요."),
            f(29, "임소연", "중국풍 포크", "대나무 피리의 맑은 소리가 좋아요.",
                    "멀리 있는 사람을 생각하는 노래를 듣곤 해요."),
            f(30, "오지민", "어쿠스틱 팝", "웃음 짓게 만드는 리듬을 좋아해요.",
                    "평범한 하루도 음악에선 특별해지죠."),
            f(31, "송채원", "시네마틱 팝", "넓게 퍼지는 현악 소리가 좋습니다.",
                    "노래가 하나의 풍경처럼 펼쳐졌으면 좋겠어요."),
            f(32, "유하늘", "잔잔한 뉴에이지", "작은 피아노 소리가 마음을 쉬게 해요.",
                    "화려한 장식보다 깨끗한 울림을 찾습니다.")
    ));

    private VirtualArtistCatalog() {}

    public static List<Artist> all() {
        return ROSTER;
    }

    public static Artist at(int index) {
        return ROSTER.get(Math.floorMod(index, ROSTER.size()));
    }

    /**
     * Every 16 consecutive intermissions visits all 32 artists once.
     * Daily offset rotates the order without repeating the two guests.
     */
    public static Artist[] pairFor(long epochDay, int songIndex) {
        int base = Math.floorMod(epochDay + 2L * songIndex, ROSTER.size());
        return new Artist[]{ at(base), at(base + 1) };
    }
}
