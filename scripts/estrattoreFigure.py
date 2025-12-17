import json
import os
import string
import re
import time
from lxml import etree

# ---------------------------------------------------------
# CONFIGURAZIONE STOP WORDS PER FIGURE
# ---------------------------------------------------------
# Lista di parole non informative da ignorare nell'estrazione dei termini dalle caption.
STOP_WORDS = {
    # Articoli e preposizioni inglesi
    'the', 'a', 'an', 'and', 'or', 'but', 'in', 'on', 'at', 'to', 'for', 'of', 'with', 'by',
    'is', 'are', 'was', 'were', 'be', 'been', 'being', 'have', 'has', 'had', 'do', 'does', 'did',
    'this', 'that', 'these', 'those', 'it', 'we', 'they', 'them', 'their', 'its', 'our', 'your',
    'can', 'may', 'should', 'would', 'could', 'will', 'shall', 'must',
    # Termini generici di riferimento
    'table', 'figure', 'fig', 'section', 'eq', 'equation', 'et', 'al', 'shown', 'using', 'used',
    'show', 'shows', 'see', 'refer', 'reference', 'caption', 'image', 'picture', 'illustration',
    'above', 'below', 'left', 'right', 'top', 'bottom', 'as', 'also', 'here', 'there',
    # Verbi comuni
    'present', 'presents', 'presented', 'display', 'displays', 'displayed', 'depict', 'depicts',
    'illustrate', 'illustrates', 'illustrated', 'demonstrate', 'demonstrates', 'demonstrated',
    # Aggettivi/avverbi comuni
    'different', 'various', 'several', 'many', 'some', 'each', 'all', 'both', 'such', 'other',
    'first', 'second', 'third', 'new', 'proposed', 'respectively', 'corresponding',
    # Articoli e preposizioni italiane
    'il', 'lo', 'la', 'i', 'gli', 'le', 'un', 'uno', 'una', 'di', 'da', 'con', 'su', 'per',
    'tra', 'fra', 'è', 'sono', 'del', 'della', 'dei', 'delle', 'nel', 'nella', 'nei', 'nelle'
}


def extract_informative_terms(text):
    """
    Pulisce il testo e restituisce un set di termini unici "informativi".
    Rimuove punteggiatura, converte in minuscolo e filtra le stop words.
    """
    if not text:
        return set()
    
    # Rimuove la punteggiatura e converte in minuscolo
    translator = str.maketrans('', '', string.punctuation)
    clean_text = text.lower().translate(translator)
    
    # Divide in parole
    tokens = clean_text.split()
    
    # Filtra parole corte (< 3 caratteri), stop words e numeri puri
    informative_terms = {
        word for word in tokens 
        if word not in STOP_WORDS and len(word) > 2 and not word.isdigit()
    }
    
    return informative_terms


def get_node_text(node):
    """
    Estrae tutto il testo visibile da un nodo e dai suoi figli, pulito.
    """
    return "".join(node.itertext()).strip()


def get_node_html(node):
    """
    Restituisce la rappresentazione HTML (stringa) del nodo.
    """
    return etree.tostring(node, pretty_print=True).decode()


def build_full_image_url(src, base_url, article_id):
    """
    Costruisce l'URL completo dell'immagine partendo dal src relativo.
    
    Args:
        src: Il percorso src dell'immagine (può essere relativo o assoluto)
        base_url: L'URL base estratto dal tag <base> dell'HTML (es. "/html/2510.12175v3/")
        article_id: L'ID dell'articolo (es. "2510.12175v3")
    
    Returns:
        L'URL completo dell'immagine su arxiv.org
    """
    if not src:
        return ""
    
    # Se è già un URL completo, restituiscilo
    if src.startswith('http://') or src.startswith('https://'):
        return src
    
    # Base URL di arxiv per le immagini HTML
    arxiv_base = "https://arxiv.org"
    
    # Se abbiamo un base_url dal documento, usiamolo
    if base_url:
        # Rimuoviamo eventuali slash finali/iniziali per evitare duplicati
        base_url = base_url.rstrip('/')
        src = src.lstrip('/')
        
        # Se il base_url inizia con /, è relativo a arxiv.org
        if base_url.startswith('/'):
            return f"{arxiv_base}{base_url}/{src}"
        else:
            return f"{arxiv_base}/{base_url}/{src}"
    
    # Fallback: costruiamo l'URL usando l'article_id
    if article_id:
        return f"{arxiv_base}/html/{article_id}/{src}"
    
    # Ultimo fallback: restituisci il src originale
    return src


