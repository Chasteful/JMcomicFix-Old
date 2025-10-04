import {type Writable, writable} from "svelte/store";

export const scaleFactor: Writable<number> = writable(1);
