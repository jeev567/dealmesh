package com.dealmesh.negotiation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

import static com.dealmesh.negotiation.NegotiationController.*;
import static com.dealmesh.negotiation.NegotiationService.SellerPolicy;

@Service
public class OpenAiNegotiationService {
 private final String apiKey;
 private final String model;
 private final ObjectMapper mapper;
 private final NegotiationService policy;
 private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

 public OpenAiNegotiationService(@Value("${openai.api-key:}") String apiKey,
   @Value("${openai.model:gpt-4o-mini}") String model, ObjectMapper mapper, NegotiationService policy) {
   this.apiKey=apiKey; this.model=model; this.mapper=mapper; this.policy=policy;
 }

 public record NegotiationTurn(String seller, String speaker, String message,
   int price, int days, int revisions, int round) {}
 public record NegotiationResult(List<Offer> offers, List<NegotiationTurn> turns) {}

 public NegotiationResult negotiate(CreateRequest buyer) {
   if(apiKey.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"OPENAI_API_KEY is not configured");
   List<Offer> winners=new ArrayList<>();
   List<NegotiationTurn> turns=new ArrayList<>();
   for(SellerPolicy seller:policy.sellers()) {
     Offer current=propose(buyer,seller);
     Offer best=null;
     for(int round=1;round<=4;round++) {
       if(!policy.isWithinSellerPolicy(current,seller)) {
         turns.add(new NegotiationTurn(seller.name(),"SYSTEM",
           "Seller proposal rejected by policy validation.",current.price(),current.days(),current.revisions(),round));
         break;
       }
       turns.add(new NegotiationTurn(seller.name(),"SELLER",current.explanation(),
         current.price(),current.days(),current.revisions(),round));
       if(policy.isAcceptable(buyer,current,seller.minimumPrice()) &&
          (best==null || current.price()<best.price() ||
           (current.price()==best.price() && current.days()<best.days()))) {
         best=current;
       }
       // Even an acceptable initial proposal should be negotiated at least once.
       // Stop when the seller has reached its minimum price or the round limit.
       if(round==4 || (best!=null && current.price()<=seller.minimumPrice())) break;
       Offer counter=agentProposal(buyer,seller,current,"BUYER");
       if(counter.price()<1 || counter.price()>buyer.budget() || counter.days()<1 ||
          counter.days()>buyer.days() || counter.revisions()<buyer.revisions() ||
          counter.revisions()>20 || counter.price()>=current.price()) {
         turns.add(new NegotiationTurn(seller.name(),"SYSTEM",
           "Buyer counteroffer rejected: it must improve price and satisfy buyer constraints.",
           counter.price(),counter.days(),counter.revisions(),round));
         break;
       }
       turns.add(new NegotiationTurn(seller.name(),"BUYER",counter.explanation(),
         counter.price(),counter.days(),counter.revisions(),round));
       Offer next=agentProposal(buyer,seller,counter,"SELLER");
       // A seller cannot increase the price relative to its prior proposal.
       if(next.price()>current.price()) {
         turns.add(new NegotiationTurn(seller.name(),"SYSTEM",
           "Seller counteroffer rejected: price increased.",next.price(),next.days(),next.revisions(),round));
         break;
       }
       current=next;
     }
     if(best!=null) {
       winners.add(best);
       turns.add(new NegotiationTurn(seller.name(),"SYSTEM",
         "Best validated offer: $"+best.price()+", "+best.days()+
         " days, "+best.revisions()+" revisions. Awaiting human review.",
         best.price(),best.days(),best.revisions(),4));
     } else {
       turns.add(new NegotiationTurn(seller.name(),"SYSTEM",
         "No mutually acceptable offer reached.",current.price(),current.days(),current.revisions(),4));
     }
   }
   winners.sort(Comparator.comparingInt(Offer::price).thenComparingInt(Offer::days));
   return new NegotiationResult(List.copyOf(winners),List.copyOf(turns));
 }

