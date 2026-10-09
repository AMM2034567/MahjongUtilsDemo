/**
 * 麻将牌的数据结构：34 种牌（kind 0..33）+ 红宝牌标记（red）。
 *
 * kind 编号约定：
 *   0-8  = 1m-9m（万）
 *   9-17 = 1p-9p（筒）
 *   18-26 = 1s-9s（索）
 *   27-33 = 1z-7z（东南西北白发中）
 *
 * 红 5（0m/0p/0s）与普通 5 共用同一个 kind（4/13/22），仅用 red 标记区分：
 * - 向听 / 和牌等算法只处理 kind，红 5 自动归一化为普通 5；
 * - red 只用于显示、宝牌计数以及“切牌选项”的区分。
 *
 * 对应 godot/scripts/core/tile.gd
 */

export const TILE_COUNT = 34;

export const SUIT_CHARS = ['m', 'p', 's', 'z'] as const;
export const SUIT_BASES = [0, 9, 18, 27] as const;
export const SUIT_LENGTHS = [9, 9, 9, 7] as const;

export const SUIT_M = 0;
export const SUIT_P = 1;
export const SUIT_S = 2;
export const SUIT_Z = 3;

/** 字牌的中文展示名（1z-7z） */
export const HONOR_NAMES = ['东', '南', '西', '北', '白', '发', '中'] as const;

export class Tile {
  kind: number;
  red: boolean;

  constructor(kind = 0, red = false) {
    this.kind = kind;
    this.red = red;
  }

  toString(): string {
    return Tile.kindToText(this.kind, this.red);
  }

  static make(kind: number, red = false): Tile {
    return new Tile(kind, red);
  }

  /** 该种类所属花色（SUIT_M / SUIT_P / SUIT_S / SUIT_Z） */
  static suitOf(kind: number): number {
    if (kind >= 27) return SUIT_Z;
    if (kind >= 18) return SUIT_S;
    if (kind >= 9) return SUIT_P;
    return SUIT_M;
  }

  static isValidKind(kind: number): boolean {
    return kind >= 0 && kind < TILE_COUNT;
  }

  /** 同一种类的剩余张数上限恒为 4（红 5 也计入该种类） */
  static maxOfKind(): number {
    return 4;
  }

  /** 单张牌的数字部分，如 kind=4,red=true -> "0"；kind=27 -> "1" */
  static kindToDigits(kind: number, red = false): string {
    if (!Tile.isValidKind(kind)) return '?';
    const suit = Tile.suitOf(kind);
    if (suit === SUIT_Z) return String(kind - SUIT_BASES[suit] + 1);
    if (red) return '0';
    return String(kind - SUIT_BASES[suit] + 1);
  }

  /** 单张牌 -> 牌谱文本，如 kind=4,red=true -> "0m"；kind=27 -> "1z" */
  static kindToText(kind: number, red = false): string {
    if (!Tile.isValidKind(kind)) return '?';
    return Tile.kindToDigits(kind, red) + SUIT_CHARS[Tile.suitOf(kind)];
  }

  /** 牌谱文本 -> 展示文本，如 "1z" -> "东"、"5s" -> "5s"、"0m" -> "0m" */
  static kindToDisplay(kind: number, red = false): string {
    if (!Tile.isValidKind(kind)) return '?';
    const suit = Tile.suitOf(kind);
    if (suit === SUIT_Z) return HONOR_NAMES[kind - SUIT_BASES[suit]];
    return Tile.kindToDigits(kind, red) + SUIT_CHARS[suit];
  }

  /**
   * 解析牌谱文本，如 "34568m235p68s"；0m/0p/0s 表示红宝牌。
   * 语法非法时返回空数组并 console.error。
   */
  static parse(text: string): Tile[] {
    const out: Tile[] = [];
    let digits = '';
    const trimmed = text.trim();

    for (const ch of trimmed) {
      const suit = SUIT_CHARS.indexOf(ch as (typeof SUIT_CHARS)[number]);
      if (suit >= 0) {
        const base = SUIT_BASES[suit];
        if (digits.length === 0) {
          console.error(`Tile.parse: 花色 '${ch}' 前缺少数字：${text}`);
          return [];
        }
        for (const d of digits) {
          const n = d.charCodeAt(0) - 48;
          if (suit === SUIT_Z) {
            if (n < 1 || n > 7) {
              console.error(`Tile.parse: 字牌只能是 1-7：${text}`);
              return [];
            }
            out.push(new Tile(base + n - 1, false));
          } else if (n === 0) {
            out.push(new Tile(base + 4, true));
          } else if (n >= 1 && n <= 9) {
            out.push(new Tile(base + n - 1, false));
          } else {
            console.error(`Tile.parse: 数字超出 1-9：${text}`);
            return [];
          }
        }
        digits = '';
      } else if (ch >= '0' && ch <= '9') {
        digits += ch;
      } else {
        console.error(`Tile.parse: 非法字符 '${ch}'：${text}`);
        return [];
      }
    }

    if (digits.length > 0) {
      console.error(`Tile.parse: 数字后缺少花色：${text}`);
      return [];
    }
    return out;
  }

  /**
   * 手牌 -> 牌谱文本（按花色分组、种类升序、同种类红 5 在前），
   * 如 "123m056m789m"
   */
  static toText(tiles: Tile[]): string {
    const grouped: Tile[][] = [[], [], [], []];
    for (const t of tiles) grouped[Tile.suitOf(t.kind)].push(t);

    const parts: string[] = [];
    for (let suit = 0; suit < 4; suit++) {
      const group = grouped[suit];
      if (group.length === 0) continue;
      group.sort((a, b) => {
        if (a.kind !== b.kind) return a.kind - b.kind;
        if (a.red === b.red) return 0;
        return a.red ? -1 : 1;
      });
      let s = '';
      for (const t of group) s += Tile.kindToDigits(t.kind, t.red);
      parts.push(s + SUIT_CHARS[suit]);
    }
    return parts.join('');
  }

  /** 34 种牌的计数数组（红 5 自动并入普通 5） */
  static countsOf(tiles: Tile[]): number[] {
    const counts = new Array<number>(TILE_COUNT).fill(0);
    for (const t of tiles) {
      if (Tile.isValidKind(t.kind)) counts[t.kind] += 1;
    }
    return counts;
  }

  /** 归一化：红 5 与普通 5 是同一种类，算法层面直接比较 kind 即可。 */
  static sameKind(a: Tile, b: Tile): boolean {
    return a.kind === b.kind;
  }
}
