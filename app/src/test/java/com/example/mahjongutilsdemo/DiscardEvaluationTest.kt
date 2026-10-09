package com.example.mahjongutilsdemo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * evaluateDiscards（切牌评分与牌效率评估）的单元测试：
 * 覆盖排序不变量、听牌 EPT、以及手牌解析失败 / 13·14·15 张等边界情况。
 */
class DiscardEvaluationTest {

    private data class Row(val shanten: Int, val advanceNum: Int, val ept: Int)

    private fun parseRows(text: String): List<Row> =
        Regex("""^\s+\d+\s+\S+\s+(\d+)\s+(\d+)\s+(-|\d+)(?:\s|$)""", RegexOption.MULTILINE)
            .findAll(text)
            .map { match ->
                Row(
                    shanten = match.groupValues[1].toInt(),
                    advanceNum = match.groupValues[2].toInt(),
                    ept = match.groupValues[3].toIntOrNull() ?: -1,
                )
            }
            .toList()

    private fun assertSorted(text: String) {
        val rows = parseRows(text)
        assertTrue("没有解析到评估行：\n$text", rows.isNotEmpty())
        for (i in 1 until rows.size) {
            val prev = rows[i - 1]
            val cur = rows[i]
            val ordered = when {
                prev.shanten != cur.shanten -> prev.shanten < cur.shanten
                prev.advanceNum != cur.advanceNum -> prev.advanceNum > cur.advanceNum
                else -> prev.ept >= cur.ept
            }
            assertTrue("第 $i 行未按 向听→进张数→EPT 排序：$prev vs $cur\n$text", ordered)
        }
    }

    @Test
    fun tenpaiHandProducesFullTableWithEpt() {
        // 已完成的和牌形（14 张）：任意切牌后均听牌，每一行都应算出 EPT
        val text = MahjongCalculator.evaluateDiscards("123456789m111z22s", null)
        assertTrue(text, text.contains("【切牌评估】"))
        assertTrue(text, text.contains("进张数"))
        assertTrue(text, text.contains("EPT"))
        val rows = parseRows(text)
        // 14 张去重后为 1m-9m、1z、2s 共 11 种切法（相同的牌合并）
        assertEquals(11, rows.size)
        assertTrue(text, rows.all { it.shanten == 0 })
        // 门清自摸至少 1 番，EPT 必为正数
        assertTrue(text, rows.all { it.ept > 0 })
        assertSorted(text)
    }

    @Test
    fun mixedShantenHandIsSorted() {
        // 官方 README 的 14 张示例：部分切法退向，向听数不完全相同
        val text = MahjongCalculator.evaluateDiscards("112233p44556s127z", null)
        assertTrue(text, text.contains("【切牌评估】"))
        assertSorted(text)
    }

    @Test
    fun nonTenpaiHandShowsDashInsteadOfEpt() {
        // 全为孤立的幺九牌：任何切法都不可能听牌，EPT 列应全部为 "-"
        val text = MahjongCalculator.evaluateDiscards("13579m13579p13s11z", null)
        val rows = parseRows(text)
        // 去重后 1m3m5m7m9m 1p3p5p7p9p 1s3s 1z 共 13 种切法
        assertEquals(13, rows.size)
        assertTrue(text, rows.all { it.ept == -1 })
        assertSorted(text)
    }

    @Test
    fun handWithFuroEvaluates() {
        // 门前 11 张 + 1 组副露 = 合计 14 张
        val text = MahjongCalculator.evaluateDiscards("12233466m111z", "789p")
        assertTrue(text, text.contains("【切牌评估】"))
        // 去重后 1m/2m/3m/4m/6m/1z 共 6 种切法
        assertEquals(6, parseRows(text).size)
        assertSorted(text)
    }

    @Test
    fun nullFuroEqualsEmptyFuro() {
        val hand = "123456789m111z22s"
        assertEquals(
            MahjongCalculator.evaluateDiscards(hand, ""),
            MahjongCalculator.evaluateDiscards(hand, null),
        )
    }

    @Test
    fun thirteenTilesFailsGracefully() {
        // 13 张未摸牌：没有牌可切，应得到带说明的失败而不是崩溃
        val result = MahjongCalculator.runSafe {
            MahjongCalculator.evaluateDiscards("345678m23456p22s", "")
        }
        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message, message.contains("14"))
    }

    @Test
    fun fifteenTilesFailsGracefully() {
        val result = MahjongCalculator.runSafe {
            MahjongCalculator.evaluateDiscards("123456789m111z22s3p", "")
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun emptyHandFailsGracefully() {
        val result = MahjongCalculator.runSafe {
            MahjongCalculator.evaluateDiscards("", null)
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun invalidTileTextFailsGracefully() {
        val result = MahjongCalculator.runSafe {
            MahjongCalculator.evaluateDiscards("xxxx", null)
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun detailedResultMatchesFormattedTable() {
        // 结构化结果与文本表格一一对应，且第一行就是评分最高（AI 会选）的那张牌
        val hand = "112233p44556s127z"
        val detailed = MahjongCalculator.evaluateDiscardsDetailed(hand, null)
        val text = MahjongCalculator.evaluateDiscards(hand, null)
        assertEquals(parseRows(text).size, detailed.size)

        val firstRowTile = Regex("""^\s+1\s+(\S+)\s""", RegexOption.MULTILINE)
            .find(text)!!
            .groupValues[1]
        assertEquals(detailed.first().discard.toString(), firstRowTile)

        // 排序不变量：向听 → 进张数 → EPT
        detailed.zipWithNext().forEach { (prev, cur) ->
            val ordered = when {
                prev.shantenNum != cur.shantenNum -> prev.shantenNum < cur.shantenNum
                prev.advanceNum != cur.advanceNum -> prev.advanceNum > cur.advanceNum
                else -> (prev.ept ?: 0) >= (cur.ept ?: 0)
            }
            assertTrue("$prev 应排在 $cur 之前", ordered)
        }
    }

    @Test
    fun detailedResultKeepsRedFiveIdentity() {
        // 红 5 与普通 5 是两种不同的切法，切牌评估不应把它们合并
        val detailed = MahjongCalculator.evaluateDiscardsDetailed("123m056m789m111z22p", null)
        val discards = detailed.map { it.discard.toString() }
        assertTrue("应同时包含 0m 与 5m：$discards", "0m" in discards && "5m" in discards)
    }

    @Test
    fun detailedResultRejectsThirteenTiles() {
        assertThrows(IllegalArgumentException::class.java) {
            MahjongCalculator.evaluateDiscardsDetailed("345678m23456p22s", null)
        }
    }
}
