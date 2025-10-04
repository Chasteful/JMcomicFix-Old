<script lang="ts">
    import {onMount, createEventDispatcher, onDestroy} from 'svelte';
    import Cat from './Cat.svelte';
    import Bullet from './Bullet.svelte';
    import { Howl } from 'howler';
    import type { Cat as CatType, Bullet as BulletType, Doro } from './gameTypes';

    const dispatch = createEventDispatcher<{
        gameStart: void;
        catHit: { catId: number };
        gameOver: { reason: string };
    }>();

    export let gameStarted = false;
    export let gameOver = false;
    let cats: CatType[] = [];
    let bullets: BulletType[] = [];
    let nextId = 1;
    let doro: Doro = {
        id: 0, x: window.innerWidth/2, y: window.innerHeight-100,
        dir: 1, speed: 300
    };

    let startCat = {
        x: window.innerWidth / 2,
        y: window.innerHeight / 2,
        dirX: Math.random() > 0.5 ? 1 : -1,
        dirY: Math.random() > 0.5 ? 1 : -1,
        speed: 120,
        evadeSpeed: 500,
        width: 165,
        isEvading: false
    };
    let mouseX = 0;
    let mouseY = 0;
    let bgmPlayed = false;
    const HEIGHT = window.innerHeight;
    const COLLISION_DISTANCE = 30;
    const KNOCKBACK_DISTANCE = 23.3;
    const BULLET_SPEED = 600;
    const CAT_BASE_SPEED_X = 40;
    const CAT_BASE_SPEED_Y = 20;

    export function reset() {
        cats = [];
        bullets = [];
        gameOver = false;
        gameStarted = false;
        nextId = 1;

        doro = {
            id: 0, x: window.innerWidth/2, y: window.innerHeight-100,
            dir: 1, speed: 300
        };
    }

    const hitSound = new Howl
    ({ src: ['audio/games/anvil.mp3'], volume: 0.8 });
    const hitdoroSound = new Howl({
        src: ['audio/games/gameover.ogg'],
        preload: true,
        volume: 0.7,
    });
    const startSound= new Howl
    ({ src: ['audio/games/bgm.mp3'],preload: true, volume: 0.6 ,  loop: true });

    function tryStartGame(e: MouseEvent) {
        if (!bgmPlayed) {
            startSound.stop();
            startSound.loop(true);
            startSound.play();
            bgmPlayed = true;
        }

        const isHit = Math.abs(e.clientX - startCat.x) < startCat.width / 2 &&
            Math.abs(e.clientY - startCat.y) < startCat.width / 2;

        if (isHit) {
            gameStarted = true;
            dispatch('gameStart');
        }
    }

    function updateDoro(dt: number) {
        doro.x += doro.speed * doro.dir * dt;
        if (doro.x < 0 || doro.x > window.innerWidth) {
            doro.dir *= -1;
            doro.x = Math.max(0, Math.min(window.innerWidth, doro.x));
        }
    }

    function updateCats(dt: number) {
        cats = cats.map(c => {
            if (Math.random() < 0.02) c.dir *= -1;
            let nx = c.x + c.speedX * c.dir * dt;
            if (nx < 0 || nx > window.innerWidth) { c.dir *= -1; nx = Math.max(0, Math.min(window.innerWidth, nx)); }
            return { ...c, x: nx, y: c.y + c.speedY * dt };
        });
    }


    export function stopBGM() {
        startSound.stop();
        bgmPlayed = false; // 重置播放状态
    }
    export function StartBGM() {
        startSound.play();
        bgmPlayed = true;
    }
     function updateStartCat(delta: number) {
        const speed = startCat.isEvading ? startCat.evadeSpeed : startCat.speed;

         if (startCat.isEvading) {

             let dx = startCat.x - mouseX;
             let dy = startCat.y - mouseY;


             const margin = 100;
             const centerX = window.innerWidth / 2;
             const centerY = window.innerHeight / 2;

             if (startCat.x < margin) dx += (centerX - startCat.x) * 0.8;
             if (startCat.x > window.innerWidth - margin) dx += (centerX - startCat.x) * 0.8;
             if (startCat.y < margin) dy += (centerY - startCat.y) * 0.8;
             if (startCat.y > window.innerHeight - margin) dy += (centerY - startCat.y) * 0.8;

             // 归一化方向
             const len = Math.hypot(dx, dy) || 1;
             startCat.dirX = dx / len;
             startCat.dirY = dy / len;
         }

        else if (Math.random() < 0.02) {
            startCat.dirX = Math.random() > 0.5 ? 1 : -1;
            startCat.dirY = Math.random() > 0.5 ? 1 : -1;
        }


        if (startCat.x < 0 || startCat.x > window.innerWidth - startCat.width) {
            startCat.dirX *= -1;
            startCat.x = Math.max(0, Math.min(window.innerWidth - startCat.width, startCat.x));
        }
        if (startCat.y < 0 || startCat.y > window.innerHeight - startCat.width) {
            startCat.dirY *= -1;
            startCat.y = Math.max(0, Math.min(window.innerHeight - startCat.width, startCat.y));
        }


        startCat.x += startCat.dirX * speed * delta;
        startCat.y += startCat.dirY * speed * delta;
    }

    function updateBullets(dt: number) {
        bullets = bullets
            .map(b => ({ ...b, y: b.y - BULLET_SPEED * dt }))
            .filter(b => b.y > -50);
    }

    function distance(a: {x:number,y:number}, b: {x:number,y:number}) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }


    function handleGlobalMouseMove(e: MouseEvent) {
        mouseX = e.clientX;
        mouseY = e.clientY;

        if (!gameStarted) {
            const dist = Math.hypot(startCat.x - mouseX, startCat.y - mouseY);
            startCat.isEvading = dist < 200;
        }
    }
    function checkCollisions() {
        const toRemoveBullets = new Set<number>();
        const toRemoveCats = new Set<number>();
        const updatedCats: CatType[] = [];


        bullets.forEach((b) => {
            cats.forEach((c, index) => {
                if (distance(c, b) < COLLISION_DISTANCE) {
                    toRemoveBullets.add(b.id);


                    const knockedBackCat = {
                        ...c,
                        health: c.health - 1,
                        isHit: true,
                        y: c.y - Math.abs(KNOCKBACK_DISTANCE),
                        x: c.x + (Math.random() > 0.5 ? KNOCKBACK_DISTANCE : -KNOCKBACK_DISTANCE)
                    };

                    if (knockedBackCat.health <= 0) {
                        toRemoveCats.add(knockedBackCat.id);
                        dispatch('catHit', { catId: knockedBackCat.id });
                    } else {
                        dispatch('catHit', {
                            catId: knockedBackCat.id,
                            remainingHealth: knockedBackCat.health
                        });
                        updatedCats.push(knockedBackCat);
                    }

                    hitSound.play();
                    cats[index] = knockedBackCat;
                }
            });
        });


        function isDoroHit(b: BulletType) {
            const DORO_W = 60;
            const DORO_H = 60;

            const hw = DORO_W / 2;
            const hh = DORO_H / 2;

            const bw = 15;
            const bh = 15;

            return (
                Math.abs(b.x - doro.x) < hw + bw &&
                Math.abs(b.y - doro.y) < hh + bh
            );
        }

        bullets.forEach((b) => {
            if (isDoroHit(b)) {
                gameOver = true;
                dispatch('gameOver', { reason: 'doroHit' });
                hitdoroSound.play();
            }
        });


        bullets = bullets.filter(b => !toRemoveBullets.has(b.id));


        cats = cats
            .filter(c => !toRemoveCats.has(c.id))
            .map(c => updatedCats.find(u => u.id === c.id) || c);

        if (cats.some(c => c.y > HEIGHT - 100)) {
            gameOver = true;
            dispatch('gameOver', { reason: 'catEscaped' });
        }
    }

    // API
    export function spawnCat(x:number,y:number) {
        cats = [...cats, { id: nextId++, x,y, dir: Math.random()>0.5?1:-1,
            health:3, speedX:CAT_BASE_SPEED_X + Math.random()*40,
            speedY:CAT_BASE_SPEED_Y + Math.random()*20, isHit:false }];
    }
    export function fireBullet(x: number, y: number) {
        const bullet: BulletType = {
            id: nextId++,
            x: x,
            y: y,
            dirX: 0,
            dirY: -1
        };
        bullets = [...bullets, bullet];
    }

    let last = performance.now();function loop() {
        const now = performance.now();
        const dt = (now - last)/1000;
        last = now;

        if (!gameStarted) {
            updateStartCat(dt);
        } else if (!gameOver) {
            updateDoro(dt);
            updateCats(dt);

                updateBullets(dt);
                setTimeout(() => {
                }, 1000);


            checkCollisions();
        }
        requestAnimationFrame(loop);
    }
    onMount(() => {

        window.addEventListener('mousemove', handleGlobalMouseMove);
        loop();
        return () => window.removeEventListener('mousemove', handleGlobalMouseMove);
    });
    onDestroy(() =>{
        stopBGM()
        }
    )
</script>

<style>
    .entities { position:absolute; top:0; left:0; width:100vw; height:100vh; }
</style>
<div class="entities">
    {#if !gameStarted}
        <img
                class="start-cat"
                src="img/gif/cat.gif"
                alt="开始游戏"
                style="
        position: absolute;
        left: {startCat.x}px;
        top: {startCat.y}px;
        width: {startCat.width}px;
    "
                on:click={tryStartGame}
        />

    {:else}
        <!-- 游戏实体 -->
        {#each cats as cat (cat.id)}
            <Cat
                    id={cat.id}
                    x={cat.x}
                    y={cat.y}
                    health={cat.health}
                    gameOver={gameOver}
            />
        {/each}


        {#each bullets as bullet (bullet.id)}
            <Bullet id={bullet.id} x={bullet.x} y={bullet.y} />
        {/each}

        <img
                src="img/gif/doro.gif"
                alt="Doro"
                style="
        position: absolute;
        left: {doro.x -15}px;
        bottom: 100px;
        width: 60px;
        pointer-events: none;
    "
        />
    {/if}
</div>
