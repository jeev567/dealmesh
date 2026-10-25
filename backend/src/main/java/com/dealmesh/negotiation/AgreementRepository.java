package com.dealmesh.negotiation;

import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static com.dealmesh.negotiation.AgreementController.Agreement;

@Repository
public class AgreementRepository {
 private final JdbcTemplate jdbc;
 public AgreementRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

 @PostConstruct public void init() {
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
   jdbc.execute("""
     CREATE TABLE IF NOT EXISTS agreement_versions (
       agreement_id TEXT NOT NULL, version INTEGER NOT NULL, project TEXT NOT NULL,
       seller TEXT NOT NULL, price INTEGER NOT NULL, days INTEGER NOT NULL,
       revisions INTEGER NOT NULL, terms_hash TEXT NOT NULL,
       created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
       PRIMARY KEY(agreement_id,version)
     )
     """);
   jdbc.execute("""
     CREATE TABLE IF NOT EXISTS agreement_approval_tokens (
       agreement_id TEXT NOT NULL, version INTEGER NOT NULL, role TEXT NOT NULL,
       token_hash TEXT NOT NULL UNIQUE, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
       PRIMARY KEY(agreement_id,version,role)
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
       terms_hash,buyer_approved,seller_demo_approved,status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
     """,a.id().toString(),a.negotiationId().toString(),a.version(),a.project(),a.seller(),
     a.price(),a.days(),a.revisions(),a.termsHash(),0,0,a.status());
   saveVersion(a);
   audit(a.id(),"CREATED_V"+a.version());
 }

 private void saveVersion(Agreement a) {
   jdbc.update("""
     INSERT INTO agreement_versions (agreement_id,version,project,seller,price,days,revisions,terms_hash)
     VALUES (?,?,?,?,?,?,?,?)
     """,a.id().toString(),a.version(),a.project(),a.seller(),a.price(),a.days(),a.revisions(),a.termsHash());
 }

 public Agreement get(UUID id) {
   var rows=jdbc.query("SELECT * FROM agreements WHERE id=?",(rs,row)->new Agreement(
     UUID.fromString(rs.getString("id")),UUID.fromString(rs.getString("negotiation_id")),
     rs.getInt("version"),rs.getString("project"),rs.getString("seller"),rs.getInt("price"),
     rs.getInt("days"),rs.getInt("revisions"),rs.getString("terms_hash"),
     rs.getInt("buyer_approved")==1,rs.getInt("seller_demo_approved")==1,rs.getString("status")),id.toString());
   if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Agreement not found");
   return rows.get(0);
 }

 public void storeToken(UUID id,int version,String role,String tokenHash) {
   jdbc.update("INSERT INTO agreement_approval_tokens(agreement_id,version,role,token_hash) VALUES (?,?,?,?)",
     id.toString(),version,role,tokenHash);
 }

 public record TokenInfo(UUID agreementId,int version,String role) {}
 public TokenInfo tokenInfo(String tokenHash) {
   var rows=jdbc.query("SELECT agreement_id,version,role FROM agreement_approval_tokens WHERE token_hash=?",
     (rs,i)->new TokenInfo(UUID.fromString(rs.getString(1)),rs.getInt(2),rs.getString(3)),tokenHash);
   if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Review link not found");
   return rows.get(0);
 }

 public synchronized Agreement approveToken(String tokenHash) {
   TokenInfo info=tokenInfo(tokenHash);
   Agreement a=get(info.agreementId());
   if(a.version()!=info.version()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Review link expired after terms revision");
   boolean already=info.role().equals("buyer")?a.buyerApproved():a.sellerDemoApproved();
   if(already) return a;
   String field=info.role().equals("buyer")?"buyer_approved":"seller_demo_approved";
   jdbc.update("UPDATE agreements SET "+field+"=1, updated_at=CURRENT_TIMESTAMP WHERE id=?",a.id().toString());
   jdbc.update("""
     UPDATE agreements SET status=CASE WHEN buyer_approved=1 AND seller_demo_approved=1
       THEN 'DEMO_APPROVED' ELSE 'PENDING_DEMO_APPROVAL' END, updated_at=CURRENT_TIMESTAMP WHERE id=?
     """,a.id().toString());
   audit(a.id(),info.role().equals("buyer")?"BUYER_APPROVED_V"+a.version():"SELLER_DEMO_APPROVED_V"+a.version());
   return get(a.id());
 }

 public synchronized Agreement revise(UUID id,int expectedVersion,String project,int price,int days,int revisions,String hash) {
   Agreement old=get(id);
   if(old.version()!=expectedVersion) throw new ResponseStatusException(HttpStatus.CONFLICT,"Agreement version changed; refresh before editing");
   Integer paymentCount=jdbc.queryForObject("SELECT count(*) FROM paypal_sandbox_payments WHERE agreement_id=?",Integer.class,id.toString());
   if(paymentCount!=null && paymentCount>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Agreement cannot be revised after a Sandbox order is created");
   int next=old.version()+1;
   jdbc.update("""
     UPDATE agreements SET version=?,project=?,price=?,days=?,revisions=?,terms_hash=?,
     buyer_approved=0,seller_demo_approved=0,status='PENDING_DEMO_APPROVAL',
     updated_at=CURRENT_TIMESTAMP WHERE id=?
     """,next,project,price,days,revisions,hash,id.toString());
   Agreement updated=get(id);
   saveVersion(updated);
   audit(id,"REVISED_V"+next+"_PRIOR_APPROVALS_INVALIDATED");
   return updated;
 }

 public record AuditEvent(long id,String event,String createdAt) {}
 public List<AuditEvent> auditHistory(UUID id) {
   get(id);
   return jdbc.query("SELECT id,event,created_at FROM agreement_audit WHERE agreement_id=? ORDER BY id",
     (rs,i)->new AuditEvent(rs.getLong(1),rs.getString(2),rs.getString(3)),id.toString());
 }
 public void audit(UUID id,String event) {
   jdbc.update("INSERT INTO agreement_audit(agreement_id,event) VALUES (?,?)",id.toString(),event);
 }
}
