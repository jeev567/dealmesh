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
   try {
     ObjectNode payload=mapper.createObjectNode();
     payload.put("model",model);
     payload.put("temperature",0.3);
     var messages=payload.putArray("messages");
     messages.addObject().put("role","system").put("content",
       "You are a fictional seller negotiation agent in a DEMO marketplace. "+
       "Propose one commercially reasonable offer. Never invent capabilities or guarantees. "+
       "Return only the required JSON object. The server will independently validate all terms.");
     String prompt="Buyer project: "+buyer.title()+"; maximum budget USD "+buyer.budget()+
       "; maximum delivery days "+buyer.days()+"; minimum revisions "+buyer.revisions()+
       ". Seller: "+seller.name()+"; asking price USD "+seller.askingPrice()+
       "; HARD minimum price USD "+seller.minimumPrice()+
       "; HARD minimum delivery days "+seller.minimumDays()+
       "; HARD maximum revisions "+seller.maxRevisions()+
       ". Offer feasible terms within BOTH parties' constraints. If impossible, return the seller's "+
       "minimum acceptable terms even if buyer constraints are not met. Explanation <= 140 chars.";
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
     return new Offer(seller.name(),offer.path("price").asInt(-1),offer.path("days").asInt(-1),
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
