import json
import requests
import random
import time
from crypto_utils import P, PUB_N, mod_add, mod_sub, encrypt_paillier

SERVER_URL = "http://10.47.101.63:8080"

def trigger_android_app(category: str, tolerance_km: int):
    print(f"--- SIMULAZIONE REQUESTER (Target: App Android - Categoria: {category}) ---")
    
    # 1. Registrazione del Requester
    req_id = f"Python_Req_{random.randint(100, 999)}"
    reg_data = {
        "clientId": req_id,
        "category": "Generico",
        "isHelper": False,
        "fcmToken": "PYTHON_NO_FCM",
        "publicModulus": str(PUB_N)
    }
    requests.post(f"{SERVER_URL}/register_profile", json=reg_data)
    print("Registrazione completata. Attesa 2s per stabilità server...")
    time.sleep(2)
    
    # 2. Creazione della Help-Request
    req_x = 432608780  # Latitudine (es. Bologna in fixed point)
    req_y = 113822960  # Longitudine
    
    # Nel protocollo reale, il Requester usa il proprio CS Blur (^csr^q) per mascherare
    # Per il test mock, passiamo il rumore in chiaro al server che lo sommerà a R_GLOBAL
    personalized_blur_r = 0#random.randint(0, P - 1)
    
    beta_plus_x = mod_add(req_x, personalized_blur_r)
    beta_plus_y = mod_add(req_y, personalized_blur_r)
    
    # La tolleranza viene moltiplicata per il fattore fixed point (es. 10^6)
    tolerance_fixed = tolerance_km * 1000000 
    
    encrypted_r = encrypt_paillier(personalized_blur_r)
    encrypted_tol = encrypt_paillier(tolerance_fixed)

    req_data = {
        "clientId": req_id,
        "category": category, 
        "blurredX": beta_plus_x,
        "blurredY": beta_plus_y,
        "encryptedR": encrypted_r,
        "encryptedTol": encrypted_tol,
        "publicModulus": str(PUB_N)
    }

    print(f"Inviando Help-Request per {category} con tolleranza {tolerance_km}km...")
    response = requests.post(f"{SERVER_URL}/help_request", json=req_data)
    
    print(f"Risposta Server: {response.json()}")
    print("Guarda l'emulatore Android: se la tolleranza lo permette, dovrebbe vibrare e mostrare il popup!")

if __name__ == "__main__":
    import sys
    cat = sys.argv[1] if len(sys.argv) > 1 else "Elettricista"
    tol = int(sys.argv[2]) if len(sys.argv) > 2 else 50 # 50 km di default
    trigger_android_app(cat, tol)