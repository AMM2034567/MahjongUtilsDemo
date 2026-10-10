import * as THREE from 'three';
import { Easing, Group, Tween } from '@tweenjs/tween.js';
import { Tile } from '../core/tile';
import { createTileFaceTexture } from './tileTexture';

/**
 * 纯代码构建的 3D 牌桌场景（俯视倾斜透视视角，参考雀魂）。
 *
 * - Scene + PerspectiveCamera + WebGLRenderer
 * - AmbientLight + DirectionalLight 提供立体感
 * - PlaneGeometry 桌面 + BoxGeometry 麻将牌（CanvasTexture 牌面）
 * - Raycaster 监听点击手牌
 * - Tween.js 驱动飞牌动画
 */

/** 牌块尺寸比例 1.2 : 1.6 : 0.6（宽 : 长 : 厚） */
export const TILE_W = 1.2;
export const TILE_H = 0.6;
export const TILE_L = 1.6;

const HAND_Z = 5.0;
const HAND_SPACING = 1.34;
const TILT = 0.18;

export type Seat = 0 | 1 | 2 | 3;
export type HandClickHandler = (index: number, tile: Tile) => void;

interface TileVisual {
  mesh: THREE.Mesh;
  tile: Tile;
}

export class GameBoard3D {
  readonly scene = new THREE.Scene();
  readonly camera: THREE.PerspectiveCamera;
  readonly renderer: THREE.WebGLRenderer;

  private readonly container: HTMLElement;
  private readonly raycaster = new THREE.Raycaster();
  private readonly pointer = new THREE.Vector2();
  private readonly tweenGroup = new Group();

  private readonly handGroup = new THREE.Group();
  private readonly rivers: TileVisual[][] = [[], [], [], []];
  private hand: TileVisual[] = [];

  private onHandClick: HandClickHandler | null = null;
  private busy = false;
  private rafId = 0;
  private readonly disposeFns: Array<() => void> = [];

  constructor(container: HTMLElement) {
    this.container = container;

    this.camera = new THREE.PerspectiveCamera(46, 1, 0.1, 200);
    this.camera.position.set(0, 11.5, 11.0);
    this.camera.lookAt(0, 0, 0.6);

    this.renderer = new THREE.WebGLRenderer({
      antialias: true,
      canvas: container instanceof HTMLCanvasElement ? container : undefined,
      alpha: false,
    });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.setClearColor(0x101820, 1);

    if (!(container instanceof HTMLCanvasElement)) {
      container.appendChild(this.renderer.domElement);
    }
    this.renderer.domElement.style.width = '100%';
    this.renderer.domElement.style.height = '100%';

    this.buildLights();
    this.buildTable();
    this.scene.add(this.handGroup);

    this.resize();
    const onResize = () => this.resize();
    window.addEventListener('resize', onResize);
    this.disposeFns.push(() => window.removeEventListener('resize', onResize));

    const canvas = this.renderer.domElement;
    const onPointerDown = (event: PointerEvent) => this.handlePointer(event);
    canvas.addEventListener('pointerdown', onPointerDown);
    this.disposeFns.push(() => canvas.removeEventListener('pointerdown', onPointerDown));

    const tick = () => {
      this.rafId = requestAnimationFrame(tick);
      this.tweenGroup.update();
      this.renderer.render(this.scene, this.camera);
    };
    tick();
  }

  // ------------------------------------------------------------------ 场景

  private buildLights(): void {
    const ambient = new THREE.AmbientLight(0xffffff, 1.1);
    this.scene.add(ambient);

    const key = new THREE.DirectionalLight(0xfff4e0, 2.2);
    key.position.set(7, 16, 9);
    this.scene.add(key);

    const fill = new THREE.DirectionalLight(0xbcd4ff, 0.9);
    fill.position.set(-9, 10, -6);
    this.scene.add(fill);
  }

  private buildTable(): void {
    const feltMaterial = new THREE.MeshStandardMaterial({
      color: 0x1d4b6b,
      roughness: 0.92,
      metalness: 0.0,
    });
    const felt = new THREE.Mesh(new THREE.PlaneGeometry(17, 17), feltMaterial);
    felt.rotation.x = -Math.PI / 2;
    felt.position.y = 0;
    this.scene.add(felt);

    const frameMaterial = new THREE.MeshStandardMaterial({
      color: 0x2b2118,
      roughness: 0.7,
      metalness: 0.05,
    });
    const frame = new THREE.Mesh(new THREE.BoxGeometry(18.4, 0.5, 18.4), frameMaterial);
    frame.position.y = -0.27;
    this.scene.add(frame);

    const innerMaterial = new THREE.MeshStandardMaterial({
      color: 0x16303f,
      roughness: 0.95,
    });
    const inner = new THREE.Mesh(new THREE.BoxGeometry(11.4, 0.06, 11.4), innerMaterial);
    inner.position.y = 0.03;
    this.scene.add(inner);

    const hub = new THREE.Mesh(
      new THREE.CylinderGeometry(1.5, 1.5, 0.12, 48),
      new THREE.MeshStandardMaterial({ color: 0x0f1b24, roughness: 0.5, metalness: 0.2 }),
    );
    hub.position.y = 0.07;
    this.scene.add(hub);
  }

