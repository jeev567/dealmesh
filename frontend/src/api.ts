export type AiOffer = { seller: string; price: number; days: number; revisions: number; explanation: string };
export type Negotiation = { id: string; status: string; offers: AiOffer[] };
type Requirements = { title: string; budget: number; days: number; revisions: number };
const API = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
async function post<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch(API + path, {
    method: 'POST', headers: {'Content-Type':'application/json'},
    ...(body !== undefined ? {body:JSON.stringify(body)} : {})
  });
  if (!response.ok) {
    let message = 'HTTP ' + response.status;
    try { const data = await response.json(); message = data.detail || data.message || message; } catch { /* use status */ }
    throw new Error(message);
  }
  return response.json() as Promise<T>;
}
export const createNegotiation = (request: Requirements) => post<Negotiation>('/api/negotiations',request);
export const generateOffers = (id: string) => post<Negotiation>('/api/negotiations/'+encodeURIComponent(id)+'/generate-offers');

export type NegotiationRound = { number: number; seller: string; speaker: 'BUYER'|'SELLER'; message: string; price: number|null; days: number|null; revisions: number|null };
export async function getRounds(id: string): Promise<NegotiationRound[]> {
  const response = await fetch(API + '/api/negotiations/' + encodeURIComponent(id) + '/rounds');
  if (!response.ok) throw new Error('Unable to load negotiation transcript');
  return response.json() as Promise<NegotiationRound[]>;
}
