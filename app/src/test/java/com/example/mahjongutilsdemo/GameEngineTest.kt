package com.example.mahjongutilsdemo

import mahjongutils.models.Tile
import mahjongutils.models.TileType
import mahjongutils.models.toTilesString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * GameEngine（单机对局循环）的单元测试：
 * 覆盖牌山构成、发牌 / 摸牌 / 切牌的数量变化、AI 决策、自摸判定、振听与流局。
 *
 * 通过给 [GameEngine.startRound] 传入固定牌山（发牌顺序：座位 s 在牌山中的下标 = 轮次 * 4 + 座位号），
 * 可以精确构造任意牌局，测试结果完全确定。
 */
class GameEngineTest {

    private fun engine(seed: Int = 42) = GameEngine(Random(seed))

    private fun tiles(text: String) = MahjongCalculator.parseTiles(text)

    /**
     * 构造一副“定式牌山”：前 [HAND_SIZE] 轮轮流发牌，之后按 [draws] 的顺序依次摸牌。
     */
    private fun riggedWall(
        player: String,
        ai1: String,
        ai2: String,
        ai3: String,
        draws: List<String> = emptyList(),
    ): List<Tile> {
        val hands = listOf(player, ai1, ai2, ai3).map { tiles(it) }
        hands.forEach { require(it.size == HAND_SIZE) { "发牌必须每家 $HAND_SIZE 张，实际 ${it.size}" } }
        return buildList {
            repeat(HAND_SIZE) { round ->
                for (seat in 0 until PLAYER_NUM) {
                    add(hands[seat][round])
                }
            }
            draws.forEach { addAll(tiles(it)) }
        }
    }

    private fun standardHands(draws: List<String> = emptyList()) = riggedWall(
        player = "123m456m789m11p23s",
        ai1 = "1p2p3p4p5p6p7p8p9p1s9s1z2z",
        ai2 = "1z2z3z4z5z6z7z1s9s1m9m1p2p",
        ai3 = "2m3m4m5m6m7m8m3s4s5s6s7s8s",
        draws = draws,
    )

    // ------------------------------------------------------------------ 牌山

    @Test
    fun wallHas136TilesWithRedDora() {
        val wall = GameEngine.buildWall(Random(1))
        assertEquals(WALL_SIZE, wall.size)

        val counts = wall.groupingBy { it }.eachCount()
        for (suit in listOf(TileType.M, TileType.P, TileType.S)) {
            for (num in 1..9) {
                if (num == 5) {
                    // 每种花色的 5：3 张普通 + 1 张红 5（替换原有的一张）
                    assertEquals(3, counts[Tile.get(suit, 5)] ?: 0)
                    assertEquals(1, counts[Tile.get(suit, 0)] ?: 0)
                } else {
                    assertEquals(4, counts[Tile.get(suit, num)] ?: 0)
                }
            }
        }
        for (num in 1..7) {
            assertEquals(4, counts[Tile.get(TileType.Z, num)] ?: 0)
        }
        assertEquals(3, wall.count { it.num == 0 })
    }

    @Test
    fun buildWallIsShuffledButReproducible() {
        val a = GameEngine.buildWall(Random(1))
        val b = GameEngine.buildWall(Random(1))
        val c = GameEngine.buildWall(Random(2))
        assertEquals(a, b) // 同种子可复现
        assertTrue(a != c) // 不同种子洗牌结果不同
    }

    // ------------------------------------------------------ 发牌 / 摸 / 切

    @Test
    fun startRoundDeals13TilesToEachSeat() {
        val state = engine().startRound()

        assertEquals(GamePhase.AwaitDraw, state.phase)
        assertEquals(PLAYER_NUM, state.hands.size)
        state.hands.forEach { assertEquals(HAND_SIZE, it.size) }
        assertEquals(PLAYER_NUM, state.rivers.size)
        state.rivers.forEach { assertTrue(it.isEmpty()) }
        // 136 - 4×13 = 84
        assertEquals(WALL_SIZE - DEAL_SIZE, state.wallRemaining)
        assertNull(state.drawnTile)
        assertNull(state.result)
        assertFalse(state.furiten)
        assertFalse(state.riichiPossible)
    }

