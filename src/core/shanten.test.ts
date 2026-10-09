import { describe, expect, it } from 'vitest';
import { Tile } from './tile';
import { advanceKinds, evaluateDiscards, INVALID, shantenText } from './shanten';
import { isAgariTiles, isChiitoi, isKokushi } from './winCheck';
import { basePoints, getPoints } from './points';
import { build, deal, WALL_SIZE } from './wall';

describe('shanten', () => {
  it('和了形为 -1', () => {
    expect(shantenText('123m456m789m123p11z')).toBe(-1);
    expect(shantenText('1122m3344p5566s77z')).toBe(-1);
    expect(shantenText('19m19p19s1234567z1m')).toBe(-1);
  });

  it('13 张听牌为 0，一向听为 1', () => {
    expect(shantenText('123m456m789m12p11z')).toBe(0);
    expect(shantenText('123m456m789m12p12z')).toBe(1);
  });

  it('非法输入返回 INVALID', () => {
    expect(shantenText('')).toBe(INVALID);
    expect(shantenText('123')).toBe(INVALID);
    expect(shantenText('123456789m')).toBe(INVALID);
  });

  it('进张枚举：听 3p', () => {
    const counts = Tile.countsOf(Tile.parse('123m456m789m12p11z'));
    expect(advanceKinds(counts, 0)).toEqual([11]);
  });

  it('切牌评估覆盖全部不重复切法并按向听排序', () => {
    const tiles = Tile.parse('123m456m789m123p11z');
    const options = evaluateDiscards(tiles);
    expect(options).toHaveLength(13);
    for (let i = 1; i < options.length; i++) {
      expect(options[i].shanten).toBeGreaterThanOrEqual(options[i - 1].shanten);
    }
    expect(options[0].shanten).toBe(0);
  });
});

describe('winCheck', () => {
  it('标准形 / 七对子 / 国士', () => {
    expect(isAgariTiles(Tile.parse('123m456m789m123p11z'))).toBe(true);
    expect(isChiitoi(Tile.countsOf(Tile.parse('1122m3344p5566s77z')))).toBe(true);
    expect(isKokushi(Tile.countsOf(Tile.parse('19m19p19s1234567z1m')))).toBe(true);
    expect(isAgariTiles(Tile.parse('123m456m789m123p11z'), 1)).toBe(false);
    expect(isAgariTiles(Tile.parse('123m456m789m12p11z'))).toBe(false);
  });
});

describe('points', () => {
  it('查表点数', () => {
    expect(basePoints(1, 30)).toBe(240);
    expect(basePoints(5, 30)).toBe(2000);
    expect(basePoints(13, 30)).toBe(8000);
    expect(getPoints(1, 30, true).ron).toBe(1500);
    expect(getPoints(1, 30, false).ron).toBe(1000);
    expect(getPoints(1, 30, false).tsumoTotal).toBe(1100);
  });
});

describe('wall', () => {
  it('136 张、每种 4 张、3 张红 5', () => {
    const tiles = build(42);
    expect(tiles).toHaveLength(WALL_SIZE);
    expect(tiles.filter((t) => t.red)).toHaveLength(3);
    for (const c of Tile.countsOf(tiles)) expect(c).toBe(4);
  });

  it('固定种子可复现', () => {
    expect(Tile.toText(build(7))).toBe(Tile.toText(build(7)));
  });

  it('发牌 4 家各 13 张', () => {
    const { hands, wall } = deal(build(1));
    expect(hands).toHaveLength(4);
    for (const hand of hands) expect(hand).toHaveLength(13);
    expect(wall).toHaveLength(WALL_SIZE - 52);
  });
});
