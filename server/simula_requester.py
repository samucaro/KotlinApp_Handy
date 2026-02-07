import requests
import json
import random
import uuid

# --- CONFIGURAZIONE ---
SERVER_URL = "http://127.0.0.1:8000"  # Localhost del tuo PC
HELPER_LAT = 44.49381
HELPER_LON = 11.34304

# Simuliamo un Requester a 20 metri di distanza
REQUESTER_LAT = HELPER_LAT + 0.00010 
REQUESTER_LON = HELPER_LON + 0.00010

# --- PARAMETRI CRITTOGRAFICI (Uguali a PrivacyEngine.kt) ---
P = 999999937
PRECISION = 10_000_000.0

def to_fixed_point(val):
    return int(val * PRECISION)

def generate_noise():
    return random.randint(1, P - 1)

def mod_add(a, b):
    return ((a % P) + (b % P)) % P

def main():
    print(f"--- 🚀 AVVIO SIMULATORE REQUESTER ---")
    
    # 1. Generazione ID
    client_id = str(uuid.uuid4())
    print(f"🆔 ID Generato: {client_id}")

    # 2. Registrazione (Opzionale ma consigliata)
    reg_payload = {
        "clientId": client_id,
        "category": "Generico",
        "isHelper": False
    }
    try:
        r = requests.post(f"{SERVER_URL}/register_profile", json=reg_payload)
        if r.status_code == 200:
            print("✅ Registrazione Requester OK")
        else:
            print(f"⚠️ Warning registrazione: {r.status_code}")
    except Exception as e:
        print(f"❌ Errore connessione al server: {e}")
        return

    # 3. Preparazione Matematica (Beta Plus) - COME L'APP
    pX = to_fixed_point(REQUESTER_LAT)
    pY = to_fixed_point(REQUESTER_LON)
    noise = generate_noise()
    
    beta_plus_x = mod_add(pX, noise)
    beta_plus_y = mod_add(pY, noise)
    
    # Tolleranza 500 metri (in fixed point o metri a seconda del server)
    # Nel tuo codice Kotlin passi tol.toDouble(), qui mandiamo 500
    tolerance = 5000

    print(f"📍 Posizione Requester: {REQUESTER_LAT}, {REQUESTER_LON}")
    print(f"🔒 Dati Offuscati (Beta+): X={beta_plus_x}, Y={beta_plus_y}")

    # 4. Invio Richiesta Aiuto
    help_payload = {
        "clientId": client_id,
        "category": "Generico", # Deve combaciare con quella del tuo Helper
        "blurredX": beta_plus_x,
        "blurredY": beta_plus_y,
        "encryptedR": noise,   # Per ora in chiaro come nell'app
        "encryptedTol": tolerance
    }

    print("\n📨 Invio richiesta al server...")
    try:
        # Assumo che l'endpoint sia /help_request come da standard naming
        r = requests.post(f"{SERVER_URL}/help_request", json=help_payload)
        
        if r.status_code == 200:
            print("✅ RICHIESTA INVIATA CON SUCCESSO!")
            print("👀 Ora guarda il Logcat di Android e lo schermo dell'Emulatore!")
        else:
            print(f"❌ Errore server: {r.status_code} - {r.text}")
            
    except Exception as e:
        print(f"❌ Errore invio richiesta: {e}")

if __name__ == "__main__":
    main()