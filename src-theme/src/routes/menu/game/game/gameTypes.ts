export interface Entity {
    id: number;
    x: number;
    y: number;
}

export interface Cat extends Entity {
    dir: -1 | 1;
    health: number;
    speedX: number;
    speedY: number;
    isHit:boolean;
}

export interface Bullet extends Entity {
    dirX: number;
    dirY: number;
}

export interface Doro extends Entity {
    dir: -1 | 1;
    speed: number;
}
