<script lang="ts">
    import type {ConfigurableSetting, Module} from "../../integration/types";
    import {getModuleSettings, setModuleEnabled} from "../../integration/rest";
    import {listen} from "../../integration/ws";
    import type {ClickGuiValueChangeEvent, KeyboardKeyEvent, ModuleToggleEvent} from "../../integration/events";
    import {highlightModuleName, filteredModules, showSearch, query} from "./clickgui_store";
    import {convertToSpacedString, spaceSeperatedNames} from "../../theme/theme_config";
    import {shrinkOut} from "../../util/animate_utils";
    import {fly, slide} from "svelte/transition";
    import {quintOut} from 'svelte/easing';
    import {onMount, onDestroy, tick} from "svelte";
    import {showResults} from "./clickgui_store";
    import {options} from "./Placeholder";
    import {get} from "svelte/store";
    import {writable} from 'svelte/store';
    import {Howl} from "howler";

    export let modules: Module[];
    let isSearchFocused = false;
    let resultElements: HTMLElement[] = [];
    let searchContainerElement: HTMLElement;
    let searchInputElement: HTMLInputElement;
    let lastClickTime = 0;
    let localFiltered: Module[] = [];
    let selectedIndex = -1;
    let hasContent = false;
    let forcedShow = false;
    let autoFocus: boolean = true;
    let clickOutsideCount = 0;
    let placeholder = "";
    let showHistory = false;
    let isCialloActive = false;
    let interval: ReturnType<typeof setInterval>;

    $: if ($showSearch && autoFocus) {
        tick().then(() => {
            searchInputElement?.focus();
        });
    }
    $: if (!$showSearch) {
        $query = '';
    }

    function handleFocus() {
        placeholder = "Search (A-Z only)";

        if ($query.length > 0) {
            setTimeout(() => {
                if (document.activeElement === searchInputElement) {
                    (searchInputElement as HTMLInputElement).select();
                }
            }, 0);
        }
    }

    const recentPlaceholders: string[] = [];
    const maxRecent = 5;

    function removeHistoryItem(index: number) {
        searchHistory.update(history => {
            const newHistory = [...history];
            newHistory.splice(index, 1);
            return newHistory;
        });
    }

    const searchHistory = writable<string[]>([]);
    const maxHistoryItems = 10;

    function clearAllHistory() {
        searchHistory.set([]);
    }

    function toggleResultVisibility() {
        const hasQuery = $query.length > 0;
        const hasResults = get(filteredModules).length > 0;

        if (hasQuery) {
            addToHistory($query);
        }

        if (hasQuery && hasResults) {
            forcedShow = !forcedShow;
            showResults.set(true);
            showHistory = false;
        } else if (!hasQuery && !hasResults) {
            showResults.set(false);
            showHistory = false;
        } else {
            showHistory = !showHistory;
            showResults.set(false);
        }

        searchInputElement.focus();
    }

    function selectFromHistory(historyItem: string) {
        $query = historyItem;
        filterModules();
        showHistory = false;
        showResults.set(true);
        searchInputElement.focus();
    }

    function handleEnterKey() {
        if ($query.length > 0) {
            addToHistory($query);
            filterModules();
            showResults.set(true);
            showHistory = false;
        } else {
            showHistory = !showHistory;
            showResults.set(false);
        }
    }

    function addToHistory(searchTerm: string) {
        if (!searchTerm.trim()) return;

        searchHistory.update(history => {
            const newHistory = history.filter(item => item !== searchTerm);
            newHistory.unshift(searchTerm);
            return newHistory.slice(0, maxHistoryItems);
        });
    }

    function filterModules() {
        hasContent = $query.length > 0;
        const pureQuery = $query.toLowerCase();

        localFiltered = hasContent
            ? modules.filter(m =>
                m.name.toLowerCase().includes(pureQuery) ||
                m.aliases.some(a => a.toLowerCase().includes(pureQuery)))
            : [];

        $filteredModules = localFiltered;

        if (hasContent) {
            showHistory = false;
        }
    }

    function handleInput() {

        if (['0721', '1011010001', 'Ciallo~(∠・ω< )⌒★'].includes($query)) {
            $query = 'Ciallo~(∠・ω< )⌒★';
            isCialloActive = true;
            const temp = new Howl({
                src: ['audio/ciallo.ogg'],
                preload: true,
                onload: () => {
                    temp.play();
                },
            });
            return;
        }

        if (isCialloActive) {
            isCialloActive = false;
            $query = '';
        }

        filterModules();

        if ($query.length > 0) {
            showHistory = false;
        }

        placeholder = getWeightedRandomPlaceholder();
    }

    let lastArrowPressTime = 0;
    const allowedInputs = ['Ciallo~(∠・ω< )⌒★'];

    async function handleKeyDown(e: KeyboardKeyEvent) {
        const validatedQuery = $query.replace(/[^a-z0-9]/gi, '');
        if (validatedQuery !== $query && !allowedInputs.includes($query)) {
            $query = validatedQuery;
            return;
        }
        if (
            e.screen === undefined ||
            !e.screen.class.startsWith("net.ccbluex.liquidbounce") ||
            !(e.screen.title === "ClickGUI" || e.screen.title === "VS-CLICKGUI")
        ) {
            return;
        }

        if (e.key === "key.keyboard.enter" && $query.length > 0 && selectedIndex === -1) {
            handleEnterKey();
        }

        if ($filteredModules.length === 0 || e.action === 0) {
            return;
        }

        const now = Date.now();
        const isArrowKey = e.key === "key.keyboard.down" || e.key === "key.keyboard.up";

        if (isArrowKey && now - lastArrowPressTime < 100) {
            return; // 小于100ms时忽略
        }

        if (isArrowKey) {
            lastArrowPressTime = now;
        }

        switch (e.key) {
            case "key.keyboard.down":
                selectedIndex = (selectedIndex + 1) % localFiltered.length;
                break;
            case "key.keyboard.up":
                selectedIndex = (selectedIndex - 1 + localFiltered.length) % localFiltered.length;
                break;
            case "key.keyboard.enter":
                if (selectedIndex >= 0) {
                    await toggleModule(localFiltered[selectedIndex].name, !localFiltered[selectedIndex].enabled);
                }
                break;
            case "key.keyboard.tab":
                const m = localFiltered[selectedIndex]?.name;
                if (m) $highlightModuleName = m;
                break;
        }

        resultElements[selectedIndex]?.scrollIntoView({
            behavior: "smooth",
            block: "nearest",
        });
    }

    function handleWindowClick(e: MouseEvent) {
        if (!searchContainerElement) return;
        const clickedInside = searchContainerElement.contains(e.target as Node);

        if (clickedInside) {
            clickOutsideCount = 0;
            return;
        }

        const now = Date.now();
        if (now - lastClickTime < 300) {
            clickOutsideCount++;
        } else {
            clickOutsideCount = 1;
        }
        lastClickTime = now;

        if (clickOutsideCount >= 2) {
            showResults.set(false);
            forcedShow = false;
            showHistory = false;
            clickOutsideCount = 0;
        } else if (!hasContent) {
            showResults.set(false);
            showHistory = false;
        }
    }


    function handleWindowKeyDown() {
        if (document.activeElement !== document.body) {
            return;
        }

        if (autoFocus) {
            searchInputElement.focus();
        }
    }

    function handleBrowserKeyDown(e: KeyboardEvent) {

        const allowedKeys = /^[a-z0-9]$|^Arrow(Down|Up|Left|Right)$|^Tab$|^Enter$|^Backspace$|^Delete$/i;


        if (!allowedKeys.test(e.key)) {
            e.preventDefault();
            return;
        }
        if (e.key === "Enter") {
            handleEnterKey();
            e.preventDefault();
        }

        if (e.key === "ArrowDown" || e.key === "ArrowUp" || e.key === "Tab") {
            e.preventDefault();
        }
    }

    async function toggleModule(name: string, enabled: boolean) {
        await setModuleEnabled(name, enabled);
    }

    function applyValues(configurable: ConfigurableSetting) {
        autoFocus = configurable.value.find(v => v.name === "SearchBarAutoFocus")?.value as boolean ?? true;
    }

    function getWeightedRandomPlaceholder(): string {
        const totalWeight = options.reduce((sum, opt) => sum + opt.weight, 0);
        let tries = 10;

        while (tries--) {
            let random = Math.random() * totalWeight;

            for (const opt of options) {
                if (random < opt.weight) {
                    if (!recentPlaceholders.includes(opt.text)) {
                        recentPlaceholders.unshift(opt.text);
                        if (recentPlaceholders.length > maxRecent) {
                            recentPlaceholders.pop();
                        }
                        return opt.text;
                    }
                    break;
                }
                random -= opt.weight;
            }
        }


        const fallback = options[Math.floor(Math.random() * options.length)].text;
        recentPlaceholders.unshift(fallback);
        if (recentPlaceholders.length > maxRecent) recentPlaceholders.pop();
        return fallback;
    }

    onMount(() => {
        const fetchSettings = async () => {
            const clickGuiSettings = await getModuleSettings("ClickGUI");
            applyValues(clickGuiSettings);
            placeholder = getWeightedRandomPlaceholder();
            if (autoFocus) {
                searchInputElement.focus();
            }
        };
        fetchSettings();
        interval = setInterval(() => {
            if (searchInputElement?.matches && !searchInputElement.matches(':focus')) {
                placeholder = getWeightedRandomPlaceholder();
            }
        }, 5000);
        return () => clearInterval(interval);
    });

    onDestroy(() => {
        showResults.set(false);
        clearInterval(interval);
    });
    listen("moduleToggle", (e: ModuleToggleEvent) => {
        const mod = modules.find((m) => m.name === e.moduleName);
        if (mod) {
            mod.enabled = e.enabled;
            localFiltered = $filteredModules;
        }
    });

    listen("keyboardKey", handleKeyDown);

    listen("clickGuiValueChange", (e: ClickGuiValueChangeEvent) => {
        applyValues(e.configurable);
    });

