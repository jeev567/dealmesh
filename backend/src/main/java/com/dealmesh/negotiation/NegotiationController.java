package com.dealmesh.negotiation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/negotiations")
@CrossOrigin(origins = "http://localhost:5173")
public class NegotiationController {
 private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
 private final NegotiationService service;
 private final OpenAiNegotiationService ai;
 public NegotiationController(NegotiationService service, OpenAiNegotiationService ai) {
   this.service=service; this.ai=ai;
 }

 public record CreateRequest(@NotBlank String title, @Min(1) int budget,
   @Min(1) @Max(365) int days, @Min(0) @Max(20) int revisions) {}
 public record Offer(String seller, int price, int days, int revisions, String explanation) {}
 public record Session(UUID id, String title, int budget, int days, int revisions,
   String status, List<Offer> offers) {}

 @PostMapping
 public ResponseEntity<Session> create(@Valid @RequestBody CreateRequest req) {
   UUID id=UUID.randomUUID();
   Session session=service.create(id,req);
   sessions.put(id,session);
   return ResponseEntity.status(HttpStatus.CREATED).body(session);
 }
 @PostMapping("/{id}/generate-offers")
 public Session generateOffers(@PathVariable UUID id) {
   Session existing=get(id);
   if(!existing.status().equals("CREATED")) {
     throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT,"Offers already generated");
   }
   List<Offer> offers=ai.generate(new CreateRequest(existing.title(),existing.budget(),existing.days(),existing.revisions()));
   Session updated=new Session(id,existing.title(),existing.budget(),existing.days(),existing.revisions(),
     offers.isEmpty()?"NO_MATCH":"OFFERS_READY",offers);
   sessions.put(id,updated);
   return updated;
 }
 @GetMapping("/{id}")
 public Session get(@PathVariable UUID id) {
   Session s=sessions.get(id);
   if(s==null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"Negotiation not found");
   return s;
 }
}
