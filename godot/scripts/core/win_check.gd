class_name WinCheck
extends RefCounted
## 和牌判断：标准形（4 面子 + 雀头）、七对子、国士无双。
##
## decompositions() 会给出全部标准形分解（雀头 + 面子列表），
## 供后续的役种判定 / 符计算使用；当前阶段只做和牌形判断。

const COUNT := 34

const ORPHAN_KINDS: Array[int] = [0, 8, 9, 17, 18, 26, 27, 28, 29, 30, 31, 32, 33]


## 和牌判断（计数数组入口）。furo_count = 已副露的面子组数。
static func is_agari(counts: Array[int], furo_count: int = 0) -> bool:
	var total := 0
	for c in counts:
		total += c
	if total != 14 - 3 * furo_count:
		return false
	if not decompositions(counts, furo_count).is_empty():
		return true
	if furo_count == 0:
		return is_chiitoi(counts) or is_kokushi(counts)
	return false


## 和牌判断（手牌入口）。tiles = 门前手牌（14 - 3*副露 张）。
static func is_agari_tiles(tiles: Array[Tile], furo_count: int = 0) -> bool:
	return is_agari(Tile.counts_of(tiles), furo_count)


## 七对子：恰好 7 个不同种类的对子（合计 14 张）。
static func is_chiitoi(counts: Array[int]) -> bool:
	var pairs := 0
	for k in COUNT:
		if counts[k] == 2:
			pairs += 1
		elif counts[k] != 0:
			return false
	return pairs == 7


## 国士无双：13 种幺九字各至少 1 张，且其中一种恰好 2 张。
static func is_kokushi(counts: Array[int]) -> bool:
	var total := 0
	var kinds := 0
	var dup := 0
	for k in COUNT:
		var c := counts[k]
		if c == 0:
			continue
		total += c
		if ORPHAN_KINDS.has(k):
			kinds += 1
			if c >= 2:
				dup += 1
		else:
			return false
	return total == 14 and kinds == 13 and dup == 1


## 全部标准形分解。每项：
## { "pair": kind, "melds": Array，元素为 [k]（刻子）或 [k, k+1, k+2]（顺子）}
## 结果已去重；无解返回空数组。
static func decompositions(counts: Array[int], furo_count: int = 0) -> Array[Dictionary]:
	var out: Array[Dictionary] = []
	var melds_needed := 4 - furo_count
	if melds_needed < 0:
		return out
	var seen: Dictionary = {}
	for pair in COUNT:
		if counts[pair] < 2:
			continue
		counts[pair] -= 2
		var current: Array = []
		_extract(counts, 0, melds_needed, current, pair, out, seen)
		counts[pair] += 2
	return out


# ------------------------------------------------------------------ 内部实现

## 递归抽取面子：i 起所有牌必须恰好被消掉 need 组面子。
static func _extract(
	counts: Array[int], i: int, need: int, current: Array,
	pair: int, out: Array, seen: Dictionary,
) -> void:
	if need == 0:
		for k in range(i, COUNT):
			if counts[k] != 0:
				return
		var melds: Array = []
		for m in current:
			melds.append(m.duplicate())
		var key := str(pair) + ":" + str(melds)
		if not seen.has(key):
			seen[key] = true
			out.append({"pair": pair, "melds": melds})
		return
	if i >= COUNT:
		return
	if counts[i] == 0:
		_extract(counts, i + 1, need, current, pair, out, seen)
		return
	# 刻子
	if counts[i] >= 3:
		counts[i] -= 3
		current.append([i, i, i])
		_extract(counts, i, need - 1, current, pair, out, seen)
		current.pop_back()
		counts[i] += 3
	# 顺子（仅同花色内）
	if i < 27 and i % 9 <= 6 and counts[i + 1] > 0 and counts[i + 2] > 0:
		counts[i] -= 1
		counts[i + 1] -= 1
		counts[i + 2] -= 1
		current.append([i, i + 1, i + 2])
		_extract(counts, i, need - 1, current, pair, out, seen)
		current.pop_back()
		counts[i] += 1
		counts[i + 1] += 1
		counts[i + 2] += 1
	# 既不能成刻也不能成顺 -> 此路径失败
