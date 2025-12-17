import json
import os
import string
from lxml import etree, html

# --- IMPORTAZIONE NLTK ---
import nltk
from nltk.corpus import stopwords

# Scarica le risorse necessarie (silenziosamente se già presenti)
nltk.download("stopwords", quiet=True)

# ---------------------------------------------------------
# CONFIGURAZIONE STOP WORDS (SOLO LIBRERIA)
# ---------------------------------------------------------
# Non ci sono più parole inserite manualmente.
# Carichiamo i set direttamente dalla libreria NLTK.

# 1. Stop words Inglesi (per i paper scientifici)
english_sw = set(stopwords.words("english"))

# 2. Stop words Italiane (per sostituire la tua lista manuale di articoli/preposizioni)
italian_sw = set(stopwords.words("italian"))

# 3. Unione dei due set
STOP_WORDS = english_sw.union(italian_sw)

# Nota: Se vuoi escludere anche termini tecnici come "table", "figure", "section",
# ora dovrai aggiungerli qui oppure accettare che vengano estratti come termini.
# Al momento il filtro è puramente basato sulla libreria NLTK.


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
    
    # Filtra parole corte (< 3 caratteri) e stop words
    informative_terms = {
        word for word in tokens 
        if word not in STOP_WORDS and len(word) > 2 and not word.isdigit()
    }
    
    return informative_terms

def get_node_text(node):
    """
    Estrae tutto il testo visibile da un nodo e dai suoi figli, pulito.
    """
    # Aggiunto spazio " " nel join per evitare parole incollate
    return " ".join(node.itertext()).strip()

def get_node_html(node):
    """
    Restituisce la rappresentazione HTML (stringa) del nodo.
    """
    return etree.tostring(node, pretty_print=True).decode()

def clean_html_text(html_content):
    """
    Rimuove tutti i tag HTML e restituisce solo il testo pulito.
    """
    if not html_content:
        return ""
    try:
        # Usa lxml.html per parsare il frammento e estrarre il testo
        tree = html.fromstring(html_content)
        return tree.text_content().strip()
    except Exception:
        return ""

def process_single_file(html_path, output_dir='output'):
    """
    Funzione principale per processare un singolo file HTML.
    """
    
    # Verifica esistenza file
    if not os.path.exists(html_path):
        print(f"Errore: Il file {html_path} non esiste.")
        return

    filename = os.path.basename(html_path)
    print(f"--- Elaborazione file: {filename} ---")

    # Parsing HTML
    try:
        with open(html_path, 'r', encoding='utf-8') as f:
            html_content = f.read()
        
        root = etree.HTML(html_content)
    except Exception as e:
        print(f"Errore nella lettura/parsing del file {filename}: {e}")
        return
    
    # Struttura dati finale
    extracted_data = {}

    # --- Estrazione del Titolo/Nome File ---
    title_nodes = root.xpath("//title")
    source_identifier = filename # Default: nome del file
    if title_nodes:
        page_title = get_node_text(title_nodes[0])
        if page_title:
            source_identifier = page_title

    # --- Ricerca Tabelle ---
    # Cerca prima tabelle con classe specifica 'ltx_tabular'
    found_tables_nodes = root.xpath("//table[contains(@class, 'ltx_tabular')]")
    
    if not found_tables_nodes:
        # Fallback generico
        found_tables_nodes = root.xpath("//table")
        
        if not found_tables_nodes:
            print(f"Nessuna tabella trovata nel file {filename}. Genero JSON vuoto.")

    # Trova tutti i paragrafi del documento (escludendo quelli dentro le tabelle)
    all_paragraphs = root.xpath("//p[not(ancestor::table)]")

    for table_node in found_tables_nodes:
        
        # --- IDENTIFICAZIONE ID E WRAPPER ---
        table_id = table_node.get('id')
        wrapper_node = table_node 

        # Se la tabella non ha ID, guardiamo il genitore
        if not table_id:
            parent_wrapper = table_node.xpath("./ancestor::*[@id][1]")
            if parent_wrapper:
                wrapper_node = parent_wrapper[0]
                table_id = wrapper_node.get('id')
        
        # Se ancora nessun ID, saltiamo la tabella
        if not table_id:
            continue

        print(f" -> Trovata tabella ID: {table_id}")

        # --- A. Estrazione Caption ---
        caption_node = wrapper_node.xpath(".//figcaption")
        caption_text = ""
        if caption_node:
            caption_text = get_node_text(caption_node[0])

        # --- B. Estrazione Corpo Tabella ---
        table_body_html = get_node_html(table_node)
        table_body_text_content = get_node_text(table_node) 

        # --- C. Analisi Termini Informativi ---
        source_text_for_terms = caption_text + " " + table_body_text_content
        target_terms = extract_informative_terms(source_text_for_terms)
        
        # --- D. Scansione Paragrafi (Citazioni e Contesto) ---
        citing_paragraphs = []
        contextual_paragraphs = []

        for p in all_paragraphs:
            p_text = get_node_text(p)
            p_html = get_node_html(p)
            
            # 1. Controllo Citazione Esplicita
            refs = p.xpath(f".//a[contains(@href, '#{table_id}')]")
            
            is_citing = False
            if refs:
                citing_paragraphs.append(p_html)
                is_citing = True
            
            # 2. Controllo Termini (Contesto)
            if not is_citing:
                p_terms = extract_informative_terms(p_text)
                common_terms = target_terms.intersection(p_terms)
                
                # SOGLIA: almeno 2 termini in comune
                if len(common_terms) >= 2: 
                    contextual_paragraphs.append({
                        "html": p_html,
                        "matched_terms": list(common_terms)
                    })

        # --- E. Salvataggio Dati Tabella ---
        extracted_data[table_id] = {
            "source_file": source_identifier,
            "caption": caption_text,
            "body": table_body_text_content,       
            "html_code": table_body_html,          
            "informative_terms_identified": list(target_terms),
            "citing_paragraphs": citing_paragraphs,
            "contextual_paragraphs": contextual_paragraphs
        }

    # Salvataggio su JSON
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)
        
    json_filename = filename.replace('.html', '_data.json')
    output_path = os.path.join(output_dir, json_filename)
    
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(extracted_data, f, indent=4, ensure_ascii=False)
        
    print(f"Salvataggio completato: {output_path}")

