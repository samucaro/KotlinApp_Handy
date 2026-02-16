import websocket
import json
import threading
import time

# CONFIGURAZIONE
SERVER_URL = "ws://127.0.0.1:8000/ws/"
MY_FAKE_ID = "5ce95ea5-6ce0-44a5-a627-9574fee1ceb5"
def on_message(ws, message):
    data = json.loads(message)
    print(f"\nMESSAGGIO RICEVUTO DA ANDROID: {data}")
    
    # RISPOSTA AUTOMATICA (BOT)
    if data.get("type") == "CHAT_MESSAGE":
        sender = data["payload"]["from"]
        msg_text = data["payload"]["message"]
        print(f"   👤 Utente dice: {msg_text}")
        
        # Rispondiamo dopo 1 secondo
        response = {
            "type": "CHAT_MESSAGE",
            "payload": {
                "to": sender, # Rispondo al mittente
                "message": f"Ho ricevuto: '{msg_text}'. Passo e chiudo!"
            }
        }
        time.sleep(1)
        ws.send(json.dumps(response))
        print("Risposta inviata!")

def on_error(ws, error):
    print(f"Errore: {error}")

def on_close(ws, close_status_code, close_msg):
    print("Connessione chiusa")

def on_open(ws):
    print(f"CONNESSO AL SERVER COME: {MY_FAKE_ID}")
    print("In attesa di messaggi...")

if __name__ == "__main__":
    # URL completo: ws://localhost:8000/ws/ID_CLIENTE
    ws_url = SERVER_URL + MY_FAKE_ID
    ws = websocket.WebSocketApp(ws_url,
                                on_open=on_open,
                                on_message=on_message,
                                on_error=on_error,
                                on_close=on_close)
    ws.run_forever()