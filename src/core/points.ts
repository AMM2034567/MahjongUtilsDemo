/**
 * 点数计算：番 / 符 -> 庄闲的荣和与自摸点数（纯查表）。
 *
 * 役种判定与符计算尚未实现，本文件先提供“已知番符 -> 点数”的基础能力。
 *
 * 对应 godot/scripts/core/points.gd
 */

/** 切上满贯（4翻30符 / 3翻60符 记 2000 底）；默认关闭 */
export const KIRIAGE_MANGAN = false;

export interface ParentPoints {
  ron: number;
  tsumoEach: number;
  tsumoTotal: number;
}

export interface ChildPoints {
  ron: number;
  tsumoParent: number;
  tsumoChild: number;
  tsumoTotal: number;
}

export type Points = ParentPoints | ChildPoints;

/** 计算底分：min(fu * 2^(2+han), 满贯上限)，6翻以上按翻数直接取固定底分。 */
export function basePoints(han: number, fu: number): number {
  if (han < 0 || fu < 0) return 0;
  if (han >= 13) return 8000; // 役满
  if (han >= 11) return 6000; // 三倍满
  if (han >= 8) return 4000; // 倍满
  if (han >= 6) return 3000; // 跳满
  if (han >= 5) return 2000; // 满贯
  const base = fu * (1 << (2 + han));
  if (KIRIAGE_MANGAN && (han === 4 || (han === 3 && fu >= 60))) return 2000;
  if (base > 2000) return 2000;
  return base;
}

/** 指定庄闲的完整点数表，所有点数均按 100 向上取整。 */
export function getPoints(han: number, fu: number, isParent: boolean): Points {
  const base = basePoints(han, fu);
  if (isParent) {
    const each = ceil100(base * 2);
    return {
      ron: ceil100(base * 6),
      tsumoEach: each,
      tsumoTotal: each * 3,
    };
  }
  const tsumoParent = ceil100(base * 2);
  const tsumoChild = ceil100(base);
  return {
    ron: ceil100(base * 4),
    tsumoParent,
    tsumoChild,
    tsumoTotal: tsumoParent + tsumoChild * 2,
  };
}

/** 100 向上取整（仅对非负值有意义） */
export function ceil100(v: number): number {
  if (v <= 0) return 0;
  return Math.ceil(v / 100) * 100;
}
