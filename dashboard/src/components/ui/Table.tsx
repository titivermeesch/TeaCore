import { HTMLAttributes, TableHTMLAttributes, ThHTMLAttributes, TdHTMLAttributes, forwardRef } from 'react';
import { cn } from '@/lib/cn';

export const Table = forwardRef<HTMLTableElement, TableHTMLAttributes<HTMLTableElement>>(
    ({ className, ...props }, ref) => (
        <div className="w-full overflow-x-auto">
            <table ref={ref} className={cn('w-full caption-bottom text-sm', className)} {...props} />
        </div>
    ),
);
Table.displayName = 'Table';

export const Thead = forwardRef<HTMLTableSectionElement, HTMLAttributes<HTMLTableSectionElement>>(
    ({ className, ...props }, ref) => (
        <thead ref={ref} className={cn('text-left text-xs uppercase tracking-wide text-muted', className)} {...props} />
    ),
);
Thead.displayName = 'Thead';

export const Tbody = forwardRef<HTMLTableSectionElement, HTMLAttributes<HTMLTableSectionElement>>(
    ({ className, ...props }, ref) => (
        <tbody ref={ref} className={cn('divide-y divide-border', className)} {...props} />
    ),
);
Tbody.displayName = 'Tbody';

export const Tr = forwardRef<HTMLTableRowElement, HTMLAttributes<HTMLTableRowElement>>(
    ({ className, ...props }, ref) => (
        <tr ref={ref} className={cn('hover:bg-bg-raised/50 transition-colors', className)} {...props} />
    ),
);
Tr.displayName = 'Tr';

export const Th = forwardRef<HTMLTableCellElement, ThHTMLAttributes<HTMLTableCellElement>>(
    ({ className, ...props }, ref) => (
        <th ref={ref} className={cn('px-3 py-2 font-medium', className)} {...props} />
    ),
);
Th.displayName = 'Th';

export const Td = forwardRef<HTMLTableCellElement, TdHTMLAttributes<HTMLTableCellElement>>(
    ({ className, ...props }, ref) => (
        <td ref={ref} className={cn('px-3 py-2', className)} {...props} />
    ),
);
Td.displayName = 'Td';
