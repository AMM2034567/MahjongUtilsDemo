class_name Shanten
extends RefCounted
## 向听数计算（标准形 / 七对子 / 国士无双）、进张枚举与切牌评估。
##
## 全部计算基于 34 种牌的计数数组（Array[int]，下标 = Tile.kind），
## 红 5 与普通 5 合并计数，因此算法无需关心红 5。
##
## 向听模型（与 Kotlin 版 mahjong-utils 对齐）：
##   score = 2*(副露数+暗刻/顺子数) + 搭子数 + 雀头(0/1)
##   约束：暗刻/顺子 + 搭子 <= 4 - 副露数（即“含雀头总共 5 个块”）
##   向听 = 8 - score（和了形为 -1）
##   门前 13/14 张时再取七对子、国士无双的最小值。

const COUNT := 34

## 非法输入（无法解析的手牌文本、张数不合法等）的返回值
const INVALID := -99

## std 向听的跨调用缓存：key = "counts|furo"，避免 AI 反复评估时重复搜索
static var _std_cache: Dictionary = {}

const ORPHAN_KINDS: Array[int] = [0, 8, 9, 17, 18, 26, 27, 28, 29, 30, 31, 32, 33]


## 推断副露组数：-1 表示按手牌张数自动推断（13/14 -> 0 组，10/11 -> 1 组 ...）。
## 张数不合法时返回 -1。
static func infer_furo(tile_count: int, furo_count: int) -> int:
	if furo_count >= 0:
		if tile_count == 13 - 3 * furo_count or tile_count == 14 - 3 * furo_count:
			return furo_count
		return -1
	match tile_count:
		13, 14:
			return 0
		10, 11:
			return 1
		7, 8:
			return 2
		4, 5:
			return 3
		_:
			return -1


## 向听数（计数数组入口）。furo_count 为副露组数。
static func shanten_counts(counts: Array[int], furo_count: int) -> int:
	if furo_count < 0:
		return INVALID
	var total := 0
	for c in counts:
		total += c
	var result := _std_shanten(counts, furo_count)
	if furo_count == 0 and (total == 13 or total == 14):
		result = mini(result, _chiitoi_shanten(counts))
		result = mini(result, _kokushi_shanten(counts))
	return result


## 向听数（手牌入口）。furo_count = -1 时按张数自动推断。
static func shanten(tiles: Array[Tile], furo_count: int = -1) -> int:
	var f := infer_furo(tiles.size(), furo_count)
	if f < 0:
		return INVALID
	return shanten_counts(Tile.counts_of(tiles), f)


## 向听数（牌谱文本入口），如 "112233p44556s127z"。
## 文本非法或张数不合法时返回 INVALID。
static func shanten_text(text: String, furo_count: int = -1) -> int:
	if text.strip_edges().is_empty():
		return INVALID
	var tiles := Tile.parse(text)
	if tiles.is_empty():
		return INVALID
	return shanten(tiles, furo_count)


## 进张：所有能使向听数 -1 的种类（未听牌时即“好形/改良张”意义上的进张）。
## 返回升序的 kind 数组；手牌张数不合法时返回空数组。
static func advance_kinds(counts: Array[int], furo_count: int) -> Array[int]:
	var empty: Array[int] = []
	if furo_count < 0:
		return empty
	var base := shanten_counts(counts, furo_count)
	if base == INVALID or base < 0:
		return empty
	var out: Array[int] = []
	for k in COUNT:
		if counts[k] >= 4:
			continue
		counts[k] += 1
		var after := shanten_counts(counts, furo_count)
		counts[k] -= 1
		if after == base - 1:
			out.append(k)
	return out


## 听牌进张（仅听牌状态返回，对应 Kotlin 版 GameEngine.waits；用于振听判定）。
static func waits(tiles: Array[Tile], furo_count: int = -1) -> Array[int]:
	var empty: Array[int] = []
	var f := infer_furo(tiles.size(), furo_count)
	if f < 0:
		return empty
	var counts := Tile.counts_of(tiles)
	if shanten_counts(counts, f) != 0:
		return empty
	return advance_kinds(counts, f)


## 切牌评估：对 14 张手牌（门前张数 + 3*副露 = 14）的每种切法，
## 计算切后向听、进张与进张剩余枚数。
##
## 每条结果：
## {
##   "discard_kind": int, "discard_red": bool,   # 切掉的牌
##   "shanten": int,                              # 切后向听
##   "advance": Array[int],                       # 切后进张（kind 升序）
##   "advance_num": int,                          # 进张剩余枚数合计
## }
## 排序：向听升序 -> 进张数降序（首条即 AI 首选）。
## 需要合计 14 张，否则返回空数组并 push_error。
static func evaluate_discards(tiles: Array[Tile], furo_count: int = -1) -> Array[Dictionary]:
	var rows: Array[Dictionary] = []
	# 张数校验：门前 + 3*副露 必须合计 14 张
	var f := -1
	if furo_count >= 0:
		if tiles.size() + 3 * furo_count == 14:
			f = furo_count
	else:
		var candidate := infer_furo(tiles.size(), -1)
		if candidate >= 0 and tiles.size() + 3 * candidate == 14:
			f = candidate
	if f < 0:
		push_error("evaluate_discards: 需要合计 14 张手牌（门前 %d 张 + 副露 %d 组）" % [
			tiles.size(), maxi(furo_count, 0)])
		return rows

	var seen: Dictionary = {}
	for i in tiles.size():
		var t := tiles[i]
		var identity := t.kind * 2 + (1 if t.red else 0)
		if seen.has(identity):
			continue
		seen[identity] = true

		var rest := tiles.duplicate()
		rest.remove_at(i)
		var counts := Tile.counts_of(rest)
		var s := shanten_counts(counts, f)
		var advance := advance_kinds(counts, f)
		var advance_num := 0
		for k in advance:
			advance_num += 4 - counts[k]
		rows.append({
			"discard_kind": t.kind,
			"discard_red": t.red,
			"shanten": s,
			"advance": advance,
			"advance_num": advance_num,
		})

	rows.sort_custom(func(a: Dictionary, b: Dictionary) -> bool:
		if a["shanten"] != b["shanten"]:
			return a["shanten"] < b["shanten"]
		if a["advance_num"] != b["advance_num"]:
			return a["advance_num"] > b["advance_num"]
		return a["discard_kind"] < b["discard_kind"]
	)
	return rows