</script>

<svelte:window on:click={handleWindowClick} on:contextmenu={handleWindowClick} on:keydown={handleWindowKeyDown}/>
{#if $showSearch}
    <!-- svelte-ignore a11y_no_static_element_interactions -->
    <div
            class="search"
            class:has-results={$showResults || showHistory}
            class:showing={isSearchFocused || $showResults || showHistory}
            on:mouseenter={() => isSearchFocused = true}
            on:mouseleave={() => isSearchFocused = false}
            bind:this={searchContainerElement}
            in:fly|global={{y: -30, duration: 200, easing: quintOut}}
            out:fly|global={{y: -30, duration: 200, easing: quintOut}}

    >
        <div class="input-wrapper" draggable="false"
             class:focus-visible={isSearchFocused}>

            <!-- svelte-ignore element_invalid_self_closing_tag -->
            <button
                    class="search-icon"
                    class:active={$showResults}
                    class:glow={isSearchFocused}
                    aria-label="Search"
                    on:click={toggleResultVisibility}
            />
            <input
                    class="search-input"
                    bind:value={$query}
                    bind:this={searchInputElement}
                    on:focus={() => isSearchFocused = true}
                    on:focus={handleFocus}
                    on:blur={() => isSearchFocused = false}
                    on:input={filterModules}
                    on:input={handleInput}
                    on:keydown={handleBrowserKeyDown}
                    placeholder={placeholder}
                    spellcheck="false"
            />

        </div>

        <!-- 搜索结果 -->
        {#if $showResults}
            <div class="results" in:fly={{ y: 16, duration: 100, easing: quintOut }} out:shrinkOut>
                {#each localFiltered as {name, enabled, aliases}, index (name)}
                    <div
                            class="result"
                            class:enabled
                            class:selected={selectedIndex === index}
                            class:first={index === 0}
                            class:last={index === localFiltered.length - 1}
                            role="button"
                            tabindex="0"
                            in:slide={{ duration: 300, easing: quintOut }}
                            out:slide={{ duration: 200 ,easing: quintOut }}
                            on:click={() => toggleModule(name, !enabled)}
                            on:keydown={(e) => {if (e.key === 'Enter' || e.key === ' ') {e.preventDefault(); toggleModule(name, !enabled);  }}}
                            on:contextmenu|preventDefault={() => $highlightModuleName = name}
                            bind:this={resultElements[index]}
                    >
                        <div class="module-name">
                            {$spaceSeperatedNames ? convertToSpacedString(name) : name}
                        </div>
                        {#if aliases.length > 0}
                            <div class="aliases">
                                (aka {aliases.map(a => $spaceSeperatedNames ? convertToSpacedString(a) : a).join(", ")})
                            </div>
                        {/if}
                    </div>
                {/each}
            </div>

            <!-- 历史记录 -->
        {:else if showHistory}
            <div class="history-results" in:fly={{ y: 16, duration: 100, easing: quintOut }} out:shrinkOut>
                <div class="history-header">
                    <div class="title">
                        <span>Recent Searches</span>
                    </div>
                    {#if $searchHistory.length > 0}
                        <button class="clear-all" on:click|stopPropagation={clearAllHistory}>
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                                <path d="M19 6L6 19M6 6l13 13" stroke="currentColor" stroke-width="2"
                                      stroke-linecap="round"/>
                            </svg>
                            <span>全部清除</span>
                        </button>
                    {/if}
                </div>
                {#each $searchHistory as historyItem, index (index)}
                    <div
                            class="history-item"
                            class:first={index === 0}
                            class:last={index === $searchHistory.length - 1}
                            role="button"
                            tabindex="0"
                            on:click={() => selectFromHistory(historyItem)}
                            on:keydown={(e) => {if (e.key === 'Enter' || e.key === ' ') {e.preventDefault(); selectFromHistory(historyItem); }}}
                    >
                        <button
                                class="remove-item"
                                on:click|stopPropagation={() => removeHistoryItem(index)}
                                aria-label="Remove this search history"
                        >
                            ×
                        </button>
                        <span class="history-text">{historyItem}</span>
                    </div>
                {/each}
            </div>
        {/if}
    </div>
{/if}
<style lang="scss">
  @use "../../colors.scss" as *;

  .search {
    position: fixed;
    top: 80px;
    left: 50%;
    transform: translateX(-50%);
    width: 600px;
    z-index: 99999999;
  }

  .input-wrapper {
    display: flex;
    align-items: center;
    overflow: hidden;
    background: rgba(255, 255, 255, 0.08);
    border-radius: 28px;
    padding: 10px 24px;
    transition: all 0.3s cubic-bezier(0.1, 0.9, 0.2, 1);
    border: 1px solid transparent;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15),
    inset 0 0 0 1px rgba(255, 255, 255, 0.05);
    transform: translateZ(0);
    isolation: isolate;
    will-change: transform;

    &::before {
      content: '';
      position: absolute;
      inset: 0;
      background: linear-gradient(135deg, rgba(255, 255, 255, 0.08) 0%, transparent 30%);
      opacity: 0;
      pointer-events: none;
      transition: opacity 0.25s, pointer-events 0.25s;
      z-index: -1;
    }

    &:hover {
      &::before {
        opacity: 0.6;
        background: linear-gradient(135deg, rgba(255, 255, 255, 0.08) 0%, transparent 50%);
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15), inset 0 0 0 1px rgba(255, 255, 255, 0.05);
      }
    }

    &:focus-within {
      &::before {
        opacity: 1;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15), inset 0 0 0 1px rgba(255, 255, 255, 0.05);
        background: linear-gradient(135deg, color-mix(in srgb, var(--primary-color) 12%, transparent) 0%, transparent 70%);
        transition: opacity 0.3s ease-out, background 0.4s ease-out;
      }
    }


    &::placeholder {
      color: rgba($text, 0.5);
      transform: translateX(2px);
      opacity: 0.7;
      transition: transform 0.3s cubic-bezier(0.1, 0.9, 0.2, 1), opacity 0.2s linear;
    }


    &:active {
      transform: scale(0.98);
      transition-duration: 0.1s;
    }
  }

  .search-icon {
    width: 20px;
    height: 20px;
    background-color: rgba($text, 0.5);
    mask-image: url("/img/clickgui/icon-search.svg");
    mask-size: contain;
    mask-position: center;
    mask-repeat: no-repeat;
    transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
    border: none;
    cursor: pointer;
    margin-right: 10px;
    position: relative;
    padding: 8px;
    transform-origin: center;


    &::before {
      content: '';
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%) scale(0.8);
      width: 36px;
      height: 36px;
      background: linear-gradient(135deg,
              color-mix(in srgb, var(--primary-color) 20%, transparent) 0%,
              color-mix(in srgb, var(--secondary-color) 10%, transparent) 100%);
      border-radius: 50%;
      opacity: 0;
      transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
      z-index: -1;
    }

    &:hover {
      filter: drop-shadow(0 0 6px color-mix(in srgb, var(--primary-color) 50%, transparent));
      background: linear-gradient(135deg, var(--primary-color) 0%, var(--secondary-color) 100%);

      &::before {
        opacity: 0.6;
        transform: translate(-50%, -50%) scale(1);
      }
    }

    &.active {
      background: linear-gradient(135deg, var(--primary-color) 0%, var(--secondary-color) 100%);
      animation: searchPulse 0.6s cubic-bezier(0.16, 1, 0.3, 1) forwards;

      &::before {
        animation: circleExpand 0.6s cubic-bezier(0.16, 1, 0.3, 1) forwards;
      }
    }


    &.glow {
      filter: drop-shadow(0 0 12px color-mix(in srgb, var(--primary-color) 80%, transparent));

      &::before {
        opacity: 0.8;
      }
    }
  }

  .search-input {
    flex: 1;
    border: none;
    outline: none;
    background: transparent;
    color: $text;
    font-size: 20px;
    letter-spacing: 1px;
    font-family: "Inter", "Genshin", sans-serif;
    font-feature-settings: "frac" 0;

    &::placeholder {
      color: rgba($text, 0.5);
    }
  }

  .search-input::selection {
    background: rgba(255, 255, 255, 0.01);
    color: inherit;
    text-shadow: 0 0 4px $text;

  }


  .results, .history-results {
    max-height: 295px;
    margin-top: 8px;
    overflow-y: auto;
    overflow-x: hidden;
    padding: 0 8px 0 0;
    border-radius: 24px;
    box-shadow: 0 16px 32px rgba(0, 0, 0, 0.4),
    inset 0 0 0 1px rgba(255, 255, 255, 0.05);
    background: linear-gradient(
                    to bottom,
                    rgba(#1a1a1a, 0.8) 0%,
                    rgba(#0d0d0d, 0.85) 100%
    );
    backdrop-filter: blur(12px);

    &::before {
      content: '';
      position: absolute;
      top: -1px;
      left: 0;
      right: 0;
      height: 2px;
      background: linear-gradient(
                      to right,
                      transparent 10%,
                      rgba($accent, 0.3) 50%,
                      transparent 90%
      );
    }


    &::-webkit-scrollbar {
      display: none;
    }
  }

  .history-results {
    .history-header {
      position: relative;
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 12px 20px;


      &::before {
        content: '';
        display: inline-block;
        width: 32px;
        height: 32px;
        margin-right: 10px;
        background: linear-gradient(135deg, var(--primary-color) 0%, var(--secondary-color) 100%);
        mask-image: url("/img/clickgui/icon-history.svg");
        mask-size: contain;
        transition: transform 0.3s ease;
      }

      &::after {
        content: '';
        display: block;
        position: absolute;
        bottom: -1px;
        left: 50%;
        transform: translateX(-50%);
        width: 40%;
        height: 2px;
        background: linear-gradient(to right,
                transparent,
                color-mix(in srgb, var(--primary-color) 60%, transparent),
                transparent);

        transition: all 0.3s ease;
      }

      &:hover {
        color: var(--primary-color);

        &::before {
          transform: rotate(-10deg) scale(1.1);
          background-color: var(--primary-color);
        }

        &::after {
          width: 60%;
          background: color-mix(in srgb, var(--primary-color) 50%, transparent);
        }
      }
    }

    .title {
      font-size: var(--font-size);
      color: color-mix(in srgb, var(--primary-color) 90%, transparent);
      border-bottom: 1px solid color-mix(in srgb, var(--primary-color) 15%, transparent);
      text-transform: uppercase;
      letter-spacing: 1px;
      font-weight: 600;
      background-clip: text;
      background: linear-gradient(to right, var(--primary-color) 0%, var(--secondary-color) 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      text-shadow: 0 0 1px rgba($accent, 0.3);
    }

    .clear-all {
      position: absolute;
      right: 16px;
      top: 50%;
      transform: translateY(-50%);
      padding: 4px 10px;
      margin-left: 12px;
      font-size: var(--font-size);
      font-weight: 500;
      color: transparent;
      background: transparent;
      border: none;
      border-radius: 12px;
      cursor: pointer;
      transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
      overflow: hidden;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;

      svg {
        width: 14px;
        height: 14px;
        display: block;
        transition: transform 0.3s ease;
      }

      &::before {
        content: '';
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: linear-gradient(135deg, rgba($red, 0.1) 0%, transparent 100%);
        opacity: 0;
        transition: opacity 0.3s ease;
      }

      &:hover {
        color: $red;
        background: rgba($red, 0.08);

        svg {
          transform: rotate(90deg);
        }

        &::before {
          opacity: 1;
        }
      }

      &:active {
        transform: scale(0.95);
        background: rgba($red, 0.15);
      }

      &:focus-visible {
        outline: none;
        box-shadow: 0 0 0 2px rgba($red, 0.3);
      }
    }

    .history-item {
      padding: 10px 20px;
      position: relative;
      transition: all 0.2s ease;
      cursor: pointer;
      color: $subtext0;
      font-size: calc(var(--font-size) + 1px);
      display: flex;
      align-items: center;

      &:not(:last-child) {
        border-bottom: 1px solid rgba($subtext0, 0.1);
      }

      &.first {
        border-top-left-radius: 0;
        border-top-right-radius: 0;
      }

      &.last {
        border-bottom-left-radius: 0;
        border-bottom-right-radius: 0;
      }


      &:hover {
        background: linear-gradient(
                        to right,
                        color-mix(in srgb, var(--primary-color) 8%, transparent) 0%,
                        color-mix(in srgb, var(--secondary-color) 4%, transparent) 100%
        );
        padding-left: 28px;
        color: $text;

        &::before {
          background-color: var(--primary-color);
          transform: scale(1.1);
        }

        .remove-item {
          opacity: 1;
        }
      }

      &:active {
        background: rgba($accent, 0.15);
      }
    }

    .remove-item {
      width: 20px;
      height: 20px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: transparent;
      border: none;
      color: rgba($subtext0, 0.5);
      font-size: calc(var(--font-size) + 2px);
      cursor: pointer;
      margin-right: 8px;
      border-radius: 50%;
      transition: all 0.2s ease;
      opacity: 0;

      &:hover {
        color: $red;
        background: rgba($red, 0.1);
      }
    }

    .history-text {
      flex: 1;
    }
  }

  .result {
    font-size: calc(var(--font-size) + 2px);
    padding: 14px 34px;
    position: relative;
    transition: all 0.2s ease;
    cursor: pointer;
    display: grid;
    grid-template-columns: max-content 1fr max-content;
    align-items: center;

    &.first {
      border-top-left-radius: 0;
      border-top-right-radius: 0;
    }

    &.last {
      border-bottom-left-radius: 0;
      border-bottom-right-radius: 0;
    }

    &:not(:last-child) {
      border-bottom: 1px solid rgba($subtext0, 0.1);
    }

    .module-name {
      color: $subtext0;
      transition: all 0.2s ease;
      font-weight: 500;
    }

    &.enabled {
      .module-name {
        background-clip: text;
        background: linear-gradient(to right, var(--primary-color) 0%, var(--secondary-color) 100%);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
        text-shadow: 0 0 8px color-mix(in srgb, var(--secondary-color) 30%, transparent);
      }

      .aliases {
        color: rgba($subtext0, 0.6);
      }
    }

    .aliases {
      color: rgba($subtext0, 0.5);
      margin-left: 10px;
      font-size: var(--font-size);
      transition: color 0.2s ease;
    }

    &.selected,
    &:hover {
      background: rgba($accent, 0.08);
      padding-left: 40px;
      color: $text;
    }

    &:hover::after {
      content: "Right-click to locate";
      color: rgba($text, 0.4);
      font-size: var(--font-size);
      position: absolute;
      right: 28px;
      top: 50%;
      transform: translateY(-50%);
      padding: 4px 8px;
    }
  }

  @keyframes searchPulse {
    0% {
      transform: scale(1);
      filter: drop-shadow(0 0 4px color-mix(in srgb, var(--primary-color) 30%, transparent));
    }
    50% {
      transform: scale(1.15);
      filter: drop-shadow(0 0 16px color-mix(in srgb, var(--primary-color) 60%, transparent));
    }
    100% {
      transform: scale(1.05);
      filter: drop-shadow(0 0 8px color-mix(in srgb, var(--primary-color) 40%, transparent));
    }
  }

  @keyframes circleExpand {
    0% {
      opacity: 0;
      transform: translate(-50%, -50%) scale(0.8);
    }
    50% {
      opacity: 0.8;
      transform: translate(-50%, -50%) scale(1.2);
    }
    100% {
      opacity: 0.6;
      transform: translate(-50%, -50%) scale(1);
    }
  }
</style>
