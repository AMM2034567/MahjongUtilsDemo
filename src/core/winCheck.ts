import { Tile, TILE_COUNT } from './tile';
import { ORPHAN_KINDS } from './shanten';

/**
 * 和牌判断：标准形（4 面子 + 雀头）、七对子、国士无双。
 *
 * decompositions() 会给出全部标准形分解（雀头 + 面子列表），
 * 供后续的役种判定 / 符计算使用。
 *
 * 对应 godot/scripts/core/win_check.gd
 */

export interface Decomposition {
  pair: number;
  /** 元素为 [k]（刻子）或 [k, k+1, k+2]（顺子） */
  melds: number[][];
}

/** 和牌判断（计数数组入口）。furoCount = 已副露的面子组数。 */
export function isAgari(counts: number[], furoCount = 0): boolean {
  let total = 0;
  for (const c of counts) total += c;
  if (total !== 14 - 3 * furoCount) return false;
  if (decompositions(counts, furoCount).length > 0) return true;
  if (furoCount === 0) return isChiitoi(counts) || isKokushi(counts);
  return false;
}

/** 和牌判断（手牌入口）。tiles = 门前手牌（14 - 3*副露 张）。 */
export function isAgariTiles(tiles: Tile[], furoCount = 0): boolean {
  return isAgari(Tile.countsOf(tiles), furoCount);
}

/** 七对子：恰好 7 个不同种类的对子（合计 14 张）。 */
export function isChiitoi(counts: number[]): boolean {
  let pairs = 0;
  for (let k = 0; k < TILE_COUNT; k++) {
    if (counts[k] === 2) {
      pairs += 1;
    } else if (counts[k] !== 0) {
      return false;
    }
  }
  return pairs === 7;
}

/** 国士无双：13 种幺九字各至少 1 张，且其中一种恰好 2 张。 */
export function isKokushi(counts: number[]): boolean {
  let total = 0;
  let kinds = 0;
  let dup = 0;
  for (let k = 0; k < TILE_COUNT; k++) {
    const c = counts[k];
    if (c === 0) continue;
    total += c;
    if (ORPHAN_KINDS.includes(k)) {
      kinds += 1;
      if (c >= 2) dup += 1;
    } else {
      return false;
    }
  }
  return total === 14 && kinds === 13 && dup === 1;
}

/** 全部标准形分解。无解返回空数组。 */
export function decompositions(counts: number[], furoCount = 0): Decomposition[] {
  const out: Decomposition[] = [];
  const meldsNeeded = 4 - furoCount;
  if (meldsNeeded < 0) return out;

  const seen = new Set<string>();
  const work = counts.slice();
  for (let pair = 0; pair < TILE_COUNT; pair++) {
    if (work[pair] < 2) continue;
    work[pair] -= 2;
    const current: number[][] = [];
    extract(work, 0, meldsNeeded, current, pair, out, seen);
    work[pair] += 2;
  }
  return out;
}

/** 递归抽取面子：i 起所有牌必须恰好被消掉 need 组面子。 */
function extract(
  counts: number[],
  i: number,
  need: number,
  current: number[][],
  pair: number,
  out: Decomposition[],
  seen: Set<string>,
): void {
  if (need === 0) {
    for (let k = i; k < TILE_COUNT; k++) {
      if (counts[k] !== 0) return;
    }
    const melds = current.map((m) => m.slice());
    const key = `${pair}:${JSON.stringify(melds)}`;
    if (!seen.has(key)) {
      seen.add(key);
      out.push({ pair, melds });
    }
    return;
  }
  if (i >= TILE_COUNT) return;
  if (counts[i] === 0) {
    extract(counts, i + 1, need, current, pair, out, seen);
    return;
  }
  // 刻子
  if (counts[i] >= 3) {
    counts[i] -= 3;
    current.push([i, i, i]);
    extract(counts, i, need - 1, current, pair, out, seen);
    current.pop();
    counts[i] += 3;
  }
  // 顺子（仅同花色内）
  if (i < 27 && i % 9 <= 6 && counts[i + 1] > 0 && counts[i + 2] > 0) {
    counts[i] -= 1;
    counts[i + 1] -= 1;
    counts[i + 2] -= 1;
    current.push([i, i + 1, i + 2]);
    extract(counts, i, need - 1, current, pair, out, seen);
    current.pop();
    counts[i] += 1;
    counts[i + 1] += 1;
    counts[i + 2] += 1;
  }
}
