# DealMesh backend — v0.3 AI offer generation

Requires Java 21, Gradle 8.5+, and an OpenAI API key.

```bash
export OPENAI_API_KEY="your-api-key"
cd backend
gradle bootRun
```

In another terminal:

```bash
curl -s -X POST http://localhost:8080/api/negotiations \
  -H 'Content-Type: application/json' \
  -d '{"title":"Business website","budget":2000,"days":14,"revisions":2}'
```

Copy the `id` from the JSON response, then run:

```bash
curl -s -X POST http://localhost:8080/api/negotiations/YOUR_ID/generate-offers
```

The endpoint calls OpenAI separately for each of three **fictional demo sellers**, requesting JSON-schema-constrained offers. The backend rejects offers that violate the seller's private minimum price, minimum delivery time, revision limit, or the buyer's budget/deadline/revisions. Valid offers are returned in ascending price order. An empty list with `NO_MATCH` means none met both parties' requirements. If no key is configured, the endpoint returns HTTP 503.

**Limitations:** These are AI-generated single-round seller proposals, not yet autonomous multi-round buyer/seller negotiations. No real sellers, binding agreements, payment execution, authentication, persistence, or production rate limits exist. Sessions are stored in memory and reset on restart. Do not expose this API publicly.

Tests: `gradle test`. The OpenAI integration has not yet been tested against a live API in this environment.