def process_figures_from_file(html_path, output_dir='output_figures'):
    """
    Funzione principale per estrarre le figure da un singolo file HTML.
    
    Per ogni figura estrae:
    - URL dell'immagine
    - Caption
    - Paragrafi che citano esplicitamente la figura
    - Paragrafi che contengono termini informativi presenti nella caption
    """
    
    # Statistiche per questo file
    file_stats = {
        'num_figures': 0,
        'with_caption': 0,
        'with_terms': 0,
        'citing_paragraphs': 0,
        'contextual_paragraphs': 0
    }
    
    # Verifica esistenza file
    if not os.path.exists(html_path):
        print(f"Errore: Il file {html_path} non esiste.")
        return file_stats

    filename = os.path.basename(html_path)
    article_id = filename.replace('.html', '')
    print(f"--- Elaborazione figure dal file: {filename} ---")

    # Parsing HTML
    try:
        with open(html_path, 'r', encoding='utf-8') as f:
            html_content = f.read()
        
        root = etree.HTML(html_content)
    except Exception as e:
        print(f"Errore nella lettura/parsing del file {filename}: {e}")
        return file_stats
    
    # Struttura dati finale
    extracted_data = {}

    # Estrazione del Titolo del documento
    title_nodes = root.xpath("//title")
    source_identifier = filename
    if title_nodes:
        page_title = get_node_text(title_nodes[0])
        if page_title and page_title != "arXiv reCAPTCHA":
            source_identifier = page_title

    # Estrazione dell'URL base dal tag <base>
    base_nodes = root.xpath("//base/@href")
    base_url = base_nodes[0] if base_nodes else None

    # Trova tutte le figure con immagini (class="ltx_figure" e contengono un <img>)
    # Escludiamo le tabelle (ltx_table) che hanno struttura diversa
    found_figure_nodes = root.xpath("//figure[contains(@class, 'ltx_figure') and .//img]")
    
    if not found_figure_nodes:
        print(f"Nessuna figura con immagine trovata nel file {filename}.")
        # Procediamo comunque per salvare un JSON vuoto

    # Trova tutti i paragrafi del documento (escludendo quelli dentro figure/tabelle)
    all_paragraphs = root.xpath("//p[not(ancestor::figure) and not(ancestor::table)]")

    for figure_node in found_figure_nodes:
        
        # --- IDENTIFICAZIONE ID ---
        figure_id = figure_node.get('id')
        
        # Se la figura non ha ID, proviamo a cercarlo nel genitore
        if not figure_id:
            parent_with_id = figure_node.xpath("./ancestor::*[@id][1]")
            if parent_with_id:
                figure_id = parent_with_id[0].get('id')
        
        # Se ancora nessun ID, generiamo uno basato sulla posizione
        if not figure_id:
            # Contiamo quante figure senza ID abbiamo già processato
            unnamed_count = sum(1 for k in extracted_data.keys() if k.startswith('unnamed_fig_'))
            figure_id = f"unnamed_fig_{unnamed_count + 1}"

        print(f" -> Trovata figura ID: {figure_id}")

        # --- A. Estrazione URL Immagine ---
        img_nodes = figure_node.xpath(".//img")
        image_url = ""
        image_alt = ""
        if img_nodes:
            img_node = img_nodes[0]
            src = img_node.get('src', '')
            image_url = build_full_image_url(src, base_url, article_id)
            image_alt = img_node.get('alt', '')

        # --- B. Estrazione Caption ---
        caption_node = figure_node.xpath(".//figcaption")
        caption_text = ""
        if caption_node:
            caption_text = get_node_text(caption_node[0])

        # --- C. Analisi Termini Informativi dalla Caption ---
        caption_terms = extract_informative_terms(caption_text)
        
        # Aggiungiamo anche termini dall'alt text se presente e informativo
        if image_alt and image_alt.lower() != "refer to caption":
            alt_terms = extract_informative_terms(image_alt)
            caption_terms = caption_terms.union(alt_terms)
        
        # --- D. Scansione Paragrafi ---
        citing_paragraphs = []
        contextual_paragraphs = []

        for p in all_paragraphs:
            p_text = get_node_text(p)
            p_html = get_node_html(p)
            
            # 1. Controllo Citazione Esplicita
            # Cerca un link (<a>) che punta all'ID della figura
            refs = p.xpath(f".//a[contains(@href, '#{figure_id}')]")
            
            is_citing = False
            if refs:
                citing_paragraphs.append(p_html)
                is_citing = True
            
            # 2. Controllo Termini (solo se non è già un paragrafo citante)
            if not is_citing and caption_terms:
                p_terms = extract_informative_terms(p_text)
                common_terms = caption_terms.intersection(p_terms)
                
                # SOGLIA: almeno 2 termini in comune per essere considerato rilevante
                if len(common_terms) >= 2:
                    contextual_paragraphs.append({
                        "html": p_html,
                        "matched_terms": list(common_terms)
                    })

        # --- E. Salvataggio Dati Figura ---
        extracted_data[figure_id] = {
            "source_file": source_identifier,
            "image_url": image_url,
            "caption": caption_text,
            "informative_terms_identified": list(caption_terms),
            "citing_paragraphs": citing_paragraphs,
            "contextual_paragraphs": contextual_paragraphs
        }
        
        # Aggiornamento statistiche file
        file_stats['num_figures'] += 1
        if caption_text:
            file_stats['with_caption'] += 1
        if caption_terms:
            file_stats['with_terms'] += 1
        file_stats['citing_paragraphs'] += len(citing_paragraphs)
        file_stats['contextual_paragraphs'] += len(contextual_paragraphs)

    # Salvataggio su JSON
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)
        
    json_filename = filename.replace('.html', '_figures.json')
    output_path = os.path.join(output_dir, json_filename)
    
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(extracted_data, f, indent=4, ensure_ascii=False)
        
    print(f"Salvataggio completato: {output_path} ({len(extracted_data)} figure estratte)")
    return file_stats


