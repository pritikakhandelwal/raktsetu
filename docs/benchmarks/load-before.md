# RaktSetu HTTP load test

- Network: 20 centres, 40 hospitals, one staff account per hospital
- Each level: 30 s after a 15 s warm-up; closed loop, no think time
- Mix: 35% overview, 20% my requests, 15% network stock, 15% notifications, 15% raise an emergency request then cancel it
- Load generator: Java 25.0.4, 8 cores, Windows 11 amd64, same machine as the server

| Users | Throughput (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | Max (ms) | Errors |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 83 | 7.7 | 31.2 | 54.6 | 258 | 0 |
| 10 | 116 | 90.1 | 159.2 | 195.9 | 255 | 0 |
| 50 | 100 | 523.0 | 781.9 | 1767.1 | 2327 | 0 |
| 100 | 86 | 1277.6 | 1834.2 | 2071.7 | 2312 | 0 |
| 200 | 96 | 2282.2 | 2817.4 | 3159.8 | 3256 | 0 |

## By operation at 200 users

| Operation | Calls | p50 (ms) | p95 (ms) | p99 (ms) | Errors |
|---|---:|---:|---:|---:|---:|
| GET /api/notifications | 387 | 1.6 | 14.2 | 58.7 | 0 |
| GET /api/requests | 578 | 2305.8 | 2819.1 | 3168.3 | 0 |
| GET /api/stock | 423 | 2302.0 | 2779.0 | 2916.7 | 0 |
| GET /api/summary | 971 | 2335.7 | 2882.3 | 3207.3 | 0 |
| POST /api/requests | 419 | 2313.7 | 2759.4 | 2917.9 | 0 |
| POST /api/requests/{id}/cancel | 419 | 2328.1 | 2846.5 | 3180.3 | 0 |
| all | 3197 | 2282.2 | 2817.4 | 3159.8 | 0 |
