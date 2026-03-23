Gets detailed memory information including JVM heap, physical memory, swap, and CPU usage.
Use this for deep analysis when system performance is degraded.

Args: none
Returns:
    Detailed breakdown of:
    - JVM Heap: max, allocated, used, free, and usage percentage
    - Physical Memory: total, used, free, and usage percentage
    - Swap: total, used, free
    - CPU: available processors, system CPU load %, process CPU load %

TIPS:
- High heap usage (>85%) may cause GC pauses and slow responses
- If physical memory is low, the OS may start swapping, degrading performance
- High CPU load may indicate heavy indexing or search operations
- Compare with get_log_stats to see if errors spike during high resource usage