import { jsx as _jsx } from "react/jsx-runtime";
import { forwardRef } from 'react';
import { cn } from '@/lib/cn';
export const Input = forwardRef(({ className, ...props }, ref) => (_jsx("input", { ref: ref, className: cn('w-full rounded-md bg-bg-raised border border-border px-3 py-2 text-sm outline-none focus:border-primary/60 focus:ring-1 focus:ring-primary/30', className), ...props })));
Input.displayName = 'Input';
