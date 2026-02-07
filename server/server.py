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

class HeartBeatModel(BaseModel):
    clientId: str
    blurredX: int
    blurredY: int
    encryptedBlur: int

class HelpRequestModel(BaseModel):
    clientId: str
    category: str
    blurredX: int
    blurredY: int
    encryptedR: int
    encryptedTol: int


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

@app.post("/heartbeat")
async def receive_heartbeat(data: HeartBeatModel):
    """
    Riceve la posizione offuscata via HTTP e notifica il Service Client via WebSocket.
    """
    print(f"💓 HEARTBEAT HTTP RICEVUTO da {data.clientId}")
    
    target_uuid = data.clientId

    # 1. Aggiorna registro (se l'utente esiste)
    if target_uuid in client_registry:
        client_registry[target_uuid]["last_known_r"] = data.encryptedBlur
    else:
        # Se non esiste (magari riavvio server), lo ricrea al volo per evitare crash
        print(f"WARN: Utente {target_uuid} non trovato, lo registro al volo.")
        client_registry[target_uuid] = {"category": "Unknown", "isHelper": True, "last_known_r": data.encryptedBlur}
        storage_map[target_uuid] = target_uuid # Auto-storage per test

    # 2. Gestione Rumore Server
    if target_uuid not in server_noise_storage:
        server_noise_storage[target_uuid] = generate_server_noise()

    srv_noise = server_noise_storage[target_uuid]

    # 3. Re-Blurring (Sottrazione)
    reblurred_x = mod_sub(data.blurredX, srv_noise)
    reblurred_y = mod_sub(data.blurredY, srv_noise)

    # 4. Invia al Service Client (via WebSocket)
    service_client_id = storage_map.get(target_uuid)

    if service_client_id:
        msg = {
            "type": "STORE_PROFILE",
            "payload": {
                "target_id": target_uuid,
                "reblurred_x": reblurred_x,
                "reblurred_y": reblurred_y,
                "username": f"User_{target_uuid[:5]}",
                "category": client_registry[target_uuid].get("category", "Unknown"),
                "rating": 5
            }
        }
        # Qui usiamo il manager per inviare il messaggio sul WebSocket aperto
        await manager.send_json(msg, service_client_id)
        print(f"   -> STORE_PROFILE inviato a {service_client_id}")
        return {"status": "processed"}
    else:
        print("   -> ERRORE: Nessun Service Client assegnato.")
        return {"status": "no_service_client"}
    
@app.post("/help_request")
async def receive_help_request(data: HelpRequestModel):
    """
    Riceve la richiesta (BetaPlus), seleziona i candidati (Service Clients)
    e invia loro le Tuple per il calcolo della distanza.
    """
    print(f"🔎 RICHIESTA HTTP: {data.clientId} cerca {data.category}")
    
    requester_id = data.clientId
    category_needed = data.category

    # 1. Filtra candidati (Chi è della categoria giusta?)
    candidates = [
        uid for uid, info in client_registry.items()
        if info.get("category") == category_needed and uid != requester_id
    ]

    # TRUCCO PER IL TEST LOCALE:
    # Se non ci sono altri idraulici connessi, aggiungi te stesso alla lista
    # così puoi vedere se il matching funziona (ti auto-matchi).
    if not candidates:
        print("   -> Nessun candidato trovato. Uso me stesso come target di test.")
        candidates.append(requester_id)

    # 2. Genera Rumore Server per la Richiesta (Unico per questa transazione)
    srv_noise_req = generate_server_noise()

    # 3. Costruisci e Invia le Tuple ai Service Clients
    sent_count = 0
    for target_id in candidates:
        # Recupera i dati offuscati del target (salvati durante l'Heartbeat)
        if target_id not in server_noise_storage:
            print(f"   -> Skip {target_id}: Nessun dato di posizione (Heartbeat mancante).")
            continue

        srv_noise_target = server_noise_storage[target_id] # R_srv del target
        target_enc_r = client_registry[target_id]["last_known_r"] # Enc(R_target)

        # --- CALCOLO TUPLA (Protocollo SMPC) ---
        # T3 & T4: Aggiungiamo lo stesso rumore richiesta a X e Y
        t3 = mod_add(data.blurredX, srv_noise_req)
        t4 = mod_add(data.blurredY, srv_noise_req)

        # T5: Somma dei raggi quadrati (qui semplificata con somma modulare per demo)
        t5 = mod_add(data.encryptedR, target_enc_r)

        # T6: Somma dei rumori server (R_srv_req + R_srv_target)
        t6 = mod_add(srv_noise_req, srv_noise_target)

        # Payload da inviare al Service Client
        tupla_payload = {
            "t1_requesterId": requester_id,
            "t2_targetId": target_id,
            "t3_betaPlusX": t3,
            "t4_betaPlusY": t4,
            "t5_sumUserBlur": t5,
            "t6_sumServerBlur": t6,
            "t7_tolerance": data.encryptedTol
        }

        # Trova chi custodisce questo target (nel test locale sei tu)
        service_client_id = storage_map.get(target_id)

        if service_client_id:
            msg = {
                "type": "COMPUTE_MATCH", # Questo attiverà ComputeMatchStrategy su Android
                "payload": tupla_payload
            }
            await manager.send_json(msg, service_client_id)
            print(f"   -> COMPUTE_MATCH inviato a {service_client_id} (Target: {target_id})")
            sent_count += 1

    return {"status": "processed", "candidates_contacted": sent_count}

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
                "target_id": target_uuid,
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
            elif data.get("type") == "MATCH_FOUND":
                print(f"✅ MATCH CONFIRMED! Il Client {client_id} ha validato la connessione.")
                print(f"   - Requester: {data.get('requester_id')}")
                print(f"   - Target: {data.get('target_id')}")
                
                # QUI POTRESTI: Inviare una notifica push al Richiedente ("Trovato!")
                # Per ora ci basta il log verde.
            else:
                print(f"WARN: Messaggio WS non riconosciuto da {client_id}")

    except WebSocketDisconnect:
        manager.disconnect(client_id)
    except Exception as e:
        print(f"Errore WS Critico: {e}")
        manager.disconnect(client_id)

# Avvio: uvicorn server:app --reload --host 0.0.0.0 --port 8000