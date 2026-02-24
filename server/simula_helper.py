import sys
import json
import asyncio
import requests
import websockets
from crypto_utils import PUB_N

SERVER_URL_HTTP = "http://127.0.0.1:8000"
SERVER_URL_WS = "ws://127.0.0.1:8000/ws"

async def run_python_helper(client_id: str, will_match: bool):
    print(f"--- AVVIO PYTHON HELPER: {client_id} ---")
    print(f"Comportamento simulato: {'MATCH POSITIVO' if will_match else 'MATCH NEGATIVO (Fuori raggio)'}")
    
    # 1. Registrazione con un token fittizio per far capire al server che siamo Python
    reg_data = {
        "clientId": client_id,
        "category": "Elettricista",
        "isHelper": True,
        "fcmToken": "PYTHON_NO_FCM", # Flag per il server
        "publicModulus": str(PUB_N)
    }
    requests.post(f"{SERVER_URL_HTTP}/register_profile", json=reg_data)

    # 2. Connessione al WebSocket
    ws_url = f"{SERVER_URL_WS}/{client_id}"
    async with websockets.connect(ws_url) as ws:
        print("In attesa che l'App Android invii una Help-Request...")
        while True:
            msg = await ws.recv()
            data = json.loads(msg)
            
            if data["type"] == "COMPUTE_MATCH":
                payload = data["payload"]
                requester = payload['t1_requesterId']
                print(f"\n🚨 Ricevuta tupla cifrata dall'App Android (Requester: {requester})")
                
                # Invece di decifrare Paillier in Python (che è lento e non è lo scopo del test),
                # forziamo l'esito del match in base al parametro di avvio dello script!
                if will_match:
                    print("✅ Esito: DISTANZA < TOLLERANZA. Invio conferma all'App Android!")
                    match_confirm = {
                        "type": "MATCH_FOUND",
                        "payload": {
                            "requester_id": requester,
                            "target_id": client_id
                        }
                    }
                    await ws.send(json.dumps(match_confirm))
                else:
                    print("❌ Esito: DISTANZA > TOLLERANZA. Scarto la richiesta (Nessun invio).")

if __name__ == "__main__":
    # Parametri da terminale: python simula_helper.py [ID] [MATCH/NOMATCH]
    cid = sys.argv[1] if len(sys.argv) > 1 else "PythonHelper_1"
    behavior = sys.argv[2] if len(sys.argv) > 2 else "MATCH"
    
    will_match = (behavior.upper() == "MATCH")
    asyncio.run(run_python_helper(cid, will_match))