package com.dealmesh.negotiation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@RestController
@RequestMapping("/api/agreements")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"})
public class AgreementController {
 private final NegotiationController negotiations;
 private final AgreementRepository repository;
 private final SecureRandom random=new SecureRandom();

 public AgreementController(NegotiationController negotiations,AgreementRepository repository) {
   this.negotiations=negotiations; this.repository=repository;
 }
 public record SelectRequest(UUID negotiationId,String seller) {}
 public record Agreement(UUID id,UUID negotiationId,int version,String project,
   String seller,int price,int days,int revisions,String termsHash,
   boolean buyerApproved,boolean sellerDemoApproved,String status) {}
 public record ApprovalLinks(Agreement agreement,String buyerToken,String sellerDemoToken) {}
 public record RevisionRequest(int expectedVersion,String project,int price,int days,int revisions) {}
 public record Review(Agreement agreement,String role,boolean approved) {}

 @PostMapping
 public synchronized ApprovalLinks create(@RequestBody SelectRequest request) {
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
   Agreement a=new Agreement(id,session.id(),1,session.title(),offer.seller(),offer.price(),
     offer.days(),offer.revisions(),termsHash(session.id(),1,session.title(),offer.seller(),
       offer.price(),offer.days(),offer.revisions()),false,false,"PENDING_DEMO_APPROVAL");
   repository.save(a);
   return issueLinks(a);
 }

 @GetMapping("/{id}") public Agreement get(@PathVariable UUID id) { return repository.get(id); }

 @PostMapping("/{id}/revise")
 public synchronized ApprovalLinks revise(@PathVariable UUID id,@RequestBody RevisionRequest req) {
   Agreement old=repository.get(id);
   if(req.project()==null || req.project().isBlank() || req.project().length()>200 ||
       req.price()<=0 || req.days()<=0 || req.revisions()<0 || req.revisions()>20)
     throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid agreement terms");
   // Editing is an unauthenticated demo operation, not a production authorization model.
   Agreement updated=repository.revise(id,req.expectedVersion(),req.project().trim(),req.price(),
     req.days(),req.revisions(),termsHash(old.negotiationId(),old.version()+1,req.project().trim(),
       old.seller(),req.price(),req.days(),req.revisions()));
   return issueLinks(updated);
 }

 @GetMapping("/review/{token}")
 public Review review(@PathVariable String token) {
   var info=repository.tokenInfo(sha256(token));
   Agreement a=repository.get(info.agreementId());
   if(a.version()!=info.version())
     throw new ResponseStatusException(HttpStatus.CONFLICT,"Link expired; agreement terms changed");
   return new Review(a,info.role(),info.role().equals("buyer")?a.buyerApproved():a.sellerDemoApproved());
 }

 @PostMapping("/review/{token}/approve")
 public Agreement approveWithToken(@PathVariable String token) {
   return repository.approveToken(sha256(token));
 }

 @GetMapping("/{id}/audit")
 public List<AgreementRepository.AuditEvent> audit(@PathVariable UUID id) {
   return repository.auditHistory(id);
 }

 // Legacy unauthenticated approval endpoint intentionally disabled.
 @PostMapping("/{id}/approve/{role}")
 public Agreement legacyApproval(@PathVariable UUID id,@PathVariable String role) {
   throw new ResponseStatusException(HttpStatus.GONE,"Use a role-specific review link");
 }

 private ApprovalLinks issueLinks(Agreement a) {
   String buyer=token(),seller=token();
   repository.storeToken(a.id(),a.version(),"buyer",sha256(buyer));
   repository.storeToken(a.id(),a.version(),"seller-demo",sha256(seller));
   repository.audit(a.id(),"REVIEW_LINKS_ISSUED_V"+a.version());
   return new ApprovalLinks(a,buyer,seller);
 }
 private String token() {
   byte[] bytes=new byte[32];random.nextBytes(bytes);
   return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
 }
 private static String termsHash(UUID negotiationId,int version,String project,String seller,
   int price,int days,int revisions) {
   return sha256("DealMesh agreement v"+version+"\nNegotiation: "+negotiationId+"\nProject: "+project+
     "\nSeller (fictional demo): "+seller+"\nPrice USD: "+price+
     "\nDelivery days: "+days+"\nIncluded revisions: "+revisions+
     "\nNo payment, legal signature or identity verification is provided.");
 }
 private static String sha256(String value) {
   try {
     return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
       .digest(value.getBytes(StandardCharsets.UTF_8)));
   } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
 }
}
