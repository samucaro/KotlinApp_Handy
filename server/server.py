import json
import random
from typing import Dict, Optional
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException
from pydantic import BaseModel # <--- NECESSARIO PER IL DTO

app = FastAPI()

# --- COSTANTI E CONFIGURAZIONE ---
P = 999999937

# --- STATO DEL SERVER (In Memoria) ---
# Mappa: UUID -> WebSocket
active_connections: Dict[str, WebSocket] = {}

# Mappa: UUID -> Dati (Category, last_known_r, isHelper)
client_registry: Dict[str, dict] = {}

# Mappa: UUID -> Unico Intero (Il rumore server scalare)
server_noise_storage: Dict[str, int] = {}

# Mappa: Target UUID -> Service Client UUID (Chi custodisce chi)
storage_map: Dict[str, str] = {}


# --- MATEMATICA MODULARE ---
def mod_add(a: int, b: int) -> int:
    return ((a % P) + (b % P)) % P

def mod_sub(a: int, b: int) -> int:
    res = (a % P) - (b % P)
    return res + P if res < 0 else res

def generate_server_noise() -> int:
    return random.randint(0, P - 1)


# --- GESTORE CONNESSIONI WEBSOCKET ---
class ConnectionManager:
    async def connect(self, websocket: WebSocket, client_id: str):
        await websocket.accept()
        active_connections[client_id] = websocket
        print(f"--> WS Connesso: {client_id}")

    def disconnect(self, client_id: str):
        if client_id in active_connections:
            del active_connections[client_id]
        print(f"<-- WS Disconnesso: {client_id}")

    async def send_json(self, message: dict, client_id: str):
        if client_id in active_connections:
            try:
                # Android Gson si aspetta una stringa JSON
                await active_connections[client_id].send_text(json.dumps(message))
            except Exception as e:
                print(f"Errore invio a {client_id}: {e}")

manager = ConnectionManager()


# --- MODELLI DATI (DTO) PER API HTTP ---
class RegistrationDTO(BaseModel):
    clientId: str
    category: str
    isHelper: bool


# --- API HTTP (REST) ---

@app.post("/register_profile")
async def register_user(data: RegistrationDTO):
    """
    Endpoint HTTP chiamato da Retrofit.
    Salva l'utente nel registro e imposta la mappa di storage.
    """
    print(f"🌍 API REGISTRAZIONE: {data.clientId} (Helper: {data.isHelper})")
    
    # 1. Aggiorna il registro utenti
    client_registry[data.clientId] = {
        "category": data.category,
        "isHelper": data.isHelper,
        "last_known_r": 0 # Inizializzato a 0
    }

    # 2. SETUP DI TEST: Auto-assegnazione
    # Ogni utente è il Service Client di se stesso per il test locale.
    storage_map[data.clientId] = data.clientId

    return {"status": "registered", "clientId": data.clientId}


# --- LOGICA DI BUSINESS WEBSOCKET ---

async def handle_heartbeat(data: dict):
    """
    Input: HeartBeatDTO via WebSocket
    """
    target_uuid = data.get("clientId")
    beta_minus_x = data.get("blurredX")
    beta_minus_y = data.get("blurredY")
    user_enc_r = data.get("encryptedBlur") 

    # 1. Aggiorna registro (se l'utente esiste già grazie alla API)
    if target_uuid in client_registry:
        client_registry[target_uuid]["last_known_r"] = user_enc_r
    else:
        print(f"WARN: Heartbeat da utente non registrato {target_uuid}")
        return

    # 2. Gestione Rumore Server
    if target_uuid not in server_noise_storage:
        server_noise_storage[target_uuid] = generate_server_noise()

    srv_noise = server_noise_storage[target_uuid]

    # 3. Re-Blurring
    reblurred_x = mod_sub(beta_minus_x, srv_noise)
    reblurred_y = mod_sub(beta_minus_y, srv_noise)

    # 4. Invia al Service Client
    service_client_id = storage_map.get(target_uuid)

    if service_client_id:
        msg = {
            "type": "STORE_PROFILE",
            "payload": {
                "target_uuid": target_uuid,
                "reblurred_x": reblurred_x,
                "reblurred_y": reblurred_y,
                "username": f"User_{target_uuid[:5]}",
                "category": client_registry[target_uuid]["category"],
                "rating": 5
            }
        }
        await manager.send_json(msg, service_client_id)
        print(f"HEARTBEAT: {target_uuid} -> Reinstradato a {service_client_id}")


