# RaktSetu HTTP load test

- Network: 20 centres, 40 hospitals, one staff account per hospital
- Each level: 30 s after a 15 s warm-up; closed loop, no think time
- Mix: 35% overview, 20% my requests, 15% network stock, 15% notifications, 15% raise an emergency request then cancel it
- Load generator: Java 25.0.4, 8 cores, Windows 11 amd64, same machine as the server

| Users | Throughput (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | Max (ms) | Errors |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 112 | 7.3 | 18.8 | 27.8 | 74 | 0 |
| 10 | 243 | 42.8 | 71.7 | 89.7 | 140 | 0 |
| 50 | 229 | 224.2 | 303.9 | 967.1 | 1463 | 0 |
| 100 | 232 | 437.7 | 748.1 | 1332.9 | 1724 | 0 |
| 200 | 228 | 914.1 | 1259.4 | 2506.0 | 2811 | 0 |

## By operation at 200 users

| Operation | Calls | p50 (ms) | p95 (ms) | p99 (ms) | Errors |
|---|---:|---:|---:|---:|---:|
| GET /api/notifications | 930 | 1.8 | 13.0 | 35.5 | 0 |
| GET /api/requests | 1235 | 930.3 | 1653.9 | 2535.7 | 0 |
| GET /api/stock | 941 | 930.5 | 1705.2 | 2507.4 | 0 |
| GET /api/summary | 2166 | 932.4 | 1344.2 | 2534.3 | 0 |
| POST /api/requests | 893 | 931.1 | 1434.4 | 2659.6 | 0 |
| POST /api/requests/{id}/cancel | 893 | 925.1 | 1265.5 | 2671.8 | 0 |
| all | 7058 | 914.1 | 1259.4 | 2506.0 | 0 |
