package com.rearcare.car

import org.junit.Assert.assertEquals
import org.junit.Test

/** 차에서 읽은 공조 값을 잠 기록의 조건으로 바꾸는 규칙 */
class CabinClimateTest {

    @Test
    fun 공조가_꺼져_있으면_설정_온도는_모르고_바람은_0이다() {
        val climate = CabinController.Climate(on = false, setC = 22f, fan = 3, cabinC = 19.1f, lightOn = true, ambient = 70)
        assertEquals(
            SleepLog.Conditions(kph = 60f, setC = null, fan = 0f, cabinC = 19.1f, lightOn = true, ambient = 70f),
            climate.toConditions(kph = 60f),
        )
    }

    @Test
    fun 공조가_켜져_있거나_켜졌는지_모르면_읽은_값을_그대로_쓴다() {
        assertEquals(
            SleepLog.Conditions(60f, 23f, 3f, 19.1f, false, 30f),
            CabinController.Climate(on = true, setC = 23f, fan = 3, cabinC = 19.1f, lightOn = false, ambient = 30).toConditions(60f),
        )
        assertEquals(
            SleepLog.Conditions(null, 23f, null, null),
            CabinController.Climate(on = null, setC = 23f, fan = null, cabinC = null).toConditions(null),
        )
    }
}
