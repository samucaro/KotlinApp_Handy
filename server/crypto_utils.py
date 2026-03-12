import random

# ==========================================
# COSTANTI CRITTOGRAFICHE E GEOMETRICHE
# ==========================================
# Costante del campo finito (Prime Number)
# Definisce la dimensione dello spazio in cui si muovono gli utenti.
P = 999999937

# --- SIMULAZIONE TTP (Trusted Third Party) ---
PUB_N = 7874450962341580680100328270946787813023151768294533948063850283744264597364978469443154976090256087773246271347638061447026650800911108869417315763553337118792328631748758369406495933645695922639504795549695482531907761922632704214404953801373923060838609126918449289628980106305071630794741711303678330680600566331729672162308377797439412509305660554916397502506647956617550679949941448797858116604387837879321607404910257736514395821404158689755009620148792500212375912950329378290290922032598104035392208842385446677401326661678909064874781042026516874990742861637172244627053441420308681625467106718541709153093
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