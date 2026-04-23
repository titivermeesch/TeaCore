import type { BadgeVariant } from '../components/ui/Badge';

/** Maps an Arena#state() enum name to a visual variant for the dashboard Badge. */
export function stateVariant(state: string): BadgeVariant {
    switch (state) {
        case 'LIVE':
            return 'success';
        case 'COUNTDOWN':
        case 'STARTING':
            return 'warning';
        case 'WAITING':
            return 'default';
        case 'ENDING':
        case 'RESETTING':
            return 'muted';
        case 'RELEASED':
            return 'error';
        default:
            return 'muted';
    }
}
