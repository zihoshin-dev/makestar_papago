package ai.makestar.papago.service

import org.springframework.stereotype.Service

@Service
class ContextDetectionService(
    private val morphologyService: KoreanMorphologyService
) {

    companion object {
        private val CONTEXT_KEYWORDS = mapOf(
            "/artist" to listOf("아티스트", "멤버", "그룹", "솔로", "데뷔", "컴백", "앨범", "타이틀곡", "활동"),
            "/artist/:id" to listOf("프로필", "디스코그래피", "바이오", "생년월일", "소속사"),
            "/project" to listOf("프로젝트", "펀딩", "목표금액", "달성률", "리워드", "후원"),
            "/project/:id" to listOf("상세", "구성품", "제작", "발송", "일정", "공지"),
            "/mypage" to listOf("마이페이지", "내 정보", "주문내역", "포인트", "쿠폰", "설정", "알림"),
            "/order" to listOf("주문", "결제", "배송", "장바구니", "수량", "합계", "카드", "환불", "교환", "반품", "송장", "택배"),
            "/community" to listOf("커뮤니티", "게시글", "댓글", "좋아요", "팔로우", "피드", "타임라인"),
            "/auth" to listOf("로그인", "회원가입", "비밀번호", "인증", "이메일", "본인확인"),
            "/fandom" to listOf("최애", "덕질", "입덕", "탈덕", "컴백", "총공", "스밍", "직캠", "팬캠", "떡밥"),
            "/event" to listOf("팬싸", "영통", "요소", "포카", "럭키드로우", "응모", "추첨", "당첨")
        )
    }

    private val CONTEXT_DESCRIPTIONS = mapOf(
        "/artist" to "K-Pop 아티스트 목록 및 검색 페이지",
        "/artist/:id" to "K-Pop 아티스트 프로필 상세 페이지",
        "/project" to "크라우드펀딩 프로젝트 목록 페이지",
        "/project/:id" to "크라우드펀딩 프로젝트 상세 페이지",
        "/mypage" to "사용자 마이페이지 (개인 정보, 주문 내역)",
        "/order" to "주문/결제/배송 관련 페이지",
        "/community" to "팬 커뮤니티 게시판 페이지",
        "/auth" to "로그인/회원가입 인증 페이지",
        "/fandom" to "K-Pop 팬덤 활동 페이지",
        "/event" to "팬 이벤트 (팬싸인회, 영통, 럭키드로우) 페이지"
    )

    fun getContextDescription(pageUrl: String?): String? {
        if (pageUrl.isNullOrBlank()) return null
        return CONTEXT_DESCRIPTIONS.entries
            .firstOrNull { pageUrl.startsWith(it.key.replace("/:id", "")) }
            ?.value
    }

    /**
     * Analyze input text and auto-detect the most relevant page context.
     * Uses morphological stemming for better keyword matching.
     * Returns the page URL path with highest keyword match, or null if no strong match.
     */
    fun detectContext(inputText: String): String? {
        if (inputText.isBlank()) return null

        // Pre-compute stems for all words in the input
        val words = inputText.split(Regex("[\\s,.!?;:()\\[\\]{}\"'~·…/\\\\|@#\$%^&*+=<>]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val inputStems = words.flatMap { morphologyService.stem(it) }.toSet()

        val scores = mutableMapOf<String, Int>()

        for ((pageUrl, keywords) in CONTEXT_KEYWORDS) {
            var matchCount = 0
            for (keyword in keywords) {
                // Stem-based match: check if any stem matches the keyword
                val stemMatch = inputStems.contains(keyword)
                // Fallback: substring match for multi-word keywords (e.g., "내 정보")
                val containsMatch = inputText.contains(keyword)
                if (stemMatch || containsMatch) {
                    matchCount++
                }
            }
            if (matchCount > 0) {
                scores[pageUrl] = matchCount
            }
        }

        if (scores.isEmpty()) return null

        // Return the page with the highest match count
        return scores.maxByOrNull { it.value }?.key
    }
}