async def handle_help_request(data: dict):
    """
    Input: HelpRequestDTO via WebSocket
    """
    requester_id = data.get("clientId")
    category_needed = data.get("category")
    beta_plus_x = data.get("blurredX")
    beta_plus_y = data.get("blurredY")
    req_enc_r = data.get("encryptedR")
    req_tol = data.get("encryptedTol")

    print(f"RICHIESTA: {requester_id} cerca {category_needed}")

    # 1. Filtra candidati
    candidates = [
        uid for uid, info in client_registry.items()
        if info.get("category") == category_needed and uid != requester_id
    ]

    # DEBUG: Auto-match se non ci sono altri utenti
    if not candidates and requester_id in client_registry:
        if client_registry[requester_id].get("category") == category_needed:
            print("DEBUG: Nessun candidato. Uso il richiedente come target di test.")
            candidates.append(requester_id)

    # 2. Genera Rumore Server per il Richiedente
    srv_noise_req = generate_server_noise()

    # 3. Costruisci Tupla per ogni candidato
    for target_id in candidates:

        if target_id not in server_noise_storage:
            print(f"Skip {target_id}: Nessun heartbeat ricevuto.")
            continue

        srv_noise_target = server_noise_storage[target_id]
        target_enc_r = client_registry[target_id]["last_known_r"]

        # --- CALCOLO TUPLA ---
        t3 = mod_add(beta_plus_x, srv_noise_req)
        t4 = mod_add(beta_plus_y, srv_noise_req)
        t5 = mod_add(req_enc_r, target_enc_r)
        t6 = mod_add(srv_noise_req, srv_noise_target)

        tupla_payload = {
            "t1_requesterId": requester_id,
            "t2_targetId": target_id,
            "t3_betaPlusX": t3,
            "t4_betaPlusY": t4,
            "t5_sumUserBlur": t5,
            "t6_sumServerBlur": t6,
            "t7_tolerance": req_tol
        }

        service_client_id = storage_map.get(target_id)

        if service_client_id:
            msg = {
                "type": "COMPUTE_MATCH",
                "payload": tupla_payload
            }
            await manager.send_json(msg, service_client_id)
            print(f"MATCH CHECK: Inviata tupla a {service_client_id} (Target: {target_id})")


# --- ROUTING WEBSOCKET ---

@app.websocket("/ws/{client_id}")
async def websocket_endpoint(websocket: WebSocket, client_id: str):
    await manager.connect(websocket, client_id)
    try:
        while True:
            text = await websocket.receive_text()
            
            # Debug
            # print(f"DEBUG RAW WS: {text}")

            data = json.loads(text)

            # Dispatch logic:
            # NOTA: La registrazione è stata rimossa da qui perché è gestita via HTTP
            
            if "encryptedBlur" in data:
                # È un Heartbeat
                await handle_heartbeat(data)
            elif "encryptedTol" in data:
                # È una richiesta di aiuto
                await handle_help_request(data)
            else:
                print(f"WARN: Messaggio WS non riconosciuto da {client_id}")

    except WebSocketDisconnect:
        manager.disconnect(client_id)
    except Exception as e:
        print(f"Errore WS Critico: {e}")
        manager.disconnect(client_id)

# Avvio: uvicorn server:app --reload --host 0.0.0.0 --port 8000