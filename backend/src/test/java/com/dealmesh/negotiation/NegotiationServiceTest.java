package com.dealmesh.negotiation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.dealmesh.negotiation.NegotiationController.*;
class NegotiationServiceTest {
 private final NegotiationService service = new NegotiationService();
 private final CreateRequest request = new CreateRequest("Website",2000,14,2);
 @Test void acceptsValidOffer() { assertTrue(service.isAcceptable(request,new Offer("A",1850,12,2,"ok"),1750)); }
 @Test void rejectsOfferBelowSellerFloor() { assertFalse(service.isAcceptable(request,new Offer("A",1700,12,2,""),1750)); }
 @Test void rejectsOverBudget() { assertFalse(service.isAcceptable(request,new Offer("A",2100,12,2,""),1750)); }
 @Test void rejectsLateDelivery() { assertFalse(service.isAcceptable(request,new Offer("A",1850,20,2,""),1750)); }
 @Test void rejectsInsufficientRevisions() { assertFalse(service.isAcceptable(request,new Offer("A",1850,12,1,""),1750)); }
 @Test void rejectsSellerFloor() { assertFalse(service.isWithinSellerPolicy(new Offer("Studio North",1600,12,2,""),service.sellers().get(0))); }
 @Test void rejectsImpossibleDelivery() { assertFalse(service.isWithinSellerPolicy(new Offer("Studio North",1850,7,2,""),service.sellers().get(0))); }
 @Test void rejectsExtraRevisions() { assertFalse(service.isWithinSellerPolicy(new Offer("Studio North",1850,12,3,""),service.sellers().get(0))); }
}
