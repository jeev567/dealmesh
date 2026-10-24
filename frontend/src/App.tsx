import { useState } from 'react';
import { createNegotiation, generateOffers, getRounds, type NegotiationRound, type AiOffer } from './api';
import { motion, AnimatePresence } from 'motion/react';
import { ArrowRight, ArrowUpRight, Bell, Bot, Check, ChevronDown, CircleDollarSign, Clock3, Command, Compass, CreditCard, FileCheck2, LayoutDashboard, Menu, MessageSquareText, Plus, Search, ShieldCheck, Sparkles, TrendingDown, Users, Wallet, X, Zap } from 'lucide-react';

type View = 'Overview' | 'Marketplace' | 'Negotiations' | 'Agreements';
type Seller = { id: number; name: string; specialty: string; price: number; days: number; rating: string; initials: string; color: string; revisions: number };
const sellers: Seller[] = [
  { id: 1, name: 'Studio North', specialty: 'Brand & web design', price: 1950, days: 10, rating: '4.9', initials: 'SN', color: 'violet', revisions: 2 },
  { id: 2, name: 'Pixel Foundry', specialty: 'Web development', price: 1800, days: 14, rating: '4.8', initials: 'PF', color: 'cyan', revisions: 2 },
  { id: 3, name: 'Brightside Labs', specialty: 'Digital experiences', price: 2100, days: 7, rating: '5.0', initials: 'BL', color: 'orange', revisions: 3 }
];
const messages = [
  { from: 'seller', name: 'Studio North', text: 'We can deliver your 5-page website for $1,950 in 10 days, including two revisions.' },
  { from: 'buyer', name: 'Buyer Agent', text: 'Could you offer $1,750 while keeping the 10-day delivery and two revisions?' },
  { from: 'seller', name: 'Studio North', text: 'I can meet you at $1,850 with a 12-day timeline and two revisions.' },
  { from: 'buyer', name: 'Buyer Agent', text: 'That works within the buyer\'s requirements. I recommend this offer for human review.' }
];
const money = (n: number) => '$' + n.toLocaleString('en-US');
const navigation: { name: View; icon: typeof LayoutDashboard }[] = [
  { name: 'Overview', icon: LayoutDashboard }, { name: 'Marketplace', icon: Compass },
  { name: 'Negotiations', icon: MessageSquareText }, { name: 'Agreements', icon: FileCheck2 }
];
export default function App() {
  const [view, setView] = useState<View>('Overview');
  const [menu, setMenu] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [request, setRequest] = useState({ title: 'Modern business website', budget: '2000', days: '14', revisions: '2' });
  const [activeRequest, setActiveRequest] = useState(request);
  const [round, setRound] = useState(1);
  const [aiOffers, setAiOffers] = useState<AiOffer[]>([]);
  const [realRounds, setRealRounds] = useState<NegotiationRound[]>([]);
  const [aiStatus, setAiStatus] = useState<'idle'|'loading'|'ready'|'no-match'|'error'>('idle');
  const [aiError, setAiError] = useState('');
  const [selectedSeller, setSelectedSeller] = useState(1);
  const [search, setSearch] = useState('');
  const [toast, setToast] = useState('');
  const notify = (s: string) => { setToast(s); window.setTimeout(() => setToast(''), 3400); };
  const start = async () => {
    setActiveRequest(request); setRound(1); setShowCreate(false); setView('Negotiations');
    setAiStatus('loading'); setAiOffers([]); setRealRounds([]); setAiError('');
    try {
      const created = await createNegotiation({title:request.title,budget:Number(request.budget),days:Number(request.days),revisions:Number(request.revisions)});
      const result = await generateOffers(created.id);
      setAiOffers(result.offers);
      setRealRounds(await getRounds(created.id));
      setAiStatus(result.offers.length ? 'ready' : 'no-match');
    } catch (error) {
      setAiStatus('error');
      setAiError(error instanceof Error ? error.message : 'Unable to connect to the backend');
    }
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
        {view === 'Negotiations' && <><PageHead kicker="AI NEGOTIATION LAB" title="Where better deals happen" desc="Review AI-generated seller proposals and validated terms before any human decision." action={() => setShowCreate(true)}/><div className="negotiation-layout"><section className="panel negotiation-panel"><div className="panel-header"><div className="agent-title"><span className="agent-icon"><Bot size={20}/></span><span><strong>Negotiation workspace</strong><small>Validated AI offers and review transcript</small></span></div><span className="status"><span/> DEMO</span></div><div className="ai-results"><div className="ai-results-head"><Sparkles size={16}/> REAL AI SELLER OFFERS</div>{aiStatus==='loading' && <p role="status">Generating and validating offers with OpenAI…</p>}{aiStatus==='error' && <p role="alert">AI request failed: {aiError}. Check the Spring Boot server and OpenAI configuration.</p>}{aiStatus==='no-match' && <p>No offers met both parties' constraints. Try adjusting your budget or deadline.</p>}{aiStatus==='ready' && <div className="ai-offer-list">{aiOffers.map(o=><div className="ai-offer" key={o.seller}><strong>{o.seller}</strong><span>{money(o.price)} · {o.days} days · {o.revisions} revisions</span><small>{o.explanation}</small></div>)}</div>}{aiStatus==='idle' && <p>Create a new deal to request actual AI-generated offers. The transcript below is a scripted demo.</p>}</div><div className="progress-area"><div className="progress-label"><span>Negotiation progress</span><strong>Step {round} of {realRounds.length || 4}</strong></div><div className="progress-track"><motion.div animate={{width:`${round/(realRounds.length || 4)*100}%`}} transition={{duration:.35}}/></div></div><div className="chat">{(realRounds.length ? realRounds.slice(0,round).map(r=>({from:r.speaker==='BUYER'?'buyer':'seller',name:r.speaker==='BUYER'?'Buyer Validator':r.seller,text:r.message})) : messages.slice(0,round)).map((m,i) => <motion.div className={`chat-row ${m.from}`} key={i} initial={{opacity:0,y:12}} animate={{opacity:1,y:0}} transition={{delay:.08}}><span className="chat-avatar">{m.from==='buyer'?'BA':'SN'}</span><div><small>{m.name}</small><p>{m.text}</p></div></motion.div>)}</div><div className="chat-footer"><button className="secondary-btn" disabled={round===1} onClick={() => setRound(r=>r-1)}>Previous</button><button className="primary-btn" onClick={() => round<(realRounds.length || 4)?setRound(r=>r+1):notify('Demo complete. Human approval and payment will be implemented in a future release.')}>{round<4?'Next negotiation round':'Review final offer'} <ArrowRight size={17}/></button></div></section><aside className="panel deal-panel"><div className="overline">CURRENT REQUEST</div><h3>{activeRequest.title}</h3><div className="deal-detail"><Wallet size={18}/> Budget <strong>{money(Number(activeRequest.budget)||0)}</strong></div><div className="deal-detail"><Clock3 size={18}/> Deadline <strong>{activeRequest.days} days</strong></div><div className="deal-detail"><ShieldCheck size={18}/> Revisions <strong>{activeRequest.revisions}</strong></div><div className="divider"/><span className="overline">BEST DEMO OFFER</span><div className="offer-number">{round>=3?'$1,850':'$1,950'}</div><p className="muted">Studio North · {round>=3?'12':'10'} days · 2 revisions</p><div className="savings"><Sparkles size={16}/> {round>=3?'$100 below initial asking price':'Advance rounds to see the counteroffer'}</div><p className="disclaimer">No real agents, contracts, or payments are active in this preview.</p></aside></div></>}
        {view === 'Agreements' && <><PageHead kicker="TRUSTED DEALS" title="Agreements" desc="A secure place for approved terms and future PayPal checkout."/><div className="empty-state"><div className="empty-icon"><FileCheck2 size={30}/></div><h3>No agreements yet</h3><p>Once a real negotiation is completed and both parties approve the terms, agreements will appear here. This feature is planned for a future release.</p><button className="primary-btn" onClick={() => changeView('Negotiations')}>Explore negotiation demo <ArrowRight size={17}/></button></div></>}
      </motion.div></AnimatePresence>
    </main>
    <AnimatePresence>{showCreate && <motion.div className="modal-backdrop" initial={{opacity:0}} animate={{opacity:1}} exit={{opacity:0}} onClick={() => setShowCreate(false)}><motion.div className="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title" initial={{opacity:0,scale:.95,y:20}} animate={{opacity:1,scale:1,y:0}} exit={{opacity:0,scale:.96,y:12}} onClick={e=>e.stopPropagation()}><div className="modal-head"><span className="modal-icon"><Plus size={21}/></span><button className="icon-btn" onClick={()=>setShowCreate(false)} aria-label="Close"><X size={20}/></button></div><span className="overline">START SOMETHING GREAT</span><h2 id="modal-title">Create a new deal</h2><p className="muted">Set your requirements to explore a simulated AI negotiation.</p><form onSubmit={e=>{e.preventDefault();start();}}><label>What do you need?<input required maxLength={90} value={request.title} onChange={e=>setRequest({...request,title:e.target.value})}/></label><div className="form-grid"><label>Maximum budget ($)<input required type="number" min="1" max="10000000" value={request.budget} onChange={e=>setRequest({...request,budget:e.target.value})}/></label><label>Deadline (days)<input required type="number" min="1" max="365" value={request.days} onChange={e=>setRequest({...request,days:e.target.value})}/></label></div><label>Included revisions<input required type="number" min="0" max="20" value={request.revisions} onChange={e=>setRequest({...request,revisions:e.target.value})}/></label><button className="primary-btn submit" type="submit">Launch demo negotiation <ArrowRight size={17}/></button></form><p className="disclaimer">Preview only: demo offers are not calculated from these inputs and do not constitute binding offers.</p></motion.div></motion.div>}</AnimatePresence>
    <AnimatePresence>{toast && <motion.div className="toast" initial={{opacity:0,y:16}} animate={{opacity:1,y:0}} exit={{opacity:0,y:10}}><Check size={17}/>{toast}</motion.div>}</AnimatePresence>
  </div>;
}
function Stat({icon:Icon,label,value,note,tone}:{icon:typeof Wallet;label:string;value:string;note:string;tone:string}) {return <div className="stat-card"><span className={`stat-icon ${tone}`}><Icon size={21}/></span><span className="stat-label">{label}</span><strong>{value}</strong><small>{note}</small></div>}
function SellerCard({seller:s,onSelect}:{seller:Seller;onSelect:()=>void}) {return <motion.button whileHover={{y:-5}} transition={{duration:.2}} className="seller-card" onClick={onSelect}><div className="seller-top"><span className={`seller-avatar ${s.color}`}>{s.initials}</span><span className="rating">★ {s.rating}</span></div><h3>{s.name}</h3><p>{s.specialty}</p><div className="seller-bottom"><span>Starting at <strong>{money(s.price)}</strong></span><span className="seller-arrow"><ArrowUpRight size={19}/></span></div></motion.button>}
function PageHead({kicker,title,desc,action}:{kicker:string;title:string;desc:string;action?:()=>void}) {return <div className="page-head"><div><span className="overline">{kicker}</span><h1>{title}</h1><p>{desc}</p></div>{action&&<button className="primary-btn" onClick={action}><Plus size={18}/> New deal</button>}</div>}
