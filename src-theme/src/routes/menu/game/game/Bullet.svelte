<script lang="ts">

  export let id: number;
  export let x: number;
  export let y: number;
</script>

<style>
  .bullet {
    position: absolute;
    width: 30px;
    pointer-events: none;
    user-select: none;
  }
</style>

<img
  class="bullet"
  src="img/gif/tnt.png"
  alt="bullet"
  style="transform: translate({x}px, {y}px);"
/>
