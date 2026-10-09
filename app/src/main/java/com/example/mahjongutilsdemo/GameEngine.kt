package com.example.mahjongutilsdemo

import mahjongutils.hora.Hora
import mahjongutils.hora.hora
import mahjongutils.models.Tile
import mahjongutils.models.TileType
import mahjongutils.models.Wind
import mahjongutils.models.toTilesString
import mahjongutils.shanten.ShantenWithoutGot
import mahjongutils.shanten.shanten
import kotlin.random.Random

/** 座位数（1 名玩家 + 3 名 AI） */
const val PLAYER_NUM = 4

/** 玩家座位号 */
const val PLAYER_SEAT = 0

/** 每家手牌张数 */
const val HAND_SIZE = 13

/** 牌山总张数（4 组 1-9m/p/s + 7 种字牌，各 4 张） */
const val WALL_SIZE = 136

/** 发牌总数 */
const val DEAL_SIZE = HAND_SIZE * PLAYER_NUM

/** 各座位的风：玩家坐东（庄），AI 依次为南 / 西 / 北，AI 暂按闲家处理 */
val SEAT_WINDS: List<Wind> = listOf(Wind.East, Wind.South, Wind.West, Wind.North)

/** 场风（原型固定东场） */
val ROUND_WIND: Wind = Wind.East

/** AI 思考延时（毫秒），由 UI 侧控制 */
const val AI_THINKING_MILLIS = 1000L

/** 单机对局的阶段 */
enum class GamePhase {
    /** 尚未开局 */
    Idle,

    /** 已发牌，等待玩家摸牌 */
    AwaitDraw,

    /** 玩家已摸牌（13 张 + 摸到的 1 张），等待切牌 */
    PlayerDiscard,

    /** AI 行动中（UI 延时后调用 [GameEngine.aiTurn]） */
    AiThinking,

    /** 本局结束（自摸或流局） */
    RoundEnd,
}

/** 本局结果 */
data class RoundResult(
    val kind: Kind,
    /** 和牌者座位；流局时为 null */
    val winnerSeat: Int?,
    /** 弹窗标题，如 “自摸！” */
    val title: String,
    /** 番 / 符 / 点数等详细信息 */
    val detail: String,
    /** 和牌时的完整计算结果；流局时为 null */
    val hora: Hora?,
) {
    enum class Kind {
        /** 玩家自摸 */
        PlayerTsumo,

        /** AI 自摸 */
        AiTsumo,

        /** 牌山摸完，流局 */
        ExhaustiveDraw,
    }
}

/**
 * 对局状态快照（不可变）。UI 持有最新的一份用于渲染。
 *
 * 手牌恒为 13 张，刚摸到的牌放在 [drawnTile]，这样向听 / 听牌 / 振听
 * 的计算都可以直接用 13 张的 [GameState.hands] 完成。
 */
data class GameState(
    val phase: GamePhase = GamePhase.Idle,
    /** 四家手牌，恒为 13 张 */
    val hands: List<List<Tile>> = List(PLAYER_NUM) { emptyList() },
    /** 当前行动者刚摸到的牌，仅在 [GamePhase.PlayerDiscard] 阶段有值 */
    val drawnTile: Tile? = null,
    /** 当前行动的座位 */
    val currentSeat: Int = PLAYER_SEAT,
    /** 四家牌河 */
    val rivers: List<List<Tile>> = List(PLAYER_NUM) { emptyList() },
    /** 牌山剩余张数 */
    val wallRemaining: Int = 0,
    /** 玩家是否振听（自己的牌河里有当前听牌的进张） */
    val furiten: Boolean = false,
    /** 玩家是否听牌（立直宣告暂未实现，仅作提示） */
    val riichiPossible: Boolean = false,
    /** 本局结果，未结束时为 null */
    val result: RoundResult? = null,
)

/**
 * 单机日麻游戏循环（纯 Kotlin，不依赖 Android / Compose，便于单元测试）。
 *
 * 状态机：
 * ```
 * startRound()  → AwaitDraw
 * playerDraw()  → PlayerDiscard（或 RoundEnd：自摸 / 流局）
 * playerDiscard() → AiThinking
 * aiTurn() ×3   → AwaitDraw（或 RoundEnd）
 * ```
 *
 * 简化规则（原型）：
 * - 无王牌区 / 宝牌指示牌，牌山 136 张全部可摸；红 5（0m/0p/0s）本身计 1 张宝牌
 * - 只判定自摸，不做荣和 / 副露 / 立直宣告 / 抢杠等
 * - 振听：自己牌河中含当前听牌的进张时，不能自摸
 * - AI 每次摸牌后用 [MahjongCalculator.evaluateDiscardsDetailed] 选评分最高的一张切出
 *
 * @param random 牌山洗牌用的随机源，传入固定种子即可复现牌局
 */
