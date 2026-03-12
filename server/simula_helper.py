import sys
import json
import asyncio
import time
import requests
import websockets
from crypto_utils import PUB_N, PRIV_SK, PUB_N_SQ, P, mod_sub, min_metric_distance

SERVER_URL_HTTP = "http://10.47.101.63:8080"
SERVER_URL_WS = "ws://10.47.101.63:8080/ws"

def decrypt_paillier(c_str: str) -> int:
    """Decifra Paillier: m = L(c^lambda mod n^2) * mu mod n"""
    # Semplificazione accademica: usando chiavi giocattolo, m = (c^phi(n) mod n^2 - 1) / n
    c = int(c_str)
    phi = (61 - 1) * (53 - 1) # Assumendo p=61, q=53 per N=3233
    mu = 2753 # Chiave privata
    u = pow(c, phi, PUB_N_SQ)
    l = (u - 1) // PUB_N
    return (l * mu) % PUB_N

async def heartbeat_loop(client_id: str):
    """Simula il NetworkService di Android inviando coordinate offuscate."""
    # Coordinate fittizie dell'Helper Python (es. Roma)
    helper_x, helper_y = 419000000, 124900000 
    
    while True:
        # In un test reale, l'Helper conoscerebbe il suo CS Blur. Qui simuliamo un invio valido.
        hb_data = {
            "clientId": client_id,
            "blurredX": helper_x, # Per semplicità nel PoC inviamo in chiaro
            "blurredY": helper_y,
            "encryptedBlur": "0" 
        }
        try:
            requests.post(f"{SERVER_URL_HTTP}/heartbeat", json=hb_data)
        except Exception as e:
            pass
        await asyncio.sleep(10) # Invia ogni 10 secondi

async def run_python_helper(client_id: str, will_match: bool, category: str):
    print(f"--- AVVIO PYTHON HELPER: {client_id} (Categoria: {category}) ---")
    
    # 1. Registrazione come Service Client
    reg_data = {
        "clientId": client_id,
        "category": category,
        "isHelper": True,
        "fcmToken": "PYTHON_NO_FCM", 
        "publicModulus": str(PUB_N)
    }
    requests.post(f"{SERVER_URL_HTTP}/register_profile", json=reg_data)

    # Avvia il demone dell'Heartbeat
    asyncio.create_task(heartbeat_loop(client_id))

    # 2. Connessione al WebSocket
    ws_url = f"{SERVER_URL_WS}/{client_id}"
    try:
        async with websockets.connect(ws_url) as ws:
            print("In attesa di Help-Request (Tuple Crittografiche)...")
            while True:
                msg = await ws.recv()
                data = json.loads(msg)
                
                if data["type"] == "COMPUTE_MATCH":
                    payload = data["payload"]
                    requester = payload['t1_requesterId']
                    print(f"\n🚨 Ricevuta tupla cifrata dal Requester: {requester}")
                    
                    # --- RISOLUZIONE DELLA TUPLA (PRIVACY ENGINE) ---
                    # 1. Coordinate offuscate
                    t3_x, t3_y = payload['t3_betaPlusX'], payload['t3_betaPlusY']
                    # 2. Rumori del server
                    t5_server_blurs = payload['t5_sumServerBlur']
                    
                    # 3. Decifratura Paillier dei rumori utente
                    sum_user_blur = decrypt_paillier(payload['t4_sumUserBlur'])
                    tolerance = decrypt_paillier(payload['t6_tolerance'])
                    
                    # 4. Calcolo Delta Topologico (Eq. 24 paper)
                    total_noise = (sum_user_blur + t5_server_blurs) % P
                    
                    # Per il test simuliamo le coordinate reali dell'Helper
                    my_x, my_y = 419000000, 124900000
                    
                    # Sottrazione del rumore e calcolo distanza
                    diff_x = mod_sub(t3_x, (my_x + total_noise) % P)
                    diff_y = mod_sub(t3_y, (my_y + total_noise) % P)
                    
                    dist_x = min_metric_distance(diff_x)
                    dist_y = min_metric_distance(diff_y)
                    
                    # Distanza Euclidea (approssimata al quadrato per evitare radici)
                    distance_sq = (dist_x * dist_x) + (dist_y * dist_y)
                    tolerance_sq = tolerance * tolerance
                    
                    # Valutazione finale della Tupla (OVERRIDE PER TEST)
                    is_match = (distance_sq <= tolerance_sq)
                    if will_match: is_match = True # Forzatura per scopi di dimostrazione
                    
                    if is_match:
                        print("✅ Esito: DISTANZA < TOLLERANZA. Match Positivo!")
                        match_confirm = {
                            "type": "MATCH_FOUND",
                            "payload": {
                                "requester_id": requester,
                                "target_id": client_id
                            }
                        }
                        await ws.send(json.dumps(match_confirm))
                        
                        print("⏳ Attendo 3 secondi per simulare la digitazione...")
                        await asyncio.sleep(3)
                        chat_msg = {
                            "type": "CHAT_MESSAGE",
                            "payload": {
                                "to": requester,
                                "message": f"Ciao! Sono il {category} Python. Sto arrivando!"
                            }
                        }
                        await ws.send(json.dumps(chat_msg))
                    else:
                        print("❌ Esito: DISTANZA > TOLLERANZA. Scarto la richiesta.")

    except Exception as e:
        print(f"Errore Helper: {e}")

if __name__ == "__main__":
    cid = sys.argv[1] if len(sys.argv) > 1 else "PythonHelper_1"
    behavior = sys.argv[2] if len(sys.argv) > 2 else "MATCH"
    cat = sys.argv[3] if len(sys.argv) > 3 else "Elettricista"
    
    will_match = (behavior.upper() == "MATCH")
    asyncio.run(run_python_helper(cid, will_match, cat))