    @Test
    fun drawAddsOneTileAndDiscardReturnsToThirteen() {
        val engine = engine()
        engine.startRound()
        val wallAfterDeal = engine.state.wallRemaining

        // 摸牌：手牌仍记 13 张，摸到的牌单独放在 drawnTile，牌山 -1
        val drawnState = engine.playerDraw()
        assertEquals(GamePhase.PlayerDiscard, drawnState.phase)
        drawnState.hands.forEach { assertEquals(HAND_SIZE, it.size) }
        assertNotNull(drawnState.drawnTile)
        assertEquals(wallAfterDeal - 1, drawnState.wallRemaining)

        // 切牌：手牌回到 13 张，牌河 +1，牌山不变
        val discard = drawnState.hands[0].first()
        val discardedState = engine.playerDiscard(discard)
        assertEquals(GamePhase.AiThinking, discardedState.phase)
        discardedState.hands.forEach { assertEquals(HAND_SIZE, it.size) }
        assertNull(discardedState.drawnTile)
        assertEquals(1, discardedState.rivers[0].size)
        assertEquals(discard, discardedState.rivers[0].single())
        assertEquals(wallAfterDeal - 1, discardedState.wallRemaining)
        assertEquals(1, discardedState.currentSeat)
    }

    @Test
    fun drawAndDiscardAreGuardedByPhase() {
        val engine = engine()
        // 未开局不能摸牌
        assertThrows(IllegalArgumentException::class.java) { engine.playerDraw() }

        engine.startRound()
        // 未摸牌不能切牌
        assertThrows(IllegalArgumentException::class.java) { engine.playerDiscard(Tile.get("1m")) }

        engine.playerDraw()
        // 不能切手里没有的牌
        val hand = engine.state.hands[0] + engine.state.drawnTile!!
        val missing = Tile.all.first { it !in hand }
        assertThrows(IllegalArgumentException::class.java) { engine.playerDiscard(missing) }
        // 玩家回合 AI 不能行动
        assertThrows(IllegalArgumentException::class.java) { engine.aiTurn() }
    }

    // ---------------------------------------------------------------- AI

    @Test
    fun aiTurnDrawsDiscardsBestTileAndAdvances() {
        val wall = riggedWall(
            player = "123m456m789m11p23s",
            ai1 = "1m3m5m7m9m1p3p5p7p9p1s3s1z",
            ai2 = "2m4m6m8m2p4p6p8p2s4s6s2z4z",
            ai3 = "1z2z3z4z5z6z7z1s5s9s3p7p9p",
            // 玩家摸 7z（不是听的 1s/4s，不自摸）；AI1 摸 9p
            draws = listOf("7z", "9p"),
        )
        val engine = engine()
        engine.startRound(wall)
        val wallAfterDeal = engine.state.wallRemaining

        engine.playerDraw()
        engine.playerDiscard(engine.state.hands[0].first())
        assertEquals(GamePhase.AiThinking, engine.state.phase)

        // AI 摸牌后应切出评分最高的一张（与 evaluateDiscardsDetailed 的第一名一致）
        val aiHand = engine.state.hands[1]
        val aiFull = (aiHand + engine.nextWallTile!!).sorted()
        val expected = MahjongCalculator
            .evaluateDiscardsDetailed(aiFull.toTilesString(), null)
            .first()
            .discard

        val state = engine.aiTurn()
        assertEquals(GamePhase.AiThinking, state.phase)
        assertEquals(2, state.currentSeat)
        assertEquals(HAND_SIZE, state.hands[1].size)
        assertEquals(1, state.rivers[1].size)
        assertEquals(expected, state.rivers[1].single())
        // 玩家的手牌与牌河不受影响；牌山 = 发牌后 - 玩家 1 次 - AI 1 次
        assertEquals(HAND_SIZE, state.hands[0].size)
        assertEquals(1, state.rivers[0].size)
        assertEquals(wallAfterDeal - 2, state.wallRemaining)
        assertNull(state.drawnTile)
    }

    // -------------------------------------------------------------- 自摸

