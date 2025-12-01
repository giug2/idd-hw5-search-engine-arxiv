# Libreria per l'estrazione delle immagini da una pagina web
# Implementata dallo script di web scraping per arXiv

# Guida di riferimento:
# https://dev.to/markpy/extracting-images-from-a-website-using-python-a-comprehensive-guide-4i2i


# Importazione delle librerie
import os
import json
import re
import requests
from bs4 import BeautifulSoup
from urllib.request import urlretrieve
from pathlib import Path

# ============ CONFIGURAZIONE ============
# Ottieni il percorso assoluto della directory in cui si trova lo script
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
HTML_FOLDER = os.path.join(SCRIPT_DIR, "papers")  # Cartella contenente i file HTML
OUTPUT_FOLDER = os.path.join(SCRIPT_DIR, "output_json")  # Dove salvare i file JSON
IMAGES_OUTPUT_FOLDER = os.path.join(SCRIPT_DIR, "paper_images") # Dove salvare le immagini scaricate
ENCODING = "utf-8"

# Termini non informativi (stopwords)
# La lista è presa da un gist pubblico su GitHub con le stopwords in inglese più comuni
gh_url = "https://gist.githubusercontent.com/rg089/35e00abf8941d72d419224cfd5b5925d/raw/12d899b70156fd0041fa9778d657330b024b959c/stopwords.txt"
try:
    stopwords_list = requests.get(gh_url).content
    STOPWORDS = set(stopwords_list.decode().splitlines()) 
except Exception as e:
    print(f"Attenzione: Impossibile scaricare stopwords ({e}). Uso set vuoto.")
    STOPWORDS = set()


def extract_caption_terms(caption_text):
    """
    Estrae termini significativi dalla caption (escludendo stopwords).
    Restituisce una lista di termini normalizzati.
    """
    # Converte in minuscolo
    text = caption_text.lower()
    # Rimuove punteggiatura eccetto spazi
    text = re.sub(r'[^a-zàèìòù0-9\s-]', ' ', text)
    # Divide per spazi e filtra stopwords
    terms = [
        t.strip() for t in text.split() 
        if len(t.strip()) > 2 and t.strip() not in STOPWORDS
    ]
    # Ritorna termini deduplicati
    return list(set(terms))


def find_paragraphs_with_image_reference(paragraphs, image_number):
    """
    Trova i paragrafi che citano l'immagine per numero.
    Cerca pattern come "Fig. 1", "Figure 1", "Figura 1", ecc.
    Restituisce una lista di paragrafi con indice e testo.
    """
    # Regex pattern per cercare riferimenti alla figura
    pattern = rf"(fig\.?|figure|figura|img|image)\s*[.:\s]*{image_number}\b"
    results = []
    
    # Itera su tutti i paragrafi
    for idx, paragraph in enumerate(paragraphs):
        if re.search(pattern, paragraph.lower()):
            results.append({
                "indice_paragrafo": idx,
                "testo": paragraph[:500]  # Limita lunghezza a 500 caratteri
            })
    
    return results


def find_paragraphs_with_terms(paragraphs, terms):
    """
    Trova i paragrafi che contengono almeno uno dei termini dalla caption.
    Restituisce una lista ordinata per rilevanza (numero di occorrenze).
    """
    # Se non ci sono termini, ritorna lista vuota
    if not terms:
        return []
    
    # Crea un pattern regex con tutti i termini
    pattern = r"\b(" + "|".join(re.escape(t) for t in terms) + r")\b"
    results = []
    
    # Itera su tutti i paragrafi
    for idx, paragraph in enumerate(paragraphs):
        if re.search(pattern, paragraph.lower()):
            # Trova tutte le occorrenze dei termini in questo paragrafo
            matches = re.findall(pattern, paragraph.lower())
            results.append({
                "indice_paragrafo": idx,
                "testo": paragraph[:500],
                "termini_trovati": list(set(matches)),
                "conteggio": len(matches)  # Numero totale di occorrenze
            })
    
    # Ordina per numero di occorrenze (più rilevanti prima)
    results.sort(key=lambda x: x["conteggio"], reverse=True)
    return results


