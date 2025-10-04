<script lang="ts">
    import type {ItemStack} from "../../../../integration/types";
    import {listen} from "../../../../integration/ws";
    import type {ClientPlayerInventoryEvent, PlayerInventory} from "../../../../integration/events";
    import {onMount} from "svelte";
    import {getPlayerInventory} from "../../../../integration/rest";
    import {fly} from "svelte/transition";
    import {expoInOut} from "svelte/easing";
    import ItemStackDisplay from "../../common/ItemView/ItemStackDisplay.svelte";

    let stacks: ItemStack[] = [];

    function updateStacks(inventory: PlayerInventory) {
        stacks = inventory.crafting;
    }

    listen("clientPlayerInventory", (data: ClientPlayerInventoryEvent) => {
        updateStacks(data.inventory);
    });
    onMount(async () => {
        const inventory = await getPlayerInventory();
        updateStacks(inventory);
    });

</script>

<div class="container hud-container" transition:fly|global={{duration: 500, y: -50, easing: expoInOut}}>
    {#each stacks as stack (stack)}
        <ItemStackDisplay
                {stack}
                dyedMode={true}
                showDurability={stack.damage > 0}
                durabilityColorMode={true}
        />
    {/each}
</div>
<style lang="scss">
  @use "../../../../colors" as *;

  .container {
    grid-template-columns: repeat(2, 1fr);
    padding: 4px;
    min-width: 72px;
    min-height: 72px;
    display: grid;
    gap: 0.5rem;
  }

</style>
