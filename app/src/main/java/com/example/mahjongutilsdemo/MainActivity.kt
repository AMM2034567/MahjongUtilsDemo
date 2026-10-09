package com.example.mahjongutilsdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mahjongutilsdemo.ui.theme.MahjongUtilsDemoTheme
import kotlinx.coroutines.delay
import mahjongutils.models.Tile
import mahjongutils.models.Wind

/**
 * 单屏 Demo：上半部分是单机对局（[GameEngine] 驱动的摸 / 切循环），
 * 下半部分是计算工具（输入手牌 → 向听 / 和牌 / 点数）。
 * 所有计算走 [MahjongCalculator]（mahjong-utils，纯本地）。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MahjongUtilsDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MahjongDemoScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/** 对局座位标签：玩家坐东（庄），AI 依次为南 / 西 / 北 */
private val seatLabels = listOf("我（东）", "AI（南）", "AI（西）", "AI（北）")

private fun gameStatusText(state: GameState): String = when (state.phase) {
    GamePhase.Idle -> "未开局"
    GamePhase.AwaitDraw -> "轮到你摸牌 · 牌山剩 ${state.wallRemaining} 张"
    GamePhase.PlayerDiscard ->
        "摸到 ${state.drawnTile}，点一张手牌切出 · 牌山剩 ${state.wallRemaining} 张"
    GamePhase.AiThinking ->
        "${seatLabels[state.currentSeat]}思考中… · 牌山剩 ${state.wallRemaining} 张"
    GamePhase.RoundEnd -> "${state.result?.title ?: "本局结束"} · 牌山剩 ${state.wallRemaining} 张"
}

