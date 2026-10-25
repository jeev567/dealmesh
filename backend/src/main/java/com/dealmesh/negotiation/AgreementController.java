package com.dealmesh.negotiation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@RestController
@RequestMapping("/api/agreements")
@CrossOrigin(origins = "http://localhost:5173")
public class AgreementController {
 private final NegotiationController negotiations;
 private final AgreementRepository repository;

 public AgreementController(NegotiationController negotiations,AgreementRepository repository) {
   this.negotiations=negotiations;
   this.repository=repository;
 }

 public record SelectRequest(UUID negotiationId, String seller) {}
 public record Agreement(UUID id, UUID negotiationId, int version, String project,
   String seller, int price, int days, int revisions, String termsHash,
   boolean buyerApproved, boolean sellerDemoApproved, String status) {}

 @PostMapping
 public synchronized Agreement create(@RequestBody SelectRequest request) {
   if(request.negotiationId()==null || request.seller()==null || request.seller().isBlank())
     throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Negotiation and seller required");
   var session=negotiations.get(request.negotiationId());
   if(!"OFFERS_READY".equals(session.status()))
     throw new ResponseStatusException(HttpStatus.CONFLICT,"Negotiation must be completed");
   if(repository.existsForNegotiation(session.id()))
     throw new ResponseStatusException(HttpStatus.CONFLICT,"An agreement already exists for this negotiation");
   var offer=session.offers().stream().filter(o->o.seller().equals(request.seller())).findFirst()
     .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Seller has no validated offer"));
   UUID id=UUID.randomUUID();
   String canonical="DealMesh agreement v1\nNegotiation: "+session.id()+"\nProject: "+session.title()+
     "\nSeller (fictional demo): "+offer.seller()+"\nPrice USD: "+offer.price()+
     "\nDelivery days: "+offer.days()+"\nIncluded revisions: "+offer.revisions()+
     "\nNo payment, legal signature or identity verification is provided.";
   String hash=sha256(canonical);
   Agreement agreement=new Agreement(id,session.id(),1,session.title(),offer.seller(),offer.price(),
     offer.days(),offer.revisions(),hash,false,false,"PENDING_DEMO_APPROVAL");
   repository.save(agreement);
   return agreement;
 }

 @GetMapping("/{id}")
 public Agreement get(@PathVariable UUID id) {
   return repository.get(id);
 }

 @PostMapping("/{id}/approve/{role}")
 public synchronized Agreement approve(@PathVariable UUID id,@PathVariable String role) {
   Agreement current=get(id);
   if(!role.equals("buyer") && !role.equals("seller-demo"))
     throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Allowed demo roles: buyer, seller-demo");
   repository.approve(id,role);
   return repository.get(id);
 }

 private static String sha256(String value) {
   try {
     byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
     return HexFormat.of().formatHex(bytes);
   } catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
 }
}
