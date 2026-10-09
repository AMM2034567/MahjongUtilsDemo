# Mahjong Godot 工程（Godot 4.3+ / 目标平台 Android）

《雀魂麻将》风格的日麻对局原型。原 Android Jetpack Compose 版本已暂停（保留在仓库根目录），
本目录为切换后的 Godot 4.x 技术栈工程。

## 目录结构

```
godot/
├── project.godot              # 工程配置：1280×720、GL Compatibility（移动端友好）、横屏
├── export_presets.cfg         # 导出预设：Android（arm64-v8a/armeabi-v7a，APK 输出 build/）
├── icon.svg                   # 工程图标
├── scenes/                    # 场景（.tscn，全部用 Control/Container/Anchor 自适应布局）
│   ├── main/                  #   主界面 / 大厅（参考图1）—— 待实现
│   ├── game/                  #   GameBoard.tscn 对局牌桌（参考图2）—— 任务三
│   └── ui/                    #   可复用 UI 组件（按钮、牌面控件等）—— 待实现
├── scripts/
│   ├── core/                  # 纯算法层（无任何节点依赖，可 headless 测试）
│   │   ├── tile.gd            #   牌数据结构：34 种 kind + 红 5 标记，牌谱文本解析
│   │   ├── shanten.gd         #   向听计算（标准形/七对子/国士）、进张、切牌评估
│   │   ├── win_check.gd       #   和牌判断与标准形分解（雀头+4面子）
│   │   ├── points.gd          #   番符 -> 庄闲点数查表（满贯以上定额）
│   │   └── wall.gd            #   136 张牌山生成、Fisher-Yates 洗牌、轮流发牌
│   ├── game/                  # 对局状态机 / AI（任务四：GameEngine 移植）—— 待实现
│   └── ui/                    # UI 逻辑脚本（手牌区、牌河、动画）—— 待实现
├── assets/
│   ├── fonts/                 # 牌面/界面字体（需含 CJK 字形）
│   ├── images/tiles/          # 牌面贴图（34 种 + 正/背面）
│   └── sounds/                # 音效（摸打、和牌、立直棒等）
└── tests/
    └── run_tests.gd           # 核心算法 headless 测试（96 个断言）
```

## 核心算法说明

### 牌数据结构（`scripts/core/tile.gd`）

- `kind: int`（0..33）：`0-8=1m-9m`，`9-17=1p-9p`，`18-26=1s-9s`，`27-33=东-中`
- `red: bool`：红 5 与普通 5 共用 kind（4/13/22），算法层自动归一；
  红标记只用于显示、宝牌计数与“切 0m / 切 5m”两种切牌选项的区分
- 文本协议与原版一致：`34568m235p68s`，`0m/0p/0s` 为红 5

### 向听计算（`scripts/core/shanten.gd`）

- 标准形：`score = 2*(副露+面子) + 搭子 + 雀头`，约束 `面子+搭子 <= 4-副露`，`向听 = 8 - score`
  - DFS 分支限界 + 状态记忆化，另有跨调用缓存（AI 反复评估同一手牌时近乎 0 开销）
- 七对子：`6 - 对子数 + max(0, 7 - 种类数)`
- 国士无双：`13 - 幺九字种类数 - 重复标记`
- 门前 13/14 张取三者最小值；`furo_count=-1` 时按张数自动推断副露组数（13/14→0，10/11→1 …）
- 产出：向听数、进张枚举（`advance_kinds`）、听牌进张（`waits`，供振听判定）、
  14 张切牌评估（`evaluate_discards`，按 向听↑→进张数↓ 排序，首条即 AI 首选）

### 和牌 / 算点

- `win_check.gd`：标准形分解（雀头 + 4 面子，返回全部解供后续役种/符计算）、七对子、国士
- `points.gd`：番符查表（3翻40符 庄荣 7700 / 闲荣 5200，5 翻以上满贯定额，13 翻役满）；
  役种判定与符计算在下一阶段对接

## 运行测试

```bash
# 首次（生成脚本全局类缓存）
godot --headless --path godot --import

# 核心算法测试（96 个断言；需要 godot 4.3+ 在 PATH，或替换为完整路径）
godot --headless --path godot -s tests/run_tests.gd
```

测试中的 `ERROR: Tile.parse ...` / `evaluate_discards ...` 为非法输入用例的预期报错输出。

## 导出 Android

```bash
godot --headless --path godot --export-debug Android build/mahjong-demo.apk
```

- 包名 `com.example.mahjongutilsdemo`（与原 Android 版一致）
- 需先在 编辑器 → 导出 → Android 中配置 SDK/Keystore（debug 可用 debug.keystore）
- 渲染后端 GL Compatibility + ETC2/ASTC 压缩已启用

## 路线图（对应 issue 四个任务）

- [x] 任务一：工程结构 + Android 导出配置
- [x] 任务二：核心算法 GDScript 化（牌结构 / 向听 / 和牌判断 / 点数 / 牌山）
- [ ] 任务三：`GameBoard.tscn` 牌桌 UI（Camera3D 2.5D、手牌 HBoxContainer、
      四家牌河自动换行、中央信息区、四角玩家信息与按钮）
- [ ] 任务四：对局循环（发牌→摸切→AI 切牌评分→自摸/流局）+ 点击手牌飞向牌河的 Tween 动画
- [ ] 后续：役种判定与符计算、立直/荣和/副露、大厅界面（参考图1）
