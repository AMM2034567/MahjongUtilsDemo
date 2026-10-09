class_name Points
extends RefCounted
## 点数计算：番 / 符 -> 庄闲的荣和与自摸点数（纯查表，与 Kotlin 版
## getParentPointByHanHu / getChildPointByHanHu 对齐）。
##
## 役种判定与符计算尚未实现（下一阶段对接和牌分析时补充），
## 本文件先提供“已知番符 -> 点数”的基础能力，供算分与测试使用。

## 切上满贯（4翻30符 / 3翻60符 记 2000 底）；默认关闭，与 mahjong-utils 默认一致
const KIRIAGE_MANGAN := false


## 计算底分：min(fu * 2^(2+han), 满贯上限)，6翻以上按翻数直接取固定底分。
static func base_points(han: int, fu: int) -> int:
	if han < 0 or fu < 0:
		return 0
	if han >= 13:
		return 8000  # 役满
	if han >= 11:
		return 6000  # 三倍满
	if han >= 8:
		return 4000  # 倍满
	if han >= 6:
		return 3000  # 跳满
	if han >= 5:
		return 2000  # 满贯
	var base := fu * (1 << (2 + han))
	if KIRIAGE_MANGAN and ((han == 4) or (han == 3 and fu >= 60)):
		return 2000
	if base > 2000:
		return 2000
	return base


## 指定庄闲的完整点数表：
## 庄家: { "ron", "tsumo_each", "tsumo_total" }
## 闲家: { "ron", "tsumo_parent"(庄家支付), "tsumo_child"(每闲支付), "tsumo_total" }
## 所有点数均按 100 向上取整。
static func get_points(han: int, fu: int, is_parent: bool) -> Dictionary:
	var base := base_points(han, fu)
	if is_parent:
		var each := ceil100(base * 2)
		return {
			"ron": ceil100(base * 6),
			"tsumo_each": each,
			"tsumo_total": each * 3,
		}
	var tsumo_parent := ceil100(base * 2)
	var tsumo_child := ceil100(base)
	return {
		"ron": ceil100(base * 4),
		"tsumo_parent": tsumo_parent,
		"tsumo_child": tsumo_child,
		"tsumo_total": tsumo_parent + tsumo_child * 2,
	}


## 100 向上取整（仅对非负值有意义）
static func ceil100(v: int) -> int:
	if v <= 0:
		return 0
	return ((v + 99) / 100) * 100
