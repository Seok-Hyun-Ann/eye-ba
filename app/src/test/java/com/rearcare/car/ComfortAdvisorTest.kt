package com.rearcare.car

import com.rearcare.car.ComfortAdvisor.Cabin
import com.rearcare.car.ComfortAdvisor.SleepRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 잠들었을 때 제안 규칙 시험. 앱이 "서늘해요/더워요"를 판정하지 않고, 부모가 정한 범위와 비교한 사실만 말하는지 본다 */
class ComfortAdvisorTest {

    private val range = SleepRange(22f, 24f)
    private val quietCabin = Cabin(tempC = 23f, fanSpeed = 2, cabinLightOn = false, rearReadingLightOn = false)

    @Test
    fun 고른_온도의_앞뒤_1도는_묻지_않고_넘으면_그_온도로_맞추자고_함() {
        val chosen = SleepRange.around(23f)
        assertEquals(23f, chosen.targetC)
        assertNull(ComfortAdvisor().propose(quietCabin.copy(tempC = 22f), chosen, 0))
        assertNull(ComfortAdvisor().propose(quietCabin.copy(tempC = 24f), chosen, 0))
        val cold = ComfortAdvisor().propose(quietCabin.copy(tempC = 21.5f), chosen, 0)
        assertEquals(23f, cold!!.setTempC)
        assertEquals(23.5f, SleepRange.around(23.5f).targetC) // 0.5도 단위도 그대로
    }

    @Test
    fun 범위_안이고_불이_꺼져_있으면_제안하지_않음() {
        assertNull(ComfortAdvisor().propose(quietCabin, range, 0))
    }

    @Test
    fun 범위보다_낮으면_사실을_말하고_가운데로_맞추자고_함() {
        val p = ComfortAdvisor().propose(quietCabin.copy(tempC = 19.1f), range, 0)
        assertNotNull(p)
        assertEquals("지금 차 안이 19도쯤인데, 정해 두신 온도보다 낮아요. 23도로 맞춰 드릴까요?", p!!.question)
        assertEquals(23f, p.setTempC)
        assertTrue("서늘" !in p.question && "추워" !in p.question)
    }

    @Test
    fun 범위보다_높고_바람이_세고_불이_켜져_있으면_한_번에_물음() {
        val p = ComfortAdvisor().propose(Cabin(26f, 5, cabinLightOn = true, rearReadingLightOn = false), range, 0)
        assertEquals("지금 차 안이 26도쯤인데, 정해 두신 온도보다 높아요. 23도로 맞추고 바람도 줄이고 불도 꺼 드릴까요?", p!!.question)
        assertEquals(1, p.setFanSpeed)
        assertTrue(p.lightsOff)
    }

    @Test
    fun 범위를_정하지_않았으면_온도는_제안하지_않음() {
        assertNull(ComfortAdvisor().propose(quietCabin.copy(tempC = 30f), null, 0))
        val p = ComfortAdvisor().propose(quietCabin.copy(tempC = 30f, rearReadingLightOn = true), null, 0)
        assertEquals("실내 불이 켜져 있어요. 꺼 드릴까요?", p!!.question)
        assertNull(p.setTempC)
    }

    @Test
    fun 바람과_불만_있을_때_문장() {
        val fan = ComfortAdvisor().propose(quietCabin.copy(fanSpeed = 5), range, 0)
        assertEquals("바람이 5단이에요. 좀 줄여 드릴까요?", fan!!.question)
        val both = ComfortAdvisor().propose(quietCabin.copy(fanSpeed = 5, cabinLightOn = true), range, 0)
        assertEquals("바람이 5단이고 실내 불도 켜져 있어요. 바람을 줄이고 불도 꺼 드릴까요?", both!!.question)
    }

    @Test
    fun 무드등이_밝으면_어둡게_하자고_함() {
        val p = ComfortAdvisor().propose(quietCabin.copy(ambientBrightness = 70), range, 0)
        assertEquals("무드등 밝기가 70이에요. 무드등을 어둡게 해 드릴까요?", p!!.question)
        assertEquals("무드등을 어둡게 해 드릴까요?", p.shortQuestion)
        assertEquals(10, p.setAmbientBrightness)
        assertFalse(p.lightsOff)
    }

    @Test
    fun 무드등이_이미_어둡거나_모르면_묻지_않음() {
        assertNull(ComfortAdvisor().propose(quietCabin.copy(ambientBrightness = 29), range, 0))
        assertNull(ComfortAdvisor().propose(quietCabin.copy(ambientBrightness = null), range, 0))
    }

    @Test
    fun 온도와_무드등을_한_번에_물음() {
        val p = ComfortAdvisor().propose(
            quietCabin.copy(tempC = 19.1f, climateOn = false, ambientBrightness = 70),
            range,
            0,
        )
        assertEquals("히터를 켜서 23도로 맞추고 무드등도 어둡게 해 드릴까요?", p!!.shortQuestion)
        assertEquals(23f, p.setTempC)
        assertEquals(10, p.setAmbientBrightness)
    }

