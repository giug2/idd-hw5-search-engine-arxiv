'''
Programma per recuperare articoli scientifici da https://arxiv.org
In particolare si recuperano articoli: 
  - disponibili in formato HTML 
  - che contengono specifiche parole chiave nel titolo o nell'abstract.
'''

'''
python arxiv_scraper.py "text to speech" 500
'''


# Importazione delle librerie necessarie
import os
import sys
import time
import requests
from lxml import etree

BATCH_SIZE = 25  # Numero massimo di articoli da scaricare per ogni batch

# Ottieni il percorso assoluto della directory in cui si trova lo script
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DIR = os.path.join(SCRIPT_DIR, 'papers')


def download_articles(query, k):
    
    """
    Funzione che scarica k articoli da arXiv basandosi sulla query fornita.
    
    Args:
        query: Parole chiave per la ricerca
        k: Numero totale di articoli da scaricare
        
    Returns:
        dict: Dizionario con statistiche del download
    """
    
    total_downloaded = 0
    total_processed = 0
    total_skipped = 0
    total_errors = 0

    # Se k è minore o uguale a BATCH_SIZE, scarica tutto in una volta.
    if k <= BATCH_SIZE:
        stats = fetch_articles(query, 0, k, BATCH_SIZE)
        total_downloaded = stats['downloaded']
        total_processed = stats['processed']
        total_skipped = stats['skipped']
        total_errors = stats['errors']
    
    # Altrimenti divide il lavoro in più batch per rispettare i limiti del server.
    else:
        for i in range(0, k, BATCH_SIZE):
            stats = fetch_articles(query, i, min(BATCH_SIZE, k - i), BATCH_SIZE)
            total_downloaded += stats['downloaded']
            total_processed += stats['processed']
            total_skipped += stats['skipped']
            total_errors += stats['errors']
            if stats['downloaded'] == 0:
                print(f"Nessun articolo trovato nel batch {i + 1}-{i + min(BATCH_SIZE, k - i)}")
    
    return {
        'requested': k,
        'processed': total_processed,
        'downloaded': total_downloaded,
        'skipped': total_skipped,
        'errors': total_errors
    }


