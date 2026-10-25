package com.dealmesh.negotiation;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins={"http://localhost:5173","http://localhost:5174"})
public class PaypalSandboxController {
 private final AgreementRepository agreements;
 private final JdbcTemplate jdbc;
 private final RestClient client=RestClient.create();
 @Value("${paypal.sandbox.client-id:}") private String clientId;
 @Value("${paypal.sandbox.client-secret:}") private String clientSecret;
 private static final String BASE="https://api-m.sandbox.paypal.com";

 public PaypalSandboxController(AgreementRepository agreements,JdbcTemplate jdbc) {
   this.agreements=agreements;this.jdbc=jdbc;
 }
 @PostConstruct public void init() {
   jdbc.execute("""
     CREATE TABLE IF NOT EXISTS paypal_sandbox_payments (
       agreement_id TEXT NOT NULL, agreement_version INTEGER NOT NULL,
       order_id TEXT NOT NULL UNIQUE, amount TEXT NOT NULL, status TEXT NOT NULL,
       capture_id TEXT, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
       updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
       PRIMARY KEY(agreement_id,agreement_version)
     )
     """);
 }
 public record Payment(String agreementId,int version,String orderId,String amount,String status,String captureId,String approvalUrl) {}
 private String bearer() {
   if(clientId.isBlank()||clientSecret.isBlank())
     throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"PayPal Sandbox credentials not configured");
   try {
     JsonNode json=client.post().uri(BASE+"/v1/oauth2/token")
       .headers(h->h.setBasicAuth(clientId,clientSecret))
       .contentType(MediaType.APPLICATION_FORM_URLENCODED)
       .body("grant_type=client_credentials").retrieve().body(JsonNode.class);
     if(json==null||json.path("access_token").asText("").isBlank())throw new IllegalStateException("Missing sandbox access token");
     return json.path("access_token").asText();
   } catch(Exception e) {throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"PayPal Sandbox authentication failed",e);}
 }
 private JsonNode paypal(String method,String path,String token,Object body,String requestId) {
   try {
     var request=client.method(org.springframework.http.HttpMethod.valueOf(method)).uri(BASE+path)
       .headers(h->{h.setBearerAuth(token);h.setContentType(MediaType.APPLICATION_JSON);
         if(requestId!=null)h.set("PayPal-Request-Id",requestId);});
     JsonNode json=body==null ? request.retrieve().body(JsonNode.class) : request.body(body).retrieve().body(JsonNode.class);
     if(json==null)throw new IllegalStateException("Empty PayPal response");
     return json;
   } catch(Exception e) {throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"PayPal Sandbox request failed; check backend logs",e);}
 }
 private AgreementController.Agreement eligible(UUID id) {
   var a=agreements.get(id);
   if(!a.buyerApproved()||!a.sellerDemoApproved()||!"DEMO_APPROVED".equals(a.status()))
     throw new ResponseStatusException(HttpStatus.CONFLICT,"Both demo approvals required");
   return a;
 }
 private Payment stored(UUID id,int version) {
   var rows=jdbc.query("SELECT * FROM paypal_sandbox_payments WHERE agreement_id=? AND agreement_version=?",
     (rs,i)->new Payment(rs.getString("agreement_id"),rs.getInt("agreement_version"),
       rs.getString("order_id"),rs.getString("amount"),rs.getString("status"),rs.getString("capture_id"),null),
     id.toString(),version);
   return rows.isEmpty()?null:rows.get(0);
 }
 @GetMapping("/{id}") public Payment status(@PathVariable UUID id) {
   var a=agreements.get(id);
   Payment p=stored(id,a.version());
   if(p==null)return new Payment(id.toString(),a.version(),null,null,"NOT_STARTED",null,null);
   return p;
 }
 @PostMapping("/{id}/orders")
 public synchronized Payment create(@PathVariable UUID id) {
   var a=eligible(id);
   Payment existing=stored(id,a.version());
   if(existing!=null)return existing;
   String amount=BigDecimal.valueOf(a.price()).setScale(2).toPlainString();
   String access=bearer();
   String requestId="dealmesh-"+id+"-v"+a.version();
   Map<String,Object> body=Map.of("intent","CAPTURE","purchase_units",List.of(
     Map.of("reference_id",id.toString()+"-v"+a.version(),
       "description","DealMesh demo agreement v"+a.version(),
       "amount",Map.of("currency_code","USD","value",amount))));
   JsonNode result=paypal("POST","/v2/checkout/orders",access,body,requestId);
   String orderId=result.path("id").asText("");
   if(orderId.isBlank())throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"PayPal did not return an order ID");
   jdbc.update("""
     INSERT INTO paypal_sandbox_payments(agreement_id,agreement_version,order_id,amount,status)
     VALUES (?,?,?,?,?)
     """,id.toString(),a.version(),orderId,amount,"CREATED");
   agreements.audit(id,"PAYPAL_SANDBOX_ORDER_CREATED_V"+a.version());
   return new Payment(id.toString(),a.version(),orderId,amount,"CREATED",null,approvalLink(result));
 }
 @GetMapping("/{id}/approval")
 public Payment approval(@PathVariable UUID id) {
   var a=eligible(id);Payment p=stored(id,a.version());
   if(p==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Create an order first");
   if("COMPLETED".equals(p.status()))return p;
   JsonNode order=paypal("GET","/v2/checkout/orders/"+p.orderId(),bearer(),null,null);
   return new Payment(p.agreementId(),p.version(),p.orderId(),p.amount(),p.status(),p.captureId(),approvalLink(order));
 }
 private String approvalLink(JsonNode order) {
   for(JsonNode link:order.path("links")) {
     if("payer-action".equals(link.path("rel").asText())||"approve".equals(link.path("rel").asText())) {
       String url=link.path("href").asText("");
       try {
         URI uri=URI.create(url);
         if("https".equals(uri.getScheme())&&("www.sandbox.paypal.com".equals(uri.getHost())))
           return url;
       } catch(Exception ignored) {}
     }
   }
   return null;
 }
 @PostMapping("/{id}/capture")
 public synchronized Payment capture(@PathVariable UUID id) {
   var a=eligible(id);
   Payment p=stored(id,a.version());
   if(p==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No Sandbox order created");
   if("COMPLETED".equals(p.status()))return p;
   String access=bearer();
   JsonNode order=paypal("GET","/v2/checkout/orders/"+p.orderId(),access,null,null);
   if(!"APPROVED".equals(order.path("status").asText()))
     throw new ResponseStatusException(HttpStatus.CONFLICT,"Buyer must approve the order on PayPal Sandbox first");
   JsonNode result=paypal("POST","/v2/checkout/orders/"+p.orderId()+"/capture",access,Map.of(),
     "dealmesh-capture-"+p.orderId());
   String status=result.path("status").asText("");
   String captureId=result.path("purchase_units").path(0).path("payments").path("captures").path(0).path("id").asText("");
   if(!"COMPLETED".equals(status)||captureId.isBlank())
     throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"PayPal capture not confirmed; reconcile order before retry");
   jdbc.update("UPDATE paypal_sandbox_payments SET status='COMPLETED',capture_id=?,updated_at=CURRENT_TIMESTAMP WHERE order_id=?",
     captureId,p.orderId());
   agreements.audit(id,"PAYPAL_SANDBOX_CAPTURE_COMPLETED_V"+a.version());
   return stored(id,a.version());
 }
}
