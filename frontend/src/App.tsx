import { useEffect, useRef, useState } from 'react';
import { createNegotiation, startNegotiation, getNegotiation, getNegotiationError, getRounds, type NegotiationRound, type AiOffer, createAgreement, reviseAgreement, getAgreement, getAudit, getReview, approveReview, type Review, type AuditEvent, type ApprovalLinks, type Agreement, createSandboxOrder, captureSandboxOrder, getSandboxPayment, getSandboxApproval, type SandboxPayment } from './api';
import { motion, AnimatePresence } from 'motion/react';
import { ArrowRight, ArrowUpRight, Bell, Bot, Check, ChevronDown, CircleDollarSign, Clock3, Command, Compass, CreditCard, FileCheck2, LayoutDashboard, Menu, MessageSquareText, Plus, Search, ShieldCheck, Sparkles, TrendingDown, Users, Wallet, X, Zap } from 'lucide-react';

type View = 'Overview' | 'Marketplace' | 'Negotiations' | 'Agreements';
type Seller = { id: number; name: string; specialty: string; price: number; days: number; rating: string; initials: string; color: string; revisions: number };
const sellers: Seller[] = [
  { id: 1, name: 'Studio North', specialty: 'Brand & web design', price: 1950, days: 10, rating: '4.9', initials: 'SN', color: 'violet', revisions: 2 },
  { id: 2, name: 'Pixel Foundry', specialty: 'Web development', price: 1800, days: 14, rating: '4.8', initials: 'PF', color: 'cyan', revisions: 2 },
  { id: 3, name: 'Brightside Labs', specialty: 'Digital experiences', price: 2100, days: 7, rating: '5.0', initials: 'BL', color: 'orange', revisions: 3 }
];
const money = (n: number) => '$' + n.toLocaleString('en-US');
const navigation: { name: View; icon: typeof LayoutDashboard }[] = [
  { name: 'Overview', icon: LayoutDashboard }, { name: 'Marketplace', icon: Compass },
  { name: 'Negotiations', icon: MessageSquareText }, { name: 'Agreements', icon: FileCheck2 }
];
export default function App() {
  const [view, setView] = useState<View>('Overview');
  const pollRef = useRef<number | null>(null);
  useEffect(() => () => { if (pollRef.current !== null) window.clearInterval(pollRef.current); }, []);
  const [menu, setMenu] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [request, setRequest] = useState({ title: 'Modern business website', budget: '2000', days: '14', revisions: '2' });
  const [activeRequest, setActiveRequest] = useState(request);
  const [round, setRound] = useState(1);
  const [aiOffers, setAiOffers] = useState<AiOffer[]>([]);
  const [negotiationId,setNegotiationId] = useState('');
  const [agreement,setAgreement] = useState<Agreement|null>(null);
  const [agreementBusy,setAgreementBusy] = useState(false);
  const [payment,setPayment] = useState<SandboxPayment|null>(null);
  const [paymentBusy,setPaymentBusy] = useState(false);
  const checkoutHandled=useRef(false);
  const [checkoutMessage,setCheckoutMessage]=useState('');
  const [paymentError,setPaymentError] = useState('');
  const paymentLocked=!!payment;
  const loadPayment=(id:string)=>getSandboxPayment(id).then(setPayment).catch(e=>setPaymentError(e instanceof Error?e.message:'Payment status unavailable'));
  const startPayment=async()=>{
    if(!agreement||paymentBusy)return;
    setPaymentBusy(true);setPaymentError('');
    try{
      const p=await createSandboxOrder(agreement.id);
      setPayment(p);
      const ready=p.approvalUrl?p:await getSandboxApproval(agreement.id);
      if(ready.approvalUrl)window.location.assign(ready.approvalUrl);
      else notify('Sandbox order created. Use Continue on PayPal to approve.');
    }catch(e){setPaymentError(e instanceof Error?e.message:'Unable to create Sandbox order');}
    finally{setPaymentBusy(false);}
  };
  const continuePayment=async()=>{
    if(!agreement||paymentBusy)return;
    setPaymentBusy(true);setPaymentError('');
    try{
      const p=await getSandboxApproval(agreement.id);
      setPayment(p);
      if(p.approvalUrl)window.location.assign(p.approvalUrl);
      else notify('No approval link available. Check the order in PayPal Sandbox.');
    }catch(e){setPaymentError(e instanceof Error?e.message:'Unable to open Sandbox approval');}
    finally{setPaymentBusy(false);}
  };
  const capturePayment=async()=>{
    if(!agreement||paymentBusy)return;
    setPaymentBusy(true);setPaymentError('');
    try{
      const p=await captureSandboxOrder(agreement.id);
      setPayment(p);refreshAudit(agreement.id);
      notify('PayPal Sandbox capture confirmed.');
    }catch(e){setPaymentError(e instanceof Error?e.message:'Capture failed');}
    finally{setPaymentBusy(false);}
  };
  const [links,setLinks] = useState<ApprovalLinks|null>(null);
  const [audit,setAudit] = useState<AuditEvent[]>([]);
  const [review,setReview] = useState<Review|null>(null);
  const [reviewError,setReviewError] = useState('');
  const [editTerms,setEditTerms] = useState(false);
  const [draftTerms,setDraftTerms] = useState({project:'',price:0,days:0,revisions:0});
  useEffect(()=>{
    const params=new URLSearchParams(window.location.search);
    const token=params.get('review');
    const checkout=params.get('checkout');
    const checkoutAgreement=params.get('agreement');
    const saved=checkoutAgreement || window.localStorage.getItem('dealmesh-last-agreement');
    if(checkout && checkoutAgreement && !checkoutHandled.current) {
      checkoutHandled.current=true;
      setView('Agreements');
      setCheckoutMessage(checkout==='cancelled'?'PayPal checkout was cancelled. No capture was requested.':'Verifying PayPal approval and completing Sandbox capture…');
      window.history.replaceState({},'',window.location.pathname);
      getAgreement(checkoutAgreement).then(a=>{
        setAgreement(a);window.localStorage.setItem('dealmesh-last-agreement',a.id);
        refreshAudit(a.id);
        if(checkout==='approved') {
          setPaymentBusy(true);
          return getSandboxPayment(a.id).then(p=>{
            if(!p||!p.orderId)throw new Error('No Sandbox order found for this agreement');
            return captureSandboxOrder(a.id).then(done=>{
              setPayment(done);
              setCheckoutMessage(done.status==='COMPLETED'?'Sandbox payment completed successfully. Capture: '+done.captureId:'PayPal status: '+done.status);
              refreshAudit(a.id);
            });
          }).finally(()=>setPaymentBusy(false));
        }
        return loadPayment(a.id);
      }).catch(e=>{setPaymentError(e instanceof Error?e.message:'Checkout verification failed');setCheckoutMessage('Could not confirm checkout. Please check the payment status before retrying.');});
      return;
    }
    if(token) {
      getReview(token).then(r=>{setReview(r);setView('Agreements');}).catch(e=>{setReviewError(e.message);setView('Agreements');});
    } else if(saved) {
      getAgreement(saved).then(a=>{setAgreement(a);loadPayment(a.id);getAudit(a.id).then(setAudit).catch(()=>{});}).catch(()=>{window.localStorage.removeItem('dealmesh-last-agreement');});
    }
  },[]);
  const refreshAudit=(id:string)=>getAudit(id).then(setAudit).catch(()=>{});
  const reviewUrl=(token:string)=>window.location.origin+window.location.pathname+'?review='+encodeURIComponent(token);
  const copyReview=async(token:string)=>{try{await navigator.clipboard.writeText(reviewUrl(token));notify('Review link copied. Keep it private.');}catch{notify('Copy failed. Check browser clipboard permissions.');}};
  const submitReview=async()=>{
    const token=new URLSearchParams(window.location.search).get('review');
    if(!token||agreementBusy)return;
    setAgreementBusy(true);
    try{await approveReview(token);setReview(await getReview(token));notify('Demo approval recorded.');}
    catch(e){setReviewError(e instanceof Error?e.message:'Approval failed');}
    finally{setAgreementBusy(false);}
  };
  const saveRevision=async()=>{
    if(!agreement||agreementBusy)return;
    setAgreementBusy(true);
    try{
      const result=await reviseAgreement(agreement.id,{expectedVersion:agreement.version,...draftTerms});
      setAgreement(result.agreement);setPayment(null);setLinks(result);setEditTerms(false);refreshAudit(result.agreement.id);
      notify('New agreement version created. Previous approvals and links invalidated.');
    }catch(e){notify(e instanceof Error?e.message:'Revision failed');}
    finally{setAgreementBusy(false);}
  };
  const [realRounds, setRealRounds] = useState<NegotiationRound[]>([]);
  const [talkingSeller,setTalkingSeller] = useState<string|null>(null);
  const talkTimer=useRef<number|null>(null);
  useEffect(()=>()=>{if(talkTimer.current!==null)window.clearTimeout(talkTimer.current);},[]);
  const highlightConversation=(seller:string)=>{
    if(talkTimer.current!==null)window.clearTimeout(talkTimer.current);
    setTalkingSeller(seller);
    talkTimer.current=window.setTimeout(()=>{setTalkingSeller(null);talkTimer.current=null;},1800);
  };
  const [aiStatus, setAiStatus] = useState<'idle'|'loading'|'ready'|'no-match'|'error'>('idle');
  const [aiError, setAiError] = useState('');
  const resultRef = useRef<HTMLDivElement|null>(null);
  useEffect(()=>{
    if(aiStatus==='ready'||aiStatus==='no-match'||aiStatus==='error') {
      resultRef.current?.scrollIntoView({behavior:'smooth',block:'center'});
    }
  },[aiStatus]);
  const [selectedSeller, setSelectedSeller] = useState(1);
  const [search, setSearch] = useState('');
  const [toast, setToast] = useState('');
  const notify = (s: string) => { setToast(s); window.setTimeout(() => setToast(''), 3400); };
  const start = async () => {
    if (pollRef.current !== null) window.clearInterval(pollRef.current);
    setActiveRequest(request); setRound(1); setShowCreate(false); setView('Negotiations');
    setAiStatus('loading'); setAiOffers([]); setRealRounds([]); setAiError(''); setTalkingSeller(null);
    try {
      const created = await createNegotiation({title:request.title,budget:Number(request.budget),days:Number(request.days),revisions:Number(request.revisions)});
      setNegotiationId(created.id);
      await startNegotiation(created.id);
      let inFlight = false;
      let seenMessages = 0;
      const started = Date.now();
      const poll = async () => {
        if (inFlight) return;
        inFlight = true;
        try {
          const [result, transcript] = await Promise.all([getNegotiation(created.id),getRounds(created.id)]);
          setRealRounds(transcript);
          setRound(transcript.length || 1);
          if(result.status==='RUNNING' && transcript.length>seenMessages) {
            const latest=transcript[transcript.length-1];
            if(latest?.seller)highlightConversation(latest.seller);
          }
          seenMessages=transcript.length;
          if (result.status !== 'RUNNING') {
            if (pollRef.current !== null) window.clearInterval(pollRef.current);
            pollRef.current = null;
            if (result.status === 'FAILED') throw new Error(await getNegotiationError(created.id));
            setAiOffers(result.offers);
            setTalkingSeller(null);
            if(talkTimer.current!==null)window.clearTimeout(talkTimer.current);
            setAiStatus(result.offers.length ? 'ready' : 'no-match');
          } else if (Date.now() - started > 180000) {
            throw new Error('Negotiation is taking longer than expected. Check the backend logs.');
          }
        } catch(error) {
          if (pollRef.current !== null) window.clearInterval(pollRef.current);
          pollRef.current = null;
          setAiStatus('error');
          setAiError(error instanceof Error ? error.message : 'Unable to connect to the backend');
        } finally { inFlight = false; }
      };
      await poll();
      if (pollRef.current === null && Date.now()-started < 180000) {
        // Only schedule more polls while the negotiation is still running.
        const status = await getNegotiation(created.id);
        if (status.status === 'RUNNING') pollRef.current = window.setInterval(poll,1200);
      }
    } catch(error) {
      setAiStatus('error');
      setAiError(error instanceof Error ? error.message : 'Unable to connect to the backend');
    }
  };
  const selectOffer = async (seller:string) => {
    if(!negotiationId || agreementBusy || agreement?.negotiationId===negotiationId) return;
    setAgreementBusy(true);
    try {
      const created=await createAgreement(negotiationId,seller);
      setAgreement(created.agreement);setPayment(null);setLinks(created);window.localStorage.setItem('dealmesh-last-agreement',created.agreement.id);refreshAudit(created.agreement.id);setView('Agreements');
      notify('Demo agreement drafted for review.');
    } catch(e) {notify(e instanceof Error ? e.message : 'Unable to draft agreement');}
    finally {setAgreementBusy(false);}
  };
  const changeView = (v: View) => { setView(v); setMenu(false); };
  const filtered = sellers.filter(s => (s.name + ' ' + s.specialty).toLowerCase().includes(search.toLowerCase()));
  return <div className="shell">
    <aside className={`sidebar ${menu ? 'open' : ''}`}>
      <button className="brand" onClick={() => changeView('Overview')}><span className="brand-symbol"><Zap size={20} fill="currentColor"/></span><span>dealmesh<span className="brand-dot">.</span></span><span className="brand-ai">AI</span></button>
      <div className="workspace"><span className="workspace-avatar">D</span><span><strong>Demo workspace</strong><small>Free preview</small></span><ChevronDown size={15}/></div>
      <div className="nav-label">WORKSPACE</div>
      <nav>{navigation.map(item => <button key={item.name} className={`nav-item ${view === item.name ? 'active' : ''}`} onClick={() => changeView(item.name)}><item.icon size={19}/>{item.name}{view === item.name && <span className="nav-dot"/>}</button>)}</nav>
      <div className="sidebar-bottom"><div className="help-card"><span className="help-icon"><Sparkles size={19}/></span><strong>Smarter deals start here</strong><p>Let AI-powered agents explore better terms for you.</p><button onClick={() => setShowCreate(true)}>Create a deal <ArrowUpRight size={15}/></button></div><div className="profile"><span className="profile-avatar">DM</span><span><strong>Demo User</strong><small>Preview account</small></span><span className="online"/></div></div>
    </aside>
    {menu && <button className="mobile-overlay" onClick={() => setMenu(false)} aria-label="Close navigation"/>}
    <main className="main">
      <header className="topbar"><div className="top-left"><button className="mobile-menu icon-btn" onClick={() => setMenu(true)} aria-label="Open menu"><Menu size={21}/></button><span>Workspace</span><span className="crumb">/</span><strong>{view}</strong></div><div className="top-actions"><span className="demo-tag"><span/> DEMO MODE</span><button className="icon-btn" onClick={() => notify('No notifications in this preview.')} aria-label="Notifications"><Bell size={19}/></button><span className="top-avatar">DM</span></div></header>
      <AnimatePresence mode="wait"><motion.div key={view} className="content" initial={{opacity:0,y:14}} animate={{opacity:1,y:0}} exit={{opacity:0,y:-8}} transition={{duration:.25}}>
        {view === 'Overview' && <>
          <div className="eyebrow"><span className="pulse"/> THE FUTURE OF DEALMAKING</div>
          <div className="hero"><div className="hero-copy"><span className="hero-kicker"><Sparkles size={15}/> INTELLIGENT NEGOTIATION PLATFORM</span><h1>Better deals.<br/><em>Less back-and-forth.</em></h1><p>Let intelligent agents explore offers, compare terms, and help you find your next great deal — all in one place.</p><div className="hero-buttons"><button className="primary-btn" onClick={() => setShowCreate(true)}>Create new deal <ArrowRight size={18}/></button><button className="ghost-btn" onClick={() => changeView('Marketplace')}>Explore marketplace <ArrowUpRight size={17}/></button></div></div><div className="hero-art" aria-hidden="true"><div className="orbit orbit-one"/><div className="orbit orbit-two"/><div className="orb-glow"/><div className="orb-core"><Command size={46}/></div><div className="floating-pill pill-one"><span>✦</span> AI MATCHED</div><div className="floating-pill pill-two"><Check size={14}/> BETTER TERMS</div></div></div>
          <div className="section-head"><div><span className="overline">YOUR WORKSPACE</span><h2>At a glance</h2></div><span className="muted">Sample dashboard data</span></div>
          <div className="stats"><Stat icon={MessageSquareText} label="Demo negotiations" value="01" note="Explore the sample flow" tone="purple"/><Stat icon={Users} label="Available sellers" value="03" note="Curated demo profiles" tone="blue"/><Stat icon={TrendingDown} label="Potential savings" value="$100" note="Illustrative negotiated offer" tone="green"/></div>
          <div className="section-head lower"><div><span className="overline">DISCOVER</span><h2>Featured sellers</h2></div><button className="text-link" onClick={() => changeView('Marketplace')}>View marketplace <ArrowRight size={16}/></button></div>
          <div className="seller-grid">{sellers.map(s => <SellerCard key={s.id} seller={s} onSelect={() => {setSelectedSeller(s.id);changeView('Marketplace');}}/>)}</div>
        </>}
        {view === 'Marketplace' && <><PageHead kicker="DISCOVER TALENT" title="Find your perfect match" desc="Explore curated demo seller profiles and compare what they offer." action={() => setShowCreate(true)}/><div className="searchbar"><Search size={19}/><input aria-label="Search sellers" placeholder="Search sellers or specialties..." value={search} onChange={e => setSearch(e.target.value)}/></div><div className="seller-grid marketplace-grid">{filtered.map(s => <SellerCard key={s.id} seller={s} onSelect={() => {setSelectedSeller(s.id);setShowCreate(true);}}/>)}</div>{filtered.length===0&&<div className="empty">No sellers match your search.</div>}<p className="disclaimer">All seller profiles and pricing are fictional demonstration data.</p></>}
        {view === 'Negotiations' && <><PageHead kicker="AI NEGOTIATION LAB" title="Where better deals happen" desc="Review multi-round buyer and seller agent proposals and validated terms before any human decision." action={() => setShowCreate(true)}/><div className="negotiation-layout"><section className="panel negotiation-panel"><div className="panel-header"><div className="agent-title"><span className="agent-icon"><Bot size={20}/></span><span><strong>Negotiation workspace</strong><small>Multi-round AI negotiation transcript</small></span></div><span className="status"><span/> DEMO</span></div><div className="ai-results" ref={resultRef} style={{border:aiStatus==='no-match'?'1px solid rgba(245,167,91,.6)':aiStatus==='ready'?'1px solid rgba(107,224,178,.55)':undefined,boxShadow:aiStatus==='ready'?'0 0 22px rgba(90,209,163,.12)':undefined}}><div className="ai-results-head"><Sparkles size={16}/> REAL AI SELLER OFFERS</div>{aiStatus==='loading' && <p role="status">Agents are negotiating live. New messages appear as they're validated…</p>}{aiStatus==='error' && <p role="alert">AI request failed: {aiError}. Check the Spring Boot server and OpenAI configuration.</p>}{aiStatus==='no-match' && <div role="status" style={{padding:16}}><strong style={{fontSize:20}}>Negotiation finished — no matching offers</strong><p>All three sellers were evaluated, but none met both parties' constraints. Try increasing the budget or extending the deadline.</p><button className="primary-btn" onClick={()=>setShowCreate(true)}>Adjust requirements and retry <ArrowRight size={16}/></button></div>}{aiStatus==='ready' && <><div role="status" style={{padding:'12px 0',fontWeight:700,fontSize:18,color:'#8be5c4'}}>Negotiation complete — {aiOffers.length} validated {aiOffers.length===1?'offer':'offers'}. Select one to draft an agreement.</div><div className="ai-offer-list">{aiOffers.map(o=><div className="ai-offer" key={o.seller}><strong>{o.seller}{aiOffers[0]?.seller===o.seller ? " · BEST PRICE" : ""}</strong><span>{money(o.price)} · {o.days} days · {o.revisions} revisions</span>{sellers.find(s=>s.name===o.seller && s.price>o.price) && <small>Compared with demo asking price: {money(sellers.find(s=>s.name===o.seller)!.price-o.price)} lower</small>}<small>{o.explanation}</small><button className="agreement-select" disabled={agreementBusy || agreement?.negotiationId===negotiationId} onClick={()=>selectOffer(o.seller)}>{agreement?.negotiationId===negotiationId && agreement?.seller===o.seller ? "Selected for agreement" : "Draft agreement"} <ArrowRight size={14}/></button></div>)}</div></>}{aiStatus==='idle' && <p>No negotiation has started yet. Create a new deal to generate a real AI transcript.</p>}</div><div style={{padding:'20px 22px',borderBottom:'1px solid rgba(147,139,205,.16)'}}>
  <div style={{display:'flex',justifyContent:'space-between',gap:12,alignItems:'center',marginBottom:16,flexWrap:'wrap'}}><div><strong style={{fontSize:16}}>Live agent network</strong><div className="muted" style={{fontSize:12}}>Follow each proposal from buyer to fictional sellers</div></div><span className="status">{aiStatus==='loading'?'NEGOTIATING':aiStatus==='ready'?'OFFERS VALIDATED':aiStatus==='no-match'?'NO MATCH — STOPPED':aiStatus==='error'?'STOPPED':'DEMO AGENTS'}</span></div>
  <div style={{display:'flex',alignItems:'stretch',gap:8,overflowX:'auto',padding:'10px 0 16px'}}>
    <motion.div animate={{boxShadow:realRounds.slice(0,round).at(-1)?.speaker==='BUYER'?'0 0 22px rgba(116,101,255,.35)':'0 0 0px transparent'}} style={{minWidth:130,padding:'18px 12px',borderRadius:16,background:'rgba(103,87,220,.2)',border:'1px solid rgba(151,136,255,.5)',textAlign:'center'}}><Bot size={26} color="#afa1ff"/><div style={{fontWeight:700,marginTop:8}}>Buyer agent</div><small className="muted">Budget {money(Number(activeRequest.budget)||0)}</small></motion.div>
    <div style={{minWidth:115,flex:1,alignSelf:'stretch',display:'flex',alignItems:'center'}}>
      <svg viewBox="0 0 180 222" width="100%" height="222" role="img" aria-label="One buyer agent branches into three seller conversations">
        {[37,111,185].map((y,i)=>{
          const path=`M0 111 C70 111, 92 ${y}, 180 ${y}`;
          const flowing=aiStatus==='loading' && talkingSeller===sellers[i].name;
          return <g key={sellers[i].id}>
            <path d={path} stroke={flowing?'#65e1d2':'rgba(143,130,230,.45)'} strokeWidth={flowing?3:2} fill="none" strokeLinecap="round"/>
            {flowing && <circle r="5" fill="#6be3d4">
              <animateMotion dur="1.25s" repeatCount="indefinite" path={path}/>
            </circle>}
          </g>;
        }>
        {[37,111,185].map((y,i)=><circle key={i} cx="177" cy={y} r="4" fill={aiStatus==='ready'?'#6be3b2':aiStatus==='no-match'?'#efb26c':'#9a8ae7'}/>)}
      </svg>
    </div>
    <div style={{display:'flex',flexDirection:'column',gap:8,minWidth:170,flex:1}}>
      {sellers.map(seller=>{const messages=realRounds.slice(0,round).filter(r=>r.seller===seller.name);const active=aiStatus==='loading'&&talkingSeller===seller.name;const validated=aiOffers.some(o=>o.seller===seller.name);return <motion.div key={seller.id} animate={{borderColor:active?'rgba(104,222,211,.95)':'rgba(150,145,199,.25)',boxShadow:active?'0 0 16px rgba(82,212,210,.2)':'none'}} style={{minHeight:68,padding:'9px 12px',borderRadius:12,border:'1px solid',background:'rgba(31,36,64,.9)',display:'flex',justifyContent:'space-between',gap:8,alignItems:'center'}}><div><strong style={{fontSize:12}}>{seller.name}</strong><div className="muted" style={{fontSize:11}}>{messages.length} messages</div></div><span style={{fontSize:11,color:active?'#6ce0d7':'#a5a9c4'}}>{aiStatus==='loading'&&active?'● ACTIVE':validated?'✓ VALIDATED':aiStatus==='no-match'?'NO MATCH':messages.length?'REPLIED':'WAITING'}</span></motion.div>;})}
    </div>
  </div>
  <div className="muted" style={{fontSize:12}}>Showing {Math.min(round,realRounds.length)} of {realRounds.length} recorded messages · Use Next message to replay the conversation</div>
</div><div className="progress-area"><div className="progress-label"><span>Negotiation progress</span><strong>{realRounds.length ? `Step ${round} of ${realRounds.length}` : 'Not started'}</strong></div><div className="progress-track"><motion.div animate={{width:`${realRounds.length ? round/realRounds.length*100 : 0}%`}} transition={{duration:.35}}/></div></div><div className="chat">{!realRounds.length && <div className="empty">{aiStatus==='loading'?'Negotiation started. Waiting for the first agent response…':aiStatus==='error'?'Unable to load negotiation. Review the error above.':aiStatus==='no-match'?'No compatible offers were found.':'Create a new deal to start an AI negotiation.'}</div>}{realRounds.slice(0,round).map(r=>({from:r.speaker==='BUYER'?'buyer':'seller',name:r.speaker==='BUYER'?'Buyer Agent':r.speaker==='SYSTEM'?'Policy Validator':r.seller,text:r.message,seller:r.seller,speaker:r.speaker,price:r.price,days:r.days,revisions:r.revisions})).map((m,i) => <motion.div className={`chat-row ${m.from}`} key={i} initial={{opacity:0,y:12}} animate={{opacity:1,y:0}} transition={{delay:.08}}><span className="chat-avatar">{m.speaker==='SYSTEM'?'PV':m.from==='buyer'?'BA':m.seller.split(' ').map(w=>w[0]).join('').slice(0,2)}</span><div><small>{m.name}</small><p>{m.text}</p>{m.speaker!=='SYSTEM' && m.price!=null && <div className="chat-terms"><strong>{money(m.price)}</strong><span>{m.days} days</span><span>{m.revisions} revisions</span></div>}</div></motion.div>)}</div><div className="chat-footer"><button className="secondary-btn" disabled={round===1 || realRounds.length===0} onClick={() => setRound(r=>r-1)}>Previous</button><button className="primary-btn" disabled={realRounds.length===0} onClick={() => round<realRounds.length?setRound(r=>r+1):notify('Review complete. Human approval and payment will be implemented in a future release.')}>{round<realRounds.length?'Next message':'Review transcript'} <ArrowRight size={17}/></button></div></section><aside className="panel deal-panel"><div className="overline">CURRENT REQUEST</div><h3>{activeRequest.title}</h3><div className="deal-detail"><Wallet size={18}/> Budget <strong>{money(Number(activeRequest.budget)||0)}</strong></div><div className="deal-detail"><Clock3 size={18}/> Deadline <strong>{activeRequest.days} days</strong></div><div className="deal-detail"><ShieldCheck size={18}/> Revisions <strong>{activeRequest.revisions}</strong></div><div className="divider"/><span className="overline">BEST VALIDATED OFFER</span>{aiOffers.length ? <><div className="offer-number">{money(aiOffers[0].price)}</div><p className="muted">{aiOffers[0].seller} · {aiOffers[0].days} days · {aiOffers[0].revisions} revisions</p><div className="savings"><Sparkles size={16}/> {sellers.find(s=>s.name===aiOffers[0].seller && s.price>aiOffers[0].price) ? money(sellers.find(s=>s.name===aiOffers[0].seller)!.price-aiOffers[0].price)+' below demo asking price' : 'Meets buyer requirements'}</div></> : <p className="muted">{aiStatus==='loading'?'Negotiating…':'No validated offer yet'}</p>}<p className="disclaimer">Fictional sellers and AI agents. Offers are nonbinding. No payments are made.</p></aside></div></>}
        {view === 'Agreements' && <><PageHead kicker="TRUSTED DEALS" title="Agreements" desc="Versioned terms, private role-specific review links and SQLite audit history."/>
        {reviewError && <p role="alert" className="disclaimer">{reviewError}</p>}
        {review && <section className="panel agreement-sheet"><span className="overline">INDEPENDENT DEMO REVIEW · V{review.agreement.version}</span><h2>{review.agreement.project}</h2><p className="muted">Role: {review.role==='buyer'?'Demo buyer':'Fictional seller'} · {review.agreement.seller}</p><div className="agreement-grid"><div><small>Price</small><strong>{money(review.agreement.price)}</strong></div><div><small>Delivery</small><strong>{review.agreement.days} days</strong></div><div><small>Revisions</small><strong>{review.agreement.revisions}</strong></div></div><div className="agreement-hash"><small>Version fingerprint</small><code>{review.agreement.termsHash}</code></div><p className="disclaimer">DEMO ONLY: Possession of this link permits role approval. No identity verification, legal signature or payment authorization.</p><button className="primary-btn" disabled={agreementBusy||review.approved} onClick={submitReview}>{review.approved?'Already approved':'Approve this version as '+(review.role==='buyer'?'demo buyer':'fictional seller')}</button></section>}
        {!review && (agreement ? <section className="panel agreement-sheet"><div className="agreement-heading"><div><span className="overline">DEALMESH · AGREEMENT V{agreement.version}</span><h2>{agreement.project}</h2><p className="muted">{agreement.seller} · fictional seller account</p></div><span className="status">{agreement.status==='DEMO_APPROVED'?'DEMO APPROVED':'AWAITING APPROVAL'}</span></div>
        <div className="agreement-grid"><div><small>Fixed price</small><strong>{money(agreement.price)}</strong></div><div><small>Delivery</small><strong>{agreement.days} days</strong></div><div><small>Revisions</small><strong>{agreement.revisions}</strong></div></div>
        <p className="muted">These are the exact selected offer terms. Scope is represented only by the project title; detailed deliverables, acceptance criteria, and payment milestones are not yet specified.</p>
        <div className="agreement-hash"><small>SHA-256 terms fingerprint</small><code>{agreement.termsHash}</code></div>
        <div className="agreement-actions"><div><strong>Buyer review</strong><small>{agreement.buyerApproved?'Demo approval recorded':'Separate private link required'}</small><button className="primary-btn" disabled={!links} onClick={()=>links&&copyReview(links.buyerToken)}>Copy buyer review link</button></div><div><strong>Fictional seller review</strong><small>{agreement.sellerDemoApproved?'Demo approval recorded':'Simulated role, separate private link'}</small><button className="secondary-btn" disabled={!links} onClick={()=>links&&copyReview(links.sellerDemoToken)}>Copy seller review link</button></div></div>
        {!links && <p className="disclaimer">Approval links are only shown when an agreement version is created or revised. To issue new links, create a new version.</p>}
        <div className="agreement-actions"><div><strong>Agreement version</strong><small>Changing terms resets both approvals and invalidates previous links.</small><button className="secondary-btn" disabled={paymentLocked} title={paymentLocked?"Revisions locked once a PayPal order exists":undefined} onClick={()=>{setDraftTerms({project:agreement.project,price:agreement.price,days:agreement.days,revisions:agreement.revisions});setEditTerms(true);}}>{paymentLocked?"Terms locked after checkout":"Revise terms"}</button></div></div>
        {editTerms && <div className="modal" style={{position:'relative',width:'100%',maxWidth:'100%',marginTop:16}}><h3>New agreement version</h3><form onSubmit={e=>{e.preventDefault();saveRevision();}}><label>Project<input required maxLength={200} value={draftTerms.project} onChange={e=>setDraftTerms({...draftTerms,project:e.target.value})}/></label><div className="form-grid"><label>Price USD<input required type="number" min="1" value={draftTerms.price} onChange={e=>setDraftTerms({...draftTerms,price:Number(e.target.value)})}/></label><label>Delivery days<input required type="number" min="1" value={draftTerms.days} onChange={e=>setDraftTerms({...draftTerms,days:Number(e.target.value)})}/></label></div><label>Revisions<input required type="number" min="0" max="20" value={draftTerms.revisions} onChange={e=>setDraftTerms({...draftTerms,revisions:Number(e.target.value)})}/></label><button className="primary-btn" type="submit" disabled={agreementBusy}>Save new version</button><button type="button" className="secondary-btn" onClick={()=>setEditTerms(false)}>Cancel</button></form></div>}
        <div className="agreement-actions"><div style={{width:'100%'}}><strong>PayPal Sandbox checkout</strong><small>Only after both demo approvals. A separate buyer action is required. Sandbox only — no real funds.</small>
        {checkoutMessage && payment?.status!=='COMPLETED' && <p role="status" className="muted">{checkoutMessage}</p>}
        {paymentError && <p role="alert" className="disclaimer">{paymentError}</p>}
        {payment?.status==='COMPLETED' ? <div role="status" style={{padding:24,borderRadius:18,background:'linear-gradient(135deg,rgba(27,176,137,.15),rgba(100,83,209,.10))',border:'1px solid rgba(77,203,159,.35)',marginTop:16}}>
          <div style={{display:'flex',alignItems:'center',gap:12,marginBottom:12}}><Check size={26} color="#6fe4b2"/><strong style={{fontSize:22}}>Sandbox payment complete</strong></div>
          <div style={{fontSize:32,fontWeight:750,marginBottom:16}}>USD {Number(payment.amount).toLocaleString('en-US',{minimumFractionDigits:2})}</div>
          <div className="muted" style={{overflowWrap:'anywhere',lineHeight:2}}>PayPal order: {payment.orderId}<br/>Capture ID: {payment.captureId}<br/>Agreement version: V{payment.version}</div>
          <small style={{display:'block',marginTop:12,color:'#a4b0c7'}}>Sandbox transaction · No real funds transferred</small>
        </div> : payment && <p className="muted">Order {payment.orderId} · USD {payment.amount} · {payment.status}</p>}
        {agreement.status==='DEMO_APPROVED' && !payment && <button className="primary-btn" disabled={paymentBusy} onClick={startPayment}>Create PayPal Sandbox order</button>}
        {agreement.status==='DEMO_APPROVED' && payment && payment.status!=='COMPLETED' && <div style={{display:'flex',gap:12,flexWrap:'wrap',marginTop:12}}><button className="secondary-btn" disabled={paymentBusy} onClick={continuePayment}>Continue on PayPal Sandbox</button><button className="primary-btn" disabled={paymentBusy} onClick={capturePayment}>I've approved on PayPal — capture payment</button></div>}

        <button className="secondary-btn" style={{marginTop:12}} disabled={paymentBusy} onClick={()=>loadPayment(agreement.id)}>Refresh payment status</button>
        </div></div>
        <div style={{marginTop:24}}><strong>Audit history</strong><button className="secondary-btn" style={{marginLeft:12}} onClick={()=>refreshAudit(agreement.id)}>Refresh history</button>{audit.map(event=><p className="muted" key={event.id}>{event.createdAt} · {event.event.replaceAll('_',' ')}</p>)}</div>
        <p className="disclaimer">DEMO ONLY: links are bearer credentials, not authenticated identities or legal signatures. Agreement versions and approvals persist in SQLite. Negotiation sessions still reset on backend restart. PayPal Sandbox checkout requires separate buyer authorization; no real funds are transferred.</p>
        </section> : <div className="empty-state"><div className="empty-icon"><FileCheck2 size={30}/></div><h3>No agreement drafted</h3><p>Finish an AI negotiation and choose a validated offer using “Draft agreement”.</p><button className="primary-btn" onClick={() => changeView('Negotiations')}>Review offers <ArrowRight size={17}/></button></div>)}</>}
      </motion.div></AnimatePresence>
    </main>
    <AnimatePresence>{showCreate && <motion.div className="modal-backdrop" initial={{opacity:0}} animate={{opacity:1}} exit={{opacity:0}} onClick={() => setShowCreate(false)}><motion.div className="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title" initial={{opacity:0,scale:.95,y:20}} animate={{opacity:1,scale:1,y:0}} exit={{opacity:0,scale:.96,y:12}} onClick={e=>e.stopPropagation()}><div className="modal-head"><span className="modal-icon"><Plus size={21}/></span><button className="icon-btn" onClick={()=>setShowCreate(false)} aria-label="Close"><X size={20}/></button></div><span className="overline">START SOMETHING GREAT</span><h2 id="modal-title">Create a new deal</h2><p className="muted">Set your requirements and let the buyer and fictional seller agents negotiate.</p><form onSubmit={e=>{e.preventDefault();start();}}><label>What do you need?<input required maxLength={90} value={request.title} onChange={e=>setRequest({...request,title:e.target.value})}/></label><div className="form-grid"><label>Maximum budget ($)<input required type="number" min="1" max="10000000" value={request.budget} onChange={e=>setRequest({...request,budget:e.target.value})}/></label><label>Deadline (days)<input required type="number" min="1" max="365" value={request.days} onChange={e=>setRequest({...request,days:e.target.value})}/></label></div><label>Included revisions<input required type="number" min="0" max="20" value={request.revisions} onChange={e=>setRequest({...request,revisions:e.target.value})}/></label><button className="primary-btn submit" type="submit">Start AI negotiation <ArrowRight size={17}/></button></form><p className="disclaimer">Demo marketplace: seller profiles are fictional. AI-generated offers are not binding and require human review.</p></motion.div></motion.div>}</AnimatePresence>
    <AnimatePresence>{toast && <motion.div className="toast" initial={{opacity:0,y:16}} animate={{opacity:1,y:0}} exit={{opacity:0,y:10}}><Check size={17}/>{toast}</motion.div>}</AnimatePresence>
  </div>;
}
function Stat({icon:Icon,label,value,note,tone}:{icon:typeof Wallet;label:string;value:string;note:string;tone:string}) {return <div className="stat-card"><span className={`stat-icon ${tone}`}><Icon size={21}/></span><span className="stat-label">{label}</span><strong>{value}</strong><small>{note}</small></div>}
function SellerCard({seller:s,onSelect}:{seller:Seller;onSelect:()=>void}) {return <motion.button whileHover={{y:-5}} transition={{duration:.2}} className="seller-card" onClick={onSelect}><div className="seller-top"><span className={`seller-avatar ${s.color}`}>{s.initials}</span><span className="rating">★ {s.rating}</span></div><h3>{s.name}</h3><p>{s.specialty}</p><div className="seller-bottom"><span>Starting at <strong>{money(s.price)}</strong></span><span className="seller-arrow"><ArrowUpRight size={19}/></span></div></motion.button>}
function PageHead({kicker,title,desc,action}:{kicker:string;title:string;desc:string;action?:()=>void}) {return <div className="page-head"><div><span className="overline">{kicker}</span><h1>{title}</h1><p>{desc}</p></div>{action&&<button className="primary-btn" onClick={action}><Plus size={18}/> New deal</button>}</div>}
