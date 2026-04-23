import clsx from 'clsx';
import { twMerge } from 'tailwind-merge';
/** Canonical shadcn-style class-name merger. */
export function cn(...inputs) {
    return twMerge(clsx(inputs));
}
