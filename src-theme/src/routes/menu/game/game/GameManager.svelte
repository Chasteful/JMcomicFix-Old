<script lang="ts">
    import { createEventDispatcher, onMount } from 'svelte';
    import GameEntities from './GameEntities.svelte';
    import { Howl } from "howler";

    const dispatch = createEventDispatcher();

    const SPAWN_INTERVAL = 1000;
    const WAVE_INTERVAL = 3000;
    let spawnTimer: NodeJS.Timeout;
    let waveTimer: NodeJS.Timeout;
    let gameEntities: GameEntities;
    let score = 0;
    let gameOver = false;
    let gameStarted = false;

    let gameOverReason = '';
    const gameOverSound = new Howl({
        src: ['audio/games/EZ4ence.mp3'],
        preload: true,
        volume: 0.7
    });
    const losersound = new Howl({
        src: ['audio/games/loser.mp3'],
        preload: true,
        volume: 0.7
    });

    function resetGame() {
        score = 0;
        gameOver = false;
        gameStarted = false;
        clearInterval(spawnTimer);
        clearInterval(waveTimer);
        gameEntities.stopBGM();
        gameEntities.reset();
    }

    function restartGame() {
        resetGame();
        gameEntities.reset();




        setTimeout(() => {
            gameStarted = true;
            handleGameStart();

            gameEntities.StartBGM();
        }, 100);
    }


    function exitGame() {
        resetGame();
        gameStarted = false;
        dispatch('exitGame');
    }

    function handleGameStart() {
        startRegularSpawn();
        startWaveSpawn();
    }

    function spawnSingleCat() {
        if (!gameStarted || gameOver) return;
        const x = Math.random() * window.innerWidth;
        gameEntities.spawnCat(x, -50);
    }

    function handleClick(e: MouseEvent) {
        if (gameStarted && !gameOver) {
            gameEntities.fireBullet(e.clientX, window.innerHeight);
        }
    }

    function startRegularSpawn() {
        if (spawnTimer) clearInterval(spawnTimer);
        spawnTimer = setInterval(() => {
            const count = 1 + Math.floor(Math.random() * 2);
            for (let i = 0; i < count; i++) {
                setTimeout(spawnSingleCat, i * 200);
            }
        }, SPAWN_INTERVAL);
    }

    function startWaveSpawn() {
        waveTimer = setInterval(() => {
            const count = 2 + Math.floor(Math.random() * 3);
            for (let i = 0; i < count; i++) {
                setTimeout(spawnSingleCat, i * 150);
            }
        }, WAVE_INTERVAL);
    }

    function handleCatHit(event: CustomEvent<{ catId: number }>) {
        score++;
    }

    function handleGameOver(event: CustomEvent<{ reason: string }>) {
        gameOver = true;
        gameOverReason = event.detail.reason;

        gameEntities.stopBGM();


        if (gameOverReason === 'doroHit') {
            gameOverSound.play();
        } else {
            losersound.play();
        }
    }
    onMount(() => {
        return () => {
            clearInterval(spawnTimer);
            clearInterval(waveTimer);
        };
    });
</script>

<div class="game-container"
     style=" width:200vw;"
     on:click={handleClick}>

    <GameEntities
            bind:this={gameEntities}
            bind:gameStarted
            bind:gameOver
            on:gameStart={handleGameStart}
            on:catHit={handleCatHit}
            on:gameOver={handleGameOver}
    />

    {#if gameStarted}
        <div class="score">Score: {score}</div>
        {#if gameOver}
            <div class="game-over">
                <h2>{gameOverReason === 'doroHit' ? '你误伤了Doro!' : 'FW Hacker'}</h2>
                <p>最终得分: {score}</p>
                <div class="buttons">
                    <button on:click={restartGame}>重开一把</button>
                    <button on:click={exitGame}>不玩了</button>
                </div>
            </div>
        {/if}
    {/if}
</div>

<style lang="scss">
  .game-container {
    position: relative;
    top: 0;
    left: 0;
    height: 100%;
    width: 100%;
  }

  .score {
    position: fixed;
    top: 1rem;
    left: 1rem;
    color: white;
    font-size: 1.5rem;
    text-shadow: 1px 1px 2px black;
    z-index: 100;
  }

  .game-over {
    position: fixed;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    background: rgba(0, 0, 0, 0.8);
    padding: 2rem;
    border-radius: 8px;
    text-align: center;
    color: white;
    z-index: 200;
    width: 80%;
    max-width: 400px;

    h2 {
      margin-bottom: 1rem;
      color: #ff5f5f;
    }

    p {
      font-size: 1.2rem;
      margin-bottom: 1.5rem;
    }

    .buttons {
      display: flex;
      justify-content: center;
      gap: 1rem;
    }

    button {
      padding: 0.75rem 1.5rem;
      font-size: 1rem;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      transition: all 0.2s;

      &:first-child {
        background: #4CAF50;
        color: white;

        &:hover {
          background: #45a049;
        }
      }

      &:last-child {
        background: #f44336;
        color: white;

        &:hover {
          background: #d32f2f;
        }
      }
    }
  }
</style>