# ------------------------------------------------------------------ 内部实现

## 标准形向听：8 - max(score)，带分支限界与状态记忆化。
static func _std_shanten(counts: Array[int], furo: int) -> int:
	var cache_key := "%s|%d" % [str(counts), furo]
	if _std_cache.has(cache_key):
		return _std_cache[cache_key]

	var limit := 4 - furo
	var best := {"score": 0}
	var memo: Dictionary = {}
	_search(counts, 0, 0, 0, 0, furo, limit, best, memo)
	var result: int = 8 - int(best["score"])

	if _std_cache.size() > 20000:
		_std_cache.clear()
	_std_cache[cache_key] = result
	return result


## 从第 i 种牌开始搜索分解方式，最大化 score = 2*(furo+melds) + partials + head。
## counts 就地修改（每个分支内成对地减/加还原）。
static func _search(
	counts: Array[int], i: int, melds: int, partials: int, head: int,
	furo: int, limit: int, best: Dictionary, memo: Dictionary,
) -> void:
	var score := 2 * (furo + melds) + partials + head
	if score > int(best["score"]):
		best["score"] = score
	if i >= COUNT:
		return
	# 分支限界：剩余每个块位至多值 2 分，雀头至多 1 分
	if score + 2 * (limit - melds - partials) + (1 - head) <= int(best["score"]):
		return
	var key := "%d;%d;%d;%d;%s" % [i, melds, partials, head, str(counts.slice(i))]
	if memo.has(key):
		return
	memo[key] = true

	if counts[i] == 0:
		_search(counts, i + 1, melds, partials, head, furo, limit, best, memo)
		return

	var in_suit := i < 27
	var off := i % 9 if in_suit else 0

	# 1) 刻子
	if counts[i] >= 3 and melds + 1 + partials <= limit:
		counts[i] -= 3
		_search(counts, i, melds + 1, partials, head, furo, limit, best, memo)
		counts[i] += 3
	# 2) 对子作搭子
	if counts[i] >= 2 and melds + partials + 1 <= limit:
		counts[i] -= 2
		_search(counts, i, melds, partials + 1, head, furo, limit, best, memo)
		counts[i] += 2
	# 3) 顺子 i,i+1,i+2
	if in_suit and off <= 6 and counts[i + 1] > 0 and counts[i + 2] > 0 \
			and melds + 1 + partials <= limit:
		counts[i] -= 1
		counts[i + 1] -= 1
		counts[i + 2] -= 1
		_search(counts, i, melds + 1, partials, head, furo, limit, best, memo)
		counts[i] += 1
		counts[i + 1] += 1
		counts[i + 2] += 1
	# 4) 两面/边张搭子 i,i+1
	if in_suit and off <= 7 and counts[i + 1] > 0 \
			and melds + partials + 1 <= limit:
		counts[i] -= 1
		counts[i + 1] -= 1
		_search(counts, i, melds, partials + 1, head, furo, limit, best, memo)
		counts[i] += 1
		counts[i + 1] += 1
	# 5) 嵌张搭子 i,i+2
	if in_suit and off <= 6 and counts[i + 2] > 0 \
			and melds + partials + 1 <= limit:
		counts[i] -= 1
		counts[i + 2] -= 1
		_search(counts, i, melds, partials + 1, head, furo, limit, best, memo)
		counts[i] += 1
		counts[i + 2] += 1
	# 6) 雀头（只取一次，不占块位额度）
	if head == 0 and counts[i] >= 2:
		counts[i] -= 2
		_search(counts, i, melds, partials, 1, furo, limit, best, memo)
		counts[i] += 2
	# 7) 剩余作浮牌丢弃
	var saved := counts[i]
	counts[i] = 0
	_search(counts, i + 1, melds, partials, head, furo, limit, best, memo)
	counts[i] = saved


## 七对子向听：6 - 对子数，不同种类不足 7 时按缺种类数加向听。
static func _chiitoi_shanten(counts: Array[int]) -> int:
	var pairs := 0
	var kinds := 0
	for k in COUNT:
		if counts[k] >= 2:
			pairs += 1
			kinds += 1
		elif counts[k] == 1:
			kinds += 1
	return 6 - pairs + maxi(0, 7 - kinds)


## 国士无双向听：13 - 已有幺九字种类数 - 是否已有重复。
static func _kokushi_shanten(counts: Array[int]) -> int:
	var kinds := 0
	var dup := 0
	for k in ORPHAN_KINDS:
		if counts[k] > 0:
			kinds += 1
		if counts[k] >= 2:
			dup = 1
	return 13 - kinds - dup
