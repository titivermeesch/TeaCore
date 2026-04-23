import clsx, { type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/** Canonical shadcn-style class-name merger. */
export function cn(...inputs: ClassValue[]) {
    return twMerge(clsx(inputs));
}