/**
 * 单机对局区：开始按钮 + 摸牌 / 切牌 + 手牌与牌河展示。
 * 规则与状态机全部在 [GameEngine]，这里只做渲染。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameSection(
    state: GameState?,
    onStart: () -> Unit,
    onDraw: () -> Unit,
    onDiscard: (Tile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val startLabel = when (state?.phase) {
        null, GamePhase.Idle -> "开始对局"
        GamePhase.RoundEnd -> "再来一局"
        else -> "重新开局"
    }
    val canDiscard = state?.phase == GamePhase.PlayerDiscard

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("单机对局（原型）", style = MaterialTheme.typography.titleSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onStart, modifier = Modifier.weight(1f)) { Text(startLabel) }
            if (state?.phase == GamePhase.AwaitDraw) {
                Button(onClick = onDraw) { Text("摸牌") }
            }
        }

        if (state == null) {
            Text(
                "你坐东（庄），另外三家为 AI。发牌后摸牌 → 切牌 → AI 依次行动，先和牌者胜。",
                style = MaterialTheme.typography.bodySmall,
            )
            return@Column
        }

        Text(gameStatusText(state), style = MaterialTheme.typography.bodyMedium)

        val flags = buildList {
            if (state.furiten) add("振听（自己牌河里有听的牌，不能和）")
            if (state.riichiPossible) add("听牌 · 可立直（立直流程未实现）")
        }
        if (flags.isNotEmpty()) {
            Text(
                flags.joinToString("　"),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.furiten) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
        }

        // ---- 我的手牌（13 张 + 摸到的牌） ----
        Text("我的手牌（${state.hands[PLAYER_SEAT].size} 张）", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            state.hands[PLAYER_SEAT].sorted().forEach { tile ->
                TileChip(tile = tile, highlighted = false, enabled = canDiscard) { onDiscard(tile) }
            }
            state.drawnTile?.let { drawn ->
                TileChip(tile = drawn, highlighted = true, enabled = canDiscard) { onDiscard(drawn) }
            }
        }

        // ---- 牌河 ----
        Text("牌河", style = MaterialTheme.typography.labelLarge)
        seatLabels.forEachIndexed { seat, label ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "$label（${state.hands[seat].size} 张）",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(96.dp),
                )
                if (state.rivers[seat].isEmpty()) {
                    Text("—", style = MaterialTheme.typography.bodySmall)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.rivers[seat].forEach { tile ->
                            TileChip(tile = tile, highlighted = false, enabled = false) {}
                        }
                    }
                }
            }
        }
    }
}

/** 一张牌的方块展示，如 [1m]；红 5（0m/0p/0s）用红色 */
@Composable
private fun TileChip(
    tile: Tile,
    highlighted: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val red = tile.num == 0
    val background = when {
        red -> Color(0x2AFF1744)
        highlighted -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        red -> Color(0xFFE53935)
        highlighted -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(
                width = 1.dp,
                color = if (highlighted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "[$tile]",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

/** 额外役候选（与手牌无关的役，通过 hora 的 extraYaku 传入） */
private data class ExtraYakuOption(val key: String, val label: String)

private val extraYakuOptions = listOf(
    ExtraYakuOption("Richi", "立直"),
    ExtraYakuOption("WRichi", "两立直"),
    ExtraYakuOption("Ippatsu", "一发"),
    ExtraYakuOption("Rinshan", "岭上"),
    ExtraYakuOption("Chankan", "抢杠"),
    ExtraYakuOption("Haitei", "海底"),
    ExtraYakuOption("Houtei", "河底"),
)

private val windOptions = listOf(
    Wind.East to "东",
    Wind.South to "南",
    Wind.West to "西",
    Wind.North to "北",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MahjongDemoScreen(modifier: Modifier = Modifier) {
    // ---- 对局状态（算法在 [GameEngine]，这里只负责渲染与转发事件） ----
    val gameEngine = remember { GameEngine() }
    var gameState by remember { mutableStateOf<GameState?>(null) }
    var showGameResult by remember { mutableStateOf(false) }

    // AI 思考：停留 1 秒模拟思考，再让引擎推进一步
    LaunchedEffect(gameState) {
        if (gameState?.phase == GamePhase.AiThinking) {
            delay(AI_THINKING_MILLIS)
            gameState = gameEngine.aiTurn()
        }
    }
    // 出现结局（自摸 / 流局）时弹窗
    LaunchedEffect(gameState?.result) {
        if (gameState?.result != null) showGameResult = true
    }

    // ---- 输入状态 ----
    var hand by remember { mutableStateOf("34568m235p68s") }
    var furo by remember { mutableStateOf("") }
    var agari by remember { mutableStateOf("7m") }
    var tsumo by remember { mutableStateOf(true) }
    var dora by remember { mutableStateOf("0") }
    var selfWind by remember { mutableStateOf(Wind.East) }
    var roundWind by remember { mutableStateOf(Wind.East) }
    var selectedExtraYaku by remember { mutableStateOf(setOf<String>()) }
    var han by remember { mutableStateOf("3") }
    var fu by remember { mutableStateOf("40") }
    var result by remember { mutableStateOf("输入手牌后点击按钮进行计算") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("日麻算法 Demo（mahjong-utils 0.7.7）", style = MaterialTheme.typography.titleMedium)
        Text(
            "牌谱记法：万 m / 筒 p / 索 s / 字 z，0m/0p/0s 为红宝牌。" +
                "例如 34568m235p68s、123456789p111z22s",
            style = MaterialTheme.typography.bodySmall,
        )

        // ---- 单机对局 ----
        GameSection(
            state = gameState,
            onStart = {
                showGameResult = false
                gameState = gameEngine.startRound()
            },
            onDraw = { gameState = gameEngine.playerDraw() },
            onDiscard = { gameState = gameEngine.playerDiscard(it) },
        )
        HorizontalDivider()

        // ---- 手牌 ----
        OutlinedTextField(
            value = hand,
            onValueChange = { hand = it },
            label = { Text("手牌（门前，13 或 14 张）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // ---- 副露（可选） ----
        OutlinedTextField(
            value = furo,
            onValueChange = { furo = it },
            label = { Text("副露（可选，空格分隔，如 789p 111z）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // ---- 和牌张 / 宝牌 ----
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = agari,
                onValueChange = { agari = it },
                label = { Text("和牌张") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = dora,
                onValueChange = { dora = it.filter(Char::isDigit) },
                label = { Text("宝牌数") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }

        // ---- 自摸开关 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("自摸", modifier = Modifier.padding(end = 8.dp))
            Switch(checked = tsumo, onCheckedChange = { tsumo = it })
        }

        // ---- 自风 / 场风 ----
        Text("自风", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            windOptions.forEach { (wind, label) ->
                WindChip(label = label, selected = wind == selfWind) { selfWind = wind }
            }
        }
        Text("场风", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            windOptions.forEach { (wind, label) ->
                WindChip(label = label, selected = wind == roundWind) { roundWind = wind }
            }
        }

        // ---- 额外役 ----
        Text("额外役（可选）", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            extraYakuOptions.forEach { option ->
                val selected = option.key in selectedExtraYaku
                FilterChip(
                    selected = selected,
                    onClick = {
                        selectedExtraYaku = if (selected) {
                            selectedExtraYaku - option.key
                        } else {
                            selectedExtraYaku + option.key
                        }
                    },
                    label = { Text(option.label) },
                )
            }
        }

        // ---- 番符快捷输入（配合“算点数”） ----
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = han,
                onValueChange = { han = it.filter(Char::isDigit) },
                label = { Text("番数") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = fu,
                onValueChange = { fu = it.filter(Char::isDigit) },
                label = { Text("符数") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }

        // ---- 三个计算按钮 ----
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                result = MahjongCalculator.runSafe {
                    MahjongCalculator.analyzeShanten(hand, furo)
                }.fold(onSuccess = { it }, onFailure = { "错误：${it.message}" })
            }) { Text("计算向听") }

            Button(onClick = {
                result = MahjongCalculator.runSafe {
                    MahjongCalculator.analyzeHora(
                        handText = hand,
                        furoText = furo,
                        agariText = agari,
                        tsumo = tsumo,
                        dora = dora.toIntOrNull() ?: 0,
                        selfWind = selfWind,
                        roundWind = roundWind,
                        extraYakuNames = selectedExtraYaku,
                    )
                }.fold(onSuccess = { it }, onFailure = { "错误：${it.message}" })
            }) { Text("分析和牌") }

            OutlinedButton(onClick = {
                result = MahjongCalculator.runSafe {
                    MahjongCalculator.calculatePoints(
                        han = han.toIntOrNull() ?: 0,
                        fu = fu.toIntOrNull() ?: 0,
                    )
                }.fold(onSuccess = { it }, onFailure = { "错误：${it.message}" })
            }) { Text("算点数") }
        }

        // ---- 切牌评分 ----
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                result = MahjongCalculator.runSafe {
                    MahjongCalculator.evaluateDiscards(hand, furo)
                }.fold(onSuccess = { it }, onFailure = { "错误：${it.message}" })
            }) { Text("计算切牌评分") }
        }

        Spacer(Modifier.height(8.dp))

        // ---- 结果 ----
        Text("结果", style = MaterialTheme.typography.titleSmall)
        Text(
            text = result,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        )
    }

    // ---- 对局结局弹窗：自摸！/ 流局 ----
    gameState?.result?.let { roundResult ->
        if (showGameResult) {
            AlertDialog(
                onDismissRequest = { showGameResult = false },
                title = { Text(roundResult.title) },
                text = {
                    Text(
                        text = roundResult.detail,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showGameResult = false }) { Text("确定") }
                },
            )
        }
    }
}

@Composable
private fun WindChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
