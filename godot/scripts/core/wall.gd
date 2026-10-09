class_name Wall
extends RefCounted
## 牌山：136 张牌的生成、洗牌与发牌基础。
##
## 构成：1-9m/p/s 各 4 张（每种花色的 5 中有 1 张换成红 5）+ 字牌 7 种各 4 张。
## 与 Kotlin 版 GameEngine.buildWall 对齐；洗牌使用 Fisher-Yates。

const WALL_SIZE := 136
const PLAYER_NUM := 4
const HAND_SIZE := 13
const DEAL_SIZE := HAND_SIZE * PLAYER_NUM

const SUIT_BASES: Array[int] = [0, 9, 18]


## 生成并洗好一副 136 张牌山。
## p_seed >= 0 时使用固定种子（可复现牌局），否则随机。
static func build(p_seed: int = -1) -> Array[Tile]:
	var rng := RandomNumberGenerator.new()
	if p_seed >= 0:
		rng.seed = p_seed
	else:
		rng.randomize()
	return build_with(rng)


## 使用给定随机源生成牌山（测试可注入）
static func build_with(rng: RandomNumberGenerator) -> Array[Tile]:
	var tiles: Array[Tile] = []
	for base in SUIT_BASES:
		for num in range(1, 10):
			if num == 5:
				for i in 3:
					tiles.append(Tile.new(base + 4, false))
				tiles.append(Tile.new(base + 4, true))  # 红 5 替换其中一张
			else:
				for i in 4:
					tiles.append(Tile.new(base + num - 1, false))
	for n in range(1, 8):
		for i in 4:
			tiles.append(Tile.new(27 + n - 1, false))
	assert(tiles.size() == WALL_SIZE, "牌山张数错误：%d" % tiles.size())
	# Fisher-Yates 洗牌
	for i in range(tiles.size() - 1, 0, -1):
		var j := rng.randi_range(0, i)
		var tmp := tiles[i]
		tiles[i] = tiles[j]
		tiles[j] = tmp
	return tiles


## 按座位轮流发牌：每位玩家依次摸 1 张，共 HAND_SIZE 轮。
## 返回 { "hands": Array（4 组 Array[Tile]），"wall": 剩余牌山 }。
static func deal(tiles: Array[Tile]) -> Dictionary:
	var wall := tiles.duplicate()
	var hands: Array = []
	for s in PLAYER_NUM:
		var hand: Array[Tile] = []
		hands.append(hand)
	for round_i in HAND_SIZE:
		for seat in PLAYER_NUM:
			if wall.is_empty():
				return {"hands": hands, "wall": wall}
			hands[seat].append(wall.pop_front())
	return {"hands": hands, "wall": wall}