# ---------------------------------------------------------
# STATISTICHE
# ---------------------------------------------------------
def summarize_tables(output_folder):
    """
    Legge tutti i file JSON nella cartella di output e stampa statistiche aggregate.
    """
    if not os.path.exists(output_folder):
        print("Cartella di output non trovata, impossibile generare statistiche.")
        return

    files = [f for f in os.listdir(output_folder) if f.endswith(".json")]

    summary = {
        "total_articles": len(files),
        "total_tables": 0,
        "field_counts": {}
    }

    # Campi da monitorare
    fields = [
        "source_file", 
        "caption", 
        "body", 
        "html_code", 
        "informative_terms_identified", 
        "citing_paragraphs", 
        "contextual_paragraphs"
    ]
    
    for field in fields:
        summary["field_counts"][field] = 0

    for f in files:
        file_path = os.path.join(output_folder, f)
        try:
            with open(file_path, "r", encoding="utf-8") as fh:
                data = json.load(fh)
                tables = data.values() 
                
                summary["total_tables"] += len(tables)

                for table in tables:
                    for field in fields:
                        value = table.get(field)
                        if value:
                            # Considera liste/stringhe non vuote come "piene"
                            if isinstance(value, (list, dict, str)):
                                if len(value) > 0:
                                    summary["field_counts"][field] += 1
                            else:
                                summary["field_counts"][field] += 1
        except Exception as e:
            print(f"Errore nella lettura delle statistiche per {f}: {e}")

    # Stampa risultati
    print("\n" + "="*30)
    print("       SUMMARY ESTRAZIONE       ")
    print("="*30)
    print(f"Articoli processati: {summary['total_articles']}")
    print(f"Tabelle estratte:    {summary['total_tables']}")
    print("-" * 30)
    print("Tabelle con campi popolati:")
    for field, count in summary["field_counts"].items():
        print(f"  {field:<30}: {count}")
    print("="*30 + "\n")

    return summary

# ---------------------------------------------------------
# ESECUZIONE
# ---------------------------------------------------------
if __name__ == '__main__':
    # -----------------------------------------------------
    # CONFIGURAZIONE DINAMICA PATH
    # -----------------------------------------------------
    
    SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
    PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)

    # Definisci i percorsi relativi alla radice del progetto
    SOURCE_DIRECTORY = os.path.join(PROJECT_ROOT, 'input', 'papers') 
    OUTPUT_DIRECTORY = os.path.join(PROJECT_ROOT, 'input', 'contenutoTabelle') 

    # NUOVA FEATURE: Numero massimo di file da processare (None = tutti)
    NUM_FILES_TO_PROCESS = None 
    
    # -----------------------------------------------------
    # LOGICA DI ESECUZIONE SU CARTELLA
    # -----------------------------------------------------
    
    print("-" * 60)
    print(f"Script Directory: {SCRIPT_DIR}")
    print(f"Project Root:     {PROJECT_ROOT}")
    print(f"Input Folder:     {SOURCE_DIRECTORY}")
    print(f"Output Folder:    {OUTPUT_DIRECTORY}")
    print("-" * 60)

    if not os.path.exists(SOURCE_DIRECTORY):
        print(f"ERRORE: La cartella 'papers' non esiste nel percorso atteso: {SOURCE_DIRECTORY}")
        print("Verifica che la cartella 'papers' sia nella root del progetto, allo stesso livello della cartella 'script'.")
    else:
        # 1. Recupera tutti i file nella cartella che finiscono con .html
        all_files = [f for f in os.listdir(SOURCE_DIRECTORY) if f.endswith(".html")]
        
        # Ordiniamo i file
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
        for i, filename in enumerate(files_to_process, 1):
            full_path = os.path.join(SOURCE_DIRECTORY, filename)
            
            print(f"\n[{i}/{len(files_to_process)}] Inizio elaborazione...")
            process_single_file(full_path, output_dir=OUTPUT_DIRECTORY)

        print("\n--- Processo su cartella completato ---")
        
        # --- STAMPA STATISTICHE FINALI ---
        summarize_tables(OUTPUT_DIRECTORY)