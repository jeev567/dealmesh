# Deal Mesh AI

**AI negotiates. Humans approve. PayPal powers payments.**

Deal Mesh AI is a multi-agent negotiation marketplace where autonomous buyer and seller agents negotiate project terms, compare offers, and help people reach better agreements.

## What it does

- Buyers specify a budget, deadline, and required revisions.
- AI buyer and seller agents exchange offers, counteroffers, and rejections within defined constraints.
- Multiple sellers negotiate independently.
- The dashboard compares acceptable offers and highlights the **BEST PRICE**.
- Buyers choose an offer and can generate a draft agreement.

For example, a buyer requesting a website for **$2,000**, delivered in **14 days** with **2 revisions**, can compare competing AI-negotiated seller offers.

## Why it matters

Deal Mesh AI reduces repetitive negotiation, makes competing offers easier to evaluate, and keeps the final decision with the buyer. Sellers can engage with opportunities more efficiently.

## Built with

- **OpenAI API:** Powers buyer and seller negotiation agents.
- **Java 21 + Spring Boot:** Backend APIs, negotiation orchestration, and business rules.
- **React + TypeScript:** Buyer experience, agent conversations, and offer comparison.
- **AWS EC2 + Nginx + Terraform:** Cloud deployment and infrastructure.
- **PayPal Developer APIs:** Payment-platform integration for AI-negotiated commerce.

## PayPal Sandbox integration (implemented)

The backend integrates **PayPal OAuth 2.0** and **Checkout Orders API v2**, linking negotiated, human-approved agreements to actual Sandbox payment orders.

| PayPal API | Method | Purpose |
| --- | --- | --- |
| `/v1/oauth2/token` | POST | Obtain a Sandbox OAuth access token using server-side client credentials |
| `/v2/checkout/orders` | POST | Create a USD order for the approved agreement amount (`intent: CAPTURE`) |
| `/v2/checkout/orders/{orderId}` | GET | Retrieve order status and PayPal buyer approval URL |
| `/v2/checkout/orders/{orderId}/capture` | POST | Capture the buyer-approved payment and persist the capture ID |

**Approval guard:** Both buyer and seller demo approvals are required before order creation or capture. Payments are tied to a specific agreement version and persisted in SQLite. The backend uses PayPal request IDs for safer retries.

**Implementation:** [PaypalSandboxController.java](backend/src/main/java/com/dealmesh/negotiation/PaypalSandboxController.java) and [frontend API client](frontend/src/api.ts).

**AI integration:** [OpenAiNegotiationService.java](backend/src/main/java/com/dealmesh/negotiation/OpenAiNegotiationService.java) uses OpenAI (configured model defaults to `gpt-4o-mini`) to generate negotiation proposals and counteroffers. Deterministic policy checks validate seller limits and buyer requirements; AI output alone does not authorize payment.

**Sandbox only:** These are test payments, not real-money transactions. Live Sandbox success requires valid credentials and a test buyer account.

## How it works

1. Enter buyer requirements.
2. Start negotiations with seller agents.
3. Review counteroffers and negotiated terms.
4. Compare eligible offers and identify the best price.
5. Select a deal and generate a draft agreement.
6. After buyer and seller demo approvals, create a PayPal Sandbox order, approve it on PayPal, and capture the payment.

## Challenges and lessons

Realistic agent negotiation requires clear roles, enforceable constraints, and consistent state across multiple rounds. Structured offer data is essential for reliable comparisons; AI handles negotiation, while people retain approval authority.

## What's next

- Expand PayPal workflows with additional payment reconciliation, tests, and optional milestone payments.
- Improve negotiation strategies and seller matching.
- Add milestone-based agreement and payment management.
- Explore freelance and B2B marketplace use cases.

## Disclaimer

This is a prototype. AI-generated agreements are drafts, not legal advice. PayPal API integration is included; individual payment capabilities should be verified in the application before relying on them.

## License

This project is available under the [MIT License](LICENSE).
