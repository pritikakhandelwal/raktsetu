# RaktSetu HTTP load test

- Network: 20 centres, 40 hospitals, one staff account per hospital
- Each level: 30 s after a 15 s warm-up; closed loop, no think time
- Mix: 35% overview, 20% my requests, 15% network stock, 15% notifications, 15% raise an emergency request then cancel it
- Load generator: Java 25.0.4, 8 cores, Windows 11 amd64, same machine as the server

| Users | Throughput (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | Max (ms) | Errors |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 102 | 8.0 | 20.3 | 39.7 | 113 | 0 |
| 10 | 323 | 30.6 | 58.0 | 79.7 | 366 | 0 |
| 50 | 352 | 152.8 | 219.3 | 274.7 | 369 | 0 |
| 100 | 429 | 254.5 | 342.9 | 413.2 | 491 | 0 |
| 200 | 383 | 565.6 | 756.8 | 946.8 | 980 | 0 |

## By operation at 200 users

| Operation | Calls | p50 (ms) | p95 (ms) | p99 (ms) | Errors |
|---|---:|---:|---:|---:|---:|
| GET /api/notifications | 1523 | 2.0 | 11.5 | 48.4 | 0 |
| GET /api/requests | 2077 | 577.2 | 766.1 | 949.7 | 0 |
| GET /api/stock | 1569 | 571.1 | 758.4 | 945.2 | 0 |
| GET /api/summary | 3586 | 584.0 | 767.6 | 950.4 | 0 |
| POST /api/requests | 1479 | 582.9 | 771.4 | 953.7 | 0 |
| POST /api/requests/{id}/cancel | 1479 | 585.2 | 777.1 | 956.5 | 0 |
| all | 11713 | 565.6 | 756.8 | 946.8 | 0 |
