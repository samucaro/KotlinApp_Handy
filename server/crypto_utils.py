import random

# Costante del campo finito
P = 999999937

# --- SIMULAZIONE TTP (Trusted Third Party) ---
PUB_N = 3233
PRIV_SK = 2753
PUB_N_SQ = PUB_N * PUB_N

def mod_add(a: int, b: int) -> int:
    return ((a % P) + (b % P)) % P

def mod_sub(a: int, b: int) -> int:
    res = (a % P) - (b % P)
    return res + P if res < 0 else res

def min_metric_distance(delta: int) -> int:
    return (P - delta) if delta > P // 2 else delta

def encrypt_paillier(m: int) -> str:
    """Cifra un intero usando la variante Paillier: c = (1 + n)^m * r^n mod n^2"""
    r = random.randint(1, PUB_N - 1)
    part1 = pow(1 + PUB_N, m, PUB_N_SQ)
    part2 = pow(r, PUB_N, PUB_N_SQ)
    c = (part1 * part2) % PUB_N_SQ
    return str(c)