class_name Tile
extends RefCounted
## 麻将牌的数据结构：34 种牌（kind 0..33）+ 红宝牌标记（red）。
##
## kind 编号约定：
##   0-8  = 1m-9m（万）
##   9-17 = 1p-9p（筒）
##   18-26 = 1s-9s（索）
##   27-33 = 1z-7z（东南西北白发中）
##
## 红 5（0m/0p/0s）与普通 5 共用同一个 kind（4/13/22），仅用 red 标记区分：
## - 向听 / 和牌等算法只处理 kind，红 5 自动归一化为普通 5；
## - red 只用于显示、宝牌计数以及“切牌选项”的区分（切 0m 与切 5m 是两种选择）。

const COUNT := 34

const SUIT_CHARS: Array[String] = ["m", "p", "s", "z"]
const SUIT_BASES: Array[int] = [0, 9, 18, 27]
const SUIT_LENGTHS: Array[int] = [9, 9, 9, 7]

const SUIT_M := 0
const SUIT_P := 1
const SUIT_S := 2
const SUIT_Z := 3

var kind: int
var red: bool


func _init(p_kind: int = 0, p_red: bool = false) -> void:
	kind = p_kind
	red = p_red


func _to_string() -> String:
	return Tile.kind_to_text(kind, red)


## 构造一张牌
static func make(p_kind: int, p_red: bool = false) -> Tile:
	return Tile.new(p_kind, p_red)


## 该种类所属花色（SUIT_M / SUIT_P / SUIT_S / SUIT_Z）
static func suit_of(p_kind: int) -> int:
	if p_kind >= 27:
		return SUIT_Z
	if p_kind >= 18:
		return SUIT_S
	if p_kind >= 9:
		return SUIT_P
	return SUIT_M


static func is_valid_kind(p_kind: int) -> bool:
	return p_kind >= 0 and p_kind < COUNT


## 同一种类的剩余张数上限恒为 4（红 5 也计入该种类）
static func max_of_kind() -> int:
	return 4


## 单张牌的数字部分，如 kind=4,red=true -> "0"；kind=27 -> "1"
static func kind_to_digits(p_kind: int, p_red: bool = false) -> String:
	if not is_valid_kind(p_kind):
		return "?"
	var suit := suit_of(p_kind)
	if suit == SUIT_Z:
		return str(p_kind - SUIT_BASES[suit] + 1)
	if p_red:
		return "0"
	return str(p_kind - SUIT_BASES[suit] + 1)


## 单张牌 -> 牌谱文本，如 kind=4,red=true -> "0m"；kind=27 -> "1z"
static func kind_to_text(p_kind: int, p_red: bool = false) -> String:
	if not is_valid_kind(p_kind):
		return "?"
	return kind_to_digits(p_kind, p_red) + SUIT_CHARS[suit_of(p_kind)]


## 解析牌谱文本，如 "34568m235p68s"；0m/0p/0s 表示红宝牌。
## 语法非法时返回空数组并 push_error。
static func parse(text: String) -> Array[Tile]:
	var out: Array[Tile] = []
	var digits := ""
	for ch in text.strip_edges():
		if SUIT_CHARS.has(ch):
			var suit: int = SUIT_CHARS.find(ch)
			var base := SUIT_BASES[suit]
			if digits.is_empty():
				push_error("Tile.parse: 花色 '%s' 前缺少数字：%s" % [ch, text])
				return []
			for d in digits:
				var n := d.unicode_at(0) - 48
				if suit == SUIT_Z:
					if n < 1 or n > 7:
						push_error("Tile.parse: 字牌只能是 1-7：%s" % text)
						return []
					out.append(Tile.new(base + n - 1, false))
				elif n == 0:
					out.append(Tile.new(base + 4, true))
				elif n >= 1 and n <= 9:
					out.append(Tile.new(base + n - 1, false))
				else:
					push_error("Tile.parse: 数字超出 1-9：%s" % text)
					return []
			digits = ""
		elif ch >= "0" and ch <= "9":
			digits += ch
		else:
			push_error("Tile.parse: 非法字符 '%s'：%s" % [ch, text])
			return []
	if not digits.is_empty():
		push_error("Tile.parse: 数字后缺少花色：%s" % text)
		return []
	return out


## 手牌 -> 牌谱文本（按花色分组、种类升序、同种类红 5 在前），如 "123m056m789m"
static func to_text(tiles: Array[Tile]) -> String:
	var grouped: Array = [[], [], [], []]
	for t in tiles:
		grouped[suit_of(t.kind)].append(t)
	var parts: Array[String] = []
	for suit in 4:
		var group: Array = grouped[suit]
		if group.is_empty():
			continue
		group.sort_custom(func(a: Tile, b: Tile) -> bool:
			if a.kind != b.kind:
				return a.kind < b.kind
			return a.red and not b.red
		)
		var s := ""
		for t in group:
			s += kind_to_digits(t.kind, t.red)
		parts.append(s + SUIT_CHARS[suit])
	return "".join(parts)


## 34 种牌的计数数组（红 5 自动并入普通 5）
static func counts_of(tiles: Array[Tile]) -> Array[int]:
	var counts: Array[int] = []
	counts.resize(COUNT)
	counts.fill(0)
	for t in tiles:
		if is_valid_kind(t.kind):
			counts[t.kind] += 1
	return counts


## 归一化：红 5 与普通 5 是同一种类，算法层面直接用 kind 比较即可。
## 此函数提供语义化的等价判断（与 Kotlin 版 GameEngine.normalize 对应）。
static func same_kind(a: Tile, b: Tile) -> bool:
	return a.kind == b.kind
