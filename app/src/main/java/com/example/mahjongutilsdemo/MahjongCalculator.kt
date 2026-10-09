package com.example.mahjongutilsdemo

import mahjongutils.hanhu.getChildPointByHanHu
import mahjongutils.hanhu.getParentPointByHanHu
import mahjongutils.hora.Hora
import mahjongutils.hora.hora
import mahjongutils.models.Furo
import mahjongutils.models.Tile
import mahjongutils.models.Wind
import mahjongutils.shanten.ShantenWithGot
import mahjongutils.shanten.ShantenWithoutGot
import mahjongutils.shanten.UnionShantenResult
import mahjongutils.shanten.shanten
import mahjongutils.yaku.Yaku
import mahjongutils.yaku.Yakus

/**
 * 对 mahjong-utils 0.7.7 的薄封装：
 * 所有计算都在本地完成，无任何网络依赖。
 * 返回格式化好的中文文本，方便直接显示在 UI 上。
 */
object MahjongCalculator {

    /** 役种英文 key -> 中文名（Yaku.name 是库内部的英文标识） */
    private val yakuNameZh = mapOf(
        "Tsumo" to "自摸",
        "Pinhu" to "平和",
        "Tanyao" to "断幺",
        "Ipe" to "一杯口",
        "SelfWind" to "自风",
        "RoundWind" to "场风",
        "Haku" to "白",
        "Hatsu" to "发",
        "Chun" to "中",
        "Sanshoku" to "三色同顺",
        "Ittsu" to "一气通贯",
        "Chanta" to "混全带幺九",
        "Chitoi" to "七对子",
        "Toitoi" to "对对和",
        "Sananko" to "三暗刻",
        "Honroto" to "混老头",
        "Sandoko" to "三色同刻",
        "Sankantsu" to "三杠子",
        "Shosangen" to "小三元",
        "Honitsu" to "混一色",
        "Junchan" to "纯全带幺九",
        "Ryanpe" to "两杯口",
        "Chinitsu" to "清一色",
        "Kokushi" to "国士无双",
        "Suanko" to "四暗刻",
        "Daisangen" to "大三元",
        "Tsuiso" to "字一色",
        "Shousushi" to "小四喜",
        "Lyuiso" to "绿一色",
        "Chinroto" to "清老头",
        "Sukantsu" to "四杠子",
        "Churen" to "九莲宝灯",
        "Daisushi" to "大四喜",
        "ChurenNineWaiting" to "纯正九莲宝灯",
        "SuankoTanki" to "四暗刻单骑",
        "KokushiThirteenWaiting" to "国士无双十三面",
        "Richi" to "立直",
        "Ippatsu" to "一发",
        "Rinshan" to "岭上开花",
        "Chankan" to "抢杠和",
        "Haitei" to "海底摸月",
        "Houtei" to "河底捞鱼",
        "WRichi" to "两立直",
        "Tenhou" to "天和",
        "Chihou" to "地和",
    )

    private fun yakuLabel(yaku: Yaku): String =
        yakuNameZh[yaku.name] ?: yaku.name

    /** 解析手牌文本，如 "34568m235p68s"；0m/0p/0s 表示红宝牌 */
    fun parseTiles(text: String): List<Tile> = Tile.parseTiles(text.trim())

