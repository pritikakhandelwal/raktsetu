# RaktSetu HTTP load test

- Network: 20 centres, 40 hospitals, one staff account per hospital
- Each level: 30 s after a 15 s warm-up; closed loop, no think time
- Mix: 35% overview, 20% my requests, 15% network stock, 15% notifications, 15% raise an emergency request then cancel it
- Load generator: Java 25.0.4, 8 cores, Windows 11 amd64, same machine as the server

| Users | Throughput (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | Max (ms) | Errors |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 133 | 6.3 | 14.9 | 21.5 | 77 | 0 |
| 10 | 413 | 25.2 | 44.6 | 53.4 | 70 | 0 |
| 50 | 428 | 128.9 | 175.6 | 193.0 | 233 | 0 |
| 100 | 432 | 253.7 | 337.9 | 414.3 | 520 | 0 |
| 200 | 426 | 521.5 | 643.7 | 736.3 | 775 | 0 |

## By operation at 200 users

| Operation | Calls | p50 (ms) | p95 (ms) | p99 (ms) | Errors |
|---|---:|---:|---:|---:|---:|
| GET /api/notifications | 1688 | 1.9 | 6.8 | 23.6 | 0 |
| GET /api/requests | 2299 | 529.8 | 644.9 | 738.1 | 0 |
| GET /api/stock | 1749 | 527.7 | 644.0 | 731.6 | 0 |
| GET /api/summary | 4024 | 538.0 | 662.4 | 740.7 | 0 |
| POST /api/requests | 1633 | 533.8 | 658.2 | 740.6 | 0 |
| POST /api/requests/{id}/cancel | 1633 | 539.4 | 661.6 | 739.5 | 0 |
| all | 13026 | 521.5 | 643.7 | 736.3 | 0 |