    @Test
    fun 불과_무드등이_함께면_이어서_물음() {
        val p = ComfortAdvisor().propose(quietCabin.copy(cabinLightOn = true, ambientBrightness = 70), range, 0)
        assertEquals("실내 불이 켜져 있어요. 무드등 밝기가 70이에요. 불을 끄고 무드등도 어둡게 해 드릴까요?", p!!.question)
    }

    @Test
    fun 불을_바꿀_수_없을_때는_불을_묻지_않음() {
        val lit = quietCabin.copy(cabinLightOn = true, rearReadingLightOn = true)
        assertNull(ComfortAdvisor().propose(lit, range, 0, lightsAllowed = false))
        val p = ComfortAdvisor().propose(lit.copy(ambientBrightness = 70), range, 0, lightsAllowed = false)
        assertEquals("무드등을 어둡게 해 드릴까요?", p!!.shortQuestion)
        assertFalse(p.lightsOff)
    }

    @Test
    fun 조명을_묻지_않게_해_두면_불과_무드등을_묻지_않음() {
        val lit = quietCabin.copy(cabinLightOn = true, rearReadingLightOn = true, ambientBrightness = 70)
        assertNull(ComfortAdvisor().propose(lit, range, 0, askLights = false))
    }

    @Test
    fun 조명을_묻지_않게_해_두어도_온도와_바람은_묻는다() {
        val p = ComfortAdvisor().propose(Cabin(19.1f, 5, cabinLightOn = true, rearReadingLightOn = false, ambientBrightness = 70), range, 0, askLights = false)
        assertEquals(23f, p!!.setTempC)
        assertEquals(1, p.setFanSpeed)
        assertFalse(p.lightsOff)
        assertNull(p.setAmbientBrightness)
        assertEquals("23도로 맞추고 바람도 줄여 드릴까요?", p.shortQuestion)
    }

    @Test
    fun 거절하면_무드등도_30분_동안_묻지_않음() {
        val a = ComfortAdvisor()
        val cabin = quietCabin.copy(ambientBrightness = 70)
        a.declined(cabin, range, 0)
        assertNull(a.propose(cabin, range, 10 * 60_000L))
        assertNotNull(a.propose(cabin, range, 31 * 60_000L))
    }

    @Test
    fun 거절하면_30분_동안_다시_묻지_않지만_1도_더_벗어나면_묻는다() {
        val a = ComfortAdvisor()
        val cold = quietCabin.copy(tempC = 20f)
        a.declined(cold, range, 0)
        assertNull(a.propose(cold, range, 10 * 60_000L))
        assertNull(a.propose(cold.copy(tempC = 19.5f), range, 10 * 60_000L)) // 0.5도 더: 아직
        assertNotNull(a.propose(cold.copy(tempC = 19f), range, 10 * 60_000L)) // 1도 더: 다시 묻는다
        assertNotNull(a.propose(cold, range, 31 * 60_000L)) // 30분 지남
    }

    @Test
    fun 공조가_꺼져_있으면_켜고_맞추자고_함() {
        val p = ComfortAdvisor().propose(quietCabin.copy(tempC = 19.1f, climateOn = false), range, 0)
        assertEquals("지금 차 안이 19도쯤인데, 정해 두신 온도보다 낮아요. 히터를 켜서 23도로 맞춰 드릴까요?", p!!.question)
        assertTrue(p.turnClimateOn)
        assertEquals(23f, p.setTempC)
        val hot = ComfortAdvisor().propose(quietCabin.copy(tempC = 27f, climateOn = false), range, 0)
        assertEquals("지금 차 안이 27도쯤인데, 정해 두신 온도보다 높아요. 에어컨을 켜서 23도로 맞춰 드릴까요?", hot!!.question)
    }

    @Test
    fun 공조가_켜져_있거나_모르면_전원은_건드리지_않음() {
        val on = ComfortAdvisor().propose(quietCabin.copy(tempC = 19.1f, climateOn = true), range, 0)
        assertEquals("지금 차 안이 19도쯤인데, 정해 두신 온도보다 낮아요. 23도로 맞춰 드릴까요?", on!!.question)
        assertFalse(on.turnClimateOn)
        assertFalse(ComfortAdvisor().propose(quietCabin.copy(tempC = 19.1f), range, 0)!!.turnClimateOn)
    }

    @Test
    fun 공조가_꺼져_있으면_바람은_제안하지_않고_온도가_범위_안이면_켜지도_않음() {
        assertNull(ComfortAdvisor().propose(quietCabin.copy(fanSpeed = 5, climateOn = false), range, 0))
        val light = ComfortAdvisor().propose(quietCabin.copy(climateOn = false, cabinLightOn = true), range, 0)
        assertEquals("실내 불이 켜져 있어요. 꺼 드릴까요?", light!!.question)
        assertFalse(light.turnClimateOn)
    }

