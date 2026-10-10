import * as THREE from 'three';
import { HONOR_NAMES, SUIT_BASES, SUIT_CHARS, SUIT_Z, Tile } from '../core/tile';

/**
 * 用 CanvasTexture 动态绘制牌面文字，完全不依赖外部图片资源（离线安全）。
 */

const TEX_W = 120;
const TEX_H = 160;

const SUIT_COLORS = ['#b02a2a', '#1f5fa8', '#1d7a3e'];
const HONOR_COLORS = ['#1a1a1a', '#1a1a1a', '#1a1a1a', '#1a1a1a', '#1f5fa8', '#1d7a3e', '#c02a2a'];

const textureCache = new Map<string, THREE.CanvasTexture>();

export function createTileFaceTexture(kind: number, red: boolean): THREE.CanvasTexture {
  const key = `${kind}:${red ? 1 : 0}`;
  const cached = textureCache.get(key);
  if (cached) return cached;

  const canvas = document.createElement('canvas');
  canvas.width = TEX_W;
  canvas.height = TEX_H;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    throw new Error('无法创建 Canvas 2D 上下文');
  }

  // 底色
  const gradient = ctx.createLinearGradient(0, 0, 0, TEX_H);
  gradient.addColorStop(0, '#fbf7ec');
  gradient.addColorStop(1, '#efe6d0');
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, TEX_W, TEX_H);

  // 内边框
  ctx.strokeStyle = 'rgba(120, 100, 70, 0.35)';
  ctx.lineWidth = 3;
  ctx.strokeRect(6, 6, TEX_W - 12, TEX_H - 12);

  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';

  const suit = Tile.suitOf(kind);

  if (suit === SUIT_Z) {
    const honorIndex = kind - SUIT_BASES[SUIT_Z];
    const name = HONOR_NAMES[honorIndex];
    ctx.fillStyle = HONOR_COLORS[honorIndex] ?? '#1a1a1a';
    ctx.font = 'bold 86px "PingFang SC", "Microsoft YaHei", sans-serif';
    ctx.fillText(name, TEX_W / 2, TEX_H / 2 + 2);
  } else {
    const digit = Tile.kindToDigits(kind, red);
    ctx.fillStyle = red ? '#d22b2b' : '#1a1a1a';
    ctx.font = 'bold 74px "Trebuchet MS", Arial, sans-serif';
    ctx.fillText(digit, TEX_W / 2, 52);

    ctx.fillStyle = SUIT_COLORS[suit];
    ctx.font = 'bold 56px "PingFang SC", "Microsoft YaHei", sans-serif';
    ctx.fillText(SUIT_CHARS[suit] === 'm' ? '萬' : SUIT_CHARS[suit] === 'p' ? '筒' : '索', TEX_W / 2, 116);
  }

  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.anisotropy = 8;
  texture.needsUpdate = true;

  textureCache.set(key, texture);
  return texture;
}

export function disposeTileTextures(): void {
  for (const texture of textureCache.values()) texture.dispose();
  textureCache.clear();
}