  // ------------------------------------------------------------------ 牌

  /** 创建一块 3D 麻将牌（BoxGeometry + CanvasTexture 牌面） */
  createTileMesh(kind: number, red: boolean): THREE.Mesh {
    const geometry = new THREE.BoxGeometry(TILE_W, TILE_H, TILE_L);
    const faceMaterial = new THREE.MeshStandardMaterial({
      map: createTileFaceTexture(kind, red),
      roughness: 0.5,
      metalness: 0.0,
    });
    const sideMaterial = new THREE.MeshStandardMaterial({
      color: 0xf4ead2,
      roughness: 0.6,
      metalness: 0.0,
    });
    const backMaterial = new THREE.MeshStandardMaterial({
      color: 0xe0a24a,
      roughness: 0.65,
      metalness: 0.0,
    });

    // BoxGeometry 材质顺序：+x, -x, +y, -y, +z, -z
    const mesh = new THREE.Mesh(geometry, [
      sideMaterial,
      sideMaterial,
      faceMaterial,
      backMaterial,
      sideMaterial,
      sideMaterial,
    ]);
    mesh.userData = { kind, red };
    return mesh;
  }

  private createTileObject(tile: Tile): TileVisual {
    const mesh = this.createTileMesh(tile.kind, tile.red);
    return { mesh, tile };
  }

  // ------------------------------------------------------------------ 手牌

  setHand(tiles: Tile[]): void {
    for (const visual of this.hand) this.disposeMesh(visual.mesh);
    this.hand = [];
    this.handGroup.clear();

    const n = tiles.length;
    const totalWidth = (n - 1) * HAND_SPACING;
    tiles.forEach((tile, index) => {
      const visual = this.createTileObject(tile);
      visual.mesh.position.set(-totalWidth / 2 + index * HAND_SPACING, TILE_L / 2, HAND_Z);
      visual.mesh.rotation.set(Math.PI / 2 - TILT, 0, 0);
      visual.mesh.userData.handIndex = index;
      this.handGroup.add(visual.mesh);
      this.hand.push(visual);
    });
  }

  getHand(): Tile[] {
    return this.hand.map((v) => v.tile);
  }

  setOnHandClick(handler: HandClickHandler | null): void {
    this.onHandClick = handler;
  }

  isBusy(): boolean {
    return this.busy;
  }

  private handlePointer(event: PointerEvent): void {
    if (this.busy || !this.onHandClick) return;
    const rect = this.renderer.domElement.getBoundingClientRect();
    this.pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    this.pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;

    this.raycaster.setFromCamera(this.pointer, this.camera);
    const meshes = this.hand.map((v) => v.mesh);
    const hits = this.raycaster.intersectObjects(meshes, false);
    if (hits.length === 0) return;

    const mesh = hits[0].object;
    const index = this.hand.findIndex((v) => v.mesh === mesh);
    if (index < 0) return;
    this.onHandClick(index, this.hand[index].tile);
  }

  // ------------------------------------------------------------------ 牌河

  private riverSlot(seat: Seat, index: number): { position: THREE.Vector3; rotationY: number } {
    const col = index % 6;
    const row = Math.floor(index / 6);
    const xStep = 1.36;
    const zStep = 1.82;

    if (seat === 0) {
      return {
        position: new THREE.Vector3(-3.4 + col * xStep, TILE_H / 2, 2.6 - row * zStep),
        rotationY: 0,
      };
    }
    if (seat === 2) {
      return {
        position: new THREE.Vector3(3.4 - col * xStep, TILE_H / 2, -2.6 + row * zStep),
        rotationY: Math.PI,
      };
    }
    const sign = seat === 1 ? 1 : -1;
    return {
      position: new THREE.Vector3(sign * 3.6, TILE_H / 2, -2.6 + col * zStep),
      rotationY: (sign * Math.PI) / 2,
    };
  }

