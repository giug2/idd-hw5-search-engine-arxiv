import json
import os
import string
from lxml import etree

# ---------------------------------------------------------
# CONFIGURAZIONE STOP WORDS
# ---------------------------------------------------------
# Lista base di parole da ignorare (articoli, preposizioni, ecc.) per l'inglese (e italiano base).
# Espandibile a piacere o sostituibile con librerie come NLTK/Spacy per maggiore precisione.
STOP_WORDS = {
    'the', 'a', 'an', 'and', 'or', 'but', 'in', 'on', 'at', 'to', 'for', 'of', 'with', 'by',
    'is', 'are', 'was', 'were', 'be', 'been', 'this', 'that', 'these', 'those', 'it', 'we',
    'can', 'may', 'should', 'table', 'figure', 'section', 'eq', 'et', 'al', 'shown', 'using',
    'il', 'lo', 'la', 'i', 'gli', 'le', 'un', 'uno', 'una', 'di', 'a', 'da', 'in', 'con', 'su', 'per',
    'tra', 'fra', 'è', 'sono'
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
    return "".join(node.itertext()).strip()

def get_node_html(node):
    """
    Restituisce la rappresentazione HTML (stringa) del nodo.
    """
    return etree.tostring(node, pretty_print=True).decode()

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
    # Aggiunto try-except per evitare che un file corrotto blocchi l'intero processo della cartella
    try:
        with open(html_path, 'r', encoding='utf-8') as f:
            html_content = f.read()
        
        root = etree.HTML(html_content)
    except Exception as e:
        print(f"Errore nella lettura/parsing del file {filename}: {e}")
        return
    
    # Struttura dati finale
    extracted_data = {}

    # --- MODIFICA LOGICA DI RICERCA ---
    # 1. Trova tutte le tabelle. Invece di cercare il contenitore esterno, cerchiamo direttamente il tag <table>
    #    che ha la classe 'ltx_tabular'. Questo trova la tabella ovunque essa sia.
    found_tables_nodes = root.xpath("//table[contains(@class, 'ltx_tabular')]")
    
    if not found_tables_nodes:
        # Se ancora non trova nulla, prova un fallback generico su qualsiasi tag <table>
        found_tables_nodes = root.xpath("//table")
        
        if not found_tables_nodes:
            print(f"Nessuna tabella trovata nel file {filename}. Genero JSON vuoto.")
            # RIMOSSO IL RETURN: Il codice prosegue per salvare un file JSON vuoto {}

    # Trova tutti i paragrafi del documento una sola volta per efficienza
    # MODIFICA: Escludiamo i paragrafi che sono DENTRO qualsiasi tabella (ancestor::table)
    all_paragraphs = root.xpath("//p[not(ancestor::table)]")

    for table_node in found_tables_nodes:
        
        # --- IDENTIFICAZIONE ID E WRAPPER ---
        # La tabella vera e propria è 'table_node'.
        # Ma l'ID e la Caption spesso sono nel genitore (es. <figure id="S1.T1">)
        table_id = table_node.get('id')
        wrapper_node = table_node # Di base, il wrapper è la tabella stessa

        # Se la tabella non ha ID, guardiamo il genitore (ancestor) più vicino che abbia un ID
        if not table_id:
            parent_wrapper = table_node.xpath("./ancestor::*[@id][1]")
            if parent_wrapper:
                wrapper_node = parent_wrapper[0]
                table_id = wrapper_node.get('id')
        
        # Se ancora nessun ID, saltiamo la tabella perché non referenziabile
        if not table_id:
            continue

        print(f" -> Trovata tabella ID: {table_id}")

        # --- A. Estrazione Caption ---
        # Cerchiamo la caption nel wrapper (che potrebbe essere la figure o la table stessa)
        caption_node = wrapper_node.xpath(".//figcaption")
        caption_text = ""
        if caption_node:
            caption_text = get_node_text(caption_node[0])

        # --- B. Estrazione Corpo Tabella (HTML) ---
        # Qui usiamo direttamente il nodo tabella trovato
        table_body_html = get_node_html(table_node)
        table_body_text_content = get_node_text(table_node) # Serve per estrarre i termini

        # --- C. Analisi Termini Informativi ---
        # Uniamo testo caption e testo interno della tabella per trovare le keywords
        source_text_for_terms = caption_text + " " + table_body_text_content
        target_terms = extract_informative_terms(source_text_for_terms)
        
        # --- D. Scansione Paragrafi (Citazioni e Contesto) ---
        citing_paragraphs = []
        contextual_paragraphs = []

        for p in all_paragraphs:
            p_text = get_node_text(p)
            p_html = get_node_html(p)
            
            # 1. Controllo Citazione Esplicita (Citing Paragraphs)
            # Cerca un link (<a>) che punta all'ID della tabella corrente
            refs = p.xpath(f".//a[contains(@href, '#{table_id}')]")
            
            is_citing = False
            if refs:
                citing_paragraphs.append(p_html)
                is_citing = True
            
            # 2. Controllo Termini (Contextual Paragraphs)
            if not is_citing:
                # Estraiamo i termini dal paragrafo
                p_terms = extract_informative_terms(p_text)
                
                # Calcoliamo l'intersezione
                common_terms = target_terms.intersection(p_terms)
                
                # SOGLIA: Consideriamo il paragrafo rilevante se condivide almeno N termini
                if len(common_terms) >= 2: 
                    contextual_paragraphs.append({
                        "html": p_html,
                        "matched_terms": list(common_terms) # Utile per debug
                    })

        # --- E. Salvataggio Dati Tabella ---
        extracted_data[table_id] = {
            "caption": caption_text,
            "body": table_body_html,
            "informative_terms_identified": list(target_terms), # Per debug/verifica
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
# ESECUZIONE
# ---------------------------------------------------------
if __name__ == '__main__':
    # -----------------------------------------------------
    # CONFIGURAZIONE DINAMICA PATH (Gerarchia Progetto)
    # -----------------------------------------------------
    
    # 1. Identifica la cartella dove si trova QUESTO script (es. .../Progetto/script)
    SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

    # 2. Identifica la cartella genitore (es. .../Progetto)
    #    Saliamo di un livello rispetto a 'scripts' usando dirname
    #    NOTA CORREZIONE: dirname accetta un solo argomento.
    PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)

    # 3. Definisci i percorsi relativi alla radice del progetto
    #    Usiamo join per scendere nella struttura delle cartelle: lucene -> src -> main -> resources
    RESOURCES_DIR = os.path.join(PROJECT_ROOT, 'lucene', 'src', 'main', 'resources')

    #    input sta in .../resources/input
    SOURCE_DIRECTORY = os.path.join(PROJECT_ROOT, 'papers')                         #AGGIORNARE PER UNIFICARE I PERCORSI DEI FILE HTML                                                         #percorso della cartella di input
    
    #    output lo mettiamo in .../resources/contenutoTabelle
    OUTPUT_DIRECTORY = os.path.join(RESOURCES_DIR, 'contenutoTabelle')                                                                #Cartella dove salvare i JSON risultanti

    # NUOVA FEATURE: Numero massimo di file da processare.
    # Imposta un numero intero (es. 1, 5, 20) per limitare l'esecuzione.
    # Imposta su None (o 0 o un numero negativo) per processare TUTTI i file nella cartella.
    NUM_FILES_TO_PROCESS = None                                                                                                       #numero di file di cui eseguire il parcing
    
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
        # 1. Recupera tutti i file nella cartella che finiscono con .html
        all_files = [f for f in os.listdir(SOURCE_DIRECTORY) if f.endswith(".html")]
        
        # Ordiniamo i file per avere un'esecuzione deterministica (es. alfabetica)
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


#    struttura del json generato:
#
#    id della tabella 1 :{
#        "caption": "Testo della didascalia",
#        "body": "<html>...</html>",                                                                    codice html grezzo dell atabella
#        "informative_terms_identified": ["termine1", "termine2", ...],                                 parole chiave individuate
#        "citing_paragraphs": [                                                                         lista dei paragrafi che citano la tabella
#            "<p>Paragrafo che cita la tabella</p>",
#            ...
#        ],
#        "contextual_paragraphs": [                                                                     lista dei paragrafi contestuali che non citano esplicitamente la tabella ma ne contengono i termini chiave individuati
#            {
#                "html": "<p>Paragrafo contestuale</p>",                                                codice html grezzo del paragrafo
#                "matched_terms": ["termine1", "termine2"]                                              parole chiave individuate nel paragrafo e corrispondenti alla tabella
#            },
#        ]
#
#        id della tabella 2 :{
#            ........
#        }
#    }