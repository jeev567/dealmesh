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

## PayPal API integration

**Deal Mesh AI integrates with the PayPal developer platform APIs**, connecting AI-driven deal negotiation with PayPal's payment ecosystem. This integration is part of the project, not merely a future roadmap item.

The AI agents negotiate terms, while users remain responsible for approving a deal. PayPal provides the payment-side integration. The specific API endpoints and supported payment lifecycle should be checked against the backend implementation before documenting individual capabilities such as order capture, refunds, or webhooks.

## How it works

1. Enter buyer requirements.
2. Start negotiations with seller agents.
3. Review counteroffers and negotiated terms.
4. Compare eligible offers and identify the best price.
5. Select a deal and generate a draft agreement.
6. Use the integrated PayPal payment functionality where supported by the application.

## Challenges and lessons

Realistic agent negotiation requires clear roles, enforceable constraints, and consistent state across multiple rounds. Structured offer data is essential for reliable comparisons; AI handles negotiation, while people retain approval authority.

## What's next

- Expand and document PayPal payment workflows, including any supported Sandbox checkout and payment verification steps.
- Improve negotiation strategies and seller matching.
- Add milestone-based agreement and payment management.
- Explore freelance and B2B marketplace use cases.

## Disclaimer

This is a prototype. AI-generated agreements are drafts, not legal advice. PayPal API integration is included; individual payment capabilities should be verified in the application before relying on them.
