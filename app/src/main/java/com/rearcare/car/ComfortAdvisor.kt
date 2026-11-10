package com.rearcare.car

/**
 * 아이가 잠들었을 때 차 실내를 바꾸자고 제안할지, 무엇을 제안할지 정한다. 순수 Kotlin.
 *
 * 원칙
 * - 아이에게 "최적 온도"로 정해진 숫자는 없다. 기관 권고는 잠자는 방 기준이고 서로 다르다
 *   (영국 NHS·Lullaby Trust 16~20°C, 질병관리청 22~24도, 미국소아과학회는 숫자 없음). 차 안 기준은 찾지 못했다.
 * - 그래서 앱은 "서늘해요/더워요"를 스스로 판정하지 않는다. 부모가 정차 중 정해 둔 "잠잘 때 범위"와 비교한 사실만 말한다.
 *   예: "지금 차 안이 19도쯤인데, 정해 두신 온도보다 낮아요."
 * - 범위를 정하지 않았으면 온도는 제안하지 않는다(조명·바람만).
 * - 지금 값이 기준을 벗어났을 때만 제안한다. 여러 개면 한 번에 묻는다.
 * - 거절하면 30분 동안, 또는 온도가 1도 이상 더 벗어나기 전까지 다시 묻지 않는다.
 * - 바람 단계(4단 이상이면 1단으로)와 무드등 밝기(30 이상이면 10으로)는 근거가 있는 기준이 아니라 정한 값이다.
 */