    @Test
    fun playerTsumoEndsRoundWithHanHuPoint() {
        // 玩家听 1s/4s，摸到 4s → 123m456m789m11p234s 自摸（平和 + 门前清自摸）
        val wall = standardHands(draws = listOf("4s"))
        val engine = engine()
        engine.startRound(wall)

        val state = engine.playerDraw()
        assertEquals(GamePhase.RoundEnd, state.phase)

        val result = requireNotNull(state.result)
        assertEquals(RoundResult.Kind.PlayerTsumo, result.kind)
        assertEquals(PLAYER_SEAT, result.winnerSeat)
        assertEquals("自摸！", result.title)
        assertEquals(Tile.get("4s"), state.drawnTile)

        val hora = requireNotNull(result.hora)
        // 门清自摸 + 平和
        assertTrue("番数应 ≥ 2，实际 ${hora.han}", hora.han >= 2)
        assertTrue(hora.hu > 0)
        assertTrue(result.detail, result.detail.contains("番"))
        assertTrue(result.detail, result.detail.contains("符"))
        assertTrue(result.detail, result.detail.contains("点数"))
        assertTrue(result.detail, result.detail.contains("平和"))
    }

    @Test
    fun nonWinningDrawKeepsRoundRunning() {
        val wall = standardHands(draws = listOf("7z")) // 不是听牌的进张
        val engine = engine()
        engine.startRound(wall)

        val state = engine.playerDraw()
        assertEquals(GamePhase.PlayerDiscard, state.phase)
        assertNull(state.result)
        assertEquals(Tile.get("7z"), state.drawnTile)
    }

    // ------------------------------------------------------- 振听 / 听牌

    @Test
    fun waitsAndFuritenAreComputedFromHandAndRiver() {
        // 2s4s 嵌张听 3s
        val tenpai = tiles("123m456m789m11p24s")
        assertEquals(setOf(Tile.get("3s")), GameEngine.waits(tenpai))
        assertTrue(GameEngine.isFuriten(tenpai, tiles("3s")))
        assertFalse(GameEngine.isFuriten(tenpai, tiles("9m")))

        // 河里的红 5 与听牌进张 5s 视为同一张牌
        val waits5 = tiles("123m456m789m11p46s")
        assertEquals(setOf(Tile.get("5s")), GameEngine.waits(waits5))
        assertTrue(GameEngine.isFuriten(waits5, tiles("0s")))

        // 未听牌不会振听
        assertFalse(GameEngine.isFuriten(tiles("123m456m789m11p5s9p"), tiles("3s")))
    }

    @Test
    fun furitenIsUpdatedAfterDiscardAndBlocksTsumo() {
        // 玩家 3 轮后摸成 “2s4s 听 3s”，而 3s 已在自己河里 → 振听，之后摸到 3s 不能自摸
        val wall = riggedWall(
            player = "123m456m789m11p3s5s", // 听 4s
            ai1 = "1z2z3z4z5z6z7z1p1p2p3p4p5p",
            ai2 = "6p7p8p9p6s7s8s9s1s2s3s4s5s",
            ai3 = "2p3p4p5p2m3m4m5m6m7m8m9m1z",
            draws = listOf(
                "9p",              // 玩家第 1 摸：切 3s
                "1m", "2m", "3m",  // AI ×3
                "2s",              // 玩家第 2 摸：切 5s
                "4m", "5m", "6m",  // AI ×3
                "4s",              // 玩家第 3 摸：切 9p → 听 3s，河里已有 3s → 振听
                "7m", "8m", "9m",  // AI ×3
                "3s",              // 玩家第 4 摸：3s 和牌形，但振听不能自摸
            ),
        )
        val engine = engine()
        engine.startRound(wall)

        engine.playerDraw()
        engine.playerDiscard(tiles("3s").single())
        assertFalse(engine.state.furiten)
        assertFalse(engine.state.riichiPossible)
        repeat(3) { engine.aiTurn() }

        engine.playerDraw()
        engine.playerDiscard(tiles("5s").single())
        assertFalse(engine.state.furiten)
        repeat(3) { engine.aiTurn() }

        engine.playerDraw()
        engine.playerDiscard(tiles("9p").single())
        // 123m456m789m11p2s4s 听 3s，3s 已在自己河里 → 振听（可立直提示）
        assertTrue(engine.state.furiten)
        assertTrue(engine.state.riichiPossible)
        assertEquals(listOf("3s", "5s", "9p"), engine.state.rivers[0].map { it.toString() })
        repeat(3) { engine.aiTurn() }
        assertEquals(GamePhase.AwaitDraw, engine.state.phase)

        // 振听中摸到和牌形也不能自摸
        val state = engine.playerDraw()
        assertEquals(GamePhase.PlayerDiscard, state.phase)
        assertNull(state.result)
        assertEquals(Tile.get("3s"), state.drawnTile)
        assertTrue(state.furiten)
    }

