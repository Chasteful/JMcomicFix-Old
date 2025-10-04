<script lang="ts">
    export let id: number;
    export let x: number;
    export let y: number;
    export let health: number = 3;
    export let isHit: boolean = false;
    export let gameOver: boolean = false;
    let currentSrc = 'img/gif/cat.gif';
    const winSrc = 'img/gif/winCat.png';
    let rotation = 0;
    let scaleX = 1;
    let filter = '';
    let isDying = false;


    function handleAttack() {
        if  (health <= 0 && !isDying) {
            isDying = true;
            currentSrc = 'img/attacked.png';
            filter = 'brightness(1.5) hue-rotate(300deg)';
        }
    }

    $: if (isHit) {
        handleAttack();

        setTimeout(() => isHit = false, 0);
    }

    $: if (gameOver) {
        currentSrc = winSrc;
    } else {
        currentSrc = 'img/gif/cat.gif';
    }
</script>

<style lang="scss">

  .cat {
    position: absolute;
    width: 120px;
    height: auto;
    pointer-events: none;
    user-select: none;
    transition:
            transform 0.1s linear,
            filter 0.3s ease-out;
    transform-origin: center;
  }

  .dying {
    transition: all 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  }
</style>

<img
        class="cat {isDying ? 'dying' : ''}"
        src={currentSrc}
        alt="cat"
        style="transform: translate({x}px, {y}px) scaleX({scaleX}) rotate({rotation}deg); filter: {filter};"
/>
