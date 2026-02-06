package ai.makestar.papago.service

import org.springframework.stereotype.Service

@Service
class ContextDetectionService {

    companion object {
        private val CONTEXT_KEYWORDS = mapOf(
            "/artist" to listOf("아티스트", "멤버", "그룹", "솔로", "데뷔", "컴백", "앨범", "타이틀곡", "활동"),
            "/artist/:id" to listOf("프로필", "디스코그래피", "바이오", "생년월일", "소속사"),
            "/project" to listOf("프로젝트", "펀딩", "목표금액", "달성률", "리워드", "후원"),
            "/project/:id" to listOf("상세", "구성품", "제작", "발송", "일정", "공지"),
            "/mypage" to listOf("마이페이지", "내 정보", "주문내역", "포인트", "쿠폰", "설정", "알림"),
            "/order" to listOf("주문", "결제", "배송", "장바구니", "수량", "합계", "카드", "환불", "교환", "반품", "송장", "택배"),
            "/community" to listOf("커뮤니티", "게시글", "댓글", "좋아요", "팔로우", "피드", "타임라인"),
            "/auth" to listOf("로그인", "회원가입", "비밀번호", "인증", "이메일", "본인확인")
        )
    }

    /**
     * Analyze input text and auto-detect the most relevant page context.
     * Returns the page URL path with highest keyword match, or null if no strong match.
     */
    fun detectContext(inputText: String): String? {
        if (inputText.isBlank()) return null

        val scores = mutableMapOf<String, Int>()

        for ((pageUrl, keywords) in CONTEXT_KEYWORDS) {
            var matchCount = 0
            for (keyword in keywords) {
                if (inputText.contains(keyword)) {
                    matchCount++
                }
            }
            if (matchCount > 0) {
                scores[pageUrl] = matchCount
            }
        }

        if (scores.isEmpty()) return null

        // Return the page with the highest match count
        // Require at least 1 match to assign context
        return scores.maxByOrNull { it.value }?.key
    }
}
