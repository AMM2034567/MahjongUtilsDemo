extends SceneTree
## 核心算法的 headless 测试（无需打开编辑器）：
##   godot --headless --path godot --import                    # 首次：生成脚本全局类缓存
##   godot --headless --path godot -s tests/run_tests.gd       # 运行测试
## 用例与 Android 版（MahjongCalculatorTest / GameEngineTest / DiscardEvaluationTest）对齐。

var passed := 0
var failed := 0


func _init() -> void:
	_test_tile()
	_test_shanten()
	_test_waits()
	_test_evaluate_discards()
	_test_win_check()
	_test_points()
	_test_wall()
	_test_invalid_inputs()

	print("")
	print("========== passed=%d failed=%d ==========" % [passed, failed])
	quit(1 if failed > 0 else 0)


func check(name: String, got: Variant, want: Variant) -> void:
	if got == want:
		passed += 1
	else:
		failed += 1
		print("FAIL  %s\n      got  = %s\n      want = %s" % [name, str(got), str(want)])


func check_true(name: String, cond: bool) -> void:
	check(name, cond, true)


# ------------------------------------------------------------------ 牌数据结构

func _test_tile() -> void:
	var tiles := Tile.parse("34568m235p68s")
	check("parse 张数", tiles.size(), 10)
	check("parse 3m kind", tiles[0].kind, 2)
	check("parse 5m kind", tiles[2].kind, 4)
	check("parse 6s kind", tiles[8].kind, 23)

	var red := Tile.parse("0m0p0s")
	check("parse 红5 张数", red.size(), 3)
	check_true("parse 0m = kind4+红", red[0].kind == 4 and red[0].red)
	check_true("parse 0p = kind13+红", red[1].kind == 13 and red[1].red)
	check_true("parse 0s = kind22+红", red[2].kind == 22 and red[2].red)

	check("kind_to_text 红5", Tile.kind_to_text(4, true), "0m")
	check("kind_to_text 5m", Tile.kind_to_text(4), "5m")
	check("kind_to_text 东", Tile.kind_to_text(27), "1z")
	check("kind_to_text 中", Tile.kind_to_text(33), "7z")

	var roundtrip := Tile.parse("123m056m789m111z22p")
	# to_text 按种类归并（同种类内红 5 在前），因此分组输出
	check("to_text 往返", Tile.to_text(roundtrip), "123056789m22p111z")
	check("to_text 再解析", Tile.parse(Tile.to_text(roundtrip)).size(), 14)

	var counts := Tile.counts_of(roundtrip)
	check("counts 总数", counts.reduce(func(a: int, b: int) -> int: return a + b, 0), 14)
	check("counts 5m 合并红5", counts[4], 2)
	check("counts 1z", counts[27], 3)

	check("parse 非法字符", Tile.parse("xxxx").size(), 0)
	check("parse 尾部数字", Tile.parse("123").size(), 0)


# ------------------------------------------------------------------ 向听

func _test_shanten() -> void:
	# 听牌（GameEngineTest 用例）
	check("123m456m789m11p23s", Shanten.shanten_text("123m456m789m11p23s"), 0)
	check("123m456m789m11p24s", Shanten.shanten_text("123m456m789m11p24s"), 0)
	check("123m456m789m11s22p", Shanten.shanten_text("123m456m789m11s22p"), 0)
	# 一向听 / 二向听
	check("123m456m789m11p5s9p", Shanten.shanten_text("123m456m789m11p5s9p"), 1)
	check("112233p44556s127z (14)", Shanten.shanten_text("112233p44556s127z"), 1)
	check("34568m235p68s (10, 副露推断)", Shanten.shanten_text("34568m235p68s"), 2)
	# 和了形 = -1
	check("123m456m789m11p234s", Shanten.shanten_text("123m456m789m11p234s"), -1)
	check("123456789m111z22s", Shanten.shanten_text("123456789m111z22s"), -1)
	# 副露：门前 11 张 + 789p = 14 张和了形
	check("12233466m111z + 789p", Shanten.shanten_text("12233466m111z", 1), -1)
	# 全孤张幺九（14 张）：靠嵌张搭子可到 3 向听
	check("13579m13579p13s11z", Shanten.shanten_text("13579m13579p13s11z"), 3)

	# 七对子
	check("chiitoi 听牌 1122334455667m", Shanten.shanten_text("1122334455667m"), 0)
	check("chiitoi 和了 11223344556677m", Shanten.shanten_text("11223344556677m"), -1)
	# 国士无双
	check("kokushi 听牌 13 种", Shanten.shanten_text("19m19p19s1234567z"), 0)
	check("kokushi 听牌 12 种+重复", Shanten.shanten_text("19m19p19s1234566z"), 0)
	check("kokushi 和了", Shanten.shanten_text("19m19p19s1234567z1z"), -1)


