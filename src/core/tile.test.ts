import { describe, expect, it } from 'vitest';
import { Tile, TILE_COUNT } from './tile';

describe('Tile', () => {
  it('解析牌谱文本并标记红 5', () => {
    const tiles = Tile.parse('0m5m1z');
    expect(tiles).toHaveLength(3);
    expect(tiles[0].kind).toBe(4);
    expect(tiles[0].red).toBe(true);
    expect(tiles[1].kind).toBe(4);
    expect(tiles[1].red).toBe(false);
    expect(tiles[2].kind).toBe(27);
    expect(tiles[2].red).toBe(false);
  });

  it('非法文本返回空数组', () => {
    expect(Tile.parse('123')).toEqual([]);
    expect(Tile.parse('8z')).toEqual([]);
    expect(Tile.parse('m123')).toEqual([]);
  });

  it('kindToText / kindToDisplay', () => {
    expect(Tile.kindToText(0)).toBe('1m');
    expect(Tile.kindToText(4, true)).toBe('0m');
    expect(Tile.kindToText(33)).toBe('7z');
    expect(Tile.kindToDisplay(27)).toBe('东');
    expect(Tile.kindToDisplay(33)).toBe('中');
    expect(Tile.kindToDisplay(13, true)).toBe('0p');
  });

  it('toText 按花色分组、同种类红 5 在前', () => {
    const tiles = Tile.parse('5m0m123m');
    expect(Tile.toText(tiles)).toBe('12305m');
  });

  it('countsOf 合并红 5', () => {
    const counts = Tile.countsOf(Tile.parse('0m5m5m'));
    expect(counts[4]).toBe(3);
    expect(counts.filter((c) => c > 0)).toHaveLength(1);
    expect(counts).toHaveLength(TILE_COUNT);
  });

  it('suitOf 花色判定', () => {
    expect(Tile.suitOf(0)).toBe(0);
    expect(Tile.suitOf(9)).toBe(1);
    expect(Tile.suitOf(18)).toBe(2);
    expect(Tile.suitOf(27)).toBe(3);
    expect(Tile.isValidKind(34)).toBe(false);
  });
});
