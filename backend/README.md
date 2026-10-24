# DealMesh Backend — v0.2 foundation

Requires Java 21 and Gradle 8.5+.

Run:
```bash
cd backend
gradle bootRun
```

Create a negotiation:
```bash
curl -X POST http://localhost:8080/api/negotiations \
 -H 'Content-Type: application/json' \
 -d '{"title":"Business website","budget":2000,"days":14,"revisions":2}'
```

The API currently creates in-memory sessions without AI execution. Data is lost on restart. No PayPal integration, account authentication, or database persistence is implemented yet. Do not expose this service publicly.

Run tests: `gradle test`.
