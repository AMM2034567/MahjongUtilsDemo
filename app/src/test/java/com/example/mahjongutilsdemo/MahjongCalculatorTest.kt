package com.example.mahjongutilsdemo

import mahjongutils.models.Wind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 针对 mahjong-utils 0.7.7 集成的 JVM 单元测试。
 * 用例与官方 README 对齐，作为 API 兼容性回归。
 */
class MahjongCalculatorTest {

    // ---------- 向听 ----------

    @Test
    fun shanten13Tiles() {
        // 官方 README 示例：34568m235p68s → 2 向听，进张 12 种
        val text = MahjongCalculator.analyzeShanten("34568m235p68s")
        assertTrue(text, text.contains("【向听数】2"))
        assertTrue(text, text.contains("【进张】"))
        // 进张应包含 7m
        assertTrue(text, text.contains("7m"))
    }

    @Test
    fun shanten14TilesShowsDiscardAdvice() {
        // 官方 README 示例：14 张 → 1 向听，给出切牌建议
        val text = MahjongCalculator.analyzeShanten("112233p44556s127z")
        assertTrue(text, text.contains("【向听数】1"))
        assertTrue(text, text.contains("【切牌推荐】"))
        assertTrue(text, text.contains("切 "))
        // 最优打法应带 ★ 标记
        assertTrue(text, text.contains("★"))
    }

    @Test
    fun shantenWithFuro() {
        // 10 张门前 + 1 副露（3 张）= 13 张
        val text = MahjongCalculator.analyzeShanten("12233466m111z", "789p")
        assertTrue(text, text.contains("【向听数】"))
    }

    @Test
    fun invalidHandReturnsError() {
        val result = MahjongCalculator.runSafe {
            MahjongCalculator.analyzeShanten("123456789m111z22s33p") // 19 张，非法
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun invalidTileTextReturnsError() {
        val result = MahjongCalculator.runSafe {
            MahjongCalculator.analyzeShanten("xxxx")
        }
        assertTrue(result.isFailure)
    }

    // ---------- 和牌分析 ----------

    @Test
    fun horaOfficialExample() {
        // 官方 README 示例：
        // 12233466m111z + 副露 789p，1z 自摸，宝牌 4，自风东、场风东
        // → 役 {SelfWind, RoundWind}，6 番 30 符，庄家自摸每家 6000
        val text = MahjongCalculator.analyzeHora(
            handText = "12233466m111z",
            furoText = "789p",
            agariText = "1z",
            tsumo = true,
            dora = 4,
            selfWind = Wind.East,
            roundWind = Wind.East,
        )
        assertTrue(text, text.contains("自风"))
        assertTrue(text, text.contains("场风"))
        assertTrue(text, text.contains("【番】6 番"))
        assertTrue(text, text.contains("【符】30 符"))
        // 自风+场风各 1 番 + 宝牌 4 = 6 番
        assertTrue(text, text.contains("自风"))
        assertTrue(text, text.contains("6000"))
    }

    @Test
    fun horaTanyaoPinfu() {
        // 门清断平自摸：345m 678m 234p 56p + 7p 成 567p，22s 雀头
        // 平和1 + 断幺1 + 自摸1 = 3 番 20 符（平和自摸）
        val text = MahjongCalculator.analyzeHora(
            handText = "345678m23456p22s",
            furoText = "",
            agariText = "7p",
            tsumo = true,
            dora = 0,
            selfWind = Wind.South,
            roundWind = Wind.East,
        )
        assertTrue(text, text.contains("平和"))
        assertTrue(text, text.contains("断幺"))
        assertTrue(text, text.contains("自摸"))
    }

    @Test
    fun horaWithRichiExtraYaku() {
        val text = MahjongCalculator.analyzeHora(
            handText = "123456789m111z22s",
            furoText = "",
            agariText = "9m",
            tsumo = false,
            dora = 0,
            selfWind = Wind.East,
            roundWind = Wind.East,
            extraYakuNames = setOf("Richi"),
        )
        assertTrue(text, text.contains("立直"))
    }

    @Test
    fun horaInvalidHandFails() {
        // 非和牌形：无法完成和牌分析
        val result = MahjongCalculator.runSafe {
            MahjongCalculator.analyzeHora(
                handText = "34568m235p68s",
                furoText = "",
                agariText = "7m",
                tsumo = true,
                dora = 0,
                selfWind = Wind.East,
                roundWind = Wind.East,
            )
        }
        assertTrue(result.isFailure)
    }

    // ---------- 点数计算 ----------

    @Test
    fun points3han40fu() {
        // 官方 README 示例：3 番 40 符
        // 庄家：荣和 7700，自摸每家 2600（合计 7800）
        // 闲家：荣和 5200，自摸庄 2600 + 闲 1300
        val text = MahjongCalculator.calculatePoints(3, 40)
        assertTrue(text, text.contains("3 番 40 符"))
        assertTrue(text, text.contains("7700"))
        assertTrue(text, text.contains("2600"))
        assertTrue(text, text.contains("5200"))
        assertTrue(text, text.contains("1300"))
    }

    @Test
    fun pointsMangan() {
        val text = MahjongCalculator.calculatePoints(5, 30)
        assertTrue(text, text.contains("8000"))
    }

    @Test
    fun pointsYakuman() {
        val text = MahjongCalculator.calculatePoints(13, 30)
        assertTrue(text, text.contains("32000") || text.contains("16000"))
    }

    // ---------- 解析 ----------

    @Test
    fun parseAkaDora() {
        // 0m 为红 5 万，解析不应报错
        val text = MahjongCalculator.analyzeShanten("0m23456789m111z2s")
        assertTrue(text, text.contains("【向听数】"))
    }

    @Test
    fun parseMultipleFuro() {
        val furo = MahjongCalculator.parseFuro("789p 111z")
        assertEquals(2, furo.size)
    }

    @Test
    fun parseEmptyFuro() {
        assertTrue(MahjongCalculator.parseFuro("").isEmpty())
        assertTrue(MahjongCalculator.parseFuro("   ").isEmpty())
    }

    @Test
    fun resultConsistentWithLibrary() {
        // 点数模块与 hora 结果一致：用 hora 得到的番符反查点数应相同
        val horaText = MahjongCalculator.analyzeHora(
            handText = "12233466m111z",
            furoText = "789p",
            agariText = "1z",
            tsumo = true,
            dora = 4,
            selfWind = Wind.East,
            roundWind = Wind.East,
        )
        // hora: 6 番 30 符 → 庄家自摸每家 6000
        val pointText = MahjongCalculator.calculatePoints(6, 30)
        assertTrue(horaText, horaText.contains("6000"))
        assertTrue(pointText, pointText.contains("6000"))
    }
}
