import random

# ==========================================
# COSTANTI CRITTOGRAFICHE E GEOMETRICHE
# ==========================================
# Costante del campo finito (Prime Number)
# Definisce la dimensione dello spazio in cui si muovono gli utenti.
P = 999999937

# --- SIMULAZIONE TTP (Trusted Third Party) ---
# PER ABLATIO TEST:
# Sostituire queste chiavi "giocattolo" a 16-bit con chiavi reali a 1024/2048-bit 
# prima di raccogliere i dati prestazionali finali per i grafici.
PUB_N = 3233
PRIV_SK = 2753
PUB_N_SQ = PUB_N * PUB_N


# ==========================================
# ARITMETICA MODULARE SU CAMPO FINITO
# ==========================================
def mod_add(a: int, b: int) -> int:
    return ((a % P) + (b % P)) % P

def mod_sub(a: int, b: int) -> int:
    res = (a % P) - (b % P)
    return res + P if res < 0 else res

def min_metric_distance(delta: int) -> int:
    return (P - delta) if delta > P // 2 else delta


# ==========================================
# CIFRATURA ASIMMETRICA
# ==========================================
def encrypt_paillier(m: int) -> str:
    """
    Cifra un intero (es. rumore o tolleranza) usando la variante Paillier.
    Formula matematica: c = (1 + n)^m * r^n mod n^2
    
    Args:
        m (int): Il messaggio in chiaro da cifrare (Plaintext).
    Returns:
        str: Il testo cifrato (Ciphertext) convertito in stringa per il trasporto JSON.
    """
    # Generazione di un fattore di casualità 'r' per garantire l'indistinguibilità (CPA)
    r = random.randint(1, PUB_N - 1)

    # Calcolo della prima componente: (1 + n)^m mod n^2
    part1 = pow(1 + PUB_N, m, PUB_N_SQ)

    # Calcolo del fattore di randomizzazione: r^n mod n^2
    part2 = pow(r, PUB_N, PUB_N_SQ)

    # Combinazione e modulo finale
    c = (part1 * part2) % PUB_N_SQ
    return str(c)