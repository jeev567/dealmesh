package com.dealmesh.negotiation;

import org.springframework.stereotype.Service;
import java.util.*;
import static com.dealmesh.negotiation.NegotiationController.*;

@Service
public class NegotiationService {
 public Session create(UUID id, CreateRequest request) {
   return new Session(id,request.title(),request.budget(),request.days(),request.revisions(),
     "CREATED",List.of());
 }
 public boolean isAcceptable(CreateRequest request, Offer offer, int sellerMinimumPrice) {
   return offer.price() >= sellerMinimumPrice
     && offer.price() <= request.budget()
     && offer.days() > 0 && offer.days() <= request.days()
     && offer.revisions() >= request.revisions();
 }
 public List<SellerPolicy> sellers() {
   return List.of(
     new SellerPolicy("Studio North",1950,1750,10,2),
     new SellerPolicy("Pixel Foundry",1800,1700,14,2),
     new SellerPolicy("Brightside Labs",2100,1900,7,3)
   );
 }
 public record SellerPolicy(String name,int askingPrice,int minimumPrice,int minimumDays,int maxRevisions) {}
 public boolean isWithinSellerPolicy(Offer offer,SellerPolicy policy) {
   return offer.seller().equals(policy.name())
     && offer.price()>=policy.minimumPrice()
     && offer.days()>=policy.minimumDays()
     && offer.revisions()>=0 && offer.revisions()<=policy.maxRevisions();
 }
}
