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
 private final Map<UUID,List<Round>> transcripts = new ConcurrentHashMap<>();
 private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(3);
 private final Map<UUID,String> failures = new ConcurrentHashMap<>();
 public NegotiationController(NegotiationService service, OpenAiNegotiationService ai) {
   this.service=service; this.ai=ai;
 }

 public record CreateRequest(@NotBlank String title, @Min(1) int budget,
   @Min(1) @Max(365) int days, @Min(0) @Max(20) int revisions) {}
 public record Offer(String seller, int price, int days, int revisions, String explanation) {}
 public record Round(int number, String seller, String speaker, String message, Integer price, Integer days, Integer revisions) {}
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
 @PostMapping("/{id}/negotiate")
 public synchronized Session negotiate(@PathVariable UUID id) {
   Session existing=get(id);
   if(!existing.status().equals("CREATED")) {
     throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT,"Negotiation already processed");
   }
   var result=ai.negotiate(new CreateRequest(existing.title(),existing.budget(),existing.days(),existing.revisions()));
   List<Round> transcript=new ArrayList<>();
   int sequence=1;
   for(var turn:result.turns()) {
     transcript.add(new Round(sequence++,turn.seller(),turn.speaker(),turn.message(),
       turn.price(),turn.days(),turn.revisions()));
   }
   Session updated=new Session(id,existing.title(),existing.budget(),existing.days(),existing.revisions(),
     result.offers().isEmpty()?"NO_MATCH":"OFFERS_READY",result.offers());
   transcripts.put(id,List.copyOf(transcript));
   sessions.put(id,updated);
   return updated;
 }
 @PostMapping("/{id}/start")
 public synchronized ResponseEntity<Session> start(@PathVariable UUID id) {
   Session existing=get(id);
   if(!existing.status().equals("CREATED")) {
     throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT,"Negotiation already started");
   }
   Session running=new Session(id,existing.title(),existing.budget(),existing.days(),existing.revisions(),"RUNNING",List.of());
   sessions.put(id,running);
   transcripts.put(id,new java.util.concurrent.CopyOnWriteArrayList<>());
   executor.submit(() -> {
     try {
       var result=ai.negotiate(new CreateRequest(existing.title(),existing.budget(),existing.days(),existing.revisions()), turn -> {
         List<Round> log=transcripts.get(id);
         log.add(new Round(log.size()+1,turn.seller(),turn.speaker(),turn.message(),
           turn.price(),turn.days(),turn.revisions()));
       });
       sessions.put(id,new Session(id,existing.title(),existing.budget(),existing.days(),existing.revisions(),
         result.offers().isEmpty()?"NO_MATCH":"OFFERS_READY",result.offers()));
     } catch(Exception e) {
       failures.put(id,"Negotiation failed. Check backend logs and OpenAI configuration.");
       sessions.put(id,new Session(id,existing.title(),existing.budget(),existing.days(),existing.revisions(),"FAILED",List.of()));
     }
   });
   return ResponseEntity.accepted().body(running);
 }
 @GetMapping("/{id}/error")
 public Map<String,String> error(@PathVariable UUID id) {
   get(id);
   return Map.of("message",failures.getOrDefault(id,""));
 }
 @GetMapping("/{id}/rounds")
 public List<Round> rounds(@PathVariable UUID id) {
   Session session=get(id);
   if(transcripts.containsKey(id)) return transcripts.get(id);
   if(!session.status().equals("OFFERS_READY")) return List.of();
   List<Round> transcript=new ArrayList<>();
   int round=1;
   for(Offer offer:session.offers()) {
     transcript.add(new Round(round++,offer.seller(),"SELLER",
       offer.explanation(),offer.price(),offer.days(),offer.revisions()));
     transcript.add(new Round(round++,offer.seller(),"BUYER",
       "This offer satisfies the stated budget, delivery deadline and revision requirements. Recommend for human review.",
       offer.price(),offer.days(),offer.revisions()));
   }
   return transcript;
 }
 @GetMapping("/{id}")
 public Session get(@PathVariable UUID id) {
   Session s=sessions.get(id);
   if(s==null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"Negotiation not found");
   return s;
 }
}
