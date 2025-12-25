from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.service import Service
from webdriver_manager.chrome import ChromeDriverManager
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
import time

# pip install selenium
# pip install webdriver-manager

# --- CONFIGURAZIONE ---
URL_PAGINA = "http://localhost:8080" 
FILE_OUTPUT = "output/report_ricerca_completo.txt"

TEST_CASES = [
    # ARTICOLI
    {"query": "title:dataset", "filtri": ["articoli"]},
    {"query": "date:2025-08-12", "filtri": ["articoli"]},
    {"query": "authors:Yang", "filtri": ["articoli"]},
    {"query": "articleAbstract:AI-assisted AND authors:Yang", "filtri": ["articoli"]},
    # TABELLE
    {"query": "caption:Multimodal Text Generation", "filtri": ["tabelle"]},
    {"query": "body:Gemini-2.5 Pro", "filtri": ["tabelle"]},
    {"query": "body:Gemini-2.5 Flash OR caption:Text Generation", "filtri": ["tabelle"]},
    # IMMAGINI
    {"query": "caption:timestamp fields", "filtri": ["figure"]},
    {"query": "caption:multimodal fusion", "filtri": ["figure"]},
    {"query": "citing_paragraphs:audio modality", "filtri": ["figure"]},
    # ARTICOLI E TABELLE
    {"query": "timestamp fields", "filtri": ["articoli", "tabelle"]},
    {"query": "multimodal fusion", "filtri": ["articoli", "tabelle"]},
    # ARTICOLI E IMMAGINI
    {"query": "Gemini-2.5 Pro", "filtri": ["articoli", "figure"]},
    {"query": "AI-assisted", "filtri": ["articoli", "figure"]},
    # TABELLE E IMMAGINI
    {"query": "large language models", "filtri": ["figure", "tabelle"]},
    {"query": "machine learning", "filtri": ["figure", "tabelle"]},
    # ARTICOLI, TABELLE E IMMAGINI
    {"query": "impact of large language models on healthcare", "filtri": ["articoli", "tabelle", "figure"]},
    {"query": "remove speaker identity from source audio", "filtri": ["articoli", "tabelle", "figure"]}
]

def esegui_test():
    options = webdriver.ChromeOptions()
    # options.add_argument("--headless") # Togli il commento se non vuoi vedere il browser
    driver = webdriver.Chrome(service=Service(ChromeDriverManager().install()), options=options)
    
    with open(FILE_OUTPUT, "w", encoding="utf-8") as f:
        f.write("=== REPORT TEST LUCENE: RISULTATI E METRICHE ===\n\n")

        try:
            for test in TEST_CASES:
                driver.get(URL_PAGINA)
                query = test["query"]
                filtri = test["filtri"]

                f.write(f"QUERY: '{query}'\n")
                f.write(f"INDICI SELEZIONATI: {', '.join(filtri)}\n")
                f.write("-" * 50 + "\n")
                
                # Gestione Checkbox (basata sull'attributo value dell'HTML)
                for tipo in ["articoli", "tabelle", "figure"]:
                    checkbox = driver.find_element(By.XPATH, f"//input[@value='{tipo}']")
                    if tipo in filtri and not checkbox.is_selected():
                        checkbox.click()
                    elif tipo not in filtri and checkbox.is_selected():
                        checkbox.click()

                # Inserimento Query e Invio
                input_query = driver.find_element(By.ID, "query")
                input_query.clear()
                input_query.send_keys(query)
                driver.find_element(By.CSS_SELECTOR, "button[type='submit']").click()
                
                # Attesa che i risultati siano visibili
                wait = WebDriverWait(driver, 10)
                wait.until(EC.presence_of_element_located((By.CLASS_NAME, "results-container")))

                # Estrazione Risultati
                f.write("RISULTATI:\n")
                colonne = driver.find_elements(By.CLASS_NAME, "result-list-column")
                for colonna in colonne:
                    intestazione = colonna.find_element(By.TAG_NAME, "h3").text
                    f.write(f"  > {intestazione}\n")
                    
                    items = colonna.find_elements(By.CLASS_NAME, "result-item")
                    for item in items:
                        titolo = item.find_element(By.TAG_NAME, "a").text
                        score = item.find_element(By.CLASS_NAME, "score").text
                        f.write(f"    - {titolo} {score}\n")

                # Estrazione Metriche (dal div nascosto #metrics-container)
                f.write("\nMETRICHE PRESTAZIONALI:\n")
                # Cerchiamo tutti i blocchi dentro metrics-container
                metrics_blocks = driver.find_elements(By.CSS_SELECTOR, "#metrics-container > div")
                
                if not metrics_blocks:
                    f.write("  [!] Nessuna metrica disponibile per questa query.\n")
                else:
                    for block in metrics_blocks:
                        # Usiamo textContent perché gli elementi sono display:none
                        idx_name = block.find_element(By.CLASS_NAME, "metric-index").get_attribute("textContent")
                        m_time = block.find_element(By.CLASS_NAME, f"time-{idx_name}").get_attribute("textContent")
                        m_prec = block.find_element(By.CLASS_NAME, f"precision-{idx_name}").get_attribute("textContent")
                        m_rr = block.find_element(By.CLASS_NAME, f"rr-{idx_name}").get_attribute("textContent")
                        m_ndcg = block.find_element(By.CLASS_NAME, f"ndcg-{idx_name}").get_attribute("textContent")
                        
                        f.write(f"  [{idx_name.upper()}] -> Tempo: {m_time}ms | Precision: {m_prec} | RR: {m_rr} | NDCG: {m_ndcg}\n")

                f.write("\n" + "="*60 + "\n\n")
                print(f"Test completato con metriche per: {query}")

        except Exception as e:
            f.write(f"ERRORE DURANTE L'ESECUZIONE: {str(e)}\n")
            print(f"Errore: {e}")
        finally:
            driver.quit()

    print(f"\nTutto dade! Report generato: {FILE_OUTPUT}")

if __name__ == "__main__":
    esegui_test()