class GameEngine(
    private val random: Random = Random.Default,
) {

    /** 牌山（从队首摸牌） */
    private val wall = ArrayDeque<Tile>()

    /** 当前状态快照 */
    var state: GameState = GameState()
        private set

    /** 牌山顶端的牌（下一张摸的牌），仅供测试 / 调试 */
    val nextWallTile: Tile? get() = wall.firstOrNull()

    /**
     * 开局：生成（或使用给定的）牌山 → 每家发 13 张 → 等待玩家摸牌。
     * 可重复调用，直接开始新的一局。
     *
     * @param wallTiles 牌山摸牌顺序；至少要有 [DEAL_SIZE] 张。默认随机洗一副完整的 136 张牌。
     */
    fun startRound(wallTiles: List<Tile>? = null): GameState {
        val tiles = wallTiles ?: buildWall(random)
        require(tiles.size >= DEAL_SIZE) { "牌山至少需要 $DEAL_SIZE 张才能发牌，实际 ${tiles.size} 张" }
        require(tiles.groupingBy { it.code }.eachCount().values.all { it <= 4 }) {
            "牌山中同一张牌超过 4 张"
        }

        wall.clear()
        wall.addAll(tiles)

        state = GameState(phase = GamePhase.AwaitDraw)
        // 轮流发牌：每位玩家依次摸 1 张，共 13 轮
        repeat(HAND_SIZE) {
            for (seat in 0 until PLAYER_NUM) {
                state = state.copy(hands = state.hands.withSeat(seat, state.hands[seat] + wall.removeFirst()))
            }
        }
        state = state.copy(wallRemaining = wall.size)
        return state
    }

    /** 玩家摸牌：牌山 -1，手牌 13 张 + 摸到的 1 张 */
    fun playerDraw(): GameState {
        require(state.phase == GamePhase.AwaitDraw) { "当前阶段不能摸牌：${state.phase}" }
        if (wall.isEmpty()) {
            return finish(exhaustiveDraw())
        }

        val drawn = wall.removeFirst()
        state = state.copy(
            phase = GamePhase.PlayerDiscard,
            currentSeat = PLAYER_SEAT,
            drawnTile = drawn,
            wallRemaining = wall.size,
        )

        if (!state.furiten) {
            checkTsumo(PLAYER_SEAT, drawn)?.let { return finish(it, drawn) }
        }
        return state
    }

    /**
     * 玩家切牌：手牌回到 13 张，牌河 +1，并重新判定振听 / 听牌。
     *
     * @param tile 要切出的牌，必须在 “13 张手牌 + 摸到的牌” 中
     */
    fun playerDiscard(tile: Tile): GameState {
        require(state.phase == GamePhase.PlayerDiscard) { "当前阶段不能切牌：${state.phase}" }
        val drawn = state.drawnTile ?: error("未摸牌")
        val full = state.hands[PLAYER_SEAT] + drawn
        require(tile in full) { "手牌中没有这张牌：$tile" }

        val hand = full.toMutableList().apply { removeAt(indexOf(tile)) }
        val river = state.rivers[PLAYER_SEAT] + tile

        state = state.copy(
            phase = GamePhase.AiThinking,
            currentSeat = PLAYER_SEAT + 1,
            hands = state.hands.withSeat(PLAYER_SEAT, hand),
            rivers = state.rivers.withSeat(PLAYER_SEAT, river),
            drawnTile = null,
            furiten = isFuriten(hand, river),
            riichiPossible = waits(hand).isNotEmpty(),
        )
        return state
    }

    /**
     * 当前 AI 行动一轮：摸牌 →（自摸判定）→ 选牌切出 → 交给下一家。
     * 三名 AI 依次行动后自动回到玩家（[GamePhase.AwaitDraw]）。
     */
    fun aiTurn(): GameState {
        require(state.phase == GamePhase.AiThinking) { "当前阶段 AI 不能行动：${state.phase}" }
        val seat = state.currentSeat
        require(seat in 1 until PLAYER_NUM) { "不是 AI 的回合：$seat" }

        if (wall.isEmpty()) {
            return finish(exhaustiveDraw())
        }

        val drawn = wall.removeFirst()
        checkTsumo(seat, drawn)?.let { return finish(it, drawn) }

        val full = state.hands[seat] + drawn
        val discard = chooseAiDiscard(full)
        val hand = full.toMutableList().also {
            // 评分结果必然来自手牌；兜底切掉刚摸到的牌，保证手牌回到 13 张
            if (!it.remove(discard)) it.removeAt(it.lastIndex)
        }
        val river = state.rivers[seat] + discard

        val next = (seat + 1) % PLAYER_NUM
        state = state.copy(
            phase = if (next == PLAYER_SEAT) GamePhase.AwaitDraw else GamePhase.AiThinking,
            currentSeat = next,
            hands = state.hands.withSeat(seat, hand),
            rivers = state.rivers.withSeat(seat, river),
            drawnTile = null,
            wallRemaining = wall.size,
        )
        return state
    }

    /**
     * AI 决策：用切牌评分选最优的一张（向听 → 进张数 → EPT）。
     * 评分失败时兜底切掉刚摸到的牌。
     */
    private fun chooseAiDiscard(full: List<Tile>): Tile {
        val sorted: List<Tile> = full.sorted()
        val text = sorted.toTilesString()
        return runCatching {
            MahjongCalculator.evaluateDiscardsDetailed(text, null).first().discard
        }.getOrElse { full.last() }
    }

    /** 自摸判定：和牌成立且有役时返回结果，否则 null；振听过滤由 [playerDraw] 负责 */
    private fun checkTsumo(seat: Int, drawn: Tile): RoundResult? {
        val hand = state.hands[seat]
        // 无宝牌指示牌，红 5 本身计 1 张宝牌
        val dora = (hand + drawn).count { it.num == 0 }
        val result = runCatching {
            hora(
                tiles = hand,
                agari = drawn,
                tsumo = true,
                dora = dora,
                selfWind = SEAT_WINDS[seat],
                roundWind = ROUND_WIND,
            )
        }.getOrNull() ?: return null
        if (result.yaku.isEmpty()) return null

        val isPlayer = seat == PLAYER_SEAT
        return RoundResult(
            kind = if (isPlayer) RoundResult.Kind.PlayerTsumo else RoundResult.Kind.AiTsumo,
            winnerSeat = seat,
            title = if (isPlayer) "自摸！" else "AI 自摸",
            detail = formatHoraDetail(seat, result),
            hora = result,
        )
    }

    private fun formatHoraDetail(seat: Int, result: Hora): String = buildString {
        val yakuText = if (result.yaku.isEmpty()) {
            "无"
        } else {
            result.yaku.sortedBy { it.name }.joinToString("、") { MahjongCalculator.yakuLabel(it) }
        }
        appendLine("【役种】$yakuText")
        if (result.dora > 0) {
            appendLine("  宝牌 ${result.dora} 番（不计役）")
        }
        appendLine()
        if (result.hasYakuman) {
            appendLine("【番】役满")
        } else {
            appendLine("【番】${result.han} 番    【符】${result.hu} 符")
        }
        appendLine()
        appendLine("【点数】")
        if (seat == PLAYER_SEAT) {
            appendLine("  庄家自摸：每家 ${result.parentPoint.tsumo}，合计 ${result.parentPoint.tsumoTotal}")
        } else {
            appendLine(
                "  闲家自摸：庄 ${result.childPoint.tsumoParent} + 闲 ${result.childPoint.tsumoChild} ×2，" +
                    "合计 ${result.childPoint.tsumoTotal}"
            )
        }
    }.trimEnd()

    private fun exhaustiveDraw() = RoundResult(
        kind = RoundResult.Kind.ExhaustiveDraw,
        winnerSeat = null,
        title = "流局",
        detail = "牌山已摸完，本局流局。",
        hora = null,
    )

    private fun finish(result: RoundResult, drawnTile: Tile? = null): GameState {
        state = state.copy(
            phase = GamePhase.RoundEnd,
            drawnTile = drawnTile,
            result = result,
            wallRemaining = wall.size,
        )
        return state
    }

    private fun List<List<Tile>>.withSeat(seat: Int, tiles: List<Tile>): List<List<Tile>> =
        toMutableList().also { it[seat] = tiles }

    companion object {
        /**
         * 生成一副完整的 136 张牌山：
         * 4 组 1-9m/p/s（每种 4 张，其中每种花色的 5 有一张换成红 5）+ 7 种字牌各 4 张。
         */
        fun buildWall(random: Random = Random.Default): List<Tile> {
            val tiles = ArrayList<Tile>(WALL_SIZE)
            for (suit in listOf(TileType.M, TileType.P, TileType.S)) {
                for (num in 1..9) {
                    if (num == 5) {
                        repeat(3) { tiles += Tile.get(suit, 5) }
                        tiles += Tile.get(suit, 0) // 红 5 替换其中一张
                    } else {
                        repeat(4) { tiles += Tile.get(suit, num) }
                    }
                }
            }
            for (num in 1..7) {
                repeat(4) { tiles += Tile.get(TileType.Z, num) }
            }
            check(tiles.size == WALL_SIZE) { "牌山张数错误：${tiles.size}" }
            tiles.shuffle(random)
            return tiles
        }

        /** 牌的等价归一化：红 5（0x）视为普通 5，便于与听牌进张比对 */
        fun normalize(tile: Tile): Tile =
            if (tile.type != TileType.Z && tile.num == 0) Tile.get(tile.type, 5) else tile

        /**
         * 手牌的听牌进张；未听牌（或手牌不是 13 张）时返回空集合。
         */
        fun waits(hand: List<Tile>): Set<Tile> {
            if (hand.size != HAND_SIZE) return emptySet()
            val info = runCatching { shanten(hand) }.getOrNull()?.shantenInfo ?: return emptySet()
            return if (info is ShantenWithoutGot && info.shantenNum == 0) info.advance else emptySet()
        }

        /** 振听判定：自己牌河中是否含当前听牌的进张 */
        fun isFuriten(hand: List<Tile>, river: List<Tile>): Boolean {
            val waits = waits(hand)
            if (waits.isEmpty()) return false
            val seen = river.map(::normalize).toSet()
            return waits.any { normalize(it) in seen }
        }
    }
}
