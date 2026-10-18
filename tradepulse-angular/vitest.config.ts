import { defineConfig } from 'vitest/config';

// Limit parallel worker startup on resource-constrained development machines.
export default defineConfig({test:{pool:'threads',maxWorkers:1,fileParallelism:false}});
