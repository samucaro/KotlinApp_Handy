import json
import random
import time
from typing import Dict, Optional
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from pydantic import BaseModel
import firebase_admin
from firebase_admin import credentials, messaging

# ==========================================
# INIZIALIZZAZIONE FIREBASE
# ==========================================
try:
    cred = credentials.Certificate("samaritan-cloud-firebase-adminsdk-fbsvc-fa3fd80e1b.json")
    firebase_admin.initialize_app(cred)
    print("Firebase Admin inizializzato con successo.")
except Exception as e:
    print(f"ATTENZIONE: Errore inizializzazione Firebase: {e}")

app = FastAPI()

# --- COSTANTI E CONFIGURAZIONE (Paper SamaritanCloud) ---
P = 999999937

# 1. GENERAZIONE GLOBAL BLUR (r^g) all'avvio del server
R_GLOBAL = random.randint(0, P - 1)
print(f"Server inizializzato. Global Blur (r^g) = {R_GLOBAL}")

# --- STATO DEL SERVER ---
active_connections: Dict[str, WebSocket] = {}
client_registry: Dict[str, dict] = {}
server_noise_storage: Dict[str, int] = {} # Client-Specific Blurs (^{cs}r_i)
storage_map: Dict[str, str] = {}


# --- MATEMATICA MODULARE Zp ---
def mod_add(a: int, b: int) -> int:
    return ((a % P) + (b % P)) % P

def mod_sub(a: int, b: int) -> int:
    res = (a % P) - (b % P)
    return res + P if res < 0 else res

# --- MATEMATICA OMOMORFICA (PAILLIER) ---
def homomorphic_add(enc_a_str: str, enc_b_str: str, pub_n_str: str) -> str:
    """
    Esegue l'addizione omomorfica: E(A + B) = (E(A) * E(B)) mod n^2
    """
    c1 = int(enc_a_str)
    c2 = int(enc_b_str)
    n = int(pub_n_str)
    n_sq = n * n
    
    # Moltiplicazione dei ciphertext modulo n^2
    c_sum = (c1 * c2) % n_sq
    return str(c_sum)


# --- GESTORE WEBSOCKET ---
class ConnectionManager:
    async def connect(self, websocket: WebSocket, client_id: str):
        await websocket.accept()
        active_connections[client_id] = websocket
    def disconnect(self, client_id: str):
        if client_id in active_connections:
            del active_connections[client_id]

manager = ConnectionManager()

# --- MODELLI DTO (Aggiornati per la crittografia forte) ---
class RegistrationDTO(BaseModel):
    clientId: str
    category: str
    isHelper: bool
    fcmToken: Optional[str] = None
    publicModulus: Optional[str] = None # 'n' di Paillier

class HeartBeatModel(BaseModel):
    clientId: str
    blurredX: int
    blurredY: int
    encryptedBlur: str # Deve essere Stringa per via delle dimensioni di Paillier

class FcmTokenDTO(BaseModel):
    clientId: str
    fcmToken: str

class HelpRequestModel(BaseModel):
    clientId: str
    category: str
    blurredX: int
    blurredY: int
    encryptedR: str # Stringa
    encryptedTol: str # Stringa
    publicModulus: str # Necessario per calcolare n^2 sul server

def send_fcm_message(token: str, action: str, payload_dict: dict):
    if not token: return
    message = messaging.Message(data={"action": action, "payload": json.dumps(payload_dict)}, token=token)
    try: messaging.send(message)
    except Exception as e: print(f"Errore FCM: {e}")

# ==========================================
# API HTTP - REDISTRIBUTION (Protocollo SamaritanCloud)
# ==========================================
@app.post("/register_profile")
async def register_user(data: RegistrationDTO):
    # Genera il Client-Specific Blur alla registrazione (se non esiste)
    if data.clientId not in server_noise_storage:
        server_noise_storage[data.clientId] = random.randint(0, P - 1)

    client_registry[data.clientId] = {
        "category": data.category,
        "isHelper": data.isHelper,
        "fcmToken": data.fcmToken,
        "publicModulus": data.publicModulus,
        "last_known_r": "0" # Stringa per Paillier
    }
    if data.isHelper: storage_map[data.clientId] = data.clientId
    return {"status": "registered"}

