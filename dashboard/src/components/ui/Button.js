import { jsx as _jsx } from "react/jsx-runtime";
import { forwardRef } from 'react';
import { cn } from '@/lib/cn';
const VARIANT_CLASSES = {
    default: 'bg-primary/10 border-primary/40 text-primary hover:bg-primary/20',
    ghost: 'bg-transparent border-transparent text-slate-300 hover:bg-bg-card',
    danger: 'bg-error/10 border-error/40 text-error hover:bg-error/20',
};
export const Button = forwardRef(({ className, variant = 'default', ...props }, ref) => (_jsx("button", { ref: ref, className: cn('inline-flex items-center gap-2 rounded-md border px-3 py-1.5 text-sm font-medium transition disabled:opacity-50 disabled:cursor-not-allowed', VARIANT_CLASSES[variant], className), ...props })));
Button.displayName = 'Button';