    // -------------------------------------------------------------- 流局

    @Test
    fun emptyWallOnDrawEndsRoundAsExhaustiveDraw() {
        val engine = engine()
        engine.startRound(standardHands()) // 只发牌，不余牌
        assertEquals(0, engine.state.wallRemaining)
        assertEquals(GamePhase.AwaitDraw, engine.state.phase)

        val state = engine.playerDraw()
        assertEquals(GamePhase.RoundEnd, state.phase)
        val result = requireNotNull(state.result)
        assertEquals(RoundResult.Kind.ExhaustiveDraw, result.kind)
        assertNull(result.winnerSeat)
        assertNull(result.hora)
        assertEquals("流局", result.title)
    }

    @Test
    fun aiCannotDrawFromEmptyWall() {
        val wall = standardHands(draws = listOf("7z"))
        val engine = engine()
        engine.startRound(wall)
        engine.playerDraw()
        engine.playerDiscard(engine.state.hands[0].first())

        val state = engine.aiTurn() // 牌山摸完 → 流局
        assertEquals(GamePhase.RoundEnd, state.phase)
        assertEquals(RoundResult.Kind.ExhaustiveDraw, state.result?.kind)
    }

    @Test
    fun startRoundRejectsTooShortWall() {
        val engine = engine()
        val tooShort = GameEngine.buildWall(Random(3)).take(DEAL_SIZE - 1)
        assertThrows(IllegalArgumentException::class.java) { engine.startRound(tooShort) }
    }

    // ---------------------------------------------------------- 整局不变式

    @Test
    fun fullGameKeepsInvariantsUntilRoundEnds() {
        val engine = engine(20261009)
        engine.startRound()

        var steps = 0
        while (engine.state.phase != GamePhase.RoundEnd && steps < 1000) {
            steps++
            val wallBefore = engine.state.wallRemaining
            val discardsBefore = engine.state.rivers.sumOf { it.size }

            when (engine.state.phase) {
                GamePhase.AwaitDraw, GamePhase.AiThinking -> {
                    // 每次行动消耗 1 张牌山；牌山摸完则直接流局（不变）
                    if (engine.state.phase == GamePhase.AwaitDraw) engine.playerDraw() else engine.aiTurn()
                    val expected = if (wallBefore == 0) 0 else wallBefore - 1
                    assertEquals(expected, engine.state.wallRemaining)
                }

                GamePhase.PlayerDiscard -> {
                    engine.playerDiscard(engine.state.hands[0].first())
                    // 切牌不消耗牌山，牌河 +1
                    assertEquals(wallBefore, engine.state.wallRemaining)
                    assertEquals(discardsBefore + 1, engine.state.rivers.sumOf { it.size })
                }

                else -> break
            }

            assertTrue("牌山不能为负", engine.state.wallRemaining >= 0)
            engine.state.hands.forEach { assertEquals(HAND_SIZE, it.size) }
        }

        assertEquals("牌山有限，对局必然结束", GamePhase.RoundEnd, engine.state.phase)
        val result = requireNotNull(engine.state.result)
        // 只有自摸或流局两种结局
        when (result.kind) {
            RoundResult.Kind.PlayerTsumo -> assertEquals(PLAYER_SEAT, result.winnerSeat)
            RoundResult.Kind.AiTsumo -> assertTrue(result.winnerSeat!! in 1 until PLAYER_NUM)
            RoundResult.Kind.ExhaustiveDraw -> assertNull(result.winnerSeat)
        }
        assertTrue(engine.state.rivers.sumOf { it.size } <= WALL_SIZE - DEAL_SIZE)
    }
}
