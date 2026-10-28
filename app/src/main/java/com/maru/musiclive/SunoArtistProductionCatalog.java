package com.maru.musiclive;

/**
 * Production design only: 32 planned original singing voices.
 * NONE are marked as trained, audited or generated. This class cannot
 * synthesize vocals or claim a style prompt guarantees identity.
 */
public final class SunoArtistProductionCatalog {
    public enum Status { DESIGN_ONLY, AUDIO_IMPORTED_VERIFIED }

    public static final class Profile {
        public final int artistId;
        public final String genre;
        public final String language;
        public final String timbre;
        public final String technique;
        public final String range;
        public final int bpm;
        public final String instruments;
        public final String originalTitle;
        public final String lyricalTheme;
        public final Status status;

        Profile(int artistId,String genre,String language,String timbre,
                String technique,String range,int bpm,String instruments,
                String originalTitle,String lyricalTheme){
            this.artistId=artistId; this.genre=genre; this.language=language;
            this.timbre=timbre;this.technique=technique;this.range=range;
            this.bpm=bpm;this.instruments=instruments;
            this.originalTitle=originalTitle;this.lyricalTheme=lyricalTheme;
            this.status=Status.DESIGN_ONLY;
        }
        public String singerName(){return VirtualArtistCatalog.at(artistId-1).name;}
        /** Suno Custom Style of Music. No copyrighted or real singer likeness. */
        public String stylePrompt(){
            return "Original " + genre + " song, language " + language
                    + ", " + bpm + " BPM, solo " +
                    (VirtualArtistCatalog.at(artistId-1).vocalType ==
                        VirtualArtistCatalog.VocalType.MALE ? "male" : "female")
                    + " singing voice, individual tone: " + timbre
                    + ". Distinct delivery: " + technique + ". Comfortable range: "
                    + range + ". Arrangement: " + instruments
                    + ". Natural pronunciation, clear lyric enunciation, "
                    + "realistic breathing, studio quality with clean dynamic mix. "
                    + "Original melody, original lyrics, no imitation of any real singer.";
        }
    }
    private SunoArtistProductionCatalog(){}
    private static final Profile[] ALL=new Profile[]{
        new Profile(1,"발라드","한국어","dark cocoa baritone, breathy low onset, grainy husky edges","legato low verses, restrained vibrato, octave leap only at final chorus","G2–E4",76,"grand piano, cello, clean electric guitar, live drums","다시 오지 않을 밤","잊힌 계절과 후회"),
        new Profile(2,"트로트","한국어","bright chest tenor with light nasal sheen","tight traditional 꺾기, straight-tone start, narrow end-note vibrato","B2–G4",104,"accordion, clean rhythm guitar, brass, drum kit","돌아가는 골목","고향 골목 재회"),
        new Profile(3,"발라드","한국어","clear light baritone with airy falsetto edge","soft speech-like phrasing, downward portamento, very short vibrato","A2–F4",70,"felt piano, solo cello, acoustic guitar","창가의 편지","전하지 못한 사과"),
        new Profile(4,"팝송","영어","warm mid tenor with relaxed western drawl","gentle scoops, linked phrasing, no runs","C3–G4",86,"acoustic guitar, electric piano, brush drums","Highway in Autumn","자유로운 가을 여행"),
        new Profile(5,"발라드","한국어","rough smoky low baritone with intimate chest resonance","understated rasp, long held vowels, gradual chorus lift","F2–D4",68,"blues piano, slide guitar, deep bass","오래된 의자","오래된 친구를 떠올림"),
        new Profile(6,"발라드","한국어","dry woody tenor-baritone, close mic","narrative diction, no melisma, tiny falling sighs","A2–E4",74,"fingerpicked guitar, harmonica, acoustic bass","돌아올 길","집으로 향하는 마음"),
        new Profile(7,"팝송","영어","shimmering clear upper tenor","crisp rhythmic articulation, buoyant head-tone hooks","D3–A4",98,"electric guitar, synth keys, punchy pop drums","Sun on the Window","작은 희망"),
        new Profile(8,"팝송","영어","velvet crooner baritone with round resonance","light behind-the-beat jazz inflections, no belting","G2–E4",80,"upright bass, brushed snare, warm jazz piano","Blue Candlelight","조용한 저녁"),
        new Profile(9,"트로트","한국어","brassy ringing tenor, forward projection","dance-style syllabic ornament, joyful long-note tremolo","C3–A4",112,"brass riff, accordion, janggu accents, dance drums","네온 골목","밤거리 연인"),
        new Profile(10,"중국노래","중국어 간체","warm rounded tenor, soft airy onset","Mandarin tonal clarity, flowing portamento, reserved vibrato","B2–F4",76,"erhu, guzheng, xiao, cello","云南的晚风","윈난 산길의 저녁"),
        new Profile(11,"발라드","한국어","gentle nasal-tinged tenor with delicate high register","breathy introspection, seamless head-voice transition","B2–A4",72,"piano, soft strings, clean guitar","마지막 인사","마음속의 작별"),
        new Profile(12,"러시아노래","러시아어","low resonant dramatic bass-baritone","rounded vowels, long-breathed Slavic folk legato, no operatic caricature","E2–C4",70,"piano, low strings, bayan, distant horn","Тихая осень","조용한 가을"),
        new Profile(13,"팝송","영어","soulful textured male mezzo-tenor","short controlled blues runs, syncopated endings","C3–G4",92,"Rhodes, melodic bass, dry drums","Under the City Lights","도시 속 재회"),
        new Profile(14,"발라드","한국어","strong gravelly rock tenor, rich chest","wide dynamic rise, controlled grit at peak, no screaming","B2–B4",76,"cinematic piano, cello, soaring clean/distorted guitar","새벽 길 위에","새벽의 결심"),
        new Profile(15,"트로트","한국어","clean youthful upper tenor with twang","jittery jjorim ornament, bouncing clipped vowels","D3–A4",118,"muted guitar, trumpet, bass, trot drums","빨간 구두","우연한 첫눈맞춤"),
        new Profile(16,"러시아노래","러시아어","soft honey baritone with soft grain","gentle Russian diction, half-whispered low verse, round chorus vowels","G2–D4",78,"nylon guitar, accordion, brushed drums","Кофе у окна","창가의 커피"),
        new Profile(17,"중국노래","중국어 간체","earthy mid tenor with light folk nasal color","clear Chinese syllables, short folk mordents, unforced sustained notes","A2–F4",82,"pipa, dizi, frame drum, acoustic guitar","远方的山路","멀리 있는 산길"),
        new Profile(18,"발라드","한국어","dark cinematic baritone-tenor, close intimate tone","restrained verses then broad cinematic vowels, no vocal gymnastics","G2–G4",72,"low strings, cello, felt piano, muted percussion","불빛 하나","고독 속 작은 희망"),
        new Profile(19,"팝송","영어","springy agile bright baritone","playful swung pickup phrasing, micro-syncopation, no scatting","A2–G4",106,"swing guitar, piano, upright bass, brushes","Meet Me by the River","강가의 약속"),
        new Profile(20,"러시아노래","러시아어","rich mature classical crossover baritone","measured sustained vowels, precise consonants, restrained vibrato","F2–F4",68,"cello, grand piano, classical strings","Письмо домой","고향으로 보내는 편지"),
        new Profile(21,"발라드","한국어","low smoky contralto, warm velvety resonance","low chest voice, subtle breath cracks, no high belting","G3–A4",70,"piano, cello, intimate strings","그날의 온도","지난날 위로"),
        new Profile(22,"중국노래","중국어 간체","clear mellow female mezzo, luminous soft mids","elegant word stress, silk-like slides, subtle vibrato","G3–C5",78,"guzheng, erhu, soft piano, xiao","茶香里的月光","차 향기의 달빛"),
        new Profile(23,"팝송","영어","dusky alto with jazz edge","laid-back blue-note turns, lightly swung phrasing","F3–B4",84,"jazz guitar, double bass, Rhodes","After the Rain","비 온 뒤"),
        new Profile(24,"트로트","한국어","round deep mezzo with clear close resonance","slow traditional bends, poignant vibrato after each held word","G3–B4",86,"accordion, janggu, acoustic guitar, warm strings","고향 정류장","버스 정류장 재회"),
        new Profile(25,"팝송","영어","bright breathy soprano with velvet highs","country inflections, clean diction, tiny grace slides","A3–D5",94,"steel guitar, acoustic drums, country piano","Little Blue Road","여름 끝 여행"),
        new Profile(26,"트로트","한국어","husky strong alto with ringing chest","expressive lower-register 꺾기, bold rhythmic stops","F3–C5",108,"accordion, janggu, brass, acoustic drums","한 잔의 웃음","친구와의 밤"),
        new Profile(27,"러시아노래","러시아어","warm clear lyric mezzo with floating top notes","Russian folk lyrical line, long breath, delicate trailing vibrato","G3–D5",74,"piano, violin, low strings, soft balalaika","Снег на ладони","손바닥의 눈"),
        new Profile(28,"트로트","한국어","silky low alto with smoky middle","slow blues-trot bends, expressive final syllable emphasis","F3–B4",96,"piano, bass, clean guitar, trot kit","내 사랑 봄날","늦은 사랑"),
        new Profile(29,"중국노래","중국어 간체","light airy female alto with clear diction","unhurried narrative delivery, tiny pentatonic turns","F3–B4",72,"dizi, xiao, pipa, cello","云上的小路","구름 위 작은 길"),
        new Profile(30,"트로트","한국어","bright youthful female mezzo with vivid twang","lively jjorim and bounce, short punchy chorus hooks","A3–D5",116,"brass, rhythm guitar, trot drum kit","장미 한 송이","방송 응원"),
        new Profile(31,"중국노래","중국어 간체","velvety female mezzo with cinematic lift","gliding Chinese syllables, broad sustained climax with clean tone","G3–D5",74,"erhu, guzheng, cello, orchestral strings","秋日的海","가을 바다"),
        new Profile(32,"러시아노래","러시아어","smoky low female contralto with intimate rasp","very low storytelling verse, gentle folk-soul vibrato","E3–A4",70,"felt piano, balalaika, cello, soft percussion","Тёплый ветер","따뜻한 바람")
    };
    public static int count(){return ALL.length;}
    public static Profile byArtistId(int id){
        if(id<1||id>ALL.length)throw new IllegalArgumentException("Invalid artist");
        return ALL[id-1];
    }
    public static Profile[] all(){return ALL.clone();}
}
