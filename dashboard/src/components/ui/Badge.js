import { jsx as _jsx } from "react/jsx-runtime";
import { cn } from '@/lib/cn';
const VARIANT_CLASSES = {
    default: 'bg-primary/10 text-primary border-primary/30',
    success: 'bg-success/10 text-success border-success/30',
    warning: 'bg-accent/10 text-accent border-accent/30',
    error: 'bg-error/10 text-error border-error/30',
    muted: 'bg-bg-raised text-muted border-border',
};
export function Badge({ variant = 'default', className, ...props }) {
    return (_jsx("span", { className: cn('inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium', VARIANT_CLASSES[variant], className), ...props }));
}
