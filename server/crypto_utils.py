import random

# Costante del campo finito (deve coincidere con Android)
P = 999999937

# --- SIMULAZIONE TTP (Trusted Third Party) ---
# Chiavi Paillier di gruppo pre-generate (per i test accademici)
# In produzione verrebbero distribuite dal SecureKeyRepository
PUB_N = 3233  # Esempio di modulo pubblico (n) piccolo per i test
PRIV_SK = 2753 # Esempio di chiave privata (sk)
PUB_N_SQ = PUB_N * PUB_N

def mod_add(a: int, b: int) -> int:
    return ((a % P) + (b % P)) % P

def mod_sub(a: int, b: int) -> int:
    res = (a % P) - (b % P)
    return res + P if res < 0 else res

def min_metric_distance(delta: int) -> int:
    return (P - delta) if delta > P // 2 else delta

def encrypt_paillier(m: int) -> str:
    """Cifra un intero usando la variante Paillier dell'app Android"""
    # Formula: c = (1 + n)^m * r^n mod n^2
    r = random.randint(1, PUB_N - 1)
    part1 = pow(1 + PUB_N, m, PUB_N_SQ)
    part2 = pow(r, PUB_N, PUB_N_SQ)
    c = (part1 * part2) % PUB_N_SQ
    return str(c)

def decrypt_paillier(c_str: str) -> int:
    """Decifra il testo cifrato usando la sk"""
    # Questo è un mock decript per il test Python che inverte l'omomorfismo
    # In una tesi reale, useresti la libreria 'phe' o riprodurresti l'esatto inverso.
    # Per il nostro test, decifreremo simulando il risultato per validare il workflow.
    pass # Lo gestiremo nei client semplificando il calcolo per la dimostrazione