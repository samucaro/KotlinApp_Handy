import requests
import json
import random
import uuid
import time

# --- CONFIGURAZIONE ---
SERVER_URL = "http://127.0.0.1:8000"
# Posizione vicina al Requester (circa 20m)
HELPER_LAT = 44.49381
HELPER_LON = 11.34304

P = 999999937
PRECISION = 10_000_000.0

def to_fixed_point(val):
    return int(val * PRECISION)

def generate_noise():
    return random.randint(1, P - 1)

def mod_sub(a, b):
    res = (a % P) - (b % P)
    return res + P if res < 0 else res

def main():
    print(f"--- AVVIO SIMULATORE HELPER ---")
    
    client_id = str(uuid.uuid4())
    category = "Idraulico" # Deve combaciare con la ricerca Android
    print(f"Helper ID: {client_id} [{category}]")

    # 1. Registrazione come HELPER (isHelper=True)
    reg_payload = {
        "clientId": client_id,
        "category": category,
        "isHelper": True
    }
    
    try:
        r = requests.post(f"{SERVER_URL}/register_profile", json=reg_payload)
        if r.status_code == 200:
            print("Helper registrato nel Server!")
        else:
            print(f"Errore reg: {r.status_code}")
            return
    except Exception as e:
        print(f"Server down: {e}")
        return

    # 2. Invio Heartbeat (Posizione Offuscata Beta Minus)
    # Simuliamo che l'Helper sia fermo lì
    pX = to_fixed_point(HELPER_LAT)
    pY = to_fixed_point(HELPER_LON)
    noise = generate_noise() # r

    # Beta- = (Pos - r)
    beta_minus_x = mod_sub(pX, noise)
    beta_minus_y = mod_sub(pY, noise)

    hb_payload = {
        "clientId": client_id,
        "blurredX": beta_minus_x,
        "blurredY": beta_minus_y,
        "encryptedBlur": noise # In chiaro per demo
    }

    print("Invio Heartbeat (Posizione disponibile)...")
    try:
        r = requests.post(f"{SERVER_URL}/heartbeat", json=hb_payload)
        if r.status_code == 200:
            print("Heartbeat ricevuto dal server. L'Helper è ora 'visibile'.")
            print("ORA VAI SU ANDROID E FAI UNA RICERCA COME 'Richiedente'!")
            print("   (Se cerchi 'Idraulico', il server dovrebbe trovare questo script come candidato)")
        else:
            print(f"Errore HB: {r.text}")
    except Exception as e:
        print(f"Errore invio HB: {e}")

if __name__ == "__main__":
    main()