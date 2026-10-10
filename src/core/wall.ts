import { Tile } from './tile';

/**
 * 牌山：136 张牌的生成、洗牌与发牌基础。
 *
 * 构成：1-9m/p/s 各 4 张（每种花色的 5 中有 1 张换成红 5）+ 字牌 7 种各 4 张。
 * 洗牌使用 Fisher-Yates。
 *
 * 对应 godot/scripts/core/wall.gd
 */

export const WALL_SIZE = 136;
export const PLAYER_NUM = 4;
export const HAND_SIZE = 13;
export const DEAL_SIZE = HAND_SIZE * PLAYER_NUM;

const SUIT_BASES = [0, 9, 18];

/** 可注入的随机源 */
export interface Rng {
  /** 返回 [0, 1) 区间的随机数 */
  next(): number;
}

/** 默认随机源 */
export const defaultRng: Rng = { next: () => Math.random() };

/** mulberry32：小而稳定的可复现随机源 */
export function seededRng(seed: number): Rng {
  let a = seed >>> 0;
  return {
    next() {
      a = (a + 0x6d2b79f5) >>> 0;
      let t = a;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    },
  };
}

/**
 * 生成并洗好一副 136 张牌山。
 * seed >= 0 时使用固定种子（可复现牌局），否则随机。
 */
export function build(seed = -1): Tile[] {
  return buildWith(seed >= 0 ? seededRng(seed) : defaultRng);
}

/** 使用给定随机源生成牌山（测试可注入） */
export function buildWith(rng: Rng): Tile[] {
  const tiles: Tile[] = [];
  for (const base of SUIT_BASES) {
    for (let num = 1; num <= 9; num++) {
      if (num === 5) {
        for (let i = 0; i < 3; i++) tiles.push(new Tile(base + 4, false));
        tiles.push(new Tile(base + 4, true)); // 红 5 替换其中一张
      } else {
        for (let i = 0; i < 4; i++) tiles.push(new Tile(base + num - 1, false));
      }
    }
  }
  for (let n = 1; n <= 7; n++) {
    for (let i = 0; i < 4; i++) tiles.push(new Tile(27 + n - 1, false));
  }
  if (tiles.length !== WALL_SIZE) {
    throw new Error(`牌山张数错误：${tiles.length}`);
  }
  // Fisher-Yates 洗牌
  for (let i = tiles.length - 1; i > 0; i--) {
    const j = Math.floor(rng.next() * (i + 1));
    const tmp = tiles[i];
    tiles[i] = tiles[j];
    tiles[j] = tmp;
  }
  return tiles;
}

export interface DealResult {
  hands: Tile[][];
  wall: Tile[];
}

/** 按座位轮流发牌：每位玩家依次摸 1 张，共 HAND_SIZE 轮。 */
export function deal(tiles: Tile[]): DealResult {
  const wall = tiles.slice();
  const hands: Tile[][] = [[], [], [], []];
  for (let round = 0; round < HAND_SIZE; round++) {
    for (let seat = 0; seat < PLAYER_NUM; seat++) {
      if (wall.length === 0) return { hands, wall };
      hands[seat].push(wall.shift() as Tile);
    }
  }
  return { hands, wall };
}