def process_html_file(file_path):
    """
    Processa un singolo file HTML ed estrae informazioni per ogni immagine.
    Restituisce un dizionario con i dati strutturati.
    """
    # Legge il file HTML
    with open(file_path, "r", encoding=ENCODING, errors="ignore") as f:
        html = f.read()
    
    # Parsa il contenuto HTML
    soup = BeautifulSoup(html, "html.parser")
    
    # Estrae tutti i paragrafi dal documento
    # Usiamo solo 'p' per evitare duplicati da 'div'
    paragraphs = []
    for element in soup.find_all("p"):
        text = element.get_text(strip=True)
        # Filtra elementi vuoti o troppo piccoli
        if text and len(text) > 20:
            paragraphs.append(text)
    
    # Estrae tutte le immagini dal documento
    images = soup.find_all("img")
    
    images_data = []
    
    # Processa ogni immagine
    for idx, img in enumerate(images, start=1):
        # Estrae l'URL dell'immagine
        src = img.get("src", "")
        
        # Scarica l'immagine (Opzionale, basato sulla prima parte del tuo script originale)
        if src:
            try:
                img_name = src.split('/')[-1]
                # Gestione per URL relativi o assoluti se necessario
                if not src.startswith('http'):
                     # Qui si potrebbe dover gestire URL relativi se presenti
                     pass
                
                # Decommenta le righe sotto se vuoi scaricare fisicamente le immagini
                # save_path = os.path.join(IMAGES_OUTPUT_FOLDER, img_name)
                # urlretrieve(src, save_path)
                pass
            except Exception as e:
                print(f"Errore download immagine {src}: {e}")

        # Estrae la caption: prima prova l'attributo alt
        caption = img.get("alt", "")
        
        # Se la caption è "Refer to caption" o vuota, cerca altrove
        if not caption or "refer to caption" in caption.lower():
            caption = "" # Resetta se era "Refer to caption"
            
            # 1. Cerca <figcaption> nel parent <figure>
            parent = img.parent
            while parent and parent.name != "figure":
                parent = parent.parent
            
            if parent and parent.name == "figure":
                figcaption = parent.find("figcaption")
                if figcaption:
                    caption = figcaption.get_text(strip=True)
            
            # 2. Se ancora vuota, cerca classi specifiche di arXiv (LateXML)
            if not caption:
                # Cerca un div con classe ltx_caption vicino all'immagine
                # Spesso l'immagine è in un figure che contiene anche la caption
                parent = img.parent
                while parent and parent.name not in ["figure", "div"]: # Risale un po'
                    parent = parent.parent
                
                if parent:
                    # Cerca caption dentro il contenitore trovato
                    caption_node = parent.find(class_="ltx_caption")
                    if caption_node:
                        caption = caption_node.get_text(strip=True)

        # Pulisci la caption da prefissi comuni come "Figure 1:"
        clean_caption = caption
        figure_number = None
        
        # Cerca il numero della figura nella caption (es. "Figure 1: ...")
        match = re.search(r"(?:Figure|Fig\.?|Figura)\s*(\d+)", caption, re.IGNORECASE)
        if match:
            figure_number = match.group(1)
            # Rimuovi il prefisso dalla caption per l'analisi dei termini
            clean_caption = re.sub(r"^(?:Figure|Fig\.?|Figura)\s*\d+[:.]?\s*", "", caption, flags=re.IGNORECASE)

        # Estrae i termini significativi dalla caption pulita
        caption_terms = extract_caption_terms(clean_caption)
        
        # Trova paragrafi che citano questa figura
        # Se abbiamo trovato il numero della figura, usiamo quello, altrimenti usiamo l'indice (fallback)
        # Ma attenzione: l'indice è spesso sbagliato per loghi/icone.
        # Meglio: se non c'è numero di figura, probabilmente non è una figura citata.
        paragraphs_citing_figure = []
        if figure_number:
            paragraphs_citing_figure = find_paragraphs_with_image_reference(
                paragraphs, figure_number
            )
        
        # Trova paragrafi che contengono i termini della caption
        paragraphs_with_terms = find_paragraphs_with_terms(
            paragraphs, caption_terms
        )
        
        # Aggiunge i dati dell'immagine alla lista
        images_data.append({
            "numero_loop": idx, # Manteniamo l'indice per riferimento interno
            "numero_figura_estratto": figure_number,
            "url": src,
            "caption": caption,
            "termini_caption": caption_terms,
            "paragrafi_che_citano_figura": paragraphs_citing_figure,
            "paragrafi_con_termini_caption": paragraphs_with_terms
        })
    
    # Ritorna i dati strutturati dell'intero file
    return {
        "file": os.path.basename(file_path),
        "numero_immagini": len(images),
        "numero_paragrafi": len(paragraphs),
        "immagini": images_data
    }


# ============ MAIN ============

if __name__ == "__main__":
    # Crea le cartelle di output se non esistono
    os.makedirs(OUTPUT_FOLDER, exist_ok=True)
    os.makedirs(IMAGES_OUTPUT_FOLDER, exist_ok=True)

    if not os.path.exists(HTML_FOLDER):
        print(f"Errore: La cartella {HTML_FOLDER} non esiste.")
    else:
        # Itera su tutti i file nella cartella HTML
        for filename in os.listdir(HTML_FOLDER):
            # Processa solo i file con estensione .html
            if filename.endswith(".html"):
                file_path = os.path.join(HTML_FOLDER, filename)
                print(f"Processando: {filename}")
                
                try:
                    # Processa il file HTML
                    data = process_html_file(file_path)
                    
                    # Genera il nome del file JSON di output
                    output_filename = Path(filename).stem + ".json"
                    output_path = os.path.join(OUTPUT_FOLDER, output_filename)
                    
                    # Salva i dati in formato JSON
                    with open(output_path, "w", encoding="utf-8") as f:
                        json.dump(data, f, indent=2, ensure_ascii=False)
                    
                    print(f"  ✓ Salvato: {output_filename}")
                    print(f"    Immagini trovate: {data['numero_immagini']}")
                
                except Exception as e:
                    print(f"  ✗ Errore durante il processamento: {e}")

        print("\nProcessamento completato!")
        print(f"File JSON salvati in: {OUTPUT_FOLDER}/")