def fetch_articles(query, start, k, batch_size):
    
    """
    Funzione che recupera e scarica articoli da arXiv.
    
    Args:
        query: Parole chiave per la ricerca
        start: Indice di partenza per la paginazione dei risultati
        k: Numero di articoli da scaricare in questo batch
        batch_size: Dimensione massima del batch per la richiesta
        
    Returns:
        dict: Dizionario con statistiche (downloaded, processed, skipped)
    """

    try:
        # Codifica della query per l'utilizzo nell'URL (sostituisce spazi con '+')
        query2 = '+'.join(query.split())
        url = f"https://arxiv.org/search/?query={query2}&searchtype=all&source=header&size={batch_size}&order=-announced_date_first&start={start}"
        print(f"Recupero risultati di ricerca per '{query}'")

        # Invio della richiesta GET alla pagina di ricerca di arXiv
        response = requests.get(url)
        response.raise_for_status()  # Verifica che non ci siano errori nella richiesta
        print(f"Risultati di ricerca recuperati con successo per '{query}'")

        # Analisi del contenuto HTML della risposta
        root = etree.HTML(response.content)
        
        if root is None:
            print("Errore: Impossibile analizzare il contenuto HTML della pagina di ricerca.")
            return {'downloaded': 0, 'processed': 0, 'skipped': 0, 'errors': 1}

        # Contatori per le statistiche
        downloads = 0
        processed = 0
        skipped = 0
        errors = 0

        # Estrazione degli URL degli articoli dalla pagina dei risultati usando XPath
        articles = root.xpath("//p[@class='list-title is-inline-block']/a/@href")

        # Iterazione su tutti gli URL degli articoli trovati nei risultati di ricerca
        for idx, article_url in enumerate(articles):
            # Controlla se abbiamo già elaborato abbastanza articoli per questo batch
            if processed >= k:
                print("Limite del batch raggiunto.")
                break

            print(f"Elaborazione articolo {idx + 1 + start}: {article_url}")
            processed += 1

            try:
                # Recupero della pagina dell'articolo
                article_response = requests.get(article_url)
                article_response.raise_for_status()

                # Analisi del contenuto HTML della pagina dell'articolo
                article_root = etree.HTML(article_response.content)

                # Verifica se esiste il link per scaricare la versione HTML (LateXML)
                if article_root.xpath("//*[@id='latexml-download-link']"):
                    # Ottiene l'URL per scaricare il file HTML
                    href = article_root.xpath("//*[@id='latexml-download-link']/@href")[0]
                    html_response = requests.get(href)
                    html_response.raise_for_status()

                    # Salva il contenuto HTML in un file
                    file_name = f"{OUTPUT_DIR}/{os.path.basename(href)}.html"
                    with open(file_name, 'wb') as f:
                        f.write(html_response.content)
                    print(f"└── File HTML scaricato e salvato: {file_name}")

                    downloads += 1 
                else:
                    print(f"└── Nessun HTML scaricabile trovato per l'articolo: {article_url}")
                    skipped += 1
            except requests.RequestException as e:
                print(f"Errore nel recupero dell'articolo {article_url}: {e}")
                errors += 1
            except Exception as e:
                print(f"Errore nell'elaborazione dell'articolo {article_url}: {e}")
                errors += 1

        print(f"Batch completato. Totale download riusciti in questo batch: {downloads}")
        return {'downloaded': downloads, 'processed': processed, 'skipped': skipped, 'errors': errors}
    except requests.RequestException as e:
        print(f"Errore nel recupero dei risultati di ricerca: {e}")
        return {'downloaded': 0, 'processed': 0, 'skipped': 0, 'errors': 1}
    except Exception as e:
        print(f"Si è verificato un errore imprevisto: {e}")
        return {'downloaded': 0, 'processed': 0, 'skipped': 0, 'errors': 1}


# Funzione main
if __name__ == '__main__':

    # Condizione per verificare che sia stato fornito il numero corretto di argomenti
    if len(sys.argv) != 3:
        print('Sintassi corretta: python arxiv_scraper.py <query> <k>')
        sys.exit(1)

    query = sys.argv[1]

    try:
        k = int(sys.argv[2])  # Verifica che k sia un intero positivo
        if k <= 0:
            raise ValueError
    except ValueError:
        print("Errore: 'k' deve essere un numero intero positivo.")
        sys.exit(1)

    # Verifica che la directory 'papers' esista
    if not os.path.exists(OUTPUT_DIR):
        os.makedirs(OUTPUT_DIR)

    start_time = time.time()

    # Esegue il download e raccoglie le statistiche
    stats = download_articles(query, k)
    
    end_time = time.time()
    total_time = end_time - start_time

    # Calcolo dimensione cartella
    total_size = 0
    for dirpath, dirnames, filenames in os.walk(OUTPUT_DIR):
        for f in filenames:
            fp = os.path.join(dirpath, f)
            if not os.path.islink(fp):
                total_size += os.path.getsize(fp)
    stats['total_size_mb'] = total_size / (1024 * 1024)

    downloaded = stats['downloaded']
    errors = stats['errors']
    
    # Stampa le statistiche finali
    print("\n==============================")
    print("     STATISTICHE FINALI")
    print("==============================")

    print(f"Tempo totale: {total_time:.2f} sec")
    if downloaded > 0:
        print(f"Tempo medio per articolo: {total_time / downloaded:.2f} sec")
        
    print(f"Articoli trovati (esearch): {stats['processed']}")
    print(f"Articoli scaricati: {downloaded}")
    print(f"Errori: {errors}")

    if stats:
        print(f"Dimensione totale cartella: {stats['total_size_mb']:.2f} MB")
    else:
        print("Nessun file HTML trovato, impossibile calcolare statistiche.")

    print("\nCOMPLETATO! Articoli salvati in:", OUTPUT_DIR)
    print("==============================\n")