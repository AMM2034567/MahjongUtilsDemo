# MahjongUtilsDemo

用于日麻算法的学习与测试的 Android Demo。**完全离线**（无 INTERNET 权限），所有计算均在本地完成。

基于 [mahjong-utils](https://github.com/ssttkkl/mahjong-utils) **0.7.7**（Kotlin Multiplatform，Maven Central）。

## 功能

| 功能 | 库 API | 说明 |
|------|--------|------|
| 单机对局（游戏循环） | `shanten()` `hora()` | 发牌 → 摸切循环 → AI 依次行动 → 自摸/流局判定；玩家坐东，三家 AI 按切牌评分决策 |
| 切牌评分 | `shanten()` | 14 张手牌的每种切法：向听 → 进张 → EPT；对局 AI 与「计算切牌评分」共用 |
| 向听分析 | `shanten()` | 13 张：向听数 + 进张；14 张：向听数 + 切牌推荐（★ 为不退向打法） |
| 和牌分析 | `hora()` | 役种（中英对照）/ 番 / 符 / 庄闲点数；支持副露、自摸、宝牌、自风/场风、额外役 |
| 点数计算 | `getParentPointByHanHu()` `getChildPointByHanHu()` | 纯番符查表，庄/闲荣和与自摸点数 |

对局原型的简化规则（见 `GameEngine`）：无王牌区/宝牌指示牌（红 5 计 1 张宝牌）、只判自摸、不做荣和/副露/立直宣告；自己牌河含听牌进张即振听（不能自摸）。

## 技术栈

- Kotlin 2.0.21 / Jetpack Compose（Material 3）
- Gradle Kotlin DSL + 版本目录（`gradle/libs.versions.toml`）
- minSdk 26 / targetSdk 34 / compileSdk 34
- mahjong-utils 0.7.7（无 Android 目标变体，Gradle 为其解析 JVM 变体 `mahjong-utils-jvm`；JVM 字节码可直接运行于 ART）

## 牌谱记法

- 万 `m` / 筒 `p` / 索 `s` / 字 `z`（z：1-4 风牌，5-7 白发中）
- `0m`/`0p`/`0s` 表示红宝牌
- 例：`34568m235p68s`、`123456789p111z22s`
- 副露：空格分隔多副，如 `789p 111z`；暗杠 `0110m`

## 运行

```bash
# 单元测试（JVM，42 个用例：库集成 16 + 切牌评分 12 + 对局引擎 14）
./gradlew :app:testDebugUnitTest

# 构建 debug APK
./gradlew :app:assembleDebug
# 输出：app/build/outputs/apk/debug/app-debug.apk
```

Android Studio：直接打开项目根目录 → 选择模拟器/真机 → Run。

安装到设备：`adb install -r app/build/outputs/apk/debug/app-debug.apk`

### 快速验证用例

- 向听：输入 `34568m235p68s`（13 张）→ 2 向听；`112233p44556s127z`（14 张）→ 1 向听 + 切牌建议
- 和牌：手牌 `12233466m111z`、副露 `789p`、和牌张 `1z`、自摸、宝牌 4、自风/场风东 → 自风+场风，6 番 30 符
- 点数：3 番 40 符 → 庄家荣和 7700 / 闲家荣和 5200

## 项目结构

```
MahjongUtilsDemo/
├── settings.gradle.kts              # 仓库与模块声明
├── build.gradle.kts                 # 根构建脚本（插件声明，apply false）
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml           # 版本目录：AGP/Kotlin/Compose/mahjong-utils
│   └── wrapper/                     # Gradle 8.7 wrapper
├── gradlew / gradlew.bat
└── app/
    ├── build.gradle.kts             # minSdk 26、Compose、mahjong-utils 依赖
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml  # 无任何权限（离线）
        │   ├── java/com/example/mahjongutilsdemo/
        │   │   ├── MainActivity.kt          # Compose UI：对局区 + 输入 + 三按钮 + 结果 + 结局弹窗
        │   │   ├── GameEngine.kt            # 单机对局状态机：牌山/发牌/摸切循环/AI/自摸与流局判定
        │   │   ├── MahjongCalculator.kt     # 库封装：解析/向听/和牌/点数/切牌评分 + 异常处理
        │   │   └── ui/theme/Theme.kt        # Material 3 主题
        │   └── res/
        │       ├── values/strings.xml
        │       └── values/themes.xml
        └── test/java/com/example/mahjongutilsdemo/
            ├── MahjongCalculatorTest.kt     # 16 个用例（对齐官方 README）
            ├── DiscardEvaluationTest.kt     # 12 个用例（切牌评分）
            └── GameEngineTest.kt            # 14 个用例（对局引擎，可注入牌山复现牌局）
```

## 关键代码导读

### 1. 依赖（`gradle/libs.versions.toml`）

```toml
[versions]
mahjongUtils = "0.7.7"

[libraries]
mahjong-utils = { group = "io.github.ssttkkl", name = "mahjong-utils", version.ref = "mahjongUtils" }
```

`app/build.gradle.kts` 中 `implementation(libs.mahjong.utils)`。库本身不做网络请求，app 也不声明 INTERNET 权限。

### 2. 向听（`MahjongCalculator.analyzeShanten`）

```kotlin
val result = shanten(Tile.parseTiles("34568m235p68s"))
when (val info = result.shantenInfo) {
    is ShantenWithoutGot -> info.shantenNum / info.advance      // 13 张
    is ShantenWithGot    -> info.discardToAdvance               // 14 张 → 切牌→进张
}
```

### 3. 和牌（`MahjongCalculator.analyzeHora`）

```kotlin
val result = hora(
    tiles = Tile.parseTiles("12233466m111z"),
    furo = listOf(Furo("789p")),   // 顶层函数 Furo(text) 等价于 Furo.parse(text)
    agari = Tile.get("1z"),
    tsumo = true, dora = 4,
    selfWind = Wind.East, roundWind = Wind.East,
)
// result.yaku / result.han / result.hu / result.parentPoint / result.childPoint
```

注意：`hora` 的 `tiles` 传 13 张时会自动补上 `agari`；传 14 张时视为已含和牌张。`Yaku.name` 为英文 key（如 `SelfWind`），UI 中 `MahjongCalculator.yakuNameZh` 做了中文映射。

### 4. 点数（`MahjongCalculator.calculatePoints`）

```kotlin
val parent = getParentPointByHanHu(3, 40)   // ron=7700, tsumo=2600/家
val child = getChildPointByHanHu(3, 40)    // ron=5200, tsumoParent=2600, tsumoChild=1300
```

### 5. 异常处理

解析失败抛 `IllegalArgumentException`；参数校验失败抛 `ValidationException`（继承前者，`message` 已拼好错误说明）。UI 通过 `MahjongCalculator.runSafe { ... }` 统一捕获并显示 `错误：...`。

### 6. 对局循环（`GameEngine`）

```kotlin
val engine = GameEngine()          // 传 Random(seed) 可复现牌局
engine.startRound()                // → AwaitDraw：136 张洗牌，轮流发 13 张
engine.playerDraw()                // → PlayerDiscard（判自摸 → RoundEnd）
engine.playerDiscard(tile)         // → AiThinking：更新牌河、振听、听牌提示
repeat(3) { engine.aiTurn() }      // 三家 AI 摸切，转回 AwaitDraw（或 RoundEnd）
```

纯 Kotlin、不依赖 Compose；UI 每次调用后拿 `GameState` 快照渲染，AI 思考延时由 UI 的 `LaunchedEffect` 控制（`AI_THINKING_MILLIS`）。牌山可由测试注入：`startRound(wallTiles)`。

## 后续可扩展方向

- **牌效率 / 打点比较**：切牌评分已实现（`evaluateDiscardsDetailed`：向听 → 进张 → EPT），可再叠加 `hora()` 估算期望打点
- **副露判断**：`furoChanceShanten()` 分析吃碰机会对向听的影响
- **听牌枚举**：听牌时 `hora()` 遍历 `Tile.all` 可算每种和牌张的番符点（打点表）
- **改良分析**：`ShantenWithoutGot.improvement` / `goodShapeImprovement`（一向听/听牌的改良张）
- **规则选项**：`HoraOptions`（切上满贯、累计役满、古役等）、`HanHuOptions`
- **UI 增强**：手牌可视化（麻将牌图标）、多 Screen 导航（Navigation Compose）、历史记录（DataStore）
- **对局规则补全**：立直宣告、荣和、副露（吃碰杠）、王牌区与宝牌指示牌、流局听牌结算、AI 的立直/和牌判断
- **AI 决策**：当前按 向听 → 进张 → EPT 切牌；可叠加 `hora()` 平均打点做真正的 EPT（efficient points）评估
- **对接实战**：从剪贴板导入牌谱文本；导出计算结果

## 官方文档

- 仓库：https://github.com/ssttkkl/mahjong-utils
- API 文档：https://mahjong-utils-docs.vercel.app/
- 版本差异请以 **0.7.7** 为准（本项目锁定该版本）；升级时先看 [Release Notes](https://github.com/ssttkkl/mahjong-utils/releases)
