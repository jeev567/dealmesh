package com.dealmesh.negotiation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import jakarta.annotation.PostConstruct;
import java.util.UUID;
import static com.dealmesh.negotiation.AgreementController.Agreement;

@Repository
public class AgreementRepository {
 private final JdbcTemplate jdbc;
 public AgreementRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

 @PostConstruct
 public void init() {
   jdbc.execute("PRAGMA journal_mode=WAL");
   jdbc.execute("PRAGMA busy_timeout=5000");
   jdbc.execute("""
     CREATE TABLE IF NOT EXISTS agreements (
       id TEXT PRIMARY KEY, negotiation_id TEXT NOT NULL UNIQUE,
       version INTEGER NOT NULL, project TEXT NOT NULL, seller TEXT NOT NULL,
       price INTEGER NOT NULL, days INTEGER NOT NULL, revisions INTEGER NOT NULL,
       terms_hash TEXT NOT NULL, buyer_approved INTEGER NOT NULL DEFAULT 0,
       seller_demo_approved INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL,
       created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
       updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
     )
     """);
   jdbc.execute("""
     CREATE TABLE IF NOT EXISTS agreement_audit (
       id INTEGER PRIMARY KEY AUTOINCREMENT, agreement_id TEXT NOT NULL,
       event TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
     )
     """);
 }

 public boolean existsForNegotiation(UUID id) {
   Integer count=jdbc.queryForObject("SELECT count(*) FROM agreements WHERE negotiation_id=?",Integer.class,id.toString());
   return count!=null && count>0;
 }

 public void save(Agreement a) {
   jdbc.update("""
     INSERT INTO agreements (id,negotiation_id,version,project,seller,price,days,revisions,
       terms_hash,buyer_approved,seller_demo_approved,status)
     VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
     """,a.id().toString(),a.negotiationId().toString(),a.version(),a.project(),a.seller(),
     a.price(),a.days(),a.revisions(),a.termsHash(),a.buyerApproved()?1:0,
     a.sellerDemoApproved()?1:0,a.status());
   audit(a.id(),"CREATED");
 }

 public Agreement get(UUID id) {
   var results=jdbc.query("SELECT * FROM agreements WHERE id=?",(rs,row)->new Agreement(
     UUID.fromString(rs.getString("id")),UUID.fromString(rs.getString("negotiation_id")),
     rs.getInt("version"),rs.getString("project"),rs.getString("seller"),rs.getInt("price"),
     rs.getInt("days"),rs.getInt("revisions"),rs.getString("terms_hash"),
     rs.getInt("buyer_approved")==1,rs.getInt("seller_demo_approved")==1,rs.getString("status")),id.toString());
   if(results.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Agreement not found");
   return results.get(0);
 }

 public void approve(UUID id,String role) {
   String field=role.equals("buyer")?"buyer_approved":"seller_demo_approved";
   jdbc.update("UPDATE agreements SET "+field+"=1, updated_at=CURRENT_TIMESTAMP WHERE id=?",id.toString());
   jdbc.update("""
     UPDATE agreements SET status=CASE WHEN buyer_approved=1 AND seller_demo_approved=1
       THEN 'DEMO_APPROVED' ELSE 'PENDING_DEMO_APPROVAL' END, updated_at=CURRENT_TIMESTAMP WHERE id=?
     """,id.toString());
   audit(id,role.equals("buyer")?"BUYER_DEMO_APPROVED":"SELLER_DEMO_APPROVED");
 }

 private void audit(UUID id,String event) {
   jdbc.update("INSERT INTO agreement_audit(agreement_id,event) VALUES (?,?)",id.toString(),event);
 }
}
