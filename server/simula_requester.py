import json
import requests
import random
from crypto_utils import P, PUB_N, mod_add, encrypt_paillier

SERVER_URL = "http://127.0.0.1:8000"

def trigger_android_app():
    print("--- SIMULAZIONE REQUESTER (Target: App Android) ---")
    
    # 1. Coordinate fittizie della richiesta (vicine a quelle dell'emulatore)
    # Sostituisci questi valori con le coordinate (in fixed point) in cui si trova il tuo emulatore!
    req_x = 444900000  # Esempio: Latitudine Bologna in fixed point
    req_y = 113400000  # Esempio: Longitudine Bologna in fixed point
    tolerance = 50000  # Tolleranza ampia per forzare il match
    
    personalized_blur_r = random.randint(0, P - 1)
    
    beta_plus_x = mod_add(req_x, personalized_blur_r)
    beta_plus_y = mod_add(req_y, personalized_blur_r)
    
    encrypted_r = encrypt_paillier(personalized_blur_r)
    encrypted_tol = encrypt_paillier(tolerance)

    # Payload identico al DTO che si aspetta l'App
    req_data = {
        "clientId": "PYTHON_REQUESTER_001",
        "category": "Elettricista", # Deve coincidere con la categoria scelta nell'emulatore
        "blurredX": beta_plus_x,
        "blurredY": beta_plus_y,
        "encryptedR": encrypted_r,
        "encryptedTol": encrypted_tol,
        "publicModulus": str(PUB_N)
    }

    print(f"Inviando Help-Request al server...")
    response = requests.post(f"{SERVER_URL}/help_request", json=req_data)
    
    print(f"Risposta Server: {response.json()}")
    print("Guarda l'emulatore Android: se la matematica è corretta, dovrebbe comparire il Popup di Match!")

if __name__ == "__main__":
    trigger_android_app()