@app.post("/heartbeat")
async def receive_heartbeat(data: HeartBeatModel):
    target_uuid = data.clientId

    if target_uuid in client_registry:
        client_registry[target_uuid]["last_known_r"] = data.encryptedBlur
        
    cs_r_i = server_noise_storage.get(target_uuid, 0)

    # RE-BLURRING UPDATE (Eq. 10 paper): p^{rblur} = (blurredX - ^{cs}r_i + r^g) mod P
    reblurred_x = mod_add(mod_sub(data.blurredX, cs_r_i), R_GLOBAL)
    reblurred_y = mod_add(mod_sub(data.blurredY, cs_r_i), R_GLOBAL)

    service_client_id = storage_map.get(target_uuid)
    if service_client_id:
        msg = {
            "type": "STORE_PROFILE",
            "payload": {
                "target_id": target_uuid,
                "reblurred_x": reblurred_x,
                "reblurred_y": reblurred_y,
                "username": f"User_{target_uuid[:5]}",
                "category": client_registry.get(target_uuid, {}).get("category", "Unknown"),
                "rating": 5
            }
        }
        await manager.send_json(msg, service_client_id)
        return {"status": "processed"}
    return {"status": "no_service_client"}
    
@app.post("/help_request")
async def receive_help_request(data: HelpRequestModel):
    requester_id = data.clientId
    category_needed = data.category

    candidates = [
        uid for uid, info in client_registry.items()
        if info.get("category") == category_needed and info.get("isHelper") is True and uid != requester_id
    ]
    if not candidates: candidates.append(requester_id) # Auto-match test

    # Client-Specific Blur del Requester (^{cs}r^q)
    cs_r_req = server_noise_storage.get(requester_id, 0)

    for target_id in candidates:
        if target_id not in server_noise_storage: continue
        
        # Client-Specific Blur del Target (^{cs}r_j)
        cs_r_target = server_noise_storage[target_id] 
        target_enc_r = client_registry[target_id]["last_known_r"]

        # --- CALCOLO TUPLA (Protocollo esteso Eq. 12, 13, 14, 15) ---
        
        # T3: (^3T_{lj}) = (p_l^q + r^q + ^{cs}r^q + r^g) mod P
        # data.blurredX è già (p_l^q + r^q)
        t3_x = mod_add(mod_add(data.blurredX, cs_r_req), R_GLOBAL)
        t3_y = mod_add(mod_add(data.blurredY, cs_r_req), R_GLOBAL)

        # T4: Addizione Omomorfica dei blur cifrati (moltiplicazione modulo n^2)
        # ^4T_{lj} = \xi_{sk}^h(r^q + r_j)
        t4_encrypted = homomorphic_add(data.encryptedR, target_enc_r, data.publicModulus)

        # T5: Somma dei Client-Specific Blurs: (^{cs}r^q + ^{cs}r_j) mod P
        t5_server_blurs = mod_add(cs_r_req, cs_r_target)

        # Payload da inviare al Service Client
        tupla_payload = {
            "t1_requesterId": requester_id,
            "t2_targetId": target_id,
            "t3_betaPlusX": t3_x,
            "t3_betaPlusY": t3_y,
            "t4_sumUserBlur": t4_encrypted, # Ora è il vero ciphertext sommato!
            "t5_sumServerBlur": t5_server_blurs,
            "t6_tolerance": data.encryptedTol # Già cifrata dal client
        }

        service_client_id = storage_map.get(target_id)
        if service_client_id and service_client_id in client_registry:
            fcm_token = client_registry[service_client_id].get("fcmToken")
            # --- MODIFICA PER I TEST ---
            if fcm_token == "PYTHON_NO_FCM":
                # È uno script Python, mandiamo via WebSocket!
                msg = {
                    "type": "COMPUTE_MATCH",
                    "payload": tupla_payload
                }
                # (Nota: manager.send_json richiede await, quindi dovrai rendere asincrono l'invio o usare un task)
                import asyncio
                asyncio.create_task(manager.send_json(msg, service_client_id))
            else:
                # È l'App Android, usiamo Firebase
                send_fcm_message(fcm_token, "COMPUTE_MATCH", tupla_payload)

    return {"status": "processed"}


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

            msg_type = data.get("type")
            
            if msg_type == "MATCH_FOUND":
                print(f"MATCH CONFIRMED! Il Client {client_id} ha validato la connessione.")
                # Qui potresti notificare il requester che un helper ha accettato!
                # È un Heartbeat
            elif msg_type == "CHAT_MESSAGE":
                await handle_chat_message(data, client_id)
            else:
                print(f"WARN: Messaggio WS non riconosciuto da {client_id}: {text}")

    except WebSocketDisconnect:
        manager.disconnect(client_id)
    except Exception as e:
        print(f"Errore WS Critico: {e}")
        manager.disconnect(client_id)

# Avvio: uvicorn server:app --reload --host 0.0.0.0 --port 8000