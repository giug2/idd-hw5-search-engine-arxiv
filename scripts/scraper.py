# arXiv Article Scraper

import requests
import feedparser
import time
from pathlib import Path
from typing import List, Dict
from urllib.parse import quote
import xml.etree.ElementTree as ET

# Configurazione
SEARCH_QUERY = "text to speech"
MAX_RESULTS = 500  # Numero massimo di articoli da recuperare
OUTPUT_DIR = Path("arxiv_articles")
DELAY_BETWEEN_REQUESTS = 3  # Secondi di delay per rispettare il rate limit di arXiv

# URL API arXiv
ARXIV_API_URL = "http://export.arxiv.org/api/query?"


def search_arxiv(query: str, max_results: int) -> List[Dict]:
    """
    Cerca articoli su arXiv usando l'API ufficiale.
    
    Args:
        query: Stringa di ricerca
        max_results: Numero massimo di risultati
    
    Returns:
        Lista di dizionari con metadati degli articoli
    """
    print(f"🔍 Ricerca su arXiv per: '{query}'")
    print(f"📊 Limitato a {max_results} risultati\n")
    
    # Crea la query per l'API arXiv
    # Formato: (ti:"termine1" OR abs:"termine2")
    search_string = f'(ti:"{query}" OR abs:"{query}")'
    
    payload = {
        'search_query': search_string,
        'start': 0,
        'max_results': max_results,
        'sortBy': 'submittedDate',
        'sortOrder': 'descending'
    }
    
    try:
        response = requests.get(ARXIV_API_URL, params=payload, timeout=10)
        response.raise_for_status()
    except requests.exceptions.RequestException as e:
        print(f"❌ Errore nella richiesta API: {e}")
        return []
    
    # Parsa la risposta Atom/RSS
    feed = feedparser.parse(response.content)
    
    articles = []
    for entry in feed.entries:
        # Estrai i campi in modo sicuro con valori predefiniti
        entry_id = getattr(entry, 'id', '')
        if not entry_id or '/abs/' not in entry_id:
            continue  # Salta entry malformate
        
        article = {
            'id': entry_id.split('/abs/')[-1],  # arXiv ID
            'title': getattr(entry, 'title', 'No title'),
            'authors': [author.name for author in getattr(entry, 'authors', []) if hasattr(author, 'name')],
            'published': getattr(entry, 'published', 'Unknown'),
            'summary': getattr(entry, 'summary', 'No summary available'),
            'pdf_url': entry_id.replace('/abs/', '/pdf/') + '.pdf',
            'html_url': entry_id,  # URL della pagina HTML su arXiv
        }
        articles.append(article)
    
    return articles


def download_pdf(article: Dict, output_dir: Path) -> bool:
    """Scarica il PDF dell'articolo."""
    output_dir.mkdir(parents=True, exist_ok=True)
    
    filename = f"{article['id'].replace('/', '_')}.pdf"
    filepath = output_dir / filename
    
    if filepath.exists():
        print(f"   ⏭️  {filename} (già presente)")
        return True
    
    try:
        print(f"   📥 Scaricando: {filename}", end=" ")
        response = requests.get(article['pdf_url'], timeout=15)
        response.raise_for_status()
        
        with open(filepath, 'wb') as f:
            f.write(response.content)
        print("✓")
        return True
    except Exception as e:
        print(f"❌ ({e})")
        return False


def save_metadata_csv(articles: List[Dict], filename: str = "articles_metadata.csv"):
    """Salva i metadati degli articoli in formato CSV."""
    import csv
    
    output_dir = Path(OUTPUT_DIR)
    output_dir.mkdir(parents=True, exist_ok=True)
    csv_path = output_dir / filename
    
    try:
        with open(csv_path, 'w', newline='', encoding='utf-8') as f:
            if not articles:
                return
            
            writer = csv.DictWriter(f, fieldnames=['id', 'title', 'published', 'authors_count', 'pdf_url', 'html_url'])
            writer.writeheader()
            
            for article in articles:
                writer.writerow({
                    'id': article['id'],
                    'title': article['title'],
                    'published': article['published'],
                    'authors_count': len(article['authors']),
                    'pdf_url': article['pdf_url'],
                    'html_url': article['html_url']
                })
        
        print(f"✅ Metadati salvati in: {csv_path}")
    except Exception as e:
        print(f"❌ Errore nel salvataggio CSV: {e}")


def save_metadata_json(articles: List[Dict], filename: str = "articles_metadata.json"):
    """Salva i metadati degli articoli in formato JSON."""
    import json
    
    output_dir = Path(OUTPUT_DIR)
    output_dir.mkdir(parents=True, exist_ok=True)
    json_path = output_dir / filename
    
    try:
        with open(json_path, 'w', encoding='utf-8') as f:
            json.dump(articles, f, indent=2, ensure_ascii=False, default=str)
        
        print(f"✅ Metadati salvati in: {json_path}")
    except Exception as e:
        print(f"❌ Errore nel salvataggio JSON: {e}")


def main():
    """Funzione principale."""
    print("=" * 60)
    print("🎓 ArXiv Article Scraper")
    print("=" * 60 + "\n")
    
    # Cerca articoli
    articles = search_arxiv(SEARCH_QUERY, MAX_RESULTS)
    
    if not articles:
        print("❌ Nessun articolo trovato!")
        return
    
    print(f"\n✅ Trovati {len(articles)} articoli\n")
    
    """ Mostra i risultati (inutile per adesso, troppo verboso)
    print("📋 Articoli trovati:")
    print("-" * 60)
    for i, article in enumerate(articles, 1):
        print(f"\n{i}. {article['title']}")
        print(f"   🔗 ID: {article['id']}")
        print(f"   📅 Data: {article['published'][:10]}")
        print(f"   👥 Autori: {', '.join(article['authors'][:3])}")
        if len(article['authors']) > 3:
            print(f"      ... e {len(article['authors']) - 3} altri")
        print(f"   📝 Abstract: {article['summary'][:150]}...")
    """ 

    # Salva metadati
    print("\n" + "=" * 60)
    save_metadata_csv(articles)
    save_metadata_json(articles)
    
    # Scarica i PDF
    print("\n" + "=" * 60)
    print("📥 Download dei PDF...\n")
    
    for i, article in enumerate(articles, 1):
        print(f"[{i}/{len(articles)}]")
        download_pdf(article, Path(OUTPUT_DIR) / "pdfs")
        if i < len(articles):
            time.sleep(DELAY_BETWEEN_REQUESTS)  # Per rispettare il rate limit imposto da arXiv
    
    print("\n" + "=" * 60)
    print("=" * 60)


if __name__ == "__main__":
    main()