  private seatHandPosition(seat: Seat, offset = 0): THREE.Vector3 {
    switch (seat) {
      case 0:
        return new THREE.Vector3(offset, TILE_L / 2, HAND_Z);
      case 1:
        return new THREE.Vector3(HAND_Z, TILE_L / 2, offset);
      case 2:
        return new THREE.Vector3(-offset, TILE_L / 2, -HAND_Z);
      default:
        return new THREE.Vector3(-HAND_Z, TILE_L / 2, -offset);
    }
  }

  private seatHandRotation(seat: Seat): THREE.Euler {
    const sign = seat === 0 || seat === 2 ? 1 : -1;
    const x = seat === 0 || seat === 2 ? (sign * Math.PI) / 2 - TILT : -TILT;
    const y = (seat * Math.PI) / 2;
    return new THREE.Euler(x, y, 0);
  }

  /** 把指定座位的手牌（handIndex）或新摸的牌飞到牌河区并躺平 */
  async discard(seat: Seat, tile: Tile, handIndex = -1): Promise<void> {
    this.busy = true;
    try {
      let visual: TileVisual;
      if (seat === 0) {
        const index = handIndex >= 0 ? handIndex : this.hand.findIndex((v) => v.tile === tile);
        const removed = this.hand[index];
        if (!removed) return;
        visual = removed;
        this.hand.splice(index, 1);
        this.reindexHand();
      } else {
        visual = this.createTileObject(tile);
        visual.mesh.position.copy(this.seatHandPosition(seat));
        visual.mesh.rotation.copy(this.seatHandRotation(seat));
        this.scene.add(visual.mesh);
      }

      this.rivers[seat].push(visual);
      const slot = this.riverSlot(seat, this.rivers[seat].length - 1);

      await this.fly(visual.mesh, slot.position, slot.rotationY, 620);
    } finally {
      this.busy = false;
    }
  }

  private fly(mesh: THREE.Mesh, target: THREE.Vector3, targetRotY: number, duration: number) {
    return new Promise<void>((resolve) => {
      const from = mesh.position.clone();
      const fromRotX = mesh.rotation.x;
      const fromRotY = mesh.rotation.y;
      const state = { t: 0 };

      new Tween(state, this.tweenGroup)
        .to({ t: 1 }, duration)
        .easing(Easing.Cubic.InOut)
        .onUpdate(() => {
          const t = state.t;
          mesh.position.lerpVectors(from, target, t);
          mesh.position.y = THREE.MathUtils.lerp(from.y, target.y, t) + Math.sin(Math.PI * t) * 2.2;
          mesh.rotation.x = THREE.MathUtils.lerp(fromRotX, 0, t);
          mesh.rotation.y = THREE.MathUtils.lerp(fromRotY, targetRotY, t);
        })
        .onComplete(() => {
          mesh.position.copy(target);
          mesh.rotation.set(0, targetRotY, 0);
          resolve();
        })
        .start();
    });
  }

  private reindexHand(): void {
    this.hand.forEach((visual, index) => {
      visual.mesh.userData.handIndex = index;
    });
    this.layoutHand();
  }

  private layoutHand(): void {
    const n = this.hand.length;
    const totalWidth = (n - 1) * HAND_SPACING;
    this.hand.forEach((visual, index) => {
      const targetX = -totalWidth / 2 + index * HAND_SPACING;
      const from = visual.mesh.position.clone();
      const to = new THREE.Vector3(targetX, TILE_L / 2, HAND_Z);
      const state = { t: 0 };
      new Tween(state, this.tweenGroup)
        .to({ t: 1 }, 240)
        .easing(Easing.Quadratic.Out)
        .onUpdate(() => {
          visual.mesh.position.lerpVectors(from, to, state.t);
        })
        .onComplete(() => visual.mesh.position.copy(to))
        .start();
    });
  }

  // ------------------------------------------------------------------ 其它

  resize(): void {
    const width = this.container.clientWidth || window.innerWidth;
    const height = this.container.clientHeight || window.innerHeight;
    this.camera.aspect = width / Math.max(height, 1);
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height, false);
  }

  private disposeMesh(mesh: THREE.Mesh): void {
    mesh.removeFromParent();
    mesh.geometry.dispose();
    const materials = Array.isArray(mesh.material) ? mesh.material : [mesh.material];
    for (const material of materials) material.dispose();
  }

  dispose(): void {
    cancelAnimationFrame(this.rafId);
    for (const fn of this.disposeFns) fn();
    for (const visual of this.hand) this.disposeMesh(visual.mesh);
    for (const river of this.rivers) for (const visual of river) this.disposeMesh(visual.mesh);
    this.handGroup.clear();
    this.tweenGroup.removeAll();
    this.renderer.dispose();
  }
}
