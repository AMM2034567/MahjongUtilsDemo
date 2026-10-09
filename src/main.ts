import './style.css';
import { Tile } from './core/tile';
import { INVALID, shanten } from './core/shanten';
import { build, deal, HAND_SIZE } from './core/wall';
import { GameBoard3D } from './scene/GameBoard3D';

function el<T extends HTMLElement>(id: string): T {
  const node = document.getElementById(id);
  if (!node) throw new Error(`元素不存在：#${id}`);
  return node as T;
}

const roundValue = el<HTMLSpanElement>('round-value');
const scoreValue = el<HTMLSpanElement>('score-value');
const shantenValue = el<HTMLSpanElement>('shanten-value');
const hintText = el<HTMLSpanElement>('hint-text');
const canvas = el<HTMLCanvasElement>('board-canvas');

const board = new GameBoard3D(canvas);

interface GameState {
  wall: Tile[];
  playerHand: Tile[];
  aiHand: Tile[];
  playerRiverCount: number;
  aiRiverCount: number;
  round: number;
  score: number;
}

function sortHand(tiles: Tile[]): void {
  tiles.sort((a, b) => {
    if (a.kind !== b.kind) return a.kind - b.kind;
    if (a.red === b.red) return 0;
    return a.red ? -1 : 1;
  });
}

function newGame(): GameState {
  const { hands, wall } = deal(build());
  const playerHand = hands[0];
  const aiHand = hands[2];
  const drawn = wall.shift();
  if (drawn) playerHand.push(drawn);
  sortHand(playerHand);

  return {
    wall,
    playerHand,
    aiHand,
    playerRiverCount: 0,
    aiRiverCount: 0,
    round: 1,
    score: 25000,
  };
}

let state = newGame();

function updateHud(): void {
  roundValue.textContent = `东${state.round}局`;
  scoreValue.textContent = String(state.score);

  const value = shanten(state.playerHand);
  shantenValue.textContent = value === INVALID ? '-' : String(Math.max(value, -1));
  hintText.textContent =
    state.playerHand.length > HAND_SIZE ? '点击底部手牌打出' : '等待摸牌…';
}

function draw(): void {
  const drawn = state.wall.shift();
  if (!drawn) return;
  state.playerHand.push(drawn);
  sortHand(state.playerHand);
}

async function playerDiscard(index: number, tile: Tile): Promise<void> {
  if (board.isBusy() || state.playerHand.length <= HAND_SIZE) return;

  hintText.textContent = `打出 ${Tile.kindToText(tile.kind, tile.red)}`;
  await board.discard(0, tile, index);
  state.playerHand.splice(index, 1);
  state.playerRiverCount += 1;
  updateHud();

  // AI 摸牌后随机打出一张
  const aiDrawn = state.wall.shift();
  if (aiDrawn) state.aiHand.push(aiDrawn);
  if (state.aiHand.length > 0) {
    const aiIndex = Math.floor(Math.random() * state.aiHand.length);
    const aiTile = state.aiHand.splice(aiIndex, 1)[0];
    hintText.textContent = '对家切牌…';
    await board.discard(2, aiTile);
    state.aiRiverCount += 1;
  }

  // 玩家摸牌，开始下一轮
  draw();
  board.setHand(state.playerHand);
  updateHud();
  hintText.textContent = '点击底部手牌打出';
}

function bootstrap(): void {
  board.setHand(state.playerHand);
  board.setOnHandClick((index, tile) => {
    void playerDiscard(index, tile);
  });
  updateHud();

  el<HTMLButtonElement>('btn-pass').addEventListener('click', () => {
    hintText.textContent = '已跳过';
  });
  el<HTMLButtonElement>('btn-riichi').addEventListener('click', () => {
    hintText.textContent = '尚未听牌，无法立直';
  });
  el<HTMLButtonElement>('btn-win').addEventListener('click', () => {
    const value = shanten(state.playerHand);
    hintText.textContent = value <= 0 ? '和牌！' : `当前向听 ${value}，还不能和牌`;
  });
}

bootstrap();

// Capacitor/浏览器都会触发，供 HMR 使用
declare global {
  interface Window {
    __mahjong3d?: { board: GameBoard3D; state: () => GameState };
  }
}
window.__mahjong3d = { board, state: () => state };
