package com.example.mahjongutilsdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mahjongutilsdemo.ui.theme.MahjongUtilsDemoTheme
import mahjongutils.models.Wind

/**
 * 单屏 Demo：输入手牌 → 向听 / 和牌 / 点数。
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
}

@Composable
private fun WindChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
