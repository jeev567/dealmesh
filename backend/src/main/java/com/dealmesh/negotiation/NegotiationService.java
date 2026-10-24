package com.dealmesh.negotiation;

import org.springframework.stereotype.Service;
import java.util.*;
import static com.dealmesh.negotiation.NegotiationController.*;

@Service
public class NegotiationService {
 public Session create(UUID id, CreateRequest request) {
   // Foundation only: no LLM calls or generated offers until policy and persistence are ready.
   return new Session(id,request.title(),request.budget(),request.days(),request.revisions(),
     "CREATED",List.of());
 }
 public boolean isAcceptable(CreateRequest request, Offer offer, int sellerMinimumPrice) {
   return offer.price() >= sellerMinimumPrice
     && offer.price() <= request.budget()
     && offer.days() > 0 && offer.days() <= request.days()
     && offer.revisions() >= request.revisions();
 }
}