 private Offer agentProposal(CreateRequest buyer,SellerPolicy seller,Offer previous,String role) {
   String instruction=role.equals("BUYER")
     ? "You are a fictional BUYER agent. Counter the seller's latest offer with price <= buyer budget, delivery <= buyer deadline, and revisions >= buyer minimum. Your price MUST be strictly lower than the previous seller offer. Prefer a meaningful discount but do not assume the seller will accept. Never commit or approve a purchase."
     : "You are a fictional SELLER agent. Respond to the buyer counteroffer. You MUST respect your minimum price, earliest delivery time and maximum revisions. Your price MUST be at or below your previous seller offer. You may compromise but never violate seller constraints.";
   return requestOffer(seller.name(),instruction,
     "Project: "+buyer.title()+". Buyer maximum USD "+buyer.budget()+", deadline "+buyer.days()+
     " days, minimum revisions "+buyer.revisions()+". Seller "+seller.name()+
     " asks USD "+seller.askingPrice()+", minimum USD "+seller.minimumPrice()+
     ", earliest "+seller.minimumDays()+" days, maximum "+seller.maxRevisions()+
     " revisions. Latest proposal: USD "+previous.price()+", "+previous.days()+
     " days, "+previous.revisions()+" revisions. Latest explanation: "+previous.explanation()+
     ". Propose your next terms in JSON; explanation must be concise.");
 }

 public List<Offer> generate(CreateRequest request) {
   if(apiKey.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
     "OPENAI_API_KEY is not configured");
   List<Offer> accepted=new ArrayList<>();
   for(SellerPolicy seller:policy.sellers()) {
     Offer offer=propose(request,seller);
     if(policy.isWithinSellerPolicy(offer,seller) && policy.isAcceptable(request,offer,seller.minimumPrice())) {
       accepted.add(offer);
     }
   }
   accepted.sort(Comparator.comparingInt(Offer::price).thenComparingInt(Offer::days));
   return List.copyOf(accepted);
 }

 private Offer propose(CreateRequest buyer,SellerPolicy seller) {
   return requestOffer(seller.name(),
     "You are a fictional seller negotiation agent in a DEMO marketplace. Propose one commercially reasonable offer. Never invent capabilities or guarantees. Return only the required JSON object. The server independently validates all terms.",
     "Buyer project: "+buyer.title()+"; maximum budget USD "+buyer.budget()+
       "; maximum delivery days "+buyer.days()+"; minimum revisions "+buyer.revisions()+
       ". Seller: "+seller.name()+"; asking price USD "+seller.askingPrice()+
       "; HARD minimum price USD "+seller.minimumPrice()+
       "; HARD minimum delivery days "+seller.minimumDays()+
       "; HARD maximum revisions "+seller.maxRevisions()+
       ". Offer feasible terms within BOTH parties' constraints. If impossible, return the seller's minimum acceptable terms. Explanation <= 140 chars.");
 }

 private Offer requestOffer(String sellerName,String instruction,String prompt) {
   try {
     ObjectNode payload=mapper.createObjectNode();
     payload.put("model",model);
     payload.put("temperature",0.3);
     var messages=payload.putArray("messages");
     messages.addObject().put("role","system").put("content",instruction);
     messages.addObject().put("role","user").put("content",prompt);
     ObjectNode format=payload.putObject("response_format").put("type","json_schema");
     ObjectNode schemaDef=format.putObject("json_schema");
     schemaDef.put("name","seller_offer");schemaDef.put("strict",true);
     ObjectNode schema=schemaDef.putObject("schema");
     schema.put("type","object");schema.put("additionalProperties",false);
     ObjectNode props=schema.putObject("properties");
     props.putObject("price").put("type","integer");
     props.putObject("days").put("type","integer");
     props.putObject("revisions").put("type","integer");
     props.putObject("explanation").put("type","string");
     var required=schema.putArray("required");
     for(String field:List.of("price","days","revisions","explanation")) required.add(field);

     HttpRequest httpRequest=HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
       .timeout(Duration.ofSeconds(35)).header("Authorization","Bearer "+apiKey)
       .header("Content-Type","application/json")
       .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
     HttpResponse<String> response=http.send(httpRequest,HttpResponse.BodyHandlers.ofString());
     if(response.statusCode()!=200) {
       throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI provider request failed (HTTP "+response.statusCode()+")");
     }
     JsonNode root=mapper.readTree(response.body());
     String content=root.path("choices").path(0).path("message").path("content").asText("");
     if(content.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI provider returned no offer");
     JsonNode offer=mapper.readTree(content);
     return new Offer(sellerName,offer.path("price").asInt(-1),offer.path("days").asInt(-1),
       offer.path("revisions").asInt(-1),offer.path("explanation").asText(""));
   } catch(ResponseStatusException e) { throw e; }
     catch(InterruptedException e) {
       Thread.currentThread().interrupt();
       throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI provider request interrupted",e);
     } catch(Exception e) {
       throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Unable to generate AI offer",e);
     }
 }
}