    @Test
    fun 승낙해서_바꾼_뒤에는_30분_동안_온도를_다시_묻지_않는다() {
        val a = ComfortAdvisor()
        val hot = quietCabin.copy(tempC = 27f)
        a.accepted(0)
        assertNull(a.propose(hot, range, 10 * 60_000L))
        assertNotNull(a.propose(hot, range, 31 * 60_000L))
    }

    @Test
    fun 깼을_때_주행_중에는_무드등만_묻는다() {
        assertEquals("무드등을 다시 밝게 해 드릴까요?", ComfortAdvisor.restoreQuestion(ambient = true, switchLights = false, parked = false))
        assertEquals("무드등을 다시 밝게 해 드릴까요?", ComfortAdvisor.restoreQuestion(ambient = true, switchLights = true, parked = false))
    }

    @Test
    fun 깼을_때_차를_세웠으면_불도_묻는다() {
        assertEquals("불을 다시 켜 드릴까요?", ComfortAdvisor.restoreQuestion(ambient = false, switchLights = true, parked = true))
        assertEquals("불을 다시 켜고 무드등도 밝게 해 드릴까요?", ComfortAdvisor.restoreQuestion(ambient = true, switchLights = true, parked = true))
    }

    @Test
    fun 무드등은_여러_단계를_거쳐_원래_밝기에_닿는다() {
        assertEquals(listOf(16, 22, 28, 34, 40, 46, 52, 58, 64, 70), ComfortAdvisor.fadeLevels(10, 70, 10))
        assertEquals(listOf(70), ComfortAdvisor.fadeLevels(70, 70, 10)) // 이미 그 밝기면 한 번만
        assertEquals(listOf(70), ComfortAdvisor.fadeLevels(10, 70, 1))
        assertEquals(listOf(40, 10), ComfortAdvisor.fadeLevels(70, 10, 2)) // 어두워지는 쪽도 마지막 값이 맞다
    }

    @Test
    fun 깼을_때_되돌릴_수_있는_불이_없으면_묻지_않는다() {
        assertNull(ComfortAdvisor.restoreQuestion(ambient = false, switchLights = false, parked = true)) // 온도만 바꾼 경우
        assertNull(ComfortAdvisor.restoreQuestion(ambient = false, switchLights = true, parked = false)) // 실내등은 주행 중에 못 켠다
    }

    @Test
    fun 화면의_짧은_질문은_앞_문장_없이도_뜻이_통한다() {
        val temp = ComfortAdvisor().propose(quietCabin.copy(tempC = 19.1f, climateOn = false), range, 0)
        assertEquals("히터를 켜서 23도로 맞춰 드릴까요?", temp!!.shortQuestion)
        val fan = ComfortAdvisor().propose(quietCabin.copy(fanSpeed = 5), range, 0)
        assertEquals("바람을 줄여 드릴까요?", fan!!.shortQuestion)
        val light = ComfortAdvisor().propose(quietCabin.copy(cabinLightOn = true), range, 0)
        assertEquals("불을 꺼 드릴까요?", light!!.shortQuestion)
    }

    @Test
    fun 온도_읽기() {
        assertEquals("19도", ComfortAdvisor.deg(19f))
        assertEquals("19.1도", ComfortAdvisor.deg(19.1f))
        assertEquals("22.5", ComfortAdvisor.deg(22.5f, unit = false))
        assertEquals(20.5f, SleepRange(20f, 21f).targetC)
    }

    // 잠 기록은 앱이 바꾼 값을 "자는 동안"으로 세지 않는다
    private fun changes(p: ComfortAdvisor.Proposal) = listOf(p.changesTemp, p.changesFan, p.changesLight)

    @Test
    fun 승낙하면_온도_바람_조명_중_무엇이_바뀌는지_안다() {
        val temp = ComfortAdvisor.Proposal(setTempC = 23f, setFanSpeed = null, lightsOff = false, question = "")
        assertEquals(listOf(true, false, false), changes(temp))
        assertEquals(listOf(false, true, false), changes(ComfortAdvisor.Proposal(setTempC = null, setFanSpeed = 1, lightsOff = false, question = "")))
        assertEquals(listOf(false, false, true), changes(ComfortAdvisor.Proposal(setTempC = null, setFanSpeed = null, lightsOff = true, question = "")))
        assertEquals(listOf(false, false, true), changes(ComfortAdvisor.Proposal(null, null, false, "", setAmbientBrightness = 10)))
    }

    @Test
    fun 공조를_켜면_온도와_바람이_둘_다_바뀐다() {
        val turnOn = ComfortAdvisor.Proposal(setTempC = 23f, setFanSpeed = null, lightsOff = false, question = "", turnClimateOn = true)
        assertEquals(listOf(true, true, false), changes(turnOn))
    }
}