    /** 解析副露文本，多副露用空格分隔，如 "789p 111z"；暗杠写法 "0110m" */
    fun parseFuro(text: String): List<Furo> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        return trimmed.split(Regex("\\s+")).map { Furo(it) }
    }

    /**
     * 向听分析。
     * 13 张（未摸牌）→ 返回向听数 + 进张；
     * 14 张（已摸牌）→ 返回向听数 + 每种切牌后的进张（推荐切牌）。
     */
    fun analyzeShanten(handText: String, furoText: String = ""): String {
        val tiles = parseTiles(handText)
        val furo = parseFuro(furoText)
        val result: UnionShantenResult = shanten(tiles, furo)
        return formatShanten(result)
    }

    private fun formatShanten(result: UnionShantenResult): String = buildString {
        val furoText = if (result.hand.furo.isEmpty()) "无" else result.hand.furo.joinToString(" ")
        appendLine("手牌: ${result.hand.tiles.joinToString(" ")}")
        appendLine("副露: $furoText")
        when (val info = result.shantenInfo) {
            is ShantenWithoutGot -> {
                appendLine()
                appendLine("【向听数】${info.shantenNum}")
                if (info.shantenNum < 0) {
                    appendLine("已和牌")
                    return@buildString
                }
                appendLine("【进张】${info.advance.joinToString(" ")}（${info.advanceNum} 种）")
                info.goodShapeAdvance?.takeIf { it.isNotEmpty() }?.let { good ->
                    appendLine("【好型进张】${good.joinToString(" ")}（${info.goodShapeAdvanceNum} 种）")
                }
            }

            is ShantenWithGot -> {
                appendLine()
                appendLine("【向听数】${info.shantenNum}（已摸牌，需切一张）")
                appendLine("【切牌推荐】")
                val sorted = info.discardToAdvance.entries
                    .sortedWith(compareBy({ it.value.shantenNum }, { -it.value.advanceNum }))
                for ((discard, after) in sorted) {
                    val star = if (after.shantenNum == info.shantenNum) " ★" else ""
                    appendLine(
                        "  切 $discard → ${after.shantenNum} 向听，" +
                            "进张 ${after.advance.joinToString(" ")}（${after.advanceNum} 种）$star"
                    )
                }
            }
        }
    }.trimEnd()

    /**
     * 和牌分析（役种 / 番 / 符 / 点数）。
     * @param extraYakuNames 额外役的 Yaku.name（如 "Richi"、"Ippatsu"），与手牌无关
     */
    fun analyzeHora(
        handText: String,
        furoText: String,
        agariText: String,
        tsumo: Boolean,
        dora: Int,
        selfWind: Wind,
        roundWind: Wind,
        extraYakuNames: Set<String> = emptySet(),
    ): String {
        val tiles = parseTiles(handText)
        val furo = parseFuro(furoText)
        val agari = Tile.get(agariText.trim())
        val extraYaku = extraYakuNames.mapNotNull { name ->
            runCatching { Yakus.getYaku(name) }.getOrNull()
        }.toSet()

        val result: Hora = hora(
            tiles = tiles,
            furo = furo,
            agari = agari,
            tsumo = tsumo,
            dora = dora,
            selfWind = selfWind,
            roundWind = roundWind,
            extraYaku = extraYaku,
        )
        return formatHora(result)
    }

    private fun formatHora(result: Hora): String = buildString {
        appendLine("和牌张: ${result.agari}（${if (result.tsumo) "自摸" else "荣和"}）")
        appendLine("自风: ${windLabel(result.selfWind)}  场风: ${windLabel(result.roundWind)}")
        appendLine()
        if (result.yaku.isEmpty() && result.dora == 0) {
            appendLine("【役种】无（无役，不能和牌）")
        } else {
            appendLine("【役种】")
            for (yaku in result.yaku.sortedBy { it.name }) {
                val hanText = if (yaku.isYakuman) "役满" else "${yaku.han} 番"
                appendLine("  ${yakuLabel(yaku)}（${yaku.name}） $hanText")
            }
            if (result.dora > 0) {
                appendLine("  宝牌 ${result.dora} 番（不计役）")
            }
        }
        appendLine()
        if (result.hasYakuman) {
            appendLine("【番】役满")
        } else {
            appendLine("【番】${result.han} 番    【符】${result.hu} 符")
        }
        appendLine()
        appendLine("【点数】")
        appendLine("  庄家: 荣和 ${result.parentPoint.ron} / 自摸 ${result.parentPoint.tsumo}（每家）")
        appendLine(
            "  闲家: 荣和 ${result.childPoint.ron} / " +
                "自摸 ${result.childPoint.tsumoParent}（庄）+ ${result.childPoint.tsumoChild}（闲）"
        )
    }.trimEnd()

    private fun windLabel(wind: Wind?): String = when (wind) {
        Wind.East -> "东"
        Wind.South -> "南"
        Wind.West -> "西"
        Wind.North -> "北"
        null -> "未指定"
    }

    /** 纯番符点数计算（不看手牌，直接由番数/符数查表） */
    fun calculatePoints(han: Int, fu: Int): String {
        require(han >= 0) { "番数不能为负" }
        require(fu >= 0) { "符数不能为负" }
        val parent = getParentPointByHanHu(han, fu)
        val child = getChildPointByHanHu(han, fu)
        return buildString {
            appendLine("$han 番 $fu 符")
            appendLine()
            appendLine("【庄家】")
            appendLine("  荣和: ${parent.ron}")
            appendLine("  自摸: ${parent.tsumo} ×3 家 = ${parent.tsumoTotal}")
            appendLine()
            appendLine("【闲家】")
            appendLine("  荣和: ${child.ron}")
            appendLine(
                "  自摸: 庄 ${child.tsumoParent} + 闲 ${child.tsumoChild} ×2 = ${child.tsumoTotal}"
            )
        }.trimEnd()
    }

    /**
     * 统一的“安全执行”入口：捕获非法输入等异常，返回 Result 供 UI 展示。
     * 库的校验异常（ValidationException）继承自 IllegalArgumentException，
     * 且自带可读的 message（如 "tiles num illegal" 等），直接透出即可。
     */
    fun runSafe(action: () -> String): Result<String> = runCatching { action() }
}