# ---------------------------------------------------------
# ESECUZIONE
# ---------------------------------------------------------
if __name__ == '__main__':
    # -----------------------------------------------------
    # CONFIGURAZIONE DINAMICA PATH (Gerarchia Progetto)
    # -----------------------------------------------------
    
    # 1. Identifica la cartella dove si trova QUESTO script
    SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

    # 2. Identifica la cartella genitore (root del progetto)
    PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)

    # 3. Definisci i percorsi relativi alla radice del progetto
    RESOURCES_DIR = os.path.join(PROJECT_ROOT, 'lucene', 'src', 'main', 'resources')

    # Input: cartella con i file HTML (usa 'input/papers' come sorgente principale)
    SOURCE_DIRECTORY = os.path.join(PROJECT_ROOT, 'input', 'papers')
    
    # Output: cartella dove salvare i JSON delle figure
    OUTPUT_DIRECTORY = os.path.join(PROJECT_ROOT, 'input', 'contenutoFigure')

    # Numero massimo di file da processare (None = tutti)
    NUM_FILES_TO_PROCESS = None
    
    # -----------------------------------------------------
    # LOGICA DI ESECUZIONE SU CARTELLA
    # -----------------------------------------------------
    
    print("-" * 60)
    print(f"Script Directory: {SCRIPT_DIR}")
    print(f"Project Root:     {PROJECT_ROOT}")
    print(f"Resources Dir:    {RESOURCES_DIR}")
    print(f"Input Folder:     {SOURCE_DIRECTORY}")
    print(f"Output Folder:    {OUTPUT_DIRECTORY}")
    print("-" * 60)

    if not os.path.exists(SOURCE_DIRECTORY):
        print(f"ERRORE: La cartella 'input' non esiste nel percorso atteso: {SOURCE_DIRECTORY}")
        print("Verifica che la cartella 'input' sia dentro 'lucene/src/main/resources/'.")
    else:
        # 1. Recupera tutti i file .html nella cartella
        all_files = [f for f in os.listdir(SOURCE_DIRECTORY) if f.endswith(".html")]
        all_files.sort()
        
        total_found = len(all_files)
        print(f"Totale file HTML trovati nella cartella: {total_found}")

        # 2. Applica il limite se impostato
        files_to_process = all_files
        if isinstance(NUM_FILES_TO_PROCESS, int) and NUM_FILES_TO_PROCESS > 0:
            print(f"Limite attivato: verranno processati solo i primi {NUM_FILES_TO_PROCESS} file.")
            files_to_process = all_files[:NUM_FILES_TO_PROCESS]
        else:
            print("Nessun limite impostato: verranno processati tutti i file.")

        # 3. Ciclo di elaborazione
        
        # Statistiche
        stats = {
            'total_files_processed': 0,
            'total_figures_extracted': 0,
            'files_with_figures': 0,
            'files_without_figures': 0,
            'start_time': time.time(),
            'total_captions': 0,
            'total_informative_terms_sets': 0,
            'total_citing_paragraphs': 0,
            'total_contextual_paragraphs': 0
        }

        for i, filename in enumerate(files_to_process, 1):
            full_path = os.path.join(SOURCE_DIRECTORY, filename)
            
            print(f"\n[{i}/{len(files_to_process)}] Inizio elaborazione...")
            file_stats = process_figures_from_file(full_path, output_dir=OUTPUT_DIRECTORY)
            num_figures = file_stats['num_figures']
            
            # Aggiornamento statistiche
            stats['total_files_processed'] += 1
            stats['total_figures_extracted'] += num_figures
            if num_figures > 0:
                stats['files_with_figures'] += 1
            else:
                stats['files_without_figures'] += 1
            
            stats['total_captions'] += file_stats['with_caption']
            stats['total_informative_terms_sets'] += file_stats['with_terms']
            stats['total_citing_paragraphs'] += file_stats['citing_paragraphs']
            stats['total_contextual_paragraphs'] += file_stats['contextual_paragraphs']

        # Calcolo statistiche finali
        end_time = time.time()
        execution_time = end_time - stats['start_time']
        avg_figures = 0
        if stats['total_files_processed'] > 0:
            avg_figures = stats['total_figures_extracted'] / stats['total_files_processed']

        print("\n" + "="*60)
        print("STATISTICHE")
        print("="*60)
        print(f"Tempo totale esecuzione:      {execution_time:.2f} secondi")
        print(f"Articoli processati:       {stats['total_files_processed']}")
        print(f"Immagini estratte:       {stats['total_figures_extracted']}")
        print(f"Articoli con immagini:              {stats['files_with_figures']}")
        print(f"Articoli senza immagini:            {stats['files_without_figures']}")
        print("-" * 60)
        print(f"Figure con caption:           {stats['total_captions']}")
        print(f"Figure con termini inform.:   {stats['total_informative_terms_sets']}")
        print(f"Paragrafi citanti totali:     {stats['total_citing_paragraphs']}")
        print(f"Paragrafi contestuali tot.:   {stats['total_contextual_paragraphs']}")
        print("="*60)

        print("\n--- Processo estrazione figure completato ---")


#    STRUTTURA DEL JSON GENERATO:
#
#    {
#        "id_figura_1": {
#            "source_file": "Titolo del paper o nome file",
#            "image_url": "https://arxiv.org/html/.../figs/image.png",
#            "caption": "Figure 1: Descrizione della figura...",
#            "informative_terms_identified": ["model", "architecture", "network", ...],
#            "citing_paragraphs": [
#                "<p>Paragrafo che cita esplicitamente la figura...</p>",
#                ...
#            ],
#            "contextual_paragraphs": [
#                {
#                    "html": "<p>Paragrafo con termini correlati...</p>",
#                    "matched_terms": ["model", "architecture"]
#                },
#                ...
#            ]
#        },
#        "id_figura_2": {
#            ...
#        }
#    }