# ------------------------------------------------------------------ 进张 / 听牌

func _test_waits() -> void:
	check("waits 123m456m789m11p23s", waits_text("123m456m789m11p23s"), ["1s", "4s"])
	check("waits 123m456m789m11p24s", waits_text("123m456m789m11p24s"), ["3s"])
	check("waits 123m456m789m11p46s", waits_text("123m456m789m11p46s"), ["5s"])
	check("waits 123m456m789m11p3s5s", waits_text("123m456m789m11p3s5s"), ["4s"])
	check("waits 未听牌为空", waits_text("123m456m789m11p5s9p"), [])
	check_true("advance 一向听有进张", not Shanten.advance_kinds(
		Tile.counts_of(Tile.parse("123m456m789m11p5s9p")), 0).is_empty())


func waits_text(text: String) -> Array[String]:
	var out: Array[String] = []
	for k in Shanten.waits(Tile.parse(text)):
		out.append(Tile.kind_to_text(k))
	return out


# ------------------------------------------------------------------ 切牌评估

func _test_evaluate_discards() -> void:
	# 和了形 14 张：任意切牌后都听牌，去重后 11 种切法
	var rows := Shanten.evaluate_discards(Tile.parse("123456789m111z22s"))
	check("和了形切法数", rows.size(), 11)
	check_true("和了形切后全听", rows.all(func(r: Dictionary) -> bool: return r["shanten"] == 0))
	check_true("和了形排序", _is_sorted(rows))

	# 官方 14 张示例
	rows = Shanten.evaluate_discards(Tile.parse("112233p44556s127z"))
	check_true("112233p... 非空", not rows.is_empty())
	check_true("112233p... 排序", _is_sorted(rows))

	# 全孤张幺九：去重 13 种切法，且切后均非听牌
	rows = Shanten.evaluate_discards(Tile.parse("13579m13579p13s11z"))
	check("孤张切法数", rows.size(), 13)
	check_true("孤张切后均非听牌", rows.all(func(r: Dictionary) -> bool: return r["shanten"] > 0))
	check_true("孤张排序", _is_sorted(rows))

	# 副露：门前 11 张 + 789p = 14
	rows = Shanten.evaluate_discards(Tile.parse("12233466m111z"), 1)
	check("副露切法数", rows.size(), 6)

	# 红 5 是独立的切牌选项（切 0m 与切 5m）
	rows = Shanten.evaluate_discards(Tile.parse("123m056m789m111z22p"))
	var seen_red5 := false
	var seen_plain5 := false
	for r in rows:
		if r["discard_kind"] == 4:
			if r["discard_red"]:
				seen_red5 = true
			else:
				seen_plain5 = true
	check_true("0m 与 5m 分开评估", seen_red5 and seen_plain5)

	# 非 14 张不能评估
	check("13 张返回空", Shanten.evaluate_discards(Tile.parse("345678m23456p22s")).size(), 0)
	check("15 张返回空", Shanten.evaluate_discards(Tile.parse("123456789m111z22s3p")).size(), 0)


func _is_sorted(rows: Array[Dictionary]) -> bool:
	for i in range(1, rows.size()):
		var a: Dictionary = rows[i - 1]
		var b: Dictionary = rows[i]
		if a["shanten"] != b["shanten"]:
			if int(a["shanten"]) > int(b["shanten"]):
				return false
		elif int(a["advance_num"]) < int(b["advance_num"]):
			return false
	return true


# ------------------------------------------------------------------ 和牌判断

func _test_win_check() -> void:
	check_true("agari 标准形", WinCheck.is_agari_tiles(Tile.parse("123m456m789m11p234s")))
	check_true("agari 123456789m111z22s", WinCheck.is_agari_tiles(Tile.parse("123456789m111z22s")))
	check_true("agari 七对子", WinCheck.is_agari_tiles(Tile.parse("11223344556677m")))
	check_true("agari 国士", WinCheck.is_agari_tiles(Tile.parse("19m19p19s1234567z1z")))
	check_true("非 agari 13 张", not WinCheck.is_agari_tiles(Tile.parse("123m456m789m11p23s")))
	check_true("非 agari 无雀头", not WinCheck.is_agari_tiles(Tile.parse("123m456m789m123s45p")))
	check_true("非 agari 七对子形状错", not WinCheck.is_agari_tiles(Tile.parse("1111223344556s1m")))

	var decs := WinCheck.decompositions(Tile.counts_of(Tile.parse("123m456m789m11p234s")))
	check_true("decompositions 非空", not decs.is_empty())
	var d: Dictionary = decs[0]
	check("decompositions 雀头 = 11p", d["pair"], Tile.parse("11p")[0].kind)
	check("decompositions 4 面子", d["melds"].size(), 4)

	# 副露 1 组：门前 11 张 + 副露 1 组 = 14 张
	check_true("agari 副露 1 组", WinCheck.is_agari(
		Tile.counts_of(Tile.parse("123m456m789m11p")), 1))
	check_true("非 agari 副露 1 组(13张门前)", not WinCheck.is_agari(
		Tile.counts_of(Tile.parse("123m456m789m11p23s")), 1))


