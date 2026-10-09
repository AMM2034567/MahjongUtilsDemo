import { Tile, TILE_COUNT } from './tile';

/**
 * 向听数计算（标准形 / 七对子 / 国士无双）、进张枚举与切牌评估。
 *
 * 全部计算基于 34 种牌的计数数组（下标 = Tile.kind），
 * 红 5 与普通 5 合并计数，因此算法无需关心红 5。
 *
 * 向听模型（与 Kotlin 版 mahjong-utils 对齐）：
 *   score = 2*(副露数+暗刻/顺子数) + 搭子数 + 雀头(0/1)
 *   约束：暗刻/顺子 + 搭子 <= 4 - 副露数（即“含雀头总共 5 个块”）
 *   向听 = 8 - score（和了形为 -1）
 *   门前 13/14 张时再取七对子、国士无双的最小值。
 *
 * 对应 godot/scripts/core/shanten.gd
 */

export interface DiscardOption {
  discardKind: number;
  discardRed: boolean;
  shanten: number;
  advance: number[];
  advanceNum: number;
}

/** 非法输入（无法解析的手牌文本、张数不合法等）的返回值 */
export const INVALID = -99;

export const ORPHAN_KINDS = [0, 8, 9, 17, 18, 26, 27, 28, 29, 30, 31, 32, 33];

/** std 向听的跨调用缓存：key = "counts|furo"，避免 AI 反复评估时重复搜索 */
const stdCache = new Map<string, number>();
const STD_CACHE_LIMIT = 20000;

/**
 * 推断副露组数：-1 表示按手牌张数自动推断（13/14 -> 0 组，10/11 -> 1 组 ...）。
 * 张数不合法时返回 -1。
 */
export function inferFuro(tileCount: number, furoCount: number): number {
  if (furoCount >= 0) {
    if (tileCount === 13 - 3 * furoCount || tileCount === 14 - 3 * furoCount) {
      return furoCount;
    }
    return -1;
  }
  switch (tileCount) {
    case 13:
    case 14:
      return 0;
    case 10:
    case 11:
      return 1;
    case 7:
    case 8:
      return 2;
    case 4:
    case 5:
      return 3;
    default:
      return -1;
  }
}

/** 向听数（计数数组入口）。furoCount 为副露组数。 */
export function shantenCounts(counts: number[], furoCount: number): number {
  if (furoCount < 0) return INVALID;
  let total = 0;
  for (const c of counts) total += c;

  let result = stdShanten(counts, furoCount);
  if (furoCount === 0 && (total === 13 || total === 14)) {
    result = Math.min(result, chiitoiShanten(counts));
    result = Math.min(result, kokushiShanten(counts));
  }
  return result;
}

/** 向听数（手牌入口）。furoCount = -1 时按张数自动推断。 */
export function shanten(tiles: Tile[], furoCount = -1): number {
  const f = inferFuro(tiles.length, furoCount);
  if (f < 0) return INVALID;
  return shantenCounts(Tile.countsOf(tiles), f);
}

/** 向听数（牌谱文本入口），如 "112233p44556s127z"。 */
export function shantenText(text: string, furoCount = -1): number {
  if (text.trim().length === 0) return INVALID;
  const tiles = Tile.parse(text);
  if (tiles.length === 0) return INVALID;
  return shanten(tiles, furoCount);
}

/**
 * 进张：所有能使向听数 -1 的种类。
 * 返回升序的 kind 数组；手牌张数不合法时返回空数组。
 */
export function advanceKinds(counts: number[], furoCount: number): number[] {
  if (furoCount < 0) return [];
  const base = shantenCounts(counts, furoCount);
  if (base === INVALID || base < 0) return [];

  const work = counts.slice();
  const out: number[] = [];
  for (let k = 0; k < TILE_COUNT; k++) {
    if (work[k] >= 4) continue;
    work[k] += 1;
    const after = shantenCounts(work, furoCount);
    work[k] -= 1;
    if (after === base - 1) out.push(k);
  }
  return out;
}

/** 听牌进张（仅听牌状态返回，用于振听判定）。 */
export function waits(tiles: Tile[], furoCount = -1): number[] {
  const f = inferFuro(tiles.length, furoCount);
  if (f < 0) return [];
  const counts = Tile.countsOf(tiles);
  if (shantenCounts(counts, f) !== 0) return [];
  return advanceKinds(counts, f);
}

/**
 * 切牌评估：对 14 张手牌（门前张数 + 3*副露 = 14）的每种切法，
 * 计算切后向听、进张与进张剩余枚数。
 * 排序：向听升序 -> 进张数降序（首条即 AI 首选）。
 */
export function evaluateDiscards(tiles: Tile[], furoCount = -1): DiscardOption[] {
  const rows: DiscardOption[] = [];

  let f = -1;
  if (furoCount >= 0) {
    if (tiles.length + 3 * furoCount === 14) f = furoCount;
  } else {
    const candidate = inferFuro(tiles.length, -1);
    if (candidate >= 0 && tiles.length + 3 * candidate === 14) f = candidate;
  }
  if (f < 0) {
    console.error(
      `evaluateDiscards: 需要合计 14 张手牌（门前 ${tiles.length} 张 + 副露 ${Math.max(furoCount, 0)} 组）`,
    );
    return rows;
  }

  const seen = new Set<number>();
  for (let i = 0; i < tiles.length; i++) {
    const t = tiles[i];
    const identity = t.kind * 2 + (t.red ? 1 : 0);
    if (seen.has(identity)) continue;
    seen.add(identity);

    const rest = tiles.slice();
    rest.splice(i, 1);
    const counts = Tile.countsOf(rest);
    const s = shantenCounts(counts, f);
    const advance = advanceKinds(counts, f);
    let advanceNum = 0;
    for (const k of advance) advanceNum += 4 - counts[k];

    rows.push({
      discardKind: t.kind,
      discardRed: t.red,
      shanten: s,
      advance,
      advanceNum,
    });
  }

  rows.sort((a, b) => {
    if (a.shanten !== b.shanten) return a.shanten - b.shanten;
    if (a.advanceNum !== b.advanceNum) return b.advanceNum - a.advanceNum;
    return a.discardKind - b.discardKind;
  });
  return rows;
}

