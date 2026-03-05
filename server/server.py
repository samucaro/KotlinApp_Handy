import json
import os
import random
import asyncio
from typing import Dict, Optional
from dotenv import load_dotenv
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from pydantic import BaseModel
import firebase_admin
from firebase_admin import credentials, messaging
from crypto_utils import P, mod_add, mod_sub

# ==========================================
# INIZIALIZZAZIONE
# ==========================================
load_dotenv()

try:
    cred_dict = json.loads(os.environ["FIREBASE_CREDENTIALS"])
    cred = credentials.Certificate(cred_dict)
    firebase_admin.initialize_app(cred)
    print("INFO: Firebase Admin inizializzato correttamente.")
except Exception as e:
    print(f"WARN: Errore inizializzazione Firebase (FCM disabilitato): {e}")

app = FastAPI()

# Generazione Global Blur (r^g) all'avvio del server
R_GLOBAL = random.randint(0, P - 1)
print(f"INFO: Server avviato. Global Blur (r^g) generato: {R_GLOBAL}")

# --- STATO DEL SERVER ---
active_connections: Dict[str, WebSocket] = {}
client_registry: Dict[str, dict] = {}
server_noise_storage: Dict[str, int] = {} # Client-Specific Blurs (^{cs}r_i)
storage_map: Dict[str, str] = {}


# --- MATEMATICA OMOMORFICA (PAILLIER) ---
def homomorphic_add(enc_a_str: str, enc_b_str: str, pub_n_str: str) -> str:
    """Esegue l'addizione omomorfica Paillier: E(A + B) = (E(A) * E(B)) mod n^2"""
    c1 = int(enc_a_str)
    c2 = int(enc_b_str)
    n_sq = int(pub_n_str) ** 2
    return str((c1 * c2) % n_sq)


# --- GESTORE WEBSOCKET ---
class ConnectionManager:
    async def connect(self, websocket: WebSocket, client_id: str):
        await websocket.accept()
        active_connections[client_id] = websocket
        
    def disconnect(self, client_id: str):
        if client_id in active_connections:
            del active_connections[client_id]

    async def send_json(self, message: dict, client_id: str):
        if client_id in active_connections:
            try:
                await active_connections[client_id].send_json(message)
            except Exception as e:
                print(f"ERR: Errore invio WS al client {client_id}: {e}")
                self.disconnect(client_id)
        else:
            print(f"WARN: Client {client_id} non connesso al WebSocket.")

manager = ConnectionManager()


# --- MODELLI DTO ---
class RegistrationDTO(BaseModel):
    clientId: str
    category: str
    isHelper: bool
    fcmToken: Optional[str] = None
    publicModulus: Optional[str] = None

class HeartBeatModel(BaseModel):
    clientId: str
    blurredX: int
    blurredY: int
    encryptedBlur: str

#class FcmTokenDTO(BaseModel):
    #clientId: str
    #fcmToken: str

class HelpRequestModel(BaseModel):
    clientId: str
    category: str
    blurredX: int
    blurredY: int
    encryptedR: str
    encryptedTol: str
    publicModulus: str

def send_fcm_message(token: str, action: str, payload_dict: dict):
    if not token or token == "PYTHON_NO_FCM": return
    message = messaging.Message(data={"action": action, "payload": json.dumps(payload_dict)}, token=token)
    try:
        messaging.send(message)
    except Exception as e:
        print(f"ERR: Impossibile inviare messaggio FCM: {e}")


# ==========================================
# API HTTP - PROTOCOLLO SAMARITAN CLOUD
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
        "last_known_r": "0"
    }
    if data.isHelper:
        storage_map[data.clientId] = data.clientId
    return {"status": "registered"}

@app.post("/heartbeat")
async def receive_heartbeat(data: HeartBeatModel):
    target_uuid = data.clientId
    print(f"INFO: Ricevuta Profile-Update-Request da {target_uuid[:8]}")

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
    print(f"INFO: Ricevuta Help-Request da {requester_id[:8]} per categoria: {data.category}")

    candidates = [
        uid for uid, info in client_registry.items()
        if info.get("category") == data.category and info.get("isHelper") is True and uid != requester_id
    ]
    if not candidates: candidates.append(requester_id) # Auto-match test

    # Client-Specific Blur del Requester (^{cs}r^q)
    cs_r_req = server_noise_storage.get(requester_id, 0)

    for target_id in candidates:
        if target_id not in server_noise_storage: continue
        
        # Client-Specific Blur del Target (^{cs}r_j)
        cs_r_target = server_noise_storage[target_id] 
        target_enc_r = client_registry[target_id]["last_known_r"]

        # CALCOLO TUPLA
        t3_x = mod_add(mod_add(data.blurredX, cs_r_req), R_GLOBAL)
        t3_y = mod_add(mod_add(data.blurredY, cs_r_req), R_GLOBAL)
        t4_encrypted = homomorphic_add(data.encryptedR, target_enc_r, data.publicModulus)
        t5_server_blurs = mod_add(cs_r_req, cs_r_target)

        tupla_payload = {
            "t1_requesterId": requester_id,
            "t2_targetId": target_id,
            "t3_betaPlusX": t3_x,
            "t3_betaPlusY": t3_y,
            "t4_sumUserBlur": t4_encrypted,
            "t5_sumServerBlur": t5_server_blurs,
            "t6_tolerance": data.encryptedTol
        }

        service_client_id = storage_map.get(target_id)
        if service_client_id and service_client_id in client_registry:
            fcm_token = client_registry[service_client_id].get("fcmToken")

            if fcm_token == "PYTHON_NO_FCM":
                msg = {"type": "COMPUTE_MATCH", "payload": tupla_payload}
                asyncio.create_task(manager.send_json(msg, service_client_id))
            else:
                send_fcm_message(fcm_token, "COMPUTE_MATCH", tupla_payload)

    return {"status": "processed"}


# ==========================================
# GESTIONE WEBSOCKET
# ==========================================
async def handle_chat_message(data: dict, sender_id: str):
    payload = data.get("payload", {})
    target_id = payload.get("to")
    
    if not target_id: return

    forward_msg = {
        "type": "CHAT_MESSAGE",
        "payload": {
            "from": sender_id,
            "to": target_id,
            "message": payload.get("message", "")
        }
    }

    if target_id in active_connections:
        await manager.send_json(forward_msg, target_id)
    else:
        # Se l'app è in background, tenta di usare Firebase
        target_info = client_registry.get(target_id)
        if target_info and target_info.get("fcmToken"):
            send_fcm_message(target_info.get("fcmToken"), "CHAT_MESSAGE", forward_msg["payload"])


@app.websocket("/ws/{client_id}")
async def websocket_endpoint(websocket: WebSocket, client_id: str):
    await manager.connect(websocket, client_id)
    try:
        while True:
            text = await websocket.receive_text()
            data = json.loads(text)
            msg_type = data.get("type")
            
            if msg_type == "MATCH_FOUND":
                requester_id = data.get("payload", {}).get("requester_id")
                if requester_id:
                    print(f"INFO: Match confermato. Inoltro al Requester {requester_id[:8]}")
                    asyncio.create_task(manager.send_json(data, requester_id))
            elif msg_type == "CHAT_MESSAGE":
                await handle_chat_message(data, client_id)
            else:
                print(f"WARN: Tipo messaggio non riconosciuto: {msg_type}")

    except WebSocketDisconnect:
        manager.disconnect(client_id)
    except Exception as e:
        print(f"ERR: WebSocket connection error: {e}")
        manager.disconnect(client_id)

# Avvio: uvicorn server:app --reload --host 0.0.0.0 --port 8000