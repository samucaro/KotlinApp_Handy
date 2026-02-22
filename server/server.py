import json
import random
import time
from typing import Dict, Optional
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException
from pydantic import BaseModel
import firebase_admin
from firebase_admin import credentials, messaging

# ==========================================
# INIZIALIZZAZIONE FIREBASE ADMIN SDK
# ==========================================
try:
    cred = credentials.Certificate("samaritan-cloud-firebase-adminsdk-fbsvc-fa3fd80e1b.json")
    firebase_admin.initialize_app(cred)
    print("Firebase Admin inizializzato con successo.")
except Exception as e:
    print(f"ATTENZIONE: Errore inizializzazione Firebase (File mancante?): {e}")

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

manager = ConnectionManager()

# --- MODELLI DATI (DTO) PER API HTTP ---
class RegistrationDTO(BaseModel):
    clientId: str
    category: str
    isHelper: bool
    fcmToken: Optional[str] = None

class HeartBeatModel(BaseModel):
    clientId: str
    blurredX: int
    blurredY: int
    encryptedBlur: int

class FcmTokenDTO(BaseModel):
    clientId: str
    fcmToken: str

class HelpRequestModel(BaseModel):
    clientId: str
    category: str
    blurredX: int
    blurredY: int
    encryptedR: int
    encryptedTol: int

# ==========================================
# HELPER: INVIO MESSAGGI FIREBASE (FCM)
# ==========================================
def send_fcm_message(token: str, action: str, payload_dict: dict):
    """
    Invia un Data Message tramite Firebase Cloud Messaging.
    Questo sveglierà l'app Android in background invocando HandyFcmService.
    """
    if not token:
        print(f"   -> INVIO FCM FALLITO: Token mancante per l'azione {action}")
        return

    # I Data Message FCM richiedono che tutti i valori nel dizionario siano stringhe.
    # Quindi serializziamo il payload in una stringa JSON.
    message = messaging.Message(
        data={
            "action": action,
            "payload": json.dumps(payload_dict)
        },
        token=token
    )
    
    try:
        response = messaging.send(message)
        print(f"   -> FCM Inviato [{action}]: {response}")
    except Exception as e:
        print(f"   -> ERRORE FCM: {e}")

# ==========================================
# API HTTP (REST) - LOGICA SAMARITAN CLOUD
# ==========================================
@app.post("/register_profile")
async def register_user(data: RegistrationDTO):
    """
    Endpoint HTTP chiamato da Retrofit.
    Salva l'utente nel registro e imposta la mappa di storage.
    """
    print(f"API REGISTRAZIONE: {data.clientId} (Helper: {data.isHelper})")
    
    # 1. Aggiorna il registro utenti
    client_registry[data.clientId] = {
        "category": data.category,
        "isHelper": data.isHelper,
        "fcmToken": data.fcmToken,
        "last_known_r": client_registry.get(data.clientId, {}).get("last_known_r", 0)
    }

    # 2. SETUP DI TEST: Auto-assegnazione
    # Ogni utente è il Service Client di se stesso per il test locale.
    if data.isHelper:
        storage_map[data.clientId] = data.clientId
    else:
        pass

    return {"status": "registered/updated", "clientId": data.clientId}

@app.post("/update_fcm_token")
async def update_token(data: FcmTokenDTO):
    if data.clientId in client_registry:
        client_registry[data.clientId]["fcmToken"] = data.fcmToken
        print(f"Token FCM aggiornato per {data.clientId}")
        return {"status": "token_updated"}
    else:
        # Se il server è stato riavviato e ha perso la RAM, ricrea l'utente base
        print(f"WARN: Token ricevuto per utente sconosciuto {data.clientId}. Ricreo voce base.")
        client_registry[data.clientId] = {"category": "Generico", "isHelper": False, "fcmToken": data.fcmToken, "last_known_r": 0}
        return {"status": "user_created_and_token_updated"}

@app.post("/heartbeat")
async def receive_heartbeat(data: HeartBeatModel):
    """
    Riceve la posizione offuscata via HTTP e notifica il Service Client via WebSocket.
    """
    print(f"HEARTBEAT HTTP RICEVUTO da {data.clientId}")
    
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
    print(f"RICHIESTA HTTP: {data.clientId} cerca {data.category}")
    
    requester_id = data.clientId
    category_needed = data.category

    # 1. Filtra candidati (Chi è della categoria giusta?)
    candidates = [
        uid for uid, info in client_registry.items()
        if info.get("category") == category_needed
        and info.get("isHelper") is True
        and uid != requester_id
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

        if service_client_id and service_client_id in client_registry:
            # Recuperiamo il token FCM del Service Client
            fcm_token = client_registry[service_client_id].get("fcmToken")

            # Inviamo tramite Firebase!
            send_fcm_message(
                token=fcm_token,
                action="COMPUTE_MATCH",
                payload_dict=tupla_payload
            )
            sent_count += 1

    return {"status": "processed", "candidates_contacted": sent_count}

# --- LOGICA DI BUSINESS WEBSOCKET ---

async def handle_chat_message(data: dict, sender_id: str):
    """
    Gestisce l'inoltro dei messaggi di chat tra utenti.
    Payload atteso: {"to": "uuid_destinatario", "message": "testo"}
    """
    payload = data.get("payload", {})
    recipient_id = payload.get("to")
    message_text = payload.get("message")

    if not recipient_id or not message_text:
        return

    print(f"CHAT: Da {sender_id} a {recipient_id}: {message_text}")

    # 1. Cerca se il destinatario è connesso
    if recipient_id in active_connections:
        socket = active_connections[recipient_id]
        
        # 2. Costruisci il messaggio di inoltro
        out_msg = {
            "type": "CHAT_MESSAGE",
            "payload": {
                "from": sender_id,      # Importante: chi lo manda?
                "message": message_text,
                "timestamp": int(time.time() * 1000)
            }
        }
        
        # 3. Invia
        await socket.send_text(json.dumps(out_msg))
    else:
        # Qui potresti salvare il messaggio in una lista "pending" se volessi supportare l'offline
        print(f"Destinatario {recipient_id} non connesso. Messaggio perso (per ora).")


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