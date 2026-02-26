# KotlinApp_Handy
Handy is a Kotlin-based Android proof-of-concept implementing a privacy-aware location-based help service inspired by the SamaritanCloud model. The system relies on blurred location profiles, local distance computation, and distributed matching to enable secure assistance requests without revealing users’ exact positions.

## ⚠️ Sicurezza e configurazione Firebase

Questo repository **non contiene chiavi Firebase sensibili**.  
Il file di chiave JSON del Service Account (`samaritan-cloud-firebase-adminsdk-*.json`) **non è incluso** e rimane solo in locale.

### 1️⃣ Creare il proprio progetto Firebase
Chi clona questo repository deve:
1. Creare un progetto su [Firebase Console](https://console.firebase.google.com/)
2. Generare un **Service Account (Admin SDK)**
3. Scaricare il file JSON della chiave privata

### 2️⃣ Configurare `.env` nel backend
1. Nella cartella `server/` crea un file `.env` (non committarlo!)
2. Inserisci il contenuto del JSON come variabile `FIREBASE_CREDENTIALS`:

```text
FIREBASE_CREDENTIALS='{"type":"service_account","project_id":"...","private_key_id":"...","private_key":"-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n","client_email":"...@...iam.gserviceaccount.com","client_id":"...","auth_uri":"https://accounts.google.com/o/oauth2/auth","token_uri":"https://oauth2.googleapis.com/token","auth_provider_x509_cert_url":"https://www.googleapis.com/oauth2/v1/certs","client_x509_cert_url":"https://www.googleapis.com/robot/v1/metadata/x509/..."}'