class ComfortAdvisor(
    private val fanHighFrom: Int = 4, // 바람이 이 단계 이상이면 줄이자고 한다
    private val fanSleepLevel: Int = 1, // 줄일 때 단계
    private val ambientBrightFrom: Int = 30, // 무드등 밝기(0~100)가 이 값 이상이면 어둡게 하자고 한다
    private val ambientSleepLevel: Int = 10, // 어둡게 할 때 밝기. 끄지 않고 은은하게 남긴다
    private val declineQuietMs: Long = 30 * 60_000L,
    private val declineTempStepC: Float = 1f,
) {
    /** 부모가 정한 잠잘 때 온도와, 묻지 않고 넘어가는 폭(℃). 설정 화면에서는 온도 하나만 고른다 */
    data class SleepRange(val minC: Float, val maxC: Float) {
        /** 바꿀 때 맞출 값: 범위 가운데(0.5도 단위) */
        val targetC: Float get() = Math.round((minC + maxC)) / 2f

        companion object {
            /**
             * 고른 온도의 앞뒤 이만큼은 묻지 않는다. 안 그러면 0.1도만 달라도 묻게 된다.
             * 근거가 있는 숫자가 아니라, 공조가 맞춰 가는 동안 같은 말을 되풀이하지 않으려고 정한 값이다.
             */
            private const val MARGIN_C = 1f

            fun around(targetC: Float) = SleepRange(targetC - MARGIN_C, targetC + MARGIN_C)
        }
    }

    /** 지금 차 상태. 모르는 값은 null */
    data class Cabin(
        val tempC: Float?, // 실제 실내 온도(센서). 설정 온도가 아니다
        val fanSpeed: Int?,
        val cabinLightOn: Boolean?,
        val rearReadingLightOn: Boolean?,
        val climateOn: Boolean? = null, // 공조 전원. 꺼져 있으면 온도만 맞춰서는 소용이 없어 켜자고 같이 묻는다
        val ambientBrightness: Int? = null, // 무드등 밝기(0~100)
    )

    /** 제안 하나. 승낙하면 null이 아닌 것만 바꾼다 */
    data class Proposal(
        val setTempC: Float?,
        val setFanSpeed: Int?,
        val lightsOff: Boolean,
        val question: String,
        val turnClimateOn: Boolean = false, // 온도를 맞추려면 공조를 먼저 켜야 한다
        val shortQuestion: String = question, // 화면에 보일 짧은 질문. 기록에는 전체를 남기고 화면은 묻는 말만 보여 준다
        val setAmbientBrightness: Int? = null,
    ) {
        // 승낙하면 바뀌는 것. 잠 기록은 앱이 바꾼 값을 "자는 동안"으로 세지 않는다. 공조를 켜면 온도와 바람이 둘 다 바뀐다
        val changesTemp: Boolean get() = setTempC != null || turnClimateOn
        val changesFan: Boolean get() = setFanSpeed != null || turnClimateOn
        val changesLight: Boolean get() = lightsOff || setAmbientBrightness != null
    }

    private var declinedAtMs: Long? = null
    private var declinedTempGapC = 0f
    private var changedAtMs: Long? = null

    /**
     * 제안할 것이 있으면 Proposal, 없으면 null.
     * lightsAllowed: 실내등·독서등을 바꿀 수 있는가. 앱에서 끄면 P단이 아닐 때 거절되므로 그때는 묻지 않는다. 되지 않을 일을 묻지 않기 위해서다.
     * askLights: 부모가 설정에서 조명을 묻게 해 두었는가. 아니면 실내등·독서등·무드등을 묻지 않는다.
     */
    fun propose(cabin: Cabin, range: SleepRange?, nowMs: Long, lightsAllowed: Boolean = true, askLights: Boolean = true): Proposal? {
        // 온도: 범위를 벗어났을 때만, 그리고 거절 뒤에는 더 벗어났을 때만. 방금 맞췄으면 실내가 따라갈 시간을 준다
        val climateOff = cabin.climateOn == false
        val temp = cabin.tempC
        val gap = if (temp != null && range != null) gapC(temp, range) else 0f
        val justChanged = changedAtMs?.let { nowMs - it < declineQuietMs } ?: false
        val tempOff = temp != null && range != null && gap > 0f && !quietAfterDecline(nowMs, gap) && !justChanged
        val setTemp = if (tempOff) range!!.targetC else null
        val tooLow = tempOff && temp!! < range!!.minC

        // 바람·조명: 거절 뒤 30분은 묻지 않는다. 공조가 꺼져 있으면 바람이 나오지 않으니 줄일 것도 없다
        val quiet = declinedAtMs?.let { nowMs - it < declineQuietMs } ?: false
        val fan = cabin.fanSpeed
        val setFan = if (!quiet && !climateOff && fan != null && fan >= fanHighFrom) fanSleepLevel else null
        val lightsOn = askLights && lightsAllowed && !quiet && (cabin.cabinLightOn == true || cabin.rearReadingLightOn == true)
        val ambient = cabin.ambientBrightness
        val setAmbient = if (askLights && !quiet && ambient != null && ambient >= ambientBrightFrom) ambientSleepLevel else null

        if (setTemp == null && setFan == null && !lightsOn && setAmbient == null) return null
        val turnOn = climateOff && setTemp != null
        val others = setAmbient != null // 무드등도 함께 묻는가

        // 말하듯이 쓴다. 판정("서늘해요")은 하지 않고, 정해 둔 온도와 비교한 사실만 말한다
        val fact = if (setTemp != null) {
            "지금 차 안이 ${Math.round(temp!!)}도쯤인데, 정해 두신 온도보다 ${if (tooLow) "낮아요" else "높아요"}."
        } else {
            listOfNotNull(
                when {
                    setFan != null && lightsOn -> "바람이 ${fan}단이고 실내 불도 켜져 있어요."
                    setFan != null -> "바람이 ${fan}단이에요."
                    lightsOn -> "실내 불이 켜져 있어요."
                    else -> null
                },
                setAmbient?.let { "무드등 밝기가 ${ambient}이에요." },
            ).joinToString(" ")
        }
        // (앞 문장에 잇는 질문, 앞 문장 없이도 뜻이 통하게 무엇을 할지 밝힌 짧은 질문)
        val (ask, short) = when {
            setTemp == null && setFan != null && !lightsOn && !others -> "좀 줄여 드릴까요?" to "바람을 줄여 드릴까요?"
            setTemp == null && setFan == null && lightsOn && !others -> "꺼 드릴까요?" to "불을 꺼 드릴까요?"
            else -> {
                // (이어 갈 때 말, 끝낼 때 말). 먼저 나온 것이 있으면 "바람도", "불도"로 잇는다
                val steps = mutableListOf<Pair<String, String>>()
                setTemp?.let {
                    val how = if (!turnOn) "" else if (tooLow) "히터를 켜서 " else "에어컨을 켜서 "
                    steps += "$how${deg(it)}로 맞추고" to "$how${deg(it)}로 맞춰"
                }
                if (setFan != null) {
                    val who = if (steps.isEmpty()) "바람을" else "바람도"
                    steps += "$who 줄이고" to "$who 줄여"
                }
                if (lightsOn) {
                    val who = if (steps.isEmpty()) "불을" else "불도"
                    steps += "$who 끄고" to "$who 꺼"
                }
                if (setAmbient != null) {
                    val who = if (steps.isEmpty()) "무드등을" else "무드등도"
                    steps += "$who 어둡게 하고" to "$who 어둡게 해"
                }
                val whole = (steps.dropLast(1).map { it.first } + steps.last().second).joinToString(" ") + " 드릴까요?"
                whole to whole
            }
        }
        return Proposal(
            setTemp, setFan, lightsOn, "$fact $ask",
            turnClimateOn = turnOn,
            shortQuestion = short,
            setAmbientBrightness = setAmbient,
        )
    }

    /** 운전자가 거절했거나 대답이 없을 때. 이때의 온도 차이를 기억한다 */
    fun declined(cabin: Cabin, range: SleepRange?, nowMs: Long) {
        declinedAtMs = nowMs
        declinedTempGapC = if (cabin.tempC != null && range != null) gapC(cabin.tempC, range) else 0f
    }

    /**
     * 승낙해서 바꾼 뒤. 거절 기록을 지우고, 30분 동안 온도를 다시 묻지 않는다.
     * 실내 온도는 설정을 바꿔도 바로 따라오지 않아서, 안 그러면 다시 잠들거나 차를 세울 때 같은 말을 하게 된다.
     */
    fun accepted(nowMs: Long) {
        declinedAtMs = null
        declinedTempGapC = 0f
        changedAtMs = nowMs
    }

    // 거절한 지 30분이 안 됐고, 그때보다 1도 이상 더 벗어나지 않았으면 조용히 있는다
    private fun quietAfterDecline(nowMs: Long, gap: Float): Boolean {
        val at = declinedAtMs ?: return false
        return nowMs - at < declineQuietMs && gap < declinedTempGapC + declineTempStepC
    }

    /** 범위에서 벗어난 정도(℃). 범위 안이면 0 */
    private fun gapC(t: Float, r: SleepRange) = when {
        t < r.minC -> r.minC - t
        t > r.maxC -> t - r.maxC
        else -> 0f
    }

    companion object {
        /**
         * 아이가 깼을 때 되돌릴지 묻는 말. 되돌릴 것이 없으면 null.
         * 불만 되돌린다. 온도·바람·공조 전원은 그대로 둔다.
         * 실내등·독서등은 P단에서만 바뀌므로 주행 중에는 묻지 않는다. 되지 않을 일을 묻지 않기 위해서다.
         */
        fun restoreQuestion(ambient: Boolean, switchLights: Boolean, parked: Boolean): String? = when {
            switchLights && parked && ambient -> "불을 다시 켜고 무드등도 밝게 해 드릴까요?"
            switchLights && parked -> "불을 다시 켜 드릴까요?"
            ambient -> "무드등을 다시 밝게 해 드릴까요?"
            else -> null
        }

        /**
         * 무드등을 천천히 바꿀 때 차례로 거치는 밝기. 마지막 값은 반드시 to다.
         */
        fun fadeLevels(from: Int, to: Int, steps: Int): List<Int> =
            if (from == to || steps <= 1) listOf(to) else (1..steps).map { from + (to - from) * it / steps }

        /** 19.0 → "19도", 19.1 → "19.1도". 반올림해서 범위와 어긋나 보이지 않게 소수 한 자리까지 말한다 */
        fun deg(c: Float, unit: Boolean = true): String {
            val tenth = Math.round(c * 10)
            val text = if (tenth % 10 == 0) "${tenth / 10}" else "${tenth / 10}.${tenth % 10}"
            return if (unit) "${text}도" else text
        }
    }
}
