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

## PayPal integration roadmap

**PayPal Checkout / PayPal Sandbox integration is planned, not currently claimed as implemented.** The intended flow is for a buyer to approve an AI-negotiated offer and then complete payment through PayPal. Future milestones include payment status tracking and milestone-based payment workflows.

## How it works

1. Enter buyer requirements.
2. Start negotiations with seller agents.
3. Review counteroffers and negotiated terms.
4. Compare eligible offers and identify the best price.
5. Select a deal and generate a draft agreement.

## Challenges and lessons

Realistic agent negotiation requires clear roles, enforceable constraints, and consistent state across multiple rounds. Structured offer data is essential for reliable comparisons; AI handles negotiation, while people retain approval authority.

## What's next

- Integrate PayPal Sandbox Checkout and verify end-to-end payment flows.
- Improve negotiation strategies and seller matching.
- Add milestone-based agreement and payment management.
- Explore freelance and B2B marketplace use cases.

## Disclaimer

This is a prototype. AI-generated agreements are drafts, not legal advice. Payment capabilities described as planned are not represented as live features.
