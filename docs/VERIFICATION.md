# Verification record

## Historical replay update — 18 October 2026

Continuation update: `continuation-backend-tests.log` records another successful root `clean install`, **17 tests passing**, including the April dataset and March-31 initial quote. The final Angular build (`continuation-ui-build.log`) passed with 283.20 kB initial and 89.25 kB lazy pages. The snapshot now contains **732 candles**: 498 warm-up, 126 March history and 108 April continuation candles. New dashboard regression tests cover unavailable quote/metadata requests, historical price fallback and continuation rollover. After initial resource-related worker startup timeouts, the serial frontend retry **passed all 9 tests in 3 files** (`continuation-ui-tests-retry.log`). No visual browser or full PostgreSQL deployment verification was performed for this update.

- Root Maven `clean install` passed: **17 tests, zero failures/errors** (`historical-clean-tests.log`). Includes six-stock data validation and measured volatility, all default strategies over March with no pre-window trades, old-demo replacement, idempotent reimport and exact replay event prices/dates.
- Angular production build passed (`historical-ui-build.log`): 282.95 kB initial bundle, 87.72 kB lazy pages bundle. Updated replay templates compile successfully.
- Downloaded and structurally validated 624 recorded OHLCV bars: 126 March candles and 498 earlier warm-up candles. Source revision and CSV hashes are bundled with the data.
- PostgreSQL/Flyway V5, full Docker deployment and a visual browser walkthrough were not exercised in this update. Database integration tests use H2; Kafka pipeline uses the embedded test broker.

The older record below describes the preceding synthetic-data version.

Backend and frontend tests verified on 17 October 2026. Final Angular delivery build verified on 18 October 2026.

| Check | Result |
|---|---|
| Backend Maven `verify` | Passed; executable Spring Boot JAR produced |
| Authentication and API integration | 5 tests passed |
| Spring application context | 1 test passed |
| Asynchronous backtest API, JSON round-trip and date range | 1 test passed |
| Backtest indicator/fee/data-validation regression suite | 6 tests passed |
| Embedded Kafka tick → alert → outbox → notification pipeline | 1 test passed |
| Angular authentication and chart tests | 6 tests passed |
| Angular production build | Passed on final source; initial bundle 282.94 kB (77.76 kB estimated transfer) |
| Real PostgreSQL/Flyway and Redis services | Not run: Docker was unavailable on this machine |
| Complete Docker Compose startup | Not run: Docker was unavailable |
| Visual browser walkthrough | Blocked: the in-app browser could not attach/connect to the local preview |
| External SMTP delivery | Not run: no real SMTP credentials configured |

Backend total: **14 tests, zero failures, zero errors**. Frontend total: **6 tests, zero failures**.

The backend tests use H2 for persistence and an actual embedded Kafka broker for the pipeline. They do not claim PostgreSQL-specific migration validation or real Redis cache verification. The Angular production compilation includes strict template checking, and the component tests render chart markup; there has been no completed screenshot-based visual QA.

The asynchronous regression test initially exposed incompatible JSON collection serialization between Hibernate and the web mapper. `JsonPersistenceConfig` now gives Hibernate the application mapper, and the full suite passed after that fix.

Test execution required running outside the filesystem sandbox because Java test instrumentation stalled there and the Angular runner could not rename its temporary cache files. Dependency downloads initially failed DNS resolution inside the sandbox and succeeded after network approval.

Artifacts:

- Backend: `tradepulse/target/tradepulse-0.0.1-SNAPSHOT.jar`
- Kafka library: `tradepulse-kafka/target/tradepulse-kafka-1.0.0.jar`
- Angular: `tradepulse-angular/dist/tradepulse/browser/`
- Detailed backend results: `tradepulse/target/surefire-reports/`

Reproduce with `scripts/verify.ps1` or the individual commands in the README. Run the acceptance walkthrough in `PROJECT_REVIEW.md` once Docker and browser access are available.

The final Angular build stalled while using its persistent cache. Disabling that cache in angular.json resolved the stall; the final production build completed successfully in 12.6 seconds. Caching remains disabled for reliable builds in this workspace. The backend was repackaged successfully after the final local-origin configuration update.


18 October follow-up: A user build failed with ClassFormatError (invalid method Code length 0) in BacktestResultEvent. A root clean verify rebuilt both modules successfully: 14 tests passed, zero failures/errors. scripts/verify.ps1 now cleans generated output before verification. See clean-verification.log for this run.
