package com.rearcare.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 화면에 쓰는 질문과 결과 문장.
 * 결과 문장은 실제로 바뀐 수와 실패한 수를 보고 고른다. 앱은 실제 결과를 확인한 뒤에 말한다.
 */
class StatusWordsTest {

    @Test
    fun 잠듦_제안을_실행한_결과() {
        assertEquals("이미 그렇게 되어 있어요.", StatusWords.applyResult(changed = 0, failed = 0))
        assertEquals("네, 맞춰 뒀어요.", StatusWords.applyResult(changed = 2, failed = 0))
        assertEquals("죄송해요, 바꾸지 못했어요.", StatusWords.applyResult(changed = 0, failed = 1))
        assertEquals("일부만 바뀌었어요. 한번 확인해 주세요.", StatusWords.applyResult(changed = 1, failed = 1))
    }

    @Test
    fun 불_되돌리기_결과는_도중에_멈췄으면_말하지_않는다() {
        // 무드등을 천천히 밝히는 중에 다시 어둡게 하면 바꾼 것도 실패한 것도 없이 끝난다
        assertNull(StatusWords.restoreResult(changed = 0, failed = 0))
        assertEquals("네, 원래대로 돌려 뒀어요.", StatusWords.restoreResult(changed = 1, failed = 0))
        assertEquals("죄송해요, 되돌리지 못했어요.", StatusWords.restoreResult(changed = 0, failed = 2))
        assertEquals("일부만 돌아갔어요. 한번 확인해 주세요.", StatusWords.restoreResult(changed = 1, failed = 1))
    }

    @Test
    fun 뒷창문_잠금_결과() {
        assertEquals("네, 뒷창문을 잠갔어요.", StatusWords.lockResult(changed = 2, failed = 0))
        assertEquals("네, 뒷창문을 잠갔어요.", StatusWords.lockResult(changed = 0, failed = 0)) // 지금 동작 그대로
        assertEquals("죄송해요, 잠그지 못했어요.", StatusWords.lockResult(changed = 0, failed = 2))
        assertEquals("일부만 잠겼어요. 한번 확인해 주세요.", StatusWords.lockResult(changed = 1, failed = 1))
    }

    @Test
    fun 그대로_둘_때_대답이_없었으면_네라고_하지_않는다() {
        assertEquals("네, 그대로 둘게요.", StatusWords.keep(answer = false))
        assertEquals("그럼 일단 그대로 둘게요.", StatusWords.keep(answer = null))
    }

    @Test
    fun 여러_화면이_같이_쓰는_글() {
        assertEquals("오늘 아이와의 운행을 기록할까요?", StatusWords.TRIP_QUESTION)
        assertEquals("정하지 않음", StatusWords.NOT_SET)
    }
}