// ------------------------------------------------------------------ 内部实现

/** 标准形向听：8 - max(score)，带分支限界与状态记忆化。 */
function stdShanten(counts: number[], furo: number): number {
  const cacheKey = `${counts.join(',')}|${furo}`;
  const cached = stdCache.get(cacheKey);
  if (cached !== undefined) return cached;

  const limit = 4 - furo;
  const best = { score: 0 };
  const memo = new Set<string>();
  search(counts, 0, 0, 0, 0, furo, limit, best, memo);
  const result = 8 - best.score;

  if (stdCache.size > STD_CACHE_LIMIT) stdCache.clear();
  stdCache.set(cacheKey, result);
  return result;
}

/**
 * 从第 i 种牌开始搜索分解方式，最大化 score = 2*(furo+melds) + partials + head。
 * counts 就地修改（每个分支内成对地减/加还原）。
 */
function search(
  counts: number[],
  i: number,
  melds: number,
  partials: number,
  head: number,
  furo: number,
  limit: number,
  best: { score: number },
  memo: Set<string>,
): void {
  const score = 2 * (furo + melds) + partials + head;
  if (score > best.score) best.score = score;
  if (i >= TILE_COUNT) return;

  // 分支限界：剩余每个块位至多值 2 分，雀头至多 1 分
  if (score + 2 * (limit - melds - partials) + (1 - head) <= best.score) return;

  const key = `${i};${melds};${partials};${head};${counts.slice(i).join(',')}`;
  if (memo.has(key)) return;
  memo.add(key);

  if (counts[i] === 0) {
    search(counts, i + 1, melds, partials, head, furo, limit, best, memo);
    return;
  }

  const inSuit = i < 27;
  const off = inSuit ? i % 9 : 0;

  // 1) 刻子
  if (counts[i] >= 3 && melds + 1 + partials <= limit) {
    counts[i] -= 3;
    search(counts, i, melds + 1, partials, head, furo, limit, best, memo);
    counts[i] += 3;
  }
  // 2) 对子作搭子
  if (counts[i] >= 2 && melds + partials + 1 <= limit) {
    counts[i] -= 2;
    search(counts, i, melds, partials + 1, head, furo, limit, best, memo);
    counts[i] += 2;
  }
  // 3) 顺子 i,i+1,i+2
  if (
    inSuit &&
    off <= 6 &&
    counts[i + 1] > 0 &&
    counts[i + 2] > 0 &&
    melds + 1 + partials <= limit
  ) {
    counts[i] -= 1;
    counts[i + 1] -= 1;
    counts[i + 2] -= 1;
    search(counts, i, melds + 1, partials, head, furo, limit, best, memo);
    counts[i] += 1;
    counts[i + 1] += 1;
    counts[i + 2] += 1;
  }
  // 4) 两面/边张搭子 i,i+1
  if (inSuit && off <= 7 && counts[i + 1] > 0 && melds + partials + 1 <= limit) {
    counts[i] -= 1;
    counts[i + 1] -= 1;
    search(counts, i, melds, partials + 1, head, furo, limit, best, memo);
    counts[i] += 1;
    counts[i + 1] += 1;
  }
  // 5) 嵌张搭子 i,i+2
  if (inSuit && off <= 6 && counts[i + 2] > 0 && melds + partials + 1 <= limit) {
    counts[i] -= 1;
    counts[i + 2] -= 1;
    search(counts, i, melds, partials + 1, head, furo, limit, best, memo);
    counts[i] += 1;
    counts[i + 2] += 1;
  }
  // 6) 雀头（只取一次，不占块位额度）
  if (head === 0 && counts[i] >= 2) {
    counts[i] -= 2;
    search(counts, i, melds, partials, 1, furo, limit, best, memo);
    counts[i] += 2;
  }
  // 7) 剩余作浮牌丢弃
  const saved = counts[i];
  counts[i] = 0;
  search(counts, i + 1, melds, partials, head, furo, limit, best, memo);
  counts[i] = saved;
}

/** 七对子向听：6 - 对子数，不同种类不足 7 时按缺种类数加向听。 */
function chiitoiShanten(counts: number[]): number {
  let pairs = 0;
  let kinds = 0;
  for (let k = 0; k < TILE_COUNT; k++) {
    if (counts[k] >= 2) {
      pairs += 1;
      kinds += 1;
    } else if (counts[k] === 1) {
      kinds += 1;
    }
  }
  return 6 - pairs + Math.max(0, 7 - kinds);
}

/** 国士无双向听：13 - 已有幺九字种类数 - 是否已有重复。 */
function kokushiShanten(counts: number[]): number {
  let kinds = 0;
  let dup = 0;
  for (const k of ORPHAN_KINDS) {
    if (counts[k] > 0) kinds += 1;
    if (counts[k] >= 2) dup = 1;
  }
  return 13 - kinds - dup;
}
