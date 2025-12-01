import json
import os
import sys
import datetime
from lxml import etree


def get_ref_dict(root_html):
    """
    Costruisce una mappa dagli ID dei riferimenti (es. id di figure/tabelle)
    ai paragrafi che li citano nel testo.

    Args:
        root_html (lxml.etree._Element): radice dell'albero HTML parsato con lxml.

    Returns:
        dict: {id_riferimento: [elemento_paragrafo, ...]}.
              L'id è normalizzato estraendo la parte dopo '#' in href.
              Vengono ignorati riferimenti senza href o senza '#'.
    """
    references_dict = {}
    # Trova tutti gli elementi che sono riferimenti (classe 'ltx_ref')
    all_references = root_html.xpath("//*[contains(@class, 'ltx_ref')]")

    for ref in all_references:
        ref_href = ref.get('href')

        # Pulisce l'href per ottenere solo l'ID (rimuove il '#')
        if ref_href and '#' in ref_href:
            ref_href = ref_href.split('#')[1]

        if ref_href:
            # Trova il paragrafo genitore che contiene questo riferimento
            paragraph = ref.xpath("./ancestor::p[@class='ltx_p'][1]")

            if len(paragraph) > 0:
                if ref_href in references_dict:
                    references_dict[ref_href].append(paragraph[0])
                else:
                    references_dict[ref_href] = [paragraph[0]]

    return references_dict


def parser(html, filename):
    """
    Analizza il contenuto HTML per estrarre tabelle, didascalie, note e contesti di citazione.
    
    Args:
        html: Stringa contenente l'HTML del paper.
        filename: Nome del file (per log o debug).
        
    Returns:
        dict: Dizionario con ID tabella come chiave e dati estratti come valore.
    """
    # Parsing dell'HTML con lxml
    root = etree.HTML(html)

    data = {}

    # Costruisce la mappa dei riferimenti incrociati (dove le tabelle sono citate nel testo)
    references_dict = get_ref_dict(root)

    # Trova tutte le tabelle (elementi con classe 'ltx_table' che contengono 'ltx_tabular')
    tables = root.xpath(
        "//*[contains(@class, 'ltx_table')][.//*[contains(@class, 'ltx_tabular')]]"
    )

    for t in tables:
        table_data = {}
        table_id = t.get('id')

        table_data["caption"] = ''
        table_data["table"] = ''
        table_data["footnotes"] = []
        table_data["references"] = []

        # --- Estrazione Didascalia (Caption) ---
        # Itera sui nodi figli di figcaption per gestire testo misto a tag (es. formule matematiche nella caption)
        caption_nodes = t.xpath(".//figcaption//node()")

        for node in caption_nodes:
            if isinstance(node, etree._ElementUnicodeResult):
                table_data["caption"] += node
            elif node.tag == 'span':
                # Ignora span vuoti o non rilevanti se necessario, qui sembra ignorare il contenuto degli span
                table_data["caption"] += ''
            else:
                # Per altri tag, converte l'intero sotto-albero in stringa
                table_data["caption"] += etree.tostring(
                    node, pretty_print=True
                ).decode()

        # --- Estrazione Contenuto Tabella ---
        # Salva l'HTML grezzo della parte tabellare
        tables_html = t.xpath(".//*[contains(@class, 'ltx_tabular')]")
        for t_html in tables_html:
            table_data["table"] += etree.tostring(
                t_html, pretty_print=True
            ).decode()

        # --- Estrazione Note a Piè di Pagina della Tabella ---
        # Cerca paragrafi dentro l'ambiente tabella che NON sono dentro la griglia tabellare stessa
        footnotes = t.xpath(
            ".//p[not(ancestor::*[contains(@class, 'ltx_tabular')])]"
        )
        for f in footnotes:
            node_footnotes = f.xpath(".//node()")
            # Ricostruisce il testo della nota gestendo nodi misti
            par = ''.join(
                e if isinstance(e, etree._ElementUnicodeResult)
                else '' if e.tag in {'span', 'em', 'a'} # Filtra alcuni tag di formattazione
                else etree.tostring(e, pretty_print=True).decode()
                for e in node_footnotes
            )
            table_data["footnotes"].append(par)

        # --- Estrazione Riferimenti nel Testo ---
        # Recupera i paragrafi che citano questa specifica tabella usando l'ID
        references = references_dict.get(table_id, [])
        for r in references:
            if isinstance(r, str):
                table_data["references"].append(r)
            else:
                # Estrae il testo dal nodo paragrafo
                ref_elements = r.xpath(".//node()")
                par = ''.join(
                    e if isinstance(e, etree._ElementUnicodeResult)
                    else '' if e.tag in {'span', 'em', 'a'}
                    else etree.tostring(e, pretty_print=True).decode()
                    for e in ref_elements
                )
                table_data["references"].append(par)

        data[table_id] = table_data

    return data


def save_json(data, filename, path='output'):
    if not os.path.exists(path):
        os.makedirs(path)
    with open(os.path.join(path, filename), 'w') as f:
        json.dump(data, f, indent=4)


if __name__ == '__main__':
    if len(sys.argv) != 3:
        print('Utilizzo: python parser.py <directory_sorgente> <directory_output>')
        sys.exit(1)

    source_path = sys.argv[1]
    output_path = sys.argv[2]

    for filename in sorted(os.listdir(source_path)):
        if filename.endswith(".html"):
            try:
                file_path = os.path.join(source_path, filename)
                with open(file_path, 'r') as f:
                    html = f.read()

                data = parser(html, filename)

                json_filename = f"{filename.replace('.html', '.json')}"
                save_json(data, json_filename, path=output_path)
            except Exception as e:
                # Se vuoi proprio zero output, puoi togliere anche questa print
                print(f"Errore durante l'elaborazione di {filename}: {e}", file=sys.stderr)