# ------------------------------------------------------------------ 点数查表

func _test_points() -> void:
	# 3 番 40 符（官方示例）
	var parent := Points.get_points(3, 40, true)
	check("3翻40符 庄荣和", parent["ron"], 7700)
	check("3翻40符 庄自摸每家", parent["tsumo_each"], 2600)
	check("3翻40符 庄自摸合计", parent["tsumo_total"], 7800)
	var child := Points.get_points(3, 40, false)
	check("3翻40符 闲荣和", child["ron"], 5200)
	check("3翻40符 闲自摸 庄付", child["tsumo_parent"], 2600)
	check("3翻40符 闲自摸 闲付", child["tsumo_child"], 1300)
	check("3翻40符 闲自摸合计", child["tsumo_total"], 5200)

	# 满贯以上
	check("5翻30符 闲荣和", Points.get_points(5, 30, false)["ron"], 8000)
	check("6翻30符 闲荣和", Points.get_points(6, 30, false)["ron"], 12000)
	check("8翻30符 闲荣和", Points.get_points(8, 30, false)["ron"], 16000)
	check("11翻30符 闲荣和", Points.get_points(11, 30, false)["ron"], 24000)
	check("13翻30符 闲荣和", Points.get_points(13, 30, false)["ron"], 32000)
	check("13翻30符 庄荣和", Points.get_points(13, 30, true)["ron"], 48000)

	# 低番符 / 满贯判定
	check("1翻30符 闲荣和", Points.get_points(1, 30, false)["ron"], 1000)
	check("4翻30符 闲荣和", Points.get_points(4, 30, false)["ron"], 7700)
	check("4翻40符 闲荣和", Points.get_points(4, 40, false)["ron"], 8000)


# ------------------------------------------------------------------ 牌山

func _test_wall() -> void:
	var wall := Wall.build(1)
	check("牌山 136 张", wall.size(), Wall.WALL_SIZE)

	var counts := Tile.counts_of(wall)
	var all_four := true
	for k in Tile.COUNT:
		if counts[k] != 4:
			all_four = false
	check_true("每种牌恰好 4 张", all_four)
	var reds := 0
	for t in wall:
		if t.red:
			reds += 1
	check("红 5 共 3 张", reds, 3)

	# 同种子可复现，不同种子不同（比较摸牌顺序，to_text 会归并排序）
	check_true("同种子复现", _wall_fingerprint(Wall.build(42)) == _wall_fingerprint(Wall.build(42)))
	check_true("不同种子不同", _wall_fingerprint(Wall.build(42)) != _wall_fingerprint(Wall.build(43)))

	# 轮流发牌：每家 13 张，剩余 84
	var dealt := Wall.deal(Wall.build(7))
	var hands: Array = dealt["hands"]
	check("发牌 4 家", hands.size(), 4)
	for h in hands:
		check("手牌 13 张", h.size(), 13)
	check("剩余 84 张", dealt["wall"].size(), Wall.WALL_SIZE - Wall.DEAL_SIZE)


## 牌山摸牌顺序指纹（kind + 红标记），用于比较洗牌结果
func _wall_fingerprint(wall: Array[Tile]) -> String:
	var parts: Array[String] = []
	for t in wall:
		parts.append("%d%d" % [t.kind, 1 if t.red else 0])
	return ",".join(parts)


# ------------------------------------------------------------------ 非法输入

func _test_invalid_inputs() -> void:
	check("shanten_text 空", Shanten.shanten_text(""), Shanten.INVALID)
	check("shanten_text xxxx", Shanten.shanten_text("xxxx"), Shanten.INVALID)
	check("shanten_text 张数非法(16)", Shanten.shanten_text("123456789m111z22s33p"), Shanten.INVALID)
	check("waits 非法文本", Shanten.waits(Tile.parse("xxxx")).size(), 0)
	check("evaluate 非法文本", Shanten.evaluate_discards(Tile.parse("xxxx")).size(), 0)
