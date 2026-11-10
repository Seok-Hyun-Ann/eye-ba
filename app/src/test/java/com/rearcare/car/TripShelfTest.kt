package com.rearcare.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 다닌 곳 선반 규칙 시험 */
class TripShelfTest {

    private fun trip(id: String, name: String, date: String = "2025-04-12", lat: Double? = null, poi: String = "") =
        Trip(id, LocalDate.parse(date), 30, name, latitude = lat, poiId = poi)

    @Test
    fun `그림의 계절은 기록 날짜의 달로 정한다`() {
        assertEquals(Season.WINTER, TripShelf.season(LocalDate.of(2025, 2, 28)))
        assertEquals(Season.SPRING, TripShelf.season(LocalDate.of(2025, 3, 1)))
        assertEquals(Season.SPRING, TripShelf.season(LocalDate.of(2025, 5, 31)))
        assertEquals(Season.SUMMER, TripShelf.season(LocalDate.of(2025, 6, 1)))
        assertEquals(Season.SUMMER, TripShelf.season(LocalDate.of(2025, 8, 31)))
        assertEquals(Season.AUTUMN, TripShelf.season(LocalDate.of(2025, 9, 1)))
        assertEquals(Season.AUTUMN, TripShelf.season(LocalDate.of(2025, 11, 30)))
        assertEquals(Season.WINTER, TripShelf.season(LocalDate.of(2025, 12, 1)))
    }

    @Test
    fun `나이 줄 제목`() {
        assertEquals("3살 때", TripShelf.title(3))
        assertEquals("돌 전", TripShelf.title(0))
        assertEquals("생일 전", TripShelf.title(TripDiary.BEFORE_BIRTH))
        assertEquals("다닌 곳", TripShelf.title(null))
    }

    @Test
    fun `나이 줄 설명은 몇 곳과 그 나이였던 기간이다`() {
        val birth = LocalDate.of(2021, 3, 10)
        val group = TripDiary.Group(3, listOf(trip("1", "경주"), trip("2", "부산"), trip("3", "경주")))
        assertEquals("2곳 · 2024년 3월 ~ 2025년 3월", TripShelf.meta(group, birth))
    }

    @Test
    fun `생일을 모르면 기간 대신 생일을 넣으라고 한다`() {
        val group = TripDiary.Group(null, listOf(trip("1", "경주")))
        assertEquals("1곳 · 설정에서 생일을 넣으면 나이별로 모여요", TripShelf.meta(group, null))
    }

    @Test
    fun `생일 전 기록에는 기간을 쓰지 않는다`() {
        val group = TripDiary.Group(TripDiary.BEFORE_BIRTH, listOf(trip("1", "경주")))
        assertEquals("1곳", TripShelf.meta(group, LocalDate.of(2021, 3, 10)))
    }

    @Test
    fun `위치나 장소 번호가 있어야 내비로 갈 수 있다`() {
        assertTrue(TripShelf.canNavigate(trip("1", "경주", lat = 35.8)))
        assertTrue(TripShelf.canNavigate(trip("2", "경주", poi = "abc")))
        assertFalse(TripShelf.canNavigate(trip("3", TripDiary.UNKNOWN_PLACE)))
    }

    @Test
    fun `사진 파일 이름은 기록 번호로 만들고 폴더를 벗어나지 못한다`() {
        assertEquals("1793886274464.jpg", TripShelf.photoFileName("1793886274464"))
        assertEquals("etcpasswd.jpg", TripShelf.photoFileName("../../etc/passwd"))
        assertEquals("trip.jpg", TripShelf.photoFileName("///"))
    }

    @Test
    fun `첫 사진의 파일 이름은 번호가 없고 다음 사진부터 번호가 붙는다`() {
        assertEquals("sample23.jpg", TripShelf.photoFileName("sample-23", 0))
        assertEquals("sample23_2.jpg", TripShelf.photoFileName("sample-23", 2))
    }

    @Test
    fun `파일 이름에서 그 기록의 사진 번호를 읽는다`() {
        assertEquals(0, TripShelf.photoNumber("sample-23", "sample23.jpg"))
        assertEquals(12, TripShelf.photoNumber("sample-23", "sample23_12.jpg"))
    }

    @Test
    fun `다른 기록의 사진이나 사진이 아닌 파일은 번호를 읽지 않는다`() {
        assertNull(TripShelf.photoNumber("sample-2", "sample23.jpg"))
        assertNull(TripShelf.photoNumber("sample-2", "sample23_1.jpg"))
        assertNull(TripShelf.photoNumber("sample-23", "sample23.jpg.new"))
        assertNull(TripShelf.photoNumber("sample-23", "sample23_x.jpg"))
        assertNull(TripShelf.photoNumber("sample-23", "sample23_0.jpg"))
    }

    @Test
    fun `사진을 넣은 결과는 실제로 넣은 장수로 말한다`() {
        assertEquals("사진을 넣었어요.", TripShelf.photoResultLine(1, 1))
        assertEquals("사진 3장을 넣었어요.", TripShelf.photoResultLine(3, 3))
        assertEquals("3장 중 2장만 넣었어요.", TripShelf.photoResultLine(2, 3))
        assertEquals("사진을 넣지 못했어요.", TripShelf.photoResultLine(0, 3))
    }

    @Test
    fun `큰 사진은 긴 쪽을 맞춰 줄이고 비율을 지킨다`() {
        assertEquals(1280 to 960, TripShelf.scaledSize(4000, 3000, 1280))
        assertEquals(960 to 1280, TripShelf.scaledSize(3000, 4000, 1280))
    }

    @Test
    fun `작은 사진은 키우지 않는다`() {
        assertEquals(800 to 600, TripShelf.scaledSize(800, 600, 1280))
    }

    @Test
    fun `읽을 때는 카드보다 작아지지 않는 만큼만 건너뛴다`() {
        assertEquals(2, TripShelf.sampleSize(1280, 960, 372, 440))
        assertEquals(1, TripShelf.sampleSize(800, 600, 372, 440))
        assertEquals(4, TripShelf.sampleSize(1600, 1920, 372, 440))
    }
}
