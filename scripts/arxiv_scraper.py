'''
Scraper per articoli scientifici da https://arxiv.org
Scarica articoli in formato HTML con protezione anti-recaptcha.

Uso: python arxiv_scraper.py "text to speech" 500
'''

import os, sys, time, random, re, requests
from lxml import etree

# Configurazione
BATCH_SIZE = 25
DELAY = (0.5, 1.5)  # min, max delay tra richieste
HEADERS = {'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36'}
BLOCKED_PATTERNS = ['recaptcha', 'captcha', 'unusual traffic', 'rate limit', 'too many requests', 
                    'access denied', 'blocked', 'please verify', 'robot', 'automated access']

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DIR = os.path.join(SCRIPT_DIR, '../input/papers/pippo')


def is_blocked(content):
    """Verifica se la pagina è un recaptcha o pagina di blocco."""
    text = content.decode('utf-8', errors='ignore').lower() if isinstance(content, bytes) else str(content).lower()
    return any(p in text for p in BLOCKED_PATTERNS)


def safe_request(url, retries=3):
    """Richiesta HTTP con retry e validazione anti-blocco."""
    for attempt in range(retries):
        try:
            if attempt > 0:
                time.sleep(DELAY[0] * (2 ** attempt) + random.random())
            
            resp = requests.get(url, headers=HEADERS, timeout=30)
            
            if resp.status_code in (429, 403, 503):
                print(f"    HTTP {resp.status_code}, retry...")
                continue
            
            resp.raise_for_status()
            
            if is_blocked(resp.content):
                print(f"    Pagina bloccata rilevata, retry...")
                continue
            
            return resp
        except requests.RequestException as e:
            print(f"    Errore: {e}")
    return None


def fetch_articles(query, start, k, batch_size):
    """Recupera e scarica articoli da arXiv."""
    url = f"https://arxiv.org/search/?query={'+'.join(query.split())}&searchtype=all&source=header&size={batch_size}&order=-announced_date_first&start={start}"
    
    print(f"Recupero risultati per '{query}'...")
    resp = safe_request(url)
    if not resp:
        return {'downloaded': 0, 'processed': 0, 'skipped': 0, 'errors': 1}
    
    root = etree.HTML(resp.content)
    articles = root.xpath("//p[@class='list-title is-inline-block']/a/@href") if root is not None else []
    
    if not articles:
        print("Nessun articolo trovato")
        return {'downloaded': 0, 'processed': 0, 'skipped': 0, 'errors': 0}
    
    print(f"Trovati {len(articles)} articoli")
    stats = {'downloaded': 0, 'processed': 0, 'skipped': 0, 'errors': 0}
    
    for idx, article_url in enumerate(articles[:k]):
        # Valida URL arXiv
        if 'arxiv.org' not in article_url or not re.search(r'/abs/|/pdf/|\d{4}\.\d{4,5}', article_url):
            stats['skipped'] += 1
            continue
        
        print(f"\n[{idx+1+start}] {article_url}")
        stats['processed'] += 1
        time.sleep(random.uniform(*DELAY))
        
        # Recupera pagina articolo
        article_resp = safe_request(article_url)
        if not article_resp:
            stats['errors'] += 1
            continue
        
        article_root = etree.HTML(article_resp.content)
        html_links = article_root.xpath("//*[@id='latexml-download-link']/@href") if article_root is not None else []
        
        if not html_links:
            print("  No HTML disponibile")
            stats['skipped'] += 1
            continue
        
        time.sleep(random.uniform(*DELAY))
        
        # Scarica HTML del paper
        html_resp = safe_request(html_links[0])
        if not html_resp or len(html_resp.content) < 500:
            stats['errors'] += 1
            continue
        
        # Verifica che sia un paper valido
        text = html_resp.content.decode('utf-8', errors='ignore').lower()
        if is_blocked(html_resp.content) or not any(x in text for x in ['<html', 'latexml', 'article']):
            print("  Contenuto non valido")
            stats['errors'] += 1
            continue
        
        # Salva file
        file_name = f"{OUTPUT_DIR}/{os.path.basename(html_links[0])}.html"
        with open(file_name, 'wb') as f:
            f.write(html_resp.content)
        print(f"  Salvato: {file_name}")
        stats['downloaded'] += 1
    
    print(f"\nBatch: {stats['downloaded']} scaricati, {stats['skipped']} saltati, {stats['errors']} errori")
    return stats


def download_articles(query, k):
    """Scarica k articoli suddividendo in batch."""
    totals = {'downloaded': 0, 'processed': 0, 'skipped': 0, 'errors': 0}
    
    for i in range(0, k, BATCH_SIZE):
        batch_k = min(BATCH_SIZE, k - i)
        stats = fetch_articles(query, i, batch_k, BATCH_SIZE)
        for key in totals:
            totals[key] += stats[key]
    
    return {'requested': k, **totals}

# -------- MAIN ---------
if __name__ == '__main__':
    if len(sys.argv) != 3:
        print('Uso: python arxiv_scraper.py <query> <k>')
        sys.exit(1)
    
    query = sys.argv[1]
    try:
        k = int(sys.argv[2])
        assert k > 0
    except:
        print("Errore: 'k' deve essere un intero positivo.")
        sys.exit(1)
    
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    start_time = time.time()
    stats = download_articles(query, k)
    elapsed = time.time() - start_time
    
    # Calcola dimensione cartella
    total_size = sum(os.path.getsize(os.path.join(dp, f)) 
                     for dp, _, files in os.walk(OUTPUT_DIR) 
                     for f in files if not os.path.islink(os.path.join(dp, f)))
    
    print(f"\n{'='*30}\n     STATISTICHE FINALI\n{'='*30}")
    print(f"Tempo: {elapsed:.2f}s" + (f" ({elapsed/stats['downloaded']:.2f}s/articolo)" if stats['downloaded'] else ""))
    print(f"Processati: {stats['processed']} | Scaricati: {stats['downloaded']} | Errori: {stats['errors']}")
    print(f"Dimensione: {total_size/(1024*1024):.2f} MB")
    print(f"\nSalvati in: {OUTPUT_DIR}\n{'='*30}")
